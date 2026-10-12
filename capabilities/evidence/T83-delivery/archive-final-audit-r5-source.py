"""Preserve the exact audit configuration exercised by archive helper r3."""
import hashlib
from pathlib import Path
import shutil

delivery = Path(__file__).resolve().parent
source = delivery / 'audit-final.py'
destination = delivery / 'audit-final-r5-source.py'
assert not destination.exists()
identity = hashlib.sha256(source.read_bytes()).hexdigest()
assert 'Audit script SHA-256: ' + identity in (
    delivery / 'validation/diagnostic-archive-helper-r3.txt').read_text()
shutil.copyfile(source, destination)
assert hashlib.sha256(destination.read_bytes()).hexdigest() == identity
print('Preserved archive-helper-r3 audit source SHA-256: ' + identity)
print('The full final audit was not executed or passed; this preserves the helper binding only.')
