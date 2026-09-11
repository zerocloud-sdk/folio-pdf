#!/usr/bin/env python3
"""Author original T12 Sources, semantic expectations and exact pixel grids.

Inputs are the worked geometry and operation plan in docs/t12-certification.md.
No Folio product, renderer output, iText material or third-party Python module
is consumed. The T10 pixel writer and T11 original PDF writer are reused.
Apache-2.0, Folio PDF by ZeroCloud contributors.
"""
import copy
from decimal import Decimal
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


_pages = authoring_module('t12_page_authoring', 'generate-t10-corpus.py')
_metadata = authoring_module('t12_metadata_authoring', 'generate-t11-corpus.py')
PROFILE = 'T12-annotations-document-actions'
PRODUCTS = ('created', 'changed', 'flattened', 'copied', 'merged', 'adopted', 'left', 'right')


def destination(page, style='FIT', *parameters):
    return {'kind': 'PAGE', 'page': page, 'style': style, 'parameters': list(parameters)}


def named(name):
    return {'kind': 'NAMED', 'named': name}


def page_definitions():
    result = {}
    for name, color in [('A', [1, 0, 0]), ('B', [0, 1, 0]), ('C', [0, 0, 1]), ('D', [0, 0, 0])]:
        program = 'q ' + ' '.join(map(str, color)) + ' rg 10 20 40 30 re f Q\n'
        if name == 'A':
            program += '2 0 0 2 13 17 cm\n0 0 1 1 re W n\n'
        result[name] = {'marker': name, 'media-box': [0, 0, 120, 100], 'crop-box': [0, 0, 120, 100],
                        'rotation': 0, 'contents': [program],
                        'paints': [[10, 20, 40, 30] + [255 * component for component in color]]}
    return result


def appearance(color):
    return {'box': [2, 3, 12, 13], 'matrix': [1, 0, 0, 1, 0, 0], 'resources': 'empty',
            'program': 'q ' + ' '.join(map(str, color)) + ' rg 2 3 10 10 re f Q\n', 'color': color}


def annotation(identifier, kind, page, rectangle, appearance_color, **fields):
    value = {'id': identifier, 'type': kind, 'page': page, 'rectangle': rectangle,
             'contents': identifier + ' original', 'flags': ['PRINT'], 'appearance': appearance(appearance_color)}
    value.update(fields)
    return value


def annotations(changed=False):
    y = 45 if changed else 65
    entries = [
        annotation('note', 'TEXT', 1, [5, y, 20, y + 15], [0, 1, 1] if changed else [0, 0, 1],
                   icon='HELP' if changed else 'NOTE', open=changed),
        annotation('stamp', 'STAMP', 1, [25, y, 45, y + 15], [1, 0, 1] if changed else [0, 1, 0],
                   stamp='Draft' if changed else 'Approved'),
        annotation('highlight', 'HIGHLIGHT', 1, [50, y, 75, y + 15], [0, 0, 1] if changed else [1, 1, 0],
                   quads=[[50, y + 15, 75, y + 15, 50, y, 75, y]], color=[1, 0, 1] if changed else [1, 1, 0]),
        annotation('file', 'FILE_ATTACHMENT', 1, [80, y, 95, y + 15], [1, 1, 0] if changed else [1, 0, 1],
                   icon='PUSHPIN' if changed else 'PAPERCLIP', attachment=_metadata.attachment(
                       'payload.txt', b'abc\0' if changed else b'abc',
                       'application/octet-stream' if changed else 'text/plain',
                       'Changed payload' if changed else 'Original payload', 'Source' if changed else 'Data')),
        annotation('widget', 'WIDGET', 1, [100, y, 115, y + 15], [0, 1, 0] if changed else [0, 1, 1]),
        annotation('direct', 'LINK', 2, [5, y, 30, y + 15], [1, 0, 0], activation='DESTINATION',
                   target=destination(1, 'FIT_R', 5, 10, 100, 90)),
        annotation('named', 'LINK', 2, [35, y, 60, y + 15], [0, 1, 0], activation='DESTINATION', target=named('shared')),
        annotation('action', 'LINK', 2, [65, y, 90, y + 15], [0, 0, 1], activation='ACTION',
                   target=destination(2, 'XYZ', 10 if changed else None, 80 if changed else 90, 1.25)),
        annotation('action-named', 'LINK', 2, [95, y, 115, y + 15], [0, 0, 0],
                   activation='ACTION', target=named('shared')),
    ]
    if changed:
        for entry in entries:
            entry['contents'] = entry['id'] + ' changed'
    return entries


