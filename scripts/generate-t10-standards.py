#!/usr/bin/env python3
"""Generate original illegal T10 structures; this is not a standards checker."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import sys


SPEC = importlib.util.spec_from_file_location('t10_corpus', Path(__file__).with_name('generate-t10-corpus.py'))
CORPUS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CORPUS)


def original():
    return [
        {'Type': '/Catalog', 'Pages': '2 0 R', 'Names': {'Dests': '7 0 R'}},
        {'Type': '/Pages', 'Kids': '[3 0 R]', 'Count': '1'},
        {'Type': '/Page', 'Parent': '2 0 R', 'MediaBox': '[0 0 200 160]', 'CropBox': '[10 20 190 140]',
         'BleedBox': '[12 22 188 138]', 'TrimBox': '[14 24 186 136]', 'ArtBox': '[16 26 184 134]',
         'Rotate': '90', 'Resources': {'XObject': {'Tile': '4 0 R'}}, 'Contents': '[5 0 R]', 'Annots': '[6 0 R]'},
        {'Type': '/XObject', 'Subtype': '/Form', 'FormType': '1', 'BBox': '[0 0 40 40]',
         'Matrix': '[1 0 0 1 0 0]', 'Resources': {}},
        {},
        {'Type': '/Annot', 'Subtype': '/Text', 'Rect': '[30 50 45 65]', 'Contents': '(note)',
         'NM': '(note)', 'F': '2', 'P': '3 0 R'},
        {'Names': '[(shared) [3 0 R /Fit]]'},
    ]


def dictionary(value):
    if not isinstance(value, dict):
        return value
    return '<< ' + ' '.join('/' + key + ' ' + dictionary(item) for key, item in value.items()) + ' >>'


def serialize(objects):
    bodies = [dictionary(value) for value in objects]
    for index, program in [(3, 'q 1 0 0 rg 0 0 40 40 re f Q\n'), (4, 'q /Tile Do Q\n')]:
        bodies[index] = CORPUS.stream(program, bodies[index][3:-3])
    return CORPUS.pdf(bodies)


def controls():
    # Rule, object number, dictionary path, replacement (None removes a key), authority.
    result = [
        ('page-rotate-value', 3, ['Rotate'], '45', 'Table 30: Rotate is a multiple of 90'),
        ('page-rotate-integer', 3, ['Rotate'], '90.5', 'Table 30: Rotate is an integer'),
        ('page-contents-nonempty', 3, ['Contents'], '[]', 'Table 30: a writer shall not create an empty Contents array'),
        ('resource-xobject-map', 3, ['Resources', 'XObject'], '42', 'Table 33: XObject is a dictionary'),
        ('resource-xobject-stream', 3, ['Resources', 'XObject', 'Tile'], '42', '7.8.3 and 8.8.1: XObjects are streams'),
        ('form-type-name', 4, ['Type'], '42', 'Table 95: Type is a name'),
        ('form-type-value', 4, ['Type'], '/Bad', 'Table 95: the optional Type is XObject'),
        ('form-subtype-required', 4, ['Subtype'], None, 'Table 95: Subtype is required'),
        ('form-subtype-name', 4, ['Subtype'], '42', 'Table 95: Subtype is a name'),
        ('form-formtype-integer', 4, ['FormType'], '1.5', 'Table 95: FormType is an integer'),
        ('form-formtype-value', 4, ['FormType'], '2', 'Table 95: FormType is 1'),
        ('form-bbox-required', 4, ['BBox'], None, 'Table 95: BBox is required'),
        ('form-bbox-rectangle', 4, ['BBox'], '[0 0 40]', 'Table 95: BBox has four coordinates'),
        ('form-bbox-numbers', 4, ['BBox'], '[0 0 /Bad 40]', 'Table 95: BBox has numeric coordinates'),
        ('form-matrix-array', 4, ['Matrix'], '42', 'Table 95: Matrix is an array'),
        ('form-matrix-count', 4, ['Matrix'], '[1 0 0 1 0]', 'Table 95: Matrix contains six numbers'),
        ('form-matrix-numbers', 4, ['Matrix'], '[1 0 0 1 /Bad 0]', 'Table 95: Matrix contains numbers'),
        ('form-resources-dictionary', 4, ['Resources'], '42', 'Table 95: Resources is a dictionary'),
        ('page-annots-array', 3, ['Annots'], '42', 'Table 30: Annots is an array'),
        ('page-annots-member', 3, ['Annots'], '[42]', '12.5.2: Annots contains annotation dictionaries'),
        ('annotation-type-name', 6, ['Type'], '42', 'Table 164: Type is a name'),
        ('annotation-type-value', 6, ['Type'], '/Bad', 'Table 164: the optional Type is Annot'),
        ('annotation-subtype-required', 6, ['Subtype'], None, 'Table 164: Subtype is required'),
        ('annotation-subtype-name', 6, ['Subtype'], '42', 'Table 164: Subtype is a name'),
        ('annotation-rect-required', 6, ['Rect'], None, 'Table 164: Rect is required'),
        ('annotation-rect-rectangle', 6, ['Rect'], '[30 50 45]', 'Table 164: Rect has four coordinates'),
        ('annotation-rect-numbers', 6, ['Rect'], '[30 50 /Bad 65]', 'Table 164: Rect has four numbers'),
        ('annotation-contents-string', 6, ['Contents'], '42', 'Table 164: Contents is a text string'),
        ('annotation-name-string', 6, ['NM'], '42', 'Table 164: NM is a text string'),
        ('annotation-flags-integer', 6, ['F'], '2.5', 'Table 164: F is an integer'),
        ('annotation-page-indirect', 6, ['P'], {'Type': '/Page'}, 'Table 164: P is an indirect page reference'),
        ('annotation-page-type', 6, ['P'], '2 0 R', 'Table 164: P refers to a Page object'),
        ('catalog-names-dictionary', 1, ['Names'], '42', 'Table 28: Names is a dictionary'),
        ('names-dests-tree', 1, ['Names', 'Dests'], '42', 'Table 31: Dests is a name tree'),
        ('nametree-names-array', 7, ['Names'], '42', 'Table 36: Names is an array'),
        ('nametree-pair-count', 7, ['Names'], '[(shared)]', 'Table 36: Names contains key-value pairs'),
        ('nametree-key-string', 7, ['Names'], '[/shared [3 0 R /Fit]]', 'Table 36: a name-tree key is a string'),
        ('nametree-key-order', 7, ['Names'], '[(z) [3 0 R /Fit] (a) [3 0 R /Fit]]',
         'Table 36: name-tree keys are in lexical order'),
        ('nametree-key-order-high-bytes', 7, ['Names'], '[<80> [3 0 R /Fit] <7f> [3 0 R /Fit]]',
         '7.9.6: name-tree keys are compared byte by byte'),
        ('nametree-key-unique', 7, ['Names'], '[(shared) [3 0 R /Fit] (shared) [3 0 R /Fit]]',
         '7.9.6: name-tree key ranges do not overlap'),
        ('nametree-key-unique-encoding', 7, ['Names'], '[(shared) [3 0 R /Fit] <736861726564> [3 0 R /Fit]]',
         '7.9.6: key equality compares bytes, not the literal or hexadecimal notation'),
        ('nametree-root-entry', 7, ['Names'], None, '7.9.6: the root contains either Names or Kids'),
        ('destination-value-type', 7, ['Names'], '[(shared) 42]', '12.3.2.3: a named destination is an array or dictionary'),
        ('destination-page-indirect', 7, ['Names'], '[(shared) [0 /Fit]]',
         '12.3.2.2: local destinations use an indirect page reference'),
        ('destination-page-type', 7, ['Names'], '[(shared) [2 0 R /Fit]]',
         '12.3.2.2: local destinations refer to a Page object'),
        ('destination-mode-name', 7, ['Names'], '[(shared) [3 0 R 42]]', 'Table 151: destination mode is a name'),
        ('destination-mode-value', 7, ['Names'], '[(shared) [3 0 R /Bad]]', 'Table 151: destination mode is one of the defined views'),
        ('destination-fit-count', 7, ['Names'], '[(shared) [3 0 R /Fit 0]]',
         'Table 151: a Fit destination contains only a page and the Fit name'),
    ]
    for box in ['CropBox', 'BleedBox', 'TrimBox', 'ArtBox']:
        result.extend([
            ('page-' + box.lower() + '-rectangle', 3, [box], '[0 0 10]', 'Table 30: ' + box + ' has four coordinates'),
            ('page-' + box.lower() + '-numbers', 3, [box], '[0 0 /Bad 10]', 'Table 30: ' + box + ' has numeric coordinates'),
        ])
    return result


def generate(target):
    target.mkdir()
    (target / 'fixtures').mkdir()
    base = original()
    (target / 'fixtures/valid.pdf').write_bytes(serialize(base))
    positive = copy.deepcopy(base)
    positive[6]['Names'] = '[(a) [3 0 R /Fit] (aa) [3 0 R /Fit] <7f> [3 0 R /Fit] <80> [3 0 R /Fit] <ff> [3 0 R /Fit]]'
    (target / 'fixtures/positive-name-byte-order.pdf').write_bytes(serialize(positive))
    catalog = {}
    for rule, number, path, replacement, authority in controls():
        objects = copy.deepcopy(base)
        value = objects[number - 1]
        for key in path[:-1]:
            value = value[key]
        if replacement is None:
            del value[path[-1]]
        else:
            value[path[-1]] = replacement
        content = serialize(objects)
        relative = 'fixtures/' + rule + '.pdf'
        (target / relative).write_bytes(content)
        catalog[rule] = {'path': relative, 'sha256': hashlib.sha256(content).hexdigest(),
                         'authority': 'ISO 32000-1:2008 ' + authority}
    (target / 'controls.json').write_text(json.dumps(catalog, indent=2, sort_keys=True) + '\n', encoding='utf-8')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Usage: generate-t10-standards.py <fresh-output-directory>')
    generate(Path(sys.argv[1]))
