#!/usr/bin/env python3
"""Author original extraction Sources, literal observations and pixel grids.

The worked examples are fixed in docs/t13-certification.md. No Folio output,
PDF parser or renderer supplies expected values. Original T10/T11 PDF, PNG
and properties serializers are reused. Apache-2.0 project fixture data.
"""
import hashlib
import importlib.util
import json
from pathlib import Path
import sys


def authoring_module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


_pages = authoring_module('t13_page_authoring', 'generate-t10-corpus.py')
_metadata = authoring_module('t13_metadata_authoring', 'generate-t11-corpus.py')
PROFILE = 'T13-text-logical-structure'


def nested_split_type3(pdf20=False):
    contents = ['1 0 0 rg BT /F1 20 Tf 1 0 0 1 10 20 Tm [<4\n',
                '1>] TJ ET /Middle Do\n']
    glyph = '500 0 0 0 400 600 d1 0 0 400 600 re f\n'
    middle = '/Leaf Do\n'
    leaf = '0 0 1 rg BT /F1 10 Tf (A) Tj ET\n'
    data = _pages.pdf([
        '<< /Type /Catalog ' + ('/Version /2.0 ' if pdf20 else '') + '/Pages 2 0 R >>',
        '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
        '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 120 100] /CropBox [0 0 120 100] /Rotate 0 '
        '/Resources << /Font << /F1 6 0 R >> /XObject << /Middle 8 0 R >> >> /Contents [4 0 R 5 0 R] >>',
        _pages.stream(contents[0]),
        _pages.stream(contents[1]),
        '<< /Type /Font /Subtype /Type3 /Name /F1 /FontBBox [0 0 400 600] '
        '/FontMatrix [.001 0 0 .001 0 0] /FirstChar 65 /LastChar 65 /Widths [500] '
        '/Encoding << /Type /Encoding /Differences [65 /A] >> /CharProcs << /A 7 0 R >> /Resources << >> >>',
        _pages.stream(glyph),
        _pages.stream(middle, '/Type /XObject /Subtype /Form /BBox [0 0 120 100] '
                      '/Matrix [1 0 0 1 20 30] /Resources << /XObject << /Leaf 9 0 R >> >>'),
        _pages.stream(leaf, '/Type /XObject /Subtype /Form /BBox [0 0 50 50] '
                      '/Matrix [2 0 0 1 5 10] /Resources << /Font << /F1 6 0 R >> >>'),
    ])
    # These literal observations come from the worked example, not a PDF interpreter.
    extraction = {'pages': [{'number': 1, 'rotation': 0, 'user-unit': 1, 'crop-box': [0, 0, 120, 100],
                            'text': 'AA', 'marked-content': [], 'items': [
                                {'index': 1, 'source': '41', 'confidence': 'INFERRED', 'unicode': 'A',
                                 'explicit': None, 'inferred': 'A', 'contribution': 'A', 'marked-content': [],
                                 'matrix': [20, 0, 0, 20, 10, 20], 'advance': [10, 0]},
                                {'index': 2, 'source': '41', 'confidence': 'INFERRED', 'unicode': 'A',
                                 'explicit': None, 'inferred': 'A', 'contribution': 'A', 'marked-content': [],
                                 'matrix': [20, 0, 0, 10, 25, 40], 'advance': [10, 0]},
                            ]}], 'roots': [], 'diagnostics': []}
    visual = {'crop-box': [0, 0, 120, 100], 'rotation': 0,
              'paints': [[10, 20, 8, 12, 255, 0, 0], [25, 40, 8, 6, 0, 0, 255]]}
    return data, {'source': 'nested-split-type3', 'extraction': extraction, 'visual': [visual],
                  'coverage': ['split-contents-token', 'nested-form-order', 'declared-type3-metrics',
                               'form-text-matrices', 'declared-encoding-inference']}


