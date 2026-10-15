"""Strict issue-84 Provider collection from original boundaries and fresh execution.

The four PDF chains inspect actual resource-free workload artifacts under the
qualified T03 predicates. The contract chain separately qualifies loopback
payloads, authorization, all frozen cases, and real direct-child observations.
A changed label, producer, manifest or hash seal cannot replace those facts.
"""
import importlib.util
import json
import os
from pathlib import Path
import re
import struct
import tempfile

from t13_foundation_reports import RetainedFiles
from t20_foundation_reports import compare_originals

PROFILE = 'T05-capability-provider-contract'
INVENTORY = 'capabilities/profiles/' + PROFILE + '/coverage.json'
TESTS = 'capabilities/profiles/' + PROFILE + '/mandatory-tests.txt'
PIN = 'scripts/t05-evidence-pin.properties'
CHAINS = ('syntax', 'standards', 'semantic', 'visual', 'contract')
PAYLOAD = bytes((10, 20, 30, 40))
RESPONSE = bytes((40, 30, 20, 10))


def require(condition, message):
    if not condition:
        raise ValueError('T05 ' + message)


def foundation():
    spec = importlib.util.spec_from_file_location('t05_foundation_driver', Path(__file__).with_name('t03-foundation.py'))
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    return module


def mandatory_tests(root):
    lines = (root / TESTS).read_text().splitlines()
    require(lines and len(lines) == len(set(lines)) and all(re.fullmatch(r'[A-Za-z0-9_.]+#[A-Za-z0-9_]+', value) for value in lines),
            'missing, malformed or repeated mandatory test')
    inventory = json.loads((root / INVENTORY).read_text())
    require(inventory['profile'] == PROFILE and inventory['mandatory-test-count'] == len(lines)
            and {line.split('#')[0] for line in lines} == set(inventory['test-classes'])
            and inventory['required-execution-profiles'] == ['IN_PROCESS', 'HARDENED_WORKER']
            and set(inventory['source-criteria']) == set(map(str, range(1, 10))), 'incomplete source/test inventory')
    require(len(inventory['cases']) == 51 and len(inventory['subprocesses']) == 16, 'missing mandatory Provider boundary')
    return set(lines), inventory


def contract_tests(root, scope, plan):
    tests, _ = mandatory_tests(root)
    require(plan['required-test-count'] == len(tests), 'runner count differs from inventory')
    require(json.loads((scope / 'contract-tests-command.json').read_text()) == plan['contract-tests-command'], 'actual contract command changed')
    transcript = (scope / 'contract-tests.txt').read_text()
    observed = re.findall(r'(?m)^T05 CASE (\S+) = (PASS|FAIL)$', transcript)
    require(len(observed) == len(tests) and {name for name, _ in observed} == tests and all(result == 'PASS' for _, result in observed),
            'omitted, duplicate, skipped or failed mandatory execution')
    require(transcript.startswith('JUnit version 4.13.2\n') and transcript.count('T05 required execution: tests=' + str(len(tests))
            + ', failures=0, ignored=0, assumptions=0, duplicates=0\n') == 1
            and len(re.findall(r'(?m)^OK \(' + str(len(tests)) + r' tests\)$', transcript)) == 1
            and 'FAILURES!!!' not in transcript, 'nonpassing complete contract execution')


def require_complete_index(root, inventory):
    import yaml
    profiles = yaml.safe_load((root / 'capabilities/foundation-environments.yaml').read_text())['profiles']
    expected = {(profile['id'], execution) for profile in profiles for execution in ('IN_PROCESS', 'HARDENED_WORKER')}
    observed = [(item['environment'], item['execution-profile']) for item in inventory['certifications'] if item['obligation'] == 'providers']
    require(len(expected) == len(observed) == 8 and set(observed) == expected, 'omitted, duplicate or unexpected Provider certification tuple')


