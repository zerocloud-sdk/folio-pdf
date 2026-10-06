import hashlib,json,re
from pathlib import Path
draft='--draft' in __import__('sys').argv
root=Path('/workspace/folio-pdf')
delivery=root/'capabilities/evidence/T81-delivery'
def read(name):return json.loads((delivery/name).read_text())
def ref(name):
    path=root/name
    return {'path':name,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()}
def verify_retained(item):
    assert ref(item['path'])==item, 'Changed retained reference: '+item['path']
identity=read('identity-summary.json')
assert read('validation/relaunch-entry-audit-r1.json')['status']=='pass'
attachment_outcome=read('relaunch-attachment-outcome.json')
assert attachment_outcome['status']=='pass'
verify_retained(attachment_outcome['validation-result'])
attachment_validation=json.loads((root/attachment_outcome['validation-result']['path']).read_text())
assert attachment_validation['exit-code']==0
verify_retained(attachment_validation['log'])
verify_retained(attachment_outcome['resume-audit'])
assert json.loads((root/attachment_outcome['resume-audit']['path']).read_text())['result']=='pass'
for failure in attachment_outcome['failed-attempts']:
    for key in ('validation-result','contract-tests','log'):verify_retained(failure[key])
    failed_validation=json.loads((root/failure['validation-result']['path']).read_text())
    assert failed_validation['exit-code']!=0 and failed_validation['log']==failure['log']
assert read('validation/attachments-final-r2-relaunch-r1-result.json')['exit-code']!=0
assert read('validation/attachments-final-r2-relaunch-r2-result.json')['exit-code']!=0
assert read('validation/attachments-final-r2-relaunch-r3-result.json')['exit-code']!=0
assert read('validation/attachments-jdk17-worker-probe-r1-result.json')['exit-code']==0
assert read('validation/attachments-jdk21-worker-probe-r1-result.json')['exit-code']==0
assert identity['status']=='pass' and identity['certification-count']==92 and identity['chain-record-count']==372
assert read('validation/clear-metadata-jdk21-worker-probe-r1-result.json')['exit-code']==0
assert json.loads((root/'capabilities/evidence/foundation/T81-final-r2/password-clear-metadata-r2/resume-audit.json').read_text())['result']=='pass'
assert read('validation/text-jdk8-worker-probe-r1-result.json')['exit-code']==0
assert json.loads((root/'capabilities/evidence/foundation/T81-final/text-r6/resume-audit.json').read_text())['result']=='pass'
assert read('validation/text-jdk11-worker-probe-r1-result.json')['exit-code']==0
assert read('validation/incremental-jdk8-worker-probe-r1-result.json')['exit-code']==0
timeout_probe=read('validation/text-timeout-probe-r1-result.json')
assert timeout_probe['diagnostic-only'] and len(timeout_probe['events'])==4
assert all(event['exit-code']==0 for event in timeout_probe['events'])
for event in timeout_probe['events']:verify_retained(event['log'])
visual_probe=read('validation/text-visual-probe-r1-result.json')
assert visual_probe['diagnostic-only'] and len(visual_probe['events'])==3
assert all(event['exit-code']==0 and event['actual-negative-control-result']=='fail'
           for event in visual_probe['events'])
for event in visual_probe['events']:
    verify_retained(event['log']);verify_retained(event['actual-result'])
clear_probe=read('validation/clear-metadata-products-probe-r1-result.json')
assert clear_probe['diagnostic-only'] and len(clear_probe['events'])==3
assert all(event['exit-code']==0 and event['product-file-count']==235 for event in clear_probe['events'])
verify_retained(clear_probe['original-configuration'])
for event in clear_probe['events']:verify_retained(event['log'])
assert json.loads((root/'capabilities/evidence/foundation/T81-final/password-clear-metadata-r2/resume-audit.json').read_text())['result']=='pass'
attachment_probe=read('validation/attachment-visual-probe-r2-result.json')
assert attachment_probe['exit-code']==0
verify_retained(attachment_probe['log'])
attachment_probe=read('validation/attachment-visual-probe-r2-retained-audit.json')
assert attachment_probe['status']=='pass' and attachment_probe['diagnostic-only'] and len(attachment_probe['events'])==3
for event in attachment_probe['events']:
    verify_retained(event['log']);verify_retained(event['actual-result'])
    assert json.loads((root/event['actual-result']['path']).read_text())['result']=='pass'
    for item in event['retained-files']:verify_retained(item)
for key in ('recipe','original-wrapper-result','original-wrapper-log','original-diagnostic-recipe'):verify_retained(attachment_probe[key])
for key in ('original-configuration','original-failure','input'):verify_retained(attachment_probe[key])
assert read('validation/attachment-visual-probe-r1-result.json')['status']=='failed'
verify_retained(identity['current-evidence-index'])
verify_retained(identity['staged-build-inputs'])
import importlib.util
import yaml
spec=importlib.util.spec_from_file_location('t81_receipt_driver',root/'scripts/t03-foundation.py')
driver=importlib.util.module_from_spec(spec);spec.loader.exec_module(driver)
driver.require_staged_build(root,yaml.safe_load((root/'capabilities/foundation-release.yaml').read_text()),
                           root/'target/foundation-0.1.0/build-inputs.json')