def marked_structure(named_font=True):
    content = ('/Span << /MCID 0 /ActualText (Outer) /Alt (PageAlt) >> BDC\n'
               'BT /F1 10 Tf 1 0 0 1 10 10 Tm (A) Tj ET\n'
               '/Span << /ActualText (Inner) >> BDC\n'
               'BT /F1 10 Tf 1 0 0 1 20 10 Tm (A) Tj ET EMC EMC /Fm Do\n')
    form = ('/Span << /MCID 0 /Lang (de) /Alt (FormAlt) >> BDC\n'
            '/Span << /ActualText (Form) >> BDC BT /F1 10 Tf (A) Tj ET EMC EMC\n')
    data = _pages.pdf([
        '<< /Type /Catalog /Version /2.0 /Pages 2 0 R /Lang (en) '
        '/MarkInfo << /Marked true >> /StructTreeRoot 8 0 R >>',
        '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
        '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 120 100] /CropBox [0 0 120 100] /Rotate 0 '
        '/Resources << /Font << /F1 5 0 R >> /XObject << /Fm 7 0 R >> >> '
        '/Contents 4 0 R /StructParents 0 /Annots [12 0 R] >>',
        _pages.stream(content),
        '<< /Type /Font /Subtype /Type3 ' + ('/Name /F1 ' if named_font else '')
        + '/FontBBox [0 0 400 600] /FontMatrix [.001 0 0 .001 0 0] '
        '/FirstChar 65 /LastChar 65 /Widths [500] /Encoding << /Type /Encoding /Differences [65 /A] >> '
        '/CharProcs << /A 6 0 R >> /Resources << >> /FontDescriptor 17 0 R >>',
        _pages.stream('500 0 0 0 400 600 d1 0 0 400 600 re f\n'),
        _pages.stream(form, '/Type /XObject /Subtype /Form /BBox [0 0 120 100] /Matrix [1 0 0 1 20 30] '
                      '/Resources << /Font << /F1 5 0 R >> >> /StructParents 1'),
        '<< /Type /StructTreeRoot /K 9 0 R /ParentTree 13 0 R /ParentTreeNextKey 3 '
        '/Namespaces [14 0 R 15 0 R 16 0 R] >>',
        '<< /Type /StructElem /S /Custom /NS 14 0 R /P 8 0 R /Pg 3 0 R /Lang (fr) '
        '/Alt (RootAlt) /ActualText (RootActual) /K [10 0 R] >>',
        '<< /Type /StructElem /S /P /NS 15 0 R /P 9 0 R /Pg 3 0 R '
        '/K [0 << /Type /MCR /Pg 3 0 R /Stm 7 0 R /MCID 0 >> << /Type /OBJR /Pg 3 0 R /Obj 12 0 R >> 11 0 R] >>',
        '<< /Type /StructElem /S /Span /NS 15 0 R /P 10 0 R /Pg 3 0 R /Lang (ja) /ActualText () >>',
        '<< /Type /Annot /Subtype /Text /P 3 0 R /Rect [0 0 0 0] /F 2 /Contents (Linked object) /StructParent 2 >>',
        '<< /Nums [0 [10 0 R] 1 [10 0 R] 2 10 0 R] >>',
        '<< /Type /Namespace /NS (urn:folio:t13:roles) /RoleMapNS << /Custom [/Intermediate 16 0 R] >> >>',
        '<< /Type /Namespace /NS (http://iso.org/pdf2/ssn) >>',
        '<< /Type /Namespace /NS (urn:folio:t13:bridge) /RoleMapNS << /Intermediate [/Document 15 0 R] >> >>',
        '<< /Type /FontDescriptor ' + ('/FontName /F1 ' if named_font else '')
        + '/Flags 33 /FontBBox [0 0 400 600] '
        '/ItalicAngle 0 /Ascent 600 /Descent 0 /CapHeight 600 /StemV 400 >>',
    ])
    marked = [
        {'id': 1, 'stream': 0, 'tag': 'Span', 'mcid': 0, 'parent': None, 'language': None,
         'alternate': 'PageAlt', 'actual': 'Outer', 'items': [1, 2]},
        {'id': 2, 'stream': 0, 'tag': 'Span', 'mcid': None, 'parent': 1, 'language': None,
         'alternate': None, 'actual': 'Inner', 'items': [2]},
        {'id': 3, 'stream': 1, 'tag': 'Span', 'mcid': 0, 'parent': None, 'language': 'de',
         'alternate': 'FormAlt', 'actual': None, 'items': [3]},
        {'id': 4, 'stream': 1, 'tag': 'Span', 'mcid': None, 'parent': 3, 'language': None,
         'alternate': None, 'actual': 'Form', 'items': [3]},
    ]
    for sequence in marked:
        for key in ('mcid', 'parent', 'language', 'alternate', 'actual'):
            sequence[key + '-present'] = sequence[key] is not None
    leaves = [
        {'kind': 'MARKED_CONTENT', 'marked-content': {'page': 1, 'mcid': 0, 'stream': 0, 'sequence': 1,
                                                   'sequence-present': True, 'owner': None, 'owner-present': False}},
        {'kind': 'MARKED_CONTENT', 'marked-content': {'page': 1, 'mcid': 0, 'stream': 1, 'sequence': 3,
                                                   'sequence-present': True, 'owner': None, 'owner-present': False}},
        {'kind': 'OBJECT', 'object-reference': {'page': 1, 'reference': 3, 'subtype': 'Text'}},
    ]
    paragraph = {'id': 2, 'role': 'P', 'resolved-role': 'P', 'role-resolution': 'STANDARD',
                 'namespace': 'http://iso.org/pdf2/ssn',
                 'namespace-reference': 2, 'resolved-namespace': 'http://iso.org/pdf2/ssn',
                 'declared-language': None, 'effective-language': 'fr', 'language-source': 'ANCESTOR',
                 'alternate': None, 'actual': None, 'children': leaves}
    span = {'id': 3, 'role': 'Span', 'resolved-role': 'Span', 'role-resolution': 'STANDARD',
            'namespace': 'http://iso.org/pdf2/ssn', 'namespace-reference': 2,
            'resolved-namespace': 'http://iso.org/pdf2/ssn', 'declared-language': 'ja',
            'effective-language': 'ja', 'language-source': 'SELF', 'alternate': None, 'actual': '', 'children': []}
    root = {'id': 1, 'role': 'Custom', 'resolved-role': 'Document', 'role-resolution': 'ROLE_MAP',
            'namespace': 'urn:folio:t13:roles', 'namespace-reference': 1, 'resolved-namespace': 'http://iso.org/pdf2/ssn',
            'declared-language': 'fr', 'effective-language': 'fr', 'language-source': 'SELF',
            'alternate': 'RootAlt', 'actual': 'RootActual',
            'children': [{'kind': 'ELEMENT', 'element': paragraph}]}
    paragraph['children'].append({'kind': 'ELEMENT', 'element': span})
    for element in (root, paragraph, span):
        for key in ('resolved-role', 'namespace', 'namespace-reference', 'resolved-namespace',
                    'declared-language', 'effective-language', 'alternate', 'actual'):
            element[key + '-present'] = element[key] is not None
    items = [{'index': index, 'source': '41', 'confidence': 'INFERRED', 'unicode': 'A', 'explicit': None,
              'inferred': 'A', 'contribution': '', 'marked-content': sequences,
              'matrix': matrix, 'advance': [5, 0]}
             for index, sequences, matrix in [(1, [1], [10, 0, 0, 10, 10, 10]),
                                               (2, [1, 2], [10, 0, 0, 10, 20, 10]),
                                               (3, [3, 4], [10, 0, 0, 10, 20, 30])]]
    extraction = {'pages': [{'number': 1, 'rotation': 0, 'user-unit': 1, 'crop-box': [0, 0, 120, 100],
                            'text': 'OuterForm', 'marked-content': marked, 'items': items}],
                  'roots': [root], 'diagnostics': []}
    visual = {'crop-box': [0, 0, 120, 100], 'rotation': 0,
              'paints': [[10, 10, 4, 6, 0, 0, 0], [20, 10, 4, 6, 0, 0, 0], [20, 30, 4, 6, 0, 0, 0]]}
    return data, {'source': 'marked-structure', 'extraction': extraction, 'visual': [visual],
                  'coverage': ['outer-actual-text', 'separate-alternate-text', 'nested-page-form-marked-content',
                               'page-form-mcid-scope', 'ordered-mcr-objr', 'cross-namespace-role-map',
                               'direct-ancestor-language', 'empty-versus-absent-replacement']}


