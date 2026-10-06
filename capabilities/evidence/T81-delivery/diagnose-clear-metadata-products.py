"""Diagnostic replay of the unchanged T79 product sequence; never certification."""
import datetime
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path('/workspace/folio-pdf')
original = ROOT / 'capabilities/evidence/foundation/T81-final/password-clear-metadata/jdk11-hardened_worker'
configuration = original / 'execution.yaml'
original_command = json.loads(configuration.read_text())['command']
output = ROOT / 'capabilities/evidence/T81-delivery/validation/clear-metadata-products-probe-r1-scope'
output.mkdir()
old_mount = str(original) + ':/workspace/' + str(original.relative_to(ROOT)) + ':rw'
new_mount = str(output) + ':/workspace/' + str(output.relative_to(ROOT)) + ':rw'
assert original_command.count(old_mount) == 1
events = []
for attempt in range(1, 4):
    command = list(original_command)
    command[command.index(old_mount)] = new_mount
    entry = command.index('net.zerocloud.pdf.acceptance.T79EvidenceCommand')
    assert command[entry + 1:] == ['/workspace', '/workspace/' + str(original.relative_to(ROOT)) + '/observations', 'HARDENED_WORKER', '0.1.0']
    products = output / ('products-' + str(attempt))
    command[entry + 1:] = ['products', '/workspace', '/workspace/' + str(products.relative_to(ROOT)), 'HARDENED_WORKER']
    log = output / ('attempt-' + str(attempt) + '.txt')
    started = datetime.datetime.now(datetime.timezone.utc).isoformat()
    print('Diagnostic-only T79 unchanged product sequence attempt ' + str(attempt), flush=True)
    with log.open('wb') as stream:
        result = subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, timeout=600)
    events.append({'command': command, 'started': started,
                   'finished': datetime.datetime.now(datetime.timezone.utc).isoformat(),
                   'exit-code': result.returncode,
                   'log': {'path': str(log.relative_to(ROOT)), 'sha256': hashlib.sha256(log.read_bytes()).hexdigest()},
                   'product-file-count': sum(path.is_file() for path in products.rglob('*'))})
    record = {'diagnostic-only': True,
              'original-configuration': {'path': str(configuration.relative_to(ROOT)), 'sha256': hashlib.sha256(configuration.read_bytes()).hexdigest()},
              'only-command-changes': 'fresh writable output; existing products-only mode runs the same full Native/Facade product sequence without independent tool observations; image, JVM, profile and product bounds unchanged',
              'events': events}
    (output.parent / 'clear-metadata-products-probe-r1-result.json').write_text(json.dumps(record, indent=2) + '\n')
    print('Diagnostic attempt exit ' + str(result.returncode), flush=True)
    if result.returncode:
        raise SystemExit(result.returncode)
