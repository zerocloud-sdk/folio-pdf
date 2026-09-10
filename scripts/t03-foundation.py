#!/usr/bin/env python3
"""Prepare and bind repository-only T03/T09/T10 observations; never publish a release."""
import hashlib
from pathlib import Path


def sha256(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def reference(root, path):
    path = Path(path)
    return {'path': path.relative_to(root).as_posix(), 'sha256': sha256(path)}


def source_inputs(root, contract):
    root = Path(root).resolve()
    inputs = set()
    for name in contract['source-roots']:
        source = root / name
        if not source.exists():
            raise FileNotFoundError(source)
        for path in ([source] if source.is_file() else source.rglob('*')):
            if path.is_file() and '__pycache__' not in path.parts:
                if path.is_symlink():
                    raise ValueError('Source symlinks are not certifiable: ' + str(path))
                inputs.add(path.relative_to(root).as_posix())
    inputs.difference_update(contract['source-exclusions'])
    return [reference(root, root / name) for name in sorted(inputs)]


def snapshot(root, contract):
    root = Path(root).resolve()
    return {'release': contract['release'],
            'inputs': source_inputs(root, contract),
            'artifacts': [reference(root, root / name)
                          for name in sorted(contract['required-artifacts'])]}


def require_unchanged(root, contract, recorded):
    if snapshot(root, contract) != recorded:
        raise ValueError('Candidate source or artifacts changed during certification')


def require_staged_build(root, contract, record):
    """Reject a candidate or test harness that no longer matches the staged build."""
    import json
    receipt = json.loads(Path(record).read_text())
    try:
        unchanged = snapshot(root, contract) == receipt['candidate']
        for item in receipt['contract-inputs'] + receipt['harness']:
            path = root / item['path']
            path.resolve(strict=True).relative_to(root.resolve())
            unchanged = unchanged and reference(root, path) == item
    except (OSError, ValueError) as error:
        raise ValueError('Staged build inputs are missing or outside the repository') from error
    if not unchanged:
        raise ValueError('Staged build inputs changed; rebuild before certification')
    return receipt


def capture_build_inputs(root, contract):
    paths = {'capabilities/foundation-release.yaml', 'capabilities/capability-matrix.yaml',
             'capabilities/facade-surface.yaml', contract['requirements'], contract['environments'],
             contract['platform-decision']}
    paths.update(item['profile-contract'] for item in contract['obligations'])
    return {'inputs': source_inputs(root, contract),
            'contract-inputs': [reference(root, root / name) for name in sorted(paths)]}


def record_staged_build(root, contract, before, harness, record):
    candidate = snapshot(root, contract)
    if capture_build_inputs(root, contract) != before or candidate['inputs'] != before['inputs']:
        raise ValueError('Source or contract inputs changed during build; rebuild before certification')
    write_json(record, {'candidate': candidate, 'contract-inputs': before['contract-inputs'],
                        'harness': [reference(root, path) for path in sorted(harness)]})


def certification_classpath(root, contract, receipt):
    products = [root / name for name in sorted(contract['required-artifacts'])
                if name.endswith('.jar') and not name.endswith(('-sources.jar', '-javadoc.jar'))
                and 'itext7-preview' not in name]
    return products + [root / item['path'] for item in receipt['harness'] if item['path'].endswith('.jar')]


def candidate_identities(root, authority, inventory, log):
    """Evaluate a separate provisional index without ever replacing the authority."""
    import re
    provisional = log.parent / 'candidate-probe.json'
    write_json(provisional, inventory)
    run_logged([str(root / 'scripts/inventory'), 'readiness', provisional.relative_to(root).as_posix()], log, cwd=root, check=False)
    text = log.read_text()
    identities = {}
    for name in ('Candidate', 'Contract'):
        match = re.search(r'^' + name + r' identity: ([a-f0-9]{64})$', text, re.MULTILINE)
        if not match:
            raise ValueError('Inventory did not establish ' + name + ' identity')
        identities[name] = match.group(1)
    return identities


def stage_products(root, contract):
    """Stage built Maven outputs; the unsigned bundle makes no release-control claim."""
    import shutil
    import zipfile
    root = Path(root).resolve()
    release = contract['release']
    files = []
    for name in sorted(contract['required-artifacts']):
        target = root / name
        if target.suffix == '.zip':
            continue
        artifact = target.name.split('-' + release)[0]
        module = root if artifact == 'pdf-parent' else root / artifact
        source = module / '.flattened-pom.xml' if target.suffix == '.pom' else module / 'target' / target.name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
        files.append((artifact, target))
    bundle = root / ('target/foundation-' + release + '/central-bundle.zip')
    with zipfile.ZipFile(bundle, 'w', zipfile.ZIP_DEFLATED) as archive:
        for artifact, path in files:
            info = zipfile.ZipInfo('net/zerocloud/' + artifact + '/' + release + '/' + path.name,
                                   date_time=(1980, 1, 1, 0, 0, 0))
            info.external_attr = 0o100644 << 16
            archive.writestr(info, path.read_bytes())


CHAINS = ('syntax', 'standards', 'semantic', 'visual')


def certification_case(obligation):
    """The frozen consumer and artifact obligations for one recorder invocation."""
    artifacts = ['net.zerocloud.pdf.migration.itext7.contract.' + name
                 for name in ('JarContractIT', 'ClasspathExclusivityIT')]
    if obligation == 'pages':
        return {'profile': 'T10-page-manipulation-merge-split', 'label': 'T10', 'test-count': 65,
                'test-classes': ['net.zerocloud.pdf.consumer.PageManipulationWorkflowTest',
                                 'net.zerocloud.pdf.itext7.consumer.PageManipulationFacadeTest'] + artifacts,
                'facade-execution-profile': 'IN_PROCESS',
                'standards-producer': 'arlington-t10-r1',
                'contract-timeout': 600,
                'recorder-timeout': 1800,
                'workflow-policy': 'REWRITE; one-based inclusive page ranges; insert, remove, move and copy; ordered named Sources; exact split coverage of all declared Targets; finite system-default policies; no network; Native tests select the recorded execution profile; Facade execution remains IN_PROCESS',
                'fonts': 'no fonts; the fixed T10 vector corpus has no text or font resources',
                'configuration-paths': ['capabilities/profiles/T10-pages',
                    'capabilities/profiles/T10-standards', 'capabilities/profiles/T03-standards',
                    'capabilities/profiles/T09-standards',
                    'build-tools/acceptance/arlington/t10-r1.patch',
                    'scripts/t10-arlington-pin.properties']}
    if obligation == 'values':
        return {'profile': 'T09-document-value-inspection-patch', 'label': 'T09', 'test-count': 83,
                'test-classes': ['net.zerocloud.pdf.consumer.PdfValueWorkflowTest',
                                 'net.zerocloud.pdf.itext7.consumer.PdfValuesFacadeTest'] + artifacts,
                'facade-execution-profile': 'IN_PROCESS',
                'workflow-policy': 'Native REWRITE and unsigned INCREMENTAL; Facade REWRITE; finite system-default policies; no network; Native tests select the recorded execution profile',
                'fonts': 'none; fixed blue rectangle; private value streams are not painted',
                'configuration-paths': ['capabilities/profiles/T09-values', 'capabilities/profiles/T09-standards',
                    'capabilities/profiles/T03-standards', 'capabilities/profiles/T09-values-visual.properties',
                    'capabilities/expected/T09-values-144dpi-srgb.png']}
    if obligation != 'transactions':
        raise ValueError('Unknown certification obligation: ' + obligation)
    return {'profile': 'T03-document-workflow-transaction', 'label': 'T03', 'test-count': 34,
            'test-classes': ['net.zerocloud.pdf.consumer.' + name for name in
                ('BlankDocumentWorkflowTest', 'WorkflowLifecycleTest', 'WorkflowTransactionContractTest', 'WorkflowResourceOwnershipTest')]
                + ['net.zerocloud.pdf.itext7.consumer.BlankDocumentFacadeTest'] + artifacts,
            'facade-execution-profile': 'IN_PROCESS',
            'workflow-policy': 'REWRITE; PDF 1.7; finite system-default resource/transaction/worker policies; no network; tests select the recorded execution profile',
            'fonts': 'none; no text or resources in the T03 products',
            'configuration-paths': ['capabilities/profiles/T03-standards',
                'capabilities/profiles/T03-document-blank-visual.properties',
                'capabilities/expected/T03-document-blank-144dpi-srgb.png']}


def properties(path):
    """Read recorder-owned flat properties (values used here contain no escapes)."""
    return dict(line.split('=', 1) for line in Path(path).read_text().splitlines()
                if line and not line.startswith('#') and '=' in line)


def record_preservation(root, run):
    """Observe final effective streams through pinned qpdf, without decoding them."""
    import base64
    import json
    import subprocess
    directory = run / 'raw-preservation'
    directory.mkdir()
    pin = properties(root / 'scripts/qpdf-pin.properties')
    executable = (root / 'scripts' / pin['QPDF_EXECUTABLE']).resolve(strict=True)
    inputs = {name: run / name / 'values.pdf' for name in ('source', 'native-rewrite', 'native-incremental', 'facade')}
    inputs['negative'] = root / 'capabilities/profiles/T09-values/fixtures/reencoded-incremental.pdf'
    observations = {}
    files = []
    for name, path in inputs.items():
        before = reference(root, path)
        command = [str(executable), '--json=2', '--json-key=qpdf', '--json-stream-data=inline', '--decode-level=none', str(path)]
        completed = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30)
        output = directory / (name + '.json')
        output.write_bytes(completed.stdout)
        (directory / (name + '-stderr.txt')).write_bytes(completed.stderr)
        invocation = directory / (name + '-command.json')
        write_json(invocation, {'command': command, 'exit': completed.returncode, 'input': before})
        files.extend([reference(root, output), reference(root, invocation), reference(root, directory / (name + '-stderr.txt'))])
        try:
            if completed.returncode != 0 or completed.stderr or before != reference(root, path):
                raise ValueError('qpdf execution did not complete cleanly')
            parsed = json.loads(completed.stdout)
            if parsed['version'] != 2 or parsed['parameters']['decodelevel'] != 'none':
                raise ValueError('Raw stream observation policy changed')
            objects = parsed['qpdf'][1]

            def value(item):
                return objects['obj:' + item]['value'] if isinstance(item, str) and item.endswith(' R') else item

            catalog = value(objects['trailer']['value']['/Root'])
            values = value(value(value(catalog['/PieceInfo'])['/FolioPDF'])['/Private'])
            page = value(value(catalog['/Pages'])['/Kids'][0])
            streams = {}
            for label, ref in (('retained-stream', values['/RetainedStream']), ('page-content', page['/Contents'])):
                stream = objects['obj:' + ref]['stream']
                raw = base64.b64decode(stream['data'], validate=True)
                streams[label] = {'raw-sha256': hashlib.sha256(raw).hexdigest(), 'length': len(raw), 'attributes': stream['dict']}
            observations[name] = streams
        except (ValueError, KeyError, TypeError, IndexError):
            observations[name] = None
    original = observations['source']
    negative = 'fail' if original is not None and observations['negative'] is not None and observations['negative'] != original else 'indeterminate'
    result = 'indeterminate' if original is None or any(value is None for value in observations.values()) or negative != 'fail' else (
        'pass' if all(observations[name] == original for name in ('native-rewrite', 'native-incremental', 'facade')) else 'fail')
    report = {'result': result, 'negative-control': negative, 'policy': 'Compare final effective raw stream bytes and filter attributes; the old incremental prefix does not qualify.',
              'observations': observations, 'findings': files, 'negative-controls': [reference(root, inputs['negative'])]}
    write_json(directory / 'result.json', report)
    return report


