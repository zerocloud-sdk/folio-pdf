"""Permit one fresh full verification only after completed preserved diagnosis."""
import ast
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

import yaml

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('t83_verify_retry', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
build = root / 'target/foundation-0.1.0/build-inputs.json'
receipt = driver.require_staged_build(root, contract, build)
frozen = json.loads((delivery / 'source-freeze-final-r4.json').read_text())
inputs = {key: frozen[key] for key in ('inputs', 'contract-inputs')}
assert driver.capture_build_inputs(root, contract) == inputs
assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip() == frozen['baseline']
authority = root / 'capabilities/foundation-evidence.yaml'
index_hash = driver.sha256(authority)
base = delivery / 'diagnostics/full-verify-worker-exit-r1'
diagnostics = []
for revision, count, directory in ((1, 100, base), (2, 10, base / 'revision-2')):
    gate = json.loads((delivery / 'validation' / ('shaping-worker-exit-diagnosis-r' + str(revision) + '-result.json')).read_text())
    assert gate['exit-code'] == 0 and gate['log'] == driver.reference(root, root / gate['log']['path'])
    path = directory / 'method-replays.json'
    record = json.loads(path.read_text())
    assert record['diagnostic-only'] and record['certifications-exported'] == 0
    assert record['candidate'] == receipt['candidate'] and record['original-index-sha256'] == index_hash
    assert len(record['results']) == count
    for row in record['results']:
        assert row['exit-code'] == 0 and row['exact-method-passed'] and not row['same-shutdown-symptom']
        assert row['log'] == driver.reference(root, root / row['log']['path'])
        assert 'OK (1 test)' in (root / row['log']['path']).read_text()
        if revision == 2:
            assert not row['outer-timeout'] and row['loaded-classpath-unchanged'] and row['owned-container-gone']
    for key in ('probe', 'helper-source', 'compile-log', 'original-report'):
        assert record[key] == driver.reference(root, root / record[key]['path'])
    diagnostics.append(driver.reference(root, path))
# Exercise the actual revision2 inventory function against the current loaded bytes.
assert record['loaded-classpath-files'] == driver.reference(root, root / record['loaded-classpath-files']['path'])
original = ET.parse(root / record['original-report']['path']).getroot()
properties = {item.attrib['name']: item.attrib['value'] for item in original.find('properties')}
function = [node for node in ast.parse((root / record['probe']['path']).read_text()).body
            if isinstance(node, ast.FunctionDef) and node.name == 'classpath_files']
assert len(function) == 1
namespace = {'root': root, 'driver': driver, 'Path': Path, 'properties': properties,
             'cp': properties['java.class.path'], 'classes': root / '.build-cache/t83-shaping-worker-exit-r2/classes'}
exec(compile(ast.Module(body=function, type_ignores=[]), record['probe']['path'], 'exec'), namespace)
assert namespace['classpath_files']() == json.loads((root / record['loaded-classpath-files']['path']).read_text())
cleanup_gate = json.loads((delivery / 'validation/post-shaping-worker-exit-diagnosis-owned-container-check-r1-result.json').read_text())
assert cleanup_gate['exit-code'] == 0 and cleanup_gate['log'] == driver.reference(root, root / cleanup_gate['log']['path'])
assert not (root / cleanup_gate['log']['path']).read_text().strip()
destination = base / 'verification-retry.json'
assert not destination.exists()
command = [sys.executable, str(delivery / 'run-gate.py'), 'full-verify-final-r5', 'ubuntu', './mvnw', '-B', '-ntp', 'verify']
record = {'diagnostic-only-records': diagnostics, 'candidate': receipt['candidate'],
          'prior-index-sha256': index_hash, 'command': command,
          'disposition': 'Observed shutdown was not reproduced in isolated diagnostics; no runtime change, cause, fix or failure-rate guarantee. Original full verification remains failed; one fresh complete verification is required.'}
driver.write_json(destination, record)
print('Completed diagnostic/input/ownership guards pass; starting one fresh complete verify.', flush=True)
process = subprocess.run(command, cwd=root)
record['exit-code'] = process.returncode
driver.write_json(destination, record)
driver.require_staged_build(root, contract, build)
assert driver.capture_build_inputs(root, contract) == inputs
assert driver.sha256(authority) == index_hash
record['source-staged-index-unchanged'] = True
driver.write_json(destination, record)
sys.exit(process.returncode)
