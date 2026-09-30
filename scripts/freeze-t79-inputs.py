#!/usr/bin/env python3
"""Freeze scope authorities before complete qualification, never reseal a run."""
import hashlib
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    paths = set()
    for folder in ('capabilities/profiles/T79-clear-metadata', 'capabilities/profiles/T79-controls'):
        paths.update(path for path in (ROOT / folder).rglob('*') if path.is_file())
    names = ['scripts/t78-evidence-pin.properties', 'scripts/t79-certification.py', 'scripts/t79-observer.py',
             'scripts/t79-byte-check.py', 'scripts/generate-t79-corpus.py', 'scripts/generate-t79-controls.py',
             'scripts/freeze-t79-inputs.py', 'scripts/provision-t79-arlington-model.py',
             'scripts/t79-arlington-runtime.sha256', 'build-tools/acceptance/arlington/t79-input.patch',
             'build-tools/acceptance/arlington/t79-output.patch',
             'build-tools/acceptance/arlington/t79-all-content-input.patch',
             'build-tools/acceptance/arlington/t79-all-content-output.patch', 'docs/research/T79-clear-metadata-profile-audit.md']
    paths.update(ROOT / name for name in names)
    folder = ROOT / '.build-cache/t79-arlington-model'
    expected = {}
    for line in (ROOT / 'scripts/t79-arlington-runtime.sha256').read_text().splitlines():
        identity, name = line.split('  ', 1)
        path = folder / name
        if path.resolve(strict=True) != path or digest(path) != identity:
            raise ValueError('Frozen clear-metadata model changed')
        expected[name] = identity
    if {p.relative_to(folder).as_posix() for p in folder.rglob('*') if p.is_file()} != set(expected):
        raise ValueError('Unpinned clear-metadata model file')
    data = '# Frozen T79 acceptance inputs; changes require complete requalification.\n'
    for path in sorted(paths):
        if path.resolve(strict=True) != path: raise ValueError('Linked clear-metadata acceptance input')
        data += path.relative_to(ROOT).as_posix() + '=' + digest(path) + '\n'
    pin = ROOT / 'scripts/t79-evidence-pin.properties'
    pin.write_text(data)
    identity = digest(pin)
    for name in ('pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/T79EvidenceCommand.java',
                 'scripts/t79_foundation_reports.py'):
        path = ROOT / name
        text, count = re.subn(r'(PIN_SHA256 = [\"\'])[^\"\']+([\"\'])',
                              lambda match: match[1] + identity + match[2], path.read_text())
        if count != 1: raise ValueError('Missing clear-metadata pin consumer')
        path.write_text(text)
    print('T79 authorities frozen: ' + identity)


if __name__ == '__main__': main()
