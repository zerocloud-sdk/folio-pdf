"""Audit retained diagnostic outputs without recreating the overwritten summary."""
import hashlib
import json
import re
from pathlib import Path

root = Path('/workspace/folio-pdf')
delivery = root / 'capabilities/evidence/T81-delivery'
output = delivery / 'validation/attachment-visual-probe-r2-retained-audit.json'
assert not output.exists(), 'Retain earlier evidence'

def reference(path):
    return {'path': path.relative_to(root).as_posix(),
            'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}

wrapper_path = delivery / 'validation/attachment-visual-probe-r2-result.json'
wrapper = json.loads(wrapper_path.read_text())
assert wrapper['exit-code'] == 0
assert wrapper['command'] == ['python3', '-B',
    'capabilities/evidence/T81-delivery/diagnose-attachment-visual.py']
assert reference(root / wrapper['log']['path']) == wrapper['log']
assert (root / wrapper['log']['path']).read_text().splitlines() == [line for number in range(1, 4)
    for line in ('Diagnostic-only exact attachment visual observation ' + str(number),
                 'Diagnostic visual exit 0')]
original = root / 'capabilities/evidence/foundation/T81-final/password-attachments/jdk8-in_process'
source = original / 'observations/native-credential-empty-user/product.pdf'
source_hash = reference(source)['sha256']
expected = root / 'capabilities/profiles/T78-password/expected.png'
base = delivery / 'validation/attachment-visual-probe-r2-scope'
events = []
for number in range(1, 4):
    scope = base / ('run-' + str(number))
    probe = json.loads((scope / 'probe-result.json').read_text())
    assert probe == {'diagnostic-only': True, 'result': 'pass', 'input-sha256': source_hash}
    clear = json.loads((scope / 'unauthenticated/result.json').read_text())
    assert clear['result'] == 'pass' and clear['unauthenticated']
    assert clear['input-sha256'] == source_hash
    visual = json.loads((scope / 'visual/result.json').read_text())
    assert visual['chain'] == 'visual' and visual['result'] == 'pass'
    assert visual['input-sha256'] == source_hash
    assert (visual['dpi'], visual['fuzz'], visual['metric'], visual['threshold']) == (144, 0, 'AE', 0)
    assert len(visual['pages']) == 1 and visual['pages'][0]['absolute-error'] == '0'
    page = visual['pages'][0]
    assert page['page'] == 1 and page['expected-sha256'] == reference(expected)['sha256']
    assert page['raster-sha256'] == reference(scope / 'visual/page-1/actual.png')['sha256']
    for relative in ('unauthenticated/pypdf-clear-copy', 'visual/page-1/render', 'visual/page-1/compare'):
        command = json.loads((scope / (relative + '.command.json')).read_text())
        assert command['exit-code'] == 0
        for stream in ('stdout', 'stderr'):
            assert command[stream + '-sha256'] == reference(scope / (relative + '.' + stream))['sha256']
    assert not (scope / 'visual/page-1/compare.stdout').read_text().strip()
    assert re.fullmatch(r'0(?:\.0+)?(?:\s+\(0(?:\.0+)?\))?\s*',
                       (scope / 'visual/page-1/compare.stderr').read_text())
    events.append({'run': number, 'actual-result': reference(scope / 'probe-result.json'),
                   'log': reference(base / ('run-' + str(number) + '.txt')),
                   'retained-files': [reference(path) for path in sorted(scope.rglob('*')) if path.is_file()]})
record = {'status': 'pass', 'diagnostic-only': True, 'certification': 'none',
    'recipe': reference(Path(__file__)), 'original-wrapper-result': reference(wrapper_path),
    'original-wrapper-log': wrapper['log'],
    'original-diagnostic-recipe': reference(delivery / 'diagnose-attachment-visual.py'),
    'original-configuration': reference(original / 'execution.yaml'),
    'original-failure': reference(original / 'recorder.txt'), 'input': reference(source),
    'events': events,
    'limitation': 'The wrapper replaced the historical detailed event summary. This audit uses only the existing individual outputs and command records; it does not reconstruct lost event timestamps or pressure snapshots, and reruns no probe.'}
output.write_text(json.dumps(record, indent=2) + '\n')
print('PASS: three retained diagnostic-only visual observations; no historical byte rewritten or probe rerun')
