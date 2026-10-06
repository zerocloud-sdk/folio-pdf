"""Summarize actual relaunch attachment attempts without supplying certification."""
import hashlib
import json
import re
from pathlib import Path

root = Path('/workspace/folio-pdf')
delivery = root / 'capabilities/evidence/T81-delivery'
output = delivery / 'relaunch-attachment-outcome.json'
assert not output.exists(), 'Preserve the retained outcome'

def reference(path):
    return {'path': path.relative_to(root).as_posix(),
            'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}

results = sorted((delivery / 'validation').glob('attachments-final-r2-relaunch-r*-result.json'),
                 key=lambda path: int(re.search(r'-r(\d+)-result.json$', path.name).group(1)))
assert len(results) >= 4
failed = []
passed = []
for path in results:
    result = json.loads(path.read_text())
    command = result['command']
    assert command[1] == '-B' and command[3] == 'certify'
    assert command[5:] == ['--obligation', 'password-attachments']
    scope = root / command[4]
    scope.relative_to(root / 'capabilities/evidence/foundation/T81-final-r2')
    assert reference(root / result['log']['path']) == result['log']
    if result['exit-code']:
        transcripts = list(scope.glob('jdk*-*/contract-tests.txt'))
        assert transcripts
        transcript = max(transcripts, key=lambda item: item.stat().st_mtime_ns)
        text = transcript.read_text()
        failed.append({'validation-result': reference(path), 'log': result['log'],
                       'contract-tests': reference(transcript),
                       'suite-result': 'fail' if 'FAILURES!!!' in text else
                           'pass' if 'OK (24 tests)' in text else 'incomplete',
                       'obligation-attempt-result': 'unsuccessful; not accepted as a complete obligation'})
    else:
        audit = scope / 'resume-audit.json'
        retained = json.loads(audit.read_text())
        assert retained['result'] == 'pass' and retained['fresh-scopes'] == 1
        assert len(retained['retained-original-scopes']) == 7
        assert retained['expected-scope-count'] == 8
        passed.append({'validation-result': reference(path), 'resume-audit': reference(audit)})
assert len(passed) == 1 and results[-1] == root / passed[0]['validation-result']['path']
assert len(failed) == len(results) - 1
output.write_text(json.dumps({'status': 'pass', 'recipe': reference(Path(__file__)),
    **passed[0], 'failed-attempts': failed,
    'diagnostics-supply-certification': False,
    'bounds-or-product-changed': False}, indent=2) + '\n')
print('Retained', len(failed), 'unsuccessful relaunch attempts and one complete accepted recovery')
