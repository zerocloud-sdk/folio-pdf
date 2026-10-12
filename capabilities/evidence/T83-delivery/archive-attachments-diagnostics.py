"""Retain attachment diagnostics separately from certification evidence."""
import hashlib
import json
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
gate = json.loads((delivery / 'validation/attachments-worker-exit-products-diagnosis-r1-result.json').read_text())
assert gate['exit-code'] == 0
diagnostic = json.loads((delivery / 'diagnostics/attachments-exit-replays-r1.json').read_text())
assert len(diagnostic['results']) == 5
assert all(item['exit-code'] == 0 and not item['same-shutdown-symptom']
           and item['published-products'] == item['completed-public-records'] == 62
           and item['all-public-outcomes-complete'] for item in diagnostic['results'])
destination = delivery / 'diagnostics/attachments-worker-exit-products.zip'
manifest_path = destination.with_suffix('.zip.json')
assert not destination.exists() and not manifest_path.exists()
base = root / '.build-cache/t83-attachments-exit-replay-r1'
assert base.is_dir()
for attempt in diagnostic['results']:
    observed = base / ('attempt-' + str(attempt['attempt'])) / 'observations'
    assert len(list(observed.glob('*/product.pdf'))) == 62
    assert len(list(observed.glob('*/publication.properties'))) == 62
    clear_records = list(observed.glob('*/clear-observation.properties'))
    assert len(clear_records) == 62
    for path in clear_records:
        values = dict(line.split('=', 1) for line in path.read_text().splitlines()
                      if line and not line.startswith('#') and '=' in line)
        assert values['clear-document'] == values['owner-attachment-bytes'] == 'pass'
files = sorted(path for path in base.rglob('*') if path.is_file())
entries = []
with zipfile.ZipFile(destination, 'x', compression=zipfile.ZIP_DEFLATED) as archive:
    for path in files:
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
record = {'purpose': 'Diagnostic artifacts only; failed attachment certification remains failed and unindexed',
          'archive': {'path': destination.relative_to(root).as_posix(),
                      'bytes': destination.stat().st_size,
                      'sha256': hashlib.sha256(destination.read_bytes()).hexdigest()},
          'entries': entries,
          'successful-attempts': 'Five unchanged exact public attachment-product replays; diagnostic gate exit 0'}
manifest_path.write_text(json.dumps(record, indent=2) + '\n')
print('Retained and verified ' + str(len(entries)) + ' attachment diagnostic files in '
      + str(record['archive']['bytes']) + ' archive bytes.')
