"""Freeze the reviewed monotonic candidate while retaining every predecessor."""
import datetime
import importlib.util
import json
from pathlib import Path
import re
import subprocess

import yaml

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
baseline = '42fe5c797711070b319b9419e14fcb7adfa98735'
assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip() == baseline
assert not subprocess.check_output(['git', 'diff', '--cached', '--name-only'], cwd=root, text=True).strip()
spec = importlib.util.spec_from_file_location('t83_freeze_r20', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
inputs = driver.capture_build_inputs(root, contract)
previous = json.loads((delivery / 'source-freeze-final-r9.json').read_text())
changes = {}
for key in ('inputs', 'contract-inputs'):
    before = {item['path']: item['sha256'] for item in previous[key]}
    after = {item['path']: item['sha256'] for item in inputs[key]}
    assert before.keys() <= after.keys(), 'Do not discard prior #83 source'
    changes[key] = {'added': sorted(after.keys() - before.keys()),
                    'changed': sorted(path for path in before if before[path] != after[path])}
assert changes['inputs']['added'] == [
    'pdf-document/src/test/java/net/zerocloud/pdf/WorkerInitializationTimingTest.java',
    'pdf-document/src/test/java/net/zerocloud/pdf/consumer/WorkflowElapsedTimeContractTest.java']
assert len(inputs['inputs']) == 2890 and len(inputs['contract-inputs']) == 32
assert driver.certification_case('recovery')['test-count'] == 180
profile = json.loads((root / 'capabilities/profiles/T22-worker-recovery-scale/coverage.json').read_text())
assert set(profile['test-classes']) == set(driver.certification_case('recovery')['test-classes']) | set(driver.ARTIFACT_CONTRACT_TESTS)
assert 'Clock.systemUTC().equals' not in (root / 'pdf-document/src/main/java/net/zerocloud/pdf/WorkflowResourceContext.java').read_text()
preserved_path = delivery / 'superseded-preservation-r20.json'
preserved = json.loads(preserved_path.read_text())
assert preserved['originals-preserved'] and preserved['diagnostics-exported-certifications'] == 0
assert preserved['candidate8-text-originals-rehashed'] == 157778
assert driver.sha256(root / 'capabilities/foundation-evidence.yaml') == preserved['previous-index']['sha256']
reviews = []
for axis in ('standards', 'spec'):
    path = delivery / 'reviews' / (axis + '-relaunch-r20.md')
    assert re.search(r'\bzero\s+(?:remaining\s+|unresolved\s+)?(?:actionable|applicable|findings)', path.read_text().lower())
    reviews.append(driver.reference(root, path))
gates = []
for name in ('monotonic-recovery-protocol-green-r3', 'monotonic-python-plan-guards-r20',
             'monotonic-python-prerequisite-guards-r20', 'preserve-superseded-r20',
             'inventory-prefreeze-generate-r20', 'inventory-prefreeze-validate-r20'):
    path = delivery / 'validation' / (name + '-result.json')
    gate = json.loads(path.read_text())
    assert gate['exit-code'] == 0 and gate['log'] == driver.reference(root, root / gate['log']['path'])
    assert gate['monotonic-duration-seconds'] >= 0
    gates.append(driver.reference(root, path))
refs = [driver.reference(root, delivery / name) for name in (
    'source-freeze-final-r9.json', 'superseded-preservation-r20.json', 'relaunch3-entry.json',
    'relaunch3-t28-investigation.md', 'relaunch3-stop-note.md',
    'refresh-final-r20-prior-index.yaml', 'refresh-relaunch-r19.py',
    'timing-audit-r20.md', 'timing-audit-r20.txt', 'python-timeout-source-r20.txt',
    'candidate-final-r9/build-inputs.json', 'candidate-final-r9/source-inputs.zip-parts.json',
    'candidate-final-r9/artifacts-contracts-harness.zip-parts.json')]
destination = delivery / 'source-freeze-final-r10.json'
assert not destination.exists()
driver.write_json(destination, {**inputs, 'baseline': baseline,
    'recorded-at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'source-changes-since-candidate9': changes, 'reviews': reviews, 'development-gates': gates,
    'superseded-preservation': driver.reference(root, preserved_path),
    'superseded-candidate9': refs,
    'freeze-driver': driver.reference(root, Path(__file__)),
    'environment-selector': driver.reference(root, delivery / 'relaunch-environment.sh'),
    'gate-wrapper': driver.reference(root, delivery / 'run-gate-monotonic.py'),
    'disposition': 'Candidate10 requires fresh full verification, four-JDK matrix, actual scale and every selected certification; prior PASS is not reused.'})
print('Frozen monotonic candidate10: 2890 source inputs / 32 contract inputs; all predecessor evidence preserved.', flush=True)
