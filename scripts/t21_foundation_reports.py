"""Collect T21 from complete named execution and fresh identity-bound observations.

The qualified T03 PDF profile owns the four document chains. The separate
contract chain owns the production Worker, public lifetime and launch controls.
The policy-qualification JVM and unavailable Java Unix APIs are never relabeled
as production Worker experiments.
"""
import importlib.util
import json
import os
from pathlib import Path
import re
import tempfile

from t13_foundation_reports import RetainedFiles, parse_properties
from t20_foundation_reports import compare_originals

PROFILE = 'T21-hardened-worker'
CHAINS = ('syntax', 'standards', 'semantic', 'visual', 'contract')
PRODUCTS = ('native-success', 'native-owned-stream', 'native-before-failure', 'facade-stream', 'facade-before-failure')
INVENTORY = 'capabilities/profiles/T21-hardened-worker/coverage.json'
TESTS = 'capabilities/profiles/T21-hardened-worker/mandatory-tests.txt'


def require(condition, message):
    if not condition:
        raise ValueError('T21 ' + message)


def foundation():
    spec = importlib.util.spec_from_file_location('t21_foundation_driver', Path(__file__).with_name('t03-foundation.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def mandatory_tests(root):
    lines = (root / TESTS).read_text().splitlines()
    require(lines and len(lines) == len(set(lines)) and all(re.fullmatch(r'[A-Za-z0-9_.]+#[A-Za-z0-9_]+(?:\[(?:IN_PROCESS|HARDENED_WORKER)\])?', name)
            for name in lines), 'duplicate, malformed or missing mandatory case inventory')
    inventory = json.loads((root / INVENTORY).read_text())
    require(inventory['profile'] == PROFILE and inventory['mandatory-test-count'] == len(lines), 'mandatory inventory/count mismatch')
    require({name.split('#')[0] for name in lines} == set(inventory['test-classes']), 'mandatory class inventory mismatch')
    shared = [name for name in lines if '.WorkflowExecutionProfileContractTest#' in name]
    require(shared and {name.replace('[IN_PROCESS]', '').replace('[HARDENED_WORKER]', '') for name in shared if name.endswith('[IN_PROCESS]')}
            == {name.replace('[IN_PROCESS]', '').replace('[HARDENED_WORKER]', '') for name in shared if name.endswith('[HARDENED_WORKER]')},
            'shared public contract must execute in both actual profiles')
    return set(lines), inventory


def closed_observations(root, files):
    _, inventory = mandatory_tests(root)
    require(json.loads(files.authorities.read(root / INVENTORY)) == inventory, 'coverage authority changed')
    observed = files.properties(files.directory / 'observations.properties')
    cases = inventory['cases']
    names = {key[:-7] for key in observed if key.endswith('.result')}
    require(names == set(cases) and observed.get('case-count') == str(len(cases))
            and observed.get('execution-profile') == 'HARDENED_WORKER', 'missing or mislabeled public case')
    allowed = {'case-count', 'execution-profile'}
    for name, expected in cases.items():
        require(observed.get(name + '.result') == observed.get(name + '.cleanup') == 'pass', 'nonpassing case: ' + name)
        allowed.update((name + '.result', name + '.cleanup'))
        require(observed.get(name + '.code') == expected.get('code'), 'wrong owning negative: ' + name)
        if expected.get('code'):
            allowed.update((name + '.code', name + '.diagnostic'))
            safe_diagnostic(observed.get(name + '.diagnostic', ''))
        for key, value in expected.get('observations', {}).items():
            allowed.add(name + '.' + key)
            require(observed.get(name + '.' + key) == value, 'missing declared observation: ' + name + '.' + key)
    require(set(observed) == allowed, 'unexpected public observations')
    require(files.properties(files.directory / 'facade-observations.properties') == inventory['facade-observations'],
            'actual IN_PROCESS Facade outcome/ownership/receipt changed')
    for name in ('checked-failure', 'cancelled', 'deadline', 'caller-failure', 'native-later', 'facade-later'):
        expected = bytes((1, 2, 3)) if name == 'facade-later' else bytes((17, 18, 19))
        require(files.read(files.directory / (name + '.sentinel')) == expected, 'Target sentinel changed: ' + name)
    return inventory


def safe_diagnostic(value):
    require(value and all(word not in value for word in ('private', '.pdf', 'PDFBox', 'Exception', '/workspace', '/tmp/')),
            'missing or unsafe diagnostic')


def validate_inputs(root, files):
    module = foundation()
    authority = root / 'scripts/t21-evidence-pin.properties'
    pin = files.authorities.properties(authority)
    require(pin and any(name.startswith('.build-cache/') for name in pin), 'missing pinned producer identities')
    for name, expected in pin.items():
        path = root / name
        path.resolve(strict=True).relative_to(root)
        actual = module.sha256(path) if name.startswith('.build-cache/') else files.authorities.digest(path)
        require(actual == expected, 'frozen producer/configuration changed: ' + name)
    expected = {**pin, 'scripts/t21-evidence-pin.properties': files.authorities.digest(authority)}
    for phase in ('before', 'after'):
        require(files.properties(files.directory / ('identities-' + phase + '.properties')) == expected,
                'observed producer/configuration identity changed: ' + phase)


def bound_execution(root, scope, contract, receipt, identities, helper, module):
    import yaml
    config = json.loads((scope / 'execution.yaml').read_text())
    match = re.fullmatch(r'jdk(8|11|17|21)-hardened_worker', scope.name)
    require(match is not None, 'scope is not a declared actual Worker tuple')
    profiles = yaml.safe_load((root / contract['environments']).read_text())['profiles']
    selected = [p for p in profiles if p['identity']['jdk-major'] == int(match[1])]
    require(len(selected) == 1, 'missing exact approved environment')
    profile = selected[0]
    environment_path = scope.parent / ('jdk' + match[1] + '-environment') / 'environment.yaml'
    environment_path.resolve(strict=True).relative_to(root)
    environment = json.loads(environment_path.read_text())
    require(environment.get('schema-version') == 1 and environment.get('profile') == profile['id'], 'environment association changed')
    module.require_environment(profile['identity'], environment.get('identity'))
    cp = ':'.join('/workspace/' + path.relative_to(root).as_posix()
                  for path in module.certification_classpath(root, contract, receipt))
    plan = module.execution_plan(root, scope, profile['identity']['image'], helper, cp,
                                 module.certification_case('worker'), 'HARDENED_WORKER')
    expected = {'schema-version': 1, 'candidate-sha256': identities['Candidate'],
        'environment-sha256': module.sha256(environment_path), 'acceptance-profile': PROFILE,
        'execution-profile': 'HARDENED_WORKER', 'command': plan['recorder-command'], 'java-options': plan['java-options'],
        'locale': 'en_US / C.UTF-8', 'timezone': 'UTC', 'settings': plan['settings'],
        'support-commands': plan['support-commands'],
        'inputs': module.certification_inputs(root, contract, receipt, module.certification_case('worker'))}
    require(config == expected, 'candidate, environment, launcher/control command or complete input binding changed')
    return profile, environment, cp


def require_contract_tests(root, scope, plan):
    tests, _ = mandatory_tests(root)
    require(len(tests) == plan['required-test-count'], 'required case count differs from runner')
    require(json.loads((scope / 'contract-tests-command.json').read_text()) == plan['contract-tests-command'], 'actual test command changed')
    transcript = (scope / 'contract-tests.txt').read_text()
    cases = re.findall(r'(?m)^T21 CASE (\S+) = (PASS|FAIL)$', transcript)
    require(len(cases) == len(tests) and {name for name, result in cases} == tests and all(result == 'PASS' for _, result in cases),
            'missing, duplicate, failed or undeclared mandatory execution')
    count = str(len(tests))
    require(transcript.startswith('JUnit version 4.13.2\n') and transcript.count('T21 required execution: tests=' + count
            + ', failures=0, ignored=0, assumptions=0, duplicates=0\n') == 1
            and len(re.findall(r'(?m)^OK \(' + count + r' tests\)$', transcript)) == 1 and 'FAILURES!!!' not in transcript,
            'nonpassing or skipped complete execution')
    files = RetainedFiles(scope / 'boundary-observations')
    require(files.result == {'profile': 'T21-hardened-worker-boundary', 'result': 'pass', 'required-case-count': count,
                            'retained-files-sha256': files.expected[files.directory / 'retained-files.sha256']}, 'missing original boundary retention')
    require(transcript.count('T21 boundary retained-sha256=' + files.expected[files.directory / 'retained-files.sha256'] + '\n') == 1,
            'boundary files are not bound to the observed mandatory execution')
    return files


def boundary_observations(root, files, environment):
    directory = files.directory
    actual = files.properties(directory / 'launcher/actual.properties')
    identity = environment['identity']
    runtime = environment['java-runtime']
    require(runtime['build'] == identity['jdk-build'] and runtime['vendor'], 'missing actual JVM runtime properties')
    for key, expected in {'java-vendor': runtime['vendor'], 'java-build': runtime['build'],
            'java-sha256': identity['java-sha256'], 'prlimit-executable': '/usr/bin/prlimit',
            'prlimit-sha256': environment['worker-launcher']['sha256'], 'heap-bytes': '67108864',
            'message-bytes': '1048576', 'owned-memory-bytes': '268435456', 'cpu-seconds': '300',
            'open-files': '64', 'root-mode': '0700'}.items():
        require(actual.get(key) == expected, 'actual launcher/configuration changed: ' + key)
    require(re.fullmatch(r'[1-9][0-9]*', actual.get('pid', '')) and re.fullmatch(r'/tmp/[^\n]+/\.folio-pdf-workflow-[^/\n]+', actual.get('root', '')),
            'missing actual separate process/root witness')
    require(actual['java-executable'] in ('/opt/java/openjdk/bin/java', '/opt/java/openjdk/jre/bin/java'), 'unexpected actual Java executable')
    argv = files.read(directory / 'launcher/cmdline.txt').decode().splitlines()
    cp = argv[argv.index('-cp') + 1]
    expected = [actual['java-executable'], '-Xmx67108864', '-XX:MaxDirectMemorySize=67108864', '-Xss1m']
    if identity['jdk-major'] >= 17:
        expected.append('-Djava.security.manager=allow')
    expected += ['-Djava.io.tmpdir=' + actual['root'], '-cp', cp, 'net.zerocloud.pdf.HardenedWorkerMain', actual['root'], '1048576', '268435456']
    require(argv == expected, 'actual Worker argument list changed')
    closure = files.properties(directory / 'launcher/classpath.properties')
    require(list(closure) == cp.split(':') and len(closure) in (8, 14), 'actual exact Worker closure changed')
    for name, expected_hash in closure.items():
        require(name.startswith('/workspace/target/foundation-0.1.0/') and name.endswith('.jar')
                and all(word not in Path(name).name for word in ('tests', 'acceptance', 'junit', 'migration')),
                'application/acceptance code entered the Worker')
        path = root / name.removeprefix('/workspace/')
        require(files.authorities.digest(path) == expected_hash, 'actual Worker JAR identity changed')
    require(files.read(directory / 'launcher/environment.bin') == b'', 'child environment is not empty')
    inventories = files.properties(directory / 'launcher/inventories.properties')
    require(inventories == {'document-worker-classes.entries': '754',
        'document-worker-classes.sha256': '99cba401304fe7d1bbc279f8afd1cbac30bf6dc0609e2747966c276b51933297',
        'provider-contract-worker-classes.entries': '25',
        'provider-contract-worker-classes.sha256': '56340dc06714414d32db2af86d87db696cbe05de93b4ece4571bb3b412a76f16'},
        'actual first-party class inventory changed')
    limits = files.read(directory / 'launcher/limits.txt').decode()
    require(re.search(r'(?m)^Max cpu time\s+300\s+300\s+seconds\s*$', limits)
            and re.search(r'(?m)^Max open files\s+64\s+64\s+files\s*$', limits), 'effective kernel process controls changed')
    observations = files.properties(directory / 'launcher/observations.properties')
    fixed = dict.fromkeys(('permitted-filesystem', 'permitted-descendant', 'permitted-inet', 'permitted-unix',
        'permitted-hard-link-access', 'permitted-symbolic-link-access', 'denied-hard-link-access', 'denied-symbolic-link-access',
        'worker-separate', 'owner-only-root', 'owner-only-files', 'denied-filesystem-read-write', 'denied-descendant', 'denied-inet',
        'denied-deep-reflection', 'denied-native-load', 'closed-classpath', 'terminated-before-cleanup', 'owned-root-removed'), 'pass')
    fixed['worker-unix'] = 'security-denied' if identity['jdk-major'] >= 17 else 'java-api-unavailable'
    fixed['owned-file-count'] = observations.get('owned-file-count', '')
    require(re.fullmatch(r'[1-9][0-9]*', fixed['owned-file-count']), 'no actual owner-only Worker file was observed')
    require(observations == fixed and files.read(directory / 'launcher/unix-control.txt') == b'unix-permitted=pass\n',
            'missing paired permitted operation or actual isolation outcome')
    policy = files.properties(directory / 'policy/policy.properties')
    expected_policy = {'installed-manager': 'net.zerocloud.pdf.HardenedWorkerSecurityManager',
        **dict.fromkeys(('permitted-hard-link', 'permitted-symbolic-link', 'denied-hard-link', 'denied-symbolic-link', 'denied-unix-permission'), 'pass'),
        'unix-java-api': 'available' if identity['jdk-major'] >= 17 else 'unavailable',
        'denied-unix-listen': 'pass' if identity['jdk-major'] >= 17 else 'api-unavailable'}
    require(policy == expected_policy, 'product policy qualification changed or unavailable API mislabeled')
    for group, codes in (('prerequisites', {'os.name': 'WORKER_UNAVAILABLE', 'java.home': 'WORKER_UNAVAILABLE'}),
                         ('faults', {'crash': 'WORKER_TERMINATED', 'malformed-response': 'WORKER_PROTOCOL_REJECTED'})):
        observed = files.properties(directory / group / 'observations.properties')
        for case, code in codes.items():
            require(observed.get(case + '.code') == code and observed.get(case + '.receipts') == 'result:NOT_ATTEMPTED:false'
                    and observed.get(case + '.cleanup') == 'pass' and observed.get(case + '.sentinel') == 'unchanged',
                    'missing stable failure, receipt, sentinel or cleanup: ' + case)
            safe_diagnostic(observed.get(case + '.diagnostic', ''))
            if group == 'faults':
                require(observed.get(case + '.child-terminated') == 'true', 'fault cleanup preceded child termination')
    elapsed = files.properties(directory / 'elapsed/observations.properties')
    safe_diagnostic(elapsed.get('diagnostic', ''))
    require(elapsed == {'code': 'ELAPSED_TIME_LIMIT_EXCEEDED', 'diagnostic': elapsed['diagnostic'],
        'receipts': 'result:NOT_ATTEMPTED:false', 'configured-elapsed-ms': '3000', 'child-terminated': 'true',
        'child-terminated-before-cleanup': 'true', 'cleanup': 'pass', 'sentinel': 'unchanged'},
        'hard elapsed termination, ordered teardown or target protection changed')
    files.verify()
    return actual


def prerequisite_observations(scope):
    require((scope / 'support-0.txt').read_text().strip() == 'T21 absent-prlimit control passed', 'actual missing-prlimit experiment did not pass')
    files = RetainedFiles(scope / 'prerequisite-control')
    require(files.result == {'profile': 'T21-unavailable-prlimit', 'result': 'pass',
        'retained-files-sha256': files.expected[files.directory / 'retained-files.sha256']}, 'unavailable-launch retention changed')
    observed = files.properties(files.directory / 'observations.properties')
    safe_diagnostic(observed.get('diagnostic', ''))
    require(observed == {'code': 'WORKER_UNAVAILABLE', 'diagnostic': observed['diagnostic'], 'receipts': 'result:NOT_ATTEMPTED:false',
                        'cleanup': 'pass', 'sentinel': 'unchanged', 'prlimit-executable': 'false'}
            and files.read(files.directory / 'sentinel') == bytes((17, 18, 19)), 'unavailable launcher did not preserve Targets/ownership')
    return files


def compare_boundary(original, fresh, old_actual, new_actual):
    old_names = {path.relative_to(original.directory).as_posix() for path in original.expected}
    new_names = {path.relative_to(fresh.directory).as_posix() for path in fresh.expected}
    require(old_names == new_names, 'missing/extra original boundary witnesses')
    def normalized(data, files, actual):
        text = data.decode().replace(actual['root'], 'T21-ROOT')
        text = text.replace(str(files.directory), 'T21-BOUNDARY')
        # Child records use the container path while the collector uses the host path.
        relative = files.directory.as_posix().split('/capabilities/', 1)[1]
        text = text.replace('/workspace/capabilities/' + relative, 'T21-BOUNDARY')
        return text
    for name in sorted(old_names - {'result.properties', 'retained-files.sha256'}):
        left, right = original.read(original.directory / name), fresh.read(fresh.directory / name)
        if name.endswith('.properties'):
            a = parse_properties(normalized(left, original, old_actual).encode())
            b = parse_properties(normalized(right, fresh, new_actual).encode())
            if name == 'launcher/actual.properties':
                a.pop('pid'); b.pop('pid')
            require(a == b, 'original boundary properties differ from live observation: ' + name)
        elif name.endswith('.txt'):
            require(normalized(left, original, old_actual) == normalized(right, fresh, new_actual),
                    'original raw boundary findings differ from live observation: ' + name)
        else:
            require(left == right, 'original boundary data differs from live observation: ' + name)
    original.verify(); fresh.verify()


def live_replay(root, run):
    import yaml
    module = foundation()
    contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
    receipt = module.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
    scope = run.parent
    replay = Path(tempfile.mkdtemp(prefix='live-collection-', dir=scope))
    identities = module.candidate_identities(root, root / 'capabilities/foundation-evidence.yaml',
        {'schema-version': 1, 'candidate': receipt['candidate'], 'environments': [], 'certifications': []}, replay / 'candidate-identity.txt')
    helper = Path(os.environ['FOLIO_HARFBUZZ_HELPER']).resolve(strict=True)
    profile, original_environment, cp = bound_execution(root, scope, contract, receipt, identities, helper, module)
    harness = root / 'target/foundation-0.1.0/harness'
    case = module.certification_case('worker')
    original_plan = module.execution_plan(root, scope, profile['identity']['image'], helper, cp, case, 'HARDENED_WORKER')
    boundary = require_contract_tests(root, scope, original_plan)
    original_actual = boundary_observations(root, boundary, original_environment)
    support = prerequisite_observations(scope)
    before = module.observe_environment(root, profile['identity']['image'], helper, replay / 'environment-before', profile, harness)
    require(before == original_environment, 'original environment/tool/native/launcher identity differs from live observation')
    plan = module.execution_plan(root, replay, profile['identity']['image'], helper, cp, case, 'HARDENED_WORKER')
    module.write_json(replay / 'command.json', plan['recorder-command'])
    module.write_json(replay / 'contract-tests-command.json', plan['contract-tests-command'])
    module.run_logged(plan['contract-tests-command'], replay / 'contract-tests.txt', cwd=root, timeout=case['contract-timeout'])
    fresh_boundary = require_contract_tests(root, replay, plan)
    fresh_actual = boundary_observations(root, fresh_boundary, before)
    compare_boundary(boundary, fresh_boundary, original_actual, fresh_actual)
    for index, command in enumerate(plan['support-commands']):
        module.run_logged(command, replay / ('support-' + str(index) + '.txt'), cwd=root, timeout=60)
    fresh_support = prerequisite_observations(replay)
    require(support.properties(support.directory / 'observations.properties') == fresh_support.properties(fresh_support.directory / 'observations.properties'),
            'original unsupported-launch outcome differs from live observation')
    module.run_logged(plan['recorder-command'], replay / 'recorder.txt', cwd=root, timeout=case['recorder-timeout'])
    require((replay / 'recorder.txt').read_text().strip() == 'T21 fixed Worker contracts and independent PDF observations passed', 'live recorder did not pass')
    after = module.observe_environment(root, profile['identity']['image'], helper, replay / 'environment-after', profile, harness)
    require(after == before, 'environment/tool/native/launcher identity changed during live replay')
    bound_execution(root, scope, contract, receipt, identities, helper, module)
    module.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
    return replay, plan, module, boundary, fresh_boundary, support, fresh_support


def collect_reports(root, run, execution):
    require(execution == 'HARDENED_WORKER', 'only the actual HARDENED_WORKER tuple is certifiable')
    root, run = Path(root).resolve(), Path(run).resolve()
    files = RetainedFiles(run)
    require(files.result == {'profile': PROFILE, 'native-execution-profile': execution, 'facade-execution-profile': 'IN_PROCESS',
        **dict.fromkeys(CHAINS, 'pass'), 'retained-files-sha256': files.expected[run / 'retained-files.sha256']}, 'missing or mislabeled required chain')
    closed_observations(root, files)
    validate_inputs(root, files)
    replay, plan, module, boundary, fresh_boundary, support, fresh_support = live_replay(root, run)
    fresh = compare_originals(root, files, replay, plan, module)
    reports = module.collect_reports(root, run / 'pdf-outcomes', 'transactions')
    for chain in CHAINS[:-1]:
        for product in PRODUCTS:
            directory = run / product
            result = files.properties(directory / 'result.properties')
            require(result.get(chain) == 'pass' and result.get('input-sha256') == files.expected[directory / 'blank.pdf'], 'actual published PDF chain/input mismatch')
            reports[chain]['products'].append(files.reference(root, directory / 'blank.pdf'))
            reports[chain]['findings'].extend(files.reference(root, path) for path in files.under(directory)
                if chain in path.name or chain == 'standards' and path.parent.name in ('pdfcpu', 'arlington'))
    reports['contract'] = {'chain': 'contract', 'result': 'pass', 'products': [],
        'findings': [files.reference(root, run / 'observations.properties'), files.reference(root, run / 'facade-observations.properties')],
        'negative-controls': [module.reference(root, root / INVENTORY), module.reference(root, root / TESTS),
            module.reference(root, run.parent / 'contract-tests.txt'), boundary.reference(root, boundary.directory / 'faults/observations.properties'),
            boundary.reference(root, boundary.directory / 'prerequisites/observations.properties'), support.reference(root, support.directory / 'observations.properties')]}
    for report in reports.values():
        report['findings'].extend(module.reference(root, run.parent / name) for name in ('contract-tests-command.json', 'contract-tests.txt', 'support-0.txt'))
        report['findings'].extend(files.reference(root, run / name) for name in ('result.properties', 'retained-files.sha256', 'identities-before.properties', 'identities-after.properties'))
        for group in (fresh, boundary, fresh_boundary, support, fresh_support):
            report['findings'].extend(group.reference(root, path) for path in group.expected)
        report['findings'].extend(module.reference(root, path) for path in replay.iterdir() if path.is_file())
        for name in ('environment-before', 'environment-after'):
            module.append_environment_observations(root, replay / name, report)
    files.verify(); files.authorities.verify(); validate_inputs(root, files)
    return reports