names=['full-verify-ubuntu-final','collector-final','inventory-validate-final','inventory-generate-final','inventory-check-final','collect-final-cli']
validation={name:read('validation/'+name+'-r2-result.json') for name in names}
assert all(v['exit-code']==0 for v in validation.values())
for value in validation.values():verify_retained(value['log'])
readiness=read('validation/inventory-readiness-final-r2-result.json')
verify_retained(readiness['log'])
text=(root/readiness['log']['path']).read_text()
assert readiness['exit-code']!=0 and 'Foundation 0.1.0: NOT READY' in text
satisfied=re.findall(r'(?m)^SATISFIED ([a-z0-9-]+) \(#([0-9]+)\)$',text)
required=identity['refresh-order']+['security']
assert set(required)<=set(n for n,s in satisfied)
blocked={}
for name,slice_id,error in re.findall(r'(?m)^BLOCKED ([a-z0-9-]+) \(#([0-9]+)\): (.+)$',text):
    blocked.setdefault(name,{'slice':int(slice_id),'diagnostics':[]})['diagnostics'].append(error)
assert not set(required)&set(blocked)
assert not re.search(r'(?m)^BLOCKED release:',text)
for label,expected in [('Candidate',identity['candidate-sha256']),('Contract',identity['foundation-contract-sha256'])]:
    assert re.search(r'(?m)^'+label+' identity: '+expected+'$',text)
