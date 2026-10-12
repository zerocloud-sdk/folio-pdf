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
import xml.etree.ElementTree as ET

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
frozen = json.loads((delivery / 'source-freeze-final-r5.json').read_text())
assert driver.capture_build_inputs(root, contract) == {key: frozen[key] for key in ('inputs', 'contract-inputs')}
archive_audit = []
archive_base = delivery / 'candidate-final-r5'
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
assert len(mandatory) == len(set(mandatory)) == 166
def audit_mandatory_transcript(path):
    transcript = path.read_text()
    cases = re.findall(r'(?m)^T22 CASE (\S+) = (PASS|FAIL)$', transcript)
    assert len(cases) == 166 and {name for name, result in cases} == set(mandatory)
    assert all(result == 'PASS' for name, result in cases)
    assert 'T22 required execution: tests=166, failures=0, ignored=0, assumptions=0, duplicates=0\n' in transcript
    assert 'OK (166 tests)' in transcript
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
                    'original-mandatory-tests': 166,
                    'original-mandatory-transcript': original_tests,
                    'fresh-mandatory-tests': fresh_tests})
assert len(recoveries) == 4
first_refresh = json.loads((delivery / 'refresh-final-r1/results.json').read_text())
second_refresh = json.loads((delivery / 'refresh-final-r2/results.json').read_text())
third_refresh = json.loads((delivery / 'refresh-final-r3/results.json').read_text())
continuation = json.loads((delivery / 'refresh-final-r4/results.json').read_text())
fifth_refresh = json.loads((delivery / 'refresh-final-r5/results.json').read_text())
sixth_refresh = json.loads((delivery / 'refresh-final-r7/results.json').read_text())
seventh_refresh = json.loads((delivery / 'refresh-final-r9/results.json').read_text())
provisioning_refresh = json.loads((delivery / 'refresh-final-r10/results.json').read_text())
fresh_refresh = json.loads((delivery / 'refresh-final-r11/results.json').read_text())
effective_refresh = json.loads((delivery / 'refresh-final-complete-r1/results.json').read_text())
assert [item['obligation'] for item in effective_refresh] == list(selected)
assert all(item['exit-code'] == 0 for item in effective_refresh)
assert [item['obligation'] for item in seventh_refresh] == list(selected[:6])
assert [item['exit-code'] for item in seventh_refresh] == [0, 0, 0, 0, 0, 1]
assert [item['obligation'] for item in fresh_refresh] == list(selected[5:])
assert all(item['exit-code'] == 0 for item in fresh_refresh)
assert [{key: value for key, value in item.items() if key != 'source-step'}
        for item in effective_refresh] == seventh_refresh[:5] + fresh_refresh
for item in effective_refresh:
    source = item['source-step']
    assert source['ledger'] == driver.reference(root, root / source['ledger']['path'])
    original_row = json.loads((root / source['ledger']['path']).read_text())[source['row-index']]
    assert original_row == {key: value for key, value in item.items() if key != 'source-step'}
actual_roots = {name: 'capabilities/evidence/foundation/T83-final-r9/' + name for name in selected}
actual_roots['text'] = 'capabilities/evidence/foundation/T83-final-r10/text'
assert all(item['configuration']['path'].startswith(actual_roots[item['obligation']] + '/')
           for item in observations)
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
assert continuation[-1]['current-index-sha256'] == fifth_refresh[0]['prior-index']['sha256']
assert fifth_refresh[-1]['obligation'] == 'password-attachments' and fifth_refresh[-1]['exit-code'] == 1
assert fifth_refresh[-1]['certifications'] == 80 and fifth_refresh[-1]['chain-records'] == 320
assert fifth_refresh[-1]['current-index-sha256'] == fifth_refresh[-1]['prior-index']['sha256']
assert fifth_refresh[-1]['current-index-sha256'] == sixth_refresh[0]['prior-index']['sha256']
assert [step['obligation'] for step in sixth_refresh] == ['transactions', 'values', 'pages']
assert [step['exit-code'] for step in sixth_refresh] == [0, 0, 1]
assert sixth_refresh[-1]['certifications'] == 16 and sixth_refresh[-1]['chain-records'] == 64
assert sixth_refresh[-1]['current-index-sha256'] == sixth_refresh[-1]['prior-index']['sha256']
assert sixth_refresh[-1]['current-index-sha256'] == seventh_refresh[0]['prior-index']['sha256']
assert seventh_refresh[-1]['current-index-sha256'] == seventh_refresh[-1]['prior-index']['sha256']
assert seventh_refresh[-1]['certifications'] == 40 and seventh_refresh[-1]['chain-records'] == 160
assert len(provisioning_refresh) == 1 and provisioning_refresh[0]['obligation'] == 'text'
assert provisioning_refresh[0]['exit-code'] == 1
assert provisioning_refresh[0]['current-index-sha256'] == provisioning_refresh[0]['prior-index']['sha256']
assert seventh_refresh[-1]['current-index-sha256'] == provisioning_refresh[0]['prior-index']['sha256']
assert provisioning_refresh[0]['current-index-sha256'] == fresh_refresh[0]['prior-index']['sha256']
assert fresh_refresh[-1]['current-index-sha256'] == driver.sha256(root / 'capabilities/foundation-evidence.yaml')
previous_index = None
for step in first_refresh + second_refresh + third_refresh + continuation + fifth_refresh + sixth_refresh + seventh_refresh + provisioning_refresh + fresh_refresh:
    assert driver.reference(root, root / step['prior-index']['path']) == step['prior-index']
    if previous_index is not None:
        assert step['prior-index']['sha256'] == previous_index
    previous_index = step['current-index-sha256']
