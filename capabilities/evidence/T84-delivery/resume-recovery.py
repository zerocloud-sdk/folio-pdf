"""Finish interrupted T22 collection without repeating completed tuples."""
import hashlib
import json
import os
from pathlib import Path
import sys

import yaml

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
sys.path.insert(0, str(root / 'scripts'))
import t05_foundation_reports
from t13_foundation_reports import RetainedFiles

module = t05_foundation_reports.foundation()
state = json.loads((delivery / 'refresh-state-candidate-r2-r3.json').read_text())
output = root / state['directories']['recovery']
assert output == root / 'capabilities/evidence/foundation/T84-final-r2/recovery'
assert set(state['completed']) == set(state['directories']) - {'recovery'}
assert set(state['running']) == {'recovery'} and not state['failed']
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
build = root / 'target/foundation-0.1.0/build-inputs.json'
receipt = module.require_staged_build(root, contract, build)
assert receipt['candidate'] == state['candidate']
case = module.certification_case('recovery')
declared = next(item for item in contract['obligations'] if item['id'] == 'recovery')
assert list(case['execution-profiles']) == declared['execution-profiles'] == ['HARDENED_WORKER']
authority = root / 'capabilities/foundation-evidence.yaml'
prior = authority.read_bytes()
assert hashlib.sha256(prior).hexdigest() == state['prior-index-sha256']
identities = json.loads((delivery / 'source-freeze.json').read_text())['final-identities']
profiles = yaml.safe_load((root / contract['environments']).read_text())['profiles']
cp = ':'.join('/workspace/' + path.relative_to(root).as_posix()
              for path in module.certification_classpath(root, contract, receipt))
inputs = module.certification_inputs(root, contract, receipt, case)
helper = Path(os.environ['FOLIO_HARFBUZZ_HELPER']).resolve(strict=True)
inventory = {'schema-version': 1, 'candidate': receipt['candidate'], 'environments': [], 'certifications': []}
retained = []
excluded = []
proof_path = output / 'resume-run3.json'
assert not proof_path.exists() and not (output / 'observed-index.json').exists()


def check_references(value):
    if isinstance(value, dict):
        if set(value) == {'path', 'sha256'}:
            path = root / value['path']
            path.resolve(strict=True).relative_to(root)
            assert module.sha256(path) == value['sha256'], 'Changed completed finding: ' + str(path)
        else:
            for child in value.values():
                check_references(child)
    elif isinstance(value, list):
        for child in value:
            check_references(child)


