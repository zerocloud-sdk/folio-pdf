#!/usr/bin/env python3
"""Install the pinned Ubuntu observer Python without changing the host or image.

Usage: provision-foundation-python.py <deb-directory> <libexpat.so.1> <fresh-install>
Inputs and official archive locations are documented in docs/t14-certification.md.
The installation is acceptance-only and must be below the repository root.
"""
import hashlib
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

PACKAGES = {
    'libpython3.12-minimal_3.12.3-1ubuntu0.15_amd64.deb': '5d16abf75f5a517c7e68dfbe888ddb40aa95d3b4445b1c223ec5ea23d2b01051',
    'libpython3.12-stdlib_3.12.3-1ubuntu0.15_amd64.deb': '47c3b48809d392570e827cb3cdeacdf750af39fc36619c83337e28cbffea791c',
    'python3.12-minimal_3.12.3-1ubuntu0.15_amd64.deb': '487383dc2a895e0a767d820e0e55f2ab7d6ebe4dccd3d2c0b81f00ee11bb1152'}
PYTHON_SHA256 = '1643dacd9feaedc58f3cc581e4d22577dfe25c09b10282936186ccf0f2e61118'
EXPAT_SHA256 = 'c42ff317838b4b4639e2ea801905f0317177c6df7e31b2f0d0240e3c3ac0cfde'


def verify(path, expected):
    if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != expected:
        raise ValueError('Pinned Ubuntu observer input mismatch: ' + str(path))


def install(packages, expat, output):
    output = output.resolve()
    output.relative_to(Path(__file__).resolve().parents[1])
    if output.exists():
        raise ValueError('The Python observer installation must be fresh')
    verify(expat, EXPAT_SHA256)
    for name, identity in PACKAGES.items():
        verify(packages / name, identity)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=output.parent) as temporary:
        unpacked = Path(temporary) / 'packages'
        for name in PACKAGES:
            subprocess.run(['dpkg-deb', '-x', str(packages / name), str(unpacked)], check=True)
        verify(unpacked / 'usr/bin/python3.12', PYTHON_SHA256)
        staged = Path(temporary) / 'installation'
        (staged / 'bin').mkdir(parents=True)
        (staged / 'lib').mkdir()
        shutil.copyfile(unpacked / 'usr/bin/python3.12', staged / 'bin/python3.12')
        (staged / 'bin/python3.12').chmod(0o755)
        source = unpacked / 'usr/lib/python3.12'
        for path in source.rglob('*'):
            if path.name == 'sitecustomize.py' or '__pycache__' in path.parts:
                continue
            target = staged / 'lib/python3.12' / path.relative_to(source)
            if path.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                path.resolve(strict=True).relative_to(unpacked)
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(path, target)
        shutil.copyfile(expat, staged / 'lib/libexpat.so.1')
        staged.rename(output)
    print('Pinned acceptance-only Python installed: ' + str(output))


if __name__ == '__main__':
    if len(sys.argv) != 4:
        raise SystemExit(__doc__)
    install(*(Path(value) for value in sys.argv[1:]))