passwords = [item for item in observations if item['obligation'] == 'password-baseline']
assert len(passwords) == 8 and all(item['configuration']['path'].startswith(
    'capabilities/evidence/foundation/T83-final-r9/password-baseline/') for item in passwords)
attachments = [item for item in observations if item['obligation'] == 'password-attachments']
assert len(attachments) == 8 and all(item['configuration']['path'].startswith(
    'capabilities/evidence/foundation/T83-final-r9/password-attachments/') for item in attachments)
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
# Preserve the fifth failed gate and the two actual unchanged-recorder timeouts.
# The separate validator predates the intentional source change; bind its exact
# retained inputs/results here instead of rerunning its old staged-build guard.
fifth_gate_path = delivery / 'validation/refresh-final-r5-password-attachments-result.json'
fifth_gate = json.loads(fifth_gate_path.read_text())
assert fifth_gate['exit-code'] == 1
assert driver.sha256(root / fifth_gate['log']['path']) == fifth_gate['log']['sha256']
old_t80_candidate = json.loads((delivery / 'candidate-final-r3/build-inputs.json').read_text())['candidate']
assert old_t80_candidate != receipt['candidate']
replay_path = delivery / 'diagnostics/attachments-coordinator-replay-r1/results.json'
replays = json.loads(replay_path.read_text())
assert replays['diagnostic-only'] and replays['certifications-exported'] == 0
assert replays['candidate'] == old_t80_candidate and replays['finished']
assert [(item['exit-code'], item['specific-coordinator-timeout']) for item in replays['runs']] == [(1, True), (1, True)]
assert replays['original-index-sha256'] == fifth_refresh[-1]['current-index-sha256']
original_t80_scope = (root / replays['execution']['path']).parent
assert replays['execution'] == driver.reference(root, root / replays['execution']['path'])
assert len(replays['original-files']) == 7062
assert [item['path'] for item in replays['original-files']] == [
    path.relative_to(root).as_posix() for path in sorted(path for path in original_t80_scope.rglob('*') if path.is_file())]
for item in replays['original-files']:
    path = root / item['path']
    assert not path.is_symlink() and path.stat().st_size == item['bytes']
    assert driver.sha256(path) == item['sha256']
assert not (original_t80_scope / 'observations/result.properties').exists()
fifth_recorder_path = original_t80_scope / 'recorder.txt'
fifth_recorder = fifth_recorder_path.read_text()
assert 'External tool time limit exceeded' in fifth_recorder
assert 'ExternalProcess.runCoordinator' in fifth_recorder
assert 'T80EvidenceCommand.main' in fifth_recorder
assert any(item['path'] == fifth_recorder_path.relative_to(root).as_posix()
           and item['sha256'] == driver.sha256(fifth_recorder_path)
           for item in replays['original-files'])
for run in replays['runs']:
    for name in ('command', 'log', 'gate'):
        assert run[name] == driver.reference(root, root / run[name]['path'])
    assert 'External tool time limit exceeded' in (root / run['log']['path']).read_text()
validated_path = delivery / 'diagnostics/validated-attachments-duration-r1.json'
validated = json.loads(validated_path.read_text())
assert validated['diagnostic-only'] and validated['certifications-exported'] == 0
assert validated['candidate'] == old_t80_candidate and validated['failed-probe-preserved']
assert validated['observer-exit-code'] == 0
assert 900 < validated['observer-seal-seconds'] < validated['elapsed-seconds-including-python-owner'] < 1800
assert validated['sealed-observation-files'] == 7060 and validated['verified-manifest-entries'] == 7058
assert validated['individual-tool-commands'] == 1797 and validated['individual-tool-limits-unchanged']
for name in ('failed-probe-gate', 'completed-observer', 'private-storage', 'validator'):
    assert validated[name] == driver.reference(root, root / validated[name]['path'])
