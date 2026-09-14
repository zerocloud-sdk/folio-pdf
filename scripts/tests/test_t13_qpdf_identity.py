"""Exercise qpdf identity failures through the public T13 observer commands."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import tempfile
import time
import unittest

ROOT = Path(__file__).resolve().parents[2]


class T13QpdfIdentityTest(unittest.TestCase):
    def test_empty_and_relative_cache_values_select_the_same_observed_and_executed_tool(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            staged = directory / 'repository'
            shutil.copytree(ROOT / 'capabilities/profiles/T13-text', staged / 'capabilities/profiles/T13-text')
            (staged / 'scripts/container-bin').mkdir(parents=True)
            for name in ('qpdf-pin.properties', 'container-bin/qpdf', 't13-qpdf-runtime.sha256'):
                shutil.copy2(ROOT / 'scripts' / name, staged / 'scripts' / name)
            genuine = ROOT / '.build-cache/qpdf'
            launcher = directory / 'launcher'
            launcher.mkdir()
            (launcher / '12.4.0').symlink_to(genuine / '12.4.0', target_is_directory=True)
            (directory / 'cache').symlink_to(genuine, target_is_directory=True)
            original = ROOT / 'capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf'
            truncated = directory / 'truncated.pdf'
            truncated.write_bytes(b'%PDF-2.0\n1 0 obj\n')
            substitute = directory / 'substitute/12.4.0'
            (substitute / 'bin').mkdir(parents=True)
            fake = substitute / 'bin/qpdf'
            fake.write_text('#!/usr/bin/python3\nimport os, sys\nargs = sys.argv[1:]\n'
                            'if args and args[-1].endswith(".pdf"):\n    args[-1] = ' + repr(str(original)) + '\n'
                            'os.environ["QPDF_CACHE_DIRECTORY"] = ' + repr(str(genuine)) + '\n'
                            'executable = ' + repr(str(ROOT / 'scripts/container-bin/qpdf')) + '\n'
                            'os.execv(executable, [executable] + args)\n')
            fake.chmod(0o700)
            for name in ('.archive-sha256', '.binary-sha256'):
                shutil.copyfile(genuine / '12.4.0' / name, substitute / name)
            (staged / '.build-cache').mkdir()
            default = staged / '.build-cache/qpdf'
            default.symlink_to(substitute.parent, target_is_directory=True)
            for sample, cache, pdf, expected in (
                    ('empty-substitute', '', truncated, 'indeterminate'),
                    ('absolute-substitute', str(substitute.parent), truncated, 'indeterminate'),
                    ('absolute-genuine', str(genuine), truncated, 'indeterminate'),
                    ('relative-genuine', '../cache', original, 'pass'),
                    ('empty-genuine', '', original, 'pass'),
                    ('file-link-empty', '', truncated, 'indeterminate'),
                    ('file-link-unset', None, truncated, 'indeterminate'),
                    ('file-link-absolute', str(genuine), truncated, 'indeterminate'),
                    ('directory-link-substitute', '', truncated, 'indeterminate'),
                    ('directory-link-genuine', '', original, 'pass'),
                    ('directory-link-pin-change', str(genuine), truncated, 'indeterminate')):
                if sample == 'empty-genuine':
                    default.unlink()
                    default.symlink_to(genuine, target_is_directory=True)
                if sample == 'file-link-empty':
                    alternate = directory / 'alternate'
                    (alternate / 'scripts/container-bin').mkdir(parents=True)
                    for name in ('qpdf-pin.properties', 'container-bin/qpdf'):
                        shutil.copy2(ROOT / 'scripts' / name, alternate / 'scripts' / name)
                    (alternate / '.build-cache').mkdir()
                    alternate_default = alternate / '.build-cache/qpdf'
                    alternate_default.symlink_to(substitute.parent, target_is_directory=True)
                    (staged / 'scripts/container-bin/qpdf').unlink()
                    (staged / 'scripts/container-bin/qpdf').symlink_to(alternate / 'scripts/container-bin/qpdf')
                if sample == 'directory-link-substitute':
                    (staged / 'scripts/container-bin/qpdf').unlink()
                    (staged / 'scripts/container-bin').rmdir()
                    (staged / 'scripts/container-bin').symlink_to(alternate / 'scripts/container-bin', target_is_directory=True)
                if sample == 'directory-link-genuine':
                    alternate_default.unlink()
                    alternate_default.symlink_to(genuine, target_is_directory=True)
                if sample == 'directory-link-pin-change':
                    selected_pin = alternate / 'scripts/qpdf-pin.properties'
                    selected_pin.write_text(selected_pin.read_text() + 'QPDF_CACHE_DIRECTORY=' + str(substitute.parent) + '\n')
                environment = dict(os.environ)
                if cache is None:
                    environment.pop('QPDF_CACHE_DIRECTORY', None)
                else:
                    environment['QPDF_CACHE_DIRECTORY'] = cache
                for script, scope, chain in (('t13-program-standards.py', 'cmaps', 'standards'),
                                             ('t13-semantics.py', 'embedded-font-kinds', 'semantic')):
                    with self.subTest(sample=sample, observer=script):
                        output = directory / (sample + '-' + chain)
                        result = subprocess.run([sys.executable, str(ROOT / 'scripts' / script), str(staged), str(pdf),
                                                 scope, str(output)], cwd=launcher,
                                                env=environment,
                                                capture_output=True, text=True, timeout=45)
                        self.assertEqual(0, result.returncode, result.stderr)
                        values = dict(line.split('=', 1) for line in (output / 'result.properties').read_text().splitlines())
                        self.assertEqual(expected, values[chain], values['finding'])

    def test_a_pin_changed_after_tool_start_cannot_retain_a_passing_result(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            staged = directory / 'repository'
            shutil.copytree(ROOT / 'capabilities/profiles/T13-text', staged / 'capabilities/profiles/T13-text')
            (staged / 'scripts/container-bin').mkdir(parents=True)
            for name in ('qpdf-pin.properties', 'container-bin/qpdf', 't13-qpdf-runtime.sha256'):
                shutil.copy2(ROOT / 'scripts' / name, staged / 'scripts' / name)
            (staged / '.build-cache').mkdir()
            (staged / '.build-cache/qpdf').symlink_to(ROOT / '.build-cache/qpdf', target_is_directory=True)
            pin = staged / 'scripts/qpdf-pin.properties'
            original_pin = pin.read_bytes()
            original = ROOT / 'capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf'
            for script, scope, chain in (('t13-program-standards.py', 'cmaps', 'standards'),
                                         ('t13-semantics.py', 'embedded-font-kinds', 'semantic')):
                with self.subTest(observer=script):
                    pin.write_bytes(original_pin)
                    output = directory / chain
                    process = subprocess.Popen([sys.executable, str(ROOT / 'scripts' / script), str(staged), str(original),
                                                scope, str(output)], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
                    try:
                        version = output / 'qpdf-version.txt'
                        deadline = time.monotonic() + 10
                        while not (version.is_file() and version.stat().st_size) and process.poll() is None and time.monotonic() < deadline:
                            time.sleep(.001)
                        self.assertIsNone(process.poll(), 'The observer must still be running when its pin changes')
                        self.assertTrue(version.is_file() and version.stat().st_size)
                        pin.write_bytes(original_pin + b'# changed after the actual version observation\n')
                        stdout, stderr = process.communicate(timeout=45)
                        self.assertEqual(0, process.returncode, stderr)
                    finally:
                        if process.poll() is None:
                            process.kill()
                            process.communicate()
                    values = dict(line.split('=', 1) for line in (output / 'result.properties').read_text().splitlines())
                    self.assertEqual('indeterminate', values[chain], values['finding'])

    def test_shared_library_identity_is_required_even_when_observations_are_unchanged(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            cache = directory / 'changed-cache'
            shutil.copytree(ROOT / '.build-cache/qpdf/12.4.0', cache / '12.4.0', symlinks=True)
            library = cache / '12.4.0/lib/libqpdf.so.30.4.0'
            data = bytearray(library.read_bytes())
            self.assertEqual('40bc77ad1cf7a085ceb36a0c3d98315807cd5403116e346a5e9a94731351fb5e',
                             hashlib.sha256(data).hexdigest())
            offset = struct.unpack_from('<Q', data, 32)[0]
            entry_size, count = struct.unpack_from('<HH', data, 54)
            build_id = None
            for index in range(count):
                kind, flags, start, address, physical, size, memory, alignment = struct.unpack_from(
                    '<IIQQQQQQ', data, offset + index * entry_size)
                if kind == 4:
                    cursor = start
                    while cursor + 12 <= start + size:
                        name_size, value_size, note_type = struct.unpack_from('<III', data, cursor)
                        note_name = data[cursor + 12:cursor + 12 + name_size]
                        value_start = cursor + 12 + (name_size + 3) // 4 * 4
                        if note_type == 3 and note_name == b'GNU\x00':
                            build_id = value_start
                        cursor = value_start + (value_size + 3) // 4 * 4
            self.assertIsNotNone(build_id)
            # Changing GNU build-id metadata preserves the actual qpdf observations but invalidates its identity.
            data[build_id] ^= 1
            library.write_bytes(data)
            environment = dict(os.environ, QPDF_CACHE_DIRECTORY=str(cache))
            version = subprocess.run([str(ROOT / 'scripts/container-bin/qpdf'), '--version'],
                                     env=environment, capture_output=True, text=True, timeout=20)
            self.assertEqual(0, version.returncode, version.stderr)
            self.assertTrue(version.stdout.startswith('qpdf version 12.4.0\n'))
            original = ROOT / 'capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf'
            for script, scope, chain in (('t13-program-standards.py', 'cmaps', 'standards'),
                                         ('t13-semantics.py', 'embedded-font-kinds', 'semantic')):
                with self.subTest(observer=script):
                    output = directory / chain
                    result = subprocess.run([sys.executable, str(ROOT / 'scripts' / script), str(ROOT), str(original),
                                             scope, str(output)], env=environment, capture_output=True, text=True, timeout=45)
                    self.assertEqual(0, result.returncode, result.stderr)
                    values = dict(line.split('=', 1) for line in (output / 'result.properties').read_text().splitlines())
                    self.assertEqual('indeterminate', values[chain], values['finding'])
                    self.assertIn('runtime', values['finding'])

    def test_redirected_pin_cannot_substitute_the_observed_pdf(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            staged = directory / 'repository'
            shutil.copytree(ROOT / 'capabilities/profiles/T13-text', staged / 'capabilities/profiles/T13-text')
            (staged / 'scripts/container-bin').mkdir(parents=True)
            for name in ('qpdf-pin.properties', 'container-bin/qpdf', 't13-qpdf-runtime.sha256'):
                shutil.copy2(ROOT / 'scripts' / name, staged / 'scripts' / name)
            (staged / '.build-cache').mkdir()
            (staged / '.build-cache/qpdf').symlink_to(ROOT / '.build-cache/qpdf', target_is_directory=True)
            original = ROOT / 'capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf'
            changed = directory / 'changed.pdf'
            changed.write_bytes(original.read_bytes().replace(b'/CMapType 1 def', b'/CMapType 2 def'))
            self.assertNotEqual(original.read_bytes(), changed.read_bytes())
            cache = directory / 'redirected-cache'
            (cache / '12.4.0/bin').mkdir(parents=True)
            substitute = cache / '12.4.0/bin/qpdf'
            substitute.write_text('#!/usr/bin/python3\nimport os, sys\nargs = sys.argv[1:]\n'
                                  'if args and args[-1].endswith(".pdf"):\n    args[-1] = ' + repr(str(original)) + '\n'
                                  'os.environ.pop("QPDF_CACHE_DIRECTORY", None)\n'
                                  'executable = ' + repr(str(ROOT / 'scripts/container-bin/qpdf')) + '\n'
                                  'os.execv(executable, [executable] + args)\n')
            substitute.chmod(0o700)
            for name in ('.archive-sha256', '.binary-sha256'):
                shutil.copyfile(ROOT / '.build-cache/qpdf/12.4.0' / name, cache / '12.4.0' / name)
            pin = staged / 'scripts/qpdf-pin.properties'
            pin.write_text(pin.read_text() + 'QPDF_CACHE_DIRECTORY=' + str(cache) + '\n')
            for script, scope, chain in (('t13-program-standards.py', 'cmaps', 'standards'),
                                         ('t13-semantics.py', 'embedded-font-kinds', 'semantic')):
                with self.subTest(observer=script):
                    output = directory / chain
                    command = [sys.executable, str(ROOT / 'scripts' / script), str(staged), str(changed), scope, str(output)]
                    result = subprocess.run(command, capture_output=True, text=True, timeout=45)
                    self.assertEqual(0, result.returncode, result.stderr)
                    values = dict(line.split('=', 1) for line in (output / 'result.properties').read_text().splitlines())
                    self.assertEqual('indeterminate', values[chain], values['finding'])
                    self.assertEqual(hashlib.sha256(changed.read_bytes()).hexdigest(), values['input-sha256'])


if __name__ == '__main__':
    unittest.main()
