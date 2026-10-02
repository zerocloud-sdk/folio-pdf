#!/usr/bin/env python3
"""Record or independently replay artifact-bound embedded-files-only certification."""
import importlib.util
import json
from pathlib import Path
import sys
import tempfile


def module(name):
    spec = importlib.util.spec_from_file_location(name.replace('-', '_'), Path(__file__).with_name(name + '.py'))
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


OBSERVER = module('t80-observer')
BASE = module('t78-certification')
require, digest, properties, CHAINS = OBSERVER.require, OBSERVER.digest, BASE.properties, BASE.CHAINS


def identities(root, original=True):
    identities = BASE.identities(root, original)
    pin = properties((root / 'scripts/t80-evidence-pin.properties').read_bytes())
    require(len(pin) > 100, 'T80 producer identities have not been frozen')
    for name, expected in pin.items():
        path = root / name
        require(path.resolve(strict=True) == path and path.is_file() and digest(path.read_bytes()) == expected,
                'T80 pinned input or tool is missing or changed: ' + name)
    identities.update(pin)
    runtime = root / '.build-cache/qpdf/t80-r1/runtime'
    expected_runtime = {}
    for line in (root / 'scripts/t80-qpdf-runtime.sha256').read_text().splitlines():
        identity, name = line.split('  ', 1)
        path = runtime / name
        require(path.resolve(strict=True) == path and digest(path.read_bytes()) == identity, 'EFF qpdf runtime identity mismatch')
        expected_runtime[name] = identity
        identities[path.relative_to(root).as_posix()] = identity
    require({p.relative_to(runtime).as_posix() for p in runtime.rglob('*') if p.is_file()} == set(expected_runtime),
            'EFF qpdf runtime contains unpinned files')
    folder = root / '.build-cache/t80-arlington-model'
    entries = {}
    for line in (root / 'scripts/t80-arlington-runtime.sha256').read_text().splitlines():
        identity, name = line.split('  ', 1)
        path = folder / name
        require(path.resolve(strict=True) == path and digest(path.read_bytes()) == identity, 'Embedded-file model identity mismatch')
        entries[name] = identity
        identities[path.relative_to(root).as_posix()] = identity
    require({p.relative_to(folder).as_posix() for p in folder.rglob('*') if p.is_file()} == set(entries),
            'Embedded-file model contains unpinned files')
    return identities


def observe_one(observer, pdf, case, directory, public, output, api='native'):
    secret = OBSERVER.credential(case)
    owner_secret = OBSERVER.credential(case, True)
    # This entire process has no password, key or authenticated input. Preserve
    # the original encrypted artifact while checking its clear page derivative.
    with tempfile.TemporaryDirectory(prefix='folio-clear-pages-') as folder:
        clear = Path(folder) / 'clear-pages.pdf'
        observer.clear_pages(pdf, clear, directory + '/semantic/unauthenticated')
        require(observer.core_standards(clear, directory + '/standards/clear-core') == 'pass', 'Clear document standards failed')
        require(observer.visual(pdf, clear, case['pages'], directory + '/visual') == 'pass', 'Original clear paint differs')
    require(observer.syntax(pdf, secret, directory + '/syntax') == 'pass', 'Original encrypted syntax failed')
    dictionary = observer.raw_dictionary(pdf, directory + '/standards/original-dictionary', output)
    require(dictionary['result'] == 'pass',
            'Original plaintext EFF dictionary predicates failed')
    # Arlington's pinned PDFium parser crashes on selective traditional xrefs,
    # R4 EFF dictionaries with an explicit byte Length, and incremental trailers. The qualified
    # raw parser owns those same rules;
    # compatible originals additionally receive the unchanged Arlington engine.
    if dictionary['arlington-compatible']:
        require(not observer.dictionary_standards(pdf, directory + '/standards/dictionary', output, owner_secret),
                'External embedded-file dictionary predicates failed: ' + directory)
    BASE.credential_proofs(observer, pdf, case, directory + '/standards/authentication', secret, owner_secret)
    # pdfcpu's encrypted object-stream loader incorrectly decrypts Identity
    # object streams for AESV2. Its qualified role here is strict clear-page
    # core validation above; original encryption rules are independently checked
    # by Arlington, pypdf dictionary predicates and credential/Perms proofs.
    require(observer.semantic(pdf, case, public, directory + '/semantic', secret, api) == 'pass',
            'Original EFF bytes or public observations disagree: ' + directory)


