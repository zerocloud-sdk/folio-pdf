import collections, datetime, hashlib, importlib.util, json, os, re, subprocess, sys
from pathlib import Path
import yaml
root=Path('/workspace/folio-pdf'); out=root/'capabilities/evidence/T78-delivery'
os.environ['FOLIO_FOUNDATION_PYTHON_ROOT']=str(root/'.build-cache/foundation-python')
expected=['transactions','values','pages','metadata','annotations','text','images','incremental','password-baseline']
def digest(path):
 with path.open('rb') as stream: return hashlib.file_digest(stream,'sha256').hexdigest()
def ref(path): return {'path':path.relative_to(root).as_posix(),'sha256':digest(path)}
def checked(reference):
 path=root/reference['path']; assert digest(path)==reference['sha256'],str(path)
 return json.loads(path.read_text())
def props(path): return dict(line.split('=',1) for line in path.read_text().splitlines() if line and not line.startswith(('#','!')))
assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()=='861c4ba81c7aecf9fba15052859f8cc80fd0259d'
contract=yaml.safe_load((root/'capabilities/foundation-release.yaml').read_text())
freeze=json.loads((out/'source-freeze.json').read_text())
spec=importlib.util.spec_from_file_location('foundation',root/'scripts/t03-foundation.py'); module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
current=module.capture_build_inputs(root,contract)
assert current=={key:freeze[key] for key in ['inputs','contract-inputs']},'Final source or contract drift'
staged=module.require_staged_build(root,contract,root/'target/foundation-0.1.0/build-inputs.json')
index_path=root/'capabilities/foundation-evidence.yaml'; index=json.loads(index_path.read_text())
assert index['candidate']==staged['candidate']
identities=json.loads((root/'capabilities/evidence/foundation/T78-final/password-baseline/identities.json').read_text())
by_obligation=collections.defaultdict(list)
for entry in index['certifications']: by_obligation[entry['obligation']].append(entry)
assert set(by_obligation)==set(expected)
environments={item['profile']:checked(item['record']) for item in index['environments']}
assert len(environments)==4
profile_ids={f'ubuntu-24.04-linux-x86-64-jdk{jdk}' for jdk in [8,11,17,21]}
assert set(environments)==profile_ids
summaries=[]; baseline_products=set(); evidence_refs=[]
for obligation in expected:
 directory=root/'capabilities/evidence/foundation/T78-final'/obligation
 assert json.loads((directory/'identities.json').read_text())==identities
 entries=by_obligation[obligation]
 assert {(entry['environment'],entry['execution-profile']) for entry in entries}=={(env,mode) for env in profile_ids for mode in ['IN_PROCESS','HARDENED_WORKER']}
 assert len(entries)==8
 tuples=[]
 for entry in entries:
  config=checked(entry['configuration']); assert config['candidate-sha256']==identities['Candidate']
  assert config['execution-profile']==entry['execution-profile']
  records=[checked(reference) for reference in entry['records']]
  assert len(records)==4 and {x['chain'] for x in records}=={'syntax','standards','semantic','visual'}
  for record in records:
   assert record['result']=='pass' and record['obligation']==obligation
   assert record['candidate-sha256']==identities['Candidate'] and record['contract-sha256']==identities['Contract']
   assert record['execution-profile']==entry['execution-profile']
   assert record['execution-configuration-sha256']==entry['configuration']['sha256']
   report=checked(record['report']); assert report['result']=='pass'
   assert report['products'] and report['negative-controls'] and report['findings']
   if obligation=='password-baseline':
    assert len(report['products'])==68 and len({x['path'] for x in report['products']})==68
    for product in report['products']:
     assert digest(root/product['path'])==product['sha256']
     baseline_products.add(product['path'])
  scope=(root/entry['configuration']['path']).parent
  test_text=(scope/'contract-tests.txt').read_text()
  match=re.search(r'OK \((\d+) tests\)',test_text);assert match
  row={'environment':entry['environment'],'native-execution-profile':entry['execution-profile'],'contract-test-count':int(match.group(1)),'chains':dict.fromkeys(['syntax','standards','semantic','visual'],'pass'),'configuration':entry['configuration'],'records':entry['records']}
  if obligation=='password-baseline':
   assert int(match.group(1))==44
   declaration=props(scope/'observations/result.properties')
   assert declaration['native-execution-profile']==entry['execution-profile'] and declaration['facade-execution-profile']=='IN_PROCESS'
   assert all(declaration[key]=='pass' for key in ['syntax','standards','semantic','visual'])
   row.update({'facade-execution-profile':'IN_PROCESS','products':68,'observations':ref(scope/'observations/result.properties'),'coverage':ref(scope/'observations/coverage.json')})
  tuples.append(row)
 summaries.append({'obligation':obligation,'result':'pass','certification-count':len(entries),'independent-chain-count':32,'identity-record':ref(directory/'identities.json'),'observed-index':ref(directory/'observed-index.json'),'tuples':tuples})
