#!/usr/bin/env python3
"""Author original T14 PDF Sources and literal extraction/pixel expectations.

No Folio output, PDF parser or renderer supplies the expected observations.
Codec payloads are separately authored acceptance assets; their hashes are
checked before use. See docs/t14-certification.md for the worked examples.
"""
import base64
import hashlib
import importlib.util
import json
from pathlib import Path
import struct
import sys
import zlib


ROOT = Path(__file__).resolve().parents[1]
PROFILE = 'T14-image-resource-extraction'
SPEC = importlib.util.spec_from_file_location('t14_pixels', Path(__file__).with_name('generate-t10-corpus.py'))
PIXELS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PIXELS)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def stream(data, attributes=''):
    if isinstance(data, str):
        data = data.encode('ascii')
    return ('<< ' + attributes + ' /Length ' + str(len(data)) + ' >>\nstream\n').encode('ascii') + data + b'\nendstream'


def pdf(objects):
    """Original T10 serializer extended to preserve original binary streams."""
    data = bytearray(b'%PDF-2.0\n%\xe2\xe3\xcf\xd3\n')
    offsets = []
    for number, body in enumerate(objects, 1):
        offsets.append(len(data))
        if isinstance(body, str):
            body = body.encode('ascii')
        data.extend(f'{number} 0 obj\n'.encode('ascii') + body + b'\nendobj\n')
    start = len(data)
    identity = digest(data)[:32]
    data.extend(f'xref\n0 {len(objects) + 1}\n0000000000 65535 f \n'.encode('ascii'))
    for offset in offsets:
        data.extend(f'{offset:010d} 00000 n \n'.encode('ascii'))
    data.extend((f'trailer\n<< /Size {len(objects) + 1} /Root 1 0 R '
                 f'/ID [<{identity}><{identity}>] >>\nstartxref\n{start}\n%%EOF\n').encode('ascii'))
    return bytes(data)


def declaration(page, path):
    return {'page': page, 'path': path}


def resource(kind, indirect, declarations):
    return {'kind': kind, 'indirect': str(indirect).lower(),
            'pages': ','.join(str(page) for page in sorted({item['page'] for item in declarations})),
            'declarations': declarations}


def byte_data(data, availability='AVAILABLE'):
    return {'selected': 'true', 'availability': availability,
            'length': '' if data is None else len(data), 'sha256': '' if data is None else digest(data)}


def filter_info(name, **parameters):
    return dict({'name': name, 'support': 'SUPPORTED' if name in (
        'ASCIIHexDecode', 'ASCII85Decode', 'RunLengthDecode', 'FlateDecode') else 'UNSUPPORTED',
        'predictor': '', 'colors': '', 'bits': '', 'columns': '', 'early-change': ''}, **parameters)


def image(encoded, decoded, width=2, height=1, color_name='DeviceRGB', bits=8, filters=None,
          availability='AVAILABLE', image_mask=False, **changes):
    family, components = {'DeviceRGB': ('DEVICE_RGB', 3), 'DeviceGray': ('DEVICE_GRAY', 1),
                          'DeviceCMYK': ('DEVICE_CMYK', 4), '': ('NONE', '')}[color_name]
    values = {'width': width, 'height': height, 'bits': bits, 'components': components,
              'image-mask': str(image_mask).lower(), 'embedded-soft-mask': 'NONE',
              'color': {'family': family, 'status': 'SUPPORTED', 'declared': color_name,
                        'resolved': color_name, 'components': components,
                        'icc-indirect': '', 'icc-length': '', 'icc-sha256': ''},
              'filters': filters or [], 'explicit-mask': {'kind': '', 'image': '', 'ranges': ''},
              'soft-mask': {'kind': '', 'image': '', 'ranges': ''},
              'encoded': byte_data(encoded), 'decoded': byte_data(decoded, availability)}
    values.update(changes)
    return values


def image_stream(data, width=2, height=1, color='/DeviceRGB', bits=8, attributes=''):
    entries = f'/Type /XObject /Subtype /Image /Width {width} /Height {height}'
    if color:
        entries += f' /ColorSpace {color}'
    if bits is not None:
        entries += f' /BitsPerComponent {bits}'
    return stream(data, entries + ' ' + attributes)


def page_program(resources, content, extra=None):
    objects = ['<< /Type /Catalog /Pages 2 0 R >>',
               '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
               '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 120 100] /CropBox [0 0 120 100] '
               f'/Resources {resources} /Contents 4 0 R >>', stream(content)]
    objects.extend(extra or [])
    return objects


