"""Continue the frozen candidate after both retained Worker-exit failures."""
import datetime
import hashlib
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
first = json.loads((delivery / 'refresh-final-r1/results.json').read_text())
second = json.loads((delivery / 'refresh-final-r2/results.json').read_text())
completed = ('transactions', 'values', 'pages', 'metadata', 'annotations', 'text', 'images',
             'incremental', 'password-baseline', 'password-clear-metadata')
assert [step['obligation'] for step in first[:-1]] == list(completed[:8])
assert [step['obligation'] for step in second[:-1]] == list(completed[8:])
assert all(step['exit-code'] == 0 for step in first[:-1] + second[:-1])
for failed, obligation, count, chains in (
        (first[-1], 'password-baseline', 64, 256),
        (second[-1], 'password-attachments', 80, 320)):
    assert failed['obligation'] == obligation and failed['exit-code'] == 1
    assert failed['certifications'] == count and failed['chain-records'] == chains
    assert failed['current-index-sha256'] == failed['prior-index']['sha256']
assert first[-1]['current-index-sha256'] == second[0]['prior-index']['sha256']
assert hashlib.sha256(authority.read_bytes()).hexdigest() == second[-1]['current-index-sha256']

spec = importlib.util.spec_from_file_location('t83_second_continuation', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
build = root / 'target/foundation-0.1.0/build-inputs.json'
receipt = driver.require_staged_build(root, contract, build)
frozen = json.loads((delivery / 'source-freeze-final-r2.json').read_text())
frozen_inputs = {key: frozen[key] for key in ('inputs', 'contract-inputs')}
assert driver.capture_build_inputs(root, contract) == frozen_inputs
previous_index = None
for step in first + second:
    assert driver.reference(root, root / step['prior-index']['path']) == step['prior-index']
    if previous_index is not None:
        assert step['prior-index']['sha256'] == previous_index
    previous_index = step['current-index-sha256']
for gate_name, record_name, count, failed_scope, probe_script in (
        ('worker-exit-products-diagnosis-r2', 'worker-exit-replays-r2.json', 68,
         'password-baseline', 'worker-exit-replay-r2.py'),
        ('attachments-worker-exit-products-diagnosis-r1', 'attachments-exit-replays-r1.json', 62,
         'password-attachments', 'attachments-exit-replay-r1.py')):
    gate = json.loads((delivery / 'validation' / (gate_name + '-result.json')).read_text())
    assert gate['exit-code'] == 0 and driver.sha256(root / gate['log']['path']) == gate['log']['sha256']
    diagnostic = json.loads((delivery / 'diagnostics' / record_name).read_text())
    assert diagnostic['candidate'] == receipt['candidate'] and len(diagnostic['results']) == 5
    assert diagnostic['source-configuration'] == driver.reference(root, root /
        ('capabilities/evidence/foundation/T83-final/' + failed_scope + '/jdk8-hardened_worker/execution.yaml'))
    assert diagnostic['probe-script-sha256'] == driver.sha256(delivery / 'diagnostics' / probe_script)
    assert all(step['exit-code'] == 0 and not step['same-shutdown-symptom']
               and step['published-products'] == step['completed-public-records'] == count
               for step in diagnostic['results'])
    if count == 62:
        assert all(step['all-public-outcomes-complete'] for step in diagnostic['results'])
for gate_name in ('archive-diagnostics-r1', 'archive-attachments-diagnostics-r1'):
    gate = json.loads((delivery / 'validation' / (gate_name + '-result.json')).read_text())
    assert gate['exit-code'] == 0 and driver.sha256(root / gate['log']['path']) == gate['log']['sha256']
current = yaml.safe_load(authority.read_text())
assert current['candidate'] == receipt['candidate']
assert len(current['certifications']) == 80
assert sum(len(item['records']) for item in current['certifications']) == 320
assert {item['obligation'] for item in current['certifications']} == set(completed)

name = 'refresh-final-r3'
record_dir = delivery / name
record_dir.mkdir()
retry_base = root / 'capabilities/evidence/foundation/T83-final-r3'
retry_base.mkdir()
steps = (
    ('password-attachments', retry_base / 'password-attachments', 88, 352),
    ('limits', root / 'capabilities/evidence/foundation/T83-final/limits', 92, 372),
    ('worker', root / 'capabilities/evidence/foundation/T83-final/worker', 96, 392),
    ('recovery', root / 'capabilities/evidence/foundation/T83-final/recovery', 100, 412))
assert all(not destination.exists() for obligation, destination, count, chains in steps)
results = []
for obligation, destination, count, chains in steps:
    driver.require_staged_build(root, contract, build)
    assert driver.capture_build_inputs(root, contract) == frozen_inputs
    prior = record_dir / (obligation + '-prior-index.yaml')
    shutil.copyfile(authority, prior)
    arguments = ['python3', '-B', 'scripts/t03-foundation.py', 'certify',
                 destination.relative_to(root).as_posix(), '--obligation', obligation]
    command = [sys.executable, str(delivery / 'run-gate.py'), name + '-' + obligation, 'host', *arguments]
    started = datetime.datetime.now(datetime.timezone.utc).isoformat()
    print('Continuing ' + obligation + ' at ' + started, flush=True)
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
    (record_dir / 'results.json').write_text(json.dumps(results, indent=2) + '\n')
    driver.require_staged_build(root, contract, build)
    assert driver.capture_build_inputs(root, contract) == frozen_inputs
    if process.returncode:
        print('Stopped at the first failed continuation: ' + obligation, flush=True)
        sys.exit(process.returncode)
    assert current['candidate'] == receipt['candidate']
    assert result['certifications'] == count and result['chain-records'] == chains
    assert {item['obligation'] for item in current['certifications']} == set(completed) | {item['obligation'] for item in results}
print('All fourteen obligations finished in order; both failed certifications stay retained and excluded.', flush=True)
