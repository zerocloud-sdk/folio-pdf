"""Read-only independent Spec audit of retained T31-r1 receipts and pixels."""
from pathlib import Path
import datetime
import hashlib
import json
import re
import struct
import subprocess
import zipfile
from PIL import Image, ImageChops

ROOT = Path('/home/ubuntu/IdeaProjects/open-pdf')
RECORDING = ROOT / 'capabilities/evidence/T31-r1'
ARTIFACTS = RECORDING / 'artifacts'
RECEIPTS = ROOT / 'capabilities/evidence/artifacts'
STEM = 'T31-two-dimensional-barcodes'
errors = []

def check(condition, detail):
    if not condition:
        errors.append(detail)

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def read_json(path):
    return json.loads(path.read_text())

def verify_hashes(directory, files, label):
    for name, expected in files.items():
        path = directory / name
        check(path.is_file() and digest(path) == expected, label + ': ' + name)

declared = read_json(RECEIPTS / 'T31-r1-artifact-audit.json')
verify_hashes(RECORDING, declared['files'], 'artifact hash')
actual_files = {str(p.relative_to(RECORDING)) for p in RECORDING.rglob('*') if p.is_file()}
check(actual_files == set(declared['files']), 'artifact set differs')
check(len(actual_files) == 1439, 'artifact count')
check(sum(p.endswith('.pdf') for p in actual_files) == 23, 'PDF count')
check(sum(p.endswith('.png') for p in actual_files) == 1382, 'PNG count')

source_manifest = ARTIFACTS / (STEM + '-sources.sha256')
sources = {}
for line in source_manifest.read_text().splitlines():
    expected, name = line.split('  ', 1)
    check(name not in sources, 'duplicate source ' + name)
    sources[name] = expected
check(len(sources) == 450, 'source count')
check(digest(source_manifest) == declared['sourceManifestSha256'], 'source manifest hash')
verify_hashes(ROOT, sources, 'source declaration')
inputs = read_json(RECEIPTS / 'T31-build-inputs-r2.json')['files']
check(len(inputs) == 841, 'build input count')
verify_hashes(ROOT, inputs, 'root build input')
verify_hashes(Path('/home/ubuntu/IdeaProjects/open-pdf-t31-validation'), inputs, 'matrix build input')
prior = read_json(RECEIPTS / 'T31-prior-evidence-before.json')['files']
verify_hashes(ROOT, prior, 'prior evidence')
check(len(prior) == 2336, 'prior evidence count')

tool_receipt = read_json(RECEIPTS / 'T31-actual-tools.json')
root_receipt = read_json(RECEIPTS / 'T31-root-verification.json')
for receipt in (tool_receipt, root_receipt):
    check(digest(RECEIPTS / receipt['rawLog']) == receipt['rawLogSha256'], 'raw log hash')
    check('[INFO] BUILD SUCCESS' in (RECEIPTS / receipt['rawLog']).read_text(), 'raw log success')
check(root_receipt['command'] == './mvnw -B -ntp verify', 'root command')
check('-DskipTests' in tool_receipt['command'] and 'acceptance-t31-record' in tool_receipt['command'], 'dedicated command')
root_log = (RECEIPTS / root_receipt['rawLog']).read_text()
test_rows = []
for match in re.finditer(r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+), Time elapsed: (.+?) -- in (\S+)', root_log):
    test_rows.append(dict(zip(('tests', 'failures', 'errors', 'skipped'), map(int, match.groups()[:4])), name=match[6]))
totals = {key: sum(row[key] for row in test_rows) for key in ('tests', 'failures', 'errors', 'skipped')}
check(len(test_rows) == 67, 'root test class count')
check(totals == {'tests': 1161, 'failures': 0, 'errors': 0, 'skipped': 4}, 'root totals')
skip_classes = {row['name']: row['skipped'] for row in test_rows if row['skipped']}
check(skip_classes == {'net.zerocloud.pdf.consumer.HardenedWorkerScaleProfileTest': 3,
                       'net.zerocloud.pdf.acceptance.T30BarcodeEvidenceCommandTest': 1}, 'root skip classes')
t31_rows = [row for row in test_rows if 'T31' in row['name'] or 'Barcode2DWorkflowTest' in row['name']]
check(len(t31_rows) == 7 and sum(row['tests'] for row in t31_rows) == 76, 'T31 test count')
check(all(row['skipped'] == 0 for row in t31_rows), 'T31 skip')
for name in ('pdf-document/src/test/java/net/zerocloud/pdf/consumer/HardenedWorkerScaleProfileTest.java',
             'pdf-acceptance/src/test/java/net/zerocloud/pdf/acceptance/T30BarcodeEvidenceCommandTest.java'):
    baseline = subprocess.check_output(['git', 'show', '060cee07a230ab1c8194efce265615934d619ff2:' + name], cwd=ROOT)
    check(baseline == (ROOT / name).read_bytes(), 'pre-existing skip source ' + name)

