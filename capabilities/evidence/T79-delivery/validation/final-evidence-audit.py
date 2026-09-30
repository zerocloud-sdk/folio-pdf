"""Verify delivery identities and every required local gate before issuing a receipt."""
import collections
import datetime
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import yaml

ROOT = Path('/workspace/folio-pdf')
OUT = ROOT / 'capabilities/evidence/T79-delivery'
VALIDATION = OUT / 'validation'
BASELINE = '7420a656d27d17b82c7632be7c6214f8a657a22f'
ORDER = ['transactions', 'values', 'pages', 'metadata', 'annotations', 'text', 'images',
         'incremental', 'password-baseline', 'password-clear-metadata']
CHAINS = {'syntax', 'standards', 'semantic', 'visual'}
os.environ['FOLIO_FOUNDATION_PYTHON_ROOT'] = str(ROOT / '.build-cache/foundation-python')


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def reference(path):
    return {'path': path.relative_to(ROOT).as_posix(), 'sha256': digest(path)}


def checked(item):
    path = ROOT / item['path']
    assert digest(path) == item['sha256'], item['path']
    return json.loads(path.read_text())


def properties(path):
    return dict(line.split('=', 1) for line in path.read_text().splitlines() if line and not line.startswith(('#', '!')))


assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip() == BASELINE
subprocess.run(['git', 'diff', '--check'], cwd=ROOT, check=True)
contract = yaml.safe_load((ROOT / 'capabilities/foundation-release.yaml').read_text())
spec = importlib.util.spec_from_file_location('foundation', ROOT / 'scripts/t03-foundation.py')
foundation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(foundation)
freeze = json.loads((OUT / 'source-freeze.json').read_text())
assert freeze['comparison-baseline'] == BASELINE and freeze['execution-starting-head'] == BASELINE
assert foundation.capture_build_inputs(ROOT, contract) == {key: freeze[key] for key in ['inputs', 'contract-inputs']}
staged = foundation.require_staged_build(ROOT, contract, ROOT / 'target/foundation-0.1.0/build-inputs.json')
assert staged == json.loads((VALIDATION / 'staged-candidate.json').read_text())
index_path = ROOT / 'capabilities/foundation-evidence.yaml'
index = json.loads(index_path.read_text())
assert index['candidate'] == staged['candidate']
identities = json.loads((ROOT / 'capabilities/evidence/foundation/T79-final/password-clear-metadata/identities.json').read_text())
environments = {item['profile']: checked(item['record']) for item in index['environments']}
profiles = {'ubuntu-24.04-linux-x86-64-jdk' + str(jdk) for jdk in (8, 11, 17, 21)}
assert set(environments) == profiles
groups = collections.defaultdict(list)
for item in index['certifications']:
    groups[item['obligation']].append(item)
assert set(groups) == set(ORDER) and len(index['certifications']) == 80
summaries = []
selection_path = VALIDATION / 'certification-selection.json'
selection = json.loads(selection_path.read_text())
assert set(selection) == set(ORDER)
diagnosis_path = VALIDATION / 'diagnosis-text-timeouts/disposition.json'
diagnosis = json.loads(diagnosis_path.read_text())
assert diagnosis['result'] == 'unchanged-replays-pass'
assert digest(ROOT / diagnosis['original-failure']['path']) == diagnosis['original-failure']['sha256']
for item in diagnosis['passing-replays']:
    assert checked(item)['exit-code'] == 0
