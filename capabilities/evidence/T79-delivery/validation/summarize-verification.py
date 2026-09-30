"""Summarize completed verification logs, retaining explicit opt-in omissions."""
import hashlib
import json
from pathlib import Path
import re

ROOT = Path('/workspace/folio-pdf')
OUT = ROOT / 'capabilities/evidence/T79-delivery/validation'
ledger = [json.loads(line) for line in (OUT / 'commands.jsonl').read_text().splitlines()]
suite_pattern = re.compile(r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), '
    r'Skipped: (\d+), Time elapsed: .*? -- in ([\w.]+)')


def summarize(text):
    rows = [{'class': row[4], 'tests': int(row[0]), 'failures': int(row[1]),
             'errors': int(row[2]), 'skipped': int(row[3])}
            for row in suite_pattern.findall(text)]
    assert rows and not any(row['failures'] or row['errors'] for row in rows)
    required = ('ClearMetadataPassword', 'PdfVersionPasswordSecurityWorkflowTest',
                'PasswordSecurityFacadeTest', 'T78PasswordProductsTest', 'T79PasswordProductsTest')
    assert not any(row['skipped'] and any(name in row['class'] for name in required) for row in rows)
    return {'tests': sum(row['tests'] for row in rows), 'failures': 0, 'errors': 0,
            'skipped': sum(row['skipped'] for row in rows),
            'skipped-suites': [row for row in rows if row['skipped']], 'suites': rows}


records = []
for name, builds in [('full-verify-resumed-r3', 1), ('native-baseline-resumed', 1),
                     ('worker-facade-resumed', 1), ('jdk-matrix', 4), ('independent-profile-final', 1)]:
    rows = [row for row in ledger if row['name'] == name]
    assert len(rows) == 1 and rows[0]['exit-code'] == 0
    log = ROOT / rows[0]['log']
    assert hashlib.sha256(log.read_bytes()).hexdigest() == rows[0]['log-sha256']
    text = log.read_text()
    assert text.count('BUILD SUCCESS') == builds
    record = {'name': name, 'log': rows[0]['log'], 'log-sha256': rows[0]['log-sha256'],
              'successful-builds': builds, **summarize(text)}
    if name == 'jdk-matrix':
        segments = re.split(r'^==> Verifying Folio PDF on JDK ', text, flags=re.MULTILINE)[1:]
        assert len(segments) == 4
        record['jdks'] = [{'jdk-major': int(segment.split(' ', 1)[0]), **summarize(segment)}
                          for segment in segments]
        assert [row['jdk-major'] for row in record['jdks']] == [8, 11, 17, 21]
    records.append(record)

result = {'result': 'pass', 'records': records,
    'opt-in-scale-tests': {'property': 'folio.pdf.t22.scale',
        'class': 'net.zerocloud.pdf.consumer.HardenedWorkerScaleProfileTest',
        'cases': ['configuredConcurrencyLimitAdmitsLimitAndRejectsFirstExcess',
                  'generatedFiveThousandPageWorkloadCompletesAndReopens',
                  'generatedExactOneGiBInputCompletesWithinStagingProfile'],
        'disposition': 'Unrelated opt-in scale profiles; not enabled by ordinary verify or the JDK script.'},
    'opt-in-barcode-raster-test': {'property': 't30.raster',
        'class': 'net.zerocloud.pdf.acceptance.T30BarcodeEvidenceCommandTest',
        'case': 'pinnedRastersDecodeAndMatchEveryDeclaredVariant',
        'disposition': 'Unrelated opt-in barcode raster profile; ordinary verification does not select it.'},
    'independent-tools': 'The ordinary Maven group exclusion is covered for T78/T79 by the passing real-tool profile and mandatory final certification routes.'}
(OUT / 'verification-summary.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps([{key: row[key] for key in ('name', 'tests', 'skipped', 'successful-builds')} for row in records], indent=2))
