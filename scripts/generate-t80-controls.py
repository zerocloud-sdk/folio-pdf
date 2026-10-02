#!/usr/bin/env python3
"""Original single-defect EFF controls, independently replayed by pinned tools."""
import hashlib
import importlib.util
import json
from pathlib import Path
import re

SPEC = importlib.util.spec_from_file_location('t80_author', Path(__file__).with_name('generate-t80-corpus.py'))
AUTHOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUTHOR)
ROOT = Path(__file__).resolve().parents[1]
DESTINATION = ROOT / 'capabilities/profiles/T80-controls'


def build():
    DESTINATION.mkdir(parents=True, exist_ok=True)
    rules = {}
    def retain(name, data, finding, producer='arlington', model='input'):
        (DESTINATION / (name + '.pdf')).write_bytes(data)
        rules[name] = {'file': name + '.pdf', 'sha256': hashlib.sha256(data).hexdigest(), 'finding': finding,
                       'producer': producer, 'model': model,
                       'source': 'T80 profile audit; ISO 32000-1 7.6.3-7.6.5, Tables 20-26, 43-44; ISO 32000-2 EC3 7.6'}
    def defect(name, field, value, finding=None, model='input', **settings):
        def edit(dictionary):
            if value is None: dictionary.pop(field, None)
            else: dictionary[field] = value(dictionary.get(field)) if callable(value) else value
        retain(name, AUTHOR.document('control-' + name, edit=edit, **settings), finding or field + ' (EncryptionStandard)', model=model)
    for field, value in [('Filter','/Other'),('SubFilter','/Unsupported'),('V','4'),('V','/Five'),('R','4'),('R','/Six'),
                         ('Length','128'),('Length','/Long'),('P','(-3904)'),('EncryptMetadata','(false)'),
                         ('StmF','/StdCF'),('StrF','/StdCF'),('EFF','/Unknown'),('EFF','(StdCF)')]:
        defect(field.lower() + '-' + str(len(rules)), field, value)
    for field in ('StmF','StrF','EFF','EncryptMetadata'):
        defect('output-' + field.lower() + '-missing', field, None, model='output')
    defect('output-metadata-true','EncryptMetadata','true',model='output')
    defect('output-eff-identity','EFF','/Identity',model='output')
    for field in ('O','U','OE','UE','Perms'):
        defect(field.lower()+'-short',field,lambda old:old[:-3]+'>')
        defect(field.lower()+'-long',field,lambda old:old[:-1]+'00>')
        defect(field.lower()+'-type',field,'42')
    for bit in (1,2,7,8,13,32):
        defect('p-reserved-'+str(bit),'P',lambda old,bit=bit:str(int(old)^(1<<(bit-1))))
    defect('pdf20-output-bit10','P',lambda old:str(int(old)&~512),model='output',version='2.0')
    for name, change, finding in [
        ('type',lambda value:value.replace('/CFM /AESV3','/Type /Wrong /CFM /AESV3'),'Type'),
        ('map-extra',lambda value:value[:-2]+' /Other << /CFM /AESV3 >> >>','StdCF'),
        ('method',lambda value:value.replace('/AESV3','/AESV2'),'CFM'),
        ('method-missing',lambda value:value.replace('/CFM /AESV3',''),'CFM'),
        ('method-type',lambda value:value.replace('/AESV3','32'),'CFM'),
        ('length-bits',lambda value:value.replace('/Length 32','/Length 256'),'Length'),
        ('length-overflow',lambda value:value.replace('/Length 32','/Length 4294967328'),'Length'),
        ('length-type',lambda value:value.replace('/Length 32','/Length /Long'),'Length'),
        ('event',lambda value:value.replace('/EFOpen','/Unexpected'),'AuthEvent'),
        ('event-type',lambda value:value.replace('/EFOpen','42'),'AuthEvent'),
        ('stdcf-missing',lambda value:'<< >>','StdCF')]:
        defect('cf-'+name,'CF',change,finding+' (CryptFilterMap)' if finding=='StdCF' else finding+' (CryptFilter)')
    for event_rule in ('cf-event', 'cf-event-type'):
        rules[event_rule].update(producer='pypdf-dictionary')
    defect('output-event-docopen','CF',lambda value:value.replace('/EFOpen','/DocOpen'),'AuthEvent (CryptFilter)',model='output')
    defect('cf-stdcf-stream','CF','<< /StdCF 11 0 R >>','StdCF (CryptFilterMap)',
           extra_object='<< /CFM /AESV3 /Length 32 /AuthEvent /EFOpen >>\nstream\n0123456789abcdef0123456789abcdef\nendstream')
    defect('pdf20-cf-length-missing','CF',lambda value:value.replace('/Length 32',''),
           'Length (CryptFilter)',version='2.0')
    defect('catalog-pdf20-cf-length-missing','CF',lambda value:value.replace('/Length 32',''),
           'Length (CryptFilter)',catalog_version='/2.0')
    retain('catalog-version-type',AUTHOR.document('catalog-version-type',catalog_version='(2.0)'),
           'Version (Catalog)','pypdf-dictionary')
    for name,settings,finding in [
        ('extension-base-string',dict(extensions='<< /ADBE << /BaseVersion (/1.7) /ExtensionLevel 8 >> >>'),'BaseVersion (DeveloperExtensions)'),
        ('extension-level-real',dict(extensions='<< /ADBE << /BaseVersion /1.7 /ExtensionLevel 8.0 >> >>'),'ExtensionLevel (DeveloperExtensions)'),
        ('extension-level-string',dict(extensions='<< /ADBE << /BaseVersion /1.7 /ExtensionLevel (8) >> >>'),'ExtensionLevel (DeveloperExtensions)'),
        ('extensions-stream',dict(extensions='11 0 R',extra_object='<< /ADBE << /BaseVersion /1.7 /ExtensionLevel 8 >> /Length 0 >>\nstream\n\nendstream'),'Extensions (Catalog)'),
        ('adobe-extension-stream',dict(extensions='<< /ADBE 11 0 R >>',extra_object='<< /BaseVersion /1.7 /ExtensionLevel 8 /Length 0 >>\nstream\n\nendstream'),'ADBE (Extensions)')]:
        retain(name,AUTHOR.document(name,**settings),finding,'pypdf-dictionary')
    for name,extensions,finding in [
        ('extensions-type','<< /Type /Wrong /ADBE << /BaseVersion /1.7 /ExtensionLevel 8 >> >>','Type (Extensions)'),
        ('extensions-name-type','<< /Type (Extensions) /ADBE << /BaseVersion /1.7 /ExtensionLevel 8 >> >>','Type (Extensions)'),
        ('adobe-extension-type','<< /ADBE << /Type /Wrong /BaseVersion /1.7 /ExtensionLevel 8 >> >>','Type (DeveloperExtensions)'),
        ('adobe-extension-name-type','<< /ADBE << /Type (DeveloperExtensions) /BaseVersion /1.7 /ExtensionLevel 8 >> >>','Type (DeveloperExtensions)')]:
        retain(name,AUTHOR.document(name,extensions=extensions),finding,'pypdf-dictionary')
    for name, rule in rules.items():
        if rule['producer'] == 'arlington' and not name.startswith('output-'): rule.update(producer='pypdf-dictionary')
    for revision in (5,6):
        for offset in range(12):
            retain('r%d-perms-%d'%(revision,offset),AUTHOR.document('perms',r=revision,perms_byte=offset),
                   'perms-verification-failed','pypdf')
    for name, settings in [('rc4',dict(v=4,r=4,bits=128,method='V2')),('aes128',dict(v=4,r=4,bits=128,method='AESV2'))]:
        retain(name+'-metadata-key',AUTHOR.document(name,edit=lambda d:d.update(EncryptMetadata='true'),**settings),
               'authentication-rejected','pypdf-authentication')
    for name, settings, predicate in [
        ('clear-attachment',dict(clear_attachment=True),'attachment-ciphertext-differs'),
        ('encrypted-page',dict(encrypted_page=True),'unauthenticated-page-matches'),
        ('encrypted-title',dict(encrypted_title=True),'unauthenticated-title-matches'),
        ('crypt-parameter-type',dict(attachment_crypt=' /Filter /Crypt /DecodeParms << /Type /Wrong /Name /StdCF >>'),'routes-consistent'),
        ('crypt-parameter-stream',dict(attachment_crypt=' /Filter /Crypt /DecodeParms 11 0 R',extra_object='<< /Name /StdCF /Length 0 >>\nstream\n\nendstream'),'routes-consistent'),
        ('filespec-name-type',dict(file_type='(Filespec)'),'routes-consistent'),
        ('embedded-name-type',dict(embedded_type='(EmbeddedFile)'),'routes-consistent'),
        ('crypt-name-type',dict(attachment_crypt=' /Filter /Crypt /DecodeParms << /Name (StdCF) >>'),'routes-consistent'),
        ('crypt-parameter-name-type',dict(attachment_crypt=' /Filter /Crypt /DecodeParms << /Type (CryptFilterDecodeParms) /Name /StdCF >>'),'routes-consistent'),
        ('ordinary-stdcf',dict(ordinary_crypt=' /Filter /Crypt /DecodeParms << /Name /StdCF >>'),'routes-consistent'),
        ('attachment-identity',dict(attachment_crypt=' /Filter /Crypt /DecodeParms << /Name /Identity >>'),'routes-consistent'),
        ('eff-inherited-identity',dict(eff=None),'routes-consistent'),
        ('late-crypt',dict(attachment_crypt=' /Filter [/FlateDecode /Crypt] /DecodeParms [null << /Name /StdCF >>]'),'routes-consistent'),
        ('duplicate-crypt',dict(attachment_crypt=' /Filter [/Crypt /Crypt] /DecodeParms [<< /Name /StdCF >> << /Name /StdCF >>]'),'routes-consistent'),
        ('wrong-crypt-shape',dict(attachment_crypt=' /Filter [/Crypt] /DecodeParms << /Name /StdCF >>'),'routes-consistent'),
        ('aliased-content',dict(alias=True),'routes-consistent')]:
        retain(name,AUTHOR.document('bytes-'+name,**settings),predicate,'bytes')
    # Re-serialize an original single changed page stream. Neither product nor
    # authenticated derivative authors the visual control.
    original=AUTHOR.document('visual')
    retain('truncated-encrypted-file',original[:-70],'syntax-rejected','qpdf')
    objects=re.findall(rb'\d+ 0 obj\n(.*?)\nendobj\n',original,re.S)
    objects[3]=objects[3].replace(b'0 0 1 rg',b'1 0 0 rg')
    retain('changed-clear-paint',AUTHOR.AUTHOR.serialize('1.7',objects,AUTHOR.AUTHOR.md5(b'T78:T80-visual')),
           'nonzero-absolute-error','visual')
    (DESTINATION/'rules.json').write_text(json.dumps({'profile':'T32-password-embedded-files-only',
        'required-rules':sorted(rules),'rules':rules},sort_keys=True,indent=2)+'\n')
    (DESTINATION/'manifest.sha256').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n'
        for p in sorted(DESTINATION.iterdir()) if p.name!='manifest.sha256'))


if __name__=='__main__':build()