def font_record(name, kind, embedding, declarations, subset=''):
    item = resource('FONT', True, declarations)
    item['font'] = {'kind': kind, 'status': 'SUPPORTED', 'embedding': embedding,
                    'base-font': name, 'subset': str(bool(subset)).lower(), 'subset-prefix': subset}
    return item


def inventory():
    # Two inherited declarations of the same direct graphics state remain two
    # records. Every indirect alias instead contributes to its first record.
    rgb = bytes.fromhex('ff00000000ff')
    objects = [
        '<< /Type /Catalog /Pages 2 0 R >>',
        '<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 /Resources << '
        '/ExtGState << /GS << /Type /ExtGState /CA 1 /ca 1 >> >> '
        '/Font << /Embedded 13 0 R /Subset 16 0 R /Type3 18 0 R >> '
        '/XObject << /AForm 6 0 R /BAlias 8 0 R /CMasked 9 0 R /DSoft 11 0 R /EColorKey 12 0 R >> >> >>',
        '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 120 100] /CropBox [0 0 120 100] /Contents 5 0 R >>',
        '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 120 100] /CropBox [0 0 120 100] >>',
        stream('q 20 0 0 20 10 10 cm /AForm Do Q\n'
               'q 20 0 0 20 40 10 cm 0 1 0 rg /CMasked Do Q\n'
               'q 20 0 0 20 70 10 cm /DSoft Do Q\n'
               'q 20 0 0 20 10 50 cm /EColorKey Do Q\n'),
        stream('/Leaf Do\n', '/Type /XObject /Subtype /Form /BBox [0 0 1 1] '
               '/Resources << /XObject << /Leaf 7 0 R >> >>'),
        stream('/Pixels Do\n', '/Type /XObject /Subtype /Form /BBox [0 0 1 1] '
               '/Resources << /XObject << /Pixels 8 0 R >> >>'),
        image_stream(rgb),
        image_stream(rgb, attributes='/Mask 10 0 R'),
        image_stream(b'\x80', color='', bits=1, attributes='/ImageMask true /Decode [0 1]'),
        image_stream(rgb, attributes='/SMask 15 0 R'),
        image_stream(rgb, attributes='/Mask [255 255 0 0 0 0]'),
        '<< /Type /Font /Subtype /TrueType /BaseFont /FolioT13Rectangle /Encoding /WinAnsiEncoding '
        '/FirstChar 65 /LastChar 65 /Widths [500] /FontDescriptor 14 0 R >>',
        '<< /Type /FontDescriptor /FontName /FolioT13Rectangle /Flags 33 /FontBBox [0 0 400 600] '
        '/ItalicAngle 0 /Ascent 600 /Descent 0 /CapHeight 600 /StemV 400 /FontFile2 17 0 R >>',
        image_stream(b'\x00\xff', color='/DeviceGray', attributes='/Matte [0 0 0]'),
        '<< /Type /Font /Subtype /TrueType /BaseFont /ABCDEF+FolioT13Rectangle /Encoding /WinAnsiEncoding '
        '/FirstChar 65 /LastChar 65 /Widths [500] /FontDescriptor 20 0 R >>',
        stream((ROOT / 'capabilities/profiles/T13-fonts/FolioT13Rectangle.ttf').read_bytes(),
               '/Length1 ' + str((ROOT / 'capabilities/profiles/T13-fonts/FolioT13Rectangle.ttf').stat().st_size)),
        '<< /Type /Font /Subtype /Type3 /FontBBox [0 0 400 600] /FontMatrix [.001 0 0 .001 0 0] '
        '/FirstChar 65 /LastChar 65 /Widths [500] /Encoding << /Type /Encoding /Differences [65 /A] >> '
        '/CharProcs << /A 19 0 R >> /Resources << >> >>',
        stream('500 0 0 0 400 600 d1 0 0 400 600 re f\n'),
        '<< /Type /FontDescriptor /FontName /ABCDEF+FolioT13Rectangle /Flags 33 /FontBBox [0 0 400 600] '
        '/ItalicAngle 0 /Ascent 600 /Descent 0 /CapHeight 600 /StemV 400 /FontFile2 17 0 R >>',
    ]
    both = lambda path: [declaration(page, path) for page in (1, 2)]
    records = [resource('EXTENDED_GRAPHICS_STATE', False, [declaration(1, 'ExtGState/GS')]),
               font_record('FolioT13Rectangle', 'TRUE_TYPE', 'EMBEDDED', both('Font/Embedded')),
               font_record('ABCDEF+FolioT13Rectangle', 'TRUE_TYPE', 'EMBEDDED', both('Font/Subset'), 'ABCDEF'),
               font_record('', 'TYPE_3', 'EMBEDDED', both('Font/Type3')),
               resource('FORM', True, both('XObject/AForm')),
               resource('FORM', True, both('XObject/AForm/XObject/Leaf'))]
    shared = resource('IMAGE', True, [declaration(p, path) for p in (1, 2)
                      for path in ('XObject/AForm/XObject/Leaf/XObject/Pixels', 'XObject/BAlias')])
    shared['image'] = image(rgb, rgb)
    records.append(shared)
    for path, pixels, parameters in (
            ('XObject/CMasked', rgb, {'explicit-mask': {'kind': 'EXPLICIT_IMAGE', 'image': 8, 'ranges': ''}}),
            ('XObject/CMasked/Mask/Mask', b'\x80', {'color': {'family': 'NONE', 'status': 'SUPPORTED',
              'declared': '', 'resolved': '', 'components': 1, 'icc-indirect': '', 'icc-length': '', 'icc-sha256': ''},
              'bits': 1, 'components': 1, 'image-mask': 'true'}),
            ('XObject/DSoft', rgb, {'soft-mask': {'kind': 'SOFT_IMAGE', 'image': 10, 'ranges': ''}}),
            ('XObject/DSoft/SMask/SMask', b'\x00\xff', {'color': {'family': 'DEVICE_GRAY', 'status': 'SUPPORTED',
              'declared': 'DeviceGray', 'resolved': 'DeviceGray', 'components': 1,
              'icc-indirect': '', 'icc-length': '', 'icc-sha256': ''}, 'components': 1}),
            ('XObject/EColorKey', rgb, {'explicit-mask': {'kind': 'COLOR_KEY', 'image': '', 'ranges': '255,255,0,0,0,0'}})):
        record = resource('IMAGE', True, both(path))
        record['image'] = image(pixels, pixels, **parameters)
        records.append(record)
    records.append(resource('EXTENDED_GRAPHICS_STATE', False, [declaration(2, 'ExtGState/GS')]))
    paints = [[10, 10, 10, 20, 255, 0, 0], [20, 10, 10, 20, 0, 0, 255],
              [50, 10, 10, 20, 0, 0, 255], [80, 10, 10, 20, 0, 0, 255], [20, 50, 10, 20, 0, 0, 255]]
    return objects, records, [paints, []], ['order', 'indirect-aliases', 'direct-occurrences',
        'nested-forms', 'declaration-page-usage', 'explicit-mask', 'soft-mask', 'color-key-mask',
        'embedded-font', 'subset-font', 'type3-font']


