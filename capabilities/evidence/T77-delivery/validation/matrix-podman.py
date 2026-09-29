#!/usr/bin/env python3
import json
import os
import sys
from pathlib import Path
root = Path('/workspace/folio-pdf')
args = sys.argv[1:]
assert args[0] == 'run'
images = {
    'a4da319337cb6504ba4fb663cbd72a75df5515efb31bbdc6fc4ad7ba8d710dee': '8',
    '09f6797de424a6a085da4db6ba64381b2a4d51c2100a5e91998b69d7c23e0ecb': '11',
    '61a94244559f2e89e4edb02bae37eeb8762ecf5deaf237251fa630e5120a8798': '17',
    '1ca5e470ad60db0d5b4137c1357068fa210051d815ad98ea9964f4bbe7367f8c': '21',
}
image = next(arg for arg in args if arg.startswith('docker.io/library/eclipse-temurin@sha256:'))
jdk = images[image.split(':')[-1]]
base = root / '.build-cache/T77-preflight/matrix' / ('jdk' + jdk)
base.mkdir(parents=True, exist_ok=True)
for index, arg in enumerate(args):
    if arg == str(root) + ':/workspace:Z':
        args[index] = str(root) + ':/workspace:ro'
mounts = ['--network=none', '--volume', str(root / '.build-cache') + ':/workspace/.build-cache:rw']
for module in ('', 'pdf-bom', 'pdf-provider-contract', 'pdf-document', 'pdf-acceptance', 'pdf-conversion', 'pdf-migration-itext7', 'pdf-migration-itext7-preview', 'build-tools/inventory', 'build-tools/release'):
    target = base / (module or 'root') / 'target'
    target.mkdir(parents=True, exist_ok=True)
    inside = '/workspace/' + (module + '/' if module else '') + 'target'
    mounts += ['--volume', str(target) + ':' + inside + ':rw']
args[1:1] = mounts
(base / 'podman-command.json').write_text(json.dumps(['podman'] + args, indent=2) + '\n')
os.execvp('podman', ['podman'] + args)
