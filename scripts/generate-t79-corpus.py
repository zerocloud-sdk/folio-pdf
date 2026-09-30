#!/usr/bin/env python3
"""Original clear-metadata fixtures, using the project's independent T78 author.

Only authoring shares the public-algorithm helpers. Observers never import this
module or the T78 author. All passwords and deterministic keys are synthetic.
"""
import hashlib
import importlib.util
import json
from pathlib import Path
import struct

SPEC = importlib.util.spec_from_file_location('t78_author', Path(__file__).with_name('generate-t78-corpus.py'))
AUTHOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUTHOR)
ROOT = Path(__file__).resolve().parents[1]
DESTINATION = ROOT / 'capabilities/profiles/T79-clear-metadata'
XMP = (b'<?xpacket begin="\xef\xbb\xbf" id="W5M0MpCehiHzreSzNTczkc9d"?>\n'
       b'<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">'
       b'<rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/">'
       b'<dc:title><rdf:Alt><rdf:li xml:lang="x-default">Folio clear XMP proof</rdf:li></rdf:Alt></dc:title>'
       b'</rdf:Description></rdf:RDF></x:xmpmeta>\n<?xpacket end="w"?>')
ATTACHMENT = b'Folio protected embedded data: metadata-like values remain private.\n'
KEYWORDS = b'Folio protected metadata-like Info value'


