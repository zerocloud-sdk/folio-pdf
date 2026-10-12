"""Retain original failed full-verify observations before any further Maven run."""
import importlib.util
import json
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET

import yaml

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('t83_preserve_verify_r11', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
receipt = driver.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
frozen = json.loads((delivery / 'source-freeze-final-r9.json').read_text())
assert driver.capture_build_inputs(root, contract) == {k: frozen[k] for k in ('inputs', 'contract-inputs')}
gate_path = delivery / 'validation/full-verify-final-r11-result.json'
gate = json.loads(gate_path.read_text())
assert gate['exit-code'] == 1 and gate['log'] == driver.reference(root, root / gate['log']['path'])
base = delivery / 'diagnostics/full-verify-unicode-elapsed-r1'
assert not base.exists()
base.mkdir()
reports = root / 'pdf-acceptance/target/surefire-reports'
copied = []
for path in sorted(reports.iterdir()):
    assert path.is_file() and not path.is_symlink()
    dest = base / 'original-surefire' / path.name
    dest.parent.mkdir(exist_ok=True)
    shutil.copyfile(path, dest)
    assert driver.sha256(path) == driver.sha256(dest)
    copied.append(driver.reference(root, dest))
extra = root / 'pdf-document/target/surefire-reports'
for path in sorted(extra.iterdir()):
    assert path.is_file() and not path.is_symlink()
    dest = base / 'original-document-surefire' / path.name
    dest.parent.mkdir(exist_ok=True)
    shutil.copyfile(path, dest)
    assert driver.sha256(path) == driver.sha256(dest)
    copied.append(driver.reference(root, dest))
report = base / 'original-surefire/TEST-net.zerocloud.pdf.acceptance.T28UnicodeEvidenceCommandTest.xml'
original = ET.parse(report).getroot()
failed = [c for c in original.findall('testcase') if c.find('error') is not None]
assert len(failed) == 1
assert failed[0].get('name') == 'sevenProfilesMatchTheIndependentGlyphGeometryInBothExecutionModes'
assert failed[0].find('error').get('message') == 'The workflow elapsed-time limit was exceeded.'
authority = root / 'capabilities/foundation-evidence.yaml'
index_hash = driver.sha256(authority)
assert index_hash == driver.sha256(delivery / 'refresh-final-r16-prior-index.yaml')
retained = [driver.reference(root, p) for p in (
    gate_path, root / gate['log']['path'], delivery / 'candidate-final-r9-verification-r17.json',
    delivery / 'refresh-final-r17/preflight.json', delivery / 'refresh-relaunch-r17.py')]
driver.write_json(base / 'preservation.json', {
    'candidate': receipt['candidate'], 'source-freeze': driver.reference(root, delivery / 'source-freeze-final-r9.json'),
    'original-index-sha256': index_hash, 'diagnostics-exported-certifications': 0,
    'preserver': driver.reference(root, Path(__file__)), 'failed-gate': driver.reference(root, gate_path),
    'original-files': copied, 'retained-inputs': retained,
    'failed-method': failed[0].get('name'), 'observed-method-elapsed-seconds': float(failed[0].get('time')),
    'symptom': failed[0].find('error').get('message'), 'source-staged-index-unchanged': True})
print('Preserved ' + str(len(copied)) + ' original Surefire files; failed full gate remains failed; no certification began.')
