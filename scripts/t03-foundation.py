#!/usr/bin/env python3
"""Prepare and bind repository-only T03 observations; never publish a release."""
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
    """Use the existing identity authority, then restore its exact prior contents."""
    import re
    previous = authority.read_bytes()
    try:
        write_json(authority, inventory)
        run_logged([str(root / 'scripts/inventory'), 'readiness'], log, cwd=root, check=False)
        text = log.read_text()
        identities = {}
        for name in ('Candidate', 'Contract'):
            match = re.search(r'^' + name + r' identity: ([a-f0-9]{64})$', text, re.MULTILINE)
            if not match:
                raise ValueError('Inventory did not establish ' + name + ' identity')
            identities[name] = match.group(1)
        return identities
    finally:
        authority.write_bytes(previous)


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


def properties(path):
    """Read recorder-owned flat properties (values used here contain no escapes)."""
    return dict(line.split('=', 1) for line in Path(path).read_text().splitlines()
                if line and not line.startswith('#') and '=' in line)


def collect_reports(root, run):
    result = properties(run / 'result.properties')
    for chain in CHAINS:
        if result.get(chain) != 'pass':
            raise ValueError('Unobserved or non-passing T03 chain: ' + chain)
    negative = properties(run / 'negative/result.properties')
    reports = {}
    for chain in CHAINS:
        if negative.get(chain) != 'fail':
            raise ValueError('Missing detected negative control for ' + chain)
        report = {'chain': chain, 'result': 'pass', 'products': [], 'findings': [],
                  'negative-controls': [reference(root, run / 'negative' / (chain + '.txt'))]}
        for edition in ('native', 'facade'):
            directory = run / edition
            observed = properties(directory / 'result.properties')
            if observed.get(chain) != 'pass':
                raise ValueError(edition + ' chain did not pass: ' + chain)
            product = reference(root, directory / 'blank.pdf')
            if product['sha256'] != observed.get('input-sha256'):
                raise ValueError('Observed product changed after checking')
            report['products'].append(product)
            report['findings'].append(reference(root, directory / (chain + '.txt')))
            report['findings'].append(reference(root, directory / 'result.properties'))
            if chain == 'visual':
                report['findings'] += [reference(root, path) for path in sorted(directory.glob('*.png'))]
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
        reports[chain] = report
    return reports


def require_environment(expected, actual):
    if expected != actual:
        raise ValueError('Observed environment does not match its approved profile: ' + repr(actual))
    return actual


def write_json(path, value):
    import json
    Path(path).write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n')


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
    tool_files = [
        ('qpdf', '12.4.0', '.build-cache/qpdf/12.4.0/bin/qpdf', ['syntax']),
        ('pdfcpu', '0.15.0', '.build-cache/pdfcpu/0.15.0/pdfcpu', ['standards']),
        ('arlington', '0.81', '.build-cache/arlington/fe4a1a8/TestGrammar/bin/linux/TestGrammar', ['standards']),
        ('pdfium-cli', 'v0.11.2-pdfium-chromium-7881', '.build-cache/pdfium/v0.11.2-chromium-7881/bin/pdfium', ['visual']),
        ('imagemagick', '7.1.2-30', '.build-cache/imagemagick/7.1.2-30/bin/imagemagick.AppImage', ['visual'])]
    for name, version, path, chains in tool_files:
        pin_name = 'pdfium' if name == 'pdfium-cli' else name
        pin = properties(root / ('scripts/' + pin_name + '-pin.properties'))
        key = {'qpdf': 'QPDF_BINARY_SHA256', 'pdfium-cli': 'PDFIUM_EXECUTABLE_SHA256',
               'imagemagick': 'IMAGEMAGICK_EXECUTABLE_SHA256'}.get(name, 'sha256')
        observed_hash = sha256(root / path)
        if observed_hash != pin[key]:
            raise ValueError('Observed checker executable differs from its pin: ' + name)
        tools.append({'id': name, 'kind': 'external-tool', 'version': version, 'sha256': observed_hash, 'chains': chains})
    tools.append({'id': 'folio-pdf-t03', 'kind': 'project-test', 'version': '0.1.0',
                  'sha256': sha256(harness / 'acceptance.jar'), 'chains': ['semantic']})
    return {'schema-version': 1, 'profile': profile['id'], 'identity': identity,
            'host': {'kernel': (directory / 'kernel.txt').read_text().strip(), 'architecture': identity['architecture']},
            'native-engine': engine, 'tools': tools}


