"""Preserve the exact audit source already exercised by archive helper r4."""
import hashlib
from pathlib import Path
import shutil

delivery = Path(__file__).resolve().parent
source = delivery / 'audit-final.py'
destination = delivery / 'audit-final-r7-preverify-source.py'
digest = hashlib.sha256(source.read_bytes()).hexdigest()
assert digest == '50bc6c04006a6b4c21b7cb342b6ed677e9e449be79017f89f0a8eec640d096d2'
assert 'Audit script SHA-256: ' + digest in (delivery / 'validation/diagnostic-archive-helper-r4.txt').read_text()
assert not destination.exists()
shutil.copyfile(source, destination)
assert hashlib.sha256(destination.read_bytes()).hexdigest() == digest
print('Preserved archive-helper-r4 audit source: ' + digest)
