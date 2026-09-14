#!/usr/bin/env python3
"""Author original T13 standards defects; this command never qualifies a checker."""
import hashlib
import importlib.util
import json
from pathlib import Path
import posixpath
import re
import sys

SPEC = importlib.util.spec_from_file_location('t13_original_corpus', Path(__file__).with_name('generate-t13-corpus.py'))
AUTHOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUTHOR)
ROOT = Path(__file__).resolve().parents[1]


def change_original_object(source, number, before, after, dictionary_only=False):
    # This is a mutation of the known original serialization, not a PDF parser.
    marker = f'\n{number} 0 obj\n'.encode('ascii')
    if source.count(marker) != 1 or len(before) != len(after):
        raise ValueError('T13 control must select one original object and preserve byte offsets')
    start = source.index(marker) + len(marker)
    end = source.index(b'\nendobj\n', start)
    if dictionary_only:
        end = source.index(b'\nstream\n', start, end)
    object_bytes = source[start:end]
    if object_bytes.count(before) != 1:
        raise ValueError('T13 control does not select one original field')
    return source[:start] + object_bytes.replace(before, after) + source[end:]


def font_finding(rule):
    if rule == 'type3-fontname-consistent':
        return 'FontDescriptor FontName does not match the owning Type3 font Name'
    if rule.endswith('w2-triples'):
        return 'special case not correct: * (ArrayOfCIDGlyphMetricsW2)'
    return 'FontDescriptor FontName does not match the owning font BaseFont'


def original_font_variant(number, before, after, inherited=False):
    # Re-author the 25 known original ASCII/ASCIIHex objects with a new xref.
    source = AUTHOR.embedded_font_kinds(inherited)[0]
    matches = re.findall(rb'(?m)^([0-9]+) 0 obj\n(.*?)\nendobj\n', source, re.DOTALL)
    if [int(key) for key, value in matches] != list(range(1, 26)):
        raise ValueError('The original embedded-font qualification objects changed')
    objects = [value.decode('ascii') for key, value in matches]
    if objects[number - 1].count(before) != 1:
        raise ValueError('The original embedded-font qualification field changed')
    objects[number - 1] = objects[number - 1].replace(before, after)
    if '\nstream\n' in objects[number - 1]:
        header, stream = objects[number - 1].split('\nstream\n', 1)
        if not stream.endswith('endstream') or len(re.findall(r'/Length [0-9]+', header)) != 1:
            raise ValueError('The original font stream serialization changed')
        header = re.sub(r'/Length [0-9]+', '/Length ' + str(len(stream[:-9].encode('ascii'))), header)
        objects[number - 1] = header + '\nstream\n' + stream
    return AUTHOR._pages.pdf(objects)


def type3_variant(width='500', glyph=None, encoding=None):
    # Re-author the nine known original objects; this is not a general PDF reader.
    source = AUTHOR.nested_split_type3(True)[0]
    matches = re.findall(rb'(?m)^([0-9]+) 0 obj\n(.*?)\nendobj\n', source, re.DOTALL)
    if [int(number) for number, value in matches] != list(range(1, 10)):
        raise ValueError('The original Type3 qualification object sequence changed')
    objects = [value.decode('ascii') for number, value in matches]
    if objects[5].count('/Widths [500]') != 1:
        raise ValueError('The original Type3 width declaration changed')
    objects[5] = objects[5].replace('/Widths [500]', '/Widths [' + width + ']')
    if encoding is not None:
        original_encoding = '/Encoding << /Type /Encoding /Differences [65 /A] >>'
        if objects[5].count(original_encoding) != 1:
            raise ValueError('The original Type3 encoding declaration changed')
        objects[5] = objects[5].replace(original_encoding, encoding)
    if glyph is not None:
        objects[6] = AUTHOR._pages.stream(glyph)
    return AUTHOR._pages.pdf(objects)


