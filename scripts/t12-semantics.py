#!/usr/bin/env python3
"""Observe T12 semantics from the pinned qpdf object graph, without Folio reads.

This acceptance-only observer consumes original frozen expectations and exact
product bytes. It never imports the product generator or a Folio implementation.
Apache-2.0, Folio PDF by ZeroCloud contributors.
"""
import base64
from decimal import Decimal
import hashlib
import json
from pathlib import Path
import platform
import re
import resource
import subprocess
import sys

PROFILE = 'T12-annotations-document-actions'
CORPUS_SHA256 = 'fa6834bb206298743aba8f54af658315b1dee54b86319afbddb427af8676ad9a'
QPDF_BINARY_SHA256 = '9ac787a28597e8428289a12ba3fedafd74bdfb4b4da1be814722faf76f14f21b'
MAX_BYTES = 16 * 1024 * 1024
REFERENCE = re.compile(r'^[1-9][0-9]* [0-9]+ R$')


class Mismatch(Exception):
    """A readable object graph differs from one fixed semantic expectation."""


def require(condition, field):
    if not condition:
        raise Mismatch(field)


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def same(expected, actual, path):
    if isinstance(expected, dict):
        require(isinstance(actual, dict) and set(expected) == set(actual), path + ' keys')
        for key in sorted(expected):
            same(expected[key], actual[key], path + '.' + key)
    elif isinstance(expected, list):
        require(isinstance(actual, list) and len(expected) == len(actual), path + ' count')
        for index, (left, right) in enumerate(zip(expected, actual)):
            same(left, right, path + '.' + str(index))
    else:
        require(expected == actual and isinstance(expected, bool) == isinstance(actual, bool), path)