assert checked(diagnosis['identity-check'])['result'] == 'pass'
assert checked(diagnosis['environment-check'])['result'] == 'pass'
for obligation in ORDER:
    directory = ROOT / selection[obligation]['directory']
    assert json.loads((directory / 'identities.json').read_text()) == identities
    entries = groups[obligation]
    assert len(entries) == 8
    assert {(x['environment'], x['execution-profile']) for x in entries} == {
        (profile, mode) for profile in profiles for mode in ('IN_PROCESS', 'HARDENED_WORKER')}
    tuples = []
    for entry in entries:
        assert entry['configuration']['path'].startswith(selection[obligation]['directory'] + '/')
        configuration = checked(entry['configuration'])
        assert configuration['candidate-sha256'] == identities['Candidate']
        assert configuration['execution-profile'] == entry['execution-profile']
        records = [checked(item) for item in entry['records']]
        assert len(records) == 4 and {item['chain'] for item in records} == CHAINS
        for record in records:
            assert record['result'] == 'pass' and record['obligation'] == obligation
            assert record['candidate-sha256'] == identities['Candidate'] and record['contract-sha256'] == identities['Contract']
            assert record['execution-configuration-sha256'] == entry['configuration']['sha256']
            assert record['execution-profile'] == entry['execution-profile']
            report = checked(record['report'])
            assert report['result'] == 'pass' and report['products'] and report['findings'] and report['negative-controls']
            if obligation in ('password-baseline', 'password-clear-metadata'):
                size = 68 if obligation == 'password-baseline' else 78
                assert len(report['products']) == len({item['path'] for item in report['products']}) == size
                for item in report['products']:
                    assert digest(ROOT / item['path']) == item['sha256']
        scope = (ROOT / entry['configuration']['path']).parent
        count = int(re.search(r'OK \((\d+) tests\)', (scope / 'contract-tests.txt').read_text()).group(1))
        assert count == foundation.certification_case(obligation)['test-count']
        row = {'environment': entry['environment'], 'native-execution-profile': entry['execution-profile'],
            'contract-tests': count, 'chains': dict.fromkeys(sorted(CHAINS), 'pass'),
            'configuration': entry['configuration'], 'records': entry['records']}
        if obligation in ('password-baseline', 'password-clear-metadata'):
            declaration = properties(scope / 'observations/result.properties')
            assert declaration['native-execution-profile'] == entry['execution-profile']
            assert declaration['facade-execution-profile'] == 'IN_PROCESS'
            assert all(declaration[chain] == 'pass' for chain in CHAINS)
            assert count == (44 if obligation == 'password-baseline' else 23)
            coverage = json.loads((scope / 'observations/coverage.json').read_text())
            if obligation == 'password-clear-metadata':
                assert len(coverage['products']) == 39 and len(coverage['original-inputs']) == 37
                assert len(coverage['required-security-rules']) == len(coverage['applied-security-rules']) == 117
                assert len(coverage['retained-baseline-rules']) == 95
            row.update({'facade-execution-profile': 'IN_PROCESS', 'coverage': reference(scope / 'observations/coverage.json')})
        tuples.append(row)
    summaries.append({'obligation': obligation, 'result': 'pass', 'certifications': 8,
        'independent-chain-records': 32, 'identities': reference(directory / 'identities.json'), 'tuples': tuples})

matrix = yaml.safe_load((ROOT / 'capabilities/capability-matrix.yaml').read_text())
caps = {item['id']: item for item in matrix['capabilities']}
aggregate = caps['document.version-password-security']
child = caps['document.version-password-security.clear-metadata']
assert aggregate['status'] == 'experimental' and child['status'] == 'compatible'
assert caps['document.version-password-security.baseline']['status'] == 'compatible'
assert child['parent-capability'] == aggregate['id']
assert all(gate in child['dependency-gates'] for gate in aggregate['dependency-gates'])
assert all(gate['capability'] != aggregate['id'] for gate in child['dependency-gates'])
obligations = {item['id']: item for item in contract['obligations']}
assert obligations['password-clear-metadata']['dependencies'] == ['password-baseline']
members = [member for family in obligations['password-clear-metadata']['facade-families'] for member in family['mappings']]
assert len(members) == len(set(members)) == 62
assert obligations['security']['members'] == ['password-baseline', 'password-clear-metadata', 'password-attachments']
assert obligations['password-attachments']['slice'] == 80
readiness_path = VALIDATION / 'inventory-final-readiness.txt'
readiness = readiness_path.read_text()
assert 'Foundation 0.1.0: NOT READY' in readiness
assert 'Candidate identity: ' + identities['Candidate'] in readiness
assert 'Contract identity: ' + identities['Contract'] in readiness
blockers = [line for line in readiness.splitlines() if line.startswith('BLOCKED ')]
assert blockers and not any(line.startswith('BLOCKED release:') for line in blockers)
assert not any(re.match('BLOCKED ' + re.escape(name) + r' \(', line) for name in ORDER for line in blockers)
remaining = sorted({re.match('BLOCKED ([^ ]+)', line).group(1) for line in blockers})
assert {'security', 'password-attachments'}.issubset(remaining)

ledger = [json.loads(line) for line in (VALIDATION / 'commands.jsonl').read_text().splitlines()]
failed_text = [row for row in ledger if row['name'] == 'certification-text']
assert len(failed_text) == 1 and failed_text[0]['exit-code'] == 1
assert digest(ROOT / failed_text[0]['log']) == failed_text[0]['log-sha256']
required = ['independent-profile-final', 'collector-all-final', 'live-qualification', 'artifact-regression',
    'full-verify-resumed-r3', 'native-baseline-resumed', 'worker-facade-resumed', 'jdk-matrix',
    'inventory-resumed-generate-r2', 'inventory-resumed-regression-r2',
    'certification-stage'] + [selection[name]['command'] for name in ORDER] + [
    'inventory-frozen-generate', 'inventory-frozen-validate', 'inventory-frozen-check',
    'inventory-final-generate', 'inventory-final-validate', 'inventory-final-check']
