#!/usr/bin/env python3
"""Record or independently replay artifact-bound clear-metadata certification."""
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


OBSERVER = module('t79-observer')
BASE = module('t78-certification')
require, digest, properties, CHAINS = OBSERVER.require, OBSERVER.digest, BASE.properties, BASE.CHAINS


def identities(root, original=True):
    identities = BASE.identities(root, original)
    pin = properties((root / 'scripts/t79-evidence-pin.properties').read_bytes())
    require(len(pin) > 100, 'T79 producer identities have not been frozen')
    for name, expected in pin.items():
        path = root / name
        require(path.resolve(strict=True) == path and path.is_file() and digest(path.read_bytes()) == expected,
                'T79 pinned input or tool is missing or changed: ' + name)
    identities.update(pin)
    folder = root / '.build-cache/t79-arlington-model'
    entries = {}
    for line in (root / 'scripts/t79-arlington-runtime.sha256').read_text().splitlines():
        identity, name = line.split('  ', 1)
        path = folder / name
        require(path.resolve(strict=True) == path and digest(path.read_bytes()) == identity, 'Clear-metadata model identity mismatch')
        entries[name] = identity
        identities[path.relative_to(root).as_posix()] = identity
    require({p.relative_to(folder).as_posix() for p in folder.rglob('*') if p.is_file()} == set(entries),
            'Clear-metadata model contains unpinned files')
    return identities


def observe_one(observer, pdf, case, directory, public, output, api='native'):
    secret = OBSERVER.credential(case)
    owner_secret = OBSERVER.credential(case, True)
    require(observer.syntax(pdf, secret, directory + '/syntax') == 'pass', 'Original input or product syntax failed')
    dictionary_secret = owner_secret if case['credential'] != 'empty-owner' else secret
    require(not observer.dictionary_standards(pdf, directory + '/standards/dictionary', output,
                                             dictionary_secret, case.get('metadata', False)),
            'External clear-metadata dictionary predicates failed: ' + directory)
    BASE.credential_proofs(observer, pdf, case, directory + '/standards/authentication')
    use_owner = case['credential'] != 'empty-owner'
    require(observer.encrypted_core_standards(pdf, owner_secret if use_owner else secret,
            directory + '/standards/encrypted-core', owner=use_owner) == 'pass',
            'External encrypted clear-metadata validation failed: ' + directory)
    require(observer.semantic(pdf, case, public, directory + '/semantic', secret, api) == 'pass',
            'Independent metadata, protected bytes or public observations disagree: ' + directory)
    with tempfile.TemporaryDirectory(prefix='folio-clear-metadata-') as folder:
        decoded = Path(folder) / 'decoded.pdf'
        observer.decrypted(pdf, secret, decoded, directory + '/standards/decryption')
        require(observer.core_standards(decoded, directory + '/standards/core') == 'pass', 'Core document standards failed')
        require(observer.visual(pdf, decoded, case['pages'], directory + '/visual') == 'pass', 'Authored pixel grid differs')