def generate(output):
    output.mkdir()
    (output / 'fixtures').mkdir()
    direct, _ = AUTHOR.embedded_font_kinds()
    inherited, _ = AUTHOR.embedded_font_kinds(True)
    (output / 'fixtures/positive-direct-fonts.pdf').write_bytes(direct)
    (output / 'fixtures/positive-inherited-fonts.pdf').write_bytes(inherited)
    (output / 'fixtures/positive-pdf20-nested-split-type3.pdf').write_bytes(AUTHOR.nested_split_type3(True)[0])
    named_type3 = AUTHOR.marked_structure()[0]
    (output / 'fixtures/positive-named-type3-fonts.pdf').write_bytes(named_type3)
    (output / 'fixtures/positive-unnamed-type3-fonts.pdf').write_bytes(AUTHOR.marked_structure(False)[0])
    for kind, number, field in (('font-name-only-type3', 17, b'/FontName /F1'),
                                ('descriptor-name-only-type3', 5, b'/Name /F1')):
        data = change_original_object(named_type3, number, field, b' ' * len(field))
        (output / ('fixtures/positive-' + kind + '-fonts.pdf')).write_bytes(data)
    controls = {}

    def add(rule, number, before, after, authority, source=direct):
        data = change_original_object(source, number, before.encode('ascii'), after.encode('ascii'))
        relative = 'fixtures/' + rule + '.pdf'
        (output / relative).write_bytes(data)
        controls[rule] = {'path': relative, 'sha256': hashlib.sha256(data).hexdigest(),
                          'authority': authority, 'qualification': 'unqualified'}

    for kind, number, name in (('type1', 12, 'FolioT13RectangleCFF'), ('truetype', 13, 'FolioT13Rectangle'),
                               ('cid0', 14, 'FolioT13RectangleCID'), ('cid2', 15, 'FolioT13Rectangle')):
        before = '/FontName /' + name
        after = '/FontName /FolioT13Invalid'
        after += ' ' * (len(before) - len(after))
        add(kind + '-fontname-consistent', number, before, after,
            'ISO 32000-1:2008 9.8.1 Table 122: FontName is the same name used for BaseFont in the owning font')
    for kind, number in (('cid0', 18), ('cid2', 21)):
        add(kind + '-w2-triples', number, '/W2 [1 [-1000 250 800]]', '/W2 [1 [-1000 250    ]]',
            'ISO 32000-1:2008 9.7.4.3: array-form W2 supplies complete w1y, v1x, v1y triples')
    add('shared-fontname-consistent', 6, '/BaseFont /FolioT13RectangleCFF', '/BaseFont /FolioT13Invalid     ',
        'ISO 32000-1:2008 9.8.1 Table 122: a shared FontDescriptor must match every owning font BaseFont')
    add('type3-fontname-consistent', 17, '/FontName /F1', '/FontName /F2',
        'ISO 32000-2:2020 9.8.1 Table 120, approved erratum 11: optional Type3 FontName matches a present owning font Name',
        named_type3)
    (output / 'controls.json').write_text(json.dumps(controls, indent=2, sort_keys=True) + '\n')
    rules = ','.join(sorted(controls))
    profile = (f'profile={AUTHOR.PROFILE}\npdf-version=2.0\nrequired-rules={rules}\ncovered-rules={rules}\n')
    for rule, control in sorted(controls.items()):
        finding = font_finding(rule)
        for key, value in (('path', control['path']), ('sha256', control['sha256']), ('finding', finding)):
            profile += f'negative.{rule}.{key}={value}\n'
    (output / 'arlington-fonts.properties').write_text(profile)
    assignments = {}
    prior = json.loads((ROOT / 'capabilities/profiles/T12-standards/assignments.json').read_text())
    for rule, entry in prior.items():
        if not rule.startswith(('catalog-pages', 'catalog-type', 'page-', 'pages-', 'form-', 'resource-xobject-',
                                'stream-', 'trailer-info-', 'annotation-', 'text-name-', 'text-open-', 'text-appearance-')):
            continue
        if rule in ('page-aa-dictionary', 'page-open-action-type', 'page-close-action-type'):
            continue
        assignments[rule] = dict(entry, checker='pdfcpu' if entry['checker'] == 'pdfcpu' else 'arlington-core',
                                 path=posixpath.normpath('../T12-standards/' + entry['path']))
    for group in ('pdfcpu', 'arlington-core'):
        selected = {rule: entry for rule, entry in sorted(assignments.items()) if entry['checker'] == group}
        rules = ','.join(selected)
        text = f'profile={AUTHOR.PROFILE}\npdf-version=2.0\nrequired-rules={rules}\ncovered-rules={rules}\n'
        for rule, entry in selected.items():
            for key in ('path', 'sha256', 'finding'):
                text += f'negative.{rule}.{key}={entry[key]}\n'
        (output / (group + '.properties')).write_text(text)
    for rule, entry in controls.items():
        assignments[rule] = dict(entry, checker='arlington-fonts', finding=font_finding(rule))
    text_controls = {}

    def add_text(rule, source, number, before, after, authority, finding, checker='arlington-text', dictionary_only=False):
        if rule in text_controls or len(after) > len(before):
            raise ValueError('Text control must be unique and preserve its original byte offsets')
        data = change_original_object(source, number, before.encode('ascii'), after.ljust(len(before)).encode('ascii'), dictionary_only)
        path = 'fixtures/' + rule + '.pdf'
        (output / path).write_bytes(data)
        text_controls[rule] = {'checker': checker, 'path': path,
                               'sha256': hashlib.sha256(data).hexdigest(), 'finding': finding, 'authority': authority}

    geometry = AUTHOR.uncertain_geometry()[0]
    for rule, field, requirement in (
            ('page-userunit-number', '/UserUnit/X', 'UserUnit is a number'),
            ('page-userunit-positive', '/UserUnit 0', 'UserUnit is strictly positive')):
        add_text(rule, geometry, 3, '/UserUnit 2', field, 'ISO 32000-1:2008 Table 30: ' + requirement,
                 'Error: wrong type: UserUnit (PageObject)' if rule == 'page-userunit-number' else
                 'Error: wrong value for possible values: UserUnit (PageObject)')
    type3 = AUTHOR.nested_split_type3(True)[0]
    required_fields = {'bbox': '/FontBBox [0 0 400 600]', 'matrix': '/FontMatrix [.001 0 0 .001 0 0]',
                       'charprocs': '/CharProcs << /A 7 0 R >>',
                       'encoding': '/Encoding << /Type /Encoding /Differences [65 /A] >>',
                       'firstchar': '/FirstChar 65', 'lastchar': '/LastChar 65', 'widths': '/Widths [500]'}
    for label, field in required_fields.items():
        key = field.split()[0][1:]
        add_text('type3-' + label + '-required', type3, 6, field, '',
                 'ISO 32000-1:2008 Table 112: ' + key + ' is required in a Type3 font',
                 'non-inheritable required key does not exist: ' + key + ' (FontType3)')
    for label, field, replacement, requirement, finding in (
            ('bbox-numbers', required_fields['bbox'], '/FontBBox [0 0 /X 600]', 'FontBBox contains four numbers',
             'rectangle does not have 4 numeric elements for key FontBBox (FontType3)'),
            ('matrix-numbers', required_fields['matrix'], '/FontMatrix [.001 0 0 /Bad 0 0]', 'FontMatrix contains six numbers',
             'matrix does not have 6 numeric elements for key FontMatrix (FontType3)'),
            ('matrix-count', required_fields['matrix'], '/FontMatrix [.001 0 0 .001 0]', 'FontMatrix contains six numbers',
             'Error: special case not correct: FontMatrix (FontType3)'),
            ('charprocs-dictionary', required_fields['charprocs'], '/CharProcs 42', 'CharProcs is a dictionary',
             'Error: wrong type: CharProcs (FontType3)'),
            ('charproc-stream', required_fields['charprocs'], '/CharProcs << /A 42 >>', 'each CharProcs value is a stream',
             'Error: wrong type for dictionary wildcard for CharProcMap/A'),
            ('encoding-dictionary', required_fields['encoding'], '/Encoding 42', 'Encoding is a dictionary',
             'Error: wrong type: Encoding (FontType3)'),
            ('firstchar-integer', required_fields['firstchar'], '/FirstChar .5', 'FirstChar is an integer',
             'Error: wrong type: FirstChar (FontType3)'),
            ('lastchar-integer', required_fields['lastchar'], '/LastChar .5', 'LastChar is an integer',
             'Error: wrong type: LastChar (FontType3)'),
            ('widths-numbers', required_fields['widths'], '/Widths [/X]', 'Widths contains numbers',
             'Error: wrong type: * (ArrayOfNumbersGeneral)'),
            ('widths-cardinality', required_fields['widths'], '/Widths []', 'Widths has LastChar minus FirstChar plus one entries',
             'Error: special case not correct: Widths (FontType3)'),
            ('resources-dictionary', '/Resources << >>', '/Resources 42', 'Resources is a dictionary',
             'Error: wrong type: Resources (FontType3)')):
        add_text('type3-' + label, type3, 6, field, replacement,
                 'ISO 32000-1:2008 Table 112: ' + requirement, finding)
    add_text('type3-tagged-descriptor-required', named_type3, 5, '/FontDescriptor 17 0 R', '',
             'ISO 32000-1:2008 Table 112: a Type3 font in Tagged PDF requires FontDescriptor',
             'non-inheritable required key does not exist: FontDescriptor (FontType3)')
    for rule, program, requirement, finding in (
            ('type3-glyph-width-agreement', '400 0 0 0 400 600 d1', 'wx agrees with the corresponding Widths entry',
             'Error: Type3 glyph wx does not match its declared Widths entry'),
            ('type3-glyph-zero-wy', '500 1 0 0 400 600 d1', 'wy is zero',
             'Error: Type3 glyph wy must be zero'),
            ('type3-glyph-header-operator', '500 0 0 0 400 600 m ', 'the first glyph operator is d0 or d1',
             'Error: Type3 glyph must begin with d0 or d1'),
            ('type3-glyph-header-operands', '500 0 0 0     600 d1', 'd0 has two and d1 has six numeric operands',
             'Error: Type3 glyph header has invalid operands')):
        add_text(rule, type3, 7, '500 0 0 0 400 600 d1', program,
                 'ISO 32000-1:2008 Table 113: ' + requirement, finding)
    glyphs = {
        'delimiter': type3_variant(glyph='500 0 0 0 400 600 d1/Artifact BMC 0 0 400 600 re f EMC\n'),
        'fractional': type3_variant(width='.1', glyph='.1 0 0 0 400 600 d1 0 0 400 600 re f\n'),
        'comments': type3_variant(glyph='% glyph header\n\x00500\t0 0 0 400 600 d1 0 0 400 600 re f\n'),
        'base-encoding': type3_variant(glyph='400 0 0 0 400 600 d1 0 0 400 600 re f\n',
                                      encoding='/Encoding << /BaseEncoding /WinAnsiEncoding /Differences [] >>'),
        'empty-differences': type3_variant(encoding='/Encoding << /Differences [] >>'),
        'unmapped-glyph': type3_variant(encoding='/Encoding << /Differences [65 /.notdef] >>'),
        'numeric-range': type3_variant(width='10000000000000000000000000000000000000000',
                                      glyph='10000000000000000000000000000000000000000 0 0 0 400 600 d1 0 0 400 600 re f\n'),
    }
    for kind, data in glyphs.items():
        (output / ('fixtures/glyph-boundary-' + kind + '.pdf')).write_bytes(data)
    for rule, data, requirement, finding in (
            ('type3-differences-disjoint', type3_variant(encoding='/Encoding << /Differences [65 /A 65 /A] >>'),
             'ISO 32000-1:2008 9.6.6.1: Differences sequences shall not overlap',
             'Error: Type3 Differences sequences must not overlap'),
            ('type3-glyph-fractional-width-agreement', type3_variant(width='.2', glyph='.1 0 0 0 400 600 d1 0 0 400 600 re f\n'),
             'ISO 32000-1:2008 Table 113 and 7.3.3: glyph wx agrees with Widths at the pinned SDK numeric precision',
             'Error: Type3 glyph wx does not match its declared Widths entry')):
        path = 'fixtures/' + rule + '.pdf'
        (output / path).write_bytes(data)
        text_controls[rule] = {'checker': 'arlington-text', 'path': path, 'sha256': hashlib.sha256(data).hexdigest(),
                               'authority': requirement, 'finding': finding}
    for kind, number, model, name, descriptor in (
            ('type1', 5, 'FontType1', 'FolioT13RectangleCFF', 12),
            ('mmtype1', 6, 'FontMultipleMaster', 'FolioT13RectangleCFF', 12),
            ('truetype', 7, 'FontTrueType', 'FolioT13Rectangle', 13)):
        fields = {'basefont': '/BaseFont /' + name, 'firstchar': '/FirstChar 65',
                  'lastchar': '/LastChar 65', 'widths': '/Widths [500]',
                  'descriptor': '/FontDescriptor ' + str(descriptor) + ' 0 R'}
        authority = 'ISO 32000-1:2008 Table 111 and 9.6.2.3/9.6.3: '
        for label, field in fields.items():
            key = field.split()[0][1:]
            add_text(kind + '-' + label + '-required', direct, number, field, '',
                     authority + key + ' is required for this nonstandard simple font',
                     'non-inheritable required key does not exist: ' + key + ' (' + model + ')')
            rule = kind + '-' + label + '-null'
            data = original_font_variant(number, field, '/' + key + ' null')
            path = 'fixtures/' + rule + '.pdf'
            (output / path).write_bytes(data)
            text_controls[rule] = {'checker': 'arlington-text', 'path': path,
                                   'sha256': hashlib.sha256(data).hexdigest(),
                                   'authority': 'ISO 32000-1:2008 7.3.9 and Table 111: a null dictionary value is equivalent to omission',
                                   'finding': 'non-inheritable required key does not exist: ' + key + ' (' + model + ')'}
        for label, field, replacement, requirement, finding in (
                ('basefont-name', fields['basefont'], '/BaseFont 42', 'BaseFont is a name',
                 'Error: wrong type: BaseFont (' + model + ')'),
                ('firstchar-integer', fields['firstchar'], '/FirstChar .5', 'FirstChar is an integer',
                 'Error: wrong type: FirstChar (' + model + ')'),
                ('lastchar-integer', fields['lastchar'], '/LastChar .5', 'LastChar is an integer',
                 'Error: wrong type: LastChar (' + model + ')'),
                ('widths-array', fields['widths'], '/Widths 42', 'Widths is an array',
                 'Error: wrong type: Widths (' + model + ')'),
                ('widths-numbers', fields['widths'], '/Widths [/X]', 'Widths entries are numbers',
                 'Error: wrong type: * (ArrayOfNumbersGeneral)'),
                ('widths-cardinality', fields['widths'], '/Widths []', 'Widths has LastChar minus FirstChar plus one entries',
                 'Error: special case not correct: Widths (' + model + ')'),
                ('descriptor-dictionary', fields['descriptor'], '/FontDescriptor 42', 'FontDescriptor is a dictionary',
                 'Error: wrong type: FontDescriptor (' + model + ')'),
                ('encoding-type', '/Encoding /WinAnsiEncoding', '/Encoding 42', 'Encoding is a name or dictionary',
                 'Error: wrong type: Encoding (' + model + ')')):
            add_text(kind + '-' + label, direct, number, field, replacement, authority + requirement, finding)
    cid_info = '/CIDSystemInfo << /Registry (Folio) /Ordering (T13) /Supplement 0 >>'
    for kind, number, model, base, descriptor in (
            ('cid0', 18, 'FontCIDType0', 'FolioT13RectangleCID', 14),
            ('cid2', 21, 'FontCIDType2', 'FolioT13Rectangle', 15),
            ('type0', 8, 'FontType0', 'FolioT13RectangleCID-FolioT13-H', None)):
        fields = {'basefont': '/BaseFont /' + base}
        if descriptor is None:
            fields.update(encoding='/Encoding 16 0 R', descendants='/DescendantFonts [18 0 R]')
        else:
            fields.update(ros=cid_info, descriptor='/FontDescriptor ' + str(descriptor) + ' 0 R',
                          registry='/Registry (Folio)', ordering='/Ordering (T13)', supplement='/Supplement 0')
        authority = 'ISO 32000-1:2008 9.7.3/9.7.4/9.7.6, Tables 116/117/121: '
        for label, field in fields.items():
            key = field.split()[0][1:]
            owner = 'CIDSystemInfo' if label in ('registry', 'ordering', 'supplement') else model
            add_text(kind + '-' + label + '-required', direct, number, field, '', authority + key + ' is required',
                     'non-inheritable required key does not exist: ' + key + ' (' + owner + ')', 'arlington-cid')
        variants = [('basefont-name', fields['basefont'], '/BaseFont 42', 'BaseFont', model)]
        if descriptor is None:
            variants.extend((('encoding-type', fields['encoding'], '/Encoding 42', 'Encoding', model),
                             ('descendants-array', fields['descendants'], '/DescendantFonts 42', 'DescendantFonts', model),
                             ('tounicode-stream', '/ToUnicode 20 0 R', '/ToUnicode 42', 'ToUnicode', model)))
            add_text('type0-descendants-nonempty', direct, number, fields['descendants'], '/DescendantFonts []',
                     authority + 'DescendantFonts contains one CIDFont',
                     'Error: array length was too short (needed 1, was 0) for ArrayOfDescendantFonts', 'arlington-cid')
        else:
            variants.extend((('ros-dictionary', cid_info, '/CIDSystemInfo 42', 'CIDSystemInfo', model),
                             ('descriptor-dictionary', fields['descriptor'], '/FontDescriptor 42', 'FontDescriptor', model),
                             ('registry-string', fields['registry'], '/Registry /Bad', 'Registry', 'CIDSystemInfo'),
                             ('ordering-string', fields['ordering'], '/Ordering /Bad', 'Ordering', 'CIDSystemInfo'),
                             ('supplement-integer', fields['supplement'], '/Supplement/X', 'Supplement', 'CIDSystemInfo'),
                             ('dw-number', '/DW 500', '/DW /X', 'DW', model),
                             ('w-array', '/W [1 [500]]', '/W 42', 'W', model),
                             ('dw2-array', '/DW2 [800 -1000]', '/DW2 42', 'DW2', model),
                             ('w2-array', '/W2 [1 [-1000 250 800]]', '/W2 42', 'W2', model)))
            for suffix, before, after, finding in (
                    ('w-numbers', '/W [1 [500]]', '/W [1 [/X]]', 'Error: wrong type: * (ArrayOfNumbersGeneral)'),
                    ('dw2-numbers', '/DW2 [800 -1000]', '/DW2 [/X -1000]', 'Error: wrong type: 0 (ArrayOf_2Numbers)'),
                    ('dw2-cardinality', '/DW2 [800 -1000]', '/DW2 [800]', 'Error: array length was too short (needed 2, was 1) for ArrayOf_2Numbers'),
                    ('w2-numbers', '/W2 [1 [-1000 250 800]]', '/W2 [1 [/X 250 800]]', 'Error: wrong type: * (ArrayOfNumbersGeneral)')):
                add_text(kind + '-' + suffix, direct, number, before, after,
                         authority + 'CID glyph metrics use complete arrays of numeric values', finding, 'arlington-cid')
        for label, before, after, key, owner in variants:
            add_text(kind + '-' + label, direct, number, before, after, authority + key + ' has its specified object type',
                     'Error: wrong type: ' + key + ' (' + owner + ')', 'arlington-cid')
    for label, replacement, finding in (
            ('type', '/CIDToGIDMap 42', 'Error: wrong type: CIDToGIDMap (FontCIDType2)'),
            ('name', '/CIDToGIDMap /Invalid', 'Error: wrong value for possible values: CIDToGIDMap (FontCIDType2)')):
        add_text('cid2-map-' + label, direct, 21, '/CIDToGIDMap /Identity', replacement,
                 'ISO 32000-1:2008 Table 117: CIDToGIDMap is Identity or a stream', finding, 'arlington-cid')
    for kind, number, model, name, flags, source in (
            ('type1', 12, 'FontDescriptorType1', 'FolioT13RectangleCFF', 33, direct),
            ('truetype', 13, 'FontDescriptorTrueType', 'FolioT13Rectangle', 33, direct),
            ('cid0', 14, 'FontDescriptorCIDType0', 'FolioT13RectangleCID', 5, direct),
            ('cid2', 15, 'FontDescriptorCIDType2', 'FolioT13Rectangle', 5, direct),
            ('type3', 17, 'FontDescriptorType3', 'F1', 33, named_type3)):
        fields = {'type': '/Type /FontDescriptor', 'fontname': '/FontName /' + name,
                  'flags': '/Flags ' + str(flags), 'bbox': '/FontBBox [0 0 400 600]',
                  'italicangle': '/ItalicAngle 0', 'ascent': '/Ascent 600', 'descent': '/Descent 0',
                  'capheight': '/CapHeight 600', 'stemv': '/StemV 400'}
        required = ('type', 'flags', 'italicangle') + (() if kind == 'type3' else
                    ('fontname', 'bbox', 'ascent', 'descent', 'stemv'))
        authority = 'ISO 32000-1:2008 9.8.1 Table 122 (PDF 2.0 Table 120): '
        for label in required:
            field = fields[label]
            key = field.split()[0][1:]
            add_text(kind + '-descriptor-' + label + '-required', source, number, field, '',
                     authority + key + ' is required for this font subtype',
                     'non-inheritable required key does not exist: ' + key + ' (' + model + ')', 'arlington-descriptors')
        for label, suffix in (('type', 'name'), ('fontname', 'name'), ('flags', 'integer'),
                              ('italicangle', 'number'), ('ascent', 'number'), ('descent', 'number'),
                              ('capheight', 'number'), ('stemv', 'number')):
            field = fields[label]
            key = field.split()[0][1:]
            after = '/' + key + (' 42' if label in ('type', 'fontname') else '/X')
            add_text(kind + '-descriptor-' + label + '-' + suffix, source, number, field, after,
                     authority + key + ' has its specified object type',
                     'Error: wrong type: ' + key + ' (' + model + ')', 'arlington-descriptors')
        add_text(kind + '-descriptor-bbox-numbers', source, number, fields['bbox'], '/FontBBox [0/X 400 600]',
                 authority + 'FontBBox contains four numeric coordinates',
                 'rectangle does not have 4 numeric elements for key FontBBox (' + model + ')', 'arlington-descriptors')
        add_text(kind + '-descriptor-descent-nonpositive', source, number, fields['descent'], '/Descent 1',
                 authority + 'Descent is nonpositive',
                 'Error: wrong value for possible values: Descent (' + model + ')', 'arlington-descriptors')
        if kind != 'type3':
            key = 'FontFile2' if kind in ('truetype', 'cid2') else 'FontFile3'
            stream = {'type1': 22, 'cid0': 24, 'truetype': 23, 'cid2': 25}[kind]
            add_text(kind + '-fontfile-stream', source, number, '/' + key + ' ' + str(stream) + ' 0 R', '/' + key + ' 42',
                     'ISO 32000-1:2008 9.9 Tables 126/127: an embedded font program is a stream',
                     'Error: wrong type: ' + key + ' (' + model + ')', 'arlington-descriptors')
    for kind, number, subtype, model in (('type1', 22, 'Type1C', 'FontFile3Type1'),
                                         ('cid0', 24, 'CIDFontType0C', 'FontFile3CIDType0')):
        for suffix, after, finding in (
                ('required', '', 'non-inheritable required key does not exist: Subtype (' + model + ')'),
                ('value', '/Subtype /Bad', 'Error: wrong value for possible values: Subtype (' + model + ')')):
            add_text(kind + '-program-subtype-' + suffix, direct, number, '/Subtype /' + subtype, after,
                     'ISO 32000-1:2008 9.9 Tables 126/127: the embedded FontFile3 subtype matches the font program kind',
                     finding, 'arlington-descriptors')
    font_length = len((ROOT / 'capabilities/profiles/T13-fonts/FolioT13Rectangle.ttf').read_bytes())
    add_text('truetype-program-length1-integer', direct, 23, '/Length1 ' + str(font_length), '/Length1/X',
             'ISO 32000-1:2008 Table 127: Length1 is an integer',
             'Error: wrong type: Length1 (FontFile2)', 'arlington-descriptors')
    for kind, number, model, name, info, mode, parent in (
            ('encoding', 17, 'CMapStream', 'FolioT13-V', cid_info, 1, 16),
            ('unicode', 20, 'ToUnicodeCMapStream', 'FolioT13-OverrideUnicode',
             '/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >>', 0, 19)):
        authority = 'ISO 32000-1:2008 9.7.5.3/9.10.3 and PDF 2.0 Table 125a: '
        fields = {'type': '/Type /CMap', 'name': '/CMapName /' + name, 'ros': info}
        if kind == 'encoding':
            for label, field in fields.items():
                key = field.split()[0][1:]
                add_text('cmap-' + kind + '-' + label + '-required', inherited, number, field, '',
                         authority + key + ' is required in an Encoding CMap stream dictionary',
                         'non-inheritable required key does not exist: ' + key + ' (' + model + ')', 'arlington-cmaps', True)
        for label, field, after, key in (
                ('type-name', fields['type'], '/Type 42', 'Type'),
                ('name-name', fields['name'], '/CMapName 42', 'CMapName'),
                ('ros-dictionary', info, '/CIDSystemInfo 42', 'CIDSystemInfo'),
                ('wmode-integer', '/WMode ' + str(mode), '/WMode/X', 'WMode'),
                ('parent-type', '/UseCMap ' + str(parent) + ' 0 R', '/UseCMap 42', 'UseCMap')):
            add_text('cmap-' + kind + '-' + label, inherited, number, field, after,
                     authority + key + ' has its specified object type when present',
                     'Error: wrong type: ' + key + ' (' + model + ')', 'arlington-cmaps', True)
        for label, field, after, key in (
                ('type-value', fields['type'], '/Type /Bad', 'Type'),
                ('wmode-value', '/WMode ' + str(mode), '/WMode 2', 'WMode')):
            add_text('cmap-' + kind + '-' + label, inherited, number, field, after,
                     authority + key + ' has its specified value',
                     'Error: wrong value for possible values: ' + key + ' (' + model + ')', 'arlington-cmaps', True)
    (output / 'text-controls.json').write_text(json.dumps(text_controls, indent=2, sort_keys=True) + '\n')
    for checker in sorted({entry['checker'] for entry in text_controls.values()}):
        selected = {rule: entry for rule, entry in sorted(text_controls.items()) if entry['checker'] == checker}
        text_rules = ','.join(selected)
        text_profile = f'profile={AUTHOR.PROFILE}\npdf-version=2.0\nrequired-rules={text_rules}\ncovered-rules={text_rules}\n'
        for rule, entry in selected.items():
            for key in ('path', 'sha256', 'finding'):
                text_profile += f'negative.{rule}.{key}={entry[key]}\n'
        (output / (checker + '.properties')).write_text(text_profile)
    assignments.update(text_controls)
    (output / 'assignments.json').write_text(json.dumps(assignments, indent=2, sort_keys=True) + '\n')
    (output / 'required-rules.txt').write_text(''.join(rule + '\n' for rule in sorted(assignments)))
    program_controls = {}
    program_sources = {
        'cmap-block-count': '9.7.5.4; Adobe 5014 7.4 counted mapping operators',
        'cmap-program-type': '9.7.5.4/9.10.3; PostScript Language Reference 3rd ed. Table 5.18',
        'cmap-operator-family': '9.7.5.4(c)/9.10.3',
        'cmap-envelope': '9.7.5.4; Adobe 5014 7.3',
        'cmap-codespace': '9.7.5.4; Adobe 5014 5.1 Codespace and 7.3',
        'cmap-codespace-overlap': '9.7.5.4; Adobe 5014 5.1 Codespace',
        'cmap-mapping-domain': '9.7.5.4; Adobe 5014 5.3/7.4',
        'cmap-cid-domain': '9.7.3/9.7.5.4; Adobe 5014 3.2.1/5.3',
        'cmap-unicode': '9.10.3 UTF-16BE destination sequences',
        'cmap-dictionary-program': '9.7.5.3 Table 120',
        'cmap-inheritance-cycle': '9.7.5.4; Adobe 5014 5.4 CMap inheritance',
        'cmap-usecmap-order': '9.7.5.4; Adobe 5014 5.4/7.3',
        'cmap-usecmap-agreement': '9.7.5.4(a)',
        'cmap-usefont-zero': '9.7.5.4(b)/9.10.3',
        'cmap-range-cardinality': '9.10.3 bfrange array cardinality',
        'cmap-inherited-codespace': '9.7.5.4; Adobe 5014 5.4',
        'cmap-declaration-type': '9.7.5.4; PostScript Language Reference 3rd ed. Table 5.18',
        'cmap-font-collection': '9.7.3 character collection compatibility',
        'cmap-font-domain': '9.10.3 codespace consistency with the owning font',
    }
    for name, number, inherited, before, after, rule in (
            ('block-count', 16, False, '1 begincidchar', '2 begincidchar', 'cmap-block-count'),
            ('encoding-type', 16, False, '/CMapType 1 def', '/CMapType 2 def', 'cmap-program-type'),
            ('unicode-type', 19, False, '/CMapType 2 def', '/CMapType 1 def', 'cmap-program-type'),
            ('encoding-bfchar', 16, False, '1 begincidchar\n<0041> 1\nendcidchar',
             '1 beginbfchar\n<0041> 1\nendbfchar', 'cmap-operator-family'),
            ('unicode-cidchar', 19, False, '1 beginbfchar\n<0041> <0041>\nendbfchar',
             '1 begincidchar\n<0041> 1\nendcidchar', 'cmap-operator-family'),
            ('missing-begin', 16, False, 'begincmap\n', '', 'cmap-envelope'),
            ('missing-end', 16, False, 'endcmap\n', '', 'cmap-envelope'),
            ('reversed', 16, False, '<0000> <FFFF>', '<FFFF> <0000>', 'cmap-codespace'),
            ('unequal-length', 16, False, '<0000> <FFFF>', '<00> <FFFF>', 'cmap-codespace'),
            ('reversed-byte', 16, False, '<0000> <FFFF>', '<00FF> <0100>', 'cmap-codespace'),
            ('overlap', 16, False, '1 begincodespacerange\n<0000> <FFFF>',
             '2 begincodespacerange\n<0><F><8><9>', 'cmap-codespace-overlap'),
            ('mapping-code', 16, False, '<0041> 1\nendcidchar', '<41> 1\nendcidchar', 'cmap-mapping-domain'),
            ('negative-cid', 16, False, '<0041> 1\nendcidchar', '<0041> -1\nendcidchar', 'cmap-cid-domain'),
            ('surrogate', 19, False, '<0041> <0041>\nendbfchar', '<0041> <D800>\nendbfchar', 'cmap-unicode'),
            ('name-agreement', 16, False, '/Type /CMap /CMapName /FolioT13-H',
             '/Type /CMap /CMapName /FolioT13-X', 'cmap-dictionary-program'),
            ('ros-agreement', 16, False,
             '/CIDSystemInfo << /Registry (Folio) /Ordering (T13) /Supplement 0 >> /WMode 0',
             '/CIDSystemInfo << /Registry (Other) /Ordering (T13) /Supplement 0 >> /WMode 0', 'cmap-dictionary-program'),
            ('mode-agreement', 16, False, '/WMode 0 /Length', '/WMode 1 /Length', 'cmap-dictionary-program'),
            ('parent-cycle', 17, True, '/UseCMap 16 0 R', '/UseCMap 17 0 R', 'cmap-inheritance-cycle'),
            ('missing-parent-space', 17, True, '/UseCMap 16 0 R', '', 'cmap-codespace'),
            ('usecmap-late', 17, True, '\nendcmap\n',
             '\n0 begincidchar\nendcidchar\n/FolioT13-H usecmap\nendcmap\n', 'cmap-usecmap-order'),
            ('usecmap-name', 17, True, '/CMapName /FolioT13-V def',
             '/UnknownParent usecmap\n/CMapName /FolioT13-V def', 'cmap-usecmap-agreement'),
            ('usecmap-missing-parent', 16, False, '/CMapName /FolioT13-H def',
             '/UnknownParent usecmap\n/CMapName /FolioT13-H def', 'cmap-usecmap-agreement'),
            ('usefont-nonzero', 16, False, '1 begincidchar', '1 usefont\n1 begincidchar', 'cmap-usefont-zero'),
            ('range-cardinality', 19, False, '1 beginbfchar\n<0041> <0041>\nendbfchar',
             '1 beginbfrange\n<0041> <0042> [<0041>]\nendbfrange', 'cmap-range-cardinality'),
            ('inherited-codespace', 17, True, '/WMode 1 def',
             '/WMode 1 def\n1 begincodespacerange\n<0000> <FFFF>\nendcodespacerange', 'cmap-inherited-codespace'),
            ('late-codespace', 16, False, 'endcidchar',
             'endcidchar\n1 begincodespacerange\n<0000> <FFFF>\nendcodespacerange', 'cmap-codespace'),
            ('xuid-string', 16, False, '/CMapType 1 def', '/CMapType 1 def\n/XUID (bad) def', 'cmap-declaration-type'),
            ('version-string', 16, False, '/CMapType 1 def', '/CMapType 1 def\n/CMapVersion (bad) def', 'cmap-declaration-type'),
            ('uid-string', 16, False, '/CMapType 1 def', '/CMapType 1 def\n/UIDOffset (bad) def', 'cmap-declaration-type'),
            ('font-registry', 18, False, '/Registry (Folio)', '/Registry (Other)', 'cmap-font-collection'),
            ('font-ordering', 18, False, '/Ordering (T13)', '/Ordering (XYZ)', 'cmap-font-collection'),
            ('font-simple-domain', 5, False, '/Encoding /WinAnsiEncoding',
             '/Encoding /WinAnsiEncoding /ToUnicode 19 0 R', 'cmap-font-domain'),
            ('font-type0-domain', 19, False, '<0000> <FFFF>\nendcodespacerange\n1 beginbfchar\n<0041> <0041>',
             '<00> <FF>\nendcodespacerange\n1 beginbfchar\n<41> <0041>', 'cmap-font-domain')):
        data = original_font_variant(number, before, after, inherited)
        path = 'fixtures/program-cmap-' + name + '.pdf'
        (output / path).write_bytes(data)
        program_controls['cmap-' + name] = {'path': path, 'sha256': hashlib.sha256(data).hexdigest(),
            'scope': 'cmaps', 'rule': rule, 'authority': 'ISO 32000-1:2008 ' + program_sources[rule]}
    (output / 'program-controls.json').write_text(json.dumps(program_controls, indent=2, sort_keys=True) + '\n')
    for name, number, inherited, before, after in (
            ('usecmap', 17, True, '/CMapName /FolioT13-V def', '/FolioT13-H usecmap\n/CMapName /FolioT13-V def'),
            ('usefont', 16, False, '1 begincidchar', '0 usefont\n1 begincidchar'),
            ('cidrange', 16, False, '1 begincidchar\n<0041> 1\nendcidchar',
             '1 begincidrange\n<0041> <0041> 1\nendcidrange'),
            ('bfrange-string', 19, False, '1 beginbfchar\n<0041> <0041>\nendbfchar',
             '1 beginbfrange\n<0041> <0041> <0041>\nendbfrange'),
            ('bfrange-array', 19, False, '1 beginbfchar\n<0041> <0041>\nendbfchar',
             '1 beginbfrange\n<0041> <0041> [<0041>]\nendbfrange')):
        (output / ('fixtures/program-positive-' + name + '.pdf')).write_bytes(
            original_font_variant(number, before, after, inherited))
    for name, unicode in (('direct-font', 20), ('direct-font-bad', 22)):
        field = ('/F4 << /Type /Font /Subtype /Type0 /BaseFont /FolioT13RectangleCID-FolioT13-H '
                 '/Encoding 16 0 R /DescendantFonts [18 0 R] /ToUnicode ' + str(unicode) + ' 0 R >>')
        (output / ('fixtures/program-boundary-' + name + '.pdf')).write_bytes(
            original_font_variant(3, '/F4 8 0 R', field))
    for count in (256, 257):
        program = ''
        for start in range(0, count, 100):
            size = min(100, count - start)
            program += str(size) + ' begincodespacerange\n'
            program += ''.join(f'<{code:04X}> <{code:04X}>\n' for code in range(start, start + size))
            program += 'endcodespacerange\n'
        (output / ('fixtures/program-boundary-codespaces-' + str(count) + '.pdf')).write_bytes(
            original_font_variant(16, '1 begincodespacerange\n<0000> <FFFF>\nendcodespacerange\n', program))
    for count in (4096, 4352):
        program = '1 begincidrange\n<0000> <00FF> 1\nendcidrange\n' * (count // 256)
        (output / ('fixtures/program-boundary-mappings-' + str(count) + '.pdf')).write_bytes(
            original_font_variant(16, '1 begincidchar\n<0041> 1\nendcidchar\n', program))
    for name, number, before, after in (
            ('simple-two-byte', 5, '/Encoding /WinAnsiEncoding',
             '/Encoding /WinAnsiEncoding /ToUnicode 19 0 R'),
            ('type0-one-byte', 19, '<0000> <FFFF>\nendcodespacerange\n1 beginbfchar\n<0041> <0041>',
             '<00> <FF>\nendcodespacerange\n1 beginbfchar\n<41> <0041>')):
        (output / ('fixtures/program-binding-' + name + '.pdf')).write_bytes(
            original_font_variant(number, before, after))
    parent_mappings = ''.join('1 beginbfrange\n' + f'<{start:04X}> <{start + 255:04X}> <4000>'
                             + '\nendbfrange\n' for start in range(0, 4096, 256))
    inherited_sources = original_font_variant(19, '1 beginbfchar\n<0041> <0041>\nendbfchar\n', parent_mappings, True)
    (output / 'fixtures/program-boundary-inherited-sources-4096.pdf').write_bytes(inherited_sources)
    (output / 'fixtures/program-boundary-inherited-sources-4097.pdf').write_bytes(
            change_original_object(inherited_sources, 20, b'<0041> <005A>', b'<1000> <005A>'))
    font_controls = {}
    for name, number, before, after, authority in (
            ('cff-header', 22, '01000401', '00000401', 'Adobe 5176 section 6 Header'),
            ('cid-cff-header', 24, '01000401', '00000401', 'Adobe 5176 section 6 Header'),
            ('truetype-header', 23, '00010000', '00020000', 'OpenType specification Font File Organization, sfntVersion')):
        data = original_font_variant(number, '\nstream\n' + before, '\nstream\n' + after)
        path = 'fixtures/program-font-' + name + '.pdf'
        (output / path).write_bytes(data)
        font_controls[name] = {'path': path, 'sha256': hashlib.sha256(data).hexdigest(),
                              'scope': 'fonts', 'rule': 'font-program-header',
                              'authority': 'ISO 32000-1:2008 9.9 Tables 126/127; ' + authority}
    fonts = ROOT / 'capabilities/profiles/T13-fonts'
    for name, number, before, after in (
            ('cff-as-cid', 24, 'FolioT13RectangleCID.cff', 'FolioT13Rectangle.cff'),
            ('cid-as-cff', 22, 'FolioT13Rectangle.cff', 'FolioT13RectangleCID.cff')):
        data = original_font_variant(number, '\nstream\n' + (fonts / before).read_bytes().hex(),
                                     '\nstream\n' + (fonts / after).read_bytes().hex())
        path = 'fixtures/program-font-' + name + '.pdf'
        (output / path).write_bytes(data)
        font_controls[name] = {'path': path, 'sha256': hashlib.sha256(data).hexdigest(),
                              'scope': 'fonts', 'rule': 'font-program-kind',
                              'authority': 'ISO 32000-1:2008 9.9 Table 127; Adobe 5176 section 18 CIDFonts'}
    def add_font_control(name, data, rule, authority):
        path = 'fixtures/program-font-' + name + '.pdf'
        (output / path).write_bytes(data)
        font_controls[name] = {'path': path, 'sha256': hashlib.sha256(data).hexdigest(),
                              'scope': 'fonts', 'rule': rule, 'authority': authority}

    truetype = (fonts / 'FolioT13Rectangle.ttf').read_bytes()
    tt_variants = []
    for name, offset, replacement, rule in (
            ('search-range', 6, b'\xff\xff', 'font-program-layout'),
            ('entry-selector', 8, b'\xff\xff', 'font-program-layout'),
            ('range-shift', 10, b'\xff\xff', 'font-program-layout'),
            ('table-tag', 12, b' O/2', 'font-program-layout'),
            ('unaligned-table', 20, (297).to_bytes(4, 'big'), 'font-program-layout'),
            ('table-outside-file', 20, len(truetype).to_bytes(4, 'big'), 'font-program-layout'),
            ('nonzero-padding', 226, b'\x01', 'font-program-layout'),
            ('table-checksum', 16, bytes([truetype[16] ^ 1]), 'font-program-data'),
            ('whole-checksum', 180, bytes([truetype[180] ^ 1]), 'font-program-checksum')):
        tt_variants.append((name, truetype[:offset] + replacement + truetype[offset + len(replacement):], rule))
    tt_variants.append(('table-order', truetype[:12] + truetype[28:44] + truetype[12:28] + truetype[44:], 'font-program-layout'))
    changed = bytearray(truetype)
    changed[466:468] = (399).to_bytes(2, 'big')
    table_sum = sum(int.from_bytes(changed[index:index + 4], 'big') for index in range(460, 484, 4))
    changed[48:52] = (table_sum & 0xffffffff).to_bytes(4, 'big')
    changed[180:184] = b'\x00' * 4
    total = sum(int.from_bytes(changed[index:index + 4], 'big') for index in range(0, len(changed), 4))
    changed[180:184] = ((0xb1b0afba - total) & 0xffffffff).to_bytes(4, 'big')
    tt_variants.append(('glyph-bounds', changed, 'font-program-outline'))
    for name, changed, rule in tt_variants:
        data = original_font_variant(23, '\nstream\n' + truetype.hex(), '\nstream\n' + changed.hex())
        authority = ('OpenType glyf Glyph Header coordinate bounds' if name == 'glyph-bounds'
                     else 'OpenType Font File Organization, Table Directory and Calculating Checksums')
        add_font_control('truetype-' + name, data, rule, 'ISO 32000-1:2008 9.9; ' + authority)
    for kind, number, filename, glyph_first in (
            ('cff', 22, 'FolioT13Rectangle.cff', 169), ('cid-cff', 24, 'FolioT13RectangleCID.cff', 217)):
        font = (fonts / filename).read_bytes()
        variants = []
        for name, offset, value in (('first-offset-zero', 7, 0), ('first-offset-two', 7, 2),
                                    ('glyph-first-offset-two', glyph_first, 2), ('last-offset-outside-file', 8, 255)):
            changed = bytearray(font)
            changed[offset] = value
            variants.append((name, changed, 'font-program-layout', 'Adobe 5176 section 5 INDEX Data'))
        changed = bytearray(font)
        changed[29:31] = b'\x00\x00'
        variants.append(('topdict-count', changed, 'font-program-data', 'Adobe 5176 sections 7/8 Name and Top DICT INDEX'))
        if kind == 'cid-cff':
            for field, opcode in (('fdarray', 36), ('fdselect', 37)):
                marker = bytes([12, opcode])
                if font.count(marker) != 1:
                    raise ValueError('Original CID CFF font dictionary selection changed')
                variants.append(('missing-' + field, font.replace(marker, bytes([12, 31])),
                                 'font-program-data', 'Adobe 5176 section 18 requires FDArray and FDSelect'))
        glyph = bytes.fromhex('f8888b16f824f8ecfc24060e')
        if font.count(glyph) != 1:
            raise ValueError('Original CFF rectangle program changed')
        for name, offset, value in (('missing-moveto', 3, 6), ('wrong-moveto-arity', 10, 22), ('missing-endchar', 11, 139)):
            changed_glyph = bytearray(glyph)
            changed_glyph[offset] = value
            variants.append((name, font.replace(glyph, changed_glyph), 'font-program-outline',
                             'Adobe 5177 sections 3.1 and 4.1/4.2, path organization and operator operands'))
        for name, changed, rule, authority in variants:
            data = original_font_variant(number, '\nstream\n' + font.hex(), '\nstream\n' + changed.hex())
            add_font_control(kind + '-' + name, data, rule, 'ISO 32000-1:2008 9.9; ' + authority)
    for name, before, after, section in (
            ('cff', b'/FolioT13RectangleCFF', b'/FolioT13RectangleBAD', '9.6.2'),
            ('cid-cff', b'/FolioT13RectangleCID', b'/FolioT13RectangleBAD', '9.7.4'),
            ('truetype', b'/FolioT13Rectangle ', b'/FolioT13Rectanglx ', '9.6.3')):
        add_font_control(name + '-program-name', direct.replace(before, after), 'font-program-name',
                         'ISO 32000-1:2008 ' + section + ' and 9.8: embedded program PostScript name')
    add_font_control('truetype-decoded-length', original_font_variant(23, '/Length1 1212', '/Length1 1211'),
                     'font-program-length', 'ISO 32000-1:2008 9.9 Table 127: Length1 is the decoded TrueType length')
    for kind, descriptor in (('cff', 12), ('truetype', 13), ('cid-cff', 14), ('cid-truetype', 15)):
        add_font_control(kind + '-descriptor-bounds',
                         change_original_object(direct, descriptor, b'/FontBBox [0 0 400 600]', b'/FontBBox [0 0 399 600]'),
                         'font-descriptor-bounds', 'ISO 32000-1:2008 9.8.1 Table 122: FontBBox encloses the glyph shapes')
    for units in (0, 15, 16385):
        changed = bytearray(truetype)
        changed[190:192] = units.to_bytes(2, 'big')
        changed[180:184] = b'\x00' * 4
        total = sum(int.from_bytes(changed[index:index + 4], 'big') for index in range(172, 228, 4))
        changed[64:68] = (total & 0xffffffff).to_bytes(4, 'big')
        total = sum(int.from_bytes(changed[index:index + 4], 'big') for index in range(0, len(changed), 4))
        changed[180:184] = ((0xb1b0afba - total) & 0xffffffff).to_bytes(4, 'big')
        add_font_control('truetype-units-per-em-' + str(units),
                         original_font_variant(23, '\nstream\n' + truetype.hex(), '\nstream\n' + changed.hex()),
                         'font-program-units', 'OpenType head table: unitsPerEm ranges from 16 through 16384')
    (output / 'font-program-controls.json').write_text(json.dumps(font_controls, indent=2, sort_keys=True) + '\n')
    metric_cases = []
    for name, number in (('type1', 5), ('mmtype1', 6), ('truetype', 7)):
        metric_cases.append((name + '-widths',
                             change_original_object(direct, number, b'/Widths [500]', b'/Widths [499]'),
                             'ISO 32000-1:2008 9.6.2 Table 111 and 9.6.3: Widths matches embedded glyph advances'))
    for kind, number in (('cid-cff', 18), ('cid-truetype', 21)):
        for form, replacement, before, after in (
                ('array', b'/W [1 [500]]', b'/W [1 [500]]', b'/W [1 [499]]'),
                ('range', b'/W [1 1 500]', b'/W [1 1 500]', b'/W [1 1 499]'),
                ('default', b'/W []', b'/DW 500', b'/DW 499')):
            positive = change_original_object(direct, number, b'/W [1 [500]]', replacement.ljust(len(b'/W [1 [500]]')))
            metric_cases.append((kind + '-widths-' + form, change_original_object(positive, number, before, after),
                                 'ISO 32000-1:2008 9.7.4.3: W and DW match the embedded CID glyph advances'))
    metric_controls = {}
    for name, data, authority in metric_cases:
        path = 'fixtures/metric-' + name + '.pdf'
        (output / path).write_bytes(data)
        metric_controls[name] = {'path': path, 'sha256': hashlib.sha256(data).hexdigest(),
                                 'scope': 'font-metrics', 'rule': 'font-widths', 'authority': authority}
    (output / 'font-metric-controls.json').write_text(json.dumps(metric_controls, indent=2, sort_keys=True) + '\n')
    content_source = AUTHOR.nested_split_type3()[0]
    content_controls = {}
    for name, before, after, section in (
            ('text-matrix-name', b'1 0 0 1 10 20 Tm', b'1 0 0 1 /x 20 Tm', '9.4.2 Text-Positioning Operators'),
            ('text-matrix-arity', b'1 0 0 1 10 20 Tm', b'1 0 0 1    20 Tm', '9.4.2 Text-Positioning Operators'),
            ('font-size-name', b'/F1 10 Tf', b'/F1 /x Tf', '9.3.1 Text State Parameters and Operators'),
            ('show-name', b'(A) Tj', b'/AA Tj', '9.4.3 Text-Showing Operators'),
            ('form-name', b'/Leaf Do', b'(xxx) Do', '8.10 Form XObjects'),
            ('rgb-name', b'0 0 1 rg', b'0/x 1 rg', '8.6.8 Colour Operators')):
        if content_source.count(before) != 1 or len(before) != len(after):
            raise ValueError('The content control must replace one same-length original token sequence')
        data = content_source.replace(before, after)
        path = 'fixtures/content-' + name + '.pdf'
        (output / path).write_bytes(data)
        content_controls[name] = {'path': path, 'sha256': hashlib.sha256(data).hexdigest(),
                                  'scope': 'content', 'rule': 'content-operands',
                                  'authority': 'ISO 32000-1:2008 ' + section}

    def add_content(name, data, rule, section, standard='ISO 32000-1:2008'):
        if name in content_controls:
            raise ValueError('Duplicate original content control')
        path = 'fixtures/content-' + name + '.pdf'
        (output / path).write_bytes(data)
        content_controls[name] = {'path': path, 'sha256': hashlib.sha256(data).hexdigest(),
                                  'scope': 'content', 'rule': rule, 'authority': standard + ' ' + section}

    leaf = b'0 0 1 rg BT /F1 10 Tf (A) Tj ET\n'
    def content_leaf(body):
        if content_source.count(leaf) != 1 or len(body) > len(leaf):
            raise ValueError('The content control must preserve the original leaf stream extent')
        return content_source.replace(leaf, body.ljust(len(leaf)))

    for name, body in (
            ('missing-name-escape', b'BT/F# 10 Tf(A)Tj ET'),
            ('nonhex-name-escape', b'BT/F#0G 10 Tf(A)Tj ET'),
            ('null-name-escape', b'BT/F#00 10 Tf(A)Tj ET'),
            ('dictionary-word', b'/S<</X invalid>>BDC EMC'),
            ('dictionary-name-alias', b'/S<</A 1/#41 2>>BDC EMC')):
        add_content(name, content_leaf(body), 'program-token', '7.3.5-7.3.7 Names, arrays and dictionaries')
    for name, body in (
            ('nested-text', b'BT BT ET ET'), ('unmatched-text-end', b'ET'), ('unterminated-text', b'BT'),
            ('matrix-outside-text', b'1 0 0 1 0 0 Tm'), ('show-outside-text', b'(A)Tj'),
            ('save-inside-text', b'BT q Q ET'), ('restore-inside-text', b'q BT Q ET'),
            ('concatenate-inside-text', b'BT 1 0 0 1 0 0 cm ET'),
            ('graphics-underflow', b'Q q'), ('graphics-unclosed', b'q'),
            ('marked-underflow', b'EMC'), ('marked-unclosed', b'/S<<>>BDC'),
            ('marked-crosses-text', b'/S<<>>BDC BT EMC ET'), ('text-crosses-marked', b'BT/S<<>>BDC ET EMC')):
        add_content(name, content_leaf(body), 'content-state', '8.2 Figure 9, 8.4.2, 9.4.1 and 14.6.1')
    add_content('form-in-text-object', content_source.replace(b'1>] TJ ET /Middle Do\n', b'1>] TJ /Middle Do ET\n'),
                'content-state', '8.2 Figure 9 and 9.4.1 Text objects')
    add_content('form-does-not-pop-caller-graphics', content_leaf(b'Q').replace(b'/Leaf 9 0 R', b'/X    9 0 R')
                .replace(b'/Leaf Do\n', b'q/X Do Q\n'), 'content-state', '8.4.2 and 8.10 Form execution context')
    first = b'1 0 0 rg BT /F1 20 Tf 1 0 0 1 10 20 Tm [<4\n'
    for name, data in (
            ('missing-font-resource', content_leaf(b'BT/F9 10 Tf(A)Tj ET')),
            ('form-used-as-font', content_source.replace(b'/F1 6 0 R', b'/F1 9 0 R')),
            ('no-selected-font', content_source.replace(b'/F1 20 Tf', b' ' * 9)),
            ('font-restored-to-unset', content_source.replace(first, b'q/F1 20 Tf Q BT[<4'.ljust(len(first)))),
            ('missing-form-resource', content_source.replace(b'/Leaf Do', b'/Lost Do'))):
        add_content(name, data, 'content-resource', '7.8.3, 8.4.2, 8.10 and 9.3.1 Font and XObject resources')
    for name, body in (
            ('negative-mcid', b'/S<</MCID -1>>BDC EMC'), ('name-mcid', b'/S<</MCID /X>>BDC EMC'),
            ('real-mcid', b'/S<</MCID 0.0>>BDC EMC'), ('boolean-mcid', b'/S<</MCID true>>BDC EMC'),
            ('numeric-language', b'/S<</Lang 1>>BDC EMC'), ('numeric-replacement', b'/S<</ActualText 1>>BDC EMC'),
            ('name-alternate', b'/S<</Alt /X>>BDC EMC')):
        add_content(name, content_leaf(body), 'content-properties', '14.7.4.2, 14.9.2-14.9.4 Marked-content properties')
    add_content('missing-property-resource', content_leaf(b'/S/P BDC EMC'),
                'content-resource', '14.6.2 Property lists')
    before = b'/Span << /ActualText (Inner) >> BDC'
    add_content('duplicate-mcid', named_type3.replace(before, b'/Span << /MCID 0 >> BDC'.ljust(len(before))),
                'content-properties', '14.7.4.2 Unique marked-content sequence identifiers')
    resources = b'/Matrix [2 0 0 1 5 10] /Resources << /Font << /F1 6 0 R >> >>'
    replacement = b'/Resources<</Font<</F1 6 0 R>>/Properties<</P 42>>>>'
    add_content('named-property-wrong-type', content_leaf(b'/S/P BDC EMC').replace(resources, replacement.ljust(len(resources))),
                'content-resource', '14.6.2 Property lists must be dictionaries')
    glyph = b'500 0 0 0 400 600 d1 0 0 400 600 re f\n'
    for name, body, rule in (
            ('glyph-missing-header', b'0 0 400 600 re f', 'type3-program'),
            ('glyph-repeated-header', b'500 0 d0 500 0 d0', 'type3-program'),
            ('glyph-rectangle-name', b'500 0 0 0 400 600 d1 0/x 400 600 re f', 'type3-program'),
            ('glyph-rectangle-arity', b'500 0 0 0 400 600 d1 0 0 400 re f', 'type3-program'),
            ('glyph-unpainted-path', b'500 0 0 0 400 600 d1 0 0 400 600 re', 'type3-program'),
            ('glyph-outside-d1-bounds', b'500 0 0 0 400 600 d1 1 0 400 600 re f', 'type3-bounds')):
        if len(body) > len(glyph) or content_source.count(glyph) != 1:
            raise ValueError('The Type3 control must preserve its original glyph stream extent')
        add_content(name, content_source.replace(glyph, body.ljust(len(glyph))), rule, '8.5.2-8.5.3 and 9.6.5 Tables 112-113')
    add_content('font-box-too-small', content_source.replace(b'/FontBBox [0 0 400 600]', b'/FontBBox [0 0 399 600]'),
                'type3-bounds', '9.6.5 Table 112 FontBBox encloses glyph ink')
    for name, body in (('graphics-crosses-marked', b'q/S<<>>BDC Q EMC'),
                       ('marked-crosses-graphics', b'/S<<>>BDC q EMC Q')):
        add_content(name, content_leaf(body).replace(b'%PDF-1.7', b'%PDF-2.0'), 'content-state',
                    '7.8.2 All matching operator pairs must nest properly', 'ISO 32000-2:2020 with approved errata')
    for label, key, invocation in (('byte', b'\xc3\xa9', b'#E9'), ('utf8', b'\xe9 ', b'#C3#A9')):
        data = content_source.replace(b'/F1', b'/' + key)
        data = data.replace(leaf.replace(b'/F1', b'/' + key),
                            (b'BT/' + invocation + b' 10 Tf(A)Tj ET').ljust(len(leaf)))
        add_content('different-font-name-' + label, data, 'content-resource', '7.3.5 Names are identified by original bytes')
    middle = b'/Matrix [1 0 0 1 20 30] /Resources << /XObject << /Leaf 9 0 R >> >>'
    caller = b'/Resources<</XObject<</Leaf 9 0 R>>/Font<</F1 6 0 R>>>>'
    nested_missing = content_source.replace(middle, caller.ljust(len(middle)))
    nested_missing = nested_missing.replace(resources, b'/Matrix [2 0 0 1 5 10]'.ljust(len(resources)))
    marked_resources = b'/Resources << /Font << /F1 5 0 R >> >>'
    for name, data in (('nested-form-caller-font', nested_missing.replace(b'%PDF-1.7', b'%PDF-2.0')),
                       ('catalog-pdf2-form-caller-font', named_type3.replace(marked_resources, b' ' * len(marked_resources)))):
        add_content(name, data, 'content-resource', '7.8.3 and Table 93: Form named resources must be local',
                    'ISO 32000-2:2020 with approved errata')
    (output / 'content-controls.json').write_text(json.dumps(content_controls, indent=2, sort_keys=True) + '\n')
    structure_controls = {}
    children = b'/K [0 << /Type /MCR /Pg 3 0 R /Stm 7 0 R /MCID 0 >> << /Type /OBJR /Pg 3 0 R /Obj 12 0 R >> 11 0 R]'
    nums = b'/Nums [0 [10 0 R] 1 [10 0 R] 2 10 0 R]'
    for name, rule, number, before, after in (
            ('root-type', 'structure-hierarchy', 8, b'/Type /StructTreeRoot', b'/Type /Wrong'),
            ('root-content-child', 'structure-hierarchy', 8, b'/K 9 0 R', b'/K 0'),
            ('shared-child', 'structure-hierarchy', 10, children, b'/K [11 0 R 11 0 R]'),
            ('parent-backlink', 'structure-hierarchy', 9, b'/P 8 0 R', b'/P 7 0 R'),
            ('missing-role', 'structure-hierarchy', 11, b'/S /Span', b''),
            ('text-language-name', 'structure-text', 9, b'/Lang (fr)', b'/Lang /X'),
            ('text-actual-name', 'structure-text', 9, b'/ActualText (RootActual)', b'/ActualText /X'),
            ('parent-key-order', 'structure-parent-tree', 13, nums, b'/Nums[1[10 0 R]0[10 0 R]2 10 0 R]'),
            ('parent-key-type', 'structure-parent-tree', 13, nums, b'/Nums[/X[10 0 R]1[10 0 R]2 10 0 R]'),
            ('parent-next-key', 'structure-parent-tree', 8, b'/ParentTreeNextKey 3', b'/ParentTreeNextKey 0'),
            ('parent-nonstructural-value', 'structure-parent-tree', 13, nums, b'/Nums[0[12 0 R]1[10 0 R]2 10 0 R]'),
            ('namespace-unlisted', 'structure-namespace', 9, b'/NS 14 0 R', b'/NS 13 0 R'),
            ('namespace-name-type', 'structure-namespace', 14, b'/NS (urn:folio:t13:roles)', b'/NS 1'),
            ('namespace-mapping-type', 'structure-namespace', 14, b'/Custom [/Intermediate 16 0 R]', b'/Custom 1'),
            ('namespace-direct-target', 'structure-namespace', 14, b'/Custom [/Intermediate 16 0 R]', b'/Custom [/Intermediate<<>>]'),
            ('content-page-key', 'structure-content', 3, b'/StructParents 0', b'/StructParents 9'),
            ('content-form-parent', 'structure-content', 13, nums, b'/Nums[0[10 0 R]1[11 0 R]2 10 0 R]'),
            ('content-object-parent', 'structure-content', 13, nums, b'/Nums[0[10 0 R]1[10 0 R]2 11 0 R]'),
            ('content-missing-page-mcid', 'structure-content', 4, b'/MCID 0', b'/MCID 1'),
            ('content-missing-form-mcid', 'structure-content', 7, b'/MCID 0', b'/MCID 1'),
            ('content-annotation-page', 'structure-content', 12, b'/P 3 0 R', b'/P 2 0 R'),
            ('content-both-parent-keys', 'structure-content', 3, b'/StructParents 0 /Annots [12 0 R]', b'/StructParents 0/StructParent 0'),
            ('content-direct-object', 'structure-content', 10, b'/Obj 12 0 R', b'/Obj <<>>')):
        if len(after) > len(before):
            raise ValueError('The structure control must preserve its original object extent')
        data = change_original_object(named_type3, number, before, after.ljust(len(before)))
        scope = 'structure-hierarchy' if rule == 'structure-text' else 'structure-namespaces' if rule == 'structure-namespace' else rule
        if rule == 'structure-namespace':
            authority = 'ISO 32000-2:2020 14.7.4 and Tables 354-355: namespace declarations and role destinations'
        elif rule == 'structure-parent-tree':
            authority = 'ISO 32000-1:2008 7.9.7 and 14.7.4.4: ordered ParentTree keys, structural values and NextKey'
        else:
            authority = 'ISO 32000-1:2008 14.7.2-14.7.4 Tables 322-326 and 12.5.2 Table 164: structure and content references'
        path = 'fixtures/structure-' + name + '.pdf'
        (output / path).write_bytes(data)
        structure_controls[name] = {'path': path, 'sha256': hashlib.sha256(data).hexdigest(),
                                    'scope': scope, 'rule': rule, 'authority': authority}
    (output / 'structure-controls.json').write_text(json.dumps(structure_controls, indent=2, sort_keys=True) + '\n')
    program_qualification = {}
    for controls in (program_controls, font_controls, metric_controls, content_controls, structure_controls):
        for name, entry in controls.items():
            identifier = entry['scope'] + '.' + name
            if identifier in program_qualification:
                raise ValueError('Program control identifiers must be unique across qualified scopes')
            program_qualification[identifier] = entry
    scopes = sorted({entry['scope'] for entry in program_qualification.values()})
    rules = sorted({entry['scope'] + '.' + entry['rule'] for entry in program_qualification.values()})
    lines = ['profile=T13-text-logical-structure', 'scopes=' + ','.join(scopes),
             'required-rules=' + ','.join(rules), 'controls=' + ','.join(sorted(program_qualification))]
    for identifier, entry in sorted(program_qualification.items()):
        for field in ('scope', 'rule', 'path', 'sha256', 'authority'):
            lines.append('negative.' + identifier + '.' + field + '=' + entry[field].replace('\\', '\\\\'))
    (output / 'program-qualification.properties').write_text('\n'.join(lines) + '\n', encoding='ascii')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Usage: generate-t13-standards.py <fresh-output-directory>')
    generate(Path(sys.argv[1]))
