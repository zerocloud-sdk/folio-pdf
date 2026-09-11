#!/usr/bin/env python3
"""Author original T11 PDF sources, exact expectations and independent pixel grids.

This program consumes no Folio products or renderer output. All geometry and
metadata are the worked examples in docs/t11-certification.md. Apache-2.0.
"""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import sys

_spec = importlib.util.spec_from_file_location('t10_authoring', Path(__file__).with_name('generate-t10-corpus.py'))
_authoring = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_authoring)
PROFILE = 'T11-metadata-outlines-destinations-attachments'


def pdf(objects, info=3):
    data = bytearray(b'%PDF-2.0\n%\xe2\xe3\xcf\xd3\n')
    offsets = []
    for number, body in enumerate(objects, 1):
        offsets.append(len(data))
        if isinstance(body, str):
            body = body.encode('ascii')
        data.extend(f'{number} 0 obj\n'.encode('ascii') + body + b'\nendobj\n')
    start = len(data)
    identity = hashlib.sha256(data).hexdigest()[:32]
    data.extend(f'xref\n0 {len(objects) + 1}\n0000000000 65535 f \n'.encode('ascii'))
    for offset in offsets:
        data.extend(f'{offset:010d} 00000 n \n'.encode('ascii'))
    data.extend((f'trailer\n<< /Size {len(objects) + 1} /Root 1 0 R /Info {info} 0 R '
                 f'/ID [<{identity}><{identity}>] >>\nstartxref\n{start}\n%%EOF\n').encode('ascii'))
    return bytes(data)


def stream(data, attributes=''):
    if isinstance(data, str):
        data = data.encode('ascii')
    return f'<< {attributes} /Length {len(data)} >>\nstream\n'.encode('ascii') + data + b'\nendstream'


def string(value):
    raw = value.encode('ascii') if value.isascii() else b'\xfe\xff' + value.encode('utf-16-be')
    return '<' + raw.hex() + '>'


def xmp(value):
    return ('<x:xmpmeta xmlns:x="adobe:ns:meta/">\n'
            '<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">\n'
            '<rdf:Description rdf:about="" xmlns:u="urn:folio:t73:unknown" u:opaque="keep">'
            '<u:value>' + value + '</u:value></rdf:Description>\n</rdf:RDF>\n</x:xmpmeta>\n')


def destination(page, style, *parameters):
    return {'page': page, 'style': style, 'parameters': list(parameters)}


def destinations():
    return {'b': destination(1, 'FIT_B'), 'bh': destination(2, 'FIT_BH', None),
            'bv': destination(3, 'FIT_BV', 12), 'fit': destination(1, 'FIT'),
            'h': destination(2, 'FIT_H', None), 'r': destination(1, 'FIT_R', 1, 2, 80, 90),
            'shared': destination(2, 'FIT'), 'v': destination(3, 'FIT_V', None),
            'xyz': destination(3, 'XYZ', None, 90, 1.25),
            'z': destination(1, 'FIT'), 'za': destination(2, 'FIT')}


def outline(title, page=None, named=None, children=None):
    return {'title': title, 'destination': page, 'named': named, 'children': children or []}


def primary_outline():
    return [outline('Sections', children=[outline('Named B', named='shared'),
                                         outline('Rectangle A', destination(1, 'FIT_R', 1, 2, 80, 90))]),
            outline('Explicit C', destination(3, 'XYZ', None, 90, 1.25))]


def attachment(name, payload, mime, description, relationship):
    return {'name': name, 'hex': payload.hex(), 'size': len(payload), 'mime': mime,
            'description': description, 'relationship': relationship,
            'sha256': hashlib.sha256(payload).hexdigest(), 'md5': hashlib.md5(payload).hexdigest()}


def primary_attachments():
    return [attachment('payload.txt', b'abc', 'text/plain', 'Primary payload', 'Data'),
            attachment('secondary.bin', b'\x00\x01\xff', 'application/octet-stream', 'Secondary payload', 'Unspecified')]


def appendix_attachment():
    return attachment('payload.txt', b'hello\n', 'text/plain', 'Appendix payload', 'Supplement')


def definitions():
    result = {}
    for name, color in [('A', [1, 0, 0]), ('B', [0, 1, 0]), ('C', [0, 0, 1]), ('D', [0, 0, 0])]:
        result[name] = {'marker': name, 'media-box': [0, 0, 120, 100], 'crop-box': [0, 0, 120, 100],
                        'rotation': 0, 'contents': ['q ' + ' '.join(map(str, color)) + ' rg 10 20 40 30 re f Q\n'],
                        'paints': [[10, 20, 40, 30] + [value * 255 for value in color]]}
    return result


