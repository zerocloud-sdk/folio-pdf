#!/usr/bin/env python3
"""Independent original-byte observations using the pinned pypdf parser.

The first reader parses clear objects with decryption disabled and receives no
credential. A separate reader authenticates exact bytes over stdin. Findings
contain only categorical checks and content hashes, never keys or O/U/Perms.
"""
import hashlib
import io
import json
import logging
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / '.build-cache/t78-security-checkers'))
import pypdf
from pypdf.generic import ArrayObject, ByteStringObject, IndirectObject, NameObject, NullObject, StreamObject, TextStringObject


def digest(data): return hashlib.sha256(data).hexdigest()


def string_bytes(value):
    if isinstance(value, ByteStringObject): return bytes(value)
    if isinstance(value, TextStringObject): return value.original_bytes
    raise ValueError('Not a PDF string')


def raw_object(reader, reference):
    if isinstance(reference, IndirectObject):
        return reader.get_object(IndirectObject(reference.idnum, reference.generation, reader))
    raise ValueError('The protected object must have an original reference')


def decoded(stream, authenticated=False):
    # pypdf has already applied StmF during object loading. Its later filter
    # decoder does not implement named Crypt filters. Remove only a qualified
    # leading StdCF after authentication, or an explicitly clear Identity.
    filters = stream.get('/Filter')
    params = stream.get('/DecodeParms')
    sequence = list(filters) if isinstance(filters, ArrayObject) else [filters]
    if sequence and sequence[0] == '/Crypt':
        parameters = list(params) if isinstance(params, ArrayObject) else [params] * len(sequence)
        if len(parameters) != len(sequence): raise ValueError('Crypt parameter shape')
        name = '/Identity' if parameters[0] is None or isinstance(parameters[0], NullObject) else parameters[0].get('/Name', '/Identity')
        if name != '/Identity' and not (authenticated and name == '/StdCF'):
            raise ValueError('Unqualified Crypt filter')
        if '/Crypt' in sequence[1:]: raise ValueError('Repeated Crypt filter')
        if len(sequence) == 1:
            return stream._data  # Already parsed and, for StdCF, authenticated/decrypted by pypdf.
        else:
            stream[NameObject('/Filter')] = ArrayObject(sequence[1:])
            stream[NameObject('/DecodeParms')] = ArrayObject(parameters[1:])
    return stream.get_data()


def observe(path, secret, raw, raw_metadata, capture):
    expected_xmp = (ROOT / 'capabilities/profiles/T79-clear-metadata/metadata.xmp').read_bytes()
    with path.open('rb') as stream:
        reader = pypdf.PdfReader(stream, strict=True)
        authority = reader.decrypt(secret)
        if not authority: raise ValueError('Authentication failed')
        root = reader.trailer['/Root']
        title = string_bytes(reader.trailer['/Info']['/Title'])
        keywords = string_bytes(reader.trailer['/Info']['/Keywords'])
        metadata_string = string_bytes(root['/Metadata']['/FolioProof'])
        page = reader.pages[0]
        component_reference = page.raw_get('/Metadata')
        component = decoded(component_reference.get_object(), authenticated=True)
        content_reference = page.raw_get('/Contents')
        if isinstance(content_reference, ArrayObject): content_reference = content_reference[0]
        content = decoded(content_reference.get_object(), authenticated=True)
        names = root['/Names']['/EmbeddedFiles']['/Names']
        file = names[1].get_object()
        attachment_reference = file['/EF'].raw_get('/F')
        attachment = decoded(attachment_reference.get_object(), authenticated=True)
        authentication_diagnostic = capture.getvalue()
        capture.seek(0); capture.truncate(0)
        protected = {}
        for name, reference, plain in [('content', content_reference, content), ('embedded-file', attachment_reference, attachment),
                                       ('component-metadata', component_reference, component)]:
            try:
                item = raw_object(raw, reference)
                recovered = item.get_data() if isinstance(item, StreamObject) else None
                protected[name] = recovered != plain
            except Exception:
                protected[name] = True
        try:
            info = raw_object(raw, reader.trailer.raw_get('/Info'))
            protected['info-title'] = string_bytes(info['/Title']) != title
            protected['info-keywords'] = string_bytes(info['/Keywords']) != keywords
        except Exception:
            # Encrypted object streams also prevent unauthenticated recovery.
            protected['info-title'] = protected['info-keywords'] = True
        protected['metadata-dictionary-string'] = string_bytes(raw.trailer['/Root']['/Metadata']['/FolioProof']) != metadata_string
        return {'authenticated': True, 'authority': int(authority),
                'diagnostic': 'perms-verification-failed' if "ignore '/Perms' verify failed" in authentication_diagnostic
                    else 'unexpected-diagnostic' if authentication_diagnostic.strip() else 'none',
                'unauthenticated-metadata-matches': raw_metadata == expected_xmp,
                'unauthenticated-metadata-sha256': digest(raw_metadata) if raw_metadata == expected_xmp else None,
                'content-matches': content == b'0 0 1 rg 12 16 24 20 re f\n',
                'title-matches': title == b'Folio baseline proof',
                'keywords-match': keywords == b'Folio protected metadata-like Info value',
                'metadata-dictionary-string-matches': metadata_string == b'Folio protected metadata-like Info value',
                'component-metadata-matches': component == expected_xmp.replace(b'clear XMP', b'protected component'),
                'embedded-file-matches': attachment == b'Folio protected embedded data: metadata-like values remain private.\n',
                'unauthenticated-protected-data-unavailable': protected}


def main():
    capture = io.StringIO()
    logger = logging.getLogger('pypdf')
    logger.handlers = [logging.StreamHandler(capture)]; logger.propagate = False
    result = {'authenticated': False, 'diagnostic': 'input-rejected', 'pypdf': pypdf.__version__}
    secret = b''
    try:
        path = Path(sys.argv[1])
        with path.open('rb') as original:
            raw = pypdf.PdfReader(original, strict=True)
            # pypdf normally prevents access to an encrypted file's objects.
            # This parser-only read disables decryption; it cannot obtain a key
            # or turn ciphertext into plaintext, including inside object streams.
            raw._encryption = None
            raw_metadata = None
            try: raw_metadata = decoded(raw.trailer['/Root']['/Metadata'])
            except Exception: pass
            raw_diagnostic = bool(capture.getvalue())
            capture.seek(0); capture.truncate(0)
            secret = sys.stdin.buffer.read(4097)
            if len(secret) > 4096: raise ValueError('Credential bound')
            observation = observe(path, secret, raw, raw_metadata, capture)
            result.update(observation, **{'raw-decode-diagnostic': raw_diagnostic or bool(capture.getvalue())})
    except Exception:
        pass
    finally:
        secret = b''; capture.close()
    print(json.dumps(result, sort_keys=True))


if __name__ == '__main__': main()
