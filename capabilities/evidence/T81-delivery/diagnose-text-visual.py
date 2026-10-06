"""Replay one indeterminate predecessor visual control; never certification."""
import datetime
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path('/workspace/folio-pdf')
DELIVERY = ROOT / 'capabilities/evidence/T81-delivery'
scope = ROOT / 'capabilities/evidence/foundation/T81-final/text-r5/jdk17-in_process'
source = scope / 'execution.yaml'
command = json.loads(source.read_text())['command']
output = DELIVERY / 'validation/text-visual-probe-r1-scope'
output.mkdir()
old_mount = str(scope) + ':/workspace/' + str(scope.relative_to(ROOT)) + ':rw'
new_mount = str(output) + ':/workspace/' + str(output.relative_to(ROOT)) + ':rw'
assert command.count(old_mount) == 1
command[command.index(old_mount)] = new_mount
main = 'net.zerocloud.pdf.acceptance.T13EvidenceCommand'
command = command[:command.index(main)] + [main]
input_path = scope / 'observations/negative/visual/vertical-font-position/extraction.pdf'
assert hashlib.sha256(input_path.read_bytes()).hexdigest() == '4eb152d0d923e2449ccd0b0751a5a606ed2dcca38b7fa0b0eba107d4201a90e4'
events = []
for i in range(1, 4):
    directory = output / ('run-' + str(i))
    argv = command + ['visual', '/workspace', '/workspace/' + str(input_path.relative_to(ROOT)),
                      'embedded-font-kinds', '/workspace/' + str(directory.relative_to(ROOT)), '0.1.0']
    log = DELIVERY / ('validation/text-visual-probe-r1-run-' + str(i) + '.txt')
    event = {'diagnostic-only': True, 'command': argv,
             'started': datetime.datetime.now(datetime.timezone.utc).isoformat()}
    with log.open('wb') as stream:
        result = subprocess.run(argv, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, timeout=120)
    properties = directory / 'result.properties'
    verdict = next((line.split('=', 1)[1] for line in properties.read_text().splitlines()
                    if line.startswith('visual=')), None) if properties.exists() else None
    event.update({'exit-code': result.returncode, 'actual-negative-control-result': verdict,
                  'finished': datetime.datetime.now(datetime.timezone.utc).isoformat(),
                  'log': {'path': str(log.relative_to(ROOT)),
                          'sha256': hashlib.sha256(log.read_bytes()).hexdigest()}})
    if properties.exists():
        event['actual-result'] = {'path': str(properties.relative_to(ROOT)),
                                  'sha256': hashlib.sha256(properties.read_bytes()).hexdigest()}
    events.append(event)
    print('visual control ' + str(i) + ': exit ' + str(result.returncode) + ', result ' + str(verdict), flush=True)
(DELIVERY / 'validation/text-visual-probe-r1-result.json').write_text(json.dumps({
    'diagnostic-only': True,
    'original-configuration': {'path': str(source.relative_to(ROOT)),
                               'sha256': hashlib.sha256(source.read_bytes()).hexdigest()},
    'input': {'path': str(input_path.relative_to(ROOT)),
              'sha256': hashlib.sha256(input_path.read_bytes()).hexdigest()},
    'changes': 'Existing visual control isolated through the existing public visual command; same image, JVM options, exact PDF, profile, pinned tools and existing tool bounds. Fresh output directories only.',
    'events': events}, indent=2) + '\n')
raise SystemExit(0 if all(e['exit-code'] == 0 and e['actual-negative-control-result'] == 'fail'
                         for e in events) else 1)
