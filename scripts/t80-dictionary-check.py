#!/usr/bin/env python3
"""Original plaintext EFF dictionary predicates through pinned pypdf's parser.

The input and output policy comes from the public T80 standards audit. This
adapter never authenticates, reads a password or imports a producer/backend.
It qualifies dictionary defects which the pinned Arlington/PDFium parser cannot
safely inspect (notably invalid EFOpen crypt-filter lengths). No raw exceptions
or authentication entries are reported.
"""
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / '.build-cache/t78-security-checkers'))
import pypdf
from pypdf.generic import BooleanObject, ByteStringObject, DictionaryObject, NameObject, NumberObject, StreamObject, TextStringObject


class DictionaryReader(pypdf.PdfReader):
    def _handle_encryption(self, password):
        if password is not None: raise ValueError('Dictionary reader cannot receive a credential')
        self._encryption = None


def check(path, output=False):
    findings = []
    def invalid(condition, key, model='EncryptionStandard'):
        if not condition: findings.append(key + ' (' + model + ')')
    def integer(value): return isinstance(value, NumberObject) and not isinstance(value, BooleanObject)
    def string(value):
        if isinstance(value, ByteStringObject): return bytes(value)
        if isinstance(value, TextStringObject): return value.original_bytes
        return b''
    try:
        with path.open('rb') as source:
            reader=DictionaryReader(source,strict=True)
            encryption=reader.trailer['/Encrypt']
            invalid(isinstance(encryption,DictionaryObject) and not isinstance(encryption,StreamObject),'Encrypt')
            version=reader.pdf_header[-3:]
            catalog=reader.trailer['/Root']
            editions={'1.'+str(number) for number in range(8)}|{'2.0'}
            invalid(version in editions,'Version','Catalog')
            override=catalog.get('/Version')
            invalid(override is None or isinstance(override,NameObject)
                    and str(override)[1:] in editions,'Version','Catalog')
            if isinstance(override,NameObject) and str(override)[1:] in editions:
                version=max(version,str(override)[1:])
            v,r=encryption.get('/V'),encryption.get('/R')
            invalid(isinstance(encryption.get('/Filter'),NameObject)
                    and encryption.get('/Filter')=='/Standard','Filter')
            invalid('/SubFilter' not in encryption,'SubFilter')
            valid_version=version>='1.5' if v==4 else version>='1.7'
            if v==5 and version=='1.7':
                extensions=catalog.get('/Extensions')
                if extensions is not None: extensions=extensions.get_object()
                extension_dictionary=isinstance(extensions,DictionaryObject) and not isinstance(extensions,StreamObject)
                invalid(extension_dictionary,'Extensions','Catalog')
                extensions_type=extensions.get('/Type') if extension_dictionary else None
                if extensions_type is not None: extensions_type=extensions_type.get_object()
                invalid(extensions_type is None or isinstance(extensions_type,NameObject)
                        and extensions_type=='/Extensions','Type','Extensions')
                adbe=extensions.get('/ADBE') if extension_dictionary else None
                if adbe is not None: adbe=adbe.get_object()
                adobe_dictionary=isinstance(adbe,DictionaryObject) and not isinstance(adbe,StreamObject)
                invalid(adobe_dictionary,'ADBE','Extensions')
                adobe_type=adbe.get('/Type') if adobe_dictionary else None
                if adobe_type is not None: adobe_type=adobe_type.get_object()
                invalid(adobe_type is None or isinstance(adobe_type,NameObject)
                        and adobe_type=='/DeveloperExtensions','Type','DeveloperExtensions')
                base=adbe.get('/BaseVersion') if adobe_dictionary else None
                level=adbe.get('/ExtensionLevel') if adobe_dictionary else None
                if base is not None: base=base.get_object()
                if level is not None: level=level.get_object()
                valid_base=isinstance(base,NameObject) and base=='/1.7'
                valid_level=integer(level) and (3 if r==5 else 8)<=level<=(1<<31)-1
                invalid(valid_base,'BaseVersion','DeveloperExtensions')
                invalid(valid_level,'ExtensionLevel','DeveloperExtensions')
                valid_version=extension_dictionary and adobe_dictionary and valid_base and valid_level
            invalid(integer(v) and v in (4,5) and valid_version and ((v==4 and r==4) or (v==5 and r in (5,6))),'V')
            invalid(integer(r) and ((v==4 and r==4) or (v==5 and r in (5,6))),'R')
            bits=128 if v==4 else 256
            length=encryption.get('/Length',40)
            invalid(integer(length) and length==bits,'Length')
            p=encryption.get('/P')
            invalid(integer(p) and -(1<<31)<=p<(1<<31) and p&3==0 and p&0xc0==0xc0 and p&0xfffff000==0xfffff000
                    and (not output or version!='2.0' or p&512!=0),'P')
            for key in ('O','U','OE','UE','Perms'):
                if key in ('OE','UE','Perms') and r==4: continue
                wanted=16 if key=='Perms' else 32 if key in ('OE','UE') or r==4 else 48
                invalid(len(string(encryption.get('/'+key)))==wanted,key)
            for key in ('StmF','StrF'):
                value=encryption.get('/'+key,NameObject('/Identity'))
                invalid(isinstance(value,NameObject) and value=='/Identity' and (not output or '/'+key in encryption),key)
            eff=encryption.get('/EFF',NameObject('/Identity'))
            invalid(isinstance(eff,NameObject) and eff in ('/StdCF','/Identity')
                    and ('/EFF' not in encryption or version>='1.6')
                    and (not output or encryption.get('/EFF')=='/StdCF'),'EFF')
            metadata=encryption.get('/EncryptMetadata')
            invalid((metadata is None and not output) or isinstance(metadata,BooleanObject)
                    and (not output or not bool(metadata.value)),'EncryptMetadata')
            filters=encryption.get('/CF')
            invalid(isinstance(filters,DictionaryObject) and not isinstance(filters,StreamObject),'CF')
            invalid(isinstance(filters,DictionaryObject) and set(filters)=={'/StdCF'},'StdCF','CryptFilterMap')
            std=filters.get('/StdCF') if isinstance(filters,DictionaryObject) else None
            if std is not None: std=std.get_object()
            invalid(isinstance(std,DictionaryObject) and not isinstance(std,StreamObject),'StdCF','CryptFilterMap')
            if isinstance(std,DictionaryObject):
                invalid('/Type' not in std or isinstance(std.get('/Type'),NameObject)
                        and std.get('/Type')=='/CryptFilter','Type','CryptFilter')
                method=std.get('/CFM')
                invalid(isinstance(method,NameObject) and method in (('/V2','/AESV2') if v==4 else ('/AESV3',))
                        and (method!='/AESV2' or version>='1.6'),'CFM','CryptFilter')
                length=std.get('/Length')
                invalid((length is None and (v!=5 or version!='2.0'))
                        or integer(length) and length==bits//8,'Length','CryptFilter')
                event=std.get('/AuthEvent',NameObject('/DocOpen'))
                invalid(isinstance(event,NameObject) and event in ('/DocOpen','/EFOpen')
                        and (not output or std.get('/AuthEvent')=='/EFOpen'),'AuthEvent','CryptFilter')
        original=path.read_bytes()
        start=re.search(rb'startxref\s+(\d+)\s+%%EOF\s*$',original)
        xref_stream=start is not None and original[int(start[1]):int(start[1])+4]!=b'xref'
        return {'pypdf':pypdf.__version__,'unauthenticated':True,'diagnostic':'none',
                'arlington-compatible': xref_stream and (r != 4 or std.get('/Length') is None) and '/Prev' not in reader.trailer,
                'findings':sorted(set(findings)),'result':'fail' if findings else 'pass'}
    except Exception:
        return {'pypdf':pypdf.__version__,'unauthenticated':True,'diagnostic':'input-rejected','findings':[],'result':'fail'}


if __name__=='__main__': print(json.dumps(check(Path(sys.argv[1]),len(sys.argv)>2 and sys.argv[2]=='output'),sort_keys=True))
