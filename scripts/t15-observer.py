#!/usr/bin/env python3
"""Closed T15 standards/semantic predicates over independent qpdf observations.

No Folio classes or fixture-authoring code are imported. Signature bytes are
structural evidence only; these predicates never assess cryptographic validity.
"""
import base64
from decimal import Decimal
import hashlib
import re

PROFILE = 'T15-incremental-signature-protection'
REFERENCE = re.compile(r'^[1-9][0-9]* [0-9]+ R$')


class Invalid(Exception):
    def __init__(self, rule, message):
        super().__init__(message)
        self.rule = rule


def require(condition, rule, message):
    if not condition:
        raise Invalid(rule, message)


def digest(data):
    return hashlib.sha256(data).hexdigest()


class Graph:
    def __init__(self, data):
        require(data.get('version') == 2 and data['qpdf'][0]['jsonversion'] == 2,
                'document', 'qpdf JSON version')
        self.objects = data['qpdf'][1]
        self.pages = [page['object'] for page in data['pages']]
        self.root = self.objects['trailer']['value']['/Root']
        require(len(self.objects) <= 10000, 'document', 'bounded qpdf object count')

    @staticmethod
    def is_reference(value):
        return isinstance(value, str) and REFERENCE.fullmatch(value) is not None

    def entry(self, reference):
        require(self.is_reference(reference) and 'obj:' + reference in self.objects,
                'document', 'live indirect reference')
        return self.objects['obj:' + reference]

    def resolve(self, value):
        seen = set()
        while self.is_reference(value):
            require(value not in seen and len(seen) < 128, 'document', 'bounded acyclic resolution')
            seen.add(value)
            entry = self.entry(value)
            value = entry['stream']['dict'] if 'stream' in entry else entry['value']
        return value

    def dictionary(self, value, rule='document'):
        value = self.resolve(value)
        require(isinstance(value, dict), rule, 'dictionary required')
        return value

    def bytes(self, reference):
        entry = self.entry(reference)
        require('stream' in entry and 'data' in entry['stream'], 'streams', 'inline stream required')
        value = base64.b64decode(entry['stream']['data'], validate=True)
        require(len(value) <= 16 * 1024 * 1024, 'streams', 'bounded stream bytes')
        return value

    def tree(self, value, active=frozenset()):
        """Stable logical content, independent of object numbering/compression."""
        require(len(active) < 128, 'document', 'bounded protected graph')
        if self.is_reference(value):
            require(value not in active, 'document', 'acyclic protected graph')
            entry = self.entry(value)
            if 'stream' in entry:
                return {'dictionary': self.tree({k: v for k, v in entry['stream']['dict'].items()
                                                 if k not in ('/Length', '/Filter', '/DecodeParms')}, active | {value}),
                        'decoded-sha256': digest(self.bytes(value))}
            return self.tree(entry['value'], active | {value})
        if isinstance(value, dict):
            return {key: self.tree(item, active) for key, item in sorted(value.items())}
        if isinstance(value, list):
            return [self.tree(item, active) for item in value]
        if isinstance(value, (int, Decimal)) and not isinstance(value, bool):
            return number(value)
        return value


def number(value):
    return format(Decimal(value).normalize(), 'f')


def text(value):
    require(isinstance(value, str) and value.startswith('u:'), 'semantic', 'Unicode string expected')
    return value[2:]


def revision(source, output):
    require(output.startswith(source), 'incremental-prefix', 'complete Source byte prefix')
    require(len(output) > len(source), 'incremental-revision', 'nonempty appended revision')
    old = re.search(rb'startxref\s+(\d+)\s+%%EOF\s*$', source)
    new = re.search(rb'startxref\s+(\d+)\s+%%EOF\s*$', output)
    require(old is not None and new is not None and len(source) <= int(new[1]) < len(output),
            'incremental-revision', 'new final cross-reference section')
    # The closed fixture/product profile uses classic xref tables, not a claim
    # of complete parser coverage for arbitrary xref/object-stream encodings.
    tail = output[int(new[1]):]
    require(tail.startswith(b'xref') and re.search(rb'\btrailer\s*<<', tail) is not None,
            'incremental-revision', 'classic cross-reference table and trailer')
    previous = re.search(rb'/Prev\s+(\d+)\b', tail)
    require(previous is not None and int(previous[1]) == int(old[1]),
            'incremental-prev', 'Prev identifies the immediate Source revision')
    return ['incremental-prefix', 'incremental-revision', 'incremental-prev']


