from pathlib import Path
import hashlib,json,shutil,sys,zipfile
root=Path('/workspace/folio-pdf')
original=root/'.build-cache/T77-preflight/protocol-refresh-r3/observations'
run=root/'.build-cache/T77-preflight/protocol-run-r3'
shutil.copytree(original,run)
for path in run.rglob('*'):
 if path.is_file() and path.suffix in ('.json','.stdout','.stderr','.properties'):
  data=path.read_bytes().replace(('/workspace/' + original.relative_to(root).as_posix()).encode(),b'/workspace/run').replace(str(original).encode(),b'/workspace/run').replace(str(root).encode(),b'/workspace')
  path.write_bytes(data)
for path in run.rglob('*.command.json'):
 record=json.loads(path.read_text()); stem=path.name[:-len('.command.json')]
 for stream in ['stdout','stderr']:
  record[stream+'-sha256']=hashlib.sha256(path.with_name(stem+'.'+stream).read_bytes()).hexdigest()
 path.write_text(json.dumps(record,indent=2,sort_keys=True)+'\n')
sys.path.insert(0,str(root/'scripts/tests'))
from test_t15_foundation import seal
seal(run)
with zipfile.ZipFile(root/'scripts/tests/fixtures/t15-collector.zip','w',zipfile.ZIP_DEFLATED) as archive:
 for path in sorted(run.rglob('*')):
  if path.is_file():
   info=zipfile.ZipInfo(path.relative_to(run).as_posix(),(1980,1,1,0,0,0)); info.compress_type=zipfile.ZIP_DEFLATED
   archive.writestr(info,path.read_bytes())
print('Protocol-only fixture:',(root/'scripts/tests/fixtures/t15-collector.zip').stat().st_size,'bytes')
