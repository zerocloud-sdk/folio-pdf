"""Freeze the Java 8 inspection correction; preserve every superseded candidate."""
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
previous = json.loads((delivery / 'source-freeze-final-r7.json').read_text())
changed = {}
for key in ('inputs', 'contract-inputs'):
    before = {item['path']: item['sha256'] for item in previous[key]}
    after = {item['path']: item['sha256'] for item in inputs[key]}
    assert before.keys() == after.keys(), 'Unexpected source addition/deletion during relaunch'
    changed[key] = sorted(path for path in before if before[path] != after[path])
assert set(changed['inputs']) == {'PROVENANCE.md', 'docs/t22-certification.md',
    'pdf-document/src/test/java/net/zerocloud/pdf/HardenedWorkerTerminationWaitTest.java'}
assert changed['contract-inputs'] == ['capabilities/evidence/T22-worker-recovery-scale.md']
assert len(inputs['inputs']) == 2888 and len(inputs['contract-inputs']) == 32
gates = []
for name, code, marker in (
        ('worker-30s-delayed-exit-red-r2', 1, 'The Worker did not terminate after completing its response.'),
        ('worker-post-finished-nonzero-red-r2', 1, 'java.lang.AssertionError: The Worker terminated without a valid response.'),
        ('worker-30s-evidence-guard-red-r1', 1, 'AssertionError: ValueError not raised'),
        ('python-recovery-worker-guards-r19', 0, 'Ran 35 tests'),
        ('checker-recovery-relaunch-r1', 0, 'Tests run: 12, Failures: 0, Errors: 0, Skipped: 0'),
        ('jdk8-interrupted-inspection-red-r1', 1, 'java.nio.channels.ClosedByInterruptException'),
        ('jdk8-interrupted-inspection-focused-green-r1', 0, 'Tests run: 158, Failures: 0, Errors: 0, Skipped: 0'),
        ('worker-fixture-focused-jdk17-green-r1', 0, 'Tests run: 158, Failures: 0, Errors: 0, Skipped: 0'),
        ('inventory-prefreeze-generate-r9', 0, None),
        ('post-r13-matrix-failure-container-check', 0, None)):
    path = delivery / 'validation' / (name + '-result.json')
    gate = json.loads(path.read_text())
    assert gate['exit-code'] == code
    assert gate['log'] == driver.reference(root, root / gate['log']['path'])
    assert marker is None or marker in (root / gate['log']['path']).read_text()
    gates.append(driver.reference(root, path))
diagnostics = {}
for name in ('jdk8-interrupted-inspection-focused-green-r1', 'worker-fixture-focused-jdk17-green-r1'):
    transcript = (delivery / 'validation' / (name + '.txt')).read_text()
    assert 'Tests run: 8, Failures: 0, Errors: 0, Skipped: 0' in transcript
    diagnostics[name] = require_exit_grace_observation(transcript)

preservation = json.loads((delivery / 'relaunch-preservation.json').read_text())
assert all(item == driver.reference(root, root / item['path']) for item in preservation['retained'])
assert all(item == driver.reference(root, root / item['path']) for item in previous['superseded-candidate6'])
reviews = [driver.reference(root, delivery / 'reviews' / (axis + '-relaunch-' + revision + '.md'))
           for revision in ('r2', 'r5') for axis in ('standards', 'spec')]
assert all('zero ' in (root / reference['path']).read_text().lower() for reference in reviews)
superseded7 = [driver.reference(root, path) for path in (
    delivery / 'source-freeze-final-r7.json',
    delivery / 'candidate-final-r7/build-inputs.json',
    delivery / 'candidate-final-r7/source-inputs.zip-parts.json',
    delivery / 'candidate-final-r7/artifacts-contracts-harness.zip-parts.json',
    delivery / 'refresh-final-r13/preflight.json',
    delivery / 'candidate-final-r7-verification.json',
    delivery / 'validation/full-verify-final-r8-result.json',
    delivery / 'validation/full-verify-final-r8.txt',
    delivery / 'validation/full-verify-final-r8-tests.json',
    delivery / 'validation/jdk-matrix-final-r3-result.json',
    delivery / 'validation/jdk-matrix-final-r3.txt',
    delivery / 'diagnostics/jdk8-interrupted-inspection-r1/preservation.json')]
failed = json.loads((delivery / 'validation/jdk-matrix-final-r3-result.json').read_text())
passed = json.loads((delivery / 'validation/full-verify-final-r8-result.json').read_text())
ledger = json.loads((delivery / 'candidate-final-r7-verification.json').read_text())
assert failed['exit-code'] == 1 and passed['exit-code'] == 0
assert [(row['gate'], row['exit-code']) for row in ledger] == [('full-verify-final-r8', 0), ('jdk-matrix-final-r3', 1)]
originals = json.loads((delivery / 'diagnostics/jdk8-interrupted-inspection-r1/preservation.json').read_text())
assert originals['no-certification-began'] is True and originals['real-grace-expiry-millis'] == 30007
assert originals['failed-result-sha256'] == driver.sha256(delivery / 'validation/jdk-matrix-final-r3-result.json')
assert originals['source-freeze7-sha256'] == driver.sha256(delivery / 'source-freeze-final-r7.json')
assert originals['staged-candidate7-sha256'] == driver.sha256(delivery / 'candidate-final-r7/build-inputs.json')
assert all(item == driver.reference(root, root / item['path']) for item in originals['originals'])
superseded7 += originals['originals']
assert not (delivery / 'refresh-final-r13/results.json').exists()
assert not (root / 'capabilities/evidence/foundation/T83-final-r13').exists()
assert not (delivery / 'validation/full-verify-final-r7-result.json').exists()
assert not (root / 'capabilities/evidence/foundation/T83-final-r12').exists()
assert (delivery / 'validation/post-r13-matrix-failure-container-check.txt').read_text() == ''
destination = delivery / 'source-freeze-final-r8.json'
assert not destination.exists()
driver.write_json(destination, {**inputs, 'recorded-at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'baseline': baseline, 'source-changes-since-final-r7': changed, 'development-gates': gates,
    'controlled-real-exit-diagnostics': diagnostics, 'reviews': reviews,
    'superseded-candidate-preservation': driver.reference(root, delivery / 'relaunch-preservation.json'),
    'superseded-candidate6': previous['superseded-candidate6'], 'superseded-candidate7': superseded7,
    'freeze-driver': driver.reference(root, Path(__file__)),
    'environment-selector': driver.reference(root, delivery / 'relaunch-environment.sh')})
print('Frozen candidate8 inputs: 2888 sources, 32 contracts, unchanged shipped Worker, 173 mandatory recovery cases; Java8/17 fixture greens and reviews resolved.')
