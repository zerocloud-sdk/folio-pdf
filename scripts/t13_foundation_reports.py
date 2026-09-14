"""Verify and collect the T13 recorder's original, separate evidence chains."""
import hashlib
from decimal import Decimal
import json
import os
from pathlib import Path, PurePosixPath
import re
import zlib

PROFILE = 'T13-text-logical-structure'
PRODUCTS = ('nested-split-type3', 'marked-structure', 'embedded-font-kinds',
            'embedded-font-inheritance', 'uncertain-geometry')
CHAINS = ('syntax', 'standards', 'semantic', 'visual')
DECLARATION_PROFILES = {
    'pdfcpu': 'dcf92c9e8ab6a48fea9d581c99cfed86dc993d74e3a8de5cb6e9ae1da919f707',
    'arlington-core': '178df803628eb17a0e6a87e9c9faac441373edbc60db115cba8e71dbb0d6d586',
    'arlington-fonts': '7c4889c2f294cc4094bacc9dfd8af59642e183b3153de6c1b31f30429f253852',
    'arlington-text': '41e5d9fd856574038ac59ce7e26c4a96f6a28ab78599b7d5f6c9afca723555bf',
    'arlington-cid': 'd358219f7da9c26bb85e0a211c88c6f7ae9e00eac2423eda763f6321418181dd',
    'arlington-descriptors': '92e09969447f8384d0a0c9b693f9fc8f17261e02582b93b6bccd361503224a95',
    'arlington-cmaps': '3382bf1a8b36dd513873a17b7d9c460384c1a9e34250af3b8465245a19b5a369'}
DECLARATION_RULES_SHA256 = '98e35f2d6c7201bd0fe1c86452ec5f9497f19e4ca58b9d19b7b8b1ca3bece8bd'
PDFCPU_NOTICE = '''***************************** Disclaimer ****************************
* PDF 2.0 features are supported on a need basis.                   *
* (See ISO 32000:2 6.3.2 Conformance of PDF processors)             *
* At the moment pdfcpu ships with basic PDF 2.0 support.            *
* Please let us know which feature you would like to see supported, *
* provide a sample PDF file and create an issue:                    *
* https://github.com/pdfcpu/pdfcpu/issues/new/choose                *
* Thank you for using pdfcpu <3                                     *
*********************************************************************'''
SEMANTIC_OBSERVER_SHA256 = '8971a28d5f2f230c861243a2b56ec90b415bd5f9add6487b05f8940d19c42cbc'
PYTHON_SHA256 = '1643dacd9feaedc58f3cc581e4d22577dfe25c09b10282936186ccf0f2e61118'
PROGRAM_OBSERVER_SHA256 = '653633a52a8ff973914af14495fc2a9fd9dc989ccec9da94064fcd8a2e430766'
PROGRAM_PROFILE_SHA256 = '0ea2838ccc0ce9b7920f822db6459fed8beba33a6709bbcc04c0be5e6715f9ce'
FONTTOOLS_SHA256 = '8bd0f759020e87bb5d323e6283914d9bf4ae35a7307dafb2cbd1e379e720ad37'


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def parse_properties(data):
    # Recorder Properties.store escapes values but never continues a physical line.
    def unescape(value):
        return re.sub(r'\\(u[0-9a-fA-F]{4}|.)', lambda match:
            chr(int(match[1][1:], 16)) if match[1].startswith('u') and len(match[1]) == 5
            else {'t': '\t', 'n': '\n', 'r': '\r', 'f': '\f'}.get(match[1], match[1]), value)
    values = {}
    for line in data.decode('utf-8').splitlines():
        if not line or line.startswith(('#', '!')):
            continue
        key, separator, value = line.partition('=')
        if not separator or key in values:
            raise ValueError('Invalid or duplicate T13 report property: ' + key)
        values[key] = unescape(value)
    return values


def require(values, expected, description):
    if any(values.get(key) != value for key, value in expected.items()):
        raise ValueError('Incomplete or inconsistent T13 ' + description)


def manifest_entries(directory, data):
    if len(data) > 16 * 1024 * 1024:
        raise ValueError('T13 retained manifest exceeds its bound')
    entries = {}
    for line in data.decode('utf-8').splitlines():
        match = re.fullmatch(r'([0-9a-f]{64})  (.+)', line)
        if not match:
            raise ValueError('Invalid T13 retained manifest entry')
        value, name = match.groups()
        relative = PurePosixPath(name)
        if relative.is_absolute() or '..' in relative.parts or relative.as_posix() != name \
                or directory / name in entries:
            raise ValueError('Invalid or duplicate T13 retained path')
        entries[directory / name] = value
    return entries


class Authorities:
    """Parse only the original authority bytes and recheck them before export."""

    def __init__(self):
        self.originals = {}

    def read(self, path):
        path = Path(os.path.abspath(path))
        if path not in self.originals:
            if path.resolve(strict=True) != path or path.stat().st_size > 16 * 1024 * 1024:
                raise ValueError('Linked or oversized T13 authority')
            data = path.read_bytes()
            if len(data) > 16 * 1024 * 1024:
                raise ValueError('T13 authority grew beyond its bound')
            self.originals[path] = (data, hashlib.sha256(data).hexdigest())
        return self.originals[path][0]

    def digest(self, path):
        return hashlib.sha256(self.read(path)).hexdigest()

    def properties(self, path):
        return parse_properties(self.read(path))

    def verify(self):
        for path, (_, expected) in self.originals.items():
            if path.resolve(strict=True) != path or digest(path) != expected:
                raise ValueError('T13 authority changed after original observation: ' + str(path))


