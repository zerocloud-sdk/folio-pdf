#!/usr/bin/env python3
"""Original single-defect controls for the frozen clear-metadata profile."""
import hashlib
import importlib.util
import json
from pathlib import Path
import re

SPEC = importlib.util.spec_from_file_location('t79_author', Path(__file__).with_name('generate-t79-corpus.py'))
AUTHOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUTHOR)
ROOT = Path(__file__).resolve().parents[1]
DESTINATION = ROOT / 'capabilities/profiles/T79-controls'


def build():
    DESTINATION.mkdir(parents=True, exist_ok=True)
    rules = {}
    def retain(name, data, finding, producer='arlington', model='output'):
        (DESTINATION / (name + '.pdf')).write_bytes(data)
        rules[name] = {'file': name + '.pdf', 'sha256': hashlib.sha256(data).hexdigest(), 'finding': finding,
                       'producer': producer, 'model': model,
                       'source': 'T79 profile audit CM-FILTERS/CM-R4-KEY/CM-PERMS/CM-PWORD/CM-CRYPT; ISO 32000-1 7.6, 14.3; ISO 32000-2 EC3 7.6'}
    def defect(name, field, value=None, remove=False, settings=None, finding=None, model='output'):
        def edit(dictionary):
            if remove: dictionary.pop(field, None)
            else: dictionary[field] = value(dictionary[field]) if callable(value) else value
        retain(name, AUTHOR.document('control', version='2.0', permissions=-4, edit=edit, **(settings or {})),
               finding or field + ' (EncryptionStandard)', model=model)
    for field, value in [('Filter', '42'), ('V', '/Five'), ('R', '/Six'), ('Length', '/Long'), ('P', '/Permissions'),
                         ('EncryptMetadata', '/False'), ('CF', '42'), ('StmF', '42'), ('StrF', '42'), ('EFF', '42')]:
        defect(field.lower() + '-type', field, value)
    defect('filter-handler', 'Filter', '/Unknown')
    defect('subfilter-present', 'SubFilter', '/adbe.pkcs7.s5', finding='Filter (EncryptionStandard)')
    for field in ('Filter', 'V', 'R', 'Length', 'P', 'CF', 'StmF', 'StrF', 'O', 'U', 'OE', 'UE', 'Perms', 'EncryptMetadata'):
        defect(field.lower() + '-absent', field, remove=True,
               finding='Filter (EncryptionStandard)' if field in ('CF', 'StmF', 'StrF', 'OE', 'UE', 'Perms') else None)
    for name, field, wrong, finding in [('tuple-v2', 'V', '2', None), ('tuple-v5-r4', 'R', '4', None),
            ('r5-output', 'R', '5', None), ('global-length-bits', 'Length', '128', None),
            ('metadata-encrypted', 'EncryptMetadata', 'true', None)]:
        defect(name, field, wrong, finding=finding)
    for field in ('StmF', 'StrF', 'EFF'): defect(field.lower() + '-identity', field, '/Identity')
    for field in ('O', 'U', 'OE', 'UE', 'Perms'):
        defect(field.lower() + '-short', field, lambda old: old[:-3] + '>')
        defect(field.lower() + '-long', field, lambda old: old[:-1] + '00>')
        defect(field.lower() + '-type', field, '42')
    for bit in (1, 2, 7, 8, 13, 32):
        defect('p-reserved-' + str(bit), 'P', lambda old, bit=bit: str(int(old) ^ (1 << (bit - 1))))
    defect('p-bit10-output', 'P', lambda old: str(int(old) & ~512))
    for name, change, finding in [
            ('cf-method', lambda value: value.replace('/AESV3', '/AESV2'), 'CFM (CryptFilter)'),
            ('cf-length-bits', lambda value: value.replace('/Length 32', '/Length 256'), 'Length (CryptFilter)'),
            ('cf-length-absent', lambda value: value.replace('/Length 32', ''), 'CFM (CryptFilter)'),
            ('cf-length-type', lambda value: value.replace('/Length 32', '/Length /Long'), 'Length (CryptFilter)'),
            ('cf-event', lambda value: value.replace('/DocOpen', '/EFOpen'), 'AuthEvent (CryptFilter)'),
            ('cf-event-type', lambda value: value.replace('/DocOpen', '42'), 'AuthEvent (CryptFilter)'),
            ('cf-method-absent', lambda value: value.replace('/CFM /AESV3', ''), 'CFM (CryptFilter)'),
            ('cf-stdcf-absent', lambda value: '<< >>', 'StdCF (CryptFilterMap)'),
            ('cf-extra', lambda value: value[:-2] + ' /Custom << /CFM /AESV3 /Length 32 >> >>', "unknown key 'Custom' is not defined in Arlington for CryptFilterMap")]:
        defect(name, 'CF', change, finding=finding)
    for revision in (5, 6):
        for offset in range(12):
            retain('r%d-perms-%d' % (revision, offset), AUTHOR.document('control', version='2.0', r=revision, permissions=-4, perms_byte=offset),
                   'perms-verification-failed', 'pypdf', 'input' if revision == 5 else 'output')
    for name, settings in {'rc4': dict(v=4, r=4, bits=128, method='V2'),
                           'aes128': dict(v=4, r=4, bits=128, method='AESV2')}.items():
        retain(name + '-metadata-key', AUTHOR.document(name, key_metadata=True, **settings),
               'authentication-rejected', 'pypdf-authentication')
        retain(name + '-length-bits', AUTHOR.document(name, edit=lambda d: d.update(CF=d['CF'].replace('/Length 16', '/Length 128')), **settings),
               'Length (CryptFilter)')
    for revision in (4, 5, 6):
        settings = dict(v=4, r=4, bits=128, method='AESV2') if revision == 4 else dict(r=revision)
        retain('p-range-r' + str(revision), AUTHOR.document('range', edit=lambda d: d.update(P='4294963392'), **settings),
               'out of signed 32-bit range', 'pdfcpu')
    for name, version, settings, finding in [
            ('rc4-before15', '1.4', dict(v=4, r=4, bits=128, method='V2'), 'V (EncryptionStandard)'),
            ('aes128-before16', '1.5', dict(v=4, r=4, bits=128, method='AESV2'), 'CFM (CryptFilter)'),
            ('aes256-before17', '1.6', {}, 'V (EncryptionStandard)')]:
        retain(name, AUTHOR.document(name, version=version, **settings), finding, model='input')
    def objects(data): return re.findall(rb'\d+ 0 obj\n(.*?)\nendobj\n', data, re.S)
    source = AUTHOR.document('extension')
    original = objects(source)
    for name, transform in [('extension-absent', lambda data: re.sub(rb' /Extensions << /ADBE <<.*?>> >>', b'', data)),
                             ('extension-level', lambda data: data.replace(b'/ExtensionLevel 8', b'/ExtensionLevel 7')),
                             ('extension-base', lambda data: data.replace(b'/BaseVersion /1.7', b'/BaseVersion /1.6'))]:
        changed = list(original); changed[0] = transform(changed[0])
        retain(name, AUTHOR.AUTHOR.serialize('1.7', changed, AUTHOR.AUTHOR.md5(b'T78:T79-extension')), 'V (EncryptionStandard)')
    for field, value in [('Type', b'/NotMetadata'), ('Subtype', b'/NotXML')]:
        changed = list(original)
        changed[6] = changed[6].replace(b'/' + field.encode() + (b' /Metadata' if field == 'Type' else b' /XML'), b'/' + field.encode() + b' ' + value)
        retain('metadata-' + field.lower(), AUTHOR.AUTHOR.serialize('1.7', changed, AUTHOR.AUTHOR.md5(b'T78:T79-extension')),
               field + ' (DocumentMetadata)')
    retain('metadata-bytes-encrypted', AUTHOR.document('encrypted-xmp', encrypt_xmp=True),
           'unauthenticated-metadata-matches', 'bytes')
    retain('metadata-bytes-changed', AUTHOR.document('changed-xmp', xmp=AUTHOR.XMP.replace(b'clear XMP', b'other XMP')),
           'unauthenticated-metadata-matches', 'bytes')
    retain('ordinary-stream-clear', AUTHOR.document('clear-stream', clear_content=True), 'content-matches', 'bytes')
    for name, declaration, finding in [
            ('name-stdcf', ' /Filter /Crypt /DecodeParms << /Name /StdCF >>', 'Name (FilterMetadataCrypt)'),
            ('name-unknown', ' /Filter /Crypt /DecodeParms << /Name /Unknown >>', 'Name (FilterMetadataCrypt)'),
            ('duplicate', ' /Filter [/Crypt /Crypt] /DecodeParms [<< /Name /Identity >> << /Name /Identity >>]', 'ArrayOfMetadataFilters'),
            ('parameters', ' /Filter [/Crypt] /DecodeParms << /Name /Identity >>', 'Filter (DocumentMetadata)'),
            ('second', ' /Filter [/FlateDecode /Crypt] /DecodeParms [null << /Name /Identity >>]', 'ArrayOfMetadataFilters')]:
        retain('metadata-crypt-' + name, AUTHOR.document('crypt-defect', metadata_crypt=declaration), finding)
    retain('component-crypt-identity', AUTHOR.document('component-identity', component_crypt=' /Filter /Crypt /DecodeParms << /Name /Identity >>', clear_component=True), 'Filter (Metadata)')
    retain('component-crypt-name-missing', AUTHOR.document('component-default', component_crypt=' /Filter /Crypt /DecodeParms << >>', clear_component=True), 'Filter (Metadata)')
    retain('component-metadata-clear', AUTHOR.document('component-clear', clear_component=True), 'component-metadata-matches', 'bytes')
    retain('metadata-dictionary-string-clear', AUTHOR.document('dictionary-clear', clear_metadata_string=True),
           'metadata-dictionary-string-matches', 'bytes')
    retain('clear-scope-changed-paint', AUTHOR.document('changed-paint', paint=AUTHOR.AUTHOR.PAINT.replace(b'0 0 1 rg', b'1 0 0 rg')),
           'authored-pixel-grid', 'visual')
    (DESTINATION / 'rules.json').write_text(json.dumps({'profile': 'T32-password-clear-metadata',
        'required-rules': sorted(rules), 'rules': rules}, indent=2, sort_keys=True) + '\n')
    (DESTINATION / 'manifest.sha256').write_text(''.join(hashlib.sha256(path.read_bytes()).hexdigest() + '  ' + path.name + '\n'
        for path in sorted(DESTINATION.iterdir()) if path.name != 'manifest.sha256'))


if __name__ == '__main__': build()
