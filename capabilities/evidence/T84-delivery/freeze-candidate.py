"""Freeze the corrected candidate and compare its inputs with preserved r1 bytes."""
import hashlib
import json
from pathlib import Path
import sys

import yaml

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
sys.path.insert(0, str(root / 'scripts'))
import t05_foundation_reports

module = t05_foundation_reports.foundation()
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
receipt = module.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
old_path = delivery / 'development/superseded-candidate-r1/source-freeze.json'
old = json.loads(old_path.read_text())
old_inputs = {item['path']: item['sha256'] for item in old['candidate-inputs']}
new_inputs = {item['path']: item['sha256'] for item in receipt['candidate']['inputs']}
assert old_inputs.keys() == new_inputs.keys()
changed = sorted(name for name in old_inputs if old_inputs[name] != new_inputs[name])
assert changed == ['scripts/t20-evidence-pin.properties', 'scripts/t21-evidence-pin.properties',
                   'scripts/t22-evidence-pin.properties'], changed
assert receipt['contract-inputs'] == old['contract-inputs']
assert module.sha256(delivery / 'execution-contract.md') == old['contract-source-sha256']
assert module.sha256(Path('/workspace/contracts/issue-84-contract.md')) == old['contract-source-sha256']
proof = json.loads((delivery / 'validation/source-pin-check-final-r2-r2-result.json').read_text())
assert proof['exit-code'] == 0 and module.sha256(root / proof['log']) == proof['log-sha256']
probe = delivery / 'development/candidate-r2-identity'
probe.mkdir()
provisional = {'schema-version': 1, 'candidate': receipt['candidate'], 'environments': [], 'certifications': []}
identities = module.candidate_identities(root, root / 'capabilities/foundation-evidence.yaml',
                                       provisional, probe / 'readiness.txt')
assert identities['Candidate'] != old['final-identities']['Candidate']
assert identities['Contract'] == old['final-identities']['Contract']
freeze = {key: old[key] for key in ('baseline', 'contract-source-sha256', 'development-proof', 'source-review')}
freeze.update({'candidate-inputs': receipt['candidate']['inputs'], 'contract-inputs': receipt['contract-inputs'],
               'final-identities': identities, 'final-evidence-base': 'capabilities/evidence/foundation/T84-final-r2',
               'pin-corrections': [{'path': name, 'previous-sha256': old_inputs[name], 'sha256': new_inputs[name]}
                                   for name in changed],
               'recertification': 'Fresh r2 observations for all inherited obligations and Providers; r1 evidence and its freeze/archives remain intact and untracked.',
               'source-pin-preflight': module.reference(root, delivery / 'validation/source-pin-check-final-r2-r2.txt')})
for key, name in (('final-build-inputs', 'build-inputs.json'), ('final-build-command', 'build-command.json'),
                  ('final-build-log', 'build.txt')):
    freeze[key] = module.reference(root, root / 'target/foundation-0.1.0' / name)
module.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
module.write_json(delivery / 'source-freeze.json', freeze)
print(json.dumps({'result': 'pass', 'changed-source-inputs': changed, 'contract-inputs-unchanged': True,
                  'source-inputs': len(new_inputs), 'identities': identities}), flush=True)
