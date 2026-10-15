"""Resume intact deferred obligations and retry incomplete runs in fresh directories."""
import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time

import yaml

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
sys.path.insert(0, str(root / 'scripts'))
import t05_foundation_reports

module = t05_foundation_reports.foundation()
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--state', required=True)
parser.add_argument('--jobs', type=int, default=2, choices=(1, 2))
parser.add_argument('--base', type=Path, default=Path('capabilities/evidence/foundation/T84-final-r1'))
parser.add_argument('--new-candidate', action='store_true', help='Create a fresh final tree only after source identity changed')
args = parser.parse_args()
assert args.state.replace('-', '').isalnum()
state_path = delivery / (args.state + '.json')
assert not state_path.exists(), 'Resume state must be fresh'
base = (root / args.base).resolve()
base.relative_to(root / 'capabilities/evidence/foundation')
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
receipt = module.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
if args.new_candidate:
    assert not base.exists(), 'A new candidate requires a fresh final tree'
    previous_freeze = json.loads((delivery / 'development/superseded-candidate-r1/source-freeze.json').read_text())
    assert receipt['candidate']['inputs'] != previous_freeze['candidate-inputs'], 'Do not repeat a still-valid candidate'
    base.mkdir()
else:
    assert base.is_dir(), 'A resumed final tree must exist'
authority = root / 'capabilities/foundation-evidence.yaml'
prior_bytes = authority.read_bytes()
prior_hash = hashlib.sha256(prior_bytes).hexdigest()
previous = yaml.safe_load(prior_bytes)
selected = list(dict.fromkeys(item['obligation'] for item in previous['certifications']))
assert selected == ['transactions', 'values', 'pages', 'metadata', 'annotations', 'text', 'images', 'incremental',
                    'password-baseline', 'password-clear-metadata', 'password-attachments', 'limits', 'worker', 'recovery']
requirements = {item['id']: set(item['dependencies']) for item in contract['obligations'] if item['id'] in selected}
state = {'candidate': receipt['candidate'], 'prior-index-sha256': prior_hash, 'jobs': args.jobs,
         'final-base': base.relative_to(root).as_posix(),
         'dependencies': {name: sorted(value) for name, value in requirements.items()},
         'directories': {}, 'completed': {}, 'running': {}, 'failed': {},
         'schedule': 'Text and password retries run alone at original bounds; then independent siblings may run in disjoint directories.'}
directories = {}
for name in selected:
    matches = sorted(base.glob(name + '*'))
    complete = []
    for directory in matches:
        if directory.name != name and not directory.name.startswith(name + '-r'):
            continue
        if not (directory / 'observed-index.json').is_file():
            continue
        observed = json.loads((directory / 'observed-index.json').read_text())
        assert observed['candidate'] == receipt['candidate']
        assert (directory / 'prior-index.sha256').read_text().strip() == prior_hash
        declared = next(item for item in contract['obligations'] if item['id'] == name)
        expected = {(environment, execution) for environment in declared['environments'] for execution in declared['execution-profiles']}
        assert {(item['environment'], item['execution-profile']) for item in observed['certifications']} == expected
        assert all(item['obligation'] == name for item in observed['certifications'])
        assert all(module.sha256(root / record['path']) == record['sha256']
                   for item in observed['certifications'] for record in item['records'])
        label = directory.name if base.name == 'T84-final-r1' else base.name + '-' + directory.name
        result_path = delivery / 'validation' / ('refresh-' + label + '-result.json')
        result = json.loads(result_path.read_text())
        assert result['exit-code'] == 0 and module.sha256(root / result['log']) == result['log-sha256']
        complete.append((directory, result))
    assert len(complete) <= 1, 'Do not repeat an already complete obligation'
    if complete:
        directory, result = complete[0]
        directories[name] = directory
        state['completed'][name] = result
        print('Adopted intact ' + name + ': ' + directory.relative_to(root).as_posix(), flush=True)
    else:
        directory = base / name
        retry = 1
        while directory.exists():
            retry += 1
            directory = base / (name + '-r' + str(retry))
        directories[name] = directory
    state['directories'][name] = directories[name].relative_to(root).as_posix()
original_five = {'transactions', 'values', 'pages', 'metadata', 'annotations'}
if base.name == 'T84-final-r1':
    assert original_five <= state['completed'].keys()
assert all(requirements[name] <= state['completed'].keys() for name in state['completed'])
state['adopted-original-successes'] = sorted(state['completed'])
module.write_json(state_path, state)


