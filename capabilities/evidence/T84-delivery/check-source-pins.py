"""Validate every frozen producer input before staging a final candidate."""
import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parents[3]
entries = 0
pins = sorted((root / 'scripts').glob('*evidence-pin.properties'))
assert pins
for pin in pins:
    seen = set()
    for line in pin.read_text().splitlines():
        if not line or line.startswith('#'):
            continue
        name, expected = line.split('=', 1)
        assert name not in seen and len(expected) == 64
        seen.add(name)
        path = root / name
        path.resolve(strict=True).relative_to(root)
        assert path.is_file(), name
        assert hashlib.sha256(path.read_bytes()).hexdigest() == expected, str(pin) + ': ' + name
        entries += 1
print(json.dumps({'result': 'pass', 'pin-files': len(pins), 'exact-input-identities': entries}), flush=True)