shipped = declared['shippedDocumentJar']
check(digest(ROOT / shipped['path']) == shipped['sha256'], 'shipped JAR identity')
with zipfile.ZipFile(ROOT / shipped['path']) as archive:
    classes = [name for name in archive.namelist() if name.endswith('.class')]
    check(len(classes) == 722, 'shipped class count')
    check({int.from_bytes(archive.read(name)[6:8], 'big') for name in classes} == {52}, 'shipped Java 8 classes')
    check(all(name.startswith('net/zerocloud/') for name in classes), 'shipped foreign classes')

chain_reports = {chain: (RECORDING / (STEM + '-' + chain + '.md')).read_text()
                 for chain in ('syntax', 'semantic', 'visual', 'standards')}
for chain, report in chain_reports.items():
    check('Result: `' + ('indeterminate' if chain == 'standards' else 'pass') + '`' in report, 'chain result ' + chain)
    if chain != 'standards':
        check('Source declaration SHA-256: `' + digest(source_manifest) + '`' in report, 'chain source hash ' + chain)

environment = read_json(RECEIPTS / 'T31-verification-environment.json')
for binary in environment['actualBinaryChecks']:
    check(digest(ROOT / binary['binary']) == binary['expected'], 'actual binary identity')
    check(digest(ROOT / binary['pin']) == binary['pinSha256'], 'tool pin identity')

expected_raw = {
    90: ('dm-raw-edifact-auto', 'A', [240, 5, 240], (12, 12)),
    91: ('dm-raw-edifact-fixed', 'A', [240, 5, 240], (12, 12)),
    92: ('dm-raw-edifact-ascii', 'A', [240, 124, 66], (12, 12)),
    93: ('dm-raw-edifact-tail', 'ABABCDA', [66, 67, 240, 4, 32, 196, 5, 240], (32, 8)),
    119: ('pdf-raw-eci-leading', 'A', [901, 927, 26, 65], (120, 8)),
    120: ('pdf-raw-eci-literal', 'AB', [901, 65, 927, 26, 66], (120, 8)),
    121: ('pdf-raw-eci-charset', '\\u00e9\\u00e9', [901, 927, 26, 195, 169, 927, 3, 233], (120, 8)),
    122: ('pdf-raw-eci-924-leading', '\\u0000' * 5 + 'A', [924, 927, 26, 0, 0, 0, 0, 65], (120, 8)),
    123: ('pdf-raw-eci-924-between', '\\u0000' * 5 + 'A' + '\\u0000' * 5 + 'B', [924, 0, 0, 0, 0, 65, 927, 26, 0, 0, 0, 0, 66], (120, 8)),
    124: ('pdf-raw-eci-text-latch', 'ABAB', [901, 65, 927, 26, 66, 900, 1], (120, 8)),
    125: ('pdf-raw-eci-consecutive', 'A', [901, 927, 26, 927, 3, 65], (120, 8)),
    126: ('pdf-raw-eci-group', '\\u0000' * 4 + '\\u0001,A', [901, 927, 26, 0, 0, 0, 0, 300, 65], (120, 8)),
}
coverage = {int(m[1]): m[2] for m in re.finditer(r'^\| (\d+) \| `([^`]+)`', (ROOT / 'capabilities/evidence/T31-coverage.md').read_text(), re.M)}
check(len(coverage) == 224, 'coverage count')
profiles = {}
raw_observations = []
for profile in ('in-process', 'hardened-worker'):
    pdf = ARTIFACTS / f'{STEM}-{profile}.pdf'
    reference_pdf = ARTIFACTS / f'{STEM}-{profile}-reference.pdf'
    for chain in ('syntax', 'semantic', 'visual'):
        check('Actual PDF SHA-256: `' + digest(pdf) + '`' in chain_reports[chain], 'chain PDF hash ' + profile)
    check('Qualified geometry reference PDF SHA-256: `' + digest(reference_pdf) + '`' in chain_reports['visual'], 'reference PDF hash ' + profile)
    neutral, replacements = re.subn(rb'(/ID\s*\[\s*<)([a-fA-F0-9]+)(>\s*<)([a-fA-F0-9]+)(>\s*\])',
        lambda m: m[1] + b'0' * len(m[2]) + m[3] + b'0' * len(m[4]) + m[5], pdf.read_bytes())
    check(replacements == 1, 'trailer ID count ' + profile)
    neutral_hash = hashlib.sha256(neutral).hexdigest()
    check('Input ID-neutral SHA-256: `' + neutral_hash + '`' in chain_reports['semantic'], 'ID-neutral hash ' + profile)
    semantic = (ARTIFACTS / (STEM + '-' + profile + '-semantic.txt')).read_text()
    visual = (ARTIFACTS / (STEM + '-' + profile + '-visual.txt')).read_text()
    semantics = {int(m[1]): (m[2], m[0]) for m in re.finditer(r'^Page (\d+) (\S+): .+$', semantic, re.M)}
    visuals = {int(m[1]): (m[2], m[0]) for m in re.finditer(r'^Page (\d+) (\S+)\n.*?(?=^Page |\Z)', visual, re.M | re.S)}
    check(set(semantics) == set(range(1, 225)) == set(visuals), profile + ' pages')
    for page in range(1, 225):
        sid, line = semantics[page]
        vid, block = visuals[page]
        check(sid == vid == coverage[page], profile + ' fixture ID ' + str(page))
        check('corrected=0;' in line and 'vector geometry/color/reuse=pass' in line, profile + ' semantic ' + str(page))
        check('corrected=0;' in block and 'declared inverse placement=pass; pass' in block, profile + ' raster ' + str(page))
        check('stderr:\n0 (0)\n' in block and 'Visual comparison: pass' in block, profile + ' visual ' + str(page))
        pngs = {}
        for kind in ('actual', 'reference', 'difference'):
            path = ARTIFACTS / f'{STEM}-{profile}-{kind}-{page}.png'
            pngs[kind] = digest(path)
            claim = re.search(kind.capitalize() + r' PNG SHA-256: ([a-f0-9]{64})', block)
            check(claim and claim[1] == pngs[kind], profile + ' reported PNG hash ' + str(page))
            if kind != 'difference':
                header = path.read_bytes()[:29]
                check(header[:8] == b'\x89PNG\r\n\x1a\n' and struct.unpack('>IIBB', header[16:26]) == (2448, 3168, 8, 2), profile + ' PNG format ' + str(page))
        check(pngs['actual'] == pngs['reference'], profile + ' actual/reference differs ' + str(page))
        if page in expected_raw:
            name, payload, prefix, (width, height) = expected_raw[page]
            check(name == sid, 'RAW page ID ' + str(page))
            for text in (line, block):
                check('payload=' + payload + ';' in text, 'RAW payload ' + str(page))
                observed = json.loads(re.search(r'controls=(\[[^\]]+\])', text)[1])
                check(observed[:min(12, len(prefix))] == prefix[:12], 'RAW displayed prefix ' + str(page))
            quiet = 1 if page < 100 else 2
            row_height = 1 if page < 100 else 3
            bbox = ((36 + quiet) * 4, (792 - 144 - quiet - height * row_height) * 4,
                    (36 + quiet + width) * 4, (792 - 144 - quiet) * 4)
            with Image.open(ARTIFACTS / f'{STEM}-{profile}-actual-{page}.png') as image:
                observed_bbox = ImageChops.difference(image, Image.new('RGB', image.size, 'white')).getbbox()
                colors = image.getcolors(3)
            check(observed_bbox == bbox, 'RAW pixel bounds ' + str(page))
            check(colors is not None and {c for n, c in colors} == {(0, 0, 0), (255, 255, 255)}, 'RAW colors ' + str(page))
            raw_observations.append({'profile': profile, 'page': page, 'fixture': name, 'payload': payload,
                                     'rawPrefix': prefix, 'pixelBounds': observed_bbox, 'pixelColors': colors,
                                     'semantic': line, 'visual': block})
    qpdf_log = (ARTIFACTS / (STEM + '-' + profile + '-qpdf.txt')).read_text()
    check('exit code: `0`' in qpdf_log and 'No syntax or stream encoding errors found' in qpdf_log, 'qpdf ' + profile)
    profiles[profile] = {'semanticPages': len(semantics), 'rasterPages': len(visuals), 'matchingPagePairs': 224}

