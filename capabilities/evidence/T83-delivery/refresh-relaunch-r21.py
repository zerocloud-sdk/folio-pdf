"""Continue unchanged candidate10 after preserving R20's text CPU-guard failure."""
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
spec = importlib.util.spec_from_file_location('t83_refresh_r21', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
authority = root / 'capabilities/foundation-evidence.yaml'
build = root / 'target/foundation-0.1.0/build-inputs.json'
frozen_path = delivery / 'source-freeze-final-r10.json'
frozen = json.loads(frozen_path.read_text())
inputs = {key: frozen[key] for key in ('inputs', 'contract-inputs')}
receipt = driver.require_staged_build(root, contract, build)
assert receipt == json.loads((delivery / 'candidate-final-r10/build-inputs.json').read_text())
assert driver.capture_build_inputs(root, contract) == inputs
selected = ('transactions', 'values', 'pages', 'metadata', 'annotations', 'text', 'images',
            'incremental', 'password-baseline', 'password-clear-metadata', 'password-attachments',
            'limits', 'worker', 'recovery')
original_ledger = delivery / 'refresh-final-r20/results.json'
original = json.loads(original_ledger.read_text())
assert len(original) == 6 and [row['obligation'] for row in original] == list(selected[:6])
assert all(row['exit-code'] == 0 for row in original[:5]) and original[-1]['exit-code'] == 1
results = original[:5]
index_hash = driver.sha256(authority)
assert index_hash == results[-1]['current-index-sha256'] == original[-1]['current-index-sha256']
assert results[-1]['certifications'] == 40 and results[-1]['chain-records'] == 160
verification = json.loads((delivery / 'candidate-final-r10-verification.json').read_text())
assert len(verification) == 3 and all(row['exit-code'] == 0 and row['source-staged-index-unchanged'] for row in verification)
diagnostics = json.loads((delivery / 't13-cpu-diagnostics-r21/results.json').read_text())
assert len(diagnostics) == 20 and all(row['exit-code'] == row['stderr-bytes'] == 0
        and row['result'] == 'fail' and row['rule'] == 'content-properties' for row in diagnostics)
preservation = json.loads((delivery / 'failed-text-certification-r20.json').read_text())
assert preservation['certifications-exported'] == 0 and preservation['index-unchanged']
assert preservation['current-index']['sha256'] == index_hash
retained = frozen['superseded-candidate9'] + frozen['reviews'] + frozen['development-gates']
retained += [driver.reference(root, path) for path in (
    frozen_path, delivery / 'candidate-final-r10/build-inputs.json', Path(__file__).resolve(),
    delivery / 'run-gate-monotonic.py', delivery / 'relaunch-environment.sh', original_ledger,
    delivery / 'refresh-final-r20/preflight.json', delivery / 'candidate-final-r10-verification.json',
    delivery / 'failed-text-certification-r20.json', delivery / 'failed-text-certification-r20.tsv.gz',
    delivery / 'validation/t13-cpu-diagnostic-r21-result.json',
    delivery / 't13-cpu-diagnostics-r21/preflight.json', delivery / 't13-cpu-diagnostics-r21/results.json')]
retained += [row[key] for row in original for key in ('gate-result', 'prior-index')]
retained += [row['result'] for row in verification]
record_dir = delivery / 'refresh-final-r21'
fresh_text = root / 'capabilities/evidence/foundation/T83-final-r21/text'
original_base = root / 'capabilities/evidence/foundation/T83-final-r20'
assert not record_dir.exists() and not fresh_text.parent.exists()
assert all(not (original_base / name).exists() for name in selected[6:])
cleanup_name = 'pre-r21-container-check'
cleanup = subprocess.run([sys.executable, str(delivery / 'run-gate-monotonic.py'), cleanup_name,
    'host', 'podman', '--log-level=error', 'ps', '--format', '{{.ID}} {{.Names}} {{.Status}}'], cwd=root)
cleanup_path = delivery / 'validation' / (cleanup_name + '-result.json')
cleanup_gate = json.loads(cleanup_path.read_text())
assert cleanup.returncode == cleanup_gate['exit-code'] == 0
assert cleanup_gate['log'] == driver.reference(root, root / cleanup_gate['log']['path'])
assert not (root / cleanup_gate['log']['path']).read_text().strip()
retained += [driver.reference(root, cleanup_path), cleanup_gate['log']]
record_dir.mkdir()
fresh_text.parent.mkdir()
starting_index = record_dir / 'starting-index.yaml'
shutil.copyfile(authority, starting_index)
assert driver.sha256(starting_index) == index_hash
retained.append(driver.reference(root, starting_index))
driver.write_json(record_dir / 'preflight.json', {
    'candidate': receipt['candidate'], 'source-freeze': driver.reference(root, frozen_path),
    'refresh-driver': driver.reference(root, Path(__file__).resolve()), 'retained-inputs': retained,
    'starting-index': driver.reference(root, starting_index), 'selected-obligations': selected,
    'current-candidate-passes-retained': [row['obligation'] for row in results],
    'preserved-failed-attempt': driver.reference(root, delivery / 'failed-text-certification-r20.json'),
    'diagnostics-exported-certifications': 0, 'reuse-of-superseded-certifications': False,
    'execution-disposition': 'Retain five successful fresh candidate10 R20 certifications without resealing; rerun text entirely in new R21 scope and run remaining eight obligations fresh in unused R20 destinations. Source, contracts, staged artifacts and passed full/matrix/scale verification are unchanged.'})
driver.write_json(record_dir / 'results.json', results)


def current(expected_index):
    driver.require_staged_build(root, contract, build)
    assert driver.capture_build_inputs(root, contract) == inputs
    assert all(item == driver.reference(root, root / item['path']) for item in retained)
    assert driver.sha256(authority) == expected_index


def destination_for(obligation):
    return fresh_text if obligation == 'text' else original_base / obligation


for obligation in selected[5:]:
    current(index_hash)
    prior = record_dir / (obligation + '-prior-index.yaml')
    shutil.copyfile(authority, prior)
    destination = destination_for(obligation)
    assert not destination.exists()
    gate_name = 'refresh-final-r21-' + obligation
    command = [sys.executable, str(delivery / 'run-gate-monotonic.py'), gate_name,
               'host', 'python3', '-B', 'scripts/t03-foundation.py', 'certify',
               destination.relative_to(root).as_posix(), '--obligation', obligation]
    started = datetime.datetime.now(datetime.timezone.utc).isoformat()
    print('Fresh unchanged candidate10 certification: ' + obligation + ' at ' + started, flush=True)
    process = subprocess.run(command, cwd=root)
    index = yaml.safe_load(authority.read_text())
    gate_path = delivery / 'validation' / (gate_name + '-result.json')
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
        raise SystemExit(process.returncode)
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
        destination_for(item['obligation']).relative_to(root).as_posix() + '/')
        for item in index['certifications'])
    chains = sum(len(item['chains']) * len(item['environments']) * len(item['execution-profiles'])
                 for item in contract['obligations'] if item['id'] in completed)
    assert row['chain-records'] == chains
assert len(index['certifications']) == 100 and results[-1]['chain-records'] == 412
print('Candidate10 freshly certifies all fourteen obligations: 100 tuples/412 chains. Failed R20 text and diagnostics remain unindexed and preserved.', flush=True)
