"""Refresh the previously certified dependency graph without early index replacement."""
import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import yaml

root=Path(__file__).resolve().parents[3]; delivery=Path(__file__).resolve().parent
sys.path.insert(0,str(root/'scripts'))
import t05_foundation_reports
module=t05_foundation_reports.foundation()
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('output',type=Path); parser.add_argument('--jobs',type=int,default=2)
parser.add_argument('--resume',action='store_true',help='Adopt intact successful observations and retry the failed password run in a fresh directory')
args=parser.parse_args()
if args.jobs not in (1,2): parser.error('Use one or two disjoint observation jobs')
base=(root/args.output).resolve(); base.relative_to(root)
if args.resume: assert base.is_dir()
else: base.mkdir()
contract=yaml.safe_load((root/'capabilities/foundation-release.yaml').read_text())
receipt=module.require_staged_build(root,contract,root/'target/foundation-0.1.0/build-inputs.json')
authority=root/'capabilities/foundation-evidence.yaml'; previous_bytes=authority.read_bytes()
previous=yaml.safe_load(previous_bytes)
selected=[]
for item in previous['certifications']:
 if item['obligation'] not in selected: selected.append(item['obligation'])
assert selected==['transactions','values','pages','metadata','annotations','text','images','incremental','password-baseline','password-clear-metadata','password-attachments','limits','worker','recovery']
requirements={item['id']:set(item['dependencies']) for item in contract['obligations'] if item['id'] in selected}
assert all(dependency in selected for dependencies in requirements.values() for dependency in dependencies)
for obligations in (selected,):
 for obligation in obligations: assert obligation in module.CERTIFICATION_CASES
state={'candidate':receipt['candidate'],'prior-index-sha256':hashlib.sha256(previous_bytes).hexdigest(),'jobs':args.jobs,
       'dependencies':{name:sorted(value) for name,value in requirements.items()},'completed':{},'running':{},'failed':{}}
directories={name:base/name for name in selected}
adopted={}
if args.resume:
 original=json.loads((delivery/'refresh-state.json').read_text())
 assert original['candidate']==receipt['candidate'] and original['prior-index-sha256']==state['prior-index-sha256']
 assert set(original['failed'])=={'password-baseline'}
 for name in selected:
  result_path=delivery/'validation'/('refresh-'+name+'-result.json')
  if not result_path.exists(): continue
  result=json.loads(result_path.read_text())
  if result['exit-code']!=0: continue
  assert module.sha256(root/result['log'])==result['log-sha256']
  observed=json.loads((directories[name]/'observed-index.json').read_text())
  assert observed['candidate']==receipt['candidate']
  assert (directories[name]/'prior-index.sha256').read_text().strip()==state['prior-index-sha256']
  expected={(profile,execution) for profile in next(item for item in contract['obligations'] if item['id']==name)['environments']
            for execution in next(item for item in contract['obligations'] if item['id']==name)['execution-profiles']}
  assert {(item['environment'],item['execution-profile']) for item in observed['certifications']}==expected
  assert all(item['obligation']==name for item in observed['certifications'])
  adopted[name]=result
 assert set(adopted)=={'transactions','values','pages','metadata','annotations'}
 assert all(requirements[name]<=set(adopted) for name in adopted)
 directories['password-baseline']=base/'password-baseline-r2'
state['adopted-original-successes']=sorted(adopted)
state['directories']={name:path.relative_to(root).as_posix() for name,path in directories.items()}
state['completed']=dict(adopted)
state_path=delivery/('refresh-state-resumed-r1.json' if args.resume else 'refresh-state.json')
if state_path.exists(): raise ValueError('Refresh state output must be fresh')
module.write_json(state_path,state)

# Long authoring/standards routes begin as soon as their completed prerequisites permit;
# other siblings keep their original relative ordering. No successor starts early.
priority=['transactions','values','password-baseline','pages','metadata','password-attachments','password-clear-metadata',
          'annotations','text','images','incremental','limits','worker','recovery']

