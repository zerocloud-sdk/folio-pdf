"""Original extraction corpus authoring through its public command."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from test_t10_corpus import rgb

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'scripts/generate-t13-corpus.py'


class T13CorpusTest(unittest.TestCase):
    def test_simple_and_cid_truetype_use_separate_programs_with_unchanged_public_expectations(self):
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / 'corpus'
            result = subprocess.run([sys.executable, str(SCRIPT), str(target)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            corpus = json.loads((target / 'corpus.json').read_text())
            simple = (ROOT / 'capabilities/profiles/T13-fonts/FolioT13Rectangle.ttf').read_bytes()
            cid = (ROOT / 'capabilities/profiles/T13-fonts/FolioT13RectangleCID.ttf').read_bytes()
            for name in ('embedded-font-kinds', 'embedded-font-inheritance'):
                with self.subTest(source=name):
                    data = (target / corpus['sources'][name]['path']).read_bytes()
                    descriptor = data.split(b'\n15 0 obj\n')[1].split(b'\nendobj\n')[0]
                    self.assertIn(b'/FontFile2 25 0 R', descriptor)
                    self.assertEqual(1, data.count(simple.hex().encode('ascii')))
                    self.assertEqual(1, data.count(cid.hex().encode('ascii')))
                    self.assertEqual('AAAZAZA', corpus['products'][name]['extraction']['pages'][0]['text'])

    def test_uncertain_mapping_and_rotated_page_geometry_are_literal_and_keep_all_diagnostics(self):
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / 'corpus'
            result = subprocess.run([sys.executable, str(SCRIPT), str(target)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            corpus = json.loads((target / 'corpus.json').read_text())
            product = corpus['products']['uncertain-geometry']
            page = product['extraction']['pages'][0]
            self.assertEqual((90, 2, [10, 20, 130, 120]), (page['rotation'], page['user-unit'], page['crop-box']))
            self.assertEqual('A AA', page['text'])
            items = page['items']
            self.assertEqual(['INFERRED'] * 3 + ['EXPLICIT', 'CONTRADICTORY', 'MISSING'],
                             [item['confidence'] for item in items])
            self.assertEqual(['41', '20', '41', '41', '41', '42'], [item['source'] for item in items])
            self.assertEqual([10, 0, 0, 30, 25, 73], items[0]['matrix'])
            self.assertEqual([31, 39.5], [items[i]['matrix'][4] for i in (1, 2)])
            self.assertEqual([[5, 0], [2.5, 0], [5, 0]], [item['advance'] for item in items[:3]])
            self.assertIsNone(items[4]['unicode'])
            self.assertFalse(items[4]['unicode-present'])
            self.assertTrue(items[4]['explicit-present'])
            self.assertEqual(('Z', 'A'), (items[4]['explicit'], items[4]['inferred']))
            self.assertIsNone(items[5]['explicit'])
            self.assertEqual(['CONTRADICTORY_UNICODE_MAPPING', 'MISSING_UNICODE_MAPPING'],
                             [value['code'] for value in product['extraction']['diagnostics']])
            self.assertEqual([5, 6], [value['item'] for value in product['extraction']['diagnostics']])
            data = (target / corpus['sources']['uncertain-geometry']['path']).read_bytes()
            self.assertIn(b'/Rotate 90 /UserUnit 2', data)
            self.assertIn(b'50 Tz 2 Ts 2 Tc 4 Tw', data)
            self.assertIn(b'[(A) 100 ( A)] TJ', data)
            self.assertEqual(['syntax', 'standards', 'semantic'], product['required-chains'])

    def test_direct_font_visual_case_preserves_inheritance_as_a_separate_required_semantic_case(self):
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / 'corpus'
            result = subprocess.run([sys.executable, str(SCRIPT), str(target)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            corpus = json.loads((target / 'corpus.json').read_text())
            direct = corpus['products']['embedded-font-kinds']
            inherited = corpus['products']['embedded-font-inheritance']
            direct_pdf = (target / corpus['sources']['embedded-font-kinds']['path']).read_bytes()
            inherited_pdf = (target / corpus['sources']['embedded-font-inheritance']['path']).read_bytes()
            self.assertNotIn(b'/UseCMap', direct_pdf)
            self.assertIn(b'/UseCMap 16 0 R', inherited_pdf)
            self.assertIn(b'/UseCMap 19 0 R', inherited_pdf)
            self.assertEqual(direct['extraction'], inherited['extraction'])
            self.assertEqual(['syntax', 'standards', 'semantic', 'visual'], direct['required-chains'])
            self.assertEqual(['syntax', 'standards', 'semantic'], inherited['required-chains'])
            visual = direct['visual'][0]
            self.assertEqual('e016ccb3e411f70ee07b9e583ab9791d9664bbb1f0c8c5efa7874cb89cff2ada',
                             visual['reference-pdf-sha256'])
            self.assertEqual(1120, visual['renderer-agreement-threshold'])
            self.assertEqual((ROOT / 'capabilities/profiles/T13-fonts/embedded-font-kinds-reference.png').read_bytes(),
                             (target / visual['raster']).read_bytes())

    def test_embedded_font_kinds_have_literal_horizontal_vertical_and_inherited_mapping_expectations(self):
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / 'corpus'
            result = subprocess.run([sys.executable, str(SCRIPT), str(target)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            corpus = json.loads((target / 'corpus.json').read_text())
            product = corpus['products']['embedded-font-inheritance']
            data = (target / corpus['sources']['embedded-font-inheritance']['path']).read_bytes()
            for subtype in ('Type1', 'MMType1', 'TrueType', 'Type0', 'CIDFontType0', 'CIDFontType2'):
                self.assertIn(('/Subtype /' + subtype + ' ').encode(), data)
            self.assertIn(b'/Subtype /Type1C ', data)
            self.assertIn(b'/Subtype /CIDFontType0C ', data)
            self.assertIn(b'/FontName /FolioT13RectangleCFF /Flags 33', data)
            self.assertIn(b'/FontName /FolioT13RectangleCID /Flags 5', data)
            self.assertEqual(4, data.count(b'/StemV 400'))
            self.assertIn(b'/UseCMap 16 0 R', data)
            self.assertIn(b'/UseCMap 19 0 R', data)
            page = product['extraction']['pages'][0]
            self.assertEqual('AAAZAZA', page['text'])
            items = page['items']
            self.assertEqual(['41'] * 3 + ['0041'] * 4, [item['source'] for item in items])
            self.assertEqual(['INFERRED'] * 3 + ['EXPLICIT'] * 4, [item['confidence'] for item in items])
            self.assertEqual([20, 0, 0, 20, 35, 64], items[4]['matrix'])
            self.assertEqual([20, 0, 0, 20, 85, 64], items[6]['matrix'])
            self.assertEqual([[10, 0]] * 4 + [[0, -20], [10, 0], [0, -20]], [item['advance'] for item in items])
            self.assertEqual(['A', 'A', 'A', 'Z', 'A', 'Z', 'A'], [item['unicode'] for item in items])
            self.assertEqual([], product['extraction']['diagnostics'])
            width, height, pixel = rgb(target / product['visual'][0]['raster'])
            self.assertEqual((240, 200), (width, height))
            self.assertEqual((0, 0, 0), pixel(70, 48))
            self.assertEqual((0, 0, 0), pixel(85, 71))
            self.assertEqual((255, 255, 255), pixel(86, 71))
            self.assertEqual((0, 0, 0), pixel(170, 48))

    def test_marked_structure_source_freezes_replacement_order_and_stream_scoped_links(self):
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / 'corpus'
            result = subprocess.run([sys.executable, str(SCRIPT), str(target)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            corpus = json.loads((target / 'corpus.json').read_text())
            product = corpus['products']['marked-structure']
            data = (target / corpus['sources']['marked-structure']['path']).read_bytes()
            self.assertIn(b'/StructParents 0', data)
            self.assertIn(b'/StructParents 1', data)
            self.assertIn(b'/Stm 7 0 R /MCID 0', data)
            self.assertIn(b'/Obj 12 0 R', data)
            self.assertIn(b'/FontDescriptor 17 0 R', data)
            self.assertIn(b'/Subtype /Type3 /Name /F1', data)
            self.assertIn(b'/Type /FontDescriptor /FontName /F1 /Flags 33', data)
            self.assertIn(b'/StemV 400', data)
            self.assertIn(b'/S /Span /NS 15 0 R /P 10 0 R', data)
            self.assertNotIn(b'/K []', data)
            page = product['extraction']['pages'][0]
            self.assertEqual('OuterForm', page['text'])
            self.assertEqual(['', '', ''], [item['contribution'] for item in page['items']])
            self.assertEqual([0, 0, 1, 1], [item['stream'] for item in page['marked-content']])
            self.assertEqual(['Outer', 'Inner', None, 'Form'], [item['actual'] for item in page['marked-content']])
            root = product['extraction']['roots'][0]
            self.assertEqual('Document', root['resolved-role'])
            self.assertEqual('urn:folio:t13:roles', root['namespace'])
            self.assertEqual(1, len(root['children']))
            paragraph = root['children'][0]['element']
            self.assertEqual('http://iso.org/pdf2/ssn', paragraph['namespace'])
            children = paragraph['children']
            self.assertEqual(['MARKED_CONTENT', 'MARKED_CONTENT', 'OBJECT', 'ELEMENT'], [item['kind'] for item in children])
            self.assertEqual([0, 1], [item['marked-content']['stream'] for item in children[:2]])
            self.assertEqual('ANCESTOR', root['children'][0]['element']['language-source'])
            self.assertEqual('fr', root['children'][0]['element']['effective-language'])
            self.assertEqual('ja', children[3]['element']['effective-language'])
            self.assertEqual(2, paragraph['namespace-reference'])
            self.assertEqual(2, children[3]['element']['namespace-reference'])
            self.assertEqual(3, children[2]['object-reference']['reference'])
            self.assertEqual([], product['extraction']['diagnostics'])
            width, height, pixel = rgb(target / product['visual'][0]['raster'])
            self.assertEqual((240, 200), (width, height))
            self.assertEqual((0, 0, 0), pixel(20, 179))
            self.assertEqual((0, 0, 0), pixel(40, 139))

    def test_nested_split_type3_source_has_literal_geometry_and_original_pixels(self):
        with tempfile.TemporaryDirectory() as temporary:
            outputs = [Path(temporary) / name for name in ('first', 'second')]
            for target in outputs:
                result = subprocess.run([sys.executable, str(SCRIPT), str(target)], capture_output=True, text=True)
                self.assertEqual(0, result.returncode, result.stderr)
            original = {p.relative_to(outputs[0]): p.read_bytes()
                        for p in outputs[0].rglob('*') if p.is_file()}
            self.assertEqual(original, {p.relative_to(outputs[1]): p.read_bytes()
                                       for p in outputs[1].rglob('*') if p.is_file()})
            corpus = json.loads((outputs[0] / 'corpus.json').read_text())
            self.assertEqual('T13-text-logical-structure', corpus['profile'])
            source = corpus['sources']['nested-split-type3']
            data = (outputs[0] / source['path']).read_bytes()
            self.assertEqual(source['sha256'], hashlib.sha256(data).hexdigest())
            xref = int(data.rsplit(b'startxref\n', 1)[1].splitlines()[0])
            self.assertEqual(b'xref', data[xref:xref + 4])
            self.assertIn(b'/Contents [4 0 R 5 0 R]', data)
            self.assertIn(b'[<4', data)
            self.assertIn(b'1>] TJ', data)
            self.assertIn(b'/Subtype /Type3', data)
            product = corpus['products']['nested-split-type3']
            page = product['extraction']['pages'][0]
            self.assertEqual('AA', page['text'])
            self.assertEqual([20, 0, 0, 20, 10, 20], page['items'][0]['matrix'])
            self.assertEqual([20, 0, 0, 10, 25, 40], page['items'][1]['matrix'])
            self.assertEqual([[10, 0], [10, 0]], [item['advance'] for item in page['items']])
            self.assertEqual(['41', '41'], [item['source'] for item in page['items']])
            self.assertEqual(['INFERRED', 'INFERRED'], [item['confidence'] for item in page['items']])
            self.assertEqual([], product['extraction']['roots'])
            self.assertEqual([], product['extraction']['diagnostics'])
            visual = product['visual'][0]
            raster = outputs[0] / visual['raster']
            width, height, pixel = rgb(raster)
            self.assertEqual((240, 200), (width, height))
            self.assertEqual((255, 0, 0), pixel(20, 136))
            self.assertEqual((255, 0, 0), pixel(35, 159))
            self.assertEqual((255, 255, 255), pixel(36, 159))
            self.assertEqual((0, 0, 255), pixel(50, 108))
            self.assertEqual((0, 0, 255), pixel(65, 119))
            self.assertEqual((255, 255, 255), pixel(66, 119))
            self.assertEqual(visual['raster-sha256'], hashlib.sha256(raster.read_bytes()).hexdigest())
            profile = (outputs[0] / 'visual/nested-split-type3-page-1.properties').read_text()
            self.assertIn('COMPARISON_THRESHOLD=0\n', profile)
            self.assertIn('COMPARISON_FUZZ_PERCENT=0\n', profile)
            refused = subprocess.run([sys.executable, str(SCRIPT), str(outputs[0])], capture_output=True, text=True)
            self.assertNotEqual(0, refused.returncode)
            self.assertEqual(original, {p.relative_to(outputs[0]): p.read_bytes()
                                       for p in outputs[0].rglob('*') if p.is_file()})


if __name__ == '__main__':
    unittest.main()
