#!/usr/bin/env python3
"""Build the separately pinned, acceptance-only pdfcpu security supplement."""
import hashlib
import os
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import tarfile
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    pin = dict(line.split('=', 1) for line in (ROOT / 'scripts/t78-pdfcpu-pin.properties').read_text().splitlines() if line)
    patch = ROOT / 'build-tools/acceptance/pdfcpu/t78-r1.patch'
    if digest(patch) != pin['patch-sha256']: raise ValueError('The acceptance supplement changed')
    cache = ROOT / '.build-cache/t78-provision'; cache.mkdir(parents=True, exist_ok=True)
    archive = cache / 'pdfcpu-source.tar.gz'
    if not archive.exists():
        with urllib.request.urlopen(pin['source-archive'], timeout=60) as response, archive.open('xb') as stream:
            total = 0
            while True:
                block = response.read(1024 * 1024)
                if not block: break
                total += len(block)
                if total > 320 * 1024 * 1024: raise ValueError('Public source archive exceeds its bound')
                stream.write(block)
    if archive.is_symlink() or digest(archive) != pin['source-archive-sha256']:
        raise ValueError('Public source archive identity mismatch')
    with tempfile.TemporaryDirectory(prefix='pdfcpu-build-', dir=cache) as directory:
        source = Path(directory) / 'source'; source.mkdir()
        with tarfile.open(archive) as files:
            for entry in files:
                relative = PurePosixPath(entry.name).relative_to('pdfcpu-' + pin['source-commit'])
                if not entry.isfile() or not relative.parts: continue
                if relative.is_absolute() or '..' in relative.parts: raise ValueError('Escaping source archive entry')
                if 'testdata' in relative.parts or entry.name.endswith('_test.go'): continue
                if relative.parts[0] not in ('cmd', 'pkg', 'internal', 'go.mod', 'go.sum', 'LICENSE', 'README.md'): continue
                path = source / relative; path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(files.extractfile(entry).read())
        with patch.open('rb') as stream:
            subprocess.run(['patch', '--batch', '--fuzz=0', '-s', '-p1'], cwd=source, stdin=stream, check=True, timeout=15)
        environment = dict(os.environ, GOTOOLCHAIN='go1.25.0', GOPATH=str(ROOT / '.build-cache/t78-go'),
                           GOCACHE=str(ROOT / '.build-cache/t78-go-build'), CGO_ENABLED='0',
                           GOOS='linux', GOARCH='amd64', GOAMD64='v1')
        subprocess.run(['go', 'version'], cwd=source, env=environment, check=True, timeout=180)
        compiler = ROOT / '.build-cache/t78-go/pkg/mod/golang.org/toolchain@v0.0.1-go1.25.0.linux-amd64/bin/go'
        if digest(compiler) != pin['go-executable-sha256']: raise ValueError('Acceptance compiler identity mismatch')
        executable = Path(directory) / 'pdfcpu'
        flags = ('-X github.com/pdfcpu/pdfcpu/pkg/pdfcpu/model.VersionStr=v0.15.0-folio-t78-r1 '
                 '-X main.commit=' + pin['source-commit'] + '+folio-t78-r1 -X main.date=2026-09-29T00:00:00Z')
        subprocess.run(['go', 'build', '-mod=readonly', '-trimpath', '-buildvcs=false', '-ldflags', flags,
                        '-o', str(executable), './cmd/pdfcpu'], cwd=source, env=environment, check=True, timeout=600)
        if digest(executable) != pin['sha256']: raise ValueError('Acceptance executable does not match the frozen build')
        destination = ROOT / '.build-cache/pdfcpu/t78-r1/pdfcpu'
        destination.parent.mkdir(parents=True, exist_ok=True)
        if destination.exists() and digest(destination) != pin['sha256']: raise ValueError('Existing acceptance cache identity mismatch')
        shutil.copyfile(executable, destination); destination.chmod(0o755)
    print('Pinned pdfcpu 0.15.0-folio-t78-r1 installed.')


if __name__ == '__main__': main()
