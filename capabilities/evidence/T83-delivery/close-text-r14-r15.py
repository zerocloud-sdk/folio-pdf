"""Finish interrupted T13 collection from sealed originals; never infer a process exit."""
import datetime
import gzip
import importlib.util
import json
import os
from pathlib import Path
import re
import sys

import yaml

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
sys.path.insert(0, str(root / 'scripts'))
spec = importlib.util.spec_from_file_location('t83_text_closeout', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
frozen_path = delivery / 'source-freeze-final-r8.json'
frozen = json.loads(frozen_path.read_text())
build = root / 'target/foundation-0.1.0/build-inputs.json'
authority = root / 'capabilities/foundation-evidence.yaml'
output = root / 'capabilities/evidence/foundation/T83-final-r14/text'
record = delivery / 'refresh-final-r15/text-closeout'
assert not record.exists()
record.mkdir(parents=True)
prior = delivery / 'refresh-final-r14/text-prior-index.yaml'
previous = authority.read_bytes()
assert driver.sha256(prior) == driver.sha256(authority)
prefix = json.loads((delivery / 'refresh-final-r14/results.json').read_text())
assert [item['obligation'] for item in prefix] == ['transactions', 'values', 'pages', 'metadata', 'annotations']
assert all(item['exit-code'] == 0 for item in prefix)
assert prefix[-1]['current-index-sha256'] == driver.sha256(authority)
receipt = driver.require_staged_build(root, contract, build)
assert receipt == json.loads((delivery / 'candidate-final-r8/build-inputs.json').read_text())
inputs = {key: frozen[key] for key in ('inputs', 'contract-inputs')}
assert driver.capture_build_inputs(root, contract) == inputs
case = driver.certification_case('text')
declared = next(item for item in contract['obligations'] if item['id'] == 'text')
profiles = yaml.safe_load((root / contract['environments']).read_text())['profiles']
assert {item['id'] for item in profiles} == set(declared['environments'])
assert set(declared['chains']) == set(driver.CHAINS)
assert set(declared['execution-profiles']) == {'IN_PROCESS', 'HARDENED_WORKER'}
assert not (delivery / 'validation/refresh-final-r14-text-result.json').exists()
assert not any((output / name).exists() for name in ('observed-index.json', 'identities.json', 'prior-index.sha256'))

# The original files are immutable, including the unclosed outer transcript.
originals = [driver.reference(root, path) for path in sorted(output.rglob('*')) if path.is_file()]
assert originals and not any(path.is_symlink() for path in output.rglob('*'))
manifest = record / 'original-files.tsv.gz'
with manifest.open('wb') as stream:
    with gzip.GzipFile(filename='', mode='wb', fileobj=stream, mtime=0) as compressed:
        for item in originals:
            compressed.write((item['sha256'] + '\t' + item['path'] + '\n').encode())
driver.write_json(record / 'preflight.json', {
    'started': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'source-freeze': driver.reference(root, frozen_path),
    'closeout-driver': driver.reference(root, Path(__file__)),
    'original-ledger': driver.reference(root, delivery / 'refresh-final-r14/results.json'),
    'original-outer-log': driver.reference(root, delivery / 'validation/refresh-final-r14-text.txt'),
    'original-file-manifest': driver.reference(root, manifest), 'original-file-count': len(originals),
    'prior-index': driver.reference(root, prior),
    'interrupted-process-exit': 'unavailable; no synthetic gate result is created',
    'operation': 'Verify eight sealed original T13 corpora; complete only absent records and closing checks'})


def current():
    driver.require_staged_build(root, contract, build)
    assert driver.capture_build_inputs(root, contract) == inputs
    assert authority.read_bytes() == previous


inventory = {'schema-version': 1, 'candidate': receipt['candidate'], 'environments': [], 'certifications': []}
identities = driver.candidate_identities(root, authority, inventory, record / 'candidate-identity.txt')
old_identity_text = (output / 'candidate-identity.txt').read_text()
assert all(re.search(r'^' + name + r' identity: ' + value + '$', old_identity_text, re.MULTILINE)
           for name, value in identities.items())
helper = Path(os.environ['FOLIO_HARFBUZZ_HELPER']).resolve(strict=True)
cp = ':'.join('/workspace/' + path.relative_to(root).as_posix()
              for path in driver.certification_classpath(root, contract, receipt))
configuration_inputs = driver.certification_inputs(root, contract, receipt, case)
closed = []
for profile in profiles:
    major = profile['identity']['jdk-major']
    directory = output / ('jdk' + str(major) + '-environment')
    environment_ref = driver.reference(root, directory / 'environment.yaml')
    environment = json.loads((directory / 'environment.yaml').read_text())
    assert environment['profile'] == profile['id'] and environment['identity'] == profile['identity']
    inventory['environments'].append({'profile': profile['id'], 'record': environment_ref})
    for execution in ('IN_PROCESS', 'HARDENED_WORKER'):
        current()
        scope = output / ('jdk' + str(major) + '-' + execution.lower())
        plan = driver.execution_plan(root, scope, profile['identity']['image'], helper, cp, case, execution)
        expected_configuration = {
            'schema-version': 1, 'candidate-sha256': identities['Candidate'],
            'environment-sha256': environment_ref['sha256'], 'acceptance-profile': case['profile'],
            'execution-profile': execution, 'command': plan['recorder-command'],
            'java-options': plan['java-options'], 'locale': 'en_US / C.UTF-8', 'timezone': 'UTC',
            'settings': plan['settings'], 'inputs': configuration_inputs}
        configuration = scope / 'execution.yaml'
        assert json.loads(configuration.read_text()) == expected_configuration
        assert json.loads((scope / 'contract-tests-command.json').read_text()) == plan['contract-tests-command']
        transcript = (scope / 'contract-tests.txt').read_text()
        assert re.search(r'^OK \(' + str(case['test-count']) + r' tests\)$', transcript, re.MULTILINE)
        assert 'FAILURES!!!' not in transcript
        print('Strict sealed-original text collection JDK ' + str(major) + ' / ' + execution, flush=True)
        reports = driver.collect_reports(root, scope / 'observations', 'text', execution)
        assert set(reports) == set(driver.CHAINS)
        records = []
        created = []
        for chain, report in reports.items():
            assert report['result'] == 'pass' and report['negative-controls']
            if chain == 'semantic':
                report['findings'] += [driver.reference(root, scope / 'contract-tests.txt'),
                                       driver.reference(root, scope / 'contract-tests-command.json')]
            driver.append_environment_observations(root, directory, report)
            report_file = scope / (chain + '-report.json')
            path = scope / (chain + '.yaml')
            assert report_file.exists() == path.exists()
            if report_file.exists():
                assert json.loads(report_file.read_text()) == report
            else:
                assert major == 21 and execution == 'HARDENED_WORKER'
                driver.write_json(report_file, report)
                created.append(chain)
            certification = {
                'schema-version': 1, 'obligation': 'text', 'acceptance-profile': case['profile'],
                'release': '0.1.0', 'candidate-sha256': identities['Candidate'],
                'contract-sha256': identities['Contract'], 'environment-sha256': environment_ref['sha256'],
                'execution-configuration-sha256': driver.sha256(configuration),
                'execution-profile': execution, 'chain': chain, 'result': 'pass',
                'producer': driver.certification_producers(case)[chain],
                'configuration': driver.reference(root, root / declared['profile-contract']),
                'report': driver.reference(root, report_file), 'negative-controls': report['negative-controls']}
            if path.exists():
                assert json.loads(path.read_text()) == certification
            else:
                driver.write_json(path, certification)
            records.append(driver.reference(root, path))
        inventory['certifications'].append({
            'obligation': 'text', 'environment': profile['id'], 'execution-profile': execution,
            'configuration': driver.reference(root, configuration), 'records': records})
        closed.append({'jdk-major': major, 'execution-profile': execution,
                       'strict-original-collection': 'pass', 'new-chain-records': created})
    # Fresh actual observations close the interruption gap without replacing any original.
    print('Fresh closing environment observation JDK ' + str(major), flush=True)
    actual = driver.observe_environment(root, profile['identity']['image'], helper,
                                       record / ('jdk' + str(major) + '-environment-after'), profile,
                                       root / 'target/foundation-0.1.0/harness')
    assert actual == environment
    current()

assert len(inventory['certifications']) == 8
assert [item['new-chain-records'] for item in closed[:-1]] == [[]] * 7
assert set(closed[-1]['new-chain-records']) == set(driver.CHAINS)
assert all(item == driver.reference(root, root / item['path']) for item in originals)
current()
driver.write_json(output / 'observed-index.json', inventory)
driver.write_json(output / 'identities.json', identities)
(output / 'prior-index.sha256').write_text(driver.sha256(prior) + '\n')
merged = driver.publish_index(root, output)
assert len(merged['certifications']) == 48
assert sum(len(item['records']) for item in merged['certifications']) == 192
driver.require_staged_build(root, contract, build)
assert driver.capture_build_inputs(root, contract) == inputs
assert all(item == driver.reference(root, root / item['path']) for item in originals)
driver.write_json(record / 'result.json', {
    'finished': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'result': 'pass',
    'closeout-driver': driver.reference(root, Path(__file__)), 'originals-preserved': True,
    'original-file-manifest': driver.reference(root, manifest), 'original-file-count': len(originals),
    'collections': closed, 'fresh-closing-environments': [item['id'] for item in profiles],
    'observed-index': driver.reference(root, output / 'observed-index.json'),
    'current-index': driver.reference(root, authority), 'certifications': 48, 'chain-records': 192,
    'interrupted-outer-process-result': 'unknown; original transcript and absent result remain untouched'})
print('Closed text from all eight sealed originals: 48 current tuples / 192 chains; interrupted gate not relabeled.', flush=True)
