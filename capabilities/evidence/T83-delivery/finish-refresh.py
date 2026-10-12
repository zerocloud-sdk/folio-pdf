"""Continue the unchanged #83 candidate after the retained Worker exit failure."""
import datetime
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys

import yaml

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
authority = root / 'capabilities/foundation-evidence.yaml'
first = json.loads((delivery / 'refresh-final-r1/results.json').read_text())
completed = ('transactions', 'values', 'pages', 'metadata', 'annotations', 'text', 'images', 'incremental')
assert [step['obligation'] for step in first[:-1]] == list(completed)
assert all(step['exit-code'] == 0 for step in first[:-1])
assert first[-1]['obligation'] == 'password-baseline' and first[-1]['exit-code'] == 1
assert first[-1]['certifications'] == 64 and first[-1]['chain-records'] == 256
assert first[-1]['current-index-sha256'] == first[-1]['prior-index']['sha256']
assert hashlib.sha256(authority.read_bytes()).hexdigest() == first[-1]['current-index-sha256']
diagnostic_gate = json.loads((delivery / 'validation/worker-exit-products-diagnosis-r2-result.json').read_text())
assert diagnostic_gate['exit-code'] == 0
diagnostic = json.loads((delivery / 'diagnostics/worker-exit-replays-r2.json').read_text())
assert len(diagnostic['results']) == 5
assert all(step['exit-code'] == 0 and not step['same-shutdown-symptom']
           and step['published-products'] == step['completed-public-records'] == 68
           for step in diagnostic['results'])
spec = importlib.util.spec_from_file_location('t83_continuation', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
build = root / 'target/foundation-0.1.0/build-inputs.json'
receipt = driver.require_staged_build(root, contract, build)
frozen = json.loads((delivery / 'source-freeze-final-r2.json').read_text())
assert driver.capture_build_inputs(root, contract) == {key: frozen[key] for key in ('inputs', 'contract-inputs')}
assert diagnostic['candidate'] == receipt['candidate']
assert yaml.safe_load(authority.read_text())['candidate'] == receipt['candidate']
name = 'refresh-final-r2'
record_dir = delivery / name
record_dir.mkdir()
retry_base = root / 'capabilities/evidence/foundation/T83-final-r2'
retry_base.mkdir()
steps = (
    ('password-baseline', retry_base / 'password-baseline', 72, 288),
    ('password-clear-metadata', root / 'capabilities/evidence/foundation/T83-final/password-clear-metadata', 80, 320),
    ('password-attachments', root / 'capabilities/evidence/foundation/T83-final/password-attachments', 88, 352),
    ('limits', root / 'capabilities/evidence/foundation/T83-final/limits', 92, 372),
    ('worker', root / 'capabilities/evidence/foundation/T83-final/worker', 96, 392),
    ('recovery', root / 'capabilities/evidence/foundation/T83-final/recovery', 100, 412))
assert all(not destination.exists() for obligation, destination, count, chains in steps)
results = []
for obligation, destination, count, chains in steps:
    driver.require_staged_build(root, contract, build)
    assert driver.capture_build_inputs(root, contract) == {key: frozen[key] for key in ('inputs', 'contract-inputs')}
    prior = record_dir / (obligation + '-prior-index.yaml')
    shutil.copyfile(authority, prior)
    arguments = ['python3', '-B', 'scripts/t03-foundation.py', 'certify',
                 destination.relative_to(root).as_posix(), '--obligation', obligation]
    command = [sys.executable, str(delivery / 'run-gate.py'), name + '-' + obligation, 'host', *arguments]
    started = datetime.datetime.now(datetime.timezone.utc).isoformat()
    print('Continuing ' + obligation + ' at ' + started, flush=True)
    process = subprocess.run(command, cwd=root)
    current = yaml.safe_load(authority.read_text())
    result = {'obligation': obligation, 'started': started,
              'finished': datetime.datetime.now(datetime.timezone.utc).isoformat(),
              'command': command, 'exit-code': process.returncode,
              'prior-index': driver.reference(root, prior),
              'current-index-sha256': driver.sha256(authority),
              'certifications': len(current['certifications']),
              'chain-records': sum(len(item['records']) for item in current['certifications'])}
    results.append(result)
    (record_dir / 'results.json').write_text(json.dumps(results, indent=2) + '\n')
    if process.returncode:
        print('Stopped at the first failed continuation: ' + obligation, flush=True)
        sys.exit(process.returncode)
    assert current['candidate'] == receipt['candidate']
    assert result['certifications'] == count and result['chain-records'] == chains
    assert {item['obligation'] for item in current['certifications']} == set(completed) | {item['obligation'] for item in results}
print('All fourteen obligations finished in contract order; the failed first attempt remains retained.', flush=True)
