"""Adopt seven strictly verified current tuples and freshly retry the eighth."""
import argparse
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

module = t05_foundation_reports.foundation()
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
source = (root / args.source).resolve(strict=True)
output = (root / args.output).resolve()
final_base = root / json.loads((delivery / 'source-freeze.json').read_text())['final-evidence-base']
source.relative_to(final_base)
output.relative_to(final_base)
assert source != output and not output.exists()
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
build = root / 'target/foundation-0.1.0/build-inputs.json'
receipt = module.require_staged_build(root, contract, build)
case = module.certification_case('annotations')
declared = next(item for item in contract['obligations'] if item['id'] == 'annotations')
assert set(declared['chains']) == set(module.CHAINS)
assert set(declared['execution-profiles']) == {'IN_PROCESS', 'HARDENED_WORKER'}
authority = root / 'capabilities/foundation-evidence.yaml'
prior = authority.read_bytes()
output.mkdir()
inventory = {'schema-version': 1, 'candidate': receipt['candidate'], 'environments': [], 'certifications': []}
identities = module.candidate_identities(root, authority, inventory, output / 'candidate-identity.txt')
assert identities == json.loads((delivery / 'source-freeze.json').read_text())['final-identities']
profiles = yaml.safe_load((root / contract['environments']).read_text())['profiles']
classpath = ':'.join('/workspace/' + path.relative_to(root).as_posix()
                     for path in module.certification_classpath(root, contract, receipt))
inputs = module.certification_inputs(root, contract, receipt, case)
helper = Path(os.environ['FOLIO_HARFBUZZ_HELPER']).resolve(strict=True)
adopted = []
fresh = []
retained_directories = set()


def configuration(scope, profile, execution, environment_ref):
    plan = module.execution_plan(root, scope, profile['identity']['image'], helper, classpath, case, execution)
    value = {'schema-version': 1, 'candidate-sha256': identities['Candidate'],
             'environment-sha256': environment_ref['sha256'], 'acceptance-profile': case['profile'],
             'execution-profile': execution, 'command': plan['recorder-command'],
             'java-options': plan['java-options'], 'locale': 'en_US / C.UTF-8', 'timezone': 'UTC',
             'settings': plan['settings'], 'inputs': inputs}
    return plan, value


def reports_and_records(scope, execution, environment_directory, environment_ref):
    reports = module.collect_reports(root, scope / 'observations', 'annotations', execution)
    records = {}
    for chain, report in reports.items():
        if chain == 'semantic':
            report['findings'] += [module.reference(root, scope / 'contract-tests.txt'),
                                   module.reference(root, scope / 'contract-tests-command.json')]
        module.append_environment_observations(root, environment_directory, report)
        records[chain] = {'schema-version': 1, 'obligation': 'annotations', 'acceptance-profile': case['profile'],
                         'release': '0.1.0', 'candidate-sha256': identities['Candidate'],
                         'contract-sha256': identities['Contract'], 'environment-sha256': environment_ref['sha256'],
                         'execution-configuration-sha256': module.sha256(scope / 'execution.yaml'),
                         'execution-profile': execution, 'chain': chain, 'result': 'pass',
                         'producer': module.certification_producers(case)[chain],
                         'configuration': module.reference(root, root / declared['profile-contract']),
                         'report': module.reference(root, scope / (chain + '-report.json')) if (scope / (chain + '-report.json')).exists() else None,
                         'negative-controls': report['negative-controls']}
    return reports, records