probe_gate = json.loads((root / validated['failed-probe-gate']['path']).read_text())
assert probe_gate['exit-code'] == 1
assert probe_gate['log'] == driver.reference(root, root / probe_gate['log']['path'])
assert (root / probe_gate['log']['path']).read_text().rstrip().endswith(
    'AssertionError: The completed observer must release its private temporary storage')
observer = json.loads((root / validated['completed-observer']['path']).read_text())
assert observer['exit-code'] == 0 and not observer['outer-timeout'] and observer['owned-container-gone']
assert observer['candidate'] == old_t80_candidate and observer['certifications-exported'] == 0
for name in ('stdout', 'stderr', 'command', 'diagnostic-python-owner', 'original-execution', 'original-replay-ledger'):
    assert observer[name] == driver.reference(root, root / observer[name]['path'])
assert len(observer['copied-actual-publication-inputs']) == 249
for item in observer['copied-actual-publication-inputs']:
    for name in ('source', 'copy'):
        assert item[name] == driver.reference(root, root / item[name]['path'])
    assert item['source']['sha256'] == item['copy']['sha256']
observer_output = (root / validated['completed-observer']['path']).parent / 'observations'
result = driver.properties(observer_output / 'result.properties')
assert all(result[name] == 'pass' for name in ('syntax', 'standards', 'semantic', 'visual'))
manifest = observer_output / 'retained-files.sha256'
assert driver.sha256(manifest) == result['retained-files-sha256']
entries = {}
for line in manifest.read_text().splitlines():
    expected_hash, name = line.split('  ', 1)
    assert name not in entries, 'Duplicate manifest entry: ' + name
    entries[name] = expected_hash
assert len(entries) == 7058
assert {path.relative_to(observer_output).as_posix() for path in observer_output.rglob('*') if path.is_file()} == set(entries) | {'result.properties', 'retained-files.sha256'}
for name, expected_hash in entries.items():
    path = observer_output / name
    assert '..' not in Path(name).parts and not Path(name).is_absolute() and not path.is_symlink()
    assert driver.sha256(path) == expected_hash
# Bind the exact previously validated seal as well as rechecking every tool record.
assert driver.sha256(manifest) == '827169e774d0650c3339ad755d6b818f2974ae8f9c0517b049d7b30df0490e79'
assert driver.sha256(observer_output / 'result.properties') == '50a7757a2dc77f310604d521e2e945c94de7fb370f567b4a6b074c4efc7b4fc9'
duration_tools = list(observer_output.rglob('*.command.json'))
assert len(duration_tools) == 1797
for path in duration_tools:
    tool = json.loads(path.read_text())
    assert 0 < tool['timeout-seconds'] <= 30 and tool['maximum-output-bytes'] == 16 * 1024 * 1024
    assert tool['limit'] is None
    stem = path.with_name(path.name.removesuffix('.command.json'))
    assert driver.sha256(stem.with_name(stem.name + '.stdout')) == tool['stdout-sha256']
    assert driver.sha256(stem.with_name(stem.name + '.stderr')) == tool['stderr-sha256']
private = json.loads((root / validated['private-storage']['path']).read_text())
assert private['temporary-backing'] == 'original container /tmp' and private['private-directory-gone']
assert len(private['remaining-private-files-before-owner-cleanup']) == 534
assert driver.sha256(delivery / 'audit-final-r5-source.py') == 'ed05634ba528975335a1748d2f58a515e01ad7c6882699adcd614d661be13456'
assert 'Audit script SHA-256: ' + driver.sha256(delivery / 'audit-final-r5-source.py') in (delivery / 'validation/diagnostic-archive-helper-r3.txt').read_text()
for name, expected_code in (
        ('attachments-coordinator-diagnosis-r1', 0), ('attachments-observer-duration-r1', 1),
        ('validate-attachments-duration-r1', 0), ('archive-final-audit-r5-source-r1', 0),
        ('post-r5-failure-owned-container-check-r1', 0),
        ('post-attachment-coordinator-diagnosis-owned-container-check-r1', 0),
        ('post-attachment-observer-duration-owned-container-check-r1', 0)):
    gate = json.loads((delivery / 'validation' / (name + '-result.json')).read_text())
    assert gate['exit-code'] == expected_code
    assert gate['log'] == driver.reference(root, root / gate['log']['path'])
    if 'owned-container-check' in name:
        assert not (root / gate['log']['path']).read_text().strip()
