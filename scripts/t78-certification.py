#!/usr/bin/env python3
"""Record or independently replay the frozen baseline security observations."""
import importlib.util
import json
import os
from pathlib import Path
import sys
import tempfile

SPEC = importlib.util.spec_from_file_location('t78_observer', Path(__file__).with_name('t78-observer.py'))
OBSERVER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(OBSERVER)
require, digest = OBSERVER.require, OBSERVER.digest
CHAINS = ('syntax', 'standards', 'semantic', 'visual')


def properties(data):
    result = {}
    for line in data.decode().splitlines():
        if not line or line.startswith(('#', '!')): continue
        key, separator, value = line.partition('=')
        require(separator and key not in result, 'Malformed or duplicate evidence property')
        result[key] = value
    return result


def identities(root, original=True):
    pin = properties((root / 'scripts/t78-evidence-pin.properties').read_bytes())
    require(len(pin) > 100, 'T78 producer identities have not been frozen')
    for name, expected in pin.items():
        path = root / name
        require(path.resolve(strict=True) == path and path.is_file() and digest(path.read_bytes()) == expected,
                'T78 pinned input or tool is missing or changed: ' + name)
    for manifest, folder in [('scripts/t78-qpdf-runtime.sha256', '.build-cache/qpdf/t78-r1/runtime'),
                             ('scripts/imagemagick-runtime.sha256', '.build-cache/imagemagick/7.1.2-30/runtime'),
                             ('scripts/t78-checkers-runtime.sha256', '.build-cache/t78-security-checkers'),
                             ('scripts/t78-arlington-runtime.sha256', '.build-cache/t78-arlington-model')]:
        entries = {}
        for line in (root / manifest).read_text().splitlines():
            expected, relative = line.split('  ', 1)
            path = root / folder / relative
            require(path.resolve(strict=True) == path and digest(path.read_bytes()) == expected,
                    'Independent runtime identity mismatch')
            pin[folder + '/' + relative] = expected
            entries[relative] = expected
        if folder.startswith('.build-cache/t78-'):
            require({p.relative_to(root / folder).as_posix() for p in (root / folder).rglob('*') if p.is_file()} == set(entries),
                    'Independent runtime contains unpinned files')
    for name in ('LD_PRELOAD', 'LD_AUDIT', 'QPDF_CACHE_DIRECTORY', 'PDFIUM_CACHE_DIRECTORY',
                 'IMAGEMAGICK_CACHE_DIRECTORY', 'IMAGEMAGICK_RUNTIME_DIRECTORY', 'SHA256_COMMAND'):
        require(not os.environ.get(name), 'Certification forbids tool or loader overrides')
    if original:
        require(digest(Path(sys.executable).resolve().read_bytes()) == OBSERVER.PYTHON_SHA256,
                'The original observation requires the pinned Ubuntu Python executable')
    # Collection can use the host Python to verify/replay the exact retained
    # projection. This field belongs to the original Ubuntu certification run.
    pin['python-executable-sha256'] = OBSERVER.PYTHON_SHA256
    return pin


def credential_proofs(observer, pdf, case, directory, secret=None, owner_secret=None, owner_kind='qpdf'):
    secret = OBSERVER.credential(case) if secret is None else secret
    expected = 2 if case['credential'] == 'equal' else 1
    observation = observer.pypdf(pdf, secret, directory + '/user')
    require(observation['authenticated'] and observation['authority'] == expected
            and not observation['perms-invalid'] and observation['diagnostic'] == 'none',
            'Independent user authentication or encrypted permissions failed')
    require(observer.authentication(pdf, secret, directory + '/user-proof', 'owner' if expected == 2 else 'user'),
            'Independent user/owner predicate differs')
    if owner_kind == 'none': return
    owner_secret = OBSERVER.credential(case, True) if owner_secret is None else owner_secret
    if case['credential'] == 'empty-owner':
        observation = observer.pypdf(pdf, owner_secret, directory + '/empty-owner')
        require(not observation['authenticated'], 'An empty owner credential must not acquire generated owner authority')
    elif owner_kind == 'pypdf':
        observation = observer.pypdf(pdf, owner_secret, directory + '/owner')
        require(observation['authenticated'] and observation['authority'] == 2, 'Literal ISO legacy owner proof failed')
    else:
        require(observer.authentication(pdf, owner_secret, directory + '/owner-proof', 'owner'),
                'Independent owner predicate failed')


