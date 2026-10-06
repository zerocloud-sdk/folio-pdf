#!/usr/bin/env python3
"""Ticket-local recovery using the unchanged, staged Foundation driver.

Retain seven complete current-candidate clear-metadata scopes, execute the remaining one
in fresh directories, and publish only the complete eight-scope obligation.
This retained orchestration recipe is outside the candidate source roots.
"""
import datetime
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import sys

import yaml

ROOT = Path('/workspace/folio-pdf')
spec = importlib.util.spec_from_file_location('t81_driver', ROOT / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
sys.path.insert(0, str(ROOT / 'scripts'))
spec.loader.exec_module(driver)


def main(output):
    obligation = 'password-clear-metadata'
    original = ROOT / 'capabilities/evidence/foundation/T81-final-r2/password-clear-metadata'
    originals = (original,)
    retained = {(major, execution): original for major in (8, 11, 17, 21)
                for execution in ('IN_PROCESS', 'HARDENED_WORKER')
                if (major, execution) != (21, 'HARDENED_WORKER')}
    environment_originals = {major: original for major in (8, 11, 17, 21)}
    case = driver.certification_case(obligation)
    contract = yaml.safe_load((ROOT / 'capabilities/foundation-release.yaml').read_text())
    declared = next(item for item in contract['obligations'] if item['id'] == obligation)
    profiles = case.get('execution-profiles', ('IN_PROCESS', 'HARDENED_WORKER'))
    chains = set(case.get('chains', driver.CHAINS))
    assert set(profiles) == set(declared['execution-profiles'])
    assert chains == set(declared['chains']) == set(driver.CHAINS)
    base = ROOT / 'target/foundation-0.1.0'
    build_record = base / 'build-inputs.json'
    receipt = driver.require_staged_build(ROOT, contract, build_record)
    candidate = receipt['candidate']
    output.relative_to(ROOT)
    output.mkdir()
    inventory = {'schema-version': 1, 'candidate': candidate, 'environments': [], 'certifications': []}
    authority = ROOT / 'capabilities/foundation-evidence.yaml'
    previous_bytes = authority.read_bytes()
    previous = json.loads(previous_bytes)
    assert previous['candidate'] == candidate and len(previous['certifications']) == 72
    previous_scopes = {(item['obligation'], item['environment'], item['execution-profile'])
                       for item in previous['certifications']}
    identities = driver.candidate_identities(ROOT, authority, inventory, output / 'candidate-identity.txt')
    environment_profiles = yaml.safe_load((ROOT / contract['environments']).read_text())['profiles']
    expected_scopes = {(obligation, profile['id'], execution)
                       for profile in environment_profiles for execution in profiles}
    assert len(expected_scopes) == 8
    configuration_inputs = driver.certification_inputs(ROOT, contract, receipt, case)
    cp = ':'.join('/workspace/' + path.relative_to(ROOT).as_posix()
                  for path in driver.certification_classpath(ROOT, contract, receipt))
    helper = Path(os.environ['FOLIO_HARFBUZZ_HELPER'])
    # Seal every original file, including the failed JDK21 suite and all passing transcripts.
    def sealed_original_files():
        return [driver.reference(ROOT, path) for path in sorted(
            path for directory in originals for path in directory.rglob('*') if path.is_file())]
    original_files = sealed_original_files()
    driver.write_json(output / 'original-files-before.json', original_files)
    reused = []
    environment_checks = []
    for profile in environment_profiles:
        major = profile['identity']['jdk-major']
        print(case['label'] + ' environment JDK ' + str(major), flush=True)
        directory = output / ('jdk' + str(major) + '-environment')
        environment = driver.observe_environment(ROOT, profile['identity']['image'], helper, directory, profile, base / 'harness')
        live_environment_path = directory / 'environment.yaml'
        driver.write_json(live_environment_path, environment)
        original_environment_path = environment_originals.get(major, original) / ('jdk' + str(major) + '-environment/environment.yaml')
        if any(item[0] == major for item in retained):
            assert environment == json.loads(original_environment_path.read_text()), 'Original/live environment mismatch'
            for original_directory in originals:
                original_before = original_directory / ('jdk' + str(major) + '-environment/environment.yaml')
                if original_before.exists():
                    assert environment == json.loads(original_before.read_text()), 'Retained original environment mismatch'
                original_after = original_directory / ('jdk' + str(major) + '-environment-after')
                if not original_after.exists():
                    continue
                # Its actual original payloads remain intact and hash-sealed above.
                old_native = json.loads((original_after / 'native-observation.json').read_text())
                live_native = json.loads((directory / 'native-observation.json').read_text())
                assert old_native['result'] == live_native['result'] == 'pass'
                for name in ('helper', 'loaded_engine', 'installation'):
                    assert old_native[name] == live_native[name], 'Closing native identity mismatch'
                for name in ('os-release.txt', 'jdk-release.txt', 'java-executable.txt', 'kernel.txt', 'architecture.txt'):
                    assert (original_after / name).read_bytes() == (directory / name).read_bytes()
            environment_ref = driver.reference(ROOT, original_environment_path)
        else:
            environment_ref = driver.reference(ROOT, live_environment_path)
        inventory['environments'].append({'profile': profile['id'], 'record': environment_ref})
        for execution in profiles:
            if (major, execution) in retained:
                scope = retained[(major, execution)] / ('jdk' + str(major) + '-' + execution.lower())
                configuration = scope / 'execution.yaml'
                config = json.loads(configuration.read_text())
                assert config['inputs'] == configuration_inputs
                assert config['candidate-sha256'] == identities['Candidate']
                assert config['environment-sha256'] == environment_ref['sha256']
                transcript = (scope / 'contract-tests.txt').read_text()
                assert 'OK (' + str(case['test-count']) + ' tests)' in transcript and 'FAILURES!!!' not in transcript
                original_plan = driver.execution_plan(ROOT, scope, profile['identity']['image'], helper, cp, case, execution)
                assert config['command'] == original_plan['recorder-command']
                assert json.loads((scope / 'contract-tests-command.json').read_text()) == original_plan['contract-tests-command']
                assert config['java-options'] == original_plan['java-options'] and config['settings'] == original_plan['settings']
                records = []
                for chain in driver.CHAINS:
                    record_path = scope / (chain + '.yaml')
                    record = json.loads(record_path.read_text())
                    expected = {'candidate-sha256': identities['Candidate'], 'contract-sha256': identities['Contract'],
                                'environment-sha256': environment_ref['sha256'], 'execution-profile': execution,
                                'execution-configuration-sha256': driver.sha256(configuration),
                                'obligation': obligation, 'chain': chain, 'result': 'pass',
                                'producer': driver.certification_producers(case)[chain]}
                    assert all(record.get(name) == value for name, value in expected.items())
                    records.append(driver.reference(ROOT, record_path))
                reused.append({'environment': profile['id'], 'execution-profile': execution,
                               'original-scope': scope.relative_to(ROOT).as_posix()})
                print(case['label'] + ' retained original JDK ' + str(major) + ' / ' + execution, flush=True)
            else:
                print(case['label'] + ' certification JDK ' + str(major) + ' / ' + execution, flush=True)
                scope = output / ('jdk' + str(major) + '-' + execution.lower())
                scope.mkdir()
                plan = driver.execution_plan(ROOT, scope, profile['identity']['image'], helper, cp, case, execution)
                configuration = scope / 'execution.yaml'
                config = {'schema-version': 1, 'candidate-sha256': identities['Candidate'],
                          'environment-sha256': environment_ref['sha256'], 'acceptance-profile': case['profile'],
                          'execution-profile': execution, 'command': plan['recorder-command'],
                          'java-options': plan['java-options'], 'locale': 'en_US / C.UTF-8', 'timezone': 'UTC',
                          'settings': plan['settings'], 'inputs': configuration_inputs}
                driver.write_json(configuration, config)
                driver.write_json(scope / 'contract-tests-command.json', plan['contract-tests-command'])
                driver.run_logged(plan['contract-tests-command'], scope / 'contract-tests.txt', cwd=ROOT,
                                  timeout=case.get('contract-timeout', 300))
                if 'OK (' + str(case['test-count']) + ' tests)' not in (scope / 'contract-tests.txt').read_text():
                    raise ValueError('The complete clear-metadata suite did not execute')
                driver.run_logged(plan['recorder-command'], scope / 'recorder.txt', cwd=ROOT,
                                  timeout=case.get('recorder-timeout', 300))
                reports = driver.collect_reports(ROOT, scope / 'observations', obligation, execution)
                assert set(reports) == chains
                records = []
                for chain, report in reports.items():
                    if chain == 'semantic':
                        report['findings'] += [driver.reference(ROOT, scope / 'contract-tests.txt'),
                                               driver.reference(ROOT, scope / 'contract-tests-command.json')]
                    driver.append_environment_observations(ROOT, directory, report)
                    report_file = scope / (chain + '-report.json')
                    driver.write_json(report_file, report)
                    record = {'schema-version': 1, 'obligation': obligation, 'acceptance-profile': case['profile'],
                              'release': '0.1.0', 'candidate-sha256': identities['Candidate'], 'contract-sha256': identities['Contract'],
                              'environment-sha256': environment_ref['sha256'], 'execution-configuration-sha256': driver.sha256(configuration),
                              'execution-profile': execution, 'chain': chain, 'result': 'pass',
                              'producer': driver.certification_producers(case)[chain],
                              'configuration': driver.reference(ROOT, ROOT / declared['profile-contract']),
                              'report': driver.reference(ROOT, report_file), 'negative-controls': report['negative-controls']}
                    path = scope / (chain + '.yaml')
                    driver.write_json(path, record)
                    records.append(driver.reference(ROOT, path))
            driver.require_unchanged(ROOT, contract, candidate)
            driver.require_staged_build(ROOT, contract, build_record)
            inventory['certifications'].append({'obligation': obligation, 'environment': profile['id'],
                'execution-profile': execution, 'configuration': driver.reference(ROOT, configuration), 'records': records})
        after = output / ('jdk' + str(major) + '-environment-after')
        if driver.observe_environment(ROOT, profile['identity']['image'], helper, after, profile, base / 'harness') != environment:
            raise ValueError('Environment/tool identities changed during observations')
        environment_checks.append({'profile': profile['id'], 'result': 'pass',
                                   'before': driver.reference(ROOT, live_environment_path),
                                   'after-observations': [driver.reference(ROOT, path) for path in sorted(after.iterdir()) if path.is_file()],
                                   'compared-with-original': any(item[0] == major for item in retained)})
    assert {(item['obligation'], item['environment'], item['execution-profile']) for item in inventory['certifications']} == expected_scopes
    assert len(inventory['certifications']) == 8 and len(reused) == 7
    assert original_files == sealed_original_files()
    driver.require_unchanged(ROOT, contract, candidate)
    driver.require_staged_build(ROOT, contract, build_record)
    assert authority.read_bytes() == previous_bytes
    driver.write_json(output / 'observed-index.json', inventory)
    driver.write_json(output / 'identities.json', identities)
    (output / 'prior-index.sha256').write_text(hashlib.sha256(previous_bytes).hexdigest() + '\n')
    # merge_evidence deliberately discards stale predecessors. Prove none would
    # be lost before publish_index can atomically replace the authority.
    prepared = driver.merge_evidence(ROOT, previous, inventory, identities)
    prepared_scopes = {(item['obligation'], item['environment'], item['execution-profile'])
                       for item in prepared['certifications']}
    assert len(prepared['certifications']) == 80
    assert prepared_scopes == previous_scopes | expected_scopes
    assert authority.read_bytes() == previous_bytes
    merged = driver.publish_index(ROOT, output)
    assert len(merged['certifications']) == 80
    assert previous_scopes.issubset({(item['obligation'], item['environment'], item['execution-profile']) for item in merged['certifications']})
    driver.write_json(output / 'resume-audit.json', {'schema-version': 1, 'result': 'pass',
        'orchestrator': driver.reference(ROOT, Path(__file__)), 'driver': driver.reference(ROOT, ROOT / 'scripts/t03-foundation.py'),
        'identities': identities, 'retained-original-scopes': reused, 'fresh-scopes': 1,
        'expected-scope-count': 8, 'expected-chains': list(driver.CHAINS), 'environment-checks': environment_checks,
        'original-files-unchanged': driver.reference(ROOT, output / 'original-files-before.json')})
    print('Recorded exactly 8 T79 certifications; retained 72 current certifications for other obligations.', flush=True)


if __name__ == '__main__':
    assert sys.argv[1] == 'certify' and sys.argv[3:] == ['--obligation', 'password-clear-metadata']
    output = ROOT / sys.argv[2]
    journal = ROOT / 'capabilities/evidence/T81-delivery/refresh-final-r2-commands.json'
    prior_events = json.loads(journal.read_text())
    attempt = 1 + max(event['attempt'] for event in prior_events if event['obligation'] == 'password-clear-metadata')
    started = datetime.datetime.now(datetime.timezone.utc).isoformat()
    status = 1
    try:
        main(output)
        status = 0
    finally:
        events = json.loads(journal.read_text())
        events.append({'obligation': 'password-clear-metadata', 'attempt': attempt,
                       'command': [sys.executable, '-B'] + sys.argv,
                       'started': started, 'finished': datetime.datetime.now(datetime.timezone.utc).isoformat(),
                       'exit-code': status, 'recovery-recipe': driver.reference(ROOT, Path(__file__))})
        driver.write_json(journal, events)
