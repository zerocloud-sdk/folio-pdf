"""Finish unchanged candidate10 qualification after two preserved text failures."""
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
spec = importlib.util.spec_from_file_location('t83_refresh_r22', root / 'scripts/t03-foundation.py')
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
old_ledger = delivery / 'refresh-final-r21/results.json'
old_rows = json.loads(old_ledger.read_text())
assert len(old_rows) == 6 and [row['obligation'] for row in old_rows] == list(selected[:6])
assert all(row['exit-code'] == 0 for row in old_rows[:5]) and old_rows[-1]['exit-code'] == 1
results = old_rows[:5]
index_hash = driver.sha256(authority)
assert index_hash == old_rows[-1]['current-index-sha256'] == results[-1]['current-index-sha256']
assert results[-1]['certifications'] == 40 and results[-1]['chain-records'] == 160
verification_path = delivery / 'candidate-final-r10-verification.json'
verification = json.loads(verification_path.read_text())
assert len(verification) == 3 and all(row['exit-code'] == 0 and row['source-staged-index-unchanged']
                                     for row in verification)
review_paths = [delivery / 'reviews' / (axis + '-text-resume-r22b.md') for axis in ('standards', 'spec')]
review_verdict = 'R22 resume verdict: PASS; actionable findings: 0.'
assert re.findall(r'^R22 resume verdict: .+$',
                  'Zero actionable findings in helper A. One actionable issue remains in helper B.',
                  re.MULTILINE) != [review_verdict]
for path in review_paths:
    assert re.findall(r'^R22 resume verdict: .+$', path.read_text(), re.MULTILINE) == [review_verdict]
retained = frozen['superseded-candidate9'] + frozen['reviews'] + frozen['development-gates']
retained += [driver.reference(root, path) for path in (
    frozen_path, delivery / 'candidate-final-r10/build-inputs.json', Path(__file__).resolve(),
    delivery / 'complete-text-relaunch-r22.py', delivery / 'run-gate-monotonic.py',
    delivery / 'relaunch-environment.sh', old_ledger, delivery / 'refresh-final-r21/preflight.json',
    delivery / 'refresh-final-r20/results.json', verification_path,
    delivery / 'failed-text-certification-r20.json', delivery / 'failed-text-certification-r20.tsv.gz',
    delivery / 'failed-text-certification-r21.json', delivery / 'failed-text-certification-r21.tsv.gz',
    delivery / 'validation/text-timeout-diagnostic-r22-result.json',
    delivery / 'text-timeout-diagnostics-r22/preflight.json',
    delivery / 'text-timeout-diagnostics-r22/results.json', *review_paths)]
retained += [row[key] for row in old_rows for key in ('gate-result', 'prior-index')]
retained += [row['result'] for row in verification]
record_dir = delivery / 'refresh-final-r22'
old_base = root / 'capabilities/evidence/foundation/T83-final-r20'
old_text = root / 'capabilities/evidence/foundation/T83-final-r21/text'
new_text = root / 'capabilities/evidence/foundation/T83-final-r22/text'
assert not record_dir.exists() and not new_text.parent.exists()
assert all(not (old_base / name).exists() for name in selected[6:])
record_dir.mkdir()
starting = record_dir / 'starting-index.yaml'
shutil.copyfile(authority, starting)
retained.append(driver.reference(root, starting))
driver.write_json(record_dir / 'preflight.json', {
    'candidate': receipt['candidate'], 'source-freeze': driver.reference(root, frozen_path),
    'refresh-driver': driver.reference(root, Path(__file__).resolve()), 'retained-inputs': retained,
    'starting-index': driver.reference(root, starting), 'selected-obligations': selected,
    'current-candidate-passes-retained': [row['obligation'] for row in results],
    'preserved-failed-text-attempts': [driver.reference(root, delivery / ('failed-text-certification-r' + str(n) + '.json'))
                                     for n in (20, 21)],
    'reuse-of-superseded-certifications': False,
    'execution-disposition': 'Keep five actual candidate10 R20 passes. Complete text by qualifying seven immutable complete candidate10 R21 originals plus one fresh JDK21 HARDENED_WORKER recording. Certify the remaining eight obligations fresh in unused R20 destinations. Source, contracts, stage and successful full/matrix/scale gates remain unchanged; no timeout or qualification limit is relaxed.'})
driver.write_json(record_dir / 'results.json', results)


def current(expected_index):
    assert driver.require_staged_build(root, contract, build) == receipt
    assert driver.capture_build_inputs(root, contract) == inputs
    assert all(driver.reference(root, root / item['path']) == item for item in retained)
    assert driver.sha256(authority) == expected_index


def expected_scope(row):
    if row['obligation'] != 'text':
        return old_base / row['obligation']
    return new_text if row['environment'].endswith('jdk21') and row['execution-profile'] == 'HARDENED_WORKER' else old_text


for obligation in selected[5:]:
    current(index_hash)
    prior = record_dir / (obligation + '-prior-index.yaml')
    shutil.copyfile(authority, prior)
    gate_name = 'refresh-final-r22-' + obligation
    if obligation == 'text':
        args = ['python3', '-B', str(delivery / 'complete-text-relaunch-r22.py')]
    else:
        destination = old_base / obligation
        assert not destination.exists()
        args = ['python3', '-B', 'scripts/t03-foundation.py', 'certify',
                destination.relative_to(root).as_posix(), '--obligation', obligation]
    command = [sys.executable, str(delivery / 'run-gate-monotonic.py'), gate_name, 'host', *args]
    started = datetime.datetime.now(datetime.timezone.utc).isoformat()
    print('Unchanged candidate10 qualification: ' + obligation + ' at ' + started, flush=True)
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
        print('Stopped at failed qualification; originals preserved and incomplete tuple unindexed.', flush=True)
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
    assert all(item['configuration']['path'].startswith(expected_scope(item).relative_to(root).as_posix() + '/')
               for item in index['certifications'])
    chains = sum(len(item['chains']) * len(item['environments']) * len(item['execution-profiles'])
                 for item in contract['obligations'] if item['id'] in completed)
    assert row['chain-records'] == chains
assert len(index['certifications']) == 100 and results[-1]['chain-records'] == 412
print('Candidate10 freshly qualifies all fourteen obligations: 100 tuples/412 chains. Original failed attempts remain failed and byte-bound.', flush=True)
