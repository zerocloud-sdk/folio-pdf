"""Run the failed existing method in isolation; diagnostic only, no certification."""
import datetime
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path('/workspace/folio-pdf')
DELIVERY = ROOT / 'capabilities/evidence/T81-delivery'
source = ROOT / 'capabilities/evidence/foundation/T81-final/text-r3/jdk8-hardened_worker/contract-tests-command.json'
original = json.loads(source.read_text())
output = DELIVERY / 'validation/text-timeout-probe-r1-scope'
output.mkdir()
old_scope = source.parent
old_mount = str(old_scope) + ':/workspace/' + str(old_scope.relative_to(ROOT)) + ':rw'
new_mount = str(output) + ':/workspace/' + str(output.relative_to(ROOT)) + ':rw'
command = original[:]
assert command.count(old_mount) == 1
command[command.index(old_mount)] = new_mount
prefix = command[:command.index('java')]
classpath = command[command.index('-cp') + 1]
probe_dir = '/workspace/' + str(output.relative_to(ROOT))
java_source = DELIVERY / 'TextTimeoutProbe.java'
compile_command = prefix + ['javac', '-source', '8', '-target', '8', '-cp', classpath,
                           '-d', probe_dir, '/workspace/' + str(java_source.relative_to(ROOT))]
probe = command[:command.index('org.junit.runner.JUnitCore')]
probe[probe.index('-cp') + 1] += ':' + probe_dir
probe += ['TextTimeoutProbe', 'net.zerocloud.pdf.consumer.TextStructureExtractionWorkflowTest',
          'unbalancedSupportedOperatorStateCannotPublishPrefix']
events = []
for label, argv in [('compile', compile_command)] + [('method-' + str(i), probe) for i in range(1, 4)]:
    log = DELIVERY / ('validation/text-timeout-probe-r1-' + label + '.txt')
    event = {'diagnostic-only': True, 'command': argv,
             'started': datetime.datetime.now(datetime.timezone.utc).isoformat()}
    with log.open('wb') as stream:
        result = subprocess.run(argv, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, timeout=60)
    event.update({'exit-code': result.returncode,
                  'finished': datetime.datetime.now(datetime.timezone.utc).isoformat(),
                  'log': {'path': str(log.relative_to(ROOT)),
                          'sha256': hashlib.sha256(log.read_bytes()).hexdigest()}})
    events.append(event)
    print(label + ' exit ' + str(result.returncode), flush=True)
    if label == 'compile' and result.returncode:
        break
(DELIVERY / 'validation/text-timeout-probe-r1-result.json').write_text(json.dumps({
    'diagnostic-only': True,
    'original-command': {'path': str(source.relative_to(ROOT)),
                         'sha256': hashlib.sha256(source.read_bytes()).hexdigest()},
    'probe-source': {'path': str(java_source.relative_to(ROOT)),
                     'sha256': hashlib.sha256(java_source.read_bytes()).hexdigest()},
    'changes': 'Existing failing method isolated with Request.method; same image, JVM settings and existing test timeout. Fresh diagnostic output and diagnostic runner class only.',
    'events': events}, indent=2) + '\n')
raise SystemExit(0 if len(events) == 4 and all(e['exit-code'] == 0 for e in events) else 1)