def observe_one(observer, pdf, case, directory, public, output, secret=None, owner_secret=None, owner_kind='qpdf'):
    secret = OBSERVER.credential(case) if secret is None else secret
    require(observer.syntax(pdf, secret, directory + '/syntax') == 'pass', 'Original input or product syntax failed')
    literal_owner = case['algorithm'] == 'RC4_40' and case['revision'] == 3
    if output and literal_owner:
        owner_kind = 'pypdf'
    dictionary_secret = (OBSERVER.credential(case, True) if output and case['credential'] != 'empty-owner' and not literal_owner
                         else secret)
    if any(value > 127 for value in dictionary_secret):
        dictionary_secret = OBSERVER.credential(case, True) if owner_secret is None else owner_secret
    require(not observer.dictionary_standards(pdf, directory + '/standards/dictionary', output, dictionary_secret),
            'External security dictionary predicates failed: ' + directory)
    if case['algorithm'] != 'NONE':
        credential_proofs(observer, pdf, case, directory + '/standards/authentication', secret, owner_secret, owner_kind)
        # The tool's command API distinguishes user from owner credentials.
        # Each case with a non-ASCII user has a known ASCII owner. New R3/40 output uses the
        # ISO owner construction verified independently by pypdf and pdfcpu.
        use_owner = output and case['credential'] != 'empty-owner' or any(value > 127 for value in secret)
        selected = (OBSERVER.credential(case, True) if owner_secret is None else owner_secret) if use_owner else secret
        require(observer.encrypted_core_standards(pdf, selected, directory + '/standards/encrypted-core', owner=use_owner) == 'pass',
                'External encrypted dictionary validation failed: ' + directory)
    require(observer.semantic(pdf, case, public, directory + '/semantic', secret) == 'pass',
            'Independent plaintext or security observation disagrees: ' + directory)
    with tempfile.TemporaryDirectory(prefix='folio-decoded-') as folder:
        decoded = Path(folder) / 'decoded.pdf'
        observer.decrypted(pdf, secret, decoded, directory + '/standards/decryption')
        require(observer.core_standards(decoded, directory + '/standards/core') == 'pass', 'Core document standards failed')
        require(observer.visual(pdf, decoded, case['pages'], directory + '/visual') == 'pass', 'Authored pixel grid differs')


