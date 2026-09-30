"""Resume the interrupted final gates without replacing any historical evidence."""
import datetime
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

ROOT = Path('/workspace/folio-pdf')
OUT = ROOT / 'capabilities/evidence/T79-delivery'
VALIDATION = OUT / 'validation'
BASELINE = '7420a656d27d17b82c7632be7c6214f8a657a22f'
ORDER = ['transactions', 'values', 'pages', 'metadata', 'annotations', 'text',
         'images', 'incremental', 'password-baseline', 'password-clear-metadata']
JAVA_HOME = json.loads((VALIDATION / 'resume-java.json').read_text())['java-home']
os.environ.update({
    'JAVA_HOME': JAVA_HOME,
    'FOLIO_HARFBUZZ_HELPER': str(ROOT / '.build-cache/t79-provision/folio-harfbuzz/bin/folio-harfbuzz'),
    'FOLIO_FOUNDATION_PYTHON_ROOT': str(ROOT / '.build-cache/foundation-python'),
    'PYTHONPATH': str(ROOT / '.build-cache/foundation-host-python'),
    'MAVEN_USER_HOME': str(ROOT / '.build-cache/maven'),
    'MAVEN_OPTS': '-Dmaven.repo.local=' + str(ROOT / '.build-cache/maven/repository'),
    'T79_COMMAND_TIMEOUT': '43200',
    'PATH': str(VALIDATION / 'container-bin') + ':' + str(ROOT / 'scripts/container-bin')
            + ':' + JAVA_HOME + '/bin:' + os.environ['PATH'],
})
sys.path.insert(0, str(ROOT / '.build-cache/foundation-host-python'))
import yaml


def digest(path):
    with path.open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def reference(path):
    return {'path': path.relative_to(ROOT).as_posix(), 'sha256': digest(path)}


def gate(name, wait=False):
    deadline = time.monotonic() + 43200
    while True:
        ledger = [json.loads(line) for line in (VALIDATION / 'commands.jsonl').read_text().splitlines()]
        rows = [row for row in ledger if row['name'] == name]
        if rows:
            assert len(rows) == 1 and rows[0]['exit-code'] == 0, 'Required gate failed: ' + name
            assert digest(ROOT / rows[0]['log']) == rows[0]['log-sha256'], name
            return
        assert wait and time.monotonic() < deadline, 'Required gate is missing: ' + name
        time.sleep(10)


def run(name, command, accepted=(0,)):
    print('Starting ' + name, flush=True)
    result = subprocess.call([sys.executable, str(VALIDATION / 'run-logged.py'), name, *command], cwd=ROOT)
    assert result in accepted, name + ' failed: ' + str(result)


print('Awaiting the resumed full verification.', flush=True)
gate('full-verify-resumed-r3', wait=True)
for name in ['independent-profile-final', 'collector-all-final', 'artifact-regression', 'live-qualification']:
    gate(name)
assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip() == BASELINE
assert (VALIDATION / 'resume-environments.json').is_file()
run('native-baseline-resumed', ['./mvnw', '-B', '-ntp', '-pl', 'pdf-document', '-am',
    '-Dtest=PdfVersionPasswordSecurityWorkflowTest', '-Dsurefire.failIfNoSpecifiedTests=false', 'test'])
run('worker-facade-resumed', ['./mvnw', '-B', '-ntp', '-pl', 'pdf-migration-itext7', '-am',
    '-Dtest=ClearMetadataPasswordWorkflowTest,ClearMetadataPasswordFacadeTest',
    '-Dfolio.t79.executionProfile=HARDENED_WORKER', '-Dsurefire.failIfNoSpecifiedTests=false', 'test'])
run('jdk-matrix', ['./scripts/verify-jdk-matrix.sh'])

spec = importlib.util.spec_from_file_location('foundation', ROOT / 'scripts/t03-foundation.py')
foundation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(foundation)
contract = yaml.safe_load((ROOT / 'capabilities/foundation-release.yaml').read_text())
frozen = foundation.capture_build_inputs(ROOT, contract)
reviews = ['standards-final.md', 'spec-final.md', 'standards-resume.md', 'spec-resume.md']
frozen.update({'comparison-baseline': BASELINE, 'execution-starting-head': BASELINE,
    'recorded-at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'sole-contract-sha256': digest(Path('/workspace/contracts/issue-79-contract.md')),
    'source-reviews': [reference(OUT / 'reviews' / name) for name in reviews],
    'resumed-worktree': reference(VALIDATION / 'resume-start.json')})
with (OUT / 'source-freeze.json').open('x') as stream:
    stream.write(json.dumps(frozen, indent=2) + '\n')
run('certification-stage', ['python3', 'scripts/t03-foundation.py', 'stage'])
shutil.copyfile(ROOT / 'target/foundation-0.1.0/build-inputs.json', VALIDATION / 'staged-candidate.json')
destination = ROOT / 'capabilities/evidence/foundation/T79-final'
destination.mkdir()
for name in ORDER:
    run('certification-' + name, ['python3', 'scripts/t03-foundation.py', 'certify',
        (destination / name).relative_to(ROOT).as_posix(), '--obligation', name])
for action in ['generate', 'validate', 'check']:
    run('inventory-final-' + action, ['./scripts/inventory', action])
run('inventory-final-readiness', ['./scripts/inventory', 'readiness'], accepted=(0, 1))
assert foundation.capture_build_inputs(ROOT, contract) == {key: frozen[key] for key in ['inputs', 'contract-inputs']}
print('All remaining gates passed; final audit and receipt remain.', flush=True)