for profile in profiles:
    major = profile['identity']['jdk-major']
    directory = output / ('jdk' + str(major) + '-environment')
    environment_path = directory / 'environment.yaml'
    if major == 21:
        assert not directory.exists()
        print('Observe fresh recovery JDK 21 environment', flush=True)
        environment = module.observe_environment(root, profile['identity']['image'], helper, directory, profile,
                                                 root / 'target/foundation-0.1.0/harness')
        module.write_json(environment_path, environment)
    else:
        environment = json.loads(environment_path.read_text())
    environment_ref = module.reference(root, environment_path)
    inventory['environments'].append({'profile': profile['id'], 'record': environment_ref})
    scope = output / ('jdk' + str(major) + '-hardened_worker')
    plan = module.execution_plan(root, scope, profile['identity']['image'], helper, cp, case, 'HARDENED_WORKER')
    config = {'schema-version': 1, 'candidate-sha256': identities['Candidate'],
              'environment-sha256': environment_ref['sha256'], 'acceptance-profile': case['profile'],
              'execution-profile': 'HARDENED_WORKER', 'command': plan['recorder-command'],
              'java-options': plan['java-options'], 'locale': 'en_US / C.UTF-8', 'timezone': 'UTC',
              'settings': plan['settings'], 'inputs': inputs}
    if major == 21:
        assert not scope.exists()
        scope.mkdir()
        module.write_json(scope / 'execution.yaml', config)
        module.write_json(scope / 'contract-tests-command.json', plan['contract-tests-command'])
        print('Run recovery JDK 21 HARDENED_WORKER mandatory contracts', flush=True)
        module.run_logged(plan['contract-tests-command'], scope / 'contract-tests.txt', cwd=root,
                          timeout=case['contract-timeout'])
        assert 'OK (180 tests)' in (scope / 'contract-tests.txt').read_text()
        module.run_logged(plan['recorder-command'], scope / 'recorder.txt', cwd=root,
                          timeout=case['recorder-timeout'])
    else:
        assert json.loads((scope / 'execution.yaml').read_text()) == config
        assert json.loads((scope / 'contract-tests-command.json').read_text()) == plan['contract-tests-command']
        assert 'OK (180 tests)' in (scope / 'contract-tests.txt').read_text()
    records = []
    if major in (8, 11):
        for chain in case['chains']:
            path = scope / (chain + '.yaml')
            record = json.loads(path.read_text())
            assert record['candidate-sha256'] == identities['Candidate']
            assert record['contract-sha256'] == identities['Contract']
            assert record['environment-sha256'] == environment_ref['sha256']
            assert record['execution-configuration-sha256'] == module.sha256(scope / 'execution.yaml')
            assert record['chain'] == chain and record['result'] == 'pass'
            assert record['obligation'] == 'recovery' and record['execution-profile'] == 'HARDENED_WORKER'
            check_references(record)
            check_references(json.loads((root / record['report']['path']).read_text()))
            records.append(module.reference(root, path))
        RetainedFiles(scope / 'observations').verify()
        retained.extend(records)
        print('Preserved completed recovery JDK ' + str(major) + ': five original chain records', flush=True)
    else:
        assert not any(scope.glob('*-report.json')), 'Do not replace a completed tuple'
        # The interrupted collection has no completed chain records. Keep its
        # partial comparisons intact and use the unchanged strict live collector.
        if major == 17:
            for previous in sorted(scope.glob('live-collection-*')):
                assert previous.is_dir()
                excluded.append(previous.relative_to(root).as_posix())
        print('Strict fresh recovery replay/collection JDK ' + str(major), flush=True)
        reports = module.collect_reports(root, scope / 'observations', 'recovery', 'HARDENED_WORKER')
        for chain, report in reports.items():
            if chain == 'semantic':
                report['findings'] += [module.reference(root, scope / 'contract-tests.txt'),
                                       module.reference(root, scope / 'contract-tests-command.json')]
            module.append_environment_observations(root, directory, report)
            module.write_json(scope / (chain + '-report.json'), report)
            record = {'schema-version': 1, 'obligation': 'recovery', 'acceptance-profile': case['profile'],
                      'release': '0.1.0', 'candidate-sha256': identities['Candidate'],
                      'contract-sha256': identities['Contract'], 'environment-sha256': environment_ref['sha256'],
                      'execution-configuration-sha256': module.sha256(scope / 'execution.yaml'),
                      'execution-profile': 'HARDENED_WORKER', 'chain': chain, 'result': 'pass',
                      'producer': module.certification_producers(case)[chain],
                      'configuration': module.reference(root, root / declared['profile-contract']),
                      'report': module.reference(root, scope / (chain + '-report.json')),
                      'negative-controls': report['negative-controls']}
            path = scope / (chain + '.yaml')
            module.write_json(path, record)
            records.append(module.reference(root, path))
    inventory['certifications'].append({'obligation': 'recovery', 'environment': profile['id'],
        'execution-profile': 'HARDENED_WORKER', 'configuration': module.reference(root, scope / 'execution.yaml'),
        'records': records})
    if major in (17, 21):
        after = output / ('jdk' + str(major) + '-environment-after')
        assert not after.exists()
        assert module.observe_environment(root, profile['identity']['image'], helper, after, profile,
            root / 'target/foundation-0.1.0/harness') == environment, 'Environment changed during recovery'
    else:
        after = output / ('jdk' + str(major) + '-environment-after')
        assert after.is_dir()
    module.require_staged_build(root, contract, build)
    assert authority.read_bytes() == prior
assert len(inventory['certifications']) == 4 and sum(len(item['records']) for item in inventory['certifications']) == 20
module.write_json(output / 'observed-index.json', inventory)
module.write_json(output / 'identities.json', identities)
(output / 'prior-index.sha256').write_text(hashlib.sha256(prior).hexdigest() + '\n')
module.write_json(proof_path, {'result': 'pass', 'candidate-identities': identities,
    'preserved-completed-records': retained, 'excluded-interrupted-replays': excluded,
    'resumed-original-tuple': 'jdk17-hardened_worker', 'fresh-tuple': 'jdk21-hardened_worker',
    'certification-tuples': 4, 'chain-records': 20,
    'scope': 'Recovery HARDENED_WORKER only; mandatory tests also exercise both profiles; Facade IN_PROCESS.'})
print('PASS: recovery four tuples / twenty chains; JDK 8/11 original records preserved', flush=True)
