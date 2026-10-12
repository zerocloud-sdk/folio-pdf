"""Record every #83 changed file and reject unrelated or ignored-file capture."""
import gzip
import hashlib
import json
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
baseline = '42fe5c797711070b319b9419e14fcb7adfa98735'
changed = subprocess.check_output(['git', 'diff', '--name-only', '-z', baseline]).decode().split('\0')
untracked = subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard', '-z']).decode().split('\0')
names = sorted(set(changed + untracked) - {''})
assert not any(name.startswith(('.build-cache/', 'target/')) or '/target/' in name for name in names)
assert not any(name.startswith(('capabilities/evidence/foundation/T82-', 'capabilities/evidence/T82-delivery/')) for name in changed)
new_source = (
    'docs/t22-certification.md', 'scripts/t22-evidence-pin.properties', 'scripts/t22_foundation_reports.py',
    'scripts/t22_semantics.py', 'scripts/t22-arlington-pin.properties', 'scripts/tests/test_t22_foundation.py',
    'pdf-document/src/test/java/net/zerocloud/pdf/consumer/ScaleProfileEvidence.java',
    'pdf-document/src/test/java/net/zerocloud/pdf/HardenedWorkerTerminationWaitTest.java',
    'pdf-acceptance/src/test/java/net/zerocloud/pdf/acceptance/RequiredCaseRunner.java',
    'capabilities/evidence/T22-worker-recovery-scale-historical.md')
allowed_new = ('capabilities/evidence/T83-', 'capabilities/evidence/foundation/T83-',
               'capabilities/profiles/T22-worker-recovery-scale',
               'pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/T22',
               'pdf-acceptance/src/test/java/net/zerocloud/pdf/acceptance/T22',
               'pdf-acceptance/src/test/java/net/zerocloud/pdf/T22')
assert all(name in new_source or name.startswith(allowed_new) for name in untracked if name), 'Unrelated untracked work detected'
path = delivery / 'changed-file-ownership.tsv.gz'
assert not path.exists()
groups = {}
source_files = []
total_bytes = 0
with path.open('wb') as output:
    with gzip.GzipFile(filename='', mode='wb', fileobj=output, mtime=0) as compressed:
        compressed.write(b'ownership\tchange\tsha256\tbytes\tpath\n')
        for name in names:
            file = root / name
            assert file.is_file() and not file.is_symlink(), name
            with file.open('rb') as stream:
                digest = hashlib.file_digest(stream, 'sha256').hexdigest()
            size = file.stat().st_size
            change = 'tracked-modified' if name in changed else 'new'
            compressed.write(('issue-83\t' + change + '\t' + digest + '\t' + str(size) + '\t' + name + '\n').encode())
            total_bytes += size
            if name.startswith('capabilities/evidence/foundation/T83-'):
                group = '/'.join(name.split('/')[:4])
            elif name.startswith('capabilities/evidence/T83-delivery/'):
                group = '/'.join(name.split('/')[:4])
            else:
                group = 'source-inventories-documentation'
                source_files.append({'path': name, 'sha256': digest, 'bytes': size, 'change': change})
            summary = groups.setdefault(group, {'files': 0, 'bytes': 0})
            summary['files'] += 1
            summary['bytes'] += size
record = {'baseline': baseline, 'entry-unrelated-files': [], 'ownership': 'issue-83',
          'changed-files': len(names), 'bytes': total_bytes, 'groups': groups,
          'source-inventories-documentation': source_files,
          'manifest': {'path': path.relative_to(root).as_posix(), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()},
          'manifest-format': 'gzip of UTF-8 TSV; every row binds ticket ownership, change type, observed SHA-256, length and exact path',
          'self-exclusion': 'This generated manifest and summary are excluded from their own rows to avoid circular hashes.',
          'existing-ignored-caches': 'Preserved; ignored cache/build outputs are not captured as unrelated or publishable source.'}
(delivery / 'changed-file-ownership.json').write_text(json.dumps(record, indent=2) + '\n')
print('Owned file audit: ' + str(len(names)) + ' ticket files; no unrelated untracked work or historical #82 mutation.')