reviews={name:(read('reviews/'+name+'-final.json') if (delivery/('reviews/'+name+'-final.json')).exists() else {'status':'pending'}) for name in ('standards','spec')}
if not draft: assert all(r['status']=='pass' and r['baseline']==identity['baseline-and-head'] and r['unresolved-findings']==0 for r in reviews.values())
worktree_name='worktree-pre-receipt-audit.json' if draft else 'worktree-audit.json'
worktree=read(worktree_name)
assert worktree['head']==identity['baseline-and-head'] and worktree['status']=='pass'
links={
 'relaunch-authority':'capabilities/evidence/T81-delivery/relaunch-authority.json',
 'relaunch-entry-audit':'capabilities/evidence/T81-delivery/validation/relaunch-entry-audit-r1.json',
 'relaunch-attachment-failure':'capabilities/evidence/T81-delivery/validation/attachments-final-r2-relaunch-r1-result.json',
 'relaunch-attachment-failed-tests':'capabilities/evidence/foundation/T81-final-r2/password-attachments-r2/jdk17-hardened_worker/contract-tests.txt',
 'relaunch-attachment-diagnostic':'capabilities/evidence/T81-delivery/validation/attachments-jdk17-worker-probe-r1-result.json',
 'relaunch-attachment-diagnostic-recipe':'capabilities/evidence/T81-delivery/diagnose-attachments-jdk17-worker.py',
 'relaunch-attachment-recipe':'capabilities/evidence/T81-delivery/resume-attachments-final-r2.py',
 'relaunch-attachment-result':attachment_outcome['validation-result']['path'],
 'relaunch-attachment-audit':attachment_outcome['resume-audit']['path'],
 'relaunch-attachment-outcome':'capabilities/evidence/T81-delivery/relaunch-attachment-outcome.json',
 'relaunch-attachment-outcome-recipe':'capabilities/evidence/T81-delivery/record-relaunch-attachments.py',
 'relaunch-jdk21-failure':'capabilities/evidence/T81-delivery/validation/attachments-final-r2-relaunch-r2-result.json',
 'relaunch-jdk21-failed-tests':'capabilities/evidence/foundation/T81-final-r2/password-attachments-r3/jdk21-hardened_worker/contract-tests.txt',
 'relaunch-jdk21-diagnostic':'capabilities/evidence/T81-delivery/validation/attachments-jdk21-worker-probe-r1-result.json',
 'relaunch-jdk21-diagnostic-recipe':'capabilities/evidence/T81-delivery/diagnose-attachments-jdk21-worker.py',
 'relaunch-last-tuple-recipe':'capabilities/evidence/T81-delivery/resume-attachments-last-final-r2.py',
 'relaunch-last-tuple-standards':'capabilities/evidence/T81-delivery/reviews/standards-relaunch-last-tuple-r1.md',
 'relaunch-last-tuple-spec':'capabilities/evidence/T81-delivery/reviews/spec-relaunch-last-tuple-r1.md',
 'relaunch-source-standards':'capabilities/evidence/T81-delivery/reviews/standards-relaunch-source-r1.md',
 'relaunch-source-spec':'capabilities/evidence/T81-delivery/reviews/spec-relaunch-source-r1.md',
 'relaunch-receipt-recipe':'capabilities/evidence/T81-delivery/write-receipt-relaunch.py',
 'coverage':'capabilities/profiles/T20-hostile-input/coverage.json',
 'fixtures':'capabilities/profiles/T20-hostile-input/fixtures.json',
 'generator':'scripts/generate-t20-corpus.py',
 'native':'pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/T20ResourceContracts.java',
 'facade':'pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/T20FacadeContracts.java',
 'recorder':'pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/T20EvidenceCommand.java',
 'collector':'scripts/t20_foundation_reports.py',
 'collector-tests':'scripts/tests/test_t20_foundation.py',
 'mapping':'capabilities/facade-surface.yaml',
 'foundation':'capabilities/foundation-release.yaml',
 'matrix':'capabilities/capability-matrix.yaml',
 'policy':'docs/hostile-input-policy.md',
 'certification':'docs/t20-certification.md',
 'chinese':'docs/zh-CN/getting-started.md',
 'provenance':'PROVENANCE.md',
 'index':'capabilities/foundation-evidence.yaml',
 'identities':'capabilities/evidence/T81-delivery/identity-summary.json',
 'strict-runner':'pdf-acceptance/src/test/java/net/zerocloud/pdf/acceptance/T20ContractTestCommand.java',
 'strict-controls':'pdf-acceptance/src/test/java/net/zerocloud/pdf/acceptance/T20ContractTestCommandTest.java',
 'initial-validation':'capabilities/evidence/T81-delivery/validation/hostile-input-initial-r1.txt',
 'focused-validation':'capabilities/evidence/T81-delivery/validation/resource-experiments-final-source-r3.txt',
 'real-tool-validation':'capabilities/evidence/T81-delivery/validation/independent-t20-ubuntu-r2.txt',
 'initial-refresh':'capabilities/evidence/T81-delivery/refresh-initial-commands.json',
 'model-provision':'capabilities/evidence/T81-delivery/validation/provision-t80-arlington-model-r1-result.json',
 'model-provision-proof':'capabilities/evidence/T81-delivery/validation/t80-cache-and-staged-identity-r1.json',
 'initial-attachments-failure':'capabilities/evidence/foundation/T81-initial/password-attachments/jdk8-in_process/recorder.txt',
 'initial-attachments-worker-failure':'capabilities/evidence/foundation/T81-initial/password-attachments-r2/jdk11-hardened_worker/contract-tests.txt',
 'attachments-worker-probe':'capabilities/evidence/T81-delivery/validation/attachments-jdk11-worker-probe-r1-result.json',
 'attachments-resume-audit':'capabilities/evidence/foundation/T81-initial/password-attachments-r3/resume-audit.json',
 'attachments-resume-recipe':'capabilities/evidence/T81-delivery/resume-attachments.py',
 'attachments-resume-standards':'capabilities/evidence/T81-delivery/reviews/standards-resume-recipe.md',
 'attachments-resume-spec':'capabilities/evidence/T81-delivery/reviews/spec-resume-recipe.md',
 'initial-envelope-failure':'capabilities/evidence/T81-delivery/validation/refresh-initial-r4.txt',
 'envelope-diagnostic':'capabilities/evidence/T81-delivery/validation/limits-envelope-diagnostic-r1.txt',
 'envelope-repair-audit':'capabilities/evidence/foundation/T81-initial/limits-envelope-r1/envelope-repair-audit.json',
 'envelope-repair-recipe':'capabilities/evidence/T81-delivery/repair-limits-envelope.py',
 'envelope-repair-standards':'capabilities/evidence/T81-delivery/reviews/standards-observer-envelope-recipe.md',
 'envelope-repair-spec':'capabilities/evidence/T81-delivery/reviews/spec-observer-envelope-recipe.md',
 'current-clear-metadata-failure':'capabilities/evidence/foundation/T81-final-r2/password-clear-metadata/jdk21-hardened_worker/contract-tests.txt',
 'current-clear-metadata-probe':'capabilities/evidence/T81-delivery/validation/clear-metadata-jdk21-worker-probe-r1-result.json',
 'current-clear-metadata-resume':'capabilities/evidence/T81-delivery/resume-clear-metadata-last-r2.py',
 'current-clear-metadata-resume-audit':'capabilities/evidence/foundation/T81-final-r2/password-clear-metadata-r2/resume-audit.json',
 'current-clear-metadata-standards':'capabilities/evidence/T81-delivery/reviews/standards-clear-metadata-last-r2.md',
 'current-clear-metadata-spec':'capabilities/evidence/T81-delivery/reviews/spec-clear-metadata-last-r2.md',
 'final-refresh':'capabilities/evidence/T81-delivery/refresh-final-r2-commands.json',
 'previous-final-refresh':'capabilities/evidence/T81-delivery/refresh-final-commands.json',
 'previous-final-index':'capabilities/evidence/T81-delivery/final-candidate/evidence-index.json',
 'inventory-expectation-failure':'capabilities/evidence/T81-delivery/validation/inventory-expectation-failure.json',
 'inventory-expectation-source':'build-tools/inventory/src/test/java/net/zerocloud/pdf/tools/inventory/FoundationReadinessCommandTest.java',
 'inventory-expectation-focused':'capabilities/evidence/T81-delivery/validation/inventory-expectation-focused-r2-result.json',
 'inventory-expectation-suite':'capabilities/evidence/T81-delivery/validation/inventory-expectation-suite-r2-result.json',
 'release-tool-sandbox-failure':'capabilities/evidence/T81-delivery/validation/release-tool-pre-restage-r1-result.json',
 'release-tool-ubuntu-suite':'capabilities/evidence/T81-delivery/validation/release-tool-ubuntu-pre-restage-r1-result.json',
 'restage-closure':'capabilities/evidence/T81-delivery/validation/final-stage-r2-closure.json',
 'inventory-expectation-standards':'capabilities/evidence/T81-delivery/reviews/standards-inventory-expectation.md',
 'inventory-expectation-spec':'capabilities/evidence/T81-delivery/reviews/spec-inventory-expectation.md',
 'final-refresh-interruption':'capabilities/evidence/T81-delivery/validation/refresh-final-r1-interruption.json',
 'final-incremental-failure':'capabilities/evidence/foundation/T81-final/incremental/jdk8-hardened_worker/contract-tests.txt',
 'incremental-worker-probe':'capabilities/evidence/T81-delivery/validation/incremental-jdk8-worker-probe-r1-result.json',
 'clear-metadata-recorder-failure':'capabilities/evidence/foundation/T81-final/password-clear-metadata/jdk11-hardened_worker/recorder.txt',
 'clear-metadata-products-probe':'capabilities/evidence/T81-delivery/validation/clear-metadata-products-probe-r1-result.json',
 'clear-metadata-resume-audit':'capabilities/evidence/foundation/T81-final/password-clear-metadata-r2/resume-audit.json',
 'clear-metadata-resume-recipe':'capabilities/evidence/T81-delivery/resume-clear-metadata.py',
 'clear-metadata-resume-standards':'capabilities/evidence/T81-delivery/reviews/standards-clear-metadata-resume.md',
 'clear-metadata-resume-spec':'capabilities/evidence/T81-delivery/reviews/spec-clear-metadata-resume.md',
 'final-attachments-visual-failure':'capabilities/evidence/foundation/T81-final/password-attachments/jdk8-in_process/recorder.txt',
 'attachments-visual-probe':'capabilities/evidence/T81-delivery/validation/attachment-visual-probe-r2-result.json',
 'attachments-visual-retained-audit':'capabilities/evidence/T81-delivery/validation/attachment-visual-probe-r2-retained-audit.json',
 'attachments-visual-retained-audit-recipe':'capabilities/evidence/T81-delivery/audit-retained-attachment-probe-r2.py',
 'attachments-visual-probe-first-timeout':'capabilities/evidence/T81-delivery/validation/attachment-visual-probe-r1-result.json',
 'attachments-visual-probe-recipe':'capabilities/evidence/T81-delivery/diagnose-attachment-visual.py',
 'attachments-visual-probe-first-recipe':'capabilities/evidence/T81-delivery/diagnose-attachment-visual-r1.py',
 'final-attachments-coordinator-failure':'capabilities/evidence/foundation/T81-final/password-attachments-r2/jdk8-in_process/recorder.txt',
 'attachments-coordinator-timing':'capabilities/evidence/T81-delivery/validation/attachment-coordinator-timing-r1.json',
 'text-recovery-interruption':'capabilities/evidence/T81-delivery/validation/text-final-resume-r1-interruption.json',
 'final-text-failure':'capabilities/evidence/foundation/T81-final/text/jdk8-hardened_worker/contract-tests.txt',
 'text-worker-probe':'capabilities/evidence/T81-delivery/validation/text-jdk8-worker-probe-r1-result.json',
 'text-timeout-failure':'capabilities/evidence/foundation/T81-final/text-r3/jdk8-hardened_worker/contract-tests.txt',
 'text-timeout-probe':'capabilities/evidence/T81-delivery/validation/text-timeout-probe-r1-result.json',
 'text-resume-audit':'capabilities/evidence/foundation/T81-final/text-r6/resume-audit.json',
 'text-visual-failure':'capabilities/evidence/foundation/T81-final/text-r5/jdk17-in_process/observations/negative/visual/vertical-font-position/page-1-visual.txt',
 'text-visual-probe':'capabilities/evidence/T81-delivery/validation/text-visual-probe-r1-result.json',
 'text-resume-four':'capabilities/evidence/T81-delivery/resume-text-four.py',
 'text-resume-four-standards':'capabilities/evidence/T81-delivery/reviews/standards-text-resume-four.md',
 'text-resume-four-spec':'capabilities/evidence/T81-delivery/reviews/spec-text-resume-four.md',
 'text-jdk11-failure':'capabilities/evidence/foundation/T81-final/text-r4/jdk11-hardened_worker/contract-tests.txt',
 'text-jdk11-probe':'capabilities/evidence/T81-delivery/validation/text-jdk11-worker-probe-r1-result.json',
 'text-resume-three':'capabilities/evidence/T81-delivery/resume-text-three.py',
 'text-resume-three-standards':'capabilities/evidence/T81-delivery/reviews/standards-text-resume-three.md',
 'text-resume-three-spec':'capabilities/evidence/T81-delivery/reviews/spec-text-resume-three.md',
 'text-resume-recipe':'capabilities/evidence/T81-delivery/resume-text.py',
 'text-recipe-history':'capabilities/evidence/T81-delivery/text-recipe-history.json',
 'wrapper-history':'capabilities/evidence/T81-delivery/validation-wrapper-history.json',
 'text-resume-standards':'capabilities/evidence/T81-delivery/reviews/standards-text-resume-recipe.md',
 'text-resume-spec':'capabilities/evidence/T81-delivery/reviews/spec-text-resume-recipe.md',
 'text-resume-standards-r2':'capabilities/evidence/T81-delivery/reviews/standards-text-resume-r2.md',
 'text-resume-spec-r2':'capabilities/evidence/T81-delivery/reviews/spec-text-resume-r2.md',
 'initial-archive':'capabilities/evidence/T81-delivery/initial-candidate-r7/source-archive.json',
 'final-archive':'capabilities/evidence/T81-delivery/final-candidate-r2/archive-map.json',
 'final-source-archive':'capabilities/evidence/T81-delivery/final-candidate-r2/source-archive.json',
 'initial-bundle':'capabilities/evidence/T81-delivery/initial-candidate-r7/artifact-archive.json',
 'final-bundle':'capabilities/evidence/T81-delivery/final-candidate-r2/artifact-archive.json',
 'previous-final-source-archive':'capabilities/evidence/T81-delivery/final-candidate/source-archive.json',
 'previous-final-bundle':'capabilities/evidence/T81-delivery/final-candidate/artifact-archive.json',
 'standards-review':'capabilities/evidence/T81-delivery/reviews/standards-final.md',
 'spec-review':'capabilities/evidence/T81-delivery/reviews/spec-final.md',
 'worktree':'capabilities/evidence/T81-delivery/'+worktree_name}