def closed_observations(root, files, execution):
    _, inventory = mandatory_tests(root)
    require(json.loads(files.authorities.read(root / INVENTORY)) == inventory, 'coverage authority changed')
    expected = {'execution-profile': execution, 'case-count': str(len(inventory['cases']))}
    for name, case in inventory['cases'].items():
        expected[name + '.result'] = 'pass'
        if 'code' in case: expected[name + '.code'] = case['code']
        expected.update((name + '.' + key, value) for key, value in case['observations'].items())
    require(files.properties(files.directory / 'observations.properties') == expected,
            'original case, authorization, selected identity, safe failure, receipt or sentinel changed')
    require(files.properties(files.directory / 'facade-observations.properties') == inventory['facade-observations'],
            'actual IN_PROCESS Facade outcome/ownership/receipt changed')
    for name, case in inventory['cases'].items():
        if 'sentinel-sha256' in case['observations']:
            path = files.directory / (name + '.sentinel' if name != 'native-partial-publication' else 'native-later.sentinel')
            require(files.read(path) == PAYLOAD, 'uncommitted Target sentinel changed: ' + name)
    require(files.read(files.directory / 'facade-later.sentinel') == bytes((1, 2, 3)), 'Facade uncommitted Target changed')
    return inventory


def metadata(files):
    facts = files.properties(files.directory / 'metadata.properties')
    expected = {'count': '4'}
    declarations = [('test.t05-unavailable', 'NATIVE', 'UNAVAILABLE', 'CALLER_SUPPLIED'),
                    ('test.t05-remote', 'REMOTE', 'AVAILABLE', 'REMOTE_SERVICE'),
                    ('test.t05-first', 'IN_PROCESS', 'AVAILABLE', 'CALLER_SUPPLIED'),
                    ('test.t05-later', 'IN_PROCESS', 'AVAILABLE', 'CALLER_SUPPLIED')]
    for index, (identity, mode, availability, distribution) in enumerate(declarations):
        values = {'id': identity, 'capabilities': 'conversion.test.t05', 'version': 't05-synthetic-v1', 'mode': mode,
                  'availability': availability, 'distribution': distribution, 'license-spdx': 'Apache-2.0',
                  'license-name': 'Apache License 2.0', 'maximum-input': '4', 'maximum-output': '4', 'maximum-duration': 'PT5S'}
        expected.update(('provider.' + str(index) + '.' + key, value) for key, value in values.items())
    require(facts == expected, 'actual registration/version/license/distribution/limit facts changed')


def preview(root, files, inventory):
    require(files.properties(files.directory / 'preview/facade-observations.properties') == inventory['facade-observations']
            and files.read(files.directory / 'preview/facade-later.sentinel') == bytes((1, 2, 3)), 'actual Preview public outcomes/ownership/receipt changed')
    actual = files.properties(files.directory / 'preview-execution.properties')
    module = foundation(); products = root / 'target/foundation-0.1.0/artifacts'
    jars = [path for path in products.glob('*.jar') if not path.name.endswith(('-sources.jar', '-javadoc.jar')) and path.name != 'pdf-migration-itext7-0.1.0.jar']
    jars += [root / 'target/foundation-0.1.0/harness/acceptance.jar', *list((root / 'target/foundation-0.1.0/harness/lib').glob('*.jar'))]
    cp = ':'.join('/workspace/' + path.relative_to(root).as_posix() for path in sorted(jars))
    inside = '/workspace/capabilities/' + files.directory.as_posix().split('/capabilities/', 1)[1]
    arguments = ['-Xmx256m', '-Duser.language=en', '-Duser.country=US', '-Duser.timezone=UTC', '-cp', cp,
                 'net.zerocloud.pdf.acceptance.T05PreviewFacadeCommand', inside + '/preview']
    expected = {'classpath': cp, 'java': actual.get('java'), 'main': arguments[6], 'exit': '0', 'execution-profile': 'IN_PROCESS',
                'artifact-sha256': module.sha256(products / 'pdf-migration-itext7-preview-0.1.0.jar'), 'argument-count': str(len(arguments)),
                **{'argument.' + str(index): value for index, value in enumerate(arguments)}}
    require(actual == expected and actual['java'] in ('/opt/java/openjdk/bin/java', '/opt/java/openjdk/jre/bin/java'),
            'actual isolated Preview artifact/command closure changed')
    require(files.text(files.directory / 'preview-process.txt').strip() == 'T05 Preview Facade public outcomes passed', 'Preview execution omitted or failed')