def collect_reports(root, run, obligation='transactions'):
    result = properties(run / 'result.properties')
    for chain in CHAINS:
        if result.get(chain) != 'pass':
            raise ValueError('Unobserved or non-passing ' + obligation + ' chain: ' + chain)
    negative = properties(run / 'negative/result.properties')
    reports = {}
    for chain in CHAINS:
        if negative.get(chain) != 'fail':
            raise ValueError('Missing detected negative control for ' + chain)
        report = {'chain': chain, 'result': 'pass', 'products': [], 'findings': [],
                  'negative-controls': [reference(root, run / 'negative' / (chain + '.txt'))]}
        if obligation == 'pages':
            editions = tuple(api + '-' + product for api in ('native', 'facade')
                             for product in ('edited', 'merged', 'left', 'right'))
        elif obligation == 'values':
            editions = ('native-rewrite', 'native-incremental', 'facade')
        else:
            editions = ('native', 'facade')
        for edition in editions:
            directory = run / edition
            observed = properties(directory / 'result.properties')
            if observed.get(chain) != 'pass':
                raise ValueError(edition + ' chain did not pass: ' + chain)
            artifact = 'values.pdf' if obligation == 'values' else (
                'pages.pdf' if obligation == 'pages' else 'blank.pdf')
            product = reference(root, directory / artifact)
            if product['sha256'] != observed.get('input-sha256'):
                raise ValueError('Observed product changed after checking')
            report['products'].append(product)
            report['findings'].append(reference(root, directory / (chain + '.txt')))
            report['findings'].append(reference(root, directory / 'result.properties'))
            if (directory / (chain + '.md')).is_file():
                report['findings'].append(reference(root, directory / (chain + '.md')))
            if chain == 'visual':
                if obligation == 'pages':
                    page_count = int(observed.get('page-count', '0'))
                    if page_count < 1:
                        raise ValueError('Missing T10 visual page count: ' + edition)
                    for page in range(1, page_count + 1):
                        if observed.get('page.' + str(page) + '.visual') != 'pass':
                            raise ValueError('Missing passing T10 page visual: '
                                             + edition + ' page ' + str(page))
                        for suffix in ('visual.md', 'visual.txt', 'expected.png', 'pdfium.png',
                                       'implementation.png', 'difference.png',
                                       'renderer-difference.png'):
                            report['findings'].append(reference(
                                root, directory / ('page-' + str(page) + '-' + suffix)))
                else:
                    report['findings'] += [reference(root, path)
                                           for path in sorted(directory.glob('*.png'))]
            if chain == 'standards':
                for checker in ('pdfcpu', 'arlington'):
                    tool = directory / checker
                    observation = properties(tool / 'standards.properties')
                    if observation.get('result') != 'pass' or not observation.get('covered-rules'):
                        raise ValueError('Missing qualified checker report: ' + checker)
                    report['findings'].append(reference(root, tool / 'standards.properties'))
                    report['findings'].append(reference(root, tool / 'findings.txt'))
                    for rule in observation['covered-rules'].split(','):
                        report['negative-controls'].append(reference(root, tool / ('negative-' + rule + '.txt')))
                        report['negative-controls'].append(reference(root, tool / ('control-' + rule + '.pdf')))
        if chain == 'visual':
            report['negative-controls'] += [reference(root, path) for path in sorted((run / 'negative').glob('*.png'))]
            report['negative-controls'] += [reference(root, path) for path in sorted((run / 'negative').glob('one-pixel-control.properties'))]
        if obligation == 'values':
            if chain in ('syntax', 'semantic'):
                name = 'invalid.pdf' if chain == 'syntax' else 'unchanged-values.pdf'
                report['negative-controls'].append(reference(root, run / 'negative' / name))
            if chain == 'visual':
                original = run / 'source'
                observed = properties(original / 'result.properties')
                source = reference(root, original / 'values.pdf')
                if observed.get('visual') != 'pass' or observed.get('input-sha256') != source['sha256']:
                    raise ValueError('Source visual observation is missing or changed')
                report['products'].append(source)
                report['findings'] += [reference(root, path) for path in sorted(original.iterdir())
                                       if path.is_file() and path.suffix != '.pdf']
                report['negative-controls'].append(reference(root, run / 'negative/values.pdf'))
        elif obligation == 'pages':
            if chain == 'syntax':
                report['negative-controls'].append(reference(root, run / 'negative/invalid.pdf'))
            elif chain == 'semantic':
                report['negative-controls'].append(reference(root, run / 'negative/wrong-order.pdf'))
            elif chain == 'visual':
                report['negative-controls'] += [reference(root, path)
                    for path in sorted((run / 'negative/visual').rglob('*')) if path.is_file()]
        reports[chain] = report
    return reports