def execute(name):
    directory = directories[name]
    command = [sys.executable, '-B', 'scripts/t03-foundation.py', 'certify', directory.relative_to(root).as_posix(),
               '--obligation', name, '--defer-index']
    label = directory.name if base.name == 'T84-final-r1' else base.name + '-' + directory.name
    log = delivery / 'validation' / ('refresh-' + label + '.txt')
    result_path = log.with_name(log.stem + '-result.json')
    assert not log.exists() and not result_path.exists()
    start = time.monotonic()
    try:
        with log.open('wb') as stream:
            status = subprocess.run(command, cwd=root, stdout=stream, stderr=subprocess.STDOUT, timeout=36000).returncode
    except subprocess.TimeoutExpired:
        status = 124
    record = {'command': command, 'exit-code': status, 'elapsed-monotonic-seconds': time.monotonic() - start,
              'timeout-monotonic-seconds': 36000, 'log': log.relative_to(root).as_posix(),
              'log-sha256': module.sha256(log), 'obligation': name}
    module.write_json(result_path, record)
    return record


priority = ['transactions', 'values', 'pages', 'metadata', 'annotations', 'text', 'password-baseline', 'password-attachments', 'password-clear-metadata',
            'images', 'incremental', 'limits', 'worker', 'recovery']
started = time.monotonic()
pending = set(selected) - state['completed'].keys()
active = {}
with concurrent.futures.ThreadPoolExecutor(max_workers=args.jobs) as executor:
    while pending or active:
        module.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
        assert authority.read_bytes() == prior_bytes
        isolated = next((name for name in ('text', 'password-baseline') if name not in state['completed']), None)
        allowed = ['transactions', 'values', 'pages', 'metadata', 'annotations'] if not original_five <= state['completed'].keys() else [isolated] if isolated else priority
        slots = 1 if isolated else args.jobs
        for name in allowed:
            if len(active) >= slots or state['failed']:
                break
            if name in pending and requirements[name] <= state['completed'].keys():
                pending.remove(name)
                state['running'][name] = {'started-monotonic-seconds': time.monotonic() - started,
                                           'prerequisites-completed': sorted(requirements[name])}
                active[executor.submit(execute, name)] = name
                print('Started ' + name + ' in fresh ' + directories[name].name, flush=True)
        module.write_json(state_path, state)
        assert active, 'No runnable obligation; inspect dependencies or retained failure'
        done, _ = concurrent.futures.wait(active, timeout=20, return_when=concurrent.futures.FIRST_COMPLETED)
        for future in done:
            name = active.pop(future)
            record = future.result()
            state['running'].pop(name)
            state['failed' if record['exit-code'] else 'completed'][name] = record
            module.write_json(state_path, state)
            print(('FAILED ' if record['exit-code'] else 'Completed ') + name + ' in '
                  + format(record['elapsed-monotonic-seconds'], '.3f') + ' monotonic seconds', flush=True)
        if state['failed']:
            # Let any already running independent sibling finish and retain
            # its result, but start no successor after the first failure.
            for future, name in list(active.items()):
                record = future.result()
                state['running'].pop(name)
                state['failed' if record['exit-code'] else 'completed'][name] = record
                module.write_json(state_path, state)
            raise RuntimeError('Inherited observation failed; fresh attempt and prior successes retained')

assert authority.read_bytes() == prior_bytes
module.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
combined = base / 'combined-index'
combined.mkdir()
merged = {'schema-version': 1, 'candidate': receipt['candidate'], 'environments': [], 'certifications': []}
environments = {}
identities = None
scopes = set()
for name in selected:
    directory = directories[name]
    fresh = json.loads((directory / 'observed-index.json').read_text())
    current_identities = json.loads((directory / 'identities.json').read_text())
    assert fresh['candidate'] == receipt['candidate'] and (directory / 'prior-index.sha256').read_text().strip() == prior_hash
    if identities is None:
        identities = current_identities
    assert identities == current_identities
    for observed in fresh['environments']:
        profile = observed['profile']
        value = json.loads((root / observed['record']['path']).read_text())
        if profile in environments:
            assert environments[profile] == value, 'Observed environment changed across obligations'
        else:
            environments[profile] = value
            merged['environments'].append(observed)
    for item in fresh['certifications']:
        key = (name, item['environment'], item['execution-profile'])
        assert item['obligation'] == name and key not in scopes
        scopes.add(key)
        merged['certifications'].append(item)
assert len(merged['certifications']) == 100 and sum(len(item['records']) for item in merged['certifications']) == 412
module.write_json(combined / 'observed-index.json', merged)
module.write_json(combined / 'identities.json', identities)
(combined / 'prior-index.sha256').write_text(prior_hash + '\n')
module.publish_index(root, combined)
module.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
state.update({'result': 'pass', 'elapsed-monotonic-seconds': time.monotonic() - started,
              'combined-index': combined.relative_to(root).as_posix()})
module.write_json(state_path, state)
print('PASS: 100 inherited tuples / 412 chains; valid completed scopes retained; strict combined index installed', flush=True)
