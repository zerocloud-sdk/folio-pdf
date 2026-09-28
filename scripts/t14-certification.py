#!/usr/bin/env python3
"""Record separate T14 independent chains against actual public API products."""
import importlib.util
import json
import os
from pathlib import Path
import re
import selectors
import shutil
import subprocess
import sys
import time

SPEC = importlib.util.spec_from_file_location('t14_observer', Path(__file__).with_name('t14-observer.py'))
OBSERVER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(OBSERVER)
PROFILE = OBSERVER.PROFILE
CHAINS = ('syntax', 'standards', 'semantic', 'visual')
MAX_BYTES = 16 * 1024 * 1024
PYTHON_SHA256 = '1643dacd9feaedc58f3cc581e4d22577dfe25c09b10282936186ccf0f2e61118'


def digest(path):
    return OBSERVER.digest(path.read_bytes())


def properties(path):
    def unescape(value):
        return re.sub(r'\\(u[0-9a-fA-F]{4}|.)', lambda match:
                      chr(int(match[1][1:], 16)) if match[1].startswith('u') and len(match[1]) == 5
                      else {'t': '\t', 'n': '\n', 'r': '\r', 'f': '\f'}.get(match[1], match[1]), value)
    result = {}
    for line in path.read_text().splitlines():
        if line and not line.startswith(('#', '!')):
            key, separator, value = line.partition('=')
            if not separator or key in result:
                raise ValueError('Invalid or duplicate report property')
            result[key] = unescape(value)
    return result


def write_json(path, value):
    with path.open('x') as stream:
        json.dump(value, stream, indent=2, sort_keys=True)
        stream.write('\n')


def require(condition, message):
    if not condition:
        raise ValueError(message)


