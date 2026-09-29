#!/usr/bin/env python3
"""Author original baseline security fixtures from public PDF algorithms.

Authoring only: cryptography 43.0.0 provides FIPS 197 AES. No Folio, PDFBox,
iText, PDF parser, or product output is used. Fixed credentials/keys below
belong only to this synthetic corpus; evidence reports use case labels.
"""
import hashlib
import json
from pathlib import Path
import struct
import stringprep
import sys
import unicodedata
import zlib
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

PADDING = bytes.fromhex('28bf4e5e4e758a4164004e56fffa01082e2e00b6d0683e802f0ca9fe6453697a')
OWNER = 'baseline-owner'
USER = 'baseline-user'
PAINT = b'0 0 1 rg 12 16 24 20 re f\n'
TITLE = b'Folio baseline proof'


def md5(data): return hashlib.md5(data).digest()
def sha(data): return hashlib.sha256(data).digest()
def pad(password): return (password + PADDING)[:32]
def xor(data, value): return bytes(item ^ value for item in data)


def rc4(key, data):
    state, j = list(range(256)), 0
    for i in range(256):
        j = (j + state[i] + key[i % len(key)]) & 255
        state[i], state[j] = state[j], state[i]
    i, j, result = 0, 0, bytearray()
    for value in data:
        i = (i + 1) & 255
        j = (j + state[i]) & 255
        state[i], state[j] = state[j], state[i]
        result.append(value ^ state[(state[i] + state[j]) & 255])
    return bytes(result)


def aes(key, data, iv=None):
    encryptor = Cipher(algorithms.AES(key), modes.ECB() if iv is None else modes.CBC(iv)).encryptor()
    return encryptor.update(data) + encryptor.finalize()


def prepared(value):
    # RFC 4013, with the RFC 3454 Unicode 3.2 tables, independent of ICU.
    mapped = ''.join(' ' if stringprep.in_table_c12(c) else c for c in value if not stringprep.in_table_b1(c))
    return unicodedata.ucd_3_2_0.normalize('NFKC', mapped).encode('utf8')[:127]


def r6hash(password, salt, user):
    key = sha(password + salt + user)
    turn, encrypted = 0, b'\xff'
    while turn < 64 or encrypted[-1] > turn - 32:
        encrypted = aes(key[:16], (password + key + user) * 64, key[16:32])
        algorithm = (hashlib.sha256, hashlib.sha384, hashlib.sha512)[sum(encrypted[:16]) % 3]
        key = algorithm(encrypted).digest()
        turn += 1
    return key[:32]


