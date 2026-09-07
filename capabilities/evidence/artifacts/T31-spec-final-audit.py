"""Independent final Spec receipt/scope audit; no builds or product probes."""
from pathlib import Path
import datetime
import hashlib
import json
import re
import subprocess

ROOT = Path('/home/ubuntu/IdeaProjects/open-pdf')
RECEIPTS = ROOT / 'capabilities/evidence/artifacts'
BASELINE = '060cee07a230ab1c8194efce265615934d619ff2'
errors = []

def check(condition, label):
    if not condition:
        errors.append(label)

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def load(name):
    return json.loads((RECEIPTS / name).read_text())

def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)

def mismatches(base, mapping):
    return [name for name, expected in mapping.items()
            if not (base / name).is_file() or sha(base / name) != expected]

scope = load('T31-review-scope-final.json')
workspace = load('T31-final-workspace-audit.json')
check(git('rev-parse', 'HEAD').decode().strip() == BASELINE, 'HEAD')
check(git('branch', '--show-current').decode().strip() == 'main', 'branch')
check(git('log', BASELINE + '..HEAD', '--oneline') == b'', 'commits')
check(git('diff', BASELINE + '...HEAD') == b'', 'three-dot diff')
check(git('diff', '--cached', '--binary', BASELINE, '--') == b'', 'staged diff')
tracked = git('diff', '--name-only', BASELINE, '--').decode().splitlines()
untracked = git('ls-files', '--others', '--exclude-standard').decode().splitlines()
untracked.remove('capabilities/evidence/artifacts/T31-review-scope-final.json')
check(tracked == scope['trackedNames'] and len(tracked) == 22, 'tracked scope')
check(untracked == scope['untrackedNames'] and len(untracked) == 1577, 'untracked scope')
check(len(scope['files']) == 1599, 'scope count')
check(not mismatches(ROOT, scope['files']), 'scope file hashes')
check(not mismatches(ROOT, workspace['files']), 'workspace receipt hashes')
for kind, row in scope['patches'].items():
    check(sha(ROOT / row['path']) == row['sha256'], kind + ' patch hash')
check(git('diff', '--binary', BASELINE, '--') == (ROOT / scope['patches']['unstaged']['path']).read_bytes(), 'fixed-point full working-tree patch')
check(git('diff', '--binary', '--') == (ROOT / scope['patches']['unstaged']['path']).read_bytes(), 'unstaged patch')

frozen = json.loads((ROOT / '.build-cache/t31/review-r2-scope.json').read_text())['files']
changed = [name for name, expected in frozen.items() if scope['files'].get(name) != expected]
check(set(changed) == {'capabilities/capability-matrix.yaml', 'docs/generated/capability-matrix.md',
                       'capabilities/evidence/T31-two-dimensional-barcodes.md'}, 'post-source-review changed files')
added = [name for name in scope['files'] if name not in frozen]
check(all(name.startswith('capabilities/evidence/') for name in added), 'post-source-review additions')
build_inputs = load('T31-build-inputs-r2.json')['files']
root_changes = mismatches(ROOT, build_inputs)
check(set(root_changes) == {'capabilities/capability-matrix.yaml', 'docs/generated/capability-matrix.md'}, 'post-build input changes')
check(not mismatches(Path('/home/ubuntu/IdeaProjects/open-pdf-t31-validation'), build_inputs), 'matrix frozen build inputs')
check(len(build_inputs) == 841, 'frozen input count')
prior = load('T31-prior-evidence-before.json')['files']
check(len(prior) == 2336 and not mismatches(ROOT, prior), 'prior evidence preservation')
artifacts = load('T31-r1-artifact-audit.json')['files']
check(len(artifacts) == 1439 and not mismatches(ROOT / 'capabilities/evidence/T31-r1', artifacts), 'actual tool artifacts preservation')
source_manifest = ROOT / 'capabilities/evidence/T31-r1/artifacts/T31-two-dimensional-barcodes-sources.sha256'
sources = {name: expected for expected, name in (line.split('  ', 1) for line in source_manifest.read_text().splitlines())}
check(len(sources) == 450 and not mismatches(ROOT, sources), 'recording source linkage')
for row in load('T31-artifact-review-retention.json')['files']:
    check(sha(ROOT / row['original']) == row['sha256'] == sha(ROOT / row['retained']), 'review retention ' + row['retained'])

matrix = load('T31-jdk-matrix-verification.json')
matrix_log = (RECEIPTS / matrix['rawLog']).read_text()
check(sha(RECEIPTS / matrix['rawLog']) == matrix['rawLogSha256'], 'matrix log hash')
check(matrix['command'] == './scripts/verify-jdk-matrix.sh' and matrix['scriptExitCode'] == 0, 'full matrix command/exit receipt')
sections = list(re.finditer(r'^==> Verifying Folio PDF on JDK (\d+) .+?\n.*?(?=^==> Verifying|\Z)', matrix_log, re.M | re.S))
check([m[1] for m in sections] == ['8', '11', '17', '21'], 'matrix JDK sequence')
observed_matrix = []
test_pattern = r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+), Time elapsed: (.+?) -- in (\S+)'
expected_skips = {'net.zerocloud.pdf.consumer.HardenedWorkerScaleProfileTest': 3,
                  'net.zerocloud.pdf.acceptance.T30BarcodeEvidenceCommandTest': 1}