class RetainedFiles:
    """An index of original producer hashes and verified reads for one collection."""

    def __init__(self, directory):
        self.directory = directory
        self.authorities = Authorities()
        record = directory / 'result.properties'
        result_bytes = record.read_bytes()
        self.result = parse_properties(result_bytes)
        manifest = directory / 'retained-files.sha256'
        data = manifest.read_bytes()
        if hashlib.sha256(data).hexdigest() != self.result.get('retained-files-sha256'):
            raise ValueError('T13 retained manifest identity mismatch')
        self.expected = manifest_entries(directory, data)
        if record in self.expected or manifest in self.expected:
            raise ValueError('T13 manifest cannot contain its own report or manifest identity')
        self.expected.update({record: hashlib.sha256(result_bytes).hexdigest(),
                              manifest: hashlib.sha256(data).hexdigest()})
        self.trees = {}
        for path in sorted(self.expected):
            for parent in path.parents:
                if not parent.is_relative_to(directory):
                    break
                self.trees.setdefault(parent, []).append(path)
        self.verify()

    def read(self, path):
        if path not in self.expected or path.resolve(strict=True) != path:
            raise ValueError('Unretained or linked T13 artifact: ' + str(path))
        data = path.read_bytes()
        if hashlib.sha256(data).hexdigest() != self.expected[path]:
            raise ValueError('T13 artifact changed after observation: ' + str(path))
        return data

    def properties(self, path):
        return parse_properties(self.read(path))

    def text(self, path):
        return self.read(path).decode('utf-8')

    def under(self, directory):
        return self.trees.get(directory, ())

    def reference(self, root, path):
        return {'path': path.relative_to(root).as_posix(), 'sha256': self.expected[path]}

    def producer_receipt(self, directory, transcript, prefix, names):
        if not transcript.startswith(prefix):
            raise ValueError('Incomplete T13 producer process observation: ' + str(directory))
        receipt = manifest_entries(directory, transcript[len(prefix):].strip().encode('utf-8'))
        if receipt != {directory / name: self.expected[directory / name] for name in names}:
            raise ValueError('Incomplete T13 original producer receipt: ' + str(directory))
        return receipt

    def manifest(self, directory, result, parent_owned=()):
        path = directory / 'retained-files.sha256'
        if self.expected[path] != result.get('retained-files-sha256'):
            raise ValueError('T13 child manifest identity mismatch')
        entries = manifest_entries(directory, self.read(path))
        excluded = {path, directory / 'result.properties'}
        excluded.update(directory / name for name in parent_owned)
        expected = {file: self.expected[file] for file in self.under(directory) if file not in excluded}
        if entries != expected:
            raise ValueError('Incomplete T13 original retained receipt: ' + str(directory))

    def verify(self):
        actual = set()
        for path in self.directory.rglob('*'):
            if path.is_symlink():
                raise ValueError('T13 evidence contains a symbolic link')
            if path.is_file():
                actual.add(path)
        if actual != set(self.expected):
            raise ValueError('Incomplete T13 original retained receipt')
        for path in self.expected:
            self.read(path)
        self.authorities.verify()


def qpdf_identity(root, authorities):
    paths = {'qpdf-pin-sha256': root / 'scripts/qpdf-pin.properties',
             'qpdf-wrapper-sha256': root / 'scripts/container-bin/qpdf',
             'qpdf-runtime-sha256': root / 'scripts/t13-qpdf-runtime.sha256'}
    expected = {
        'qpdf-pin-sha256': '62c63de3d888ba08b60df9e3c33c9ebefe4cc0c8ee747cc5269390017b57169c',
        'qpdf-wrapper-sha256': 'a12d5a4e48fd37e8aefa3b92b30f002c25d2de9f96944fb68efeb64f2b79431e',
        'qpdf-runtime-sha256': 'a06ee3eb9314e2fa3db4fb475d806f8249f82e0daea85528535c04362fa280ad'}
    if any(authorities.digest(path) != expected[key] for key, path in paths.items()):
        raise ValueError('T13 qpdf authority identity mismatch')
    pin = authorities.properties(paths['qpdf-pin-sha256'])
    expected.update({'qpdf-version': pin['QPDF_VERSION'],
                     'qpdf-binary-sha256': pin['QPDF_BINARY_SHA256']})
    expected.update({'qpdf-runtime.' + path.relative_to(root).as_posix(): value
                     for path, value in manifest_entries(root, authorities.read(paths['qpdf-runtime-sha256'])).items()})
    return expected, pin['QPDF_ARCHIVE_SHA256']


def markdown_fields(text, expected, description):
    for key, value in expected.items():
        if re.findall(r'^' + re.escape(key) + r': `([^`\r\n]*)`$', text, re.MULTILINE) != [value]:
            raise ValueError('Incomplete or inconsistent T13 ' + description + ': ' + key)


def syntax(root, directory, input_hash, files, verdict='pass'):
    identity, distribution = qpdf_identity(root, files.authorities)
    result = files.properties(directory / 'result.properties')
    require(result, dict(identity, **{'profile': PROFILE, 'syntax': verdict, 'qpdf-result': verdict,
        'input-sha256': input_hash, 'input-after-sha256': input_hash,
        'retained-after-sha256': input_hash}), 'syntax observation')
    files.manifest(directory, result)
    if files.expected[directory / 'extraction.pdf'] != input_hash:
        raise ValueError('T13 syntax input identity mismatch')
    common = {'Input exact SHA-256': input_hash, 'Final determination': verdict}
    markdown_fields(files.text(directory / 'qpdf-syntax.md'), dict(common, **{
        'Capability': 'document.text-structure.extract', 'Acceptance Profile': PROFILE,
        'Chain': 'syntax', 'Result': verdict, 'Producer kind': 'external-tool',
        'Producer': 'qpdf', 'Producer version': identity['qpdf-version'],
        'Tool distribution SHA-256': distribution}), 'qpdf record')
    raw = files.text(directory / 'qpdf-syntax.txt')
    markdown_fields(raw, dict(common, **{'Tool': 'qpdf', 'Tool version': identity['qpdf-version'],
        'Distribution SHA-256': distribution, 'Invocation': 'qpdf --check extraction.pdf',
        '`extraction.pdf` exit code': '0' if verdict == 'pass' else '2'}), 'qpdf process')
    errors = re.findall(r'### Standard error\n\n```text\n(.*?)\n```', raw, re.DOTALL)
    completed = (errors == [''] and
                 'No syntax or stream encoding errors found; the file may still contain\nerrors that qpdf cannot detect' in raw
                 if verdict == 'pass' else len(errors) == 1 and
                 'qpdf: extraction.pdf: unable to find trailer dictionary while recovering damaged file' in errors[0])
    if not completed:
        raise ValueError('Incomplete T13 qpdf syntax check')
    for name in ('syntax.md', 'syntax.txt'):
        markdown_fields(files.text(directory / name), common, 'final syntax observation')
    markdown_fields(files.text(directory / 'syntax.txt'), {
        'Raw qpdf result before final identity checks': verdict}, 'raw syntax observation')


