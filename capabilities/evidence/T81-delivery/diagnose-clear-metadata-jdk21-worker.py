"""Replay the unchanged clear-metadata suite as diagnostic evidence, never certification."""
import datetime
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path('/workspace/folio-pdf')
original = ROOT / 'capabilities/evidence/foundation/T81-final-r2/password-clear-metadata/jdk21-hardened_worker'
source = original / 'contract-tests-command.json'
command = json.loads(source.read_text())
output = ROOT / 'capabilities/evidence/T81-delivery/validation/clear-metadata-jdk21-worker-probe-r1-scope'
output.mkdir()
old_mount = str(original) + ':/workspace/' + str(original.relative_to(ROOT)) + ':rw'
new_mount = str(output) + ':/workspace/' + str(output.relative_to(ROOT)) + ':rw'
assert command.count(old_mount) == 1
command[command.index(old_mount)] = new_mount
log = ROOT / 'capabilities/evidence/T81-delivery/validation/clear-metadata-jdk21-worker-probe-r1.txt'
record = {'command': command, 'original-command': {'path': str(source.relative_to(ROOT)),
    'sha256': hashlib.sha256(source.read_bytes()).hexdigest()},
    'only-command-change': 'fresh writable diagnostic output directory; all 23 tests, image, JVM flags and execution settings unchanged',
    'diagnostic-only': True, 'started': datetime.datetime.now(datetime.timezone.utc).isoformat()}
def pressure():
    return {name: Path('/proc/' + name).read_text() for name in
            ('pressure/cpu', 'pressure/memory', 'pressure/io', 'loadavg')}
record['pressure-before'] = pressure()
record['observed-original-failure'] = 'Elapsed workflow limit at owner rewrite; suite duration 1273.116 seconds; cause unproven'
record['original-failure'] = {'path': str((original / 'contract-tests.txt').relative_to(ROOT)),
    'sha256': hashlib.sha256((original / 'contract-tests.txt').read_bytes()).hexdigest()}
print('Replaying unchanged JDK21 Worker full 23-test suite as diagnostic only', flush=True)
status = 1
try:
    with log.open('wb') as stream:
        result = subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, timeout=1800)
    status = result.returncode
    if status == 0 and 'OK (23 tests)' not in log.read_text():
        status = 1
        record['error'] = 'Complete original suite marker missing'
except subprocess.TimeoutExpired as error:
    record['error'] = 'Diagnostic orchestration timeout: ' + str(error.timeout) + ' seconds'
finally:
    record.update({'exit-code': status,
        'finished': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'pressure-after': pressure(),
        'log': {'path': str(log.relative_to(ROOT)), 'sha256': hashlib.sha256(log.read_bytes()).hexdigest()}})
    (log.parent / 'clear-metadata-jdk21-worker-probe-r1-result.json').write_text(json.dumps(record, indent=2) + '\n')
print('Diagnostic replay exit ' + str(status), flush=True)
raise SystemExit(status)
