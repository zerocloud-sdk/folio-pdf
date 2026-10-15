"""Retain one authorized local validation command with monotonic elapsed time."""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import subprocess
import sys
import time

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('label'); parser.add_argument('--timeout',type=float,default=36000)
arguments=sys.argv[1:]
if '--' not in arguments: parser.error('Separate wrapper arguments from the command with --')
separator=arguments.index('--'); args=parser.parse_args(arguments[:separator]); command=arguments[separator+1:]
if not command or not args.label.replace('-','').replace('_','').isalnum() or not math.isfinite(args.timeout) or args.timeout<=0: parser.error('A safe label, finite timeout and command are required')
root=Path(__file__).resolve().parents[3]; directory=Path(__file__).resolve().parent/'validation'
log=directory/(args.label+'.txt'); result=directory/(args.label+'-result.json')
if log.exists() or result.exists(): raise ValueError('Validation outputs must be fresh')
started=time.monotonic(); failure=None
try:
 with log.open('wb') as stream: status=subprocess.run(command,cwd=root,stdout=stream,stderr=subprocess.STDOUT,timeout=args.timeout).returncode
except subprocess.TimeoutExpired:
 status=124; failure='monotonic timeout expired'
record={'command':command,'exit-code':status,'elapsed-monotonic-seconds':time.monotonic()-started,'timeout-monotonic-seconds':args.timeout,
        'log':log.relative_to(root).as_posix(),'log-sha256':hashlib.file_digest(log.open('rb'),'sha256').hexdigest(),
        'environment':{key:os.environ[key] for key in ('JAVA_HOME','MAVEN_USER_HOME','MAVEN_OPTS','PYTHONPATH','FOLIO_FOUNDATION_PYTHON_ROOT','FOLIO_HARFBUZZ_HELPER','CONTAINERS_STORAGE_CONF') if key in os.environ}}
if failure: record['failure']=failure
result.write_text(json.dumps(record,indent=2)+'\n'); print(json.dumps({'label':args.label,'exit-code':status,'elapsed-monotonic-seconds':record['elapsed-monotonic-seconds']}),flush=True)
raise SystemExit(status)
