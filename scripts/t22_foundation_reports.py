"""Strict T22 collection: actual scale bytes, public recovery, zero skips and live replay.

Only observed artifact identities qualify. A freshly sealed but false manifest,
producer label, missing profile, miniature input or stale candidate never repairs
an invalid run. The full giant input is retained losslessly in gzip.
"""
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import tempfile

from t13_foundation_reports import RetainedFiles
import t21_foundation_reports as worker
from t20_foundation_reports import compare_originals

PROFILE = 'T22-worker-recovery-scale'
CHAINS = worker.CHAINS
INVENTORY = 'capabilities/profiles/T22-worker-recovery-scale/coverage.json'
TESTS = 'capabilities/profiles/T22-worker-recovery-scale/mandatory-tests.txt'
PRODUCTS = ('native-acknowledgement', 'native-acknowledgement-second', 'native-stream',
            'native-before-failure', 'native-retry', 'facade-stream', 'facade-before-failure')
POLICY = {'input': '1073741824', 'pages': '5000', 'objects': '2000000', 'nesting': '16384',
          'decompressed': '4294967296', 'pixels': '1000000000', 'memory': '268435456',
          'temporary': '4294967296', 'elapsed': 'PT5M', 'concurrency': '4'}


def require(condition, message):
    if not condition:
        raise ValueError('T22 ' + message)


def mandatory_tests(root):
    tests, inventory = worker.mandatory_tests(root, INVENTORY, TESTS, PROFILE)
    baseline, _ = worker.mandatory_tests(root)
    require(baseline < tests and inventory.get('scale-profiles') == ['pages', 'input', 'concurrency'],
            'Worker prerequisite or mandatory scale profiles omitted')
    require(any('.HardenedWorkerRecoveryTest#' in name for name in tests)
            and sum('.HardenedWorkerScaleProfileTest#' in name for name in tests) == 3
            and sum('.T22RecoveryObservationCases#' in name for name in tests) == 2,
            'recovery, actual scale, interruption or malformed Source execution omitted')
    return tests, inventory


def require_contract_tests(root, scope, plan):
    mandatory_tests(root)
    boundary = worker.require_contract_tests(root, scope, plan, 'T22', INVENTORY, TESTS, PROFILE)
    require_exit_grace_observation((scope / 'contract-tests.txt').read_text())
    return boundary


def require_exit_grace_observation(transcript):
    """Qualify the original real 30-second failure diagnostic, not just PASS labels."""
    lines = [line for line in transcript.splitlines() if line.startswith('T22_WORKER_EXIT_GRACE')]
    require(len(lines) == 1 and len(lines[0]) < 1024, 'exit-grace diagnostic omitted, repeated or oversized')
    number = r'(0|[1-9][0-9]*)'
    state = r'[RSDTtXZPIWK]'
    match = re.fullmatch(
        r'T22_WORKER_EXIT_GRACE The Worker did not terminate after completing its response\. '
        r'elapsedSinceFinishedMillis=' + number + r'; exitGraceSeconds=30; childAlive=true; '
        r'waitIterations=' + number + r'; maximumPollMillis=' + number
        + r'; childExitCode=unavailable; childPid=' + number
        + r'; childState=(' + state + r'); threadStates=(' + state + r'(?:,' + state + r')*); sampledThreads=' + number,
        lines[0])
    require(match is not None, 'exit-grace elapsed/liveness/available child-state diagnostic altered or unsafe')
    elapsed, iterations, maximum_poll, pid = map(int, match.groups()[:4])
    child_state, thread_states, sampled = match.groups()[4:]
    require(30000 <= elapsed < 60000 and 0 < iterations <= elapsed + 1
            and 0 < maximum_poll <= elapsed and pid > 0
            and 1 <= int(sampled) <= 8 and len(thread_states.split(',')) == int(sampled),
            'exit-grace timing or bounded child-state observation invalid')
    # Measured times, PIDs and scheduling states stay in the original transcript;
    # they need not be equal across fresh executions, each of which is qualified.
    return {'elapsed-since-finished-millis': elapsed, 'exit-grace-seconds': 30,
            'child-alive': True, 'wait-iterations': iterations, 'maximum-poll-millis': maximum_poll,
            'child-pid': pid, 'child-state': child_state,
            'thread-states': thread_states.split(','), 'sampled-threads': int(sampled)}


