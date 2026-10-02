"""Embedded-file recorder/collector rejection contracts; live replay is a separate gate."""
import importlib.util
import io
import json
import logging
import os
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
import t80_foundation_reports as COLLECTOR
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


class EmbeddedFilesFoundationTest(unittest.TestCase):
    def test_attachments_route_keeps_actual_facade_mode_and_two_native_modes(self):
        foundation = load('t03-foundation')
        case = foundation.certification_case('password-attachments')
        self.assertEqual(24, case['test-count'])
        self.assertEqual('T32-password-embedded-files-only', case['profile'])
        self.assertEqual('IN_PROCESS', case['facade-execution-profile'])
        self.assertEqual('qpdf-t80-r1', case['syntax-producer'])
        self.assertIn('scripts/t80-evidence-pin.properties', case['configuration-paths'])
        root = Path('/repository')
        for mode in ('IN_PROCESS', 'HARDENED_WORKER'):
            with mock.patch.dict(os.environ, {'FOLIO_FOUNDATION_PYTHON_ROOT': ''}):
                plan = foundation.execution_plan(root, root / 'run', 'image@sha256:fixed',
                        Path('/native/bin/folio-harfbuzz'), '/workspace/acceptance.jar', case, mode)
            self.assertIn('-Dfolio.t80.executionProfile=' + mode, plan['java-options'])
            self.assertIn('net.zerocloud.pdf.acceptance.T80EvidenceCommand', plan['recorder-command'])

    def test_original_fixture_authors_reproduce_bytes_without_product_or_checker(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            author = load('generate-t80-corpus')
            author.DESTINATION = root / 'corpus'
            author.build()
            controls = load('generate-t80-controls')
            controls.DESTINATION = root / 'controls'
            controls.build()
            for source, produced in [('T80-embedded-files-only', 'corpus'), ('T80-controls', 'controls')]:
                for path in (ROOT / 'capabilities/profiles' / source).iterdir():
                    self.assertEqual(path.read_bytes(), (root / produced / path.name).read_bytes(), path.name)

    def test_missing_tool_cannot_write_passing_syntax(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); observer = O.Observations(ROOT, root)
            observer.qpdf = root / 'missing-qpdf'
            # The supervisor reports command-not-found as exit 127. That must
            # abort the chain; a failed report can never become certification.
            self.assertEqual('fail', observer.syntax(ROOT / 'capabilities/profiles/T80-embedded-files-only/aes-256-r6.pdf',
                              b'baseline-user', 'product/syntax'))
            self.assertEqual(127, json.loads((root / 'product/syntax/qpdf.command.json').read_text())['exit-code'])
            self.assertEqual('fail', json.loads((root / 'product/syntax/result.json').read_text())['result'])
            self.assertFalse((root / 'result.properties').exists())

    def test_missing_pinned_tool_or_changed_identity_prevents_recording(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); (root / 'scripts').mkdir()
            shutil.copyfile(ROOT / 'scripts/t80-evidence-pin.properties', root / 'scripts/t80-evidence-pin.properties')
            with self.assertRaises((FileNotFoundError, ValueError)):
                C.identities(root, original=False)
            self.assertFalse((root / 'result.properties').exists())

    def test_required_rule_without_coverage_is_rejected_before_tools(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); folder = root / 'capabilities/profiles/T80-controls'; folder.mkdir(parents=True)
            (folder / 'rules.json').write_text(json.dumps({'profile': O.PROFILE, 'required-rules': ['absent-rule'], 'rules': {}}))
            observer = O.Observations(root, root / 'run')
            with self.assertRaisesRegex(ValueError, 'Missing required embedded-file rule'):
                C.qualify(observer)

    def test_undetected_control_cannot_be_qualified(self):
        with tempfile.TemporaryDirectory() as temporary:
            observer = O.Observations(ROOT, Path(temporary))
            with mock.patch.object(observer, 'dictionary_standards', return_value=[]):
                with self.assertRaisesRegex(ValueError, 'Undetected EFF control'):
                    C.qualify(observer)

    def test_invalid_independent_credentials_are_not_a_passing_label(self):
        observer = mock.Mock()
        observer.pypdf.return_value = {'authenticated': False, 'authority': 0,
                                      'perms-invalid': False, 'diagnostic': 'none'}
        case = {'credential': 'ordinary', 'algorithm': 'AES_256'}
        with self.assertRaisesRegex(ValueError, 'Independent user authentication'):
            C.BASE.credential_proofs(observer, Path('original.pdf'), case, 'proof')
        observer.authentication.assert_not_called()

    def test_authenticated_parser_warning_remains_a_failing_diagnostic(self):
        checker = load('t80-byte-check')
        path = ROOT / 'capabilities/profiles/T80-embedded-files-only/aes-256-r6.pdf'
        capture = io.StringIO()
        logger = logging.getLogger('pypdf')
        decrypt = checker.pypdf.PdfReader.decrypt
        def warned(reader, secret):
            authority = decrypt(reader, secret)
            logger.warning('unexpected authenticated parser warning')
            return authority
        from contextlib import redirect_stdout
        with mock.patch.object(sys, 'argv', ['t80-byte-check.py', str(path)]), \
                mock.patch.object(sys, 'stdin', io.TextIOWrapper(io.BytesIO(b'baseline-user'))), \
                mock.patch.object(checker.pypdf.PdfReader, 'decrypt', warned), redirect_stdout(capture):
            checker.main()
        observation = json.loads(capture.getvalue())
        self.assertTrue(observation['authenticated'])
        self.assertTrue(observation['unauthenticated-page-matches'])
        self.assertEqual('unexpected-diagnostic', observation['diagnostic'])
        self.assertNotIn('unexpected authenticated parser warning', json.dumps(observation))

    def test_clear_reader_never_attempts_an_implicit_empty_password(self):
        checker = load('t80-byte-check')
        from pypdf._encryption import Encryption
        path = ROOT / 'capabilities/profiles/T80-embedded-files-only/aes-256-r6-empty.pdf'
        with mock.patch.object(Encryption, 'verify', side_effect=AssertionError('Unexpected credential attempt')):
            with path.open('rb') as source:
                raw = checker.ClearReader(source, strict=True)
                self.assertIsNone(raw._encryption)
                self.assertEqual(b'Folio T80 clear document title', checker.string_bytes(raw.trailer['/Info']['/Title']))
        dictionary = load('t80-dictionary-check')
        with mock.patch.object(Encryption, 'verify', side_effect=AssertionError('Unexpected dictionary credential attempt')):
            self.assertEqual('pass', dictionary.check(path)['result'])

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

    def test_missing_chain_and_mismatched_native_mode_fail_before_replay(self):
        for missing in C.CHAINS:
            with tempfile.TemporaryDirectory() as temporary:
                run = Path(temporary)
                fields = {'profile': O.PROFILE, 'phase': 'certification', 'native-execution-profile': 'IN_PROCESS',
                          'facade-execution-profile': 'IN_PROCESS', **dict.fromkeys(C.CHAINS, 'pass')}
                del fields[missing]
                (run / 'result.properties').write_text(''.join(key + '=' + value + '\n' for key, value in fields.items()))
                seal(run)
                with mock.patch.object(C, 'observe') as replay:
                    with self.assertRaisesRegex(ValueError, 'Wrong or incomplete'):
                        COLLECTOR.collect_reports(ROOT, run, 'IN_PROCESS')
                    replay.assert_not_called()
                fields[missing] = 'pass'
                (run / 'result.properties').write_text(''.join(key + '=' + value + '\n' for key, value in fields.items()))
                seal(run)
                with self.assertRaisesRegex(ValueError, 'Wrong or incomplete'):
                    COLLECTOR.collect_reports(ROOT, run, 'HARDENED_WORKER')

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
            receipt.write_bytes(O.BASE.json_bytes(data)); seal(run)
            with self.assertRaisesRegex(ValueError, 'differs from independent replay'):
                O.Observations(ROOT, run, True).process(command, 'tool/observed')



def qualify_live(run):
    """Replay the actual run, then require resealed public/tool mutations to fail."""
    reports = COLLECTOR.collect_reports(ROOT, run, 'IN_PROCESS')
    count = sum(1 if case.get('apis') == 'native' else 2 for case in json.loads((ROOT / 'capabilities/profiles/T80-embedded-files-only/products.json').read_text())['products'].values())
    if any(len(value['products']) != count for value in reports.values()):
        raise ValueError('Live embedded-files-only collection did not include all original products')
    first = next(iter(json.loads((ROOT / 'capabilities/profiles/T80-embedded-files-only/products.json').read_text())['products']))
    finding = run / ('native-' + first) / 'syntax/qpdf.stdout'
    receipt = finding.with_suffix('.command.json')
    changed_finding = b'changed and resealed syntax finding\n'
    changed_receipt = json.loads(receipt.read_text())
    changed_receipt['stdout-sha256'] = O.digest(changed_finding)
    native = run / ('native-' + first) / 'publication.properties'
    clear = run / ('native-' + first) / 'clear-observation.properties'
    public = run / ('native-' + first) / 'reopened.properties'
    missing = run / ('native-' + first) / 'syntax/result.json'
    declaration = run / 'result.properties'
    mutations = [
        ('resealed-tool-finding', {finding: changed_finding, receipt: O.BASE.json_bytes(changed_receipt)},
         'differs from independent replay'),
        ('native-execution-mode', {native: native.read_bytes().replace(b'execution-profile=IN_PROCESS\n',
             b'execution-profile=HARDENED_WORKER\n')}, 'Actual public embedded-files-only product identity or mode mismatch'),
        ('facade-execution-mode', {declaration: declaration.read_bytes().replace(b'facade-execution-profile=IN_PROCESS\n',
             b'facade-execution-profile=HARDENED_WORKER\n')}, 'Wrong or incomplete embedded-files-only evidence declaration'),
        ('resealed-clear-credential-label', {clear: clear.read_bytes().replace(b'credential=absent\n', b'credential=supplied\n')},
         'The public clear-content or protected-attachment observation changed'),
        ('resealed-public-scope', {public: public.read_bytes().replace(b'scope=EMBEDDED_FILES_ONLY\n', b'scope=ALL_CONTENT\n')},
         'Reopened scope does not match the literal contract'),
        ('missing-original-syntax-chain', {missing: None}, 'Retained observation differs from independent replay')]
    qualified = []
    for name, changes, expected in mutations:
        originals = {path: path.read_bytes() for path in changes}
        if any(originals[path] == changed for path, changed in changes.items()):
            raise ValueError('Live qualification mutation did not change its selected field')
        try:
            for path, changed in changes.items():
                if changed is None: path.unlink()
                else: path.write_bytes(changed)
            seal(run)
            try: COLLECTOR.collect_reports(ROOT, run, 'IN_PROCESS')
            except ValueError as failure:
                if expected not in str(failure): raise
            else: raise ValueError('A resealed live embedded-files-only mutation was accepted')
            qualified.append({'control': name, 'result': 'detected', 'expected-rejection': expected})
        finally:
            for path, original in originals.items(): path.write_bytes(original)
            seal(run)
    print(json.dumps({'result': 'pass', 'products': count, 'replay': 'pass', 'isolated-controls': qualified}, sort_keys=True))
    print('T80 live collector replay and resealed-finding/mode rejection passed')


if __name__ == '__main__':
    if len(sys.argv) == 3 and sys.argv[1] == '--qualify-run': qualify_live(Path(sys.argv[2]).resolve())
    else: unittest.main()
