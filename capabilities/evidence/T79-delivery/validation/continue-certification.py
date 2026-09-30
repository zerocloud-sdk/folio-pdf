"""Continue on the unchanged candidate after the retained text timeout attempt."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path('/workspace/folio-pdf')
OUT = ROOT / 'capabilities/evidence/T79-delivery'
VALIDATION = OUT / 'validation'
ORDER = ['transactions', 'values', 'pages', 'metadata', 'annotations', 'text',
         'images', 'incremental', 'password-baseline', 'password-clear-metadata']
java = json.loads((VALIDATION / 'resume-java.json').read_text())['java-home']
os.environ.update({
    'JAVA_HOME': java,
    'FOLIO_HARFBUZZ_HELPER': str(ROOT / '.build-cache/t79-provision/folio-harfbuzz/bin/folio-harfbuzz'),
    'FOLIO_FOUNDATION_PYTHON_ROOT': str(ROOT / '.build-cache/foundation-python'),
    'PYTHONPATH': str(ROOT / '.build-cache/foundation-host-python'),
    'MAVEN_USER_HOME': str(ROOT / '.build-cache/maven'),
    'MAVEN_OPTS': '-Dmaven.repo.local=' + str(ROOT / '.build-cache/maven/repository'),
    'T79_COMMAND_TIMEOUT': '43200',
    'PATH': str(VALIDATION / 'container-bin') + ':' + str(ROOT / 'scripts/container-bin')
        + ':' + java + '/bin:' + os.environ['PATH'],
})
sys.path.insert(0, str(ROOT / '.build-cache/foundation-host-python'))
import yaml


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def gate(name):
    rows = [json.loads(line) for line in (VALIDATION / 'commands.jsonl').read_text().splitlines()]
    rows = [row for row in rows if row['name'] == name]
    assert len(rows) == 1 and rows[0]['exit-code'] == 0, name
    assert digest(ROOT / rows[0]['log']) == rows[0]['log-sha256']


def run(name, command, accepted=(0,)):
    print('Starting ' + name, flush=True)
    result = subprocess.call([sys.executable, str(VALIDATION / 'run-logged.py'), name, *command], cwd=ROOT)
    assert result in accepted, name + ' failed: ' + str(result)


diagnosis = json.loads((VALIDATION / 'diagnosis-text-timeouts/disposition.json').read_text())
assert diagnosis['result'] == 'unchanged-replays-pass'
for item in diagnosis['passing-replays']:
    path = ROOT / item['path']
    assert digest(path) == item['sha256'] and json.loads(path.read_text())['exit-code'] == 0
assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip() == '7420a656d27d17b82c7632be7c6214f8a657a22f'
spec = importlib.util.spec_from_file_location('foundation', ROOT / 'scripts/t03-foundation.py')
foundation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(foundation)
contract = yaml.safe_load((ROOT / 'capabilities/foundation-release.yaml').read_text())
frozen = json.loads((OUT / 'source-freeze.json').read_text())
assert foundation.capture_build_inputs(ROOT, contract) == {k: frozen[k] for k in ['inputs', 'contract-inputs']}
assert foundation.require_staged_build(ROOT, contract, ROOT / 'target/foundation-0.1.0/build-inputs.json') == json.loads((VALIDATION / 'staged-candidate.json').read_text())
for name in ['full-verify-resumed-r3', 'jdk-matrix', 'certification-stage'] + ['certification-' + x for x in ORDER[:5]]:
    gate(name)
selection = {name: {'directory': 'capabilities/evidence/foundation/T79-final/' + (name + '-r2' if name == 'text' else name),
                    'command': 'certification-' + (name + '-r2' if name == 'text' else name)} for name in ORDER}
with (VALIDATION / 'certification-selection.json').open('x') as stream:
    stream.write(json.dumps(selection, indent=2) + '\n')
for name in ORDER[5:]:
    item = selection[name]
    run(item['command'], ['python3', 'scripts/t03-foundation.py', 'certify', item['directory'], '--obligation', name])
for action in ['generate', 'validate', 'check']:
    run('inventory-final-' + action, ['./scripts/inventory', action])
run('inventory-final-readiness', ['./scripts/inventory', 'readiness'], accepted=(0, 1))
assert foundation.capture_build_inputs(ROOT, contract) == {k: frozen[k] for k in ['inputs', 'contract-inputs']}
print('All remaining gates passed; final audit and receipt remain.', flush=True)
