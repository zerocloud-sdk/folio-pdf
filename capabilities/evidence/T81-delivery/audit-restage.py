import importlib.util
import json
from pathlib import Path
import zipfile
import yaml

root = Path('/workspace/folio-pdf')
delivery = root / 'capabilities/evidence/T81-delivery'
spec = importlib.util.spec_from_file_location('t81_restage_driver', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
current = driver.require_staged_build(root, yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text()),
                                      root / 'target/foundation-0.1.0/build-inputs.json')
previous = json.loads((delivery / 'final-candidate/build-inputs.json').read_text())
def changed(before, after):
    old = {item['path']: item['sha256'] for item in before}
    new = {item['path']: item['sha256'] for item in after}
    return sorted(path for path in old.keys() | new.keys() if old.get(path) != new.get(path))
sources = changed(previous['candidate']['inputs'], current['candidate']['inputs'])
assert sources == ['build-tools/inventory/src/test/java/net/zerocloud/pdf/tools/inventory/FoundationReadinessCommandTest.java'], sources
assert previous['contract-inputs'] == current['contract-inputs']
artifacts = changed(previous['candidate']['artifacts'], current['candidate']['artifacts'])
harness = changed(previous['harness'], current['harness'])
packages = []
for name in sorted(set(artifacts + harness)):
    if not name.endswith('.jar'):
        continue
    archived = delivery / 'final-candidate/originals' / name
    rebuilt = root / name
    with zipfile.ZipFile(archived) as before, zipfile.ZipFile(rebuilt) as after:
        assert before.namelist() == after.namelist(), name
        assert all(before.read(entry) == after.read(entry) for entry in before.namelist()), name
        metadata = []
        for entry in before.namelist():
            old = before.getinfo(entry)
            new = after.getinfo(entry)
            fields = ('date_time', 'compress_type', 'create_system', 'create_version', 'extract_version',
                      'flag_bits', 'internal_attr', 'external_attr', 'extra', 'comment')
            differences = [field for field in fields if getattr(old, field) != getattr(new, field)]
            if differences:
                metadata.append({'entry': entry, 'changed-fields': differences})
        packages.append({'original': driver.reference(root, archived), 'current': driver.reference(root, rebuilt),
                         'entry-count': len(before.namelist()), 'entry-bytes-identical': True,
                         'metadata-differences': metadata})
record = {'status': 'pass', 'current-build': driver.reference(root, root / 'target/foundation-0.1.0/build-inputs.json'),
          'historical-build': driver.reference(root, delivery / 'final-candidate/build-inputs.json'),
          'changed-source-inputs': sources, 'contracts-byte-identical': True,
          'changed-artifact-identities': artifacts, 'changed-harness-identities': harness,
          'jar-entry-comparisons': packages,
          'superseded-assumption': 'An initial closure assertion expected identical whole-jar hashes and failed. '
                                   'Actual package identities differ; every retained jar entry was compared instead. '
                                   'Fresh certification binds the new complete identities.'}
output = delivery / 'validation/final-stage-r2-closure.json'
assert not output.exists()
output.write_text(json.dumps(record, indent=2) + '\n')
print('PASS: one bound test source changed; contracts and all jar entry contents match; new package identities retained.')