def semantics(root, directory, input_hash, source_hash, mode, expected_values, files, verdict='pass'):
    if files.authorities.digest(root / 'scripts/t13-semantics.py') != SEMANTIC_OBSERVER_SHA256:
        raise ValueError('T13 semantic observer identity mismatch')
    identity, _ = qpdf_identity(root, files.authorities)
    expected = {key: identity[key] for key in ('qpdf-version', 'qpdf-binary-sha256',
                'qpdf-wrapper-sha256', 'qpdf-runtime-sha256')}
    expected.update({'profile': PROFILE, 'semantic': verdict,
        'semantic-scope': 'independent-qpdf-decoded-graph-preservation',
        'input-sha256': input_hash, 'reference-sha256': source_hash,
        'expectations-sha256': 'fb348a2b139df12b93633cbf9c999490c584030aebeacc9b95bff57c9982707f',
        'observer-sha256': SEMANTIC_OBSERVER_SHA256, 'python-version': '3.12.3',
        'python-executable-sha256': PYTHON_SHA256})
    child = directory / 'qpdf'
    command = files.text(directory / 'semantic-command.txt')
    prefix = 'observer: scripts/t13-semantics.py\ninput exact SHA-256: ' + input_hash + '\nexit-code: 0\n'
    allowed = ('qpdf-version.txt', 'qpdf-version-stderr.txt', 'reference-qpdf.json',
        'reference-qpdf-stderr.txt', 'qpdf.json', 'qpdf-stderr.txt', 'reference.json',
        'observed.json', 'result.properties', 'semantic.txt')
    files.producer_receipt(child, command, prefix, allowed)
    for name in ('qpdf-version-stderr.txt', 'reference-qpdf-stderr.txt', 'qpdf-stderr.txt'):
        if files.read(child / name):
            raise ValueError('T13 semantic qpdf process has findings')
    if not files.text(child / 'qpdf-version.txt').startswith('qpdf version 12.4.0\n'):
        raise ValueError('T13 semantic qpdf version mismatch')
    expected.update({'qpdf-json-sha256': files.expected[child / 'qpdf.json'],
                     'reference-qpdf-json-sha256': files.expected[child / 'reference-qpdf.json']})
    independent = files.properties(child / 'result.properties')
    require(independent, expected, 'independent semantic observation')
    original = json.loads(files.read(child / 'reference.json'))
    observed = json.loads(files.read(child / 'observed.json'))
    if not isinstance(original, dict) or not original.get('catalog') or not original.get('objects') \
            or not isinstance(observed, dict) or not observed.get('catalog') or not observed.get('objects') \
            or (original == observed) != (verdict == 'pass'):
        raise ValueError('T13 independently decoded graphs disagree with the required observation')
    result = files.properties(directory / 'result.properties')
    require(result, dict(independent, **{'execution-profile': mode if verdict == 'pass' else 'unavailable',
            'public-observation': 'pass' if verdict == 'pass' else 'indeterminate'}),
            'public semantic result')
    files.manifest(directory, result)
    if files.expected[directory / 'extraction.pdf'] != input_hash \
            or (verdict == 'pass' and files.properties(directory / 'observation.properties') != expected_values) \
            or (verdict == 'fail' and directory / 'observation.properties' in files.expected):
        raise ValueError('T13 semantic input or complete public values differ')


def programs(root, directory, input_hash, files):
    authority = root / 'capabilities/profiles/T13-standards'
    profile_path = authority / 'program-qualification.properties'
    if files.authorities.digest(profile_path) != PROGRAM_PROFILE_SHA256 \
            or files.authorities.digest(root / 'scripts/t13-program-standards.py') != PROGRAM_OBSERVER_SHA256:
        raise ValueError('T13 program qualification authority mismatch')
    profile = files.authorities.properties(profile_path)
    identity, _ = qpdf_identity(root, files.authorities)
    identity.update({'observer-sha256': PROGRAM_OBSERVER_SHA256,
        'qualification-profile-sha256': PROGRAM_PROFILE_SHA256,
        'fonttools-wheel-sha256': FONTTOOLS_SHA256, 'python-executable-sha256': PYTHON_SHA256})
    scopes = profile['scopes'].split(',')
    controls = profile['controls'].split(',')
    required = set(profile['required-rules'].split(','))
    result = files.properties(directory / 'result.properties')
    require(result, {'profile': PROFILE, 'standards-scope': 'programs-only',
        'program-standards': 'pass', 'input-sha256': input_hash, 'qualified-rule-count': '42',
        'negative-control-count': '165', 'covered-rules': ','.join(sorted(required))}, 'qualified programs')
    files.manifest(directory, result)
    if files.expected[directory / 'extraction.pdf'] != input_hash:
        raise ValueError('T13 program input identity mismatch')

    def observation(folder, scope, source_hash, verdict, rule=None):
        expected = {key: identity[key] for key in ('observer-sha256', 'python-executable-sha256',
                    'qpdf-version', 'qpdf-binary-sha256', 'qpdf-wrapper-sha256', 'qpdf-runtime-sha256')}
        expected.update({'profile': PROFILE, 'scope': scope, 'input-sha256': source_hash,
                         'standards': verdict, 'python-version': '3.12.3', 'python-optimization-level': '0'})
        if scope in ('fonts', 'font-metrics'):
            expected.update({'fonttools-version': '4.59.2', 'fonttools-wheel-sha256': FONTTOOLS_SHA256})
        if rule is not None:
            expected['rule'] = rule
        raw = files.text(folder / 'process.txt')
        allowed = ('qpdf-version.txt', 'qpdf-version.txt.stderr', 'qpdf.json', 'qpdf.json.stderr', 'result.properties')
        receipt = files.producer_receipt(folder, raw, 'exit=0\n', allowed)
        for name in ('qpdf-version.txt.stderr', 'qpdf.json.stderr'):
            if files.read(folder / name):
                raise ValueError('T13 program qpdf process has findings')
        if not files.text(folder / 'qpdf-version.txt').startswith('qpdf version 12.4.0\n'):
            raise ValueError('T13 program qpdf version mismatch')
        expected['qpdf-json-sha256'] = receipt[folder / 'qpdf.json']
        require(files.properties(folder / 'result.properties'), expected, 'program scope ' + scope)

    for scope in scopes:
        observation(directory / 'input' / scope, scope, input_hash, 'pass')
    qualified = set()
    findings = files.text(directory / 'programs.txt').splitlines()
    for control in controls:
        key = 'negative.' + control
        source_hash, scope, rule = (profile[key + '.' + field] for field in ('sha256', 'scope', 'rule'))
        if scope not in scopes or scope + '.' + rule not in required \
                or files.authorities.digest(authority / profile[key + '.path']) != source_hash \
                or files.expected[directory / ('control-' + control + '.pdf')] != source_hash:
            raise ValueError('Invalid T13 program control identity or rule: ' + control)
        identity[key + '.sha256'] = source_hash
        observation(directory / 'qualifications' / control, scope, source_hash, 'fail', rule)
        if ('Negative control ' + control + ' sha256=' + source_hash + ' detected=true rule=' + rule) not in findings:
            raise ValueError('Undetected T13 program qualification control: ' + control)
        qualified.add(scope + '.' + rule)
    if qualified != required:
        raise ValueError('Incomplete T13 program qualified rule union')
    require(result, identity, 'program authority identity')
    for phase in ('before', 'after'):
        if files.properties(directory / ('identities-' + phase + '.properties')) != identity:
            raise ValueError('T13 program authority identity mismatch: ' + phase)


