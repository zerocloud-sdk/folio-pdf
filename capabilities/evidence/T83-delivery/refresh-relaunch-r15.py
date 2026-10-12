"""Resume the authenticated-interrupted R14 at text closeout, preserving its ledger."""
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
spec = importlib.util.spec_from_file_location('t83_refresh_r15', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
build = root / 'target/foundation-0.1.0/build-inputs.json'
authority = root / 'capabilities/foundation-evidence.yaml'
frozen_path = delivery / 'source-freeze-final-r8.json'
frozen = json.loads(frozen_path.read_text())
inputs = {key: frozen[key] for key in ('inputs', 'contract-inputs')}
receipt = driver.require_staged_build(root, contract, build)
assert receipt == json.loads((delivery / 'candidate-final-r8/build-inputs.json').read_text())
assert driver.capture_build_inputs(root, contract) == inputs
prior_dir = delivery / 'refresh-final-r14'
prefix = json.loads((prior_dir / 'results.json').read_text())
assert [row['obligation'] for row in prefix] == ['transactions', 'values', 'pages', 'metadata', 'annotations']
assert all(row['exit-code'] == 0 for row in prefix)
index_hash = prefix[-1]['current-index-sha256']
assert driver.sha256(authority) == index_hash == driver.sha256(prior_dir / 'text-prior-index.yaml')
verification = json.loads((delivery / 'candidate-final-r8-verification.json').read_text())
assert [(row['gate'], row['exit-code'], row['source-staged-index-unchanged']) for row in verification] == [
    ('full-verify-final-r9', 0, True), ('jdk-matrix-final-r4', 0, True), ('standalone-scale-final-r7', 0, True)]
retained = json.loads((delivery / 'relaunch-preservation.json').read_text())['retained']
retained += frozen['superseded-candidate6'] + frozen['superseded-candidate7']
retained += [driver.reference(root, path) for path in (
    frozen_path, build, delivery / 'candidate-final-r8/build-inputs.json', Path(__file__),
    delivery / 'close-text-r14-r15.py', delivery / 'run-gate.py', delivery / 'relaunch-environment.sh',
    prior_dir / 'preflight.json', prior_dir / 'results.json', prior_dir / 'text-prior-index.yaml',
    delivery / 'candidate-final-r8-verification.json', delivery / 'validation/refresh-final-r14-text.txt')]
for row in prefix:
    retained += [row['gate-result'], row['prior-index']]
    gate = json.loads((root / row['gate-result']['path']).read_text())
    assert gate['exit-code'] == 0
    retained += [gate['log']]
for row in verification:
    retained += [row['result']]
    gate = json.loads((root / row['result']['path']).read_text())
    assert gate['exit-code'] == 0
    retained += [gate['log']]
record_dir = delivery / 'refresh-final-r15'
assert not record_dir.exists()
record_dir.mkdir()
selected = tuple(json.loads((prior_dir / 'preflight.json').read_text())['selected-obligations'])
assert selected == ('transactions', 'values', 'pages', 'metadata', 'annotations', 'text', 'images',
                    'incremental', 'password-baseline', 'password-clear-metadata', 'password-attachments',
                    'limits', 'worker', 'recovery')
driver.write_json(record_dir / 'preflight.json', {
    'started': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'candidate': receipt['candidate'], 'source-freeze': driver.reference(root, frozen_path),
    'refresh-driver': driver.reference(root, Path(__file__)), 'retained-inputs': retained,
    'starting-index': driver.reference(root, authority), 'selected-obligations': selected,
    'preserved-prefix': driver.reference(root, prior_dir / 'results.json'),
    'interruption': 'User-reported ChatGPT API 403 auth disconnect at 2026-10-09 17:35 CST; not a test failure',
    'diagnostics-exported-certifications': 0, 'reuse-of-superseded-certifications': False})


def current(expected_index):
    driver.require_staged_build(root, contract, build)
    assert driver.capture_build_inputs(root, contract) == inputs
    assert all(item == driver.reference(root, root / item['path']) for item in retained)
    assert driver.sha256(authority) == expected_index


results = []
for obligation in selected[5:]:
    current(index_hash)
    prior = prior_dir / (obligation + '-prior-index.yaml')
    if obligation == 'text':
        assert driver.sha256(prior) == index_hash
        gate_name = 'refresh-final-r14-text-closeout-r15'
        arguments = [sys.executable, '-B', str(delivery / 'close-text-r14-r15.py')]
    else:
        assert not prior.exists()
        shutil.copyfile(authority, prior)
        gate_name = 'refresh-final-r14-' + obligation
        destination = root / 'capabilities/evidence/foundation/T83-final-r14' / obligation
        assert not destination.exists()
        arguments = ['python3', '-B', 'scripts/t03-foundation.py', 'certify',
                     destination.relative_to(root).as_posix(), '--obligation', obligation]
    command = [sys.executable, str(delivery / 'run-gate.py'), gate_name, 'host', *arguments]
    started = datetime.datetime.now(datetime.timezone.utc).isoformat()
    print('Same candidate8 ' + ('sealed text closeout' if obligation == 'text' else 'fresh certification: ' + obligation)
          + ' at ' + started, flush=True)
    process = subprocess.run(command, cwd=root)
    gate_path = delivery / 'validation' / (gate_name + '-result.json')
    gate = json.loads(gate_path.read_text())
    assert gate['exit-code'] == process.returncode
    assert gate['log'] == driver.reference(root, root / gate['log']['path'])
    index = yaml.safe_load(authority.read_text())
    row = {'obligation': obligation, 'started': started,
           'finished': datetime.datetime.now(datetime.timezone.utc).isoformat(),
           'command': command, 'exit-code': process.returncode,
           'gate-result': driver.reference(root, gate_path), 'prior-index': driver.reference(root, prior),
           'current-index-sha256': driver.sha256(authority), 'certifications': len(index['certifications']),
           'chain-records': sum(len(item['records']) for item in index['certifications'])}
    if obligation == 'text':
        row['operation'] = 'Strict sealed-original collection and fresh closing environment checks'
        row['interrupted-gate-result'] = 'unavailable; no historical success fabricated'
    results.append(row)
    driver.write_json(record_dir / 'results.json', results)
    if process.returncode:
        assert row['current-index-sha256'] == index_hash == row['prior-index']['sha256']
        current(index_hash)
        print('Stopped at failed continuation; observations preserved and not indexed: ' + obligation, flush=True)
        sys.exit(process.returncode)
    index_hash = row['current-index-sha256']
    current(index_hash)
    complete = prefix + results
    completed = {item['obligation'] for item in complete}
    expected = {(item['id'], environment, execution)
                for item in contract['obligations'] if item['id'] in completed
                for environment in item['environments'] for execution in item['execution-profiles']}
    assert index['candidate'] == receipt['candidate']
    assert {(item['obligation'], item['environment'], item['execution-profile'])
            for item in index['certifications']} == expected
    assert all(item['configuration']['path'].startswith(
        'capabilities/evidence/foundation/T83-final-r14/' + item['obligation'] + '/')
        for item in index['certifications'])
    assert row['chain-records'] == sum(len(item['chains']) * len(item['environments']) * len(item['execution-profiles'])
        for item in contract['obligations'] if item['id'] in completed)
    driver.write_json(record_dir / 'completed-results.json', complete)
assert len(index['certifications']) == 100 and results[-1]['chain-records'] == 412
print('Candidate8 completes all fourteen obligations: 100 tuples / 412 chains, including four recovery tuples / 20 chains.', flush=True)
