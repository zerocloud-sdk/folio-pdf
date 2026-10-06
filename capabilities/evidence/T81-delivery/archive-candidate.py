import hashlib,json,shutil,sys,zipfile
from pathlib import Path
root=Path('/workspace/folio-pdf')
base=root/'target/foundation-0.1.0'
receipt=json.loads((base/'build-inputs.json').read_text())
dest=root/'capabilities/evidence/T81-delivery'/sys.argv[1]
assert not dest.exists()
dest.mkdir()
for name in ('build-inputs.json','build-command.json','build.txt'):
    shutil.copy2(base/name,dest/name)
refs=receipt['candidate']['artifacts']+receipt['contract-inputs']+[r for r in receipt['harness'] if r['path'].endswith('.jar')]
records=[]
for ref in refs:
    original=root/ref['path'];data=original.read_bytes()
    assert hashlib.sha256(data).hexdigest()==ref['sha256']
    archived=dest/'originals'/ref['path'];archived.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(original,archived)
    assert hashlib.sha256(archived.read_bytes()).hexdigest()==ref['sha256']
    records.append({'original-path':ref['path'],'sha256':ref['sha256'],'archive-path':str(archived.relative_to(root))})
(dest/'archive-map.json').write_text(json.dumps(records,indent=2)+'\n')
archive=dest/'source-inputs.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as output:
    for ref in receipt['candidate']['inputs']:
        data=(root/ref['path']).read_bytes()
        assert hashlib.sha256(data).hexdigest()==ref['sha256']
        info=zipfile.ZipInfo(ref['path'],(1980,1,1,0,0,0));info.external_attr=0o100644<<16;info.compress_type=zipfile.ZIP_DEFLATED
        output.writestr(info,data)
with zipfile.ZipFile(archive) as check:
    assert len(check.namelist())==len(receipt['candidate']['inputs'])
    for ref in receipt['candidate']['inputs']:assert hashlib.sha256(check.read(ref['path'])).hexdigest()==ref['sha256']
(dest/'source-archive.json').write_text(json.dumps({'path':str(archive.relative_to(root)),'sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),'original-inputs':receipt['candidate']['inputs']},indent=2)+'\n')
print('Archived',len(records),'exact final artifacts/contracts/harness jars and',len(receipt['candidate']['inputs']),'source inputs')

import hashlib,json,sys,zipfile
from pathlib import Path
root=Path('/workspace/folio-pdf')
dest=root/'capabilities/evidence/T81-delivery'/sys.argv[1]
records=json.loads((dest/'archive-map.json').read_text())
archive=dest/'artifacts-contracts-harness.zip'
assert not archive.exists()
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as output:
    for item in records:
        data=(root/item['archive-path']).read_bytes()
        assert hashlib.sha256(data).hexdigest()==item['sha256']
        info=zipfile.ZipInfo(item['original-path'],(1980,1,1,0,0,0));info.external_attr=0o100644<<16;info.compress_type=zipfile.ZIP_DEFLATED
        output.writestr(info,data)
    for name in ('build-inputs.json','build-command.json','build.txt'):
        info=zipfile.ZipInfo('receipts/'+name,(1980,1,1,0,0,0));info.external_attr=0o100644<<16;info.compress_type=zipfile.ZIP_DEFLATED
        output.writestr(info,(dest/name).read_bytes())
with zipfile.ZipFile(archive) as check:
    for item in records:assert hashlib.sha256(check.read(item['original-path'])).hexdigest()==item['sha256']
(dest/'artifact-archive.json').write_text(json.dumps({'path':str(archive.relative_to(root)),'sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),'originals':records,'build-receipts':['receipts/'+name for name in ('build-inputs.json','build-command.json','build.txt')]},indent=2)+'\n')
print('Verified portable archive of',len(records),'original artifacts/contracts/harness jars and three exact build receipts')
