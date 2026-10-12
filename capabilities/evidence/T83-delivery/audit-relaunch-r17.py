"""Check actual current-candidate delivery facts; never relabel old observations."""
import datetime
import gzip
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import zipfile

import yaml

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
baseline = '42fe5c797711070b319b9419e14fcb7adfa98735'
sys.path.insert(0, str(root / 'scripts'))
spec = importlib.util.spec_from_file_location('t83_relaunch_audit', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
from t22_foundation_reports import require_exit_grace_observation

selected = ('transactions', 'values', 'pages', 'metadata', 'annotations', 'text', 'images',
            'incremental', 'password-baseline', 'password-clear-metadata', 'password-attachments',
            'limits', 'worker', 'recovery')
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
frozen_path = delivery / 'source-freeze-final-r9.json'
frozen = json.loads(frozen_path.read_text())
receipt = driver.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
assert driver.capture_build_inputs(root, contract) == {key: frozen[key] for key in ('inputs', 'contract-inputs')}
assert receipt == json.loads((delivery / 'candidate-final-r9/build-inputs.json').read_text())
assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip() == baseline
assert not subprocess.check_output(['git', 'diff', '--name-only', '--cached'], cwd=root, text=True).strip()

seen = set()
def bind(reference):
    key = (reference['path'], reference['sha256'])
    if key not in seen:
        path = root / reference['path']
        assert path.is_file() and driver.reference(root, path) == reference, key
        seen.add(key)
    return root / reference['path']


def bind_tree(value):
    if isinstance(value, dict):
        if set(value) == {'path', 'sha256'}:
            bind(value)
        else:
            for child in value.values():
                bind_tree(child)
    elif isinstance(value, list):
        for child in value:
            bind_tree(child)


superseded_references = frozen['superseded-candidate6'] + frozen['superseded-candidate7'] + frozen['superseded-candidate8']
for item in superseded_references:
    bind(item)
superseded_archives = []
for manifest_ref in superseded_references:
    if not manifest_ref['path'].endswith('-parts.json'):
        continue
    manifest_path = bind(manifest_ref)
    manifest = json.loads(manifest_path.read_text())
    digest = hashlib.sha256()
    offset = 0
    for part in manifest['parts']:
        path = manifest_path.parent / part['path']
        assert path.is_file() and not path.is_symlink()
        assert part['offset'] == offset and 0 < part['bytes'] <= 48 * 1024 * 1024
        assert path.stat().st_size == part['bytes']
        part_digest = hashlib.sha256()
        with path.open('rb') as stream:
            while chunk := stream.read(1024 * 1024):
                part_digest.update(chunk)
                digest.update(chunk)
        assert part_digest.hexdigest() == part['sha256']
        offset += part['bytes']
    assert offset == manifest['bytes'] and digest.hexdigest() == manifest['sha256']
    superseded_archives.append({'manifest': manifest_ref, 'verified-parts': len(manifest['parts']),
        'bytes': offset, 'sha256': manifest['sha256']})
assert len(superseded_archives) == 6 and sum(item['verified-parts'] for item in superseded_archives) == 15

# Candidate8's source/stage paths now belong to candidate9. Its exact archived
# ZIP bytes witness those historical references instead of relabeling new files.
archive8 = delivery / 'candidate-final-r8'
receipt8 = json.loads((archive8 / 'build-inputs.json').read_text())
historical_archived = {}
expected_archived = {item['path']: item['sha256'] for item in
    receipt8['candidate']['inputs'] + receipt8['candidate']['artifacts']
    + receipt8['contract-inputs'] + receipt8['harness']}
expected_archived.update({
    'target/foundation-0.1.0/' + name: driver.sha256(archive8 / name)
    for name in ('build-inputs.json', 'build-command.json', 'build.txt')})
for name in ('source-inputs.zip', 'artifacts-contracts-harness.zip'):
    manifest = json.loads((archive8 / (name + '-parts.json')).read_text())
    entries = {item['path']: item['sha256'] for item in manifest['entries']}
    assert len(entries) == len(manifest['entries'])
    with tempfile.TemporaryFile(dir=root / '.build-cache') as joined:
        for part in manifest['parts']:
            with (archive8 / part['path']).open('rb') as stream:
                while chunk := stream.read(1024 * 1024):
                    joined.write(chunk)
        joined.seek(0)
        with zipfile.ZipFile(joined) as archive:
            assert archive.namelist() == sorted(entries)
            for path, expected in entries.items():
                assert expected == expected_archived[path]
                with archive.open(path) as stream:
                    assert hashlib.file_digest(stream, 'sha256').hexdigest() == expected
                assert path not in historical_archived or historical_archived[path] == expected
                historical_archived[path] = expected
assert historical_archived == expected_archived

text_closeout = json.loads((delivery / 'refresh-final-r15/text-closeout/result.json').read_text())
assert text_closeout['result'] == 'pass' and text_closeout['originals-preserved']
text_originals = bind(text_closeout['original-file-manifest'])
text_seen = set()
with gzip.open(text_originals, 'rt', encoding='utf-8') as stream:
    for line in stream:
        expected, path = line.rstrip('\n').split('\t')
        assert path not in text_seen
        assert path.startswith('capabilities/evidence/foundation/T83-final-r14/text/')
        bind({'path': path, 'sha256': expected})
        text_seen.add(path)
assert len(text_seen) == text_closeout['original-file-count'] == 157778

historical_seen = set()
def historical8(reference, visiting=None, kind=None):
    key = (reference['path'], reference['sha256'], kind)
    if key in historical_seen:
        return
    path = root / reference['path']
    if reference['path'] in historical_archived:
        assert historical_archived[reference['path']] == reference['sha256']
        historical_seen.add(key)
        return
    bind(reference)
    historical_seen.add(key)
    if kind == 'observation' or path.suffix not in ('.json', '.yaml'):
        return
    visiting = set() if visiting is None else visiting
    assert path not in visiting
    value = yaml.safe_load(path.read_text())
    ancestors = visiting | {path}
    def walk(node):
        if isinstance(node, dict):
            if set(node) == {'path', 'sha256'}:
                historical8(node, ancestors)
            else:
                for name, child in node.items():
                    if node is value and kind == 'certification' and name == 'report':
                        historical8(child, ancestors, 'report')
                    elif node is value and kind == 'report' and name == 'environment-observations':
                        for observation in child:
                            historical8(observation, ancestors, 'observation')
                    else:
                        walk(child)
        elif isinstance(node, list):
            for child in node:
                walk(child)
    walk(value)

old_index_ref = next(item for item in frozen['superseded-candidate8']
                     if item['path'].endswith('/refresh-final-r16-prior-index.yaml'))
old_index = json.loads(bind(old_index_ref).read_text())
assert old_index['candidate'] == receipt8['candidate']
assert len(old_index['certifications']) == 48
assert sum(len(item['records']) for item in old_index['certifications']) == 192
for item in old_index['environments']:
    historical8(item['record'])
for item in old_index['certifications']:
    historical8(item['configuration'])
    for record in item['records']:
        historical8(record, kind='certification')
preservation = json.loads((delivery / 'relaunch-preservation.json').read_text())
for item in preservation['retained']:
    bind(item)
assert not (delivery / 'validation/full-verify-final-r7-result.json').exists()
assert not (root / 'capabilities/evidence/foundation/T83-final-r12').exists()
assert not (root / 'capabilities/evidence/foundation/T83-final-r13').exists()
assert not (delivery / 'refresh-final-r13/results.json').exists()
old_verification = json.loads((delivery / 'candidate-final-r7-verification.json').read_text())
assert [(row['gate'], row['exit-code']) for row in old_verification] == [('full-verify-final-r8', 0), ('jdk-matrix-final-r3', 1)]
assert (delivery / 'validation/post-r13-matrix-failure-container-check.txt').read_text() == ''

# The failed R16 verification and exact-method diagnostics remain diagnostic-only.
diagnostic_spec = importlib.util.spec_from_file_location('t83_barcode_final_archive',
    delivery / 'diagnostics/barcode-elapsed-preservation-r1.py')
diagnostic = importlib.util.module_from_spec(diagnostic_spec)
diagnostic_spec.loader.exec_module(diagnostic)
barcode_diagnostic = diagnostic.validate()
assert barcode_diagnostic == json.loads((diagnostic.base / 'sealed-diagnostic.json').read_text())
assert barcode_diagnostic['certifications-exported'] == 0
failed_r16 = json.loads((delivery / 'candidate-final-r9-verification.json').read_text())
assert [(row['gate'], row['exit-code']) for row in failed_r16] == [('full-verify-final-r10', 1)]
assert not (delivery / 'refresh-final-r16/results.json').exists()
assert not (root / 'capabilities/evidence/foundation/T83-final-r16').exists()

archive_audit = []
archive_base = delivery / 'candidate-final-r9'
archive_inputs = {
    'source-inputs.zip': receipt['candidate']['inputs'],
    'artifacts-contracts-harness.zip': receipt['candidate']['artifacts'] + receipt['contract-inputs'] + receipt['harness']
        + [driver.reference(root, root / 'target/foundation-0.1.0' / name)
           for name in ('build-inputs.json', 'build-command.json', 'build.txt')]}
for name, references in archive_inputs.items():
    manifest_path = archive_base / (name + '-parts.json')
    manifest = json.loads(manifest_path.read_text())
    entries = {item['path']: item['sha256'] for item in references}
    assert manifest['entries'] == [{'path': path, 'sha256': entries[path]} for path in sorted(entries)]
    with tempfile.TemporaryFile(dir=root / '.build-cache') as joined:
        digest = hashlib.sha256()
        offset = 0
        for part in manifest['parts']:
            path = archive_base / part['path']
            assert part['offset'] == offset and 0 < part['bytes'] <= 48 * 1024 * 1024
            assert path.stat().st_size == part['bytes'] and driver.sha256(path) == part['sha256']
            with path.open('rb') as stream:
                while chunk := stream.read(1024 * 1024):
                    joined.write(chunk)
                    digest.update(chunk)
            offset += part['bytes']
        assert offset == manifest['bytes'] and digest.hexdigest() == manifest['sha256']
        joined.seek(0)
        with zipfile.ZipFile(joined) as archive:
            assert archive.namelist() == sorted(entries)
            for path, expected in entries.items():
                with archive.open(path) as stream:
                    assert hashlib.file_digest(stream, 'sha256').hexdigest() == expected, path
    archive_audit.append({'manifest': driver.reference(root, manifest_path), 'entries': len(entries),
        'parts': len(manifest['parts']), 'bytes': offset, 'sha256': manifest['sha256']})

readiness = (delivery / 'validation/inventory-readiness-final-r17.txt').read_text()
assert 'BLOCKED release:' not in readiness
identities = {name: re.search(r'^' + name + r' identity: ([a-f0-9]{64})$', readiness, re.MULTILINE)[1]
              for name in ('Candidate', 'Contract')}
for name in selected:
    assert re.search(r'^SATISFIED ' + re.escape(name) + r' \(#[0-9]+\)$', readiness, re.MULTILINE), name
baseline_readiness = subprocess.check_output(
    ['git', 'show', baseline + ':docs/generated/foundation-readiness.md'], cwd=root, text=True)
previously_satisfied = re.findall(r'^\| \[`([a-z0-9-]+)`\].*\| satisfied \|$', baseline_readiness, re.MULTILINE)
assert len(previously_satisfied) == 13
for name in previously_satisfied:
    assert re.search(r'^SATISFIED ' + re.escape(name) + r' \(#[0-9]+\)$', readiness, re.MULTILINE), name
blocked = re.findall(r'^BLOCKED ([a-z0-9-]+) \(#([0-9]+)\): (.*)$', readiness, re.MULTILINE)
assert blocked and all(name not in selected and reason for name, issue, reason in blocked)

index_path = root / 'capabilities/foundation-evidence.yaml'
index = yaml.safe_load(index_path.read_text())
assert index['candidate'] == receipt['candidate']
observations = index['certifications']
assert len(observations) == 100 and sum(len(item['records']) for item in observations) == 412
expected = {(item['id'], environment, execution) for item in contract['obligations'] if item['id'] in selected
            for environment in item['environments'] for execution in item['execution-profiles']}
assert {(item['obligation'], item['environment'], item['execution-profile']) for item in observations} == expected
expected_root = 'capabilities/evidence/foundation/T83-final-r17/'
environments = {item['profile']: item['record'] for item in index['environments']}
chain_counts = {}
for item in observations:
    assert item['configuration']['path'].startswith(expected_root + item['obligation'] + '/')
    configuration = json.loads(bind(item['configuration']).read_text())
    bind_tree(configuration)
    environment_ref = environments[item['environment']]
    environment = json.loads(bind(environment_ref).read_text())
    bind_tree(environment)
    assert configuration['candidate-sha256'] == identities['Candidate']
    assert configuration['environment-sha256'] == environment_ref['sha256']
    chains = set()
    case = driver.certification_case(item['obligation'])
    for record_ref in item['records']:
        record = json.loads(bind(record_ref).read_text())
        bind_tree(record)
        assert all(record.get(key) == value for key, value in {
            'obligation': item['obligation'], 'execution-profile': item['execution-profile'], 'result': 'pass',
            'candidate-sha256': identities['Candidate'], 'contract-sha256': identities['Contract'],
            'environment-sha256': environment_ref['sha256'],
            'execution-configuration-sha256': item['configuration']['sha256']}.items())
        assert record['chain'] not in chains
        chains.add(record['chain'])
        assert record['producer'] == driver.certification_producers(case)[record['chain']]
        report = json.loads(bind(record['report']).read_text())
        bind_tree(report)
        assert report['chain'] == record['chain'] and report['result'] == 'pass'
        assert record['negative-controls'] and report['negative-controls'] == record['negative-controls']
    assert chains == set(case.get('chains', driver.CHAINS))
    chain_counts[item['obligation']] = chain_counts.get(item['obligation'], 0) + len(chains)

mandatory = (root / 'capabilities/profiles/T22-worker-recovery-scale/mandatory-tests.txt').read_text().splitlines()
assert len(mandatory) == len(set(mandatory)) == 173
recoveries = []
for item in observations:
    if item['obligation'] != 'recovery':
        continue
    assert item['execution-profile'] == 'HARDENED_WORKER'
    scope = (root / item['configuration']['path']).parent
    replays = sorted(scope.glob('live-collection-*'))
    assert len(replays) == 1
    executions = []
    for execution in [scope, *replays]:
        transcript_path = execution / 'contract-tests.txt'
        transcript = transcript_path.read_text()
        cases = re.findall(r'^T22 CASE (\S+) = (PASS|FAIL)$', transcript, re.MULTILINE)
        assert len(cases) == 173 and {name for name, result in cases} == set(mandatory)
        assert all(result == 'PASS' for name, result in cases)
        assert 'T22 required execution: tests=173, failures=0, ignored=0, assumptions=0, duplicates=0\n' in transcript
        assert 'OK (173 tests)' in transcript
        diagnostic = require_exit_grace_observation(transcript)
        actual_profiles = re.findall(r'^T22_SCALE profile=(\w+) .+$', transcript, re.MULTILINE)
        assert len(actual_profiles) == 3 and set(actual_profiles) == {'pages', 'input', 'concurrency'}
        assert (execution / 'recorder.txt').read_text().strip() == 'T22 recovery and actual scale contracts and independent PDF observations passed'
        result = driver.properties(execution / 'observations/result.properties')
        assert result['native-execution-profile'] == 'HARDENED_WORKER' and result['facade-execution-profile'] == 'IN_PROCESS'
        scale = {}
        for profile in actual_profiles:
            path = execution / 'scale-observations' / profile / 'observations.properties'
            values = driver.properties(path)
            assert values['result'] == values['owned-storage-cleanup'] == 'pass'
            assert values['usage.pages'] == ('5000' if profile == 'pages' else '1')
            if profile == 'input':
                assert values['input-length'] == values['usage.input'] == values['usage.temporary'] == '1073741824'
                assert values['output-count'] == '0' and values['caller-input-open'] == 'true'
            if profile == 'concurrency':
                assert values['admitted-workflows'] == values['policy.concurrency'] == '2'
                assert values['first-excess-callback-count'] == '0' and values['retry-state'] == 'COMPLETED'
            scale[profile] = {'properties': driver.reference(root, path), 'actual-observations': values}
        executions.append({'scope': execution.relative_to(root).as_posix(), 'mandatory-tests': 173,
            'transcript': driver.reference(root, transcript_path), 'actual-exit-diagnostic': diagnostic,
            'actual-scale': scale, 'native': 'HARDENED_WORKER', 'facade': 'IN_PROCESS'})
    environment = json.loads(bind(environments[item['environment']]).read_text())
    launcher = driver.properties(scope / 'boundary-observations/launcher/actual.properties')
    assert launcher['java-vendor'] == environment['java-runtime']['vendor']
    assert launcher['java-build'] == environment['identity']['jdk-build']
    recoveries.append({'environment': item['environment'], 'environment-record': environments[item['environment']],
        'observed-launcher': launcher, 'execution-evidence': executions})
assert len(recoveries) == 4 and chain_counts['recovery'] == 20

steps_path = delivery / 'refresh-final-r17/results.json'
steps = json.loads(steps_path.read_text())
assert [item['obligation'] for item in steps] == list(selected) and all(item['exit-code'] == 0 for item in steps)
prior_hash = driver.sha256(delivery / 'refresh-final-r17-prior-index.yaml')
for step in steps:
    bind(step['prior-index'])
    bind(step['gate-result'])
    assert step['prior-index']['sha256'] == prior_hash
    prior_hash = step['current-index-sha256']
assert prior_hash == driver.sha256(index_path)
verification = json.loads((delivery / 'candidate-final-r9-verification-r17.json').read_text())
assert [row['gate'] for row in verification] == ['full-verify-final-r11', 'jdk-matrix-final-r5', 'standalone-scale-final-r8']
assert all(row['exit-code'] == 0 and row['source-staged-index-unchanged'] for row in verification)

scale_runs = json.loads((delivery / 'scale-final-r8/results.json').read_text())
assert [item['jdk-major'] for item in scale_runs] == [8, 11, 17, 21]
for item in scale_runs:
    assert item['tests'] == 3 and item['exit-code'] == item['skipped'] == 0
    assert set(item['actual-profiles']) == {'pages', 'input', 'concurrency'}
    bind_tree(item)
ordinary = json.loads((delivery / 'validation/full-verify-final-r11-tests.json').read_text())
matrix = json.loads((delivery / 'validation/jdk-matrix-final-r5-tests.json').read_text())
assert ordinary['failures'] == ordinary['errors'] == 0 and ordinary['skipped'] == 4
assert [item['jdk-major'] for item in matrix['environments']] == [8, 11, 17, 21]
assert all(item['failures'] == item['errors'] == 0 and item['skipped'] == 4 for item in matrix['environments'])
bind_tree(ordinary)
bind_tree(matrix)
gates = {}
for name in ('full-verify-final-r11', 'jdk-matrix-final-r5', 'standalone-scale-final-r8',
             'source-freeze-final-r9', 'stage-final-r9', 'archive-candidate-final-r9',
             'worker-all-exit-recovery-protocol-focused-green-r1', 'jdk8-interrupted-inspection-focused-green-r1',
             'worker-fixture-focused-jdk17-green-r1', 'python-recovery-worker-guards-r19',
             'checker-recovery-relaunch-r1', 'inventory-generate-final-r17', 'inventory-validate-final-r17',
             'inventory-check-final-r17', 'inventory-readiness-final-r17', 'post-final-r17-container-check',
             'diff-check-final-r17', 'retain-full-verify-counts-r17', 'retain-jdk-matrix-counts-r17',
             *('refresh-final-r17-' + obligation for obligation in selected)):
    path = delivery / 'validation' / (name + '-result.json')
    gate = json.loads(path.read_text())
    assert gate['exit-code'] == (1 if name == 'inventory-readiness-final-r17' else 0)
    bind_tree(gate)
    gates[name] = driver.reference(root, path)
assert (delivery / 'validation/post-final-r17-container-check.txt').read_text() == ''
for reference in frozen['reviews']:
    bind(reference)
    assert re.search(r'\bzero\s+', (root / reference['path']).read_text().lower())
helper_reviews = [driver.reference(root, delivery / 'reviews' / (axis + '-relaunch-' + revision + '.md'))
                  for revision in ('r3', 'r4', 'r6', 'r9', 'r10') for axis in ('standards', 'spec')]
for reference in helper_reviews:
    bind(reference)
    assert re.search(r'\bzero\s+', (root / reference['path']).read_text().lower())
changed = subprocess.check_output(['git', 'diff', '--name-only', baseline], cwd=root, text=True).splitlines()
shipping = [path for path in changed if path.startswith(('pdf-document/src/main/', 'pdf-provider-contract/src/main/',
    'pdf-conversion/src/main/', 'pdf-migration-itext7/src/main/', 'pdf-migration-itext7-preview/src/main/'))]
assert shipping == ['pdf-document/src/main/java/net/zerocloud/pdf/HardenedWorkerEngine.java']
assert not any(path.endswith('pom.xml') or path.startswith(('capabilities/evidence/foundation/T82-',
    'capabilities/evidence/T82-delivery/')) for path in changed)
output = {'recorded-at': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'baseline': baseline,
    'candidate-identity': identities['Candidate'], 'contract-identity': identities['Contract'],
    'source-freeze': driver.reference(root, frozen_path), 'current-index': driver.reference(root, index_path),
    'selected-obligations': selected, 'certification-tuples': 100, 'chain-records': 412,
    'chain-counts': chain_counts, 'recovery-tuples': 4, 'recovery-chain-records': 20,
    'recovery-evidence': recoveries, 'previously-satisfied-preserved': previously_satisfied,
    'remaining-unrelated-obligations': [{'obligation': name, 'issue': int(issue), 'reason': reason}
        for name, issue, reason in blocked], 'ordinary-verification': ordinary, 'jdk-matrix': matrix,
    'explicit-four-environment-scale': scale_runs, 'gates': gates, 'refreshed-ledger': driver.reference(root, steps_path),
    'verified-portable-archives': archive_audit, 'verified-references': len(seen),
    'superseded-evidence-preserved': driver.reference(root, delivery / 'relaunch-preservation.json'),
    'superseded-candidate6-preserved': frozen['superseded-candidate6'],
    'superseded-candidate7-preserved': frozen['superseded-candidate7'],
    'superseded-candidate8-preserved': frozen['superseded-candidate8'], 'reviews': frozen['reviews'],
    'superseded-candidate8-original-text-files-rehashed': len(text_seen),
    'superseded-candidate8-transitive-references-verified': len(historical_seen),
    'superseded-candidate8-archive-entries-verified': len(historical_archived),
    'superseded-candidates6-7-8-verified-archive-parts': superseded_archives,
    'failed-full-verify-r16-preserved': barcode_diagnostic,
    'delivery-helper-reviews': helper_reviews,
    'changed-shipping-source': shipping, 'commit-authority': 'One signed local commit after all criteria pass',
    'publication-and-tracker': 'Left to parent orchestrator; no push, PR, merge, close or release'}
path = delivery / 'final-audit-r17.json'
assert not path.exists()
driver.write_json(path, output)
print('Verified current frozen candidate: all 14 obligations SATISFIED, 100 tuples/412 chains; recovery 4 tuples/20 chains, 173 original/fresh cases without skips.')
