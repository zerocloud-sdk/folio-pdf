#!/usr/bin/env python3
"""Author original rectangle glyph fonts for T13 acceptance, never runtime.

Requires the repository's already licensed fontTools 4.59.2 preparation tool.
The 400 by 600 unit rectangle and 500 unit advance are original Apache-2.0
fixture values, independent of Folio or renderer output.
"""
import hashlib
import json
from pathlib import Path
import sys

import fontTools
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.pens.t2CharStringPen import T2CharStringPen
from fontTools.cffLib import FDArrayIndex, FDSelect, FontDict


def draw_rectangle(pen):
    pen.moveTo((0, 0))
    pen.lineTo((400, 0))
    pen.lineTo((400, 600))
    pen.lineTo((0, 600))
    pen.closePath()


def truetype(path, cid=False):
    builder = FontBuilder(1000, isTTF=True)
    builder.setupGlyphOrder(['.notdef', 'A'])
    builder.setupCharacterMap({65: 'A'})
    empty = TTGlyphPen(None).glyph()
    pen = TTGlyphPen(None)
    draw_rectangle(pen)
    builder.setupGlyf({'.notdef': empty, 'A': pen.glyph()})
    builder.setupHorizontalMetrics({'.notdef': (500, 0), 'A': (500, 0)})
    builder.setupHorizontalHeader(ascent=600, descent=0, lineGap=0)
    builder.setupNameTable({'familyName': 'Folio T13 Rectangle', 'styleName': 'Regular',
                           'uniqueFontIdentifier': 'FolioT13Rectangle Apache-2.0',
                           'fullName': 'Folio T13 Rectangle', 'psName': 'FolioT13Rectangle',
                           'version': 'Version 1.0', 'copyright': 'Folio PDF by ZeroCloud contributors',
                           'licenseDescription': 'Original Apache-2.0 project acceptance fixture'})
    builder.setupOS2(sTypoAscender=600, sTypoDescender=0, sTypoLineGap=0, usWinAscent=600, usWinDescent=0,
                    sCapHeight=600, sxHeight=600)
    builder.setupPost(isFixedPitch=1)
    builder.font.recalcTimestamp = False
    builder.font['head'].created = 2082844800
    builder.font['head'].modified = 2082844800
    if cid:
        del builder.font['cmap']
    builder.save(path)
    builder.font.close()


def cff_font(path, cid):
    builder = FontBuilder(1000, isTTF=False)
    glyph_name = 'cid00001' if cid else 'A'
    builder.setupGlyphOrder(['.notdef', glyph_name])
    empty = T2CharStringPen(500, None).getCharString()
    pen = T2CharStringPen(500, None)
    draw_rectangle(pen)
    builder.setupCFF('FolioT13RectangleCID' if cid else 'FolioT13RectangleCFF', {'FullName': 'Folio T13 Rectangle CFF',
                     'FamilyName': 'Folio T13 Rectangle', 'Weight': 'Regular', 'FontBBox': [0, 0, 400, 600],
                     'Notice': 'Original Apache-2.0 project acceptance fixture', 'isFixedPitch': True},
                     {'.notdef': empty, glyph_name: pen.getCharString()}, {'defaultWidthX': 500, 'nominalWidthX': 0})
    if cid:
        top = builder.font['CFF '].cff.topDictIndex[0]
        top.ROS = ('Folio', 'T13', 0)
        top.CIDCount = 2
        dictionary = FontDict()
        dictionary.FontName = 'FolioT13RectangleCID'
        dictionary.Private = top.Private
        top.FDArray = FDArrayIndex()
        top.FDArray.strings = None
        top.FDArray.GlobalSubrs = top.GlobalSubrs
        top.FDArray.append(dictionary)
        top.FDSelect = FDSelect(format=0)
        top.FDSelect.gidArray = [0, 0]
        top.CharStrings.fdArray = top.FDArray
        top.CharStrings.fdSelect = top.FDSelect
        del top.Private
    path.write_bytes(builder.font['CFF '].compile(builder.font))
    builder.font.close()


def generate(output):
    if fontTools.__version__ != '4.59.2':
        raise ValueError('The original font recipe requires fontTools 4.59.2')
    output.mkdir()
    fonts = {}
    for name, author in (('FolioT13Rectangle.ttf', truetype),
                         ('FolioT13RectangleCID.ttf', lambda path: truetype(path, True)),
                         ('FolioT13Rectangle.cff', lambda path: cff_font(path, False)),
                         ('FolioT13RectangleCID.cff', lambda path: cff_font(path, True))):
        font = output / name
        author(font)
        fonts[name] = {'sha256': hashlib.sha256(font.read_bytes()).hexdigest(), 'units-per-em': 1000,
                       'glyph': 'cid00001' if name.endswith('CID.cff') else 'A',
                       'glyph-index': 1, 'advance': 500, 'ink-box': [0, 0, 400, 600]}
    record = {'fonttools-version': fontTools.__version__, 'fixture-license': 'Apache-2.0',
              'fonts': fonts}
    (output / 'fonts.json').write_text(json.dumps(record, indent=2, sort_keys=True) + '\n')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Usage: generate-t13-fonts.py <fresh-output-directory>')
    generate(Path(sys.argv[1]))