for name in names:links[name]=validation[name]['log']['path']
links['readiness']=readiness['log']['path']
refs={key:(ref(value) if (root/value).exists() else {'path':value,'sha256':None,'status':'pending'}) for key,value in links.items()}
if not draft: assert all(r.get('sha256') for r in refs.values())
criterion_links={
1:['coverage','fixtures','native','identities'],2:['coverage','fixtures','generator','native','identities'],
3:['coverage','native','identities'],4:['native','coverage','identities'],5:['native','coverage','identities'],
6:['coverage','native','identities','final-refresh'],7:['native','facade','identities'],
8:['facade','mapping','foundation','policy','identities'],9:['recorder','collector','identities'],
10:['collector','collector-tests','strict-runner','strict-controls','collector-final','collect-final-cli','initial-envelope-failure','envelope-diagnostic','envelope-repair-audit','envelope-repair-recipe','envelope-repair-standards','envelope-repair-spec'],
11:['identities','index','final-archive','final-source-archive','final-bundle'],12:['initial-refresh','final-refresh','final-refresh-interruption','initial-archive','initial-bundle','final-archive','final-source-archive','final-bundle','index','model-provision','model-provision-proof','initial-attachments-failure','initial-attachments-worker-failure','attachments-worker-probe','attachments-resume-audit','attachments-resume-recipe','attachments-resume-standards','attachments-resume-spec'],
13:['matrix','mapping','foundation','policy','certification','chinese','provenance','inventory-check-final'],
14:['readiness','identities'],15:['initial-validation','focused-validation','real-tool-validation'],
16:['full-verify-ubuntu-final','strict-runner','identities','standards-review'],
17:['collector-final','inventory-validate-final','inventory-generate-final','inventory-check-final','readiness','collect-final-cli'],
18:['standards-review','spec-review'],19:['identities','worktree','readiness','standards-review','spec-review'],
20:['worktree'],21:['worktree']}
criterion_links[12]+=['final-text-failure','text-worker-probe','text-resume-audit','text-resume-recipe',
                     'text-resume-standards','text-resume-spec','text-recovery-interruption',
                     'text-timeout-failure','text-timeout-probe','text-resume-standards-r2','text-resume-spec-r2',
                     'text-recipe-history','text-jdk11-failure','text-jdk11-probe','text-resume-three',
                     'text-resume-three-standards','text-resume-three-spec','text-visual-failure','text-visual-probe',
                     'text-resume-four','text-resume-four-standards','text-resume-four-spec','wrapper-history',
                     'final-incremental-failure','incremental-worker-probe']
