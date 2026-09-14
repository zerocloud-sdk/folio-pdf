from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import time

root = Path('/home/ubuntu/IdeaProjects/open-pdf')
profile = root / 'capabilities/profiles/T13-standards'
output = root / 'capabilities/evidence/T75-delivery/standards-r5/program-cmaps-r1'
output.mkdir(parents=True)
script = root / 'scripts/t13-program-standards.py'
observer = hashlib.sha256(script.read_bytes()).hexdigest()
shutil.copy2(script, output / 'observer.py')
cases = []
for name in ('nested-split-type3', 'marked-structure', 'embedded-font-kinds', 'embedded-font-inheritance', 'uncertain-geometry'):
    cases.append(('source-' + name, root / ('capabilities/profiles/T13-text/fixtures/' + name + '.pdf'), 'pass', None))
for name, entry in json.loads((profile / 'program-controls.json').read_text()).items():
    pdf = profile / entry['path']
    assert hashlib.sha256(pdf.read_bytes()).hexdigest() == entry['sha256']
    cases.append((name, pdf, 'fail', entry['rule']))
for name in ('usecmap', 'usefont', 'cidrange', 'bfrange-string', 'bfrange-array'):
    cases.append(('positive-' + name, profile / ('fixtures/program-positive-' + name + '.pdf'), 'pass', None))
for name, expected in (('direct-font', 'pass'), ('direct-font-bad', 'indeterminate'),
                       ('codespaces-256', 'pass'), ('codespaces-257', 'indeterminate'),
                       ('mappings-4096', 'pass'), ('mappings-4352', 'indeterminate'),
                       ('inherited-sources-4096', 'pass'), ('inherited-sources-4097', 'indeterminate')):
    cases.append(('boundary-' + name, profile / ('fixtures/program-boundary-' + name + '.pdf'), expected, None))
review_cases = {'indirect-subtype-negative-cid': ('fail', 'cmap-cid-domain'),
               'hash-name-mismatch': ('fail', 'cmap-dictionary-program'), 'hash-name-legal': ('pass', None),
               'xuid-string': ('fail', 'cmap-declaration-type'), 'xuid-executable': ('indeterminate', None),
               'unicode-usefont-zero': ('pass', None), 'zero-block-before-codespace': ('fail', 'cmap-codespace'),
               'ff-comment-hidden-bfchar': ('fail', 'cmap-operator-family'),
               'codespace-after-mapping': ('fail', 'cmap-codespace'),
               'mixed-conflicting-prefixes': ('fail', 'cmap-font-domain')}
for name, identity in json.loads((profile / 'review-fixtures/sha256.json').read_text()).items():
    pdf = profile / ('review-fixtures/' + name + '.pdf')
    assert hashlib.sha256(pdf.read_bytes()).hexdigest() == identity
    expected, rule = review_cases[name]
    cases.append(('review-' + name, pdf, expected, rule))

def observe(case):
    name, pdf, expected, rule = case
    start = time.monotonic()
    result = subprocess.run([sys.executable, str(script), str(root), str(pdf), 'cmaps', str(output / name)],
                            capture_output=True, timeout=30)
    (output / (name + '.stdout')).write_bytes(result.stdout)
    (output / (name + '.stderr')).write_bytes(result.stderr)
    assert result.returncode == 0, (name, result.stderr)
    record = output / name / 'result.properties'
    values = dict(line.split('=', 1) for line in record.read_text().splitlines())
    assert values['standards'] == expected, (name, values)
    assert rule is None or values.get('rule') == rule, (name, values)
    assert values['input-sha256'] == hashlib.sha256(pdf.read_bytes()).hexdigest()
    assert values['observer-sha256'] == observer
    assert values['qpdf-json-sha256'] == hashlib.sha256((output / name / 'qpdf.json').read_bytes()).hexdigest()
    return {'case': name, 'input': str(pdf.relative_to(root)), 'input-sha256': values['input-sha256'],
            'expected': expected, 'actual': values['standards'], 'rule': values.get('rule'),
            'record': name + '/result.properties', 'record-sha256': hashlib.sha256(record.read_bytes()).hexdigest(),
            'elapsed-seconds': round(time.monotonic() - start, 3)}

with ThreadPoolExecutor(max_workers=4) as pool:
    records = list(pool.map(observe, cases))
assert hashlib.sha256(script.read_bytes()).hexdigest() == observer
(output / 'qualification.json').write_text(json.dumps({'kind': 'development qualification; not final candidate certification',
    'observer-sha256': observer, 'cases': records}, indent=2, sort_keys=True) + '\n')
print('Recorded', len(records), 'actual pinned qpdf program observations; all expected outcomes and identities agree')
