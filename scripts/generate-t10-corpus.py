#!/usr/bin/env python3
"""Author the fixed T10 PDFs and original pixel grids into a fresh directory.

No PDF renderer, product output, reference-library material or third-party Python
module is used. See docs/t10-certification.md for the geometry and product plan.
"""
import copy
import hashlib
import json
from pathlib import Path
import struct
import sys
import zlib


PROFILE = 'T10-page-manipulation-merge-split'


def pdf(objects):
    data = bytearray(b'%PDF-1.7\n%\xe2\xe3\xcf\xd3\n')
    offsets = []
    for number, body in enumerate(objects, 1):
        offsets.append(len(data))
        data.extend(f'{number} 0 obj\n{body}\nendobj\n'.encode('ascii'))
    start = len(data)
    identity = hashlib.sha256(data).hexdigest()[:32]
    data.extend(f'xref\n0 {len(objects) + 1}\n0000000000 65535 f \n'.encode('ascii'))
    for offset in offsets:
        data.extend(f'{offset:010d} 00000 n \n'.encode('ascii'))
    data.extend((f'trailer\n<< /Size {len(objects) + 1} /Root 1 0 R '
                 f'/ID [<{identity}><{identity}>] >>\nstartxref\n{start}\n%%EOF\n').encode('ascii'))
    return bytes(data)


def stream(program, attributes=''):
    return f'<< {attributes} /Length {len(program.encode("ascii"))} >>\nstream\n{program}endstream'


def array(values):
    return '[' + ' '.join(str(value) for value in values) + ']'


def page_definition(name, media, crop, rotation, background, rectangle, tile_color, tile_at):
    background_program = ' '.join(map(str, background)) + ' rg ' + ' '.join(map(str, rectangle)) + ' re f'
    contents = ['q 0 0 0 rg 0 0 15 25 re f ' + background_program + ' Q\n',
                f'q 1 0 0 1 {tile_at[0]} {tile_at[1]} cm /Tile Do Q\n']
    tile_program = 'q ' + ' '.join(map(str, tile_color)) + ' rg 0 0 40 40 re f Q\n'
    return {'marker': name, 'media-box': media, 'crop-box': crop, 'rotation': rotation,
            'bleed-box': [crop[0] + 2, crop[1] + 2, crop[2] - 2, crop[3] - 2],
            'trim-box': [crop[0] + 4, crop[1] + 4, crop[2] - 4, crop[3] - 4],
            'art-box': [crop[0] + 6, crop[1] + 6, crop[2] - 6, crop[3] - 6],
            'contents': contents, 'tile-program': tile_program,
            'annotation-name': 'note-' + name, 'annotation-contents': 'note-' + name,
            'annotation-rectangle': [30, 50, 45, 65], 'annotation-flags': 2,
            'paints': [[0, 0, 15, 25, 0, 0, 0], rectangle + [255 * value for value in background],
                       tile_at + [40, 40] + [255 * value for value in tile_color]]}


def definitions():
    result = {
        'A': page_definition('A', [0, 0, 200, 160], [10, 20, 190, 140], 90,
                             [1, 1, 0], [20, 30, 80, 60], [1, 0, 0], [50, 40]),
        'B': page_definition('B', [0, 0, 240, 180], [0, 0, 240, 180], 0,
                             [0, 1, 1], [40, 70, 100, 40], [1, 0, 0], [90, 80]),
        'C': page_definition('C', [0, 0, 160, 200], [5, 15, 155, 185], 180,
                             [1, 0, 1], [10, 40, 70, 100], [1, 0, 0], [30, 90]),
        'D': page_definition('D', [0, 0, 200, 160], [10, 20, 190, 140], 0,
                             [1, 1, 0], [30, 20, 90, 60], [0, 1, 0], [80, 30]),
        'E': page_definition('E', [0, 0, 200, 160], [10, 20, 190, 140], 270,
                             [0, 1, 1], [60, 50, 70, 70], [0, 0, 1], [90, 70]),
        'blank': {'marker': None, 'media-box': [0, 0, 612, 792], 'crop-box': [0, 0, 612, 792],
                  'rotation': 0, 'contents': [], 'paints': []},
    }
    result['copy-A'] = copy.deepcopy(result['A'])
    result['copy-A']['contents'][0] = 'q 0 0 0 rg 0 0 15 25 re f 0 1 1 rg 20 30 80 60 re f Q\n'
    result['copy-A']['paints'][1][4:] = [0, 255, 255]
    result['copy-A']['annotation-name'] = 'note-A-1'
    return result


