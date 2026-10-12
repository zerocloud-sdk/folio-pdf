"""Retain one fresh #83 gate with primary monotonic timing and paired wall diagnostics."""
import datetime
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import traceback
import time

import yaml

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
name, mode, *arguments = sys.argv[1:]
log = delivery / 'validation' / (name + '.txt')
result_path = log.with_name(name + '-result.json')
assert not log.exists() and not result_path.exists(), 'Use a fresh attempt name'
command = arguments
identity = None
if mode == 'ubuntu':
    spec = importlib.util.spec_from_file_location('foundation_gate', root / 'scripts/t03-foundation.py')
    driver = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(driver)
    profiles = yaml.safe_load((root / 'capabilities/foundation-environments.yaml').read_text())['profiles']
    profile = next(p for p in profiles if p['identity']['jdk-major'] == 17)
    identity = profile['identity']
    helper = Path(os.environ['FOLIO_HARFBUZZ_HELPER']).resolve(strict=True)
    command = driver.container_command(root, identity['image'], helper)
    command[command.index(str(root) + ':/workspace:ro')] = str(root) + ':/workspace:rw'
    command[-1:-1] = ['--env', 'MAVEN_USER_HOME=/workspace/.build-cache/maven',
                     '--env', 'MAVEN_OPTS=-Dmaven.repo.local=/workspace/.build-cache/maven/repository',
                     '--env', 'FOLIO_FOUNDATION_PYTHON_ROOT=/workspace/.build-cache/foundation-python']
    command += arguments
elif mode != 'host':
    raise ValueError('Expected host or ubuntu')
record = {'command': command, 'repository-command': arguments, 'cwd': str(root),
          'timeout-seconds': 21600,
          'delivery-wrapper-sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
          'actual-declared-image': identity,
          'started': datetime.datetime.now(datetime.timezone.utc).isoformat(),
          'environment': {key: os.environ.get(key) for key in (
              'JAVA_HOME', 'MAVEN_USER_HOME', 'MAVEN_OPTS', 'PYTHONPATH',
              'FOLIO_FOUNDATION_PYTHON_ROOT', 'FOLIO_HARFBUZZ_HELPER', 'CONTAINERS_STORAGE_CONF')}}
record['timing-semantics'] = 'Timeout and primary elapsed duration use monotonic time; wall duration and UTC timestamps are diagnostic.'
print('Starting ' + name, flush=True)
started_wall = time.time()
started_monotonic = time.monotonic()
with log.open('wb') as output:
    try:
        process = subprocess.run(command, cwd=root, stdout=output, stderr=subprocess.STDOUT, timeout=21600)
        exit_code = process.returncode
    except subprocess.TimeoutExpired:
        exit_code = 124
        record['failure'] = 'Delivery wrapper timeout; preserve observations and verify any owned descendant before retry.'
        output.write(traceback.format_exc().encode())
elapsed_monotonic = time.monotonic() - started_monotonic
elapsed_wall = time.time() - started_wall
record.update({'monotonic-duration-seconds': elapsed_monotonic,
               'wall-duration-seconds': elapsed_wall,
               'wall-minus-monotonic-seconds': elapsed_wall - elapsed_monotonic,
               'finished': datetime.datetime.now(datetime.timezone.utc).isoformat(),
               'exit-code': exit_code,
               'log': {'path': log.relative_to(root).as_posix(),
                       'sha256': hashlib.sha256(log.read_bytes()).hexdigest()}})
result_path.write_text(json.dumps(record, indent=2) + '\n')
print(name + ' exit ' + str(exit_code), flush=True)
sys.exit(exit_code)