class Observation:
    def __init__(self, graph, corpus, product):
        require(graph['version'] == 2 and graph['qpdf'][0]['jsonversion'] == 2, 'qpdf JSON schema')
        self.objects = graph['qpdf'][1]
        self.page_refs = [p['object'] for p in graph['pages']]
        require(len(self.page_refs) <= 100 and len(set(self.page_refs)) == len(self.page_refs), 'page identity bound')
        self.pages = {reference: index for index, reference in enumerate(self.page_refs, 1)}
        self.corpus, self.expected = corpus, corpus['products'][product]
        self.observed = {}

    def raw(self, value):
        seen = set()
        while isinstance(value, str) and REFERENCE.fullmatch(value):
            require(value not in seen and len(seen) < 64, 'acyclic bounded reference')
            seen.add(value)
            item = self.objects['obj:' + value]
            if 'stream' in item:
                return item
            value = item['value']
        return value

    def dictionary(self, value):
        result = self.raw(value)
        require(isinstance(result, dict) and 'stream' not in result, 'dictionary value')
        return result

    def array(self, value):
        result = self.raw(value)
        require(isinstance(result, list) and len(result) <= 10000, 'bounded array value')
        return result

    def stream(self, value):
        item = self.raw(value)['stream']
        require(isinstance(item['dict'], dict), 'stream dictionary')
        data = base64.b64decode(item['data'], validate=True)
        require(len(data) <= 1024 * 1024, 'decoded stream bound')
        return item['dict'], data

    def text(self, value):
        value = self.raw(value)
        require(isinstance(value, str) and value.startswith('u:'), 'Unicode PDF string')
        return value[2:]

    def target(self, value):
        resolved = self.raw(value)
        if isinstance(resolved, str):
            require(resolved.startswith(('u:', '/')), 'named target kind')
            return {'kind': 'NAMED', 'named': resolved[2:] if resolved.startswith('u:') else resolved[1:]}
        array = self.array(resolved)
        require(len(array) >= 2 and array[0] in self.pages, 'live explicit target page')
        styles = {'/Fit': 'FIT', '/FitH': 'FIT_H', '/FitR': 'FIT_R', '/XYZ': 'XYZ'}
        style = styles[array[1]]
        return {'kind': 'PAGE', 'page': self.pages[array[0]], 'style': style, 'parameters': array[2:]}

    def action(self, value):
        if value is None:
            return None
        action = self.dictionary(value)
        require(set(action) == {'/S', '/D'} and action['/S'] == '/GoTo', 'single inert local GoTo action')
        return self.target(action['/D'])

    def appearance(self, normal):
        dictionary, data = self.stream(normal)
        require(dictionary.get('/Type') == '/XObject' and dictionary.get('/Subtype') == '/Form', 'normal Form identity')
        require(dictionary.get('/FormType', 1) == 1, 'normal Form type')
        require(self.dictionary(dictionary.get('/Resources')) == {}, 'resource-free normal appearance')
        matrix = self.array(dictionary.get('/Matrix', [1, 0, 0, 1, 0, 0]))
        same([1, 0, 0, 1, 0, 0], matrix, 'normal appearance identity matrix')
        program = data.decode('ascii')
        color = re.fullmatch(r'q ([01]) ([01]) ([01]) rg 2 3 10 10 re f Q\n', program)
        # The frozen corpus contains only these exactly known resource-free programs.
        require(color is not None, 'frozen appearance graphics program')
        return {'box': self.array(dictionary['/BBox']), 'matrix': matrix, 'resources': 'empty',
                'program': program, 'color': [int(component) for component in color.groups()]}

    def attachment(self, value):
        specification = self.dictionary(value)
        file_name = self.text(specification['/F'])
        if '/UF' in specification:
            require(self.text(specification['/UF']) == file_name, 'Unicode attachment filename')
        embedded = self.dictionary(specification['/EF'])
        stream, data = self.stream(embedded['/F'])
        if '/UF' in embedded:
            require(self.stream(embedded['/UF'])[1] == data, 'Unicode embedded-file alias')
        return {'name': file_name, 'hex': data.hex(), 'size': len(data), 'sha256': sha256(data),
                'md5': hashlib.md5(data).hexdigest(), 'mime': stream['/Subtype'][1:],
                'description': self.text(specification['/Desc']), 'relationship': specification['/AFRelationship'][1:]}

    def annotation(self, reference, page_number, page_reference):
        annotation = self.dictionary(reference)
        require(annotation.get('/Type', '/Annot') == '/Annot', 'annotation Type')
        require(annotation.get('/P', page_reference) == page_reference, 'annotation owning page reference')
        types = {'/Text': 'TEXT', '/Stamp': 'STAMP', '/Highlight': 'HIGHLIGHT', '/FileAttachment': 'FILE_ATTACHMENT',
                 '/Widget': 'WIDGET', '/Link': 'LINK'}
        kind = types[annotation['/Subtype']]
        require(annotation.get('/F') == 4, 'annotation PRINT flags')
        appearances = self.dictionary(annotation['/AP'])
        require(set(appearances) == {'/N'}, 'one normal appearance binding')
        result = {'id': self.text(annotation['/NM']), 'type': kind, 'page': page_number,
                  'rectangle': self.array(annotation['/Rect']), 'contents': self.text(annotation['/Contents']),
                  'flags': ['PRINT'], 'appearance': self.appearance(appearances['/N'])}
        if kind == 'TEXT':
            result.update(icon=annotation.get('/Name', '/Note')[1:].upper(), open=annotation.get('/Open', False))
        elif kind == 'STAMP':
            result['stamp'] = annotation['/Name'][1:]
        elif kind == 'HIGHLIGHT':
            quads = self.array(annotation['/QuadPoints'])
            require(len(quads) > 0 and len(quads) % 8 == 0, 'Highlight quadrilateral count')
            result.update(quads=[quads[i:i + 8] for i in range(0, len(quads), 8)], color=self.array(annotation['/C']))
        elif kind == 'FILE_ATTACHMENT':
            result.update(icon=annotation['/Name'][1:].upper(), attachment=self.attachment(annotation['/FS']))
        elif kind == 'WIDGET':
            require(not set(annotation).intersection({'/FT', '/Parent', '/Kids', '/V', '/DV', '/T'}), 'standalone Widget')
        elif kind == 'LINK':
            require(('/Dest' in annotation) != ('/A' in annotation), 'one Link activation')
            require(annotation.get('/Border', [0, 0, 0]) == [0, 0, 0], 'zero Link border')
            result.update(activation='DESTINATION' if '/Dest' in annotation else 'ACTION',
                          target=self.target(annotation['/Dest']) if '/Dest' in annotation else self.action(annotation['/A']))
        return result

    def verify_page_paint(self, page_number, page, marker):
        original = self.corpus['pages'][marker]
        resources = self.dictionary(page['/Resources'])
        raw_contents = self.raw(page['/Contents'])
        streams = raw_contents if isinstance(raw_contents, list) else [page['/Contents']]
        programs = [self.stream(stream)[1].decode('ascii') for stream in streams]
        for name, key in [('media-box', '/MediaBox'), ('crop-box', '/CropBox')]:
            same(original[name], self.array(page[key]), 'page ' + str(page_number) + ' ' + name)
        same(original['rotation'], page.get('/Rotate', 0), 'page rotation')
        flattened = [a for a in self.expected['flattened'] if a['page'] == page_number]
        observation = {'marker': marker, 'contents': programs, 'flattened': []}
        self.observed['page-observations'].append(observation)
        if not flattened:
            same([p.rstrip('\x00\t\n\x0c\r ') for p in original['contents']],
                 [p.rstrip('\x00\t\n\x0c\r ') for p in programs], 'retained page programs')
            require(resources == {}, 'unchanged page resources')
            return
        count = len(original['contents'])
        same(['q\n'] + original['contents'] + ['Q\n'], programs[:count + 2], 'isolated retained page programs')
        xobjects = self.dictionary(resources['/XObject'])
        require(set(resources) == {'/XObject'} and len(xobjects) == len(flattened), 'flattened Form resource count')
        tokens = ' '.join(programs[count + 2:]).split()
        require(len(tokens) == 11 * len(flattened), 'flattened invocation count')
        for index, expected in enumerate(flattened):
            invocation = tokens[index * 11:(index + 1) * 11]
            require(invocation[0] == 'q' and invocation[7] == 'cm' and invocation[9:] == ['Do', 'Q'], 'flattened invocation operators')
            actual = self.appearance(xobjects[invocation[8]])
            same(expected['appearance'], actual, 'flattened normal appearance')
            box = [Decimal(str(v)) for v in expected['appearance']['box']]
            rect = [Decimal(str(v)) for v in expected['rectangle']]
            sx, sy = (rect[2] - rect[0]) / (box[2] - box[0]), (rect[3] - rect[1]) / (box[3] - box[1])
            matrix = [sx, Decimal(0), Decimal(0), sy, rect[0] - box[0] * sx, rect[1] - box[1] * sy]
            require(matrix == [Decimal(v) for v in invocation[1:7]], 'flattened appearance placement')
            observation['flattened'].append({'appearance': actual, 'matrix': [str(v) for v in matrix]})

    def verify(self):
        catalog = self.dictionary(self.objects['trailer']['value']['/Root'])
        require('/AcroForm' not in catalog, 'no AcroForm')
        same({'/Flag': True, '/Numbers': [1, 2]}, self.dictionary(catalog['/T74Keep']), 'retained catalog extension')
        self.observed = {'pages': [], 'annotations': [], 'destinations': {},
                         'actions': {'document-open': self.action(catalog.get('/OpenAction')), 'pages': []},
                         'page-observations': []}
        require(len(self.page_refs) == len(self.expected['pages']), 'page count')
        for number, reference in enumerate(self.page_refs, 1):
            page = self.dictionary(reference)
            marker = page['/T74Marker'][1:]
            self.observed['pages'].append(marker)
            self.verify_page_paint(number, page, marker)
            for annotation in self.array(page.get('/Annots', [])):
                self.observed['annotations'].append(self.annotation(annotation, number, reference))
            actions = self.dictionary(page.get('/AA', {}))
            require(set(actions).issubset({'/O', '/C'}), 'page Action allowlist')
            if actions:
                self.observed['actions']['pages'].append({'page': number, 'open': self.action(actions.get('/O')),
                                                         'close': self.action(actions.get('/C'))})
        names = self.dictionary(catalog.get('/Names', {}))
        tree = self.dictionary(names.get('/Dests', {}))
        pairs = self.array(tree.get('/Names', []))
        require(len(pairs) % 2 == 0 and '/Kids' not in tree, 'frozen flat destination tree')
        for index in range(0, len(pairs), 2):
            name = self.text(pairs[index])
            require(name not in self.observed['destinations'], 'unique named destination')
            self.observed['destinations'][name] = self.target(pairs[index + 1])
        for key in ('pages', 'annotations', 'destinations', 'actions'):
            same(self.expected[key], self.observed[key], key)