def endpoint(files, build):
    actual = files.properties(files.directory / 'endpoint.properties')
    fixed = {'address': '127.0.0.1', 'requests': '2', 'disclosed-bytes': '8', 'adapter-java-build': build,
             'protocol': 't05-synthetic-length-framed-v1'}
    require(set(actual) == set(fixed) | {'port', 'adapter-pid', 'parent-pid'} and all(actual.get(key) == value for key, value in fixed.items()),
            'endpoint identity, actual requests/disclosure or protocol changed')
    require(re.fullmatch(r'[1-9][0-9]*', actual['port']) and 0 < int(actual['port']) < 65536
            and re.fullmatch(r'[1-9][0-9]*', actual['parent-pid']) and actual['adapter-pid'] == actual['parent-pid'],
            'loopback endpoint or actual parent broker identity missing')
    for index in range(2):
        require(files.read(files.directory / ('endpoint-request-' + str(index) + '.bin')) == PAYLOAD
                and files.read(files.directory / ('endpoint-response-' + str(index) + '.bin')) == RESPONSE,
                'exact authorized endpoint payload/result changed')
    require(len([path for path in files.expected if path.name.startswith('endpoint-request-')]) == 2
            and len([path for path in files.expected if path.name.startswith('endpoint-response-')]) == 2,
            'unexpected endpoint transfer; consent leak or registration disclosure')


