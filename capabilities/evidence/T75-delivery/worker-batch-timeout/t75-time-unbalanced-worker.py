from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import subprocess

root = Path.cwd()
repro = Path(Path('/tmp/t75-unbalanced-worker-repro-path.txt').read_text().strip())
output = repro / 'timing'
output.mkdir()
source = root / 'pdf-document/src/test/java/net/zerocloud/pdf/consumer/TextStructureExtractionWorkflowTest.java'
original = source.read_text()
start = original.index('    @Test(timeout = 10000L)\n    public void unbalancedSupportedOperatorStateCannotPublishPrefix()')
end = original.index('    @Test(timeout = 10000L)', start + 30)
block = original[start:end]
timed = block.replace('        String[] malformedOperators = {',
    '        long timingStart = System.nanoTime();\n        String[] malformedOperators = {', 1)
for target, label in (('source', 'page-" + index + "'), ('form', 'form')):
    line = '            ' if target == 'source' else '        '
    pair = line + 'assertQueryFailure(' + target + ', limits());\n' + line + 'assertQueryFailure(' + target + ', limits());'
    assert timed.count(pair) == 1
    replacement = []
    for attempt in (1, 2):
        for when in ('before', 'after'):
            if when == 'after':
                replacement.append(line + 'assertQueryFailure(' + target + ', limits());')
            replacement.append(line + 'System.err.println("[T75-TIMING] ' + label + '-' + str(attempt) + ' ' + when + ' " + ((System.nanoTime() - timingStart) / 1000000));')
    timed = timed.replace(pair, '\n'.join(replacement))
form_only = timed[:timed.index('        String[] malformedOperators')] + timed[timed.index('        Path form ='):]
base_command = json.loads((repro / 'worker-method-command.json').read_text())
base_compile = json.loads((repro / 'compile-command.json').read_text())
compiled_root = Path(base_compile[-2]).parent
commands = {}
for name, body in (('timed-full', timed), ('timed-form-only', form_only)):
    variant_source = output / name / source.name
    variant_source.parent.mkdir()
    variant_source.write_text(original[:start] + body + original[end:])
    classes = compiled_root / name
    classes.mkdir()
    compile_command = base_compile[:-3] + ['-d', str(classes), str(variant_source)]
    (variant_source.parent / 'compile-command.json').write_text(json.dumps(compile_command, indent=2) + '\n')
    with (variant_source.parent / 'compile.txt').open('xb') as stream:
        subprocess.run(compile_command, stdout=stream, stderr=subprocess.STDOUT, check=True)
    command = list(base_command)
    index = command.index('-cp') + 1
    command[index] = '/workspace/' + classes.relative_to(root).as_posix() + ':' + command[index]
    commands[name] = command
commands['jdk17-timed-full'] = [
    'docker.io/library/eclipse-temurin@sha256:61a94244559f2e89e4edb02bae37eeb8762ecf5deaf237251fa630e5120a8798'
    if value.startswith('docker.io/library/eclipse-temurin@sha256:') else value
    for value in commands['timed-full']]
commands['jdk8-in-process-original'] = [
    '-Dfolio.t13.executionProfile=IN_PROCESS'
    if value == '-Dfolio.t13.executionProfile=HARDENED_WORKER' else value
    for value in base_command]
records = []
for name, command in commands.items():
    started = datetime.now(timezone.utc).isoformat()
    with (output / (name + '.txt')).open('xb') as stream:
        completed = subprocess.run(command, stdout=stream, stderr=subprocess.STDOUT, timeout=60)
    record = {'name': name, 'command': command, 'started-utc': started,
              'completed-utc': datetime.now(timezone.utc).isoformat(),
              'returncode': completed.returncode,
              'scope': 'diagnostic only; staged product/test JARs and repository sources unchanged; every copied method retains the original 10000ms JUnit timeout'}
    records.append(record)
    (output / (name + '.json')).write_text(json.dumps(record, indent=2) + '\n')
    print(name, 'actual exit', completed.returncode, flush=True)
assert source.read_text() == original
(output / 'observations.json').write_text(json.dumps({
    'original-source-sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
    'runs': records,
    'diagnostic-edits': 'Target-method timing prints; separate variant removes the fourteen Page-source workflows to isolate the two Form workflows. No timeout is raised. Other comparisons change only JDK image or Native execution profile.'}, indent=2) + '\n')
