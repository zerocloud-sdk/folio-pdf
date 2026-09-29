"""Validate original T15 process receipts before exporting Foundation chains.

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

SPEC = importlib.util.spec_from_file_location('t15_observer', Path(__file__).with_name('t15-observer.py'))
OBSERVER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(OBSERVER)
PROFILE = OBSERVER.PROFILE
CHAINS = ('syntax', 'standards', 'semantic', 'visual')
PIN_SHA256 = '6043a95d14af6819e2e8736ddd09ce3843ba9abb1066a4efb208bca40dca133a'
PYTHON_SHA256 = '1643dacd9feaedc58f3cc581e4d22577dfe25c09b10282936186ccf0f2e61118'
SEMANTIC_CONTROLS = {'page-count': 'pages.count', 'marker': 'revision.marker',
                     'placement': 'annotation.existing.page', 'identifier': 'annotation.existing.contents'}


def require(condition, message):
    if not condition:
        raise ValueError('T15 ' + message)


class Collection:
    def __init__(self, root, run):
        self.root, self.run = root, run
        self.files = RetainedFiles(run)
        self.authority = self.files.authorities
        pin = root / 'scripts/t15-evidence-pin.properties'
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
        _, out, err = self.process(directory, stem, ['/workspace/scripts/container-bin/qpdf', '--json=2', '--json-key=qpdf', '--json-key=pages',
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

    def standards(self, pdf, directory, source=None, rule=None):
        data = self.files.read(pdf) if pdf in self.files.expected else self.authority.read(pdf)
        try:
            source_bytes = (self.files.read(source) if source in self.files.expected else self.authority.read(source)) if source else None
            checked = OBSERVER.revision(source_bytes, data) if source else []
            checked += OBSERVER.standards(self.graph(pdf, directory, 'raw'), len(data))
            result = {'result': 'pass', 'checked-rules': sorted(set(checked)), 'rule': '',
                      'finding': 'Closed incremental and signature predicates passed'}
        except OBSERVER.Invalid as failure:
            result = {'result': 'fail', 'checked-rules': [], 'rule': failure.rule, 'finding': str(failure)}
        require(result['rule'] == (rule or '') and result['result'] == ('fail' if rule else 'pass'),
                'independent incremental predicate disagrees with the required result')
        result.update(chain='standards', **{'input-sha256': self.digest(pdf),
                      'source-sha256': self.digest(source) if source else ''})
        require(self.json(directory / 'result.json') == result, 'standards report contradicts raw observations')
        return set(result['checked-rules'])

    def semantic(self, pdf, source, product, directory, public, verdict='pass', public_control=None):
        before = self.graph(source, directory, 'source', 'all')
        after = self.graph(pdf, directory, 'decoded', 'all')
        result, inventory, source_graph, product_graph = OBSERVER.semantic(before, after, product, public)
        result.update(chain='semantic', **{'input-sha256': self.digest(pdf), 'source-sha256': self.digest(source)})
        require(self.json(directory / 'independent-inventory.json') == inventory
                and self.json(directory / 'source-graph.json') == source_graph
                and self.json(directory / 'product-graph.json') == product_graph, 'semantic raw observations were altered')
        require(result['result'] == verdict and self.json(directory / 'result.json') == result,
                'semantic report does not establish the required result')
        if public_control:
            require(result['public-differences'] == [public_control] and not result['inventory-differences']
                    and result['graph-preserved'] and result['appearances-match'], 'detached control changed the wrong property')
        elif verdict == 'fail':
            require(not result['graph-preserved'] and not result['public-differences']
                    and not result['inventory-differences'] and result['appearances-match'],
                    'changed paint control did not change only the protected content')

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

    def visual(self, pdf, pages, directory, errors=None):
        observed = []
        for number, page in enumerate(pages, 1):
            raster = directory / ('page-' + str(number) + '.png')
            _, out, err = self.process(directory, 'render-' + str(number), ['/workspace/scripts/container-bin/pdfium',
                'render', self.inside(pdf), self.inside(raster), '--dpi', '144', '--file-type', 'png', '--pages', str(number), '--render-annotations'])
            require(not err.strip() and out.decode().strip() ==
                    'Rendered page ' + str(number) + ' into ' + self.inside(raster), 'PDFium observation is incomplete')
            reference = self.root / 'capabilities/profiles/T15-signatures' / page['path']
            require(self.authority.digest(reference) == page['sha256'], 'authored raster identity mismatch')
            require(self.files.read(raster).startswith(b'\x89PNG\r\n\x1a\n'), 'missing original PDFium raster')
            measured = self.compare(reference, raster, directory, 'compare-' + str(number), Decimal(errors[number - 1] if errors else 0))
            observed.append({'page': number, 'absolute-error': measured, 'raster-sha256': self.digest(raster),
                             'expected-sha256': page['sha256']})
        require(self.json(directory / 'result.json') == {'chain': 'visual', 'input-sha256': self.digest(pdf),
            'result': 'fail' if errors else 'pass', 'dpi': 144, 'render-annotations': True, 'metric': 'AE', 'fuzz': 0, 'threshold': 0, 'pages': observed},
            'visual report contradicts raw observations')

    def controls(self, corpus):
        authority = self.root / 'capabilities/profiles/T15-signatures'
        source = authority / 'unsigned.pdf'
        covered = set()
        for rule, definition in corpus['rules'].items():
            name = (corpus['controls'][definition['control']]['file'] if 'control' in definition
                    else definition['fixture'] + '.pdf')
            directory = self.run / 'negative/standards/incremental' / rule
            pdf = directory / 'control.pdf'
            require(self.digest(pdf) == self.authority.digest(authority / name), 'wrong original incremental defect')
            self.standards(pdf, directory, source if 'control' in definition else None, rule)
            covered.add(rule)
        core_path = self.root / 'capabilities/profiles/T03-standards/pdfcpu.properties'
        core = self.authority.properties(core_path)
        core_rules = core['required-rules'].split(',')
        for rule in core_rules:
            prefix = 'negative.' + rule
            directory = self.run / 'negative/standards/pdfcpu' / rule
            pdf = directory / 'control.pdf'
            require(self.digest(pdf) == self.authority.digest((core_path.parent / core[prefix + '.path']).resolve())
                    == core[prefix + '.sha256'], 'wrong original pdfcpu defect')
            self.pdfcpu(pdf, directory, core[prefix + '.finding'])
        require(self.json(self.run / 'negative/standards/result.json') == {'result': 'fail',
                'incremental-rules': sorted(covered), 'pdfcpu-rules': core_rules}, 'standards qualification is incomplete')
        directory = self.run / 'negative/syntax'
        invalid = directory / 'invalid.pdf'
        require(self.digest(invalid) == self.authority.digest(authority / corpus['controls']['syntax-truncated']['file']),
                'wrong authored syntax defect')
        self.syntax(invalid, directory, 'fail')
        positive = authority / corpus['controls']['revision-positive']['file']
        self.standards(positive, self.run / 'controls/revision-positive', source)
        product = corpus['products']['unsigned-first']
        for name, key in SEMANTIC_CONTROLS.items():
            public = dict(product['expected'])
            public[key] = 'incorrect'
            self.semantic(positive, source, product, self.run / 'negative/semantic' / name, public, 'fail', key)
        altered = authority / corpus['controls']['changed-paint']['file']
        self.semantic(altered, source, product, self.run / 'negative/semantic/changed-paint', product['expected'], 'fail')
        self.visual(altered, product['visual'], self.run / 'negative/visual/changed-paint', [1600, 1600, 0])
        directory = self.run / 'negative/visual/one-pixel'
        reference = authority / product['visual'][0]['path']
        changed = directory / 'changed.png'
        _, out, err = self.process(directory, 'alter', ['/workspace/scripts/container-bin/imagemagick',
            self.inside(reference), '-fill', '#000000', '-draw', 'point 0,0', self.inside(changed)])
        require(not out.strip() and not err.strip(), 'one pixel construction failed')
        measured = self.compare(reference, changed, directory, 'compare', Decimal(1))
        require(self.json(directory / 'result.json') == {'result': 'fail', 'absolute-error': measured,
            'expected-sha256': self.digest(reference), 'changed-sha256': self.digest(changed)}, 'one pixel control not detected')
        return covered


def collect_reports(root, run, execution):
    require(execution in ('IN_PROCESS', 'HARDENED_WORKER'), 'collection requires the actual Native execution profile')
    collected = Collection(root, run)
    files = collected.files
    expected = {'profile': PROFILE, 'phase': 'certification', 'native-execution-profile': execution,
                'facade-execution-profile': 'IN_PROCESS', **dict.fromkeys(CHAINS, 'pass'),
                'retained-files-sha256': collected.digest(run / 'retained-files.sha256')}
    authority = root / 'capabilities/profiles/T15-signatures'
    corpus = json.loads(collected.authority.read(authority / 'products.json'))
    require(corpus['profile'] == PROFILE and corpus['visual'] == {'dpi': 144, 'render-annotations': True,
            'metric': 'AE', 'fuzz': 0, 'threshold': 0}, 'wrong closed corpus or visual thresholds')
    require(files.properties(run / 'products.properties') == {'phase': 'products-only',
        'native-execution-profile': execution, 'facade-execution-profile': 'IN_PROCESS'}, 'wrong public generation modes')
    reports = {chain: {'chain': chain, 'result': 'pass', 'products': [], 'findings': [], 'negative-controls': []}
               for chain in CHAINS}
    checked = set()
    for api in ('native', 'facade'):
        for name, product in corpus['products'].items():
            directory = run / (api + '-' + name)
            pdf = directory / 'incremental.pdf'
            source = (run / (api + '-unsigned-first/incremental.pdf') if name == 'unsigned-second'
                      else authority / (product['source'] + '.pdf'))
            require(files.properties(directory / 'publication.properties') == {
                'execution-profile': execution if api == 'native' else 'IN_PROCESS', 'save-mode': 'INCREMENTAL',
                'target-name': 'target', 'status': 'COMMITTED', 'partial-output-possible': 'false',
                'source-preserved': 'pass', 'reopened': 'pass', 'source-sha256': collected.digest(source),
                'output-sha256': collected.digest(pdf)}, 'wrong actual API execution or Publication Receipt')
            public = product['expected']
            require(files.properties(directory / 'observation.properties') == public
                    and files.properties(directory / 'reopened.properties') == public, 'public literal/reopened values disagree')
            collected.syntax(pdf, directory / 'syntax')
            collected.pdfcpu(pdf, directory / 'standards/pdfcpu')
            checked.update(collected.standards(pdf, directory / 'standards/incremental', source))
            collected.semantic(pdf, source, product, directory / 'semantic', public)
            collected.visual(pdf, product['visual'], directory / 'visual')
            for chain in CHAINS:
                expected[api + '.' + name + '.' + chain] = 'pass'
                reports[chain]['products'].append(files.reference(root, pdf))
                reports[chain]['findings'].extend(files.reference(root, path) for path in files.under(directory / chain))
                if chain == 'semantic':
                    reports[chain]['findings'].extend(files.reference(root, directory / path) for path in
                        ('publication.properties', 'observation.properties', 'reopened.properties'))
    require(files.result == expected, 'aggregate record has missing or inconsistent chains')
    require(collected.controls(corpus) == checked == set(corpus['rules']), 'required rule was not applied and qualified')
    for chain, report in reports.items():
        report['negative-controls'].extend(files.reference(root, path) for path in files.under(run / 'negative' / chain))
        report['findings'].extend(files.reference(root, run / name) for name in
            ('result.properties', 'retained-files.sha256', 'products.properties', 'identities-before.json', 'identities-after.json'))
        if chain == 'standards':
            report['findings'].extend(files.reference(root, path) for path in files.under(run / 'controls'))
    files.verify()
    return reports
