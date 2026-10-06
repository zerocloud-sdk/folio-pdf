import datetime,importlib.util,json,os,subprocess,sys
from pathlib import Path
import yaml
root=Path('/workspace/folio-pdf')
spec=importlib.util.spec_from_file_location('foundation_driver',root/'scripts/t03-foundation.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
profiles=yaml.safe_load((root/'capabilities/foundation-environments.yaml').read_text())['profiles']
profile=next(p for p in profiles if p['identity']['jdk-major']==17)
helper=Path(os.environ['FOLIO_HARFBUZZ_HELPER']).resolve(strict=True)
command=m.container_command(root,profile['identity']['image'],helper)
command[command.index(str(root)+':/workspace:ro')]=str(root)+':/workspace:rw'
command[-1:-1]=['--env','MAVEN_USER_HOME=/workspace/.build-cache/maven',
                 '--env','MAVEN_OPTS=-Dmaven.repo.local=/workspace/.build-cache/maven/repository',
                 '--env','FOLIO_FOUNDATION_PYTHON_ROOT=/workspace/.build-cache/foundation-python']
command+=sys.argv[2:]
delivery=root/'capabilities/evidence/T81-delivery'
name=sys.argv[1]
log=delivery/('validation/'+name+'.txt')
assert not log.exists() and not (delivery/('validation/'+name+'-result.json')).exists(), 'Use a fresh attempt name'
record={'command':command,'required-repository-command':sys.argv[2:],'actual-image':profile['identity'],
        'started':datetime.datetime.now(datetime.timezone.utc).isoformat()}
print('Running focused gate in exact Ubuntu/JDK17 image',flush=True)
with log.open('wb') as stream:
    result=subprocess.run(command,cwd=root,stdout=stream,stderr=subprocess.STDOUT,timeout=10800)
record.update({'finished':datetime.datetime.now(datetime.timezone.utc).isoformat(),'exit-code':result.returncode,
               'log':m.reference(root,log)})
(delivery/('validation/'+name+'-result.json')).write_text(json.dumps(record,indent=2)+'\n')
print('Focused gate exit',result.returncode,flush=True)
sys.exit(result.returncode)
