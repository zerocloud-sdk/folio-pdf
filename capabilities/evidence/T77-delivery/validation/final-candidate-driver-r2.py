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
while True:
    independent = (base / 'independent-tests-r2.log').read_text(errors='replace')
    if '[INFO] BUILD FAILURE' in independent:
        raise SystemExit('Independent certification tests failed')
    result_file = base / 'matrix-results-r2.json'
    if result_file.exists():
        results = json.loads(result_file.read_text())
        if any(item['exit'] != 0 for item in results):
            raise SystemExit('JDK verification failed')
        if '[INFO] BUILD SUCCESS' in independent:
            break
    time.sleep(20)
run(['./scripts/inventory', 'validate'], 'final-inventory-validate-r2.log')
run(['./scripts/inventory', 'generate'], 'final-inventory-generate-r2.log')
run(['./scripts/inventory', 'check'], 'final-inventory-check-before-stage-r2.log')
run(['git', 'diff', '--check'], 'final-diff-check-before-stage-r2.log')
run([python, 'scripts/t03-foundation.py', 'stage'], 'final-stage-r2.log')
for obligation in ['incremental', 'transactions', 'values', 'pages', 'metadata', 'annotations', 'text', 'images']:
    run([python, 'scripts/t03-foundation.py', 'certify', 'capabilities/evidence/T77-' + obligation + '-r2', '--obligation', obligation], 'final-certify-' + obligation + '-r2.log')
run(['./scripts/inventory', 'generate'], 'final-inventory-generate-after-cert-r2.log')
run(['./scripts/inventory', 'check'], 'final-inventory-check-after-cert-r2.log')
print('All selected validations and candidate certifications completed', flush=True)
