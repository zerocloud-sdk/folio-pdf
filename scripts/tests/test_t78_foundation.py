"""Baseline recorder/collector rejection contracts; live replay is a separate gate."""
import ctypes
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
import t78_foundation_reports as COLLECTOR
C = COLLECTOR.CERTIFICATION
O = C.OBSERVER


def load(name):
    spec = importlib.util.spec_from_file_location(name.replace('-', '_'), ROOT / 'scripts' / (name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def seal(run):
    manifest, result = run / 'retained-files.sha256', run / 'result.properties'
    manifest.write_text(''.join(O.digest(path.read_bytes()) + '  ' + path.relative_to(run).as_posix() + '\n'
        for path in sorted(run.rglob('*')) if path.is_file() and path not in (manifest, result)))
    fields = C.properties(result.read_bytes())
    fields['retained-files-sha256'] = O.digest(manifest.read_bytes())
    result.write_text(''.join(key + '=' + value + '\n' for key, value in sorted(fields.items())))


class PasswordFoundationTest(unittest.TestCase):
    def test_baseline_route_keeps_actual_facade_mode_and_two_native_modes(self):
        foundation = load('t03-foundation')
        case = foundation.certification_case('password-baseline')
        self.assertEqual(44, case['test-count'])
        self.assertEqual('T32-password-baseline', case['profile'])
        self.assertEqual('IN_PROCESS', case['facade-execution-profile'])
        self.assertEqual('qpdf-t78-r1', case['syntax-producer'])
        self.assertIn('scripts/t78-evidence-pin.properties', case['configuration-paths'])
        root = Path('/repository')
        for mode in ('IN_PROCESS', 'HARDENED_WORKER'):
            plan = foundation.execution_plan(root, root / 'run', 'image@sha256:fixed',
                    Path('/native/bin/folio-harfbuzz'), '/workspace/acceptance.jar', case, mode)
            self.assertIn('-Dfolio.t78.executionProfile=' + mode, plan['java-options'])
            self.assertIn('net.zerocloud.pdf.acceptance.T78EvidenceCommand', plan['recorder-command'])

    def test_original_fixture_authors_reproduce_bytes_without_product_or_checker(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            author = load('generate-t78-corpus')
            author.build(root / 'corpus')
            controls = load('generate-t78-controls')
            controls.DESTINATION = root / 'controls'
            controls.build()
            for source, produced in [('T78-password', 'corpus'), ('T78-controls', 'controls')]:
                for path in (ROOT / 'capabilities/profiles' / source).iterdir():
                    self.assertEqual(path.read_bytes(), (root / produced / path.name).read_bytes(), path.name)

    def test_missing_tool_cannot_write_passing_syntax(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); observer = O.Observations(ROOT, root)
            observer.qpdf = root / 'missing-qpdf'
            # The supervisor reports command-not-found as exit 127. That must
            # abort the chain; a failed report can never become certification.
            with self.assertRaisesRegex(ValueError, 'Original input or product syntax failed'):
                C.observe_one(observer, ROOT / 'capabilities/profiles/T78-password/aes-256-r6.pdf',
                              {'algorithm': 'AES_256', 'credential': 'ordinary'}, 'product', {}, True)
            self.assertEqual(127, json.loads((root / 'product/syntax/qpdf.command.json').read_text())['exit-code'])
            self.assertEqual('fail', json.loads((root / 'product/syntax/result.json').read_text())['result'])
            self.assertFalse((root / 'result.properties').exists())

    def test_missing_pinned_tool_or_changed_identity_prevents_recording(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); (root / 'scripts').mkdir()
            shutil.copyfile(ROOT / 'scripts/t78-evidence-pin.properties', root / 'scripts/t78-evidence-pin.properties')
            with self.assertRaises((FileNotFoundError, ValueError)):
                C.identities(root, original=False)
            self.assertFalse((root / 'result.properties').exists())

    def test_required_rule_without_coverage_is_rejected_before_tools(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); folder = root / 'capabilities/profiles/T78-controls'; folder.mkdir(parents=True)
            (folder / 'rules.json').write_text(json.dumps({'profile': O.PROFILE, 'required-rules': ['absent-rule'], 'rules': {}}))
            observer = O.Observations(root, root / 'run')
            with self.assertRaisesRegex(ValueError, 'Missing required security rule'):
                C.qualify(observer)

    def test_undetected_control_cannot_be_qualified(self):
        with tempfile.TemporaryDirectory() as temporary:
            observer = O.Observations(ROOT, Path(temporary))
            with mock.patch.object(observer, 'dictionary_standards', return_value=[]):
                with self.assertRaisesRegex(ValueError, 'Undetected security control'):
                    C.qualify(observer)

    def test_invalid_independent_credentials_are_not_a_passing_label(self):
        observer = mock.Mock()
        observer.pypdf.return_value = {'authenticated': False, 'authority': 0,
                                      'perms-invalid': False, 'diagnostic': 'none'}
        case = {'credential': 'ordinary', 'algorithm': 'AES_256'}
        with self.assertRaisesRegex(ValueError, 'Independent user authentication'):
            C.credential_proofs(observer, Path('original.pdf'), case, 'proof')
        observer.authentication.assert_not_called()

    def test_collector_rejects_falsely_declared_modes_before_replay(self):
        with tempfile.TemporaryDirectory() as temporary:
            run = Path(temporary)
            result = {'profile': O.PROFILE, 'phase': 'certification', 'native-execution-profile': 'IN_PROCESS',
                      'facade-execution-profile': 'HARDENED_WORKER', **dict.fromkeys(C.CHAINS, 'pass')}
            (run / 'result.properties').write_text(''.join(k + '=' + v + '\n' for k, v in result.items()))
            seal(run)
            with mock.patch.object(C, 'observe') as replay:
                with self.assertRaisesRegex(ValueError, 'Wrong or incomplete'):
                    COLLECTOR.collect_reports(ROOT, run, 'IN_PROCESS')
                replay.assert_not_called()

    def test_resealed_tool_finding_cannot_change_independent_replay(self):
        # A deterministic external process supplies this protocol-only observation.
        # It is never a PDF oracle or retained Foundation evidence.
        with tempfile.TemporaryDirectory() as temporary:
            run = Path(temporary)
            command = [sys.executable, '-I', '-c', 'print("fixed independent finding")']
            O.Observations(ROOT, run).process(command, 'tool/observed')
            (run / 'result.properties').write_text('syntax=pass\n')
            seal(run)
            O.Observations(ROOT, run, True).process(command, 'tool/observed')
            observed = run / 'tool/observed.stdout'
            observed.write_text('a changed finding relabeled pass\n')
            receipt = run / 'tool/observed.command.json'
            data = json.loads(receipt.read_text()); data['stdout-sha256'] = O.digest(observed.read_bytes())
            receipt.write_bytes(O.json_bytes(data)); seal(run)
            with self.assertRaisesRegex(ValueError, 'differs from independent replay'):
                O.Observations(ROOT, run, True).process(command, 'tool/observed')

    def test_cancelled_coordinator_stops_active_tool_and_unwinds_private_files(self):
        self.check_coordinator_termination(signal.SIGTERM)

    def test_forced_coordinator_death_stops_active_tool(self):
        self.check_coordinator_termination(signal.SIGKILL)

    def test_supervisor_preserves_binary_input_streams_and_exit_status(self):
        with tempfile.TemporaryDirectory() as temporary:
            observer = O.Observations(ROOT, Path(temporary))
            value = b'\x00\x80\xffsynthetic-channel'
            result = observer.process([sys.executable, '-I', '-c',
                'import sys;sys.stdout.buffer.write(sys.stdin.buffer.read());sys.stderr.write("fixed diagnostic");sys.exit(7)'],
                'tool/channel', stdin=value)
            self.assertEqual((7, value, b'fixed diagnostic'), result)

    def check_coordinator_termination(self, termination):
        # This real child only sleeps; its PID and private-directory marker prove
        # lifecycle behavior without using credentials or PDF implementation data.
        code = '''
import importlib.util, pathlib, sys, tempfile
root, run = map(pathlib.Path, sys.argv[1:])
spec = importlib.util.spec_from_file_location('observer', root / 'scripts/t78-observer.py')
observer = importlib.util.module_from_spec(spec); spec.loader.exec_module(observer)
with observer.cancellation_scope(), tempfile.TemporaryDirectory(dir=run) as private:
    (run / 'private-marker').write_text(private)
    pathlib.Path(private, 'private').write_bytes(b'synthetic-private-data')
    observer.Observations(root, run).process([sys.executable, '-I', '-c',
        'import os,pathlib,subprocess,sys,time;run=pathlib.Path(sys.argv[1]);'
        'subprocess.Popen([sys.executable,"-I","-c","import os,pathlib,sys,time;pathlib.Path(sys.argv[1]).write_text(str(os.getpid()));time.sleep(60)",str(run/"descendant")]);'
        '(run/"supervisor").write_text(str(os.getppid()));(run/"pid").write_text(str(os.getpid()));time.sleep(60)',
        str(run)], 'tool/observed')
'''
        with tempfile.TemporaryDirectory() as temporary:
            run = Path(temporary)
            # Adopt and reap the killed fixture descendants instead of leaving
            # zombies to a container init that may not implement child reaping.
            libc, prior = ctypes.CDLL(None), ctypes.c_int()
            self.assertEqual(0, libc.prctl(37, ctypes.byref(prior), 0, 0, 0))
            self.assertEqual(0, libc.prctl(36, 1, 0, 0, 0))
            coordinator = subprocess.Popen([sys.executable, '-I', '-c', code, str(ROOT), str(run)],
                                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            pids = []
            try:
                deadline = time.monotonic() + 10
                while not all((run / name).exists() for name in ('pid', 'supervisor', 'descendant')) and coordinator.poll() is None and time.monotonic() < deadline:
                    time.sleep(.01)
                self.assertTrue((run / 'descendant').is_file(), 'Tool descendant did not start')
                pids = [int((run / name).read_text()) for name in ('pid', 'supervisor', 'descendant')]
                coordinator.send_signal(termination)
                coordinator.wait(timeout=10)
                def running(pid):
                    status = Path('/proc') / str(pid) / 'stat'
                    return status.exists() and status.read_text().split(') ', 1)[1].split()[0] != 'Z'
                deadline = time.monotonic() + 5
                while any(running(pid) for pid in pids) and time.monotonic() < deadline: time.sleep(.01)
                self.assertFalse(any(running(pid) for pid in pids), 'Tool or descendant survived its coordinator')
                if termination == signal.SIGTERM:
                    self.assertFalse(Path((run / 'private-marker').read_text()).exists(), 'Private files survived cancellation')
                self.assertFalse((run / 'tool/observed.command.json').exists(), 'A cancelled observation was retained')
            finally:
                if coordinator.poll() is None:
                    coordinator.kill(); coordinator.wait(timeout=5)
                for pid in pids:
                    try: os.kill(pid, signal.SIGKILL)
                    except ProcessLookupError: pass
                    try: os.waitpid(pid, 0)
                    except ChildProcessError: pass
                libc.prctl(36, prior.value, 0, 0, 0)


def qualify_live(run):
    """Replay the actual run, then require resealed public/tool mutations to fail."""
    reports = COLLECTOR.collect_reports(ROOT, run, 'IN_PROCESS')
    if any(len(value['products']) != 68 for value in reports.values()):
        raise ValueError('Live baseline collection did not include all original products')
    first = next(iter(json.loads((ROOT / 'capabilities/profiles/T78-password/products.json').read_text())['products']))
    mutations = [(run / ('native-' + first) / 'syntax/qpdf.stdout', b'changed and resealed syntax finding\n'),
                 (run / ('native-' + first) / 'publication.properties', b'execution-profile=HARDENED_WORKER\n')]
    for path, changed in mutations:
        original = path.read_bytes()
        try:
            path.write_bytes(changed); seal(run)
            try: COLLECTOR.collect_reports(ROOT, run, 'IN_PROCESS')
            except (ValueError, KeyError): pass
            else: raise ValueError('A resealed live baseline mutation was accepted')
        finally:
            path.write_bytes(original); seal(run)
    print('T78 live collector replay and resealed-finding/mode rejection passed')


if __name__ == '__main__':
    if len(sys.argv) == 3 and sys.argv[1] == '--qualify-run': qualify_live(Path(sys.argv[2]).resolve())
    else: unittest.main()
