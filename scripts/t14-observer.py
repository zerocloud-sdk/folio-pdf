#!/usr/bin/env python3
"""Independent T14 observations over pinned qpdf JSON, never Folio objects.

The closed acceptance profile and original controls are documented separately.
This module imports neither the product nor the corpus authoring program.
"""
import base64
from decimal import Decimal
import hashlib
import json
from pathlib import Path
import re
import math
import struct

PROFILE = 'T14-image-resource-extraction'
REFERENCE = re.compile(r'^[1-9][0-9]* [0-9]+ R$')
SUPPORTED_FILTERS = {'ASCIIHexDecode', 'ASCII85Decode', 'RunLengthDecode', 'FlateDecode'}
FILTERS = SUPPORTED_FILTERS | {'LZWDecode', 'DCTDecode', 'JPXDecode', 'CCITTFaxDecode', 'JBIG2Decode', 'Crypt'}
MAX_BYTES = 16 * 1024 * 1024


class Invalid(Exception):
    def __init__(self, rule, message):
        super().__init__(message)
        self.rule = rule


def require(condition, rule, message):
    if not condition:
        raise Invalid(rule, message)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def flat(value, prefix=''):
    result = {}
    if isinstance(value, dict):
        for key, item in value.items():
            result.update(flat(item, prefix + ('.' if prefix else '') + key))
    elif isinstance(value, list):
        result[prefix + '.count'] = str(len(value))
        for index, item in enumerate(value):
            result.update(flat(item, prefix + '.' + str(index)))
    else:
        result[prefix] = '' if value is None else str(value)
    return result


class Graph:
    def __init__(self, data):
        require(data.get('version') == 2 and data['qpdf'][0]['jsonversion'] == 2, 'document', 'qpdf JSON version')
        self.objects = data['qpdf'][1]
        self.pages = [page['object'] for page in data['pages']]
        self.root = self.objects['trailer']['value']['/Root']
        self.version = data['qpdf'][0]['pdfversion']
        require(len(self.objects) <= 10000, 'document', 'bounded qpdf object count')

    def is_reference(self, value):
        return isinstance(value, str) and REFERENCE.fullmatch(value) is not None

    def entry(self, reference):
        require(self.is_reference(reference) and 'obj:' + reference in self.objects, 'document', 'live indirect reference')
        return self.objects['obj:' + reference]

    def resolve(self, value):
        seen = set()
        while self.is_reference(value):
            require(value not in seen and len(seen) < 128, 'document', 'acyclic indirect resolution')
            seen.add(value)
            entry = self.entry(value)
            value = entry['stream']['dict'] if 'stream' in entry else entry['value']
        return value

    def dictionary(self, value, rule='resources'):
        value = self.resolve(value)
        require(isinstance(value, dict), rule, 'dictionary required')
        return value

    def bytes(self, reference):
        entry = self.entry(reference)
        require('stream' in entry and 'data' in entry['stream'], 'streams', 'inline qpdf stream required')
        value = base64.b64decode(entry['stream']['data'], validate=True)
        require(len(value) <= MAX_BYTES, 'streams', 'bounded stream bytes')
        return value

    def resources(self, page):
        seen = set()
        while page is not None:
            require(page not in seen and len(seen) < 100, 'resources', 'acyclic page ancestry')
            seen.add(page)
            dictionary = self.dictionary(page)
            if dictionary.get('/Resources') is not None:
                return self.dictionary(dictionary['/Resources'])
            page = dictionary.get('/Parent')
        raise Invalid('resources', 'missing inherited page Resources')

    def canonical(self):
        identities, pending, objects = {}, [], []

        def value(item, depth=0):
            require(depth < 128, 'document', 'bounded direct graph')
            if self.is_reference(item):
                self.entry(item)
                if item not in identities:
                    identities[item] = len(identities) + 1
                    pending.append(item)
                return {'reference': identities[item]}
            if isinstance(item, dict):
                return {key: value(item[key], depth + 1) for key in sorted(item)}
            if isinstance(item, list):
                return [value(child, depth + 1) for child in item]
            if isinstance(item, (int, Decimal)) and not isinstance(item, bool):
                return {'number': str(Decimal(item).normalize())}
            return item

        result = {'root': value(self.root), 'objects': objects}
        trailer = self.objects['trailer']['value']
        if '/Info' in trailer:
            result['info'] = value(trailer['/Info'])
        index = 0
        while index < len(pending):
            reference = pending[index]
            entry = self.entry(reference)
            if 'stream' in entry:
                dictionary = {key: member for key, member in entry['stream']['dict'].items() if key != '/Length'}
                objects.append({'dictionary': value(dictionary), 'encoded-sha256': digest(self.bytes(reference))})
            else:
                item = entry['value']
                if reference == self.root:
                    item = {key: member for key, member in item.items() if key != '/Version'}
                objects.append({'value': value(item)})
            index += 1
        return result


