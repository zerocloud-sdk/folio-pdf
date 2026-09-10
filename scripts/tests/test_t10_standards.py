"""The public T10 standards corpus generator emits original, isolated defects."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'scripts/generate-t10-standards.py'


class T10StandardsCorpusTest(unittest.TestCase):
    def test_name_tree_byte_boundaries_are_fixed_before_checker_observation(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'controls'
            generated = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, generated.returncode, generated.stderr)
            positive = output / 'fixtures/positive-name-byte-order.pdf'
            self.assertTrue(positive.is_file(), 'Retain an unsigned-byte name ordering positive')
            self.assertIn(b'/Names [(a) [3 0 R /Fit] (aa) [3 0 R /Fit] <7f> [3 0 R /Fit] <80> [3 0 R /Fit] <ff> [3 0 R /Fit]]',
                          positive.read_bytes())
            self.assertIn(b'/Names [<80> [3 0 R /Fit] <7f> [3 0 R /Fit]]',
                          (output / 'fixtures/nametree-key-order-high-bytes.pdf').read_bytes())
            self.assertIn(b'/Names [(shared) [3 0 R /Fit] <736861726564> [3 0 R /Fit]]',
                          (output / 'fixtures/nametree-key-unique-encoding.pdf').read_bytes())

    def test_all_page_preservation_structure_families_have_explicit_negative_controls(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'controls'
            generated = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, generated.returncode, generated.stderr)
            catalog = json.loads((output / 'controls.json').read_text())
            for rule, defect in {'page-cropbox-rectangle': b'/CropBox [0 0 10]',
                                 'page-bleedbox-numbers': b'/BleedBox [0 0 /Bad 10]',
                                 'page-trimbox-numbers': b'/TrimBox [0 0 /Bad 10]',
                                 'page-artbox-numbers': b'/ArtBox [0 0 /Bad 10]',
                                 'page-contents-nonempty': b'/Contents []',
                                 'resource-xobject-map': b'/XObject 42',
                                 'form-matrix-count': b'/Matrix [1 0 0 1 0]',
                                 'annotation-flags-integer': b'/F 2.5',
                                 'nametree-key-order': b'/Names [(z) [3 0 R /Fit] (a) [3 0 R /Fit]]',
                                 'destination-page-indirect': b'/Names [(shared) [0 /Fit]]'}.items():
                self.assertIn(rule, catalog)
                self.assertIn(defect, (output / catalog[rule]['path']).read_bytes())

    def test_generator_preserves_valid_serialization_around_named_illegal_values(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'controls'
            generated = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, generated.returncode, generated.stderr)
            catalog = json.loads((output / 'controls.json').read_text())
            examples = {'page-rotate-value': b'/Rotate 45',
                        'form-bbox-required': b'/Subtype /Form',
                        'annotation-rect-numbers': b'/Rect [30 50 /Bad 65]',
                        'destination-fit-count': b'/Names [(shared) [3 0 R /Fit 0]]'}
            for rule, illegal in examples.items():
                entry = catalog[rule]
                pdf = (output / entry['path']).read_bytes()
                self.assertIn(illegal, pdf)
                self.assertEqual(entry['sha256'], hashlib.sha256(pdf).hexdigest())
                start = int(pdf.rsplit(b'startxref\n', 1)[1].splitlines()[0])
                self.assertEqual(b'xref', pdf[start:start + 4])
                self.assertIn('ISO 32000-1:2008', entry['authority'])
            self.assertNotIn(b'/BBox', (output / catalog['form-bbox-required']['path']).read_bytes())
            before = {p.relative_to(output): p.read_bytes() for p in output.rglob('*') if p.is_file()}
            refused = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertNotEqual(0, refused.returncode)
            self.assertEqual(before, {p.relative_to(output): p.read_bytes() for p in output.rglob('*') if p.is_file()})


if __name__ == '__main__':
    unittest.main()
