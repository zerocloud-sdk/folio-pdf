#!/usr/bin/env python3
"""Original single-defect controls; no validator or product is an authoring oracle."""
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import struct
import zlib

SPEC = importlib.util.spec_from_file_location('author', Path(__file__).with_name('generate-t78-corpus.py'))
AUTHOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUTHOR)
ROOT = Path(__file__).resolve().parents[1]
DESTINATION = ROOT / 'capabilities/profiles/T78-controls'


def build():
    DESTINATION.mkdir(exist_ok=True)
    rules = {}
    base = dict(version='2.0', v=5, r=6, bits=256, method='AESV3')

    def retain(name, data, finding, producer='arlington', model='output'):
        (DESTINATION / (name + '.pdf')).write_bytes(data)
        rules[name] = {'file': name + '.pdf', 'sha256': hashlib.sha256(data).hexdigest(),
                       'producer': producer, 'finding': finding, 'model': model,
                       'source': 'ISO 32000-1:2008 7.6 / ISO 32000-2:2020 EC3 7.6; T78 closed profile audit'}

    def defect(name, field, value=None, remove=False, settings=None, model='output'):
        def edit(dictionary):
            if remove: dictionary.pop(field, None)
            else: dictionary[field] = value(dictionary[field]) if callable(value) else value
        retain(name, AUTHOR.document('aes-256-pdf20', edit=edit, **(settings or base)),
               field + ' (EncryptionStandard)', model=model)

    for field, wrong in [('Filter', '42'), ('V', '/Five'), ('R', '/Six'), ('Length', '/Long'),
                         ('P', '/Permissions'), ('EncryptMetadata', '/True'), ('CF', '42'),
                         ('StmF', '42'), ('StrF', '42'), ('EFF', '42')]:
        defect(field.lower() + '-type', field, wrong)
    defect('filter-handler', 'Filter', '/Unknown')
    defect('subfilter-present', 'SubFilter', '/adbe.pkcs7.s5')
    for name, field, wrong in [('tuple-v2-r6', 'V', '2'), ('tuple-v5-r4', 'R', '4'), ('tuple-v9', 'V', '9'),
                             ('global-length-bits', 'Length', '128'),
                             ('metadata-clear-output', 'EncryptMetadata', 'false')]:
        defect(name, field, wrong)
    retain('p-range', AUTHOR.document('aes-256-pdf20', edit=lambda d: d.update(P='4294967292'), **base),
           'out of signed 32-bit range', 'pdfcpu')
    for name, settings in {
            'r2': dict(version='1.1', v=1, r=2, bits=40),
            'r3-40': dict(version='1.4', v=1, r=3, bits=40, permissions=-3904),
            'r3-128': dict(version='1.4', v=2, r=3, bits=128),
            'v4-rc4': dict(version='1.5', v=4, r=4, bits=128, method='V2'),
            'aes128': dict(version='1.6', v=4, r=4, bits=128, method='AESV2'),
            'r5': dict(v=5, r=5, bits=256, method='AESV3'),
            'r6-17': dict(v=5, r=6, bits=256, method='AESV3')}.items():
        retain('p-range-' + name, AUTHOR.document('p-range-' + name,
            edit=lambda d: d.update(P=str(int(d['P']) + (1 << 32))), **settings),
            'out of signed 32-bit range', 'pdfcpu')
    for field in ('Filter', 'V', 'R', 'Length', 'P', 'CF', 'StmF', 'StrF', 'O', 'U', 'OE', 'UE', 'Perms'):
        defect(field.lower() + '-absent', field, remove=True)
    for name in ('cf-absent', 'stmf-absent', 'strf-absent', 'oe-absent', 'ue-absent', 'perms-absent', 'subfilter-present'):
        rules[name]['finding'] = 'Filter (EncryptionStandard)'
    rules['tuple-v2-r6']['finding'] = 'R (EncryptionStandard)'
    for field in ('StmF', 'StrF', 'EFF'):
        defect(field.lower() + '-identity', field, '/Identity')
    for field in ('O', 'U', 'OE', 'UE', 'Perms'):
        defect(field.lower() + '-short', field, lambda old: old[:-3] + '>')
        defect(field.lower() + '-long', field, lambda old: old[:-1] + '00>')
        defect(field.lower() + '-type', field, '42')
    for bit in (1, 2, 7, 8, 13, 32):
        defect('p-reserved-' + str(bit), 'P', lambda old, bit=bit: str(int(old) ^ (1 << (bit - 1))))
    defect('p-bit10-output', 'P', lambda old: str(int(old) & ~512))
    defect('r2-reserved9', 'P', '-260', settings=dict(version='1.1', v=1, r=2, bits=40))
    for name, changed in [('cf-method', lambda old: old.replace('/AESV3', '/AESV2')),
                          ('cf-length-bits', lambda old: old.replace('/Length 32', '/Length 256')),
                          ('cf-length-absent', lambda old: old.replace('/Length 32', '')),
                          ('cf-length-type', lambda old: old.replace('/Length 32', '/Length /Long')),
                          ('cf-event', lambda old: old.replace('/DocOpen', '/EFOpen')),
                          ('cf-event-type', lambda old: old.replace('/DocOpen', '42')),
                          ('cf-method-absent', lambda old: old.replace('/CFM /AESV3', '')),
                          ('cf-stdcf-absent', lambda old: '<< >>'),
                          ('cf-extra', lambda old: old[:-2] + ' /Custom << /CFM /AESV3 /Length 32 >> >>')]:
        defect(name, 'CF', changed)
        rules[name]['finding'] = {'cf-event': 'AuthEvent (CryptFilter)', 'cf-event-type': 'AuthEvent (CryptFilter)',
            'cf-length-bits': 'Length (CryptFilter)', 'cf-length-type': 'Length (CryptFilter)',
            'cf-length-absent': 'CFM (CryptFilter)', 'cf-method': 'CFM (CryptFilter)',
            'cf-method-absent': 'CFM (CryptFilter)', 'cf-stdcf-absent': 'StdCF (CryptFilterMap)',
            'cf-extra': "unknown key 'Custom' is not defined in Arlington for CryptFilterMap"}[name]
    for bit in (0, 4, 5, 6, 7, 8, 9):
        # ISO2 Algorithm 10: fixed first12 bytes; last4 deliberately unconstrained.
        block = bytearray(struct.pack('<i', -4) + b'\xff' * 4 + b'Tadb' + b'T78!')
        block[bit] ^= 1
        key = hashlib.sha256(b'file:aes-256-pdf20').digest()
        value = '<' + AUTHOR.aes(key, bytes(block)).hex() + '>'
        data = AUTHOR.document('aes-256-pdf20', edit=lambda d, value=value: d.update(Perms=value), **base)
        retain('perms-plaintext-' + str(bit), data, 'perms-verification-failed', 'pypdf')

    source = AUTHOR.document('aes-256-r6', v=5, r=6, bits=256, method='AESV3')
    objects = re.findall(rb'\d+ 0 obj\n(.*?)\nendobj\n', source, re.S)
    for label, alter in {
        'extension-missing': lambda x: re.sub(rb' /Extensions << /ADBE <<.*?>> >>', b'', x),
        'extension-level': lambda x: x.replace(b'/ExtensionLevel 8', b'/ExtensionLevel 7'),
        'extension-base': lambda x: x.replace(b'/BaseVersion /1.7', b'/BaseVersion /1.6')}.items():
        copy = list(objects); copy[0] = alter(copy[0])
        retain(label, AUTHOR.serialize('1.7', copy, AUTHOR.md5(b'T78:aes-256-r6')), 'V (EncryptionStandard)')
    retain('aes128-version15', AUTHOR.document('aes128-version15', version='1.5', v=4, r=4, bits=128, method='AESV2'), 'CFM (CryptFilter)')
    retain('rc4-r3-version13', AUTHOR.document('rc4-r3-version13', version='1.3', v=1, r=3, bits=40,
        permissions=-3904), 'V (EncryptionStandard)')
    defect('v5-aesv2-consistent', 'CF', lambda old: old.replace('/AESV3', '/AESV2').replace('/Length 32', '/Length 16'))
    rules['v5-aesv2-consistent']['finding'] = 'CFM (CryptFilter)'
    retain('normative-r3-output-owner', AUTHOR.document('normative-r3-output-owner', version='1.7',
        v=1, r=3, bits=40, permissions=-3904, owner_hash='interop'), 'owner-authentication-failed', 'pypdf-owner')

    source = AUTHOR.document('crypt-filter-order', v=4, r=4, bits=128, method='AESV2', stream_crypt=True)
    objects = re.findall(rb'\d+ 0 obj\n(.*?)\nendobj\n', source, re.S)
    for label, filters, params in [
            ('crypt-array-second', b'[/ASCIIHexDecode /Crypt]', b'[null << /Name /StdCF >>]'),
            ('crypt-array-duplicate', b'[/Crypt /Crypt]', b'[<< /Name /StdCF >> << /Name /StdCF >>]'),
            ('crypt-name-identity', b'/Crypt', b'<< /Name /Identity >>'),
            ('crypt-name-missing', b'/Crypt', b'<< >>'),
            ('crypt-array-identity', b'[/Crypt]', b'[<< /Name /Identity >>]'),
            ('crypt-array-name-missing', b'[/Crypt]', b'[<< >>]'),
            ('crypt-array-params-missing', b'[/Crypt]', b'[]'),
            ('crypt-array-params-null', b'[/Crypt]', b'[null]')]:
        copy = list(objects)
        copy[3] = copy[3].replace(b'/Filter /Crypt /DecodeParms << /Name /StdCF >>', b'/Filter ' + filters + b' /DecodeParms ' + params)
        finding = '1* (ArrayOfFilterNames)' if label in ('crypt-array-second', 'crypt-array-duplicate') else 'Filter (Stream)'
        retain(label, AUTHOR.serialize('1.7', copy, AUTHOR.md5(b'T78:crypt-filter-order')), finding)

    (DESTINATION / 'syntax-truncated.pdf').write_bytes(b'%PDF-1.7\n1 0 obj\n<<\n')
    pixels = b''.join(b'\0' + b''.join(b'\0\0\0' if x == 0 and y == 0 else
        b'\0\0\xff' if 24 <= x < 72 and 72 <= y < 112 else b'\xff\xff\xff'
        for x in range(144)) for y in range(144))
    def chunk(kind, data): return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    (DESTINATION / 'one-pixel.png').write_bytes(b'\x89PNG\r\n\x1a\n' +
        chunk(b'IHDR', struct.pack('>IIBBBBB', 144, 144, 8, 2, 0, 0, 0)) +
        chunk(b'IDAT', zlib.compress(pixels, 9)) + chunk(b'IEND', b''))
    previous = AUTHOR.PAINT
    try:
        AUTHOR.PAINT = b'0 0 1 rg 13 16 24 20 re f\n'
        (DESTINATION / 'changed-paint.pdf').write_bytes(AUTHOR.document('changed-paint', v=5, r=6, bits=256, method='AESV3', permissions=-3904))
    finally: AUTHOR.PAINT = previous
    (DESTINATION / 'rules.json').write_text(json.dumps({'profile': 'T32-password-baseline',
        'required-rules': sorted(rules), 'rules': rules}, sort_keys=True, indent=2) + '\n')
    (DESTINATION / 'manifest.sha256').write_text(''.join(hashlib.sha256(path.read_bytes()).hexdigest() + '  ' + path.name + '\n'
        for path in sorted(DESTINATION.iterdir()) if path.name != 'manifest.sha256'))


if __name__ == '__main__': build()