for name in required:
    rows = [row for row in ledger if row['name'] == name]
    assert len(rows) == 1 and rows[0]['exit-code'] == 0, name
    assert digest(ROOT / rows[0]['log']) == rows[0]['log-sha256']
resume = json.loads((VALIDATION / 'resume-start.json').read_text())
assert resume['head'] == BASELINE
assert resume['contract-sha256'] == digest(Path('/workspace/contracts/issue-79-contract.md'))
interrupted = resume['interrupted-verification']
assert digest(ROOT / interrupted['path']) == interrupted['sha256']
for review in freeze['source-reviews']:
    assert digest(ROOT / review['path']) == review['sha256']
for log, expected_builds in [('full-verify-resumed-r3.txt', 1), ('jdk-matrix.txt', 4)]:
    assert (VALIDATION / log).read_text().count('BUILD SUCCESS') == expected_builds
qualification = (VALIDATION / 'live-qualification.txt').read_text()
assert json.loads((VALIDATION / 'verification-summary.json').read_text())['result'] == 'pass'
live = next(json.loads(line) for line in qualification.splitlines() if line.startswith('{"isolated-controls"'))
assert live['result'] == live['replay'] == 'pass' and live['products'] == 78
assert len(live['isolated-controls']) == 3 and all(x['result'] == 'detected' for x in live['isolated-controls'])
for item in json.loads((VALIDATION / 'live-qualification-source.json').read_text()).values():
    assert digest(ROOT / item['path']) == item['sha256']
assert 'Ran 84 tests' in (VALIDATION / 'collector-all-final.txt').read_text()
assert 'BUILD SUCCESS' in (VALIDATION / 'native-baseline.txt').read_text()
status = subprocess.check_output(['git', 'status', '--short', '--untracked-files=normal'], cwd=ROOT, text=True)
(VALIDATION / 'final-worktree-status.txt').write_text(status)
allowed = json.loads((VALIDATION / 'ticket-paths.json').read_text())
changed = subprocess.check_output(['git', 'diff', '--name-only', BASELINE, '--'], cwd=ROOT, text=True).splitlines()
changed += subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard'], cwd=ROOT, text=True).splitlines()
assert all(name in allowed['source-paths'] or any(name.startswith(prefix) for prefix in allowed['evidence-prefixes'])
           for name in changed), 'An unrelated worktree path appeared; inspect and preserve it'
assert not subprocess.check_output(['git', 'diff', '--cached', '--name-only'], cwd=ROOT, text=True).strip()
record = {'schema-version': 1, 'result': 'pass', 'recorded-at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'comparison-baseline': BASELINE, 'candidate-sha256': identities['Candidate'], 'contract-sha256': identities['Contract'],
    'source-input-count': len(freeze['inputs']), 'contract-input-count': len(freeze['contract-inputs']),
    'source-freeze': reference(OUT / 'source-freeze.json'), 'staged-candidate': reference(VALIDATION / 'staged-candidate.json'),
    'selected-certification-runs': reference(selection_path),
    'text-timeout-diagnosis': reference(VALIDATION / 'diagnosis-text-timeouts/disposition.json'),
    'evidence-index': reference(index_path), 'certifications': 80, 'independent-chain-records': 320,
    'clear-metadata-product-artifacts': 624, 'baseline-product-artifacts': 544, 'facade-mappings': 62,
    'aggregate-status': aggregate['status'], 'clear-metadata-status': child['status'],
    'environments': environments, 'obligations': summaries,
    'readiness': {'result': 'not-ready-outside-selected-scope', 'report': reference(readiness_path),
        'blocker-count': len(blockers), 'remaining-obligations': remaining},
    'validation-ledger': reference(VALIDATION / 'commands.jsonl'), 'live-qualification': live,
    'verification-summary': reference(VALIDATION / 'verification-summary.json'),
    'resume-start': reference(VALIDATION / 'resume-start.json'),
    'restored-environment-probes': reference(VALIDATION / 'resume-environments.json'),
    'interrupted-verification': interrupted,
    'worktree-status': reference(VALIDATION / 'final-worktree-status.txt'), 'optional-local-commit': None}
(VALIDATION / 'final-evidence-audit.json').write_text(json.dumps(record, indent=2) + '\n')
print(json.dumps({key: value for key, value in record.items() if key not in ('environments', 'obligations')}, indent=2))
