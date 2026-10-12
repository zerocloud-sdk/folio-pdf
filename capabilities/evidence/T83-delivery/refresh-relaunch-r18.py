"""Fresh verification and all fourteen certifications for the frozen candidate9."""
import datetime
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys

import yaml

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('t83_relaunch_refresh', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
build = root / 'target/foundation-0.1.0/build-inputs.json'
authority = root / 'capabilities/foundation-evidence.yaml'
frozen_path = delivery / 'source-freeze-final-r9.json'
frozen = json.loads(frozen_path.read_text())
inputs = {key: frozen[key] for key in ('inputs', 'contract-inputs')}
receipt = driver.require_staged_build(root, contract, build)
assert receipt == json.loads((delivery / 'candidate-final-r9/build-inputs.json').read_text())
assert driver.capture_build_inputs(root, contract) == inputs
assert receipt['candidate'] != json.loads((delivery / 'candidate-final-r8/build-inputs.json').read_text())['candidate']
preservation = json.loads((delivery / 'relaunch-preservation.json').read_text())
retained = preservation['retained'] + frozen['superseded-candidate6'] + frozen['superseded-candidate7'] + frozen['superseded-candidate8'] + [driver.reference(root, path) for path in (
    frozen_path, build, delivery / 'candidate-final-r9/build-inputs.json', Path(__file__),
    delivery / 'relaunch-environment.sh', delivery / 'relaunch-preservation.json')]
index_hash = driver.sha256(authority)
assert index_hash == driver.sha256(delivery / 'refresh-final-r16-prior-index.yaml')
# The failed predecessor remains immutable; diagnostics export no qualification.
diagnostic_spec = importlib.util.spec_from_file_location('t83_barcode_archive',
    delivery / 'diagnostics/barcode-elapsed-preservation-r1.py')
diagnostic = importlib.util.module_from_spec(diagnostic_spec)
diagnostic_spec.loader.exec_module(diagnostic)
diagnostic_summary = diagnostic.validate()
sealed_path = diagnostic.base / 'sealed-diagnostic.json'
assert diagnostic_summary == json.loads(sealed_path.read_text())
seal_gate_path = delivery / 'validation/seal-barcode-elapsed-diagnostics-r1-result.json'
seal_gate = json.loads(seal_gate_path.read_text())
assert seal_gate['exit-code'] == 0 and seal_gate['log'] == driver.reference(root, root / seal_gate['log']['path'])
retained += [driver.reference(root, p) for p in (sealed_path, seal_gate_path,
    root / seal_gate['log']['path'], delivery / 'diagnostics/barcode-elapsed-preservation-r1.py')]
original, replay, replay_path, replay_gate = diagnostic.observations()
retained += original['original-files'] + original['retained-inputs']
retained += [driver.reference(root, replay_path), driver.reference(root, replay_gate)]
retained += [replay[k] for k in ('probe', 'helper-source', 'compile-log', 'original-report', 'loaded-classpath-files')]
retained += replay['helper-classes'] + [row['log'] for row in replay['results']]
archive_manifest = json.loads((diagnostic.base / 'original-loaded-classpath.zip-parts.json').read_text())
retained += [driver.reference(root, diagnostic.base / 'original-loaded-classpath.zip-parts.json')]
retained += [driver.reference(root, diagnostic.base / part['path']) for part in archive_manifest['parts']]
unicode_spec = importlib.util.spec_from_file_location('t83_unicode_archive',
    delivery / 'diagnostics/unicode-elapsed-preservation-r2.py')
unicode_diagnostic = importlib.util.module_from_spec(unicode_spec)
unicode_spec.loader.exec_module(unicode_diagnostic)
unicode_summary = unicode_diagnostic.validate()
assert unicode_summary['passing-exact-method-replays'] == 3
retained += unicode_summary['retained-inputs']
negative_gate_path = delivery / 'validation/unicode-preservation-negative-guards-r3-result.json'
negative_gate = json.loads(negative_gate_path.read_text())
assert negative_gate['exit-code'] == 0 and negative_gate['log'] == driver.reference(root, root / negative_gate['log']['path'])
negative_path = delivery / 'validation/unicode-preservation-r3.json'
negative = json.loads(negative_path.read_text())
assert negative['actual-preservation-passed'] and negative['originals-unchanged']
assert negative['certifications-exported'] == 0
assert negative['validator'] == driver.reference(root, delivery / 'diagnostics/unicode-elapsed-preservation-r2.py')
assert negative['checker'] == driver.reference(root, delivery / 'validate-unicode-preservation-r3.py')
assert {item['case'] for item in negative['negative-cases']} == {'altered-ledger', 'resealed-log', 'resealed-report', 'omitted-prior-witness'}
assert all(item['rejected'] and item['isolated-copy'] for item in negative['negative-cases'])
retained += [driver.reference(root, negative_gate_path), negative_gate['log'], driver.reference(root, negative_path), negative['validator'], negative['checker']]
for axis in ('standards', 'spec'):
    review = delivery / 'reviews' / (axis + '-relaunch-r10.md')
    assert review.is_file()
    retained.append(driver.reference(root, review))
cleanup_name = 'pre-r18-container-check'
cleanup_process = subprocess.run([sys.executable, str(delivery / 'run-gate.py'),
    cleanup_name, 'host', 'podman', '--log-level=error', 'ps', '--format', '{{.ID}} {{.Names}} {{.Status}}'], cwd=root)
cleanup_gate_path = delivery / 'validation' / (cleanup_name + '-result.json')
cleanup_gate = json.loads(cleanup_gate_path.read_text())
assert cleanup_gate['exit-code'] == cleanup_process.returncode == 0
assert cleanup_gate['log'] == driver.reference(root, root / cleanup_gate['log']['path'])
assert not (root / cleanup_gate['log']['path']).read_text().strip(), 'Inspect containers; leave unrelated ones untouched'
retained += [driver.reference(root, cleanup_gate_path), cleanup_gate['log']]
new_prior = delivery / 'refresh-final-r18-prior-index.yaml'
assert not new_prior.exists()
shutil.copyfile(authority, new_prior)
retained.append(driver.reference(root, new_prior))
selected = ('transactions', 'values', 'pages', 'metadata', 'annotations', 'text', 'images',
            'incremental', 'password-baseline', 'password-clear-metadata', 'password-attachments',
            'limits', 'worker', 'recovery')
record_dir = delivery / 'refresh-final-r18'
output_base = root / 'capabilities/evidence/foundation/T83-final-r17'
assert not record_dir.exists() and not output_base.exists()
record_dir.mkdir()
driver.write_json(record_dir / 'preflight.json', {
    'candidate': receipt['candidate'], 'source-freeze': driver.reference(root, frozen_path),
    'refresh-driver': driver.reference(root, Path(__file__)), 'retained-inputs': retained,
    'starting-index': driver.reference(root, authority), 'selected-obligations': selected,
    'diagnostics-exported-certifications': 0, 'reuse-of-superseded-certifications': False,
    'failed-full-verify': driver.reference(root, delivery / 'validation/full-verify-final-r10-result.json'),
    'sealed-exact-method-diagnostics': diagnostic_summary,
    'unicode-diagnostics': unicode_summary,
    'unicode-preservation-negatives': driver.reference(root, negative_path),
    'retry-disposition': 'Barcode error did not reproduce; Unicode replay failed then three fresh unchanged runs passed with paired timers. No code or policy change or causal fix; fresh full verification is mandatory.'})


def current(expected_index):
    driver.require_staged_build(root, contract, build)
    assert driver.capture_build_inputs(root, contract) == inputs
    assert all(item == driver.reference(root, root / item['path']) for item in retained)
    assert driver.sha256(authority) == expected_index


verification = []
for name, mode, arguments in (
        ('full-verify-final-r12', 'ubuntu', ['./mvnw', '-B', '-ntp', 'verify']),
        ('jdk-matrix-final-r5', 'host', ['./scripts/verify-jdk-matrix.sh']),
        ('standalone-scale-final-r8', 'host', ['python3', '-B',
            'capabilities/evidence/T83-delivery/standalone-scale.py', 'scale-final-r8'])):
    current(index_hash)
    command = [sys.executable, str(delivery / 'run-gate.py'), name, mode, *arguments]
    process = subprocess.run(command, cwd=root)
    gate_path = delivery / 'validation' / (name + '-result.json')
    gate = json.loads(gate_path.read_text())
    assert gate['exit-code'] == process.returncode
    assert gate['log'] == driver.reference(root, root / gate['log']['path'])
    current(index_hash)
    verification.append({'gate': name, 'exit-code': process.returncode,
        'source-staged-index-unchanged': True, 'result': driver.reference(root, gate_path)})
    driver.write_json(delivery / 'candidate-final-r9-verification-r18.json', verification)
    if process.returncode:
        print('Stopped at failed verification; no certification began: ' + name, flush=True)
        sys.exit(process.returncode)

output_base.mkdir()
results = []
for obligation in selected:
    current(index_hash)
    prior = record_dir / (obligation + '-prior-index.yaml')
    shutil.copyfile(authority, prior)
    destination = output_base / obligation
    command = [sys.executable, str(delivery / 'run-gate.py'), 'refresh-final-r18-' + obligation,
               'host', 'python3', '-B', 'scripts/t03-foundation.py', 'certify',
               destination.relative_to(root).as_posix(), '--obligation', obligation]
    started = datetime.datetime.now(datetime.timezone.utc).isoformat()
    print('Fresh candidate9 certification: ' + obligation + ' at ' + started, flush=True)
    process = subprocess.run(command, cwd=root)
    index = yaml.safe_load(authority.read_text())
    gate_path = delivery / 'validation' / ('refresh-final-r18-' + obligation + '-result.json')
    gate = json.loads(gate_path.read_text())
    assert gate['exit-code'] == process.returncode
    assert gate['log'] == driver.reference(root, root / gate['log']['path'])
    row = {'obligation': obligation, 'started': started,
        'finished': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'command': command, 'exit-code': process.returncode,
        'gate-result': driver.reference(root, gate_path), 'prior-index': driver.reference(root, prior),
        'current-index-sha256': driver.sha256(authority),
        'certifications': len(index['certifications']),
        'chain-records': sum(len(item['records']) for item in index['certifications'])}
    results.append(row)
    driver.write_json(record_dir / 'results.json', results)
    if process.returncode:
        assert row['current-index-sha256'] == index_hash == row['prior-index']['sha256']
        current(index_hash)
        print('Stopped at failed certification; partial observations remain unindexed: ' + obligation, flush=True)
        sys.exit(process.returncode)
    index_hash = row['current-index-sha256']
    current(index_hash)
    completed = {item['obligation'] for item in results}
    expected = {(item['id'], environment, execution)
                for item in contract['obligations'] if item['id'] in completed
                for environment in item['environments'] for execution in item['execution-profiles']}
    assert index['candidate'] == receipt['candidate']
    assert {(item['obligation'], item['environment'], item['execution-profile'])
            for item in index['certifications']} == expected
    assert all(item['configuration']['path'].startswith(
        output_base.relative_to(root).as_posix() + '/' + item['obligation'] + '/')
        for item in index['certifications'])
    chains = sum(len(item['chains']) * len(item['environments']) * len(item['execution-profiles'])
                 for item in contract['obligations'] if item['id'] in completed)
    assert row['chain-records'] == chains
assert len(index['certifications']) == 100 and results[-1]['chain-records'] == 412
print('Candidate9 freshly certifies all fourteen obligations: 100 tuples / 412 chains, including four recovery tuples / 20 chains.', flush=True)