def execute(obligation):
 command=[sys.executable,'-B','scripts/t03-foundation.py','certify',directories[obligation].relative_to(root).as_posix(),'--obligation',obligation,'--defer-index']
 label=obligation+'-r2' if args.resume and obligation=='password-baseline' else obligation
 log=delivery/'validation'/('refresh-'+label+'.txt'); result=log.with_name('refresh-'+label+'-result.json')
 if log.exists() or result.exists(): raise ValueError('Observation logs must be fresh')
 start=time.monotonic(); status=124
 with log.open('wb') as stream:
  try: status=subprocess.run(command,cwd=root,stdout=stream,stderr=subprocess.STDOUT,timeout=36000).returncode
  except subprocess.TimeoutExpired: pass
 record={'command':command,'exit-code':status,'elapsed-monotonic-seconds':time.monotonic()-start,
         'log':log.relative_to(root).as_posix(),'log-sha256':module.sha256(log),'obligation':obligation}
 module.write_json(result,record)
 return record

started=time.monotonic(); pending=set(selected)-set(adopted); active={}; completed=dict(adopted)
with concurrent.futures.ThreadPoolExecutor(max_workers=args.jobs) as executor:
 while pending or active:
  module.require_staged_build(root,contract,root/'target/foundation-0.1.0/build-inputs.json')
  assert authority.read_bytes()==previous_bytes,'Evidence authority changed during deferred refresh'
  for obligation in priority:
   if len(active)>=args.jobs: break
   if obligation in pending and requirements[obligation]<=set(completed):
    assert not state['failed'],'No further execution after a failed inherited observation'
    pending.remove(obligation); state['running'][obligation]={'started-monotonic-seconds':time.monotonic()-started,'prerequisites-completed':sorted(requirements[obligation])}
    active[executor.submit(execute,obligation)]=obligation
    print('Started inherited '+obligation+' after '+str(sorted(requirements[obligation])),flush=True)
  module.write_json(state_path,state)
  if not active: raise ValueError('No runnable inherited obligation; incomplete dependency graph')
  done,_=concurrent.futures.wait(active,timeout=20,return_when=concurrent.futures.FIRST_COMPLETED)
  for future in done:
   obligation=active.pop(future); record=future.result(); state['running'].pop(obligation)
   if record['exit-code']:
    state['failed'][obligation]=record; module.write_json(state_path,state)
    raise RuntimeError('Inherited observation failed: '+obligation+'; original attempt retained')
   completed[obligation]=record; state['completed'][obligation]=record
   print('Completed inherited '+obligation+' in '+format(record['elapsed-monotonic-seconds'],'.3f')+' monotonic seconds',flush=True)
  module.write_json(state_path,state)
module.require_staged_build(root,contract,root/'target/foundation-0.1.0/build-inputs.json')
assert authority.read_bytes()==previous_bytes
combined=base/'combined-index'; combined.mkdir()
merged={'schema-version':1,'candidate':receipt['candidate'],'environments':[],'certifications':[]}
environments={}; identities=None; scopes=set()
for obligation in selected:
 directory=directories[obligation]; fresh=json.loads((directory/'observed-index.json').read_text()); actual_id=json.loads((directory/'identities.json').read_text())
 assert fresh['candidate']==receipt['candidate'] and (directory/'prior-index.sha256').read_text().strip()==state['prior-index-sha256']
 if identities is None: identities=actual_id
 assert identities==actual_id
 for observed in fresh['environments']:
  profile=observed['profile']; value=json.loads((root/observed['record']['path']).read_text())
  if profile in environments: assert environments[profile]==value,'Observed environment changed across inherited obligations'
  else: environments[profile]=value; merged['environments'].append(observed)
 expected={(profile,execution) for profile in next(item for item in contract['obligations'] if item['id']==obligation)['environments']
           for execution in next(item for item in contract['obligations'] if item['id']==obligation)['execution-profiles']}
 assert {(item['environment'],item['execution-profile']) for item in fresh['certifications']}==expected
 for certification in fresh['certifications']:
  assert certification['obligation']==obligation
  key=(obligation,certification['environment'],certification['execution-profile']); assert key not in scopes; scopes.add(key)
  merged['certifications'].append(certification)
assert len(merged['certifications'])==100 and sum(len(item['records']) for item in merged['certifications'])==412
module.write_json(combined/'observed-index.json',merged); module.write_json(combined/'identities.json',identities)
(combined/'prior-index.sha256').write_text(state['prior-index-sha256']+'\n')
module.publish_index(root,combined)
module.require_staged_build(root,contract,root/'target/foundation-0.1.0/build-inputs.json')
state['elapsed-monotonic-seconds']=time.monotonic()-started; state['result']='pass'; state['combined-index']=combined.relative_to(root).as_posix()
module.write_json(state_path,state)
print('Strict combined inherited refresh passed: 100 tuples / 412 chains; unchanged candidate; dependency order preserved',flush=True)