def visual(root, directory, input_hash, product, definition, files, verdict='pass'):
    pin_path = root / 'scripts/pdfium-pin.properties'
    comparator_path = root / 'scripts/imagemagick-pin.properties'
    if files.authorities.digest(pin_path) != '0037480e2d77ae1a6d72566c43cf4c5fb9ccf9bb446b31b2c124e0e51c52ac0e' \
            or files.authorities.digest(comparator_path) != '0cbd171a2ac7bf35623c4a2cf83f0a3850b15efe3873cb7b40b4e765c5458374':
        raise ValueError('T13 visual tool authority identity mismatch')
    pin, comparator = files.authorities.properties(pin_path), files.authorities.properties(comparator_path)
    result = files.properties(directory / 'result.properties')
    require(result, {'profile': PROFILE, 'input-sha256': input_hash, 'visual': verdict,
                    'page-count': '1', 'page.1.visual': verdict}, 'visual observation')
    files.manifest(directory, result)
    if files.expected[directory / 'extraction.pdf'] != input_hash:
        raise ValueError('T13 visual input identity mismatch')
    expected_hash = definition['raster-sha256']
    if files.authorities.digest(root / 'capabilities/profiles/T13-text' / definition['raster']) != expected_hash \
            or files.expected[directory / 'page-1-expected.png'] != expected_hash:
        raise ValueError('T13 original visual expectation identity mismatch')
    hashes = {}
    for label, name in (('Expected', 'expected'), ('PDFium', 'pdfium'), ('Implementation', 'implementation'),
                        ('Difference', 'difference'), ('Renderer difference', 'renderer-difference')):
        path = directory / ('page-1-' + name + '.png')
        data = files.read(path)
        if len(data) > 4 * 1024 * 1024 or data[:16] != b'\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR' \
                or int.from_bytes(data[16:20], 'big') != definition['raster-width'] \
                or int.from_bytes(data[20:24], 'big') != definition['raster-height']:
            raise ValueError('Invalid or incorrectly sized T13 retained raster')
        hashes[label + ' raster SHA-256'] = files.expected[path]
    raw = files.text(directory / 'page-1-visual.txt')
    metrics = {}
    for key in ('Expected comparison AE', 'Renderer agreement AE'):
        values = re.findall(r'^' + re.escape(key) + r': `([0-9]+(?:\.[0-9]+)?)`$', raw, re.MULTILINE)
        if len(values) != 1:
            raise ValueError('Missing T13 visual comparison metric')
        metrics[key] = values[0]
    error, agreement = (Decimal(metrics[key]) for key in ('Expected comparison AE', 'Renderer agreement AE'))
    if (error == 0) != (verdict == 'pass') or agreement > definition['renderer-agreement-threshold']:
        raise ValueError('T13 visual differences exceed the frozen bounds')
    common = dict(metrics, **{'Input exact SHA-256': input_hash, 'Final determination': verdict,
        'Review required': 'false', 'PDFium CLI version': pin['PDFIUM_CLI_VERSION'],
        'PDFium engine version': pin['PDFIUM_ENGINE_VERSION'], 'ImageMagick version': comparator['IMAGEMAGICK_VERSION'],
        'Implementation renderer version': '3.0.8'})
    record = dict(common, **{key: value for key, value in hashes.items()
                             if key in ('Expected raster SHA-256', 'PDFium raster SHA-256', 'Implementation raster SHA-256')})
    record.update({'Capability': 'document.text-structure.extract', 'Acceptance Profile': PROFILE,
        'Chain': 'visual', 'Result': verdict, 'Producer kind': 'external-tool', 'Producer': 'pdfium-cli',
        'Producer version': 'v0.11.2-pdfium-chromium-7881',
        'PDFium engine distribution SHA-256': pin['PDFIUM_ENGINE_ARCHIVE_SHA256'],
        'PDFium distribution SHA-256': pin['PDFIUM_ARCHIVE_SHA256'],
        'PDFium executable SHA-256': pin['PDFIUM_EXECUTABLE_SHA256'],
        'ImageMagick distribution SHA-256': comparator['IMAGEMAGICK_ARCHIVE_SHA256'],
        'ImageMagick executable SHA-256': comparator['IMAGEMAGICK_EXECUTABLE_SHA256']})
    markdown_fields(files.text(directory / 'page-1-visual.md'), record, 'visual tool record')
    embedded = product == 'embedded-font-kinds'
    expected = dict(common, **hashes)
    expected.update({'Profile': PROFILE + '-' + product + '-page-1',
        'Page box': 'effective CropBox [0 0 120 100] points', 'DPI': '144',
        'Color policy': 'sRGB, opaque 8-bit RGB PNG after compositing over opaque white',
        'Font policy': ('original embedded Type1C, CID CFF and TrueType rectangle glyphs; no system fonts or substitution'
                        if embedded else 'original embedded Type3 rectangle glyph; no system fonts or substitution'),
        'Antialiasing policy': ('pinned PDFium default font smoothing; independent original PDF reference'
                               if embedded else 'pinned PDFium default smoothing; vector edges are axis-aligned'),
        'Raster dimensions': str(definition['raster-width']) + 'x' + str(definition['raster-height']),
        'Comparison metric': 'AE', 'Comparison fuzz percent': '0', 'Comparison threshold': '0',
        'Renderer agreement threshold': str(definition['renderer-agreement-threshold'])})
    markdown_fields(raw, expected, 'visual raster observation')
    comparison = 'magick compare -metric AE -fuzz 0% -highlight-color #ff0000 -lowlight-color #ffffff -define png:exclude-chunk=time,date '
    invocations = ['pdfium --version',
        'pdfium render extraction.pdf page-1-pdfium.png --dpi 144 --file-type png --pages first',
        'magick --version', comparison + 'page-1-expected.png page-1-pdfium.png page-1-difference.png',
        comparison + 'page-1-pdfium.png page-1-implementation.png page-1-renderer-difference.png']
    codes = re.findall(r'^Exit code: `([^`\r\n]+)`$', raw, re.MULTILINE)
    cutoff = Decimal(definition['raster-width'] * definition['raster-height']) / 1000000
    if re.findall(r'^Invocation: `([^`\r\n]+)`$', raw, re.MULTILINE) != invocations \
            or len(codes) != 5 or codes[:3] != ['0', '0', '0'] \
            or any(code not in ('0', '1') or (code == '0' and metric > cutoff) or (code == '1' and metric == 0)
                   for code, metric in zip(codes[3:], (error, agreement))):
        raise ValueError('Incomplete T13 visual process observations')
    errors = re.findall(r'### Standard error\n\n```text\n(.*?)\n```', raw, re.DOTALL)
    if len(errors) != 5 or errors[:3] != ['', '', ''] \
            or any(not re.fullmatch(re.escape(str(count)) + r'(?: \([0-9.eE+-]+\))?', value)
                   for count, value in zip((error, agreement), errors[3:])):
        raise ValueError('T13 visual process findings disagree with comparison metrics')
    outputs = re.findall(r'### Standard output\n\n```text\n(.*?)\n```', raw, re.DOTALL)
    if len(outputs) != 5 or outputs[0].strip() != 'pdfium version ' + pin['PDFIUM_CLI_VERSION'] \
            or outputs[1].strip() != 'Rendered page 1 into page-1-pdfium.png' \
            or not outputs[2].startswith('Version: ImageMagick ' + comparator['IMAGEMAGICK_VERSION'] + ' ') \
            or outputs[3:] != ['', '']:
        raise ValueError('T13 visual version or output summaries contradict original process stdout')
    pixels = []
    primary_bound = 0 if verdict == 'pass' else definition['raster-width'] * definition['raster-height']
    for key, bound in (('expected to PDFium', primary_bound), ('PDFium to secondary', definition['renderer-agreement-threshold'])):
        values = re.findall(r'^Exact changed RGB pixels, ' + key + r': ([0-9]+)$', raw, re.MULTILINE)
        if len(values) != 1 or int(values[0]) > bound:
            raise ValueError('T13 exact raster differences exceed the frozen pixel bounds')
        pixels.append(values[0])
    if (int(pixels[0]) == 0) != (verdict == 'pass'):
        raise ValueError('T13 primary changed pixels do not establish the required visual result')
    if embedded and verdict == 'pass':
        require(files.properties(directory / 'font-raster-agreement.properties'), {
            'profile': PROFILE + '-embedded-font-kinds-page-1', 'raster-agreement': 'pass',
            'primary-sha256': hashes['PDFium raster SHA-256'], 'secondary-sha256': hashes['Implementation raster SHA-256'],
            'outside-edge-pixels': '0', 'changed-pixels': pixels[1]}, 'font edge observation')


