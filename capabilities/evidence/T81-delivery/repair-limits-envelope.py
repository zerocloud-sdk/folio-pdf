#!/usr/bin/env python3
"""Correct only raw-observer reference roles around unchanged initial evidence.

The original collector outputs and execution configurations are immutable. The
existing merge checks still verify every retained file hash; only actual live
environment payloads receive the already-defined raw-observation role.
"""
import collections
import copy
import datetime
import hashlib
import importlib.util
import json
from pathlib import Path
import sys

import yaml

ROOT = Path('/workspace/folio-pdf')
sys.path.insert(0, str(ROOT / 'scripts'))
spec = importlib.util.spec_from_file_location('t81_envelope_driver', ROOT / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)


def references(items):
    assert all(set(item) == {'path', 'sha256'} for item in items)
    return collections.Counter((item['path'], item['sha256']) for item in items)


def main(output):
    original = ROOT / 'capabilities/evidence/foundation/T81-initial/limits'
    contract = yaml.safe_load((ROOT / 'capabilities/foundation-release.yaml').read_text())
    build_record = ROOT / 'target/foundation-0.1.0/build-inputs.json'
    staged = driver.require_staged_build(ROOT, contract, build_record)
    case = driver.certification_case('limits')
    declared = next(item for item in contract['obligations'] if item['id'] == 'limits')
    assert set(case['chains']) == set(declared['chains'])
    assert case['execution-profiles'] == ('IN_PROCESS',)
    output.relative_to(ROOT)
    output.mkdir()
    original_files = [driver.reference(ROOT, path) for path in sorted(original.rglob('*')) if path.is_file()]
    driver.write_json(output / 'original-files-before.json', original_files)
    fresh = json.loads((original / 'observed-index.json').read_text())
    original_identities = json.loads((original / 'identities.json').read_text())
    assert fresh['candidate'] == staged['candidate']
    authority = ROOT / 'capabilities/foundation-evidence.yaml'
    previous_bytes = authority.read_bytes()
    previous = json.loads(previous_bytes)
    assert previous['candidate'] == fresh['candidate'] and len(previous['certifications']) == 88
    identities = driver.candidate_identities(ROOT, authority,
        {'schema-version': 1, 'candidate': staged['candidate'], 'environments': [], 'certifications': []},
        output / 'candidate-identity.txt')
    assert identities == original_identities
    expected_scopes = {('limits', environment, 'IN_PROCESS') for environment in declared['environments']}
    original_scopes = {(item['obligation'], item['environment'], item['execution-profile'])
                       for item in fresh['certifications']}
    assert original_scopes == expected_scopes and len(fresh['certifications']) == 4
    environment_refs = {item['profile']: item['record'] for item in fresh['environments']}
    assert set(environment_refs) == set(declared['environments'])
    audit = []
    for certification in fresh['certifications']:
        configuration = certification['configuration']
        assert driver.reference(ROOT, ROOT / configuration['path']) == configuration
        config = json.loads((ROOT / configuration['path']).read_text())
        assert config['candidate-sha256'] == identities['Candidate']
        assert config['environment-sha256'] == environment_refs[certification['environment']]['sha256']
        assert config['execution-profile'] == 'IN_PROCESS'
        assert config['inputs'] == driver.certification_inputs(ROOT, contract, staged, case)
        scope = (ROOT / configuration['path']).parent
        replays = list(scope.glob('live-collection-*'))
        assert len(replays) == 1
        replay = replays[0]
        observer_refs = [driver.reference(ROOT, path)
                         for name in ('environment-before', 'environment-after')
                         for path in (replay / name).iterdir() if path.is_file()]
        assert len(observer_refs) == 20 and len(references(observer_refs)) == 20
        observer_paths = {item['path'] for item in observer_refs}
        corrected_scope = output / scope.name
        corrected_scope.mkdir()
        new_records = []
        seen = set()
        for record_ref in certification['records']:
            assert driver.reference(ROOT, ROOT / record_ref['path']) == record_ref
            record = json.loads((ROOT / record_ref['path']).read_text())
            chain = record['chain']
            assert chain not in seen
            seen.add(chain)
            expected = {'obligation': 'limits', 'execution-profile': 'IN_PROCESS', 'result': 'pass',
                        'candidate-sha256': identities['Candidate'], 'contract-sha256': identities['Contract'],
                        'environment-sha256': environment_refs[certification['environment']]['sha256'],
                        'execution-configuration-sha256': configuration['sha256'],
                        'producer': driver.certification_producers(case)[chain]}
            assert all(record.get(name) == value for name, value in expected.items())
            old_report_ref = record['report']
            assert driver.reference(ROOT, ROOT / old_report_ref['path']) == old_report_ref
            old_report = json.loads((ROOT / old_report_ref['path']).read_text())
            assert old_report['chain'] == chain and old_report['result'] == 'pass'
            moved = [item for item in old_report['findings'] if item['path'] in observer_paths]
            assert references(moved) == references(observer_refs)
            corrected_report = copy.deepcopy(old_report)
            corrected_report['findings'] = [item for item in old_report['findings'] if item['path'] not in observer_paths]
            corrected_report['environment-observations'] = old_report['environment-observations'] + moved
            assert len(old_report['environment-observations']) == 11
            assert references(old_report['findings'] + old_report['environment-observations']) == references(
                corrected_report['findings'] + corrected_report['environment-observations'])
            assert {k: v for k, v in old_report.items() if k not in ('findings', 'environment-observations')} == {
                k: v for k, v in corrected_report.items() if k not in ('findings', 'environment-observations')}
            new_report_path = corrected_scope / (chain + '-report.json')
            driver.write_json(new_report_path, corrected_report)
            corrected_record = {**record, 'report': driver.reference(ROOT, new_report_path)}
            assert {k: v for k, v in corrected_record.items() if k != 'report'} == {
                k: v for k, v in record.items() if k != 'report'}
            new_record_path = corrected_scope / (chain + '.yaml')
            driver.write_json(new_record_path, corrected_record)
            new_records.append(driver.reference(ROOT, new_record_path))
            audit.append({'environment': certification['environment'], 'chain': chain,
                          'original-report': old_report_ref, 'corrected-report': corrected_record['report'],
                          'original-record': record_ref, 'corrected-record': new_records[-1],
                          'moved-references': moved, 'existing-environment-references': old_report['environment-observations'],
                          'combined-reference-multiset-unchanged': True, 'other-report-and-record-fields-unchanged': True})
        assert seen == set(case['chains']) and len(new_records) == 5
        certification['records'] = new_records
    assert len(audit) == 20
    driver.require_unchanged(ROOT, contract, staged['candidate'])
    driver.require_staged_build(ROOT, contract, build_record)
    assert original_files == [driver.reference(ROOT, path) for path in sorted(original.rglob('*')) if path.is_file()]
    assert authority.read_bytes() == previous_bytes
    driver.write_json(output / 'observed-index.json', fresh)
    driver.write_json(output / 'identities.json', identities)
    (output / 'prior-index.sha256').write_text(hashlib.sha256(previous_bytes).hexdigest() + '\n')
    print('Preflighting exact four scopes / five chains with unchanged hash and identity checks', flush=True)
    empty = {'schema-version': 1, 'candidate': fresh['candidate'], 'environments': fresh['environments'], 'certifications': []}
    verified = driver.merge_evidence(ROOT, fresh, empty, identities)
    assert verified['certifications'] == fresh['certifications']
    print('Preflighting preservation of all 88 predecessor scopes', flush=True)
    prepared = driver.merge_evidence(ROOT, previous, fresh, identities)
    previous_scopes = {(item['obligation'], item['environment'], item['execution-profile'])
                       for item in previous['certifications']}
    assert len(prepared['certifications']) == 92
    assert {(item['obligation'], item['environment'], item['execution-profile']) for item in prepared['certifications']} == previous_scopes | expected_scopes
    assert authority.read_bytes() == previous_bytes
    print('Publishing through the unchanged locked, stale-index-guarded route', flush=True)
    merged = driver.publish_index(ROOT, output)
    assert len(merged['certifications']) == 92 and merged['certifications'] == prepared['certifications']
    assert original_files == [driver.reference(ROOT, path) for path in sorted(original.rglob('*')) if path.is_file()]
    driver.require_staged_build(ROOT, contract, build_record)
    driver.write_json(output / 'envelope-repair-audit.json', {'schema-version': 1, 'result': 'pass',
        'recipe': driver.reference(ROOT, Path(__file__)), 'driver': driver.reference(ROOT, ROOT / 'scripts/t03-foundation.py'),
        'identities': identities, 'original-files-unchanged': driver.reference(ROOT, output / 'original-files-before.json'),
        'envelopes': audit, 'limits-scope-count': 4, 'limits-chain-count': 20,
        'retained-predecessor-scope-count': 88, 'published-scope-count': 92,
        'published-index': driver.reference(ROOT, authority)})
    print('Published exactly four limits certifications and preserved all 88 predecessors', flush=True)


if __name__ == '__main__':
    assert sys.argv[1] == 'certify' and sys.argv[3:] == ['--obligation', 'limits']
    started = datetime.datetime.now(datetime.timezone.utc).isoformat()
    status = 1
    try:
        main(ROOT / sys.argv[2])
        status = 0
    finally:
        journal = ROOT / 'capabilities/evidence/T81-delivery/refresh-initial-commands.json'
        events = json.loads(journal.read_text())
        events.append({'obligation': 'limits', 'attempt': 2, 'action': 'initial-envelope-role-repair',
                       'command': [sys.executable, '-B'] + sys.argv,
                       'started': started, 'finished': datetime.datetime.now(datetime.timezone.utc).isoformat(),
                       'exit-code': status, 'repair-recipe': driver.reference(ROOT, Path(__file__))})
        driver.write_json(journal, events)
