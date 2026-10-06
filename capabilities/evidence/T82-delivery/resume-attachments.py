"""Finish the interrupted T80 refresh without rewriting or relabeling completed evidence."""
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import sys

import yaml

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
sys.path.insert(0, str(root / 'scripts'))
spec = importlib.util.spec_from_file_location('t82_attachment_resume', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
obligation = 'password-attachments'
original = root / 'capabilities/evidence/foundation/T82-final/password-attachments'
output = root / 'capabilities/evidence/foundation/T82-final-r2/password-attachments'
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
case = driver.certification_case(obligation)
declared = next(item for item in contract['obligations'] if item['id'] == obligation)
assert set(case.get('chains', driver.CHAINS)) == set(declared['chains'])
assert set(case.get('execution-profiles', ('IN_PROCESS', 'HARDENED_WORKER'))) == set(declared['execution-profiles'])
profiles = yaml.safe_load((root / contract['environments']).read_text())['profiles']
base = root / 'target/foundation-0.1.0'
build_record = base / 'build-inputs.json'
receipt = driver.require_staged_build(root, contract, build_record)
frozen = json.loads((delivery / 'source-freeze-r3.json').read_text())
assert driver.capture_build_inputs(root, contract) == {key: frozen[key] for key in ('inputs', 'contract-inputs')}
authority = root / 'capabilities/foundation-evidence.yaml'
previous_bytes = authority.read_bytes()
ledger = json.loads((delivery / 'refresh-final-r1/results.json').read_text())
assert [item['obligation'] for item in ledger] == ['transactions', 'values', 'pages', 'metadata', 'annotations',
    'text', 'images', 'incremental', 'password-baseline', 'password-clear-metadata', obligation]
assert all(item['exit-code'] == 0 for item in ledger[:-1]) and ledger[-1]['exit-code'] == 1
assert hashlib.sha256(previous_bytes).hexdigest() == ledger[-1]['current-index-sha256']
identities = {}
for line in (original / 'candidate-identity.txt').read_text().splitlines():
    for name in ('Candidate', 'Contract'):
        if line.startswith(name + ' identity: '):
            identities[name] = line.split(': ')[1]
assert set(identities) == {'Candidate', 'Contract'}
classpath = ':'.join('/workspace/' + path.relative_to(root).as_posix()
                     for path in driver.certification_classpath(root, contract, receipt))
inputs = driver.certification_inputs(root, contract, receipt, case)


def configuration(plan, environment, execution):
    return {'schema-version': 1, 'candidate-sha256': identities['Candidate'],
        'environment-sha256': environment['sha256'], 'acceptance-profile': case['profile'],
        'execution-profile': execution, 'command': plan['recorder-command'], 'java-options': plan['java-options'],
        'locale': 'en_US / C.UTF-8', 'timezone': 'UTC', 'settings': plan['settings'], 'inputs': inputs}


def verify_index(index, expected, bound_identities):
    scopes = [(item['obligation'], item['environment'], item['execution-profile']) for item in index['certifications']]
    if len(scopes) != len(set(scopes)) or set(scopes) != expected:
        raise ValueError('Incomplete or duplicate retained scope')
    empty = {**index, 'certifications': []}
    checked = driver.merge_evidence(root, index, empty, bound_identities)
    if checked['certifications'] != index['certifications']:
        raise ValueError('Retained identity or transitive evidence mismatch')
    for item in index['certifications']:
        if len(item['records']) != len(declared['chains']):
            raise ValueError('Incomplete retained chains')
        for ref in item['records']:
            record = json.loads((root / ref['path']).read_text())
            if record['producer'] != driver.certification_producers(case)[record['chain']]:
                raise ValueError('Retained producer differs from the unchanged qualified producer')
    return checked


retained = {'schema-version': 1, 'candidate': receipt['candidate'], 'environments': [], 'certifications': []}
expected_reused = set()
raw_after = []
for profile in profiles:
    major = profile['identity']['jdk-major']
    if major == 21:
        continue
    assert major in (8, 11, 17)
    environment_path = original / ('jdk' + str(major) + '-environment/environment.yaml')
    environment_ref = driver.reference(root, environment_path)
    retained['environments'].append({'profile': profile['id'], 'record': environment_ref})
    after = original / ('jdk' + str(major) + '-environment-after')
    assert (after / 'native-observation.json').is_file() and (after / 'invocation.json').is_file()
    raw_after.extend(driver.reference(root, path) for path in sorted(after.iterdir()) if path.is_file())
    for execution in declared['execution-profiles']:
        scope = original / ('jdk' + str(major) + '-' + execution.lower())
        plan = driver.execution_plan(root, scope, profile['identity']['image'],
                                    Path(os.environ['FOLIO_HARFBUZZ_HELPER']), classpath, case, execution)
        config_path = scope / 'execution.yaml'
        assert json.loads(config_path.read_text()) == configuration(plan, environment_ref, execution)
        assert json.loads((scope / 'contract-tests-command.json').read_text()) == plan['contract-tests-command']
        assert 'OK (24 tests)' in (scope / 'contract-tests.txt').read_text()
        records = [driver.reference(root, scope / (chain + '.yaml')) for chain in declared['chains']]
        retained['certifications'].append({'obligation': obligation, 'environment': profile['id'],
            'execution-profile': execution, 'configuration': driver.reference(root, config_path), 'records': records})
        expected_reused.add((obligation, profile['id'], execution))
assert len(expected_reused) == 6
verify_index(retained, expected_reused, identities)

if sys.argv[1:] == ['--check-only']:
    rejected = []
    for mutation in ('missing-scope', 'duplicate-scope', 'missing-chain', 'configuration-hash', 'record-hash', 'environment-hash', 'candidate-identity'):
        altered = copy.deepcopy(retained)
        bound = dict(identities)
        if mutation == 'missing-scope':
            altered['certifications'].pop()
        elif mutation == 'duplicate-scope':
            altered['certifications'].append(copy.deepcopy(altered['certifications'][0]))
        elif mutation == 'missing-chain':
            altered['certifications'][0]['records'].pop()
        elif mutation == 'configuration-hash':
            altered['certifications'][0]['configuration']['sha256'] = '0' * 64
        elif mutation == 'record-hash':
            altered['certifications'][0]['records'][0]['sha256'] = '0' * 64
        elif mutation == 'environment-hash':
            altered['environments'][0]['record']['sha256'] = '0' * 64
        else:
            bound['Candidate'] = '0' * 64
        try:
            verify_index(altered, expected_reused, bound)
        except ValueError:
            rejected.append(mutation)
        else:
            raise AssertionError('Mutation accepted: ' + mutation)
    print(json.dumps({'complete-retained-tuples': 6, 'chain-records': 24, 'rejected-controls': rejected}, indent=2))
    sys.exit(0)
assert not sys.argv[1:]

output.parent.mkdir(exist_ok=True)
output.mkdir()
inventory = copy.deepcopy(retained)
observed_identities = driver.candidate_identities(root, authority, inventory, output / 'candidate-identity.txt')
assert observed_identities == identities, 'Candidate/contract changed since the interrupted attempt'
helper = Path(os.environ['FOLIO_HARFBUZZ_HELPER']).resolve(strict=True)
reobserved = []
for profile in profiles:
    major = profile['identity']['jdk-major']
    print('T80 continuation environment JDK ' + str(major), flush=True)
    directory = output / ('jdk' + str(major) + '-environment')
    environment = driver.observe_environment(root, profile['identity']['image'], helper, directory, profile, base / 'harness')
    old_environment = json.loads((original / ('jdk' + str(major) + '-environment/environment.yaml')).read_text())
    assert environment == old_environment, 'Actual environment, launcher, native engine or tools changed'
    record_path = directory / 'environment.yaml'
    driver.write_json(record_path, environment)
    reobserved.append(driver.reference(root, record_path))
    if major != 21:
        continue
    environment_ref = driver.reference(root, record_path)
    inventory['environments'].append({'profile': profile['id'], 'record': environment_ref})
    for execution in declared['execution-profiles']:
        print('T80 fresh JDK21 / ' + execution, flush=True)
        scope = output / ('jdk21-' + execution.lower())
        scope.mkdir()
        plan = driver.execution_plan(root, scope, profile['identity']['image'], helper, classpath, case, execution)
        config_path = scope / 'execution.yaml'
        driver.write_json(config_path, configuration(plan, environment_ref, execution))
        driver.write_json(scope / 'contract-tests-command.json', plan['contract-tests-command'])
        driver.run_logged(plan['contract-tests-command'], scope / 'contract-tests.txt', cwd=root, timeout=case['contract-timeout'])
        assert 'OK (24 tests)' in (scope / 'contract-tests.txt').read_text()
        driver.run_logged(plan['recorder-command'], scope / 'recorder.txt', cwd=root, timeout=case['recorder-timeout'])
        reports = driver.collect_reports(root, scope / 'observations', obligation, execution)
        records = []
        for chain, report in reports.items():
            if chain == 'semantic':
                report['findings'] += [driver.reference(root, scope / 'contract-tests.txt'),
                                      driver.reference(root, scope / 'contract-tests-command.json')]
            driver.append_environment_observations(root, directory, report)
            report_file = scope / (chain + '-report.json')
            driver.write_json(report_file, report)
            record = {'schema-version': 1, 'obligation': obligation, 'acceptance-profile': case['profile'],
                'release': '0.1.0', 'candidate-sha256': identities['Candidate'], 'contract-sha256': identities['Contract'],
                'environment-sha256': environment_ref['sha256'], 'execution-configuration-sha256': driver.sha256(config_path),
                'execution-profile': execution, 'chain': chain, 'result': 'pass',
                'producer': driver.certification_producers(case)[chain],
                'configuration': driver.reference(root, root / declared['profile-contract']),
                'report': driver.reference(root, report_file), 'negative-controls': report['negative-controls']}
            path = scope / (chain + '.yaml')
            driver.write_json(path, record)
            records.append(driver.reference(root, path))
        driver.require_unchanged(root, contract, receipt['candidate'])
        driver.require_staged_build(root, contract, build_record)
        inventory['certifications'].append({'obligation': obligation, 'environment': profile['id'],
            'execution-profile': execution, 'configuration': driver.reference(root, config_path), 'records': records})
    after = output / 'jdk21-environment-after'
    assert driver.observe_environment(root, profile['identity']['image'], helper, after, profile, base / 'harness') == environment

driver.require_unchanged(root, contract, receipt['candidate'])
driver.require_staged_build(root, contract, build_record)
expected_all = {(obligation, profile['id'], execution) for profile in profiles for execution in declared['execution-profiles']}
verify_index(inventory, expected_all, identities)
assert authority.read_bytes() == previous_bytes
driver.write_json(output / 'reuse-receipt.json', {'reason': 'Original overall gate hit delivery-only 10800-second cap; original gate remains failed.',
    'original-attempt': driver.reference(root, delivery / 'validation/refresh-final-r1-password-attachments-timeout.json'),
    'staged-build': driver.reference(root, build_record), 'source-freeze': driver.reference(root, delivery / 'source-freeze-r3.json'),
    'identities': identities, 'reused-original-tuples': retained, 'original-after-environment-observations': raw_after,
    'fresh-matching-environments': reobserved, 'fresh-JDK21-tuples': 2,
    'unchanged': 'Original configurations, records, producer labels and all transitive hashes; original six tuples verified with the live strict merge validator.'})
driver.write_json(output / 'observed-index.json', inventory)
driver.write_json(output / 'identities.json', identities)
(output / 'prior-index.sha256').write_text(hashlib.sha256(previous_bytes).hexdigest() + '\n')
merged = driver.publish_index(root, output)
assert len(merged['certifications']) == 88 and sum(len(item['records']) for item in merged['certifications']) == 352
print('Completed T80: six verified unchanged original tuples plus two fresh JDK21 tuples; index 88 certifications / 352 chains.', flush=True)
