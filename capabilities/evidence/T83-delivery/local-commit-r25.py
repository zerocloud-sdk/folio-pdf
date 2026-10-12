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
suffix = 'final-r25'
summary_path = delivery / ('changed-file-ownership-' + suffix + '.json')
summary_bytes = summary_path.read_bytes()
ownership = json.loads(summary_bytes)
audit_path = delivery / 'final-audit-r23.json'
audit = json.loads(audit_path.read_text())
receipt = delivery / 'receipt-r25.md'
assert receipt.is_file() and audit['certification-tuples'] == 100 and audit['chain-records'] == 412
assert audit['recovery-tuples'] == 4 and audit['recovery-chain-records'] == 20
assert len(audit['selected-obligations']) == 14 and ownership['ownership'] == 'issue-83'
assert ownership['entry-unrelated-files'] == [] and ownership['baseline'] == baseline
manifest = root / ownership['manifest']['path']
manifest_snapshot = Path('/tmp/issue83-ownership-final-r25.tsv.gz')
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


guard_spec = importlib.util.spec_from_file_location('t83_commit_git_guard_r26', delivery / 'commit-git-guard-r26.py')
guard = importlib.util.module_from_spec(guard_spec)
guard_spec.loader.exec_module(guard)
authority = json.loads((delivery / 'local-commit-recovery-r25.json').read_text())
assert authority['commits-before-resume'] == 0 and authority['candidate-unchanged']
for reference in authority['reviewed-inputs']:
    assert driver.reference(root, root / reference['path']) == reference


def git(*args, capture=False):
    command = ['git', *args]
    if capture:
        return subprocess.check_output(command, cwd=root, env=guard.git_environment())
    print('Running ' + ' '.join(command[:3]), flush=True)
    result = guard.run_git(root, list(args), baseline)
    if result.stdout:
        sys.stdout.buffer.write(result.stdout)
    if result.stderr:
        sys.stderr.buffer.write(result.stderr)


assert git('rev-parse', 'HEAD', capture=True).decode().strip() == baseline
assert git('branch', '--show-current', capture=True).decode().strip() == 'main'


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


def expected_rows():
    return heapq.merge(rows(), sorted(self_rows, key=lambda row: row[4].encode('utf-8')),
                      key=lambda row: row[4].encode('utf-8'))


legacy_summary_path = root / authority['original-ownership-summary']['path']
legacy_bytes = legacy_summary_path.read_bytes()
assert driver.reference(root, legacy_summary_path) == authority['original-ownership-summary']
legacy_ownership = json.loads(legacy_bytes)
legacy_snapshot = Path('/tmp/issue83-ownership-final-r23.tsv.gz')
assert driver.sha256(legacy_snapshot) == legacy_ownership['manifest']['sha256']


def legacy_rows():
    with gzip.open(legacy_snapshot, 'rt', encoding='utf-8') as stream:
        assert next(stream) == 'ownership\tchange\tsha256\tbytes\tpath\n'
        for line in stream:
            fields = line.rstrip('\n').split('\t')
            assert len(fields) == 5 and fields[0] == 'issue-83'
            yield fields


def legacy_expected_rows():
    included_self = [
        ('issue-83', 'new', legacy_ownership['manifest']['sha256'], str(legacy_snapshot.stat().st_size),
         legacy_ownership['manifest']['path']),
        ('issue-83', 'new', hashlib.sha256(legacy_bytes).hexdigest(), str(len(legacy_bytes)),
         authority['original-ownership-summary']['path'])]
    return heapq.merge(legacy_rows(), sorted(included_self, key=lambda row: row[4].encode('utf-8')),
                      key=lambda row: row[4].encode('utf-8'))


# First admit only bytes authorized by the original seal. Known later delivery
# edits are restaged from the new seal; they cannot be mistaken for corruption.
partial = guard.verify_staged(root, legacy_expected_rows(), complete=False)
print('Verified admissible partial staging: ' + str(partial['files']) + ' owned files.', flush=True)
staged_names = set()
legacy_iterator = iter(legacy_expected_rows())
legacy_row = next(legacy_iterator, None)
for row in expected_rows():
    while legacy_row is not None and legacy_row[4].encode('utf-8') < row[4].encode('utf-8'):
        legacy_row = next(legacy_iterator, None)
    if row[4] in partial['names'] and legacy_row is not None and legacy_row[4] == row[4] \
            and legacy_row[2:4] == row[2:4]:
        staged_names.add(row[4])
completed_groups = {}
for name in staged_names:
    if name.startswith('capabilities/evidence/foundation/T83-'):
        prefix = '/'.join(name.split('/')[:6])
    elif name.startswith('capabilities/evidence/T83-delivery/') and len(name.split('/')) > 4:
        prefix = '/'.join(name.split('/')[:4])
    else:
        continue
    completed_groups[prefix] = completed_groups.get(prefix, 0) + 1
missing_exact = [name for name in exact if name not in staged_names]
if missing_exact:
    git('add', '--', *missing_exact)
pending = len(missing_exact)
skipped_groups = 0
for number, (prefix, entries) in enumerate(sorted(groups.items()), 1):
    if completed_groups.get(prefix, 0) == entries:
        skipped_groups += 1
        continue
    print('Audited staging group ' + str(number) + '/' + str(len(groups))
          + ': ' + str(entries) + ' files at ' + prefix, flush=True)
    git('add', '--', prefix)
    pending += entries
    if pending >= 30000 or os.statvfs(root).f_favail < 750000:
        pack()
        pending = 0
if pending:
    pack()
print('Resumed staging skipped ' + str(skipped_groups) + ' independently verified complete groups.', flush=True)
complete = guard.verify_staged(root, expected_rows(), complete=True)
total = complete['files']
assert total == count + 2
assert git('rev-parse', 'HEAD', capture=True).decode().strip() == baseline
candidate_unchanged()
print('Exact audited staging complete: ' + str(total) + ' files. Creating the sole authorized commit.', flush=True)
# Never retry commit after the final byte proof: another index writer during a
# lock wait could change staged content while HEAD still remains at baseline.
subprocess.run(['git', '-c', 'user.name=mabaiqiu', '-c', 'user.email=mabaiqiu@gmail.com',
    'commit', '--quiet', '-s', '-m', 'Certify Worker recovery and bounded Foundation scale (#83)',
    '-m', 'Use monotonic workflow budgets, preserve absolute wall deadlines, and bind recovery/scale certification to the verified Ubuntu/Temurin 8/11/17/21 candidate.'],
    cwd=root, check=True,
    env=guard.git_environment({'GIT_AUTHOR_NAME': 'mabaiqiu', 'GIT_AUTHOR_EMAIL': 'mabaiqiu@gmail.com',
                               'GIT_COMMITTER_NAME': 'mabaiqiu', 'GIT_COMMITTER_EMAIL': 'mabaiqiu@gmail.com'}))
git('config', '--local', '--unset', 'core.splitIndex')
assert subprocess.run(['git', 'config', '--local', '--get', 'core.splitIndex'], cwd=root,
    env=guard.git_environment(), capture_output=True).returncode == 1
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
          'staged-content-sha256-verified': True, 'unique-staged-blobs-verified': complete['unique-blobs'],
          'partial-staged-files-verified': partial['files'], 'verified-groups-skipped': skipped_groups,
          'temporary-core-splitIndex-restored': True,
          'ownership-summary': ownership['manifest'], 'receipt-sha256': hashlib.sha256(receipt.read_bytes()).hexdigest()}
Path('/tmp/issue83-local-commit-result.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result, indent=2), flush=True)