def run_process(arguments, output, stem):
    def limits():
        resource.setrlimit(resource.RLIMIT_FSIZE, (MAX_BYTES, MAX_BYTES))
    with (output / (stem + '.txt')).open('wb') as stdout, (output / (stem + '-stderr.txt')).open('wb') as stderr:
        result = subprocess.run(arguments, stdout=stdout, stderr=stderr, timeout=30, preexec_fn=limits)
    if result.returncode != 0:
        raise OSError('Pinned qpdf observation did not complete successfully')
    data = (output / (stem + '.txt')).read_bytes()
    if len(data) >= MAX_BYTES:
        raise OSError('Pinned qpdf observation exceeded its output bound')
    return data


def properties(path, values):
    def escaped(value):
        return str(value).replace('\\', '\\\\').replace('\n', '\\n').replace('\r', '\\r')
    path.write_text(''.join(key + '=' + escaped(value) + '\n' for key, value in sorted(values.items())), encoding='utf-8')


def inspect(root, input_pdf, product, output):
    output.mkdir()
    before = sha256(input_pdf.read_bytes())
    result = {'profile': PROFILE, 'semantic': 'indeterminate', 'input-sha256': before,
              'python-version': platform.python_version(), 'observer-sha256': sha256(Path(__file__).read_bytes()),
              'python-executable-sha256': sha256(Path(sys.executable).resolve().read_bytes()),
              'semantic-scope': 'independent-qpdf-object-graph'}
    observation = None
    try:
        authority = root / 'capabilities/profiles/T12-annotations/corpus.json'
        corpus_bytes = authority.read_bytes()
        if sha256(corpus_bytes) != CORPUS_SHA256:
            raise OSError('Frozen T12 corpus identity mismatch')
        result['expectations-sha256'] = CORPUS_SHA256
        corpus = json.loads(corpus_bytes)
        if product not in corpus['products']:
            raise OSError('Unknown frozen T12 product')
        pin = dict(line.split('=', 1) for line in (root / 'scripts/qpdf-pin.properties').read_text().splitlines()
                   if line and not line.startswith('#'))
        if pin['QPDF_VERSION'] != '12.4.0' or pin['QPDF_BINARY_SHA256'] != QPDF_BINARY_SHA256:
            raise OSError('Pinned qpdf authority identity mismatch')
        executable = (root / 'scripts' / pin['QPDF_EXECUTABLE']).resolve()
        if executable != (root / 'scripts/container-bin/qpdf').resolve():
            raise OSError('Unqualified qpdf executable')
        result['qpdf-wrapper-sha256'] = sha256(executable.read_bytes())
        version = run_process([str(executable), '--version'], output, 'qpdf-version').decode('ascii')
        if not version.startswith('qpdf version 12.4.0\n'):
            raise OSError('Observed qpdf version mismatch')
        result['qpdf-version'] = '12.4.0'
        result['qpdf-binary-sha256'] = QPDF_BINARY_SHA256
        graph = run_process([str(executable), '--json=2', '--json-stream-data=inline', '--decode-level=all', str(input_pdf)],
                            output, 'qpdf')
        (output / 'qpdf.txt').rename(output / 'qpdf.json')
        result['qpdf-json-sha256'] = sha256(graph)
        observation = Observation(json.loads(graph), corpus, product)
        observation.verify()
        result.update(semantic='pass', finding='All frozen annotation, Action, target, payload and retained-paint expectations matched.')
    except (Mismatch, KeyError, TypeError, ValueError, IndexError, UnicodeError) as failure:
        result.update(semantic='fail', finding='Frozen semantic mismatch: ' + str(failure))
    except (OSError, subprocess.TimeoutExpired) as failure:
        result.update(semantic='indeterminate', finding=str(failure))
    if sha256(input_pdf.read_bytes()) != before:
        result.update(semantic='indeterminate', finding='Input changed during independent semantic observation')
    if observation is not None:
        (output / 'observed.json').write_text(json.dumps(observation.observed, indent=2, sort_keys=True) + '\n')
    properties(output / 'result.properties', result)
    (output / 'semantic.txt').write_text('Input exact SHA-256: ' + before + '\n' + result['finding'] + '\n')


if __name__ == '__main__':
    if len(sys.argv) != 5:
        raise SystemExit('Usage: t12-semantics.py <repository> <input.pdf> <product> <fresh-output>')
    inspect(Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve(), sys.argv[3], Path(sys.argv[4]).resolve())
