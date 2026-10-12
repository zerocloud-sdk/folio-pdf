"""Freeze the approved Worker grace correction before staging a fresh candidate."""
import datetime
import importlib.util
import json
from pathlib import Path
import subprocess
import sys

import yaml

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
baseline = '42fe5c797711070b319b9419e14fcb7adfa98735'
assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip() == baseline
assert not subprocess.check_output(['git', 'diff', '--cached', '--name-only'], cwd=root, text=True).strip()
sys.path.insert(0, str(root / 'scripts'))
spec = importlib.util.spec_from_file_location('t83_relaunch_freeze', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
from t22_foundation_reports import require_exit_grace_observation

contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
inputs = driver.capture_build_inputs(root, contract)
previous = json.loads((delivery / 'source-freeze-final-r6.json').read_text())
changed = {}
for key in ('inputs', 'contract-inputs'):
    before = {item['path']: item['sha256'] for item in previous[key]}
    after = {item['path']: item['sha256'] for item in inputs[key]}
    assert before.keys() == after.keys(), 'Unexpected source addition/deletion during relaunch'
    changed[key] = sorted(path for path in before if before[path] != after[path])
assert set(changed['inputs']) == {
    'PROVENANCE.md', 'docs/foundation-readiness.md', 'docs/hardened-worker.md',
    'docs/t22-certification.md', 'docs/zh-CN/getting-started.md',
    'capabilities/profiles/T22-worker-recovery-scale/coverage.json',
    'capabilities/profiles/T22-worker-recovery-scale/mandatory-tests.txt',
    'pdf-document/src/main/java/net/zerocloud/pdf/HardenedWorkerEngine.java',
    'pdf-document/src/test/java/net/zerocloud/pdf/HardenedWorkerTerminationWaitTest.java',
    'scripts/t03-foundation.py', 'scripts/t22_foundation_reports.py', 'scripts/tests/test_t22_foundation.py'}
assert changed['contract-inputs'] == ['capabilities/evidence/T22-worker-recovery-scale.md']
assert len(inputs['inputs']) == 2888 and len(inputs['contract-inputs']) == 32
gates = []
for name, code, marker in (
        ('worker-30s-delayed-exit-red-r2', 1, 'The Worker did not terminate after completing its response.'),
        ('worker-post-finished-nonzero-red-r2', 1, 'java.lang.AssertionError: The Worker terminated without a valid response.'),
        ('worker-all-exit-recovery-protocol-focused-green-r1', 0, 'Tests run: 158, Failures: 0, Errors: 0, Skipped: 0'),
        ('worker-30s-evidence-guard-red-r1', 1, 'AssertionError: ValueError not raised'),
        ('python-recovery-worker-guards-r19', 0, 'Ran 35 tests'),
        ('checker-recovery-relaunch-r1', 0, 'Tests run: 12, Failures: 0, Errors: 0, Skipped: 0'),
        ('inventory-prefreeze-generate-r8', 0, None),
        ('post-r12-controlled-stop-container-check-r1', 0, None)):
    path = delivery / 'validation' / (name + '-result.json')
    gate = json.loads(path.read_text())
    assert gate['exit-code'] == code
    assert gate['log'] == driver.reference(root, root / gate['log']['path'])
    assert marker is None or marker in (root / gate['log']['path']).read_text()
    gates.append(driver.reference(root, path))
diagnostic = require_exit_grace_observation((delivery / 'validation/worker-all-exit-recovery-protocol-focused-green-r1.txt').read_text())
preservation = json.loads((delivery / 'relaunch-preservation.json').read_text())
assert all(item == driver.reference(root, root / item['path']) for item in preservation['retained'])
reviews = [driver.reference(root, delivery / 'reviews' / (axis + '-relaunch-r2.md'))
           for axis in ('standards', 'spec')]
superseded = [driver.reference(root, path) for path in (
    delivery / 'source-freeze-final-r6.json',
    delivery / 'candidate-final-r6/build-inputs.json',
    delivery / 'candidate-final-r6/source-inputs.zip-parts.json',
    delivery / 'candidate-final-r6/artifacts-contracts-harness.zip-parts.json',
    delivery / 'refresh-final-r12/preflight.json',
    delivery / 'validation/full-verify-final-r7.txt',
    delivery / 'relaunch-r12-controlled-stop.json')]
assert not (delivery / 'validation/full-verify-final-r7-result.json').exists()
assert not (root / 'capabilities/evidence/foundation/T83-final-r12').exists()
assert (delivery / 'validation/post-r12-controlled-stop-container-check-r1.txt').read_text() == ''
destination = delivery / 'source-freeze-final-r7.json'
assert not destination.exists()
driver.write_json(destination, {**inputs, 'recorded-at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'baseline': baseline, 'source-changes-since-final-r6': changed, 'development-gates': gates,
    'controlled-real-exit-diagnostic': diagnostic, 'reviews': reviews,
    'superseded-candidate-preservation': driver.reference(root, delivery / 'relaunch-preservation.json'),
    'superseded-candidate6': superseded,
    'freeze-driver': driver.reference(root, Path(__file__)),
    'environment-selector': driver.reference(root, delivery / 'relaunch-environment.sh')})
print('Frozen candidate7 inputs: 2888 sources, 32 contracts, 173 mandatory recovery cases; reviews resolved.')
