#!/usr/bin/env python3
"""Observe original clear objects before credentials; independently decrypt EF bytes.

pypdf 6.1.1 authenticates and supplies object-specific PyCryptodome cryptors.
Its general stream decoder does not route EFF or explicit named Crypt. This
adapter selects that independently qualified cryptor from original EF routes,
then delegates decoding. No producer algorithms or Folio implementation enter
this observer. Findings contain categorical predicates only.
"""
import io
import json
import logging
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / '.build-cache/t78-security-checkers'))
import pypdf
from pypdf.generic import ArrayObject, ByteStringObject, DictionaryObject, IndirectObject, NameObject, NullObject, StreamObject, TextStringObject


class ClearReader(pypdf.PdfReader):
    """Parse original Identity objects without even pypdf's implicit empty-password attempt."""
    def _handle_encryption(self, password):
        if password is not None: raise ValueError('Clear reader cannot receive a credential')
        self._encryption = None


def string_bytes(value):
    if isinstance(value, ByteStringObject): return bytes(value)
    if isinstance(value, TextStringObject): return value.original_bytes
    raise ValueError('String type')


def selected(stream, default):
    filters = stream.get('/Filter')
    if filters is not None: filters=filters.get_object()
    sequence = list(filters) if isinstance(filters, ArrayObject) else [] if filters is None else [filters]
    if any(not isinstance(name,NameObject) for name in sequence): raise ValueError('Filter name type')
    parameters = stream.get('/DecodeParms')
    if '/Crypt' not in sequence: return default, sequence, parameters
    if sequence[0] != '/Crypt' or sequence.count('/Crypt') != 1: raise ValueError('Crypt order')
    if isinstance(filters, ArrayObject) and parameters is not None and not isinstance(parameters, NullObject):
        if not isinstance(parameters, ArrayObject) or len(parameters) != len(sequence): raise ValueError('Crypt shape')
        first = parameters[0]
    else: first = parameters
    if first is not None: first = first.get_object()
    if first is not None and not isinstance(first, NullObject) and (not isinstance(first, DictionaryObject) or isinstance(first, StreamObject)):
        raise ValueError('Crypt parameter dictionary')
    name = '/Identity' if first is None or isinstance(first, NullObject) else first.get('/Name', '/Identity')
    if first is not None and not isinstance(first, NullObject):
        if '/Name' in first and not isinstance(first['/Name'],NameObject): raise ValueError('Crypt name type')
        if '/Type' in first and (not isinstance(first['/Type'],NameObject) or first['/Type']!='/CryptFilterDecodeParms'):
            raise ValueError('Crypt parameter type')
    if name not in ('/Identity', '/StdCF'): raise ValueError('Unknown Crypt')
    return name, sequence[1:], list(parameters)[1:] if isinstance(parameters, ArrayObject) else None


def decode(stream, sequence, parameters, data=None):
    if data is not None: stream._data = data
    if sequence:
        stream[NameObject('/Filter')] = ArrayObject(sequence)
        if isinstance(parameters, list): stream[NameObject('/DecodeParms')] = ArrayObject(parameters)
    else:
        stream.pop('/Filter', None); stream.pop('/DecodeParms', None)
        return stream._data
    return stream.get_data()