def security(name, revision, bits, permissions, owner, user, metadata=True, owner_hash='iso', owner_prepared=True):
    identifier = md5(('T78:' + name).encode())
    if revision <= 4:
        owner_key = md5(pad(owner.encode('latin1')))
        if revision >= 3:
            for _ in range(50): owner_key = md5(owner_key[:bits // 8] if owner_hash == 'interop' else owner_key)
        owner_key = owner_key[:bits // 8]
        o = rc4(owner_key, pad(user.encode('latin1')))
        if revision >= 3:
            for turn in range(1, 20): o = rc4(xor(owner_key, turn), o)
        material = pad(user.encode('latin1')) + o + struct.pack('<i', permissions) + identifier
        if revision >= 4 and not metadata: material += b'\xff' * 4
        key = md5(material)
        if revision >= 3:
            for _ in range(50): key = md5(key[:bits // 8])
        key = key[:bits // 8]
        u = rc4(key, PADDING if revision == 2 else md5(PADDING + identifier))
        if revision >= 3:
            for turn in range(1, 20): u = rc4(xor(key, turn), u)
            u += b'\0' * 16
        entries = {'O': o, 'U': u}
    else:
        key = sha(('file:' + name).encode())
        owner, user = prepared(owner) if owner_prepared else owner.encode('utf8')[:127], prepared(user)
        hash_password = (lambda password, salt, u: sha(password + salt + u)) if revision == 5 else r6hash
        usalt, osalt = sha(('u:' + name).encode())[:16], sha(('o:' + name).encode())[:16]
        u = hash_password(user, usalt[:8], b'') + usalt
        o = hash_password(owner, osalt[:8], u) + osalt
        entries = {'O': o, 'U': u,
                   'UE': aes(hash_password(user, usalt[8:], b''), key, bytes(16)),
                   'OE': aes(hash_password(owner, osalt[8:], u), key, bytes(16)),
                   'Perms': aes(key, struct.pack('<i', permissions) + b'\xff' * 4
                                + (b'T' if metadata else b'F') + b'adb' + b'T78!')}
    return identifier, key, entries


def serialize(version, objects, identifier=None):
    output, offsets = bytearray(('%PDF-' + version + '\n').encode() + b'%\xe2\xe3\xcf\xd3 FolioT78Original\n'), [0]
    for number, value in enumerate(objects, 1):
        offsets.append(len(output))
        output.extend(str(number).encode() + b' 0 obj\n' + value + b'\nendobj\n')
    xref = len(output)
    output.extend(('xref\n0 %d\n0000000000 65535 f \n' % len(offsets)).encode())
    for offset in offsets[1:]: output.extend(('%010d 00000 n \n' % offset).encode())
    trailer = ' /Encrypt 6 0 R /ID [<' + identifier.hex() + '><' + identifier.hex() + '>]' if identifier else ''
    output.extend(('trailer\n<< /Root 1 0 R /Info 5 0 R /Size %d%s >>\nstartxref\n%d\n%%%%EOF\n'
                   % (len(offsets), trailer, xref)).encode())
    return bytes(output)


def document(name, version='1.7', v=0, r=0, bits=0, method=None, permissions=-4,
             owner=OWNER, user=USER, metadata=True, event='DocOpen', eff=False, edit=None,
             owner_hash='iso', owner_prepared=True, stream_crypt=False):
    identifier = None
    extension = ' /Extensions << /ADBE << /BaseVersion /1.7 /ExtensionLevel %d >> >>' % (3 if r == 5 else 8) if r >= 5 and version == '1.7' else ''
    objects = [('<< /Type /Catalog /Pages 2 0 R' + extension + ' >>').encode(),
               b'<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
               b'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 72 72] /Resources << >> /Contents 4 0 R >>']
    if v:
        identifier, file_key, entries = security(name, r, bits, permissions, owner, user, metadata, owner_hash, owner_prepared)
        def encrypt(number, data):
            key = file_key if r >= 5 else md5(file_key + number.to_bytes(3, 'little') + b'\0\0'
                    + (b'sAlT' if method == 'AESV2' else b''))[:min(bits // 8 + 5, 16)]
            if method in ('AESV2', 'AESV3'):
                iv = md5((name + ':' + str(number)).encode())
                padding = 16 - len(data) % 16
                return iv + aes(key, data + bytes([padding]) * padding, iv)
            return rc4(key, data)
        stream, title = encrypt(4, PAINT), encrypt(5, TITLE)
        dictionary = {'Filter': '/Standard', 'V': str(v), 'R': str(r), 'Length': str(bits), 'P': str(permissions)}
        dictionary.update({key: '<' + value.hex() + '>' for key, value in entries.items()})
        if method:
            cf = '/CFM /' + method + ' /Length ' + str(bits // 8)
            if event is not None: cf += ' /AuthEvent /' + event
            dictionary.update(CF='<< /StdCF << ' + cf + ' >> >>', StmF='/StdCF', StrF='/StdCF',
                              EncryptMetadata=str(metadata).lower())
            if eff: dictionary['EFF'] = '/StdCF'
        if edit: edit(dictionary)
        encryption = ('<< ' + ' '.join('/' + key + ' ' + value for key, value in dictionary.items()) + ' >>').encode()
    else:
        stream, title = PAINT, TITLE
    crypt = ' /Filter /Crypt /DecodeParms << /Name /StdCF >>' if stream_crypt else ''
    if stream_crypt == 'array': crypt = ' /Filter [/Crypt] /DecodeParms [<< /Name /StdCF >>]'
    objects.extend([('<< /Length %d%s >>\nstream\n' % (len(stream), crypt)).encode() + stream + b'\nendstream',
                    b'<< /Title <' + title.hex().encode() + b'> >>'])
    if v: objects.append(encryption)
    return serialize(version, objects, identifier)


def png(blank=False):
    width, height = (1224, 1584) if blank else (144, 144)
    rows = []
    for y in range(height):
        rows.append(b'\0' + b''.join(b'\0\0\xff' if not blank and 24 <= x < 72 and 72 <= y < 112 else b'\xff\xff\xff'
                                    for x in range(width)))
    def chunk(kind, data): return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0)) \
        + chunk(b'IDAT', zlib.compress(b''.join(rows), 9)) + chunk(b'IEND', b'')


def products(destination):
    definitions = {}
    def add(name, algorithm='AES_256', mask=-3904, version='1.7', credential='ordinary',
            source='version-1-7.pdf', mode='REWRITE', explicit=True):
        revision = {'NONE': 0, 'RC4_40': 2 if mask & 0xf00 == 0xf00 else 3,
                    'RC4_128': 3, 'AES_128': 4, 'AES_256': 6}[algorithm]
        definitions[name] = dict(algorithm=algorithm, mask=mask, version=version, credential=credential,
                source=source, mode=mode, revision=revision, pages=2 if mode == 'INCREMENTAL' else 1,
                explicit=str(explicit).lower())
    add('default-version', algorithm='NONE', mask=0, explicit=False)
    add('explicit-17', algorithm='NONE', mask=0)
    add('explicit-20', algorithm='NONE', mask=0, version='2.0')
    add('secure-default', explicit=False)
    add('secure-20', mask=-3392, version='2.0')
    for algorithm in ('RC4_40', 'RC4_128', 'AES_128'):
        add(algorithm.lower().replace('_', '-'), algorithm)
    add('rc4-40-r2', 'RC4_40', -4)
    for name, bit in [('print', 4), ('modify', 8), ('extract', 16), ('annotate', 32),
                      ('fill', 256), ('accessibility', 512), ('assemble', 1024), ('faithful', 2048)]:
        add('permission-' + name, mask=-3904 | bit)
    add('unrestricted-user', mask=-4)
    for name in ('empty-user', 'empty-owner', 'equal', 'unicode', 'unicode-127', 'unicode-split', 'bidi', 'long'):
        add('credential-' + name, credential=name)
    add('credential-legacy-32', 'RC4_128', credential='legacy-32')
    add('rewrite-r5', source='aes-256-r5.pdf')
    add('rewrite-r5-pdf20', source='aes-256-r5.pdf', version='2.0', mask=-3392)
    add('rewrite-rc4-cf', source='rc4-cf.pdf')
    add('rewrite-iso40', source='rc4-40-r3.pdf')
    add('rewrite-crypt-scalar', source='aes-128-stream-crypt.pdf')
    add('rewrite-crypt-array', source='aes-128-stream-crypt-array.pdf')
    add('incremental', mask=-4, source='aes-256-r6.pdf', mode='INCREMENTAL')
    (destination / 'products.json').write_text(json.dumps({'profile': 'T32-password-baseline', 'products': definitions,
        'visual': {'dpi': 144, 'metric': 'AE', 'fuzz': 0, 'threshold': 0, 'render-annotations': True}}, sort_keys=True, indent=2) + '\n')
    (destination / 'products.properties').write_text('cases=' + ','.join(definitions) + '\n' + ''.join(
            name + '.' + key + '=' + str(value) + '\n' for name, definition in definitions.items() for key, value in definition.items()))


def build(destination):
    destination.mkdir(parents=True, exist_ok=True)
    cases = {}
    def case(name, expected='success', credential='ordinary', **settings):
        data = document(name, **settings)
        (destination / (name + '.pdf')).write_bytes(data)
        cases[name] = dict(settings, credential=credential, expected=expected, sha256=hashlib.sha256(data).hexdigest())
        for secret in ('owner', 'user', 'edit'): cases[name].pop(secret, None)
    for version in ('1.0', '1.1', '1.2', '1.3', '1.4', '1.5', '1.6', '1.7', '2.0'):
        case('version-' + version.replace('.', '-'), version=version)
    profiles = {
        'rc4-40-r2': dict(version='1.1', v=1, r=2, bits=40, permissions=-4),
        'rc4-40-r3': dict(version='1.4', v=1, r=3, bits=40, permissions=-3904),
        'rc4-128': dict(version='1.4', v=2, r=3, bits=128, permissions=-3904),
        'rc4-cf': dict(version='1.5', v=4, r=4, bits=128, method='V2', permissions=-3904),
        'aes-128': dict(version='1.6', v=4, r=4, bits=128, method='AESV2', permissions=-3904),
        'aes-256-r5': dict(v=5, r=5, bits=256, method='AESV3'),
        'aes-256-r6': dict(v=5, r=6, bits=256, method='AESV3'),
        'aes-256-pdf20': dict(version='2.0', v=5, r=6, bits=256, method='AESV3')}
    for name, settings in profiles.items(): case(name, **settings)
    case('rc4-40-r3-interop', version='1.4', v=1, r=3, bits=40, permissions=-3904, owner_hash='interop')
    case('rc4-40-v2', version='1.4', v=2, r=3, bits=40)
    case('rc4-40-r2-default-length', version='1.1', v=1, r=2, bits=40, edit=lambda d: d.pop('Length'))
    case('rc4-40-v2-default-length', version='1.4', v=2, r=3, bits=40, edit=lambda d: d.pop('Length'))
    case('legacy-literal-question', v=2, r=3, bits=128, user='?')
    case('aes-128-default-event-eff', v=4, r=4, bits=128, method='AESV2', event=None, eff=True)
    case('aes-128-stream-crypt', v=4, r=4, bits=128, method='AESV2', stream_crypt=True)
    case('aes-128-stream-crypt-array', v=4, r=4, bits=128, method='AESV2', stream_crypt='array')
    case('aes-128-metadata-clear', v=4, r=4, bits=128, method='AESV2', metadata=False)
    case('aes-256-r5-prepared', v=5, r=5, bits=256, method='AESV3', owner='owner-IX', user='IX')
    case('aes-256-r6-prepared', v=5, r=6, bits=256, method='AESV3', owner='owner-IX', user='IX')
    case('aes-256-r5-noncanonical-owner', v=5, r=5, bits=256, method='AESV3', owner='I\u00adX', user='IX',
         owner_prepared=False, permissions=-3904)
    for name, kind, owner, user in [
            ('empty-user', 'empty-user', OWNER, ''), ('equal', 'equal', 'equal-baseline', 'equal-baseline'),
            ('boundary-split', 'unicode-split', OWNER, 'a' * 126 + '\u00e9'),
            ('empty-owner', 'input-empty-owner', '', USER)]:
        case('aes-256-r5-' + name, credential=kind, v=5, r=5, bits=256, method='AESV3', owner=owner, user=user)
    case('aes-256-r6-bidi', credential='bidi', v=5, r=6, bits=256, method='AESV3', user='\u05d0\u05d1')
    for name in ('rc4-40-r3', 'rc4-128', 'aes-128'):
        case(name + '-byte-boundary', credential='legacy-original-32',
             user='\u0080' + '\u00e9' * 32, **profiles[name])
    for name, settings in profiles.items():
        if name != 'aes-256-pdf20': case(name + '-pdf20', **dict(settings, version='2.0'))
    case('aes-256-pdf20-bit10-clear', version='2.0', v=5, r=6, bits=256, method='AESV3', permissions=-3904)
    case('aes-256-perms-invalid', expected='PASSWORD_SECURITY_UNSUPPORTED', v=5, r=6, bits=256, method='AESV3',
         edit=lambda d: d.update(Perms='<' + '00' * 16 + '>'))
    case('aes-256-r5-perms-invalid', expected='PASSWORD_SECURITY_UNSUPPORTED', v=5, r=5, bits=256, method='AESV3',
         edit=lambda d: d.update(Perms='<' + '00' * 16 + '>'))
    case('aes-128-efopen', expected='PASSWORD_SECURITY_UNSUPPORTED', v=4, r=4, bits=128, method='AESV2', event='EFOpen')
    (destination / 'expected.png').write_bytes(png())
    (destination / 'blank.png').write_bytes(png(True))
    (destination / 'cases.json').write_text(json.dumps(cases, sort_keys=True, indent=2) + '\n')
    (destination / 'cases.properties').write_text(''.join(name + '=' + value['expected'] + '\n' for name, value in sorted(cases.items())))
    products(destination)
    (destination / 'manifest.sha256').write_text(''.join(hashlib.sha256(path.read_bytes()).hexdigest() + '  ' + path.name + '\n'
            for path in sorted(destination.iterdir()) if path.name != 'manifest.sha256'))


if __name__ == '__main__':
    # NIST SP 800-38A AES-256 CBC first block; validates authoring primitive.
    assert aes(bytes.fromhex('603deb1015ca71be2b73aef0857d77811f352c073b6108d72d9810a30914dff4'),
               bytes.fromhex('6bc1bee22e409f96e93d7e117393172a'), bytes.fromhex('000102030405060708090a0b0c0d0e0f')) \
        == bytes.fromhex('f58c4c04d6e5f1ba779eabfb5f7bfbd6')
    build(Path(sys.argv[1] if len(sys.argv) > 1 else 'capabilities/profiles/T78-password'))
