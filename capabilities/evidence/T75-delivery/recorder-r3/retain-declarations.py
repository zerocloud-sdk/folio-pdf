"""Archive completed declaration qualification, preserving every original byte identity."""
import hashlib
import json
from pathlib import Path
import sys
import tarfile

destination = Path(__file__).resolve().parent
repository = destination.parents[3]
sources = {
    'standards-original': '/tmp/t75-declaration-standards-review-wjysmimc',
    'spec-original': '/tmp/t75-spec-declarations.4jjakvw8',
    'original-snapshot': '/tmp/t75-declaration-review-snapshot.j5u1k2xa',
    'retained-closure-snapshot': '/tmp/t75-declaration-retained-closure-snapshot.0_arb179',
}
for name in (
        'final-controls-green', 'late-controls-red', 'pin-controls-green', 'pin-controls-red',
        'recorder-final-identities-green', 'recorder-first-green', 'recorder-pin-green',
        'recorder-retained-artifacts-green', 'retained-controls-green',
        'retained-controls-r2-red', 'retained-controls-red'):
    sources[name] = '/tmp/t75-declaration-' + name
manifest_name = 'declaration-archive-identities.json'
if len(sys.argv) == 3:
    sources = json.loads(Path(sys.argv[1]).read_text())
    manifest_name = sys.argv[2]


def sha(data):
    return hashlib.sha256(data).hexdigest()


original_pdfcpu = repository / '.build-cache/pdfcpu/0.15.0/pdfcpu'
pdfcpu_bytes = original_pdfcpu.read_bytes()
records = {}
for name, original in sources.items():
    source = Path(original)
    assert source.is_dir(), source
    archive = destination / (name + '.tar.xz')
    members, omitted, links = [], [], []
    with tarfile.open(archive, 'w:xz') as output:
        for path in sorted(source.rglob('*')):
            relative = path.relative_to(source).as_posix()
            if path.is_symlink():
                links.append({'path': relative, 'target': str(path.readlink())})
                continue
            if not path.is_file():
                continue
            data = path.read_bytes()
            identity = {'path': relative, 'size': len(data), 'sha256': sha(data)}
            if 'classes' in path.relative_to(source).parts:
                identity['reason'] = 'Compiled classes and dependency resources are identified, not redistributed.'
                omitted.append(identity)
                continue
            if data.startswith(b'\x7fELF'):
                identity['reason'] = 'Pinned external executable is not redistributed.'
                if data in (pdfcpu_bytes, pdfcpu_bytes + b'\0'):
                    rebuilt = pdfcpu_bytes + (b'\0' if len(data) > len(pdfcpu_bytes) else b'')
                    assert rebuilt == data
                    identity['reconstruction'] = {
                        'original': str(original_pdfcpu), 'original_sha256': sha(pdfcpu_bytes),
                        'append_hex': '00' if len(data) > len(pdfcpu_bytes) else '', 'byte_verified': True}
                omitted.append(identity)
                continue
            output.add(path, arcname=relative, recursive=False)
            members.append(identity)
    with tarfile.open(archive, 'r:xz') as retained:
        assert len(retained.getmembers()) == len(members)
        for member, identity in zip(retained.getmembers(), members):
            data = retained.extractfile(member).read()
            assert member.name == identity['path']
            assert data == (source / member.name).read_bytes(), member.name
            assert sha(data) == identity['sha256']
    records[name] = {'original': original, 'archive': archive.name, 'sha256': sha(archive.read_bytes()),
                     'members': members, 'omitted': omitted, 'symbolic_links': links}
    print(name, len(members), 'members byte-verified;', len(omitted), 'identified exclusions')
(destination / manifest_name).write_text(json.dumps(records, indent=2) + '\n')
for path in sorted(Path('/tmp').glob('t75-declaration*.log')):
    copy = destination / path.name
    copy.write_bytes(path.read_bytes())
    assert copy.read_bytes() == path.read_bytes()