def document(name, version='1.7', v=5, r=6, bits=256, method='AESV3', permissions=-3904,
             owner=AUTHOR.OWNER, user=AUTHOR.USER, metadata=False, edit=None, xmp=XMP,
             encrypt_xmp=False, clear_content=False, key_metadata=None, perms_byte=None,
             cf_length=True, event=True, eff=False, stream_crypt=False, metadata_crypt=None,
             metadata_type="Metadata", metadata_subtype="XML", metadata_reference="7 0 R", paint=AUTHOR.PAINT, component=True, clear_component=False, clear_metadata_string=False, component_crypt=None):
    identifier = None
    if v:
        identifier, key, entries = AUTHOR.security('T79-' + name, r, bits, permissions, owner, user,
                                                  metadata if key_metadata is None else key_metadata)
        if perms_byte is not None:
            block = bytearray(struct.pack('<i', permissions) + b'\xff' * 4 + (b'T' if metadata else b'F') + b'adbT79!')
            block[perms_byte] ^= 1
            entries['Perms'] = AUTHOR.aes(key, bytes(block))
        def encrypt(number, value):
            object_key = key if r >= 5 else AUTHOR.md5(key + number.to_bytes(3, 'little') + b'\0\0'
                    + (b'sAlT' if method == 'AESV2' else b''))[:min(bits // 8 + 5, 16)]
            if method in ('AESV2', 'AESV3'):
                iv = AUTHOR.md5(('T79:' + name + ':' + str(number)).encode())
                count = 16 - len(value) % 16
                return iv + AUTHOR.aes(object_key, value + bytes([count]) * count, iv)
            return AUTHOR.rc4(object_key, value)
        dictionary = dict(Filter='/Standard', V=str(v), R=str(r), Length=str(bits), P=str(permissions))
        dictionary.update({field: '<' + value.hex() + '>' for field, value in entries.items()})
        if method:
            cf = '/CFM /' + method
            if cf_length: cf += ' /Length ' + str(bits // 8)
            if event: cf += ' /AuthEvent /DocOpen'
            dictionary.update(CF='<< /StdCF << ' + cf + ' >> >>', StmF='/StdCF', StrF='/StdCF',
                              EncryptMetadata=str(metadata).lower())
            if eff: dictionary['EFF'] = '/StdCF'
        if edit: edit(dictionary)
        encryption = ('<< ' + ' '.join('/' + field + ' ' + value for field, value in dictionary.items()) + ' >>').encode()
    else:
        encrypt = lambda number, value: value
        encryption = b'<< >>'
    def string(number, value): return b'<' + encrypt(number, value).hex().encode() + b'>'
    def stream(number, value, extra=b'', clear=False):
        data = value if clear else encrypt(number, value)
        return b'<< /Length ' + str(len(data)).encode() + extra + b' >>\nstream\n' + data + b'\nendstream'
    extension = (' /Extensions << /ADBE << /BaseVersion /1.7 /ExtensionLevel %d >> >>' % (3 if r == 5 else 8)
                 if r >= 5 and v and version == '1.7' else '').encode()
    crypt = b' /Filter /Crypt /DecodeParms << /Name /StdCF >>' if stream_crypt else b''
    if isinstance(stream_crypt, str) and stream_crypt.startswith(' /'): crypt = stream_crypt.encode()
    if stream_crypt == 'array': crypt = b' /Filter [/Crypt] /DecodeParms [<< /Name /StdCF >>]'
    objects = [b'<< /Type /Catalog /Pages 2 0 R /Metadata ' + metadata_reference.encode() + b' /Names << /EmbeddedFiles << /Names ['
               + string(1, b'proof.txt') + b' 8 0 R] >> >>' + extension + b' >>',
               b'<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
               b'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 72 72] /Resources << >> /Contents 4 0 R'
               + (b' /Metadata 10 0 R' if component else b'') + b' >>',
               stream(4, paint, crypt, clear_content),
               b'<< /Title ' + string(5, AUTHOR.TITLE) + b' /Keywords ' + string(5, KEYWORDS) + b' >>',
               encryption,
               stream(7, xmp, (' /Type /' + metadata_type + ' /Subtype /' + metadata_subtype).encode()
                      + b' /FolioProof ' + (b'<' + KEYWORDS.hex().encode() + b'>' if clear_metadata_string else string(7, KEYWORDS))
                      + (metadata_crypt.encode() if metadata_crypt else b''), not (metadata or encrypt_xmp)),
               b'<< /Type /Filespec /F ' + string(8, b'proof.txt') + b' /EF << /F 9 0 R >> >>',
               stream(9, ATTACHMENT, b' /Type /EmbeddedFile /Subtype /text#2Fplain'),
               stream(10, XMP.replace(b'clear XMP', b'protected component'), b' /Type /Metadata /Subtype /XML' + (component_crypt.encode() if component_crypt else b''), clear_component)]
    return AUTHOR.serialize(version, objects, identifier)


def write_properties(path, definitions):
    path.write_text('cases=' + ','.join(definitions) + '\n')
    with path.open('a') as stream:
        for name, definition in definitions.items():
            for key, value in definition.items():
                stream.write(name + '.' + key + '=' + (str(value).lower() if isinstance(value, bool) else str(value)) + '\n')


def build():
    DESTINATION.mkdir(parents=True, exist_ok=True)
    (DESTINATION / 'metadata.xmp').write_bytes(XMP)
    (DESTINATION / 'attachment.txt').write_bytes(ATTACHMENT)
    cases = {}
    def case(name, expected='success', credential='ordinary', **settings):
        data = document(name, **settings)
        (DESTINATION / (name + '.pdf')).write_bytes(data)
        definition = dict(version='1.7', v=5, r=6, bits=256, method='AESV3', permissions=-3904, metadata=False,
                          **{})
        definition.update(settings)
        definition.update(expected=expected, credential=credential, sha256=AUTHOR.sha(data).hex())
        for private in ('owner', 'user', 'edit'): definition.pop(private, None)
        cases[name] = definition
    case('plain', v=0, r=0, bits=0, method=None, permissions=0)
    profiles = {
        'rc4-cf': dict(v=4, r=4, bits=128, method='V2', version='1.5'),
        'aes-128': dict(v=4, r=4, bits=128, method='AESV2', version='1.6'),
        'aes-256-r5': dict(r=5), 'aes-256-r6': {}, 'aes-256-pdf20': dict(version='2.0', permissions=-3392)}
    for name, settings in profiles.items():
        case(name, **settings)
        if name != 'aes-256-pdf20': case(name + '-pdf20', **dict(settings, version='2.0'))
    for name in ('rc4-cf', 'aes-128'):
        case(name + '-defaults', **profiles[name], cf_length=False, event=False, eff=True)
        case(name + '-boundary', **profiles[name], credential='legacy-32', user='\u00e9' * 33)
    for name, credential, user in [('empty', 'empty-user', ''), ('unicode', 'unicode', 'I\u00adX\u00a0\u2168'),
                                    ('split', 'unicode-split', 'a' * 126 + '\u00e9'), ('long', 'long', 'a' * 128)]:
        for r in (5, 6): case('aes-256-r%d-%s' % (r, name), r=r, credential=credential, user=user)
    case('aes-256-equal', credential='equal', owner='equal-baseline', user='equal-baseline')
    case('aes-256-unrestricted', permissions=-4)
    case('aes-128-crypt-scalar', v=4, r=4, bits=128, method='AESV2', stream_crypt=True)
    case('aes-128-crypt-array', v=4, r=4, bits=128, method='AESV2', stream_crypt='array')
    for form, declaration in [('scalar', ' /Filter /Crypt /DecodeParms << /Name /Identity >>'),
                              ('array', ' /Filter [/Crypt] /DecodeParms [<< /Name /Identity >>]'),
                              ('default', ' /Filter /Crypt /DecodeParms << >>'),
                              ('array-default', ' /Filter [/Crypt] /DecodeParms [<< >>]'),
                              ('array-null', ' /Filter [/Crypt] /DecodeParms [null]'),
                              ('omitted', ' /Filter /Crypt'),
                              ('array-omitted', ' /Filter [/Crypt]')]:
        case('metadata-crypt-' + form, metadata_crypt=declaration)
    case('component-crypt', component_crypt=' /Filter /Crypt /DecodeParms << /Name /StdCF >>')
    (DESTINATION / 'all-content-metadata-stdcf.pdf').write_bytes(document('all-content-metadata-stdcf', metadata=True,
            metadata_crypt=' /Filter /Crypt /DecodeParms << /Name /StdCF >>'))
    (DESTINATION / 'plain-identity.pdf').write_bytes(document('plain-identity', v=0, r=0, bits=0, method=None, permissions=0,
            stream_crypt=' /Filter /Crypt /DecodeParms << /Name /Identity >>'))
    (DESTINATION / 'plain-malformed-crypt.pdf').write_bytes(document('plain-malformed-crypt', v=0, r=0, bits=0, method=None, permissions=0,
            stream_crypt=' /Filter /Crypt /DecodeParms << /Name /Unknown >>'))
    for name, settings in [('catalog-metadata-type', {'metadata_type': 'NotMetadata'}),
                           ('catalog-metadata-subtype', {'metadata_subtype': 'NotXML'}),
                           ('catalog-metadata-nonstream', {'metadata_reference': '42'}),
                           ('metadata-crypt-stdcf', {'metadata_crypt': ' /Filter /Crypt /DecodeParms << /Name /StdCF >>'}),
                           ('metadata-crypt-duplicate', {'metadata_crypt': ' /Filter [/Crypt /Crypt] /DecodeParms [<< /Name /Identity >> << /Name /Identity >>]'}),
                           ('metadata-crypt-parameters', {'metadata_crypt': ' /Filter [/Crypt] /DecodeParms << /Name /Identity >>'})]:
        case(name, expected='PASSWORD_SECURITY_UNSUPPORTED', **settings)
    for name, edit in [('metadata-type', lambda d: d.update(EncryptMetadata='/False')),
                       ('strings-clear', lambda d: d.update(StrF='/Identity')),
                       ('streams-clear', lambda d: d.update(StmF='/Identity')),
                       ('embedded-clear', lambda d: d.update(EFF='/Identity'))]:
        case(name, expected='PASSWORD_SECURITY_UNSUPPORTED', edit=edit)
    for offset in range(12): case('perms-' + str(offset), expected='PASSWORD_SECURITY_UNSUPPORTED', perms_byte=offset)
    case('rc4-wrong-metadata-key', expected='CREDENTIAL_REJECTED', v=4, r=4, bits=128, method='V2', key_metadata=True)
    case('aes128-wrong-metadata-key', expected='CREDENTIAL_REJECTED', v=4, r=4, bits=128, method='AESV2', key_metadata=True)
    (DESTINATION / 'merge-donor.pdf').write_bytes(document('merge-donor', component=False))
    (DESTINATION / 'cases.json').write_text(json.dumps(cases, indent=2, sort_keys=True) + '\n')
    write_properties(DESTINATION / 'cases.properties', cases)
    definitions = {}
    def product(name, algorithm='AES_256', version='1.7', source='plain.pdf', mask=-3904,
                credential='ordinary', mode='REWRITE', metadata=False):
        definitions[name] = dict(algorithm=algorithm, version=version, source=source, mask=mask, credential=credential,
            mode=mode, metadata=metadata, revision=6 if algorithm == 'AES_256' else 4,
            v=5 if algorithm == 'AES_256' else 4, pages=2 if mode == 'INCREMENTAL' else 1, explicit=True)
    product('secure-17')
    product('secure-20', version='2.0', mask=-3392)
    product('rc4-128', algorithm='RC4_128')
    product('aes-128', algorithm='AES_128')
    for name in ('empty-user', 'empty-owner', 'equal', 'unicode', 'unicode-127', 'unicode-split', 'bidi', 'long'):
        product('credential-' + name, credential=name)
    product('legacy-32', algorithm='RC4_128', credential='legacy-32')
    for bit in (4, 8, 16, 32, 256, 512, 1024, 2048): product('permission-' + str(bit), mask=-3904 | bit)
    product('unrestricted', mask=-4)
    for name in profiles:
        product('rewrite-' + name, source=name + '.pdf', version='2.0' if name == 'aes-256-pdf20' else '1.7',
                mask=-3392 if name == 'aes-256-pdf20' else -3904)
    product('rewrite-r5-pdf20', source='aes-256-r5.pdf', version='2.0', mask=-3392)
    product('rewrite-strengthened', source='aes-256-r6.pdf', metadata=True)
    product('rewrite-stdcf-all-content', source='all-content-metadata-stdcf.pdf', metadata=True)
    product('rewrite-stdcf-clear', source='all-content-metadata-stdcf.pdf')
    product('rewrite-component-crypt', source='component-crypt.pdf')
    product('create-from-identity', source='plain-identity.pdf')
    product('rewrite-identity', source='metadata-crypt-array.pdf')
    product('rewrite-identity-strengthened', source='metadata-crypt-scalar.pdf', metadata=True)
    for name in ('rc4-cf', 'aes-128', 'aes-256-r5', 'aes-256-r6'):
        case(name + '-assembly', **dict(profiles[name], version='1.7', permissions=-4))
        product('incremental-' + name, source=name + '-assembly.pdf', mask=-4, mode='INCREMENTAL',
                algorithm='RC4_128' if name == 'rc4-cf' else 'AES_128' if name == 'aes-128' else 'AES_256')
        definitions['incremental-' + name]['revision'] = profiles[name].get('r', 6)
    (DESTINATION / 'cases.json').write_text(json.dumps(cases, indent=2, sort_keys=True) + '\n')
    write_properties(DESTINATION / 'cases.properties', cases)
    (DESTINATION / 'products.json').write_text(json.dumps({'profile': 'T32-password-clear-metadata', 'products': definitions,
        'visual': {'dpi': 144, 'metric': 'AE', 'fuzz': 0, 'threshold': 0, 'render-annotations': True}}, indent=2, sort_keys=True) + '\n')
    write_properties(DESTINATION / 'products.properties', definitions)
    (DESTINATION / 'manifest.sha256').write_text(''.join(hashlib.sha256(path.read_bytes()).hexdigest() + '  ' + path.name + '\n'
        for path in sorted(DESTINATION.iterdir()) if path.name != 'manifest.sha256'))


if __name__ == '__main__': build()
