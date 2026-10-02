#!/usr/bin/env python3
"""Rebuild the acceptance-only, separately identified EFF qpdf supplement."""
import argparse
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
    parser = argparse.ArgumentParser()
    parser.add_argument('--freeze-runtime', action='store_true', help='Author the new runtime once, before qualification')
    args = parser.parse_args()
    pin_path = ROOT / 'scripts/t80-qpdf-pin.properties'
    pin = dict(line.split('=', 1) for line in pin_path.read_text().splitlines() if line)
    patches = [(ROOT / 'build-tools/acceptance/qpdf/t78-r1.patch', pin['base-patch-sha256']),
               (ROOT / 'build-tools/acceptance/qpdf/t80-r1.patch', pin['patch-sha256'])]
    for patch, expected in patches:
        if patch.is_symlink() or digest(patch) != expected: raise ValueError('EFF qpdf source patch changed')
    runtime_manifest = ROOT / 'scripts/t80-qpdf-runtime.sha256'
    if args.freeze_runtime and (runtime_manifest.exists() or pin['sha256'] != 'pending'):
        raise ValueError('An existing EFF runtime cannot be resealed')
    cache = ROOT / '.build-cache/t80-provision'; cache.mkdir(parents=True, exist_ok=True)
    archive = cache / 'qpdf-source.tar.gz'
    if not archive.exists():
        with urllib.request.urlopen(pin['source-archive'], timeout=60) as response, archive.open('xb') as stream:
            total = 0
            while True:
                block = response.read(1024 * 1024)
                if not block: break
                total += len(block)
                if total > 32 * 1024 * 1024: raise ValueError('Qpdf source exceeds its bound')
                stream.write(block)
    if archive.is_symlink() or digest(archive) != pin['source-archive-sha256']:
        raise ValueError('Qpdf public source archive changed')
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
        for patch, _ in patches:
            with patch.open('rb') as stream:
                subprocess.run(['patch', '--batch', '--fuzz=0', '-s', '-p1'], cwd=source, stdin=stream, check=True, timeout=15)
        package_manifest = ROOT / 'build-tools/acceptance/qpdf/t80-build-packages.txt'
        selected = ('build-essential', 'cmake', 'pkg-config:amd64', 'zlib1g-dev:amd64', 'libjpeg-dev:amd64', 'libssl-dev:amd64')
        if args.freeze_runtime:
            specifications = ' '.join(selected)
        else:
            if digest(package_manifest) != pin['build-packages-sha256']: raise ValueError('EFF build package identities changed')
            packages = dict(line.split('\t') for line in package_manifest.read_text().splitlines())
            specifications = ' '.join(name + '=' + packages[name] for name in selected)
        script = '''set -eu
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends ''' + specifications + '''
dpkg-query --show --showformat='${binary:Package}\t${Version}\n' > /outputs/build-packages.txt
cmake -S /outputs/source -B /outputs/build -DFOLIO_QPDF_CLI_ONLY=ON -DCMAKE_BUILD_TYPE=Release -DBUILD_DOC=OFF -DINSTALL_EXAMPLES=OFF -DBUILD_SHARED_LIBS=OFF -DBUILD_STATIC_LIBS=ON -DUSE_IMPLICIT_CRYPTO=OFF -DREQUIRE_CRYPTO_OPENSSL=ON -DDEFAULT_CRYPTO=openssl -DOPENSSL_USE_STATIC_LIBS=TRUE -DJPEG_LIBRARY=/usr/lib/x86_64-linux-gnu/libjpeg.a -DZLIB_LIBRARY=/usr/lib/x86_64-linux-gnu/libz.a -DCMAKE_CXX_FLAGS=-ffile-prefix-map=/outputs/source=/folio-qpdf-t80 -DCMAKE_EXE_LINKER_FLAGS="-static-libstdc++ -static-libgcc"
cmake --build /outputs/build --target qpdf --parallel 4
mkdir /outputs/runtime
cp /outputs/build/qpdf/qpdf /outputs/runtime/qpdf
for name in libz.so.1 libjpeg.so.8 libcrypto.so.3 libc.so.6 ld-linux-x86-64.so.2; do
    cp -L /lib/x86_64-linux-gnu/$name /outputs/runtime/$name
done
'''
        subprocess.run(['podman', 'run', '--rm', '--user', '0', '--entrypoint', '/bin/sh',
                        '-v', str(prepared) + ':/outputs:rw', IMAGE, '-c', script], check=True, timeout=1800)
        actual = {p.name: digest(p) for p in (prepared / 'runtime').iterdir() if p.is_file()}
        if args.freeze_runtime:
            package_manifest.write_bytes((prepared / 'build-packages.txt').read_bytes())
            pin['build-packages-sha256'] = digest(package_manifest)
            runtime_manifest.write_text(''.join(identity + '  ' + name + '\n' for name, identity in sorted(actual.items())))
            pin['sha256'] = actual['qpdf']
            pin_path.write_text(''.join(name + '=' + value + '\n' for name, value in pin.items()))
        expected = dict((name, value) for value, name in (line.split('  ', 1) for line in runtime_manifest.read_text().splitlines()))
        if actual != expected or actual['qpdf'] != pin['sha256']:
            raise ValueError('EFF qpdf executable/runtime differs from the frozen build')
        destination = ROOT / '.build-cache/qpdf/t80-r1/runtime'; destination.mkdir(parents=True, exist_ok=True)
        for name, identity in expected.items():
            target = destination / name
            if target.exists() and (target.is_symlink() or digest(target) != identity):
                raise ValueError('Existing EFF qpdf runtime changed')
            shutil.copy2(prepared / 'runtime' / name, target)
    print('Pinned qpdf 12.4.0-folio-t80-r1 installed.')


if __name__ == '__main__': main()
