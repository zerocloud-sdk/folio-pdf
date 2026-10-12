"""Preserve all diagnostic products without qualifying them as certifications."""
import hashlib
import json
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
destination = delivery / 'diagnostics/worker-exit-products.zip'
manifest_path = destination.with_suffix('.zip.json')
assert not destination.exists() and not manifest_path.exists()
files = []
for attempt in ('r1', 'r2'):
    base = root / '.build-cache' / ('t83-worker-exit-replay-' + attempt)
    assert base.is_dir()
    files.extend(path for path in base.rglob('*') if path.is_file())
entries = []
with zipfile.ZipFile(destination, 'x', compression=zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(files):
        assert not path.is_symlink()
        data = path.read_bytes()
        name = path.relative_to(root).as_posix()
        item = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
        item.compress_type = zipfile.ZIP_DEFLATED
        archive.writestr(item, data)
        entries.append({'path': name, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()})
assert destination.stat().st_size <= 48 * 1024 * 1024
with zipfile.ZipFile(destination) as archive:
    assert archive.namelist() == [entry['path'] for entry in entries]
    for entry in entries:
        data = archive.read(entry['path'])
        assert len(data) == entry['bytes'] and hashlib.sha256(data).hexdigest() == entry['sha256']
record = {'purpose': 'Diagnostic artifacts only; r1 gate remains failed and no certification is exported',
          'archive': {'path': destination.relative_to(root).as_posix(),
                      'bytes': destination.stat().st_size,
                      'sha256': hashlib.sha256(destination.read_bytes()).hexdigest()},
          'entries': entries,
          'first-attempt': 'r1 retained one complete product replay before its receipt writer failed',
          'successful-attempts': 'r2 retained five complete unchanged product replays; gate exit 0'}
manifest_path.write_text(json.dumps(record, indent=2) + '\n')
print('Retained and verified ' + str(len(entries)) + ' diagnostic files in ' + str(record['archive']['bytes']) + ' archive bytes.')
