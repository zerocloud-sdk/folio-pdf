"""Complete the missing fresh candidate10 text tuple; preserve all R21 originals."""
import datetime
import gzip
import importlib.util
import json
import os
from pathlib import Path
import shutil

import yaml

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('t83_complete_text_r22', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
frozen_path = delivery / 'source-freeze-final-r10.json'
frozen = json.loads(frozen_path.read_text())
inputs = {key: frozen[key] for key in ('inputs', 'contract-inputs')}
build = root / 'target/foundation-0.1.0/build-inputs.json'
receipt = driver.require_staged_build(root, contract, build)
assert receipt == json.loads((delivery / 'candidate-final-r10/build-inputs.json').read_text())
assert driver.capture_build_inputs(root, contract) == inputs
old = root / 'capabilities/evidence/foundation/T83-final-r21/text'
output = root / 'capabilities/evidence/foundation/T83-final-r22/text'
assert not output.parent.exists()
authority = root / 'capabilities/foundation-evidence.yaml'
previous = authority.read_bytes()
preserved_path = delivery / 'failed-text-certification-r21.json'
preserved = json.loads(preserved_path.read_text())
assert preserved['certifications-exported'] == 0 and preserved['index-unchanged']
assert driver.reference(root, authority) == preserved['observed-index']
assert preserved['prior-index']['sha256'] == driver.sha256(authority)
diagnostic = delivery / 'text-timeout-diagnostics-r22'
attempts = json.loads((diagnostic / 'results.json').read_text())
assert len(attempts) == 24
assert all(row['exit-code'] == 0 and row['required-execution'] for row in attempts[:23])
assert attempts[-1]['exit-code'] == 1 and not attempts[-1]['required-execution']
assert attempts[-1]['mode'] == 'prefix' and attempts[-1]['attempt'] == 4
assert json.loads((delivery / 'validation/text-timeout-diagnostic-r22-result.json').read_text())['exit-code'] == 1
cleanup_path = delivery / 'validation/post-r22-diagnostic-container-check-result.json'
cleanup = json.loads(cleanup_path.read_text())
assert cleanup['exit-code'] == 0 and not (root / cleanup['log']['path']).read_text().strip()


def preserved_originals():
    metadata = json.loads(preserved_path.read_text())
    assert driver.reference(root, root / metadata['manifest']['path']) == metadata['manifest']
    count = total = 0
    with gzip.open(root / metadata['manifest']['path'], 'rt', encoding='utf-8') as stream:
        assert next(stream) == 'sha256\tbytes\tpath\n'
        for line in stream:
            digest, length, name = line.rstrip('\n').split('\t')
            path = root / name
            assert path.is_file() and not path.is_symlink()
            assert path.stat().st_size == int(length) and driver.sha256(path) == digest
            count += 1
            total += int(length)
    assert count == metadata['original-files'] and total == metadata['original-bytes']
    for key in ('gate', 'ledger', 'prior-index', 'failure', 'source-freeze', 'refresh-driver', 'preflight'):
        item = metadata[key]
        assert driver.reference(root, root / item['path']) == item


preserved_originals()
output.parent.mkdir()
output.mkdir()
shutil.copyfile(authority, output / 'starting-index.yaml')
refs = [driver.reference(root, path) for path in (
    Path(__file__).resolve(), frozen_path, preserved_path, root / preserved['manifest']['path'],
    delivery / 'refresh-final-r21/results.json', delivery / 'candidate-final-r10-verification.json',
    delivery / 'validation/text-timeout-diagnostic-r22-result.json', cleanup_path,
    root / cleanup['log']['path'], output / 'starting-index.yaml')]
refs += [driver.reference(root, path) for path in sorted(diagnostic.rglob('*')) if path.is_file()]
driver.write_json(output / 'preflight.json', {
    'recorded-at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'candidate': receipt['candidate'], 'preserved-inputs': refs,
    'retained-complete-original-tuples': 7, 'fresh-required-tuples': 1,
    'failed-attempts-relabelled': False, 'source-or-limit-changes': False,
    'disposition': 'Fully revalidate the seven complete actual candidate10 R21 recordings at their original paths; execute the missing JDK21 HARDENED_WORKER tuple in this fresh scope. Publish only the complete eight-tuples/four-chain text set with the unchanged collector and atomic publisher.'})


def current():
    assert authority.read_bytes() == previous
    assert all(driver.reference(root, root / item['path']) == item for item in refs)
    assert driver.capture_build_inputs(root, contract) == inputs
    assert driver.require_staged_build(root, contract, build) == receipt


case = driver.certification_case('text')
declared = next(item for item in contract['obligations'] if item['id'] == 'text')
assert case['test-count'] == 126
assert set(case.get('execution-profiles', ('IN_PROCESS', 'HARDENED_WORKER'))) == set(declared['execution-profiles'])
assert set(case.get('chains', driver.CHAINS)) == set(declared['chains'])
helper = Path(os.environ['FOLIO_HARFBUZZ_HELPER']).resolve(strict=True)
base = root / 'target/foundation-0.1.0'
harness = base / 'harness'
cp = ':'.join('/workspace/' + path.relative_to(root).as_posix()
              for path in driver.certification_classpath(root, contract, receipt))
configuration_inputs = driver.certification_inputs(root, contract, receipt, case)
inventory = {'schema-version': 1, 'candidate': receipt['candidate'], 'environments': [], 'certifications': []}
identities = driver.candidate_identities(root, authority, inventory, output / 'candidate-identity.txt')
profiles = yaml.safe_load((root / contract['environments']).read_text())['profiles']


def config_for(scope, profile, execution, environment_ref):
    plan = driver.execution_plan(root, scope, profile['identity']['image'], helper, cp, case, execution)
    assert not plan.get('support-commands')
    return plan, {'schema-version': 1, 'candidate-sha256': identities['Candidate'],
                  'environment-sha256': environment_ref['sha256'], 'acceptance-profile': case['profile'],
                  'execution-profile': execution, 'command': plan['recorder-command'],
                  'java-options': plan['java-options'], 'locale': 'en_US / C.UTF-8', 'timezone': 'UTC',
                  'settings': plan['settings'], 'inputs': configuration_inputs}


for profile in profiles:
    major = profile['identity']['jdk-major']
    original_environment = old / ('jdk' + str(major) + '-environment')
    environment = json.loads((original_environment / 'environment.yaml').read_text())
    environment_ref = driver.reference(root, original_environment / 'environment.yaml')
    assert environment['profile'] == profile['id']
    inventory['environments'].append({'profile': profile['id'], 'record': environment_ref})
    fresh_directory = output / ('jdk' + str(major) + '-environment')
    assert driver.observe_environment(root, profile['identity']['image'], helper,
                                      fresh_directory, profile, harness) == environment
    driver.write_json(fresh_directory / 'environment.yaml', environment)
    assert driver.sha256(fresh_directory / 'environment.yaml') == environment_ref['sha256']
    for execution in declared['execution-profiles']:
        fresh = major == 21 and execution == 'HARDENED_WORKER'
        scope = (output if fresh else old) / ('jdk' + str(major) + '-' + execution.lower())
        plan, config = config_for(scope, profile, execution, environment_ref)
        if fresh:
            current()
            scope.mkdir()
            driver.write_json(scope / 'execution.yaml', config)
            driver.write_json(scope / 'contract-tests-command.json', plan['contract-tests-command'])
            print('Fresh exact candidate10 JDK21 HARDENED_WORKER: all 126 cases, unchanged timeouts.', flush=True)
            driver.run_logged(plan['contract-tests-command'], scope / 'contract-tests.txt', cwd=root,
                              timeout=case.get('contract-timeout', 300))
        assert json.loads((scope / 'execution.yaml').read_text()) == config
        assert json.loads((scope / 'contract-tests-command.json').read_text()) == plan['contract-tests-command']
        assert 'OK (126 tests)' in (scope / 'contract-tests.txt').read_text()
        if fresh:
            driver.run_logged(plan['recorder-command'], scope / 'recorder.txt', cwd=root,
                              timeout=case.get('recorder-timeout', 300))
        reports = driver.collect_reports(root, scope / 'observations', 'text', execution)
        assert set(reports) == set(declared['chains'])
        records = []
        for chain, report in reports.items():
            if chain == 'semantic':
                report['findings'] += [driver.reference(root, scope / 'contract-tests.txt'),
                                      driver.reference(root, scope / 'contract-tests-command.json')]
            driver.append_environment_observations(root, fresh_directory if fresh else original_environment, report)
            report_file = scope / (chain + '-report.json')
            record = {'schema-version': 1, 'obligation': 'text', 'acceptance-profile': case['profile'],
                      'release': '0.1.0', 'candidate-sha256': identities['Candidate'],
                      'contract-sha256': identities['Contract'], 'environment-sha256': environment_ref['sha256'],
                      'execution-configuration-sha256': driver.sha256(scope / 'execution.yaml'),
                      'execution-profile': execution, 'chain': chain, 'result': 'pass',
                      'producer': driver.certification_producers(case)[chain],
                      'configuration': driver.reference(root, root / declared['profile-contract']),
                      'report': driver.reference(root, report_file) if not fresh else None,
                      'negative-controls': report['negative-controls']}
            path = scope / (chain + '.yaml')
            if fresh:
                driver.write_json(report_file, report)
                record['report'] = driver.reference(root, report_file)
                driver.write_json(path, record)
            else:
                assert json.loads(report_file.read_text()) == report
                assert json.loads(path.read_text()) == record
            records.append(driver.reference(root, path))
        inventory['certifications'].append({'obligation': 'text', 'environment': profile['id'],
            'execution-profile': execution, 'configuration': driver.reference(root, scope / 'execution.yaml'),
            'records': records})
        current()
    if major == 21:
        assert driver.observe_environment(root, profile['identity']['image'], helper,
                    output / 'jdk21-environment-after', profile, harness) == environment

assert len(inventory['certifications']) == 8
assert sum(len(row['records']) for row in inventory['certifications']) == 32
current()
preserved_originals()
driver.write_json(output / 'observed-index.json', inventory)
driver.write_json(output / 'identities.json', identities)
(output / 'prior-index.sha256').write_text(driver.sha256(authority) + '\n')
merged = driver.publish_index(root, output)
assert len(merged['certifications']) == 48
assert sum(len(row['records']) for row in merged['certifications']) == 192
assert driver.capture_build_inputs(root, contract) == inputs
assert driver.require_staged_build(root, contract, build) == receipt
assert all(driver.reference(root, root / item['path']) == item for item in refs)
preserved_originals()
print('Completed eight actual candidate10 text tuples / 32 chains; seven immutable originals plus one fresh tuple. No failure relabelled; all limits unchanged.', flush=True)
