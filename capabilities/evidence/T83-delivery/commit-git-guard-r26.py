"""Guard resumed local staging and retry only a transient Git index lock."""
import hashlib
import os
from pathlib import Path
import subprocess
import time


def git_environment(extra=None):
    environment = os.environ.copy()
    environment['GIT_OPTIONAL_LOCKS'] = '0'
    environment['LC_ALL'] = 'C'
    if extra:
        environment.update(extra)
    return environment


def run_git(root, arguments, baseline, extra_environment=None, lock_timeout=90):
    """Wait for a live index writer; never remove a lock or retry another error."""
    assert lock_timeout > 0
    deadline = time.monotonic() + lock_timeout
    command = ['git', *arguments]
    environment = git_environment(extra_environment)
    marker = ("fatal: Unable to create '" + str(Path(root) / '.git/index.lock')
              + "': File exists.").encode()
    last_lock_failure = None
    while True:
        if last_lock_failure is not None and time.monotonic() >= deadline:
            raise subprocess.CalledProcessError(last_lock_failure.returncode, command,
                                                last_lock_failure.stdout, last_lock_failure.stderr)
        result = subprocess.run(command, cwd=root, env=environment, capture_output=True)
        if result.returncode == 0:
            return result
        if result.returncode != 128 or marker not in result.stderr:
            raise subprocess.CalledProcessError(result.returncode, command, result.stdout, result.stderr)
        last_lock_failure = result
        head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, env=environment).decode().strip()
        assert head == baseline, 'Do not retry a mutation after HEAD changes'
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise subprocess.CalledProcessError(result.returncode, command, result.stdout, result.stderr)
        print('Transient Git index lock; retrying ' + arguments[0]
              + ' within the monotonic ' + str(lock_timeout) + ' second bound.', flush=True)
        time.sleep(min(0.25, remaining))


def nul_values(stream):
    leftover = b''
    while chunk := stream.read(1024 * 1024):
        parts = (leftover + chunk).split(b'\0')
        leftover = parts.pop()
        yield from parts
    assert not leftover


def verify_staged(root, expected_rows, complete):
    """Bind every actual staged path/mode/blob to immutable ownership rows."""
    expected = iter(expected_rows)
    wanted = next(expected, None)
    environment = git_environment()
    process = subprocess.Popen(['git', 'diff', '--cached', '--raw', '--no-abbrev', '--no-renames', '-z'],
                               cwd=root, env=environment, stdout=subprocess.PIPE)
    blobber = subprocess.Popen(['git', 'cat-file', '--batch'], cwd=root, env=environment,
                               stdin=subprocess.PIPE, stdout=subprocess.PIPE)
    observed = nul_values(process.stdout)
    verified = {}
    names = set()
    total = 0
    successful = False
    try:
        while (raw_value := next(observed, None)) is not None:
            raw = raw_value.split()
            assert len(raw) == 5 and raw[0].startswith(b':')
            assert raw[1] in (b'100644', b'100755') and raw[4] in (b'A', b'M')
            name_bytes = next(observed)
            while wanted is not None and wanted[4].encode('utf-8') < name_bytes:
                assert not complete, 'Missing staged ownership row'
                wanted = next(expected, None)
            assert wanted is not None and wanted[4].encode('utf-8') == name_bytes, 'Unowned staged path'
            owner, change, digest, length, name = wanted
            assert owner == 'issue-83' and name not in names
            object_id = raw[3]
            if object_id not in verified:
                blobber.stdin.write(object_id + b'\n')
                blobber.stdin.flush()
                header = blobber.stdout.readline().split()
                assert len(header) == 3 and header[0] == object_id and header[1] == b'blob'
                size = int(header[2])
                remaining = size
                checksum = hashlib.sha256()
                while remaining:
                    chunk = blobber.stdout.read(min(1024 * 1024, remaining))
                    assert chunk, 'Truncated staged blob'
                    checksum.update(chunk)
                    remaining -= len(chunk)
                assert blobber.stdout.read(1) == b'\n'
                verified[object_id] = (checksum.hexdigest(), size)
            assert verified[object_id] == (digest, int(length)), 'Staged bytes differ from ownership'
            if not complete:
                names.add(name)
            total += 1
            if total % 20000 == 0:
                print('Verified ' + ('complete' if complete else 'partial')
                      + ' staged content for ' + str(total) + ' owned files.', flush=True)
            wanted = next(expected, None)
        assert not complete or wanted is None, 'Missing final staged ownership rows'
        successful = True
    finally:
        process.stdout.close()
        blobber.stdin.close()
        blobber.stdout.close()
        if not successful:
            for child in (process, blobber):
                if child.poll() is None:
                    child.terminate()
        codes = [child.wait(timeout=30) for child in (process, blobber)]
        if successful:
            assert codes == [0, 0]
    return {'files': total, 'names': names, 'unique-blobs': len(verified)}
