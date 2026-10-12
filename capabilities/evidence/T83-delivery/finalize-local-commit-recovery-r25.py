"""Bind the reviewed, actually proved delivery-only local staging recovery."""
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import sys

import yaml

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
baseline = '42fe5c797711070b319b9419e14fcb7adfa98735'
sys.path.insert(0, str(root / 'scripts'))
spec = importlib.util.spec_from_file_location('t83_commit_recovery_finalize_r25', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
frozen = json.loads((delivery / 'source-freeze-final-r10.json').read_text())
assert driver.capture_build_inputs(root, contract) == {key: frozen[key] for key in ('inputs', 'contract-inputs')}
assert driver.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json') == \
    json.loads((delivery / 'candidate-final-r10/build-inputs.json').read_text())
assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip() == baseline
assert subprocess.check_output(['git', 'rev-list', '--count', baseline + '..HEAD'], cwd=root, text=True).strip() == '0'
audit = json.loads((delivery / 'final-audit-r23.json').read_text())
assert audit['certification-tuples'] == 100 and audit['chain-records'] == 412
assert driver.reference(root, root / audit['current-index']['path']) == audit['current-index']
proof_path = delivery / 'local-commit-guard-proof-r26.json'
proof = json.loads(proof_path.read_text())
assert proof['result'] == 'pass' and proof['isolated-commits-created'] == 0
assert proof['target-head-unchanged'] and not proof['target-index-written']
assert len(proof['observations']) == 10
assert proof['guard-sha256'] == driver.sha256(delivery / 'commit-git-guard-r26.py')
partial_path = delivery / 'partial-staged-admission-preflight-r25.json'
partial = json.loads(partial_path.read_text())
assert partial['result'] == 'pass' and partial['partial-staged-files'] == 495057
assert partial['commits-created'] == 0 and not partial['target-index-written']
assert partial['guard-sha256'] == driver.sha256(delivery / 'commit-git-guard-r25.py')
gate_path = delivery / 'validation/local-commit-guards-r26-result.json'
gate = json.loads(gate_path.read_text())
assert gate['exit-code'] == 0 and driver.reference(root, root / gate['log']['path']) == gate['log']
failure_path = delivery / 'validation/local-commit-r23-failure.json'
failure = json.loads(failure_path.read_text())
assert failure['exit-code'] == 1 and failure['commits-created'] == 0 and failure['git-exit-code'] == 128
assert driver.reference(root, root / failure['helper']['path']) == failure['helper']
reviews = []
for axis in ('standards', 'spec'):
    path = delivery / 'reviews' / (axis + '-local-commit-r25.md')
    assert re.findall(r'^R25 local commit recovery verdict: .+$', path.read_text(), re.MULTILINE) == [
        'R25 local commit recovery verdict: PASS; actionable findings: 0.']
    reviews.append(driver.reference(root, path))
inputs = [driver.reference(root, delivery / name) for name in (
    'commit-git-guard-r26.py', 'commit-git-guard-r25.py', 'local-commit-r25.py', 'audit-relaunch-ownership-r25.py',
    'write-relaunch-receipt-r25.py', 'finalize-local-commit-recovery-r25.py',
    'prove-local-commit-guards-r26.py', 'failed-attempts-before-local-commit-r25.md')]
assert driver.sha256(delivery / 'local-commit-r25.py') in (delivery / 'reviews/standards-local-commit-r25.md').read_text()
path = delivery / 'local-commit-recovery-r25.json'
assert not path.exists()
driver.write_json(path, {'baseline': baseline, 'commits-before-resume': 0, 'candidate-unchanged': True,
    'source-freeze': driver.reference(root, delivery / 'source-freeze-final-r10.json'),
    'completed-candidate-audit': driver.reference(root, delivery / 'final-audit-r23.json'),
    'current-index': audit['current-index'], 'failed-controller': driver.reference(root, failure_path),
    'original-ownership-summary': driver.reference(root, delivery / 'changed-file-ownership-final-r23.json'),
    'real-git-guard-proof': driver.reference(root, proof_path), 'proof-gate': driver.reference(root, gate_path),
    'actual-partial-stage-preflight': driver.reference(root, partial_path),
    'reviewed-inputs': inputs + reviews + [driver.reference(root, proof_path), driver.reference(root, gate_path),
                                       driver.reference(root, partial_path)],
    'reviews': reviews, 'maximum-staging-index-lock-retry-monotonic-seconds': 90,
    'commit-index-lock-retries': 0,
    'partial-admission': 'Verify all cached paths/modes/blob SHA256/length against the original seal; skip only rows whose bytes still equal the renewed seal.',
    'final-admission': 'Every staged blob including both new seal files must match the renewed final ownership before exactly one DCO commit.',
    'temporary-split-index': 'Originally unset; enabled for bounded index writes; restored after successful commit.',
    'unchanged-gates': 'Frozen product/contracts/candidate, all14/100/412 certifications, full verify, matrix, scale and live inventory remain unchanged.',
    'publication-and-tracker': 'No push/PR/merge/issue edit/release; orchestrator retains publication.'})
print('Reviewed local staging recovery authority bound; product/certification candidate unchanged; zero commits so far.')