def digest(files, path):
    return hashlib.sha256(files.read(path)).hexdigest()


def duration(value):
    match = re.fullmatch(r'PT(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?', value)
    require(match and any(match.groups()), 'missing or malformed elapsed observation')
    from decimal import Decimal
    return Decimal(match[1] or '0') * 60 + Decimal(match[2] or '0')


def usage(values, prefix, pages, policy):
    for dimension in ('input', 'pages', 'objects', 'decompressed', 'pixels', 'memory', 'temporary'):
        value = values.get(prefix + dimension, '')
        require(re.fullmatch(r'0|[1-9][0-9]*', value) is not None and int(value) <= int(policy[dimension]),
                'missing, invalid or over-limit modeled observation: ' + prefix + dimension)
    require(int(values[prefix + 'pages']) == pages and 0 < int(values[prefix + 'memory'])
            and 0 < int(values[prefix + 'temporary']), 'missing actual pages or modeled high-water/spill')
    require(0 <= duration(values.get(prefix + 'elapsed', '')) <= duration(policy['elapsed']), 'elapsed bound exceeded')


def archived_input(root, files, directory, values, required_length=1073741824):
    archive = directory / 'consumed-input.pdf.gz'
    header, tail = files.read(directory / 'header.bin'), files.read(directory / 'tail.bin')
    require(header == files.authorities.read(root / 'capabilities/profiles/T22-worker-recovery-scale/input-header.bin')
            and hashlib.sha256(header).hexdigest() == values.get('header-sha256')
            and hashlib.sha256(tail).hexdigest() == values.get('tail-sha256'), 'input construction/header/tail changed')
    require(values.get('input-length') == str(required_length) and values.get('input-tail-offset') == str(required_length - len(tail)),
            'giant input was omitted, shortened or has an altered construction')
    position, digest = 0, hashlib.sha256()
    with gzip.open(archive, 'rb') as stream:
        while True:
            chunk = stream.read(1 << 20)
            if not chunk:
                break
            require(position + len(chunk) <= required_length, 'retained input exceeds its exact bound')
            expected = bytearray(b'\n' * len(chunk))
            for data, offset in ((header, 0), (tail, required_length - len(tail))):
                start, end = max(position, offset), min(position + len(chunk), offset + len(data))
                if start < end:
                    expected[start - position:end - position] = data[start - offset:end - offset]
            require(chunk == expected, 'actual consumed input bytes differ from the declared construction')
            digest.update(chunk); position += len(chunk)
    require(position == required_length and digest.hexdigest() == values.get('input-sha256'), 'missing or changed actual consumed input')
    files.read(archive)  # Verify the complete original compressed artifact against its producer manifest.


