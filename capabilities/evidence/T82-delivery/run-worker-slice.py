"""Run one unindexed development T21 tuple through the real certification path."""
import importlib.util
import json
import os
from pathlib import Path
import sys
import yaml

root = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(root / 'scripts'))
spec = importlib.util.spec_from_file_location('worker_slice', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
output = root / sys.argv[1]
major = int(sys.argv[2])
output.mkdir()
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
receipt = driver.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
helper = Path(os.environ['FOLIO_HARFBUZZ_HELPER']).resolve(strict=True)
profile = next(p for p in yaml.safe_load((root / contract['environments']).read_text())['profiles']
               if p['identity']['jdk-major'] == major)
inventory = {'schema-version': 1, 'candidate': receipt['candidate'], 'environments': [], 'certifications': []}
identities = driver.candidate_identities(root, root / 'capabilities/foundation-evidence.yaml', inventory, output / 'candidate-identity.txt')
driver.write_json(output / 'identities.json', identities)
directory = output / ('jdk' + str(major) + '-environment')
environment = driver.observe_environment(root, profile['identity']['image'], helper, directory, profile,
                                         root / 'target/foundation-0.1.0/harness')
environment_path = directory / 'environment.yaml'
driver.write_json(environment_path, environment)
cp = ':'.join('/workspace/' + path.relative_to(root).as_posix()
              for path in driver.certification_classpath(root, contract, receipt))
scope = output / ('jdk' + str(major) + '-hardened_worker'); scope.mkdir()
case = driver.certification_case('worker')
plan = driver.execution_plan(root, scope, profile['identity']['image'], helper, cp, case, 'HARDENED_WORKER')
driver.write_json(scope / 'execution.yaml', {'schema-version': 1, 'candidate-sha256': identities['Candidate'],
    'environment-sha256': driver.sha256(environment_path), 'acceptance-profile': case['profile'],
    'execution-profile': 'HARDENED_WORKER', 'command': plan['recorder-command'], 'java-options': plan['java-options'],
    'locale': 'en_US / C.UTF-8', 'timezone': 'UTC', 'settings': plan['settings'],
    'support-commands': plan['support-commands'], 'inputs': driver.certification_inputs(root, contract, receipt, case)})
driver.write_json(scope / 'plan.json', plan)
driver.write_json(scope / 'contract-tests-command.json', plan['contract-tests-command'])
print('Running exact mandatory cases for JDK ' + str(major), flush=True)
driver.run_logged(plan['contract-tests-command'], scope / 'contract-tests.txt', cwd=root, timeout=case['contract-timeout'])
for index, command in enumerate(plan['support-commands']):
    driver.run_logged(command, scope / ('support-' + str(index) + '.txt'), cwd=root, timeout=60)
print('Recording actual public products and independent findings', flush=True)
driver.run_logged(plan['recorder-command'], scope / 'recorder.txt', cwd=root, timeout=case['recorder-timeout'])
print('Collecting and reproducing original observations', flush=True)
reports = driver.collect_reports(root, scope / 'observations', 'worker', 'HARDENED_WORKER')
for chain, report in reports.items():
    driver.write_json(scope / (chain + '-report.json'), report)
driver.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
print('Unindexed T21 development tuple passed: JDK ' + str(major), flush=True)