def require_environment(expected, actual):
    if expected != actual:
        raise ValueError('Observed environment does not match its approved profile: ' + repr(actual))
    return actual


def merge_evidence(root, previous, fresh, identities):
    """Keep other obligations only with current identities and intact transitive evidence.

    Historical files are never rewritten. A stale record stays historical rather than
    being copied into the current-candidate authority with a new label.
    """
    import copy
    import yaml
    merged = copy.deepcopy(fresh)
    if previous.get('candidate') != fresh['candidate']:
        return merged
    environments = {item['profile']: item['record'] for item in fresh['environments']}
    old_environments = {item['profile']: item['record'] for item in previous.get('environments', [])}
    replaced = {(item['obligation'], item['environment'], item['execution-profile']) for item in fresh['certifications']}

    def verified(item, visiting=None, kind=None):
        visiting = set() if visiting is None else visiting
        path = root / item['path']
        path.resolve(strict=True).relative_to(root.resolve())
        if reference(root, path) != item:
            raise ValueError('Changed retained evidence: ' + str(path))
        if kind == 'observation' or path.suffix not in ('.json', '.yaml'):
            return None
        if path in visiting:
            raise ValueError('Cyclic evidence references')
        value = yaml.safe_load(path.read_text())
        ancestors = visiting | {path}

        def walk(node):
            if isinstance(node, dict):
                if set(node) == {'path', 'sha256'}:
                    verified(node, ancestors)
                else:
                    for name, child in node.items():
                        if node is value and kind == 'certification' and name == 'report':
                            verified(child, ancestors, 'report')
                        elif node is value and kind == 'report' and name == 'environment-observations':
                            # Raw observer payloads describe container/install paths, not repository references.
                            for observation in child:
                                verified(observation, ancestors, 'observation')
                        else:
                            walk(child)
            elif isinstance(node, list):
                for child in node:
                    walk(child)

        walk(value)
        return value

    retained = []
    for certification in previous.get('certifications', []):
        key = tuple(certification[name] for name in ('obligation', 'environment', 'execution-profile'))
        if key in replaced:
            continue
        try:
            old_environment = old_environments[certification['environment']]
            environment = environments.get(certification['environment'], old_environment)
            if old_environment['sha256'] != environment['sha256']:
                raise ValueError('Observed environment changed')
            verified(old_environment)
            configuration = verified(certification['configuration'])
            if configuration['candidate-sha256'] != identities['Candidate'] or configuration['environment-sha256'] != environment['sha256']:
                raise ValueError('Execution identity changed')
            chains = set()
            for item in certification['records']:
                record = verified(item, kind='certification')
                expected = {'candidate-sha256': identities['Candidate'], 'contract-sha256': identities['Contract'],
                            'environment-sha256': environment['sha256'],
                            'execution-configuration-sha256': certification['configuration']['sha256'],
                            'obligation': certification['obligation'], 'execution-profile': certification['execution-profile'],
                            'result': 'pass'}
                if any(record.get(name) != value for name, value in expected.items()) or record['chain'] in chains:
                    raise ValueError('Retained certification identity changed')
                chains.add(record['chain'])
            if chains != set(CHAINS):
                raise ValueError('Incomplete retained certification')
            retained.append(copy.deepcopy(certification))
            if certification['environment'] not in environments:
                environments[certification['environment']] = old_environment
                merged['environments'].append({'profile': certification['environment'], 'record': copy.deepcopy(old_environment)})
        except (OSError, ValueError, KeyError, TypeError, yaml.YAMLError):
            # Fail closed for this old scope; its original files remain untouched.
            continue
    merged['certifications'] = retained + merged['certifications']
    return merged