def scale_observations(root, files):
    _, inventory = mandatory_tests(root)
    require(json.loads(files.authorities.read(root / INVENTORY)) == inventory, 'scale coverage authority changed')
    scale = files.directory / 'scale'
    require({p.name for p in scale.iterdir()} == {'pages', 'input', 'concurrency'}, 'missing or extra scale profile')
    for profile in ('pages', 'input', 'concurrency'):
        directory = scale / profile
        values = files.properties(directory / 'observations.properties')
        require(values.get('profile') == profile and values.get('execution-profile') == 'HARDENED_WORKER'
                and values.get('result') == values.get('owned-storage-cleanup') == 'pass', 'missing actual scale execution or cleanup')
        policy = {**POLICY, 'concurrency': '2' if profile == 'concurrency' else '4'}
        require({key.removeprefix('policy.'): value for key, value in values.items() if key.startswith('policy.')} == policy,
                'full finite scale policy missing or changed')
        require(re.fullmatch(r'[1-9][0-9]*', values.get('wall-nanos', '')) is not None, 'missing actual wall duration')
        usage(values, 'usage.', 5000 if profile == 'pages' else 1, policy)
        expected_outputs = 0 if profile == 'input' else 3 if profile == 'concurrency' else 1
        require(values.get('output-count') == str(expected_outputs), 'missing output identities or fabricated read-only output')
        if profile == 'input':
            require(values.get('public-pages') == '1' and values.get('caller-input-open') == 'true'
                    and values.get('output-absence') == 'read-only; no Target declared; no PDF published'
                    and values.get('usage.input') == '1073741824' and values.get('usage.temporary') == '1073741824',
                    'actual input consumption, caller ownership, spill or output absence missing')
            require(not any(key.startswith('output.') for key in values), 'fabricated read-only output')
            archived_input(root, files, directory, values)
        else:
            count = 5000 if profile == 'pages' else 1
            commands = files.read(directory / 'commands.txt')
            require(commands == b'AddBlankPage.version1\n' * count
                    and values.get('input-command-count') == str(count)
                    and values.get('input-length') == str(len(commands))
                    and values.get('input-sha256') == hashlib.sha256(commands).hexdigest(), 'actual command construction missing or altered')
            for index in range(expected_outputs):
                prefix = 'output.' + str(index) + '.'
                name = values.get(prefix + 'file', '')
                require(name == ('generated-5000-pages.pdf' if profile == 'pages' else ('concurrent-' + str(index) + '.pdf' if index < 2 else 'concurrent-excess.pdf')),
                        'actual workload artifact identity omitted/relabeled')
                product = directory / name
                require(digest(files, product) == values.get(prefix + 'sha256')
                        and values.get(prefix + 'length') == str(product.stat().st_size)
                        and values.get(prefix + 'reopened-pages') == str(count)
                        and values.get(prefix + 'receipt') == 'result:COMMITTED:false'
                        and values.get(prefix + 'transaction'), 'actual publication, receipt or reopen identity missing')
                usage(values, prefix, count, policy)
            if profile == 'concurrency':
                require(all(values.get(key) == value for key, value in {
                    'first-excess-code': 'CONCURRENCY_LIMIT_EXCEEDED', 'first-excess-state': 'RECOVERABLE',
                    'first-excess-receipts': 'result:NOT_ATTEMPTED:false', 'first-excess-callback-count': '0',
                    'first-excess-target': 'absent', 'admitted-workflows': '2', 'retry-state': 'COMPLETED'}.items()),
                    'configured two/first excess/retry controls missing')
    return inventory


