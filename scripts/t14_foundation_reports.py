"""Validate original T14 process receipts before exporting Foundation chains.

Collection never promotes aggregate pass labels on their own: it verifies the
retained tree, frozen inputs, actual commands, raw findings and public values.
It does not execute Folio or generate expectations from the observed products.
"""
import importlib.util
import json
from pathlib import Path
import re
from decimal import Decimal

from t13_foundation_reports import RetainedFiles, parse_properties

SPEC = importlib.util.spec_from_file_location('t14_observer', Path(__file__).with_name('t14-observer.py'))
OBSERVER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(OBSERVER)
PROFILE = OBSERVER.PROFILE
CHAINS = ('syntax', 'standards', 'semantic', 'visual')
PIN_SHA256 = 'a7304b08ff1b86d9742c0462fcc8a30856cda545c269b0038f6a9fe5cd4c26c8'
PYTHON_SHA256 = '1643dacd9feaedc58f3cc581e4d22577dfe25c09b10282936186ccf0f2e61118'
SEMANTIC_CONTROLS = {
    'order': 'resources.0.kind', 'usage': 'resources.6.pages',
    'hash': 'resources.6.image.encoded.sha256', 'identity': 'resources.6.identity',
    'mask': 'resources.7.image.explicit-mask.image', 'font': 'resources.2.font.subset-prefix'}


def require(condition, message):
    if not condition:
        raise ValueError('T14 ' + message)