def color_metadata(graph, value, resources, mask=False, depth=0, declared=None):
    require(depth <= 32, 'colors', 'bounded color aliases')
    item = graph.resolve(value)
    if mask:
        family, status, name, components = 'NONE', 'SUPPORTED', '', 1
    elif isinstance(item, str):
        name = item[1:]
        declared = declared or name
        families = {'DeviceGray': ('DEVICE_GRAY', 1), 'DeviceRGB': ('DEVICE_RGB', 3), 'DeviceCMYK': ('DEVICE_CMYK', 4)}
        if name in families:
            family, components = families[name]
            status = 'SUPPORTED'
        else:
            aliases = graph.dictionary(resources.get('/ColorSpace', {}))
            if item in aliases:
                return color_metadata(graph, aliases[item], resources, depth=depth + 1, declared=declared)
            family, status, name, components = 'UNKNOWN', 'MALFORMED', '', ''
    elif isinstance(item, list) and item and isinstance(item[0], str):
        name = item[0][1:]
        declared = declared or name
        family, components = {'CalGray': ('CAL_GRAY', 1), 'CalRGB': ('CAL_RGB', 3), 'Lab': ('LAB', 3),
                              'Indexed': ('INDEXED', 1), 'Separation': ('SEPARATION', 1),
                              'DeviceN': ('DEVICE_N', len(graph.resolve(item[1])) if name == 'DeviceN' else 0),
                              'ICCBased': ('ICC_BASED', graph.dictionary(item[1]).get('/N') if name == 'ICCBased' else 0)}.get(name, ('UNKNOWN', ''))
        status = 'SUPPORTED'
        if name in ('ICCBased', 'Separation', 'DeviceN') or name == 'Indexed' and graph.is_reference(item[3]):
            status = 'UNSUPPORTED'
        if family == 'UNKNOWN':
            status, name = 'MALFORMED', ''
    else:
        family, status, name, components = 'UNKNOWN', 'MALFORMED', '', ''
    result = {'family': family, 'status': status, 'declared': declared or '', 'resolved': name,
            'components': components, 'icc-indirect': '', 'icc-length': '', 'icc-sha256': ''}
    if family == 'ICC_BASED' and graph.is_reference(item[1]):
        profile = graph.bytes(item[1])
        dictionary = graph.dictionary(item[1])
        if dictionary.get('/Filter') == '/ASCIIHexDecode':
            profile = bytes.fromhex(profile.rstrip(b'>').decode('ascii'))
        if digest(profile) == '384b832de3412066743b52a75ee906b6fb9fb8d9e09e936fc2c43223815c6e0a' and components == 3:
            result.update(status='SUPPORTED', **{'icc-indirect': 'true', 'icc-length': len(profile), 'icc-sha256': digest(profile)})
    return result


