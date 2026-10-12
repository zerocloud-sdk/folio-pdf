"""Run final public inventory gates after all fourteen fresh certifications pass."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys

import yaml

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('t83_finish_r22', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
frozen = json.loads((delivery / 'source-freeze-final-r10.json').read_text())
inputs = {key: frozen[key] for key in ('inputs', 'contract-inputs')}
build = root / 'target/foundation-0.1.0/build-inputs.json'
receipt = driver.require_staged_build(root, contract, build)
assert receipt == json.loads((delivery / 'candidate-final-r10/build-inputs.json').read_text())
assert driver.capture_build_inputs(root, contract) == inputs
steps = json.loads((delivery / 'refresh-final-r22/results.json').read_text())
assert len(steps) == 14 and all(row['exit-code'] == 0 for row in steps)
assert steps[-1]['obligation'] == 'recovery'
assert steps[-1]['certifications'] == 100 and steps[-1]['chain-records'] == 412
authority = root / 'capabilities/foundation-evidence.yaml'
index_hash = driver.sha256(authority)
assert steps[-1]['current-index-sha256'] == index_hash
index = yaml.safe_load(authority.read_text())
assert index['candidate'] == receipt['candidate']
assert len(index['certifications']) == 100 and sum(len(row['records']) for row in index['certifications']) == 412


def current():
    driver.require_staged_build(root, contract, build)
    assert driver.capture_build_inputs(root, contract) == inputs
    assert driver.sha256(authority) == index_hash


for name, expected, arguments in (
    ('inventory-generate-final-r22', 0, ['./scripts/inventory', 'generate']),
    ('inventory-validate-final-r22', 0, ['./scripts/inventory', 'validate']),
    ('inventory-check-final-r22', 0, ['./scripts/inventory', 'check']),
    ('inventory-readiness-final-r22', 1, ['./scripts/inventory', 'readiness']),
    ('post-final-r22-container-check', 0, ['podman', '--log-level=error', 'ps', '--format', '{{.ID}} {{.Names}} {{.Status}}']),
    ('diff-check-final-r22', 0, ['git', 'diff', '--check'])):
    current()
    command = [sys.executable, str(delivery / 'run-gate-monotonic.py'), name, 'host', *arguments]
    process = subprocess.run(command, cwd=root)
    gate_path = delivery / 'validation' / (name + '-result.json')
    gate = json.loads(gate_path.read_text())
    assert gate['exit-code'] == process.returncode == expected
    assert gate['log'] == driver.reference(root, root / gate['log']['path'])
    if name == 'post-final-r22-container-check':
        assert (root / gate['log']['path']).read_text() == '', 'Inspect live containers; do not delete unrelated containers'
    current()
print('Final inventory generation/validation/freshness/readiness, container and diff gates passed; evidence reviews, audit, ownership and commit follow.', flush=True)