def standards(graph, length):
    checked = set()

    def check(rule, condition, message):
        require(condition, rule, message)
        checked.add(rule)

    def direct(value):
        check('direct-values', not graph.is_reference(value), 'signature-critical values are direct')
        return value

    catalog = graph.dictionary(graph.root)
    form = graph.dictionary(catalog.get('/AcroForm', {}), 'field-tree')
    fields = graph.resolve(form.get('/Fields', []))
    check('field-tree', isinstance(fields, list), 'AcroForm Fields array')
    signatures, visited = {}, set()

    def visit(reference, parent=None, inherited=None, depth=0):
        check('field-tree', graph.is_reference(reference) and reference not in visited
              and len(visited) < 4096 and depth < 64, 'bounded distinct indirect field tree')
        visited.add(reference)
        field = graph.dictionary(reference, 'field-tree')
        check('field-tree', field.get('/Parent') == parent, 'coherent field Parent')
        kind = graph.resolve(field.get('/FT', inherited))
        check('field-tree', kind is None or isinstance(kind, str) and kind.startswith('/'), 'field type name')
        value = field.get('/V')
        if kind == '/Sig' and graph.resolve(value) is not None:
            check('signature-dictionary', graph.is_reference(value), 'indirect populated signature dictionary')
            signatures[value] = graph.dictionary(value, 'signature-dictionary')
        children = graph.resolve(field.get('/Kids', []))
        check('field-tree', isinstance(children, list), 'field Kids array')
        for child in children:
            visit(child, reference, kind, depth + 1)

    for field in fields:
        visit(field)
    certifications = []
    for reference, signature in signatures.items():
        for key in ('/Type', '/Contents', '/ByteRange', '/Reference'):
            direct(signature.get(key))
        check('signature-dictionary', signature.get('/Type', '/Sig') == '/Sig'
              and isinstance(signature.get('/Contents'), str)
              and signature['/Contents'].startswith(('b:', 'u:')), 'signature type and Contents string')
        ranges = signature.get('/ByteRange')
        check('byte-range', isinstance(ranges, list) and 4 <= len(ranges) <= 256 and len(ranges) % 2 == 0,
              'even bounded ByteRange pairs')
        end = 0
        for offset, count in zip(ranges[::2], ranges[1::2]):
            direct(offset)
            direct(count)
            check('byte-range', type(offset) is int and type(count) is int
                  and offset >= end and count >= 0 and offset + count <= length,
                  'integer nonnegative nonoverlapping in-bounds ByteRange')
            end = offset + count
        references = signature.get('/Reference', [])
        check('signature-reference', isinstance(references, list) and len(references) <= 64,
              'bounded signature reference array')
        certs = []
        for transform in references:
            direct(transform)
            check('signature-reference', isinstance(transform, dict), 'direct transform dictionary')
            method = direct(transform.get('/TransformMethod'))
            check('signature-reference', isinstance(method, str) and method.startswith('/'), 'transform method name')
            if method == '/DocMDP':
                params = direct(transform.get('/TransformParams'))
                check('docmdp-parameters', isinstance(params, dict), 'DocMDP TransformParams dictionary')
                for key in ('/Type', '/P', '/V'):
                    direct(params.get(key))
                check('docmdp-parameters', params.get('/Type', '/TransformParams') == '/TransformParams'
                      and type(params.get('/P', 2)) is int and params.get('/P', 2) in (1, 2, 3)
                      and params.get('/V', '/1.2') == '/1.2', 'DocMDP parameter types and supported ranges')
                certs.append(params.get('/P', 2))
        check('certification-link', len(certs) <= 1, 'one coherent DocMDP transform')
        if certs:
            certifications.append(reference)
    perms = graph.dictionary(catalog.get('/Perms', {}), 'catalog-permissions')
    check('catalog-permissions', len(perms) <= 16 and all(graph.is_reference(v) for v in perms.values()),
          'bounded named permission references')
    check('certification-link', len(certifications) <= 1
          and (perms.get('/DocMDP') == certifications[0] if certifications else '/DocMDP' not in perms),
          'catalog and sole certification signature agree')
    return sorted(checked)


