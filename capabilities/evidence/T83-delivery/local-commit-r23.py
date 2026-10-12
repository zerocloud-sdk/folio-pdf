"""Stage audited issue-83 files in bounded groups and make one authorized DCO commit."""
import gzip
import hashlib
import heapq
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

import yaml

root = Path('/workspace/folio-pdf')
delivery = root / 'capabilities/evidence/T83-delivery'
baseline = '42fe5c797711070b319b9419e14fcb7adfa98735'
suffix = 'final-r23'
summary_path = delivery / ('changed-file-ownership-' + suffix + '.json')
summary_bytes = summary_path.read_bytes()
ownership = json.loads(summary_bytes)
audit_path = delivery / 'final-audit-r23.json'
audit = json.loads(audit_path.read_text())
receipt = delivery / 'receipt-r23.md'
assert receipt.is_file() and audit['certification-tuples'] == 100 and audit['chain-records'] == 412
assert audit['recovery-tuples'] == 4 and audit['recovery-chain-records'] == 20
assert len(audit['selected-obligations']) == 14 and ownership['ownership'] == 'issue-83'
assert ownership['entry-unrelated-files'] == [] and ownership['baseline'] == baseline
manifest = root / ownership['manifest']['path']
manifest_snapshot = Path('/tmp/issue83-ownership-final-r23.tsv.gz')
assert not manifest_snapshot.exists()
with manifest.open('rb') as source, manifest_snapshot.open('xb') as destination:
    checksum = hashlib.sha256()
    while chunk := source.read(1024 * 1024):
        checksum.update(chunk)
        destination.write(chunk)
