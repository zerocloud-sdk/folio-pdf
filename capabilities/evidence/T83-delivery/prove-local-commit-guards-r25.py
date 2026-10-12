"""Exercise resumed staging and lock retry against actual isolated Git indexes."""
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import threading
import time

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('t83_commit_git_guard_proof_r25', delivery / 'commit-git-guard-r25.py')
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)
baseline = '42fe5c797711070b319b9419e14fcb7adfa98735'
assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip() == baseline
observations = []


def git(directory, *arguments):
    return subprocess.run(['git', *arguments], cwd=directory, env=guard.git_environment(),
                          check=True, capture_output=True).stdout


def expected():
    return [('issue-83', 'new', hashlib.sha256(data).hexdigest(), str(len(data)), name)
            for name, data in [('a.txt', b'original\n'), ('b.txt', b'second\n')]]


def observe(directory, name, complete, accepted):
    try:
        value = guard.verify_staged(directory, iter(expected()), complete)
        actual = True
    except AssertionError:
        value = None
        actual = False
    assert actual == accepted, name
    observations.append({'case': name, 'accepted': actual,
                         'staged-files': value['files'] if value else None})


with tempfile.TemporaryDirectory(prefix='issue83-commit-proof-r25-', dir='/tmp') as location:
    directory = Path(location)
    git(directory, 'init', '--quiet')
    (directory / 'a.txt').write_bytes(b'original\n')
    git(directory, 'add', '--', 'a.txt')
    observe(directory, 'partial-correct-subset', False, True)
    observe(directory, 'complete-missing-row', True, False)
    (directory / 'a.txt').write_bytes(b'tampered\n')
    git(directory, 'add', '--', 'a.txt')
    observe(directory, 'partial-equal-length-altered-blob', False, False)
    (directory / 'a.txt').write_bytes(b'original\n')
    (directory / 'b.txt').write_bytes(b'second\n')
    git(directory, 'add', '--', 'a.txt', 'b.txt')
    observe(directory, 'complete-correct-set', True, True)
    (directory / 'unowned.txt').write_bytes(b'foreign\n')
    git(directory, 'add', '--', 'unowned.txt')
    observe(directory, 'partial-unowned-staged-path', False, False)
    observe(directory, 'complete-unowned-staged-path', True, False)
    assert git(directory, 'rev-list', '--all') == b''

with tempfile.TemporaryDirectory(prefix='issue83-lock-proof-r25-', dir='/tmp') as location:
    directory = Path(location)
    git(directory, 'init', '--quiet')
    # Reuse the existing baseline through a read-only object alternate. This
    # fixture creates no commit object or branch in the target repository.
    (directory / '.git/objects/info/alternates').write_text(str(root / '.git/objects') + '\n')
    (directory / '.git/HEAD').write_text(baseline + '\n')
    (directory / 'a.txt').write_bytes(b'original\n')
    lock = directory / '.git/index.lock'
    lock.write_bytes(b'fixture-owned-live-lock')
    original_wall = time.time
    stepped = {'value': False}
    time.time = lambda: original_wall() + (3600 if stepped['value'] else 0)
    jump = threading.Timer(0.05, lambda: stepped.update(value=True))
    release = threading.Timer(0.20, lock.unlink)
    jump.start()
    release.start()
    started = time.monotonic()
    try:
        result = guard.run_git(directory, ['add', '--', 'a.txt'], baseline, lock_timeout=5)
        assert result.returncode == 0 and stepped['value']
    finally:
        jump.join()
        release.join()
        time.time = original_wall
    observations.append({'case': 'live-lock-released-after-forward-wall-step',
                         'accepted': True, 'monotonic-seconds': time.monotonic() - started})
    lock.write_bytes(b'fixture-owned-live-lock')
    started = time.monotonic()
    try:
        guard.run_git(directory, ['add', '--', 'a.txt'], baseline, lock_timeout=0.05)
        raise AssertionError('Held lock must time out')
    except subprocess.CalledProcessError as error:
        assert error.returncode == 128 and lock.read_bytes() == b'fixture-owned-live-lock'
    finally:
        lock.unlink()  # Only the fixture owner removes its own synthetic lock.
    observations.append({'case': 'held-lock-monotonic-bound-and-no-removal',
                         'accepted': False, 'monotonic-seconds': time.monotonic() - started})
    started = time.monotonic()
    try:
        guard.run_git(directory, ['add', '--', 'missing.txt'], baseline, lock_timeout=5)
        raise AssertionError('Unrelated Git error must propagate')
    except subprocess.CalledProcessError as error:
        assert error.returncode == 128 and b'index.lock' not in error.stderr
    observations.append({'case': 'unrelated-error-never-retried', 'accepted': False,
                         'monotonic-seconds': time.monotonic() - started})
    assert git(directory, 'rev-parse', 'HEAD').decode().strip() == baseline

assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip() == baseline
output = {'result': 'pass', 'isolated-commits-created': 0, 'target-head-unchanged': True,
          'target-index-written': False, 'observations': observations,
          'guard-sha256': hashlib.sha256((delivery / 'commit-git-guard-r25.py').read_bytes()).hexdigest()}
path = delivery / 'local-commit-guard-proof-r25.json'
assert not path.exists()
path.write_text(json.dumps(output, indent=2) + '\n')
print('Nine real-Git admission/lock controls passed; zero commits created; target index untouched.')
