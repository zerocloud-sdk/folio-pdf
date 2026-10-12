"""Fresh fourteen-obligation certification after the bounded T80 correction."""
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
spec = importlib.util.spec_from_file_location('t83_refresh_seven', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
build = root / 'target/foundation-0.1.0/build-inputs.json'
receipt = driver.require_staged_build(root, contract, build)
frozen = json.loads((delivery / 'source-freeze-final-r4.json').read_text())
frozen_inputs = {key: frozen[key] for key in ('inputs', 'contract-inputs')}
assert driver.capture_build_inputs(root, contract) == frozen_inputs
old_receipt = json.loads((delivery / 'candidate-final-r3/build-inputs.json').read_text())
assert receipt['candidate'] != old_receipt['candidate'], 'Changed coordinator requires a fresh candidate'
previous = None
history = []
for number in range(1, 6):
    steps = json.loads((delivery / ('refresh-final-r' + str(number)) / 'results.json').read_text())
    assert steps[-1]['exit-code'] == 1
    assert steps[-1]['current-index-sha256'] == steps[-1]['prior-index']['sha256']
    for step in steps:
        assert driver.reference(root, root / step['prior-index']['path']) == step['prior-index']
        if previous is not None:
            assert step['prior-index']['sha256'] == previous
        previous = step['current-index-sha256']
    history.extend(steps)
assert history[-1]['obligation'] == 'password-attachments'
assert history[-1]['certifications'] == 80 and history[-1]['chain-records'] == 320
assert driver.sha256(authority) == previous
old_index = yaml.safe_load(authority.read_text())
assert old_index['candidate'] == old_receipt['candidate']

def require_gate(name, code=0):
    gate = json.loads((delivery / 'validation' / (name + '-result.json')).read_text())
    assert gate['exit-code'] == code
    assert driver.sha256(root / gate['log']['path']) == gate['log']['sha256']
    return gate

for name in ('stage-final-r4', 'python-recovery-worker-guards-r13',
             'recovery-bound-memory-test-green-r2', 'recovery-input-memory-bound-replay-r2',
             'post-r4-owned-container-check-r1', 'archive-candidate-final-r4',
             'diagnostic-archive-helper-r5', 'full-verify-final-r5', 'standalone-scale-final-r3', 'source-freeze-final-r4',
             'coordinator-budget-focused-r1', 'validate-attachments-duration-r1',
             'archive-final-audit-r5-source-r1',
             'post-attachment-observer-duration-owned-container-check-r1',
             'shaping-worker-exit-diagnosis-r1', 'shaping-worker-exit-diagnosis-r2',
             'archive-final-audit-r7-preverify-source-r1',
             'post-shaping-worker-exit-diagnosis-owned-container-check-r1'):
    require_gate(name)
require_gate('recovery-bound-memory-test-red-r1', 1)
require_gate('attachments-observer-duration-r1', 1)
assert not (delivery / 'validation/post-attachment-observer-duration-owned-container-check-r1.txt').read_text().strip()
duration = json.loads((delivery / 'diagnostics/validated-attachments-duration-r1.json').read_text())
assert duration['diagnostic-only'] and duration['certifications-exported'] == 0
assert duration['candidate'] == old_receipt['candidate'] and duration['observer-exit-code'] == 0
assert 900 < duration['observer-seal-seconds'] < duration['elapsed-seconds-including-python-owner'] < 1800
for name in ('failed-probe-gate', 'completed-observer', 'private-storage', 'validator'):
    assert duration[name] == driver.reference(root, root / duration[name]['path'])
require_gate('recovery-complete-source-test-red-r1', 1)
assert not (delivery / 'validation/post-r4-owned-container-check-r1.txt').read_text().strip()
assert 'Audit script SHA-256: ' + driver.sha256(delivery / 'audit-final.py') in (delivery / 'validation/diagnostic-archive-helper-r5.txt').read_text()
diagnostic = json.loads((delivery / 'diagnostics/recovery-input-memory-bound-replay-r2.json').read_text())
assert diagnostic['diagnostic-only'] and diagnostic['certifications-exported'] == 0
assert [item['usage.memory'] for item in diagnostic['observations']] == ['1060', '1068']
for item in diagnostic['observations']:
    assert driver.sha256(root / item['path']) == item['sha256']
assert driver.sha256(delivery / 'audit-final-r4-source.py') == '6aff17c950f3628b9fc842feca4e517cdd7dad8796ea8a2efe7aeacc09ef22ee'
for archive_name in ('source-inputs.zip', 'artifacts-contracts-harness.zip'):
    base = delivery / 'candidate-final-r4'
    manifest = json.loads((base / (archive_name + '-parts.json')).read_text())
    for part in manifest['parts']:
        assert driver.sha256(base / part['path']) == part['sha256']
        assert (base / part['path']).stat().st_size == part['bytes'] <= 48 * 1024 * 1024
assert json.loads((delivery / 'candidate-final-r4/build-inputs.json').read_text()) == receipt

selected = ('transactions', 'values', 'pages', 'metadata', 'annotations', 'text', 'images',
            'incremental', 'password-baseline', 'password-clear-metadata', 'password-attachments',
            'limits', 'worker', 'recovery')
record_dir = delivery / 'refresh-final-r7'
output_base = root / 'capabilities/evidence/foundation/T83-final-r7'
assert not record_dir.exists() and not output_base.exists()
record_dir.mkdir()
output_base.mkdir()
results = []
for obligation in selected:
    driver.require_staged_build(root, contract, build)
    assert driver.capture_build_inputs(root, contract) == frozen_inputs
    prior = record_dir / (obligation + '-prior-index.yaml')
    shutil.copyfile(authority, prior)
    destination = output_base / obligation
    command = [sys.executable, str(delivery / 'run-gate.py'), 'refresh-final-r7-' + obligation,
               'host', 'python3', '-B', 'scripts/t03-foundation.py', 'certify',
               destination.relative_to(root).as_posix(), '--obligation', obligation]
    started = datetime.datetime.now(datetime.timezone.utc).isoformat()
    print('Fresh candidate: ' + obligation + ' at ' + started, flush=True)
    process = subprocess.run(command, cwd=root)
    current = yaml.safe_load(authority.read_text())
    result = {'obligation': obligation, 'started': started,
              'finished': datetime.datetime.now(datetime.timezone.utc).isoformat(),
              'command': command, 'exit-code': process.returncode,
              'prior-index': driver.reference(root, prior),
              'current-index-sha256': driver.sha256(authority),
              'certifications': len(current['certifications']),
              'chain-records': sum(len(item['records']) for item in current['certifications'])}
    results.append(result)
    driver.write_json(record_dir / 'results.json', results)
    driver.require_staged_build(root, contract, build)
    assert driver.capture_build_inputs(root, contract) == frozen_inputs
    if process.returncode:
        assert result['current-index-sha256'] == result['prior-index']['sha256']
        print('Stopped at the first failed fresh certification: ' + obligation, flush=True)
        sys.exit(process.returncode)
    assert current['candidate'] == receipt['candidate']
    completed = set(item['obligation'] for item in results)
    expected = {(item['id'], environment, execution)
                for item in contract['obligations'] if item['id'] in completed
                for environment in item['environments'] for execution in item['execution-profiles']}
    assert {(item['obligation'], item['environment'], item['execution-profile'])
            for item in current['certifications']} == expected
    assert all(item['configuration']['path'].startswith(output_base.relative_to(root).as_posix() + '/')
               for item in current['certifications'])
    expected_chains = sum(len(item['chains']) * len(item['environments']) * len(item['execution-profiles'])
                          for item in contract['obligations'] if item['id'] in completed)
    assert result['chain-records'] == expected_chains
assert len(current['certifications']) == 100 and result['chain-records'] == 412
print('All fourteen obligations freshly certified; historical and failed records remain separate.', flush=True)