def write_json(path, value):
    import json
    Path(path).write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n')


def publish_index(root, directory):
    """Publish already observed records with a lock, stale-index guard and atomic replace."""
    import fcntl
    import json
    import os
    import tempfile
    import yaml
    authority = root / 'capabilities/foundation-evidence.yaml'
    lock = root / '.build-cache/foundation-evidence.lock'
    lock.parent.mkdir(exist_ok=True)
    with lock.open('a+b') as held:
        fcntl.flock(held, fcntl.LOCK_EX)
        previous_bytes = authority.read_bytes()
        if hashlib.sha256(previous_bytes).hexdigest() != (directory / 'prior-index.sha256').read_text().strip():
            raise ValueError('Evidence authority changed during certification; refusing to overwrite it')
        previous = yaml.safe_load(previous_bytes)
        fresh = json.loads((directory / 'observed-index.json').read_text())
        identities = json.loads((directory / 'identities.json').read_text())
        empty = {'schema-version': 1, 'candidate': fresh['candidate'], 'environments': fresh['environments'], 'certifications': []}
        verified = merge_evidence(root, fresh, empty, identities)
        scopes = {(item['obligation'], item['environment'], item['execution-profile']) for item in fresh['certifications']}
        if not scopes or len(scopes) != len(fresh['certifications']) or verified['certifications'] != fresh['certifications']:
            raise ValueError('New evidence is incomplete, stale or does not match the observed identities')
        merged = merge_evidence(root, previous, fresh, identities)
        prepared = None
        try:
            with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', dir=authority.parent,
                                             prefix='.foundation-evidence-', suffix='.tmp', delete=False) as stream:
                prepared = Path(stream.name)
                json.dump(merged, stream, indent=2, ensure_ascii=False)
                stream.write('\n')
                stream.flush()
                os.fsync(stream.fileno())
            if json.loads(prepared.read_text()) != merged:
                raise ValueError('Prepared evidence index failed its round-trip check')
            os.chmod(prepared, authority.stat().st_mode & 0o777)
            if authority.read_bytes() != previous_bytes:
                raise ValueError('Evidence authority changed during index preparation')
            os.replace(prepared, authority)
            descriptor = os.open(str(authority.parent), os.O_RDONLY | os.O_DIRECTORY)
            try:
                os.fsync(descriptor)
            finally:
                os.close(descriptor)
        finally:
            if prepared is not None:
                prepared.unlink(missing_ok=True)
        return merged


