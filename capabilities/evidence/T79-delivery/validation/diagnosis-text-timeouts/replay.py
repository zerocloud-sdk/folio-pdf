"""Replay unchanged staged contract methods in the exact failed environment."""
import json
import os
from pathlib import Path
import subprocess
import sys
import time

ROOT = Path('/workspace/folio-pdf')
HERE = Path(__file__).resolve().parent
INSIDE = '/workspace/' + HERE.relative_to(ROOT).as_posix()
original = ROOT / 'capabilities/evidence/foundation/T79-final/text/jdk8-hardened_worker/contract-tests-command.json'
command = json.loads(original.read_text())
command[0] = str(ROOT / 'capabilities/evidence/T79-delivery/validation/container-bin/podman')
old = str(ROOT / 'capabilities/evidence/foundation/T79-final/text/jdk8-hardened_worker')
command = [str(HERE) + ':' + INSIDE + ':rw' if item.startswith(old + ':') else item for item in command]
cp_index = command.index('-cp') + 1
classpath = command[cp_index]
host_classpath = classpath.replace('/workspace/', str(ROOT) + '/')
javac = ROOT / '.build-cache/t79-resume-jdk17/jdk-17.0.20+8/bin/javac'
subprocess.run([str(javac), '--release', '8', '-cp', host_classpath, '-d', str(HERE),
                str(HERE / 'MethodReplay.java')], check=True)
main_index = command.index('org.junit.runner.JUnitCore')
command = command[:main_index] + ['MethodReplay'] + sys.argv[1:]
command[cp_index] = INSIDE + ':' + classpath
label = os.environ.get('T79_REPLAY_LABEL', 'focused-1')
(HERE / (label + '-command.json')).write_text(json.dumps(command, indent=2) + '\n')
start = time.monotonic()
with (HERE / (label + '.txt')).open('xb') as stream:
    result = subprocess.run(command, stdout=stream, stderr=subprocess.STDOUT, cwd=ROOT)
record = {'command': label + '-command.json', 'exit-code': result.returncode,
          'duration-seconds': time.monotonic() - start}
(HERE / (label + '-result.json')).write_text(json.dumps(record, indent=2) + '\n')
print(json.dumps(record))
sys.exit(result.returncode)