criterion_links[12]+=['clear-metadata-recorder-failure','clear-metadata-products-probe','clear-metadata-resume-audit',
                     'clear-metadata-resume-recipe','clear-metadata-resume-standards','clear-metadata-resume-spec']
criterion_links[12]+=['final-attachments-visual-failure','attachments-visual-probe','attachments-visual-probe-first-timeout',
                     'attachments-visual-probe-recipe','attachments-visual-probe-first-recipe',
                     'final-attachments-coordinator-failure','attachments-coordinator-timing']
criterion_links[12]+=['previous-final-refresh','previous-final-index','previous-final-source-archive',
                     'previous-final-bundle','inventory-expectation-failure','inventory-expectation-source',
                     'inventory-expectation-focused','inventory-expectation-standards','inventory-expectation-spec']
criterion_links[12]+=['restage-closure']
criterion_links[12]+=['current-clear-metadata-failure','current-clear-metadata-probe','current-clear-metadata-resume',
                     'current-clear-metadata-resume-audit','current-clear-metadata-standards','current-clear-metadata-spec']
criterion_links[12]+=['relaunch-authority','relaunch-entry-audit','relaunch-attachment-recipe','relaunch-attachment-result','relaunch-attachment-audit','relaunch-source-standards','relaunch-source-spec','relaunch-attachment-failure','relaunch-attachment-failed-tests','relaunch-attachment-diagnostic','relaunch-attachment-diagnostic-recipe']
criterion_links[12]+=['relaunch-jdk21-failure','relaunch-jdk21-failed-tests','relaunch-jdk21-diagnostic',
                     'relaunch-jdk21-diagnostic-recipe','relaunch-last-tuple-recipe',
                     'relaunch-last-tuple-standards','relaunch-last-tuple-spec','relaunch-attachment-outcome',
                     'relaunch-attachment-outcome-recipe']
criterion_links[19]+=['relaunch-receipt-recipe']
criterion_links[12]+=['attachments-visual-retained-audit','attachments-visual-retained-audit-recipe']
criterion_links[21]+=['relaunch-authority']
criterion_links[16]+=['inventory-expectation-suite','release-tool-sandbox-failure','release-tool-ubuntu-suite']
notes={
6:'All 75 fixed experiments assert owned-root and target-directory cleanup. Existing public ownership/lifecycle suites and the three fresh encryption predecessors retain credential, descriptor, snapshot/cache/spill and terminal-path observations.',
9:'Every successful T20 blank PDF has four qualified PDF chains; the separate fifth chain certifies enforcement. Successful split-product features reuse the same candidate\'s complete T10 predecessor evidence.',
12:'Both initial and final cycles refresh eleven predecessors in the required order and preserve eight tuples each; limits then adds four IN_PROCESS scopes. Promotion changed bound inputs and therefore required the entire final cycle. Historical and failed attempts remain retained without relabeling.',
16:'Full verification ran inside the actual pinned Ubuntu/JDK17 environment. No shipped code, dependency, POM, build compatibility or CI changes were introduced, so the conditional verify-jdk-matrix.sh rule is not triggered; Standards review confirms this. Four actual JDK certifications, including artifact checks and strict zero-skip suites, are complete.',
20:'Optional local commit omitted. HEAD remains the baseline; no commit or Signed-off-by trailer is claimed.',
21:'The original execution began clean; this authorized relaunch retained its dirty ticket worktree and all evidence. Every changed/untracked deliverable is owned by issue #81; unrelated work was untouched.'}
criteria=[]
for item in read('criteria-authority.json')['completion-criteria']:
    ident=item['id'];criteria.append({**item,'status':('pending-review' if draft and ident in (18,19) else 'satisfied'),'evidence':[refs[n] for n in criterion_links[ident]],'observation':notes.get(ident,'See bound original/live records and retained source observations.')})
