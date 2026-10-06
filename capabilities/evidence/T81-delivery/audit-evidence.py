import hashlib, importlib.util, json, os, re
from pathlib import Path
import yaml

root=Path('/workspace/folio-pdf')
delivery=root/'capabilities/evidence/T81-delivery'
spec=importlib.util.spec_from_file_location('foundation_driver',root/'scripts/t03-foundation.py')
module=importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
contract=yaml.safe_load((root/'capabilities/foundation-release.yaml').read_text())
staged=module.require_staged_build(root,contract,root/'target/foundation-0.1.0/build-inputs.json')
index=json.loads((root/'capabilities/foundation-evidence.yaml').read_text())
assert index['candidate']==staged['candidate']
identities=module.candidate_identities(root,root/'capabilities/foundation-evidence.yaml',index,delivery/'validation/final-audit-identities.txt')
sequence=['transactions','values','pages','metadata','annotations','text','images','incremental',
          'password-baseline','password-clear-metadata','password-attachments','limits']
profiles=yaml.safe_load((root/contract['environments']).read_text())['profiles']
expected={(name,profile['id'],mode) for name in sequence for profile in profiles
          for mode in module.certification_case(name).get('execution-profiles',('IN_PROCESS','HARDENED_WORKER'))}
assert len(expected)==92
actual={(c['obligation'],c['environment'],c['execution-profile']) for c in index['certifications']}
assert actual==expected and len(index['certifications'])==92
empty={'schema-version':index['schema-version'],'candidate':index['candidate'],
       'environments':index['environments'],'certifications':[]}
fully_verified=module.merge_evidence(root,index,empty,identities)
assert fully_verified['certifications']==index['certifications'], 'Changed or incomplete transitive evidence'
hashes={}
def digest(path):
    path=path.resolve(strict=True); path.relative_to(root)
    if path not in hashes: hashes[path]=hashlib.sha256(path.read_bytes()).hexdigest()
    return hashes[path]
def verify(ref):
    assert digest(root/ref['path'])==ref['sha256'],ref['path']
    return ref

environments={e['profile']:e for e in index['environments']}
assert len(environments)==4
for e in environments.values(): verify(e['record'])
certifications=[]
record_count=0
for c in index['certifications']:
    case=module.certification_case(c['obligation'])
    verify(c['configuration'])
    config=json.loads((root/c['configuration']['path']).read_text())
    env=json.loads((root/environments[c['environment']]['record']['path']).read_text())
    assert config['candidate-sha256']==identities['Candidate']
    assert config['environment-sha256']==environments[c['environment']]['record']['sha256']
    for ref in config['inputs']:verify(ref)
    chains=[]
    for ref in c['records']:
        verify(ref)
        r=json.loads((root/ref['path']).read_text())
        assert r['result']=='pass' and r['candidate-sha256']==identities['Candidate']
        assert r['contract-sha256']==identities['Contract']
        assert r['execution-profile']==c['execution-profile']
        assert r['execution-configuration-sha256']==c['configuration']['sha256']
        assert r['environment-sha256']==environments[c['environment']]['record']['sha256']
        assert r['producer']==module.certification_producers(case)[r['chain']]
        verify(r['configuration']);verify(r['report'])
        report=json.loads((root/r['report']['path']).read_text())
        assert report['result']=='pass' and report['chain']==r['chain']
        assert report['products'] or r['chain']=='contract'
        assert report['findings'] and report['negative-controls']
        for key in ('products','findings','negative-controls','environment-observations'):
            for finding in report.get(key,[]):verify(finding)
        for control in r['negative-controls']:verify(control)
        chains.append({'chain':r['chain'],'producer':r['producer'],'record':ref,'report':r['report']})
        record_count+=1
    assert {r['chain'] for r in chains}==set(case.get('chains',module.CHAINS))
    scope=(root/c['configuration']['path']).parent
    transcript=(scope/'contract-tests.txt').read_text()
    assert re.search(r'(?m)^OK \('+str(case['test-count'])+r' tests\)\s*$',transcript)
    assert 'FAILURES!!!' not in transcript
    certifications.append({**c,'candidate-sha256':identities['Candidate'],'contract-sha256':identities['Contract'],
        'actual-environment':env['identity'],'native-engine':env['native-engine'],'chain-identities':chains,
        'public-test-count':case['test-count']})
assert record_count==372
sys_path=str(root/'scripts')
import sys
sys.path.insert(0,sys_path)
import t20_foundation_reports as reports
cp=':'.join('/workspace/'+p.relative_to(root).as_posix() for p in module.certification_classpath(root,contract,staged))
helper=Path(os.environ['FOLIO_HARFBUZZ_HELPER']).resolve(strict=True)
cli_gate=json.loads((delivery/'validation/collect-final-cli-r2-result.json').read_text())
assert cli_gate['exit-code']==0
verify(cli_gate['log'])
cli_reports=json.loads((root/cli_gate['log']['path']).read_text())
assert set(cli_reports)==set(reports.CHAINS)
for chain,report in cli_reports.items():
    assert report['chain']==chain and report['result']=='pass'
    for key in ('products','findings','negative-controls','environment-observations'):
        for item in report.get(key,[]):verify(item)