def declarations(root, directory, input_hash, files, verdict='pass'):
    authority = root / 'capabilities/profiles/T13-standards'
    catalog = authority / 'required-rules.txt'
    if files.authorities.digest(catalog) != DECLARATION_RULES_SHA256:
        raise ValueError('T13 declaration rule authority changed')
    expected_rules = set(files.authorities.read(catalog).decode('utf-8').splitlines())
    result = files.properties(directory / 'result.properties')
    require(result, {'profile': PROFILE, 'input-sha256': input_hash,
        'standards-scope': 'declarations-only', 'declarations': verdict,
        'qualified-rule-count': '334' if verdict == 'pass' else '0',
        'covered-rules': ','.join(sorted(expected_rules)) if verdict == 'pass' else '',
        'required-rules-sha256': DECLARATION_RULES_SHA256}, 'qualified declarations')
    files.manifest(directory, result)
    if files.expected[directory / 'extraction.pdf'] != input_hash:
        raise ValueError('T13 declaration input identity mismatch')
    identities = {'required-rules-sha256': DECLARATION_RULES_SHA256}
    observed_rules = set()
    for group, profile_hash in DECLARATION_PROFILES.items():
        profile_path = authority / (group + '.properties')
        if files.authorities.digest(profile_path) != profile_hash:
            raise ValueError('T13 declaration profile changed: ' + group)
        profile = files.authorities.properties(profile_path)
        rules = profile['required-rules'].split(',')
        if observed_rules.intersection(rules):
            raise ValueError('Duplicate T13 declaration rule assignment')
        observed_rules.update(rules)
        pin_name = 't13-arlington' if group.startswith('arlington') else 'pdfcpu'
        pin_path = root / 'scripts' / (pin_name + '-pin.properties')
        expected_pin = ('e00f7b42bd5712f45b346bba68061fc89ac64888bc35c6b4a32f043784900c8b'
                        if pin_name == 't13-arlington' else
                        '21c58822196a4123561cac927e1f8ba487e6581ba8f488d5b7f40ba949429fc8')
        if files.authorities.digest(pin_path) != expected_pin:
            raise ValueError('T13 declaration tool pin changed')
        pin = files.authorities.properties(pin_path)
        identities.update({'profile.' + group + '.sha256': profile_hash,
                           pin_name + '.pin-sha256': expected_pin,
                           pin_name + '.executable-sha256': pin['sha256']})
        if pin_name == 't13-arlington':
            for kind in ('model', 'patch'):
                identities[pin_name + '.' + kind + '-sha256'] = pin[kind + '-sha256']
        observed = files.properties(directory / group / 'standards.properties')
        require(observed, {'result': verdict, 'profile': PROFILE, 'input-sha256': input_hash,
            'covered-rules': ','.join(rules) if verdict == 'pass' else '', 'pin-sha256': expected_pin,
            'profile-sha256': profile_hash, 'executable-sha256': pin['sha256'],
            'model-sha256': pin.get('model-sha256', 'none'),
            'patch-sha256': pin.get('patch-sha256', 'none')}, 'checker ' + group)
        raw_findings = files.text(directory / group / 'findings.txt')
        version, separator, validation = raw_findings.partition('\nValidation exit: ')
        exit_code = '1' if verdict == 'fail' and group == 'pdfcpu' else '0'
        if not version.startswith('Version exit: 0\n') or pin['version'] not in version \
                or not separator or not validation.startswith(exit_code + '\n'):
            raise ValueError('Incomplete T13 declaration process observation: ' + group)
        if verdict == 'fail':
            detected = ('validation error' in validation and
                        'document catalog: dict=rootDict entry=Type invalid dict entry: Bogus' in validation
                        if group == 'pdfcpu' else ' BEGIN - TestGrammar ' in validation and '\nEND\n' in validation
                        and validation.strip().endswith('DONE - 1 files processed') and
                        '\nError: wrong value for possible values: Type (Catalog) should be: name [Catalog] in PDF 2.0 and is name==Bogus' in validation)
            if not detected:
                raise ValueError('T13 invalid Catalog control was not detected: ' + group)
            continue
        positive = validation[2:].partition('\nNegative control ')[0].strip()
        if group == 'pdfcpu':
            completed = re.fullmatch(r'validating\(mode=strict\) [^\r\n]+ \.\.\.\n\n'
                                     + re.escape(PDFCPU_NOTICE) + r'\nvalidation ok', positive) is not None
        else:
            completed = (' BEGIN - TestGrammar ' in positive and '\nEND\n' in positive
                         and positive.endswith('DONE - 1 files processed')
                         and 'Error:' not in positive and 'Warning:' not in positive)
        if not completed:
            raise ValueError('Incomplete T13 positive declaration validation: ' + group)
        findings = raw_findings.splitlines()
        for rule in rules:
            key = 'negative.' + rule
            control = directory / group / ('control-' + rule + '.pdf')
            raw = directory / group / ('negative-' + rule + '.txt')
            negative = files.text(raw)
            detected = (negative.startswith('exit=1\n') and
                        ('validation error:' in negative or 'validation error (obj#:' in negative)
                        if group == 'pdfcpu' else
                        negative.startswith('exit=0\n') and '\nError: ' in negative)
            expected = profile[key + '.sha256']
            if files.expected[control] != expected or files.authorities.digest(authority / profile[key + '.path']) != expected \
                    or ('Negative control ' + rule + ' sha256=' + expected + ' detected=true') not in findings \
                    or not detected or profile[key + '.finding'] not in negative:
                raise ValueError('Missing qualified T13 declaration control: ' + rule)
    if observed_rules != expected_rules:
        raise ValueError('Incomplete T13 declaration rule union')
    for phase in ('before', 'after'):
        path = directory / ('identities-' + phase + '.properties')
        if files.properties(path) != identities or files.expected[path] != result.get('identities-' + phase + '-sha256'):
            raise ValueError('T13 declaration authority identity mismatch: ' + phase)


