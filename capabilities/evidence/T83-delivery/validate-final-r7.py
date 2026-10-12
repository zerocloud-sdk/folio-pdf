"""Validate generated inventories and live readiness after the complete fresh run."""
import datetime
import importlib.util
import json
from pathlib import Path
import subprocess
import sys

import yaml

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
selected = ('transactions', 'values', 'pages', 'metadata', 'annotations', 'text', 'images',
            'incremental', 'password-baseline', 'password-clear-metadata', 'password-attachments',
            'limits', 'worker', 'recovery')
refresh = json.loads((delivery / 'refresh-final-r7/results.json').read_text())
assert [step['obligation'] for step in refresh] == list(selected)
assert all(step['exit-code'] == 0 for step in refresh)
spec = importlib.util.spec_from_file_location('t83_post_refresh', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
build = root / 'target/foundation-0.1.0/build-inputs.json'
receipt = driver.require_staged_build(root, contract, build)
frozen = json.loads((delivery / 'source-freeze-final-r4.json').read_text())
inputs = {key: frozen[key] for key in ('inputs', 'contract-inputs')}
index = yaml.safe_load((root / 'capabilities/foundation-evidence.yaml').read_text())
assert index['candidate'] == receipt['candidate'] and len(index['certifications']) == 100
assert sum(len(item['records']) for item in index['certifications']) == 412
assert refresh[-1]['current-index-sha256'] == driver.sha256(root / 'capabilities/foundation-evidence.yaml')
assert driver.capture_build_inputs(root, contract) == inputs
destination = delivery / 'post-refresh-validation-r1.json'
assert not destination.exists()
results = []
for action in ('generate', 'validate', 'check', 'readiness'):
    command = [sys.executable, str(delivery / 'run-gate.py'),
               'inventory-' + action + '-final-r1', 'host', './scripts/inventory', action]
    started = datetime.datetime.now(datetime.timezone.utc).isoformat()
    process = subprocess.run(command, cwd=root)
    expected = 1 if action == 'readiness' else 0
    results.append({'action': action, 'command': command, 'started': started,
                    'finished': datetime.datetime.now(datetime.timezone.utc).isoformat(),
                    'exit-code': process.returncode, 'expected-exit-code': expected})
    driver.write_json(destination, results)
    assert process.returncode == expected, 'Unexpected inventory result'
    driver.require_staged_build(root, contract, build)
    assert driver.capture_build_inputs(root, contract) == inputs, 'Generated source view changed frozen inputs'
print('Inventory generate/validate/check passed; live NOT READY awaits unrelated-obligation audit.', flush=True)