launch_failure = json.loads((delivery / 'validation/full-verify-final-r3-result.json').read_text())
assert launch_failure['exit-code'] == 1
assert launch_failure['log'] == driver.reference(root, root / launch_failure['log']['path'])
assert 'newuidmap' in (root / launch_failure['log']['path']).read_text()
assert 'Operation not permitted' in (root / launch_failure['log']['path']).read_text()
assert 'BUILD FAILURE' not in (root / launch_failure['log']['path']).read_text()
duration_audit = {'fifth-failed-gate': driver.reference(root, fifth_gate_path),
                  'failed-recorder-replays': driver.reference(root, replay_path),
                  'fifth-failed-recorder': driver.reference(root, fifth_recorder_path),
                  'separately-validated-duration': driver.reference(root, validated_path),
                  'diagnostic-only': True, 'certifications-exported': 0}
# The failed complete reactor is distinct from the five certification failures.
# Its isolated probes cannot establish a causal fix or replay the preceding suite.
verification_failure_path = delivery / 'validation/full-verify-final-r4-result.json'
verification_failure = json.loads(verification_failure_path.read_text())
assert verification_failure['exit-code'] == 1
assert verification_failure['log'] == driver.reference(root, root / verification_failure['log']['path'])
failed_full_log = (root / verification_failure['log']['path']).read_text()
assert 'Tests run: 1265, Failures: 0, Errors: 1, Skipped: 3' in failed_full_log
assert 'The Worker did not terminate after completing its response.' in failed_full_log
shaping_base = delivery / 'diagnostics/full-verify-worker-exit-r1'
shaping_ledgers = []
for revision, count, directory, ledger_hash in (
        (1, 100, shaping_base, 'f4df6302b82b63357c9f8f8b63a1adab8e44cfe78c692028a293f6bf2d799abd'),
        (2, 10, shaping_base / 'revision-2', '71e623a36104855fed41483e7283f69e6586525a9ece81b6f01ee055df057eb0')):
    path = directory / 'method-replays.json'
    assert driver.sha256(path) == ledger_hash
    record = json.loads(path.read_text())
    assert record['diagnostic-only'] and record['certifications-exported'] == 0
    assert record['candidate'] == json.loads((delivery / 'candidate-final-r4/build-inputs.json').read_text())['candidate']
    assert record['original-index-sha256'] == fifth_refresh[-1]['current-index-sha256']
    assert len(record['results']) == count
    for name in ('failed-gate', 'original-report', 'probe', 'helper-source', 'compile-log'):
        assert record[name] == driver.reference(root, root / record[name]['path'])
    for item in record['helper-classes']:
        assert item == driver.reference(root, root / item['path'])
        assert (root / item['path']).read_bytes()[:8] == bytes.fromhex('cafebabe00000034')
    for row in record['results']:
        assert row['exit-code'] == 0 and row['exact-method-passed'] and not row['same-shutdown-symptom']
        assert row['log'] == driver.reference(root, root / row['log']['path'])
        assert 'OK (1 test)' in (root / row['log']['path']).read_text()
        if revision == 2:
            assert not row['outer-timeout'] and row['timeout-cleanup'] is None
            assert row['loaded-classpath-unchanged'] and row['owned-container-gone']
    if revision == 2:
        assert record['loaded-classpath-files'] == driver.reference(root, root / record['loaded-classpath-files']['path'])
        loaded = json.loads((root / record['loaded-classpath-files']['path']).read_text())
        assert loaded and len(loaded) == len({item['path'] for item in loaded})
        assert all(len(item['sha256']) == 64 and item['bytes'] >= 0 for item in loaded)
        # This complete module-cwd snapshot includes reports subsequently regenerated
        # by full verification. Its actual per-run checks and retry preflight are
        # retained; do not incorrectly compare regenerated files to historical bytes.
    shaping_ledgers.append(driver.reference(root, path))
report_path = shaping_base / 'TEST-net.zerocloud.pdf.consumer.ShapingCompositionWorkflowTest.xml'
report = ET.parse(report_path).getroot()
assert report.attrib['tests'] == '34' and report.attrib['errors'] == '1'
errors = [item for item in report.findall('testcase') if item.find('error') is not None]
assert len(errors) == 1
assert errors[0].attrib['name'] == 'tableMinimumAdmitsAJoinedLigatureNarrowerThanItsIsolatedGraphemes[HARDENED_WORKER]'
assert 'The Worker did not terminate after completing its response.' in errors[0].find('error').text
retry_path = shaping_base / 'verification-retry.json'
retry = json.loads(retry_path.read_text())
assert retry['diagnostic-only-records'] == shaping_ledgers
assert retry['candidate'] == json.loads((delivery / 'candidate-final-r4/build-inputs.json').read_text())['candidate'] and retry['exit-code'] == 0
assert retry['source-staged-index-unchanged'] and retry['prior-index-sha256'] == fifth_refresh[-1]['current-index-sha256']
assert driver.sha256(delivery / 'audit-final-r7-preverify-source.py') == '50bc6c04006a6b4c21b7cb342b6ed677e9e449be79017f89f0a8eec640d096d2'
assert 'Audit script SHA-256: ' + driver.sha256(delivery / 'audit-final-r7-preverify-source.py') in (delivery / 'validation/diagnostic-archive-helper-r4.txt').read_text()
verification_diagnosis = {'retained-failed-full-gate': driver.reference(root, verification_failure_path),
                          'diagnostic-ledgers': shaping_ledgers,
                          'guarded-fresh-verification': driver.reference(root, retry_path),
                          'diagnostics-exported-certifications': 0,
                          'cause-fix-or-failure-rate-guarantee': False}