def qualify(observer):
    authority = observer.root / 'capabilities/profiles/T80-controls'
    definition = json.loads(observer.read(authority / 'rules.json'))
    require(definition['profile'] == OBSERVER.PROFILE and set(definition['required-rules']) == set(definition['rules'])
            and len(definition['required-rules']) == len(set(definition['required-rules'])), 'Missing required embedded-file rule')
    checked = []
    for name, rule in definition['rules'].items():
        pdf = authority / rule['file']
        require(digest(observer.read(pdf)) == rule['sha256'], 'Embedded-file control identity mismatch')
        directory = 'negative/standards/attachment-security/' + name
        if rule['producer'] == 'arlington':
            findings = observer.dictionary_standards(pdf, directory, rule['model'] == 'output')
            require(findings and any(rule['finding'] in finding for finding in findings), 'Undetected EFF control: ' + name)
        elif rule['producer'] == 'pypdf-dictionary':
            finding = observer.raw_dictionary(pdf, directory, rule['model'] == 'output')
            require(rule['finding'] in finding['findings'] or finding['diagnostic'] == 'input-rejected',
                    'Undetected raw EFF dictionary control: ' + name)
        elif rule['producer'] == 'pypdf':
            finding = observer.pypdf(pdf, b'baseline-user', directory)
            require(finding['authenticated'] and finding['perms-invalid'] and finding['diagnostic'] == rule['finding'],
                    'External EFF Perms control was not detected: ' + name)
        elif rule['producer'] == 'pypdf-authentication':
            for label in ('user', 'owner'):
                finding = observer.pypdf(pdf, ('baseline-' + label).encode(), directory + '/' + label)
                require(not finding['authenticated'] and finding['authority'] == 0, 'Metadata-sensitive key defect was undetected')
        elif rule['producer'] in ('bytes', 'bytes-standards'):
            finding = observer.byte_observation(pdf, b'baseline-user', 'negative/' + ('standards' if rule['producer'] == 'bytes-standards' else 'semantic') + '/attachment-bytes/' + name)
            require(finding.get(rule['finding']) is False or finding.get('diagnostic') == 'input-rejected',
                    'Original EFF byte/route defect was not detected: ' + name)
        elif rule['producer'] == 'visual':
            directory = 'negative/visual/changed-clear-paint'
            with tempfile.TemporaryDirectory(prefix='folio-clear-visual-') as folder:
                clear = Path(folder) / 'clear.pdf'
                observer.clear_pages(pdf, clear, directory + '/unauthenticated', paint=False)
                require(observer.visual(pdf, clear, 1, directory) == 'fail', 'Changed clear paint was undetected')
        elif rule['producer'] == 'qpdf':
            require(observer.syntax(pdf, b'baseline-user', 'negative/syntax/' + name) == 'fail', 'Malformed original EFF syntax was undetected')
        else:
            raise ValueError('Unqualified attachment-only producer')
        checked.append(name)
    baseline = OBSERVER.BASE.Observations(observer.root, observer.output, observer.replay)
    baseline_rules = BASE.qualify(baseline)
    observer.files.update(baseline.files)
    observer.emit('negative/standards/attachment-security/result.json', {'result': 'fail', 'detected-rules': sorted(checked),
                  'retained-baseline-rules': baseline_rules})
    correct = observer.root / 'capabilities/profiles/T80-embedded-files-only/aes-256-r6.pdf'
    case = {'algorithm': 'AES_256', 'revision': 6, 'v': 5, 'version': '1.7', 'mask': -3904,
            'credential': 'ordinary', 'pages': 1, 'metadata': False}
    for field in ('scope', 'revision', 'crypto-mode'):
        public = OBSERVER.expected_public(case); public[field] = 'incorrect'
        require(observer.semantic(correct, case, public, 'negative/semantic/attachment-public-' + field) == 'fail',
                'Changed public EFF observation was undetected')
    for label, secret in [('incorrect', b'incorrect'), ('missing', b'')]:
        finding = observer.byte_observation(correct, secret, 'negative/semantic/attachment-credentials/' + label)
        require(not finding['authenticated'] and not finding.get('embedded-file-matches'), 'An invalid credential disclosed EF bytes')
    return sorted(checked), baseline_rules


def observe(root, output, execution, replay=False):
    with OBSERVER.cancellation_scope():
        return observe_bounded(root, output, execution, replay)