def qualify(observer):
    root = observer.root
    authority = root / 'capabilities/profiles/T78-controls'
    definition = json.loads(observer.read(authority / 'rules.json'))
    require(definition['profile'] == OBSERVER.PROFILE and set(definition['required-rules']) == set(definition['rules'])
            and len(definition['required-rules']) == len(set(definition['required-rules'])), 'Missing required security rule')
    checked = []
    for name, rule in definition['rules'].items():
        pdf = authority / rule['file']
        require(digest(observer.read(pdf)) == rule['sha256'], 'Qualification fixture identity mismatch')
        directory = 'negative/standards/security/' + name
        if rule['producer'] == 'arlington':
            findings = observer.dictionary_standards(pdf, directory, rule['model'] == 'output')
            require(findings and any(rule['finding'] in finding for finding in findings), 'Undetected security control: ' + name)
        elif rule['producer'] == 'pypdf':
            finding = observer.pypdf(pdf, b'baseline-user', directory)
            require(finding['authenticated'] and finding['perms-invalid'] and finding['diagnostic'] == rule['finding'],
                    'External encrypted-Perms control was not detected')
        elif rule['producer'] == 'pdfcpu':
            observer.encrypted_core_standards(pdf, b'baseline-owner', directory, rule['finding'])
        elif rule['producer'] == 'pypdf-owner':
            user = observer.pypdf(pdf, b'baseline-user', directory + '/user')
            owner = observer.pypdf(pdf, b'baseline-owner', directory + '/owner')
            require(user['authenticated'] and user['authority'] == 1 and not owner['authenticated'],
                    'The nonnormative output owner-construction control was not detected')
        else: raise ValueError('Unqualified standards producer')
        checked.append(name)
    observer.emit('negative/standards/security/result.json', {'result': 'fail', 'detected-rules': sorted(checked)})
    core_path = root / 'capabilities/profiles/T03-standards/pdfcpu.properties'
    core = properties(observer.read(core_path))
    core_rules = core['required-rules'].split(',')
    require(set(core_rules) == set(core['covered-rules'].split(',')), 'A required core document rule is uncovered')
    for rule in core_rules:
        prefix = 'negative.' + rule
        pdf = (core_path.parent / core[prefix + '.path']).resolve()
        require(digest(observer.read(pdf)) == core[prefix + '.sha256'], 'Core control identity mismatch')
        observer.core_standards(pdf, 'negative/standards/core/' + rule, core[prefix + '.finding'])
    require(observer.syntax(authority / 'syntax-truncated.pdf', b'', 'negative/syntax') == 'fail', 'Syntax defect was not detected')
    case = json.loads(observer.read(root / 'capabilities/profiles/T78-password/products.json'))['products']['secure-default']
    correct = root / 'capabilities/profiles/T78-password/aes-256-r6.pdf'
    # These controls test interpretation separately from external cryptographic
    # proof. All original public values must participate in the comparison.
    literal = dict(case, mask=-4)
    for key in OBSERVER.expected_public(literal):
        public = OBSERVER.expected_public(literal); public[key] = 'incorrect'
        require(observer.semantic(correct, literal, public, 'negative/semantic/public-' + key) == 'fail',
                'A changed public observation was not detected')
    changed = authority / 'changed-paint.pdf'
    require(observer.semantic(changed, case, OBSERVER.expected_public(case), 'negative/semantic/paint') == 'fail',
            'Changed decrypted content was not detected')
    with tempfile.TemporaryDirectory(prefix='folio-control-') as folder:
        decoded = Path(folder) / 'decoded.pdf'
        observer.decrypted(changed, b'baseline-user', decoded, 'negative/visual/paint/decryption')
        require(observer.visual(changed, decoded, 1, 'negative/visual/paint') == 'fail', 'Changed visual paint was not detected')
        expected = root / 'capabilities/profiles/T78-password/expected.png'
        one_pixel = authority / 'one-pixel.png'
        measured = observer.compare(expected, one_pixel, Path(folder) / 'difference.png', 'negative/visual/one-pixel')
        require(OBSERVER.Decimal(measured) == 1, 'The authored one-pixel defect was not detected exactly')
        observer.emit('negative/visual/one-pixel/result.json', {'result': 'fail', 'absolute-error': measured,
            'expected-sha256': digest(observer.read(expected)), 'changed-sha256': digest(observer.read(one_pixel))})
    for label, secret in [('incorrect', b'incorrect'), ('missing', b'')]:
        result = observer.pypdf(correct, secret, 'negative/standards/credentials/' + label)
        require(not result['authenticated'] and result['authority'] == 0, 'An invalid credential authenticated independently')
    observer.emit('negative/standards/result.json', {'result': 'fail', 'security-rules': sorted(checked), 'core-rules': core_rules,
                  'credential-controls': ['incorrect', 'missing']})
    return sorted(checked)


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
    authority = root / 'capabilities/profiles/T78-password'
    corpus = json.loads(observer.read(authority / 'products.json'))
    require(corpus['profile'] == OBSERVER.PROFILE and corpus['visual'] == {'dpi': 144, 'render-annotations': True,
        'metric': 'AE', 'fuzz': 0, 'threshold': 0}, 'Closed security profile or visual policy changed')
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
                'Actual public product identity or mode mismatch')
            public = properties(observer.read(output / directory / 'reopened.properties'))
            require(public == OBSERVER.expected_public(case), 'Reopened product does not match the literal contract')
            if case['mode'] == 'INCREMENTAL':
                require(observer.read(pdf).startswith(observer.read(source)) and len(observer.read(pdf)) > len(observer.read(source)),
                        'Incremental publication did not preserve its complete encrypted prefix')
            all_originals[pdf] = digest(observer.read(pdf))
            observe_one(observer, pdf, case, directory, public, True)
    inputs = json.loads(observer.read(authority / 'cases.json'))
    for name, definition in inputs.items():
        if definition['expected'] != 'success': continue
        revision = definition.get('r', 0)
        algorithm = ('AES_256' if revision >= 5 else 'AES_128' if definition.get('method') == 'AESV2'
                     else 'RC4_40' if definition.get('bits') == 40 else 'RC4_128' if revision else 'NONE')
        case = {'algorithm': algorithm, 'revision': revision, 'version': definition.get('version', '1.7'),
                'mask': definition.get('permissions', -4) if revision else 0, 'pages': 1,
                'credential': definition.get('credential', 'ordinary'),
                'metadata': definition.get('metadata', True), 'v': definition.get('v')}
        if 'method' in definition: case['method'] = definition['method']
        secret, owner = OBSERVER.credential(case), OBSERVER.credential(case, True)
        if name.endswith('-prepared'): secret, owner = b'IX', b'owner-IX'
        if name == 'aes-256-r5-noncanonical-owner': secret = b'IX'
        if name == 'legacy-literal-question': secret = b'?'
        kind = ('pypdf' if revision == 3 and definition.get('bits') == 40 and definition.get('owner_hash') != 'interop'
                else 'none' if name == 'aes-256-r5-noncanonical-owner' else 'qpdf')
        pdf = authority / (name + '.pdf')
        require(digest(observer.read(pdf)) == definition['sha256'], 'Original encrypted fixture identity mismatch')
        observe_one(observer, pdf, case, 'inputs/' + name, OBSERVER.expected_public(case), False, secret, owner, kind)
    checked = qualify(observer)
    observer.emit('coverage.json', {'required-security-rules': checked, 'applied-security-rules': checked,
        'products': sorted(corpus['products']), 'original-inputs': sorted(name for name, d in inputs.items() if d['expected'] == 'success'),
        'native-execution-profile': execution, 'facade-execution-profile': 'IN_PROCESS'})
    require(original == identities(root, not replay), 'Tools or frozen authorities changed during observation')
    observer.emit('identities-after.json', original)
    for path, identity in all_originals.items():
        require(digest(observer.read(path)) == identity, 'A randomized encrypted product changed during observation')
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
    print('T78 independent syntax, standards, semantic and visual observations passed', flush=True)


if __name__ == '__main__':
    require(len(sys.argv) == 4, 'Usage: t78-certification.py <repository> <products-directory> <execution-profile>')
    record(Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve(), sys.argv[3])
