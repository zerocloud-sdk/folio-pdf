"""Audit delivery facts after final gates; never alter certification identities."""
import contextlib
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
spec = importlib.util.spec_from_file_location('final_audit', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
receipt = driver.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
frozen = json.loads((delivery / 'source-freeze-final-r3.json').read_text())
assert driver.capture_build_inputs(root, contract) == {key: frozen[key] for key in ('inputs', 'contract-inputs')}
archive_audit = []
archive_base = delivery / 'candidate-final-r3'
archive_inputs = {
    'source-inputs.zip': receipt['candidate']['inputs'],
    'artifacts-contracts-harness.zip': receipt['candidate']['artifacts'] + receipt['contract-inputs'] + receipt['harness']
        + [driver.reference(root, root / 'target/foundation-0.1.0' / name)
           for name in ('build-inputs.json', 'build-command.json', 'build.txt')]}
for archive_name, references in archive_inputs.items():
    manifest_path = archive_base / (archive_name + '-parts.json')
    manifest = json.loads(manifest_path.read_text())
    expected_entries = {item['path']: item['sha256'] for item in references}
    assert manifest['entries'] == [{'path': path, 'sha256': expected_entries[path]} for path in sorted(expected_entries)]
    with tempfile.TemporaryFile(dir=root / '.build-cache') as reconstructed:
        whole = hashlib.sha256()
        offset = 0
        for part in manifest['parts']:
            path = archive_base / part['path']
            assert part['offset'] == offset and 0 < part['bytes'] <= 48 * 1024 * 1024
            assert path.stat().st_size == part['bytes'] and driver.sha256(path) == part['sha256']
            with path.open('rb') as stream:
                while chunk := stream.read(1024 * 1024):
                    reconstructed.write(chunk)
                    whole.update(chunk)
            offset += part['bytes']
        assert offset == manifest['bytes'] and whole.hexdigest() == manifest['sha256']
        reconstructed.seek(0)
        with zipfile.ZipFile(reconstructed) as archive:
            assert archive.namelist() == sorted(expected_entries)
            for name, expected in expected_entries.items():
                with archive.open(name) as stream:
                    assert hashlib.file_digest(stream, 'sha256').hexdigest() == expected, name
    archive_audit.append({'manifest': driver.reference(root, manifest_path), 'archive-sha256': manifest['sha256'],
                          'entries': len(expected_entries), 'parts': len(manifest['parts']), 'bytes': offset})
index = yaml.safe_load((root / 'capabilities/foundation-evidence.yaml').read_text())
selected = ('transactions', 'values', 'pages', 'metadata', 'annotations', 'text', 'images',
            'incremental', 'password-baseline', 'password-clear-metadata', 'password-attachments', 'limits', 'worker', 'recovery')
observations = index['certifications']
assert len(observations) == 100
assert sum(len(item['records']) for item in observations) == 412
assert {item['obligation'] for item in observations} == set(selected)
assert index['candidate'] == receipt['candidate']
expected = set()
for obligation in contract['obligations']:
    if obligation['id'] in selected:
        expected.update((obligation['id'], environment, execution)
                        for environment in obligation['environments'] for execution in obligation['execution-profiles'])
assert {(item['obligation'], item['environment'], item['execution-profile']) for item in observations} == expected
recoveries = []
mandatory = (root / 'capabilities/profiles/T22-worker-recovery-scale/mandatory-tests.txt').read_text().splitlines()
assert len(mandatory) == len(set(mandatory)) == 165
def audit_mandatory_transcript(path):
    transcript = path.read_text()
    cases = re.findall(r'(?m)^T22 CASE (\S+) = (PASS|FAIL)$', transcript)
    assert len(cases) == 165 and {name for name, result in cases} == set(mandatory)
    assert all(result == 'PASS' for name, result in cases)
    assert 'T22 required execution: tests=165, failures=0, ignored=0, assumptions=0, duplicates=0\n' in transcript
    assert 'OK (165 tests)' in transcript
    return driver.reference(root, path)
for item in observations:
    if item['obligation'] != 'recovery':
        continue
    scope = (root / item['configuration']['path']).parent
    original_tests = audit_mandatory_transcript(scope / 'contract-tests.txt')
    fresh_tests = [audit_mandatory_transcript(path)
                   for path in sorted(scope.glob('live-collection-*/contract-tests.txt'))]
    assert fresh_tests, 'Missing actual collector replay: ' + str(scope)
    for execution_scope in [scope, *sorted(scope.glob('live-collection-*'))]:
        assert (execution_scope / 'recorder.txt').read_text().strip() == 'T22 recovery and actual scale contracts and independent PDF observations passed'
        stdout = (execution_scope / 'contract-tests.txt').read_text()
        actual = re.findall(r'^T22_SCALE profile=(\w+) .+$', stdout, re.MULTILINE)
        assert len(actual) == 3 and set(actual) == {'pages', 'input', 'concurrency'}
        for profile in actual:
            values = driver.properties(execution_scope / 'scale-observations' / profile / 'observations.properties')
            assert values['result'] == values['owned-storage-cleanup'] == 'pass'
            assert values['usage.pages'] == ('5000' if profile == 'pages' else '1')
            if profile == 'input':
                assert values['input-length'] == values['usage.input'] == values['usage.temporary'] == '1073741824'
                assert values['output-count'] == '0' and values['caller-input-open'] == 'true'
            if profile == 'concurrency':
                assert values['admitted-workflows'] == '2' and values['policy.concurrency'] == '2'
                assert values['first-excess-callback-count'] == '0' and values['retry-state'] == 'COMPLETED'
    environment = next(observed for observed in index['environments'] if observed['profile'] == item['environment'])
    actual_environment = json.loads((root / environment['record']['path']).read_text())
    launcher = driver.properties(scope / 'boundary-observations/launcher/actual.properties')
    chains = [json.loads((root / record['path']).read_text()) for record in item['records']]
    assert {record['chain'] for record in chains} == {'syntax', 'standards', 'semantic', 'visual', 'contract'}
    assert all(record['result'] == 'pass' for record in chains)
    assert launcher['java-vendor'] == actual_environment['java-runtime']['vendor']
    assert launcher['java-build'] == actual_environment['identity']['jdk-build']
    recoveries.append({'environment': actual_environment, 'scope': scope.relative_to(root).as_posix(),
                    'launcher': launcher, 'chains': chains,
                    'original-mandatory-tests': 165,
                    'original-mandatory-transcript': original_tests,
                    'fresh-mandatory-tests': fresh_tests})
assert len(recoveries) == 4
first_refresh = json.loads((delivery / 'refresh-final-r1/results.json').read_text())
second_refresh = json.loads((delivery / 'refresh-final-r2/results.json').read_text())
third_refresh = json.loads((delivery / 'refresh-final-r3/results.json').read_text())
continuation = json.loads((delivery / 'refresh-final-r4/results.json').read_text())
fresh_refresh = json.loads((delivery / 'refresh-final-r5/results.json').read_text())
effective_refresh = fresh_refresh
assert [item['obligation'] for item in effective_refresh] == list(selected)
assert all(item['exit-code'] == 0 for item in effective_refresh)
assert first_refresh[-1]['obligation'] == 'password-baseline' and first_refresh[-1]['exit-code'] == 1
assert first_refresh[-1]['current-index-sha256'] == first_refresh[-1]['prior-index']['sha256']
assert first_refresh[-1]['current-index-sha256'] == second_refresh[0]['prior-index']['sha256']
assert second_refresh[-1]['obligation'] == 'password-attachments' and second_refresh[-1]['exit-code'] == 1
assert second_refresh[-1]['certifications'] == 80 and second_refresh[-1]['chain-records'] == 320
assert second_refresh[-1]['current-index-sha256'] == second_refresh[-1]['prior-index']['sha256']
assert second_refresh[-1]['current-index-sha256'] == third_refresh[0]['prior-index']['sha256']
assert len(third_refresh) == 1
assert third_refresh[-1]['obligation'] == 'password-attachments' and third_refresh[-1]['exit-code'] == 1
assert third_refresh[-1]['certifications'] == 80 and third_refresh[-1]['chain-records'] == 320
assert third_refresh[-1]['current-index-sha256'] == third_refresh[-1]['prior-index']['sha256']
assert third_refresh[-1]['current-index-sha256'] == continuation[0]['prior-index']['sha256']
assert continuation[-1]['obligation'] == 'recovery' and continuation[-1]['exit-code'] == 1
assert continuation[-1]['certifications'] == 96 and continuation[-1]['chain-records'] == 392
assert continuation[-1]['current-index-sha256'] == continuation[-1]['prior-index']['sha256']
assert continuation[-1]['current-index-sha256'] == fresh_refresh[0]['prior-index']['sha256']
assert fresh_refresh[-1]['current-index-sha256'] == driver.sha256(root / 'capabilities/foundation-evidence.yaml')
previous_index = None
for step in first_refresh + second_refresh + third_refresh + continuation + fresh_refresh:
    assert driver.reference(root, root / step['prior-index']['path']) == step['prior-index']
    if previous_index is not None:
        assert step['prior-index']['sha256'] == previous_index
    previous_index = step['current-index-sha256']
passwords = [item for item in observations if item['obligation'] == 'password-baseline']
assert len(passwords) == 8 and all(item['configuration']['path'].startswith(
    'capabilities/evidence/foundation/T83-final-r5/password-baseline/') for item in passwords)
attachments = [item for item in observations if item['obligation'] == 'password-attachments']
assert len(attachments) == 8 and all(item['configuration']['path'].startswith(
    'capabilities/evidence/foundation/T83-final-r5/password-attachments/') for item in attachments)
old_candidate = json.loads((delivery / 'candidate-final-r2/build-inputs.json').read_text())['candidate']
assert old_candidate != receipt['candidate']
diagnostic = json.loads((delivery / 'diagnostics/worker-exit-replays-r2.json').read_text())
assert diagnostic['candidate'] == old_candidate and len(diagnostic['results']) == 5
assert all(item['exit-code'] == 0 and not item['same-shutdown-symptom']
           and item['published-products'] == item['completed-public-records'] == 68
           for item in diagnostic['results'])
@contextlib.contextmanager
def verified_diagnostic_archive(manifest_path):
    manifest = json.loads(manifest_path.read_text())
    archive_path = root / manifest['archive']['path']
    assert archive_path == manifest_path.with_suffix('')
    assert driver.sha256(archive_path) == manifest['archive']['sha256']
    assert archive_path.stat().st_size == manifest['archive']['bytes'] <= 48 * 1024 * 1024
    with zipfile.ZipFile(archive_path) as archive:
        entries = {item['path']: item for item in manifest['entries']}
        assert archive.namelist() == list(entries)
        for name, entry in entries.items():
            data = archive.read(name)
            assert len(data) == entry['bytes'] and hashlib.sha256(data).hexdigest() == entry['sha256']
        yield manifest, archive, entries

def audit_diagnostic_archive(manifest_path, replay_record, replay_root, expected_products, require_clear=False):
    with verified_diagnostic_archive(manifest_path) as (manifest, archive, entries):
        for replay in replay_record['results']:
            assert hashlib.sha256(archive.read(replay['log']['path'])).hexdigest() == replay['log']['sha256']
            base = replay_root + '/attempt-' + str(replay['attempt']) + '/observations/'
            products = [name for name in entries if name.startswith(base) and name.endswith('/product.pdf')]
            publications = [name for name in entries if name.startswith(base) and name.endswith('/publication.properties')]
            assert len(products) == len(publications) == expected_products
            for name in publications:
                values = dict(line.split('=', 1) for line in archive.read(name).decode().splitlines()
                              if line and not line.startswith('#') and '=' in line)
                assert values['status'] == 'COMMITTED' and values['partial-output-possible'] == 'false'
                assert values['reopened'] == values['source-preserved'] == 'pass'
                product = name.removesuffix('publication.properties') + 'product.pdf'
                assert values['output-sha256'] == entries[product]['sha256']
            if require_clear:
                clear_records = [name for name in entries if name.startswith(base) and name.endswith('/clear-observation.properties')]
                assert len(clear_records) == expected_products
                for name in clear_records:
                    values = dict(line.split('=', 1) for line in archive.read(name).decode().splitlines()
                                  if line and not line.startswith('#') and '=' in line)
                    assert values['clear-document'] == values['owner-attachment-bytes'] == 'pass'
    return {'entries': len(manifest['entries']), 'archive': driver.reference(root, root / manifest['archive']['path'])}

def audit_method_archive(manifest_path, replay_path):
    method = json.loads(replay_path.read_text())
    assert len(method['results']) == 10 and {item['attempt'] for item in method['results']} == set(range(1, 11))
    assert method['test-class'] == 'net.zerocloud.pdf.consumer.EmbeddedFilesPasswordWorkflowTest'
    assert method['test-method'] == 'originalInputsOpenClearContentWithoutAttachmentCredentials'
    assert method['probe-script-sha256'] == driver.sha256(delivery / 'diagnostics/input-case-exit-replay-r1.py')
    assert method['helper-source'] == driver.reference(root, delivery / 'diagnostics/T83ExistingMethodReplay.java')
    scope = root / 'capabilities/evidence/foundation/T83-final-r3/password-attachments/jdk8-hardened_worker'
    assert method['source-command'] == driver.reference(root, scope / 'contract-tests-command.json')
    assert method['source-configuration'] == driver.reference(root, scope / 'execution.yaml')
    original = json.loads((scope / 'contract-tests-command.json').read_text())
    boundary = original.index('org.junit.runner.JUnitCore')
    cp_index = original.index('-cp') + 1
    mount = str(scope) + ':' + str(scope).replace(str(root), '/workspace', 1) + ':rw'
    assert original.count(mount) == 1
    replay_root = '.build-cache/t83-input-case-replay-r1'
    with verified_diagnostic_archive(manifest_path) as (manifest, archive, entries):
        assert manifest['replay-ledger'] == driver.reference(root, replay_path)
        assert manifest['helper-source'] == method['helper-source']
        expected = {item['log']['path'] for item in method['results']}
        expected.update((method['helper-class']['path'], replay_root + '/compile.txt'))
        assert set(entries) == expected and len(entries) == 12
        helper = archive.read(method['helper-class']['path'])
        assert helper[:8] == bytes.fromhex('cafebabe00000034')
        assert hashlib.sha256(helper).hexdigest() == method['helper-class']['sha256']
        for replay in method['results']:
            destination = root / replay_root / ('attempt-' + str(replay['attempt']))
            container_destination = str(destination).replace(str(root), '/workspace', 1)
            command = [str(destination) + ':' + container_destination + ':rw' if argument == mount else argument
                       for argument in original[:boundary]]
            command[cp_index] += ':/workspace/' + replay_root + '/classes'
            command += ['T83ExistingMethodReplay', method['test-class'], method['test-method']]
            assert replay['command'] == command
            assert replay['exit-code'] == 0 and replay['exact-method-passed'] and not replay['same-shutdown-symptom']
            data = archive.read(replay['log']['path'])
            assert hashlib.sha256(data).hexdigest() == replay['log']['sha256']
            assert 'OK (1 test)' in data.decode() and 'FAILURES!!!' not in data.decode()
            assert 'The Worker did not terminate after completing its response.' not in data.decode()
    return {'entries': len(manifest['entries']), 'archive': driver.reference(root, root / manifest['archive']['path'])}

diagnostic_manifest_path = delivery / 'diagnostics/worker-exit-products.zip.json'
audit_diagnostic_archive(diagnostic_manifest_path, diagnostic, '.build-cache/t83-worker-exit-replay-r2', 68)
attachment_diagnostic_path = delivery / 'diagnostics/attachments-exit-replays-r1.json'
attachment_diagnostic = json.loads(attachment_diagnostic_path.read_text())
assert attachment_diagnostic['candidate'] == old_candidate and len(attachment_diagnostic['results']) == 5
assert all(item['exit-code'] == 0 and not item['same-shutdown-symptom']
           and item['published-products'] == item['completed-public-records'] == 62
           and item['all-public-outcomes-complete'] for item in attachment_diagnostic['results'])
attachment_manifest_path = delivery / 'diagnostics/attachments-worker-exit-products.zip.json'
audit_diagnostic_archive(attachment_manifest_path, attachment_diagnostic,
                        '.build-cache/t83-attachments-exit-replay-r1', 62, require_clear=True)
method_diagnostic_path = delivery / 'diagnostics/input-case-exit-replays-r1.json'
method_diagnostic = json.loads(method_diagnostic_path.read_text())
assert method_diagnostic['candidate'] == old_candidate
method_manifest_path = delivery / 'diagnostics/input-open-method-replays.zip.json'
audit_method_archive(method_manifest_path, method_diagnostic_path)
failed_gate_path = delivery / 'validation/refresh-final-r1-password-baseline-result.json'
failed_gate = json.loads(failed_gate_path.read_text())
assert failed_gate['exit-code'] == 1 and driver.sha256(root / failed_gate['log']['path']) == failed_gate['log']['sha256']
attachment_failed_gate_path = delivery / 'validation/refresh-final-r2-password-attachments-result.json'
attachment_failed_gate = json.loads(attachment_failed_gate_path.read_text())
assert attachment_failed_gate['exit-code'] == 1
assert driver.sha256(root / attachment_failed_gate['log']['path']) == attachment_failed_gate['log']['sha256']
method_failed_gate_path = delivery / 'validation/refresh-final-r3-password-attachments-result.json'
method_failed_gate = json.loads(method_failed_gate_path.read_text())
assert method_failed_gate['exit-code'] == 1
assert driver.sha256(root / method_failed_gate['log']['path']) == method_failed_gate['log']['sha256']
failed_transcript = root / 'capabilities/evidence/foundation/T83-final-r3/password-attachments/jdk8-hardened_worker/contract-tests.txt'
assert 'Tests run: 24,  Failures: 1' in failed_transcript.read_text()
assert 'originalInputsOpenClearContentWithoutAttachmentCredentials' in failed_transcript.read_text()
assert 'The Worker did not terminate after completing its response.' in failed_transcript.read_text()
assert not (failed_transcript.parent / 'observations').exists()
recovery_failed_gate = json.loads((delivery / 'validation/refresh-final-r4-recovery-result.json').read_text())
assert recovery_failed_gate['exit-code'] == 1
assert driver.sha256(root / recovery_failed_gate['log']['path']) == recovery_failed_gate['log']['sha256']
assert 'original findings/observations differ from live replay: scale/input/observations.properties' in (root / recovery_failed_gate['log']['path']).read_text()
memory_diagnostic = json.loads((delivery / 'diagnostics/recovery-input-memory-bound-replay-r2.json').read_text())
assert memory_diagnostic['diagnostic-only'] and memory_diagnostic['certifications-exported'] == 0
assert [item['usage.memory'] for item in memory_diagnostic['observations']] == ['1060', '1068']
for item in memory_diagnostic['observations']:
    assert driver.sha256(root / item['path']) == item['sha256']
for name in ('recovery-input-memory-repro-r1', 'recovery-input-memory-repro-r2', 'recovery-bound-memory-test-red-r1'):
    gate = json.loads((delivery / 'validation' / (name + '-result.json')).read_text())
    assert gate['exit-code'] == 1 and driver.sha256(root / gate['log']['path']) == gate['log']['sha256']
assert driver.sha256(delivery / 'audit-final-r4-source.py') == '6aff17c950f3628b9fc842feca4e517cdd7dad8796ea8a2efe7aeacc09ef22ee'
assert 'Audit script SHA-256: ' + driver.sha256(delivery / 'audit-final-r4-source.py') in (delivery / 'validation/diagnostic-archive-helper-r2.txt').read_text()
refresh_audit = {'first-attempt': driver.reference(root, delivery / 'refresh-final-r1/results.json'),
                 'second-attempt': driver.reference(root, delivery / 'refresh-final-r2/results.json'),
                 'third-attempt': driver.reference(root, delivery / 'refresh-final-r3/results.json'),
                 'fourth-attempt': driver.reference(root, delivery / 'refresh-final-r4/results.json'),
                 'final-refresh': driver.reference(root, delivery / 'refresh-final-r5/results.json'),
                 'retained-failed-recovery-gate': driver.reference(root, delivery / 'validation/refresh-final-r4-recovery-result.json'),
                 'bound-memory-diagnostic': driver.reference(root, delivery / 'diagnostics/recovery-input-memory-bound-replay-r2.json'),
                 'retained-failed-gate': driver.reference(root, failed_gate_path),
                 'retained-failed-attachment-gate': driver.reference(root, attachment_failed_gate_path),
                 'retained-failed-method-gate': driver.reference(root, method_failed_gate_path),
                 'retained-failed-method-transcript': driver.reference(root, failed_transcript),
                 'diagnostic': driver.reference(root, delivery / 'diagnostics/worker-exit-replays-r2.json'),
                 'diagnostic-artifacts': driver.reference(root, diagnostic_manifest_path),
                 'attachment-diagnostic': driver.reference(root, attachment_diagnostic_path),
                 'attachment-diagnostic-artifacts': driver.reference(root, attachment_manifest_path),
                 'method-diagnostic': driver.reference(root, method_diagnostic_path),
                 'method-diagnostic-artifacts': driver.reference(root, method_manifest_path)}
gates = {}
for name in ('full-verify-final-r2', 'python-recovery-worker-guards-r12', 'stage-final-r3',
             'inventory-validate-final-r1', 'inventory-generate-final-r1',
             'inventory-check-final-r1', 'inventory-readiness-final-r1',
             'standalone-scale-final-r2', 'worker-exit-products-diagnosis-r2', 'archive-diagnostics-r1',
             'attachments-worker-exit-products-diagnosis-r1', 'archive-attachments-diagnostics-r1',
             'input-open-case-diagnosis-r1', 'archive-input-case-diagnostics-r1', 'diagnostic-archive-helper-r3',
             'post-r3-owned-container-check-r1',
             'post-r4-owned-container-check-r1', 'recovery-bound-memory-test-green-r2',
             'recovery-input-memory-bound-replay-r2',
             *('refresh-final-r5-' + obligation for obligation in selected)):
    result = json.loads((delivery / 'validation' / (name + '-result.json')).read_text())
    assert result['exit-code'] == (1 if name == 'inventory-readiness-final-r1' else 0), (name, result['exit-code'])
    assert driver.sha256(root / result['log']['path']) == result['log']['sha256']
    gates[name] = result
assert 'Audit script SHA-256: ' + driver.sha256(Path(__file__)) in (root / gates['diagnostic-archive-helper-r3']['log']['path']).read_text()
assert not (root / gates['post-r3-owned-container-check-r1']['log']['path']).read_text().strip()
scale_runs = json.loads((delivery / 'scale-final-r2/results.json').read_text())
assert [item['jdk-major'] for item in scale_runs] == [8, 11, 17, 21]
assert all(item['tests'] == 3 and item['exit-code'] == item['skipped'] == 0
           and set(item['actual-profiles']) == {'pages', 'input', 'concurrency'} for item in scale_runs)
readiness = (delivery / 'validation/inventory-readiness-final-r1.txt').read_text()
assert 'BLOCKED release:' not in readiness, 'Global evidence, candidate or contract identity errors remain'
for name in selected:
    assert re.search(r'^SATISFIED ' + re.escape(name) + r' \(#[0-9]+\)$', readiness, re.MULTILINE), name
baseline_readiness = subprocess.check_output(
    ['git', 'show', baseline + ':docs/generated/foundation-readiness.md'], text=True)
previously_satisfied = re.findall(
    r'^\| \[`([a-z0-9-]+)`\].*\| satisfied \|$', baseline_readiness, re.MULTILINE)
assert len(previously_satisfied) == 13, previously_satisfied
for name in previously_satisfied:
    assert re.search(r'^SATISFIED ' + re.escape(name) + r' \(#[0-9]+\)$', readiness, re.MULTILINE), name
blocked = re.findall(r'^BLOCKED ([a-z0-9-]+) \(#([0-9]+)\): (.*)$', readiness, re.MULTILINE)
assert all(name not in selected for name, issue, reason in blocked)
identities = {name: re.search(r'^' + name + r' identity: ([a-f0-9]{64})$', readiness, re.MULTILINE)[1]
              for name in ('Candidate', 'Contract')}
changed = subprocess.check_output(['git', 'diff', '--name-only', baseline], text=True).splitlines()
assert not any(name.startswith(('pdf-document/src/main/', 'pdf-provider-contract/src/main/', 'pdf-conversion/src/main/',
                                'pdf-migration-itext7/src/main/', 'pdf-migration-itext7-preview/src/main/')) for name in changed)
assert not any(name.endswith('pom.xml') for name in changed)
assert not any(name.startswith(('capabilities/evidence/foundation/T82-', 'capabilities/evidence/T82-delivery/')) for name in changed)
assert not subprocess.check_output(['git', 'diff', '--name-only', '--cached'], text=True).strip()
head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
assert head == baseline
status = subprocess.check_output(['git', 'status', '--short', '--branch'], text=True)
output = {'baseline': baseline, 'final-head': head, 'status': status, 'tracked-changed-files': changed,
          'candidate-identity': identities['Candidate'], 'contract-identity': identities['Contract'],
          'current-index': driver.reference(root, root / 'capabilities/foundation-evidence.yaml'),
          'certifications': len(observations), 'chain-records': 412, 'satisfied-obligations': list(selected),
          'previously-satisfied-obligations-preserved': previously_satisfied,
          'remaining-unrelated-blockers': [{'obligation': name, 'issue': int(issue), 'reason': reason}
                                          for name, issue, reason in blocked],
          'recovery-certifications': recoveries, 'gates': gates, 'explicit-four-environment-scale': scale_runs, 'refresh-attempts': refresh_audit,
          'verified-retained-archives': archive_audit,
          'shipping-source-or-build-compatibility-changed': False,
          'commit': 'Authorized once after this audit and final reviews pass', 'publication': 'Parent orchestrator only'}
path = delivery / 'final-audit.json'
assert not path.exists()
path.write_text(json.dumps(output, indent=2) + '\n')
print('Verified final frozen delivery: 100 certifications, 412 chain records, all14 selected obligations SATISFIED.')