def subprocesses(files, inventory, build):
    for mode, fixed in inventory['subprocesses'].items():
        directory = files.directory / 'subprocess'
        lifecycle = files.properties(directory / (mode + '-lifecycle.properties'))
        started = fixed['direct-child-state'] == 'terminated'
        allowed = set(fixed) | {'mode', 'elapsed-millis'} | ({'direct-child-pid'} if started else set())
        require(set(lifecycle) == allowed and lifecycle['mode'] == mode
                and all(lifecycle.get(key) == value for key, value in fixed.items()), 'process lifecycle category/cleanup changed: ' + mode)
        require(re.fullmatch(r'0|[1-9][0-9]*', lifecycle['elapsed-millis']) and 0 <= int(lifecycle['elapsed-millis']) < 6000,
                'process elapsed bound failed: ' + mode)
        command = files.properties(directory / (mode + '-command.properties'))
        max_input = '3' if mode == 'pre-start-input' else '1048576' if mode == 'stalled-input' else '4'
        max_output = '3' if mode == 'first-excess-output' else '4'
        require(command['maximum-input'] == max_input and command['maximum-output'] == max_output
                and command['maximum-duration'] == 'PT5S' and command['mode'] == 'SUBPROCESS', 'process operating metadata changed: ' + mode)
        count = 1 if mode == 'startup' else 6
        require(command['argument-count'] == str(count) and set(command) ==
                {'argument-count', 'maximum-input', 'maximum-output', 'maximum-duration', 'mode'} | {'argument.' + str(i) for i in range(count)},
                'actual process command arguments omitted: ' + mode)
        # Paths are recorded by the container, while the collector may run on the host.
        suffix = files.directory.as_posix().split('/capabilities/', 1)[-1]
        inside = '/workspace/capabilities/' + suffix
        caller = directory / (mode + '-root/caller-owned.sentinel')
        require(files.read(caller) == PAYLOAD, 'caller-owned process root contents changed: ' + mode)
        if mode == 'startup':
            require(command['argument.0'] == inside + '/subprocess/startup-root/missing-program', 'startup control command changed')
        else:
            require(command['argument.0'].endswith('/bin/java') and command['argument.1'] == '-cp'
                    and command['argument.2'] and command['argument.3'] == 'net.zerocloud.pdf.acceptance.T05ProcessFixture'
                    and command['argument.4'] == ('echo' if mode == 'first-excess-output' else mode)
                    and command['argument.5'] == inside + '/subprocess/' + mode + '-child.properties', 'project-authored framed fixture omitted or substituted')
        actual_path = directory / (mode + '-child.properties')
        require((actual_path in files.expected) == started, 'pre-start rejection/actual process start changed: ' + mode)
        if started:
            actual = files.properties(actual_path)
            require(re.fullmatch(r'[1-9][0-9]*', lifecycle['direct-child-pid']) and actual.get('pid') == lifecycle['direct-child-pid']
                    and actual.get('mode') == ('echo' if mode == 'first-excess-output' else mode)
                    and actual.get('java-version') == build, 'actual direct-child identity missing: ' + mode)
            prefix = inside + '/subprocess/' + mode + '-root/.folio-pdf-provider-'
            require(actual.get('staging', '').startswith(prefix) and re.fullmatch(r'[A-Za-z0-9_-]+', actual['staging'][len(prefix):]),
                    'private per-run staging identity missing: ' + mode)
            keys = {'pid', 'mode', 'staging', 'java-version'}
            if mode != 'stalled-input':
                keys |= {'capability', 'input-length', 'input-sha256'}
                require(actual.get('capability') == 'conversion.test.t05' and actual.get('input-length') == '4'
                        and actual.get('input-sha256') == inventory['cases']['remote-exact-transfer']['observations']['input-sha256'], 'request frame identity changed: ' + mode)
            require(set(actual) == keys, 'unexpected process findings: ' + mode)
        frame_path = Path(str(actual_path) + '.response.bin')
        framed = mode in ('echo', 'first-excess-output', 'wrong-magic', 'wrong-version', 'negative-length', 'oversized-length', 'truncated-header', 'truncated-payload', 'trailing-output')
        require((frame_path in files.expected) == framed, 'raw process response frame missing or invented: ' + mode)
        if framed:
            raw = struct.pack('>I', 0x12345678 if mode == 'wrong-magic' else 0x4f504452)
            if mode != 'truncated-header':
                raw += struct.pack('>Iq', 2 if mode == 'wrong-version' else 1, -1 if mode == 'negative-length' else 5 if mode == 'oversized-length' else 4)
                if mode not in ('negative-length', 'oversized-length'): raw += PAYLOAD[:-1] if mode == 'truncated-payload' else PAYLOAD
                if mode == 'trailing-output': raw += bytes((99,))
            require(files.read(frame_path) == raw, 'original response frame differs from qualified malformed/successful boundary: ' + mode)
        descendant = Path(str(actual_path) + '.descendant.properties')
        require((descendant in files.expected) == (mode == 'inherited-pipe'), 'inherited open-pipe control omitted or relabeled')
        if mode == 'inherited-pipe':
            child = files.properties(descendant)
            require(set(child) == {'pid', 'mode', 'staging', 'java-version'} and child['mode'] == 'hold-pipe'
                    and child['java-version'] == build and re.fullmatch(r'[1-9][0-9]*', child['pid'])
                    and child['pid'] != lifecycle['direct-child-pid'] and child['staging'] == files.properties(actual_path)['staging'],
                    'inherited descriptor/descendant observation missing')


def validate_inputs(root, files):
    module = foundation(); authority = root / PIN; pin = files.authorities.properties(authority)
    require(pin and any(name.startswith('.build-cache/') for name in pin), 'pinned original producer/tool inputs missing')
    for name, expected in pin.items():
        path = root / name; path.resolve(strict=True).relative_to(root)
        actual = module.sha256(path) if name.startswith('.build-cache/') else files.authorities.digest(path)
        require(actual == expected, 'frozen producer/configuration changed: ' + name)
    expected = {**pin, PIN: files.authorities.digest(authority)}
    for phase in ('before', 'after'):
        require(files.properties(files.directory / ('identities-' + phase + '.properties')) == expected,
                'actual input/tool/configuration identity mismatch: ' + phase)


