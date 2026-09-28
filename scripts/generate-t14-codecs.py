#!/usr/bin/env python3
"""Author T14 codec payloads from constant pixels and public format syntax."""
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def strip(path):
    """Read the sole Group 4 strip from the pinned author's little-endian TIFF."""
    data = path.read_bytes()
    if data[:4] != b'II*\x00':
        raise ValueError('Unexpected codec author TIFF encoding')
    offset = struct.unpack_from('<I', data, 4)[0]
    count = struct.unpack_from('<H', data, offset)[0]
    tags = {}
    for index in range(count):
        tag, kind, length, value = struct.unpack_from('<HHII', data, offset + 2 + index * 12)
        if tag in (259, 273, 279):
            if length != 1 or kind not in (3, 4):
                raise ValueError('Unexpected TIFF strip field')
            tags[tag] = value & 65535 if kind == 3 else value
    if tags[259] != 4:
        raise ValueError('The original fax author must use Group 4')
    return data[tags[273]:tags[273] + tags[279]]


def segment(number, kind, payload):
    # T.88 7.2: number, type/flags, no referred segments, page 1, data length.
    return struct.pack('>IBBBI', number, kind, 0, 1, len(payload)) + payload


def generate(output):
    output.mkdir()
    with tempfile.TemporaryDirectory() as temporary:
        work = Path(temporary)
        magick = ROOT / 'scripts/container-bin/imagemagick'
        gray = work / 'gray.pgm'
        gray.write_bytes(b'P5\n8 8\n255\n' + b'\x80' * 64)
        for suffix in ('jpg', 'jp2'):
            subprocess.run([str(magick), str(gray), '-quality', '100', str(output / ('gray.' + suffix))], check=True)
        for name, pixel in (('black', 0), ('white', 255)):
            pgm = work / (name + '.pgm')
            pgm.write_bytes(b'P5\n16 16\n255\n' + bytes([pixel]) * 256)
            subprocess.run([str(magick), str(pgm), '-monochrome', '-compress', 'Group4', str(work / (name + '.tif'))], check=True)
        (output / 'black.ccitt').write_bytes(strip(work / 'black.tif'))
        # T.88 7.4.8 page info and 7.4.6 immediate generic MMR region. TIFF's
        # white run represents set (black) JBIG2 samples with this MMR polarity.
        page = struct.pack('>IIIIBH', 16, 16, 0, 0, 0, 0)
        region = struct.pack('>IIIIBB', 16, 16, 0, 0, 0, 1) + strip(work / 'white.tif')
        (output / 'black.jbig2').write_bytes(segment(0, 48, page) + segment(1, 38, region) + segment(2, 49, b''))
    # ISO 32000 LZW clear code, 64 literal samples, EOD. No dictionary-width
    # transition is reached; the final byte is zero padded.
    bits = ''.join(format(value, '09b') for value in [256] + [128] * 64 + [257])
    bits += '0' * (-len(bits) % 8)
    (output / 'gray.lzw').write_bytes(int(bits, 2).to_bytes(len(bits) // 8, 'big'))
    observed = {'files': {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in sorted(output.iterdir())}}
    expected = json.loads((ROOT / 'capabilities/profiles/T14-images/codecs/payloads.json').read_text())
    if observed != expected:
        raise ValueError('Original codec authoring did not reproduce its frozen payloads')
    (output / 'payloads.json').write_text(json.dumps(observed, indent=2, sort_keys=True) + '\n')


if __name__ == '__main__':
    generate(Path(sys.argv[1]))
