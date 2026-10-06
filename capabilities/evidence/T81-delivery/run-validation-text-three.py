import datetime
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

root = Path('/workspace/folio-pdf')
name, *command = sys.argv[1:]
assert name and command
base = root / 'capabilities/evidence/T81-delivery/validation'
log = base / (name + '.txt')
record_path = base / (name + '-result.json')
assert not log.exists() and not record_path.exists(), 'Retain previous attempts; choose a fresh name'
env = os.environ.copy()
if command[:4] == ['python3', '-B', '-m', 'unittest']:
    env.pop('FOLIO_FOUNDATION_PYTHON_ROOT', None)
    env['PYTHONPATH'] = '.build-cache/foundation-host-python'
record = {'command': command, 'cwd': str(root),
    'started': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'environment': {key: env.get(key) for key in ('JAVA_HOME', 'PYTHONPATH',
        'FOLIO_FOUNDATION_PYTHON_ROOT', 'FOLIO_HARFBUZZ_HELPER')}}
print('Starting ' + name, flush=True)
with log.open('wb') as stream:
    result = subprocess.run(command, cwd=root, env=env, stdout=stream,
        stderr=subprocess.STDOUT, timeout=None if any(arg.endswith(('refresh-ordered.py', 'resume-text.py', 'resume-text-three.py', 'resume-attachments.py')) for arg in command) else 10800)
record.update({'exit-code': result.returncode,
    'finished': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'log': {'path': str(log.relative_to(root)),
        'sha256': hashlib.sha256(log.read_bytes()).hexdigest()}})
record_path.write_text(json.dumps(record, indent=2) + '\n')
print(name + ' exit ' + str(result.returncode), flush=True)
sys.exit(result.returncode)