def bind_scale_execution(scope, files):
    """Bind retained scale counters/bytes to the actual mandatory test execution."""
    observations = {}
    for line in (scope / 'contract-tests.txt').read_text().splitlines():
        if line.startswith('T22_SCALE '):
            pairs = [part.split('=', 1) for part in shlex.split(line)[1:]]
            require(all(len(pair) == 2 for pair in pairs), 'malformed actual scale stdout')
            values = dict(pairs)
            require(len(values) == len(pairs) and values.get('profile') not in observations,
                    'duplicate scale stdout fields/profile')
            observations[values['profile']] = values
    require(set(observations) == {'pages', 'input', 'concurrency'}, 'actual scale stdout omitted/replaced')
    dimensions = {'input': 'acceptedInput', 'pages': 'observedPages', 'objects': 'observedObjects',
                  'decompressed': 'decompressed', 'pixels': 'decodedPixels', 'memory': 'peakMemory',
                  'temporary': 'peakTemporary', 'elapsed': 'accountedElapsed'}
    expected = {'pages': {'observations.properties', 'commands.txt', 'generated-5000-pages.pdf'},
                'input': {'observations.properties', 'consumed-input.pdf.gz', 'header.bin', 'tail.bin'},
                'concurrency': {'observations.properties', 'commands.txt', 'concurrent-0.pdf',
                                'concurrent-1.pdf', 'concurrent-excess.pdf'}}
    for profile, stdout in observations.items():
        source = scope / 'scale-observations' / profile
        destination = files.directory / 'scale' / profile
        require(source.is_dir() and not source.is_symlink()
                and {path.name for path in source.iterdir()} == expected[profile],
                'actual scale source artifact inventory missing or changed')
        for original in source.iterdir():
            require(original.is_file() and not original.is_symlink(),
                    'actual scale source artifact inventory contains an alias')
            require(original.read_bytes() == files.read(destination / original.name),
                    'retained scale artifact differs from actual mandatory execution')
        values = files.properties(destination / 'observations.properties')
        require(all(values['usage.' + name] == stdout.get(field) for name, field in dimensions.items()),
                'modeled scale observations differ from original stdout')
        require(all(value == stdout.get('policy' + name.capitalize()) for key, value in values.items()
                    if key.startswith('policy.') for name in [key.removeprefix('policy.')]),
                'finite scale policy differs from original stdout')
        require(int(values['wall-nanos']) // 1000000 == int(stdout.get('wallMillis', '-1')),
                'scale wall duration differs from original stdout')


def recovery_observations(root, files, boundary):
    values = files.properties(files.directory / 'recovery-observations.properties')
    expected = {'path-acknowledgement.state': 'COMPLETED', 'path-acknowledgement.receipts': 'first:COMMITTED:false,second:COMMITTED:false',
        'stream-acknowledgement.state': 'COMPLETED', 'stream-acknowledgement.receipts': 'result:COMMITTED:false',
        'partial-publication.state': 'FAILED', 'partial-publication.receipts': 'first:COMMITTED:false,current:FAILED:true,later:NOT_ATTEMPTED:false',
        'partial-publication.failure-code': 'PUBLICATION_FAILED', 'cancelled.state': 'RECOVERABLE',
        'cancelled.receipts': 'result:NOT_ATTEMPTED:false', 'cancelled.failure-code': 'WORKFLOW_CANCELLED',
        'running.state': 'RUNNING', 'running.receipts': 'result:NOT_ATTEMPTED:false',
        'retry.state': 'COMPLETED', 'retry.receipts': 'result:COMMITTED:false',
        'running.duplicate': 'TRANSACTION_IN_PROGRESS; callback=0', 'running.conflict': 'TRANSACTION_IDENTITY_MISMATCH; callback=0',
        'retry.replay': 'TRANSACTION_ALREADY_FINAL; callback=0', 'environment-local': 'lookup absent in another Environment',
        'owned-storage-cleanup': 'pass', 'execution-profile': 'HARDENED_WORKER'}
    for name, identity in (('path-acknowledgement', 'path-acknowledgement'), ('stream-acknowledgement', 'stream-acknowledgement'),
            ('partial-publication', 'partial-publication'), ('cancelled', 'recoverable-cancellation'),
            ('running', 'recoverable-cancellation'), ('retry', 'recoverable-cancellation')):
        expected[name + '.identity'] = identity
    for name in ('path-acknowledgement', 'stream-acknowledgement', 'partial-publication'):
        expected[name + '.replay'] = 'rejected; callback=0; products-unchanged' if name == 'path-acknowledgement' else 'rejected; callback=0; bytes-unchanged'
    expected.update({'stream-acknowledgement.caller-output-open': 'true', 'partial-publication.caller-output-open': 'true',
                     'partial-publication.later-target': 'unchanged'})
    require(values == expected, 'retained lookup/ordered receipt, replay or ownership outcome changed')
    facade = json.loads((files.authorities.read(root / worker.INVENTORY)))['facade-observations']
    require(files.properties(files.directory / 'facade-observations.properties') == facade, 'actual IN_PROCESS Facade outcomes changed')
    require(files.read(files.directory / 'native-later.sentinel') == bytes((17, 18, 19))
            and files.read(files.directory / 'facade-later.sentinel') == bytes((1, 2, 3)), 'later Target altered')
    for name, code, state in (('interruption', 'WORKER_TERMINATED', 'RECOVERABLE'), ('malformed-input', 'SOURCE_READ_FAILED', 'FAILED')):
        path = boundary.directory / ('recovery-failures/' + name + '.properties')
        actual = boundary.properties(path)
        worker.safe_diagnostic(actual.get('diagnostic', ''))
        expected = {'code': code, 'diagnostic': actual['diagnostic'], 'receipts': 'result:NOT_ATTEMPTED:false',
            'retained-state': state, 'sentinel': 'unchanged', 'cleanup': 'pass', 'final-replay': 'rejected-before-callback-publication',
            'retry': 'COMPLETED' if name == 'interruption' else 'ineligible'}
        if name == 'interruption': expected.update({'child-terminated': 'true', 'interrupt-preserved': 'true'})
        require(actual == expected and boundary.read(path.with_suffix('.sentinel')) == bytes((17, 18, 19)),
                'actual interruption/input failure, ownership or final replay missing')


