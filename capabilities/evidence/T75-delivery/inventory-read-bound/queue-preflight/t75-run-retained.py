#!/usr/bin/env python3
"""Run one delivery command and retain its real exit status across tool turns."""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import subprocess
import sys


def now():
    return datetime.now(timezone.utc).isoformat()


parser = argparse.ArgumentParser()
parser.add_argument('--name', required=True, type=Path)
parser.add_argument('command', nargs=argparse.REMAINDER)
args = parser.parse_args()
command = args.command
if command and command[0] == '--':
    command = command[1:]
if not command:
    parser.error('a command is required')
prefix = args.name.resolve()
log_path = Path(str(prefix) + '.log')
command_path = Path(str(prefix) + '-command.json')
result_path = Path(str(prefix) + '-result.json')
for path in (log_path, command_path, result_path):
    if path.exists():
        parser.error('refusing to overwrite retained output: ' + str(path))
record = {
    'command': command,
    'cwd': str(Path.cwd()),
    'started-utc': now(),
    'wrapper-pid': os.getpid(),
    'explicit-environment': {
        key: os.environ[key]
        for key in ('FOLIO_HARFBUZZ_HELPER',)
        if key in os.environ
    },
    'original-log': str(log_path),
}
with command_path.open('x') as retained:
    json.dump(record, retained, indent=2)
    retained.write('\n')
result = dict(record)
try:
    with log_path.open('xb') as output:
        process = subprocess.Popen(command, stdout=output, stderr=subprocess.STDOUT)
        print('Started PID', process.pid, 'with original output at', log_path, flush=True)
        result['command-pid'] = process.pid
        result['returncode'] = process.wait()
except Exception as error:
    result['returncode'] = None
    result['runner-error'] = repr(error)
result['completed-utc'] = now()
with result_path.open('x') as retained:
    json.dump(result, retained, indent=2)
    retained.write('\n')
print(json.dumps(result), flush=True)
sys.exit(result['returncode'] if result['returncode'] is not None else 1)
