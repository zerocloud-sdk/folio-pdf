"""Observe the failed environment again without changing its configuration."""
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path('/workspace/folio-pdf')
HERE = Path(__file__).resolve().parent
command = json.loads((HERE / 'focused-1-command.json').read_text())
base = command[:command.index('java')]
observations = {}
for name, args in [('java-version', ['java', '-version']),
                   ('environment-hashes', ['sha256sum', '/opt/java/openjdk/bin/java', '/etc/os-release'])]:
    actual = base + args
    (HERE / (name + '-command.json')).write_text(json.dumps(actual, indent=2) + '\n')
    result = subprocess.run(actual, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=True)
    path = HERE / (name + '.txt')
    with path.open('xb') as stream:
        stream.write(result.stdout)
    observations[name] = {'path': path.relative_to(ROOT).as_posix(),
                          'sha256': hashlib.sha256(result.stdout).hexdigest()}
version = (HERE / 'java-version.txt').read_text()
hashes = (HERE / 'environment-hashes.txt').read_text()
assert '1.8.0_502' in version and '1.8.0_502-b07' in version
assert 'fa55c40b8accf16501ed11ac41a2b6cf5f411d36ea083f935d4a87cd3a7131a8' in hashes
assert '01af466feb100306498c86aa6bad1815e33036019aa34d4362c20f374ea5c829' in hashes
record = {'result': 'pass', 'observations': observations,
          'image': next(value for value in base if value.startswith('docker.io/'))}
(HERE / 'environment-check.json').write_text(json.dumps(record, indent=2) + '\n')
print(json.dumps(record))
