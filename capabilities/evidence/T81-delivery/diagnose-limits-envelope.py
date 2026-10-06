#!/usr/bin/env python3
"""Read-only failure diagnostic: add exception logging to the staged merge.

No collector, validation, identity, parsing or publication checks are changed.
The cloned function never writes the evidence authority.
"""
import datetime
import hashlib
import json
from pathlib import Path
import sys

ROOT = Path('/workspace/folio-pdf')
sys.path.insert(0, str(ROOT / 'scripts'))
source_path = ROOT / 'scripts/t03-foundation.py'
source = source_path.read_text()
old = '        except (OSError, ValueError, KeyError, TypeError, yaml.YAMLError):\n            # Fail closed for this old scope; its original files remain untouched.'
new = '        except (OSError, ValueError, KeyError, TypeError, yaml.YAMLError) as error:\n            print("DIAGNOSTIC", key, type(error).__name__, str(error), flush=True)\n            # Fail closed for this old scope; its original files remain untouched.'
assert source.count(old) == 1
namespace = {'__name__': 't81_read_only_merge_diagnostic', '__file__': str(source_path)}
exec(compile(source.replace(old, new), str(source_path), 'exec'), namespace)
directory = ROOT / 'capabilities/evidence/foundation/T81-initial/limits'
fresh = json.loads((directory / 'observed-index.json').read_text())
identities = json.loads((directory / 'identities.json').read_text())
empty = {'schema-version': 1, 'candidate': fresh['candidate'], 'environments': fresh['environments'], 'certifications': []}
authority = ROOT / 'capabilities/foundation-evidence.yaml'
before = authority.read_bytes()
print('Diagnostic started', datetime.datetime.now(datetime.timezone.utc).isoformat(), flush=True)
print('Unchanged staged driver SHA256', hashlib.sha256(source_path.read_bytes()).hexdigest(), flush=True)
merged = namespace['merge_evidence'](ROOT, fresh, empty, identities)
assert authority.read_bytes() == before
print('Retained fresh scopes:', len(merged['certifications']), flush=True)
print('Diagnostic finished', datetime.datetime.now(datetime.timezone.utc).isoformat(), flush=True)
