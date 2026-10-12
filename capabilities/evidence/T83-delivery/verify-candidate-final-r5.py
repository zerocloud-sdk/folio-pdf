"""Run mandatory JDK/full verification without changing the frozen candidate."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import yaml
root=Path(__file__).resolve().parents[3];delivery=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('t83_verify_five',root/'scripts/t03-foundation.py')
driver=importlib.util.module_from_spec(spec);spec.loader.exec_module(driver)
contract=yaml.safe_load((root/'capabilities/foundation-release.yaml').read_text())
build=root/'target/foundation-0.1.0/build-inputs.json'
frozen=json.loads((delivery/'source-freeze-final-r5.json').read_text())
inputs={key:frozen[key] for key in ('inputs','contract-inputs')}
receipt=driver.require_staged_build(root,contract,build)
assert receipt==json.loads((delivery/'candidate-final-r5/build-inputs.json').read_text())
assert driver.capture_build_inputs(root,contract)==inputs
index=root/'capabilities/foundation-evidence.yaml';index_hash=driver.sha256(index)
steps=[('jdk-matrix-final-r1','host','./scripts/verify-jdk-matrix.sh'),
       ('full-verify-final-r6','ubuntu','./mvnw','-B','-ntp','verify')]
output=delivery/'candidate-final-r5-verification.json';assert not output.exists();results=[]
for name,mode,*command in steps:
    driver.require_staged_build(root,contract,build)
    assert driver.capture_build_inputs(root,contract)==inputs and driver.sha256(index)==index_hash
    process=subprocess.run([sys.executable,str(delivery/'run-gate.py'),name,mode,*command],cwd=root)
    row={'gate':name,'exit-code':process.returncode,'source-staged-index-unchanged':False,
         'result':driver.reference(root,delivery/'validation'/(name+'-result.json'))}
    results.append(row);driver.write_json(output,results)
    driver.require_staged_build(root,contract,build)
    assert driver.capture_build_inputs(root,contract)==inputs and driver.sha256(index)==index_hash
    row['source-staged-index-unchanged']=True;driver.write_json(output,results)
    if process.returncode:sys.exit(process.returncode)
print('Actual four-image JDK matrix and explicit full verify passed; source, staged candidate and index unchanged.')
