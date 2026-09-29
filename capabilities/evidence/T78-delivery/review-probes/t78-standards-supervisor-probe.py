#!/usr/bin/env python3
"""Read-only T78 review probes; synthetic data, bounded children, /tmp only."""
import ctypes
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time

sys.dont_write_bytecode = True
ROOT = Path(sys.argv[1]).resolve()
spec = importlib.util.spec_from_file_location('observer', ROOT / 'scripts/t78-observer.py')
O = importlib.util.module_from_spec(spec)
spec.loader.exec_module(O)


def children(pid):
    try:
        return [int(n) for n in (Path('/proc') / str(pid) / 'task' / str(pid) / 'children').read_text().split()]
    except FileNotFoundError:
        return []


def descendants(pid):
    direct = children(pid)
    return direct + [d for p in direct for d in descendants(p)]


def state(pid):
    try:
        return (Path('/proc') / str(pid) / 'stat').read_text().split(') ', 1)[1].split()[0]
    except FileNotFoundError:
        return 'gone'


def executable(pid):
    try:
        return os.readlink('/proc/' + str(pid) + '/exe')
    except FileNotFoundError:
        return 'gone'


def cleanup(coordinator, group):
    if group is not None:
        try:
            os.killpg(group, signal.SIGKILL)
        except ProcessLookupError:
            pass
    if coordinator.poll() is None:
        coordinator.kill()
        coordinator.wait(timeout=5)
    deadline = time.monotonic() + 5
    while time.monotonic() < deadline:
        try:
            pid, _ = os.waitpid(-1, os.WNOHANG)
            if not pid:
                time.sleep(.01)
        except ChildProcessError:
            return
    raise RuntimeError('Probe descendants did not reap within five seconds')


def appimage():
    code = '''
import importlib.util, pathlib, sys
sys.dont_write_bytecode = True
root, output = map(pathlib.Path, sys.argv[1:])
spec = importlib.util.spec_from_file_location('observer', root / 'scripts/t78-observer.py')
observer = importlib.util.module_from_spec(spec); spec.loader.exec_module(observer)
with observer.cancellation_scope():
    observer.Observations(root, output).process([root / 'scripts/container-bin/imagemagick', '-bench', '100000', '-limit', 'thread', '1', '-size', '16x16', 'xc:white', 'null:'], 'probe')
'''
    with tempfile.TemporaryDirectory(prefix='t78-applied-supervisor-') as private:
        coordinator = subprocess.Popen([sys.executable, '-B', '-c', code, str(ROOT), private],
            env=dict(os.environ, TMPDIR=private, PYTHONDONTWRITEBYTECODE='1'),
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        group, targets = None, []
        try:
            deadline = time.monotonic() + 15
            while time.monotonic() < deadline and coordinator.poll() is None:
                direct = children(coordinator.pid)
                if direct:
                    group = direct[0]
                    targets = descendants(group)
                    if any(Path(executable(p)).name == 'magick' for p in targets):
                        break
                time.sleep(.01)
            assert any(Path(executable(p)).name == 'magick' for p in targets), 'Actual AppImage child did not start'
            names = {p: executable(p).replace(private, '@private').replace(str(ROOT), '@repository') for p in [group] + targets}
            coordinator.kill()
            coordinator.wait(timeout=5)
            time.sleep(.3)
            result = [{'executable': names[p], 'state-after-parent-sigkill': state(p)} for p in [group] + targets]
            assert all(item['state-after-parent-sigkill'] in ('gone', 'Z') for item in result), result
            return result
        finally:
            cleanup(coordinator, group)


def setup_race(reset):
    # Widen only the scheduling interval after the guard's parent-PID check.
    # The inherited-handler variant is a negative control, not product code.
    code = '''
import ctypes, os, pathlib, signal, subprocess, sys, time
cancelled = False
def cancel(signum, frame):
    global cancelled
    cancelled = True
signal.signal(signal.SIGTERM, cancel)
private = pathlib.Path(sys.argv[2])
parent, libc = os.getpid(), ctypes.CDLL(None, use_errno=True)
def guard():
    if sys.argv[3] == 'reset': signal.signal(signal.SIGTERM, signal.SIG_DFL)
    if libc.prctl(1, signal.SIGTERM, 0, 0, 0) != 0 or os.getppid() != parent: os._exit(125)
    (private / 'preexec-pid').write_text(str(os.getpid()))
    time.sleep(.5)
process = subprocess.Popen(['/bin/sh', '-c', sys.argv[1], 'folio-t78-tool', sys.executable, '-B', '-c', 'import os,pathlib,sys,time;pathlib.Path(sys.argv[1]).write_text(str(os.getpid()));time.sleep(60)', str(private / 'tool-pid')], stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True, preexec_fn=guard)
process.wait()
'''
    with tempfile.TemporaryDirectory(prefix='t78-setup-race-') as temporary:
        private = Path(temporary)
        coordinator = subprocess.Popen([sys.executable, '-B', '-c', code, O.SUPERVISOR, str(private), 'reset' if reset else 'inherited'],
                                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        group = None
        try:
            deadline = time.monotonic() + 5
            while time.monotonic() < deadline and coordinator.poll() is None:
                marker = private / 'preexec-pid'
                if marker.exists() and marker.read_text().isdigit():
                    group = int(marker.read_text())
                    break
                time.sleep(.01)
            assert group is not None, 'Setup did not reach barrier'
            coordinator.kill()
            coordinator.wait(timeout=5)
            time.sleep(.8)
            started = (private / 'tool-pid').exists()
            assert started is not reset, (reset, started)
            return {'sigterm-handler-reset': reset, 'tool-started-after-parent-death': started}
        finally:
            cleanup(coordinator, group)


def streams():
    with tempfile.TemporaryDirectory(prefix='t78-stdio-review-') as private:
        payload = bytes(range(256)) * 8
        code, out, err = O.Observations(ROOT, Path(private)).process(
            [sys.executable, '-B', '-c', 'import sys;sys.stdout.buffer.write(sys.stdin.buffer.read());sys.stderr.write("stderr-marker");sys.exit(7)'],
            'probe', stdin=payload)
        assert (code, out, err) == (7, payload, b'stderr-marker')
        return {'binary-stdin-stdout-exact': True, 'stderr-exact': True, 'exit-code': code}


libc, prior = ctypes.CDLL(None), ctypes.c_int()
assert libc.prctl(37, ctypes.byref(prior), 0, 0, 0) == 0
assert libc.prctl(36, 1, 0, 0, 0) == 0
try:
    result = {
        'kind': 'development-standards-review-probes-not-certification',
        'observer-sha256': hashlib.sha256((ROOT / 'scripts/t78-observer.py').read_bytes()).hexdigest(),
        'frozen-inputs-sha256': hashlib.sha256((ROOT / 'scripts/t78-evidence-pin.properties').read_bytes()).hexdigest(),
        'shell': str(Path('/bin/sh').resolve()),
        'supervisor': O.SUPERVISOR,
        'binary-streams': streams(),
        'actual-pinned-appimage-on-parent-sigkill': appimage(),
        'controlled-setup-race': [setup_race(False), setup_race(True)],
        'cleanup': 'All probe groups terminated, children reaped, and private directories removed.'
    }
    print(json.dumps(result, indent=2, sort_keys=True))
finally:
    assert libc.prctl(36, prior.value, 0, 0, 0) == 0
