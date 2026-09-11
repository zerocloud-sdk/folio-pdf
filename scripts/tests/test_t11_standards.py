"""Public authoring seam for the frozen metadata standards controls."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'scripts/generate-t11-standards.py'


class T11StandardsCorpusTest(unittest.TestCase):
    def test_rule_catalog_retains_core_and_navigation_assignments(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'rules'
            result = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            path = output / 'required-rules.txt'
            self.assertTrue(path.is_file(), 'The full required rule union must be frozen')
            rules = path.read_text().splitlines()
            self.assertEqual(sorted(set(rules)), rules)
            for rule in ['catalog-pages', 'pages-count-value', 'page-contents-array-member',
                         'nametree-key-order-high-bytes', 'nametree-key-unique-encoding',
                         'destination-fit-count', 'outline-named-target', 'embedded-checksum-value']:
                self.assertIn(rule, rules)
            assignments = json.loads((output / 'assignments.json').read_text())
            self.assertEqual(set(rules), set(assignments))
            self.assertEqual('pdfcpu', assignments['pages-count-value']['checker'])
            self.assertEqual('arlington', assignments['nametree-key-order-high-bytes']['checker'])
            self.assertEqual('arlington', assignments['embedded-checksum-value']['checker'])
            self.assertEqual('arlington', assignments['info-moddate-date']['checker'])
            self.assertNotIn('form-bbox-required', rules)
            combined = set()
            for group, count in [('pdfcpu', 31), ('arlington-core', 35), ('arlington-metadata', 104)]:
                profile = output / (group + '.properties')
                self.assertTrue(profile.is_file(), 'Every bounded checker group must have a frozen profile')
                values = dict(line.split('=', 1) for line in profile.read_text().splitlines() if line and not line.startswith('#'))
                self.assertEqual('2.0', values['pdf-version'])
                selected = values['required-rules'].split(',')
                self.assertEqual(count, len(selected))
                self.assertEqual(values['required-rules'], values['covered-rules'])
                self.assertFalse(combined.intersection(selected))
                combined.update(selected)
                for rule in selected:
                    self.assertTrue(values['negative.' + rule + '.finding'])
                    self.assertEqual(assignments[rule]['sha256'], values['negative.' + rule + '.sha256'])
            self.assertEqual(set(rules), combined)

    def test_original_illegal_controls_cover_metadata_graphs_and_exact_destination_shapes(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'controls'
            result = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            catalog = json.loads((output / 'controls.json').read_text())
            positive = output / 'fixtures/positive-kids.pdf'
            self.assertTrue(positive.is_file(), 'Limits qualification needs a legal multilevel name tree')
            self.assertIn(b'/Kids [22 0 R 23 0 R]', positive.read_bytes())
            self.assertIn(b'/Limits [(b) (r)]', positive.read_bytes())
            self.assertIn(b'/Limits [(shared) (za)]', positive.read_bytes())
            closed = output / 'fixtures/positive-closed-outline.pdf'
            self.assertTrue(closed.is_file(), 'Outline counts need a legal closed-parent control')
            self.assertIn(b'/Count -2', closed.read_bytes())
            self.assertIn(b'/Type /Outlines /First 14 0 R /Last 15 0 R /Count 2', closed.read_bytes())
            filtered = output / 'fixtures/positive-filtered-attachment.pdf'
            self.assertTrue(filtered.is_file(), 'Hash qualification must accept checksums over decoded data')
            self.assertIn(b'/Filter /FlateDecode', filtered.read_bytes())
            self.assertIn(b'/Size 3 /CheckSum <900150983cd24fb0d6963f7d28e17f72>', filtered.read_bytes())
            required = {'info-title-string': b'/Title 42', 'info-custom-string': b'/T73Keep [1 2]',
                        'metadata-type-required': b'/Subtype /XML', 'metadata-xml-wellformed': b'<broken>',
                        'outline-title-required': b'/Parent 6 0 R', 'outline-parent-link': b'/Parent 2 0 R',
                        'outline-count-value': b'/Count 99', 'outline-next-cycle': b'/Next 14 0 R',
                        'nametree-kids-cycle': b'/Kids [5 0 R]', 'nametree-child-limits-value': b'/Limits [(a) (z)]',
                        'filespec-description-string': b'/Desc 42', 'filespec-ef-dictionary': b'/EF 42',
                        'embedded-size-value': b'/Size 4', 'embedded-checksum-value': b'/CheckSum <00000000000000000000000000000000>'}
            for rule, defect in required.items():
                self.assertIn(rule, catalog)
                data = (output / catalog[rule]['path']).read_bytes()
                self.assertIn(defect, data)
            for view in ['xyz', 'fitb', 'fith', 'fitv', 'fitr', 'fitbh', 'fitbv']:
                self.assertIn('destination-' + view + '-count', catalog)
            for view in ['xyz', 'fith', 'fitv', 'fitr', 'fitbh', 'fitbv']:
                self.assertIn('destination-' + view + '-operand', catalog)
            for rule, entry in catalog.items():
                data = (output / entry['path']).read_bytes()
                self.assertEqual(entry['sha256'], hashlib.sha256(data).hexdigest())
                start = int(data.rsplit(b'startxref\n', 1)[1].splitlines()[0])
                self.assertEqual(b'xref', data[start:start + 4])
                self.assertTrue(entry['authority'].startswith('ISO 32000'))
                self.assertIn(entry['checker'], ['pdfcpu', 'arlington'])
            self.assertNotIn(b'/Type /Metadata', (output / catalog['metadata-type-required']['path']).read_bytes())
            before = {p.relative_to(output): p.read_bytes() for p in output.rglob('*') if p.is_file()}
            repeat = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertNotEqual(0, repeat.returncode)
            self.assertEqual(before, {p.relative_to(output): p.read_bytes() for p in output.rglob('*') if p.is_file()})


if __name__ == '__main__':
    unittest.main()