# Sixth failed certification remains immutable, separate from the controlled race.
sixth_gate_path = delivery / 'validation/refresh-final-r7-pages-result.json'
sixth_gate = json.loads(sixth_gate_path.read_text())
assert sixth_gate['exit-code'] == 1 and sixth_gate['log'] == driver.reference(root, root / sixth_gate['log']['path'])
sixth_transcript = root / 'capabilities/evidence/foundation/T83-final-r7/pages/jdk8-hardened_worker/contract-tests.txt'
assert 'pageMovementRejectsMalformedInheritedAttributesBeforeMutation' in sixth_transcript.read_text()
assert 'The Worker did not terminate after completing its response.' in sixth_transcript.read_text()
assert 'Tests run: 65,  Failures: 1' in sixth_transcript.read_text()
old_worker_candidate = json.loads((delivery / 'candidate-final-r4/build-inputs.json').read_text())['candidate']
assert old_worker_candidate != receipt['candidate']
controlled_path = delivery / 'diagnostics/worker-handshake-stage4-red-r3/result.json'
controlled = json.loads(controlled_path.read_text())
assert controlled['diagnostic-only'] and controlled['certifications-exported'] == 0
assert controlled['candidate'] == old_worker_candidate and controlled['exit-code'] == 1
assert controlled['source-old-stage-index-unchanged']
assert controlled['prospective-source-inputs'] == {key: frozen[key] for key in ('inputs', 'contract-inputs')}
for item in [controlled['helper-source'], controlled['compiler'], controlled['compile-log'], *controlled['loaded-helper-classes']]:
    assert item == driver.reference(root, root / item['path'])
assert '--release' in controlled['compile-command'] and '8' in controlled['compile-command']
assert Path(controlled['compile-command'][-1]).read_bytes() == (root / controlled['helper-source']['path']).read_bytes()
assert len(controlled['loaded-helper-classes']) == 3
for item in controlled['loaded-helper-classes']:
    assert (root / item['path']).read_bytes()[:8] == bytes.fromhex('cafebabe00000034')
controlled_gate = json.loads((delivery / 'validation/worker-handshake-stage4-red-r3-result.json').read_text())
assert controlled_gate['exit-code'] == 1 and controlled_gate['log'] == driver.reference(root, root / controlled_gate['log']['path'])
controlled_text = (root / controlled_gate['log']['path']).read_text()
assert 'Tests run: 1,  Failures: 1' in controlled_text
assert 'The Worker did not terminate after completing its response.' in controlled_text
assert 'Real process must exit zero before parent resumes' not in controlled_text
# Three same-wrapper timing faults and the single-variable no-delay control.
control_path = delivery / 'diagnostics/exit-wait-race-r4/result.json'
control = json.loads(control_path.read_text())
assert control['diagnostic-only'] and control['certifications-exported'] == 0
assert control['candidate'] == old_worker_candidate and control['source-staged-index-unchanged']
assert [row['parent-delay-millis'] for row in control['trials']] == [1200, 1200, 1200, 0]
assert [row['exit-code'] for row in control['trials']] == [1, 1, 1, 0]
for row in control['trials']:
    assert row['result'] == driver.reference(root, root / row['result']['path'])
    gate = json.loads((root / row['result']['path']).read_text())
    assert gate['exit-code'] == row['exit-code'] and gate['log'] == driver.reference(root, root / gate['log']['path'])
    text = (root / gate['log']['path']).read_text()
    assert 'child-exited-zero=true' in text
    if row['parent-delay-millis']:
        assert 'delayed=true child-exited-zero=true output-exists=false' in text
        assert 'Real child exited zero before parent resumed, but the termination wait rejected it' in text
    else:
        assert 'completed=true delayed=true child-exited-zero=true' in text
for name, code in (('worker-termination-regression-red-r2', 1),
                   ('worker-termination-regression-green-r1', 0),
                   ('recovery-protocol-focused-after-exit-fix-r3', 0),
                   ('python-recovery-worker-guards-r16', 0),
                   ('post-r7-failure-owned-container-check-r1', 0)):
    gate = json.loads((delivery / 'validation' / (name + '-result.json')).read_text())
    assert gate['exit-code'] == code and gate['log'] == driver.reference(root, root / gate['log']['path'])
