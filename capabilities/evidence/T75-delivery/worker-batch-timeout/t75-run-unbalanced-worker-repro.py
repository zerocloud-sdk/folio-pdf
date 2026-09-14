from datetime import datetime, timezone
import json
from pathlib import Path
import subprocess
import sys

output = Path('/tmp/t75-unbalanced-worker-repro-path.txt').read_text().strip()
output = Path(output)
compile_command = json.loads((output / 'compile-command.json').read_text())
with (output / 'compile.txt').open('xb') as stream:
    compiled = subprocess.run(compile_command, stdout=stream, stderr=subprocess.STDOUT)
if compiled.returncode:
    sys.exit(compiled.returncode)
command = json.loads((output / 'worker-method-command.json').read_text())
results = []
for attempt in range(1, 4):
    path = output / ('worker-attempt-' + str(attempt) + '.txt')
    started = datetime.now(timezone.utc).isoformat()
    with path.open('xb') as stream:
        completed = subprocess.run(command, stdout=stream, stderr=subprocess.STDOUT, timeout=60)
    record = {'attempt': attempt, 'command': command, 'started-utc': started,
              'completed-utc': datetime.now(timezone.utc).isoformat(),
              'returncode': completed.returncode, 'original-log': str(path)}
    (output / ('worker-attempt-' + str(attempt) + '.json')).write_text(json.dumps(record, indent=2) + '\n')
    results.append(record)
    print('Worker attempt', attempt, 'actual exit', completed.returncode, flush=True)
(output / 'attempts.json').write_text(json.dumps(results, indent=2) + '\n')
sys.exit(1 if any(item['returncode'] for item in results) else 0)