def published_document_observations(root, files):
    """Bind every recovery/Facade child finding before neutral PDF comparison."""
    for name in PRODUCTS:
        directory = files.directory / name
        exact = digest(files, directory / 'blank.pdf')
        observed = files.properties(directory / 'result.properties')
        require(observed.get('input-sha256') == exact
                and all(observed.get(chain) == 'pass' for chain in CHAINS[:-1]),
                'published recovery outcome identity/chain changed')
        for checker in ('pdfcpu', 'arlington'):
            observed = files.properties(directory / checker / 'standards.properties')
            require(observed.get('result') == 'pass' and observed.get('input-sha256') == exact,
                    'published recovery child standards identity changed')
        for chain in CHAINS[:-1]:
            require(files.read(directory / (chain + '.txt')).startswith(
                    ('Input exact SHA-256: ' + exact + '\n').encode()),
                    'published recovery raw finding identifies another artifact')


def semantic_observation(root, files, directory, artifact, exact, pages, passing):
    semantic = json.loads(files.read(directory / 'result.json'))
    graph = json.loads(files.read(directory / 'qpdf.json'))
    from t22_semantics import blank_graph
    require(semantic.get('result') == ('pass' if passing else 'fail')
            and semantic.get('all-pages-inspected') is passing and semantic.get('exit-code') == 0
            and semantic.get('input-sha256') == exact and semantic.get('page-count') == pages
            and semantic.get('observer-sha256') == files.authorities.digest(root / 'scripts/t22_semantics.py')
            and semantic.get('qpdf-sha256') == files.authorities.digest(root / 'scripts/container-bin/qpdf')
            and semantic.get('qpdf-json-sha256') == digest(files, directory / 'qpdf.json')
            and semantic.get('command') == ['/workspace/scripts/container-bin/qpdf', '--json=2',
                '--json-key=qpdf', '--json-key=pages', '/workspace/' + artifact.relative_to(root).as_posix()]
            and blank_graph(graph, pages) is passing,
            'independent actual graph findings/producer/identity altered or omitted')
    return graph