assert len(criteria)==21
ac_map={1:[1,2],2:[3,4,5,6,7],3:[8,13],4:[8,9],5:[9,10,11,12],6:[8],7:[13,14],8:[15,16,17,18],9:[6,13,16,20,21]}
classification={1:'demonstrably incomplete',2:'unverified',3:'evidenced complete; preserved',4:'demonstrably incomplete',5:'demonstrably incomplete',6:'unverified',7:'demonstrably incomplete',8:'demonstrably incomplete for issue #81',9:'evidenced complete; preserved'}
map_doc={'contract':ref('capabilities/evidence/T81-delivery/execution-contract.md'),'baseline':identity['baseline-and-head'],
 'current-identities':refs['identities'],'completion-criteria':criteria,
 'issue-acceptance-criteria':[{'id':'AC'+str(i),'baseline-classification':classification[i],'status':('pending-review' if draft and set(ids)&{18,19} else 'satisfied'),'completion-criterion-ids':ids} for i,ids in ac_map.items()],
 'remaining-unrelated-foundation-blockers':blocked,'optional-commit':'omitted'}
(delivery/('evidence-map-draft.json' if draft else 'evidence-map.json')).write_text(json.dumps(map_doc,indent=2)+'\n')
(delivery/('validation-results-draft.json' if draft else 'validation-results.json')).write_text(json.dumps({'status':'pending-review' if draft else 'pass','gates':validation,'expected-unrelated-readiness-failure':readiness,'reviews':reviews},indent=2)+'\n')

def link(name):
    p=Path(links[name]);rel=Path(__import__('os').path.relpath(root/p,delivery)).as_posix()
    return '['+name+']('+rel+')'
receipt='# Issue #81 delivery receipt'+(' — draft pending final reviews' if draft else '')+'\n\n'
receipt+=('Certification and validation are complete; final reviews are pending under the sole ' if draft else 'Completed the sole ')+'[execution contract](execution-contract.md) against baseline and unchanged HEAD `'+identity['baseline-and-head']+'`. The worktree certifies `document.hostile-input-limits`, `T20-hostile-input-limits` and Foundation `limits` for exactly four actual Ubuntu 24.04 / Linux x86-64 JDK 8/11/17/21 tuples, Native `IN_PROCESS`, with corresponding existing Facade `IN_PROCESS` observations.\n\n'
receipt+='The existing Native implementation and #80 encryption behavior are retained. Changes add independently authored fixed fixtures, public observations, strict zero-skip execution, five-chain collection, rejection controls and coordinated inventories/docs. The approved Native-only policy-control decision and cooperative modeled-usage guarantee remain in force. Windows/macOS are explicitly uncertified.\n\n'
receipt+='Candidate SHA-256: `'+identity['candidate-sha256']+'`. Foundation contract SHA-256: `'+identity['foundation-contract-sha256']+'`. The [identity summary](identity-summary.json) retains the exact source/artifact/harness/corpus/tool identities, image digests, observed OS, Temurin vendor/build/java executable hashes, execution settings and all 92 current scope references.\n\n'
receipt+='There are 372 current chain records: eleven predecessors × eight declared tuples × four chains, then four limits tuples × five chains. Each limits tuple executes all 75 closed experiments and all 133 public/artifact tests in both its original and live replay, with zero failures, ignored tests or assumption skips. Eight successful blank outcomes per original tuple receive independent PDF chains. Additional live collection through the documented CLI is retained.\n\n'
receipt+='| JDK | Exact image | Native / Facade | Five chains |\n| --- | --- | --- | --- |\n'
for item in identity['limits-certifications']:
    env=item['actual-environment'];major=env['jdk-major'];receipt+='| '+str(major)+' | `'+env['image']+'` | IN_PROCESS / IN_PROCESS | PASS |\n'
