"""Retain program recorder observations; verify each archive member against its source."""
import hashlib
import json
from pathlib import Path
import tarfile

destination = Path(__file__).resolve().parent
sources = {
    'first-positive': '/tmp/t75-program-recorder-first-green',
    'producer-receipt-unqualified': '/tmp/t75-program-recorder-producer-receipt-red',
    'producer-receipt-positive': '/tmp/t75-program-recorder-producer-receipt-green',
    'adapter-controls': '/tmp/t75-program-recorder-adapter-controls',
    'python-publication-red': '/tmp/t75-program-python-publication-red',
    'python-publication-green': '/tmp/t75-program-python-publication-green',
    'standards-original': '/tmp/t75-program-recorder-standards-review-hgrbpkxk',
    'spec-original': '/tmp/t75-spec-program-recorder.caqgz2lp',
}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def archive(name, source):
    path = destination / (name + '.tar.xz')
    members, links = [], []
    with tarfile.open(path, 'w:xz') as output:
        for original in sorted(source.rglob('*')):
            relative = original.relative_to(source).as_posix()
            if original.is_symlink():
                links.append({'path': relative, 'target': str(original.readlink())})
            elif original.is_file():
                data = original.read_bytes()
                assert not data.startswith(b'\x7fELF'), original
                assert 'classes' not in original.relative_to(source).parts, original
                output.add(original, arcname=relative, recursive=False)
                members.append({'path': relative, 'size': len(data), 'sha256': sha(data)})
    with tarfile.open(path, 'r:xz') as retained:
        assert len(retained.getmembers()) == len(members)
        for member, identity in zip(retained.getmembers(), members):
            data = retained.extractfile(member).read()
            assert member.name == identity['path']
            assert data == (source / member.name).read_bytes(), member.name
            assert sha(data) == identity['sha256']
    print(name, len(members), 'members byte-verified')
    return {'original': str(source), 'archive': path.name, 'sha256': sha(path.read_bytes()),
            'members': members, 'symbolic_links': links}


if __name__ == '__main__':
    records = {}
    for name, source in sources.items():
        records[name] = archive(name, Path(source))
    (destination / 'original-archive-identities.json').write_text(json.dumps(records, indent=2) + '\n')
    for path in sorted(Path('/tmp').glob('t75-program-*.log')):
        copy = destination / path.name
        copy.write_bytes(path.read_bytes())
        assert copy.read_bytes() == path.read_bytes()