assert 'Tests run: 151, Failures: 0, Errors: 0, Skipped: 0' in (delivery / 'validation/recovery-protocol-focused-after-exit-fix-r3.txt').read_text()
assert not (delivery / 'validation/post-r7-failure-owned-container-check-r1.txt').read_text().strip()
assert driver.sha256(delivery / 'audit-final-r7-helper5-source.py') == 'f09d23164aa545ae4f298e92dcafca21f9046376a18ffff2f7b81fc7081d3684'
assert 'Audit script SHA-256: ' + driver.sha256(delivery / 'audit-final-r7-helper5-source.py') in (delivery / 'validation/diagnostic-archive-helper-r5.txt').read_text()
termination_diagnosis = {'sixth-failed-certification': driver.reference(root, sixth_gate_path),
                        'sixth-failed-transcript': driver.reference(root, sixth_transcript),
                        'source-bound-controlled-old-runtime-red': driver.reference(root, controlled_path),
                        'three-red-one-delay-free-control': driver.reference(root, control_path),
                        'isolated-cause': 'Deadline evaluated stale liveness after parent checkpoint delay; recheck child before declaring timeout.',
                        'all-historical-failures-attributed': False,
                        'finite-shutdown-bound-seconds': 1,
                        'diagnostics-exported-certifications': 0}
# The corrected candidate still had an uncontrolled text shutdown failure.
text_failed_gate_path = delivery / 'validation/refresh-final-r9-text-result.json'
text_failed_gate = json.loads(text_failed_gate_path.read_text())
assert text_failed_gate['exit-code'] == 1
assert text_failed_gate['log'] == driver.reference(root, root / text_failed_gate['log']['path'])
text_failed_scope = root / 'capabilities/evidence/foundation/T83-final-r9/text/jdk17-hardened_worker'
text_failed_report = text_failed_scope / 'contract-tests.txt'
text_failure = text_failed_report.read_text()
assert 'Tests run: 126,  Failures: 1' in text_failure
assert 'toUnicodeNodesAndCodeSpaceDeclarationsShareFontDataBounds' in text_failure
assert 'The Worker did not terminate after completing its response.' in text_failure
r9_failed_steps = json.loads((delivery / 'refresh-final-r9/results.json').read_text())
assert [item['exit-code'] for item in r9_failed_steps] == [0, 0, 0, 0, 0, 1]
assert r9_failed_steps[-1]['current-index-sha256'] == r9_failed_steps[-1]['prior-index']['sha256']
assert r9_failed_steps[-1]['certifications'] == 40 and r9_failed_steps[-1]['chain-records'] == 160
text_replays = []
for relative, count, marker, expected_cases in (
        ('diagnostics/text-worker-exit-r1/method-replays.json', 20, 'exact-method-passed', 1),
        ('diagnostics/text-worker-prefix-r2/prefix-replays.json', 10, 'exact-prefix-passed', 3)):
    ledger_path = delivery / relative
    probe = json.loads(ledger_path.read_text())
    assert probe['diagnostic-only'] and probe['certifications-exported'] == 0
    assert probe['candidate'] == receipt['candidate'] and len(probe['results']) == count
    assert probe['original-index-sha256'] == r9_failed_steps[-1]['current-index-sha256']
    assert probe['source-and-staged-inputs-unchanged'] and probe['index-unchanged']
    bound = [probe['probe'], probe['helper-source'], probe['compile-log'],
             probe['failed-gate'], probe['original-report'], probe['original-command'],
             probe['loaded-classpath-files']]
    helpers = probe.get('helper-classes', [probe.get('helper-class')])
    bound += helpers
    for item in bound:
        assert item == driver.reference(root, root / item['path'])
    for item in helpers:
        assert (root / item['path']).read_bytes()[:8] == bytes.fromhex('cafebabe00000034')
    loaded = json.loads((root / probe['loaded-classpath-files']['path']).read_text())
    for item in loaded:
        assert driver.sha256(root / item['path']) == item['sha256']
        assert (root / item['path']).stat().st_size == item['bytes']
    for row in probe['results']:
        assert row['exit-code'] == 0 and row[marker] and not row['same-shutdown-symptom']
        assert row['loaded-classpath-unchanged'] and row['owned-container-gone']
        assert not row['outer-timeout'] and row['timeout-cleanup'] is None
        assert row['log'] == driver.reference(root, root / row['log']['path'])
        assert 'OK (' + str(expected_cases) + (' test)' if expected_cases == 1 else ' tests)') in (root / row['log']['path']).read_text()
        assert '-Dfolio.t13.executionProfile=HARDENED_WORKER' in row['command']
        assert 'docker.io/library/eclipse-temurin@sha256:61a94244559f2e89e4edb02bae37eeb8762ecf5deaf237251fa630e5120a8798' in row['command']
        if expected_cases == 3:
            assert (root / row['log']['path']).read_text().count('[T83-TEXT-PREFIX] ') == 3
            failed_volume = str(text_failed_scope) + ':' + str(text_failed_scope).replace(str(root), '/workspace', 1) + ':ro'
            assert failed_volume in row['command']
    text_replays.append(driver.reference(root, ledger_path))