def main():
    capture = io.StringIO()
    logger = logging.getLogger('pypdf')
    logger.handlers = [logging.StreamHandler(capture)]; logger.propagate = False
    result = {'pypdf': pypdf.__version__, 'authenticated': False, 'diagnostic': 'input-rejected'}
    secret = b''
    try:
        path = Path(sys.argv[1])
        with path.open('rb') as original:
            raw = ClearReader(original, strict=True)
            root = raw.trailer['/Root']
            page = raw.pages[0]
            contents = page.raw_get('/Contents')
            references = contents if isinstance(contents, ArrayObject) else [contents]
            clear_page = b''
            for reference in references:
                item = reference.get_object()
                route, sequence, parameters = selected(item, '/Identity')
                if route != '/Identity': raise ValueError('Ordinary crypt route')
                clear_page += decode(item, sequence, parameters)
            title = string_bytes(raw.trailer['/Info']['/Title'])
            xmp = root['/Metadata'].get_data()
            file = root['/Names']['/EmbeddedFiles']['/Names'][1].get_object()
            if not isinstance(file,DictionaryObject) or isinstance(file,StreamObject): raise ValueError('File specification dictionary')
            if '/Type' in file and (not isinstance(file['/Type'],NameObject) or file['/Type']!='/Filespec'):
                raise ValueError('File specification type')
            ef=file['/EF']
            if not isinstance(ef,DictionaryObject) or isinstance(ef,StreamObject): raise ValueError('EF dictionary')
            reference = ef.raw_get('/F')
            if not isinstance(reference, IndirectObject): raise ValueError('Original EF reference')
            embedded = reference.get_object()
            if not isinstance(embedded,StreamObject): raise ValueError('EF stream')
            if '/Type' in embedded and (not isinstance(embedded['/Type'],NameObject) or embedded['/Type']!='/EmbeddedFile'):
                raise ValueError('Embedded stream type')
            encryption = raw.trailer['/Encrypt']
            default = encryption.get('/EFF', encryption.get('/StmF', '/Identity'))
            route, sequence, parameters = selected(embedded, default)
            routes = encryption.get('/StmF', '/Identity') == '/Identity' and encryption.get('/StrF', '/Identity') == '/Identity'
            routes = routes and route == '/StdCF' and embedded.get('/Type', '/EmbeddedFile') == '/EmbeddedFile'
            routes = routes and encryption['/CF']['/StdCF'].get('/AuthEvent', '/DocOpen') in ('/DocOpen', '/EFOpen')
            routes = routes and not any(isinstance(item, IndirectObject) and item == reference for item in references)
            ciphertext = embedded._data
            expected = (ROOT / 'capabilities/profiles/T80-embedded-files-only/attachment.txt').read_bytes()
            result.update({'unauthenticated-page-matches': clear_page == b'0 0 1 rg 12 16 24 20 re f\n',
                           'unauthenticated-title-matches': title == b'Folio T80 clear document title',
                           'unauthenticated-metadata-matches': xmp == (ROOT / 'capabilities/profiles/T80-embedded-files-only/metadata.xmp').read_bytes(),
                           'attachment-ciphertext-differs': ciphertext != expected, 'routes-consistent': bool(routes)})
            if len(sys.argv) == 3:
                writer = pypdf.PdfWriter()
                for clear in raw.pages: writer.add_page(clear)
                writer.write(sys.argv[2])  # Private visual/core derivative from clear bytes, with no attachment credential.
                result.update(diagnostic='none', **{'unauthenticated-page-copy': True})
            else:
                # Only after retaining categorical clear observations do we read a credential.
                secret = sys.stdin.buffer.read(4097)
                if len(secret) > 4096: raise ValueError('Credential bound')
                with path.open('rb') as authenticated:
                    reader = pypdf.PdfReader(authenticated, strict=True)
                    authority = reader.decrypt(secret)
                    if not authority: raise ValueError('Authentication rejected')
                    method = encryption['/CF']['/StdCF']['/CFM']
                    if not routes or method not in ('/V2', '/AESV2', '/AESV3'): raise ValueError('EF route')
                    engine = reader._encryption
                    saved = engine.EFF
                    try:
                        engine.EFF = method  # Apply the actual selected filter, including explicit StdCF overriding EFF Identity.
                        cryptor = engine._make_crypt_filter(reference.idnum, reference.generation).ef_crypt
                    finally: engine.EFF = saved
                    clear = cryptor.decrypt(ciphertext)
                    payload = decode(embedded, sequence, parameters, clear)
                    diagnostic = capture.getvalue()
                    result.update(authenticated=True, authority=int(authority), **{'embedded-file-matches': payload == expected})
                    result['diagnostic'] = 'perms-verification-failed' if "ignore '/Perms' verify failed" in diagnostic else 'unexpected-diagnostic' if diagnostic.strip() else 'none'
    except Exception:
        # Neither raw exceptions, keys, authentication entries nor document bytes leave the process.
        pass
    finally:
        secret = b''; capture.close()
    print(json.dumps(result, sort_keys=True))


if __name__ == '__main__': main()