def document_observations(root, files):
    scale = files.directory / 'scale'
    products = [(scale / 'pages', 'generated-5000-pages.pdf', 5000, (1, 2500, 5000)),
                (scale / 'input', 'consumed-input.pdf.gz', 1, (1,))]
    products += [(scale / ('concurrency/concurrent-' + name), 'concurrent-' + name + '.pdf', 1, (1,)) for name in ('0', '1', 'excess')]
    required_rules = set()
    for checker in ('pdfcpu', 'arlington'):
        required_rules.update(worker.foundation().properties(root / ('capabilities/profiles/T03-standards/' + checker + '.properties'))['required-rules'].split(','))
    require(len(required_rules) == 22, 'qualified standards union changed')
    for directory, name, pages, visual_pages in products:
        result = files.properties(directory / 'document.properties')
        exact = files.properties(directory / 'observations.properties')['input-sha256'] if name.endswith('.gz') else digest(files, directory / name)
        require(all(result.get(chain) == 'pass' for chain in CHAINS[:-1]) and result.get('input-sha256') == exact
                and result.get('page-count') == str(pages) and set(result.get('covered-rules', '').split(',')) == required_rules,
                'actual scale artifact chains/identities/rules incomplete')
        for checker in ('pdfcpu', 'arlington'):
            observed = files.properties(directory / checker / 'standards.properties')
            require(observed.get('result') == 'pass' and observed.get('input-sha256') == exact, 'altered actual standards observations')
        artifact = directory / (name.removesuffix('.gz') if name.endswith('.gz') else name)
        graph = semantic_observation(root, files, directory / 'semantic', artifact, exact, pages, True)
        require(files.read(directory / 'syntax.txt').startswith(('input-sha256=' + exact + '\n').encode()),
                'syntax producer inspected another input')
        if pages == 5000:
            negative_graph = semantic_observation(root, files, directory / 'semantic-wrong-count', artifact, exact, 1, False)
            require(negative_graph == graph, 'actual scale count control inspected another graph')
        for page in visual_pages:
            require(result.get('page.' + str(page) + '.visual') == 'pass', 'actual required visual page missing')
            for name in ('visual.txt', 'visual.md', 'T22-scale-expected.png', 'T22-scale-pdfium.png',
                         'T22-scale-implementation.png', 'T22-scale-difference.png', 'T22-scale-renderer-difference.png'):
                files.read(directory / ('page-' + str(page)) / name)
    return products


def bound_scale_normalizer(original_scope, original, replay_scope, fresh):
    """Compare independently bound scale runs while retaining each measured peak."""
    bind_scale_execution(original_scope, original)
    bind_scale_execution(replay_scope, fresh)
    memory = {}
    for profile in ('pages', 'input', 'concurrency'):
        name = 'scale/' + profile + '/observations.properties'
        values = [files.properties(files.directory / name) for files in (original, fresh)]
        policy = {**POLICY, 'concurrency': '2' if profile == 'concurrency' else '4'}
        for observed in values:
            require({key.removeprefix('policy.'): value for key, value in observed.items()
                     if key.startswith('policy.')} == policy, 'replay comparison finite policy changed')
            usage(observed, 'usage.', 5000 if profile == 'pages' else 1, policy)
        memory[name] = tuple(observed['usage.memory'] for observed in values)

    def normalize(name, left, right, normalized, old_dir, new_dir):
        if name in memory:
            require((left.get('usage.memory'), right.get('usage.memory')) == memory[name],
                    'replay peak memory differs from independently bound executions')
            left, right = dict(left), dict(right)
            left.pop('usage.memory')
            right.pop('usage.memory')
        return normalize_extra(name, left, right, normalized, old_dir, new_dir)
    return normalize