def page_body(page, parent, contents, annotation, attributes=''):
    boxes = ' '.join('/' + key + ' ' + array(page[field]) for key, field in
                     [('BleedBox', 'bleed-box'), ('TrimBox', 'trim-box'), ('ArtBox', 'art-box')])
    references = ' '.join(f'{number} 0 R' for number in contents)
    return (f'<< /Type /Page /Parent {parent} 0 R /T10Marker /{page["marker"]} {attributes} {boxes} '
            f'/Contents [{references}] /Annots [{annotation} 0 R] >>')


def annotation_body(page, page_number):
    return (f'<< /Type /Annot /Subtype /Text /Rect {array(page["annotation-rectangle"])} '
            f'/Contents ({page["annotation-contents"]}) /NM ({page["annotation-name"]}) '
            f'/F 2 /P {page_number} 0 R >>')


def form_body(page):
    return stream(page['tile-program'], '/Type /XObject /Subtype /Form /FormType 1 '
                  '/BBox [0 0 40 40] /Matrix [1 0 0 1 0 0] /Resources << >>')


def primary_source(pages):
    a, b, c = (pages[name] for name in ('A', 'B', 'C'))
    return pdf([
        '<< /Type /Catalog /Pages 2 0 R /Names << /Dests 17 0 R >> >>',
        '<< /Type /Pages /Kids [3 0 R 5 0 R 6 0 R] /Count 3 /MediaBox [0 0 240 180] '
        '/Resources << /XObject << /Tile 7 0 R >> >> >>',
        '<< /Type /Pages /Parent 2 0 R /Kids [4 0 R] /Count 1 '
        '/MediaBox [0 0 200 160] /CropBox [10 20 190 140] /Rotate 90 >>',
        page_body(a, 3, [8, 9], 14),
        page_body(b, 2, [10, 11], 15),
        page_body(c, 2, [12, 13], 16, '/MediaBox [0 0 160 200] /CropBox [5 15 155 185] /Rotate 180'),
        form_body(a),
        stream(a['contents'][0]), stream(a['contents'][1]),
        stream(b['contents'][0]), stream(b['contents'][1]),
        stream(c['contents'][0]), stream(c['contents'][1]),
        annotation_body(a, 4), annotation_body(b, 5), annotation_body(c, 6),
        '<< /Names [(shared) [5 0 R /Fit]] >>',
    ])


def additional_source(page):
    return pdf([
        '<< /Type /Catalog /Pages 2 0 R /Names << /Dests 8 0 R >> >>',
        f'<< /Type /Pages /Kids [3 0 R] /Count 1 /MediaBox {array(page["media-box"])} '
        f'/CropBox {array(page["crop-box"])} /Rotate {page["rotation"]} '
        '/Resources << /XObject << /Tile 4 0 R >> >> >>',
        page_body(page, 2, [5, 6], 7), form_body(page),
        stream(page['contents'][0]), stream(page['contents'][1]), annotation_body(page, 3),
        '<< /Names [(shared) [3 0 R /Fit]] >>',
    ])


def raster(page):
    x0, y0, x1, y1 = page['crop-box']
    width, height = 2 * (x1 - x0), 2 * (y1 - y0)
    pixels = bytearray(b'\xff' * (width * height * 3))
    for x, y, w, h, red, green, blue in page['paints']:
        left, right = max(0, 2 * (x - x0)), min(width, 2 * (x + w - x0))
        top, bottom = max(0, 2 * (y1 - y - h)), min(height, 2 * (y1 - y))
        if right > left:
            row_bytes = bytes((red, green, blue)) * (right - left)
            for row in range(top, bottom):
                start = (row * width + left) * 3
                pixels[start:start + len(row_bytes)] = row_bytes
    rotation = page['rotation']
    if rotation == 0:
        return width, height, pixels
    out_width, out_height = (height, width) if rotation in (90, 270) else (width, height)
    rotated = bytearray(len(pixels))
    for y in range(height):
        for x in range(width):
            if rotation == 90:
                nx, ny = height - 1 - y, x
            elif rotation == 180:
                nx, ny = width - 1 - x, height - 1 - y
            else:
                nx, ny = y, width - 1 - x
            before, after = (y * width + x) * 3, (ny * out_width + nx) * 3
            rotated[after:after + 3] = pixels[before:before + 3]
    return out_width, out_height, rotated


