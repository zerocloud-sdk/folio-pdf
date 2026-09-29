#!/usr/bin/env python3
"""Install frozen acceptance-only Python tools into the repository build cache."""
import hashlib
from pathlib import Path
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ARCHIVES = (
    ('pypdf-6.1.1-py3-none-any.whl', '7781f99493208a37a7d4275601d883e19af24e62a525c25844d22157c2e4cde7',
     'https://files.pythonhosted.org/packages/07/ed/adae13756d9dabdddee483fc7712905bb5585fbf6e922b1a19aca3a29cd1/'),
    ('pycryptodome-3.23.0-cp37-abi3-manylinux_2_17_x86_64.manylinux2014_x86_64.whl',
     'c8987bd3307a39bc03df5c8e0e3d8be0c4c3518b7f044b0f4c15d1aa78f52575',
     'https://files.pythonhosted.org/packages/5f/e9/a09476d436d0ff1402ac3867d933c61805ec2326c6ea557aeeac3825604e/'))


def main():
    cache = ROOT / '.build-cache/t78-checker-archives'
    destination = ROOT / '.build-cache/t78-security-checkers'
    cache.mkdir(parents=True, exist_ok=True)
    destination.mkdir(parents=True, exist_ok=True)
    installed = {}
    for name, expected, base in ARCHIVES:
        archive = cache / name
        if not archive.is_file():
            with urllib.request.urlopen(base + name, timeout=60) as response:
                data = response.read(32 * 1024 * 1024 + 1)
            if len(data) > 32 * 1024 * 1024 or hashlib.sha256(data).hexdigest() != expected:
                raise ValueError('Acceptance archive identity mismatch')
            archive.write_bytes(data)
        if archive.is_symlink() or hashlib.sha256(archive.read_bytes()).hexdigest() != expected:
            raise ValueError('Acceptance archive identity mismatch')
        with zipfile.ZipFile(archive) as wheel:
            for item in wheel.infolist():
                path = destination / item.filename
                if not path.resolve().is_relative_to(destination.resolve()) or item.external_attr >> 16 & 0o170000 == 0o120000:
                    raise ValueError('Linked or escaping acceptance archive entry')
                if item.is_dir():
                    path.mkdir(parents=True, exist_ok=True)
                    continue
                data = wheel.read(item)
                installed[item.filename] = hashlib.sha256(data).hexdigest()
                if path.exists() and path.read_bytes() != data:
                    raise ValueError('Installed acceptance tool differs from the pinned archive')
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(data)
    actual = {path.relative_to(destination).as_posix() for path in destination.rglob('*') if path.is_file()}
    if actual != set(installed) or any(path.is_symlink() for path in destination.rglob('*')):
        raise ValueError('Acceptance runtime has unpinned files; recreate its owned cache directory')
    manifest = ''.join(value + '  ' + name + '\n' for name, value in sorted(installed.items()))
    frozen = ROOT / 'scripts/t78-checkers-runtime.sha256'
    if not frozen.is_file() or frozen.read_text() != manifest:
        raise ValueError('Installed acceptance runtime differs from the frozen inventory')
    print('Pinned acceptance-only pypdf 6.1.1 and PyCryptodome 3.23.0 installed.')


if __name__ == '__main__':
    main()
