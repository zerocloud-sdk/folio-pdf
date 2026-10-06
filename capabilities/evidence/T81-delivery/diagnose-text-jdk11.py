"""Replay the unchanged text suite as diagnostic evidence, never certification."""
import datetime
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path('/workspace/folio-pdf')
original = ROOT / 'capabilities/evidence/foundation/T81-final/text-r4/jdk11-hardened_worker'
source = original / 'contract-tests-command.json'
command = json.loads(source.read_text())
output = ROOT / 'capabilities/evidence/T81-delivery/validation/text-jdk11-worker-probe-r1-scope'
output.mkdir()
old_mount = str(original) + ':/workspace/' + str(original.relative_to(ROOT)) + ':rw'
new_mount = str(output) + ':/workspace/' + str(output.relative_to(ROOT)) + ':rw'
assert command.count(old_mount) == 1
command[command.index(old_mount)] = new_mount
log = ROOT / 'capabilities/evidence/T81-delivery/validation/text-jdk11-worker-probe-r1.txt'
record = {'command': command, 'original-command': {'path': str(source.relative_to(ROOT)),
    'sha256': hashlib.sha256(source.read_bytes()).hexdigest()},
    'only-command-change': 'fresh writable diagnostic output directory; all 126 tests, image, JVM flags and execution settings unchanged',
    'diagnostic-only': True, 'started': datetime.datetime.now(datetime.timezone.utc).isoformat()}
print('Replaying unchanged JDK11 Worker full 126-test suite as diagnostic only', flush=True)
with log.open('wb') as stream:
    result = subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, timeout=600)
record.update({'exit-code': result.returncode,
    'finished': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'log': {'path': str(log.relative_to(ROOT)), 'sha256': hashlib.sha256(log.read_bytes()).hexdigest()}})
assert result.returncode or 'OK (126 tests)' in log.read_text()
(log.parent / 'text-jdk11-worker-probe-r1-result.json').write_text(json.dumps(record, indent=2) + '\n')
print('Diagnostic replay exit ' + str(result.returncode), flush=True)
raise SystemExit(result.returncode)