prefix_preflight_path = delivery / 'diagnostics/text-worker-prefix-r1/prefix-replays.json'
prefix_preflight = json.loads(prefix_preflight_path.read_text())
prefix_preflight_binding = json.loads((delivery / 'diagnostics/text-worker-prefix-r2/prefix-replays.json').read_text())['previous-diagnostic-preflight-failure']
assert prefix_preflight_binding == driver.reference(root, prefix_preflight_path)
prefix_refs = [prefix_preflight[key] for key in ('probe', 'helper-source', 'compile-log',
               'failed-gate', 'original-report', 'original-command', 'loaded-classpath-files')]
prefix_refs += prefix_preflight['helper-classes']
prefix_refs += [row['log'] for row in prefix_preflight['results']]
for item in prefix_refs:
    assert item == driver.reference(root, root / item['path'])
for item in prefix_preflight['helper-classes']:
    assert (root / item['path']).read_bytes()[:8] == bytes.fromhex('cafebabe00000034')
for item in json.loads((root / prefix_preflight['loaded-classpath-files']['path']).read_text()):
    assert driver.sha256(root / item['path']) == item['sha256']
    assert (root / item['path']).stat().st_size == item['bytes']
assert prefix_preflight['certifications-exported'] == 0 and len(prefix_preflight['results']) == 1
assert prefix_preflight['results'][0]['exit-code'] == 1
assert not prefix_preflight['results'][0]['same-shutdown-symptom']
assert prefix_preflight['results'][0]['owned-container-gone']
assert 'The captured failing method must be original case four' in (root / prefix_preflight['results'][0]['log']['path']).read_text()
for name in ('post-r9-failure-owned-container-check-r1', 'post-text-diagnostics-owned-container-check-r1'):
    gate = json.loads((delivery / 'validation' / (name + '-result.json')).read_text())
    assert gate['exit-code'] == 0 and gate['log'] == driver.reference(root, root / gate['log']['path'])
    assert not (root / gate['log']['path']).read_text().strip()
assert driver.sha256(delivery / 'audit-final-r9-helper6-source.py') == 'c5b4fce754a3309f192795afc50b039051ba6d3c6326448cc5aea274c6bffb08'
assert 'Audit script SHA-256: ' + driver.sha256(delivery / 'audit-final-r9-helper6-source.py') in (delivery / 'validation/diagnostic-archive-helper-r6.txt').read_text()
provisioning_gate_path = delivery / 'validation/refresh-final-r10-text-result.json'
provisioning_gate = json.loads(provisioning_gate_path.read_text())
assert provisioning_gate['exit-code'] == 1
assert provisioning_gate['log'] == driver.reference(root, root / provisioning_gate['log']['path'])
provisioning_error = (root / provisioning_gate['log']['path']).read_text()
assert 'FileNotFoundError' in provisioning_error and 'T83-final-r10/text' in provisioning_error
assert 'T13 environment JDK' not in provisioning_error
assert driver.sha256(delivery / 'audit-final.py') == 'a61d4bfb85c543815fa0d0ee1eed1769bf1b1b8eb3bd797e0d7d3e70d1d6adcb'
assert 'Audit script SHA-256: ' + driver.sha256(delivery / 'audit-final.py') in (delivery / 'validation/diagnostic-archive-helper-r7.txt').read_text()
text_shutdown_diagnosis = {
    'failed-gate': driver.reference(root, text_failed_gate_path),
    'failed-report': driver.reference(root, text_failed_report),
    'unchanged-candidate': receipt['candidate'], 'unindexed-failed-text-tuples': True,
    'unchanged-method-and-ordered-prefix-replays': text_replays,
    'retained-prefix-count-preflight-failure': driver.reference(root, prefix_preflight_path),
    'cause-or-failure-rate-guarantee-established': False,
    'runtime-changed-after-this-failure': False,
    'retained-pre-execution-directory-failure': driver.reference(root, provisioning_gate_path), 'diagnostics-exported-certifications': 0}
ordinary = json.loads((delivery / 'validation/full-verify-final-r6-tests.json').read_text())
assert ordinary['failures'] == ordinary['errors'] == 0 and ordinary['skipped'] == 4
assert ordinary['log'] == driver.reference(root, root / ordinary['log']['path'])
assert [(item['class'], item['skipped']) for item in ordinary['ordinary-skips']] == [
    ('net.zerocloud.pdf.consumer.HardenedWorkerScaleProfileTest', 3),
    ('net.zerocloud.pdf.acceptance.T30BarcodeEvidenceCommandTest', 1)]