def source_objects(pages, appendix=False):
    objects = [None] * 7
    selection = ['D'] if appendix else ['A', 'B', 'C']
    page_refs = list(range(8, 8 + len(selection)))
    objects.extend([None] * len(selection))

    def add(body):
        objects.append(body)
        return len(objects)

    for name, reference in zip(selection, page_refs):
        content = add(stream(pages[name]['contents'][0]))
        objects[reference - 1] = (f'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 120 100] '
                                  f'/CropBox [0 0 120 100] /Rotate 0 /Resources << >> '
                                  f'/T73Marker /{name} /Contents {content} 0 R >>')
    objects[0] = ('<< /Type /Catalog /Pages 2 0 R /Metadata 4 0 R /Outlines 6 0 R '
                  '/Names << /Dests 5 0 R /EmbeddedFiles 7 0 R >> '
                  '/T73Keep << /Flag true /Numbers [1 2] >> >>')
    objects[1] = ('<< /Type /Pages /Kids [' + ' '.join(f'{ref} 0 R' for ref in page_refs)
                  + f'] /Count {len(selection)} >>')
    info = {'Title': 'Appendix title', 'Subject': 'Appendix subject'} if appendix else {
        'Title': 'Source title', 'Author': 'Source author', 'T73Keep': 'Preserved info'}
    objects[2] = '<< ' + ' '.join('/' + key + ' ' + string(value) for key, value in info.items()) + ' >>'
    objects[3] = stream(xmp('appendix' if appendix else 'primary'),
                        '/Type /Metadata /Subtype /XML /T73Keep << /Opaque (stream marker) >>')
    targets = {'shared': destination(1, 'FIT')} if appendix else destinations()

    def dest_array(target):
        mode = {'FIT': 'Fit', 'FIT_B': 'FitB', 'FIT_BH': 'FitBH', 'FIT_BV': 'FitBV',
                'FIT_H': 'FitH', 'FIT_R': 'FitR', 'FIT_V': 'FitV', 'XYZ': 'XYZ'}[target['style']]
        params = ''.join(' ' + ('null' if value is None else str(value)) for value in target['parameters'])
        return f'[{page_refs[target["page"] - 1]} 0 R /{mode}{params}]'

    objects[4] = '<< /Names [' + ' '.join(string(key) + ' ' + dest_array(value) for key, value in targets.items()) + '] >>'
    items = [outline('Appendix named', named='shared')] if appendix else primary_outline()

    def write_level(items, parent):
        references = [add(None) for _ in items]
        total = len(items)
        for index, (item, reference) in enumerate(zip(items, references)):
            fields = f'/Title {string(item["title"])} /Parent {parent} 0 R '
            if index:
                fields += f'/Prev {references[index - 1]} 0 R '
            if index + 1 < len(items):
                fields += f'/Next {references[index + 1]} 0 R '
            if item['named'] is not None:
                fields += '/Dest ' + string(item['named']) + ' '
            if item['destination'] is not None:
                fields += '/Dest ' + dest_array(item['destination']) + ' '
            if item['children']:
                first, last, count = write_level(item['children'], reference)
                total += count
                fields += f'/First {first} 0 R /Last {last} 0 R /Count {count} '
            objects[reference - 1] = '<< ' + fields + '>>'
        return references[0], references[-1], total

    first, last, count = write_level(items, 6)
    objects[5] = f'<< /Type /Outlines /First {first} 0 R /Last {last} 0 R /Count {count} >>'
    files = [appendix_attachment()] if appendix else primary_attachments()
    pairs = []
    for entry in files:
        payload = add(stream(bytes.fromhex(entry['hex']),
                             '/Type /EmbeddedFile /Subtype /' + entry['mime'].replace('/', '#2F')
                             + f' /Params << /Size {entry["size"]} /CheckSum <{entry["md5"]}> >>'))
        name = string(entry['name'])
        specification = add(f'<< /Type /Filespec /F {name} /UF {name} /Desc {string(entry["description"])} '
                            f'/AFRelationship /{entry["relationship"]} /EF << /F {payload} 0 R /UF {payload} 0 R >> >>')
        pairs.append(f'{name} {specification} 0 R')
    objects[6] = '<< /Names [' + ' '.join(pairs) + '] >>'
    return objects


def retarget(items, page_map, names):
    result = []
    for original in items:
        item = copy.deepcopy(original)
        item['children'] = retarget(item['children'], page_map, names)
        target = item['destination']
        if target is not None:
            if target['page'] not in page_map:
                if item['children']:
                    item['destination'] = None
                else:
                    continue
            else:
                target['page'] = page_map[target['page']]
        if item['named'] is not None and item['named'] not in names:
            if item['children']:
                item['named'] = None
            else:
                continue
        if target is None and item['named'] is None and not item['children']:
            continue
        result.append(item)
    return result


