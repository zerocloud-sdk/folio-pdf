#!/usr/bin/env python3
"""Author original, one-defect controls for the frozen T14 standards predicates."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import sys

SPEC = importlib.util.spec_from_file_location('t14_authoring', Path(__file__).with_name('generate-t14-corpus.py'))
AUTHOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUTHOR)


def replace(objects, index, before, after):
    result = copy.deepcopy(objects)
    data = result[index - 1]
    if isinstance(data, bytes):
        before, after = before.encode('ascii'), after.encode('ascii')
    if data.count(before) != 1:
        raise ValueError('Mutation must identify one original field: ' + str(before))
    result[index - 1] = data.replace(before, after)
    return result


def controls():
    sources = {name: constructor()[0] for name, constructor in (
        ('inventory', AUTHOR.inventory), ('filters', AUTHOR.filters), ('formats', AUTHOR.formats), ('colors', AUTHOR.colors))}
    result = []

    def mutation(name, rule, source, object_number, before, after):
        result.append((name, rule, source, replace(sources[source], object_number, before, after)))

    mutation('image-type-name', 'image-type', 'filters', 5, '/Type /XObject', '/Type /Other')
    mutation('image-type-scalar', 'image-type', 'filters', 5, '/Type /XObject', '/Type 42')
    for name, before, after in [('width-zero', '/Width 2', '/Width 0'), ('height-negative', '/Height 1', '/Height -1'),
                               ('width-real', '/Width 2', '/Width 2.5'), ('height-boolean', '/Height 1', '/Height true'),
                               ('width-missing', '/Width 2', '')]:
        mutation(name, 'image-dimensions', 'filters', 5, before, after)
    for name, after in [('bits-3', '3'), ('bits-boolean', 'true'), ('bits-name', '/Eight')]:
        mutation(name, 'image-bits', 'filters', 5, '/BitsPerComponent 8', '/BitsPerComponent ' + after)
    mutation('color-unknown', 'image-color', 'filters', 5, '/ColorSpace /DeviceRGB', '/ColorSpace /NoSuchColor')
    mutation('color-resource-alias', 'image-color', 'colors', 5, '/ColorSpace /DeviceRGB', '/ColorSpace /Alias')
    mutation('procset-type', 'resource-maps', 'colors', 3,
             '/ProcSet [/ImageC /PDF /ImageB /ImageC]', '/ProcSet << /PDF true >>')
    mutation('procset-member', 'resource-maps', 'colors', 3,
             '/ProcSet [/ImageC /PDF /ImageB /ImageC]', '/ProcSet [/ImageC 42 /ImageB /ImageC]')
    mutation('calibrated-whitepoint-y', 'calibrated-color', 'colors', 7, '/WhitePoint [0.9505 1 1.089]', '/WhitePoint [0.9505 2 1.089]')
    mutation('calibrated-gamma', 'calibrated-color', 'colors', 7, '/Gamma 2.2', '/Gamma -1')
    mutation('calibrated-matrix', 'calibrated-color', 'colors', 8, '/Matrix [1 0 0 0 1 0 0 0 1]', '/Matrix [1 0 0]')
    mutation('lab-range-order', 'calibrated-color', 'colors', 9, '/Range [-100 100 -100 100]', '/Range [100 -100 -100 100]')
    mutation('indexed-hival', 'indexed-color', 'colors', 10, '/DeviceRGB 1 <ff00000000ff>', '/DeviceRGB 256 <ff00000000ff>')
    mutation('indexed-short-lookup', 'indexed-color', 'colors', 10, '<ff00000000ff>', '<ff0000>')
    mutation('icc-components', 'icc-profile', 'colors', 12, '/N 3', '/N 1')
    mutation('icc-header', 'icc-profile', 'colors', 12, '61637370', '61637371')
    mutation('filter-unknown', 'image-filters', 'filters', 6, '/ASCIIHexDecode', '/NotAFilter')
    mutation('filter-abbreviation', 'image-filters', 'filters', 6, '/ASCIIHexDecode', '/AHx')
    mutation('filter-array-scalar', 'image-filters', 'filters', 10, '[/ASCII85Decode /FlateDecode]', '[/ASCII85Decode 3]')
    mutation('decodeparams-shape', 'decode-parameters', 'filters', 10, '[null null]', 'true')
    mutation('decodeparams-count', 'decode-parameters', 'filters', 10, '[null null]', '[null]')
    mutation('decodeparams-member', 'decode-parameters', 'filters', 10, '[null null]', '[null true]')
    mutation('predictor-value', 'predictor-parameters', 'filters', 11, '/Predictor 2', '/Predictor 9')
    mutation('predictor-depth', 'predictor-parameters', 'filters', 11, '/BitsPerComponent 8 /Columns 2', '/BitsPerComponent 3 /Columns 2')
    mutation('predictor-width', 'predictor-geometry', 'filters', 11, '/Columns 2', '/Columns 3')
    mutation('lzw-earlychange', 'lzw-parameters', 'formats', 9, '/EarlyChange 1', '/EarlyChange 2')
    mutation('jpeg-bits', 'codec-bits', 'formats', 6, '/BitsPerComponent 8', '/BitsPerComponent 4')
    mutation('ccitt-bits', 'codec-bits', 'formats', 5, '/BitsPerComponent 1', '/BitsPerComponent 8')
    mutation('ccitt-columns', 'ccitt-parameters', 'formats', 5, '/Columns 16', '/Columns 15')
    mutation('ccitt-polarity', 'ccitt-parameters', 'formats', 5, '/BlackIs1 true', '/BlackIs1 1')
    mutation('jpx-embedded-mask', 'jpx-mask', 'formats', 8, '/Filter /JPXDecode', '/Filter /JPXDecode /SMaskInData 3')
    mutation('image-mask-boolean', 'image-mask', 'filters', 5, '/Subtype /Image', '/Subtype /Image /ImageMask 1')
    mutation('image-mask-color', 'image-mask', 'inventory', 10, '/ImageMask true', '/ImageMask true /ColorSpace /DeviceGray')
    mutation('explicit-mask-kind', 'explicit-mask', 'inventory', 9, '/Mask 10 0 R', '/Mask 8 0 R')
    mutation('color-key-count', 'color-key-mask', 'inventory', 12, '[255 255 0 0 0 0]', '[255 255]')
    mutation('color-key-range', 'color-key-mask', 'inventory', 12, '[255 255 0 0 0 0]', '[255 254 0 0 0 0]')
    mutation('soft-mask-color', 'soft-mask', 'inventory', 15, '/DeviceGray', '/DeviceRGB')
    mutation('soft-mask-cycle', 'soft-mask', 'inventory', 15, '/Matte [0 0 0]', '/Matte [0 0 0] /SMask 11 0 R')
    mutation('matte-count', 'matte', 'inventory', 15, '/Matte [0 0 0]', '/Matte [0 0]')
    mutation('matte-range', 'matte', 'inventory', 15, '/Matte [0 0 0]', '/Matte [0 0 2]')
    mutation('form-resource-shape', 'form-resources', 'inventory', 6,
             '/Resources << /XObject << /Leaf 7 0 R >> >>', '/Resources []')
    mutation('font-subtype', 'font-declaration', 'inventory', 13, '/Subtype /TrueType', '/Subtype /Unsupported')
    mutation('font-base-name', 'font-declaration', 'inventory', 13, '/BaseFont /FolioT13Rectangle', '/BaseFont 1')
    mutation('font-program-stream', 'font-embedding', 'inventory', 14, '/FontFile2 17 0 R', '/FontFile2 20 0 R')
    mutation('type3-program-stream', 'font-embedding', 'inventory', 18, '/A 19 0 R', '/A 20 0 R')
    mutation('graphics-state-type', 'graphics-state', 'inventory', 2, '/Type /ExtGState', '/Type /NotExtGState')
    mutation('xobject-subtype', 'xobject-subtype', 'filters', 5, '/Subtype /Image', '/Subtype /Unknown')
    changed = copy.deepcopy(sources['filters'])
    changed[4] = '<< /Type /XObject /Subtype /Image /Width 2 /Height 1 /BitsPerComponent 8 /ColorSpace /DeviceRGB >>'
    result.append(('xobject-not-stream', 'xobject-stream', 'filters', changed))
    changed = copy.deepcopy(sources['filters'])
    changed[2] = ('<< /Type /Page /Parent 2 0 R /MediaBox [0 0 120 100] /Resources << /XObject [] >> /Contents 4 0 R >>')
    result.append(('resource-category-array', 'resource-maps', 'filters', changed))
    changed = copy.deepcopy(sources['filters'])
    changed[4] = AUTHOR.image_stream(bytes.fromhex('ff00000000'))
    result.append(('decoded-row-length', 'decoded-samples', 'filters', changed))
    return sources, result


def write(directory):
    directory.mkdir(parents=True, exist_ok=False)
    (directory / 'fixtures').mkdir()
    sources, cases = controls()
    catalog = {'profile': AUTHOR.PROFILE, 'controls': {}, 'positive-sources': {}}
    for name, objects in sources.items():
        data = AUTHOR.pdf(objects)
        catalog['positive-sources'][name] = hashlib.sha256(data).hexdigest()
    for name, rule, source, objects in cases:
        data = AUTHOR.pdf(objects)
        relative = 'fixtures/' + name + '.pdf'
        (directory / relative).write_bytes(data)
        catalog['controls'][name] = {'rule': rule, 'source': source, 'path': relative, 'sha256': hashlib.sha256(data).hexdigest()}
    catalog['required-rules'] = sorted({case[1] for case in cases})
    (directory / 'qualification.json').write_text(json.dumps(catalog, indent=2, sort_keys=True) + '\n')


if __name__ == '__main__':
    write(Path(sys.argv[1]))
