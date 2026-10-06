"""Refresh the frozen #82 candidate in contract order; stop on first failure."""
import datetime
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys

import yaml

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
name = sys.argv[1]
record_dir = delivery / name
record_dir.mkdir()
output_base = root / 'capabilities/evidence/foundation/T82-final'
output_base.mkdir(exist_ok=True)
obligations = ('transactions', 'values', 'pages', 'metadata', 'annotations',
               'text', 'images', 'incremental', 'password-baseline',
               'password-clear-metadata', 'password-attachments', 'limits', 'worker')
results = []
for obligation in obligations:
    destination = output_base / obligation
    if destination.exists():
        raise ValueError('Preserve existing observations; choose a fresh final attempt')
    index = root / 'capabilities/foundation-evidence.yaml'
    prior = record_dir / (obligation + '-prior-index.yaml')
    shutil.copyfile(index, prior)
    command = [sys.executable, str(delivery / 'run-gate.py'),
               name + '-' + obligation, 'host', 'python3', '-B',
               'scripts/t03-foundation.py', 'certify',
               destination.relative_to(root).as_posix(), '--obligation', obligation]
    started = datetime.datetime.now(datetime.timezone.utc).isoformat()
    print('Refreshing ' + obligation + ' at ' + started, flush=True)
    process = subprocess.run(command, cwd=root)
    current = yaml.safe_load(index.read_text())
    results.append({'obligation': obligation, 'started': started,
                    'finished': datetime.datetime.now(datetime.timezone.utc).isoformat(),
                    'command': command, 'exit-code': process.returncode,
                    'prior-index': {'path': prior.relative_to(root).as_posix(),
                                    'sha256': hashlib.sha256(prior.read_bytes()).hexdigest()},
                    'current-index-sha256': hashlib.sha256(index.read_bytes()).hexdigest(),
                    'certifications': len(current['certifications']),
                    'chain-records': sum(len(item['records']) for item in current['certifications'])})
    (record_dir / 'results.json').write_text(json.dumps(results, indent=2) + '\n')
    if process.returncode:
        print('Stopped at the first failed certification: ' + obligation, flush=True)
        sys.exit(process.returncode)
print('All thirteen obligations refreshed in the required order.', flush=True)
