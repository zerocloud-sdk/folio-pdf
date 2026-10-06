import collections,gzip,hashlib,json,subprocess
from pathlib import Path
root=Path('/workspace/folio-pdf');delivery=root/'capabilities/evidence/T81-delivery'
commands=[]
def git(*args,allowed=(0,)):
    argv=['git',*args];r=subprocess.run(argv,cwd=root,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    commands.append({'command':argv,'exit-code':r.returncode,'stderr':r.stderr.decode()})
    assert r.returncode in allowed,(argv,r.stderr.decode())
    return r.stdout
baseline='05e7f546f5885680e333ad8d3dea3645b25d26b7'
head=git('rev-parse','HEAD').decode().strip();assert head==baseline
branch=git('branch','--show-current').decode().strip();assert branch=='main'
origin=git('remote','get-url','origin').decode().strip();assert origin=='https://github.com/zerocloud-sdk/folio-pdf.git'
identity={'name':git('config','user.name').decode().strip(),'email':git('config','user.email').decode().strip()}
assert identity=={'name':'mabaiqiu','email':'mabaiqiu@gmail.com'}
assert not git('diff','--cached','--name-only','-z')
check=git('diff','--check');assert not check
tracked=[p.decode() for p in git('diff',baseline,'--name-only','-z').split(b'\0') if p]
allowed_tracked={'.gitignore','PROVENANCE.md','README.md','capabilities/capability-matrix.yaml','capabilities/facade-surface.yaml','capabilities/foundation-evidence.yaml','capabilities/evidence/T20-hostile-input-limits.md','docs/generated/capability-matrix.md','docs/generated/facade-surface.md','docs/generated/foundation-readiness.md','docs/hostile-input-policy.md','docs/zh-CN/getting-started.md','pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/T03EvidenceCommand.java','scripts/t03-foundation.py','scripts/tests/test_t10_foundation.py'}
allowed_tracked.add('build-tools/inventory/src/test/java/net/zerocloud/pdf/tools/inventory/FoundationReadinessCommandTest.java')
assert set(tracked)<=allowed_tracked,set(tracked)-allowed_tracked
untracked=[p.decode() for p in git('ls-files','--others','--exclude-standard','-z').split(b'\0') if p]
prefixes=['capabilities/evidence/T81-delivery/','capabilities/evidence/foundation/T81-initial/','capabilities/evidence/foundation/T81-final/','capabilities/evidence/foundation/T81-final-r2/','capabilities/profiles/T20-hostile-input/']
singletons={'docs/t20-certification.md','scripts/generate-t20-corpus.py','scripts/t20_foundation_reports.py','scripts/t20-evidence-pin.properties','scripts/tests/test_t20_foundation.py'}
singletons.update('capabilities/evidence/T81-limits-'+c+'.md' for c in ('syntax','standards','semantic','visual','contract'))
singletons.update('pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/T20'+name+'.java' for name in ('ResourceContracts','FacadeContracts','EvidenceCommand'))
singletons.update('pdf-acceptance/src/test/java/net/zerocloud/pdf/acceptance/T20'+name+'.java' for name in ('ContractTestCommand','ContractTestCommandTest','EvidenceCommandTest','IndependentEvidenceTest'))
unknown=[p for p in untracked if p not in singletons and not any(p.startswith(prefix) for prefix in prefixes)]
assert not unknown,unknown[:20]
manifest=delivery/'untracked-paths.txt.gz'
with manifest.open('wb') as stream:
    with gzip.GzipFile(filename='',mode='wb',fileobj=stream,mtime=0) as compressed:compressed.write(('\n'.join(sorted(untracked))+'\n').encode())
counts=collections.Counter(next((prefix for prefix in prefixes if p.startswith(prefix)),'ticket source/docs') for p in untracked)
result={'status':'pass','comparison-baseline':baseline,'head':head,'branch':branch,'origin':origin,'dco-identity':identity,'entry-worktree':'clean as recorded in execution-authority.json','staged-changes':[],'tracked-ticket-changes':tracked,'untracked-ticket-count':len(untracked),'untracked-ticket-groups':dict(counts),'untracked-path-manifest':{'path':str(manifest.relative_to(root)),'sha256':hashlib.sha256(manifest.read_bytes()).hexdigest()},'unrelated-changes':[],'optional-commit':'omitted','external-publication':'none','commands':commands}
(delivery/'worktree-audit.json').write_text(json.dumps(result,indent=2)+'\n')
print('PASS: unchanged baseline HEAD, no staged changes,',len(tracked),'tracked ticket paths,',len(untracked),'untracked ticket paths, no unrelated paths')
