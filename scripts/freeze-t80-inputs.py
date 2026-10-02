#!/usr/bin/env python3
"""Freeze scope authorities before complete qualification, never reseal a run."""
import hashlib
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    paths = set()
    for folder in ('capabilities/profiles/T80-embedded-files-only', 'capabilities/profiles/T80-controls'):
        paths.update(path for path in (ROOT / folder).rglob('*') if path.is_file())
    names = ['scripts/t78-evidence-pin.properties', 'scripts/t80-certification.py', 'scripts/t80-observer.py',
             'scripts/t80-byte-check.py', 'scripts/t80-dictionary-check.py', 'scripts/generate-t80-corpus.py', 'scripts/generate-t80-controls.py',
             'scripts/provision-t80-qpdf.py', 'scripts/t80-qpdf-pin.properties', 'scripts/t80-qpdf-runtime.sha256',
             'scripts/container-bin/t80-qpdf', 'build-tools/acceptance/qpdf/t80-r1.patch',
             'build-tools/acceptance/qpdf/t80-build-packages.txt',
             'scripts/freeze-t80-inputs.py', 'scripts/provision-t80-arlington-model.py',
             'scripts/t80-arlington-runtime.sha256', 'build-tools/acceptance/arlington/t80-input.patch',
             'build-tools/acceptance/arlington/t80-output.patch',
             'docs/research/T80-embedded-files-only-profile-audit.md']
    paths.update(ROOT / name for name in names)
    folder = ROOT / '.build-cache/t80-arlington-model'
    expected = {}
    for line in (ROOT / 'scripts/t80-arlington-runtime.sha256').read_text().splitlines():
        identity, name = line.split('  ', 1)
        path = folder / name
        if path.resolve(strict=True) != path or digest(path) != identity:
            raise ValueError('Frozen embedded-files-only model changed')
        expected[name] = identity
    if {p.relative_to(folder).as_posix() for p in folder.rglob('*') if p.is_file()} != set(expected):
        raise ValueError('Unpinned embedded-files-only model file')
    data = '# Frozen T80 acceptance inputs; changes require complete requalification.\n'
    for path in sorted(paths):
        if path.resolve(strict=True) != path: raise ValueError('Linked embedded-files-only acceptance input')
        data += path.relative_to(ROOT).as_posix() + '=' + digest(path) + '\n'
    pin = ROOT / 'scripts/t80-evidence-pin.properties'
    pin.write_text(data)
    identity = digest(pin)
    for name in ('pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/T80EvidenceCommand.java',
                 'scripts/t80_foundation_reports.py'):
        path = ROOT / name
        text, count = re.subn(r'(PIN_SHA256 = [\"\'])[^\"\']+([\"\'])',
                              lambda match: match[1] + identity + match[2], path.read_text())
        if count != 1: raise ValueError('Missing embedded-files-only pin consumer')
        path.write_text(text)
    print('T80 authorities frozen: ' + identity)


if __name__ == '__main__': main()