def bound_execution(root, scope, contract, receipt, identities, helper, module, execution):
    import yaml
    match = re.fullmatch(r'jdk(8|11|17|21)-' + execution.lower(), scope.name)
    require(match is not None, 'scope is not a declared actual Provider tuple')
    selected = [p for p in yaml.safe_load((root / contract['environments']).read_text())['profiles'] if p['identity']['jdk-major'] == int(match[1])]
    require(len(selected) == 1, 'missing exact approved environment'); profile = selected[0]
    path = scope.parent / ('jdk' + match[1] + '-environment/environment.yaml'); path.resolve(strict=True).relative_to(root)
    environment = json.loads(path.read_text())
    require(environment.get('schema-version') == 1 and environment.get('profile') == profile['id'], 'environment association changed')
    module.require_environment(profile['identity'], environment.get('identity'))
    cp = ':'.join('/workspace/' + path.relative_to(root).as_posix() for path in module.certification_classpath(root, contract, receipt))
    case = module.certification_case('providers'); plan = module.execution_plan(root, scope, profile['identity']['image'], helper, cp, case, execution)
    expected = {'schema-version': 1, 'candidate-sha256': identities['Candidate'], 'environment-sha256': module.sha256(path),
                'acceptance-profile': PROFILE, 'execution-profile': execution, 'command': plan['recorder-command'], 'java-options': plan['java-options'],
                'locale': 'en_US / C.UTF-8', 'timezone': 'UTC', 'settings': plan['settings'], 'inputs': module.certification_inputs(root, contract, receipt, case)}
    require(json.loads((scope / 'execution.yaml').read_text()) == expected, 'candidate/environment/command or full staged input closure changed')
    return profile, environment, cp, plan


def qualify(root, files, execution, build):
    inventory = closed_observations(root, files, execution); metadata(files); endpoint(files, build); subprocesses(files, inventory, build); preview(root, files, inventory); validate_inputs(root, files)
    return inventory


def normalize_volatile(name, left, right, normalized, old, new):
    if name.endswith('.properties') and isinstance(left, dict) and isinstance(right, dict):
        for values in (left, right):
            if name.endswith('-lifecycle.properties'):
                values.pop('elapsed-millis', None); values.pop('direct-child-pid', None)
            if name.endswith('-child.properties') or name.endswith('.descendant.properties'):
                values.pop('pid', None)
                values['staging'] = re.sub(r'\.folio-pdf-provider-[A-Za-z0-9_-]+$', '.folio-pdf-provider-OWNED', values['staging'])
            if name == 'endpoint.properties':
                values.pop('port'); values.pop('adapter-pid'); values.pop('parent-pid')
    return left, right


