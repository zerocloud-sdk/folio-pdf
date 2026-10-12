"""Freeze only reviewed current-status/count corrections, preserving candidate8."""
import datetime
import importlib.util
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys

import yaml

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
baseline = '42fe5c797711070b319b9419e14fcb7adfa98735'
assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip() == baseline
assert not subprocess.check_output(['git', 'diff', '--cached', '--name-only'], cwd=root, text=True).strip()
sys.path.insert(0, str(root / 'scripts'))
spec = importlib.util.spec_from_file_location('t83_freeze_r16', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
inputs = driver.capture_build_inputs(root, contract)
previous = json.loads((delivery / 'source-freeze-final-r8.json').read_text())
changed = {}
for key in ('inputs', 'contract-inputs'):
    before = {item['path']: item['sha256'] for item in previous[key]}
    after = {item['path']: item['sha256'] for item in inputs[key]}
    assert before.keys() == after.keys()
    changed[key] = sorted(path for path in before if before[path] != after[path])
assert changed['inputs'] == ['docs/generated/capability-matrix.md', 'docs/hardened-worker.md']
assert changed['contract-inputs'] == ['capabilities/capability-matrix.yaml']
assert len(inputs['inputs']) == 2888 and len(inputs['contract-inputs']) == 32
assert 'retain their separate experimental recovery/scale status' not in (root / 'docs/hardened-worker.md').read_text()
assert 'All 173 mandatory named cases' in (root / 'capabilities/capability-matrix.yaml').read_text()
assert 'All 165 mandatory named cases' not in (root / 'capabilities/capability-matrix.yaml').read_text()
closeout_path = delivery / 'validation/refresh-final-r14-text-closeout-r15-result.json'
closeout = json.loads(closeout_path.read_text())
assert closeout['exit-code'] == 0
assert closeout['log'] == driver.reference(root, root / closeout['log']['path'])
result_path = delivery / 'refresh-final-r15/text-closeout/result.json'
result = json.loads(result_path.read_text())
assert result['result'] == 'pass' and result['originals-preserved'] is True
assert result['certifications'] == 48 and result['chain-records'] == 192
assert result['current-index'] == driver.reference(root, root / 'capabilities/foundation-evidence.yaml')
assert not (delivery / 'validation/refresh-final-r14-text-result.json').exists()
prefix_path = delivery / 'refresh-final-r14/results.json'
prefix = json.loads(prefix_path.read_text())
assert [item['obligation'] for item in prefix] == ['transactions', 'values', 'pages', 'metadata', 'annotations']
assert all(item['exit-code'] == 0 for item in prefix)
index = yaml.safe_load((root / 'capabilities/foundation-evidence.yaml').read_text())
assert len(index['certifications']) == 48 and sum(len(item['records']) for item in index['certifications']) == 192
assert {item['obligation'] for item in index['certifications']} == {'transactions', 'values', 'pages', 'metadata', 'annotations', 'text'}
old_receipt = json.loads((delivery / 'candidate-final-r8/build-inputs.json').read_text())
assert index['candidate'] == old_receipt['candidate']
assert json.loads((root / 'target/foundation-0.1.0/build-inputs.json').read_text()) == old_receipt
for item in old_receipt['candidate']['artifacts'] + old_receipt['harness']:
    assert driver.reference(root, root / item['path']) == item
reviews = [driver.reference(root, delivery / 'reviews' / (axis + '-relaunch-r8.md'))
           for axis in ('standards', 'spec')]
for item in reviews:
    assert re.search(r'\bzero\s+(?:remaining\s+)?(?:actionable|applicable)', (root / item['path']).read_text().lower())
gates = []
for name in ('inventory-prefreeze-generate-r10', 'inventory-prefreeze-validate-r10'):
    path = delivery / 'validation' / (name + '-result.json')
    gate = json.loads(path.read_text())
    assert gate['exit-code'] == 0 and gate['log'] == driver.reference(root, root / gate['log']['path'])
    gates.append(driver.reference(root, path))
preservation = json.loads((delivery / 'relaunch-preservation.json').read_text())
assert all(item == driver.reference(root, root / item['path']) for item in preservation['retained'])
assert all(item == driver.reference(root, root / item['path'])
           for item in previous['superseded-candidate6'] + previous['superseded-candidate7'])
prior_index = delivery / 'refresh-final-r16-prior-index.yaml'
assert not prior_index.exists()
shutil.copyfile(root / 'capabilities/foundation-evidence.yaml', prior_index)
superseded8 = [driver.reference(root, path) for path in (
    delivery / 'source-freeze-final-r8.json', delivery / 'candidate-final-r8/build-inputs.json',
    delivery / 'candidate-final-r8/source-inputs.zip-parts.json',
    delivery / 'candidate-final-r8/artifacts-contracts-harness.zip-parts.json',
    delivery / 'candidate-final-r8-verification.json', delivery / 'refresh-final-r14/preflight.json',
    prefix_path, delivery / 'validation/refresh-final-r14-text.txt', closeout_path,
    root / closeout['log']['path'], result_path,
    delivery / 'refresh-final-r15/text-closeout/preflight.json',
    delivery / 'refresh-final-r15/text-closeout/original-files.tsv.gz', prior_index,
    delivery / 'reviews/standards-relaunch-r7.md', delivery / 'reviews/spec-relaunch-r7.md')]
for row in prefix:
    superseded8 += [row['gate-result'], row['prior-index']]
    gate = json.loads((root / row['gate-result']['path']).read_text())
    superseded8 += [gate['log']]
for row in json.loads((delivery / 'candidate-final-r8-verification.json').read_text()):
    assert row['exit-code'] == 0
    superseded8 += [row['result']]
    gate = json.loads((root / row['result']['path']).read_text())
    superseded8 += [gate['log']]
destination = delivery / 'source-freeze-final-r9.json'
assert not destination.exists()
driver.write_json(destination, {**inputs, 'recorded-at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'baseline': baseline, 'source-changes-since-final-r8': changed,
    'development-gates': previous['development-gates'] + gates,
    'controlled-real-exit-diagnostics': previous['controlled-real-exit-diagnostics'], 'reviews': reviews,
    'superseded-candidate-preservation': driver.reference(root, delivery / 'relaunch-preservation.json'),
    'superseded-candidate6': previous['superseded-candidate6'],
    'superseded-candidate7': previous['superseded-candidate7'], 'superseded-candidate8': superseded8,
    'freeze-driver': driver.reference(root, Path(__file__)),
    'environment-selector': driver.reference(root, delivery / 'relaunch-environment.sh')})
print('Frozen candidate9: two reviewed documentation corrections and generated matrix; shipped code/tests unchanged, candidate8 text closed and preserved.')
