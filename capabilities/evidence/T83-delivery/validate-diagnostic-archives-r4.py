"""Exercise the final archive checks against the three actual retained archives."""
import ast
import contextlib
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import zipfile

import yaml

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
sys.path.insert(0, str(root / 'scripts'))
spec = importlib.util.spec_from_file_location('diagnostic_archive_validation', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
receipt = driver.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
frozen = json.loads((delivery / 'source-freeze-final-r4.json').read_text())
assert driver.capture_build_inputs(root, contract) == {key: frozen[key] for key in ('inputs', 'contract-inputs')}
audit = delivery / 'audit-final.py'
names = {'verified_diagnostic_archive', 'audit_diagnostic_archive', 'audit_method_archive'}
functions = [node for node in ast.parse(audit.read_text()).body
             if isinstance(node, ast.FunctionDef) and node.name in names]
assert {node.name for node in functions} == names
namespace = {'root': root, 'delivery': delivery, 'driver': driver, 'contextlib': contextlib,
             'hashlib': hashlib, 'json': json, 'zipfile': zipfile}
exec(compile(ast.Module(body=functions, type_ignores=[]), str(audit), 'exec'), namespace)
for manifest, ledger, replay_root, count, require_clear in (
        ('worker-exit-products.zip.json', 'worker-exit-replays-r2.json', '.build-cache/t83-worker-exit-replay-r2', 68, False),
        ('attachments-worker-exit-products.zip.json', 'attachments-exit-replays-r1.json', '.build-cache/t83-attachments-exit-replay-r1', 62, True)):
    result = namespace['audit_diagnostic_archive'](
        delivery / 'diagnostics' / manifest,
        json.loads((delivery / 'diagnostics' / ledger).read_text()), replay_root, count,
        require_clear=require_clear)
    print(manifest + ': ' + str(result['entries']) + ' entries and all required public bindings pass')
result = namespace['audit_method_archive'](
    delivery / 'diagnostics/input-open-method-replays.zip.json',
    delivery / 'diagnostics/input-case-exit-replays-r1.json')
assert result['entries'] == 12
print('input-open-method-replays.zip.json: 12 entries, ten exact launch commands/logs and Java 8 helper pass')
nodes = ast.parse(audit.read_text()).body
def assignment_index(name):
    matches = [index for index, node in enumerate(nodes)
               if isinstance(node, ast.Assign)
               and any(isinstance(target, ast.Name) and target.id == name for target in node.targets)]
    assert len(matches) == 1, name
    return matches[0]
start = assignment_index('fifth_gate_path')
end = assignment_index('ordinary')
assert start < end
namespace.update({'Path': Path, 'receipt': receipt,
    'fifth_refresh': json.loads((delivery / 'refresh-final-r5/results.json').read_text())})
exec(compile(ast.Module(body=nodes[start:end], type_ignores=[]), str(audit), 'exec'), namespace)
assert namespace['duration_audit']['certifications-exported'] == 0
print('Current audit fifth-failure/duration checks pass: original 7062 files, sealed 7058 entries, 1797 bounded tool commands, failed probe preserved.')
print('Audit script SHA-256: ' + driver.sha256(audit))
print('Validation script SHA-256: ' + driver.sha256(Path(__file__)))