def run_logged(command, path, *, cwd, timeout=1800, check=True):
    import subprocess
    with Path(path).open('wb') as log:
        result = subprocess.run(command, cwd=cwd, stdout=log, stderr=subprocess.STDOUT, timeout=timeout)
    if check and result.returncode:
        raise RuntimeError('Command failed; see ' + str(path))
    return result.returncode


def stage_harness(root, base):
    import shutil
    import zipfile
    directory = base / 'harness'
    if directory.exists():
        shutil.rmtree(directory)
    directory.mkdir(parents=True, exist_ok=True)
    for module, name in [('pdf-document', 'native-tests'), ('pdf-migration-itext7', 'facade-tests')]:
        classes = root / module / 'target/test-classes'
        with zipfile.ZipFile(directory / (name + '.jar'), 'w') as jar:
            for path in sorted(classes.rglob('*')):
                if path.is_file():
                    info = zipfile.ZipInfo(path.relative_to(classes).as_posix(), (1980, 1, 1, 0, 0, 0))
                    info.external_attr = 0o100644 << 16
                    jar.writestr(info, path.read_bytes())
    shutil.copyfile(root / 'pdf-acceptance/target/pdf-acceptance-0.1.0.jar', directory / 'acceptance.jar')
    libraries = directory / 'lib'
    libraries.mkdir(exist_ok=True)
    for module in ('pdf-document', 'pdf-migration-itext7', 'pdf-acceptance'):
        for item in (root / module / 'target/t03-classpath.txt').read_text().strip().split(':'):
            source = Path(item)
            if source.name.startswith(('pdf-provider-contract-', 'pdf-conversion-', 'pdf-document-', 'pdf-migration-itext7-')):
                continue
            target = libraries / source.name
            if target.exists() and sha256(target) != sha256(source):
                raise ValueError('Classpath filename collision: ' + source.name)
            shutil.copyfile(source, target)
    comparator_runtime = root / '.build-cache/imagemagick/7.1.2-30/runtime'
    runtime = [comparator_runtime / line.split()[1]
               for line in (root / 'scripts/imagemagick-runtime.sha256').read_text().splitlines()]
    return sorted(path for path in directory.rglob('*.jar')) + runtime


def container_command(root, image, helper):
    """Read-only inputs, separate evidence output bind, and no network in certification."""
    return ['podman', 'run', '--rm', '--network=none', '--userns=keep-id',
            '--volume', str(root) + ':/workspace:ro',
            '--volume', str(helper.parent.parent) + ':/folio-harfbuzz:ro',
            '--volume', '/usr/bin/python3.12:/usr/bin/python3.12:ro',
            '--volume', '/usr/lib/python3.12:/usr/lib/python3.12:ro',
            '--volume', str(Path('/lib/x86_64-linux-gnu/libexpat.so.1').resolve()) + ':/lib/x86_64-linux-gnu/libexpat.so.1:ro',
            '--workdir', '/workspace', '--env', 'LANG=C.UTF-8', '--env', 'LC_ALL=C.UTF-8', '--env', 'TZ=UTC',
            '--env', 'FOLIO_HARFBUZZ_HELPER=/folio-harfbuzz/bin/folio-harfbuzz', image]


def certification_tools():
    """Return the common, pinned tool catalog recorded for every certification."""
    external = [
        {'id': 'qpdf', 'kind': 'external-tool', 'version': '12.4.0',
         'path': '.build-cache/qpdf/12.4.0/bin/qpdf',
         'pin': 'scripts/qpdf-pin.properties', 'hash-key': 'QPDF_BINARY_SHA256',
         'chains': ['syntax']},
        {'id': 'pdfcpu', 'kind': 'external-tool', 'version': '0.15.0',
         'path': '.build-cache/pdfcpu/0.15.0/pdfcpu',
         'pin': 'scripts/pdfcpu-pin.properties', 'hash-key': 'sha256',
         'chains': ['standards']},
        {'id': 'arlington', 'kind': 'external-tool', 'version': '0.81',
         'path': '.build-cache/arlington/fe4a1a8/TestGrammar/bin/linux/TestGrammar',
         'pin': 'scripts/arlington-pin.properties', 'hash-key': 'sha256',
         'chains': ['standards']},
        {'id': 'arlington-t10-r1', 'kind': 'external-tool',
         'version': '0.81-folio-t10-r1',
         'path': '.build-cache/arlington/t10-r1/TestGrammar/bin/linux/TestGrammar',
         'pin': 'scripts/t10-arlington-pin.properties', 'hash-key': 'sha256',
         'chains': ['standards']},
        {'id': 'pdfium-cli', 'kind': 'external-tool',
         'version': 'v0.11.2-pdfium-chromium-7881',
         'path': '.build-cache/pdfium/v0.11.2-chromium-7881/bin/pdfium',
         'pin': 'scripts/pdfium-pin.properties', 'hash-key': 'PDFIUM_EXECUTABLE_SHA256',
         'chains': ['visual']},
        {'id': 'imagemagick', 'kind': 'external-tool', 'version': '7.1.2-30',
         'path': '.build-cache/imagemagick/7.1.2-30/bin/imagemagick.AppImage',
         'pin': 'scripts/imagemagick-pin.properties',
         'hash-key': 'IMAGEMAGICK_EXECUTABLE_SHA256', 'chains': ['visual']}]
    project = [{'id': 'folio-pdf-' + label, 'kind': 'project-test',
                'version': '0.1.0', 'chains': ['semantic']}
               for label in ('t03', 't09', 't10')]
    return external + project