def embedded_font_kinds(inherited=False):
    font_directory = Path(__file__).resolve().parents[1] / 'capabilities/profiles/T13-fonts'
    pins = {'FolioT13Rectangle.ttf': '148b880e4f5ce722430de5ed3ecf893d629ae1ce5c68de0b30219b5eeab413ab',
            'FolioT13RectangleCID.ttf': 'bc846fd8f382e7bd59f2742e81fe33ce5905f7f365026abfce261cd12f548651',
            'FolioT13Rectangle.cff': '8cf18cd4e2c873af964be2ebff686deadb8db10a96343fd86636f7e460779bc2',
            'FolioT13RectangleCID.cff': 'e13bc2975a68ccdbe34088f8b9f7e400b2f69aec8295dc728389a0b3d12c5322'}
    fonts = {name: (font_directory / name).read_bytes() for name in pins}
    if any(hashlib.sha256(fonts[name]).hexdigest() != digest for name, digest in pins.items()):
        raise ValueError('Original T13 font identity mismatch')
    cid_info = '<< /Registry (Folio) /Ordering (T13) /Supplement 0 >>'
    unicode_info = '<< /Registry (Adobe) /Ordering (UCS) /Supplement 0 >>'

    def cmap(name, kind, mode, mapping, parent=None):
        info = cid_info if kind == 1 else unicode_info
        header = f'/Type /CMap /CMapName /{name} /CIDSystemInfo {info} /WMode {mode}'
        if parent is not None:
            header += f' /UseCMap {parent} 0 R'
        program = ('/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n'
                   f'/CIDSystemInfo {info} def\n/CMapName /{name} def\n/CMapType {kind} def\n/WMode {mode} def\n')
        if parent is None:
            program += '1 begincodespacerange\n<0000> <FFFF>\nendcodespacerange\n'
        program += mapping + 'endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n'
        return _pages.stream(program, header)

    def descriptor(name, flags, entry):
        return (f'<< /Type /FontDescriptor /FontName /{name} /Flags {flags} /FontBBox [0 0 400 600] '
                f'/ItalicAngle 0 /Ascent 600 /Descent 0 /CapHeight 600 /StemV 400 {entry} >>')

    content = ('BT /F1 20 Tf 1 0 0 1 10 20 Tm (A) Tj ET\n'
               'BT /F2 20 Tf 1 0 0 1 30 20 Tm (A) Tj ET\n'
               'BT /F3 20 Tf 1 0 0 1 50 20 Tm (A) Tj ET\n'
               'BT /F4 20 Tf 1 0 0 1 10 60 Tm <0041> Tj ET\n'
               'BT /F5 20 Tf 1 0 0 1 40 80 Tm <0041> Tj ET\n'
               'BT /F6 20 Tf 1 0 0 1 60 60 Tm <0041> Tj ET\n'
               'BT /F7 20 Tf 1 0 0 1 90 80 Tm <0041> Tj ET\n')
    data = _pages.pdf([
        '<< /Type /Catalog /Version /2.0 /Pages 2 0 R >>',
        '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
        '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 120 100] /CropBox [0 0 120 100] /Rotate 0 '
        '/Resources << /Font << /F1 5 0 R /F2 6 0 R /F3 7 0 R /F4 8 0 R /F5 9 0 R /F6 10 0 R /F7 11 0 R >> >> '
        '/Contents 4 0 R >>',
        _pages.stream(content),
        '<< /Type /Font /Subtype /Type1 /BaseFont /FolioT13RectangleCFF /Encoding /WinAnsiEncoding '
        '/FirstChar 65 /LastChar 65 /Widths [500] /FontDescriptor 12 0 R >>',
        '<< /Type /Font /Subtype /MMType1 /BaseFont /FolioT13RectangleCFF /Encoding /WinAnsiEncoding '
        '/FirstChar 65 /LastChar 65 /Widths [500] /FontDescriptor 12 0 R >>',
        '<< /Type /Font /Subtype /TrueType /BaseFont /FolioT13Rectangle /Encoding /WinAnsiEncoding '
        '/FirstChar 65 /LastChar 65 /Widths [500] /FontDescriptor 13 0 R >>',
        '<< /Type /Font /Subtype /Type0 /BaseFont /FolioT13RectangleCID-FolioT13-H '
        '/Encoding 16 0 R /DescendantFonts [18 0 R] /ToUnicode 20 0 R >>',
        '<< /Type /Font /Subtype /Type0 /BaseFont /FolioT13RectangleCID-FolioT13-V '
        '/Encoding 17 0 R /DescendantFonts [18 0 R] /ToUnicode 19 0 R >>',
        '<< /Type /Font /Subtype /Type0 /BaseFont /FolioT13Rectangle '
        '/Encoding 16 0 R /DescendantFonts [21 0 R] /ToUnicode 20 0 R >>',
        '<< /Type /Font /Subtype /Type0 /BaseFont /FolioT13Rectangle '
        '/Encoding 17 0 R /DescendantFonts [21 0 R] /ToUnicode 19 0 R >>',
        descriptor('FolioT13RectangleCFF', 33, '/FontFile3 22 0 R'),
        descriptor('FolioT13Rectangle', 33, '/FontFile2 23 0 R'),
        descriptor('FolioT13RectangleCID', 5, '/FontFile3 24 0 R'),
        descriptor('FolioT13Rectangle', 5, '/FontFile2 25 0 R'),
        cmap('FolioT13-H', 1, 0, '1 begincidchar\n<0041> 1\nendcidchar\n'),
        cmap('FolioT13-V', 1, 1, '' if inherited else '1 begincidchar\n<0041> 1\nendcidchar\n',
             16 if inherited else None),
        '<< /Type /Font /Subtype /CIDFontType0 /BaseFont /FolioT13RectangleCID /CIDSystemInfo ' + cid_info + ' '
        '/DW 500 /W [1 [500]] /DW2 [800 -1000] /W2 [1 [-1000 250 800]] /FontDescriptor 14 0 R >>',
        cmap('FolioT13-BaseUnicode', 2, 0, '1 beginbfchar\n<0041> <0041>\nendbfchar\n'),
        cmap('FolioT13-OverrideUnicode', 2, 0, '1 beginbfchar\n<0041> <005A>\nendbfchar\n',
             19 if inherited else None),
        '<< /Type /Font /Subtype /CIDFontType2 /BaseFont /FolioT13Rectangle /CIDSystemInfo ' + cid_info + ' '
        '/DW 500 /W [1 [500]] /DW2 [800 -1000] /W2 [1 [-1000 250 800]] /CIDToGIDMap /Identity /FontDescriptor 15 0 R >>',
        _pages.stream(fonts['FolioT13Rectangle.cff'].hex() + '>\n', '/Subtype /Type1C /Filter /ASCIIHexDecode'),
        _pages.stream(fonts['FolioT13Rectangle.ttf'].hex() + '>\n',
                      '/Length1 ' + str(len(fonts['FolioT13Rectangle.ttf'])) + ' /Filter /ASCIIHexDecode'),
        _pages.stream(fonts['FolioT13RectangleCID.cff'].hex() + '>\n', '/Subtype /CIDFontType0C /Filter /ASCIIHexDecode'),
        _pages.stream(fonts['FolioT13RectangleCID.ttf'].hex() + '>\n',
                      '/Length1 ' + str(len(fonts['FolioT13RectangleCID.ttf'])) + ' /Filter /ASCIIHexDecode'),
    ])
    # Explicit W2 origin (250,800) at size 20 shifts the two vertical glyph
    # origins by (-5,-16); displacement is (0,-20). Font programs supply ink,
    # never Unicode inference for CID glyphs. The local ToUnicode overrides
    # map the horizontal source 0041 to Z; the inherited base maps it to A.
    items = []
    for index, position, unicode, advance in (
            (1, [10, 20], 'A', [10, 0]), (2, [30, 20], 'A', [10, 0]), (3, [50, 20], 'A', [10, 0]),
            (4, [10, 60], 'Z', [10, 0]), (5, [35, 64], 'A', [0, -20]),
            (6, [60, 60], 'Z', [10, 0]), (7, [85, 64], 'A', [0, -20])):
        simple = index <= 3
        items.append({'index': index, 'source': '41' if simple else '0041',
                      'confidence': 'INFERRED' if simple else 'EXPLICIT', 'unicode': unicode,
                      'explicit': None if simple else unicode, 'inferred': unicode if simple else None,
                      'contribution': unicode, 'marked-content': [], 'matrix': [20, 0, 0, 20] + position,
                      'advance': advance})
    extraction = {'pages': [{'number': 1, 'rotation': 0, 'user-unit': 1, 'crop-box': [0, 0, 120, 100],
                            'text': 'AAAZAZA', 'marked-content': [], 'items': items}], 'roots': [], 'diagnostics': []}
    visual = {'crop-box': [0, 0, 120, 100], 'rotation': 0, 'paints': [
        [10, 20, 8, 12, 0, 0, 0], [30, 20, 8, 12, 0, 0, 0], [50, 20, 8, 12, 0, 0, 0],
        [10, 60, 8, 12, 0, 0, 0], [35, 64, 8, 12, 0, 0, 0], [60, 60, 8, 12, 0, 0, 0], [85, 64, 8, 12, 0, 0, 0]]}
    return data, {'source': 'embedded-font-inheritance' if inherited else 'embedded-font-kinds',
                  'required-chains': ['syntax', 'standards', 'semantic'] + ([] if inherited else ['visual']), 'extraction': extraction, 'visual': [visual], 'font-sha256': pins,
                  'coverage': ['embedded-type1-cff', 'mmtype1-snapshot', 'embedded-truetype',
                               'embedded-cid0-horizontal-vertical', 'embedded-cid2-horizontal-vertical',
                               'declared-vertical-origin'] + (['encoding-cmap-inheritance', 'tounicode-inheritance-override']
                               if inherited else ['direct-encoding-and-tounicode-cmaps'])}