def filter_metadata(graph, dictionary):
    names = graph.resolve(dictionary.get('/Filter'))
    names = [] if names is None else names if isinstance(names, list) else [names]
    parameters = graph.resolve(dictionary.get('/DecodeParms'))
    parameters = parameters if isinstance(parameters, list) else [parameters] * len(names)
    result = []
    for index, name in enumerate(names):
        name = graph.resolve(name)[1:]
        entry = {'name': name, 'support': 'SUPPORTED' if name in SUPPORTED_FILTERS else 'UNSUPPORTED',
                 'predictor': '', 'colors': '', 'bits': '', 'columns': '', 'early-change': ''}
        if name in ('FlateDecode', 'LZWDecode'):
            params = graph.resolve(parameters[index]) or {}
            entry.update(predictor=params.get('/Predictor', 1), colors=params.get('/Colors', 1),
                         bits=params.get('/BitsPerComponent', 8), columns=params.get('/Columns', 1))
            if name == 'LZWDecode':
                entry['early-change'] = params.get('/EarlyChange', 1)
        result.append(entry)
    return result


def inventory(graph, decoded, access):
    records, references = [], {}

    def bytes_metadata(reference, selected, availability, source):
        data = source.bytes(reference) if selected and availability == 'AVAILABLE' else None
        return {'selected': str(selected).lower(), 'availability': availability,
                'length': '' if data is None else len(data), 'sha256': '' if data is None else digest(data)}

    def visit(raw, category, page, path, resources, active):
        require(len(path.split('/')) <= 64 and len(records) < 1000, 'resources', 'bounded resource traversal')
        indirect = graph.is_reference(raw)
        value = graph.resolve(raw)
        if category in ('/Mask', '/SMask') or category == '/XObject' and value.get('/Subtype') == '/Image':
            kind = 'IMAGE'
        elif category == '/XObject' and value.get('/Subtype') == '/Form':
            kind = 'FORM'
        else:
            kind = {'/Font': 'FONT', '/ColorSpace': 'COLOR_SPACE', '/ExtGState': 'EXTENDED_GRAPHICS_STATE',
                    '/Pattern': 'PATTERN', '/Shading': 'SHADING', '/Properties': 'PROPERTIES', '/ProcSet': 'PROCEDURE_SET'}.get(category, 'OTHER')
        if indirect:
            require(raw not in active, 'resources', 'acyclic Form and mask declarations')
        existing = references.get(raw) if indirect else None
        if existing is None:
            index = len(records)
            record = {'kind': kind, 'indirect': str(indirect).lower(), 'identity': len(references) if indirect else '',
                      'pages': '', 'declarations': []}
            records.append(record)
            if indirect:
                references[raw] = index
        else:
            index, record = existing, records[existing]
            require(record['kind'] == kind, 'resources', 'consistent resource category reuse')
        declaration = {'page': page, 'path': path}
        record['declarations'].append(declaration)
        record['pages'] = ','.join(str(p) for p in sorted({d['page'] for d in record['declarations']}))
        next_active = active | {raw} if indirect else active
        if kind == 'FONT':
            subtype = value['/Subtype']
            base_font = value.get('/BaseFont', '')[1:] if '/BaseFont' in value else ''
            descriptor = graph.dictionary(value.get('/FontDescriptor', {}))
            if subtype == '/Type0':
                descendant = graph.dictionary(graph.resolve(value['/DescendantFonts'])[0])
                descriptor = graph.dictionary(descendant.get('/FontDescriptor', {}))
            embedding = 'EMBEDDED' if subtype == '/Type3' or any(key in descriptor for key in (
                '/FontFile', '/FontFile2', '/FontFile3')) else 'NOT_EMBEDDED'
            prefix = re.match(r'^([A-Z]{6})\+', base_font)
            record['font'] = {'kind': {'/Type0': 'TYPE_0', '/Type1': 'TYPE_1', '/MMType1': 'MM_TYPE_1',
                                      '/TrueType': 'TRUE_TYPE', '/Type3': 'TYPE_3'}[subtype],
                              'status': 'SUPPORTED', 'embedding': embedding, 'base-font': base_font,
                              'subset': str(prefix is not None).lower(), 'subset-prefix': prefix[1] if prefix else ''}
        elif kind == 'FORM':
            walk(graph.dictionary(value.get('/Resources', {})), page, path, next_active)
        elif kind == 'IMAGE':
            mask = value.get('/ImageMask', False)
            color = color_metadata(graph, value.get('/ColorSpace'), resources, mask)
            filters = filter_metadata(graph, value)
            external = value.get('/F') is not None
            available = 'EXTERNAL_STREAM' if external else 'AVAILABLE'
            decoded_available = available if external or all(f['support'] == 'SUPPORTED' for f in filters) else 'UNSUPPORTED_FILTER'
            metadata = {'width': value['/Width'], 'height': value['/Height'], 'bits': value.get('/BitsPerComponent', ''),
                        'components': color['components'], 'image-mask': str(mask).lower(), 'embedded-soft-mask': 'NONE',
                        'color': color, 'filters': filters,
                        'encoded': bytes_metadata(raw, access in ('ENCODED', 'ENCODED_AND_DECODED'), available, graph),
                        'decoded': bytes_metadata(raw, access in ('DECODED', 'ENCODED_AND_DECODED'), decoded_available, decoded)}
            for entry, field, kind_name in [('/Mask', 'explicit-mask', 'EXPLICIT_IMAGE'), ('/SMask', 'soft-mask', 'SOFT_IMAGE')]:
                target = value.get(entry)
                relationship = {'kind': '', 'image': '', 'ranges': ''}
                if target is not None and graph.resolve(target) != '/None':
                    if isinstance(graph.resolve(target), list):
                        relationship.update(kind='COLOR_KEY', ranges=','.join(str(n) for n in graph.resolve(target)))
                    else:
                        related = visit(target, entry, page, path + entry + entry, resources, next_active)
                        relationship.update(kind=kind_name, image=related)
                metadata[field] = relationship
            record['image'] = metadata
        return index

    def walk(resources, page, path='', active=frozenset()):
        for category in sorted(resources):
            entries = graph.resolve(resources[category])
            if entries is None:
                continue
            if category == '/ProcSet':
                require(isinstance(entries, list), 'resources', 'ProcSet array')
                for entry in entries:
                    name = graph.resolve(entry)
                    require(isinstance(name, str) and name.startswith('/'), 'resources', 'ProcSet name')
                    visit(entry, category, page, (path + category + name).lstrip('/'), resources, active)
                continue
            require(isinstance(entries, dict), 'resources', 'closed profile named resource maps')
            for name in sorted(entries):
                if graph.resolve(entries[name]) is not None:
                    visit(entries[name], category, page, (path + category + name).lstrip('/'), resources, active)

    for number, page in enumerate(graph.pages, 1):
        walk(graph.resources(page), number)
    return {'resources': records}


