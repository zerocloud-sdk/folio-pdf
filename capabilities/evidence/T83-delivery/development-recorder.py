"""Development-only actual-artifact recorder exercise; never exports certification."""
import json
import os
from pathlib import Path
import shutil
import sys
import yaml
root = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(root / 'scripts'))
import t21_foundation_reports as worker
module = worker.foundation()
receipt = json.loads((root / 'target/foundation-0.1.0/build-inputs.json').read_text())
profile = next(p for p in yaml.safe_load((root / 'capabilities/foundation-environments.yaml').read_text())['profiles'] if p['identity']['jdk-major'] == 17)
scope = root / 'capabilities/evidence/T83-delivery' / sys.argv[1]
scope.mkdir()
helper = Path(os.environ['FOLIO_HARFBUZZ_HELPER']).resolve(strict=True)
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
cp = ':'.join('/workspace/' + path.relative_to(root).as_posix() for path in module.certification_classpath(root, contract, receipt))
plan = module.execution_plan(root, scope, profile['identity']['image'], helper, cp, module.certification_case('recovery'), 'HARDENED_WORKER')
if len(sys.argv) == 3:
    shutil.copytree(root / sys.argv[2] / 'scale-observations', scope / 'scale-observations')
else:
    module.write_json(scope / 'contract-tests-command.json', plan['contract-tests-command'])
    module.run_logged(plan['contract-tests-command'], scope / 'contract-tests.txt', cwd=root, timeout=1800)
module.write_json(scope / 'recorder-command.json', plan['recorder-command'])
module.run_logged(plan['recorder-command'], scope / 'recorder.txt', cwd=root, timeout=1800)
print('Development actual-artifact recorder passed; no certification exported', flush=True)
