"""Validate completed candidate10 qualification and preserve predecessor identities."""
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
spec = importlib.util.spec_from_file_location('t83_final_audit_r23', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
from t22_foundation_reports import require_exit_grace_observation

selected = ('transactions', 'values', 'pages', 'metadata', 'annotations', 'text', 'images',
            'incremental', 'password-baseline', 'password-clear-metadata', 'password-attachments',
            'limits', 'worker', 'recovery')
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
frozen_path = delivery / 'source-freeze-final-r10.json'
frozen = json.loads(frozen_path.read_text())
receipt = driver.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
assert driver.capture_build_inputs(root, contract) == {key: frozen[key] for key in ('inputs', 'contract-inputs')}
assert receipt == json.loads((delivery / 'candidate-final-r10/build-inputs.json').read_text())
assert receipt['candidate'] != json.loads((delivery / 'candidate-final-r9/build-inputs.json').read_text())['candidate']
assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip() == baseline
assert not subprocess.check_output(['git', 'diff', '--name-only', '--cached'], cwd=root, text=True).strip()

seen = set()
review_conclusion = re.compile(r'\bzero\s+(?:(?:remaining|unresolved)\s+)?actionable\b', re.IGNORECASE)
assert not review_conclusion.search('zero validation gates completed; one unresolved actionable finding')


def bind(reference):
    key = (reference['path'], reference['sha256'])
    if key not in seen:
        path = root / reference['path']
        assert path.is_file() and not path.is_symlink() and driver.reference(root, path) == reference, key
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


for reference in frozen['superseded-candidate9'] + frozen['reviews'] + frozen['development-gates']:
    bind(reference)
preservation_spec = importlib.util.spec_from_file_location('t83_preserve_final_r20', delivery / 'preserve-superseded-r20.py')
preservation_module = importlib.util.module_from_spec(preservation_spec)
preservation_spec.loader.exec_module(preservation_module)
preservation = preservation_module.validate()
assert preservation == json.loads(bind(frozen['superseded-preservation']).read_text())
assert preservation['originals-preserved'] and preservation['diagnostics-exported-certifications'] == 0
assert preservation['candidate8-text-originals-rehashed'] == 157778

# Check every portable candidate10 part and every uncompressed entry against
# the actual staged receipt; a parts manifest alone is insufficient.
archive_audit = []
archive_base = delivery / 'candidate-final-r10'
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

readiness = (delivery / 'validation/inventory-readiness-final-r23.txt').read_text()
assert 'BLOCKED release:' not in readiness
identities = {name: re.search(r'^' + name + r' identity: ([a-f0-9]{64})$', readiness, re.MULTILINE)[1]
              for name in ('Candidate', 'Contract')}
for name in selected:
    assert re.search(r'^SATISFIED ' + re.escape(name) + r' \(#[0-9]+\)$', readiness, re.MULTILINE), name
baseline_readiness = subprocess.check_output(
    ['git', 'show', baseline + ':docs/generated/foundation-readiness.md'], cwd=root, text=True)
baseline_satisfied = re.findall(r'^\| \[`([a-z0-9-]+)`\].*\| satisfied \|$', baseline_readiness, re.MULTILINE)
# Baseline readiness includes the derived security aggregate in addition to
# the thirteen producer-certified prerequisites. Preserve both sets exactly.
previously_satisfied = [name for name in baseline_satisfied if name != 'security']
assert len(previously_satisfied) == 13 and set(previously_satisfied) == set(selected) - {'recovery'}
assert len(baseline_satisfied) == 14 and set(baseline_satisfied) == set(previously_satisfied) | {'security'}
for name in baseline_satisfied:
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
expected_root = 'capabilities/evidence/foundation/T83-final-r20/'
environments = {item['profile']: item['record'] for item in index['environments']}
chain_counts = {}
for item in observations:
    if item['obligation'] == 'text':
        attempt = 'r23' if item['environment'].endswith('jdk21') and item['execution-profile'] == 'HARDENED_WORKER' else 'r21'
        observation_root = 'capabilities/evidence/foundation/T83-final-' + attempt + '/'
    else:
        observation_root = expected_root
    assert item['configuration']['path'].startswith(observation_root + item['obligation'] + '/')
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
assert len(mandatory) == len(set(mandatory)) == 180
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
        assert len(cases) == 180 and {name for name, result in cases} == set(mandatory)
        assert all(result == 'PASS' for name, result in cases)
        assert 'T22 required execution: tests=180, failures=0, ignored=0, assumptions=0, duplicates=0\n' in transcript
        assert 'OK (180 tests)' in transcript
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
        executions.append({'scope': execution.relative_to(root).as_posix(), 'mandatory-tests': 180,
            'transcript': driver.reference(root, transcript_path), 'actual-exit-diagnostic': diagnostic,
            'actual-scale': scale, 'native': 'HARDENED_WORKER', 'facade': 'IN_PROCESS'})
    environment = json.loads(bind(environments[item['environment']]).read_text())
    launcher = driver.properties(scope / 'boundary-observations/launcher/actual.properties')
    assert launcher['java-vendor'] == environment['java-runtime']['vendor']
    assert launcher['java-build'] == environment['identity']['jdk-build']
    recoveries.append({'environment': item['environment'], 'environment-record': environments[item['environment']],
        'observed-launcher': launcher, 'execution-evidence': executions})
assert len(recoveries) == 4 and chain_counts['recovery'] == 20

steps_path = delivery / 'refresh-final-r23/results.json'
steps = json.loads(steps_path.read_text())
assert [item['obligation'] for item in steps] == list(selected) and all(item['exit-code'] == 0 for item in steps)
preflight = json.loads((delivery / 'refresh-final-r23/preflight.json').read_text())
bind_tree(preflight)
assert preflight['candidate'] == receipt['candidate'] and not preflight['reuse-of-superseded-certifications']
assert preflight['current-candidate-passes-retained'] == list(selected[:5])
original_steps_path = delivery / 'refresh-final-r20/results.json'
original_steps = json.loads(original_steps_path.read_text())
assert len(original_steps) == 6 and original_steps[:5] == steps[:5]
assert original_steps[-1]['obligation'] == 'text' and original_steps[-1]['exit-code'] == 1
assert original_steps[-1]['prior-index']['sha256'] == original_steps[-1]['current-index-sha256'] == original_steps[-2]['current-index-sha256']
bind_tree(original_steps)
failed_text_path = delivery / 'failed-text-certification-r20.json'
failed_text = json.loads(failed_text_path.read_text())
assert failed_text['exit-code'] == 1 and failed_text['certifications-exported'] == 0 and failed_text['index-unchanged']
bind(failed_text['prior-index'])
bind(failed_text['gate'])
failure_text = bind(failed_text['failure']).read_text()
assert 'Independent checker CPU qualification bound reached' in failure_text and 'platform.python_version' in failure_text
preserved_files = preserved_bytes = 0
with gzip.open(bind(failed_text['manifest']), 'rt', encoding='utf-8') as manifest:
    assert next(manifest) == 'sha256\tbytes\tpath\n'
    for line in manifest:
        digest, length, name = line.rstrip('\n').split('\t')
        assert (root / name).stat().st_size == int(length)
        bind({'path': name, 'sha256': digest})
        preserved_files += 1
        preserved_bytes += int(length)
assert preserved_files == failed_text['original-files'] == 58245
assert preserved_bytes == failed_text['original-bytes']
cpu_diagnostics_path = delivery / 't13-cpu-diagnostics-r21/results.json'
cpu_diagnostics = json.loads(cpu_diagnostics_path.read_text())
assert len(cpu_diagnostics) == 20 and all(row['exit-code'] == row['stderr-bytes'] == 0
    and row['result'] == 'fail' and row['rule'] == 'content-properties' for row in cpu_diagnostics)
assert all(0 <= row['child-user-cpu-seconds'] + row['child-system-cpu-seconds'] < 5 for row in cpu_diagnostics)

# R21's failed eighth tuple remains failed. Seven independently complete
# original tuples are reused at their actual paths, with no byte changes.
failed_junit_path = delivery / 'failed-text-certification-r21.json'
failed_junit = json.loads(failed_junit_path.read_text())
assert failed_junit['exit-code'] == 1 and failed_junit['certifications-exported'] == 0 and failed_junit['index-unchanged']
for name in ('prior-index', 'gate', 'ledger', 'failure', 'source-freeze', 'refresh-driver', 'preflight'):
    bind(failed_junit[name])
assert failed_junit['observed-index']['sha256'] == failed_junit['prior-index']['sha256']
failure_text = bind(failed_junit['failure']).read_text()
assert 'TestTimedOutException: test timed out after 10000 milliseconds' in failure_text
assert 'HardenedWorkerEngine.execute(HardenedWorkerEngine.java:210)' in failure_text
assert 'Tests run: 126,  Failures: 1' in failure_text
junit_files = junit_bytes = 0
with gzip.open(bind(failed_junit['manifest']), 'rt', encoding='utf-8') as manifest:
    assert next(manifest) == 'sha256\tbytes\tpath\n'
    for line in manifest:
        digest, length, name = line.rstrip('\n').split('\t')
        assert (root / name).stat().st_size == int(length)
        bind({'path': name, 'sha256': digest})
        junit_files += 1
        junit_bytes += int(length)
assert junit_files == failed_junit['original-files'] == 138077
assert junit_bytes == failed_junit['original-bytes'] == 900290576
junit_steps = json.loads(bind(failed_junit['ledger']).read_text())
assert len(junit_steps) == 6 and junit_steps[:5] == steps[:5]
assert junit_steps[-1]['exit-code'] == 1 and junit_steps[-1]['obligation'] == 'text'
diagnostic_base = delivery / 'text-timeout-diagnostics-r22'
diagnostic_results_path = diagnostic_base / 'results.json'
diagnostic_results = json.loads(diagnostic_results_path.read_text())
assert len(diagnostic_results) == 24
assert all(row['exit-code'] == 0 and row['required-execution'] for row in diagnostic_results[:23])
assert [(row['mode'], row['attempt']) for row in diagnostic_results] == \
    [('method', index) for index in range(1, 21)] + [('prefix', index) for index in range(1, 5)]
assert diagnostic_results[-1]['exit-code'] == 1 and not diagnostic_results[-1]['required-execution']
for row in diagnostic_results:
    path = diagnostic_base / (row['mode'] + '-' + str(row['attempt'])) / 'tests.txt'
    bind({'path': path.relative_to(root).as_posix(), 'sha256': row['transcript-sha256']})
    transcript = path.read_text()
    assert row['test-timeout-millis'] == 10000 and row['monotonic-duration-seconds'] >= 0
    assert 'ignored=0 assumptions=0 target-timeout-millis=10000' in transcript
failed_prefix = (diagnostic_base / 'prefix-4/tests.txt').read_text()
assert 'ProxyDocumentSession.query' in failed_prefix and 'monotonic-millis=10685' in failed_prefix
assert 'T83 DIAGNOSTIC mode=prefix tests=18 failures=1' in failed_prefix
diagnostic_gate_path = delivery / 'validation/text-timeout-diagnostic-r22-result.json'
diagnostic_gate = json.loads(diagnostic_gate_path.read_text())
assert diagnostic_gate['exit-code'] == 1
bind_tree(diagnostic_gate)
diagnostic_preflight = json.loads((diagnostic_base / 'preflight.json').read_text())
assert diagnostic_preflight['index']['sha256'] == failed_junit['prior-index']['sha256']
assert diagnostic_preflight['candidate'] == receipt['candidate']
bind_tree(diagnostic_preflight['preserved-inputs'])
completion = root / 'capabilities/evidence/foundation/T83-final-r23/text'
completion_preflight = json.loads((completion / 'preflight.json').read_text())
bind_tree(completion_preflight)
assert completion_preflight['candidate'] == receipt['candidate']
assert completion_preflight['retained-complete-original-tuples'] == 7 and completion_preflight['fresh-required-tuples'] == 1
assert not completion_preflight['failed-attempts-relabelled'] and not completion_preflight['source-or-limit-changes']
text_tuples = [row for row in observations if row['obligation'] == 'text']
assert len(text_tuples) == 8 and sum(len(row['records']) for row in text_tuples) == 32
assert sum('/T83-final-r21/text/' in row['configuration']['path'] for row in text_tuples) == 7
assert sum('/T83-final-r23/text/' in row['configuration']['path'] for row in text_tuples) == 1
completion_failure_path = delivery / 'failed-text-completion-r22.json'
completion_failure = json.loads(completion_failure_path.read_text())
assert completion_failure['exit-code'] == 1 and completion_failure['certifications-exported'] == 0
assert completion_failure['index-unchanged'] and completion_failure['original-files'] == 17
bind_tree(completion_failure['retained-inputs'])
bind(completion_failure['prior-index'])
completion_files = completion_bytes = 0
with gzip.open(bind(completion_failure['manifest']), 'rt', encoding='utf-8') as manifest:
    assert next(manifest) == 'sha256\tbytes\tpath\n'
    for line in manifest:
        digest, length, name = line.rstrip('\n').split('\t')
        assert (root / name).stat().st_size == int(length)
        bind({'path': name, 'sha256': digest})
        completion_files += 1
        completion_bytes += int(length)
assert completion_files == 17 and completion_bytes == completion_failure['original-bytes'] == 1841985
failed_completion_gate_path = delivery / 'validation/refresh-final-r22-text-result.json'
failed_completion_gate = json.loads(failed_completion_gate_path.read_text())
assert failed_completion_gate['exit-code'] == 1
assert "ModuleNotFoundError: No module named 't13_foundation_reports'" in bind(failed_completion_gate['log']).read_text()
bind_tree(failed_completion_gate)
prior_hash = driver.sha256(delivery / 'refresh-final-r20-prior-index.yaml')
for step in steps:
    bind(step['prior-index'])
    bind(step['gate-result'])
    assert step['prior-index']['sha256'] == prior_hash
    prior_hash = step['current-index-sha256']
assert prior_hash == driver.sha256(index_path)
verification = json.loads((delivery / 'candidate-final-r10-verification.json').read_text())
assert [row['gate'] for row in verification] == ['full-verify-final-r13', 'jdk-matrix-final-r6', 'standalone-scale-final-r9']
assert all(row['exit-code'] == 0 and row['source-staged-index-unchanged'] for row in verification)
bind_tree(verification)

scale_runs = json.loads((delivery / 'scale-final-r9/results.json').read_text())
assert [item['jdk-major'] for item in scale_runs] == [8, 11, 17, 21]
for item in scale_runs:
    assert item['tests'] == 3 and item['exit-code'] == item['skipped'] == 0
    assert set(item['actual-profiles']) == {'pages', 'input', 'concurrency'}
    bind_tree(item)
ordinary = json.loads((delivery / 'validation/full-verify-final-r13-tests.json').read_text())
matrix = json.loads((delivery / 'validation/jdk-matrix-final-r6-tests.json').read_text())
assert ordinary['failures'] == ordinary['errors'] == 0 and ordinary['skipped'] == 4
assert [item['jdk-major'] for item in matrix['environments']] == [8, 11, 17, 21]
assert all(item['failures'] == item['errors'] == 0 and item['skipped'] == 4 for item in matrix['environments'])
assert len(ordinary['monotonic-regression-executions']) == 2
assert all(sum(row['tests'] for row in item['monotonic-regression-executions']) == 7 for item in matrix['environments'])
bind_tree(ordinary)
bind_tree(matrix)
gates = {}
for name in ('full-verify-final-r13', 'jdk-matrix-final-r6', 'standalone-scale-final-r9',
             'source-freeze-final-r10', 'stage-final-r10', 'archive-candidate-final-r10',
             'monotonic-recovery-protocol-green-r3', 'monotonic-python-plan-guards-r20',
             'monotonic-python-prerequisite-guards-r20', 'preserve-superseded-r20',
             'inventory-generate-final-r23', 'inventory-validate-final-r23',
             'inventory-check-final-r23', 'inventory-readiness-final-r23', 'post-final-r23-container-check',
             'diff-check-final-r23', 'retain-full-verify-final-r13-counts-r20',
             'retain-jdk-matrix-final-r6-counts-r20',
             't13-cpu-diagnostic-r21', 'pre-r21-container-check',
             'preserve-failed-text-r21', 'post-r21-failure-container-check', 'post-r22-diagnostic-container-check',
             'staged-content-proof-r23',
             *('refresh-final-r20-' + obligation for obligation in selected[:5]),
             *('refresh-final-r23-' + obligation for obligation in selected[5:])):
    path = delivery / 'validation' / (name + '-result.json')
    gate = json.loads(path.read_text())
    assert gate['exit-code'] == (1 if name == 'inventory-readiness-final-r23' else 0)
    assert gate['monotonic-duration-seconds'] >= 0
    assert gate['wall-minus-monotonic-seconds'] == gate['wall-duration-seconds'] - gate['monotonic-duration-seconds']
    bind_tree(gate)
    gates[name] = driver.reference(root, path)
assert (delivery / 'validation/post-final-r23-container-check.txt').read_text() == ''
staged_content_proof_path = delivery / 'staged-content-proof-r23.json'
staged_content_proof = json.loads(staged_content_proof_path.read_text())
assert staged_content_proof['result'] == 'pass' and staged_content_proof['isolated-commits-created'] == 0
assert staged_content_proof['target-head-and-index-unchanged']
assert [row['accepted'] for row in staged_content_proof['observations']] == [True, False]
assert all(row['same-path'] and row['same-length'] for row in staged_content_proof['observations'])
commit_plan_path = delivery / 'local-commit-r23.py'
commit_review_path = delivery / 'reviews/standards-local-commit-r23d.md'
assert review_conclusion.search(commit_review_path.read_text())
assert driver.sha256(commit_plan_path) in commit_review_path.read_text()
for reference in frozen['reviews']:
    bind(reference)
    assert review_conclusion.search((root / reference['path']).read_text())
final_reviews = [driver.reference(root, delivery / 'reviews' / (axis + '-relaunch-r23.md'))
                 for axis in ('standards', 'spec')]
for reference in final_reviews:
    bind(reference)
    assert re.findall(r'^R23 final verdict: .+$', (root / reference['path']).read_text(), re.MULTILINE) == [
        'R23 final verdict: PASS; actionable findings: 0.']
closing_reviews = [driver.reference(root, delivery / 'reviews' / (axis + '-closing-audit-r24.md'))
                   for axis in ('standards', 'spec')]
for reference in closing_reviews:
    bind(reference)
    assert re.findall(r'^R24 closing correction verdict: .+$', (root / reference['path']).read_text(), re.MULTILINE) == [
        'R24 closing correction verdict: PASS; actionable findings: 0.']
failed_audit_path = delivery / 'validation/final-evidence-audit-r23-result.json'
failed_audit = json.loads(failed_audit_path.read_text())
assert failed_audit['exit-code'] == 1
bind_tree(failed_audit)
assert 'assert len(previously_satisfied) == 13' in bind(failed_audit['log']).read_text()
changed = subprocess.check_output(['git', 'diff', '--name-only', baseline], cwd=root, text=True).splitlines()
shipping = [path for path in changed if path.startswith(('pdf-document/src/main/', 'pdf-provider-contract/src/main/',
    'pdf-conversion/src/main/', 'pdf-migration-itext7/src/main/', 'pdf-migration-itext7-preview/src/main/'))]
assert set(shipping) == {'pdf-document/src/main/java/net/zerocloud/pdf/' + name + '.java' for name in (
    'DocumentWorkflow', 'HardenedWorkerEngine', 'WorkerMessages', 'WorkflowEnvironment',
    'WorkflowResourceContext', 'WorkflowResourcePolicy', 'WorkflowResourceUsage')}
assert not any(path.endswith('pom.xml') or path.startswith(('capabilities/evidence/foundation/T82-',
    'capabilities/evidence/T82-delivery/')) for path in changed)
red_path = delivery / 'validation/monotonic-wall-step-red-r1-result.json'
red = json.loads(red_path.read_text())
assert red['exit-code'] == 1
bind_tree(red)
assert 'The workflow elapsed-time limit was exceeded.' in bind(red['log']).read_text()
focused_path = delivery / 'validation/monotonic-recovery-protocol-green-r3-result.json'
focused = json.loads(focused_path.read_text())
focused_log = bind(focused['log']).read_text()
focused_rows = re.findall(r'^\[INFO\] Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+).* -- in (\S+)$', focused_log, re.MULTILINE)
assert sum(int(row[0]) for row in focused_rows) == 271
assert all(row[1:4] == ('0', '0', '0') for row in focused_rows)
output = {'recorded-at': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'baseline': baseline,
    'audit-invocation': ['python3', '-B', Path(__file__).relative_to(root).as_posix()],
    'candidate-identity': identities['Candidate'], 'contract-identity': identities['Contract'],
    'source-freeze': driver.reference(root, frozen_path), 'current-index': driver.reference(root, index_path),
    'selected-obligations': selected, 'certification-tuples': 100, 'chain-records': 412,
    'chain-counts': chain_counts, 'recovery-tuples': 4, 'recovery-chain-records': 20,
    'recovery-evidence': recoveries, 'previously-satisfied-preserved': previously_satisfied,
    'previously-satisfied-aggregates-preserved': ['security'],
    'remaining-unrelated-obligations': [{'obligation': name, 'issue': int(issue), 'reason': reason}
        for name, issue, reason in blocked], 'ordinary-verification': ordinary, 'jdk-matrix': matrix,
    'explicit-four-environment-scale': scale_runs, 'gates': gates, 'refreshed-ledger': driver.reference(root, steps_path),
    'text-observer-cpu-failure': {'original-ledger': driver.reference(root, original_steps_path),
        'preservation': driver.reference(root, failed_text_path), 'original-files-rehashed': preserved_files,
        'unchanged-cpu-guard': {'soft-seconds': 5, 'hard-seconds': 10},
        'exact-control-diagnostics': driver.reference(root, cpu_diagnostics_path), 'diagnostics-exported-certifications': 0,
        'resolution': 'Text rerun in fresh R21 scopes under unchanged guards. Seven complete same-candidate R21 originals and a fresh R23 eighth tuple are fully qualified; five successful same-candidate R20 obligations remain intact.'},
    'text-junit-watchdog-failure': {
        'preservation': driver.reference(root, failed_junit_path), 'original-files-rehashed': junit_files,
        'original-bytes': junit_bytes, 'failed-diagnostic-gate': driver.reference(root, diagnostic_gate_path),
        'diagnostic-attempts': driver.reference(root, diagnostic_results_path),
        'diagnostic-disposition': 'Twenty exact-method and three prefix parents passed; prefix four failed and six planned prefixes never executed. No certification exported or cause inferred.',
        'complete-text-preflight': driver.reference(root, completion / 'preflight.json'),
        'original-tuples-retained': 7, 'fresh-tuples-executed': 1, 'timeouts-or-limits-changed': False,
        'resolution': 'Preserved all original bytes; unchanged collector requalified seven complete originals at actual paths and the newly executed eighth. Unchanged publisher committed only the complete eight-tuples/32-chain text set.'},
    'text-completion-import-failure': {
        'preservation': driver.reference(root, completion_failure_path), 'original-files-rehashed': completion_files,
        'original-bytes': completion_bytes, 'failed-gate': driver.reference(root, failed_completion_gate_path),
        'certifications-exported': 0,
        'resolution': 'Fresh R23 delivery helper adds repository scripts to its import path before loading the unchanged collector. Product, staged inputs, assertions and qualification limits remain unchanged.'},
    'verified-portable-archives': archive_audit, 'verified-references': len(seen),
    'superseded-evidence-revalidated': preservation, 'source-reviews': frozen['reviews'], 'final-reviews': final_reviews,
    'closing-audit-supplemental-reviews': closing_reviews,
    'failed-final-audit-attempts': [{'gate': driver.reference(root, failed_audit_path),
        'helper': driver.reference(root, delivery / 'audit-relaunch-r23.py'),
        'disposition': 'Delivery-only assertion counted the derived security aggregate as a fourteenth producer prerequisite. The R24 audit requires all thirteen exact producer prerequisites and the security aggregate to remain SATISFIED. Product, contracts, staged artifacts and certification predicates remain unchanged.'}],
    'changed-shipping-source': shipping,
    'monotonic-timing': {'audit': driver.reference(root, delivery / 'timing-audit-r20.md'),
        'supplemental-audit': driver.reference(root, delivery / 'timing-audit-supplement-r23.md'),
        'supplemental-findings': driver.reference(root, delivery / 'timing-audit-supplement-r23.txt'),
        'behavioral-red': driver.reference(root, red_path), 'focused-green': driver.reference(root, focused_path),
        'focused-tests': 271, 'focused-monotonic-duration-seconds': focused['monotonic-duration-seconds'],
        'focused-wall-duration-seconds': focused['wall-duration-seconds'],
        'focused-wall-minus-monotonic-seconds': focused['wall-minus-monotonic-seconds'],
        'semantics': 'Elapsed limits, remaining budgets and usage default to nanosecond ticker. Explicit Clock-only environments retain logical test time; absolute deadlines remain wall instants.'},
    'commit-authority': 'One signed local commit after all criteria pass',
    'commit-integrity': {'reviewed-plan': driver.reference(root, commit_plan_path),
        'standards-review': driver.reference(root, commit_review_path),
        'real-git-staged-content-proof': driver.reference(root, staged_content_proof_path),
        'predicate': 'Every staged blob SHA-256 and length must match the immutable final ownership rows; both self-excluded files are bound too. Then create exactly one local DCO commit and verify parent, identity, sign-off and clean workspace.'},
    'publication-and-tracker': 'Left to parent orchestrator; no push, PR, merge, close or release'}
path = delivery / 'final-audit-r23.json'
assert not path.exists()
driver.write_json(path, output)
print('Verified frozen candidate10: 14 obligations SATISFIED, 100 tuples/412 chains; recovery 4 tuples/20 chains, 180 original/fresh cases without skips.')