def primary_actions(changed=False):
    return {'document-open': named('shared'), 'pages': [
        {'page': 1, 'open': destination(2, 'FIT_H', None) if changed else destination(2), 'close': None},
        {'page': 2, 'open': None, 'close': named('shared')},
        {'page': 3, 'open': destination(1, 'XYZ', None, 90, 2) if changed else destination(1, 'FIT_R', 5, 10, 100, 90),
         'close': None}]}


def primary_source():
    return {'pages': ['A', 'B', 'C'], 'annotations': [], 'flattened': [],
            'destinations': {'shared': destination(3)}, 'actions': {'document-open': None, 'pages': []}}


def appendix_source():
    note = annotation('note', 'TEXT', 1, [5, 65, 20, 80], [1, 0, 0], icon='COMMENT', open=False)
    note['contents'] = 'Appendix note'
    link = annotation('appendix-link', 'LINK', 1, [35, 65, 60, 80], [0, 0, 1],
                      activation='ACTION', target=named('shared'))
    return {'pages': ['D'], 'annotations': [note, link], 'flattened': [],
            'destinations': {'shared': destination(1)},
            'actions': {'document-open': named('shared'), 'pages': [{'page': 1, 'open': named('shared'), 'close': None}]}}


def remap_target(target, pages, names):
    if target is None:
        return None
    if target['kind'] == 'NAMED':
        return named(names[target['named']]) if target['named'] in names else None
    if target['page'] not in pages:
        return None
    result = copy.deepcopy(target)
    result['page'] = pages[result['page']]
    return result


def remap_annotations(entries, pages, names, suffix=''):
    result = []
    for original in entries:
        if original['page'] not in pages:
            continue
        entry = copy.deepcopy(original)
        entry['page'] = pages[original['page']]
        entry['id'] += suffix
        if 'target' in entry:
            entry['target'] = remap_target(entry['target'], pages, names)
            if entry['target'] is None:
                continue
        result.append(entry)
    return result


def remap_page_actions(entries, owners, targets, names):
    result = []
    for original in entries:
        if original['page'] not in owners:
            continue
        value = {'page': owners[original['page']], 'open': remap_target(original['open'], targets, names),
                 'close': remap_target(original['close'], targets, names)}
        if value['open'] is not None or value['close'] is not None:
            result.append(value)
    return result


def split_product(merged, selection):
    pages = {original: index for index, original in enumerate(selection, 1)}
    result = {'pages': [merged['pages'][number - 1] for number in selection], 'flattened': []}
    result['destinations'] = {name: remap_target(value, pages, {}) for name, value in merged['destinations'].items()
                              if value['page'] in pages}
    names = {name: name for name in result['destinations']}
    result['annotations'] = remap_annotations(merged['annotations'], pages, names)
    result['actions'] = {'document-open': remap_target(merged['actions']['document-open'], pages, names),
                         'pages': remap_page_actions(merged['actions']['pages'], pages, pages, names)}
    return result


def product_definitions():
    created = primary_source()
    created['annotations'] = annotations()
    created['actions'] = primary_actions()
    changed = primary_source()
    changed['annotations'] = annotations(True)
    changed['actions'] = primary_actions(True)
    flattened = copy.deepcopy(changed)
    flattened['flattened'] = [a for a in flattened['annotations'] if a['type'] != 'WIDGET']
    flattened['annotations'] = [a for a in flattened['annotations'] if a['type'] == 'WIDGET']
    copied = copy.deepcopy(changed)
    copied['pages'] += ['A', 'B']
    # Copied owners are selected A/B, while C is an external retained target.
    copied_annotations = remap_annotations(changed['annotations'], {1: 4, 2: 5, 3: 3}, {'shared': 'shared'}, '-1')
    copied['annotations'] += copied_annotations
    copied['actions']['pages'] += remap_page_actions(changed['actions']['pages'], {1: 4, 2: 5},
                                                     {1: 4, 2: 5, 3: 3}, {'shared': 'shared'})
    merged = copy.deepcopy(changed)
    appendix = appendix_source()
    merged['pages'].append('D')
    merged['destinations']['shared-1'] = destination(4)
    appended = remap_annotations(appendix['annotations'], {1: 4}, {'shared': 'shared-1'})
    appended[0]['id'] = 'note-1'
    merged['annotations'] += appended
    merged['actions']['pages'] += remap_page_actions(appendix['actions']['pages'], {1: 4}, {1: 4}, {'shared': 'shared-1'})
    adopted = copy.deepcopy(merged)
    adopted['actions']['document-open'] = named('shared-1')
    return {'created': created, 'changed': changed, 'flattened': flattened, 'copied': copied,
            'merged': merged, 'adopted': adopted, 'left': split_product(merged, [1, 2]),
            'right': split_product(merged, [3, 4])}