class Collection:
    def __init__(self, root, run):
        self.root, self.run = root, run
        self.files = RetainedFiles(run)
        self.authority = self.files.authorities
        pin = root / 'scripts/t14-evidence-pin.properties'
        require(self.authority.digest(pin) == PIN_SHA256, 'frozen acceptance identities changed')
        expected = self.authority.properties(pin)
        for name, identity in expected.items():
            if not name.startswith('.build-cache/'):
                require(self.authority.digest(root / name) == identity, 'authority identity mismatch: ' + name)
        expected['python-executable-sha256'] = PYTHON_SHA256
        for name, folder in [('scripts/t13-qpdf-runtime.sha256', '.build-cache/qpdf/12.4.0'),
                             ('scripts/imagemagick-runtime.sha256', '.build-cache/imagemagick/7.1.2-30/runtime')]:
            for line in self.authority.read(root / name).decode().splitlines():
                identity, relative = line.split('  ', 1)
                expected[folder + '/' + relative] = identity
        for phase in ('before', 'after'):
            require(self.json(run / ('identities-' + phase + '.json')) == expected,
                    'tool or authority observation changed: ' + phase)

    def json(self, path):
        return json.loads(self.files.read(path))

    def inside(self, path):
        return '/workspace/' + path.relative_to(self.root).as_posix()

    def digest(self, path):
        return self.files.expected[path] if path in self.files.expected else self.authority.digest(path)

    def process(self, directory, stem, command, codes=(0,), timeout=30):
        receipt = self.json(directory / (stem + '.command.json'))
        out = self.files.read(directory / (stem + '.stdout'))
        err = self.files.read(directory / (stem + '.stderr'))
        expected = {'command': command, 'exit-code': receipt.get('exit-code'), 'limit': None,
                    'maximum-output-bytes': 16 * 1024 * 1024, 'timeout-seconds': timeout,
                    'stdout-sha256': OBSERVER.digest(out), 'stderr-sha256': OBSERVER.digest(err)}
        require(receipt == expected and receipt['exit-code'] in codes and len(out) + len(err) <= 16 * 1024 * 1024,
                'missing, failed or inconsistent process observation: ' + str(directory / stem))
        return receipt['exit-code'], out, err

    def graph(self, pdf, directory, stem, decode='none'):
        _, out, err = self.process(directory, stem, ['/workspace/scripts/container-bin/qpdf', '--json=2',
            '--json-stream-data=inline', '--decode-level=' + decode, self.inside(pdf)])
        require(not err.strip(), 'qpdf graph observation has a diagnostic')
        return OBSERVER.Graph(json.loads(out, parse_float=Decimal))

    def syntax(self, pdf, directory, verdict='pass'):
        code, out, err = self.process(directory, 'qpdf',
            ['/workspace/scripts/container-bin/qpdf', '--check', self.inside(pdf)], (0,) if verdict == 'pass' else (2,))
        require((code == 0 and not err.strip() and b'No syntax or stream encoding errors found' in out)
                if verdict == 'pass' else b'unable to find trailer dictionary' in err,
                'syntax finding does not establish its result')
        require(self.json(directory / 'result.json') == {
            'chain': 'syntax', 'result': verdict, 'input-sha256': self.digest(pdf)}, 'syntax input/result mismatch')

    def pdfcpu(self, pdf, directory, finding=None):
        verdict = 'fail' if finding else 'pass'
        _, out, err = self.process(directory, 'pdfcpu', ['/workspace/.build-cache/pdfcpu/0.15.0/pdfcpu',
            'validate', '--mode', 'strict', '--conf', 'disable', '--offline', self.inside(pdf)],
            (1,) if finding else (0,), timeout=10)
        text = (out + err).decode('utf-8')
        require(('validation error' in text and finding in text) if finding else
                text.rstrip().endswith('validation ok') and 'validation error' not in text,
                'strict pdfcpu finding is incomplete')
        require(self.json(directory / 'result.json') == {
            'chain': 'standards', 'result': verdict, 'input-sha256': self.digest(pdf)}, 'pdfcpu input/result mismatch')

    def standards(self, pdf, directory, rule=None):
        raw = self.graph(pdf, directory, 'raw')
        try:
            OBSERVER.standards(raw)
            checked = OBSERVER.standards(raw, self.graph(pdf, directory, 'decoded', 'all'))
            expected = {'result': 'pass', 'checked-rules': checked, 'rule': '',
                        'finding': 'Closed image declaration predicates passed'}
        except OBSERVER.Invalid as failure:
            expected = {'result': 'fail', 'checked-rules': [], 'rule': failure.rule, 'finding': str(failure)}
        require(expected['rule'] == (rule or '') and expected['result'] == ('fail' if rule else 'pass'),
                'independent image predicate disagrees with the required control or positive')
        expected.update(chain='standards', **{'input-sha256': self.digest(pdf)})
        require(self.json(directory / 'result.json') == expected, 'image predicate report contradicts raw qpdf graph')
        return set(expected['checked-rules'])

    def semantic(self, pdf, source, product, directory, public, verdict='pass', public_control=None):
        source_graph = self.graph(source, directory, 'source').canonical()
        raw = self.graph(pdf, directory, 'raw')
        decoded = self.graph(pdf, directory, 'decoded', 'all') if product['byte-access'] != 'ENCODED' else raw
        inventory = OBSERVER.flat(OBSERVER.inventory(raw, decoded, product['byte-access']))
        expected = OBSERVER.flat(product['extraction'])
        differences = sorted(key for key in expected.keys() | inventory.keys() if expected.get(key) != inventory.get(key))
        public_differences = sorted(key for key in expected.keys() | public.keys() if expected.get(key) != public.get(key))
        graph = raw.canonical()
        require(self.json(directory / 'independent-inventory.json') == inventory
                and self.json(directory / 'source-graph.json') == source_graph
                and self.json(directory / 'product-graph.json') == graph, 'semantic raw observation was altered')
        observed = {'chain': 'semantic', 'input-sha256': self.digest(pdf), 'source-sha256': self.digest(source),
                    'graph-preserved': graph == source_graph, 'inventory-differences': differences,
                    'public-differences': public_differences,
                    'result': 'pass' if graph == source_graph and not differences and not public_differences else 'fail'}
        require(observed['result'] == verdict and self.json(directory / 'result.json') == observed,
                'semantic report does not establish required result')
        if public_control:
            require(public_differences == [public_control] and not differences and graph == source_graph,
                    'detached negative control changed the wrong field')
        elif verdict == 'fail':
            require(bool(differences) and graph != source_graph and not public_differences,
                    'changed sample control did not change independently observed bytes and graph')

    def compare(self, expected, actual, directory, stem, required):
        difference = directory / (stem + '-difference.png')
        code, out, err = self.process(directory, stem, ['/workspace/scripts/container-bin/imagemagick',
            'compare', '-metric', 'AE', '-fuzz', '0%', self.inside(expected), self.inside(actual),
            self.inside(difference)], (0, 1))
        metric = err.decode('ascii').strip()
        require(not out.strip() and re.fullmatch(
            r'[0-9]+(?:\.[0-9]+)?(?:[eE][+\-]?[0-9]+)?(?:\s+\([0-9.eE+\-]+\))?', metric) is not None,
            'missing original AE metric')
        error = Decimal(metric.split()[0])
        require(error.is_finite() and error == required and (code != 1 or error > 0),
                'AE or process status contradicts the qualified threshold')
        require(self.files.read(difference).startswith(b'\x89PNG\r\n\x1a\n'), 'missing comparator raster')
        return str(error)

    def visual(self, pdf, pages, directory, error=0):
        observed = []
        for number, page in enumerate(pages, 1):
            raster = directory / ('page-' + str(number) + '.png')
            _, out, err = self.process(directory, 'render-' + str(number), ['/workspace/scripts/container-bin/pdfium',
                'render', self.inside(pdf), self.inside(raster), '--dpi', '144', '--file-type', 'png', '--pages', str(number)])
            require(not err.strip() and out.decode().strip() ==
                    'Rendered page ' + str(number) + ' into ' + self.inside(raster), 'PDFium observation is incomplete')
            reference = self.root / 'capabilities/profiles/T14-images' / page['path']
            require(self.authority.digest(reference) == page['sha256'], 'authored raster identity mismatch')
            require(self.files.read(raster).startswith(b'\x89PNG\r\n\x1a\n'), 'missing original PDFium raster')
            measured = self.compare(reference, raster, directory, 'compare-' + str(number), Decimal(error))
            observed.append({'page': number, 'absolute-error': measured, 'raster-sha256': self.digest(raster),
                             'expected-sha256': page['sha256']})
        require(self.json(directory / 'result.json') == {'chain': 'visual', 'input-sha256': self.digest(pdf),
            'result': 'fail' if error else 'pass', 'dpi': 144, 'metric': 'AE', 'fuzz': 0, 'threshold': 0, 'pages': observed},
            'visual report contradicts raw observations')

    def controls(self, corpus):
        directory = self.root / 'capabilities/profiles/T14-standards'
        qualification = json.loads(self.authority.read(directory / 'qualification.json'))
        covered = set()
        for name, definition in qualification['controls'].items():
            scope = self.run / 'negative/standards/images' / name
            pdf = scope / 'control.pdf'
            require(self.digest(pdf) == self.authority.digest(directory / definition['path']) == definition['sha256'],
                    'image control is not the original authored defect')
            self.standards(pdf, scope, definition['rule'])
            covered.add(definition['rule'])
        require(covered == set(qualification['required-rules']), 'unqualified required image rule')
        core = self.authority.properties(directory / 'pdfcpu.properties')
        core_rules = core['required-rules'].split(',')
        for rule in core_rules:
            scope = self.run / 'negative/standards/pdfcpu' / rule
            pdf = scope / 'control.pdf'
            prefix = 'negative.' + rule
            require(self.digest(pdf) == self.authority.digest((directory / core[prefix + '.path']).resolve())
                    == core[prefix + '.sha256'], 'pdfcpu control is not the original authored defect')
            self.pdfcpu(pdf, scope, core[prefix + '.finding'])
        require(self.json(self.run / 'negative/standards/result.json') == {'result': 'fail',
            'image-rules': sorted(covered), 'image-control-count': len(qualification['controls']), 'pdfcpu-rules': core_rules},
            'standards qualification is incomplete')
        syntax = self.run / 'negative/syntax'
        require(self.files.read(syntax / 'invalid.pdf') == b'%PDF-2.0\n1 0 obj\n', 'wrong syntax defect')
        self.syntax(syntax / 'invalid.pdf', syntax, 'fail')
        authority = self.root / 'capabilities/profiles/T14-images'
        source = authority / 'inventory.pdf'
        for name, key in SEMANTIC_CONTROLS.items():
            public = OBSERVER.flat(corpus['products']['inventory']['extraction'])
            public[key] = '0' if name == 'identity' else 'incorrect'
            self.semantic(source, source, corpus['products']['inventory'], self.run / 'negative/semantic' / name,
                          public, 'fail', key)
        altered = authority / 'changed-samples.pdf'
        self.semantic(altered, authority / 'filters.pdf', corpus['products']['filters'],
            self.run / 'negative/semantic/changed-samples', OBSERVER.flat(corpus['products']['filters']['extraction']), 'fail')
        self.visual(altered, corpus['products']['filters']['visual'], self.run / 'negative/visual/changed-samples', 800)
        scope = self.run / 'negative/visual/one-pixel'
        reference = authority / 'inventory-page-1.png'
        changed = scope / 'changed.png'
        _, _, err = self.process(scope, 'alter', ['/workspace/scripts/container-bin/imagemagick',
            self.inside(reference), '-fill', '#000000', '-draw', 'point 0,0', self.inside(changed)])
        require(not err.strip(), 'one pixel control construction failed')
        measured = self.compare(reference, changed, scope, 'compare', Decimal(1))
        require(self.json(scope / 'result.json') == {'result': 'fail', 'absolute-error': measured,
            'expected-sha256': self.digest(reference), 'changed-sha256': self.digest(changed)}, 'one pixel control not detected')
        return covered


