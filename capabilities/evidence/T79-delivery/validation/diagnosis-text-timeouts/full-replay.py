"""Diagnostic replay of the exact failed suite, with the unchanged timeout."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import time

ROOT = Path('/workspace/folio-pdf')
HERE = Path(__file__).resolve().parent
INSIDE = '/workspace/' + HERE.relative_to(ROOT).as_posix()
source = ROOT / 'capabilities/evidence/foundation/T79-final/text/jdk8-hardened_worker/contract-tests-command.json'
command = json.loads(source.read_text())
command[0] = str(ROOT / 'capabilities/evidence/T79-delivery/validation/container-bin/podman')
old = str(ROOT / 'capabilities/evidence/foundation/T79-final/text/jdk8-hardened_worker')
command = [str(HERE) + ':' + INSIDE + ':rw' if item.startswith(old + ':') else item for item in command]
spec = importlib.util.spec_from_file_location('foundation', ROOT / 'scripts/t03-foundation.py')
foundation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(foundation)
timeout = foundation.certification_case('text').get('contract-timeout', 300)
label = os.environ.get('T79_REPLAY_LABEL', 'full-1')
(HERE / (label + '-command.json')).write_text(json.dumps(command, indent=2) + '\n')
start = time.monotonic()
with (HERE / (label + '.txt')).open('xb') as stream:
    result = subprocess.run(command, stdout=stream, stderr=subprocess.STDOUT, cwd=ROOT, timeout=timeout)
record = {'command': label + '-command.json', 'exit-code': result.returncode,
          'unchanged-timeout-seconds': timeout, 'duration-seconds': time.monotonic() - start}
(HERE / (label + '-result.json')).write_text(json.dumps(record, indent=2) + '\n')
print(json.dumps(record))
sys.exit(result.returncode)