def changed_source_hash(root, corpus, product, before, after, files):
    source = corpus['sources'][product]
    data = files.authorities.read(root / 'capabilities/profiles/T13-text' / source['path'])
    before, after = before.encode('ascii'), after.encode('ascii')
    if hashlib.sha256(data).hexdigest() != source['sha256'] or len(before) != len(after) or data.count(before) != 1:
        raise ValueError('T13 negative control does not identify one unchanged original Source')
    return hashlib.sha256(data.replace(before, after)).hexdigest()


def semantic_controls(root, directory, corpus, expected_values, execution, files):
    result = files.properties(directory / 'result.properties')
    require(result, {'profile': PROFILE, 'native-execution-profile': execution,
                    'semantic': 'fail', 'positive': 'pass', 'control-count': '12'}, 'semantic control batch')
    files.manifest(directory, result)
    for product in PRODUCTS:
        require(result, {'positive.' + product: 'pass'}, 'positive semantic control')
        prefix = 'products.' + product + '.extraction.'
        expected = {key[len(prefix):]: value for key, value in expected_values.items() if key.startswith(prefix)}
        source_hash = corpus['sources'][product]['sha256']
        semantics(root, directory / ('positive-' + product), source_hash, source_hash, execution, expected, files)
    controls = (
        ('contents-order', 'nested-split-type3', '/Contents [4 0 R 5 0 R]', '/Contents [5 0 R 4 0 R]'),
        ('form-position', 'nested-split-type3', '/Matrix [2 0 0 1 5 10]', '/Matrix [2 0 0 1 6 10]'),
        ('font-width', 'nested-split-type3', '/Widths [500]', '/Widths [600]'),
        ('replacement', 'marked-structure', '/ActualText (Outer)', '/ActualText (Other)'),
        ('alternate', 'marked-structure', '/Alt (PageAlt)', '/Alt (PageBad)'),
        ('language', 'marked-structure', '/Lang (fr)', '/Lang (de)'),
        ('mcr', 'marked-structure', '/Stm 7 0 R /MCID 0', '/Stm 7 0 R /MCID 1'),
        ('parent-tree', 'marked-structure', '/Nums [0 [10 0 R] 1 [10 0 R] 2 10 0 R]', '/Nums [0 [11 0 R] 1 [10 0 R] 2 10 0 R]'),
        ('object-reference', 'marked-structure', '/Obj 12 0 R', '/Obj 11 0 R'),
        ('role-target', 'marked-structure', '/Intermediate [/Document 15 0 R]', '/Intermediate [/Document 14 0 R]'),
        ('empty-replacement', 'marked-structure', '/ActualText ()', '              '),
        ('namespace-reference', 'marked-structure', '/S /Span /NS 15 0 R', '/S /Span /NS 14 0 R'))
    for name, product, before, after in controls:
        require(result, {name: 'fail'}, 'negative semantic control')
        source_hash = corpus['sources'][product]['sha256']
        semantics(root, directory / name, changed_source_hash(root, corpus, product, before, after, files),
                  source_hash, execution, {}, files, 'fail')


