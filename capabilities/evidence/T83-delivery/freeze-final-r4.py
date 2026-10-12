"""Freeze reviewed T80 coordinator inputs before the new candidate build."""
import datetime
import importlib.util
import json
from pathlib import Path
import subprocess

import yaml

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
baseline = '42fe5c797711070b319b9419e14fcb7adfa98735'
assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip() == baseline
assert not subprocess.check_output(['git', 'diff', '--cached', '--name-only'], cwd=root, text=True).strip()
spec = importlib.util.spec_from_file_location('t83_freeze_four', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
inputs = driver.capture_build_inputs(root, contract)
old = json.loads((delivery / 'source-freeze-final-r3.json').read_text())
assert inputs['contract-inputs'] == old['contract-inputs']
before = {item['path']: item['sha256'] for item in old['inputs']}
after = {item['path']: item['sha256'] for item in inputs['inputs']}
assert before.keys() == after.keys()
changed = {path for path in after if after[path] != before[path]}
assert changed == {
    'PROVENANCE.md', 'docs/t22-certification.md', 'docs/t80-certification.md',
    'pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/ExternalProcess.java',
    'pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/T80EvidenceCommand.java',
    'pdf-acceptance/src/test/java/net/zerocloud/pdf/acceptance/T80PasswordProductsTest.java'}
assert len(after) == 2887 and len(inputs['contract-inputs']) == 32
for name in ('coordinator-budget-focused-r1', 'python-recovery-worker-guards-r13',
             'inventory-prefreeze-generate-r5', 'validate-attachments-duration-r1'):
    gate = json.loads((delivery / 'validation' / (name + '-result.json')).read_text())
    assert gate['exit-code'] == 0
    assert driver.sha256(root / gate['log']['path']) == gate['log']['sha256']
destination = delivery / 'source-freeze-final-r4.json'
assert not destination.exists()
driver.write_json(destination, {**inputs,
    'recorded-at': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'baseline': baseline})
print('Frozen final-r4: 2887 source inputs, 32 unchanged contract inputs, six reviewed changes.')