def certify(root, output, contract, helper):
    import yaml
    root = root.resolve()
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
    identities = candidate_identities(root, authority, inventory, output / 'candidate-identity.txt')
    environment_profiles = yaml.safe_load((root / contract['environments']).read_text())['profiles']
    classpath_files = certification_classpath(root, contract, receipt)
    cp = ':'.join('/workspace/' + path.relative_to(root).as_posix() for path in classpath_files)
    configuration_inputs = sorted(set(classpath_files + [root / item['path'] for item in receipt['harness']]
        + [build_record, base / 'build-command.json', root / 'scripts/imagemagick-runtime.sha256']
        + list((root / 'capabilities/profiles/T03-standards').rglob('*'))
        + [root / 'capabilities/profiles/T03-document-blank-visual.properties', root / 'capabilities/expected/T03-document-blank-144dpi-srgb.png']
        + [root / ('scripts/' + name + '-pin.properties') for name in ('qpdf', 'pdfium', 'imagemagick', 'pdfcpu', 'arlington')]))
    configuration_inputs = [reference(root, path) for path in configuration_inputs if path.is_file()]
    for profile in environment_profiles:
        major = profile['identity']['jdk-major']
        print('T03 environment JDK ' + str(major), flush=True)
        directory = output / ('jdk' + str(major) + '-environment')
        environment = observe_environment(root, profile['identity']['image'], helper, directory, profile, harness)
        record_path = directory / 'environment.yaml'
        write_json(record_path, environment)
        environment_ref = reference(root, record_path)
        inventory['environments'].append({'profile': profile['id'], 'record': environment_ref})
        for execution in ('IN_PROCESS', 'HARDENED_WORKER'):
            print('T03 certification JDK ' + str(major) + ' / ' + execution, flush=True)
            scope = output / ('jdk' + str(major) + '-' + execution.lower())
            scope.mkdir()
            inside = '/workspace/' + scope.relative_to(root).as_posix()
            command = container_command(root, profile['identity']['image'], helper)
            command[-1:-1] = ['--volume', str(scope) + ':' + inside + ':rw']
            options = ['-Xmx1024m', '-Duser.language=en', '-Duser.country=US', '-Duser.timezone=UTC',
                       '-Dfolio.harfBuzzHelper=/folio-harfbuzz/bin/folio-harfbuzz',
                       '-Dfolio.t03.executionProfile=' + execution]
            evidence_command = command + ['java'] + options + ['-cp', cp, 'net.zerocloud.pdf.acceptance.T03EvidenceCommand',
                '/workspace', inside + '/observations', execution, '0.1.0']
            test_options = [
                '-DrepositoryRoot=/workspace',
                '-DartifactPath=/workspace/target/foundation-0.1.0/artifacts/pdf-migration-itext7-preview-0.1.0.jar',
                '-DstableArtifactPath=/workspace/target/foundation-0.1.0/artifacts/pdf-migration-itext7-0.1.0.jar',
                '-DdocumentArtifactPath=/workspace/target/foundation-0.1.0/artifacts/pdf-document-0.1.0.jar',
                '-DtestClassesPath=/workspace/target/foundation-0.1.0/harness/facade-tests.jar']
            test_command = command + ['java'] + options + test_options + ['-cp', cp, 'org.junit.runner.JUnitCore'] + [
                'net.zerocloud.pdf.consumer.' + name for name in ('BlankDocumentWorkflowTest', 'WorkflowLifecycleTest',
                    'WorkflowTransactionContractTest', 'WorkflowResourceOwnershipTest')]
            test_command += ['net.zerocloud.pdf.itext7.consumer.BlankDocumentFacadeTest',
                             'net.zerocloud.pdf.migration.itext7.contract.JarContractIT',
                             'net.zerocloud.pdf.migration.itext7.contract.ClasspathExclusivityIT']
            config = {'schema-version': 1, 'candidate-sha256': identities['Candidate'],
                      'environment-sha256': environment_ref['sha256'], 'acceptance-profile': 'T03-document-workflow-transaction',
                      'execution-profile': execution, 'command': evidence_command, 'java-options': options,
                      'locale': 'en_US / C.UTF-8', 'timezone': 'UTC',
                      'settings': {'workflow-policy': 'REWRITE; PDF 1.7; finite system-default resource/transaction/worker policies; no network; tests select the recorded execution profile',
                                   'fonts': 'none; no text or resources in the T03 products',
                                   'providers': 'none; Stable Facade retains its IN_PROCESS default'},
                      'inputs': configuration_inputs}
            configuration = scope / 'execution.yaml'
            write_json(configuration, config)
            write_json(scope / 'contract-tests-command.json', test_command)
            run_logged(test_command, scope / 'contract-tests.txt', cwd=root, timeout=300)
            if 'OK (34 tests)' not in (scope / 'contract-tests.txt').read_text():
                raise ValueError('The complete T03 consumer/artifact contract suite did not execute')
            run_logged(evidence_command, scope / 'recorder.txt', cwd=root, timeout=300)
            reports = collect_reports(root, scope / 'observations')
            records = []
            for chain, report in reports.items():
                if chain == 'semantic':
                    report['findings'] += [reference(root, scope / 'contract-tests.txt'), reference(root, scope / 'contract-tests-command.json')]
                report['environment-observations'] = [reference(root, path) for path in sorted(directory.iterdir()) if path.is_file()]
                report_file = scope / (chain + '-report.json')
                write_json(report_file, report)
                record = {'schema-version': 1, 'obligation': 'transactions', 'acceptance-profile': 'T03-document-workflow-transaction',
                          'release': '0.1.0', 'candidate-sha256': identities['Candidate'], 'contract-sha256': identities['Contract'],
                          'environment-sha256': environment_ref['sha256'], 'execution-configuration-sha256': sha256(configuration),
                          'execution-profile': execution, 'chain': chain, 'result': 'pass',
                          'producer': {'syntax': 'qpdf', 'standards': 'arlington', 'semantic': 'folio-pdf-t03', 'visual': 'pdfium-cli'}[chain],
                          'configuration': reference(root, root / 'capabilities/evidence/T03-document-workflow-transaction.md'),
                          'report': reference(root, report_file), 'negative-controls': report['negative-controls']}
                path = scope / (chain + '.yaml')
                write_json(path, record)
                records.append(reference(root, path))
            require_unchanged(root, contract, candidate)
            require_staged_build(root, contract, build_record)
            inventory['certifications'].append({'obligation': 'transactions', 'environment': profile['id'],
                'execution-profile': execution, 'configuration': reference(root, configuration), 'records': records})
        observed_after = output / ('jdk' + str(major) + '-environment-after')
        if observe_environment(root, profile['identity']['image'], helper, observed_after, profile, harness) != environment:
            raise ValueError('Environment/tool identities changed during observations')
    require_unchanged(root, contract, candidate)
    require_staged_build(root, contract, build_record)
    write_json(authority, inventory)
    print('Recorded exactly eight T03 certifications. Other Foundation obligations remain uncertified.', flush=True)


def main():
    import argparse
    import os
    import shutil
    import tempfile
    import yaml
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('stage', 'certify'))
    parser.add_argument('output', nargs='?', type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
    helper = Path(os.environ['FOLIO_HARFBUZZ_HELPER']).resolve(strict=True)
    if not helper.is_file() or not os.access(helper, os.X_OK):
        raise ValueError('An explicit executable native helper is required')
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
        certify(root, root / args.output, contract, helper)


if __name__ == '__main__':
    main()
