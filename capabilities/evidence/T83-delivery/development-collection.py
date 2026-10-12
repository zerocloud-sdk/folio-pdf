"""One actual development tuple plus strict live collection; never publishes an evidence index."""
import json
import os
from pathlib import Path
import sys
import yaml
root = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(root / 'scripts'))
import t21_foundation_reports as worker
module = worker.foundation()
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
receipt = module.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
output = root / sys.argv[1]; output.mkdir()
identity = module.candidate_identities(root, root / 'capabilities/foundation-evidence.yaml',
    {'schema-version': 1, 'candidate': receipt['candidate'], 'environments': [], 'certifications': []}, output / 'candidate-identity.txt')
profile = next(p for p in yaml.safe_load((root / contract['environments']).read_text())['profiles'] if p['identity']['jdk-major'] == 17)
helper = Path(os.environ['FOLIO_HARFBUZZ_HELPER']).resolve(strict=True)
case = module.certification_case('recovery')
environment_dir = output / 'jdk17-environment'
environment = module.observe_environment(root, profile['identity']['image'], helper, environment_dir, profile, root / 'target/foundation-0.1.0/harness')
module.write_json(environment_dir / 'environment.yaml', environment)
scope = output / 'jdk17-hardened_worker'; scope.mkdir()
cp = ':'.join('/workspace/' + path.relative_to(root).as_posix() for path in module.certification_classpath(root, contract, receipt))
plan = module.execution_plan(root, scope, profile['identity']['image'], helper, cp, case, 'HARDENED_WORKER')
configuration = {'schema-version': 1, 'candidate-sha256': identity['Candidate'],
    'environment-sha256': module.sha256(environment_dir / 'environment.yaml'),
    'acceptance-profile': case['profile'], 'execution-profile': 'HARDENED_WORKER',
    'command': plan['recorder-command'], 'java-options': plan['java-options'],
    'locale': 'en_US / C.UTF-8', 'timezone': 'UTC', 'settings': plan['settings'],
    'inputs': module.certification_inputs(root, contract, receipt, case)}
module.write_json(scope / 'execution.yaml', configuration)
module.write_json(scope / 'contract-tests-command.json', plan['contract-tests-command'])
module.run_logged(plan['contract-tests-command'], scope / 'contract-tests.txt', cwd=root, timeout=1800)
module.write_json(scope / 'recorder-command.json', plan['recorder-command'])
module.run_logged(plan['recorder-command'], scope / 'recorder.txt', cwd=root, timeout=1800)
reports = module.collect_reports(root, scope / 'observations', 'recovery', 'HARDENED_WORKER')
module.write_json(scope / 'development-reports.json', reports)
module.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
print('Development strict original/live collection passed; no certification index exported', flush=True)
