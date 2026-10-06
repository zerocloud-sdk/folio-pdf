import datetime,hashlib,json,os,re,subprocess,sys
from pathlib import Path
root=Path('/workspace/folio-pdf')
delivery=root/'capabilities/evidence/T81-delivery'
mode=sys.argv[1]
commands={
 'collector-final':['python3','-B','-m','unittest','discover','-s','scripts/tests','-p','test_*foundation.py'],
 **{'inventory-'+name+'-final':[str(root/'scripts/inventory'),name] for name in ('validate','generate','check','readiness')},
 'collect-final-cli':['python3','-B','scripts/t03-foundation.py','collect','capabilities/evidence/foundation/T81-final/limits/jdk17-in_process/observations','--obligation','limits','--execution-profile','IN_PROCESS']}
command=commands[mode]
env=os.environ.copy()
if mode=='collector-final':
    env.pop('FOLIO_FOUNDATION_PYTHON_ROOT',None)
    env['PYTHONPATH']='.build-cache/foundation-host-python'
log=delivery/('validation/'+mode+'.txt')
result_path=delivery/('validation/'+mode+'-result.json')
assert not log.exists() and not result_path.exists(), 'Use a fresh attempt name'
record={'command':command,'cwd':str(root),'started':datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'environment':{name:env.get(name) for name in ('JAVA_HOME','MAVEN_USER_HOME','MAVEN_OPTS','PYTHONPATH','FOLIO_FOUNDATION_PYTHON_ROOT','FOLIO_HARFBUZZ_HELPER')}}
print('Starting '+mode,flush=True)
with log.open('wb') as stream:
    result=subprocess.run(command,cwd=root,env=env,stdout=stream,stderr=subprocess.STDOUT,timeout=10800)
record.update({'exit-code':result.returncode,'finished':datetime.datetime.now(datetime.timezone.utc).isoformat(),
 'log':{'path':str(log.relative_to(root)),'sha256':hashlib.sha256(log.read_bytes()).hexdigest()}})
result_path.write_text(json.dumps(record,indent=2)+'\n')
print(mode+' exit '+str(result.returncode),flush=True)
if mode=='inventory-readiness-final':
    text=log.read_text()
    identities={key:re.search(r'(?m)^'+key+' identity: ([a-f0-9]{64})$',text).group(1) for key in ('Candidate','Contract')}
    (delivery/'validation/readiness-identities.json').write_text(json.dumps(identities,indent=2)+'\n')
    print(text)
sys.exit(result.returncode)
