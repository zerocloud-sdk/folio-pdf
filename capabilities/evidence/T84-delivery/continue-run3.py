"""Resume remaining gates after recovery; preserve completed certifications."""
import hashlib
import json
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
state_path = delivery / 'refresh-state-candidate-r2-r3.json'
recovery_result = delivery / 'validation/recovery-resume-run3-r3-result.json'


def proof(path):
    value = json.loads(path.read_text())
    assert value['exit-code'] == 0
    assert module.sha256(root / value['log']) == value['log-sha256']
    return value


def combine():
    state = json.loads(state_path.read_text())
    assert set(state['running']) == {'recovery'} and not state['failed']
    assert set(state['completed']) == set(state['directories']) - {'recovery'}
    completed = proof(recovery_result)
    contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
    receipt = module.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
    assert receipt['candidate'] == state['candidate']
    authority = root / 'capabilities/foundation-evidence.yaml'
    prior = authority.read_bytes()
    assert hashlib.sha256(prior).hexdigest() == state['prior-index-sha256']
    combined = root / state['final-base'] / 'combined-index'
    assert not combined.exists()
    merged = {'schema-version': 1, 'candidate': receipt['candidate'], 'environments': [], 'certifications': []}
    environments = {}
    identities = None
    scopes = set()
    for name, location in state['directories'].items():
        directory = root / location
        observed = json.loads((directory / 'observed-index.json').read_text())
        current_identities = json.loads((directory / 'identities.json').read_text())
        assert observed['candidate'] == receipt['candidate']
        assert (directory / 'prior-index.sha256').read_text().strip() == state['prior-index-sha256']
        if identities is None:
            identities = current_identities
        assert identities == current_identities
        declared = next(item for item in contract['obligations'] if item['id'] == name)
        expected = {(environment, execution) for environment in declared['environments']
                    for execution in declared['execution-profiles']}
        assert {(item['environment'], item['execution-profile']) for item in observed['certifications']} == expected
        for environment in observed['environments']:
            key = environment['profile']
            value = json.loads((root / environment['record']['path']).read_text())
            assert module.sha256(root / environment['record']['path']) == environment['record']['sha256']
            if key in environments:
                assert environments[key] == value, 'Actual environment changed across obligations'
            else:
                environments[key] = value
                merged['environments'].append(environment)
        for item in observed['certifications']:
            key = (name, item['environment'], item['execution-profile'])
            assert item['obligation'] == name and key not in scopes
            assert {json.loads((root / record['path']).read_text())['chain'] for record in item['records']} == set(declared['chains'])
            assert len(item['records']) == len(declared['chains'])
            assert all(module.sha256(root / record['path']) == record['sha256'] for record in item['records'])
            scopes.add(key)
            merged['certifications'].append(item)
        if name != 'recovery':
            proof(delivery / 'validation' / (Path(state['completed'][name]['log']).stem + '-result.json'))
    assert identities == json.loads((delivery / 'source-freeze.json').read_text())['final-identities']
    assert len(merged['certifications']) == 100 and sum(len(item['records']) for item in merged['certifications']) == 412
    assert authority.read_bytes() == prior
    combined.mkdir()
    module.write_json(combined / 'observed-index.json', merged)
    module.write_json(combined / 'identities.json', identities)
    (combined / 'prior-index.sha256').write_text(state['prior-index-sha256'] + '\n')
    module.publish_index(root, combined)
    module.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
    # Save the interrupted scheduler state before recording its successful resume.
    saved = delivery / 'development/refresh-state-before-run3-completion.json'
    assert not saved.exists()
    saved.write_bytes(state_path.read_bytes())
    state['completed']['recovery'] = completed
    state['running'].pop('recovery')
    state['result'] = 'pass'
    state['combined-index'] = combined.relative_to(root).as_posix()
    state['run3-resume-proof'] = module.reference(root, root / state['directories']['recovery'] / 'resume-run3.json')
    module.write_json(state_path, state)
    print('PASS: 100 inherited tuples / 412 chains; all completed scopes preserved', flush=True)


def finish():
    pipeline_path = delivery / 'development/continue-run3-state.json'
    assert not pipeline_path.exists()
    started = time.monotonic()
    pipeline = {'phase': 'waiting-for-recovery', 'completed': [], 'result': 'running'}

    def record():
        pipeline['elapsed-monotonic-seconds'] = time.monotonic() - started
        module.write_json(pipeline_path, pipeline)

    def run(label, command, expected=0, timeout=36000):
        pipeline['phase'] = label
        record()
        print('Started ' + label, flush=True)
        result = subprocess.run([sys.executable, '-B', str(delivery / 'run-validation.py'), label,
            '--timeout', str(timeout), '--'] + command, cwd=root).returncode
        assert result == expected, (label, result)
        pipeline['completed'].append(label)
        record()

    try:
        record()
        while not recovery_result.exists():
            assert time.monotonic() - started < 18000, 'Recovery result did not arrive'
            time.sleep(10)
        proof(recovery_result)
        run('inherited-refresh-run3', [sys.executable, '-B', str(Path(__file__)), 'combine'])
        run('provider-certification-final-r2', [sys.executable, '-B', 'scripts/t03-foundation.py', 'certify',
            'capabilities/evidence/foundation/T84-final-r2/providers', '--obligation', 'providers'])
        for action in ('generate', 'validate', 'check'):
            run('inventory-' + action + '-final', ['./scripts/inventory', action])
        run('readiness-final', ['./scripts/inventory', 'readiness'], expected=1)
        run('readiness-analysis-run3', [sys.executable, '-B', str(delivery / 'analyse-readiness.py')])
        pipeline['phase'] = 'awaiting-final-review-and-publication-audits'
        pipeline['result'] = 'pass'
        record()
    except BaseException as failure:
        pipeline['result'] = 'fail'
        pipeline['failure'] = type(failure).__name__ + ': ' + str(failure)
        record()
        raise


assert len(sys.argv) == 2 and sys.argv[1] in ('combine', 'finish')
combine() if sys.argv[1] == 'combine' else finish()