def png(width, height, pixels):
    def chunk(kind, payload):
        return struct.pack('>I', len(payload)) + kind + payload + struct.pack('>I', zlib.crc32(kind + payload) & 0xffffffff)

    rows = b''.join(b'\0' + pixels[row * width * 3:(row + 1) * width * 3] for row in range(height))
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0))
            + chunk(b'sRGB', b'\0') + chunk(b'IDAT', zlib.compress(rows, 9)) + chunk(b'IEND', b''))


def properties(value, prefix=''):
    """Mirror the frozen corpus for Java 8 consumers without a JSON dependency."""
    if isinstance(value, dict):
        return ''.join(properties(item, prefix + ('.' if prefix else '') + key)
                       for key, item in sorted(value.items()))
    if isinstance(value, list):
        return (f'{prefix}.count={len(value)}\n'
                + ''.join(properties(item, f'{prefix}.{index}') for index, item in enumerate(value)))
    text = '' if value is None else str(value)
    return prefix + '=' + text.replace('\\', '\\\\').replace('\n', '\\n').replace('\r', '\\r') + '\n'


def visual_profile(product, selection, page_count, page):
    crop = ' '.join(map(str, page['crop-box']))
    return '\n'.join([
        '# Original T10 geometry and pixels; authored before product observation.',
        f'PROFILE_ID={PROFILE}-{product}-page-{selection}',
        f'PAGE_COUNT={page_count}',
        f'PAGE_SELECTION={selection}',
        f'PAGE_BOX=effective CropBox [{crop}] points',
        'DPI=144',
        'COLOR_POLICY=sRGB, opaque 8-bit RGB PNG after compositing over opaque white',
        'FONT_POLICY=not applicable; the artifact has no text or font resources and uses no system fonts',
        'ANTIALIASING_POLICY=pinned PDFium default smoothing; vector edges are axis-aligned',
        'BACKGROUND=opaque white (#ffffff)',
        f'RASTER_WIDTH={page["raster-width"]}',
        f'RASTER_HEIGHT={page["raster-height"]}',
        'COMPARISON_METRIC=AE',
        'COMPARISON_FUZZ_PERCENT=0',
        'COMPARISON_THRESHOLD=0',
        'RENDERER_AGREEMENT_THRESHOLD=0',
        f'EXPECTED_RASTER=../{page["raster"]}',
        f'EXPECTED_RASTER_SHA256={page["raster-sha256"]}',
        '',
    ])


def generate(target):
    target.mkdir()
    (target / 'fixtures').mkdir()
    (target / 'expected').mkdir()
    (target / 'visual').mkdir()
    pages = definitions()
    sources = {}
    for name, content, selection in [('primary', primary_source(pages), ['A', 'B', 'C']),
                                      ('appendix', additional_source(pages['D']), ['D']),
                                      ('cover', additional_source(pages['E']), ['E'])]:
        relative = 'fixtures/' + name + '.pdf'
        (target / relative).write_bytes(content)
        sources[name] = {'path': relative, 'sha256': hashlib.sha256(content).hexdigest(), 'pages': selection}
    for name, page in pages.items():
        width, height, pixels = raster(page)
        image = png(width, height, pixels)
        relative = 'expected/' + name + '.png'
        (target / relative).write_bytes(image)
        page.update({'raster': relative, 'raster-sha256': hashlib.sha256(image).hexdigest(),
                     'raster-width': width, 'raster-height': height})
        del page['paints']
    products = {
        'edited': {'pages': ['A', 'blank', 'copy-A', 'B'], 'destinations': {'shared': 4}},
        'merged': {'pages': ['A', 'B', 'C', 'E', 'D'],
                   'destinations': {'shared': 2, 'shared-1': 4, 'shared-2': 5}},
        'left': {'pages': ['A', 'B', 'C'], 'destinations': {'shared': 2}},
        'right': {'pages': ['C', 'E', 'D'], 'destinations': {'shared-1': 2, 'shared-2': 3}},
    }
    for product, definition in products.items():
        for selection, page_name in enumerate(definition['pages'], 1):
            (target / 'visual' / f'{product}-page-{selection}.properties').write_text(
                visual_profile(product, selection, len(definition['pages']), pages[page_name]),
                encoding='ascii')
    corpus = {'schema-version': 1, 'profile': PROFILE, 'sources': sources,
              'pages': pages, 'products': products}
    (target / 'corpus.json').write_text(json.dumps(corpus, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    (target / 'corpus.properties').write_text(properties(corpus), encoding='ascii')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Usage: generate-t10-corpus.py <fresh-output-directory>')
    generate(Path(sys.argv[1]))
