#!/usr/bin/env python3
"""Freeze acceptance authorities before qualification; never updates old evidence."""
import hashlib
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def main():
    paths = set()
    for folder in ('capabilities/profiles/T03-standards', 'capabilities/profiles/T78-password',
                   'capabilities/profiles/T78-controls', 'build-tools/acceptance/pdfcpu',
                   'build-tools/acceptance/qpdf'):
        paths.update(p for p in (ROOT / folder).rglob('*') if p.is_file())
    names = ['scripts/container-bin/t78-qpdf', 'scripts/container-bin/pdfium',
             'scripts/container-bin/imagemagick', 'scripts/visual-tool-common',
             'scripts/t78-certification.py', 'scripts/t78-observer.py', 'scripts/t78-pypdf-check.py',
             'scripts/freeze-t78-inputs.py', 'scripts/generate-t78-corpus.py', 'scripts/generate-t78-controls.py',
             'scripts/t78-qpdf-pin.properties', 'scripts/t78-pdfcpu-pin.properties',
             'scripts/t78-checkers-pin.properties', 'scripts/t78-qpdf-runtime.sha256',
             'scripts/t78-arlington-runtime.sha256', 'scripts/t78-checkers-runtime.sha256',
             'scripts/arlington-pin.properties', 'scripts/pdfcpu-pin.properties', 'scripts/pdfium-pin.properties',
             'scripts/imagemagick-pin.properties', 'scripts/imagemagick-runtime.sha256',
             'build-tools/acceptance/arlington/t78-input.patch', 'build-tools/acceptance/arlington/t78-output.patch',
             '.build-cache/qpdf/t78-r1/runtime/qpdf', '.build-cache/pdfcpu/t78-r1/pdfcpu',
             '.build-cache/arlington/fe4a1a8/TestGrammar/bin/linux/TestGrammar',
             '.build-cache/pdfcpu/0.15.0/pdfcpu', '.build-cache/pdfium/v0.11.2-chromium-7881/bin/pdfium',
             '.build-cache/imagemagick/7.1.2-30/bin/imagemagick.AppImage']
    paths.update(ROOT / name for name in names)
    for manifest, folder in [('scripts/t78-qpdf-runtime.sha256', '.build-cache/qpdf/t78-r1/runtime'),
                             ('scripts/t78-checkers-runtime.sha256', '.build-cache/t78-security-checkers'),
                             ('scripts/t78-arlington-runtime.sha256', '.build-cache/t78-arlington-model'),
                             ('scripts/imagemagick-runtime.sha256', '.build-cache/imagemagick/7.1.2-30/runtime')]:
        for line in (ROOT / manifest).read_text().splitlines():
            expected, name = line.split('  ', 1)
            path = ROOT / folder / name
            if path.is_symlink() or hashlib.sha256(path.read_bytes()).hexdigest() != expected:
                raise ValueError('Frozen independent runtime changed: ' + name)
    data = '# Frozen T78 acceptance inputs; changes require complete requalification.\n'
    for path in sorted(paths):
        if path.resolve(strict=True) != path: raise ValueError('Linked acceptance input')
        data += path.relative_to(ROOT).as_posix() + '=' + hashlib.sha256(path.read_bytes()).hexdigest() + '\n'
    (ROOT / 'scripts/t78-evidence-pin.properties').write_text(data)
    identity = hashlib.sha256(data.encode()).hexdigest()
    for name in ('pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/T78EvidenceCommand.java',
                 'scripts/t78_foundation_reports.py'):
        path = ROOT / name
        changed, count = re.subn(r'(PIN_SHA256 = [\"\'])[^\"\']+([\"\'])',
                                 lambda match: match[1] + identity + match[2], path.read_text())
        if count != 1: raise ValueError('Missing pin consumer')
        path.write_text(changed)
    print('T78 authorities frozen: ' + identity)


if __name__ == '__main__': main()
