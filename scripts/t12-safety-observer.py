#!/usr/bin/env python3
"""Acceptance-only Linux observer of Action canary effects across child processes.

Project-owned, Apache-2.0. Uses Python's standard library and Linux inotify;
it does not load Folio or infer execution from backend implementation details.
Every invocation qualifies all five observations with actual external effects.
Only the declared canary paths and loopback endpoint are observed. A script
observation means its declared file effect, not a JavaScript interpreter trace.
"""
import ctypes
import hashlib
import json
import os
from pathlib import Path
import platform
import select
import socket
import struct
import subprocess
import sys
import threading
import time


class Observer:
    def __init__(self, directory):
        if sys.platform != 'linux':
            raise RuntimeError('T12 effect observations require Linux')
        self.directory = directory
        directory.mkdir()
        self.paths = {kind: directory / kind for kind in ('read', 'write', 'script', 'process')}
        for path in self.paths.values():
            path.write_bytes(b'original canary\n')
        self.paths['process'].write_text('#!/bin/sh\nexit 0\n', encoding='ascii')
        self.paths['process'].chmod(0o700)
        libc = ctypes.CDLL(None, use_errno=True)
        self.fd = libc.inotify_init1(os.O_NONBLOCK | os.O_CLOEXEC)
        if self.fd < 0:
            raise OSError(ctypes.get_errno(), 'inotify_init1')
        self.watches = {}
        for kind, path in self.paths.items():
            # OPEN and ACCESS observe reading/launching; MODIFY observes writes.
            mask = 0x21 if kind in ('read', 'process') else 0x02
            watch = libc.inotify_add_watch(self.fd, os.fsencode(path), mask)
            if watch < 0:
                raise OSError(ctypes.get_errno(), 'inotify_add_watch')
            self.watches[watch] = kind
        self.listener = socket.socket()
        self.listener.bind(('127.0.0.1', 0))
        self.listener.listen(32)
        self.listener.settimeout(0.05)
        self.network = 0
        self.stopped = threading.Event()
        self.thread = threading.Thread(target=self.listen, daemon=True)
        self.thread.start()

    def listen(self):
        while not self.stopped.is_set():
            try:
                connection, _ = self.listener.accept()
                connection.close()
                self.network += 1
            except socket.timeout:
                continue

    def environment(self):
        result = dict(os.environ)
        for kind, path in self.paths.items():
            result['FOLIO_T12_' + ('LAUNCH' if kind == 'process' else kind.upper()) + '_CANARY'] = str(path)
        result['FOLIO_T12_CANARY_PORT'] = str(self.listener.getsockname()[1])
        return result

    def drain(self):
        # All writer processes have exited before draining. This grace period
        # only lets the independent listener consume already queued connections.
        time.sleep(0.1)
        result = {kind: 0 for kind in ('read', 'write', 'script', 'process', 'network')}
        result['network'], self.network = self.network, 0
        while select.select([self.fd], [], [], 0)[0]:
            raw = os.read(self.fd, 65536)
            offset = 0
            while offset < len(raw):
                watch, mask, _, length = struct.unpack_from('iIII', raw, offset)
                offset += 16 + length
                if mask & 0x4000 or watch not in self.watches:
                    raise RuntimeError('Incomplete inotify observation')
                result[self.watches[watch]] += 1
        return result

    def qualify(self):
        command = "import os,socket,subprocess; e=os.environ; " \
            "open(e['FOLIO_T12_READ_CANARY'],'rb').read(); " \
            "open(e['FOLIO_T12_WRITE_CANARY'],'wb').write(b'qualified'); " \
            "open(e['FOLIO_T12_SCRIPT_CANARY'],'wb').write(b'qualified'); " \
            "subprocess.run([e['FOLIO_T12_LAUNCH_CANARY']],check=True); " \
            "socket.create_connection(('127.0.0.1',int(e['FOLIO_T12_CANARY_PORT']))).close()"
        subprocess.run([sys.executable, '-c', command], env=self.environment(), check=True, timeout=10)
        counts = self.drain()
        return {kind: 'pass' if count > 0 else 'indeterminate' for kind, count in counts.items()}, counts

    def close(self):
        self.stopped.set()
        self.thread.join(timeout=1)
        self.listener.close()
        os.close(self.fd)


def main(arguments):
    if len(arguments) < 2:
        raise ValueError('Usage: t12-safety-observer.py <fresh-output> <command> [arguments...]')
    output = Path(arguments[0]).resolve()
    output.mkdir()
    record = {'result': 'indeterminate', 'observer_sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
              'python_sha256': hashlib.sha256(Path(sys.executable).read_bytes()).hexdigest(),
              'python_version': sys.version, 'kernel': platform.release(), 'command': arguments[1:],
              'scope': 'Declared Action canary file access, writes, launch access, script file effect and loopback connection; all descendant processes'}
    observer = None
    try:
        observer = Observer(output / 'canaries')
        record['qualification'], record['qualification_counts'] = observer.qualify()
        with (output / 'child.log').open('wb') as log:
            child = subprocess.run(arguments[1:], env=observer.environment(), stdout=log, stderr=subprocess.STDOUT, timeout=180)
        record['child_exit'] = child.returncode
        record['effects'] = observer.drain()
        if all(value == 'pass' for value in record['qualification'].values()):
            record['result'] = 'pass' if child.returncode == 0 and not any(record['effects'].values()) else 'fail'
    except (OSError, RuntimeError, subprocess.SubprocessError) as failure:
        record['finding'] = str(failure)
    finally:
        if observer is not None:
            observer.close()
        (output / 'observation.json').write_text(json.dumps(record, indent=2, sort_keys=True) + '\n', encoding='utf-8')
        lines = ['result=' + record['result'], 'observer-sha256=' + record['observer_sha256'], 'python-sha256=' + record['python_sha256']]
        for group in ('qualification', 'effects'):
            for key, value in record.get(group, {}).items():
                lines.append(group + '.' + key + '=' + str(value))
        lines.append('child-exit=' + str(record.get('child_exit', 'unavailable')))
        (output / 'observation.properties').write_text('\n'.join(lines) + '\n', encoding='ascii')


if __name__ == '__main__':
    main(sys.argv[1:])
