"""Fresh remaining certification, gated on two successful unchanged recorder replays."""
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
spec = importlib.util.spec_from_file_location('t83_sixth_continuation', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
build = root / 'target/foundation-0.1.0/build-inputs.json'
receipt = driver.require_staged_build(root, contract, build)
freeze = json.loads((delivery / 'source-freeze-final-r3.json').read_text())
inputs = {key: freeze[key] for key in ('inputs', 'contract-inputs')}
assert driver.capture_build_inputs(root, contract) == inputs
assert json.loads((delivery / 'candidate-final-r3/build-inputs.json').read_text()) == receipt
completed = ('transactions', 'values', 'pages', 'metadata', 'annotations', 'text', 'images',
             'incremental', 'password-baseline', 'password-clear-metadata')
history = []
previous = None
for number in range(1, 6):
    rows = json.loads((delivery / ('refresh-final-r' + str(number)) / 'results.json').read_text())
    assert rows[-1]['exit-code'] == 1
    assert rows[-1]['current-index-sha256'] == rows[-1]['prior-index']['sha256']
    for row in rows:
        assert driver.reference(root, root / row['prior-index']['path']) == row['prior-index']
        if previous is not None:
            assert row['prior-index']['sha256'] == previous
        previous = row['current-index-sha256']
    history.extend(rows)
fifth = json.loads((delivery / 'refresh-final-r5/results.json').read_text())
assert [row['obligation'] for row in fifth[:-1]] == list(completed)
assert all(row['exit-code'] == 0 for row in fifth[:-1])
failed = fifth[-1]
assert failed['obligation'] == 'password-attachments'
assert failed['certifications'] == 80 and failed['chain-records'] == 320
assert previous == driver.sha256(authority) == 'f073a34f04162a29a39bfb2d1ab126449a2801f8c701a013992fe3cc3fc03a70'

def require_gate(name, expected=0):
    gate = json.loads((delivery / 'validation' / (name + '-result.json')).read_text())
    assert gate['exit-code'] == expected, (name, gate['exit-code'])
    assert driver.sha256(root / gate['log']['path']) == gate['log']['sha256']
    return gate

for name in ('stage-final-r3', 'archive-candidate-final-r3', 'full-verify-final-r2',
             'standalone-scale-final-r2', 'python-recovery-worker-guards-r12',
             'attachments-coordinator-diagnosis-r1', 'post-r5-failure-owned-container-check-r1',
             'post-attachment-coordinator-diagnosis-owned-container-check-r1',
             'diagnostic-archive-helper-r4'):
    require_gate(name)
require_gate('refresh-final-r5-password-attachments', 1)
for name in ('post-r5-failure-owned-container-check-r1',
             'post-attachment-coordinator-diagnosis-owned-container-check-r1'):
    assert not (delivery / 'validation' / (name + '.txt')).read_text().strip()
assert 'Audit script SHA-256: ' + driver.sha256(delivery / 'audit-final.py') in (
    delivery / 'validation/diagnostic-archive-helper-r4.txt').read_text()
assert 'Audit script SHA-256: ' + driver.sha256(delivery / 'audit-final-r5-source.py') in (
    delivery / 'validation/diagnostic-archive-helper-r3.txt').read_text()
diagnostic_path = delivery / 'diagnostics/attachments-coordinator-replay-r1/results.json'
diagnostic = json.loads(diagnostic_path.read_text())
assert diagnostic['finished'] and diagnostic['diagnostic-only']
assert diagnostic['certifications-exported'] == 0
assert diagnostic['original-files-unchanged'] and diagnostic['index-unchanged']
assert diagnostic['candidate'] == receipt['candidate']
assert diagnostic['coordinator-timeout-milliseconds'] == 900000
assert diagnostic['original-index-sha256'] == driver.sha256(authority)
assert [row['number'] for row in diagnostic['runs']] == [1, 2]
source = root / 'capabilities/evidence/foundation/T83-final-r5/password-attachments/jdk8-in_process'
assert diagnostic['execution'] == driver.reference(root, source / 'execution.yaml')
assert len(diagnostic['original-files']) == 7062
assert [row['path'] for row in diagnostic['original-files']] == [
    path.relative_to(root).as_posix()
    for path in sorted(path for path in source.rglob('*') if path.is_file())]
for row in diagnostic['original-files']:
    path = root / row['path']
    assert not path.is_symlink() and path.stat().st_size == row['bytes']
    assert driver.sha256(path) == row['sha256']
assert not (source / 'observations/result.properties').exists()
original_command = yaml.safe_load((source / 'execution.yaml').read_text())['command']
mount = str(source) + ':/workspace/' + source.relative_to(root).as_posix() + ':rw'
assert original_command.count(mount) == 1
for row in diagnostic['runs']:
    # The outer diagnostic can succeed with recorded timeouts. Qualification
    # requires each real recorder to succeed and seal its own complete output.
    assert row['exit-code'] == 0 and not row['specific-coordinator-timeout']
    number = row['number']
    gate = require_gate('attachment-coordinator-replay-r1-run-' + str(number))
    assert row['gate'] == driver.reference(root, delivery / 'validation' /
        ('attachment-coordinator-replay-r1-run-' + str(number) + '-result.json'))
    assert row['log'] == gate['log']
    assert row['command'] == driver.reference(root, root / row['command']['path'])
    scope = diagnostic_path.parent / ('run-' + str(number))
    command = list(original_command)
    command[command.index(mount)] = str(scope) + ':/workspace/' + scope.relative_to(root).as_posix() + ':rw'
    command[-3] = '/workspace/' + (scope / 'observations').relative_to(root).as_posix()
    assert json.loads((root / row['command']['path']).read_text()) == command == gate['command']
    result = driver.properties(scope / 'observations/result.properties')
    assert result['phase'] == 'certification'
    assert result['native-execution-profile'] == result['facade-execution-profile'] == 'IN_PROCESS'
    assert all(result[chain] == 'pass' for chain in ('syntax', 'standards', 'semantic', 'visual'))
    assert result['retained-files-sha256'] == driver.sha256(scope / 'observations/retained-files.sha256')
    assert len(list((scope / 'observations').glob('*/product.pdf'))) == 62

current = yaml.safe_load(authority.read_text())
assert current['candidate'] == receipt['candidate']
assert len(current['certifications']) == 80
assert sum(len(item['records']) for item in current['certifications']) == 320
assert {item['obligation'] for item in current['certifications']} == set(completed)
assert all(item['configuration']['path'].startswith('capabilities/evidence/foundation/T83-final-r5/')
           for item in current['certifications'])
for archive in ('source-inputs.zip', 'artifacts-contracts-harness.zip'):
    base = delivery / 'candidate-final-r3'
    manifest = json.loads((base / (archive + '-parts.json')).read_text())
    for part in manifest['parts']:
        assert driver.sha256(base / part['path']) == part['sha256']
        assert (base / part['path']).stat().st_size == part['bytes'] <= 48 * 1024 * 1024

record_dir = delivery / 'refresh-final-r6'
retry_base = root / 'capabilities/evidence/foundation/T83-final-r6'
steps = (
    ('password-attachments', retry_base / 'password-attachments', 88, 352),
    ('limits', root / 'capabilities/evidence/foundation/T83-final-r5/limits', 92, 372),
    ('worker', root / 'capabilities/evidence/foundation/T83-final-r5/worker', 96, 392),
    ('recovery', root / 'capabilities/evidence/foundation/T83-final-r5/recovery', 100, 412))
assert not record_dir.exists() and not retry_base.exists()
assert all(not destination.exists() for _, destination, _, _ in steps)
record_dir.mkdir()
retry_base.mkdir()
results = []
for obligation, destination, count, chains in steps:
    driver.require_staged_build(root, contract, build)
    assert driver.capture_build_inputs(root, contract) == inputs
    prior = record_dir / (obligation + '-prior-index.yaml')
    shutil.copyfile(authority, prior)
    command = [sys.executable, str(delivery / 'run-gate.py'), 'refresh-final-r6-' + obligation,
               'host', 'python3', '-B', 'scripts/t03-foundation.py', 'certify',
               destination.relative_to(root).as_posix(), '--obligation', obligation]
    started = datetime.datetime.now(datetime.timezone.utc).isoformat()
    print('Fresh remaining obligation: ' + obligation + ' at ' + started, flush=True)
    process = subprocess.run(command, cwd=root)
    current = yaml.safe_load(authority.read_text())
    row = {'obligation': obligation, 'started': started,
           'finished': datetime.datetime.now(datetime.timezone.utc).isoformat(),
           'command': command, 'exit-code': process.returncode,
           'prior-index': driver.reference(root, prior), 'current-index-sha256': driver.sha256(authority),
           'certifications': len(current['certifications']),
           'chain-records': sum(len(item['records']) for item in current['certifications'])}
    results.append(row)
    driver.write_json(record_dir / 'results.json', results)
    driver.require_staged_build(root, contract, build)
    assert driver.capture_build_inputs(root, contract) == inputs
    if process.returncode:
        assert row['current-index-sha256'] == row['prior-index']['sha256']
        print('Stopped at first failed remaining certification: ' + obligation, flush=True)
        sys.exit(process.returncode)
    assert current['candidate'] == receipt['candidate']
    assert row['certifications'] == count and row['chain-records'] == chains
    effective = set(completed) | {item['obligation'] for item in results}
    expected = {(item['id'], environment, execution)
                for item in contract['obligations'] if item['id'] in effective
                for environment in item['environments'] for execution in item['execution-profiles']}
    assert {(item['obligation'], item['environment'], item['execution-profile'])
            for item in current['certifications']} == expected
    for item in current['certifications']:
        base = retry_base if item['obligation'] == 'password-attachments' else root / 'capabilities/evidence/foundation/T83-final-r5'
        assert item['configuration']['path'].startswith(base.relative_to(root).as_posix() + '/')
print('All fourteen same-candidate obligations finished in order; five failed attempts stay retained and excluded.', flush=True)
