"""Prove the final live readiness result preserves every initially satisfied scope."""
import hashlib
import json
from pathlib import Path
import re

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
validation = delivery / 'validation'
record = json.loads((validation / 'readiness-final-result.json').read_text())
log = root / record['log']
assert hashlib.sha256(log.read_bytes()).hexdigest() == record['log-sha256']
text = log.read_text()
identities = json.loads((delivery / 'source-freeze.json').read_text())['final-identities']
for name, expected in identities.items():
    assert re.search(r'^' + name + r' identity: ' + expected + r'$', text, re.MULTILINE)
prior = {'transactions', 'values', 'pages', 'metadata', 'annotations', 'text', 'images', 'incremental',
         'security', 'limits', 'worker', 'recovery', 'password-baseline', 'password-clear-metadata', 'password-attachments'}
satisfied = set(re.findall(r'(?m)^SATISFIED (\S+) \(#\d+\)$', text))
assert prior | {'providers'} <= satisfied, 'Previously satisfied or Provider scope lost live readiness'
assert record['exit-code'] == 1 and 'Foundation 0.1.0: NOT READY' in text
assert not re.search(r'(?m)^BLOCKED release:', text), 'Global actual environment/candidate error remains'
blocked = re.findall(r'(?m)^BLOCKED (\S+) \(#(\d+)\): (.*)$', text)
assert blocked and all(name not in prior | {'providers'} for name, _, _ in blocked)
index = json.loads((root / 'capabilities/foundation-evidence.yaml').read_text())
assert len(index['certifications']) == 108
assert sum(len(item['records']) for item in index['certifications']) == 452
provider = [item for item in index['certifications'] if item['obligation'] == 'providers']
assert len(provider) == 8 and sum(len(item['records']) for item in provider) == 40
result = {'result': 'pass', 'global-foundation-ready': False, 'readiness-exit-code': record['exit-code'],
          'candidate-sha256': identities['Candidate'], 'contract-sha256': identities['Contract'],
          'readiness-log': record['log'], 'readiness-log-sha256': record['log-sha256'],
          'preserved-initially-satisfied-obligations': sorted(prior), 'satisfied-obligations': sorted(satisfied),
          'provider-tuples': 8, 'provider-chains': 40, 'total-tuples': 108, 'total-chains': 452,
          'unrelated-blocked-obligations': sorted({name for name, _, _ in blocked}),
          'unrelated-blockers': [{'obligation': name, 'slice': int(issue), 'reason': reason} for name, issue, reason in blocked],
          'interpretation': 'Provider and all initially satisfied obligations are live SATISFIED; global readiness is false solely for unrelated outstanding release obligations.'}
output = validation / 'readiness-analysis.json'
assert not output.exists(), 'Readiness analysis must be fresh'
output.write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps({key: result[key] for key in ('result', 'global-foundation-ready', 'satisfied-obligations', 'unrelated-blocked-obligations')}, indent=2), flush=True)
