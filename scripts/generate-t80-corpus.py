#!/usr/bin/env python3
"""Original EF-only fixtures from public algorithms, independent of the product.

The project-owned T78 author supplies primitive algorithms only. No observer
imports this author, and all credentials and deterministic keys are synthetic.
"""
import importlib.util
import json
from pathlib import Path
import struct
import zlib

SPEC = importlib.util.spec_from_file_location('t78_author', Path(__file__).with_name('generate-t78-corpus.py'))
AUTHOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUTHOR)
ROOT = Path(__file__).resolve().parents[1]
DESTINATION = ROOT / 'capabilities/profiles/T80-embedded-files-only'
ATTACHMENT = b'Folio T80 private embedded payload: exact detached bytes.\n'
TITLE = b'Folio T80 clear document title'
PAINT = AUTHOR.PAINT
XMP = (b'<?xpacket begin="\xef\xbb\xbf" id="W5M0MpCehiHzreSzNTczkc9d"?>'
       b'<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">'
       b'<rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/">'
       b'<dc:title><rdf:Alt><rdf:li xml:lang="x-default">Folio T80 clear XMP</rdf:li></rdf:Alt></dc:title>'
       b'</rdf:Description></rdf:RDF></x:xmpmeta><?xpacket end="w"?>')


def document(name, version='1.7', v=5, r=6, bits=256, method='AESV3', permissions=-3904,
             owner=AUTHOR.OWNER, user=AUTHOR.USER, metadata=False, edit=None,
             event='EFOpen', eff='StdCF', defaults=False, typed=True,
             attachment_crypt=None, ordinary_crypt=None, compressed=False,
             clear_attachment=False, encrypted_page=False, encrypted_title=False,
             alias=False, perms_byte=None, cf_length=True, extra_object=None,
             catalog_version=None, file_type='/Filespec', embedded_type=None, extensions=None):
    identifier = None
    if v:
        identifier, key, entries = AUTHOR.security('T80-' + name, r, bits, permissions, owner, user, metadata)
        if perms_byte is not None:
            block = bytearray(struct.pack('<i', permissions) + b'\xff' * 4 + (b'T' if metadata else b'F') + b'adbT80!')
            block[perms_byte] ^= 1
            entries['Perms'] = AUTHOR.aes(key, bytes(block))
        def encrypt(number, value):
            object_key = key if r >= 5 else AUTHOR.md5(key + number.to_bytes(3, 'little') + b'\0\0'
                    + (b'sAlT' if method == 'AESV2' else b''))[:min(bits // 8 + 5, 16)]
            if method in ('AESV2', 'AESV3'):
                iv = AUTHOR.md5(('T80:' + name + ':' + str(number)).encode())
                count = 16 - len(value) % 16
                return iv + AUTHOR.aes(object_key, value + bytes([count]) * count, iv)
            return AUTHOR.rc4(object_key, value)
        cf = '/CFM /' + method
        if cf_length: cf += ' /Length ' + str(bits // 8)
        if event is not None: cf += ' /AuthEvent /' + event
        dictionary = dict(Filter='/Standard', V=str(v), R=str(r), Length=str(bits), P=str(permissions),
                          CF='<< /StdCF << ' + cf + ' >> >>', EncryptMetadata=str(metadata).lower())
        if not defaults: dictionary.update(StmF='/Identity', StrF='/Identity')
        if eff is not None: dictionary['EFF'] = '/' + eff
        dictionary.update({field: '<' + value.hex() + '>' for field, value in entries.items()})
        if edit: edit(dictionary)
        encryption = ('<< ' + ' '.join('/' + field + ' ' + value for field, value in dictionary.items()) + ' >>').encode()
    else:
        encrypt = lambda number, value: value
        encryption = b'<< >>'
    def string(value): return b'<' + value.hex().encode() + b'>'
    def stream(number, value, extra=b'', protected=False):
        data = encrypt(number, value) if protected else value
        return b'<< /Length ' + str(len(data)).encode() + extra + b' >>\nstream\n' + data + b'\nendstream'
    effective = max(version, catalog_version[1:]) if catalog_version and catalog_version.startswith('/') else version
    extension = (' /Extensions << /ADBE << /BaseVersion /1.7 /ExtensionLevel %d >> >>' % (3 if r == 5 else 8)
                 if r >= 5 and v and effective == '1.7' else '').encode()
    if extensions is not None: extension = (' /Extensions ' + extensions).encode()
    declaration = (' /Version ' + catalog_version).encode() if catalog_version else b''
    extra = b' /Subtype /text#2Fplain' + (b' /Type /EmbeddedFile' if typed else b'')
    if embedded_type is not None: extra = b' /Subtype /text#2Fplain /Type ' + embedded_type.encode()
    data = zlib.compress(ATTACHMENT) if compressed else ATTACHMENT
    if compressed: extra += b' /Filter /FlateDecode'
    if attachment_crypt: extra += attachment_crypt.encode()
    ordinary = ordinary_crypt.encode() if ordinary_crypt else b''
    objects = [b'<< /Type /Catalog /Pages 2 0 R /Metadata 7 0 R /Names << /EmbeddedFiles << /Names ['
               + string(b'proof.txt') + b' 8 0 R] >> >>' + extension + declaration + b' >>',
               b'<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
               b'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 72 72] /Resources << >> /Contents '
               + (b'9 0 R' if alias else b'4 0 R') + b' >>',
               stream(4, PAINT, ordinary, encrypted_page),
               b'<< /Title ' + string(encrypt(5, TITLE) if encrypted_title else TITLE) + b' >>',
               encryption,
               stream(7, XMP, b' /Type /Metadata /Subtype /XML /FolioProof ' + string(TITLE)),
               b'<< /Type ' + file_type.encode() + b' /F ' + string(b'proof.txt')
               + (b' /UF ' + string(b'proof.txt') if version >= '1.7' else b'')
               + b' /EF << /F 9 0 R' + (b' /UF 9 0 R' if version >= '1.7' else b'') + b' >> >>',
               stream(9, data, extra, v != 0 and not clear_attachment),
               # A suggestive type that has no EF relationship remains ordinary data.
               stream(10, b'Folio T80 clear unbound stream', b' /Type /EmbeddedFile')]
    if extra_object is not None: objects.append(extra_object.encode())
    return AUTHOR.serialize(version, objects, identifier)


def write_properties(path, definitions):
    path.write_text('cases=' + ','.join(definitions) + '\n')
    with path.open('a') as stream:
        for name, definition in definitions.items():
            for key, value in definition.items():
                if isinstance(value, (str, int, bool)):
                    stream.write(name + '.' + key + '=' + (str(value).lower() if isinstance(value, bool) else str(value)) + '\n')


def build():
    DESTINATION.mkdir(parents=True, exist_ok=True)
    (DESTINATION / 'attachment.txt').write_bytes(ATTACHMENT)
    (DESTINATION / 'metadata.xmp').write_bytes(XMP)
    cases = {}
    def case(name, expected='success', credential='ordinary', **settings):
        data = document(name, **settings)
        (DESTINATION / (name + '.pdf')).write_bytes(data)
        definition = dict(version='1.7', v=5, r=6, bits=256, method='AESV3', permissions=-3904,
                          metadata=False, event='EFOpen', eff='StdCF', pages=1)
        definition.update(settings)
        if settings.get('catalog_version', '').startswith('/'):
            definition['version'] = max(definition['version'], settings['catalog_version'][1:])
        definition.update(expected=expected, credential=credential, sha256=AUTHOR.sha(data).hex())
        for private in ('owner', 'user', 'edit'): definition.pop(private, None)
        cases[name] = definition
    case('plain', v=0, r=0, bits=0, method=None, permissions=0)
    profiles = {'rc4-cf': dict(v=4, r=4, bits=128, method='V2', version='1.6'),
                'aes-128': dict(v=4, r=4, bits=128, method='AESV2', version='1.6'),
                'aes-256-r5': dict(r=5), 'aes-256-r6': {},
                'aes-256-pdf20': dict(version='2.0', permissions=-3392)}
    scalar = ' /Filter /Crypt /DecodeParms << /Name /StdCF >>'
    array = ' /Filter [/Crypt] /DecodeParms [<< /Name /StdCF >>]'
    case('rc4-explicit-pdf15-assembly', v=4, r=4, bits=128, method='V2', version='1.5', eff=None, attachment_crypt=scalar, permissions=-4)
    case('aes-256-explicit-assembly', eff='Identity', attachment_crypt=scalar, permissions=-4)
    case('rc4-explicit-pdf15', v=4, r=4, bits=128, method='V2', version='1.5', eff=None, attachment_crypt=scalar)
    for name, settings in profiles.items():
        case(name, **settings)
        case(name + '-untyped', **settings, typed=False)
        case(name + '-defaults', **settings, defaults=True)
        case(name + '-crypt-scalar', **settings, attachment_crypt=scalar)
        case(name + '-crypt-array', **settings, attachment_crypt=array, eff=None)
        case(name + '-eff-identity', **settings, attachment_crypt=scalar, eff='Identity')
        case(name + '-assembly', **dict(settings, permissions=-4))
        if name != 'aes-256-pdf20':
            case(name + '-pdf20', **dict(settings, version='2.0'))
    case('typed-crypt', edit=lambda d:d.update(CF='<< /StdCF << /Type /CryptFilter /CFM /AESV3 /Length 32 /AuthEvent /EFOpen >> >>'), attachment_crypt=' /Filter /Crypt /DecodeParms << /Type /CryptFilterDecodeParms /Name /StdCF >>')
    case('indirect-filter', edit=lambda d:d.update(CF='<< /StdCF 11 0 R >>'), extra_object='<< /CFM /AESV3 /Length 32 /AuthEvent /EFOpen >>')
    case('indirect-crypt', attachment_crypt=' /Filter /Crypt /DecodeParms 11 0 R', extra_object='<< /Type /CryptFilterDecodeParms /Name /StdCF >>')
    case('indirect-extensions', extensions='11 0 R', extra_object='<< /ADBE << /BaseVersion /1.7 /ExtensionLevel 8 >> >>')
    case('indirect-adobe-extension', extensions='<< /ADBE 11 0 R >>', extra_object='<< /BaseVersion /1.7 /ExtensionLevel 8 >>')
    case('indirect-extension-base', extensions='<< /ADBE << /BaseVersion 11 0 R /ExtensionLevel 8 >> >>', extra_object='/1.7')
    case('indirect-extension-level', extensions='<< /ADBE << /BaseVersion /1.7 /ExtensionLevel 11 0 R >> >>', extra_object='8')
    case('typed-extensions', extensions='<< /Type /Extensions /ADBE << /Type /DeveloperExtensions /BaseVersion /1.7 /ExtensionLevel 8 >> >>')
    case('aes-256-pdf17-filter-length-default', cf_length=False)
    case('aes-256-catalog-pdf20', catalog_version='/2.0')
    case('aes-256-compressed', compressed=True)
    case('aes-256-metadata-true', metadata=True)
    case('aes-256-docopen', event='DocOpen', credential='equal', owner='equal-baseline', user='equal-baseline')
    case('aes-256-default-event', event=None, credential='equal', owner='equal-baseline', user='equal-baseline')
    for name, user in [('empty', ''), ('unicode', 'I\u00adX\u00a0\u2168'), ('unicode-split', 'a' * 126 + '\u00e9'), ('long', 'a' * 128)]:
        for r in (5, 6): case('aes-256-r%d-%s' % (r, name), r=r, credential=name, user=user)
    for name in ('rc4-cf', 'aes-128'):
        case(name + '-boundary', **profiles[name], credential='legacy-32', user='\u00e9' * 33)
        case(name + '-filter-length-default', **profiles[name], cf_length=False)
    case('aes-256-extraction', permissions=-3888)
    case('aes-256-equal', credential='equal', owner='equal-baseline', user='equal-baseline')
    case('ordinary-identity', ordinary_crypt=' /Filter /Crypt /DecodeParms << /Name /Identity >>')
    case('ordinary-default-crypt', ordinary_crypt=' /Filter /Crypt')
    negatives = [('bad-filter-map', dict(edit=lambda d:d.update(CF='<< /StdCF << /CFM /AESV3 /Length 32 /AuthEvent /EFOpen >> /Other << /CFM /AESV3 >> >>'))),
                 ('bad-extension-base-type', dict(extensions='<< /ADBE << /BaseVersion (/1.7) /ExtensionLevel 8 >> >>')),
                 ('bad-extension-level-real', dict(extensions='<< /ADBE << /BaseVersion /1.7 /ExtensionLevel 8.0 >> >>')),
                 ('bad-extension-level-string', dict(extensions='<< /ADBE << /BaseVersion /1.7 /ExtensionLevel (8) >> >>')),
                 ('bad-extensions-stream', dict(extensions='11 0 R', extra_object='<< /ADBE << /BaseVersion /1.7 /ExtensionLevel 8 >> /Length 0 >>\nstream\n\nendstream')),
                 ('bad-adobe-extension-stream', dict(extensions='<< /ADBE 11 0 R >>', extra_object='<< /BaseVersion /1.7 /ExtensionLevel 8 /Length 0 >>\nstream\n\nendstream')),
                 ('bad-extensions-type', dict(extensions='<< /Type /Wrong /ADBE << /BaseVersion /1.7 /ExtensionLevel 8 >> >>')),
                 ('bad-extensions-name-type', dict(extensions='<< /Type (Extensions) /ADBE << /BaseVersion /1.7 /ExtensionLevel 8 >> >>')),
                 ('bad-adobe-extension-type', dict(extensions='<< /ADBE << /Type /Wrong /BaseVersion /1.7 /ExtensionLevel 8 >> >>')),
                 ('bad-adobe-extension-name-type', dict(extensions='<< /ADBE << /Type (DeveloperExtensions) /BaseVersion /1.7 /ExtensionLevel 8 >> >>')),
                 ('bad-filter-stream', dict(edit=lambda d:d.update(CF='<< /StdCF 11 0 R >>'), extra_object='<< /CFM /AESV3 /Length 32 /AuthEvent /EFOpen >>\nstream\n0123456789abcdef0123456789abcdef\nendstream')),
                 ('bad-crypt-stream', dict(attachment_crypt=' /Filter /Crypt /DecodeParms 11 0 R', extra_object='<< /Name /StdCF /Length 0 >>\nstream\n\nendstream')),
                 ('bad-pdf20-filter-length-missing', dict(version='2.0', cf_length=False)),
                 ('bad-catalog-pdf20-filter-length', dict(catalog_version='/2.0', cf_length=False)),
                 ('bad-filespec-name-type', dict(file_type='(Filespec)')),
                 ('bad-embedded-name-type', dict(embedded_type='(EmbeddedFile)')),
                 ('bad-crypt-name-type', dict(attachment_crypt=' /Filter /Crypt /DecodeParms << /Name (StdCF) >>')),
                 ('bad-crypt-parameter-name-type', dict(attachment_crypt=' /Filter /Crypt /DecodeParms << /Type (CryptFilterDecodeParms) /Name /StdCF >>')),
                 ('bad-filter-type', dict(edit=lambda d:d.update(CF='<< /StdCF << /Type /Wrong /CFM /AESV3 /Length 32 /AuthEvent /EFOpen >> >>'))),
                 ('bad-crypt-type', dict(attachment_crypt=' /Filter /Crypt /DecodeParms << /Type /Wrong /Name /StdCF >>')), ('bad-eff', dict(edit=lambda d: d.update(EFF='/Unknown'))),
                 ('bad-eff-type', dict(edit=lambda d: d.update(EFF='(StdCF)'))),
                 ('bad-event', dict(event='Unexpected')),
                 ('event-docopen-routing', dict(edit=lambda d: d.update(StmF='/StdCF'))),
                 ('bad-string-route', dict(edit=lambda d: d.update(StrF='/StdCF'))),
                 ('bad-filter-length', dict(edit=lambda d: d.update(CF='<< /StdCF << /CFM /AESV3 /Length 256 /AuthEvent /EFOpen >> >>'))),
                 ('bad-filter-length-overflow', dict(edit=lambda d: d.update(CF='<< /StdCF << /CFM /AESV3 /Length 4294967328 /AuthEvent /EFOpen >> >>'))),
                 ('bad-cfm', dict(edit=lambda d: d.update(CF='<< /StdCF << /CFM /AESV2 /AuthEvent /EFOpen >> >>'))),
                 ('bad-revision', dict(edit=lambda d: d.update(R='4'))),
                 ('bad-auth-length', dict(edit=lambda d: d.update(U='<00>'))),
                 ('bad-permission-type', dict(edit=lambda d: d.update(P='(-3904)'))),
                 ('bad-perms', dict(perms_byte=9)),
                 ('bad-metadata-agreement', dict(edit=lambda d: d.update(EncryptMetadata='true'))),
                 ('attachment-identity', dict(attachment_crypt=' /Filter /Crypt /DecodeParms << /Name /Identity >>')),
                 ('attachment-default-identity', dict(attachment_crypt=' /Filter /Crypt')),
                 ('missing-eff-protection', dict(eff=None)),
                 ('ordinary-stdcf', dict(ordinary_crypt=scalar)),
                 ('unknown-crypt', dict(attachment_crypt=' /Filter /Crypt /DecodeParms << /Name /Unknown >>')),
                 ('duplicate-crypt', dict(attachment_crypt=' /Filter [/Crypt /Crypt] /DecodeParms [<< /Name /StdCF >> << /Name /StdCF >>]')),
                 ('late-crypt', dict(attachment_crypt=' /Filter [/FlateDecode /Crypt] /DecodeParms [null << /Name /StdCF >>]')),
                 ('bad-crypt-parameters', dict(attachment_crypt=' /Filter [/Crypt] /DecodeParms << /Name /StdCF >>')),
                 ('aliased-protected-content', dict(alias=True)),
                 ('version-too-early', dict(version='1.6'))]
    for name, settings in negatives: case(name, expected='PASSWORD_SECURITY_UNSUPPORTED', **settings)
    case('bad-catalog-version-type', expected='PDF_VERSION_INVALID', catalog_version='(2.0)')
    (DESTINATION / 'cases.json').write_text(json.dumps(cases, sort_keys=True, indent=2) + '\n')
    write_properties(DESTINATION / 'cases.properties', cases)
    products = {}
    def product(name, **settings):
        definition = dict(algorithm='AES_256', version='1.7', credential='ordinary', source='plain.pdf',
                          mask=-3904, pages=1, mode='REWRITE', explicit=True, metadata=False,
                          scope='EMBEDDED_FILES_ONLY', v=5, revision=6)
        definition.update(settings)
        if definition['algorithm'] == 'AES_128': definition.update(v=4, revision=4)
        products[name] = definition
    product('secure-default')
    product('aes128-legacy', algorithm='AES_128')
    product('rc4-native-legacy', algorithm='RC4_128', apis='native', v=4, revision=4)
    product('secure-pdf20', version='2.0', mask=-3392)
    for kind in ('empty-user', 'equal', 'unicode', 'unicode-split', 'long', 'legacy-32'):
        product('credential-' + kind, credential=kind, algorithm='AES_128' if kind == 'legacy-32' else 'AES_256')
    for bit in (4, 8, 16, 32, 256, 512, 1024, 2048): product('permission-' + str(bit), mask=-3904 | bit)
    product('unrestricted', mask=-4)
    for name in profiles:
        product('rewrite-' + name, source=name + '.pdf', version='2.0' if name == 'aes-256-pdf20' else '1.7',
                mask=-3392 if name == 'aes-256-pdf20' else -3904)
    product('rewrite-r5-pdf20', source='aes-256-r5.pdf', version='2.0', mask=-3392)
    product('rewrite-explicit-crypt', source='aes-256-r6-crypt-array.pdf')
    product('rewrite-untyped', source='aes-256-r6-untyped.pdf')
    # Reference Suite suppresses the RC4 embedded-only selector. Native RC4
    # output is independently exercised by the Workflow products, separately
    # from matching Facade profiles; it is not silently forced through Facade.
    for name in ('aes-128', 'aes-256-r5', 'aes-256-r6'):
        product('incremental-' + name, source=name + '-assembly.pdf', mask=-4, mode='INCREMENTAL',
                algorithm='AES_128' if name == 'aes-128' else 'AES_256', pages=2, replaceAttachment=True)
        products['incremental-' + name]['version'] = profiles[name].get('version', '1.7')
        products['incremental-' + name]['revision'] = profiles[name].get('r', 6)
    product('incremental-explicit-crypt', source='aes-256-explicit-assembly.pdf', mode='INCREMENTAL', mask=-4, pages=2, replaceAttachment=True)
    product('incremental-rc4-native', source='rc4-explicit-pdf15-assembly.pdf', mode='INCREMENTAL', mask=-4, pages=2, replaceAttachment=True, algorithm='RC4_128', v=4, revision=4, version='1.5', apis='native')
    (DESTINATION / 'products.json').write_text(json.dumps({'profile': 'T32-password-embedded-files-only', 'products': products,
        'visual': {'dpi': 144, 'metric': 'AE', 'fuzz': 0, 'threshold': 0, 'render-annotations': True}}, sort_keys=True, indent=2) + '\n')
    write_properties(DESTINATION / 'products.properties', products)
    import hashlib
    (DESTINATION / 'manifest.sha256').write_text(''.join(hashlib.sha256(path.read_bytes()).hexdigest() + '  ' + path.name + '\n'
        for path in sorted(DESTINATION.iterdir()) if path.name != 'manifest.sha256'))


if __name__ == '__main__': build()
