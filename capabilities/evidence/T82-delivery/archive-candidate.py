"""Archive exact staged sources, contracts, artifacts and harness as portable parts."""
import hashlib
import json
from pathlib import Path
import shutil
import sys
import tempfile
import zipfile

root = Path(__file__).resolve().parents[3]
base = root / 'target/foundation-0.1.0'
receipt = json.loads((base / 'build-inputs.json').read_text())
destination = Path(__file__).resolve().parent / sys.argv[1]
destination.mkdir()
for name in ('build-inputs.json', 'build-command.json', 'build.txt'):
    shutil.copyfile(base / name, destination / name)

def archive(name, references):
    unique = {item['path']: item['sha256'] for item in references}
    with tempfile.TemporaryDirectory(prefix='t82-archive-') as scratch:
        path = Path(scratch) / name
        with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as output:
            for source, expected in sorted(unique.items()):
                data = (root / source).read_bytes()
                assert hashlib.sha256(data).hexdigest() == expected, source
                info = zipfile.ZipInfo(source, (1980, 1, 1, 0, 0, 0))
                info.external_attr = 0o100644 << 16
                info.compress_type = zipfile.ZIP_DEFLATED
                output.writestr(info, data)
        with zipfile.ZipFile(path) as check:
            for source, expected in unique.items():
                assert hashlib.sha256(check.read(source)).hexdigest() == expected, source
        whole = hashlib.sha256(); parts = []; offset = 0
        with path.open('rb') as stream:
            while data := stream.read(48 * 1024 * 1024):
                whole.update(data)
                target = destination / (name + '.part' + str(len(parts) + 1).zfill(3))
                target.write_bytes(data)
                parts.append({'path': target.name, 'offset': offset, 'bytes': len(data),
                              'sha256': hashlib.sha256(data).hexdigest()})
                offset += len(data)
        record = {'archive': name, 'bytes': offset, 'sha256': whole.hexdigest(), 'parts': parts,
            'reassembly': 'Concatenate parts in listed order without separators, then verify the whole SHA-256 and ZIP entry hashes.',
            'entries': [{'path': source, 'sha256': expected} for source, expected in sorted(unique.items())]}
        (destination / (name + '-parts.json')).write_text(json.dumps(record, indent=2) + '\n')
        print(name + ': verified ' + str(len(unique)) + ' entries, ' + str(len(parts)) + ' portable parts', flush=True)

archive('source-inputs.zip', receipt['candidate']['inputs'])
archive('artifacts-contracts-harness.zip', receipt['candidate']['artifacts'] + receipt['contract-inputs'] + receipt['harness']
        + [{'path': (base / name).relative_to(root).as_posix(), 'sha256': hashlib.sha256((base / name).read_bytes()).hexdigest()}
           for name in ('build-inputs.json', 'build-command.json', 'build.txt')])
(destination / 'README.md').write_text('''# Retained unsigned candidate

Each parts manifest binds every ZIP entry and the complete archive. Concatenate
the listed parts in order without separators, verify the whole SHA-256, then
unzip at a separate review location. Entry paths preserve the repository layout.
All parts are at most 48 MiB. Source and staged artifact bytes were checked
against the build receipt before and after compression. This archive performs
no signing, publication, upload or credential access.
''')