assert checksum.hexdigest() == ownership['manifest']['sha256']
sys.path.insert(0, str(root / 'scripts'))
spec = importlib.util.spec_from_file_location('t83_commit_guard', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
frozen = json.loads((delivery / 'source-freeze-final-r10.json').read_text())
staged_receipt = json.loads((delivery / 'candidate-final-r10/build-inputs.json').read_text())


def candidate_unchanged():
    assert driver.capture_build_inputs(root, contract) == {key: frozen[key] for key in ('inputs', 'contract-inputs')}
    assert driver.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json') == staged_receipt
    assert driver.reference(root, root / audit['current-index']['path']) == audit['current-index']


candidate_unchanged()


def git(*args, capture=False):
    command = ['git', *args]
    if capture:
        return subprocess.check_output(command, cwd=root)
    print('Running ' + ' '.join(command[:3]), flush=True)
    subprocess.run(command, cwd=root, check=True)


assert git('rev-parse', 'HEAD', capture=True).decode().strip() == baseline
assert git('branch', '--show-current', capture=True).decode().strip() == 'main'
assert git('diff', '--name-only', '--cached', capture=True) == b''


def rows():
    with gzip.open(manifest_snapshot, 'rt', encoding='utf-8') as stream:
        assert next(stream) == 'ownership\tchange\tsha256\tbytes\tpath\n'
        for line in stream:
            fields = line.rstrip('\n').split('\t')
            assert len(fields) == 5 and fields[0] == 'issue-83'
            yield fields


groups = {}
exact = []
count = 0
for owner, change, digest, length, name in rows():
    path = root / name
    assert path.is_file() and not path.is_symlink() and path.stat().st_size == int(length)
    assert not name.startswith(('.git/', '.build-cache/', 'target/')) and '/target/' not in name
    if name.startswith('capabilities/evidence/foundation/T83-'):
        prefix = '/'.join(name.split('/')[:6])
        groups[prefix] = groups.get(prefix, 0) + 1
    elif name.startswith('capabilities/evidence/T83-delivery/') and len(name.split('/')) > 4:
        prefix = '/'.join(name.split('/')[:4])
        groups[prefix] = groups.get(prefix, 0) + 1
    else:
        exact.append(name)
    count += 1
assert count == ownership['changed-files']
self_files = [manifest.relative_to(root).as_posix(), summary_path.relative_to(root).as_posix()]
assert all(name not in exact for name in self_files)
self_rows = [
    ('issue-83', 'new', ownership['manifest']['sha256'], str(manifest_snapshot.stat().st_size), self_files[0]),
    ('issue-83', 'new', hashlib.sha256(summary_bytes).hexdigest(), str(len(summary_bytes)), self_files[1])]
exact += self_files
assert shutil.disk_usage(root).free > 2 * 1024**3


def pack():
    git('repack', '-d', '--window-memory=64m', '--threads=2', '--max-pack-size=512m')
    assert os.statvfs(root).f_favail > 150000 and shutil.disk_usage(root).free > 1024**3


git('add', '--', *exact)
pending = len(exact)
for number, (prefix, entries) in enumerate(sorted(groups.items()), 1):
    print('Audited staging group ' + str(number) + '/' + str(len(groups))
          + ': ' + str(entries) + ' files at ' + prefix, flush=True)
    git('add', '--', prefix)
    pending += entries
    if pending >= 30000 or os.statvfs(root).f_favail < 750000:
        pack()
        pending = 0
if pending:
    pack()


def nul_values(stream):
    leftover = b''
    while chunk := stream.read(1024 * 1024):
        parts = (leftover + chunk).split(b'\0')
        leftover = parts.pop()
        yield from parts
    assert not leftover


expected = heapq.merge(rows(), sorted(self_rows, key=lambda row: row[4].encode('utf-8')),
                      key=lambda row: row[4].encode('utf-8'))
process = subprocess.Popen(['git', 'diff', '--cached', '--raw', '--no-abbrev', '--no-renames', '-z'],
                           cwd=root, stdout=subprocess.PIPE)
observed = nul_values(process.stdout)
blobber = subprocess.Popen(['git', 'cat-file', '--batch'], cwd=root, stdin=subprocess.PIPE, stdout=subprocess.PIPE)
verified_blobs = {}
total = 0
try:
    for owner, change, digest, length, name in expected:
        raw = next(observed).split()
        assert len(raw) == 5 and raw[0].startswith(b':')
        assert raw[1] in (b'100644', b'100755') and raw[4] in (b'A', b'M')
        assert next(observed) == name.encode('utf-8'), 'Staged paths must exactly match final ownership plus its two self-excluded files'
        object_id = raw[3]
        if object_id not in verified_blobs:
            blobber.stdin.write(object_id + b'\n')
            blobber.stdin.flush()
            header = blobber.stdout.readline().split()
            assert len(header) == 3 and header[0] == object_id and header[1] == b'blob'
            size = int(header[2])
            remaining = size
            checksum = hashlib.sha256()
            while remaining:
                chunk = blobber.stdout.read(min(1024 * 1024, remaining))
                assert chunk, 'Staged blob must not be truncated'
                checksum.update(chunk)
                remaining -= len(chunk)
            assert blobber.stdout.read(1) == b'\n'
            verified_blobs[object_id] = (checksum.hexdigest(), size)
        assert verified_blobs[object_id] == (digest, int(length)), 'Staged bytes must exactly match audited SHA-256 and length: ' + name
        total += 1
        if total % 20000 == 0:
            print('Verified exact staged content for ' + str(total) + ' owned files.', flush=True)
finally:
    blobber.stdin.close()
    blobber.stdout.close()
    assert blobber.wait(timeout=60) == 0
assert next(observed, None) is None and process.wait() == 0
assert total == count + 2
assert git('rev-parse', 'HEAD', capture=True).decode().strip() == baseline
candidate_unchanged()
print('Exact audited staging complete: ' + str(total) + ' files. Creating the sole authorized commit.', flush=True)
environment = os.environ.copy()
environment.update({'GIT_AUTHOR_NAME': 'mabaiqiu', 'GIT_AUTHOR_EMAIL': 'mabaiqiu@gmail.com',
                    'GIT_COMMITTER_NAME': 'mabaiqiu', 'GIT_COMMITTER_EMAIL': 'mabaiqiu@gmail.com'})
subprocess.run(['git', '-c', 'user.name=mabaiqiu', '-c', 'user.email=mabaiqiu@gmail.com',
    'commit', '--quiet', '-s', '-m', 'Certify Worker recovery and bounded Foundation scale (#83)',
    '-m', 'Use monotonic workflow budgets, preserve absolute wall deadlines, and bind recovery/scale certification to the verified Ubuntu/Temurin 8/11/17/21 candidate.'],
    cwd=root, env=environment, check=True)
head = git('rev-parse', 'HEAD', capture=True).decode().strip()
assert git('rev-parse', 'HEAD^', capture=True).decode().strip() == baseline
assert git('rev-list', '--count', baseline + '..HEAD', capture=True).decode().strip() == '1'
assert git('show', '-s', '--format=%an <%ae>%n%cn <%ce>', 'HEAD', capture=True).decode().splitlines() == [
    'mabaiqiu <mabaiqiu@gmail.com>', 'mabaiqiu <mabaiqiu@gmail.com>']
assert 'Signed-off-by: mabaiqiu <mabaiqiu@gmail.com>' in git('show', '-s', '--format=%B', 'HEAD', capture=True).decode()
assert git('status', '--porcelain', capture=True) == b''
candidate_unchanged()
result = {'commit': head, 'parent': baseline, 'commits-created': 1, 'DCO-identity': 'mabaiqiu <mabaiqiu@gmail.com>',
          'audited-staged-files': total, 'workspace-clean': True, 'push-or-publish': False,
          'staged-content-sha256-verified': True, 'unique-staged-blobs-verified': len(verified_blobs),
          'ownership-summary': ownership['manifest'], 'receipt-sha256': hashlib.sha256(receipt.read_bytes()).hexdigest()}
Path('/tmp/issue83-local-commit-result.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result, indent=2), flush=True)