def observe_environment(root, image, helper, directory, profile, harness):
    import json
    import subprocess
    directory.mkdir()
    inside = '/workspace/' + directory.relative_to(root).as_posix()
    command = container_command(root, image, helper)
    command[-1:-1] = ['--volume', str(directory) + ':' + inside + ':rw']
    shell = '''set -eu
out=$1
cat /etc/os-release > "$out/os-release.txt"
cat "$JAVA_HOME/release" > "$out/jdk-release.txt"
java -XshowSettings:properties -version > "$out/java-version.txt" 2>&1
sha256sum "$JAVA_HOME/bin/java" > "$out/java-executable.txt"
uname -r > "$out/kernel.txt"
uname -m > "$out/architecture.txt"
/usr/bin/python3.12 scripts/t29-native-observation.py /folio-harfbuzz/bin/folio-harfbuzz > "$out/native-observation.json"
'''
    invocation = command + ['sh', '-c', shell, 't03-observe', inside]
    run_logged(invocation, directory / 'observer.log', cwd=root, timeout=60)
    write_json(directory / 'invocation.json', invocation)
    digest = subprocess.check_output(['podman', 'image', 'inspect', image, '--format', '{{.Digest}}'], text=True).strip()
    (directory / 'image-digest.txt').write_text(digest + '\n')
    os_release = {key: value.strip('"') for key, value in properties(directory / 'os-release.txt').items()}
    jdk = {key: value.strip('"') for key, value in properties(directory / 'jdk-release.txt').items()}
    runtime = next(line.split('=', 1)[1].strip()
                   for line in (directory / 'java-version.txt').read_text().splitlines()
                   if line.strip().startswith('java.runtime.version ='))
    major = int(runtime.split('.')[1] if runtime.startswith('1.') else runtime.split('.')[0])
    architecture = (directory / 'architecture.txt').read_text().strip()
    actual = {'os': os_release['ID'], 'os-version': os_release['VERSION_ID'],
              'architecture': 'x86-64' if architecture == 'x86_64' else architecture,
              'image': image.split('@')[0] + '@' + digest,
              'os-release-sha256': sha256(directory / 'os-release.txt'), 'jdk-major': major,
              'jdk-vendor': jdk['IMPLEMENTOR'], 'jdk-build': runtime,
              'java-sha256': (directory / 'java-executable.txt').read_text().split()[0]}
    identity = require_environment(profile['identity'], actual)
    native = json.loads((directory / 'native-observation.json').read_text())
    if native.get('result') != 'pass':
        raise ValueError('Live native installation observation did not pass')
    installation = helper.parent.parent / 'installation.json'
    engine = {'name': 'harfbuzz', 'version': '10.2.0', 'helper-sha256': sha256(helper),
              'library-sha256': native['loaded_engine']['sha256'], 'installation-sha256': sha256(installation)}
    tools = []
    for tool in certification_tools():
        observed = dict(tool)
        if tool['kind'] == 'project-test':
            observed['sha256'] = sha256(harness / 'acceptance.jar')
        else:
            pin = properties(root / tool['pin'])
            observed_hash = sha256(root / tool['path'])
            if observed_hash != pin[tool['hash-key']]:
                raise ValueError('Observed checker executable differs from its pin: ' + tool['id'])
            observed['sha256'] = observed_hash
        for internal in ('path', 'pin', 'hash-key'):
            observed.pop(internal, None)
        tools.append(observed)
    return {'schema-version': 1, 'profile': profile['id'], 'identity': identity,
            'host': {'kernel': (directory / 'kernel.txt').read_text().strip(), 'architecture': identity['architecture']},
            'native-engine': engine, 'tools': tools}


def execution_plan(root, scope, image, helper, cp, case, execution):
    """Generate exactly the commands used by certify; planning does not run them."""
    inside = '/workspace/' + scope.relative_to(root).as_posix()
    command = container_command(root, image, helper)
    command[-1:-1] = ['--volume', str(scope) + ':' + inside + ':rw']
    options = ['-Xmx1024m', '-Duser.language=en', '-Duser.country=US', '-Duser.timezone=UTC',
               '-Dfolio.harfBuzzHelper=/folio-harfbuzz/bin/folio-harfbuzz',
               '-Dfolio.' + case['label'].lower() + '.executionProfile=' + execution]
    evidence_command = command + ['java'] + options + ['-cp', cp, 'net.zerocloud.pdf.acceptance.' + case['label'] + 'EvidenceCommand',
        '/workspace', inside + '/observations', execution, '0.1.0']
    test_options = [
        '-DrepositoryRoot=/workspace',
        '-DartifactPath=/workspace/target/foundation-0.1.0/artifacts/pdf-migration-itext7-preview-0.1.0.jar',
        '-DstableArtifactPath=/workspace/target/foundation-0.1.0/artifacts/pdf-migration-itext7-0.1.0.jar',
        '-DdocumentArtifactPath=/workspace/target/foundation-0.1.0/artifacts/pdf-document-0.1.0.jar',
        '-DtestClassesPath=/workspace/target/foundation-0.1.0/harness/facade-tests.jar']
    test_command = command + ['java'] + options + test_options + ['-cp', cp, 'org.junit.runner.JUnitCore'] + case['test-classes']
    return {'recorder-command': evidence_command, 'contract-tests-command': test_command,
            'preservation-command': command + ['/usr/bin/python3.12', '/workspace/scripts/t03-foundation.py',
                'preservation', inside + '/observations', '--root', '/workspace'] if case['label'] == 'T09' else [],
            'required-test-count': case['test-count'], 'java-options': options,
            'settings': {'workflow-policy': case['workflow-policy'], 'fonts': case['fonts'],
                         'providers': 'none; Stable Facade execution is ' + case['facade-execution-profile']}}