def number(value):
    if value is None:
        return 'null'
    return format(Decimal(str(value)).normalize(), 'f')


def array(values):
    return '[' + ' '.join(number(value) for value in values) + ']'


def pdf_source(definition, pages):
    objects = [None, None, '<< >>']
    page_refs = list(range(4, 4 + len(definition['pages'])))
    objects += [None] * len(page_refs)

    def add(value):
        objects.append(value)
        return len(objects)

    def target(value):
        if value['kind'] == 'NAMED':
            return _metadata.string(value['named'])
        style = {'FIT': 'Fit', 'FIT_R': 'FitR', 'FIT_H': 'FitH', 'XYZ': 'XYZ'}[value['style']]
        operands = ''.join(' ' + number(v) for v in value['parameters'])
        return f'[{page_refs[value["page"] - 1]} 0 R /{style}{operands}]'

    def action(value):
        return '<< /S /GoTo /D ' + target(value) + ' >>'

    def form(entry):
        value = entry['appearance']
        return add(_metadata.stream(value['program'], '/Type /XObject /Subtype /Form /FormType 1 '
                                    '/BBox ' + array(value['box']) + ' /Matrix [1 0 0 1 0 0] /Resources << >>'))

    def annotation_object(entry):
        subtype = {'TEXT': 'Text', 'STAMP': 'Stamp', 'HIGHLIGHT': 'Highlight', 'FILE_ATTACHMENT': 'FileAttachment',
                   'WIDGET': 'Widget', 'LINK': 'Link'}[entry['type']]
        fields = (f'/Type /Annot /Subtype /{subtype} /NM {_metadata.string(entry["id"])} '
                  f'/P {page_refs[entry["page"] - 1]} 0 R /Rect {array(entry["rectangle"])} '
                  f'/Contents {_metadata.string(entry["contents"])} /F 4 /AP << /N {form(entry)} 0 R >> ')
        if entry['type'] == 'TEXT':
            fields += '/Name /' + {'NOTE': 'Note', 'HELP': 'Help', 'COMMENT': 'Comment'}[entry['icon']]
            fields += ' /Open ' + str(entry['open']).lower() + ' '
        elif entry['type'] == 'STAMP':
            fields += '/Name /' + entry['stamp'] + ' '
        elif entry['type'] == 'HIGHLIGHT':
            fields += '/QuadPoints ' + array([v for quad in entry['quads'] for v in quad]) + ' /C ' + array(entry['color']) + ' '
        elif entry['type'] == 'FILE_ATTACHMENT':
            file = entry['attachment']
            payload = add(_metadata.stream(bytes.fromhex(file['hex']), '/Type /EmbeddedFile /Subtype /'
                                           + file['mime'].replace('/', '#2F')
                                           + f' /Params << /Size {file["size"]} /CheckSum <{file["md5"]}> >>'))
            name = _metadata.string(file['name'])
            fs = add(f'<< /Type /Filespec /F {name} /UF {name} /Desc {_metadata.string(file["description"])} '
                     f'/AFRelationship /{file["relationship"]} /EF << /F {payload} 0 R /UF {payload} 0 R >> >>')
            fields += f'/FS {fs} 0 R /Name /' + {'PAPERCLIP': 'Paperclip', 'PUSHPIN': 'PushPin'}[entry['icon']] + ' '
        elif entry['type'] == 'LINK':
            fields += '/Border [0 0 0] '
            fields += '/A ' + action(entry['target']) if entry['activation'] == 'ACTION' else '/Dest ' + target(entry['target'])
        return add('<< ' + fields + ' >>')

    for page_number, (name, reference) in enumerate(zip(definition['pages'], page_refs), 1):
        page = pages[name]
        content = [add(_metadata.stream(program)) for program in page['contents']]
        annotations = [annotation_object(a) for a in definition['annotations'] if a['page'] == page_number]
        selected = [a for a in definition['flattened'] if a['page'] == page_number]
        resources = ''
        if selected:
            content = [add(_metadata.stream('q\n'))] + content + [add(_metadata.stream('Q\n'))]
            invocations = []
            for index, entry in enumerate(selected):
                form_name = 'Flat' + str(index)
                resources += '/' + form_name + f' {form(entry)} 0 R '
                x0, y0, x1, y1 = map(Decimal, entry['rectangle'])
                sx, sy = (x1 - x0) / 10, (y1 - y0) / 10
                matrix = ' '.join(number(v) for v in (sx, 0, 0, sy, x0 - 2 * sx, y0 - 3 * sy))
                invocations.append('q ' + matrix + ' cm /' + form_name + ' Do Q\n')
            content.append(add(_metadata.stream(''.join(invocations))))
        resource_dictionary = '<< /XObject << ' + resources + '>> >>' if resources else '<< >>'
        fields = (f'/Type /Page /Parent 2 0 R /MediaBox [0 0 120 100] /CropBox [0 0 120 100] /Rotate 0 '
                  f'/T74Marker /{name} /Resources {resource_dictionary} /Contents ['
                  + ' '.join(f'{ref} 0 R' for ref in content) + '] ')
        if annotations:
            fields += '/Annots [' + ' '.join(f'{ref} 0 R' for ref in annotations) + '] '
        for bindings in definition['actions']['pages']:
            if bindings['page'] == page_number:
                fields += '/AA << ' + ''.join('/' + key + ' ' + action(bindings[event]) + ' '
                                               for event, key in [('open', 'O'), ('close', 'C')]
                                               if bindings[event] is not None) + '>> '
        objects[reference - 1] = '<< ' + fields + '>>'
    catalog = '/Type /Catalog /Pages 2 0 R /T74Keep << /Flag true /Numbers [1 2] >> '
    if definition['destinations']:
        names = ' '.join(_metadata.string(key) + ' ' + target(value) for key, value in sorted(definition['destinations'].items()))
        catalog += '/Names << /Dests << /Names [' + names + '] >> >> '
    if definition['actions']['document-open'] is not None:
        catalog += '/OpenAction ' + action(definition['actions']['document-open']) + ' '
    objects[0] = '<< ' + catalog + '>>'
    objects[1] = '<< /Type /Pages /Kids [' + ' '.join(f'{ref} 0 R' for ref in page_refs) + f'] /Count {len(page_refs)} >>'
    return _metadata.pdf(objects)


