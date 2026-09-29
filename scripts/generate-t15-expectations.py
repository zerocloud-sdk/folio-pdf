#!/usr/bin/env python3
"""Write literal T15 expectations, original controls and exact 144 DPI grids."""
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
import zlib


def digest(data):
    return hashlib.sha256(data).hexdigest()


def png(width, height, rectangles):
    pixels = bytearray(b'\xff\xff\xff' * width * height)
    for left, bottom, right, top, color in rectangles:
        for y in range(height - top * 2, height - bottom * 2):
            for x in range(left * 2, right * 2):
                pixels[(y * width + x) * 3:(y * width + x + 1) * 3] = bytes(color)

    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    rows = b''.join(b'\x00' + pixels[y * width * 3:(y + 1) * width * 3] for y in range(height))
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0))
            + chunk(b'sRGB', b'\x00') + chunk(b'IDAT', zlib.compress(rows, 9)) + chunk(b'IEND', b''))


def append(source, prior=None):
    previous = int(re.findall(rb'startxref\s+(\d+)\s+%%EOF', source)[-1]) if prior is None else prior
    values = {
        1: '<< /Type /Catalog /Pages 2 0 R /FolioKeep 11 0 R /FolioRevision (first) >>',
        2: '<< /Type /Pages /Kids [3 0 R 4 0 R 12 0 R] /Count 3 >>',
        12: '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << >> >>'}
    data = bytearray(source)
    offsets = {}
    for number, value in values.items():
        offsets[number] = len(data)
        data.extend((str(number) + ' 0 obj\n' + value + '\nendobj\n').encode('ascii'))
    xref = len(data)
    data.extend(b'xref\n')
    for number, offset in offsets.items():
        data.extend(('%d 1\n%010d 00000 n \n' % (number, offset)).encode('ascii'))
    data.extend(('trailer\n<< /Size 13 /Root 1 0 R /Prev %d >>\nstartxref\n%d\n%%%%EOF\n'
                 % (previous, xref)).encode('ascii'))
    return bytes(data)


def build(root):
    products = {}
    blue = [10, 10, 30, 30, [0, 0, 255]]
    red = [40, 40, 60, 60, [255, 0, 0]]
    green = [40, 40, 60, 60, [0, 255, 0]]
    added = [65, 10, 85, 30, [0, 255, 0]]
    annotation = {'page': 1, 'subtype': 'Stamp', 'contents': 'Original', 'rect': '40,40,60,60',
                  'appearance-sha256': digest(b'1 0 0 rg 0 0 20 20 re f\n')}
    for name in ('unsigned-first', 'unsigned-second', 'create', 'replace', 'move', 'remove'):
        count = 3 if name == 'unsigned-first' else 4 if name == 'unsigned-second' else 2
        expected = {'pages.count': str(count), 'keep.token': 'Unrelated',
                    'revision.marker': 'first' if name.startswith('unsigned') else ''}
        annotations = {'existing': dict(annotation)}
        paint = [[blue, red], [blue]] + [[] for _ in range(count - 2)]
        if name == 'create':
            annotations['added'] = {'page': 1, 'subtype': 'Stamp', 'contents': 'Added', 'rect': '65,10,85,30',
                                    'appearance-sha256': digest(b'0 1 0 rg 0 0 20 20 re f\n')}
            paint[0].append(added)
        elif name == 'replace':
            annotations['existing'].update(contents='Replacement', **{'appearance-sha256': digest(b'0 1 0 rg 0 0 20 20 re f\n')})
            paint[0] = [blue, green]
        elif name == 'move':
            annotations['existing']['page'] = 2
            paint = [[blue], [blue, red]]
        elif name == 'remove':
            annotations = {}
            paint[0] = [blue]
        expected['annotations.count'] = str(len(annotations))
        for identifier, item in annotations.items():
            for key in ('page', 'subtype', 'contents', 'rect'):
                expected['annotation.' + identifier + '.' + key] = str(item[key])
        visual = []
        for number in range(1, count + 1):
            width, height = (200, 200) if number <= 2 else (1224, 1584)
            expected['page.' + str(number) + '.box'] = '0,0,100,100' if number <= 2 else '0,0,612,792'
            data = png(width, height, paint[number - 1])
            filename = name + '-page-' + str(number) + '.png'
            (root / filename).write_bytes(data)
            visual.append({'path': filename, 'sha256': digest(data), 'width': width, 'height': height})
        products[name] = {'source': 'unsigned' if name == 'unsigned-first' else 'unsigned-first'
                          if name == 'unsigned-second' else 'p3', 'expected': expected,
                          'annotations': annotations, 'visual': visual}
        (root / (name + '.properties')).write_text(''.join(k + '=' + v + '\n' for k, v in sorted(expected.items())))
    source = (root / 'unsigned.pdf').read_bytes()
    valid = append(source)
    controls = {
        'revision-positive': valid,
        'prefix-changed': valid.replace(b'Unrelated', b'UnrelateX', 1),
        'revision-missing': source,
        'previous-xref-wrong': append(source, 0),
        'changed-paint': valid.replace(b'0 0 1 rg', b'1 1 0 rg', 1),
        'syntax-truncated': b'%PDF-1.7\n1 0 obj\n',
    }
    identities = {}
    for name, data in controls.items():
        path = 'control-' + name + '.pdf'
        (root / path).write_bytes(data)
        identities[name] = {'file': path, 'sha256': digest(data)}
    rules = {
        'incremental-prefix': {'clause': 'ISO 32000-1 7.5.6', 'control': 'prefix-changed'},
        'incremental-revision': {'clause': 'ISO 32000-1 7.5.6', 'control': 'revision-missing'},
        'incremental-prev': {'clause': 'ISO 32000-1 7.5.6', 'control': 'previous-xref-wrong'},
        'field-tree': {'clause': 'ISO 32000-1 12.7.3.1, Table 220', 'fixture': 'repeated-field'},
        'signature-dictionary': {'clause': 'ISO 32000-1 12.8.1, Table 252', 'fixture': 'contents-type'},
        'byte-range': {'clause': 'ISO 32000-1 12.8.1, Table 252', 'fixture': 'range-overlap'},
        'direct-values': {'clause': 'ISO 32000-1 12.8.1, Table 252', 'fixture': 'indirect-parameters'},
        'signature-reference': {'clause': 'ISO 32000-1 12.8.2.1, Table 253', 'fixture': 'method-type'},
        'docmdp-parameters': {'clause': 'ISO 32000-1 12.8.2.2, Table 254', 'fixture': 'permission-four'},
        'catalog-permissions': {'clause': 'ISO 32000-1 12.8.4, Table 258', 'fixture': 'perms-type-name'},
        'certification-link': {'clause': 'ISO 32000-1 12.8.2.2 and 12.8.4', 'fixture': 'conflicting-docmdp'},
    }
    (root / 'products.json').write_text(json.dumps({'profile': 'T15-incremental-signature-protection',
        'products': products, 'controls': identities, 'rules': rules,
        'content-sha256': digest(b'0 0 1 rg 10 10 20 20 re f\n'),
        'visual': {'dpi': 144, 'render-annotations': True, 'metric': 'AE', 'fuzz': 0, 'threshold': 0}},
        indent=2, sort_keys=True) + '\n')
    print('T15 literal products, revision controls and 144 DPI grids authored')


if __name__ == '__main__':
    build(Path(sys.argv[1]))