def live_replay(root, run, execution):
    import yaml
    module = foundation(); contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
    receipt = module.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
    scope = run.parent; replay = Path(tempfile.mkdtemp(prefix='live-collection-', dir=scope))
    identities = module.candidate_identities(root, root / 'capabilities/foundation-evidence.yaml',
            {'schema-version': 1, 'candidate': receipt['candidate'], 'environments': [], 'certifications': []}, replay / 'candidate-identity.txt')
    helper = Path(os.environ['FOLIO_HARFBUZZ_HELPER']).resolve(strict=True)
    profile, environment, cp, original_plan = bound_execution(root, scope, contract, receipt, identities, helper, module, execution)
    contract_tests(root, scope, original_plan)
    harness = root / 'target/foundation-0.1.0/harness'; case = module.certification_case('providers')
    before = module.observe_environment(root, profile['identity']['image'], helper, replay / 'environment-before', profile, harness)
    require(before == environment, 'actual environment/tool/native identity differs from original observation')
    plan = module.execution_plan(root, replay, profile['identity']['image'], helper, cp, case, execution)
    module.write_json(replay / 'command.json', plan['recorder-command']); module.write_json(replay / 'contract-tests-command.json', plan['contract-tests-command'])
    module.run_logged(plan['contract-tests-command'], replay / 'contract-tests.txt', cwd=root, timeout=case['contract-timeout']); contract_tests(root, replay, plan)
    module.run_logged(plan['recorder-command'], replay / 'recorder.txt', cwd=root, timeout=case['recorder-timeout'])
    require((replay / 'recorder.txt').read_text().strip() == 'T05 actual Provider boundaries and independent PDF observations passed', 'fresh Provider recorder did not pass')
    after = module.observe_environment(root, profile['identity']['image'], helper, replay / 'environment-after', profile, harness)
    require(after == before, 'actual environment/tool/native changed during replay')
    bound_execution(root, scope, contract, receipt, identities, helper, module, execution)
    module.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
    return replay, plan, module, environment


def collect_reports(root, run, execution):
    require(execution in ('IN_PROCESS', 'HARDENED_WORKER'), 'an explicit actual Workflow profile is required')
    root, run = Path(root).resolve(), Path(run).resolve(); files = RetainedFiles(run)
    require(files.result == {'profile': PROFILE, 'native-execution-profile': execution, 'facade-execution-profile': 'IN_PROCESS',
            **dict.fromkeys(CHAINS, 'pass'), 'retained-files-sha256': files.expected[run / 'retained-files.sha256']}, 'missing, mislabeled or nonpassing required chain')
    # Preflight closed observations before any expensive replay; build is observed in the original endpoint record.
    build = files.properties(run / 'endpoint.properties')['adapter-java-build']; inventory = qualify(root, files, execution, build)
    replay, plan, module, environment = live_replay(root, run, execution)
    require(build == environment['identity']['jdk-build'], 'original adapter runtime differs from the approved actual environment')
    fresh = RetainedFiles(replay / 'observations'); qualify(root, fresh, execution, build)
    compare_originals(root, files, replay, plan, module, normalize_volatile)
    reports = module.collect_reports(root, run / 'pdf-controls', 'transactions')
    for chain in CHAINS[:-1]:
        for product in inventory['pdf-products']:
            directory = run / product; observed = files.properties(directory / 'result.properties')
            require(observed.get(chain) == 'pass' and observed.get('input-sha256') == files.expected[directory / 'blank.pdf'], 'actual workload PDF chain/input mismatch')
            reports[chain]['products'].append(files.reference(root, directory / 'blank.pdf'))
            reports[chain]['findings'].extend(files.reference(root, path) for path in files.under(directory)
                    if chain in path.name or chain == 'standards' and path.parent.name in ('pdfcpu', 'arlington'))
            if chain == 'standards':
                reports[chain]['negative-controls'].extend(files.reference(root, path) for path in files.under(directory)
                        if path.name.startswith(('negative-', 'control-')))
            if chain == 'visual':
                reports[chain]['findings'].extend(files.reference(root, path) for path in files.under(directory) if path.suffix == '.png')
    reports['contract'] = {'chain': 'contract', 'result': 'pass', 'products': [], 'findings': [],
                          'negative-controls': [files.reference(root, run / 'observations.properties'), module.reference(root, root / INVENTORY), module.reference(root, root / TESTS)]}
    for report in reports.values():
        report['findings'].extend(module.reference(root, run.parent / name) for name in ('contract-tests-command.json', 'contract-tests.txt'))
        for group in (files, fresh): report['findings'].extend(group.reference(root, path) for path in group.expected)
        report['findings'].extend(module.reference(root, path) for path in replay.iterdir() if path.is_file())
        for name in ('environment-before', 'environment-after'): module.append_environment_observations(root, replay / name, report)
    files.verify(); fresh.verify(); validate_inputs(root, files)
    return reports
