"""Record every #83 changed file and reject unrelated or ignored-file capture."""
import gzip
import hashlib
import json
from pathlib import Path
import subprocess
import re
import sys

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
baseline = '42fe5c797711070b319b9419e14fcb7adfa98735'
suffix = sys.argv[1]
assert re.fullmatch(r'(?:prefinal|final)-r22', suffix)
assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip() == baseline
prefix = 'changed-file-ownership-' + suffix
assert not (delivery / (prefix + '.json')).exists()
changed = subprocess.check_output(['git', 'diff', '--name-only', '-z', baseline]).decode().split('\0')
untracked = subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard', '-z']).decode().split('\0')
# Exact tracked paths for the user-approved issue-83 dirty relaunch and
# its authorized monotonic correction. Final index/views stay within this set.
approved_tracked = {
    '.gitignore', 'PROVENANCE.md', 'README.md',
    'build-tools/inventory/src/main/java/net/zerocloud/pdf/tools/inventory/FoundationEvidence.java',
    'build-tools/inventory/src/test/java/net/zerocloud/pdf/tools/inventory/FoundationReadinessCommandTest.java',
    'capabilities/capability-matrix.yaml', 'capabilities/evidence/T22-worker-recovery-scale.md',
    'capabilities/facade-surface.yaml', 'capabilities/foundation-evidence.yaml',
    'capabilities/foundation-release.yaml', 'capabilities/foundation-requirements.yaml',
    'docs/foundation-readiness.md', 'docs/generated/capability-matrix.md',
    'docs/generated/facade-surface.md', 'docs/generated/foundation-readiness.md',
    'docs/hardened-worker.md', 'docs/hostile-input-policy.md', 'docs/t21-certification.md', 'docs/t80-certification.md',
    'docs/zh-CN/getting-started.md',
    'pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/ExternalProcess.java',
    'pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/T80EvidenceCommand.java',
    'pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/VisualEvidenceChain.java',
    'pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/VisualProfile.java',
    'pdf-acceptance/src/test/java/net/zerocloud/pdf/acceptance/T21ContractTestCommand.java',
    'pdf-acceptance/src/test/java/net/zerocloud/pdf/acceptance/T80PasswordProductsTest.java',
    'pdf-document/src/main/java/net/zerocloud/pdf/HardenedWorkerEngine.java',
    'pdf-document/src/main/java/net/zerocloud/pdf/DocumentWorkflow.java',
    'pdf-document/src/main/java/net/zerocloud/pdf/WorkerMessages.java',
    'pdf-document/src/main/java/net/zerocloud/pdf/WorkflowEnvironment.java',
    'pdf-document/src/main/java/net/zerocloud/pdf/WorkflowResourceContext.java',
    'pdf-document/src/main/java/net/zerocloud/pdf/WorkflowResourcePolicy.java',
    'pdf-document/src/main/java/net/zerocloud/pdf/WorkflowResourceUsage.java',

    'pdf-document/src/test/java/net/zerocloud/pdf/consumer/HardenedWorkerScaleProfileTest.java',
    'scripts/t03-foundation.py', 'scripts/t20-evidence-pin.properties', 'scripts/t20_foundation_reports.py',
    'scripts/t21-evidence-pin.properties', 'scripts/t21_foundation_reports.py',
    'scripts/t22-scale', 'scripts/tests/test_t10_foundation.py'}
assert set(changed) - {''} <= approved_tracked, 'Unrelated tracked work detected; leave it untouched'
names = sorted(set(changed + untracked) - {''})
assert not any(name.startswith(('.build-cache/', 'target/')) or '/target/' in name for name in names)
assert not any(name.startswith(('capabilities/evidence/foundation/T82-', 'capabilities/evidence/T82-delivery/')) for name in changed)
new_source = (
    'docs/t22-certification.md', 'scripts/t22-evidence-pin.properties', 'scripts/t22_foundation_reports.py',
    'scripts/t22_semantics.py', 'scripts/t22-arlington-pin.properties', 'scripts/tests/test_t22_foundation.py',
    'pdf-document/src/test/java/net/zerocloud/pdf/consumer/ScaleProfileEvidence.java',
    'pdf-document/src/test/java/net/zerocloud/pdf/HardenedWorkerTerminationWaitTest.java',
    'pdf-document/src/test/java/net/zerocloud/pdf/WorkerInitializationTimingTest.java',
    'pdf-document/src/test/java/net/zerocloud/pdf/consumer/WorkflowElapsedTimeContractTest.java',
    'pdf-acceptance/src/test/java/net/zerocloud/pdf/acceptance/RequiredCaseRunner.java',
    'capabilities/evidence/T22-worker-recovery-scale-historical.md')
allowed_new = ('capabilities/evidence/T83-', 'capabilities/evidence/foundation/T83-',
               'capabilities/profiles/T22-worker-recovery-scale',
               'pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/T22',
               'pdf-acceptance/src/test/java/net/zerocloud/pdf/acceptance/T22',
               'pdf-acceptance/src/test/java/net/zerocloud/pdf/T22')
assert all(name in new_source or name.startswith(allowed_new) for name in untracked if name), 'Unrelated untracked work detected'
path = delivery / (prefix + '.tsv.gz')
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
          'invocation': ['python3', '-B', 'capabilities/evidence/T83-delivery/audit-relaunch-ownership-r22.py', suffix],
          'invocation-disposition': 'Run directly after in-tree gate logs settle; no logging wrapper may capture this audit in a file it enumerates.',
          'entry-state': 'User-approved relaunch on this agent own prior issue-83 dirty tree; all pre-existing work retained',
          'approved-tracked-paths': sorted(approved_tracked),
          'changed-files': len(names), 'bytes': total_bytes, 'groups': groups,
          'source-inventories-documentation': source_files,
          'manifest': {'path': path.relative_to(root).as_posix(), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()},
          'manifest-format': 'gzip of UTF-8 TSV; every row binds ticket ownership, change type, observed SHA-256, length and exact path',
          'self-exclusion': 'This generated manifest and summary are excluded from their own rows to avoid circular hashes.',
          'existing-ignored-caches': 'Preserved; ignored cache/build outputs are not captured as unrelated or publishable source.'}
(delivery / (prefix + '.json')).write_text(json.dumps(record, indent=2) + '\n')
print('Owned file audit: ' + str(len(names)) + ' ticket files; no unrelated untracked work or historical #82 mutation.')