receipt+='\nThe initial qualification preceded promotion. Because promotion changed bound inputs, a fresh final stage and the complete ordered predecessor refresh preceded final limits certification. Exact initial sources, artifacts and records remain archived; historical identities were not relabeled. See '+link('initial-refresh')+', '+link('final-refresh')+', '+link('initial-archive')+' and '+link('final-archive')+'.\n\n'
receipt+='| Validation | Result | Retained command/output |\n| --- | --- | --- |\n'
for name in names:receipt+='| '+name+' | PASS (exit 0) | '+link(name)+' |\n'
receipt+='| Live Foundation readiness | limits, prerequisites and all refreshed predecessors SATISFIED; '+str(len(blocked))+' unrelated obligations remain blocked | '+link('readiness')+' |\n'
receipt+='\nThe smallest initial Native gate passed all 19 tests. Focused recorder/runner and real-tool checks passed. The conditional JDK matrix is not triggered because only acceptance infrastructure and documentation changed; no shipped code or build compatibility changed. Four actual JDK certifications still ran. Broad verification opt-ins do not replace the mandatory strict original/live suites. See [validation results]('+('validation-results-draft.json' if draft else 'validation-results.json')+').\n\n'
receipt+='Earlier unsuccessful development attempts remain retained and unindexed. The first attachment recorder stopped before independent observations because its pinned Arlington model cache was missing. The existing provisioner restored all 1,234 exact model files; all 3,361 T80 cached identities matched, and staged source/contracts/harness stayed unchanged before a fresh retry. That retry passed three complete attachment tuples, then failed the existing JDK11 Worker one-second child-exit check; the unchanged full 24-test suite passed diagnostic replay. The reviewed recovery recipe retained those three original records byte-for-byte, executed all five remaining full tuples in fresh directories, checked original/live environment identities and all 80 existing predecessor scopes before publication, and published only the complete eight-tuple obligation. No timeout or Worker product behavior changed; the intermittent exit cause was not established. Host-Debian Worker startup failures are separate from the successful pinned-Ubuntu final gate. An initial predecessor JDK11 Worker transaction run reported a child-exit timeout; its exact class passed three isolated replays, and the entire fresh eight-tuple transaction obligation passed. The cause was not established and no Worker product change was made. The initial limits index publication correctly refused raw live observer JSON misclassified as ordinary findings: container paths were interpreted as repository references. A reviewed metadata-only envelope recipe moved only the exact raw observer refs into the existing environment-observations role, preserved all original bytes and reference multisets, and accepted only four complete limits scopes plus all 88 predecessors through unchanged verification. The collector/driver role composition was then fixed in bound source with failure controls, and the entire final candidate was staged and recertified normally. Early coverage/count/expectation errors and a stale generated-readiness full-gate failure were corrected before final staging. None supplies accepted final certification.\n\n'
receipt+='The first final-refresh wrapper was stopped when its three-hour whole-sequence timeout was identified as too short for the required tool runs. Its partial transaction files remain unindexed; the full transaction obligation restarted in a fresh directory. Only that orchestration timeout was removed, with all existing per-command and product timeouts preserved. See '+link('final-refresh-interruption')+'.\n\n'
receipt+='The first final incremental attempt completed JDK8 IN_PROCESS, then JDK8 Worker reported the existing child-exit check. Its unchanged full 36-test diagnostic suite passed. The entire eight-tuple obligation restarted through the unchanged driver in a fresh directory; no partial tuple or diagnostic supplied certification and no Worker or timeout changed. The intermittent cause was not established. See '+link('final-incremental-failure')+' and '+link('incremental-worker-probe')+'.\n\n'
receipt+='The first text recovery wrapper inherited the same overall timer and was stopped before completing a fresh tuple. Its partial text-r2 files remain unindexed. The recovery restarted in text-r3 with the same reviewed recipe and individual command limits, after removing only the overall wrapper timer. See '+link('text-recovery-interruption')+'.\n\n'
receipt+='The final text predecessor first completed JDK8 IN_PROCESS, then its JDK8 Worker suite reported the existing child-exit check after a successful response. The unchanged full 126-test suite passed diagnostic replay. A fresh text-r3 attempt then exceeded a different existing test\'s ten-second timeout; that unchanged method passed three isolated diagnostic runs in 3.6–3.8 seconds. The next text-r4 attempt completed JDK8 Worker and JDK11 IN_PROCESS, then JDK11 Worker reported the existing child-exit and test-timeout checks. Its full unchanged diagnostic suite also passed. Text-r5 completed JDK11 Worker, then the JDK17 IN_PROCESS recorder rejected an indeterminate visual control: its pinned PDFium render was unavailable during that invocation. The precise cause was not retained or established. That exact control was detected in three isolated runs with unchanged input, profile, tools and bounds; the entire failed tuple was rerun. These probes establish no certification. The reviewed text-r6 recovery retained all four complete tuples byte-for-byte, ran four complete tuples in fresh directories, compared all original/live environment identities and completed closing observations, and preflighted all forty existing scopes before unchanged locked publication. Failed attempts remain historical; no Worker, product, suite or per-command timeout changed. See '+link('text-resume-audit')+', '+link('text-worker-probe')+', '+link('text-timeout-probe')+', '+link('text-jdk11-probe')+' and '+link('text-visual-probe')+'.\n\n'
receipt+='The first final clear-metadata attempt completed JDK8 IN_PROCESS/Worker and JDK11 IN_PROCESS. Its JDK11 Worker suite passed all 23 tests, then the recorder reported the existing child-exit check during product creation. The unchanged complete product sequence passed three diagnostic-only runs through the existing products-only recorder mode; the independent tool observers were omitted only for these diagnostics. The reviewed recovery retained three complete tuples byte-for-byte, ran five full tuples in fresh directories, checked original/live environments and all 72 existing scopes, and published only the complete eight-tuple obligation through unchanged verification. No Worker, product or individual timeout changed; the intermittent cause was not established. See '+link('clear-metadata-recorder-failure')+', '+link('clear-metadata-products-probe')+' and '+link('clear-metadata-resume-audit')+'.\n\n'
receipt+='The first final attachment attempt passed JDK8 IN_PROCESS\'s 24 tests, then its independent PDFium render and process-stop wait timed out. The recorder refused certification. Command responses were unusually slow and actual CPU/memory/I/O pressure was observed; the precise cause remains unproven. The first isolated diagnostic exceeded its outer 120-second wrapper bound without retaining an observer result. Its exact helper and empty observer log remain historical. After resource pressure fell, the exact original PDF\'s unauthenticated page-copy and PDFium/ImageMagick observation passed three isolated runs through unchanged pinned observer code and existing per-tool bounds. Only the diagnostic wrapper allowed more startup/pin-check time. These diagnostics supplied no certification. The entire eight-tuple attachment obligation restarted in a fresh directory using the unchanged driver, with original failures preserved and no product, Worker or observer-bound changes. See '+link('final-attachments-visual-failure')+', '+link('attachments-visual-probe-first-timeout')+' and '+link('attachments-visual-probe')+'.\n\n'
receipt+='The second final attachment attempt reached the existing 15-minute recorder coordinator bound during negative-control qualification. It retained 6,259 files without completing certification. A diagnostic comparison retained the earlier successful original\'s approximately 867-second file span versus this attempt\'s approximately 908-second span; these timestamps are not individual command timings and establish no cause. The entire obligation restarted again in a fresh directory with unchanged coordinator and per-tool bounds. See '+link('final-attachments-coordinator-failure')+' and '+link('attachments-coordinator-timing')+'.\n\n'
receipt+='After the complete promoted candidate passed all 92 scopes, required full verification found a stale real-worktree inventory test that demanded BLOCKED #79/#80 despite their actual SATISFIED evidence. The test now requires exact truthful SATISFIED lines or BLOCKED prefixes with nonempty diagnostics for #79/#80/#81, preserving clean-checkout behavior and exact controlled-fixture success/rejection checks. Final live certification still requires all selected obligations SATISFIED. Both review axes approved the correction. Because this changed one bound test source, the complete earlier candidate, index, source/artifact archives and failed gate remain historical; a new stage and the entire ordered eleven-predecessor/four-limits refresh produced the current candidate. No historical record was relabeled or reused as current certification. See '+link('inventory-expectation-failure')+', '+link('inventory-expectation-source')+', '+link('previous-final-index')+' and '+link('final-refresh')+'.\n\n'
receipt+='The current candidate’s clear-metadata refresh completed seven full tuples, then the JDK21 Worker suite failed the existing elapsed workflow bound at owner rewrite (JUnit suite duration 1,273.116 seconds). The unchanged full 23-test diagnostic replay passed in 161.438 seconds; its pressure snapshots establish no cause and it supplies no certification. The reviewed recovery preserved all seven original complete scopes and the failed transcript byte-for-byte, ran the entire remaining tuple afresh through unchanged suite, recorder and four-chain live collection, observed all four environments before/after, protected all 72 prior current-candidate scopes and published only the exact 80-scope union. No product, Worker, observer or certification bound changed. See '+link('current-clear-metadata-failure')+', '+link('current-clear-metadata-probe')+' and '+link('current-clear-metadata-resume-audit')+'.\n\n'

