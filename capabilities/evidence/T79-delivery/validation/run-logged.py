import datetime,hashlib,json,os,subprocess,sys,time
from pathlib import Path
root=Path('/workspace/folio-pdf');folder=root/'capabilities/evidence/T79-delivery/validation'
name=sys.argv[1];command=sys.argv[2:];log=folder/(name+'.txt');started=datetime.datetime.now(datetime.timezone.utc).isoformat();clock=time.monotonic()
with log.open('xb') as output:
    try: code=subprocess.run(command,cwd=root,stdout=output,stderr=subprocess.STDOUT,timeout=int(os.environ.get('T79_COMMAND_TIMEOUT','7200'))).returncode
    except subprocess.TimeoutExpired: code=124
record={'name':name,'command':command,'cwd':str(root),'environment':{k:os.environ[k] for k in ('FOLIO_HARFBUZZ_HELPER','FOLIO_FOUNDATION_PYTHON_ROOT','PYTHONPATH','MAVEN_USER_HOME','MAVEN_OPTS','JAVA_HOME','PATH') if k in os.environ},'start':started,'end':datetime.datetime.now(datetime.timezone.utc).isoformat(),'duration-seconds':round(time.monotonic()-clock,3),'exit-code':code,'log':str(log.relative_to(root)),'log-sha256':hashlib.sha256(log.read_bytes()).hexdigest()}
with (folder/'commands.jsonl').open('a') as receipt: receipt.write(json.dumps(record,sort_keys=True)+'\n')
print(json.dumps({'name':name,'exit-code':code,'duration-seconds':record['duration-seconds']}),flush=True)
sys.exit(code)