def inventory(graph):
    root = graph.dictionary(graph.root)
    result = {'pages.count': str(len(graph.pages)),
              'keep.token': text(graph.dictionary(root['/FolioKeep'])['/Token']),
              'revision.marker': text(root['/FolioRevision']) if '/FolioRevision' in root else ''}
    appearances, total = {}, 0
    for index, reference in enumerate(graph.pages, 1):
        page = graph.dictionary(reference)
        result['page.' + str(index) + '.box'] = ','.join(number(n) for n in graph.resolve(page['/MediaBox']))
        for item in graph.resolve(page.get('/Annots', [])):
            annotation = graph.dictionary(item)
            if annotation['/Subtype'] == '/Widget':
                continue
            name = text(annotation['/NM'])
            require(name not in appearances, 'semantic', 'distinct Annotation Identifier')
            prefix = 'annotation.' + name + '.'
            result.update({prefix + 'page': str(index), prefix + 'subtype': annotation['/Subtype'][1:],
                           prefix + 'contents': text(annotation['/Contents']),
                           prefix + 'rect': ','.join(number(n) for n in graph.resolve(annotation['/Rect']))})
            appearances[name] = digest(graph.bytes(graph.dictionary(annotation['/AP'])['/N']))
            total += 1
    result['annotations.count'] = str(total)
    return result, appearances


def protected(graph):
    """Everything outside the approved page/annotation edits in this corpus.

    Parent/P links are structural back-edges. Keeping their raw references also
    detects rebinding of the protected Widget, while avoiding recursive cycles.
    """
    root = graph.dictionary(graph.root)
    result = {'keep': graph.tree(root['/FolioKeep']), 'original-pages': [], 'widgets': [],
              'permissions': graph.tree(root.get('/Perms', {}))}
    for reference in graph.pages[:2]:
        page = graph.dictionary(reference)
        result['original-pages'].append(graph.tree({key: value for key, value in page.items()
                                                    if key not in ('/Annots', '/Parent')}))
        for annotation in graph.resolve(page.get('/Annots', [])):
            item = graph.dictionary(annotation)
            if item.get('/Subtype') == '/Widget':
                result['widgets'].append({'page': reference, 'reference': annotation,
                    'dictionary': graph.tree({key: value for key, value in item.items() if key not in ('/P', '/Parent')}),
                    'page-link': item.get('/P'), 'parent-link': item.get('/Parent')})
    if '/AcroForm' in root:
        form = graph.dictionary(root['/AcroForm'])
        result['form'] = graph.tree({key: value for key, value in form.items() if key != '/Fields'})
        # The positive signed corpus has one combined field/Widget; standards
        # predicates separately establish its field-tree and catalog coherence.
        result['field-roots'] = graph.resolve(form['/Fields'])
    return result


def semantic(source, output, product, public):
    observed, appearances = inventory(output)
    expected = product['expected']
    differences = sorted(key for key in expected.keys() | observed.keys() if expected.get(key) != observed.get(key))
    public_differences = sorted(key for key in expected.keys() | public.keys() if expected.get(key) != public.get(key))
    expected_appearances = {key: item['appearance-sha256'] for key, item in product['annotations'].items()}
    before, after = protected(source), protected(output)
    preserved = before == after
    result = {'graph-preserved': preserved, 'inventory-differences': differences,
              'public-differences': public_differences, 'appearances-match': appearances == expected_appearances,
              'result': 'pass' if preserved and not differences and not public_differences
              and appearances == expected_appearances else 'fail'}
    return result, observed, before, after