def normalize_extra(name, left, right, normalized, old_dir, new_dir):
    if name.startswith('scale/') and name.endswith('/visual.md'):
        def authority_links(text, directory):
            def link(match):
                target = match[1]
                if not target.startswith('../'):
                    return match[0]
                resolved = (directory / name).parent.joinpath(target).resolve()
                if resolved.is_relative_to(directory):
                    return match[0]
                # Replay has one extra directory level. Compare the same authority
                # endpoint while retaining the original relative links unchanged.
                return '](' + resolved.as_posix() + ')'
            return re.sub(r'\]\(([^)]+)\)', link, text)
        return authority_links(left, old_dir), authority_links(right, new_dir)
    if name.endswith('/observations.properties') and name.startswith('scale/'):
        # All counters are separately checked against the full finite policy. Only actual
        # elapsed/wall observations can vary; original values remain retained unchanged.
        for value in (left, right):
            for key in list(value):
                if key == 'wall-nanos' or key.endswith('.elapsed'):
                    value.pop(key)
        return left, right
    if name.endswith('.json'):
        def json_value(data, directory):
            value = json.loads(normalized(data, directory))
            if name.endswith('/qpdf.json'):
                objects = value['qpdf'][1]
                trailer = objects['trailer']['value']
                # qpdf repeats an XRef-stream trailer as its physical stream dictionary.
                # Normalize only that exact duplicate, never arbitrary object fields.
                duplicates = [entry['stream']['dict'] for entry in objects.values()
                    if isinstance(entry, dict) and isinstance(entry.get('stream'), dict)
                    and entry['stream'].get('dict') == trailer and trailer.get('/Type') == '/XRef']
                for dictionary in [trailer, *duplicates]:
                    dictionary.pop('/ID', None)
            if name.endswith(('/semantic/result.json', '/semantic-wrong-count/result.json')):
                value.pop('qpdf-json-sha256', None)
            # Python equality conflates true/1 and 1/1.0; raw JSON finding types
            # must survive comparison after the narrowly permitted exclusions.
            return json.dumps(value, sort_keys=True, separators=(',', ':'))
        return json_value(left, old_dir), json_value(right, new_dir)
    return left, right


def live_replay(root, run):
    import yaml
    module = worker.foundation()
    contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
    receipt = module.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
    scope = run.parent
    replay = Path(tempfile.mkdtemp(prefix='live-collection-', dir=scope))
    identities = module.candidate_identities(root, root / 'capabilities/foundation-evidence.yaml',
        {'schema-version': 1, 'candidate': receipt['candidate'], 'environments': [], 'certifications': []}, replay / 'candidate-identity.txt')
    helper = Path(os.environ['FOLIO_HARFBUZZ_HELPER']).resolve(strict=True)
    profile, environment, cp = worker.bound_execution(root, scope, contract, receipt, identities, helper, module, 'recovery')
    case = module.certification_case('recovery')
    original_plan = module.execution_plan(root, scope, profile['identity']['image'], helper, cp, case, 'HARDENED_WORKER')
    boundary = require_contract_tests(root, scope, original_plan)
    original_actual = worker.boundary_observations(root, boundary, environment)
    before = module.observe_environment(root, profile['identity']['image'], helper, replay / 'environment-before', profile, root / 'target/foundation-0.1.0/harness')
    require(before == environment, 'original environment/tool/native/launcher identities differ from live observation')
    plan = module.execution_plan(root, replay, profile['identity']['image'], helper, cp, case, 'HARDENED_WORKER')
    module.write_json(replay / 'command.json', plan['recorder-command'])
    module.write_json(replay / 'contract-tests-command.json', plan['contract-tests-command'])
    module.run_logged(plan['contract-tests-command'], replay / 'contract-tests.txt', cwd=root, timeout=case['contract-timeout'])
    fresh_boundary = require_contract_tests(root, replay, plan)
    fresh_actual = worker.boundary_observations(root, fresh_boundary, before)
    worker.compare_boundary(boundary, fresh_boundary, original_actual, fresh_actual)
    module.run_logged(plan['recorder-command'], replay / 'recorder.txt', cwd=root, timeout=case['recorder-timeout'])
    require((replay / 'recorder.txt').read_text().strip() == 'T22 recovery and actual scale contracts and independent PDF observations passed', 'live recorder did not pass')
    after = module.observe_environment(root, profile['identity']['image'], helper, replay / 'environment-after', profile, root / 'target/foundation-0.1.0/harness')
    require(after == before, 'environment/tool/native/launcher identities changed during observation')
    worker.bound_execution(root, scope, contract, receipt, identities, helper, module, 'recovery')
    module.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
    return replay, plan, module, boundary, fresh_boundary


