"""Preserve exact-method diagnostic logs and the Java 8 runner separately."""
import hashlib
import json
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
gate = json.loads((delivery / 'validation/input-open-case-diagnosis-r1-result.json').read_text())
assert gate['exit-code'] == 0
assert hashlib.sha256((root / gate['log']['path']).read_bytes()).hexdigest() == gate['log']['sha256']
ledger = delivery / 'diagnostics/input-case-exit-replays-r1.json'
diagnostic = json.loads(ledger.read_text())
assert len(diagnostic['results']) == 10
assert all(item['exit-code'] == 0 and item['exact-method-passed']
           and not item['same-shutdown-symptom'] for item in diagnostic['results'])
assert diagnostic['test-class'] == 'net.zerocloud.pdf.consumer.EmbeddedFilesPasswordWorkflowTest'
assert diagnostic['test-method'] == 'originalInputsOpenClearContentWithoutAttachmentCredentials'
base = root / '.build-cache/t83-input-case-replay-r1'
expected = {root / item['log']['path'] for item in diagnostic['results']}
expected.update((root / diagnostic['helper-class']['path'], base / 'compile.txt'))
files = sorted(path for path in base.rglob('*') if path.is_file())
assert set(files) == expected and len(files) == 12
for reference in [diagnostic['helper-class'], diagnostic['helper-source'],
                  *[item['log'] for item in diagnostic['results']]]:
    assert hashlib.sha256((root / reference['path']).read_bytes()).hexdigest() == reference['sha256']
assert (root / diagnostic['helper-class']['path']).read_bytes()[:8] == bytes.fromhex('cafebabe00000034')
destination = delivery / 'diagnostics/input-open-method-replays.zip'
manifest_path = destination.with_suffix('.zip.json')
assert not destination.exists() and not manifest_path.exists()
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
record = {'purpose': 'Ten unchanged public-method diagnostic replays; no certification export or published output',
          'replay-ledger': {'path': ledger.relative_to(root).as_posix(),
                           'sha256': hashlib.sha256(ledger.read_bytes()).hexdigest()},
          'helper-source': diagnostic['helper-source'],
          'archive': {'path': destination.relative_to(root).as_posix(),
                      'bytes': destination.stat().st_size,
                      'sha256': hashlib.sha256(destination.read_bytes()).hexdigest()},
          'entries': entries}
manifest_path.write_text(json.dumps(record, indent=2) + '\n')
print('Retained and verified 12 exact-method diagnostic files in ' + str(record['archive']['bytes']) + ' bytes.')
