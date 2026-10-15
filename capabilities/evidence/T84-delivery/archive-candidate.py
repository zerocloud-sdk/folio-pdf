"""Retain final unsigned candidate/source bytes in bounded SHA-256-pinned ZIP parts."""
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import zipfile

root=Path(__file__).resolve().parents[3]; delivery=Path(__file__).resolve().parent
sys.path.insert(0,str(root/'scripts'))
import t05_foundation_reports
module=t05_foundation_reports.foundation()
import yaml
contract=yaml.safe_load((root/'capabilities/foundation-release.yaml').read_text())
receipt=module.require_staged_build(root,contract,root/'target/foundation-0.1.0/build-inputs.json')
output=delivery/'retained-inputs'; output.mkdir()
source={item['path']:item['sha256'] for item in receipt['candidate']['inputs']}
products={item['path']:item['sha256'] for item in receipt['candidate']['artifacts']+receipt['contract-inputs']+receipt['harness']}
for name in ('target/foundation-0.1.0/build-inputs.json','target/foundation-0.1.0/build-command.json','target/foundation-0.1.0/build.txt'):
 products[name]=module.sha256(root/name)
for label,files in (('source-inputs',source),('artifacts-contracts-harness',products)):
 descriptor,name=tempfile.mkstemp(prefix='t84-'+label+'-',suffix='.zip',dir='/tmp'); os.close(descriptor); archive=Path(name)
 entries=[]
 with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as zipped:
  for name,expected in sorted(files.items()):
   path=root/name; path.resolve(strict=True).relative_to(root); data=path.read_bytes()
   assert hashlib.sha256(data).hexdigest()==expected,'Changed frozen archive input: '+name
   info=zipfile.ZipInfo(name,date_time=(1980,1,1,0,0,0)); info.external_attr=(0o100000|(path.stat().st_mode&0o777))<<16
   info.compress_type=zipfile.ZIP_DEFLATED; zipped.writestr(info,data)
   entries.append({'path':name,'sha256':expected,'bytes':len(data),'mode':path.stat().st_mode&0o777})
 parts=[]; offset=0
 with archive.open('rb') as stream:
  while data:=stream.read(48*1024*1024):
   part=output/(label+'.zip.part'+format(len(parts)+1,'03d')); part.write_bytes(data)
   parts.append({'path':part.name,'offset':offset,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()}); offset+=len(data)
 manifest={'archive':label+'.zip','bytes':archive.stat().st_size,'sha256':module.sha256(archive),'parts':parts,'entries':entries,
           'reassembly':'Concatenate parts in listed order without separators; verify whole SHA-256 and all ZIP entry hashes before restoring.'}
 module.write_json(output/(label+'.zip-parts.json'),manifest); archive.unlink()
 print(label+': '+str(offset)+' compressed bytes, '+str(len(parts))+' parts, '+str(len(entries))+' exact entries',flush=True)
module.require_staged_build(root,contract,root/'target/foundation-0.1.0/build-inputs.json')
(output/'README.md').write_text('# Retained final unsigned candidate\n\nThe source and artifact/contracts/harness manifests bind every original entry and\nthe complete archive. Parts are at most 48 MiB. Reassemble in order, verify the\nwhole SHA-256, then verify each entry before restoring at the same declared\nworkspace path. These bytes grant no signing or release-publication authority.\nPinned external tool/native installations and immutable container images remain\nexplicit prerequisites; the final live readiness receipt checks their actual\nlocal bytes. Source, candidate, Python observer and staged harness inputs are\nretained for review rather than represented solely by a portable report.\n')