negatives = (ARTIFACTS / (STEM + '-negative-controls.txt')).read_text()
negative_visual = (ARTIFACTS / (STEM + '-negative-visual.txt')).read_text()
negative_rows = list(re.finditer(r'^(\S+): expected rejection=(.+?); result=pass; PDF SHA-256=([a-f0-9]{64})$', negatives, re.M))
check(len(negative_rows) == 19, 'negative PDF count')
check(negative_visual.count('Raster rejection:') == 19 and negative_visual.count('negative control pass') == 19, 'negative raster count')
for match in negative_rows:
    name, reason, expected = match.groups()
    check(digest(ARTIFACTS / ('T31-negative-' + name + '.pdf')) == expected, 'negative PDF hash ' + name)
    check(reason in (ARTIFACTS / ('T31-negative-' + name + '-semantic.txt')).read_text(), 'negative reason ' + name)
check(len(re.findall(r'Exit: 1\nstdout:\n\nstderr:\n[1-9]\d*(?:\s|$)', negative_visual)) == 19, 'negative positive AE')

report = {'auditedUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'sourceDeclarations': len(sources),
          'artifactFiles': len(actual_files), 'buildInputFiles': len(inputs), 'priorEvidenceFiles': len(prior),
          'profiles': profiles, 'rawRegressionObservations': raw_observations,
          'rootTestClasses': len(test_rows), 'rootTestTotals': totals, 'rootT31TestRows': t31_rows,
          'skipClasses': skip_classes, 'issues': errors,
          'scope': 'Retained artifacts and root receipt review only. Final matrix, inventory and scope gates remain open.',
          'mavenOrProductProbesRun': False}
output = ROOT / '.build-cache/t31/review-spec/artifacts-r1-audit.json'
output.write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps({k: v for k, v in report.items() if k != 'rawRegressionObservations'}, indent=2))
raise SystemExit(1 if errors else 0)