def generate(target):
    target.mkdir()
    for directory in ('fixtures', 'expected', 'visual'):
        (target / directory).mkdir()
    pages = page_definitions()
    products = product_definitions()
    source_definitions = {'primary': primary_source(), 'appendix': appendix_source()}
    source_definitions.update({'reference-' + name: product for name, product in products.items()})
    sources = {}
    for name, definition in source_definitions.items():
        data = pdf_source(definition, pages)
        relative = 'fixtures/' + name + '.pdf'
        (target / relative).write_bytes(data)
        sources[name] = {'path': relative, 'sha256': hashlib.sha256(data).hexdigest(), 'pages': definition['pages']}
    for name, product in products.items():
        product['visual'] = []
        for index, marker in enumerate(product['pages'], 1):
            page = copy.deepcopy(pages[marker])
            for entry in product['flattened'] + product['annotations']:
                if entry['page'] == index:
                    x0, y0, x1, y1 = entry['rectangle']
                    page['paints'].append([x0, y0, x1 - x0, y1 - y0]
                                          + [255 * component for component in entry['appearance']['color']])
            width, height, pixels = _pages.raster(page)
            raster = _pages.png(width, height, pixels)
            page['raster'] = f'expected/{name}-page-{index}.png'
            page['raster-width'], page['raster-height'] = width, height
            page['raster-sha256'] = hashlib.sha256(raster).hexdigest()
            (target / page['raster']).write_bytes(raster)
            profile = _pages.visual_profile(name, index, len(product['pages']), page)
            profile = profile.replace(_pages.PROFILE, PROFILE).replace('Original T10', 'Original T12')
            (target / 'visual' / f'{name}-page-{index}.properties').write_text(profile, encoding='utf-8')
            product['visual'].append(page)
    corpus = {'profile': PROFILE, 'pages': pages, 'sources': sources, 'products': products}
    (target / 'corpus.json').write_text(json.dumps(corpus, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    (target / 'corpus.properties').write_text(_pages.properties(corpus), encoding='utf-8')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Usage: generate-t12-corpus.py <fresh-output-directory>')
    generate(Path(sys.argv[1]))
