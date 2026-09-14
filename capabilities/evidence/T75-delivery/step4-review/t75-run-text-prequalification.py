"""Execute the existing unverified T13 plan without publishing certification."""
import importlib.util
import json
from pathlib import Path
import shutil
import sys
import yaml

root = Path('/home/ubuntu/IdeaProjects/open-pdf')
sys.path.insert(0, str(root / 'scripts'))
spec = importlib.util.spec_from_file_location('foundation', root / 'scripts/t03-foundation.py')
foundation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(foundation)
output = (root / sys.argv[1]).resolve()
plan = json.loads((output / 'plan.json').read_text())
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
profiles = yaml.safe_load((root / contract['environments']).read_text())['profiles']
helper = root / '.build-cache/harfbuzz/10.2.0-final/bin/folio-harfbuzz'
base = root / 'target/foundation-0.1.0'
receipt = foundation.require_staged_build(root, contract, base / 'build-inputs.json')
shutil.copyfile(base / 'build-inputs.json', output / 'build-inputs.json')
shutil.copyfile(base / 'build-command.json', output / 'build-command.json')
shutil.copyfile(base / 'build.txt', output / 'build.txt')
results = []
for index, profile in enumerate(profiles):
    major = profile['identity']['jdk-major']
    print('T13 prequalification environment JDK ' + str(major), flush=True)
    before = foundation.observe_environment(root, profile['identity']['image'], helper,
        output / ('jdk' + str(major) + '-environment'), profile, base / 'harness')
    foundation.write_json(output / ('jdk' + str(major) + '-environment/environment.json'), before)
    for mode_index, execution in enumerate(('IN_PROCESS', 'HARDENED_WORKER')):
        item = plan['executions'][2 * index + mode_index]
        scope = output / ('jdk' + str(major) + '-' + execution.lower())
        scope.mkdir()
        print('T13 prequalification JDK ' + str(major) + ' / ' + execution + ' contract tests', flush=True)
        foundation.run_logged(item['contract-tests-command'], scope / 'contract-tests.txt', cwd=root, timeout=600)
        if 'OK (' + str(item['required-test-count']) + ' tests)' not in (scope / 'contract-tests.txt').read_text():
            raise ValueError('Incomplete public contract tests')
        print('T13 prequalification JDK ' + str(major) + ' / ' + execution + ' recorder', flush=True)
        foundation.run_logged(item['recorder-command'], scope / 'recorder.txt', cwd=root, timeout=1800)
        reports = foundation.collect_reports(root, scope / 'observations', 'text', execution)
        foundation.write_json(scope / 'collected-reports.json', reports)
        foundation.require_staged_build(root, contract, base / 'build-inputs.json')
        results.append({'environment': profile['id'], 'native-execution-profile': execution,
            'facade-execution-profile': 'IN_PROCESS', 'contract-tests': foundation.reference(root, scope / 'contract-tests.txt'),
            'reports': foundation.reference(root, scope / 'collected-reports.json')})
        foundation.write_json(output / 'completed-observations.json', results)
        print('T13 prequalification JDK ' + str(major) + ' / ' + execution + ' four chains passed', flush=True)
    after = foundation.observe_environment(root, profile['identity']['image'], helper,
        output / ('jdk' + str(major) + '-environment-after'), profile, base / 'harness')
    foundation.write_json(output / ('jdk' + str(major) + '-environment-after/environment.json'), after)
    if before != after:
        raise ValueError('Environment or tool identities changed')
foundation.require_staged_build(root, contract, base / 'build-inputs.json')
foundation.write_json(output / 'result.json', {'status': 'qualification-only', 'completed-combinations': len(results),
    'plan': foundation.reference(root, output / 'plan.json'), 'staged-build': foundation.reference(root, output / 'build-inputs.json'),
    'observations': results, 'certification-published': False})
print('All eight actual text qualification combinations passed; no certification was published.', flush=True)
