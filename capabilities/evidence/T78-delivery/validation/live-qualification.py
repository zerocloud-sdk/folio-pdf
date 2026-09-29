import datetime, hashlib, importlib.util, json, os, shutil, subprocess, sys
from pathlib import Path
root=Path('/workspace/folio-pdf'); validation=root/'capabilities/evidence/T78-delivery/validation'
certifications=json.loads((validation/'certification-validation.json').read_text())
assert len(certifications)==10 and all(row['result']=='pass' for row in certifications)
shutil.copyfile(Path(__file__),validation/'live-qualification.py')
source=root/'capabilities/evidence/foundation/T78-final/password-baseline/jdk8-in_process/observations'
run=root/'target/t78-live-collector-final'
def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()
original={name:digest(source/name) for name in ['result.properties','retained-files.sha256']}
shutil.copytree(source,run)
command=['python3','-B','scripts/tests/test_t78_foundation.py','--qualify-run',run.relative_to(root).as_posix()]
env=dict(os.environ,PYTHONPATH=str(root/'.build-cache/foundation-host-python'),PYTHONDONTWRITEBYTECODE='1')
started=datetime.datetime.now(datetime.timezone.utc).isoformat(); log=validation/'live-qualification-final.txt'
with log.open('xb') as stream:
 result=subprocess.run(command,cwd=root,env=env,stdout=stream,stderr=subprocess.STDOUT)
record={'command':command,'environment':{'PYTHONPATH':env['PYTHONPATH'],'PYTHONDONTWRITEBYTECODE':'1'},'started':started,'finished':datetime.datetime.now(datetime.timezone.utc).isoformat(),'exit-code':result.returncode,'result':'pending-isolated-mode-controls' if result.returncode==0 else 'fail','replay-result':'pass' if result.returncode==0 else 'fail','source-observations':source.relative_to(root).as_posix(),'copy':run.relative_to(root).as_posix(),'report':log.relative_to(root).as_posix(),'sha256':digest(log),'isolated-mode-controls':[]}
(validation/'live-qualification.json').write_text(json.dumps(record,indent=2)+'\n')
if result.returncode: raise SystemExit(result.returncode)
sys.dont_write_bytecode=True;sys.path.insert(0,str(root/'scripts'))
spec=importlib.util.spec_from_file_location('t78_live_tests',root/'scripts/tests/test_t78_foundation.py'); tests=importlib.util.module_from_spec(spec);spec.loader.exec_module(tests)
first=next(iter(json.loads((root/'capabilities/profiles/T78-password/products.json').read_text())['products']))
controls=[('native-publication-mode',run/('native-'+first)/'publication.properties',b'execution-profile=IN_PROCESS\n',b'execution-profile=HARDENED_WORKER\n','Actual public product identity or mode mismatch'),('facade-declared-mode',run/'result.properties',b'facade-execution-profile=IN_PROCESS\n',b'facade-execution-profile=HARDENED_WORKER\n','Wrong or incomplete baseline evidence declaration')]
for name,path,before,after,expected in controls:
 prior=path.read_bytes(); assert prior.count(before)==1
 try:
  path.write_bytes(prior.replace(before,after,1));tests.seal(run)
  try:tests.COLLECTOR.collect_reports(root,run,'IN_PROCESS')
  except ValueError as failure:assert expected in str(failure),str(failure)
  else:raise AssertionError('An isolated false execution mode was accepted')
 finally:
  path.write_bytes(prior);tests.seal(run)
 record['isolated-mode-controls'].append({'name':name,'result':'detected','mutation':'Only the named execution-mode value changed; all other fields retained.','expected-rejection':expected})
assert {name:digest(source/name) for name in original}==original,'Certified source evidence changed'
record.update({'certified-source-unchanged':True,'source-binding':original,'finished':datetime.datetime.now(datetime.timezone.utc).isoformat(),'result':'pass','qualification-driver':{'path':(validation/'live-qualification.py').relative_to(root).as_posix(),'sha256':digest(Path(__file__))}})
(validation/'live-qualification.json').write_text(json.dumps(record,indent=2)+'\n')
print('PASS: final68 replay, resealed finding, and isolated Native/Facade mode controls; certified evidence unchanged',flush=True)
