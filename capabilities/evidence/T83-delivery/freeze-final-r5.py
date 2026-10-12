"""Freeze the narrow reviewed Worker termination correction and mandatory inventory."""
import datetime
import importlib.util
import json
from pathlib import Path
import subprocess
import yaml
root=Path(__file__).resolve().parents[3]
delivery=Path(__file__).resolve().parent
baseline='42fe5c797711070b319b9419e14fcb7adfa98735'
assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()==baseline
assert not subprocess.check_output(['git','diff','--cached','--name-only'],cwd=root,text=True).strip()
spec=importlib.util.spec_from_file_location('t83_freeze_five',root/'scripts/t03-foundation.py')
driver=importlib.util.module_from_spec(spec);spec.loader.exec_module(driver)
contract=yaml.safe_load((root/'capabilities/foundation-release.yaml').read_text())
inputs=driver.capture_build_inputs(root,contract)
old=json.loads((delivery/'source-freeze-final-r4.json').read_text())
assert inputs['contract-inputs']==old['contract-inputs']
before={item['path']:item['sha256'] for item in old['inputs']}
after={item['path']:item['sha256'] for item in inputs['inputs']}
added={'pdf-document/src/test/java/net/zerocloud/pdf/HardenedWorkerTerminationWaitTest.java'}
assert after.keys()-before.keys()==added and not before.keys()-after.keys()
changed={path for path in before if after[path]!=before[path]}
assert changed=={'PROVENANCE.md','docs/t22-certification.md',
 'pdf-document/src/main/java/net/zerocloud/pdf/HardenedWorkerEngine.java',
 'capabilities/profiles/T22-worker-recovery-scale/coverage.json',
 'capabilities/profiles/T22-worker-recovery-scale/mandatory-tests.txt',
 'scripts/t03-foundation.py','scripts/tests/test_t22_foundation.py'}
assert len(after)==2888 and len(inputs['contract-inputs'])==32
for name,code in (('recovery-protocol-focused-after-exit-fix-r3',0),
 ('python-recovery-worker-guards-r16',0),('inventory-prefreeze-generate-r6',0),
 ('worker-termination-regression-red-r2',1),('worker-handshake-stage4-red-r3',1)):
 gate=json.loads((delivery/'validation'/(name+'-result.json')).read_text())
 assert gate['exit-code']==code
 assert driver.reference(root,root/gate['log']['path'])==gate['log']
red=json.loads((delivery/'diagnostics/worker-handshake-stage4-red-r3/result.json').read_text())
assert red['exit-code']==1 and red['source-old-stage-index-unchanged']
assert red['diagnostic-only'] and red['certifications-exported']==0
for ref in [red['helper-source'],red['compiler'],red['compile-log'],*red['loaded-helper-classes']]:
 assert driver.reference(root,root/ref['path'])==ref
assert 'Tests run: 151, Failures: 0, Errors: 0, Skipped: 0' in (delivery/'validation/recovery-protocol-focused-after-exit-fix-r3.txt').read_text()
assert 'Ran 35 tests' in (delivery/'validation/python-recovery-worker-guards-r16.txt').read_text()
destination=delivery/'source-freeze-final-r5.json';assert not destination.exists()
driver.write_json(destination,{**inputs,'recorded-at':datetime.datetime.now(datetime.timezone.utc).isoformat(),
 'baseline':baseline,'source-changes-since-final-r4':sorted(changed|added),
 'controlled-old-runtime-regression':driver.reference(root,delivery/'diagnostics/worker-handshake-stage4-red-r3/result.json')})
print('Frozen final-r5: 2888 source inputs,32 unchanged contract inputs;166 mandatory recovery cases.')
