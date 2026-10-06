#!/usr/bin/env python3
"""Author small hostile-input operands from PDF syntax, without a PDF library.

The numeric expectations are declarations made before executing Folio. No
quota search, product parser, backend output or implementation is an oracle.
"""
from pathlib import Path
import hashlib
import json
import sys
import base64
import zlib


def pdf(extra='', objects=(), resources=''):
    bodies = [f'<< /Type /Catalog /Pages 2 0 R {extra} >>',
              '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
              f'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << {resources} >> >>', *objects]
    output = bytearray(b'%PDF-1.7\n%\xe2\xe3\xcf\xd3\n')
    offsets = []
    for number, body in enumerate(bodies, 1):
        offsets.append(len(output))
        output.extend(f'{number} 0 obj\n{body}\nendobj\n'.encode('ascii'))
    start = len(output)
    output.extend(f'xref\n0 {len(bodies) + 1}\n0000000000 65535 f \n'.encode('ascii'))
    for offset in offsets:
        output.extend(f'{offset:010} 00000 n \n'.encode('ascii'))
    output.extend(f'trailer\n<< /Size {len(bodies) + 1} /Root 1 0 R >>\nstartxref\n{start}\n%%EOF\n'.encode('ascii'))
    return bytes(output)


def create(directory):
    directory.mkdir(parents=True, exist_ok=True)
    def payload(encoded, filters):
        return pdf('/Payload 4 0 R', [f'<< /Length {len(encoded)} /Filter {filters} >>\nstream\n{encoded}\nendstream'])
    # Five explicit nine-bit codes: clear, A, B, C, EOD; padding is not data.
    codes = ''.join(f'{value:09b}' for value in (256, 65, 66, 67, 257)) + '000'
    lzw = bytes(int(codes[index:index + 8], 2) for index in range(0, len(codes), 8))
    fixtures = {
        'plain.pdf': pdf(),
        # Trailer (1), indirect Catalog reference (2), Catalog (3), seven arrays (4..10).
        'nested.pdf': pdf('/Payload ' + '[' * 7 + '0' + ']' * 7),
        # AHx emits five bytes; RunLength emits three. Every stage counts.
        'filters.pdf': pdf('/Payload 4 0 R', ['<< /Length 11 /Filter [/ASCIIHexDecode /RunLengthDecode] >>\nstream\n0241424380>\nendstream']),
        'pixels.pdf': pdf('/Payload 4 0 R', ['<< /Type /XObject /Subtype /Image /Width 2 /Height 3 /ColorSpace /DeviceRGB /BitsPerComponent 8 /Length 18 >>\nstream\nabcdefghijklmnopqr\nendstream'], '/XObject << /ImageFixture 4 0 R >>'),
        'malformed.pdf': b'not a PDF\n',
        'unsupported.pdf': pdf('/Payload 4 0 R', ['<< /Length 3 /Filter /UnsupportedT20Filter >>\nstream\nABC\nendstream']),
        'ascii85.pdf': payload(base64.a85encode(b'ABC').decode() + '~>', '/ASCII85Decode'),
        'flate.pdf': payload(zlib.compress(b'ABC').hex() + '>', '[/ASCIIHexDecode /FlateDecode]'),
        'lzw.pdf': payload(lzw.hex() + '>', '[/ASCIIHexDecode /LZWDecode]'),
        'xmp.pdf': pdf('/Metadata 4 0 R', ['<< /Type /Metadata /Subtype /XML /Length 4 >>\nstream\n<x/>\nendstream']),
    }
    for name, data in fixtures.items():
        (directory / name).write_bytes(data)
    (directory / 'fixtures.json').write_text(json.dumps({
        'profile': 'T20-hostile-input-limits',
        'origin': 'Project-authored ISO 32000-1 syntax; Apache-2.0; no implementation-derived fixtures',
        'fixtures': {name: {'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data)}
                     for name, data in fixtures.items()},
        'expectations': {'plain-pages': 1, 'plain-objects': 3, 'nested-depth': 10,
                         'filter-stage-bytes': [5, 3], 'pixels': 6,
                         'ascii85-stage-bytes': [3], 'flate-stage-bytes': [11, 3],
                         'lzw-stage-bytes': [6, 3], 'xmp-query-memory': 8, 'two-xmp-query-memory': 12},
    }, indent=2) + '\n')


if __name__ == '__main__':
    create(Path(sys.argv[1]))
