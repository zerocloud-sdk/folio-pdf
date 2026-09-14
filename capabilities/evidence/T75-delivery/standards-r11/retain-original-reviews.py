import hashlib
import json
from pathlib import Path
import tarfile

root = Path('/home/ubuntu/IdeaProjects/open-pdf/capabilities/evidence/T75-delivery/standards-r11')
records = {}
for name, original in (
        ('standards-original', '/tmp/t75-structure-standards-review-5oc4nufc'),
        ('spec-original', '/tmp/t75-spec-structure._jaafen4')):
    source = Path(original)
    archive = root / (name + '.tar.xz')
    members = sorted(path for path in source.rglob('*') if path.is_file())
    with tarfile.open(archive, 'w:xz') as output:
        for path in members:
            output.add(path, arcname=path.relative_to(source).as_posix(), recursive=False)
    manifests = []
    with tarfile.open(archive, 'r:xz') as retained:
        assert len(retained.getmembers()) == len(members)
        for member in retained.getmembers():
            data = retained.extractfile(member).read()
            assert data == (source / member.name).read_bytes(), member.name
            manifests.append({'path': member.name, 'size': len(data), 'sha256': hashlib.sha256(data).hexdigest()})
    records[name] = {'original': original, 'archive': archive.name,
                     'sha256': hashlib.sha256(archive.read_bytes()).hexdigest(), 'members': manifests}
    print(name, len(manifests), 'members verified')
(root / 'original-review-archives.json').write_text(json.dumps(records, indent=2) + '\n')
(root / 'retain-original-reviews.py').write_bytes(Path(__file__).read_bytes())
