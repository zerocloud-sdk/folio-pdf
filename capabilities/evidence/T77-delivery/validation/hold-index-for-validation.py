import fcntl, json, time
from pathlib import Path
root = Path('/workspace/folio-pdf')
base = root / '.build-cache/T77-preflight'
with (root / '.build-cache/foundation-evidence.lock').open('a+b') as held:
    fcntl.flock(held, fcntl.LOCK_EX)
    print('Holding index publication while required full builds check retained documentation', flush=True)
    while True:
        paths = [base / 'matrix-results-r3.json', base / 'matrix-results-r4.json']
        independent = (base / 'independent-tests-r4.log').read_text(errors='replace')
        if all(path.exists() for path in paths):
            results = json.loads(paths[0].read_text())
            selected = [row for row in results if row['jdk'] in (17, 21)] + json.loads(paths[1].read_text())
            if any(row['exit'] for row in selected):
                raise SystemExit('Final matrix run failed; publication hold released for diagnosis')
            if '[INFO] BUILD SUCCESS' in independent:
                print('Final JDK 8/11/17/21 and independent gates passed; releasing publication hold', flush=True)
                break
        if '[INFO] BUILD FAILURE' in independent:
            raise SystemExit('Independent gate failed; publication hold released for diagnosis')
        time.sleep(15)