def qualify(observer):
    authority = observer.root / 'capabilities/profiles/T79-controls'
    definition = json.loads(observer.read(authority / 'rules.json'))
    require(definition['profile'] == OBSERVER.PROFILE and set(definition['required-rules']) == set(definition['rules'])
            and len(definition['required-rules']) == len(set(definition['required-rules'])), 'Missing required clear-metadata rule')
    checked = []
    for name, rule in definition['rules'].items():
        pdf = authority / rule['file']
        require(digest(observer.read(pdf)) == rule['sha256'], 'Clear-metadata control identity mismatch')
        directory = 'negative/standards/clear-security/' + name
        if rule['producer'] == 'arlington':
            findings = observer.dictionary_standards(pdf, directory, rule['model'] == 'output')
            require(findings and any(rule['finding'] in finding for finding in findings), 'Undetected clear-metadata control: ' + name)
        elif rule['producer'] == 'pypdf':
            finding = observer.pypdf(pdf, b'baseline-user', directory)
            require(finding['authenticated'] and finding['perms-invalid'] and finding['diagnostic'] == rule['finding'],
                    'External clear-metadata Perms control was not detected: ' + name)
        elif rule['producer'] == 'pypdf-authentication':
            for label in ('user', 'owner'):
                finding = observer.pypdf(pdf, ('baseline-' + label).encode(), directory + '/' + label)
                require(not finding['authenticated'] and finding['authority'] == 0, 'Metadata-sensitive key control was not detected')
        elif rule['producer'] == 'pdfcpu':
            observer.encrypted_core_standards(pdf, b'baseline-owner', directory, rule['finding'])
        elif rule['producer'] == 'bytes':
            directory = 'negative/semantic/clear-bytes/' + name
            finding = observer.byte_observation(pdf, b'baseline-user', directory)
            require(finding.get(rule['finding']) is False or finding.get('diagnostic') == 'input-rejected',
                    'Original metadata/protected-byte control was not detected: ' + name)
        elif rule['producer'] == 'visual':
            directory = 'negative/visual/clear-scope-changed-paint'
            with tempfile.TemporaryDirectory(prefix='folio-clear-visual-') as folder:
                decoded = Path(folder) / 'decoded.pdf'
                observer.decrypted(pdf, b'baseline-user', decoded, directory + '/decryption')
                require(observer.visual(pdf, decoded, 1, directory) == 'fail', 'Changed clear-scope paint was not detected')
        else:
            raise ValueError('Unqualified clear-metadata standards producer')
        checked.append(name)
    # The unchanged all-content qualification stays intact. Its syntax, core
    # document, visual and shared dictionary controls qualify those reused tools.
    baseline = OBSERVER.BASE.Observations(observer.root, observer.output, observer.replay)
    baseline_rules = BASE.qualify(baseline)
    observer.files.update(baseline.files)
    observer.emit('negative/standards/clear-security/result.json', {'result': 'fail', 'detected-rules': sorted(checked),
                  'retained-baseline-rules': baseline_rules})
    correct = observer.root / 'capabilities/profiles/T79-clear-metadata/aes-256-r6.pdf'
    case = {'algorithm': 'AES_256', 'revision': 6, 'v': 5, 'version': '1.7', 'mask': -3904,
            'credential': 'ordinary', 'pages': 1, 'metadata': False}
    for field in ('scope', 'revision', 'crypto-mode'):
        public = OBSERVER.expected_public(case); public[field] = 'incorrect'
        require(observer.semantic(correct, case, public, 'negative/semantic/clear-public-' + field) == 'fail',
                'A changed public scope observation was not detected')
    for label, secret in [('incorrect', b'incorrect'), ('missing', b'')]:
        result = observer.pypdf(correct, secret, 'negative/standards/clear-credentials/' + label)
        require(not result['authenticated'] and result['authority'] == 0, 'An invalid clear-metadata credential authenticated')
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
    authority = root / 'capabilities/profiles/T79-clear-metadata'
    corpus = json.loads(observer.read(authority / 'products.json'))
    require(corpus['profile'] == OBSERVER.PROFILE and corpus['visual'] == {'dpi': 144, 'render-annotations': True,
        'metric': 'AE', 'fuzz': 0, 'threshold': 0}, 'Closed clear-metadata or visual policy changed')
    all_originals = {}
    for api in ('native', 'facade'):
        for name, case in corpus['products'].items():
            directory = api + '-' + name
            pdf = output / directory / 'product.pdf'
            source = authority / case['source']
            receipt = properties(observer.read(output / directory / 'publication.properties'))
            require(receipt == {'execution-profile': execution if api == 'native' else 'IN_PROCESS', 'save-mode': case['mode'],
                'target-name': 'target', 'status': 'COMMITTED', 'partial-output-possible': 'false', 'source-preserved': 'pass',
                'reopened': 'pass', 'source-sha256': digest(observer.read(source)), 'output-sha256': digest(observer.read(pdf))},
                'Actual public clear-metadata product identity or mode mismatch')
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
                'credential': definition['credential'], 'metadata': definition['metadata'], 'v': definition['v'], 'method': definition['method']}
        pdf = authority / (name + '.pdf')
        require(digest(observer.read(pdf)) == definition['sha256'], 'Original encrypted clear-metadata fixture changed')
        observe_one(observer, pdf, case, 'inputs/' + name, OBSERVER.expected_public(case), False)
        successes.append(name)
    checked, baseline_rules = qualify(observer)
    observer.emit('coverage.json', {'required-security-rules': checked, 'applied-security-rules': checked,
        'retained-baseline-rules': baseline_rules, 'products': sorted(corpus['products']), 'original-inputs': sorted(successes),
        'native-execution-profile': execution, 'facade-execution-profile': 'IN_PROCESS'})
    require(original == identities(root, not replay), 'Tools or frozen authorities changed during clear-metadata observation')
    observer.emit('identities-after.json', original)
    for path, identity in all_originals.items():
        require(digest(observer.read(path)) == identity, 'A randomized encrypted clear-metadata product changed')
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
    print('T79 independent syntax, standards, semantic and visual observations passed', flush=True)


if __name__ == '__main__':
    require(len(sys.argv) == 4, 'Usage: t79-certification.py <repository> <products-directory> <execution-profile>')
    record(Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve(), sys.argv[3])
