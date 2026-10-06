"""Isolate a failed pinned T80 visual observation; diagnostic only."""
import datetime
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile

if len(sys.argv) > 1 and sys.argv[1] == '--inside':
    root, pdf, output = map(Path, sys.argv[2:])
    spec = importlib.util.spec_from_file_location('t81_t80_probe', root / 'scripts/t80-certification.py')
    certification = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(certification)
    certification.identities(root)
    output.mkdir()
    observer = certification.OBSERVER.Observations(root, output)
    with certification.OBSERVER.cancellation_scope():
        with tempfile.TemporaryDirectory(prefix='folio-clear-pages-probe-') as folder:
            clear = Path(folder) / 'clear.pdf'
            observer.clear_pages(pdf, clear, 'unauthenticated')
            verdict = observer.visual(pdf, clear, 1, 'visual')
    observer.emit('probe-result.json', {'diagnostic-only': True, 'result': verdict,
                                      'input-sha256': hashlib.sha256(pdf.read_bytes()).hexdigest()})
    raise SystemExit(0 if verdict == 'pass' else 1)

ROOT = Path('/workspace/folio-pdf')
delivery = ROOT / 'capabilities/evidence/T81-delivery'
scope = ROOT / 'capabilities/evidence/foundation/T81-final/password-attachments/jdk8-in_process'
configuration = scope / 'execution.yaml'
original_command = json.loads(configuration.read_text())['command']
pdf = scope / 'observations/native-credential-empty-user/product.pdf'
output = delivery / 'validation/attachment-visual-probe-r2-scope'
output.mkdir()
old_mount = str(scope) + ':/workspace/' + str(scope.relative_to(ROOT)) + ':rw'
new_mount = str(output) + ':/workspace/' + str(output.relative_to(ROOT)) + ':rw'
assert original_command.count(old_mount) == 1
command = original_command[:original_command.index('java')]
command[command.index(old_mount)] = new_mount
events = []
def reference(path):
    return {'path': str(path.relative_to(ROOT)), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
record = {'diagnostic-only': True, 'original-configuration': reference(configuration),
          'original-failure': reference(scope / 'recorder.txt'), 'input': reference(pdf),
          'changes': 'Isolate the same failed unauthenticated page-copy and PDFium/ImageMagick visual observation through unchanged pinned observer code and existing individual tool bounds; fresh output only.',
          'events': events}
for number in range(1, 4):
    directory = output / ('run-' + str(number))
    argv = command + ['/usr/bin/python3.12', '-I', '-B', '/workspace/' + str(Path(__file__).relative_to(ROOT)),
                      '--inside', '/workspace', '/workspace/' + str(pdf.relative_to(ROOT)),
                      '/workspace/' + str(directory.relative_to(ROOT))]
    log = output / ('run-' + str(number) + '.txt')
    started = datetime.datetime.now(datetime.timezone.utc).isoformat()
    pressure = {name: Path('/proc/pressure/' + name).read_text() for name in ('cpu', 'memory', 'io')}
    print('Diagnostic-only exact attachment visual observation ' + str(number), flush=True)
    pending = {'command': argv, 'started': started, 'pressure-before': pressure, 'status': 'running', 'outer-diagnostic-timeout-seconds': 600}
    record['active-event'] = pending
    (delivery / 'validation/attachment-visual-probe-r2-result.json').write_text(json.dumps(record, indent=2) + '\n')
    timed_out = False
    with log.open('wb') as stream:
        try:
            result = subprocess.run(argv, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, timeout=600)
            exit_code = result.returncode
        except subprocess.TimeoutExpired:
            timed_out = True
            exit_code = -1
    event = {'command': argv, 'started': started, 'pressure-before': pressure,
             'finished': datetime.datetime.now(datetime.timezone.utc).isoformat(),
             'exit-code': exit_code, 'outer-diagnostic-timeout': timed_out, 'log': reference(log)}
    if (directory / 'probe-result.json').exists():
        event['actual-result'] = reference(directory / 'probe-result.json')
        event['visual-result'] = json.loads((directory / 'probe-result.json').read_text())['result']
    events.append(event)
    record.pop('active-event', None)
    (delivery / 'validation/attachment-visual-probe-r2-result.json').write_text(json.dumps(record, indent=2) + '\n')
    print('Diagnostic visual exit ' + str(exit_code), flush=True)
    if exit_code:
        raise SystemExit(1)
