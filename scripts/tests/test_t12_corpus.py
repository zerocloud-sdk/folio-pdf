"""Exercise original T12 corpus authoring through its public command."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from test_t10_corpus import rgb

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'scripts/generate-t12-corpus.py'


class T12CorpusTest(unittest.TestCase):
    def test_sources_expectations_and_exact_pixels_are_original_and_reproducible(self):
        with tempfile.TemporaryDirectory() as temporary:
            first, second = Path(temporary) / 'first', Path(temporary) / 'second'
            for target in (first, second):
                result = subprocess.run([sys.executable, str(SCRIPT), str(target)], capture_output=True, text=True)
                self.assertEqual(0, result.returncode, result.stderr)
            original = {p.relative_to(first): p.read_bytes() for p in first.rglob('*') if p.is_file()}
            self.assertEqual(original, {p.relative_to(second): p.read_bytes() for p in second.rglob('*') if p.is_file()})
            corpus = json.loads((first / 'corpus.json').read_text())
            self.assertEqual('T12-annotations-document-actions', corpus['profile'])
            for source in corpus['sources'].values():
                data = (first / source['path']).read_bytes()
                self.assertTrue(data.startswith(b'%PDF-2.0\n'))
                self.assertEqual(source['sha256'], hashlib.sha256(data).hexdigest())
                xref = int(data.rsplit(b'startxref\n', 1)[1].splitlines()[0])
                self.assertEqual(b'xref', data[xref:xref + 4])
                self.assertNotIn(b'/AcroForm', data)
            created, changed = (corpus['products'][name] for name in ('created', 'changed'))
            self.assertEqual(['A', 'B', 'C'], created['pages'])
            self.assertEqual(['note', 'stamp', 'highlight', 'file', 'widget', 'direct', 'named', 'action', 'action-named'],
                             [a['id'] for a in created['annotations']])
            self.assertEqual(['TEXT', 'STAMP', 'HIGHLIGHT', 'FILE_ATTACHMENT', 'WIDGET', 'LINK'],
                             list(dict.fromkeys(a['type'] for a in created['annotations'])))
            self.assertEqual([5, 65, 20, 80], created['annotations'][0]['rectangle'])
            self.assertEqual([5, 45, 20, 60], changed['annotations'][0]['rectangle'])
            self.assertEqual('HELP', changed['annotations'][0]['icon'])
            self.assertEqual('Draft', changed['annotations'][1]['stamp'])
            self.assertEqual('61626300', changed['annotations'][3]['attachment']['hex'])
            self.assertEqual([2, 3, 12, 13], changed['annotations'][0]['appearance']['box'])
            self.assertEqual(['widget'], [a['id'] for a in corpus['products']['flattened']['annotations']])
            self.assertEqual(8, len(corpus['products']['flattened']['flattened']))
            copied = corpus['products']['copied']
            self.assertEqual(['A', 'B', 'C', 'A', 'B'], copied['pages'])
            self.assertEqual('note-1', copied['annotations'][9]['id'])
            copied_direct = next(a for a in copied['annotations'] if a['id'] == 'direct-1')
            self.assertEqual(5, copied_direct['page'])
            self.assertEqual(4, copied_direct['target']['page'])
            self.assertEqual(3, copied['destinations']['shared']['page'])
            merged = corpus['products']['merged']
            self.assertEqual(['A', 'B', 'C', 'D'], merged['pages'])
            self.assertEqual(4, merged['destinations']['shared-1']['page'])
            self.assertEqual('note-1', merged['annotations'][9]['id'])
            self.assertEqual('shared-1', merged['annotations'][10]['target']['named'])
            self.assertEqual('shared', merged['actions']['document-open']['named'])
            self.assertEqual('shared-1', corpus['products']['adopted']['actions']['document-open']['named'])
            left, right = (corpus['products'][name] for name in ('left', 'right'))
            self.assertEqual(['A', 'B'], left['pages'])
            self.assertEqual(['C', 'D'], right['pages'])
            self.assertEqual(['note', 'stamp', 'highlight', 'file', 'widget', 'direct', 'action'],
                             [a['id'] for a in left['annotations']])
            self.assertIsNone(left['actions']['document-open'])
            self.assertEqual(['note-1', 'appendix-link'], [a['id'] for a in right['annotations']])
            self.assertEqual(2, right['annotations'][1]['page'])
            self.assertEqual(2, right['destinations']['shared-1']['page'])
            self.assertEqual(1, right['destinations']['shared']['page'])
            raster = first / created['visual'][0]['raster']
            width, height, pixel = rgb(raster)
            self.assertEqual((240, 200), (width, height))
            self.assertEqual((255, 0, 0), pixel(20, 120))  # retained page paint
            self.assertEqual((0, 0, 255), pixel(12, 44))  # Text appearance
            self.assertEqual((255, 255, 255), pixel(8, 44))  # outside annotation
            for product, definition in corpus['products'].items():
                self.assertEqual(len(definition['pages']), len(definition['visual']))
                for index, page in enumerate(definition['visual'], 1):
                    raster = first / page['raster']
                    self.assertEqual(page['raster-sha256'], hashlib.sha256(raster.read_bytes()).hexdigest())
                    values = dict(line.split('=', 1) for line in
                                  (first / 'visual' / f'{product}-page-{index}.properties').read_text().splitlines()
                                  if line and not line.startswith('#'))
                    self.assertEqual('144', values['DPI'])
                    self.assertEqual('AE', values['COMPARISON_METRIC'])
                    self.assertEqual('0', values['COMPARISON_FUZZ_PERCENT'])
                    self.assertEqual('0', values['COMPARISON_THRESHOLD'])
                    self.assertEqual('0', values['RENDERER_AGREEMENT_THRESHOLD'])
                    self.assertEqual(str(index), values['PAGE_SELECTION'])
                    self.assertEqual(str(len(definition['pages'])), values['PAGE_COUNT'])
            refused = subprocess.run([sys.executable, str(SCRIPT), str(first)], capture_output=True, text=True)
            self.assertNotEqual(0, refused.returncode)
            self.assertEqual(original, {p.relative_to(first): p.read_bytes() for p in first.rglob('*') if p.is_file()})


if __name__ == '__main__':
    unittest.main()