for profile in profiles:
    major = profile['identity']['jdk-major']
    original_environment = source / ('jdk' + str(major) + '-environment')
    old_environment_path = original_environment / 'environment.yaml'
    old_environment = json.loads(old_environment_path.read_text())
    before = output / ('jdk' + str(major) + '-environment')
    current_environment = module.observe_environment(root, profile['identity']['image'], helper, before, profile,
                                                      root / 'target/foundation-0.1.0/harness')
    assert current_environment == old_environment, 'Actual environment changed before adoption'
    module.write_json(before / 'environment.yaml', current_environment)
    environment_ref = module.reference(root, old_environment_path)
    inventory['environments'].append({'profile': profile['id'], 'record': environment_ref})
    retained_directories.add(original_environment.relative_to(root).as_posix())
    original_after = source / ('jdk' + str(major) + '-environment-after')
    if major != 21:
        assert original_after.is_dir()
        # Compare the original observed payloads through the same extraction
        # that produced its environment record; the final live collector and
        # fresh before/after bracket separately check current actual bytes.
        for name in ('image-digest.txt', 'java-executable.txt', 'jdk-release.txt', 'java-version.txt',
                     'architecture.txt', 'os-release.txt', 'kernel.txt',
                     'prlimit-executable.txt', 'prlimit-version.txt'):
            assert (original_after / name).read_bytes() == (original_environment / name).read_bytes(), name
        native_before = json.loads((original_environment / 'native-observation.json').read_text())
        native_after = json.loads((original_after / 'native-observation.json').read_text())
        assert native_before['result'] == native_after['result'] == 'pass'
        # Observation timestamps and ASLR addresses differ per invocation.
        # Compare every other field, including every actually loaded file
        # and engine hash, while retaining both original raw observations.
        volatile = {'created_utc', 'process_maps'}
        assert volatile <= native_before.keys() and volatile <= native_after.keys()
        assert {key: value for key, value in native_before.items() if key not in volatile} == {
            key: value for key, value in native_after.items() if key not in volatile}, 'Native environment facts changed'
        retained_directories.add(original_after.relative_to(root).as_posix())
    else:
        assert not original_after.exists(), 'Expected missing JDK21 after proof is already present'
    for execution in declared['execution-profiles']:
        original_scope = source / ('jdk' + str(major) + '-' + execution.lower())
        retry = major == 21 and execution == 'HARDENED_WORKER'
        scope = output / original_scope.name if retry else original_scope
        plan, expected_configuration = configuration(scope, profile, execution, environment_ref)
        if retry:
            assert not any(original_scope.glob('*-report.json')), 'Do not repeat a completed tuple'
            scope.mkdir()
            module.write_json(scope / 'execution.yaml', expected_configuration)
            module.write_json(scope / 'contract-tests-command.json', plan['contract-tests-command'])
            print('Fresh annotations JDK21 HARDENED_WORKER retry at original bounds', flush=True)
            module.run_logged(plan['contract-tests-command'], scope / 'contract-tests.txt', cwd=root,
                              timeout=case.get('contract-timeout', 300))
            assert 'OK (' + str(case['test-count']) + ' tests)' in (scope / 'contract-tests.txt').read_text()
            module.run_logged(plan['recorder-command'], scope / 'recorder.txt', cwd=root,
                              timeout=case.get('recorder-timeout', 300))
        else:
            assert json.loads((scope / 'execution.yaml').read_text()) == expected_configuration
            assert json.loads((scope / 'contract-tests-command.json').read_text()) == plan['contract-tests-command']
            assert 'OK (' + str(case['test-count']) + ' tests)' in (scope / 'contract-tests.txt').read_text()
        reports, records = reports_and_records(scope, execution, original_environment, environment_ref)
        for chain, report in reports.items():
            if retry:
                module.write_json(scope / (chain + '-report.json'), report)
                records[chain]['report'] = module.reference(root, scope / (chain + '-report.json'))
                module.write_json(scope / (chain + '.yaml'), records[chain])
            else:
                assert json.loads((scope / (chain + '-report.json')).read_text()) == report, 'Original report differs from strict collection'
                assert json.loads((scope / (chain + '.yaml')).read_text()) == records[chain], 'Original tuple identity changed'
        inventory['certifications'].append({'obligation': 'annotations', 'environment': profile['id'],
            'execution-profile': execution, 'configuration': module.reference(root, scope / 'execution.yaml'),
            'records': [module.reference(root, scope / (chain + '.yaml')) for chain in reports]})
        (fresh if retry else adopted).append(scope.relative_to(root).as_posix())
        if not retry:
            retained_directories.add(scope.relative_to(root).as_posix())
            print('Adopted unchanged strictly collected ' + scope.name, flush=True)
        module.require_staged_build(root, contract, build)
    after = output / ('jdk' + str(major) + '-environment-after')
    assert module.observe_environment(root, profile['identity']['image'], helper, after, profile,
                                      root / 'target/foundation-0.1.0/harness') == old_environment

assert len(adopted) == 7 and len(fresh) == 1
expected = {(environment, execution) for environment in declared['environments'] for execution in declared['execution-profiles']}
assert {(item['environment'], item['execution-profile']) for item in inventory['certifications']} == expected
assert len(inventory['certifications']) == 8 and sum(len(item['records']) for item in inventory['certifications']) == 32
# Reuse the repository's transitive identity validation as a final all-tuples
# gate; a partial, changed or mismatched tuple must not disappear silently.
verified = module.merge_evidence(root, inventory, {**inventory, 'certifications': []}, identities)
assert verified == inventory, 'Strict transitive validation dropped or changed a tuple'
module.require_staged_build(root, contract, build)
assert authority.read_bytes() == prior
module.write_json(output / 'observed-index.json', inventory)
module.write_json(output / 'identities.json', identities)
(output / 'prior-index.sha256').write_text(hashlib.sha256(prior).hexdigest() + '\n')
module.write_json(output / 'adoption.json', {'result': 'pass', 'candidate-identities': identities,
    'adopted-unchanged-tuples': adopted, 'fresh-tuples': fresh, 'retained-directories': sorted(retained_directories),
    'failed-tuple-excluded': (source / 'jdk21-hardened_worker').relative_to(root).as_posix(),
    'environment-proof': 'Fresh actual before/after observations match the original facts, including the originally missing JDK21 after bracket.'})
print('Completed annotations: seven unchanged validated tuples plus one fresh retry; eight tuples / 32 chains.', flush=True)