def products():
    base = {'pages': ['A', 'B', 'C'], 'info': {'Title': 'Source title', 'Author': 'Source author', 'T73Keep': 'Preserved info'},
            'xmp': xmp('primary'), 'destinations': destinations(), 'outlines': primary_outline(),
            'attachments': primary_attachments(), 'catalog-flag': True, 'catalog-numbers': [1, 2], 'xmp-marker': 'stream marker'}
    edited = copy.deepcopy(base)
    edited['pages'] = ['C', 'A', 'B', 'A']
    edited['info'] = {'Title': 'Changed title', 'T73Keep': 'Preserved info', 'Custom': 'Edited info'}
    edited['xmp'] = xmp('edited')
    edited['destinations']['xyz']['parameters'] = [10, None, 2]
    edited['outlines'][0]['title'] = 'Edited sections'
    edited['outlines'][1]['destination'] = destination(3, 'XYZ', 10, None, 2)
    edited['attachments'][0] = attachment('payload.txt', b'abc\x00', 'application/octet-stream', 'Edited payload', 'Source')
    mapping = {1: 2, 2: 3, 3: 1}
    edited['outlines'] = retarget(edited['outlines'], mapping, set(edited['destinations']))
    for target in edited['destinations'].values():
        target['page'] = mapping[target['page']]
    merged = copy.deepcopy(base)
    merged['pages'].append('D')
    merged['info']['Subject'] = 'Appendix subject'
    merged['destinations']['shared-1'] = destination(4, 'FIT')
    merged['outlines'].append(outline('Appendix named', named='shared-1'))
    collision = appendix_attachment()
    collision['name'] = 'payload.txt-1'
    merged['attachments'].insert(1, collision)
    result = {'edited': edited, 'merged': merged}
    for name, selected, mapping in [('left', ['A', 'B'], {1: 1, 2: 2}), ('right', ['C', 'D'], {3: 1, 4: 2})]:
        split = copy.deepcopy(merged)
        split['pages'] = selected
        split['destinations'] = {key: copy.deepcopy(value) for key, value in merged['destinations'].items() if value['page'] in mapping}
        for target in split['destinations'].values():
            target['page'] = mapping[target['page']]
        split['outlines'] = retarget(merged['outlines'], mapping, set(split['destinations']))
        result[name] = split
    # One self-consistent ASCII encoding, in worked byte order per ISO 32000-2 7.9.6.
    result['edited']['destination-order'] = ['b', 'bh', 'bv', 'fit', 'h', 'r', 'shared', 'v', 'xyz', 'z', 'za']
    result['merged']['destination-order'] = ['b', 'bh', 'bv', 'fit', 'h', 'r', 'shared', 'shared-1', 'v', 'xyz', 'z', 'za']
    result['left']['destination-order'] = ['b', 'bh', 'fit', 'h', 'r', 'shared', 'z', 'za']
    result['right']['destination-order'] = ['bv', 'shared-1', 'v', 'xyz']
    return result


def properties(value, prefix=''):
    # Properties.load(InputStream) uses ISO-8859-1; emit Unicode escapes explicitly.
    if isinstance(value, dict):
        return ''.join(properties(item, prefix + ('.' if prefix else '') + key) for key, item in sorted(value.items()))
    if isinstance(value, list):
        key = prefix.encode('ascii', 'backslashreplace').decode('ascii')
        return f'{key}.count={len(value)}\n' + ''.join(properties(item, f'{prefix}.{index}') for index, item in enumerate(value))
    def escape(text):
        text = text.replace('\\', '\\\\').replace('\n', '\\n').replace('\r', '\\r')
        return ''.join(character if ord(character) < 128 else '\\u' + character.encode('utf-16-be').hex() for character in text)
    text = '' if value is None else str(value).lower() if isinstance(value, bool) else str(value)
    return escape(prefix) + '=' + escape(text) + '\n'


def generate(target):
    target.mkdir()
    for directory in ['fixtures', 'expected', 'visual']:
        (target / directory).mkdir()
    pages, sources = definitions(), {}
    for name, appendix, selection in [('primary', False, ['A', 'B', 'C']), ('appendix', True, ['D'])]:
        data = pdf(source_objects(pages, appendix))
        relative = 'fixtures/' + name + '.pdf'
        (target / relative).write_bytes(data)
        sources[name] = {'path': relative, 'sha256': hashlib.sha256(data).hexdigest(), 'pages': selection}
    for name, page in pages.items():
        width, height, pixels = _authoring.raster(page)
        raster = _authoring.png(width, height, pixels)
        relative = 'expected/' + name + '.png'
        (target / relative).write_bytes(raster)
        page.update({'raster': relative, 'raster-sha256': hashlib.sha256(raster).hexdigest(),
                     'raster-width': width, 'raster-height': height})
    corpus = {'profile': PROFILE, 'pdf-version': '2.0', 'sources': sources, 'pages': pages, 'products': products()}
    (target / 'corpus.json').write_text(json.dumps(corpus, ensure_ascii=True, indent=2, sort_keys=True) + '\n', encoding='ascii')
    (target / 'corpus.properties').write_text(properties(corpus), encoding='ascii')
    for product, definition in corpus['products'].items():
        for selection, name in enumerate(definition['pages'], 1):
            profile = _authoring.visual_profile(product, selection, len(definition['pages']), pages[name])
            profile = profile.replace(_authoring.PROFILE, PROFILE).replace('Original T10 geometry', 'Original T11 geometry')
            (target / 'visual' / f'{product}-page-{selection}.properties').write_text(profile, encoding='ascii')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('usage: generate-t11-corpus.py FRESH_OUTPUT_DIRECTORY')
    generate(Path(sys.argv[1]))