def uncertain_geometry():
    def font(first, widths, differences, charprocs, unicode=None):
        return ('<< /Type /Font /Subtype /Type3 /FontBBox [0 0 400 600] /FontMatrix [.001 0 0 .001 0 0] '
                f'/FirstChar {first} /LastChar {first + len(widths) - 1} /Widths [' + ' '.join(map(str, widths)) + '] '
                f'/Encoding << /Type /Encoding /Differences [{differences}] >> /CharProcs << {charprocs} >> '
                '/Resources << >>' + (f' /ToUnicode {unicode} 0 R' if unicode is not None else '') + ' >>')

    def unicode(destination):
        return _pages.stream('/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n'
                             '/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n'
                             '/CMapName /FolioT13-Uncertainty def\n/CMapType 2 def\n'
                             '1 begincodespacerange\n<00> <FF>\nendcodespacerange\n'
                             f'1 beginbfchar\n<41> <{destination}>\nendbfchar\n'
                             'endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n')

    data = _pages.pdf([
        '<< /Type /Catalog /Version /2.0 /Pages 2 0 R >>',
        '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
        '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 140 140] /CropBox [10 20 130 120] /Rotate 90 /UserUnit 2 '
        '/Resources << /Font << /F1 5 0 R /F2 6 0 R /F3 7 0 R /F4 8 0 R >> >> /Contents 4 0 R >>',
        _pages.stream('q 2 0 0 3 5 7 cm BT /F1 10 Tf 50 Tz 2 Ts 2 Tc 4 Tw '
                      '1 0 0 1 10 20 Tm [(A) 100 ( A)] TJ ET Q\n'
                      'BT /F2 10 Tf 1 0 0 1 40 40 Tm (A) Tj ET\n'
                      'BT /F3 10 Tf 1 0 0 1 60 40 Tm (A) Tj ET\n'
                      'BT /F4 10 Tf 1 0 0 1 80 40 Tm (B) Tj ET\n'),
        font(32, [250] + [0] * 32 + [500], '32 /space 65 /A', '/A 9 0 R /space 10 0 R'),
        font(65, [500], '65 /A', '/A 9 0 R', 11),
        font(65, [500], '65 /A', '/A 9 0 R', 12),
        font(66, [500], '66 /UnmappedPaint', '/UnmappedPaint 9 0 R'),
        _pages.stream('500 0 0 0 400 600 d1 0 0 400 600 re f\n'),
        _pages.stream('250 0 d0\n'),
        unicode('0041'), unicode('005A'),
    ])
    items = []
    for index, source, confidence, selected, explicit, inferred, matrix, advance in (
            (1, '41', 'INFERRED', 'A', None, 'A', [10, 0, 0, 30, 25, 73], [5, 0]),
            (2, '20', 'INFERRED', ' ', None, ' ', [10, 0, 0, 30, 31, 73], [2.5, 0]),
            (3, '41', 'INFERRED', 'A', None, 'A', [10, 0, 0, 30, 39.5, 73], [5, 0]),
            (4, '41', 'EXPLICIT', 'A', 'A', 'A', [10, 0, 0, 10, 40, 40], [5, 0]),
            (5, '41', 'CONTRADICTORY', None, 'Z', 'A', [10, 0, 0, 10, 60, 40], [5, 0]),
            (6, '42', 'MISSING', None, None, None, [10, 0, 0, 10, 80, 40], [5, 0])):
        items.append({'index': index, 'source': source, 'confidence': confidence, 'unicode': selected,
                      'explicit': explicit, 'inferred': inferred, 'contribution': selected or '',
                      'marked-content': [], 'matrix': matrix, 'advance': advance})
    diagnostics = [
        {'code': 'CONTRADICTORY_UNICODE_MAPPING', 'page': 1, 'item': 5, 'source': '41',
         'message': 'Explicit and standard Unicode mappings disagree for this character code.'},
        {'code': 'MISSING_UNICODE_MAPPING', 'page': 1, 'item': 6, 'source': '42',
         'message': 'No defensible Unicode mapping is available for this character code.'},
    ]
    return data, {'source': 'uncertain-geometry', 'required-chains': ['syntax', 'standards', 'semantic'],
                  'extraction': {'pages': [{'number': 1, 'rotation': 90, 'user-unit': 2,
                                            'crop-box': [10, 20, 130, 120], 'text': 'A AA',
                                            'marked-content': [], 'items': items}],
                                 'roots': [], 'diagnostics': diagnostics}, 'visual': [],
                  'coverage': ['explicit-inferred-contradictory-missing', 'no-fabricated-whitespace',
                               'unrotated-user-space', 'rotation-user-unit', 'ctm-rise-scaling-spacing-tj']}


