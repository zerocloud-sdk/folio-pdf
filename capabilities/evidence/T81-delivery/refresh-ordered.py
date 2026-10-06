import datetime
import json
import subprocess
import sys
from pathlib import Path

root=Path('/workspace/folio-pdf')
cycle=sys.argv[1]
base=root/('capabilities/evidence/foundation/T81-'+cycle)
base.mkdir(parents=True,exist_ok=True)
journal=root/('capabilities/evidence/T81-delivery/refresh-'+cycle+'-commands.json')
events=json.loads(journal.read_text()) if journal.exists() else []
staged=json.loads((root/'target/foundation-0.1.0/build-inputs.json').read_text())['candidate']
sequence=['transactions','values','pages','metadata','annotations','text','images','incremental',
          'password-baseline','password-clear-metadata','password-attachments','limits']
for obligation in sequence:
    previous=[event for event in events if event['obligation']==obligation]
    completed=next((event for event in reversed(previous) if event['exit-code']==0),None)
    if completed:
        observed=root/completed['command'][4]/'observed-index.json'
        if json.loads(observed.read_text())['candidate']!=staged:
            raise ValueError('Cannot resume an obligation from another candidate')
        print(cycle+' already completed '+obligation,flush=True)
        continue
    output=base/obligation
    attempt=1
    while output.exists():
        attempt+=1
        output=base/(obligation+'-r'+str(attempt))
    command=[sys.executable,'-B',str(root/'scripts/t03-foundation.py'),'certify',str(output.relative_to(root)),
             '--obligation',obligation]
    started=datetime.datetime.now(datetime.timezone.utc).isoformat()
    print(cycle+' starting '+obligation+' attempt '+str(attempt),flush=True)
    result=subprocess.run(command,cwd=root)
    events.append({'obligation':obligation,'attempt':attempt,'command':command,'started':started,
                   'finished':datetime.datetime.now(datetime.timezone.utc).isoformat(),'exit-code':result.returncode})
    journal.write_text(json.dumps(events,indent=2)+'\n')
    if result.returncode:sys.exit(result.returncode)
    print(cycle+' completed '+obligation,flush=True)
print(cycle+' ordered refresh complete',flush=True)
