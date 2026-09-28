#!/usr/bin/env python3
"""Normalize a completed development receipt into a collector protocol fixture.

The archive is test data, never candidate/environment certification. Rewrites
only repository-relative observation paths and their process/manifest hashes;
does not change PDFs, inventory values, findings, rasters or tool identities.
"""
import hashlib
import json
from pathlib import Path
import sys
import zipfile


def digest(data):
    return hashlib.sha256(data).hexdigest()


def generate(root, source, destination):
    from t14_foundation_reports import collect_reports
    collect_reports(root, source, 'IN_PROCESS')
    prefix = ('/workspace/' + source.relative_to(root).as_posix()).encode()
    contents = {}
    for path in sorted(source.rglob('*')):
        if not path.is_file():
            continue
        relative = path.relative_to(source).as_posix()
        if relative in ('retained-files.sha256', 'result.properties'):
            continue
        data = path.read_bytes()
        if path.suffix in ('.json', '.stdout', '.stderr', '.properties'):
            data = data.replace(prefix, b'/workspace/run')
        contents[relative] = data
    for name, data in list(contents.items()):
        if name.endswith('.command.json'):
            receipt = json.loads(data)
            stem = name[:-len('.command.json')]
            receipt['stdout-sha256'] = digest(contents[stem + '.stdout'])
            receipt['stderr-sha256'] = digest(contents[stem + '.stderr'])
            contents[name] = (json.dumps(receipt, indent=2, sort_keys=True) + '\n').encode()
    manifest = ''.join(digest(data) + '  ' + name + '\n' for name, data in sorted(contents.items())).encode()
    contents['retained-files.sha256'] = manifest
    lines = [line for line in (source / 'result.properties').read_text().splitlines()
             if not line.startswith('retained-files-sha256=')]
    contents['result.properties'] = ('\n'.join(lines) + '\nretained-files-sha256=' + digest(manifest) + '\n').encode()
    with zipfile.ZipFile(destination, 'x', zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(contents.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.external_attr = 0o100644 << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)


if __name__ == '__main__':
    generate(Path(__file__).resolve().parents[1], Path(sys.argv[1]).resolve(), Path(sys.argv[2]))
