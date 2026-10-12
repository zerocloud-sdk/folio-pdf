"""Run final inventory/count/audit gates only after actual R18 certification completes."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys

import yaml

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('t83_finish_r18', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
frozen = json.loads((delivery / 'source-freeze-final-r9.json').read_text())
inputs = {key: frozen[key] for key in ('inputs', 'contract-inputs')}
build = root / 'target/foundation-0.1.0/build-inputs.json'
receipt = driver.require_staged_build(root, contract, build)
assert receipt == json.loads((delivery / 'candidate-final-r9/build-inputs.json').read_text())
assert driver.capture_build_inputs(root, contract) == inputs
steps = json.loads((delivery / 'refresh-final-r18/results.json').read_text())
assert len(steps) == 14 and all(row['exit-code'] == 0 for row in steps)
assert steps[-1]['obligation'] == 'recovery'
assert steps[-1]['certifications'] == 100 and steps[-1]['chain-records'] == 412
authority = root / 'capabilities/foundation-evidence.yaml'
index_hash = driver.sha256(authority)
assert steps[-1]['current-index-sha256'] == index_hash
index = yaml.safe_load(authority.read_text())
assert index['candidate'] == receipt['candidate']
assert len(index['certifications']) == 100 and sum(len(row['records']) for row in index['certifications']) == 412
# Final baseline-relative reviews follow actual certification and precede this
# audit so that the audit can bind their conclusions. Ownership/receipt follow.
for axis in ('standards', 'spec'):
    assert (delivery / 'reviews' / (axis + '-relaunch-r11.md')).is_file()


def current():
    driver.require_staged_build(root, contract, build)
    assert driver.capture_build_inputs(root, contract) == inputs
    assert driver.sha256(authority) == index_hash


for name, expected, arguments in (
    ('retain-full-verify-counts-r18', 0, ['python3', '-B', str(delivery / 'retain-relaunch-verification-counts-r18.py'), 'full-verify-final-r12']),
    ('retain-jdk-matrix-counts-r18', 0, ['python3', '-B', str(delivery / 'retain-relaunch-verification-counts-r18.py'), 'jdk-matrix-final-r5']),
    ('inventory-generate-final-r18', 0, ['./scripts/inventory', 'generate']),
    ('inventory-validate-final-r18', 0, ['./scripts/inventory', 'validate']),
    ('inventory-check-final-r18', 0, ['./scripts/inventory', 'check']),
    ('inventory-readiness-final-r18', 1, ['./scripts/inventory', 'readiness']),
    ('post-final-r18-container-check', 0, ['podman', '--log-level=error', 'ps', '--format', '{{.ID}} {{.Names}} {{.Status}}']),
    ('diff-check-final-r18', 0, ['git', 'diff', '--check']),
    ('final-audit-r18', 0, ['python3', '-B', str(delivery / 'audit-relaunch-r18.py')])):
    current()
    command = [sys.executable, str(delivery / 'run-gate.py'), name, 'host', *arguments]
    process = subprocess.run(command, cwd=root)
    gate_path = delivery / 'validation' / (name + '-result.json')
    gate = json.loads(gate_path.read_text())
    assert gate['exit-code'] == process.returncode == expected
    assert gate['log'] == driver.reference(root, root / gate['log']['path'])
    if name == 'post-final-r18-container-check':
        assert (root / gate['log']['path']).read_text() == '', 'Inspect live containers; do not delete unrelated containers'
    current()
print('Final count, inventory, live readiness, cleanup, diff and evidence audit gates completed; closing ownership/receipt/commit still required.', flush=True)
