"""Qualify the independent decoded-graph CLI on actual original PDFs."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import shutil
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'scripts/t13-semantics.py'


class T13SemanticsTest(unittest.TestCase):
    def test_cli_exports_the_exact_original_report_receipt(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(os.environ.get('FOLIO_T13_SEMANTIC_RECEIPT_OUTPUT', str(Path(temporary) / 'semantic')))
            source = ROOT / 'capabilities/profiles/T13-text/fixtures/marked-structure.pdf'
            before = source.read_bytes()
            completed = subprocess.run([sys.executable, '-I', str(SCRIPT), str(ROOT), str(source),
                                        'marked-structure', str(output)], capture_output=True, text=True, timeout=45)
            self.assertEqual(0, completed.returncode, completed.stderr)
            receipt = {}
            for line in completed.stdout.splitlines():
                digest, name = line.split('  ', 1)
                self.assertNotIn(name, receipt)
                receipt[name] = digest
            self.assertEqual({path.name for path in output.iterdir() if path.is_file()}, set(receipt))
            self.assertIn('result.properties', receipt)
            for name, digest in receipt.items():
                self.assertEqual(hashlib.sha256((output / name).read_bytes()).hexdigest(), digest)
            self.assertEqual(before, source.read_bytes())

    def test_modified_tool_wrapper_cannot_turn_a_changed_pdf_into_a_passing_graph(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            staged = directory / 'repository'
            shutil.copytree(ROOT / 'capabilities/profiles/T13-text', staged / 'capabilities/profiles/T13-text')
            (staged / 'scripts/container-bin').mkdir(parents=True)
            shutil.copyfile(ROOT / 'scripts/qpdf-pin.properties', staged / 'scripts/qpdf-pin.properties')
            (staged / '.build-cache').mkdir()
            (staged / '.build-cache/qpdf').symlink_to(ROOT / '.build-cache/qpdf', target_is_directory=True)
            original = ROOT / 'capabilities/profiles/T13-text/fixtures/marked-structure.pdf'
            wrapper = staged / 'scripts/container-bin/qpdf'
            wrapper.write_text('#!/usr/bin/python3\nimport os, sys\nargs = sys.argv[1:]\n'
                               'if args and args[-1].endswith(".pdf"):\n    args[-1] = ' + repr(str(original)) + '\n'
                               'executable = ' + repr(str(ROOT / 'scripts/container-bin/qpdf')) + '\n'
                               'os.execv(executable, [executable] + args)\n')
            wrapper.chmod(0o700)
            changed = directory / 'changed.pdf'
            changed.write_bytes(original.read_bytes().replace(b'/ActualText (Outer)', b'/ActualText (Other)'))
            output = directory / 'record'
            completed = subprocess.run([sys.executable, str(SCRIPT), str(staged), str(changed),
                                        'marked-structure', str(output)], capture_output=True, text=True, timeout=45)
            self.assertEqual(0, completed.returncode, completed.stderr)
            values = dict(line.split('=', 1) for line in (output / 'result.properties').read_text().splitlines())
            self.assertEqual('indeterminate', values['semantic'])
            self.assertIn('wrapper identity', values['finding'])

    def test_original_graphs_and_reserialization_pass_but_changed_stream_and_relationship_fail(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)

            def observe(pdf, product, name, verdict):
                output = directory / name
                before = hashlib.sha256(pdf.read_bytes()).hexdigest()
                result = subprocess.run([sys.executable, str(SCRIPT), str(ROOT), str(pdf), product, str(output)],
                                        capture_output=True, text=True, timeout=45)
                self.assertEqual(0, result.returncode, result.stderr)
                values = dict(line.split('=', 1) for line in (output / 'result.properties').read_text().splitlines())
                self.assertEqual(verdict, values['semantic'], values['finding'])
                self.assertEqual(before, values['input-sha256'])
                self.assertEqual(before, hashlib.sha256(pdf.read_bytes()).hexdigest())
                self.assertEqual('12.4.0', values['qpdf-version'])
                self.assertEqual(hashlib.sha256((output / 'qpdf.json').read_bytes()).hexdigest(), values['qpdf-json-sha256'])
                self.assertTrue((output / 'reference-qpdf.json').is_file())
                self.assertTrue((output / 'observed.json').is_file())
                self.assertTrue((output / 'reference.json').is_file())
                return output

            for name in ('nested-split-type3', 'marked-structure', 'embedded-font-kinds', 'embedded-font-inheritance',
                         'uncertain-geometry'):
                original = ROOT / 'capabilities/profiles/T13-text/fixtures' / (name + '.pdf')
                output = observe(original, name, 'original-' + name, 'pass')
                graph = json.loads((output / 'observed.json').read_text())
                self.assertGreater(len(graph['objects']), 5)
                rewritten = directory / (name + '-reserialized.pdf')
                completed = subprocess.run([str(ROOT / 'scripts/container-bin/qpdf'), '--object-streams=generate',
                                            '--stream-data=compress', str(original), str(rewritten)],
                                           capture_output=True, text=True, timeout=30)
                self.assertEqual(0, completed.returncode, completed.stderr)
                self.assertNotEqual(original.read_bytes(), rewritten.read_bytes())
                observe(rewritten, name, 'rewritten-' + name, 'pass')

            original = ROOT / 'capabilities/profiles/T13-text/fixtures/marked-structure.pdf'
            for name, before, after in (
                    ('replacement', b'/ActualText (Outer)', b'/ActualText (Other)'),
                    ('role-target', b'/Intermediate [/Document 15 0 R]', b'/Intermediate [/Document 14 0 R]')):
                data = original.read_bytes()
                self.assertEqual(len(before), len(after))
                self.assertEqual(1, data.count(before))
                changed = directory / (name + '.pdf')
                changed.write_bytes(data.replace(before, after))
                observe(changed, 'marked-structure', name, 'fail')


if __name__ == '__main__':
    unittest.main()