receipt+='The user authorized a fresh relaunch on 2026-10-06 after the prior thread died with a model-capacity error. HEAD still matched the recorded baseline, the staged candidate matched the retained final-candidate-r2 archive, and the entry audit verified all 80 indexed scopes plus five complete attachment tuples. The interrupted JDK17 Worker recorder had no complete chain records and was preserved byte-for-byte. The first relaunch recovery stopped when its full 24-test JDK17 Worker suite reported the existing child-exit check after a completed response. Its unchanged full-suite diagnostic replay passed all 24 tests in 234.221 seconds. The recovery retried all three unfinished tuples in a fresh directory and completed JDK17 Worker and JDK21 IN_PROCESS, including their recorders and four-chain live collection. The final JDK21 Worker suite then reported the same child-exit check; its unchanged full-suite diagnostic replay passed all 24 tests in 257.805 seconds. Pressure snapshots establish no cause, and neither diagnostic supplies certification. The reviewed last-tuple recovery sealed both completed evidence trees, retained exactly seven complete tuples, reran the entire remaining JDK21 Worker tuple in a fresh directory, checked all original/live environments and files, protected all 80 prior current scopes, and published only the exact 88-scope union through unchanged index verification. Limits certification then followed in order. Failed and interrupted files remain intact and unindexed. No product, tool, suite or per-command bound changed. See '+link('relaunch-attachment-failure')+', '+link('relaunch-attachment-diagnostic')+', '+link('relaunch-jdk21-failure')+', '+link('relaunch-jdk21-diagnostic')+', '+link('relaunch-last-tuple-recipe')+', '+link('relaunch-authority')+', '+link('relaunch-entry-audit')+', '+link('relaunch-attachment-result')+' and '+link('relaunch-attachment-audit')+'.\n\n'

receipt+='Further full-tuple retries stopped without certification. All '+str(len(attachment_outcome['failed-attempts']))+' unsuccessful relaunch recovery attempts, their actual validation commands/results, suite transcripts and original hashes are linked in '+link('relaunch-attachment-outcome')+'. Only the complete successful recovery supplied accepted attachment evidence; the reviewed helper, suite, recorder, tools and bounds remained unchanged.\n\n'
receipt+='Draft generation exposed a historical diagnostic wrapper/detail-summary filename collision. The original wrapper record and all three actual visual-probe outputs remain unchanged. A retained audit verifies those existing outputs, command stdout/stderr hashes, exact input/raster hashes and zero-error comparisons; it reconstructs no lost timestamps or pressure snapshots, reruns no probe and supplies no certification. The receipt consumer now uses these actual records. See '+link('attachments-visual-retained-audit')+' and '+link('attachments-visual-retained-audit-recipe')+'.\n\n'
receipt+='Final baseline-relative '+link('standards-review')+' and '+link('spec-review')+(' are pending; no final review result is claimed.' if draft else ' report no unresolved applicable findings.')+ ' The [evidence map]('+('evidence-map-draft.json' if draft else 'evidence-map.json')+') maps all nine issue criteria and all 21 exact contract completion criteria to observable retained evidence.\n\n'
receipt+='| Contract item | Result | Evidence |\n| --- | --- | --- |\n'
for criterion in criteria:
    receipt+='| C'+str(criterion['id'])+' | '+criterion['status'].upper()+' | '+', '.join(link(n) for n in criterion_links[criterion['id']])+' |\n'
receipt+='\nRemaining unrelated Foundation blockers: '+', '.join('`'+name+'` (#'+str(value['slice'])+')' for name,value in blocked.items())+'. Complete diagnostics are retained in [the evidence map]('+('evidence-map-draft.json' if draft else 'evidence-map.json')+') and live readiness output. Overall Foundation remains NOT READY; requirements and producer labels were preserved.\n\n'
receipt+='The [worktree audit]('+worktree_name+') confirms unchanged baseline HEAD, no staged changes, and only this ticket\'s worktree/untracked deliverables. The optional local commit was omitted. No push, PR, merge, issue edit/closure, candidate signing/upload, deployment or release publication was performed. Existing release-tool regression tests use disposable synthetic signed fixtures only. Delivery stops at this reviewed worktree.\n'
(delivery/('receipt-draft.md' if draft else 'receipt.md')).write_text(receipt)
print('Wrote evidence map for all 21 completion criteria and all 9 issue criteria, plus '+('the draft receipt pending final reviews' if draft else 'the final delivery receipt'))
