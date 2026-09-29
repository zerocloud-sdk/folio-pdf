import json
import os
import subprocess
import time
from pathlib import Path
root = Path('/workspace/folio-pdf')
base = root / '.build-cache/T77-preflight'
env = os.environ.copy()
env.update(MAVEN_USER_HOME=str(root / '.build-cache/maven'), MAVEN_ARGS='-Dmaven.repo.local=' + str(root / '.build-cache/maven/repository'), FOLIO_HARFBUZZ_HELPER=str(base / 'harfbuzz/bin/folio-harfbuzz'), FOLIO_FOUNDATION_PYTHON_ROOT=str(base / 'foundation-python'))
python = str(base / 'python/bin/python')
def run(command, log):
    print('Starting ' + ' '.join(command), flush=True)
    with (base / log).open('wb') as output:
        result = subprocess.run(command, cwd=root, env=env, stdout=output, stderr=subprocess.STDOUT)
    print('Finished exit ' + str(result.returncode) + ': ' + log, flush=True)
    if result.returncode:
        raise SystemExit(result.returncode)
run(['./scripts/inventory', 'validate'], 'final-inventory-validate-r3.log')
run(['./scripts/inventory', 'generate'], 'final-inventory-generate-r3.log')
run(['./scripts/inventory', 'check'], 'final-inventory-check-before-stage-r3.log')
run(['git', 'diff', '--check'], 'final-diff-check-before-stage-r3.log')
run([python, 'scripts/t03-foundation.py', 'stage'], 'final-stage-r3.log')
import shutil
validation = root / 'capabilities/evidence/T77-delivery/validation'
for source, destination in [('build-inputs.json', 'final-stage-inputs-r3.json'), ('build-command.json', 'final-stage-command-r3.json'), ('build.log', 'final-stage-build-r3.txt')]:
    original = root / 'target/foundation-0.1.0' / source
    if original.exists():
        shutil.copyfile(original, validation / destination)
for obligation in ['incremental', 'transactions', 'values', 'pages', 'metadata', 'annotations', 'text', 'images']:
    run([python, 'scripts/t03-foundation.py', 'certify', 'capabilities/evidence/T77-' + obligation + '-r3', '--obligation', obligation], 'final-certify-' + obligation + '-r3.log')
run(['./scripts/inventory', 'generate'], 'final-inventory-generate-after-cert-r3.log')
run(['./scripts/inventory', 'check'], 'final-inventory-check-after-cert-r3.log')
print('All selected validations and candidate certifications completed', flush=True)
