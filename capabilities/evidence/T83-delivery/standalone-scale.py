"""Run the explicit scale command sequentially in all four declared environments."""
import datetime
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import sys

import yaml

root = Path(__file__).resolve().parents[3]
destination = Path(__file__).resolve().parent / sys.argv[1]
destination.mkdir()
spec = importlib.util.spec_from_file_location('foundation_scale', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
receipt = driver.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
profiles = yaml.safe_load((root / contract['environments']).read_text())['profiles']
assert [p['identity']['jdk-major'] for p in profiles] == [8, 11, 17, 21]
helper = Path(os.environ['FOLIO_HARFBUZZ_HELPER']).resolve(strict=True)
results = []
for profile in profiles:
    major = profile['identity']['jdk-major']
    scope = destination / ('jdk' + str(major))
    scope.mkdir()
    observations = scope / 'profiles'
    observations.mkdir()
    environment = driver.observe_environment(root, profile['identity']['image'], helper,
                                             scope / 'environment', profile,
                                             root / 'target/foundation-0.1.0/harness')
    driver.write_json(scope / 'environment.yaml', environment)
    command = driver.container_command(root, profile['identity']['image'], helper)
    command[command.index(str(root) + ':/workspace:ro')] = str(root) + ':/workspace:rw'
    command[-1:-1] = ['--env', 'MAVEN_USER_HOME=/workspace/.build-cache/maven',
                     '--env', 'MAVEN_OPTS=-Dmaven.repo.local=/workspace/.build-cache/maven/repository',
                     '--env', 'FOLIO_T22_OUTPUT=/workspace/' + observations.relative_to(root).as_posix()]
    command += ['./scripts/t22-scale', 'all']
    driver.write_json(scope / 'command.json', command)
    started = datetime.datetime.now(datetime.timezone.utc).isoformat()
    print('Explicit ./scripts/t22-scale all on JDK ' + str(major) + ' started ' + started, flush=True)
    log = scope / 'scale.txt'
    driver.run_logged(command, log, cwd=root, timeout=1800)
    lines = log.read_text()
    assert re.search(r'Tests run: 3, Failures: 0, Errors: 0, Skipped: 0', lines), lines[-4000:]
    assert len(re.findall(r'^T22_SCALE ', lines, re.MULTILINE)) == 3
    retained = {}
    for name in ('pages', 'input', 'concurrency'):
        path = observations / name / 'observations.properties'
        assert path.is_file(), path
        retained[name] = {'path': path.relative_to(root).as_posix(),
                          'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
    driver.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
    results.append({'jdk-major': major, 'declared-environment': profile['id'],
                    'started': started, 'finished': datetime.datetime.now(datetime.timezone.utc).isoformat(),
                    'command': command, 'exit-code': 0, 'tests': 3, 'skipped': 0,
                    'environment': driver.reference(root, scope / 'environment.yaml'),
                    'log': driver.reference(root, log), 'actual-profiles': retained})
    driver.write_json(destination / 'results.json', results)
    print('JDK ' + str(major) + ': all three actual profiles passed; no skips', flush=True)
print('Explicit scale command passed in all four declared environments.', flush=True)