for section, declared in zip(sections, matrix['observations']):
    text = section[0]
    rows = [dict(zip(('tests', 'failures', 'errors', 'skipped'), map(int, match.groups()[:4])), name=match[6])
            for match in re.finditer(test_pattern, text)]
    totals = {key: sum(row[key] for row in rows) for key in ('tests', 'failures', 'errors', 'skipped')}
    skips = {row['name']: row['skipped'] for row in rows if row['skipped']}
    t31 = [row for row in rows if 'T31' in row['name'] or 'Barcode2DWorkflowTest' in row['name']]
    check(len(rows) == 67 and totals == {'tests': 1161, 'failures': 0, 'errors': 0, 'skipped': 4}, 'matrix test totals ' + section[1])
    check(skips == expected_skips, 'matrix skips ' + section[1])
    check(len(t31) == 7 and sum(row['tests'] for row in t31) == 76 and all(row['skipped'] == 0 for row in t31), 'matrix T31 tests ' + section[1])
    check(text.count('[INFO] BUILD SUCCESS') == 1 and '[INFO] BUILD FAILURE' not in text, 'matrix success ' + section[1])
    check(declared['jdk'] == section[1] and all(declared[key] == value for key, value in totals.items()), 'matrix declared observations ' + section[1])
    observed_matrix.append({'jdk': section[1], 'testClasses': len(rows), **totals, 't31Tests': sum(row['tests'] for row in t31)})
root_receipt = load('T31-root-verification.json')
check(sha(RECEIPTS / root_receipt['rawLog']) == root_receipt['rawLogSha256'], 'root reviewed raw log unchanged')

inventory = load('T31-final-inventory-validation.json')
check(not mismatches(ROOT, inventory['finalInventoryFiles']), 'final inventory file hashes')
check([row['command'] for row in inventory['observations']] == ['./scripts/inventory validate', './scripts/inventory generate', './scripts/inventory check'], 'inventory command sequence')
for row in inventory['observations']:
    text = (RECEIPTS / row['rawLog']).read_text()
    check(sha(RECEIPTS / row['rawLog']) == row['rawLogSha256'] and row['exitCode'] == 0, 'inventory log/exit')
    check('[INFO] BUILD SUCCESS' in text and 'Inventory validation passed: 23 capabilities, 12 facade surfaces, 22 exclusions.' in text, 'inventory raw result')
check('Generated inventory documentation is current.' in (RECEIPTS / 'T31-final-inventory-check.txt').read_text(), 'generated view current')

ledger_path = ROOT / 'capabilities/evidence/T31-two-dimensional-barcodes.md'
ledger = ledger_path.read_text()
criterion_rows = re.findall(r'^\| (\d+) \| \[ \].*$', ledger, re.M)
check(criterion_rows == [str(n) for n in range(1, 35)] and not re.search(r'\[[xX]\]', ledger), '34 unchecked criterion rows')
local_links = []
for target in re.findall(r'\]\(([^)]+)\)', ledger):
    if target.startswith(('https://', 'http://')):
        continue
    name, _, fragment = target.partition('#')
    path = (ledger_path.parent / name).resolve()
    check(path.is_file(), 'ledger link ' + target)
    if fragment and path.is_file():
        text = path.read_text()
        headings = re.findall(r'^#+\s+(.+?)\s*#*$', text, re.M)
        slugs = [re.sub(r'[^\w\- ]', '', heading.replace('`', '').lower()).replace(' ', '-') for heading in headings]
        check(fragment in slugs or 'id="' + fragment + '"' in text, 'ledger fragment ' + target)
    local_links.append(target)

report = {'auditedUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
          'reviewedFileCount': len(scope['files']), 'trackedChangedCount': len(tracked), 'untrackedCountExcludingScopeReceipt': len(untracked),
          'changedSinceSourceReview': changed, 'newSinceSourceReviewCount': len(added), 'postBuildInputChanges': root_changes,
          'matrixObservationsRecalculated': observed_matrix, 'unchangedPriorEvidenceCount': len(prior),
          'unchangedActualToolArtifactCount': len(artifacts), 'matchingRecordingSourceCount': len(sources),
          'uncheckedCriterionCount': len(criterion_rows), 'ledgerLocalLinksChecked': len(local_links),
          'issues': errors, 'mavenOrProductProbesRun': False,
          'scopeBoundary': scope['permittedClosureChanges']}
(ROOT / '.build-cache/t31/review-spec/final-audit.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report, indent=2))
raise SystemExit(1 if errors else 0)
