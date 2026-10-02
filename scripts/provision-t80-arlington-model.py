#!/usr/bin/env python3
"""Apply the frozen, separately qualified T80 models to unchanged Arlington."""
import hashlib
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
BASE_SHA256 = '334aa8d6ccd88c96cf01c463971f3079206a47f5cf2101bf6d8cf81f374d5408'


def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    base = ROOT / '.build-cache/arlington/fe4a1a8/tsv/latest'
    files = sorted(base.glob('*.tsv'))
    identity = hashlib.sha256(''.join(path.name + ' ' + digest(path) + '\n' for path in files).encode()).hexdigest()
    if not files or any(path.is_symlink() for path in files) or identity != BASE_SHA256:
        raise ValueError('Provision the original frozen Arlington model first')
    expected = {}
    for line in (ROOT / 'scripts/t80-arlington-runtime.sha256').read_text().splitlines():
        value, name = line.split('  ', 1); expected[name] = value
    destination = ROOT / '.build-cache/t80-arlington-model'
    with tempfile.TemporaryDirectory(prefix='folio-arlington-') as directory:
        prepared = Path(directory)
        for mode in ('input', 'output'):
            model = prepared / mode; model.mkdir(parents=True)
            for path in files: shutil.copyfile(path, model / path.name)
            patch = ROOT / ('build-tools/acceptance/arlington/t80-' + mode.replace('/', '-') + '.patch')
            with patch.open('rb') as source:
                process = subprocess.run(['patch', '--batch', '--fuzz=0', '-s', '-p1'], cwd=model, stdin=source,
                                         stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=15)
            if process.returncode or process.stdout or process.stderr:
                raise ValueError('The frozen Arlington overlay did not apply exactly')
        actual = {path.relative_to(prepared).as_posix(): digest(path) for path in prepared.rglob('*') if path.is_file()}
        if actual != expected: raise ValueError('Prepared Arlington models differ from frozen identities')
        destination.mkdir(exist_ok=True)
        for name, identity in actual.items():
            target = destination / name
            if target.exists() and (target.is_symlink() or digest(target) != identity):
                raise ValueError('Existing owned model cache differs; recreate its cache directory')
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(prepared / name, target)
        if {p.relative_to(destination).as_posix() for p in destination.rglob('*') if p.is_file()} != set(expected):
            raise ValueError('Model cache contains unpinned files')
    print('Frozen T80 input and output Arlington models provisioned.')


if __name__ == '__main__': main()
