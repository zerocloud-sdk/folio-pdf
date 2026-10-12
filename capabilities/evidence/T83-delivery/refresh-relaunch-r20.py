"""Fresh full verification, matrix, scale and all fourteen candidate10 obligations."""
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
spec = importlib.util.spec_from_file_location('t83_refresh_r20', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
build = root / 'target/foundation-0.1.0/build-inputs.json'
authority = root / 'capabilities/foundation-evidence.yaml'
frozen_path = delivery / 'source-freeze-final-r10.json'
frozen = json.loads(frozen_path.read_text())
inputs = {key: frozen[key] for key in ('inputs', 'contract-inputs')}
receipt = driver.require_staged_build(root, contract, build)
assert receipt == json.loads((delivery / 'candidate-final-r10/build-inputs.json').read_text())
assert receipt['candidate'] != json.loads((delivery / 'candidate-final-r9/build-inputs.json').read_text())['candidate']
assert driver.capture_build_inputs(root, contract) == inputs
retained = frozen['superseded-candidate9'] + frozen['reviews'] + frozen['development-gates']
retained += [driver.reference(root, path) for path in (
    frozen_path, delivery / 'candidate-final-r10/build-inputs.json', Path(__file__),
    delivery / 'run-gate-monotonic.py', delivery / 'relaunch-environment.sh')]
index_hash = driver.sha256(authority)
assert index_hash == driver.sha256(delivery / 'refresh-final-r20-prior-index.yaml')
selected = ('transactions', 'values', 'pages', 'metadata', 'annotations', 'text', 'images',
            'incremental', 'password-baseline', 'password-clear-metadata', 'password-attachments',
            'limits', 'worker', 'recovery')
record_dir = delivery / 'refresh-final-r20'
output_base = root / 'capabilities/evidence/foundation/T83-final-r20'
assert not record_dir.exists() and not output_base.exists()
cleanup_name = 'pre-r20-container-check'
cleanup = subprocess.run([sys.executable, str(delivery / 'run-gate-monotonic.py'),
    cleanup_name, 'host', 'podman', '--log-level=error', 'ps', '--format', '{{.ID}} {{.Names}} {{.Status}}'], cwd=root)
cleanup_path = delivery / 'validation' / (cleanup_name + '-result.json')
cleanup_gate = json.loads(cleanup_path.read_text())
assert cleanup.returncode == cleanup_gate['exit-code'] == 0
assert cleanup_gate['log'] == driver.reference(root, root / cleanup_gate['log']['path'])
assert not (root / cleanup_gate['log']['path']).read_text().strip(), 'Inspect owned containers before execution'
retained += [driver.reference(root, cleanup_path), cleanup_gate['log']]
record_dir.mkdir()
driver.write_json(record_dir / 'preflight.json', {
    'candidate': receipt['candidate'], 'source-freeze': driver.reference(root, frozen_path),
    'refresh-driver': driver.reference(root, Path(__file__)), 'retained-inputs': retained,
    'starting-index': driver.reference(root, authority), 'selected-obligations': selected,
    'diagnostics-exported-certifications': 0, 'reuse-of-superseded-certifications': False,
    'execution-disposition': 'New monotonic candidate; all gates and fourteen selected obligations run fresh in declared immutable images.'})


def current(expected_index):
    driver.require_staged_build(root, contract, build)
    assert driver.capture_build_inputs(root, contract) == inputs
    assert all(item == driver.reference(root, root / item['path']) for item in retained)
    assert driver.sha256(authority) == expected_index


verification = []
for name, mode, arguments in (
        ('full-verify-final-r13', 'ubuntu', ['./mvnw', '-B', '-ntp', 'verify']),
        ('jdk-matrix-final-r6', 'host', ['./scripts/verify-jdk-matrix.sh']),
        ('standalone-scale-final-r9', 'host', ['python3', '-B',
            'capabilities/evidence/T83-delivery/standalone-scale.py', 'scale-final-r9'])):
    current(index_hash)
    command = [sys.executable, str(delivery / 'run-gate-monotonic.py'), name, mode, *arguments]
    process = subprocess.run(command, cwd=root)
    gate_path = delivery / 'validation' / (name + '-result.json')
    gate = json.loads(gate_path.read_text())
    assert gate['exit-code'] == process.returncode
    assert gate['log'] == driver.reference(root, root / gate['log']['path'])
    current(index_hash)
    verification.append({'gate': name, 'exit-code': process.returncode,
        'source-staged-index-unchanged': True, 'result': driver.reference(root, gate_path),
        'monotonic-duration-seconds': gate['monotonic-duration-seconds'],
        'wall-duration-seconds': gate['wall-duration-seconds']})
    driver.write_json(delivery / 'candidate-final-r10-verification.json', verification)
    if process.returncode:
        print('Stopped at failed verification; no certification began: ' + name, flush=True)
        sys.exit(process.returncode)
    if name != 'standalone-scale-final-r9':
        count_command = [sys.executable, str(delivery / 'run-gate-monotonic.py'),
            'retain-' + name + '-counts-r20', 'host', sys.executable, '-B',
            str(delivery / 'retain-relaunch-verification-counts-r20.py'), name]
        subprocess.run(count_command, cwd=root, check=True)
        current(index_hash)

output_base.mkdir()
results = []
for obligation in selected:
    current(index_hash)
    prior = record_dir / (obligation + '-prior-index.yaml')
    shutil.copyfile(authority, prior)
    destination = output_base / obligation
    command = [sys.executable, str(delivery / 'run-gate-monotonic.py'), 'refresh-final-r20-' + obligation,
               'host', 'python3', '-B', 'scripts/t03-foundation.py', 'certify',
               destination.relative_to(root).as_posix(), '--obligation', obligation]
    started = datetime.datetime.now(datetime.timezone.utc).isoformat()
    print('Fresh candidate10 certification: ' + obligation + ' at ' + started, flush=True)
    process = subprocess.run(command, cwd=root)
    index = yaml.safe_load(authority.read_text())
    gate_path = delivery / 'validation' / ('refresh-final-r20-' + obligation + '-result.json')
    gate = json.loads(gate_path.read_text())
    assert gate['exit-code'] == process.returncode
    assert gate['log'] == driver.reference(root, root / gate['log']['path'])
    row = {'obligation': obligation, 'started': started,
        'finished': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'command': command, 'exit-code': process.returncode,
        'gate-result': driver.reference(root, gate_path), 'prior-index': driver.reference(root, prior),
        'current-index-sha256': driver.sha256(authority),
        'monotonic-duration-seconds': gate['monotonic-duration-seconds'],
        'wall-duration-seconds': gate['wall-duration-seconds'],
        'certifications': len(index['certifications']),
        'chain-records': sum(len(item['records']) for item in index['certifications'])}
    results.append(row)
    driver.write_json(record_dir / 'results.json', results)
    if process.returncode:
        assert row['current-index-sha256'] == index_hash == row['prior-index']['sha256']
        current(index_hash)
        print('Stopped at failed certification; partial observations remain unindexed: ' + obligation, flush=True)
        sys.exit(process.returncode)
    index_hash = row['current-index-sha256']
    current(index_hash)
    completed = {item['obligation'] for item in results}
    expected = {(item['id'], environment, execution)
                for item in contract['obligations'] if item['id'] in completed
                for environment in item['environments'] for execution in item['execution-profiles']}
    assert index['candidate'] == receipt['candidate']
    assert {(item['obligation'], item['environment'], item['execution-profile'])
            for item in index['certifications']} == expected
    assert all(item['configuration']['path'].startswith(
        output_base.relative_to(root).as_posix() + '/' + item['obligation'] + '/')
        for item in index['certifications'])
    chains = sum(len(item['chains']) * len(item['environments']) * len(item['execution-profiles'])
                 for item in contract['obligations'] if item['id'] in completed)
    assert row['chain-records'] == chains
assert len(index['certifications']) == 100 and results[-1]['chain-records'] == 412
print('Candidate10 freshly certifies all fourteen obligations: 100 tuples / 412 chains, including four recovery tuples / 20 chains with 180 mandatory cases in original and fresh executions.', flush=True)
