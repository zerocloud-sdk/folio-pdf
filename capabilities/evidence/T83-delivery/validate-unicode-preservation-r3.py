"""Meaningful isolated altered/resealed-history checks; never mutate real evidence."""
import importlib.util
import json
from pathlib import Path
import shutil
import tempfile

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
path = delivery / 'diagnostics/unicode-elapsed-preservation-r2.py'
spec = importlib.util.spec_from_file_location('t83_unicode_guard_cases', path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
actual = module.validate()
assert actual['certifications-exported'] == 0
references = {item['path']: item for item in actual['retained-inputs']}
results = []
for kind in ('altered-ledger', 'resealed-log', 'resealed-report', 'omitted-prior-witness'):
    with tempfile.TemporaryDirectory(prefix='t83-unicode-guard-', dir=root / '.build-cache') as scratch:
        fixture = Path(scratch)
        for name, reference in references.items():
            target = fixture / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(root / name, target)
        module.root = fixture
        module.delivery = fixture / 'capabilities/evidence/T83-delivery'
        module.base = module.delivery / 'diagnostics/full-verify-unicode-elapsed-r1'
        module.__file__ = str(fixture / path.relative_to(root))
        assert module.validate()['passing-exact-method-replays'] == 3
        prior = module.base / 'method-replay-r1/results.json'
        later = module.base / 'method-replay-r2/results.json'
        value = json.loads(prior.read_text())
        if kind == 'altered-ledger':
            value['scope'] += ' Altered isolated fixture.'
        elif kind.startswith('resealed-'):
            key = 'log' if kind == 'resealed-log' else 'report'
            changed = fixture / value['results'][0][key]['path']
            extra = '\nIsolated altered transcript\n' if key == 'log' else '\n<!-- Isolated altered report -->\n'
            changed.write_bytes(changed.read_bytes() + extra.encode())
            value['results'][0][key] = module.driver.reference(fixture, changed)
        else:
            later_value = json.loads(later.read_text())
            del later_value['prior-failed-diagnostic']
            later.write_text(json.dumps(later_value, indent=2) + '\n')
        if kind != 'omitted-prior-witness':
            prior.write_text(json.dumps(value, indent=2) + '\n')
        rejected = False
        try:
            module.validate()
        except (AssertionError, KeyError):
            rejected = True
        assert rejected, 'Altered or resealed history admitted: ' + kind
        results.append({'case': kind, 'rejected': True, 'isolated-copy': True})
module.root = root
module.delivery = delivery
module.base = delivery / 'diagnostics/full-verify-unicode-elapsed-r1'
module.__file__ = str(path)
assert module.validate() == actual
output = delivery / 'validation/unicode-preservation-r3.json'
assert not output.exists()
module.driver.write_json(output, {'validator': module.driver.reference(root, path),
    'checker': module.driver.reference(root, Path(__file__)), 'actual-preservation-passed': True,
    'originals-unchanged': True, 'negative-cases': results, 'certifications-exported': 0})
print('Actual Unicode preservation PASS; four altered/resealed/omitted-history fixtures rejected; originals unchanged; zero certifications.')