def raster_pixels(data):
    """Decode only the bounded RGB8 control format, independent of PNG encoding."""
    width, height, stride = 240, 200, 720
    if len(data) > 4 * 1024 * 1024 or data[:16] != b'\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR' \
            or data[16:29] != width.to_bytes(4, 'big') + height.to_bytes(4, 'big') + b'\x08\x02\x00\x00\x00':
        raise ValueError('Invalid T13 RGB8 raster control format')
    position, last_kind, compressed = 8, None, bytearray()
    while position < len(data):
        length = int.from_bytes(data[position:position + 4], 'big')
        end = position + length + 12
        kind = data[position + 4:position + 8]
        payload = data[position + 8:end - 4]
        if end > len(data) or not re.fullmatch(b'[A-Za-z]{4}', kind) \
                or zlib.crc32(kind + payload) != int.from_bytes(data[end - 4:end], 'big'):
            raise ValueError('Invalid T13 raster control PNG chunk')
        # The frozen opaque controls have only these three chunk kinds.
        # Transparency or color-interpretation metadata is not qualified.
        if kind not in (b'IHDR', b'IDAT', b'IEND') or kind == b'IHDR' and position != 8:
            raise ValueError('Unsupported T13 raster control PNG chunk')
        if kind == b'IDAT':
            compressed.extend(payload)
        last_kind = kind
        position = end
        if kind == b'IEND':
            if length or position != len(data):
                raise ValueError('Invalid T13 raster control PNG end')
            break
    if last_kind != b'IEND' or not compressed:
        raise ValueError('Incomplete T13 raster control PNG')
    inflater = zlib.decompressobj()
    expected_size = height * (stride + 1)
    decoded = inflater.decompress(compressed, expected_size + 1)
    if len(decoded) != expected_size or not inflater.eof or inflater.unused_data or inflater.unconsumed_tail:
        raise ValueError('Invalid T13 raster control decompressed size')
    pixels, previous = bytearray(), bytearray(stride)
    for row_number in range(height):
        offset = row_number * (stride + 1)
        filtering = decoded[offset]
        if filtering > 4:
            raise ValueError('Invalid T13 raster control PNG filter')
        row = bytearray(decoded[offset + 1:offset + stride + 1])
        for column in range(stride):
            left = row[column - 3] if column >= 3 else 0
            above, corner = previous[column], previous[column - 3] if column >= 3 else 0
            predictor = 0
            if filtering == 1:
                predictor = left
            elif filtering == 2:
                predictor = above
            elif filtering == 3:
                predictor = (left + above) // 2
            elif filtering == 4:
                estimate = left + above - corner
                predictor = min((left, above, corner), key=lambda value: abs(estimate - value))
            row[column] = (row[column] + predictor) & 255
        pixels.extend(row)
        previous = row
    return pixels


def visual_controls(root, directory, corpus, files):
    result = files.properties(directory / 'result.properties')
    require(result, {'profile': PROFILE, 'visual': 'fail', 'positive': 'pass',
        'positive.embedded-font-kinds': 'pass', 'control-count': '4', 'raster-control-count': '3',
        'source-sha256': corpus['sources']['nested-split-type3']['sha256']}, 'visual control batch')
    files.manifest(directory, result)
    for name, product in (('positive', 'nested-split-type3'), ('positive-embedded-font-kinds', 'embedded-font-kinds')):
        visual(root, directory / name, corpus['sources'][product]['sha256'], product,
               corpus['products'][product]['visual'][0], files)
    controls = (
        ('glyph-ink', 'nested-split-type3', '0 0 400 600 re f', '0 0 300 600 re f'),
        ('form-position', 'nested-split-type3', '/Matrix [2 0 0 1 5 10]', '/Matrix [2 0 0 1 6 10]'),
        ('simple-font-position', 'embedded-font-kinds', '1 0 0 1 10 20 Tm', '1 0 0 1 11 20 Tm'),
        ('vertical-font-position', 'embedded-font-kinds', '1 0 0 1 40 80 Tm', '1 0 0 1 41 80 Tm'))
    for name, product, before, after in controls:
        require(result, {name: 'fail'}, 'negative visual control')
        visual(root, directory / name, changed_source_hash(root, corpus, product, before, after, files),
               product, corpus['products'][product]['visual'][0], files, 'fail')
    primary = directory / 'raster/original.png'
    expected = corpus['products']['embedded-font-kinds']['visual'][0]['raster-sha256']
    if files.expected[primary] != expected:
        raise ValueError('T13 raster control primary differs from the frozen original')
    require(result, {'raster.primary-sha256': expected}, 'raster control authority')
    original = raster_pixels(files.read(primary))
    for name, changed, outside in (('edge-only', 1, 0), ('missing-glyph', 432, 308), ('interior-hole', 1, 1), ('outside-ink', 1, 1)):
        folder = directory / 'raster' / name
        secondary = folder / 'secondary.png'
        verdict = 'pass' if name == 'edge-only' else 'fail'
        require(result, {'raster.' + name: verdict}, 'secondary raster control')
        require(files.properties(folder / 'result.properties'), {
            'profile': PROFILE + '-embedded-font-kinds-page-1', 'raster-agreement': verdict,
            'primary-sha256': expected, 'secondary-sha256': files.expected[secondary],
            'changed-pixels': str(changed), 'outside-edge-pixels': str(outside)}, 'original raster control ' + name)
        altered = original.copy()
        coordinates = {'edge-only': ((20, 136),), 'interior-hole': ((23, 139),),
                       'outside-ink': ((1, 1),),
                       'missing-glyph': ((x, y) for y in range(135, 161) for x in range(19, 37))}[name]
        for x, y in coordinates:
            offset = (y * 240 + x) * 3
            altered[offset:offset + 3] = b'\x00\x00\x00' if name == 'outside-ink' else b'\xff\xff\xff'
        if raster_pixels(files.read(secondary)) != altered:
            raise ValueError('T13 raster control pixels do not contain the specified defect: ' + name)