def standards(graph, decoded=None):
    """Check the frozen ordinary-PDF image declaration profile independently.

    pdfcpu separately qualifies Catalog, page tree and Form declaration rules.
    These predicates cover image/resource relationships and filter constraints
    that its broad validation result alone does not establish.
    """
    checked = set()

    def check(rule, condition, message):
        checked.add(rule)
        require(condition, rule, message)

    def integer(value):
        return type(value) is int

    def numbers(value, count):
        return isinstance(value, list) and len(value) == count and all(
            type(number) in (int, float, Decimal) and math.isfinite(number) for number in value)

    def array(value):
        return graph.resolve(value)

    def color(value, resources):
        declaration = graph.resolve(value)
        check('image-color', not isinstance(declaration, str)
              or declaration in ('/DeviceGray', '/DeviceRGB', '/DeviceCMYK'),
              'image color names identify families directly, not resource aliases')
        metadata = color_metadata(graph, value, resources)
        check('image-color', metadata['family'] != 'UNKNOWN', 'recognized image ColorSpace required')
        if isinstance(declaration, list):
            family = declaration[0]
            if family in ('/CalGray', '/CalRGB', '/Lab'):
                check('calibrated-color', len(declaration) == 2 and isinstance(graph.resolve(declaration[1]), dict),
                      'calibrated color parameter dictionary')
                params = graph.dictionary(declaration[1])
                white = array(params.get('/WhitePoint'))
                check('calibrated-color', numbers(white, 3) and white[0] > 0 and white[1] == 1 and white[2] > 0,
                      'positive normalized WhitePoint')
                black = array(params.get('/BlackPoint', [0, 0, 0]))
                check('calibrated-color', numbers(black, 3) and all(n >= 0 for n in black), 'nonnegative BlackPoint')
                if family == '/CalGray':
                    gamma = params.get('/Gamma', 1)
                    check('calibrated-color', type(gamma) in (int, float, Decimal) and math.isfinite(gamma) and gamma > 0,
                          'positive calibrated gray Gamma')
                elif family == '/CalRGB':
                    gamma = array(params.get('/Gamma', [1, 1, 1]))
                    check('calibrated-color', numbers(gamma, 3) and all(n > 0 for n in gamma), 'positive calibrated RGB Gamma')
                    check('calibrated-color', numbers(array(params.get('/Matrix', [1, 0, 0, 0, 1, 0, 0, 0, 1])), 9),
                          'calibrated RGB Matrix')
                else:
                    ranges = array(params.get('/Range', [-100, 100, -100, 100]))
                    check('calibrated-color', numbers(ranges, 4) and ranges[0] <= ranges[1] and ranges[2] <= ranges[3],
                          'ordered Lab ranges')
            elif family == '/Indexed':
                check('indexed-color', len(declaration) == 4 and integer(declaration[2]) and 0 <= declaration[2] <= 255,
                      'Indexed hival and array shape')
                base = color(declaration[1], resources)
                lookup = graph.resolve(declaration[3])
                check('indexed-color', base['family'] not in ('INDEXED', 'NONE') and isinstance(lookup, str)
                      and lookup.startswith('b:'), 'closed profile literal Indexed lookup')
                check('indexed-color', len(bytes.fromhex(lookup[2:])) >= (declaration[2] + 1) * base['components'],
                      'Indexed lookup covers every palette entry')
            elif family == '/ICCBased':
                check('icc-profile', len(declaration) == 2 and graph.is_reference(declaration[1]), 'indirect ICC stream')
                params = graph.dictionary(declaration[1])
                check('icc-profile', params.get('/N') in (1, 3, 4), 'ICC wrapper component count')
                profile = (decoded or graph).bytes(declaration[1])
                if decoded is None and params.get('/Filter') == '/ASCIIHexDecode':
                    try:
                        profile = bytes.fromhex(profile.rstrip(b'>').decode('ascii'))
                    except (ValueError, UnicodeError):
                        raise Invalid('icc-profile', 'invalid original ICC encoding')
                check('icc-profile', len(profile) >= 132 and struct.unpack_from('>I', profile)[0] == len(profile)
                      and profile[36:40] == b'acsp', 'complete ICC header and exact byte length')
                check('icc-profile', profile[16:20] == {1: b'GRAY', 3: b'RGB ', 4: b'CMYK'}[params['/N']],
                      'ICC profile and wrapper components agree')
                check('icc-profile', digest(profile) == '384b832de3412066743b52a75ee906b6fb9fb8d9e09e936fc2c43223815c6e0a',
                      'closed profile original ICC identity')
        return metadata

    visited = set()

    def image(reference, resources, active=frozenset()):
        require(reference not in active, 'image-mask', 'acyclic image mask relationships')
        dictionary = graph.dictionary(reference)
        check('image-type', dictionary.get('/Type', '/XObject') == '/XObject' and dictionary.get('/Subtype') == '/Image',
              'Image XObject type and subtype')
        width, height = dictionary.get('/Width'), dictionary.get('/Height')
        check('image-dimensions', integer(width) and integer(height) and width > 0 and height > 0,
              'positive integer image dimensions')
        mask = dictionary.get('/ImageMask', False)
        check('image-mask', type(mask) is bool, 'boolean ImageMask')
        names = array(dictionary.get('/Filter'))
        names = [] if names is None else names if isinstance(names, list) else [names]
        check('image-filters', all(isinstance(name, str) and name.startswith('/') and name[1:] in FILTERS for name in names),
              'full standard filter names in declared order')
        bits = dictionary.get('/BitsPerComponent')
        check('image-bits', bits is None and '/JPXDecode' in names or integer(bits) and bits in (1, 2, 4, 8, 16),
              'legal image BitsPerComponent')
        if mask:
            check('image-mask', bits in (None, 1) and dictionary.get('/ColorSpace') is None,
                  'one-bit Image Mask has no ColorSpace')
            metadata = {'components': 1}
        else:
            metadata = color(dictionary.get('/ColorSpace'), resources)
        params = array(dictionary.get('/DecodeParms'))
        check('decode-parameters', params is None or isinstance(params, (dict, list)), 'DecodeParms container')
        if isinstance(params, list):
            check('decode-parameters', len(params) == len(names), 'one DecodeParms slot per filter')
            params = [array(entry) for entry in params]
        else:
            check('decode-parameters', params is None or len(names) == 1, 'single DecodeParms dictionary for one filter')
            params = [params] * len(names)
        check('decode-parameters', all(entry is None or isinstance(entry, dict) for entry in params), 'DecodeParms members')
        for name, parameter in zip(names, params):
            parameter = parameter or {}
            if name in ('/FlateDecode', '/LZWDecode'):
                predictor, columns = parameter.get('/Predictor', 1), parameter.get('/Columns', 1)
                components, depth = parameter.get('/Colors', 1), parameter.get('/BitsPerComponent', 8)
                check('predictor-parameters', integer(predictor) and predictor in (1, 2, 10, 11, 12, 13, 14, 15)
                      and integer(columns) and columns > 0 and integer(components) and components > 0
                      and integer(depth) and depth in (1, 2, 4, 8, 16), 'effective predictor parameters')
                if predictor != 1:
                    check('predictor-geometry', columns == width and components == metadata['components'] and depth == bits,
                          'predictor and image geometry agree')
                if name == '/LZWDecode':
                    check('lzw-parameters', integer(parameter.get('/EarlyChange', 1)) and parameter.get('/EarlyChange', 1) in (0, 1),
                          'LZW EarlyChange')
            elif name == '/CCITTFaxDecode':
                check('codec-bits', bits == 1 and metadata['components'] == 1, 'one-component one-bit CCITT image')
                check('ccitt-parameters', integer(parameter.get('/K', 0)) and integer(parameter.get('/Columns', 1728))
                      and parameter.get('/Columns', 1728) == width and integer(parameter.get('/Rows', 0))
                      and parameter.get('/Rows', 0) in (0, height) and type(parameter.get('/BlackIs1', False)) is bool,
                      'CCITT geometry and boolean polarity')
            elif name == '/JBIG2Decode':
                check('codec-bits', bits == 1 and metadata['components'] == 1, 'one-component one-bit JBIG2 image')
            elif name == '/DCTDecode':
                check('codec-bits', bits == 8, 'eight-bit JPEG image')
        embedded = dictionary.get('/SMaskInData', 0)
        if '/JPXDecode' in names:
            check('jpx-mask', integer(embedded) and embedded in (0, 1, 2)
                  and (embedded == 0 or not mask and dictionary.get('/SMask') in (None, '/None')),
                  'JPX embedded soft-mask declaration')
        if decoded is not None and all(name[1:] in SUPPORTED_FILTERS for name in names):
            data = decoded.bytes(reference)
            size = ((width * metadata['components'] * (bits or 1) + 7) // 8) * height
            check('decoded-samples', len(data) == size, 'decoded sample length matches declared rows')
        explicit = array(dictionary.get('/Mask'))
        if explicit is not None:
            check('image-mask', not mask, 'an Image Mask cannot have a subsidiary Mask')
            if isinstance(explicit, list):
                check('color-key-mask', len(explicit) == 2 * metadata['components'] and all(integer(n) for n in explicit)
                      and all(0 <= low <= high < 2 ** bits for low, high in zip(explicit[::2], explicit[1::2])),
                      'color-key component ranges')
            else:
                check('explicit-mask', isinstance(explicit, dict) and explicit.get('/ImageMask') is True,
                      'explicit mask references an Image Mask')
                image(dictionary['/Mask'], resources, active | {reference})
        soft = array(dictionary.get('/SMask'))
        if soft not in (None, '/None'):
            check('soft-mask', not mask and isinstance(soft, dict) and soft.get('/ColorSpace') == '/DeviceGray'
                  and soft.get('/BitsPerComponent') in (1, 2, 4, 8, 16)
                  and not soft.get('/ImageMask', False) and soft.get('/Mask') is None and soft.get('/SMask') is None,
                  'grayscale subsidiary soft mask without nested masks')
            if '/Matte' in soft:
                matte = array(soft['/Matte'])
                check('matte', soft.get('/Width') == width and soft.get('/Height') == height
                      and numbers(matte, metadata['components']) and all(0 <= number <= 1 for number in matte),
                      'closed profile Matte geometry and component ranges')
            image(dictionary['/SMask'], resources, active | {reference})
        visited.add(reference)

    def walk(resources, active=frozenset()):
        check('resource-maps', isinstance(resources, dict), 'Resource dictionary')
        for category, value in resources.items():
            entries = array(value)
            if category == '/ProcSet':
                check('resource-maps', isinstance(entries, list), 'ProcSet array')
                check('resource-maps', all(array(entry) in ('/PDF', '/Text', '/ImageB', '/ImageC', '/ImageI')
                      for entry in entries), 'ProcSet standard names')
                continue
            check('resource-maps', isinstance(entries, dict), 'named resource category dictionary')
            for name, reference in entries.items():
                check('resource-maps', isinstance(name, str) and name.startswith('/'), 'resource declaration name')
                item = array(reference)
                if category == '/XObject':
                    check('xobject-stream', graph.is_reference(reference) and 'stream' in graph.entry(reference),
                          'indirect XObject stream')
                    check('xobject-subtype', item.get('/Subtype') in ('/Image', '/Form'), 'closed profile XObject subtype')
                    if item['/Subtype'] == '/Image':
                        image(reference, resources)
                    else:
                        check('form-resources', reference not in active and isinstance(array(item.get('/Resources', {})), dict),
                              'acyclic Form with resource dictionary')
                        walk(array(item.get('/Resources', {})), active | {reference})
                elif category == '/Font':
                    check('font-declaration', isinstance(item, dict) and item.get('/Type', '/Font') == '/Font'
                          and item.get('/Subtype') in ('/Type0', '/Type1', '/MMType1', '/TrueType', '/Type3'),
                          'supported Font dictionary declaration')
                    if item['/Subtype'] == '/Type3':
                        glyphs = array(item.get('/CharProcs'))
                        check('font-embedding', isinstance(glyphs, dict) and all(graph.is_reference(ref)
                              and 'stream' in graph.entry(ref) for ref in glyphs.values()), 'Type3 glyph streams')
                    else:
                        check('font-declaration', isinstance(item.get('/BaseFont'), str) and item['/BaseFont'].startswith('/'),
                              'BaseFont name')
                        descriptor = graph.dictionary(item.get('/FontDescriptor', {}))
                        if item['/Subtype'] == '/Type0':
                            descendants = array(item.get('/DescendantFonts'))
                            check('font-declaration', isinstance(descendants, list) and len(descendants) == 1,
                                  'one Type0 descendant')
                            descriptor = graph.dictionary(graph.dictionary(descendants[0]).get('/FontDescriptor', {}))
                        for key in ('/FontFile', '/FontFile2', '/FontFile3'):
                            if key in descriptor:
                                check('font-embedding', graph.is_reference(descriptor[key]) and 'stream' in graph.entry(descriptor[key]),
                                      'embedded font program stream')
                elif category == '/ColorSpace':
                    color(reference, resources)
                elif category == '/ExtGState':
                    check('graphics-state', isinstance(item, dict) and item.get('/Type', '/ExtGState') == '/ExtGState',
                          'graphics state dictionary')
    for page in graph.pages:
        walk(graph.resources(page))
    return sorted(checked)
