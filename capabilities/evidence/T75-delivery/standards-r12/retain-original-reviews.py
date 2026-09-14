"""Retain completed independent review files and compare every archived byte."""
import hashlib
import json
from pathlib import Path
import tarfile

destination = Path(__file__).resolve().parent
records = {}
for name, original in (
        ('standards-original', '/tmp/t75-structure-content-review-gpwm8fv9'),
        ('spec-original', '/tmp/t75-spec-structure-content.453_lsec'),
        ('page-association-assessment', '/tmp/t75-spec-form-page-association.x2wdtqnl')):
    source = Path(original)
    archive = destination / (name + '.tar.xz')
    members = sorted(path for path in source.rglob('*') if path.is_file())
    assert members, original
    with tarfile.open(archive, 'w:xz') as output:
        for path in members:
            output.add(path, arcname=path.relative_to(source).as_posix(), recursive=False)
    manifests = []
    with tarfile.open(archive, 'r:xz') as retained:
        assert len(retained.getmembers()) == len(members)
        for member in retained.getmembers():
            data = retained.extractfile(member).read()
            assert data == (source / member.name).read_bytes(), member.name
            manifests.append({'path': member.name, 'size': len(data),
                              'sha256': hashlib.sha256(data).hexdigest()})
    records[name] = {'original': original, 'archive': archive.name,
                     'sha256': hashlib.sha256(archive.read_bytes()).hexdigest(), 'members': manifests}
    print(name, len(manifests), 'members verified')
(destination / 'original-review-archives.json').write_text(json.dumps(records, indent=2) + '\n')