def collect_reports(root, run, execution):
    require(execution == 'HARDENED_WORKER', 'only the actual HARDENED_WORKER tuple is certifiable')
    root, run = Path(root).resolve(), Path(run).resolve()
    files = RetainedFiles(run)
    require(files.result == {'profile': PROFILE, 'native-execution-profile': execution, 'facade-execution-profile': 'IN_PROCESS',
        **dict.fromkeys(CHAINS, 'pass'), 'retained-files-sha256': files.expected[run / 'retained-files.sha256']}, 'missing, failed or relabeled required chain')
    worker.validate_inputs(root, files, 'scripts/t22-evidence-pin.properties')
    scale_observations(root, files)
    bind_scale_execution(run.parent, files)
    published_document_observations(root, files)
    products = document_observations(root, files)
    replay, plan, module, boundary, fresh_boundary = live_replay(root, run)
    recovery_observations(root, files, boundary)
    fresh_files = RetainedFiles(replay / 'observations')
    scale_observations(root, fresh_files)
    bind_scale_execution(replay, fresh_files)
    published_document_observations(root, fresh_files)
    document_observations(root, fresh_files)
    recovery_observations(root, fresh_files, fresh_boundary)
    normalize = bound_scale_normalizer(run.parent, files, replay, fresh_files)
    fresh = compare_originals(root, files, replay, plan, module, normalize)
    reports = module.collect_reports(root, run / 'pdf-outcomes', 'transactions')
    for chain in CHAINS[:-1]:
        if chain == 'semantic':
            reports[chain]['negative-controls'].append(files.reference(root, run / 'scale/pages/semantic-wrong-count/result.json'))
        for product in PRODUCTS:
            directory = run / product
            reports[chain]['products'].append(files.reference(root, directory / 'blank.pdf'))
            reports[chain]['findings'].extend(files.reference(root, path) for path in files.under(directory)
                if chain in path.name or chain == 'standards' and path.parent.name in ('pdfcpu', 'arlington'))
        for directory, name, _, _ in products:
            reports[chain]['products'].append(files.reference(root, directory / name))
            reports[chain]['findings'].extend(files.reference(root, path) for path in files.under(directory) if path.suffix != '.pdf')
    reports['contract'] = {'chain': 'contract', 'result': 'pass', 'products': [],
        'findings': [files.reference(root, run / 'recovery-observations.properties'), files.reference(root, run / 'facade-observations.properties')],
        'negative-controls': [module.reference(root, root / INVENTORY), module.reference(root, root / TESTS),
            module.reference(root, run.parent / 'contract-tests.txt'), boundary.reference(root, boundary.directory / 'faults/observations.properties'),
            boundary.reference(root, boundary.directory / 'recovery-failures/interruption.properties'),
            boundary.reference(root, boundary.directory / 'recovery-failures/malformed-input.properties')]}
    for report in reports.values():
        report['findings'].extend(module.reference(root, run.parent / name) for name in ('contract-tests-command.json', 'contract-tests.txt'))
        report['findings'].extend(files.reference(root, run / name) for name in ('result.properties', 'retained-files.sha256', 'identities-before.properties', 'identities-after.properties'))
        report['findings'].extend(files.reference(root, path) for path in files.under(run / 'scale') if path.name == 'observations.properties')
        for group in (fresh, boundary, fresh_boundary): report['findings'].extend(group.reference(root, path) for path in group.expected)
        report['findings'].extend(module.reference(root, path) for path in replay.iterdir() if path.is_file())
        for name in ('environment-before', 'environment-after'): module.append_environment_observations(root, replay / name, report)
    files.verify(); files.authorities.verify(); worker.validate_inputs(root, files, 'scripts/t22-evidence-pin.properties')
    return reports
