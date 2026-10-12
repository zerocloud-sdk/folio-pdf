"""Throwaway diagnostic shapes; none are certification evidence or reduced workload substitutes."""
import gzip
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
import yaml
root = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(root / 'scripts'))
import t21_foundation_reports as worker
module = worker.foundation()
cache = root / '.build-cache/t83-qpdf-diagnostics'; cache.mkdir(exist_ok=True)
original = root / 'capabilities/evidence/T83-delivery/development-jdk17-r1/scale-observations/input'
header = (original / 'header.bin').read_bytes()
objects = (original / 'tail.bin').read_bytes().split(b'xref\n')[0]
object_offsets = [0, objects.index(b'2 0 obj'), objects.index(b'3 0 obj')]
def tail(offset, front):
    entries = [(len(header) + relative) if front else offset + relative for relative in object_offsets]
    xref_offset = offset if front else offset + len(objects)
    xref = b'xref\n0 4\n0000000000 65535 f \n' + b''.join(('%010d 00000 n \n' % at).encode() for at in entries)
    xref += b'trailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n' + str(xref_offset).encode() + b'\n%%EOF\n'
    return xref if front else objects + xref
for length, front in ((4096, False), (1048576, False), (67108864, False), (1073741824, False), (1073741824, True)):
    name = str(length) + ('-front' if front else '-tail') + '.pdf'; path = cache / name
    if path.exists(): continue
    if length == 1073741824 and not front:
        with gzip.open(original / 'consumed-input.pdf.gz', 'rb') as source, path.open('wb') as target:
            shutil.copyfileobj(source, target, 1 << 20)
    else:
        offset = length - 512
        while True:
            ending = tail(offset, front); new_offset = length - len(ending)
            if offset == new_offset: break
            offset = new_offset
        prefix = header + objects if front else header
        with path.open('wb') as target:
            target.write(prefix); remaining = offset - len(prefix)
            while remaining:
                chunk = min(1 << 20, remaining); target.write(b'\n' * chunk); remaining -= chunk
            target.write(ending)
    assert path.stat().st_size == length
profile = next(p for p in yaml.safe_load((root / 'capabilities/foundation-environments.yaml').read_text())['profiles'] if p['identity']['jdk-major'] == 17)
helper = Path(os.environ['FOLIO_HARFBUZZ_HELPER']).resolve(strict=True)
import subprocess
observed = []
for path in sorted(cache.glob('*.pdf')):
    command = module.container_command(root, profile['identity']['image'], helper)
    command += ['/workspace/scripts/container-bin/qpdf', '--check', '/workspace/' + path.relative_to(root).as_posix()]
    process = subprocess.run(command, capture_output=True, timeout=300)
    observed.append({'input': path.relative_to(root).as_posix(), 'length': path.stat().st_size,
        'sha256': module.sha256(path), 'command': command, 'exit': process.returncode,
        'stdout': process.stdout.decode(), 'stderr': process.stderr.decode()})
    print(path.name, process.returncode, process.stderr.decode().strip(), flush=True)
(root / 'capabilities/evidence/T83-delivery/giant-input-diagnosis.json').write_text(json.dumps(observed, indent=2) + '\n')
