"""Collect T20's five chains, validating original bytes and a fresh live replay.

The qualified T03 PDF profile is reused explicitly for resource-free published
outcomes. The separate contract chain owns enforcement and lifetime predicates.
Aggregate labels, a resealed manifest or a changed producer are never an oracle.
"""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

from t13_foundation_reports import RetainedFiles, parse_properties

PROFILE = 'T20-hostile-input-limits'
CHAINS = ('syntax', 'standards', 'semantic', 'visual', 'contract')
PRODUCTS = ('native-first', 'native-second', 'native-stream-high-water', 'native-before-failure',
            'facade-stream', 'facade-before-failure', 'pdf-outcomes/native', 'pdf-outcomes/facade')


def require(condition, message):
    if not condition:
        raise ValueError('T20 ' + message)


def foundation():
    spec = importlib.util.spec_from_file_location('t20_foundation_driver', Path(__file__).with_name('t03-foundation.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def neutral_pdf(data):
    pattern = rb'/ID\s*\[\s*<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>\s*\]'
    def zero(match):
        value = bytearray(match[0])
        for group in (1, 2):
            start, end = match.start(group) - match.start(), match.end(group) - match.start()
            value[start:end] = b'0' * (end - start)
        return bytes(value)
    return re.sub(pattern, zero, data)


def closed_observations(root, files):
    inventory = json.loads(files.authorities.read(root / 'capabilities/profiles/T20-hostile-input/coverage.json'))
    require(inventory['profile'] == PROFILE and inventory['dimensions'] ==
            ['input', 'pages', 'objects', 'nesting', 'decompression', 'pixels', 'memory', 'temporary', 'elapsed', 'concurrency'],
            'closed resource inventory changed')
    observed = files.properties(files.directory / 'observations.properties')
    cases = inventory['cases']
    names = {key[:-7] for key in observed if key.endswith('.result')}
    require(names == set(cases) and observed.get('case-count') == str(len(cases))
            and observed.get('execution-profile') == 'IN_PROCESS', 'missing case or actual execution observation')
    for name, expected in cases.items():
        require(observed.get(name + '.result') == observed.get(name + '.cleanup') == 'pass', 'nonpassing case: ' + name)
        require(observed.get(name + '.code') == expected.get('code'), 'undetected control or wrong owning failure: ' + name)
        if expected.get('code'):
            diagnostic = observed.get(name + '.diagnostic', '')
            require(diagnostic and all(token not in diagnostic for token in ('private', '.pdf', 'PDFBox', 'Exception')),
                    'unsafe diagnostic: ' + name)
        for key, value in expected.get('observations', {}).items():
            require(observed.get(name + '.' + key) == value, 'missing declared observation: ' + name + '.' + key)
    facade = files.properties(files.directory / 'facade-observations.properties')
    require(facade == inventory['facade-observations'], 'Facade outcome, ownership, lifecycle or receipts changed')
    return inventory


def validate_inputs(root, files):
    authority = files.authorities
    pin_path = root / 'scripts/t20-evidence-pin.properties'
    pin = authority.properties(pin_path)
    require(pin and any(name.startswith('.build-cache/') for name in pin), 'missing actual tool identities')
    for name, expected in pin.items():
        path = root / name
        path.resolve(strict=True).relative_to(root.resolve())
        actual = foundation().sha256(path) if name.startswith('.build-cache/') else authority.digest(path)
        require(actual == expected, 'missing or changed frozen input: ' + name)
    expected = {**pin, 'scripts/t20-evidence-pin.properties': authority.digest(pin_path)}
    for phase in ('before', 'after'):
        require(files.properties(files.directory / ('identities-' + phase + '.properties')) == expected,
                'actual input/tool identity mismatch: ' + phase)


def bound_execution(root, scope, contract, receipt, identities, helper, module):
    """Validate the original candidate, full input closure and environment association."""
    import yaml
    config = json.loads((scope / 'execution.yaml').read_text())
    match = re.fullmatch(r'jdk(8|11|17|21)-in_process', scope.name)
    require(match is not None, 'execution scope has no declared actual JDK tuple')
    profiles = yaml.safe_load((root / contract['environments']).read_text())['profiles']
    selected = [profile for profile in profiles if profile['identity']['jdk-major'] == int(match[1])]
    require(len(selected) == 1, 'missing exact approved Ubuntu JDK profile')
    profile = selected[0]
    environment_path = scope.parent / ('jdk' + match[1] + '-environment') / 'environment.yaml'
    environment_path.resolve(strict=True).relative_to(root)
    environment = json.loads(environment_path.read_text())
    require(environment.get('schema-version') == 1 and environment.get('profile') == profile['id'],
            'original environment profile changed')
    module.require_environment(profile['identity'], environment.get('identity'))
    cp = ':'.join('/workspace/' + path.relative_to(root).as_posix()
                  for path in module.certification_classpath(root, contract, receipt))
    case = module.certification_case('limits')
    plan = module.execution_plan(root, scope, profile['identity']['image'], helper, cp, case, 'IN_PROCESS')
    expected = {'schema-version': 1, 'candidate-sha256': identities['Candidate'],
                'environment-sha256': module.sha256(environment_path), 'acceptance-profile': PROFILE,
                'execution-profile': 'IN_PROCESS', 'command': plan['recorder-command'], 'java-options': plan['java-options'],
                'locale': 'en_US / C.UTF-8', 'timezone': 'UTC', 'settings': plan['settings'],
                'inputs': module.certification_inputs(root, contract, receipt, case)}
    require(config == expected, 'original candidate, environment, command, locale or complete input binding changed')
    return profile, environment, cp


def require_contract_tests(scope, plan):
    require(json.loads((scope / 'contract-tests-command.json').read_text()) == plan['contract-tests-command'],
            'public/artifact regression command differs from the bound IN_PROCESS command')
    transcript = (scope / 'contract-tests.txt').read_text()
    require(transcript.startswith('JUnit version 4.13.2\n')
            and re.search(r'(?m)^OK \(' + str(plan['required-test-count']) + r' tests\)\s*$', transcript)
            and 'FAILURES!!!' not in transcript
            and transcript.count('T20 required execution: tests=' + str(plan['required-test-count'])
                                 + ', failures=0, ignored=0, assumptions=0\n') == 1,
            'missing, skipped or nonpassing complete public/artifact regressions')


def live_replay(root, run):
    import yaml
    module = foundation()
    contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
    receipt = module.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
    scope = run.parent
    replay = Path(tempfile.mkdtemp(prefix='live-collection-', dir=scope))
    identities = module.candidate_identities(root, root / 'capabilities/foundation-evidence.yaml',
        {'schema-version': 1, 'candidate': receipt['candidate'], 'environments': [], 'certifications': []},
        replay / 'candidate-identity.txt')
    helper = Path(os.environ['FOLIO_HARFBUZZ_HELPER']).resolve(strict=True)
    profile, original_environment, cp = bound_execution(root, scope, contract, receipt, identities, helper, module)
    harness = root / 'target/foundation-0.1.0/harness'
    case = module.certification_case('limits')
    original_plan = module.execution_plan(root, scope, profile['identity']['image'], helper, cp, case, 'IN_PROCESS')
    require_contract_tests(scope, original_plan)
    before = module.observe_environment(root, profile['identity']['image'], helper, replay / 'environment-before', profile, harness)
    require(before == original_environment, 'original environment, tool or native identity differs from live observation')
    plan = module.execution_plan(root, replay, profile['identity']['image'], helper, cp, case, 'IN_PROCESS')
    module.write_json(replay / 'command.json', plan['recorder-command'])
    module.write_json(replay / 'contract-tests-command.json', plan['contract-tests-command'])
    module.run_logged(plan['contract-tests-command'], replay / 'contract-tests.txt', cwd=root, timeout=case['contract-timeout'])
    require_contract_tests(replay, plan)
    module.run_logged(plan['recorder-command'], replay / 'recorder.txt', cwd=root, timeout=case['recorder-timeout'])
    require((replay / 'recorder.txt').read_text().strip() ==
            'T20 fixed resource contracts and independent PDF observations passed', 'live recorder did not pass')
    after = module.observe_environment(root, profile['identity']['image'], helper, replay / 'environment-after', profile, harness)
    require(after == before, 'actual environment, tool or native identity changed during live replay')
    bound_execution(root, scope, contract, receipt, identities, helper, module)
    module.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
    return replay, plan, module


def compare_originals(root, original, replay, plan, module):
    fresh = RetainedFiles(replay / 'observations')
    old_names = {path.relative_to(original.directory).as_posix() for path in original.expected}
    new_names = {path.relative_to(fresh.directory).as_posix() for path in fresh.expected}
    require(old_names == new_names, 'original retained tree has missing or extra observations')
    replacements = {}
    for name in sorted(old_names):
        old, new = original.directory / name, fresh.directory / name
        if old.suffix == '.pdf':
            require(neutral_pdf(original.read(old)) == neutral_pdf(fresh.read(new)), 'published PDF/control differs from live observation: ' + name)
            replacements[original.expected[old]] = fresh.expected[new]
        elif old.suffix == '.png':
            # Requalify original pixels with the pinned external comparator; metadata
            # timestamps may vary while required pixel, alpha and size outcomes do not.
            difference = replay / ('compare-' + hashlib.sha256(name.encode()).hexdigest()[:16] + '.png')
            invocation = plan['recorder-command'][:plan['recorder-command'].index('java')]
            invocation += ['/workspace/scripts/container-bin/imagemagick', 'compare', '-metric', 'AE', '-fuzz', '0%',
                           '/workspace/' + old.relative_to(root).as_posix(),
                           '/workspace/' + new.relative_to(root).as_posix(),
                           '/workspace/' + difference.relative_to(root).as_posix()]
            result = subprocess.run(invocation, cwd=root, capture_output=True, timeout=60)
            require(result.returncode == 0 and not result.stdout.strip() and re.fullmatch(rb'0(?:\.0+)?(?:\s+\(0(?:\.0+)?\))?\s*', result.stderr),
                    'original raster/control differs from live pixels: ' + name)
            module.write_json(difference.with_suffix('.json'), {'command': invocation, 'exit-code': result.returncode,
                'stdout': result.stdout.decode(), 'stderr': result.stderr.decode(), 'original': original.reference(root, old),
                'replay': fresh.reference(root, new)})
            replacements[original.expected[old]] = fresh.expected[new]
    def normalized(data, directory):
        text = data.decode('utf-8').replace(str(directory), 'T20-OBSERVATIONS')
        text = text.replace('/workspace/' + directory.relative_to(root).as_posix(), 'T20-OBSERVATIONS')
        for before, after in replacements.items():
            text = text.replace(before, after)
        return text
    for name in sorted(old_names):
        old, new = original.directory / name, fresh.directory / name
        if name in ('retained-files.sha256', 'result.properties') or old.suffix in ('.pdf', '.png'):
            continue
        left, right = original.read(old), fresh.read(new)
        if old.suffix == '.properties':
            left = parse_properties(normalized(left, original.directory).encode())
            right = parse_properties(normalized(right, fresh.directory).encode())
        elif old.suffix in ('.txt', '.md'):
            left, right = normalized(left, original.directory), normalized(right, fresh.directory)
        require(left == right, 'original findings/observations differ from live replay: ' + name)
    fresh.verify()
    original.verify()
    return fresh


def collect_reports(root, run, execution):
    require(execution == 'IN_PROCESS', 'only actual IN_PROCESS execution is certifiable')
    root, run = Path(root).resolve(), Path(run).resolve()
    files = RetainedFiles(run)
    expected = {'profile': PROFILE, 'native-execution-profile': execution, 'facade-execution-profile': 'IN_PROCESS',
                **dict.fromkeys(CHAINS, 'pass'), 'retained-files-sha256': files.expected[run / 'retained-files.sha256']}
    require(files.result == expected, 'missing, failed or indeterminate required chain')
    inventory = closed_observations(root, files)
    validate_inputs(root, files)
    replay, plan, module = live_replay(root, run)
    fresh = compare_originals(root, files, replay, plan, module)
    reports = module.collect_reports(root, run / 'pdf-outcomes', 'transactions')
    for chain in CHAINS[:-1]:
        for product in PRODUCTS[:-2]:
            directory = run / product
            result = files.properties(directory / 'result.properties')
            require(result.get(chain) == 'pass' and result.get('input-sha256') == files.expected[directory / 'blank.pdf'],
                    'published outcome chain/input mismatch')
            reports[chain]['products'].append(files.reference(root, directory / 'blank.pdf'))
            reports[chain]['findings'].extend(files.reference(root, path) for path in files.under(directory)
                if chain in path.name or chain == 'standards' and path.parent.name in ('pdfcpu', 'arlington'))
    reports['contract'] = {'chain': 'contract', 'result': 'pass', 'products': [],
        'findings': [files.reference(root, run / 'observations.properties'), files.reference(root, run / 'facade-observations.properties')],
        'negative-controls': [files.reference(root, run / 'observations.properties'),
                              module.reference(root, root / 'capabilities/profiles/T20-hostile-input/coverage.json')]}
    require(any(case.get('code') for case in inventory['cases'].values()), 'missing required failing resource controls')
    for report in reports.values():
        report['findings'].extend(module.reference(root, run.parent / name)
            for name in ('contract-tests-command.json', 'contract-tests.txt'))
        report['findings'].extend(files.reference(root, run / name) for name in
            ('result.properties', 'retained-files.sha256', 'identities-before.properties', 'identities-after.properties'))
        report['findings'].extend(fresh.reference(root, path) for path in fresh.expected)
        report['findings'].extend(module.reference(root, path) for path in replay.iterdir() if path.is_file())
        for name in ('environment-before', 'environment-after'):
            module.append_environment_observations(root, replay / name, report)
    files.verify()
    files.authorities.verify()
    validate_inputs(root, files)
    return reports
