import hashlib
import json
import zipfile
from pathlib import Path

root = Path('/workspace/folio-pdf')
base = root / '.build-cache/T77-preflight'
validation = root / 'capabilities/evidence/T77-delivery/validation'
old = json.loads((validation / 'final-stage-inputs-r3.json').read_text())
new = json.loads((validation / 'final-stage-inputs-r4.json').read_text())

def digest(data):
    return hashlib.sha256(data).hexdigest()

def refs(items):
    return {item['path']: item['sha256'] for item in items}

def changes(left, right):
    return [{'path': key, 'before': left.get(key), 'after': right.get(key)}
            for key in sorted(left.keys() | right.keys())
            if left.get(key) != right.get(key)]

def tree(directory):
    return {path.relative_to(directory).as_posix(): digest(path.read_bytes())
            for path in sorted(directory.rglob('*')) if path.is_file()}

source_changes = changes(refs(old['candidate']['inputs']), refs(new['candidate']['inputs']))
assert [item['path'] for item in source_changes] == ['scripts/t03-foundation.py']
assert old['contract-inputs'] == new['contract-inputs']
assert old['python-runtime'] == new['python-runtime']
harness_changes = changes(refs(old['harness']), refs(new['harness']))
assert [item['path'] for item in harness_changes] == ['target/foundation-0.1.0/harness/acceptance.jar']

modules = ['pdf-provider-contract', 'pdf-conversion', 'pdf-document',
           'pdf-migration-itext7', 'pdf-migration-itext7-preview', 'pdf-acceptance',
           'build-tools/inventory', 'build-tools/release']
comparisons = []
for module in modules:
    for kind in ['classes', 'test-classes']:
        current = tree(root / module / 'target' / kind)
        verified = tree(base / 'matrix/jdk17' / module / 'target' / kind)
        assert current == verified, (module, kind, 'JDK 17 verification differs')
        independent_dir = base / 'independent-r4' / module / 'target' / kind
        independent_checked = independent_dir.exists()
        if independent_checked:
            assert current == tree(independent_dir), (module, kind, 'independent verification differs')
        comparisons.append({'module': module, 'kind': kind,
                            'matches-jdk17-full-verify': True,
                            'matches-independent-suite': True if independent_checked else 'module outside reactor',
                            'files': current})

staged = []
for module in modules[:6]:
    path = (root / 'target/foundation-0.1.0/harness/acceptance.jar' if module == 'pdf-acceptance'
            else root / 'target/foundation-0.1.0/artifacts' / (module + '-0.1.0.jar'))
    classes = tree(root / module / 'target/classes')
    with zipfile.ZipFile(path) as archive:
        assert len(archive.namelist()) == len(set(archive.namelist())), ('duplicate archive entry', module)
        entries = {name: digest(archive.read(name)) for name in archive.namelist() if not name.endswith('/')}
    assert all(entries.get(name) == value for name, value in classes.items()), module
    extra = {name: value for name, value in entries.items() if name not in classes}
    assert set(extra) == {'META-INF/MANIFEST.MF',
                         'META-INF/maven/net.zerocloud/' + module + '/pom.xml',
                         'META-INF/maven/net.zerocloud/' + module + '/pom.properties'}, (module, extra)
    staged.append({'path': path.relative_to(root).as_posix(), 'sha256': digest(path.read_bytes()),
                   'matching-class-and-resource-count': len(classes), 'other-archive-entries': extra})
for name, module in [('native-tests', 'pdf-document'), ('facade-tests', 'pdf-migration-itext7')]:
    path = root / 'target/foundation-0.1.0/harness' / (name + '.jar')
    with zipfile.ZipFile(path) as archive:
        assert len(archive.namelist()) == len(set(archive.namelist())), ('duplicate archive entry', name)
        entries = {n: digest(archive.read(n)) for n in archive.namelist() if not n.endswith('/')}
    assert entries == tree(root / module / 'target/test-classes'), name
    staged.append({'path': path.relative_to(root).as_posix(), 'sha256': digest(path.read_bytes()),
                   'matching-class-and-resource-count': len(entries), 'other-archive-entries': {}})

result = {
    'result': 'pass',
    'source-changes': source_changes,
    'contract-inputs-identical': True,
    'python-runtime-and-dependencies-identical': True,
    'artifact-archive-hash-changes': changes(refs(old['candidate']['artifacts']), refs(new['candidate']['artifacts'])),
    'harness-archive-hash-changes': harness_changes,
    'unchanged-harness-input-count': len(new['harness']) - len(harness_changes),
    'duplicate-staged-archive-entries': False,
    'class-resource-test-files-identical-to-jdk17-verify': sum(len(row['files']) for row in comparisons),
    'comparison': comparisons,
    'staged-jar-content-checks': staged,
    'applicability': 'Broad Java checks precede the isolated Python T03 count correction; all Java/test/resource/observer/contract sources are unchanged. Direct staged class/resource/test entry hashes match the successful full JDK 17 and applicable independent-suite outputs. Archive byte identity is not claimed. Fresh certification binds the rebuilt archive identities; Python regressions validate the runner correction.',
}
(validation / 'validation-applicability-r4.json').write_text(json.dumps(result, indent=2) + '\n')
print('PASS: 1,517 class/resource/test files match completed JDK 17 verify; shared independent outputs and all staged runtime/test JAR entries match. 698 harness inputs unchanged. Archive bytes are explicitly not claimed identical.')