def collect_reports(root, run, execution):
    files = RetainedFiles(run)
    result = files.result
    require(result, dict(dict.fromkeys(CHAINS, 'pass'), **{
        'profile': PROFILE, 'phase': 'certification', 'native-execution-profile': execution,
        'facade-execution-profile': 'IN_PROCESS'}), 'whole-corpus result')
    authority = root / 'capabilities/profiles/T13-text'
    if files.authorities.digest(authority / 'corpus.json') != 'fb348a2b139df12b93633cbf9c999490c584030aebeacc9b95bff57c9982707f' \
            or files.authorities.digest(authority / 'corpus.properties') != '2b6ebdd09f9ee86bae7c60e3916f82feb8ba413aab6b4d1465c53823646b79d1':
        raise ValueError('T13 frozen corpus identity mismatch')
    corpus = json.loads(files.authorities.read(authority / 'corpus.json'))
    expected_values = files.authorities.properties(authority / 'corpus.properties')
    for source in corpus['sources'].values():
        if files.authorities.digest(authority / source['path']) != source['sha256']:
            raise ValueError('T13 frozen Source identity mismatch')
    require(files.properties(run / 'products.properties'), {'phase': 'products-only',
        'native-execution-profile': execution, 'facade-execution-profile': 'IN_PROCESS'}, 'product generation')

    def reference(path):
        return files.reference(root, path)

    negative = files.properties(run / 'negative/result.properties')
    require(negative, dict(dict.fromkeys(CHAINS, 'fail'), **{
        'profile': PROFILE, 'native-execution-profile': execution}), 'negative controls')
    files.manifest(run / 'negative', negative)
    syntax(root, run / 'negative/syntax', hashlib.sha256(b'%PDF-2.0\n1 0 obj\n').hexdigest(), files, 'fail')
    declarations(root, run / 'negative/standards', changed_source_hash(root, corpus, 'marked-structure',
                 '/Type /Catalog', '/Type /Bogus  ', files), files, 'fail')
    semantic_controls(root, run / 'negative/semantic', corpus, expected_values, execution, files)
    visual_controls(root, run / 'negative/visual', corpus, files)
    reports = {chain: {'chain': chain, 'result': 'pass', 'products': [],
                       'findings': [], 'negative-controls': []} for chain in CHAINS}
    for api in ('native', 'facade'):
        for index, product in enumerate(PRODUCTS):
            directory = run / (api + '-' + product)
            observed = files.properties(directory / 'result.properties')
            mode = execution if api == 'native' else 'IN_PROCESS'
            required = CHAINS if index < 3 else CHAINS[:3]
            pdf = directory / 'extraction.pdf'
            require(observed, {'profile': PROFILE, 'product': product,
                'execution-profile': mode, 'input-sha256': files.expected[pdf],
                'required-chains': ','.join(sorted(required)),
                'qualified-standard-rule-count': '376'}, 'product ' + directory.name)
            files.manifest(directory, observed, (
                'observation.properties', 'reopened.properties', 'publication.properties'))
            syntax(root, directory / 'syntax', files.expected[pdf], files)
            declarations(root, directory / 'declarations', files.expected[pdf], files)
            programs(root, directory / 'programs', files.expected[pdf], files)
            if 'visual' in required:
                visual(root, directory / 'visual', files.expected[pdf], product,
                       corpus['products'][product]['visual'][0], files)
            require(files.properties(directory / 'publication.properties'), {
                'execution-profile': mode, 'target-name': 'target', 'status': 'COMMITTED',
                'partial-output-possible': 'false', 'source-preserved': 'pass', 'reopened': 'pass',
                'source-sha256': corpus['sources'][product]['sha256'],
                'output-sha256': files.expected[pdf]}, directory.name + ' Publication Receipt')
            prefix = 'products.' + product + '.extraction.'
            expected = {key[len(prefix):]: value for key, value in expected_values.items()
                        if key.startswith(prefix)}
            semantics(root, directory / 'semantic', files.expected[pdf], corpus['sources'][product]['sha256'],
                      mode, expected, files)
            for name in ('observation.properties', 'reopened.properties'):
                if files.properties(directory / name) != expected:
                    raise ValueError('T13 detached public values disagree with the frozen corpus: '
                                     + directory.name + '/' + name)
            for chain in CHAINS:
                verdict = 'pass' if chain in required else 'not-required'
                require(observed, {chain: verdict}, directory.name + ' ' + chain)
                require(result, {api + '.' + product + '.' + chain: verdict}, 'aggregate ' + chain)
                if chain not in required:
                    continue
                report = reports[chain]
                report['products'].append(reference(pdf))
                folders = ('declarations', 'programs') if chain == 'standards' else (chain,)
                report['findings'].extend(reference(path) for folder in folders
                                          for path in files.under(directory / folder))
                if chain == 'standards':
                    report['negative-controls'].extend(reference(path) for path in files.under(directory / 'declarations')
                        if path.name.startswith(('negative-', 'control-')))
                    report['negative-controls'].extend(reference(path) for path in files.under(directory / 'programs')
                        if path.name.startswith('control-') or (directory / 'programs/qualifications') in path.parents)
                report['findings'].extend(reference(directory / name)
                                          for name in ('result.properties', 'retained-files.sha256'))
                if chain == 'semantic':
                    report['findings'].extend(reference(directory / name) for name in (
                        'publication.properties', 'observation.properties', 'reopened.properties'))
    for chain, report in reports.items():
        report['negative-controls'].extend(reference(path) for path in files.under(run / 'negative' / chain))
        report['negative-controls'].extend(reference(run / 'negative' / name)
                                          for name in ('result.properties', 'retained-files.sha256'))
        report['findings'].extend(reference(run / name)
                                 for name in ('result.properties', 'retained-files.sha256', 'products.properties'))
    files.verify()
    return reports
