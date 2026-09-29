#!/usr/bin/env python3
"""Build the separately identified T78 qpdf CLI and freeze its Ubuntu runtime."""
import hashlib
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import tarfile
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
IMAGE = 'docker.io/library/eclipse-temurin@sha256:1ca5e470ad60db0d5b4137c1357068fa210051d815ad98ea9964f4bbe7367f8c'


def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    pin = dict(line.split('=', 1) for line in (ROOT / 'scripts/t78-qpdf-pin.properties').read_text().splitlines() if line)
    patch = ROOT / 'build-tools/acceptance/qpdf/t78-r1.patch'
    if digest(patch) != pin['patch-sha256']: raise ValueError('qpdf supplement patch changed')
    cache = ROOT / '.build-cache/t78-provision'; cache.mkdir(parents=True, exist_ok=True)
    archive = cache / 'qpdf-source.tar.gz'
    if not archive.exists():
        with urllib.request.urlopen(pin['source-archive'], timeout=60) as response, archive.open('xb') as stream:
            total = 0
            while True:
                block = response.read(1024 * 1024)
                if not block: break
                total += len(block)
                if total > 32 * 1024 * 1024: raise ValueError('Source archive exceeds its bound')
                stream.write(block)
    if archive.is_symlink() or digest(archive) != pin['source-archive-sha256']:
        raise ValueError('qpdf public source archive identity mismatch')
    with tempfile.TemporaryDirectory(prefix='qpdf-build-', dir=cache) as directory:
        prepared = Path(directory); source = prepared / 'source'; source.mkdir()
        with tarfile.open(archive) as files:
            for entry in files:
                relative = PurePosixPath(entry.name).relative_to('qpdf-' + pin['source-commit'])
                if not relative.parts or not entry.isfile(): continue
                if relative.is_absolute() or '..' in relative.parts: raise ValueError('Escaping source entry')
                if any(part in ('qtest', 'qpdf_extra') for part in relative.parts): continue
                if relative.suffix.lower() in ('.pdf', '.png', '.jpg', '.fuzz', '.ttf', '.otf'): continue
                target = source / relative; target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(files.extractfile(entry).read())
        with patch.open('rb') as stream:
            subprocess.run(['patch', '--batch', '--fuzz=0', '-s', '-p1'], cwd=source, stdin=stream, check=True, timeout=15)
        packages = dict(line.split('\t') for line in
                        (ROOT / 'build-tools/acceptance/qpdf/t78-build-packages.txt').read_text().splitlines())
        selected = ('build-essential', 'cmake', 'pkg-config:amd64', 'zlib1g-dev:amd64', 'libjpeg-dev:amd64', 'libssl-dev:amd64')
        specifications = ' '.join(name + '=' + packages[name] for name in selected)
        script = '''set -eu
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends ''' + specifications + '''
cmake -S /outputs/source -B /outputs/build -DFOLIO_QPDF_CLI_ONLY=ON -DCMAKE_BUILD_TYPE=Release -DBUILD_DOC=OFF -DINSTALL_EXAMPLES=OFF -DBUILD_SHARED_LIBS=OFF -DBUILD_STATIC_LIBS=ON -DUSE_IMPLICIT_CRYPTO=OFF -DREQUIRE_CRYPTO_OPENSSL=ON -DDEFAULT_CRYPTO=openssl -DOPENSSL_USE_STATIC_LIBS=TRUE -DJPEG_LIBRARY=/usr/lib/x86_64-linux-gnu/libjpeg.a -DZLIB_LIBRARY=/usr/lib/x86_64-linux-gnu/libz.a -DCMAKE_CXX_FLAGS=-ffile-prefix-map=/outputs/source=/folio-qpdf-t78 -DCMAKE_EXE_LINKER_FLAGS="-static-libstdc++ -static-libgcc"
cmake --build /outputs/build --target qpdf --parallel 4
mkdir /outputs/runtime
cp /outputs/build/qpdf/qpdf /outputs/runtime/qpdf
for name in libz.so.1 libjpeg.so.8 libcrypto.so.3 libc.so.6 ld-linux-x86-64.so.2; do
    cp -L /lib/x86_64-linux-gnu/$name /outputs/runtime/$name
done
'''
        subprocess.run(['podman', 'run', '--rm', '--user', '0', '--entrypoint', '/bin/sh',
                        '-v', str(prepared) + ':/outputs:rw', IMAGE, '-c', script], check=True, timeout=1200)
        expected = dict((name, value) for value, name in (line.split('  ', 1) for line in
                        (ROOT / 'scripts/t78-qpdf-runtime.sha256').read_text().splitlines()))
        actual = {p.name: digest(p) for p in (prepared / 'runtime').iterdir() if p.is_file()}
        if actual != expected: raise ValueError('qpdf executable/runtime differs from the frozen build')
        destination = ROOT / '.build-cache/qpdf/t78-r1/runtime'; destination.mkdir(parents=True, exist_ok=True)
        for name, identity in expected.items():
            target = destination / name
            if target.exists() and (target.is_symlink() or digest(target) != identity):
                raise ValueError('Existing qpdf supplement cache changed')
            shutil.copy2(prepared / 'runtime' / name, target)
    print('Pinned qpdf 12.4.0-folio-t78-r1 installed.')


if __name__ == '__main__': main()
