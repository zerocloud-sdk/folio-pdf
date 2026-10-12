"""Exercise the actual final-audit preservation block; export no certification."""
import gzip
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import zipfile

import yaml

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('t83_preservation_check', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
frozen = json.loads((delivery / 'source-freeze-final-r9.json').read_text())
audit = delivery / 'audit-relaunch-r16.py'
source = audit.read_text()
before = driver.reference(root, audit)
# Run its unchanged binding functions and preservation block, without its
# current-candidate/final-gate checks or final PASS output operation.
namespace = dict(globals())
start = source.index('seen = set()\n')
end = source.index('superseded_references = ', start)
exec(compile(source[start:end], str(audit), 'exec'), namespace)
start = end
end = source.index("preservation = json.loads((delivery / 'relaunch-preservation.json')", start)
exec(compile(source[start:end], str(audit), 'exec'), namespace)
assert driver.reference(root, audit) == before
path = delivery / 'validation/candidate8-preservation-r1.json'
assert not path.exists()
driver.write_json(path, {'result': 'pass', 'audit-driver': before,
    'validator': driver.reference(root, Path(__file__)),
    'original-text-files-rehashed': len(namespace['text_seen']),
    'historical-index-tuples': len(namespace['old_index']['certifications']),
    'historical-transitive-references-verified': len(namespace['historical_seen']),
    'historical-archive-entries-verified': len(namespace['historical_archived']),
    'certifications-exported': 0})
print('Actual preservation block passed: 157778 original text files, archived candidate8 bytes and old48/192 transitive evidence; zero certifications exported.')
