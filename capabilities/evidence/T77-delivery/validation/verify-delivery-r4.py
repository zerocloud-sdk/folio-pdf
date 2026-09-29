from pathlib import Path
import json, re, hashlib, runpy, yaml, os
root = Path('/workspace/folio-pdf')
base = root / '.build-cache/T77-preflight'
os.environ['FOLIO_FOUNDATION_PYTHON_ROOT'] = str(base / 'foundation-python')
validation = root / 'capabilities/evidence/T77-delivery/validation'
runner = runpy.run_path(str(root / 'scripts/t03-foundation.py'))
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
current = runner['capture_build_inputs'](root, contract)
frozen = json.loads((validation / 'source-inputs-r4.json').read_text())
assert current == frozen, 'Final source/contract inputs changed since the validated freeze'
staged = json.loads((root / 'target/foundation-0.1.0/build-inputs.json').read_text())
assert runner['require_staged_build'](root, contract, root / 'target/foundation-0.1.0/build-inputs.json') == staged
assert frozen['inputs'] == staged['candidate']['inputs'], 'Staged sources differ'
assert frozen['contract-inputs'] == staged['contract-inputs'], 'Staged contracts differ'
index = yaml.safe_load((root / 'capabilities/foundation-evidence.yaml').read_text())
assert index['candidate'] == staged['candidate'], 'Index candidate differs from staged artifacts'
selected = ['incremental', 'transactions', 'values', 'pages', 'metadata', 'annotations', 'text', 'images']
observed = {}
for obligation in selected:
    records = [item for item in index['certifications'] if item['obligation'] == obligation]
    assert len(records) == 8, (obligation, len(records))
    modes = {(item['environment'], item['execution-profile']) for item in records}
    required = {(env['id'], mode) for env in yaml.safe_load((root / contract['environments']).read_text())['profiles'] for mode in ['IN_PROCESS', 'HARDENED_WORKER']}
    assert modes == required, obligation
    directory = root / ('capabilities/evidence/T77-' + obligation + '-r4')
    identities = json.loads((directory / 'identities.json').read_text())
    case = runner['certification_case'](obligation)
    for item in records:
        execution = json.loads((root / item['configuration']['path']).read_text())
        assert execution['candidate-sha256'] == identities['Candidate']
        scope = (root / item['configuration']['path']).parent
        assert 'OK (' + str(case['test-count']) + ' tests)' in (scope / 'contract-tests.txt').read_text()
        if obligation == 'incremental':
            for name in ['result.properties', 'products.properties']:
                props = dict(line.split('=', 1) for line in (scope / 'observations' / name).read_text().splitlines() if '=' in line)
                assert props['native-execution-profile'] == item['execution-profile']
                assert props['facade-execution-profile'] == 'IN_PROCESS'
        chains = []
        for ref in item['records']:
            record = json.loads((root / ref['path']).read_text())
            assert record['result'] == 'pass' and record['candidate-sha256'] == identities['Candidate'] and record['contract-sha256'] == identities['Contract']
            report = json.loads((root / record['report']['path']).read_text())
            assert report['result'] == 'pass' and report['negative-controls']
            chains.append(record['chain'])
        assert set(chains) == set(runner['CHAINS'])
    observed[obligation] = {'certifications': len(records), 'consumer-tests-per-tuple': case['test-count'], 'chain-records': 4 * len(records), 'identities': identities}
assert len({(item['identities']['Candidate'], item['identities']['Contract']) for item in observed.values()}) == 1
(validation / 'final-certification-summary.json').write_text(json.dumps(observed, indent=2) + '\n')
print('PASS: source and contract match staged/index candidate; all eight obligations have exactly eight required tuples and 32 passing chain records each')