def observe_bounded(root, output, execution, replay=False):
    require(execution in ('IN_PROCESS', 'HARDENED_WORKER'), 'The actual Native execution mode is required')
    observer = OBSERVER.Observations(root, output, replay)
    original = identities(root, not replay)
    observer.emit('identities-before.json', original)
    require(properties(observer.read(output / 'products.properties')) == {'phase': 'products-only',
        'native-execution-profile': execution, 'facade-execution-profile': 'IN_PROCESS'}, 'Product execution mode mismatch')
    authority = root / 'capabilities/profiles/T80-embedded-files-only'
    corpus = json.loads(observer.read(authority / 'products.json'))
    require(corpus['profile'] == OBSERVER.PROFILE and corpus['visual'] == {'dpi': 144, 'render-annotations': True,
        'metric': 'AE', 'fuzz': 0, 'threshold': 0}, 'Closed embedded-files-only or visual policy changed')
    all_originals = {}
    for api in ('native', 'facade'):
        for name, case in corpus['products'].items():
            if case.get('apis', 'native,facade') == 'native' and api == 'facade': continue
            directory = api + '-' + name
            pdf = output / directory / 'product.pdf'
            source = authority / case['source']
            receipt = properties(observer.read(output / directory / 'publication.properties'))
            require(receipt == {'execution-profile': execution if api == 'native' else 'IN_PROCESS', 'save-mode': case['mode'],
                'target-name': 'target', 'status': 'COMMITTED', 'partial-output-possible': 'false', 'source-preserved': 'pass',
                'reopened': 'pass', 'source-sha256': digest(observer.read(source)), 'output-sha256': digest(observer.read(pdf))},
                'Actual public embedded-files-only product identity or mode mismatch')
            clear = properties(observer.read(output / directory / 'clear-observation.properties'))
            require(clear == {'execution-profile': execution if api == 'native' else 'IN_PROCESS', 'credential': 'absent',
                'clear-document': 'pass', 'attachment-access': 'CREDENTIAL_REQUIRED', 'owner-attachment-bytes': 'pass'},
                'The public clear-content or protected-attachment observation changed')
            public = properties(observer.read(output / directory / 'reopened.properties'))
            require(public == OBSERVER.expected_public(case, api), 'Reopened scope does not match the literal contract')
            if case['mode'] == 'INCREMENTAL':
                require(observer.read(pdf).startswith(observer.read(source)) and len(observer.read(pdf)) > len(observer.read(source)),
                        'Incremental publication did not preserve its complete encrypted prefix')
            all_originals[pdf] = digest(observer.read(pdf))
            observe_one(observer, pdf, case, directory, public, case['mode'] != 'INCREMENTAL', api)
    inputs = json.loads(observer.read(authority / 'cases.json'))
    successes = []
    for name, definition in inputs.items():
        if definition['expected'] != 'success' or not definition.get('r'): continue
        revision = definition['r']
        case = {'algorithm': 'AES_256' if revision >= 5 else 'AES_128' if definition['method'] == 'AESV2' else 'RC4_128',
                'revision': revision, 'version': definition['version'], 'mask': definition['permissions'], 'pages': 1,
                'credential': 'empty-user' if definition['credential'] == 'empty' else definition['credential'], 'metadata': definition['metadata'], 'v': definition['v'], 'method': definition['method']}
        pdf = authority / (name + '.pdf')
        require(digest(observer.read(pdf)) == definition['sha256'], 'Original encrypted embedded-files-only fixture changed')
        observe_one(observer, pdf, case, 'inputs/' + name, OBSERVER.expected_public(case), False)
        successes.append(name)
    checked, baseline_rules = qualify(observer)
    observer.emit('coverage.json', {'required-security-rules': checked, 'applied-security-rules': checked,
        'retained-baseline-rules': baseline_rules, 'products': sorted(corpus['products']), 'original-inputs': sorted(successes),
        'native-execution-profile': execution, 'facade-execution-profile': 'IN_PROCESS'})
    require(original == identities(root, not replay), 'Tools or frozen authorities changed during embedded-files-only observation')
    observer.emit('identities-after.json', original)
    for path, identity in all_originals.items():
        require(digest(observer.read(path)) == identity, 'A randomized encrypted embedded-files-only product changed')
    return observer


def record(root, output, execution):
    observe(root, output, execution)
    paths = sorted(path for path in output.rglob('*') if path.is_file())
    require(all(path.resolve() == path for path in output.rglob('*')), 'Linked evidence cannot be retained')
    manifest = output / 'retained-files.sha256'
    with manifest.open('x') as stream:
        stream.write(''.join(digest(path.read_bytes()) + '  ' + path.relative_to(output).as_posix() + '\n' for path in paths))
    result = {'profile': OBSERVER.PROFILE, 'phase': 'certification', 'native-execution-profile': execution,
              'facade-execution-profile': 'IN_PROCESS', 'retained-files-sha256': digest(manifest.read_bytes()), **dict.fromkeys(CHAINS, 'pass')}
    with (output / 'result.properties').open('x') as stream:
        stream.write(''.join(key + '=' + value + '\n' for key, value in sorted(result.items())))
    print('T80 independent syntax, standards, semantic and visual observations passed', flush=True)


if __name__ == '__main__':
    require(len(sys.argv) == 4, 'Usage: t80-certification.py <repository> <products-directory> <execution-profile>')
    record(Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve(), sys.argv[3])
