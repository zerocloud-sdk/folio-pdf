"""Audit delivery facts after final gates; never alter certification identities."""
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
baseline = 'df2df726a2695267bf63a7c9df49a4a7481f0169'
sys.path.insert(0, str(root / 'scripts'))
spec = importlib.util.spec_from_file_location('final_audit', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
receipt = driver.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
frozen = json.loads((delivery / 'source-freeze-r3.json').read_text())
assert driver.capture_build_inputs(root, contract) == {key: frozen[key] for key in ('inputs', 'contract-inputs')}
archive_audit = []
archive_base = delivery / 'candidate-final-r2'
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
            'incremental', 'password-baseline', 'password-clear-metadata', 'password-attachments', 'limits', 'worker')
observations = index['certifications']
assert len(observations) == 96
assert sum(len(item['records']) for item in observations) == 392
assert {item['obligation'] for item in observations} == set(selected)
assert index['candidate'] == receipt['candidate']
expected = set()
for obligation in contract['obligations']:
    if obligation['id'] in selected:
        expected.update((obligation['id'], environment, execution)
                        for environment in obligation['environments'] for execution in obligation['execution-profiles'])
assert {(item['obligation'], item['environment'], item['execution-profile']) for item in observations} == expected
workers = []
mandatory = (root / 'capabilities/profiles/T21-hardened-worker/mandatory-tests.txt').read_text().splitlines()
assert len(mandatory) == len(set(mandatory)) == 137
def audit_mandatory_transcript(path):
    transcript = path.read_text()
    cases = re.findall(r'(?m)^T21 CASE (\S+) = (PASS|FAIL)$', transcript)
    assert len(cases) == 137 and {name for name, result in cases} == set(mandatory)
    assert all(result == 'PASS' for name, result in cases)
    assert 'T21 required execution: tests=137, failures=0, ignored=0, assumptions=0, duplicates=0\n' in transcript
    assert 'OK (137 tests)' in transcript
    return driver.reference(root, path)
for item in observations:
    if item['obligation'] != 'worker':
        continue
    scope = (root / item['configuration']['path']).parent
    original_tests = audit_mandatory_transcript(scope / 'contract-tests.txt')
    fresh_tests = [audit_mandatory_transcript(path)
                   for path in sorted(scope.glob('live-collection-*/contract-tests.txt'))]
    assert fresh_tests, 'Missing actual collector replay: ' + str(scope)
    environment = next(observed for observed in index['environments'] if observed['profile'] == item['environment'])
    actual_environment = json.loads((root / environment['record']['path']).read_text())
    launcher = driver.properties(scope / 'boundary-observations/launcher/actual.properties')
    chains = [json.loads((root / record['path']).read_text()) for record in item['records']]
    assert {record['chain'] for record in chains} == {'syntax', 'standards', 'semantic', 'visual', 'contract'}
    assert all(record['result'] == 'pass' for record in chains)
    assert launcher['java-vendor'] == actual_environment['java-runtime']['vendor']
    assert launcher['java-build'] == actual_environment['identity']['jdk-build']
    workers.append({'environment': actual_environment, 'scope': scope.relative_to(root).as_posix(),
                    'launcher': launcher, 'chains': chains,
                    'original-mandatory-tests': 137,
                    'original-mandatory-transcript': original_tests,
                    'fresh-mandatory-tests': fresh_tests})
assert len(workers) == 4
first_refresh = json.loads((delivery / 'refresh-final-r1/results.json').read_text())
continuation = json.loads((delivery / 'refresh-final-r2/results.json').read_text())
assert [item['obligation'] for item in first_refresh[:-1] + continuation] == list(selected)
assert all(item['exit-code'] == 0 for item in first_refresh[:-1] + continuation)
assert first_refresh[-1]['obligation'] == 'password-attachments' and first_refresh[-1]['exit-code'] == 1
assert first_refresh[-1]['current-index-sha256'] == continuation[0]['prior-index']['sha256']
assert continuation[-1]['current-index-sha256'] == driver.sha256(root / 'capabilities/foundation-evidence.yaml')
for step in first_refresh + continuation:
    assert driver.reference(root, root / step['prior-index']['path']) == step['prior-index']
reuse_path = root / 'capabilities/evidence/foundation/T82-final-r2/password-attachments/reuse-receipt.json'
reuse = json.loads(reuse_path.read_text())
assert len(reuse['reused-original-tuples']['certifications']) == 6 and reuse['fresh-JDK21-tuples'] == 2
refresh_audit = {'first-attempt': driver.reference(root, delivery / 'refresh-final-r1/results.json'),
                 'continuation': driver.reference(root, delivery / 'refresh-final-r2/results.json'),
                 'reuse-receipt': driver.reference(root, reuse_path),
                 'retained-failed-gate': driver.reference(root, delivery / 'validation/refresh-final-r1-password-attachments-timeout.json')}
gates = {}
for name in ('full-ubuntu-verify-final-r1', 'collector-suite-final-r2', 'stage-final-r2',
             'inventory-validate-final-r1', 'inventory-generate-final-r1',
             'inventory-check-final-r1', 'inventory-readiness-final-r1', 't21-cli-collect-final-r1',
             't80-resume-guards-r1', 'refresh-final-r2-password-attachments',
             'refresh-final-r2-limits', 'refresh-final-r2-worker'):
    result = json.loads((delivery / 'validation' / (name + '-result.json')).read_text())
    assert result['exit-code'] == (1 if name == 'inventory-readiness-final-r1' else 0), (name, result['exit-code'])
    assert driver.sha256(root / result['log']['path']) == result['log']['sha256']
    gates[name] = result
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
assert reuse['identities'] == identities
changed = subprocess.check_output(['git', 'diff', '--name-only', baseline], text=True).splitlines()
assert not any(name.startswith(('pdf-document/src/main/', 'pdf-provider-contract/src/main/', 'pdf-conversion/src/main/',
                                'pdf-migration-itext7/src/main/', 'pdf-migration-itext7-preview/src/main/')) for name in changed)
assert not any(name.endswith('pom.xml') for name in changed)
assert not any(name.startswith(('capabilities/evidence/foundation/T81-', 'capabilities/evidence/T81-delivery/')) for name in changed)
assert not subprocess.check_output(['git', 'diff', '--name-only', '--cached'], text=True).strip()
head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
assert head == baseline
status = subprocess.check_output(['git', 'status', '--short', '--branch'], text=True)
output = {'baseline': baseline, 'final-head': head, 'status': status, 'tracked-changed-files': changed,
          'candidate-identity': identities['Candidate'], 'contract-identity': identities['Contract'],
          'current-index': driver.reference(root, root / 'capabilities/foundation-evidence.yaml'),
          'certifications': len(observations), 'chain-records': 392, 'satisfied-obligations': list(selected),
          'previously-satisfied-obligations-preserved': previously_satisfied,
          'remaining-unrelated-blockers': [{'obligation': name, 'issue': int(issue), 'reason': reason}
                                          for name, issue, reason in blocked],
          'worker-certifications': workers, 'gates': gates, 'refresh-attempts': refresh_audit,
          'verified-retained-archives': archive_audit,
          'shipping-source-or-build-compatibility-changed': False,
          'commit': None, 'publication': None}
path = delivery / 'final-audit.json'
assert not path.exists()
path.write_text(json.dumps(output, indent=2) + '\n')
print('Verified final frozen delivery: 96 certifications, 392 chain records, all13 selected obligations SATISFIED.')