def collect_reports(root, run, execution):
    require(execution in ('IN_PROCESS', 'HARDENED_WORKER'), 'collection requires the actual Native execution profile')
    collected = Collection(root, run)
    files = collected.files
    expected = {'profile': PROFILE, 'phase': 'certification', 'native-execution-profile': execution,
                'facade-execution-profile': 'IN_PROCESS', **dict.fromkeys(CHAINS, 'pass'),
                'retained-files-sha256': collected.digest(run / 'retained-files.sha256')}
    corpus = json.loads(collected.authority.read(root / 'capabilities/profiles/T14-images/corpus.json'))
    require(corpus['profile'] == PROFILE, 'wrong corpus profile')
    require(files.properties(run / 'products.properties') == {'phase': 'products-only',
        'native-execution-profile': execution, 'facade-execution-profile': 'IN_PROCESS'}, 'wrong public generation modes')
    reports = {chain: {'chain': chain, 'result': 'pass', 'products': [], 'findings': [], 'negative-controls': []}
               for chain in CHAINS}
    checked = set()
    for api in ('native', 'facade'):
        for name, product in corpus['products'].items():
            directory = run / (api + '-' + name)
            pdf = directory / 'extraction.pdf'
            source = root / 'capabilities/profiles/T14-images' / corpus['sources'][name]['file']
            require(collected.authority.digest(source) == corpus['sources'][name]['sha256'], 'authored Source changed')
            require(files.properties(directory / 'publication.properties') == {
                'execution-profile': execution if api == 'native' else 'IN_PROCESS', 'target-name': 'target',
                'status': 'COMMITTED', 'partial-output-possible': 'false', 'source-preserved': 'pass', 'reopened': 'pass',
                'source-sha256': collected.digest(source), 'output-sha256': collected.digest(pdf)},
                'wrong actual API execution or Publication Receipt')
            public = OBSERVER.flat(product['extraction'])
            require(files.properties(directory / 'observation.properties') == public
                    and files.properties(directory / 'reopened.properties') == public,
                    'detached or reopened public values differ from literal authored expectations')
            collected.syntax(pdf, directory / 'syntax')
            collected.semantic(pdf, source, product, directory / 'semantic', public)
            if 'standards' in product['required-chains']:
                collected.pdfcpu(pdf, directory / 'standards/pdfcpu')
                checked.update(collected.standards(pdf, directory / 'standards/images'))
            if 'visual' in product['required-chains']:
                collected.visual(pdf, product['visual'], directory / 'visual')
            for chain in CHAINS:
                expected[api + '.' + name + '.' + chain] = 'pass' if chain in product['required-chains'] else 'not-required'
                if chain in product['required-chains']:
                    reports[chain]['products'].append(files.reference(root, pdf))
                    reports[chain]['findings'].extend(files.reference(root, path) for path in files.under(directory / chain))
                    if chain == 'semantic':
                        reports[chain]['findings'].extend(files.reference(root, directory / path) for path in
                            ('publication.properties', 'observation.properties', 'reopened.properties'))
    require(files.result == expected, 'aggregate record has missing or inconsistent chains')
    require(collected.controls(corpus) == checked, 'required image rules were not both observed and qualified')
    for chain, report in reports.items():
        report['negative-controls'].extend(files.reference(root, path) for path in files.under(run / 'negative' / chain))
        report['findings'].extend(files.reference(root, run / name) for name in
            ('result.properties', 'retained-files.sha256', 'products.properties', 'identities-before.json', 'identities-after.json'))
    files.verify()
    return reports
