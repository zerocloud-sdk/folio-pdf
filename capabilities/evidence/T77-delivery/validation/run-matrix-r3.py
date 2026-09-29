import concurrent.futures
import json
import os
import subprocess
from pathlib import Path
root = Path('/workspace/folio-pdf')
base = root / '.build-cache/T77-preflight'
env = os.environ.copy()
env.update(MAVEN_USER_HOME=str(root / '.build-cache/maven'), MAVEN_ARGS='-Dmaven.repo.local=' + str(root / '.build-cache/maven/repository'), FOLIO_HARFBUZZ_HELPER=str(base / 'harfbuzz/bin/folio-harfbuzz'), PODMAN_COMMAND=str(base / 'matrix-podman.py'))
def run(jdk):
    command = ['./scripts/verify-jdk-matrix.sh', str(jdk)]
    print('Starting JDK ' + str(jdk), flush=True)
    with (base / ('matrix-jdk' + str(jdk) + '-r3.log')).open('wb') as log:
        result = subprocess.run(command, cwd=root, env=env, stdout=log, stderr=subprocess.STDOUT)
    print('JDK ' + str(jdk) + ' exit ' + str(result.returncode), flush=True)
    return {'jdk': jdk, 'command': command, 'exit': result.returncode}
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
    results = list(pool.map(run, [8, 11, 17, 21]))
(base / 'matrix-results-r3.json').write_text(json.dumps(results, indent=2) + '\n')
raise SystemExit(1 if any(row['exit'] for row in results) else 0)
