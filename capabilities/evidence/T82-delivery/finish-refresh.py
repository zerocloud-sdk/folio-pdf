"""Continue the frozen #82 refresh after the retained delivery-wrapper timeout."""
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
name = 'refresh-final-r2'
record_dir = delivery / name
record_dir.mkdir()
authority = root / 'capabilities/foundation-evidence.yaml'
prior_attempt = json.loads((delivery / 'refresh-final-r1/results.json').read_text())
assert prior_attempt[-1]['exit-code'] == 1 and prior_attempt[-1]['obligation'] == 'password-attachments'
assert hashlib.sha256(authority.read_bytes()).hexdigest() == prior_attempt[-1]['current-index-sha256']
guard = json.loads((delivery / 'validation/t80-resume-guards-r1-result.json').read_text())
assert guard['exit-code'] == 0
steps = (
    ('password-attachments', ['python3', '-B', 'capabilities/evidence/T82-delivery/resume-attachments.py'], 88, 352),
    ('limits', ['python3', '-B', 'scripts/t03-foundation.py', 'certify',
                'capabilities/evidence/foundation/T82-final/limits', '--obligation', 'limits'], 92, 372),
    ('worker', ['python3', '-B', 'scripts/t03-foundation.py', 'certify',
                'capabilities/evidence/foundation/T82-final/worker', '--obligation', 'worker'], 96, 392))
results = []
for obligation, arguments, count, chains in steps:
    prior = record_dir / (obligation + '-prior-index.yaml')
    shutil.copyfile(authority, prior)
    command = [sys.executable, str(delivery / 'run-gate.py'), name + '-' + obligation, 'host', *arguments]
    started = datetime.datetime.now(datetime.timezone.utc).isoformat()
    print('Continuing ' + obligation + ' at ' + started, flush=True)
    process = subprocess.run(command, cwd=root)
    current = yaml.safe_load(authority.read_text())
    result = {'obligation': obligation, 'started': started,
        'finished': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'command': command,
        'exit-code': process.returncode, 'prior-index': {'path': prior.relative_to(root).as_posix(),
            'sha256': hashlib.sha256(prior.read_bytes()).hexdigest()},
        'current-index-sha256': hashlib.sha256(authority.read_bytes()).hexdigest(),
        'certifications': len(current['certifications']),
        'chain-records': sum(len(item['records']) for item in current['certifications'])}
    results.append(result)
    (record_dir / 'results.json').write_text(json.dumps(results, indent=2) + '\n')
    if process.returncode:
        print('Stopped at the first failed continuation: ' + obligation, flush=True)
        sys.exit(process.returncode)
    assert result['certifications'] == count and result['chain-records'] == chains
print('Finished all thirteen obligations in contract order, preserving the failed first attachment attempt.', flush=True)