class Recorder:
    def __init__(self, root, output):
        self.root, self.output = root, output
        self.inputs = {}
        self.qpdf = root / 'scripts/container-bin/qpdf'
        self.pdfcpu = root / '.build-cache/pdfcpu/0.15.0/pdfcpu'
        self.pdfium = root / 'scripts/container-bin/pdfium'
        self.magick = root / 'scripts/container-bin/imagemagick'
        self.identities = self.identity()
        write_json(output / 'identities-before.json', self.identities)

    def read(self, path):
        require(path.resolve() == path and path.is_file() and path.stat().st_size <= MAX_BYTES,
                'Linked, missing or oversized acceptance input: ' + str(path))
        data = path.read_bytes()
        require(len(data) <= MAX_BYTES, 'Acceptance input grew beyond its byte bound')
        identity = OBSERVER.digest(data)
        require(path not in self.inputs or self.inputs[path] == identity, 'Acceptance input changed: ' + str(path))
        self.inputs[path] = identity
        return data

    def identity(self):
        pin = properties(self.root / 'scripts/t14-evidence-pin.properties')
        identities = {}
        for name, expected in pin.items():
            path = self.root / name
            require(path.is_file() and digest(path) == expected, 'T14 pinned identity mismatch: ' + name)
            identities[name] = expected
        require(digest(Path(sys.executable).resolve()) == PYTHON_SHA256, 'T14 Python executable identity mismatch')
        identities['python-executable-sha256'] = PYTHON_SHA256
        for manifest, directory in [('scripts/t13-qpdf-runtime.sha256', '.build-cache/qpdf/12.4.0'),
                                    ('scripts/imagemagick-runtime.sha256', '.build-cache/imagemagick/7.1.2-30/runtime')]:
            for line in (self.root / manifest).read_text().splitlines():
                expected, relative = line.split('  ', 1)
                path = self.root / directory / relative
                require(digest(path) == expected, 'Independent tool runtime mismatch: ' + str(path))
                identities[path.relative_to(self.root).as_posix()] = expected
        for name in ('LD_PRELOAD', 'LD_AUDIT', 'QPDF_CACHE_DIRECTORY', 'PDFIUM_CACHE_DIRECTORY',
                     'IMAGEMAGICK_CACHE_DIRECTORY', 'IMAGEMAGICK_RUNTIME_DIRECTORY', 'SHA256_COMMAND'):
            require(not os.environ.get(name), 'Certification forbids tool/loader override: ' + name)
        return identities

    def execute(self, command, directory, stem, timeout=30):
        directory.mkdir(parents=True, exist_ok=True)
        command = [str(item) for item in command]
        start = time.monotonic()
        captured = {1: bytearray(), 2: bytearray()}
        limited = None
        process = subprocess.Popen(command, cwd=self.root, stdin=subprocess.DEVNULL,
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        try:
            with selectors.DefaultSelector() as selector:
                selector.register(process.stdout, selectors.EVENT_READ, 1)
                selector.register(process.stderr, selectors.EVENT_READ, 2)
                while selector.get_map():
                    if time.monotonic() - start > timeout:
                        limited = 'time limit'
                        break
                    for key, _ in selector.select(.1):
                        block = os.read(key.fileobj.fileno(), 65536)
                        if not block:
                            selector.unregister(key.fileobj)
                            continue
                        if sum(len(value) for value in captured.values()) + len(block) > MAX_BYTES:
                            limited = 'output byte limit'
                            break
                        captured[key.data].extend(block)
                    if limited:
                        break
            if limited:
                process.kill()
            code = process.wait(timeout=5)
        finally:
            if process.poll() is None:
                process.kill()
                process.wait(timeout=5)
            process.stdout.close()
            process.stderr.close()
        out, err = bytes(captured[1]), bytes(captured[2])
        (directory / (stem + '.stdout')).write_bytes(out)
        (directory / (stem + '.stderr')).write_bytes(err)
        write_json(directory / (stem + '.command.json'), {'command': command, 'exit-code': code,
                   'stdout-sha256': OBSERVER.digest(out), 'stderr-sha256': OBSERVER.digest(err),
                   'limit': limited, 'timeout-seconds': timeout, 'maximum-output-bytes': MAX_BYTES})
        require(limited is None, 'Independent tool exceeded ' + str(limited))
        return code, out, err

    def graph(self, pdf, directory, stem, decode='none'):
        before = self.read(pdf)
        code, out, err = self.execute([self.qpdf, '--json=2', '--json-stream-data=inline',
                                       '--decode-level=' + decode, pdf], directory, stem)
        require(code == 0 and not err.strip(), 'qpdf graph observation failed')
        require(before == self.read(pdf), 'qpdf input changed during observation')
        return OBSERVER.Graph(json.loads(out, parse_float=OBSERVER.Decimal))

    def syntax(self, pdf, directory):
        identity = OBSERVER.digest(self.read(pdf))
        code, out, err = self.execute([self.qpdf, '--check', pdf], directory, 'qpdf')
        require(identity == OBSERVER.digest(self.read(pdf)), 'Syntax input changed')
        verdict = 'pass' if code == 0 and not err.strip() and b'No syntax or stream encoding errors found' in out else 'fail'
        write_json(directory / 'result.json', {'chain': 'syntax', 'result': verdict, 'input-sha256': identity})
        return verdict

    def standards(self, pdf, directory):
        identity = OBSERVER.digest(self.read(pdf))
        raw = self.graph(pdf, directory, 'raw')
        try:
            OBSERVER.standards(raw)
            decoded = self.graph(pdf, directory, 'decoded', 'all')
            checked = OBSERVER.standards(raw, decoded)
            result = {'result': 'pass', 'checked-rules': checked, 'rule': '', 'finding': 'Closed image declaration predicates passed'}
        except OBSERVER.Invalid as failure:
            result = {'result': 'fail', 'checked-rules': [], 'rule': failure.rule, 'finding': str(failure)}
        result.update(chain='standards', **{'input-sha256': identity})
        write_json(directory / 'result.json', result)
        return result

    def strict_pdfcpu(self, pdf, directory):
        identity = OBSERVER.digest(self.read(pdf))
        code, out, err = self.execute([self.pdfcpu, 'validate', '--mode', 'strict', '--conf', 'disable', '--offline', pdf],
                                      directory, 'pdfcpu', timeout=10)
        verdict = 'pass' if code == 0 and b'validation ok' in out + err else 'fail'
        write_json(directory / 'result.json', {'chain': 'standards', 'result': verdict, 'input-sha256': identity})
        return verdict, (out + err).decode('utf-8')

    def semantic(self, pdf, source, access, expected, directory, public_values):
        raw_source = self.graph(source, directory, 'source')
        raw = self.graph(pdf, directory, 'raw')
        decoded = self.graph(pdf, directory, 'decoded', 'all') if access != 'ENCODED' else raw
        observed = OBSERVER.flat(OBSERVER.inventory(raw, decoded, access))
        canonical_source, canonical = raw_source.canonical(), raw.canonical()
        differences = sorted(key for key in expected.keys() | observed.keys() if expected.get(key) != observed.get(key))
        public_differences = sorted(key for key in expected.keys() | public_values.keys()
                                    if expected.get(key) != public_values.get(key))
        result = {'chain': 'semantic', 'input-sha256': OBSERVER.digest(self.read(pdf)),
                  'source-sha256': OBSERVER.digest(self.read(source)), 'graph-preserved': canonical_source == canonical,
                  'inventory-differences': differences, 'public-differences': public_differences,
                  'result': 'pass' if canonical_source == canonical and not differences and not public_differences else 'fail'}
        write_json(directory / 'independent-inventory.json', observed)
        write_json(directory / 'source-graph.json', canonical_source)
        write_json(directory / 'product-graph.json', canonical)
        write_json(directory / 'result.json', result)
        return result['result']

    def compare(self, expected, actual, directory, stem):
        self.read(expected)
        self.read(actual)
        code, out, err = self.execute([self.magick, 'compare', '-metric', 'AE', '-fuzz', '0%', expected, actual,
                                       directory / (stem + '-difference.png')], directory, stem)
        metric = err.decode('ascii').strip()
        require(code in (0, 1) and not out.strip() and re.fullmatch(r'[0-9]+(?:\.[0-9]+)?(?:[eE][+\-]?[0-9]+)?(?:\s+\([0-9.eE+\-]+\))?', metric) is not None,
                'ImageMagick did not produce its bounded exact AE metric')
        error = OBSERVER.Decimal(metric.split()[0])
        require(error.is_finite() and error >= 0 and (code != 1 or error > 0),
                'Comparator exit and metric disagree')
        # The pinned comparator measures channel magnitudes, and its status can
        # be zero for a small nonzero error. Apply our exact threshold ourselves.
        return str(error)

    def visual(self, pdf, pages, directory):
        identity = OBSERVER.digest(self.read(pdf))
        results = []
        for number, expected in enumerate(pages, 1):
            raster = directory / ('page-' + str(number) + '.png')
            code, out, err = self.execute([self.pdfium, 'render', pdf, raster, '--dpi', '144', '--file-type', 'png',
                                           '--pages', str(number)], directory, 'render-' + str(number))
            require(code == 0 and not err.strip() and raster.is_file(), 'PDFium image was unavailable')
            reference = self.root / 'capabilities/profiles/T14-images' / expected['path']
            require(OBSERVER.digest(self.read(reference)) == expected['sha256'], 'Original pixel grid identity mismatch')
            error = self.compare(reference, raster, directory, 'compare-' + str(number))
            results.append({'page': number, 'absolute-error': error, 'raster-sha256': digest(raster),
                            'expected-sha256': expected['sha256']})
        result = 'pass' if results and all(OBSERVER.Decimal(page['absolute-error']) == 0 for page in results) else 'fail'
        write_json(directory / 'result.json', {'chain': 'visual', 'input-sha256': identity, 'result': result,
                                              'dpi': 144, 'metric': 'AE', 'fuzz': 0, 'threshold': 0, 'pages': results})
        return result

    def verify(self):
        require(self.identities == self.identity(), 'Acceptance tools or authorities changed during observation')
        for path, expected in self.inputs.items():
            require(digest(path) == expected, 'Original acceptance input changed: ' + str(path))
        write_json(self.output / 'identities-after.json', self.identity())


def qualify(recorder, corpus):
    root, output = recorder.root, recorder.output
    qualification = json.loads(recorder.read(root / 'capabilities/profiles/T14-standards/qualification.json'))
    covered = set()
    for name, control in qualification['controls'].items():
        source = root / 'capabilities/profiles/T14-standards' / control['path']
        require(OBSERVER.digest(recorder.read(source)) == control['sha256'], 'Original image control identity mismatch')
        directory = output / 'negative/standards/images' / name
        directory.mkdir(parents=True)
        copy = directory / 'control.pdf'
        shutil.copyfile(source, copy)
        result = recorder.standards(copy, directory)
        require(result['result'] == 'fail' and result['rule'] == control['rule'], 'Image rule control was not detected: ' + name)
        covered.add(control['rule'])
    require(covered == set(qualification['required-rules']), 'Incomplete required image rule qualification')
    core_path = root / 'capabilities/profiles/T14-standards/pdfcpu.properties'
    core = properties(core_path)
    required_core = core['required-rules'].split(',')
    for rule in required_core:
        prefix = 'negative.' + rule
        source = (core_path.parent / core[prefix + '.path']).resolve()
        require(OBSERVER.digest(recorder.read(source)) == core[prefix + '.sha256'], 'Original pdfcpu control identity mismatch')
        directory = output / 'negative/standards/pdfcpu' / rule
        directory.mkdir(parents=True)
        copy = directory / 'control.pdf'
        shutil.copyfile(source, copy)
        verdict, findings = recorder.strict_pdfcpu(copy, directory)
        require(verdict == 'fail' and core[prefix + '.finding'] in findings, 'pdfcpu control was not detected: ' + rule)
    write_json(output / 'negative/standards/result.json', {'result': 'fail', 'image-rules': sorted(covered),
               'image-control-count': len(qualification['controls']), 'pdfcpu-rules': required_core})

    directory = output / 'negative/syntax'
    directory.mkdir(parents=True)
    invalid = directory / 'invalid.pdf'
    invalid.write_bytes(b'%PDF-2.0\n1 0 obj\n')
    require(recorder.syntax(invalid, directory) == 'fail', 'Syntax control was not detected')

    source = root / 'capabilities/profiles/T14-images/inventory.pdf'
    expected = OBSERVER.flat(corpus['products']['inventory']['extraction'])
    public = dict(expected)
    for name, key in [('order', 'resources.0.kind'), ('usage', 'resources.6.pages'),
                      ('hash', 'resources.6.image.encoded.sha256'), ('identity', 'resources.6.identity'),
                      ('mask', 'resources.7.image.explicit-mask.image'), ('font', 'resources.2.font.subset-prefix')]:
        changed = dict(public)
        changed[key] = '0' if name == 'identity' else 'incorrect'
        require(recorder.semantic(source, source, 'ENCODED_AND_DECODED', expected,
                output / 'negative/semantic' / name, changed) == 'fail', 'Detached semantic control was not detected')

    source = root / 'capabilities/profiles/T14-images/filters.pdf'
    altered = root / 'capabilities/profiles/T14-images/changed-samples.pdf'
    expected = OBSERVER.flat(corpus['products']['filters']['extraction'])
    require(recorder.semantic(altered, source, 'ENCODED_AND_DECODED', expected,
            output / 'negative/semantic/changed-samples', expected) == 'fail',
            'Independent byte/graph semantic control was not detected')
    directory = output / 'negative/visual/changed-samples'
    require(recorder.visual(altered, corpus['products']['filters']['visual'], directory) == 'fail',
            'PDFium changed-sample control was not detected')
    observed = json.loads((directory / 'result.json').read_text())
    require(OBSERVER.Decimal(observed['pages'][0]['absolute-error']) == 800,
            'The renderer control did not complement exactly the original sample rectangle')

    directory = output / 'negative/visual/one-pixel'
    directory.mkdir(parents=True)
    reference = root / 'capabilities/profiles/T14-images/inventory-page-1.png'
    changed = directory / 'changed.png'
    code, _, err = recorder.execute([recorder.magick, reference, '-fill', '#000000', '-draw', 'point 0,0', changed], directory, 'alter')
    require(code == 0 and not err.strip(), 'One-pixel visual control was unavailable')
    error = recorder.compare(reference, changed, directory, 'compare')
    require(OBSERVER.Decimal(error) == 1, 'Exact one-pixel visual control was not detected')
    write_json(directory / 'result.json', {'result': 'fail', 'absolute-error': error,
               'expected-sha256': digest(reference), 'changed-sha256': digest(changed)})
    return covered


def record(root, output, execution):
    recorder = Recorder(root, output)
    corpus = json.loads(recorder.read(root / 'capabilities/profiles/T14-images/corpus.json'))
    require(corpus['profile'] == PROFILE, 'Wrong original image corpus')
    original_products = {path: digest(path) for path in output.rglob('*') if path.is_file()}
    observed_rules = set()
    overall = {'profile': PROFILE, 'phase': 'certification', 'native-execution-profile': execution,
               'facade-execution-profile': 'IN_PROCESS', **dict.fromkeys(CHAINS, 'pass')}
    for api in ('native', 'facade'):
        for name, product in corpus['products'].items():
            directory = output / (api + '-' + name)
            pdf = directory / 'extraction.pdf'
            source = root / 'capabilities/profiles/T14-images' / corpus['sources'][name]['file']
            require(OBSERVER.digest(recorder.read(source)) == corpus['sources'][name]['sha256'], 'Original Source identity mismatch')
            actual = execution if api == 'native' else 'IN_PROCESS'
            receipt = properties(directory / 'publication.properties')
            require(receipt['execution-profile'] == actual and receipt['status'] == 'COMMITTED'
                    and receipt['output-sha256'] == OBSERVER.digest(recorder.read(pdf))
                    and receipt['source-sha256'] == digest(source) and receipt['source-preserved'] == 'pass'
                    and receipt['reopened'] == 'pass' and receipt['partial-output-possible'] == 'false',
                    'Actual public publication record does not identify this product')
            expected = OBSERVER.flat(product['extraction'])
            public = properties(directory / 'observation.properties')
            require(public == expected and properties(directory / 'reopened.properties') == expected,
                    'Detached or reopened values differ from the original literal inventory')
            require(recorder.syntax(pdf, directory / 'syntax') == 'pass', 'Actual product syntax failed')
            require(recorder.semantic(pdf, source, product['byte-access'], expected, directory / 'semantic', public) == 'pass',
                    'Independent extraction or preserved graph disagrees')
            for chain in CHAINS:
                overall[api + '.' + name + '.' + chain] = 'pass' if chain in product['required-chains'] else 'not-required'
            if 'standards' in product['required-chains']:
                require(recorder.strict_pdfcpu(pdf, directory / 'standards/pdfcpu')[0] == 'pass', 'Strict pdfcpu observation failed')
                result = recorder.standards(pdf, directory / 'standards/images')
                require(result['result'] == 'pass', 'Independent image declaration predicates failed')
                observed_rules.update(result['checked-rules'])
            if 'visual' in product['required-chains']:
                require(recorder.visual(pdf, product['visual'], directory / 'visual') == 'pass', 'Original pixel grid disagrees')
    qualified = qualify(recorder, corpus)
    require(qualified == observed_rules, 'Required image rules were not all applied and qualified')
    for path, expected in original_products.items():
        require(digest(path) == expected, 'A public API product or receipt changed during observation')
    recorder.verify()
    manifest = output / 'retained-files.sha256'
    paths = sorted(path for path in output.rglob('*') if path.is_file())
    require(all(not path.is_symlink() for path in output.rglob('*')), 'Linked evidence cannot be retained')
    manifest.write_text(''.join(digest(path) + '  ' + path.relative_to(output).as_posix() + '\n' for path in paths))
    overall['retained-files-sha256'] = digest(manifest)
    (output / 'result.properties').write_text(''.join(key + '=' + value + '\n' for key, value in sorted(overall.items())))
    print('T14 independent syntax, standards, semantic and visual observations passed', flush=True)


if __name__ == '__main__':
    require(len(sys.argv) == 4 and sys.argv[3] in ('IN_PROCESS', 'HARDENED_WORKER'),
            'Usage: t14-certification.py <repository> <products-directory> <execution-profile>')
    record(Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve(), sys.argv[3])
