"""Retain executed ordinary reactor counts separately from mandatory certification."""
import hashlib
import json
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
name = sys.argv[1]
gate = json.loads((delivery / 'validation' / (name + '-result.json')).read_text())
assert gate['exit-code'] == 0
path = root / gate['log']['path']
assert hashlib.sha256(path.read_bytes()).hexdigest() == gate['log']['sha256']
text = re.sub(r'\x1b\[[0-9;]*m', '', path.read_text())
pattern = r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+),[^\n]* -- in (\S+)'
rows = [{'tests': int(tests), 'failures': int(failures), 'errors': int(errors),
         'skipped': int(skipped), 'class': selected}
        for tests, failures, errors, skipped, selected in re.findall(pattern, text)]
assert rows and 'BUILD SUCCESS' in text
totals = {key: sum(row[key] for row in rows) for key in ('tests', 'failures', 'errors', 'skipped')}
assert totals['failures'] == totals['errors'] == 0
skips = [row for row in rows if row['skipped']]
assert [(row['class'], row['skipped']) for row in skips] == [
    ('net.zerocloud.pdf.consumer.HardenedWorkerScaleProfileTest', 3),
    ('net.zerocloud.pdf.acceptance.T30BarcodeEvidenceCommandTest', 1)]
result = {**totals, 'recorded-class-executions': len(rows), 'ordinary-skips': skips,
          'log': gate['log'],
          'scope': 'Full ordinary reactor verification; repeated integration class executions are counted as executed. Opt-in CI skips are not recovery certification.'}
destination = delivery / 'validation' / (name + '-tests.json')
assert not destination.exists()
destination.write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result, indent=2))
