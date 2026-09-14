#!/usr/bin/env python3
"""Wait for real complete validation/stage, then collect all six new certifications serially."""
from collections import Counter
from datetime import datetime,timezone
import hashlib,importlib.util,json,os,re,shutil,subprocess,sys,tempfile,time
from pathlib import Path
ROOT=Path('/home/ubuntu/IdeaProjects/open-pdf');os.chdir(ROOT)
LIVE=ROOT/'capabilities/evidence/T75-foundation-20260914-r4'
DEST=ROOT/'capabilities/evidence/T75-delivery/final-certification-r4'
COUNTS={'text':126,'transactions':34,'values':83,'pages':65,'metadata':73,'annotations':47}
CONTRACT='3de508f79fcbef7258feee8abc013d6f02b000840cac1cb263cb0d6f927b8f2b'
OLD_CANDIDATE='e2dd9ebbeae5668ccc8e2985ccd8af17c8921d1ec5e12e0c913cbd1fa00dfbd6'
waiting=Path('/tmp/t75-final-validation-stage-queue-r4-result.json')
print('Waiting for actual complete host/matrix/stage queue result; no certification will start before exit0.',flush=True)
while True:
    try:
        result=json.loads(waiting.read_bytes());break
    except (FileNotFoundError,json.JSONDecodeError):time.sleep(5)
assert result['returncode']==0,result
assert not LIVE.exists() and not DEST.exists()
DEST.mkdir()
frozen=json.loads((ROOT/'capabilities/evidence/T75-delivery/final-candidate-r4/build-inputs.json').read_bytes())
assert json.loads((ROOT/'capabilities/evidence/T75-delivery/final-candidate-r4/freeze.json').read_bytes())['matches-complete-host-and-four-jdk-source-contract-inputs']
env=dict(os.environ);env['FOLIO_HARFBUZZ_HELPER']=str(ROOT/'.build-cache/harfbuzz/10.2.0-final/bin/folio-harfbuzz')
spec=importlib.util.spec_from_file_location('r4_command_retention',ROOT/'capabilities/evidence/T75-delivery/recorder-r4/retain-programs.py');retain=importlib.util.module_from_spec(spec);spec.loader.exec_module(retain);retain.destination=DEST
new_candidate=None
for obligation,test_count in COUNTS.items():
    prefix=Path('/tmp/t75-final-'+obligation+'-certify-r4')
    print(datetime.now(timezone.utc).isoformat(),'Starting actual',obligation,flush=True)
    command=[sys.executable,'scripts/t03-foundation.py','certify',str((LIVE/obligation).relative_to(ROOT)),'--obligation',obligation]
    subprocess.run([sys.executable,'-B','/tmp/t75-run-retained.py','--name',str(prefix),'--']+command,cwd=ROOT,env=env,check=True)
    actual=json.loads(Path(str(prefix)+'-result.json').read_bytes());assert actual['returncode']==0
    observed=json.loads((LIVE/obligation/'observed-index.json').read_bytes());assert len(observed['certifications'])==8
    assert observed['candidate']==frozen['candidate']
    ids=json.loads((LIVE/obligation/'identities.json').read_bytes());assert ids['Contract']==CONTRACT and ids['Candidate']!=OLD_CANDIDATE
    if new_candidate is None:new_candidate=ids['Candidate']
    assert ids['Candidate']==new_candidate
    authority=json.loads((ROOT/'capabilities/foundation-evidence.yaml').read_bytes())
    published=Counter(c['obligation'] for c in authority['certifications'])
    for name in published:
        own=json.loads((LIVE/name/'observed-index.json').read_bytes())
        assert published[name]==8
        assert [c for c in authority['certifications'] if c['obligation']==name]==own['certifications']
    tuples=[]
    for major in (8,11,17,21):
        for execution in ('IN_PROCESS','HARDENED_WORKER'):
            scope=LIVE/obligation/('jdk'+str(major)+'-'+execution.lower())
            tests=(scope/'contract-tests.txt').read_text();assert 'OK ('+str(test_count)+' tests)' in tests
            seconds=re.findall(r'^Time: ([0-9.]+)$',tests,re.M);assert len(seconds)==1
            for chain in ('syntax','standards','semantic','visual'):
                record=json.loads((scope/(chain+'.yaml')).read_bytes())
                expected={'obligation':obligation,'execution-profile':execution,'chain':chain,'result':'pass','candidate-sha256':new_candidate,'contract-sha256':CONTRACT}
                assert all(record.get(k)==v for k,v in expected.items())
            tuples.append({'scope':str(scope.relative_to(ROOT)),'formal-tests':test_count,'seconds':float(seconds[0])})
    original=Path(tempfile.mkdtemp(prefix='t75-final-'+obligation+'-certified-r4-'))
    copies=[]
    def copy(source,target):
        data=source.read_bytes()
        with (original/target).open('xb') as out:out.write(data)
        assert (original/target).read_bytes()==data
        copies.append({'original':str(source),'copy':target,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()})
    for suffix,target in (('-command.json','command.json'),('-result.json','result.json'),('.log','command.log')):copy(Path(str(prefix)+suffix),target)
    for source in (Path('/tmp/t75-run-retained.py'),Path(__file__)):copy(source,source.name)
    for name in ('observed-index.json','identities.json','prior-index.sha256'):copy(LIVE/obligation/name,name)
    copy(ROOT/'capabilities/foundation-evidence.yaml','authority-at-retention.yaml')
    receipt={'recorded-utc':datetime.now(timezone.utc).isoformat(),'obligation':obligation,'candidate':new_candidate,'contract':CONTRACT,'actual-result':actual,'actual-exit':0,'observed-tuples':8,'matching-pass-records':32,'published-subsets-exactly-match-own-observed-indexes':True,'authority-counts':dict(published),'authority-snapshot-scope':'Dated observation after this command finished and before this queue starts the next command.','tuples':tuples,'original-copies':copies,'scope':'Actual command and publication retention; independent review and final inventory/delivery remain separate.'}
    with (original/'receipt.json').open('x') as out:out.write(json.dumps(receipt,indent=2)+'\n')
    name=obligation+'-command';assert not (DEST/(name+'.tar.xz')).exists()
    identity=retain.archive(name,original)
    with (DEST/(name+'-archive-identity.json')).open('x') as out:out.write(json.dumps(identity,indent=2)+'\n')
    for source,suffix in (('command.json','-command.json'),('result.json','-result.json'),('command.log','-command.log'),('receipt.json','-receipt.json')):
        with (DEST/(obligation+suffix)).open('xb') as out:out.write((original/source).read_bytes())
    with Path('/tmp/t75-final-'+obligation+'-certified-r4-original-path.txt').open('x') as out:out.write(str(original)+'\n')
    print(datetime.now(timezone.utc).isoformat(),obligation,'actual exit0;8tuples/32PASS; originals retained',original,flush=True)
assert published=={name:8 for name in COUNTS}
print('All six actual r4 certification commands completed successfully:48tuples,192PASS records on',new_candidate,flush=True)
print('Final inventory, independent review and authorized delivery remain required.',flush=True)
