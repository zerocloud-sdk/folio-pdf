"""Public original-authoring seam for the frozen annotation standards controls."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'scripts/generate-t12-standards.py'


class T12StandardsTest(unittest.TestCase):
    def test_profiles_assign_every_required_rule_once_with_a_frozen_specific_diagnostic(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'profiles'
            result = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            rules = set((output / 'required-rules.txt').read_text().splitlines())
            assignments = json.loads((output / 'assignments.json').read_text())
            self.assertEqual(174, len(rules))
            union = set()
            for group in ('pdfcpu', 'arlington-core', 'arlington-annotations'):
                profile = output / (group + '.properties')
                self.assertTrue(profile.is_file(), 'A fixed checker profile is required for ' + group)
                values = dict(line.split('=', 1) for line in profile.read_text().splitlines() if line and not line.startswith('#'))
                self.assertEqual('T12-annotations-document-actions', values['profile'])
                self.assertEqual('2.0', values['pdf-version'])
                selected = set(values['required-rules'].split(','))
                self.assertEqual(values['required-rules'], values['covered-rules'])
                self.assertFalse(union.intersection(selected))
                union.update(selected)
                for rule in selected:
                    finding = values['negative.' + rule + '.finding']
                    self.assertGreater(len(finding), 12, 'A generic Error marker cannot qualify ' + rule)
                    self.assertEqual(assignments[rule]['sha256'], values['negative.' + rule + '.sha256'])
                    self.assertEqual(assignments[rule]['finding'], finding)
            self.assertEqual(rules, union)

    def test_original_illegal_controls_and_inherited_rules_are_reproducible(self):
        with tempfile.TemporaryDirectory() as temporary:
            outputs = [Path(temporary) / name for name in ('first', 'second')]
            for output in outputs:
                result = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
                self.assertEqual(0, result.returncode, result.stderr)
            files = {p.relative_to(outputs[0]): p.read_bytes() for p in outputs[0].rglob('*') if p.is_file()}
            self.assertEqual(files, {p.relative_to(outputs[1]): p.read_bytes() for p in outputs[1].rglob('*') if p.is_file()})
            controls = json.loads((outputs[0] / 'controls.json').read_text())
            assignments = json.loads((outputs[0] / 'assignments.json').read_text())
            rules = (outputs[0] / 'required-rules.txt').read_text().splitlines()
            for positive in ('same-name-other-page', 'optional-bindings', 'null-bindings'):
                self.assertTrue((outputs[0] / ('fixtures/positive-' + positive + '.pdf')).is_file(),
                                'The graph supplement must accept legal boundary: ' + positive)
            self.assertEqual(sorted(set(rules)), rules)
            self.assertEqual(set(rules), set(assignments))
            for inherited in ['page-annots-array', 'page-annots-member', 'annotation-rect-numbers', 'form-matrix-count', 'form-resources-dictionary',
                              'destination-xyz-operand', 'filespec-relationship-value', 'embedded-checksum-value']:
                self.assertIn(inherited, assignments)
            expected = {
                'annotation-ap-dictionary': b'/AP 42',
                'appearance-normal-required': b'/AP << >>',
                'appearance-normal-type': b'/N 42',
                'annotation-flags-reserved': b'/F 2048',
                'annotation-page-owner': b'/P 5 0 R',
                'annotation-id-unique': b'/NM <6e6f7465>',
                'text-name-name': b'/Name 42',
                'text-open-boolean': b'/Open 42',
                'stamp-name-name': b'/Name 42',
                'highlight-quads-required': b'/Subtype /Highlight',
                'highlight-quads-count': b'/QuadPoints [1 2 3 4 5 6 7]',
                'highlight-color-range': b'/C [1 2 0]',
                'file-annotation-fs-required': b'/Subtype /FileAttachment',
                'file-annotation-fs-type': b'/FS 42',
                'file-icon-name': b'/Name 42',
                'link-destination-type': b'/Dest 42',
                'link-action-type': b'/A 42',
                'link-activation-exclusive': b'/Dest [4 0 R /Fit] /A ',
                'catalog-openaction-type': b'/OpenAction 42',
                'page-aa-dictionary': b'/AA 42',
                'page-open-action-type': b'/O 42',
                'page-close-action-type': b'/C 42',
                'goto-s-required': b'/OpenAction << /D ',
                'goto-s-name': b'/S 42',
                'goto-d-required': b'/OpenAction << /S /GoTo >>',
                'goto-d-type': b'/D 42',
                'goto-named-target': b'/D (absent)'
            }
            for family in ('text', 'stamp', 'highlight', 'file', 'widget'):
                self.assertIn(family + '-appearance-required', controls)
            for rule, defect in expected.items():
                self.assertIn(rule, controls)
                self.assertIn(defect, (outputs[0] / controls[rule]['path']).read_bytes())
            for rule, control in controls.items():
                data = (outputs[0] / control['path']).read_bytes()
                self.assertEqual(control['sha256'], hashlib.sha256(data).hexdigest())
                self.assertEqual(b'xref', data[int(data.rsplit(b'startxref\n', 1)[1].splitlines()[0]):][:4])
                self.assertIn('ISO 32000', control['authority'])
                self.assertEqual('arlington', control['checker'])
            repeat = subprocess.run([sys.executable, str(SCRIPT), str(outputs[0])], capture_output=True, text=True)
            self.assertNotEqual(0, repeat.returncode)
            self.assertEqual(files, {p.relative_to(outputs[0]): p.read_bytes() for p in outputs[0].rglob('*') if p.is_file()})


if __name__ == '__main__':
    unittest.main()