refresh_audit = {'first-attempt': driver.reference(root, delivery / 'refresh-final-r1/results.json'),
                 'second-attempt': driver.reference(root, delivery / 'refresh-final-r2/results.json'),
                 'third-attempt': driver.reference(root, delivery / 'refresh-final-r3/results.json'),
                 'fourth-attempt': driver.reference(root, delivery / 'refresh-final-r4/results.json'),
                 'fifth-attempt': driver.reference(root, delivery / 'refresh-final-r5/results.json'),
                 'sixth-attempt': driver.reference(root, delivery / 'refresh-final-r7/results.json'),
                 'seventh-attempt': driver.reference(root, delivery / 'refresh-final-r9/results.json'),
                 'retained-directory-provisioning-failure': driver.reference(root, delivery / 'refresh-final-r10/results.json'),
                 'unchanged-candidate-continuation': driver.reference(root, delivery / 'refresh-final-r11/results.json'),
                 'final-refresh': driver.reference(root, delivery / 'refresh-final-complete-r1/results.json'),
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
for name in ('full-verify-final-r6', 'jdk-matrix-final-r1', 'python-recovery-worker-guards-r16', 'stage-final-r5',
             'recovery-protocol-focused-after-exit-fix-r3',
             'inventory-validate-final-r1', 'inventory-generate-final-r1',
             'inventory-check-final-r1', 'inventory-readiness-final-r1',
             'standalone-scale-final-r4', 'worker-exit-products-diagnosis-r2', 'archive-diagnostics-r1',
             'attachments-worker-exit-products-diagnosis-r1', 'archive-attachments-diagnostics-r1',
             'input-open-case-diagnosis-r1', 'archive-input-case-diagnostics-r1', 'diagnostic-archive-helper-r8',
             'text-worker-method-diagnostic-r1', 'text-worker-prefix-diagnostic-r2',
             'post-r9-failure-owned-container-check-r1', 'post-text-diagnostics-owned-container-check-r1',
             'post-r3-owned-container-check-r1',
             'post-r4-owned-container-check-r1', 'post-final-owned-container-check-r1',
             'source-freeze-final-r5', 'archive-candidate-final-r5', 'coordinator-budget-focused-r1',
             'validate-attachments-duration-r1', 'shaping-worker-exit-diagnosis-r1',
             'shaping-worker-exit-diagnosis-r2', 'archive-final-audit-r7-preverify-source-r1',
             'post-shaping-worker-exit-diagnosis-owned-container-check-r1',
             'recovery-bound-memory-test-green-r2',
             'recovery-input-memory-bound-replay-r2',
             *('refresh-final-r9-' + obligation for obligation in selected[:5]),
             *('refresh-final-r11-' + obligation for obligation in selected[5:])):
    result = json.loads((delivery / 'validation' / (name + '-result.json')).read_text())
    assert result['exit-code'] == (1 if name == 'inventory-readiness-final-r1' else 0), (name, result['exit-code'])
    assert driver.sha256(root / result['log']['path']) == result['log']['sha256']
    gates[name] = result
assert 'Audit script SHA-256: ' + driver.sha256(Path(__file__)) in (root / gates['diagnostic-archive-helper-r8']['log']['path']).read_text()
assert not (root / gates['post-r3-owned-container-check-r1']['log']['path']).read_text().strip()
assert not (root / gates['post-final-owned-container-check-r1']['log']['path']).read_text().strip()
scale_runs = json.loads((delivery / 'scale-final-r4/results.json').read_text())
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
shipping_changed = [name for name in changed if name.startswith(('pdf-document/src/main/', 'pdf-provider-contract/src/main/',
                    'pdf-conversion/src/main/', 'pdf-migration-itext7/src/main/', 'pdf-migration-itext7-preview/src/main/'))]
assert shipping_changed == ['pdf-document/src/main/java/net/zerocloud/pdf/HardenedWorkerEngine.java']
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
          'attachments-coordinator-diagnosis': duration_audit,
          'ordinary-verification': ordinary,
          'full-verification-shutdown-diagnosis': verification_diagnosis,
          'worker-termination-diagnosis': termination_diagnosis,
          'post-correction-text-shutdown-diagnosis': text_shutdown_diagnosis,
          'actual-certification-root-map': actual_roots,
          'shipping-source-or-build-compatibility-changed': True,
          'commit': 'Authorized once after this audit and final reviews pass', 'publication': 'Parent orchestrator only'}
path = delivery / 'final-audit.json'
assert not path.exists()
path.write_text(json.dumps(output, indent=2) + '\n')
print('Verified final frozen delivery: 100 certifications, 412 chain records, all14 selected obligations SATISFIED.')
