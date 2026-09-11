"""Qualify the independent semantic CLI with original PDFs and actual PDF mutations."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'scripts/t12-semantics.py'
SPEC = importlib.util.spec_from_file_location('t12_original_author', ROOT / 'scripts/generate-t12-corpus.py')
AUTHOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUTHOR)


class T12SemanticsTest(unittest.TestCase):
    def test_original_products_pass_and_actual_semantic_mutations_fail(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)

            def observe(pdf, product, name, verdict):
                output = directory / name
                before = hashlib.sha256(pdf.read_bytes()).hexdigest()
                result = subprocess.run([sys.executable, str(SCRIPT), str(ROOT), str(pdf), product, str(output)],
                                        capture_output=True, text=True)
                self.assertEqual(0, result.returncode, result.stderr)
                values = dict(line.split('=', 1) for line in (output / 'result.properties').read_text().splitlines()
                              if line and not line.startswith('#'))
                self.assertEqual(verdict, values['semantic'], name + ': ' + values['finding'])
                self.assertEqual(before, values['input-sha256'])
                self.assertEqual(before, hashlib.sha256(pdf.read_bytes()).hexdigest())
                self.assertEqual('12.4.0', values['qpdf-version'])
                self.assertEqual(hashlib.sha256((output / 'qpdf.json').read_bytes()).hexdigest(), values['qpdf-json-sha256'])
                self.assertTrue((output / 'semantic.txt').is_file())
                return output

            for product in AUTHOR.PRODUCTS:
                pdf = ROOT / 'capabilities/profiles/T12-annotations/fixtures' / ('reference-' + product + '.pdf')
                output = observe(pdf, product, 'positive-' + product, 'pass')
                observed = json.loads((output / 'observed.json').read_text())
                self.assertIn('annotations', observed)
                self.assertIn('actions', observed)
                self.assertIn('destinations', observed)
                self.assertIn('page-observations', observed)

            # PDF whitespace after a complete program is semantically inert.
            # This separately authored positive control fixes the normalization policy.
            spaced_pages = AUTHOR.page_definitions()
            for page in spaced_pages.values():
                page['contents'] = [program + '\n\t\r ' for program in page['contents']]
            spaced = directory / 'legal-trailing-whitespace.pdf'
            spaced.write_bytes(AUTHOR.pdf_source(AUTHOR.product_definitions()['created'], spaced_pages))
            observe(spaced, 'created', 'legal-trailing-whitespace', 'pass')

            def mutation(name, product, change):
                definition = copy.deepcopy(AUTHOR.product_definitions()[product])
                pages = AUTHOR.page_definitions()
                change(definition, pages)
                pdf = directory / (name + '.pdf')
                pdf.write_bytes(AUTHOR.pdf_source(definition, pages))
                observe(pdf, product, name, 'fail')

            mutation('order', 'created', lambda d, p: d['annotations'].__setitem__(slice(0, 2), d['annotations'][1::-1]))
            mutation('identifier', 'created', lambda d, p: d['annotations'][1].__setitem__('id', 'note'))
            mutation('rectangle', 'created', lambda d, p: d['annotations'][0]['rectangle'].__setitem__(0, 6))
            mutation('appearance', 'created', lambda d, p: d['annotations'][4]['appearance'].__setitem__('program', 'q 1 0 0 rg 2 3 10 10 re f Q\n'))
            mutation('appearance-box', 'created', lambda d, p: d['annotations'][0]['appearance']['box'].__setitem__(0, 1))
            mutation('payload', 'changed', lambda d, p: d['annotations'][3]['attachment'].__setitem__('hex', '61626301'))
            mutation('icon', 'changed', lambda d, p: d['annotations'][0].__setitem__('icon', 'NOTE'))
            mutation('direct-target', 'created', lambda d, p: d['annotations'][5]['target'].__setitem__('page', 2))
            mutation('action-operand', 'created', lambda d, p: d['annotations'][7]['target']['parameters'].__setitem__(0, 10))
            mutation('named-target', 'created', lambda d, p: d['destinations']['shared'].__setitem__('page', 2))
            mutation('copy-target', 'copied', lambda d, p: d['annotations'][14]['target'].__setitem__('page', 1))
            mutation('copy-external', 'copied', lambda d, p: d['destinations']['shared'].__setitem__('page', 4))
            mutation('merge-name', 'merged', lambda d, p: d['annotations'][10]['target'].__setitem__('named', 'shared'))
            mutation('split-survival', 'left', lambda d, p: d['destinations'].__setitem__('shared', AUTHOR.destination(1)))
            mutation('open-adoption', 'adopted', lambda d, p: d['actions']['document-open'].__setitem__('named', 'shared'))
            mutation('flatten-removal', 'flattened', lambda d, p: d['annotations'].append(copy.deepcopy(d['flattened'][0])))
            mutation('flatten-placement', 'flattened', lambda d, p: d['flattened'][0]['rectangle'].__setitem__(0, 6))
            mutation('retained-paint', 'flattened', lambda d, p: p['A']['contents'].__setitem__(0, 'q 1 0 0 rg 11 20 40 30 re f Q\n'))


if __name__ == '__main__':
    unittest.main()