def generate(target):
    target.mkdir()
    for directory in ('fixtures', 'expected', 'visual'):
        (target / directory).mkdir()
    sources, products = {}, {}
    for name, author in (('nested-split-type3', nested_split_type3), ('marked-structure', marked_structure),
                         ('embedded-font-kinds', embedded_font_kinds),
                         ('embedded-font-inheritance', lambda: embedded_font_kinds(True)),
                         ('uncertain-geometry', uncertain_geometry)):
        data, product = author()
        relative = 'fixtures/' + name + '.pdf'
        (target / relative).write_bytes(data)
        sources[name] = {'path': relative, 'sha256': hashlib.sha256(data).hexdigest(),
                         'pdf-version': '1.7' if name == 'nested-split-type3' else '2.0'}
        products[name] = product
        product.setdefault('required-chains', ['syntax', 'standards', 'semantic', 'visual'])
        for page in product['extraction']['pages']:
            for item in page['items']:
                item['rendering-mode'] = 'FILL'
                for key in ('unicode', 'explicit', 'inferred'):
                    item[key + '-present'] = item[key] is not None
        for index, page in enumerate(product['visual'], 1):
            width, height, pixels = _pages.raster(page)
            raster = _pages.png(width, height, pixels)
            page['renderer-agreement-threshold'] = 0
            if name == 'embedded-font-kinds':
                # Font smoothing is defined by the pinned renderer, not by
                # the ideal ink rectangle. Its reference is the original raw
                # PDF, authored before any direct-font Folio product exists.
                reference = Path(__file__).resolve().parents[1] / 'capabilities/profiles/T13-fonts'
                reference_pdf = reference / 'embedded-font-kinds-reference.pdf'
                reference_png = reference / 'embedded-font-kinds-reference.png'
                pdf_sha = 'e016ccb3e411f70ee07b9e583ab9791d9664bbb1f0c8c5efa7874cb89cff2ada'
                png_sha = 'a835cd6affd09b91f34d189aaa109d96aa36833b796578d5bdaa6f11f412e788'
                reference_bytes = reference_pdf.read_bytes()
                raster = reference_png.read_bytes()
                if (hashlib.sha256(reference_bytes).hexdigest() != pdf_sha or reference_bytes != data
                        or hashlib.sha256(raster).hexdigest() != png_sha):
                    raise ValueError('Original font visual reference identity mismatch')
                page['reference-pdf'] = '../T13-fonts/embedded-font-kinds-reference.pdf'
                page['reference-pdf-sha256'] = pdf_sha
                # Seven 16x24 pixel rectangles; a one-pixel inner/outer
                # boundary shell has 18*26-14*22 = 160 pixels per glyph.
                # Primary comparison stays exactly zero.
                page['renderer-agreement-threshold'] = 1120
            page['raster'] = f'expected/{name}-page-{index}.png'
            page['raster-width'], page['raster-height'] = width, height
            page['raster-sha256'] = hashlib.sha256(raster).hexdigest()
            (target / page['raster']).write_bytes(raster)
            profile = _pages.visual_profile(name, index, len(product['visual']), page)
            profile = profile.replace(_pages.PROFILE, PROFILE).replace('Original T10', 'Original T13')
            if name == 'embedded-font-kinds':
                profile = profile.replace('RENDERER_AGREEMENT_THRESHOLD=0', 'RENDERER_AGREEMENT_THRESHOLD=1120')
                profile = profile.replace('pinned PDFium default smoothing; vector edges are axis-aligned',
                                          'pinned PDFium default font smoothing; independent original PDF reference')
            profile = profile.replace('FONT_POLICY=not applicable; the artifact has no text or font resources and uses no system fonts',
                                      'FONT_POLICY=' + ('original embedded Type1C, CID CFF and TrueType rectangle glyphs; no system fonts or substitution'
                                      if name.startswith('embedded-font-') else
                                      'original embedded Type3 rectangle glyph; no system fonts or substitution'))
            (target / 'visual' / f'{name}-page-{index}.properties').write_text(profile, encoding='utf-8')
    corpus = {'profile': PROFILE, 'sources': sources, 'products': products}
    (target / 'corpus.json').write_text(json.dumps(corpus, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    # Properties.load discards an unescaped leading space; extraction has
    # legitimate one-space character mappings and contributions.
    (target / 'corpus.properties').write_text(_metadata.properties(corpus).replace('= ', '=\\ '), encoding='ascii')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Usage: generate-t13-corpus.py <fresh-output-directory>')
    generate(Path(sys.argv[1]))