assert len(baseline_products)==544
matrix=yaml.safe_load((root/'capabilities/capability-matrix.yaml').read_text()); caps={c['id']:c for c in matrix['capabilities']}
aggregate=caps['document.version-password-security']; baseline=caps['document.version-password-security.baseline']
assert aggregate['status']=='experimental' and baseline['status']=='compatible'
assert baseline['parent-capability']==aggregate['id']
assert baseline['dependency-gates']==aggregate['dependency-gates']
assert all(gate['capability']!=aggregate['id'] for gate in baseline['dependency-gates'])
obligations={o['id']:o for o in contract['obligations']}
assert obligations['security']['members']==['password-baseline','password-clear-metadata','password-attachments']
assert obligations['password-clear-metadata']['slice']==79 and obligations['password-attachments']['slice']==80
members=[member for family in obligations['password-baseline']['facade-families'] for member in family['mappings']]
assert len(members)==len(set(members))==61
readiness_path=out/'validation/inventory-readiness-final.txt';readiness=readiness_path.read_text()
assert f"Candidate identity: {identities['Candidate']}" in readiness and f"Contract identity: {identities['Contract']}" in readiness
assert 'Foundation 0.1.0: NOT READY' in readiness
blockers=[line for line in readiness.splitlines() if line.startswith('BLOCKED ')]
assert blockers and not any(line.startswith('BLOCKED release:') for line in blockers)
assert not any(re.match(r'BLOCKED '+re.escape(name)+r' \(',line) for line in blockers for name in expected)
remaining=sorted({re.match(r'BLOCKED ([^ ]+)',line).group(1) for line in blockers})
assert {'security','password-clear-metadata','password-attachments'}.issubset(remaining)
full_checks=json.loads((out/'validation/full-validation.json').read_text())
assert [(item['name'],item['result']) for item in full_checks]==[('full-verify','pass'),('jdk-matrix','pass')]
certification_checks=json.loads((out/'validation/certification-validation.json').read_text())
assert len(certification_checks)==10 and all(item['result']=='pass' for item in certification_checks)
inventory_checks=json.loads((out/'validation/inventory-final.json').read_text())
assert [item['result'] for item in inventory_checks]==['pass','pass','pass','not-ready']
live=json.loads((out/'validation/live-qualification.json').read_text())
assert live['result']=='pass' and live['replay-result']=='pass' and live['certified-source-unchanged']
assert len(live['isolated-mode-controls'])==2 and all(item['result']=='detected' for item in live['isolated-mode-controls'])
assert digest(root/live['qualification-driver']['path'])==live['qualification-driver']['sha256']
assert live['source-observations']=='capabilities/evidence/foundation/T78-final/password-baseline/jdk8-in_process/observations'
for name,expected_hash in live['source-binding'].items():
 assert digest(root/live['source-observations']/name)==expected_hash,'Qualification source no longer matches certification'
for check in full_checks+certification_checks+inventory_checks+[live]:
 assert digest(root/check['report'])==check['sha256'],'Changed validation report'
record={'schema-version':1,'result':'pass','recorded-at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'comparison-baseline':freeze['comparison-baseline'],'candidate-sha256':identities['Candidate'],'contract-sha256':identities['Contract'],'source-freeze':ref(out/'source-freeze.json'),'source-input-count':len(current['inputs']),'contract-input-count':len(current['contract-inputs']),'evidence-index':ref(index_path),'certifications':72,'independent-chain-records':288,'baseline-product-artifacts':544,'facade-mappings':61,'aggregate-status':aggregate['status'],'baseline-status':baseline['status'],'environments':environments,'obligations':summaries,'readiness':{'result':'not-ready-outside-selected-scope','report':ref(readiness_path),'blocker-count':len(blockers),'remaining-obligations':remaining},'live-qualification':ref(out/'validation/live-qualification.json'),'optional-local-commit':None}
(out/'validation/final-evidence-audit.json').write_text(json.dumps(record,indent=2)+'\n')
print(json.dumps({key:value for key,value in record.items() if key not in ['environments','obligations']},indent=2))
