#!/usr/bin/env python3
"""Acceptance adapter: report pypdf's authentication and Perms findings safely.

No PDF algorithms are implemented here. Strict pypdf decrypt() performs the
external checks; its exact ignored-Perms diagnostic is a failing observation.
"""
import io
import json
import logging
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(root / '.build-cache/t78-security-checkers'))
import pypdf
import Crypto
from pypdf._crypt_providers import crypt_provider

capture = io.StringIO()
logger = logging.getLogger('pypdf')
logger.handlers = [logging.StreamHandler(capture)]
logger.propagate = False
secret = sys.stdin.buffer.read(4097)
result = {'pypdf': pypdf.__version__, 'pycryptodome': Crypto.__version__, 'provider': list(crypt_provider),
          'authenticated': False, 'authority': 0, 'perms-invalid': False, 'diagnostic': 'none'}
try:
    if len(secret) > 4096:
        raise ValueError('credential bound')
    with open(sys.argv[1], 'rb') as stream:
        reader = pypdf.PdfReader(stream, strict=True)
        authority = reader.decrypt(secret) if reader.is_encrypted else 0
        diagnostic = capture.getvalue()
        invalid = "ignore '/Perms' verify failed" in diagnostic
        result.update(authenticated=bool(authority), authority=int(authority))
        result['perms-invalid'] = invalid
        result['diagnostic'] = 'perms-verification-failed' if invalid else 'other' if diagnostic else 'none'
except Exception:
    # Raw parser exceptions can contain input paths, bytes and dictionary values.
    result['diagnostic'] = 'input-rejected'
finally:
    secret = b''
    capture.close()
print(json.dumps(result, sort_keys=True))
