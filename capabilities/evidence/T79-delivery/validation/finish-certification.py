"""Continue staging after successful validation; load the explicit host YAML dependency."""
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
ORDER = ['transactions', 'values', 'pages', 'metadata', 'annotations', 'text', 'images',
         'incremental', 'password-baseline', 'password-clear-metadata']


def record(path):
    return {'path': path.relative_to(ROOT).as_posix(), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}


def wait_gate(name):
    print('Awaiting ' + name, flush=True)
    while True:
        values = [json.loads(line) for line in (VALIDATION / 'commands.jsonl').read_text().splitlines()]
        found = [value for value in values if value['name'] == name]
        if found:
            assert len(found) == 1 and found[0]['exit-code'] == 0, 'Required gate failed: ' + name
            return
        time.sleep(10)


def run(name, command, accepted=(0,)):
    print('Starting ' + name, flush=True)
    code = subprocess.call([sys.executable, str(VALIDATION / 'run-logged.py'), name, *command], cwd=ROOT)
    assert code in accepted, name + ' failed: ' + str(code)


os.environ.update({
    'FOLIO_HARFBUZZ_HELPER': str(ROOT / '.build-cache/t79-provision/folio-harfbuzz/bin/folio-harfbuzz'),
    'FOLIO_FOUNDATION_PYTHON_ROOT': str(ROOT / '.build-cache/foundation-python'),
    'PYTHONPATH': str(ROOT / '.build-cache/foundation-host-python'),
    'MAVEN_OPTS': '-Dmaven.repo.local=' + str(ROOT / '.build-cache/maven/repository'),
    'T79_COMMAND_TIMEOUT': '43200',
    'PATH': str(ROOT / 'scripts/container-bin') + ':' + os.environ['PATH'],
})
wait_gate('full-verify-final')
wait_gate('jdk-matrix')
sys.path.insert(0, str(ROOT / '.build-cache/foundation-host-python'))
import yaml
spec = importlib.util.spec_from_file_location('foundation', ROOT / 'scripts/t03-foundation.py')
foundation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(foundation)
contract = yaml.safe_load((ROOT / 'capabilities/foundation-release.yaml').read_text())
frozen = foundation.capture_build_inputs(ROOT, contract)
frozen.update({'comparison-baseline': BASELINE, 'execution-starting-head': BASELINE,
    'recorded-at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'sole-contract-sha256': hashlib.sha256(Path('/workspace/contracts/issue-79-contract.md').read_bytes()).hexdigest(),
    'source-reviews': [record(OUT / 'reviews' / name) for name in ['standards-final.md', 'spec-final.md']]})
(OUT / 'source-freeze.json').write_text(json.dumps(frozen, indent=2) + '\n')
run('certification-stage', ['python3', 'scripts/t03-foundation.py', 'stage'])
shutil.copyfile(ROOT / 'target/foundation-0.1.0/build-inputs.json', VALIDATION / 'staged-candidate.json')
destination = ROOT / 'capabilities/evidence/foundation/T79-final'
destination.mkdir(exist_ok=True)
for name in ORDER:
    run('certification-' + name, ['python3', 'scripts/t03-foundation.py', 'certify',
        (destination / name).relative_to(ROOT).as_posix(), '--obligation', name])
for action in ['generate', 'validate', 'check']:
    run('inventory-final-' + action, ['./scripts/inventory', action])
run('inventory-final-readiness', ['./scripts/inventory', 'readiness'], accepted=(0, 1))
assert foundation.capture_build_inputs(ROOT, contract) == {key: frozen[key] for key in ['inputs', 'contract-inputs']}
print('All validation and certification commands completed; receipt audit remains.', flush=True)