def record_plan(root, output, contract, helper, obligation):
    import json
    import yaml
    output.mkdir()
    receipt = json.loads((root / 'target/foundation-0.1.0/build-inputs.json').read_text())
    cp = ':'.join('/workspace/' + path.relative_to(root).as_posix()
                  for path in certification_classpath(root, contract, receipt))
    case = certification_case(obligation)
    executions = []
    for profile in yaml.safe_load((root / contract['environments']).read_text())['profiles']:
        for execution in ('IN_PROCESS', 'HARDENED_WORKER'):
            scope = output / ('jdk' + str(profile['identity']['jdk-major']) + '-' + execution.lower())
            executions.append(execution_plan(root, scope, profile['identity']['image'], helper, cp, case, execution))
    write_json(output / 'plan.json', {'status': 'unverified-plan', 'configuration-paths': case['configuration-paths'], 'executions': executions})


def certify(root, output, contract, helper, obligation='transactions'):
    import json
    import yaml
    root = root.resolve()
    case = certification_case(obligation)
    output = output.resolve()
    output.relative_to(root)
    base = root / 'target/foundation-0.1.0'
    harness = base / 'harness'
    build_record = base / 'build-inputs.json'
    receipt = require_staged_build(root, contract, build_record)
    output.mkdir()
    candidate = receipt['candidate']
    inventory = {'schema-version': 1, 'candidate': candidate, 'environments': [], 'certifications': []}
    authority = root / 'capabilities/foundation-evidence.yaml'
    previous_bytes = authority.read_bytes()
    identities = candidate_identities(root, authority, inventory, output / 'candidate-identity.txt')
    environment_profiles = yaml.safe_load((root / contract['environments']).read_text())['profiles']
    classpath_files = certification_classpath(root, contract, receipt)
    cp = ':'.join('/workspace/' + path.relative_to(root).as_posix() for path in classpath_files)
    profile_inputs = []
    for name in case['configuration-paths']:
        path = root / name
        profile_inputs += list(path.rglob('*')) if path.is_dir() else [path]
    configuration_inputs = sorted(set(classpath_files + [root / item['path'] for item in receipt['harness']]
        + [build_record, base / 'build-command.json', root / 'scripts/imagemagick-runtime.sha256']
        + profile_inputs
        + [root / item['pin'] for item in certification_tools()
           if item['kind'] == 'external-tool']))
    configuration_inputs = [reference(root, path) for path in configuration_inputs if path.is_file()]
    for profile in environment_profiles:
        major = profile['identity']['jdk-major']
        print(case['label'] + ' environment JDK ' + str(major), flush=True)
        directory = output / ('jdk' + str(major) + '-environment')
        environment = observe_environment(root, profile['identity']['image'], helper, directory, profile, harness)
        record_path = directory / 'environment.yaml'
        write_json(record_path, environment)
        environment_ref = reference(root, record_path)
        inventory['environments'].append({'profile': profile['id'], 'record': environment_ref})
        for execution in ('IN_PROCESS', 'HARDENED_WORKER'):
            print(case['label'] + ' certification JDK ' + str(major) + ' / ' + execution, flush=True)
            scope = output / ('jdk' + str(major) + '-' + execution.lower())
            scope.mkdir()
            plan = execution_plan(root, scope, profile['identity']['image'], helper, cp, case, execution)
            evidence_command = plan['recorder-command']
            test_command = plan['contract-tests-command']
            options = plan['java-options']
            config = {'schema-version': 1, 'candidate-sha256': identities['Candidate'],
                      'environment-sha256': environment_ref['sha256'], 'acceptance-profile': case['profile'],
                      'execution-profile': execution, 'command': evidence_command, 'java-options': options,
                      'locale': 'en_US / C.UTF-8', 'timezone': 'UTC',
                      'settings': plan['settings'],
                      'inputs': configuration_inputs}
            configuration = scope / 'execution.yaml'
            write_json(configuration, config)
            write_json(scope / 'contract-tests-command.json', test_command)
            run_logged(test_command, scope / 'contract-tests.txt', cwd=root,
                       timeout=case.get('contract-timeout', 300))
            if 'OK (' + str(case['test-count']) + ' tests)' not in (scope / 'contract-tests.txt').read_text():
                raise ValueError('The complete ' + case['label'] + ' consumer/artifact contract suite did not execute')
            run_logged(evidence_command, scope / 'recorder.txt', cwd=root,
                       timeout=case.get('recorder-timeout', 300))
            reports = collect_reports(root, scope / 'observations', obligation)
            if obligation == 'values':
                write_json(scope / 'raw-preservation-command.json', plan['preservation-command'])
                run_logged(plan['preservation-command'], scope / 'raw-preservation.txt', cwd=root, timeout=60)
                preserved = json.loads((scope / 'observations/raw-preservation/result.json').read_text())
                if preserved['result'] != 'pass':
                    raise ValueError('T09 final encoded-stream preservation did not pass')
                reports['semantic']['findings'] += preserved['findings'] + [reference(root, scope / 'observations/raw-preservation/result.json')]
                reports['semantic']['findings'] += [reference(root, scope / 'raw-preservation-command.json'), reference(root, scope / 'raw-preservation.txt')]
                reports['semantic']['negative-controls'] += preserved['negative-controls']
            records = []
            for chain, report in reports.items():
                if chain == 'semantic':
                    report['findings'] += [reference(root, scope / 'contract-tests.txt'), reference(root, scope / 'contract-tests-command.json')]
                report['environment-observations'] = [reference(root, path) for path in sorted(directory.iterdir()) if path.is_file()]
                report_file = scope / (chain + '-report.json')
                write_json(report_file, report)
                record = {'schema-version': 1, 'obligation': obligation, 'acceptance-profile': case['profile'],
                          'release': '0.1.0', 'candidate-sha256': identities['Candidate'], 'contract-sha256': identities['Contract'],
                          'environment-sha256': environment_ref['sha256'], 'execution-configuration-sha256': sha256(configuration),
                          'execution-profile': execution, 'chain': chain, 'result': 'pass',
                          'producer': {'syntax': 'qpdf',
                                       'standards': case.get('standards-producer', 'arlington'),
                                       'semantic': 'folio-pdf-' + case['label'].lower(),
                                       'visual': 'pdfium-cli'}[chain],
                          'configuration': reference(root, root / ('capabilities/evidence/' + case['profile'] + '.md')),
                          'report': reference(root, report_file), 'negative-controls': report['negative-controls']}
                path = scope / (chain + '.yaml')
                write_json(path, record)
                records.append(reference(root, path))
            require_unchanged(root, contract, candidate)
            require_staged_build(root, contract, build_record)
            inventory['certifications'].append({'obligation': obligation, 'environment': profile['id'],
                'execution-profile': execution, 'configuration': reference(root, configuration), 'records': records})
        observed_after = output / ('jdk' + str(major) + '-environment-after')
        if observe_environment(root, profile['identity']['image'], helper, observed_after, profile, harness) != environment:
            raise ValueError('Environment/tool identities changed during observations')
    require_unchanged(root, contract, candidate)
    require_staged_build(root, contract, build_record)
    if authority.read_bytes() != previous_bytes:
        raise ValueError('Evidence authority changed during certification; refusing to overwrite it')
    write_json(output / 'observed-index.json', inventory)
    write_json(output / 'identities.json', identities)
    (output / 'prior-index.sha256').write_text(hashlib.sha256(previous_bytes).hexdigest() + '\n')
    merged = publish_index(root, output)
    print('Recorded exactly eight ' + case['label'] + ' certifications; retained '
          + str(len(merged['certifications']) - 8) + ' current certifications for other obligations.', flush=True)


