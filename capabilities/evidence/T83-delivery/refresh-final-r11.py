"""Continue unchanged candidate5 after R9's failed, unindexed text gate."""
import datetime
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys

import yaml

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
authority = root / 'capabilities/foundation-evidence.yaml'
spec = importlib.util.spec_from_file_location('t83_refresh_ten', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
build = root / 'target/foundation-0.1.0/build-inputs.json'
receipt = driver.require_staged_build(root, contract, build)
frozen_path = delivery / 'source-freeze-final-r5.json'
frozen = json.loads(frozen_path.read_text())
inputs = {key: frozen[key] for key in ('inputs', 'contract-inputs')}
assert driver.capture_build_inputs(root, contract) == inputs
assert receipt == json.loads((delivery / 'candidate-final-r5/build-inputs.json').read_text())
selected = ('transactions', 'values', 'pages', 'metadata', 'annotations', 'text', 'images',
            'incremental', 'password-baseline', 'password-clear-metadata', 'password-attachments',
            'limits', 'worker', 'recovery')
old_ledger_path = delivery / 'refresh-final-r9/results.json'
old_steps = json.loads(old_ledger_path.read_text())
assert [row['obligation'] for row in old_steps] == list(selected[:6])
assert [row['exit-code'] for row in old_steps] == [0, 0, 0, 0, 0, 1]
assert old_steps[-1]['certifications'] == 40 and old_steps[-1]['chain-records'] == 160
assert old_steps[-1]['prior-index']['sha256'] == old_steps[-1]['current-index-sha256']
assert driver.sha256(authority) == old_steps[-1]['current-index-sha256']

retained_paths = [frozen_path, build, delivery / 'candidate-final-r5/build-inputs.json',
                  old_ledger_path, delivery / 'candidate-final-r5-verification.json',
                  delivery / 'audit-final-r9-helper6-source.py', delivery / 'audit-final-r11.py']
for row in old_steps:
    assert row['prior-index'] == driver.reference(root, root / row['prior-index']['path'])
    retained_paths.append(root / row['prior-index']['path'])
    gate_path = delivery / 'validation' / ('refresh-final-r9-' + row['obligation'] + '-result.json')
    gate = json.loads(gate_path.read_text())
    assert gate['exit-code'] == row['exit-code']
    assert gate['log'] == driver.reference(root, root / gate['log']['path'])
    retained_paths += [gate_path, root / gate['log']['path']]
failed_scope = root / 'capabilities/evidence/foundation/T83-final-r9/text/jdk17-hardened_worker'
failed_text = (failed_scope / 'contract-tests.txt').read_text()
assert 'Tests run: 126,  Failures: 1' in failed_text
assert 'toUnicodeNodesAndCodeSpaceDeclarationsShareFontDataBounds' in failed_text
assert 'The Worker did not terminate after completing its response.' in failed_text
retained_paths += [failed_scope / 'contract-tests.txt', failed_scope / 'contract-tests-command.json']
for name in ('stage-final-r5', 'source-freeze-final-r5', 'archive-candidate-final-r5',
             'jdk-matrix-final-r1', 'full-verify-final-r6', 'standalone-scale-final-r4',
             'recovery-protocol-focused-after-exit-fix-r3', 'python-recovery-worker-guards-r16',
             'diagnostic-archive-helper-r8', 'text-worker-method-diagnostic-r1',
             'text-worker-prefix-diagnostic-r2', 'post-r9-failure-owned-container-check-r1',
             'post-text-diagnostics-owned-container-check-r1'):
    gate_path = delivery / 'validation' / (name + '-result.json')
    gate = json.loads(gate_path.read_text())
    assert gate['exit-code'] == 0 and gate['log'] == driver.reference(root, root / gate['log']['path'])
    retained_paths += [gate_path, root / gate['log']['path']]
assert 'Audit script SHA-256: ' + driver.sha256(delivery / 'audit-final-r11.py') in (delivery / 'validation/diagnostic-archive-helper-r8.txt').read_text()
assert not (delivery / 'validation/post-text-diagnostics-owned-container-check-r1.txt').read_text().strip()
for relative, count, marker in (
        ('diagnostics/text-worker-exit-r1/method-replays.json', 20, 'exact-method-passed'),
        ('diagnostics/text-worker-prefix-r2/prefix-replays.json', 10, 'exact-prefix-passed')):
    path = delivery / relative
    probe = json.loads(path.read_text())
    assert probe['diagnostic-only'] and probe['certifications-exported'] == 0
    assert probe['candidate'] == receipt['candidate'] and len(probe['results']) == count
    assert probe['original-index-sha256'] == driver.sha256(authority)
    assert probe['source-and-staged-inputs-unchanged'] and probe['index-unchanged']
    assert all(row['exit-code'] == 0 and row[marker] and not row['same-shutdown-symptom']
               and row['loaded-classpath-unchanged'] and row['owned-container-gone']
               for row in probe['results'])
    retained_paths.append(path)
    retained_paths += [root / row['log']['path'] for row in probe['results']]
verification = json.loads((delivery / 'candidate-final-r5-verification.json').read_text())
assert [row['gate'] for row in verification] == ['jdk-matrix-final-r1', 'full-verify-final-r6']
assert all(row['exit-code'] == 0 and row['source-staged-index-unchanged'] for row in verification)
for row in verification:
    assert row['result'] == driver.reference(root, root / row['result']['path'])
setup_ledger_path = delivery / 'refresh-final-r10/results.json'
setup_steps = json.loads(setup_ledger_path.read_text())
assert len(setup_steps) == 1 and setup_steps[0]['obligation'] == 'text' and setup_steps[0]['exit-code'] == 1
assert setup_steps[0]['current-index-sha256'] == setup_steps[0]['prior-index']['sha256'] == driver.sha256(authority)
setup_gate_path = delivery / 'validation/refresh-final-r10-text-result.json'
setup_gate = json.loads(setup_gate_path.read_text())
assert setup_gate['exit-code'] == 1 and setup_gate['log'] == driver.reference(root, root / setup_gate['log']['path'])
setup_text = (root / setup_gate['log']['path']).read_text()
assert 'FileNotFoundError' in setup_text and 'T13 environment JDK' not in setup_text
retained_paths += [setup_ledger_path, setup_gate_path, root / setup_gate['log']['path'],
                   delivery / 'refresh-final-r10/preflight.json', root / setup_steps[0]['prior-index']['path'],
                   delivery / 'refresh-final-r10.py', delivery / 'audit-final.py']
retained = [driver.reference(root, path) for path in sorted(set(retained_paths))]

roots = {name: 'capabilities/evidence/foundation/T83-final-r9/' + name for name in selected}
roots['text'] = 'capabilities/evidence/foundation/T83-final-r10/text'
for name in selected[5:]:
    assert not (root / roots[name]).exists(), 'A fresh destination is required: ' + roots[name]

def assert_current(completed, expected_hash):
    driver.require_staged_build(root, contract, build)
    assert driver.capture_build_inputs(root, contract) == inputs
    assert all(item == driver.reference(root, root / item['path']) for item in retained)
    assert driver.sha256(authority) == expected_hash
    index = yaml.safe_load(authority.read_text())
    assert index['candidate'] == receipt['candidate']
    expected = {(item['id'], environment, execution)
                for item in contract['obligations'] if item['id'] in completed
                for environment in item['environments'] for execution in item['execution-profiles']}
    assert {(item['obligation'], item['environment'], item['execution-profile'])
            for item in index['certifications']} == expected
    assert all(item['configuration']['path'].startswith(roots[item['obligation']] + '/')
               for item in index['certifications'])
    chains = sum(len(item['chains']) * len(item['environments']) * len(item['execution-profiles'])
                 for item in contract['obligations'] if item['id'] in completed)
    assert sum(len(item['records']) for item in index['certifications']) == chains
    return index

completed = set(selected[:5])
previous = driver.sha256(authority)
assert_current(completed, previous)
record_dir = delivery / 'refresh-final-r11'
combined_dir = delivery / 'refresh-final-complete-r1'
assert not record_dir.exists() and not combined_dir.exists()
record_dir.mkdir()
driver.write_json(record_dir / 'preflight.json', {
    'candidate': receipt['candidate'], 'source-freeze': driver.reference(root, frozen_path),
    'continuation-driver': driver.reference(root, Path(__file__)), 'retained-inputs': retained,
    'starting-index': driver.reference(root, authority), 'completed': list(selected[:5]),
    'actual-root-map': roots, 'diagnostics-exported-certifications': 0,
    'uncontrolled-text-failure-cause-established': False})
results = []
for name in selected[5:]:
    assert_current(completed, previous)
    prior = record_dir / (name + '-prior-index.yaml')
    shutil.copyfile(authority, prior)
    destination = root / roots[name]
    assert not destination.exists()
    # The certification command creates its root without parents.
    # Only the new, unused R10 text parent requires provisioning.
    if not destination.parent.exists():
        assert name == 'text' and destination.parent == root / 'capabilities/evidence/foundation/T83-final-r10'
        destination.parent.mkdir()
    command = [sys.executable, str(delivery / 'run-gate.py'), 'refresh-final-r11-' + name,
               'host', 'python3', '-B', 'scripts/t03-foundation.py', 'certify',
               roots[name], '--obligation', name]
    started = datetime.datetime.now(datetime.timezone.utc).isoformat()
    print('Unchanged candidate continuation: ' + name + ' at ' + started, flush=True)
    process = subprocess.run(command, cwd=root)
    index = yaml.safe_load(authority.read_text())
    row = {'obligation': name, 'started': started,
           'finished': datetime.datetime.now(datetime.timezone.utc).isoformat(),
           'command': command, 'exit-code': process.returncode,
           'prior-index': driver.reference(root, prior),
           'current-index-sha256': driver.sha256(authority),
           'certifications': len(index['certifications']),
           'chain-records': sum(len(item['records']) for item in index['certifications'])}
    results.append(row)
    driver.write_json(record_dir / 'results.json', results)
    if process.returncode:
        assert row['current-index-sha256'] == previous == row['prior-index']['sha256']
        assert_current(completed, previous)
        print('Stopped at failed fresh continuation; no failed tuples admitted: ' + name, flush=True)
        sys.exit(process.returncode)
    completed.add(name)
    previous = row['current-index-sha256']
    assert_current(completed, previous)
assert len(index['certifications']) == 100 and row['chain-records'] == 412
combined_dir.mkdir()
new_ledger_path = record_dir / 'results.json'
combined = []
for ledger, steps in ((old_ledger_path, old_steps[:5]), (new_ledger_path, results)):
    for position, step in enumerate(steps):
        combined.append({**step, 'source-step': {'ledger': driver.reference(root, ledger),
                                               'row-index': position}})
assert [row['obligation'] for row in combined] == list(selected)
assert all(row['exit-code'] == 0 for row in combined)
driver.write_json(combined_dir / 'results.json', combined)
print('All fourteen obligations qualify unchanged candidate5; failed R9 text and diagnostics remain separate.', flush=True)