limits=[]
for c in [c for c in certifications if c['obligation']=='limits']:
    scope=(root/c['configuration']['path']).parent
    env=json.loads((root/environments[c['environment']]['record']['path']).read_text())
    plan=module.execution_plan(root,scope,env['identity']['image'],helper,cp,module.certification_case('limits'),'IN_PROCESS')
    reports.require_contract_tests(scope,plan)
    original=reports.RetainedFiles(scope/'observations')
    closed=reports.closed_observations(root,original)
    assert len(closed['cases'])==75
    original.verify()
    replays=list(scope.glob('live-collection-*'))
    assert replays
    original_environments=scope.parent/('jdk'+str(env['identity']['jdk-major'])+'-environment')
    indexed_replay=[p for p in replays if any(
        item['path'].startswith(str(p.relative_to(root))+'/')
        for record in c['chain-identities']
        for item in json.loads((root/record['report']['path']).read_text()).get('environment-observations',[]))]
    assert len(indexed_replay)==1
    cli_replays=[p for p in replays if any(
        item['path'].startswith(str(p.relative_to(root))+'/')
        for report in cli_reports.values()
        for item in report.get('environment-observations',[]))]
    if env['identity']['jdk-major']==17:
        assert len(cli_replays)==1
    else:
        assert not cli_replays
    required_replays=sorted(set(indexed_replay+cli_replays))
    expected_observers={str(path.relative_to(root)) for directory in
        (original_environments,indexed_replay[0]/'environment-before',indexed_replay[0]/'environment-after')
        for path in directory.iterdir() if path.is_file()}
    assert len(expected_observers)==31
    for record in c['chain-identities']:
        report=json.loads((root/record['report']['path']).read_text())
        observed=report['environment-observations']
        assert len(observed)==31 and {item['path'] for item in observed}==expected_observers
        assert not expected_observers & {item['path'] for item in report['findings']}
    observations=[]
    for replay in required_replays:
        live_plan=module.execution_plan(root,replay,env['identity']['image'],helper,cp,module.certification_case('limits'),'IN_PROCESS')
        reports.require_contract_tests(replay,live_plan)
        fresh=reports.RetainedFiles(replay/'observations');fresh.verify()
        reports.closed_observations(root,fresh)
        pngs={path.relative_to(original.directory).as_posix() for path in original.expected if path.suffix=='.png'}
        assert len(pngs)==46
        expected_comparisons={replay/('compare-'+hashlib.sha256(name.encode()).hexdigest()[:16]+'.json'):
                              name for name in pngs}
        comparisons=set(replay.glob('compare-*.json'))
        assert comparisons==set(expected_comparisons)
        for path,name in expected_comparisons.items():
            comparison=json.loads(path.read_text())
            assert comparison['exit-code']==0 and not comparison['stdout'].strip()
            assert re.fullmatch(r'0(?:\.0+)?(?:\s+\(0(?:\.0+)?\))?\s*',comparison['stderr'])
            assert comparison['original']==original.reference(root,original.directory/name)
            assert comparison['replay']==fresh.reference(root,fresh.directory/name)
            verify(comparison['original']);verify(comparison['replay'])
            command=live_plan['recorder-command'][:live_plan['recorder-command'].index('java')]
            command+=['/workspace/scripts/container-bin/imagemagick','compare','-metric','AE','-fuzz','0%',
                      '/workspace/'+str((original.directory/name).relative_to(root)),
                      '/workspace/'+str((fresh.directory/name).relative_to(root)),
                      '/workspace/'+str(path.with_suffix('.png').relative_to(root))]
            assert comparison['command']==command
        observations.append({'directory':str(replay.relative_to(root)),'raster-comparison-count':len(comparisons),
                             'contract-tests':module.reference(root,replay/'contract-tests.txt')})
    limits.append({**c,'closed-case-count':75,'original-public-tests':module.reference(root,scope/'contract-tests.txt'),
        'original-retained-manifest':module.reference(root,scope/'observations/retained-files.sha256'),
        'actual-facade-observations':module.reference(root,scope/'observations/facade-observations.properties'),
        'live-replays':observations,
        'historical-unindexed-replays':[str(p.relative_to(root)) for p in replays if p not in required_replays]})
result={'status':'pass','baseline-and-head':'05e7f546f5885680e333ad8d3dea3645b25d26b7',
    'candidate-sha256':identities['Candidate'],'foundation-contract-sha256':identities['Contract'],
    'execution-contract-sha256':digest(delivery/'execution-contract.md'),
    't20-authority-pin':module.reference(root,root/'scripts/t20-evidence-pin.properties'),
    'current-evidence-index':module.reference(root,root/'capabilities/foundation-evidence.yaml'),
    'staged-build-inputs':module.reference(root,root/'target/foundation-0.1.0/build-inputs.json'),
    'candidate-input-count':len(staged['candidate']['inputs']),'artifact-count':len(staged['candidate']['artifacts']),
    'harness-input-count':len(staged['harness']),'certification-count':92,'chain-record-count':372,
    'verified-distinct-reference-count':len(hashes),'refresh-order':sequence,
    'environments':[json.loads((root/e['record']['path']).read_text()) for e in environments.values()],
    'limits-certifications':limits,'all-certifications':certifications}
(delivery/'identity-summary.json').write_text(json.dumps(result,indent=2)+'\n')
print('PASS: 92 exact current scopes, 372 chain records, four strict T20 original/live certifications')