def filters():
    pixels = bytes.fromhex('ff00000000ff')
    compressed = zlib.compress(pixels, 9)
    cases = [
        ('AUnfiltered', pixels, '', []),
        ('BHex', pixels.hex().encode('ascii') + b'>', '/Filter /ASCIIHexDecode', [filter_info('ASCIIHexDecode')]),
        ('C85', base64.a85encode(pixels) + b'~>', '/Filter /ASCII85Decode', [filter_info('ASCII85Decode')]),
        ('DRun', b'\x05' + pixels + b'\x80', '/Filter /RunLengthDecode', [filter_info('RunLengthDecode')]),
        ('EFlate', compressed, '/Filter /FlateDecode', [filter_info('FlateDecode', predictor=1, colors=1, bits=8, columns=1)]),
        ('FChain', base64.a85encode(compressed) + b'~>', '/Filter [/ASCII85Decode /FlateDecode] /DecodeParms [null null]',
         [filter_info('ASCII85Decode'), filter_info('FlateDecode', predictor=1, colors=1, bits=8, columns=1)]),
        ('GTiff', zlib.compress(bytes.fromhex('ff00000100ff'), 9),
         '/Filter /FlateDecode /DecodeParms << /Predictor 2 /Colors 3 /BitsPerComponent 8 /Columns 2 >>',
         [filter_info('FlateDecode', predictor=2, colors=3, bits=8, columns=2)]),
        ('HPng', zlib.compress(b'\x01' + bytes.fromhex('ff00000100ff'), 9),
         '/Filter /FlateDecode /DecodeParms << /Predictor 15 /Colors 3 /BitsPerComponent 8 /Columns 2 >>',
         [filter_info('FlateDecode', predictor=15, colors=3, bits=8, columns=2)]),
    ]
    declarations = ' '.join(f'/{name} {index + 5} 0 R' for index, (name, _, _, _) in enumerate(cases))
    content, paints, extra, records = [], [], [], []
    for index, (name, encoded, attributes, descriptions) in enumerate(cases):
        x, y = 10 + (index % 4) * 25, 10 + (index // 4) * 40
        content.append(f'q 20 0 0 20 {x} {y} cm /{name} Do Q\n')
        paints.extend([[x, y, 10, 20, 255, 0, 0], [x + 10, y, 10, 20, 0, 0, 255]])
        extra.append(image_stream(encoded, attributes=attributes))
        record = resource('IMAGE', True, [declaration(1, 'XObject/' + name)])
        record['image'] = image(encoded, pixels, filters=descriptions)
        records.append(record)
    return page_program('<< /XObject << ' + declarations + ' >> >>', ''.join(content), extra), records, [paints], [
        'unfiltered', 'asciihex', 'ascii85', 'runlength', 'flate', 'filter-chain', 'tiff-predictor', 'png-predictor']


def formats():
    directory = ROOT / 'capabilities/profiles/T14-images/codecs'
    pins = json.loads((directory / 'payloads.json').read_text())
    payloads = {}
    for name, expected in pins['files'].items():
        payloads[name] = (directory / name).read_bytes()
        if digest(payloads[name]) != expected:
            raise ValueError('Original codec payload identity mismatch: ' + name)
    cases = [('ACcitt', 'black.ccitt', 'CCITTFaxDecode', 16, 1,
              '/DecodeParms << /K -1 /Columns 16 /Rows 16 /BlackIs1 true >>', 0),
             ('BDct', 'gray.jpg', 'DCTDecode', 8, 8, '', 128),
             ('CJbig2', 'black.jbig2', 'JBIG2Decode', 16, 1, '', 0),
             ('DJpx', 'gray.jp2', 'JPXDecode', 8, 8, '', 128),
             ('ELzw', 'gray.lzw', 'LZWDecode', 8, 8, '/DecodeParms << /EarlyChange 1 >>', 128)]
    declarations = ' '.join(f'/{item[0]} {index + 5} 0 R' for index, item in enumerate(cases))
    content, paints, extra, records = [], [], [], []
    for index, (name, filename, filter_name, size, bits, params, gray) in enumerate(cases):
        x, y = 10 + (index % 3) * 35, 10 + (index // 3) * 40
        content.append(f'q 16 0 0 16 {x} {y} cm /{name} Do Q\n')
        paints.append([x, y, 16, 16, gray, gray, gray])
        extra.append(image_stream(payloads[filename], size, size, '/DeviceGray', bits,
                                  '/Filter /' + filter_name + ' ' + params))
        description = filter_info(filter_name)
        if filter_name == 'LZWDecode':
            description.update(predictor=1, colors=1, bits=8, columns=1, **{'early-change': 1})
        record = resource('IMAGE', True, [declaration(1, 'XObject/' + name)])
        record['image'] = image(payloads[filename], None, size, size, 'DeviceGray', bits,
                                [description], 'UNSUPPORTED_FILTER')
        records.append(record)
    return page_program('<< /XObject << ' + declarations + ' >> >>', ''.join(content), extra), records, [paints], [
        'ccitt-encoded-success', 'dct-encoded-success', 'jbig2-encoded-success', 'jpx-encoded-success',
        'lzw-encoded-success', 'unsupported-decoded-availability']


def colors():
    cases = [
        ('ARgb', '/DeviceRGB', 'DeviceRGB', 'DeviceRGB', 'DEVICE_RGB', 3, b'\xff\x00\x00'),
        ('BCmyk', '/DeviceCMYK', 'DeviceCMYK', 'DeviceCMYK', 'DEVICE_CMYK', 4, b'\x00\xff\xff\x00'),
        ('CCalGray', '[/CalGray << /WhitePoint [0.9505 1 1.089] /Gamma 2.2 >>]',
         'CalGray', 'CalGray', 'CAL_GRAY', 1, b'\x80'),
        ('DCalRgb', '[/CalRGB << /WhitePoint [0.9505 1 1.089] /Gamma [2.2 2.2 2.2] '
         '/Matrix [1 0 0 0 1 0 0 0 1] >>]', 'CalRGB', 'CalRGB', 'CAL_RGB', 3, b'\xff\x00\x00'),
        ('ELab', '[/Lab << /WhitePoint [0.9505 1 1.089] /Range [-100 100 -100 100] >>]',
         'Lab', 'Lab', 'LAB', 3, b'\x80\x80\x80'),
        ('FIndexed', '[/Indexed /DeviceRGB 1 <ff00000000ff>]', 'Indexed', 'Indexed', 'INDEXED', 1, b'\x01'),
        ('GIcc', '[/ICCBased 12 0 R]', 'ICCBased', 'ICCBased', 'ICC_BASED', 3, b'\xff\x00\x00'),
    ]
    declarations = ' '.join(f'/{item[0]} {index + 5} 0 R' for index, item in enumerate(cases))
    records = [resource('COLOR_SPACE', False, [declaration(1, 'ColorSpace/Alias')])]
    records.extend(resource('PROCEDURE_SET', False, [declaration(1, 'ProcSet/' + name)])
                   for name in ('ImageC', 'PDF', 'ImageB', 'ImageC'))
    extra = []
    for name, pdf_color, declared, resolved, family, components, pixels in cases:
        extra.append(image_stream(pixels, 1, 1, pdf_color))
        item = resource('IMAGE', True, [declaration(1, 'XObject/' + name)])
        item['image'] = image(pixels, pixels, 1, 1, components=components,
                              color={'declared': declared, 'resolved': resolved, 'family': family,
                                     'status': 'SUPPORTED', 'components': components,
                                     'icc-indirect': '', 'icc-length': '', 'icc-sha256': ''})
        records.append(item)
    icc = (ROOT / 'capabilities/profiles/T14-images/color/sRGB2014.icc').read_bytes()
    if digest(icc) != '384b832de3412066743b52a75ee906b6fb9fb8d9e09e936fc2c43223815c6e0a':
        raise ValueError('ICC acceptance profile identity mismatch')
    extra.append(stream(icc.hex() + '>', '/N 3 /Alternate /DeviceRGB /Filter /ASCIIHexDecode'))
    records[-1]['image']['color'].update(**{'icc-indirect': 'true', 'icc-length': len(icc), 'icc-sha256': digest(icc)})
    objects = page_program('<< /ColorSpace << /Alias /DeviceRGB >> /ProcSet [/ImageC /PDF /ImageB /ImageC] '
                           '/XObject << ' + declarations + ' >> >>', '', extra)
    return objects, records, [[]], ['color-resource-declaration', 'device-cmyk', 'calgray', 'calrgb', 'lab', 'indexed-string',
                                   'unpainted-declarations', 'procset-declared-order-and-direct-duplicates', 'bounded-icc-metadata-sha256']


def classifications():
    # This is intentionally a metadata-classification Source, not a standards
    # positive or a visual oracle. The public contract preserves these records.
    extra = [image_stream(b'\x80', 1, 1, '/Pattern'),
             image_stream(b'\xff\x00\x00', 1, 1, '[/ICCBased 11 0 R]'),
             image_stream(b'\x00', 1, 1, '[/Indexed /DeviceRGB 0 12 0 R]'),
             image_stream(b'\x80', 1, 1, '[/Separation /Spot /DeviceRGB '
                          '<< /FunctionType 2 /Domain [0 1] /C0 [1 1 1] /C1 [0 0 0] /N 1 >>]'),
             image_stream(b'\xff\x00\x00', 1, 1, attributes='/F (folio-t14-never-resolve.bin)'),
             image_stream(b'\x80', 1, 1, '/DeviceGray', attributes='/Filter /Crypt /DecodeParms << /Name /Identity >>'),
             stream(b'not-an-icc-profile', '/N 3 /Alternate /DeviceRGB'), stream(b'\xff\x00\x00'),
             image_stream(b'\xff\x00\x00', 1, 1, '/Alias')]
    names = ['AMalformed', 'BIcc', 'CIndexed', 'DSeparation', 'EExternal', 'FCrypt']
    definitions = [('UNKNOWN', 'MALFORMED', 'Pattern', '', '', b'\x80'),
                   ('ICC_BASED', 'UNSUPPORTED', 'ICCBased', 'ICCBased', 3, b'\xff\x00\x00'),
                   ('INDEXED', 'UNSUPPORTED', 'Indexed', 'Indexed', 1, b'\x00'),
                   ('SEPARATION', 'UNSUPPORTED', 'Separation', 'Separation', 1, b'\x80'),
                   ('DEVICE_RGB', 'SUPPORTED', 'DeviceRGB', 'DeviceRGB', 3, None),
                   ('DEVICE_GRAY', 'SUPPORTED', 'DeviceGray', 'DeviceGray', 1, b'\x80')]
    records = [resource('COLOR_SPACE', False, [declaration(1, 'ColorSpace/Alias')])]
    for name, (family, status, declared, resolved, components, encoded) in zip(names, definitions):
        record = resource('IMAGE', True, [declaration(1, 'XObject/' + name)])
        record['image'] = image(encoded, None, 1, 1, components=components,
            filters=[filter_info('Crypt')] if name == 'FCrypt' else [],
            color={'family': family, 'status': status, 'declared': declared, 'resolved': resolved,
                   'components': components, 'icc-indirect': '', 'icc-length': '', 'icc-sha256': ''})
        record['image']['decoded']['selected'] = 'false'
        if name == 'FCrypt':
            record['image']['decoded']['availability'] = 'UNSUPPORTED_FILTER'
        if name == 'EExternal':
            record['image']['encoded']['availability'] = 'EXTERNAL_STREAM'
            record['image']['decoded']['availability'] = 'EXTERNAL_STREAM'
        records.append(record)
    alias = resource('IMAGE', True, [declaration(1, 'XObject/GAlias')])
    alias['image'] = image(b'\xff\x00\x00', None, 1, 1)
    alias['image']['color']['declared'] = 'Alias'
    alias['image']['decoded']['selected'] = 'false'
    records.append(alias)
    resources = '<< /ColorSpace << /Alias /DeviceRGB >> /XObject << ' + ' '.join(
        f'/{name} {index + 5} 0 R' for index, name in enumerate(names)) + ' /GAlias 13 0 R >> >>'
    return page_program(resources, '', extra), records, [], ['malformed-color-classification', 'unsupported-icc',
        'indexed-stream-availability', 'separation-availability', 'external-stream-never-resolved', 'crypt-availability',
        'nonconforming-color-alias-metadata']


def write(output):
    output.mkdir(parents=True, exist_ok=False)
    corpus = {'profile': PROFILE, 'sources': {}, 'products': {}}
    for name, create in [('inventory', inventory), ('filters', filters), ('formats', formats),
                         ('colors', colors), ('classifications', classifications)]:
        objects, resources, pages, coverage = create()
        identity = 0
        for record in resources:
            record['identity'] = identity if record['indirect'] == 'true' else ''
            if record['indirect'] == 'true':
                identity += 1
        data = pdf(objects)
        (output / (name + '.pdf')).write_bytes(data)
        # A path/sha256 pair is a repository evidence reference. This filename
        # is relative to the corpus directory and has a distinct field name.
        corpus['sources'][name] = {'file': name + '.pdf', 'sha256': digest(data)}
        product = {'source': name, 'extraction': {'resources': resources}, 'pages': max(1, len(pages)),
                   'byte-access': 'ENCODED' if name == 'classifications' else 'ENCODED_AND_DECODED',
                   'required-chains': ['syntax', 'semantic'] if name == 'classifications'
                       else ['syntax', 'standards', 'semantic', 'visual'],
                   'coverage': coverage, 'visual': []}
        for page, paints in enumerate(pages, 1):
            raster = PIXELS.png(*PIXELS.raster({'crop-box': [0, 0, 120, 100], 'rotation': 0, 'paints': paints}))
            filename = f'{name}-page-{page}.png'
            (output / filename).write_bytes(raster)
            product['visual'].append({'path': filename, 'sha256': digest(raster), 'paints': paints})
        corpus['products'][name] = product
    (output / 'corpus.json').write_text(json.dumps(corpus, indent=2, sort_keys=True) + '\n')
    (output / 'corpus.properties').write_text(PIXELS.properties(corpus))
    # Same valid image declarations with one deliberately changed sample. This
    # is a semantic/renderer negative control, never an expected product.
    altered = filters()[0]
    # Complement red with cyan so all three channels differ by their full range.
    altered[4] = image_stream(bytes.fromhex('00ffff0000ff'))
    (output / 'changed-samples.pdf').write_bytes(pdf(altered))


if __name__ == '__main__':
    write(Path(sys.argv[1]))