def main():
    import argparse
    import os
    import shutil
    import tempfile
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('stage', 'certify', 'collect', 'preservation', 'plan', 'merge-index'))
    parser.add_argument('output', nargs='?', type=Path)
    parser.add_argument('--obligation', choices=('transactions', 'values', 'pages'), default='transactions')
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    root = args.root.resolve()
    if args.action == 'merge-index':
        if args.output is None:
            parser.error('merge-index requires a completed observation directory')
        publish_index(root, (root / args.output).resolve())
        return
    if args.action == 'preservation':
        if args.output is None:
            parser.error('preservation requires an observation directory')
        result = record_preservation(root, (root / args.output).resolve())
        if result['result'] != 'pass':
            raise ValueError('T09 final encoded-stream preservation did not pass')
        return
    if args.action == 'collect':
        import json
        if args.output is None:
            parser.error('collect requires an observation directory')
        print(json.dumps(collect_reports(root, (root / args.output).resolve(), args.obligation), indent=2))
        return
    import yaml
    contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
    helper = Path(os.environ['FOLIO_HARFBUZZ_HELPER']).resolve(strict=True)
    if not helper.is_file() or not os.access(helper, os.X_OK):
        raise ValueError('An explicit executable native helper is required')
    if args.action == 'plan':
        if args.output is None:
            parser.error('plan requires a fresh output directory')
        record_plan(root, (root / args.output).resolve(), contract, helper, args.obligation)
        return
    if args.action == 'stage':
        base = root / 'target/foundation-0.1.0'
        base.mkdir(parents=True, exist_ok=True)
        before = capture_build_inputs(root, contract)
        (base / 'build-inputs.json').unlink(missing_ok=True)
        command = [str(root / 'mvnw'), '-B', '-ntp', '-Drevision=0.1.0', '-Pcentral-release',
                   '-DskipTests', '-DincludeScope=test', '-Dmdep.outputFile=target/t03-classpath.txt',
                   'clean', 'package', 'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath']
        print('Building the unsigned local candidate and acceptance harness', flush=True)
        logs = Path(tempfile.mkdtemp(prefix='t03-stage-', dir=root / '.build-cache'))
        run_logged(command, logs / 'build.txt', cwd=root)
        base.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(logs / 'build.txt', base / 'build.txt')
        stage_products(root, contract)
        harness = stage_harness(root, base)
        write_json(base / 'build-command.json', command)
        record_staged_build(root, contract, before, harness, base / 'build-inputs.json')
        # Flattened POMs are build intermediates, already retained in the candidate.
        for module in ('', 'pdf-bom', 'pdf-provider-contract', 'pdf-conversion', 'pdf-document',
                       'pdf-migration-itext7', 'pdf-migration-itext7-preview', 'pdf-acceptance',
                       'build-tools/inventory', 'build-tools/release'):
            (root / module / '.flattened-pom.xml').unlink(missing_ok=True)
    else:
        if args.output is None:
            parser.error('certify requires a fresh repository-relative output directory')
        certify(root, root / args.output, contract, helper, args.obligation)


if __name__ == '__main__':
    main()
