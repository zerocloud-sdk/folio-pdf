"""Qualify decoded T13 standards through the public independent qpdf CLI."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'scripts/t13-program-standards.py'


class T13ProgramStandardsTest(unittest.TestCase):
    def test_public_observer_refuses_output_directory_links_before_resolution(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            source = ROOT / 'capabilities/profiles/T13-text/fixtures/marked-structure.pdf'
            failures = []
            for name in ('directory', 'ancestor'):
                outside = directory / (name + '-outside')
                link = directory / (name + '-link')
                link.symlink_to(outside)
                if name == 'ancestor':
                    outside.mkdir()
                output = link if name == 'directory' else link / 'observation'
                process = subprocess.run([sys.executable, '-I', str(SCRIPT), str(ROOT), str(source), 'cmaps', str(output)],
                                         capture_output=True, text=True, timeout=40)
                if (outside.exists() if name == 'directory' else list(outside.iterdir())):
                    failures.append(name + ': outside directory written')
                if process.returncode == 0:
                    failures.append(name + ': output link accepted')
            self.assertEqual([], failures)

    def test_public_observer_refuses_preexisting_output_links_without_changing_their_targets(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(os.environ.get('T75_PROGRAM_PUBLICATION_CONTROLS', temporary))
            directory.mkdir(exist_ok=True)
            failures = []
            for name, artifact, hardlink in (('raw-json-link', 'qpdf.json', False),
                                             ('raw-report-link', 'result.properties', False),
                                             ('raw-json-hardlink', 'qpdf.json', True)):
                work = directory / name
                work.mkdir()
                source = work / 'original.pdf'
                source_bytes = (ROOT / 'capabilities/profiles/T13-text/fixtures/marked-structure.pdf').read_bytes()
                source.write_bytes(source_bytes)
                external = work / 'unrelated.txt' if hardlink else source
                target_bytes = b'Unrelated test-owned content\n' if hardlink else source_bytes
                external.write_bytes(target_bytes)
                output = work / 'output'
                command = [sys.executable, '-I', str(SCRIPT), str(ROOT), str(source), 'cmaps', str(output)]
                process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
                try:
                    deadline = time.monotonic() + 20
                    while not output.is_dir():
                        self.assertLess(time.monotonic(), deadline, 'No public observer output directory')
                        time.sleep(.001)
                    if hardlink:
                        os.link(external, output / artifact)
                    else:
                        (output / artifact).symlink_to(external)
                    stdout, stderr = process.communicate(timeout=40)
                finally:
                    if process.poll() is None:
                        process.kill()
                        process.communicate()
                (work / 'stdout.txt').write_text(stdout)
                (work / 'stderr.txt').write_text(stderr)
                (work / 'command.json').write_text(json.dumps({'argv': command, 'exit': process.returncode,
                    'source_sha256': hashlib.sha256(source_bytes).hexdigest(),
                    'source_after_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
                    'target_sha256': hashlib.sha256(target_bytes).hexdigest(),
                    'target_after_sha256': hashlib.sha256(external.read_bytes()).hexdigest()}, indent=2) + '\n')
                if external.read_bytes() != target_bytes:
                    failures.append(name + ': external target changed')
                if source.read_bytes() != source_bytes:
                    failures.append(name + ': input changed')
                if process.returncode == 0:
                    values = dict(line.split('=', 1) for line in (output / 'result.properties').read_text().splitlines())
                    if values.get('standards') == 'pass':
                        failures.append(name + ': existing output accepted')
            self.assertEqual([], failures)

    def test_public_observer_returns_producer_digests_for_all_retained_outputs(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'observation'
            source = ROOT / 'capabilities/profiles/T13-text/fixtures/marked-structure.pdf'
            process = subprocess.run([sys.executable, '-I', str(SCRIPT), str(ROOT), str(source), 'content', str(output)],
                                     capture_output=True, text=True, timeout=40)
            self.assertEqual(0, process.returncode, process.stderr)
            manifest = {}
            for line in process.stdout.splitlines():
                digest, name = line.split('  ', 1)
                self.assertNotIn(name, manifest)
                manifest[name] = digest
            self.assertEqual({'qpdf-version.txt', 'qpdf-version.txt.stderr', 'qpdf.json',
                              'qpdf.json.stderr', 'result.properties'}, set(manifest))
            for name, digest in manifest.items():
                self.assertEqual(hashlib.sha256((output / name).read_bytes()).hexdigest(), digest)
            result = dict(line.split('=', 1) for line in (output / 'result.properties').read_text().splitlines())
            self.assertEqual('pass', result['standards'])
            self.assertEqual(manifest['qpdf.json'], result['qpdf-json-sha256'])

    def test_content_resource_dictionary_positions_reject_stream_objects(self):
        program = b'/S/P BDC BT/F1 10 Tf(A)Tj ET EMC /Fm Do'
        objects = [b'<</Type/Catalog/Pages 2 0 R>>', b'<</Type/Pages/Count 1/Kids[3 0 R]>>',
                   b'<</Type/Page/Parent 2 0 R/MediaBox[0 0 100 100]/Resources 4 0 R/Contents 5 0 R>>',
                   b'<</Font 6 0 R/Properties 8 0 R/XObject 10 0 R>>',
                   ('<</Length %d>>\nstream\n' % len(program)).encode('ascii') + program + b'\nendstream',
                   b'<</F1 7 0 R>>', b'<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>',
                   b'<</P 9 0 R>>', b'<</MCID 0>>', b'<</Fm 11 0 R>>',
                   b'<</Type/XObject/Subtype/Form/BBox[0 0 10 10]/Resources 12 0 R/Length 0>>\nstream\n\nendstream',
                   b'<<>>']
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, number in (('ordinary-dictionaries', None), ('page-resources', 4), ('font-map', 6),
                                   ('font', 7), ('property-map', 8), ('property-list', 9),
                                   ('xobject-map', 10), ('form-resources', 12)):
                with self.subTest(resource=name):
                    changed = list(objects)
                    if number is not None:
                        changed[number - 1] = changed[number - 1][:-2] + b'/Length 0>>\nstream\n\nendstream'
                    data = bytearray(b'%PDF-2.0\n'); offsets = []
                    for index, body in enumerate(changed, 1):
                        offsets.append(len(data)); data.extend(('%d 0 obj\n' % index).encode('ascii') + body + b'\nendobj\n')
                    xref = len(data); data.extend(b'xref\n0 13\n0000000000 65535 f \n')
                    for offset in offsets:
                        data.extend(('%010d 00000 n \n' % offset).encode('ascii'))
                    data.extend(('trailer\n<</Root 1 0 R/Size 13>>\nstartxref\n%d\n%%%%EOF\n' % xref).encode('ascii'))
                    pdf = directory / (name + '.pdf'); pdf.write_bytes(data)
                    if number == 4:
                        output = directory / name
                        run = subprocess.run([sys.executable, str(SCRIPT), str(ROOT), str(pdf), 'content', str(output)],
                                             capture_output=True, text=True, timeout=40)
                        self.assertEqual(0, run.returncode, run.stderr)
                        values = dict(line.split('=', 1) for line in (output / 'result.properties').read_text().splitlines())
                        self.assertEqual('indeterminate', values['standards'])
                        self.assertIn('qpdf did not complete without findings', values['finding'])
                        self.assertEqual(hashlib.sha256(data).hexdigest(), values['input-sha256'])
                        self.assertEqual(hashlib.sha256(SCRIPT.read_bytes()).hexdigest(), values['observer-sha256'])
                    else:
                        self.observe(directory, pdf, 'pass' if number is None else 'fail',
                                     None if number is None else 'content-resource', scope='content')

    def test_forms_use_local_named_resources_in_pdf2_and_legacy_fallback_remains_unqualified(self):
        original = (ROOT / 'capabilities/profiles/T13-text/fixtures/nested-split-type3.pdf').read_bytes()
        leaf = b'0 0 1 rg BT /F1 10 Tf (A) Tj ET\n'
        middle = b'/Matrix [1 0 0 1 20 30] /Resources << /XObject << /Leaf 9 0 R >> >>'
        caller = b'/Resources<</XObject<</Leaf 9 0 R>>/Font<</F1 6 0 R>>>>'
        local = b'/Matrix [2 0 0 1 5 10] /Resources << /Font << /F1 6 0 R >> >>'
        self.assertLessEqual(len(caller), len(middle))
        nested = original.replace(middle, caller.ljust(len(middle)))
        missing = nested.replace(local, b'/Matrix [2 0 0 1 5 10]'.ljust(len(local)))
        inherited = missing.replace(leaf, b'BT(A)Tj ET'.ljust(len(leaf)))
        marked = (ROOT / 'capabilities/profiles/T13-text/fixtures/marked-structure.pdf').read_bytes()
        marked_local = b'/Resources << /Font << /F1 5 0 R >> >>'
        self.assertEqual(1, marked.count(marked_local))
        override = marked.replace(marked_local, b' ' * len(marked_local))
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, data, expected in (
                    ('pdf2-local-form-font', nested.replace(b'%PDF-1.7', b'%PDF-2.0'), 'pass'),
                    ('pdf2-caller-form-font', missing.replace(b'%PDF-1.7', b'%PDF-2.0'), 'fail'),
                    ('legacy-caller-form-font', missing, 'indeterminate'),
                    ('pdf2-inherited-font-state', inherited.replace(b'%PDF-1.7', b'%PDF-2.0'), 'pass'),
                    ('legacy-inherited-font-state', inherited, 'pass'),
                    ('pdf2-empty-form-resources', nested.replace(local, b'/Resources<<>>'.ljust(len(local)))
                     .replace(b'%PDF-1.7', b'%PDF-2.0'), 'fail'),
                    ('catalog-pdf2-form-font', override, 'fail'),
                    ('catalog-legacy-form-font', override.replace(b'/Version /2.0', b'/Version /1.7'), 'indeterminate'),
                    ('header-pdf2-over-lower-catalog', override.replace(b'/Version /2.0', b'/Version /1.7')
                     .replace(b'%PDF-1.7', b'%PDF-2.0'), 'fail')):
                with self.subTest(resource=name):
                    self.assertIn(len(data), (len(original), len(marked)))
                    pdf = directory / (name + '.pdf'); pdf.write_bytes(data)
                    self.observe(directory, pdf, expected, 'content-resource' if expected == 'fail' else None, scope='content')

    def test_content_resource_names_keep_original_bytes_across_qpdf_name_encodings(self):
        original = (ROOT / 'capabilities/profiles/T13-text/fixtures/nested-split-type3.pdf').read_bytes()
        first = b'1 0 0 rg BT /F1 20 Tf 1 0 0 1 10 20 Tm [<4\n'
        leaf = b'0 0 1 rg BT /F1 10 Tf (A) Tj ET\n'
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, key, invocation, expected in (
                    ('font-utf8', b'\xc3\xa9', b'\xc3\xa9', 'pass'),
                    ('font-byte', b'\xe9', b'\xe9', 'pass'),
                    ('font-escaped-utf8', b'\xc3\xa9', b'#C3#A9', 'pass'),
                    ('font-escaped-byte', b'\xe9', b'#E9', 'pass'),
                    ('font-different-byte', b'\xc3\xa9', b'#E9', 'fail'),
                    ('font-different-utf8', b'\xe9', b'#C3#A9', 'fail')):
                with self.subTest(name=name):
                    data = original.replace(b'/F1', b'/' + key.ljust(2))
                    data = data.replace(first.replace(b'/F1', b'/' + key.ljust(2)),
                                        (b'BT/' + invocation + b' 20 Tf[<4').ljust(len(first)))
                    data = data.replace(leaf.replace(b'/F1', b'/' + key.ljust(2)),
                                        (b'BT/' + invocation + b' 10 Tf(A)Tj ET').ljust(len(leaf)))
                    self.assertEqual(len(original), len(data))
                    pdf = directory / (name + '.pdf'); pdf.write_bytes(data)
                    self.observe(directory, pdf, expected, 'content-resource' if expected == 'fail' else None, scope='content')
            for label, key in (('utf8', b'\xc3\xa9'), ('byte', b'\xe9')):
                with self.subTest(name='form-' + label):
                    pdf = directory / ('form-' + label + '.pdf')
                    pdf.write_bytes(original.replace(b'/Leaf', b'/' + key.ljust(4)))
                    self.observe(directory, pdf, 'pass', scope='content')
                with self.subTest(name='property-' + label):
                    resources = b'/Matrix [2 0 0 1 5 10] /Resources << /Font << /F1 6 0 R >> >>'
                    replacement = b'/Resources<</Font<</F1 6 0 R>>/Properties<</' + key + b'<<>>>>>>'
                    self.assertLessEqual(len(replacement), len(resources))
                    pdf = directory / ('property-' + label + '.pdf')
                    pdf.write_bytes(original.replace(resources, replacement.ljust(len(resources)))
                                    .replace(leaf, (b'/S/' + key + b' BDC EMC').ljust(len(leaf))))
                    self.observe(directory, pdf, 'pass', scope='content')

    def test_type3_rectangle_glyph_programs_have_valid_paths_and_declared_ink_bounds(self):
        original = (ROOT / 'capabilities/profiles/T13-text/fixtures/nested-split-type3.pdf').read_bytes()
        glyph = b'500 0 0 0 400 600 d1 0 0 400 600 re f\n'
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            variants = (
                ('glyph-missing-header', b'0 0 400 600 re f', 'fail', 'type3-program'),
                ('glyph-repeated-header', b'500 0 d0 500 0 d0', 'fail', 'type3-program'),
                ('glyph-rectangle-name', b'500 0 0 0 400 600 d1 0/x 400 600 re f', 'fail', 'type3-program'),
                ('glyph-rectangle-arity', b'500 0 0 0 400 600 d1 0 0 400 re f', 'fail', 'type3-program'),
                ('glyph-unpainted-path', b'500 0 0 0 400 600 d1 0 0 400 600 re', 'fail', 'type3-program'),
                ('glyph-outside-d1-bounds', b'500 0 0 0 400 600 d1 1 0 400 600 re f', 'fail', 'type3-bounds'),
                ('glyph-negative-dimensions', b'500 0 0 0 400 600 d1 4 6 -4 -6 re f', 'pass', None),
                ('glyph-fractional-dimensions', b'500 0 0 0 400 600 d1 .1 0 .2 600 re f', 'pass', None),
                ('glyph-multiple-contours', b'500 0 d0 0 0 1 1 re 1 1 -1 -1 re f', 'indeterminate', None),
                ('glyph-other-graphics', b'500 0 d0 0 0 m 400 600 l S', 'indeterminate', None))
            for name, body, expected, rule in variants:
                with self.subTest(glyph=name):
                    self.assertLessEqual(len(body), len(glyph))
                    pdf = directory / (name + '.pdf')
                    pdf.write_bytes(original.replace(glyph, body.ljust(len(glyph))))
                    self.observe(directory, pdf, expected, rule, scope='content')
            bbox = b'/FontBBox [0 0 400 600]'
            for name, value, expected in (('font-box-too-small', b'/FontBBox [0 0 399 600]', 'fail'),
                                          ('font-box-unspecified', b'/FontBBox [0 0 0 0]', 'pass')):
                with self.subTest(glyph=name):
                    pdf = directory / (name + '.pdf')
                    pdf.write_bytes(original.replace(bbox, value.ljust(len(bbox))))
                    self.observe(directory, pdf, expected, 'type3-bounds' if expected == 'fail' else None, scope='content')

    def test_marked_properties_resolve_named_resources_and_identify_unique_sequences(self):
        original = (ROOT / 'capabilities/profiles/T13-text/fixtures/nested-split-type3.pdf').read_bytes()
        leaf = b'0 0 1 rg BT /F1 10 Tf (A) Tj ET\n'
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, body, expected, rule in (
                    ('negative-mcid', b'/S<</MCID -1>>BDC EMC', 'fail', 'content-properties'),
                    ('name-mcid', b'/S<</MCID /X>>BDC EMC', 'fail', 'content-properties'),
                    ('real-mcid', b'/S<</MCID 0.0>>BDC EMC', 'fail', 'content-properties'),
                    ('boolean-mcid', b'/S<</MCID true>>BDC EMC', 'fail', 'content-properties'),
                    ('numeric-language', b'/S<</Lang 1>>BDC EMC', 'fail', 'content-properties'),
                    ('numeric-replacement', b'/S<</ActualText 1>>BDC EMC', 'fail', 'content-properties'),
                    ('name-alternate', b'/S<</Alt /X>>BDC EMC', 'fail', 'content-properties'),
                    ('empty-replacement', b'/S<</ActualText()>>BDC EMC', 'pass', None),
                    ('null-property', b'/S<</MCID null>>BDC EMC', 'pass', None),
                    ('missing-property-resource', b'/S/P BDC EMC', 'fail', 'content-resource')):
                with self.subTest(properties=name):
                    self.assertLessEqual(len(body), len(leaf))
                    pdf = directory / (name + '.pdf')
                    pdf.write_bytes(original.replace(leaf, body.ljust(len(leaf))))
                    self.observe(directory, pdf, expected, rule, scope='content')
            marked = (ROOT / 'capabilities/profiles/T13-text/fixtures/marked-structure.pdf').read_bytes()
            before = b'/Span << /ActualText (Inner) >> BDC'
            with self.subTest(properties='duplicate-mcid'):
                pdf = directory / 'duplicate-mcid.pdf'
                pdf.write_bytes(marked.replace(before, b'/Span << /MCID 0 >> BDC'.ljust(len(before))))
                self.observe(directory, pdf, 'fail', 'content-properties', scope='content')
            resources = b'/Matrix [2 0 0 1 5 10] /Resources << /Font << /F1 6 0 R >> >>'
            for name, properties, expected in (('named-property', b'<</Alt(x)>>', 'pass'),
                                                ('named-property-wrong-type', b'42', 'fail')):
                with self.subTest(properties=name):
                    replacement = b'/Resources<</Font<</F1 6 0 R>>/Properties<</P ' + properties + b'>>>>'
                    self.assertLessEqual(len(replacement), len(resources))
                    self.assertEqual(1, original.count(resources))
                    pdf = directory / (name + '.pdf')
                    pdf.write_bytes(original.replace(resources, replacement.ljust(len(resources)))
                                    .replace(leaf, b'/S/P BDC EMC'.ljust(len(leaf))))
                    self.observe(directory, pdf, expected, 'content-resource' if expected == 'fail' else None, scope='content')

    def test_content_fonts_resolve_in_the_active_resources_and_follow_graphics_state(self):
        original = (ROOT / 'capabilities/profiles/T13-text/fixtures/nested-split-type3.pdf').read_bytes()
        leaf = b'0 0 1 rg BT /F1 10 Tf (A) Tj ET\n'
        first = b'1 0 0 rg BT /F1 20 Tf 1 0 0 1 10 20 Tm [<4\n'
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            variants = (
                ('missing-font-resource', original.replace(leaf, b'BT/F9 10 Tf(A)Tj ET'.ljust(len(leaf))), 'fail'),
                ('form-used-as-font', original.replace(b'/F1 6 0 R', b'/F1 9 0 R'), 'fail'),
                ('no-selected-font', original.replace(b'/F1 20 Tf', b' ' * 9), 'fail'),
                ('form-inherits-selected-font', original.replace(leaf, b'BT(A)Tj ET'.ljust(len(leaf))), 'pass'),
                ('font-restored-to-unset', original.replace(first, b'q/F1 20 Tf Q BT[<4'.ljust(len(first))), 'fail'),
                ('font-restored-to-selected', original.replace(first, b'/F1 20 Tf q Q BT[<4'.ljust(len(first))), 'pass'),
                ('missing-form-resource', original.replace(b'/Leaf Do', b'/Lost Do'), 'fail'))
            for name, data, expected in variants:
                with self.subTest(resource=name):
                    self.assertNotEqual(original, data)
                    self.assertEqual(len(original), len(data))
                    pdf = directory / (name + '.pdf')
                    pdf.write_bytes(data)
                    self.observe(directory, pdf, expected, 'content-resource' if expected == 'fail' else None, scope='content')

    def test_content_text_graphics_and_marked_sequences_balance_within_each_execution_context(self):
        original = (ROOT / 'capabilities/profiles/T13-text/fixtures/nested-split-type3.pdf').read_bytes()
        leaf = b'0 0 1 rg BT /F1 10 Tf (A) Tj ET\n'
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, body, expected in (
                    ('balanced-graphics-text', b'q BT/F1 10 Tf(A)Tj ET Q', 'pass'),
                    ('nested-text', b'BT BT ET ET', 'fail'),
                    ('unmatched-text-end', b'ET', 'fail'),
                    ('unterminated-text', b'BT', 'fail'),
                    ('matrix-outside-text', b'1 0 0 1 0 0 Tm', 'fail'),
                    ('show-outside-text', b'(A)Tj', 'fail'),
                    ('save-inside-text', b'BT q Q ET', 'fail'),
                    ('restore-inside-text', b'q BT Q ET', 'fail'),
                    ('concatenate-inside-text', b'BT 1 0 0 1 0 0 cm ET', 'fail'),
                    ('graphics-underflow', b'Q q', 'fail'),
                    ('graphics-unclosed', b'q', 'fail'),
                    ('marked-underflow', b'EMC', 'fail'),
                    ('marked-unclosed', b'/S<<>>BDC', 'fail'),
                    ('marked-crosses-text', b'/S<<>>BDC BT EMC ET', 'fail'),
                    ('text-crosses-marked', b'BT/S<<>>BDC ET EMC', 'fail'),
                    ('graphics-crosses-marked', b'q/S<<>>BDC Q EMC', 'fail'),
                    ('marked-crosses-graphics', b'/S<<>>BDC q EMC Q', 'fail'),
                    ('graphics-within-marked', b'/S<<>>BDC q Q EMC', 'pass'),
                    ('marked-within-graphics', b'q/S<<>>BDC EMC Q', 'pass'),
                    ('text-within-marked', b'/S<<>>BDC BT ET EMC', 'pass'),
                    ('marked-within-text', b'BT/S<<>>BDC EMC ET', 'pass')):
                with self.subTest(state=name):
                    self.assertLessEqual(len(body), len(leaf))
                    pdf = directory / (name + '.pdf')
                    pdf.write_bytes(original.replace(leaf, body.ljust(len(leaf))))
                    self.observe(directory, pdf, expected, 'content-state' if expected == 'fail' else None, scope='content')
            for name, first, second in (
                    ('graphics-spans-contents', b'q BT/F1 20 Tf[<4', b'1>]TJ ET Q\n'),
                    ('marked-spans-contents', b'/S<<>>BDC BT/F1 20 Tf[<4', b'1>]TJ ET EMC\n')):
                with self.subTest(state=name):
                    pdf = directory / (name + '.pdf')
                    pdf.write_bytes(original.replace(b'1 0 0 rg BT /F1 20 Tf 1 0 0 1 10 20 Tm [<4\n', first.ljust(43))
                                    .replace(b'1>] TJ ET /Middle Do\n', second.ljust(21)))
                    self.observe(directory, pdf, 'pass', scope='content')
            with self.subTest(state='form-in-text-object'):
                pdf = directory / 'form-in-text-object.pdf'
                pdf.write_bytes(original.replace(b'1>] TJ ET /Middle Do\n', b'1>] TJ /Middle Do ET\n'))
                self.observe(directory, pdf, 'fail', 'content-state', scope='content')
            with self.subTest(state='form-does-not-pop-caller-graphics'):
                pdf = directory / 'form-does-not-pop-caller-graphics.pdf'
                pdf.write_bytes(original.replace(b'/Leaf 9 0 R', b'/X    9 0 R')
                                .replace(b'/Leaf Do\n', b'q/X Do Q\n').replace(leaf, b'Q'.ljust(len(leaf))))
                self.observe(directory, pdf, 'fail', 'content-state', scope='content')

    def test_content_names_comments_and_dictionary_values_follow_pdf_lexical_rules(self):
        original = (ROOT / 'capabilities/profiles/T13-text/fixtures/nested-split-type3.pdf').read_bytes()
        leaf = b'0 0 1 rg BT /F1 10 Tf (A) Tj ET\n'
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            pdf = directory / 'escaped-form-name.pdf'
            pdf.write_bytes(original.replace(b'/Leaf 9 0 R', b'/X    9 0 R')
                            .replace(b'/Leaf Do\n', b'/#58 Do\n '))
            with self.subTest(lexical='escaped-form-name'):
                self.observe(directory, pdf, 'pass', scope='content')
            for name, body, expected in (
                    ('brace-name-tag', b'/{S}<<>>BDC EMC', 'pass'),
                    ('brace-name-key', b'/S<</{key} 1>>BDC EMC', 'pass'),
                    ('escaped-brace-name', b'/#7BS#7D<<>>BDC EMC', 'pass'),
                    ('form-feed-comment', b'%x\x0cBAD\nBT/F1 10 Tf(A)Tj ET\n', 'pass'),
                    ('line-feed-comment', b'%x\nBT/F1 10 Tf(A)Tj ET\n', 'pass'),
                    ('missing-name-escape', b'BT/F# 10 Tf(A)Tj ET\n', 'fail'),
                    ('nonhex-name-escape', b'BT/F#0G 10 Tf(A)Tj ET\n', 'fail'),
                    ('null-name-escape', b'BT/F#00 10 Tf(A)Tj ET\n', 'fail'),
                    ('dictionary-word', b'/S<</X invalid>>BDC EMC\n', 'fail'),
                    ('dictionary-name-alias', b'/S<</A 1/#41 2>>BDC EMC\n', 'fail'),
                    ('literal-values', b'/S<</X[true false null]>>BDC EMC', 'pass')):
                with self.subTest(lexical=name):
                    self.assertEqual(1, original.count(leaf))
                    self.assertLessEqual(len(body), len(leaf))
                    pdf = directory / (name + '.pdf')
                    pdf.write_bytes(original.replace(leaf, body.ljust(len(leaf))))
                    self.observe(directory, pdf, expected, 'program-token' if expected == 'fail' else None, scope='content')

    def test_pdf_content_operand_grammar_follows_split_pages_and_nested_forms(self):
        fixtures = ROOT / 'capabilities/profiles/T13-text/fixtures'
        original = (fixtures / 'nested-split-type3.pdf').read_bytes()
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for product in ('nested-split-type3', 'marked-structure', 'embedded-font-kinds',
                            'embedded-font-inheritance', 'uncertain-geometry'):
                with self.subTest(source=product):
                    self.observe(directory, fixtures / (product + '.pdf'), 'pass', scope='content')
            variants = (
                ('text-matrix-name', b'1 0 0 1 10 20 Tm', b'1 0 0 1 /x 20 Tm'),
                ('text-matrix-arity', b'1 0 0 1 10 20 Tm', b'1 0 0 1    20 Tm'),
                ('font-size-name', b'/F1 10 Tf', b'/F1 /x Tf'),
                ('show-name', b'(A) Tj', b'/AA Tj'),
                ('form-name', b'/Leaf Do', b'(xxx) Do'),
                ('rgb-name', b'0 0 1 rg', b'0/x 1 rg'),
            )
            for name, before, after in variants:
                with self.subTest(defect=name):
                    self.assertEqual(1, original.count(before))
                    # These controls change only same-length stream tokens, retaining xref and Length.
                    self.assertLessEqual(len(after), len(before))
                    pdf = directory / (name + '.pdf')
                    pdf.write_bytes(original.replace(before, after.ljust(len(before))))
                    self.observe(directory, pdf, 'fail', 'content-operands', scope='content')

    def test_curved_truetype_outlines_remain_outside_exact_polygon_bounds_qualification(self):
        pdf = ROOT / 'capabilities/profiles/T13-standards/review-fixtures/ttf-offcurve-bbox-legal.pdf'
        with tempfile.TemporaryDirectory() as temporary:
            result = self.observe(Path(temporary), pdf, 'indeterminate', scope='fonts')
            self.assertIn('Curved TrueType', result['finding'])

    def test_cff_real_dictionary_widths_remain_unqualified_after_float_decoding(self):
        fixtures = ROOT / 'capabilities/profiles/T13-standards/review-fixtures'
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for kind in ('cff', 'cidcff'):
                for field in ('default', 'nominal'):
                    for value, declared in (('500.1', '500.1'), ('500.1', '500.2'), ('500.5', '500.5')):
                        name = kind + '-' + field + '-' + value + '-pdf-' + declared
                        with self.subTest(program=name):
                            result = self.observe(directory, fixtures / (name + '.pdf'), 'indeterminate', scope='font-metrics')
                            self.assertIn('DICT widths', result['finding'])

    def test_truetype_outline_bounds_preserve_exact_units_per_em_ratios(self):
        fixtures = ROOT / 'capabilities/profiles/T13-standards/review-fixtures'
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name in ('ttf-bbox-exact-320.8', 'ttf-bbox-margin-321'):
                with self.subTest(bounds=name):
                    self.observe(directory, fixtures / (name + '.pdf'), 'pass', scope='font-metrics')
            original = (fixtures / 'ttf-bbox-exact-320.8.pdf').read_bytes()
            self.assertEqual(1, original.count(b'/FontBBox [0 0 320.8 480]'))
            pdf = directory / 'ttf-bbox-too-small-320.7.pdf'
            pdf.write_bytes(original.replace(b'/FontBBox [0 0 320.8 480]', b'/FontBBox [0 0 320.7 480]'))
            self.observe(directory, pdf, 'fail', 'font-descriptor-bounds', scope='font-metrics')

    def test_truetype_widths_preserve_exact_units_per_em_ratios(self):
        fixtures = ROOT / 'capabilities/profiles/T13-standards/review-fixtures'
        identities = json.loads((fixtures / 'font-sha256.json').read_text())
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, expected in (('exact-fractional-truetype-width', 'pass'),
                                   ('ttf-rational-200.4', 'pass'),
                                   ('ttf-rational-200.5', 'fail'),
                                   ('cidttf-rational-200.4', 'pass')):
                with self.subTest(program=name):
                    pdf = fixtures / (name + '.pdf')
                    self.assertEqual(identities[pdf.name], hashlib.sha256(pdf.read_bytes()).hexdigest())
                    self.observe(directory, pdf, expected,
                                 'font-widths' if expected == 'fail' else None, scope='font-metrics')

    def test_cid_widths_and_default_widths_agree_with_selected_embedded_glyphs(self):
        original = (ROOT / 'capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf').read_bytes()
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for number in (18, 21):
                start = original.index(('\n' + str(number) + ' 0 obj\n').encode('ascii'))
                end = original.index(b'\nendobj', start)
                body = original[start:end]
                self.assertEqual(1, body.count(b'/W [1 [500]]'))
                self.assertEqual(1, body.count(b'/DW 500'))
                for field in ('W', 'W-range', 'DW'):
                    replacement = {'W': b'/W [1 [500]]', 'W-range': b'/W [1 1 500]', 'DW': b'/W []'}[field]
                    baseline = body.replace(b'/W [1 [500]]', replacement.ljust(len(b'/W [1 [500]]')))
                    with self.subTest(font=number, field=field, positive=True):
                        pdf = directory / ('cid-' + str(number) + '-' + field + '-positive.pdf')
                        pdf.write_bytes(original[:start] + baseline + original[end:])
                        self.observe(directory, pdf, 'pass', scope='font-metrics')
                    with self.subTest(font=number, field=field, positive=False):
                        before, after = {'W': (b'/W [1 [500]]', b'/W [1 [499]]'),
                                         'W-range': (b'/W [1 1 500]', b'/W [1 1 499]'),
                                         'DW': (b'/DW 500', b'/DW 499')}[field]
                        changed = baseline.replace(before, after)
                        pdf = directory / ('cid-' + str(number) + '-' + field + '-wrong.pdf')
                        pdf.write_bytes(original[:start] + changed + original[end:])
                        self.observe(directory, pdf, 'fail', 'font-widths', scope='font-metrics')

    def test_simple_pdf_widths_agree_with_the_embedded_glyph_program(self):
        original = (ROOT / 'capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf').read_bytes()
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for product in ('nested-split-type3', 'marked-structure', 'embedded-font-kinds',
                            'embedded-font-inheritance', 'uncertain-geometry'):
                with self.subTest(positive=product):
                    pdf = ROOT / ('capabilities/profiles/T13-text/fixtures/' + product + '.pdf')
                    self.observe(directory, pdf, 'pass', scope='font-metrics')
            for number in (5, 6, 7):
                with self.subTest(font=number):
                    start = original.index(('\n' + str(number) + ' 0 obj\n').encode('ascii'))
                    end = original.index(b'\nendobj', start)
                    body = original[start:end]
                    self.assertEqual(1, body.count(b'/Widths [500]'))
                    changed = body.replace(b'/Widths [500]', b'/Widths [499]')
                    pdf = directory / ('font-' + str(number) + '-inconsistent-width.pdf')
                    pdf.write_bytes(original[:start] + changed + original[end:])
                    self.observe(directory, pdf, 'fail', 'font-widths', scope='font-metrics')

    def test_explicit_cff_matrices_remain_unqualified_in_the_default_matrix_profile(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name in ('cff-explicit-decimal-matrix', 'cff-explicit-decimal-matrix-0051',
                         'cidcff-explicit-fd-matrix'):
                with self.subTest(program=name):
                    pdf = ROOT / ('capabilities/profiles/T13-standards/review-fixtures/' + name + '.pdf')
                    values = self.observe(directory, pdf, 'indeterminate', scope='fonts')
                    self.assertIn('FontMatrix', values['finding'])

    def test_cid_cff_required_font_dictionary_selection_produces_failure_records(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            pdf = ROOT / 'capabilities/profiles/T13-standards/review-fixtures/cidcff-missing-fdselect.pdf'
            with self.subTest(required='FDSelect'):
                self.observe(directory, pdf, 'fail', 'font-program-data', scope='fonts')
            original = (ROOT / 'capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf').read_bytes()
            font = (ROOT / 'capabilities/profiles/T13-fonts/FolioT13RectangleCID.cff').read_bytes()
            self.assertEqual(1, font.count(bytes([12, 36])))
            changed = font.replace(bytes([12, 36]), bytes([12, 31]))
            pdf = directory / 'cidcff-missing-fdarray.pdf'
            pdf.write_bytes(original.replace(font.hex().encode('ascii'), changed.hex().encode('ascii')))
            with self.subTest(required='FDArray'):
                self.observe(directory, pdf, 'fail', 'font-program-data', scope='fonts')

    def test_font_descriptor_rectangle_coordinates_may_be_indirect(self):
        with tempfile.TemporaryDirectory() as temporary:
            pdf = ROOT / 'capabilities/profiles/T13-standards/review-fixtures/font-bbox-indirect-coordinate.pdf'
            self.observe(Path(temporary), pdf, 'pass', scope='fonts')

    def test_truetype_units_per_em_must_be_valid_before_scaling_outlines(self):
        original = (ROOT / 'capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf').read_bytes()
        font = (ROOT / 'capabilities/profiles/T13-fonts/FolioT13Rectangle.ttf').read_bytes()
        self.assertEqual((1000).to_bytes(2, 'big'), font[190:192])
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for units in (0, 15, 16385):
                with self.subTest(units_per_em=units):
                    changed = bytearray(font)
                    changed[190:192] = units.to_bytes(2, 'big')
                    changed[180:184] = b'\x00' * 4
                    total = sum(int.from_bytes(changed[index:index + 4], 'big') for index in range(172, 228, 4))
                    changed[64:68] = (total & 0xffffffff).to_bytes(4, 'big')
                    total = sum(int.from_bytes(changed[index:index + 4], 'big') for index in range(0, len(changed), 4))
                    changed[180:184] = ((0xb1b0afba - total) & 0xffffffff).to_bytes(4, 'big')
                    pdf = directory / ('units-per-em-' + str(units) + '.pdf')
                    pdf.write_bytes(original.replace(font.hex().encode('ascii'), changed.hex().encode('ascii')))
                    self.observe(directory, pdf, 'fail', 'font-program-units', scope='fonts')

    def test_font_descriptor_rectangle_can_use_either_diagonal_corner_order(self):
        original = (ROOT / 'capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf').read_bytes()
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for box in (b'[400 600 0 0]', b'[0 600 400 0]', b'[400 0 0 600]'):
                with self.subTest(box=box):
                    pdf = directory / ('diagonal-' + box.decode('ascii').replace(' ', '-') + '.pdf')
                    pdf.write_bytes(original.replace(b'/FontBBox [0 0 400 600]', b'/FontBBox ' + box))
                    self.observe(directory, pdf, 'pass', scope='fonts')

    def test_font_descriptor_bounds_enclose_the_embedded_glyph_outlines(self):
        original = (ROOT / 'capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf').read_bytes()
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for descriptor in (12, 13, 14, 15):
                marker = ('\n' + str(descriptor) + ' 0 obj\n').encode('ascii')
                start = original.index(marker) + len(marker)
                end = original.index(b'\nendobj', start)
                body = original[start:end]
                self.assertEqual(1, body.count(b'/FontBBox [0 0 400 600]'))
                for maximum, expected in ((399, 'fail'), (401, 'pass')):
                    with self.subTest(descriptor=descriptor, maximum=maximum):
                        changed = body.replace(b'/FontBBox [0 0 400 600]',
                                               ('/FontBBox [0 0 ' + str(maximum) + ' 600]').encode('ascii'))
                        pdf = directory / ('descriptor-' + str(descriptor) + '-xmax-' + str(maximum) + '.pdf')
                        pdf.write_bytes(original[:start] + changed + original[end:])
                        self.observe(directory, pdf, expected,
                                     'font-descriptor-bounds' if expected == 'fail' else None, scope='fonts')

    def test_conflicting_platform_postscript_names_cannot_qualify_one_program_name(self):
        with tempfile.TemporaryDirectory() as temporary:
            pdf = ROOT / 'capabilities/profiles/T13-standards/review-fixtures/ttf-conflicting-postscript-names.pdf'
            values = self.observe(Path(temporary), pdf, 'indeterminate', scope='fonts')
            self.assertIn('Conflicting TrueType PostScript names', values['finding'])

    def test_cff_charstring_type_must_match_the_qualified_outline_grammar(self):
        original = (ROOT / 'capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf').read_bytes()
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for filename in ('FolioT13Rectangle.cff', 'FolioT13RectangleCID.cff'):
                font = (ROOT / 'capabilities/profiles/T13-fonts' / filename).read_bytes()
                self.assertEqual(1, font.count(bytes.fromhex('8c0c01')))
                for kind in (1, 2, 3):
                    with self.subTest(font=filename, charstring_type=kind):
                        changed = font.replace(bytes.fromhex('8c0c01'), bytes([139 + kind, 12, 6]))
                        pdf = directory / (filename + '-charstring-type-' + str(kind) + '.pdf')
                        pdf.write_bytes(original.replace(font.hex().encode('ascii'), changed.hex().encode('ascii')))
                        values = self.observe(directory, pdf, 'pass' if kind == 2 else 'indeterminate', scope='fonts')
                        if kind != 2:
                            self.assertIn('CharstringType', values['finding'])

    def test_declared_truetype_length_matches_the_decoded_program(self):
        original = (ROOT / 'capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf').read_bytes()
        self.assertEqual(1, original.count(b'/Length1 1212'))
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            pdf = directory / 'wrong-decoded-font-length.pdf'
            pdf.write_bytes(original.replace(b'/Length1 1212', b'/Length1 1211'))
            self.observe(directory, pdf, 'fail', 'font-program-length', scope='fonts')

    def test_pdf_font_names_must_identify_the_actual_embedded_program(self):
        original = (ROOT / 'capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf').read_bytes()
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, before, after in (
                    ('cff', b'/FolioT13RectangleCFF', b'/FolioT13RectangleBAD'),
                    ('cid-cff', b'/FolioT13RectangleCID', b'/FolioT13RectangleBAD'),
                    ('truetype', b'/FolioT13Rectangle ', b'/FolioT13Rectanglx ')):
                with self.subTest(font=name):
                    self.assertEqual(len(before), len(after))
                    self.assertIn(before, original)
                    pdf = directory / ('program-name-' + name + '.pdf')
                    pdf.write_bytes(original.replace(before, after))
                    self.observe(directory, pdf, 'fail', 'font-program-name', scope='fonts')

    def test_truetype_glyph_bounds_must_cover_the_actual_outline_with_valid_checksums(self):
        original = (ROOT / 'capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf').read_bytes()
        font = (ROOT / 'capabilities/profiles/T13-fonts/FolioT13Rectangle.ttf').read_bytes()
        self.assertEqual(b'glyf', font[44:48])
        self.assertEqual((460).to_bytes(4, 'big'), font[52:56])
        self.assertEqual((400).to_bytes(2, 'big'), font[466:468])
        corrupt = bytearray(font)
        corrupt[466:468] = (399).to_bytes(2, 'big')
        table_sum = sum(int.from_bytes(corrupt[index:index + 4], 'big') for index in range(460, 484, 4))
        corrupt[48:52] = (table_sum & 0xffffffff).to_bytes(4, 'big')
        corrupt[180:184] = b'\x00' * 4
        total = sum(int.from_bytes(corrupt[index:index + 4], 'big') for index in range(0, len(corrupt), 4))
        corrupt[180:184] = ((0xb1b0afba - total) & 0xffffffff).to_bytes(4, 'big')
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            pdf = directory / 'glyph-bounds-too-small.pdf'
            pdf.write_bytes(original.replace(font.hex().encode('ascii'), corrupt.hex().encode('ascii')))
            self.observe(directory, pdf, 'fail', 'font-program-outline', scope='fonts')

    def test_cff_outlines_require_moveto_valid_operands_and_terminal_endchar(self):
        original = (ROOT / 'capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf').read_bytes()
        glyph = bytes.fromhex('f8888b16f824f8ecfc24060e')
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name in ('FolioT13Rectangle.cff', 'FolioT13RectangleCID.cff'):
                font = (ROOT / 'capabilities/profiles/T13-fonts' / name).read_bytes()
                self.assertEqual(1, font.count(glyph))
                for label, offset, opcode in (('missing-moveto', 3, 6), ('wrong-moveto-arity', 10, 22),
                                              ('missing-endchar', 11, 139)):
                    with self.subTest(font=name, defect=label):
                        program = bytearray(glyph)
                        program[offset] = opcode
                        corrupt = font.replace(glyph, program)
                        pdf = directory / (name + '-' + label + '.pdf')
                        pdf.write_bytes(original.replace(font.hex().encode('ascii'), corrupt.hex().encode('ascii')))
                        self.observe(directory, pdf, 'fail', 'font-program-outline', scope='fonts')

    def test_cff_index_offsets_start_at_one_and_remain_inside_the_program(self):
        original = (ROOT / 'capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf').read_bytes()
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name in ('FolioT13Rectangle.cff', 'FolioT13RectangleCID.cff'):
                font = (ROOT / 'capabilities/profiles/T13-fonts' / name).read_bytes()
                self.assertEqual(bytes.fromhex('00010101'), font[4:8])
                glyph_index_first = 217 if name.endswith('CID.cff') else 169
                self.assertEqual(1, font[glyph_index_first])
                for label, offset, value in (('first-offset-zero', 7, 0), ('first-offset-two', 7, 2),
                                              ('glyph-first-offset-two', glyph_index_first, 2),
                                              ('last-offset-outside-file', 8, 255)):
                    with self.subTest(font=name, field=label):
                        corrupt = bytearray(font)
                        corrupt[offset] = value
                        pdf = directory / (name + '-' + label + '.pdf')
                        pdf.write_bytes(original.replace(font.hex().encode('ascii'), corrupt.hex().encode('ascii')))
                        self.observe(directory, pdf, 'fail', 'font-program-layout', scope='fonts')

    def test_truetype_whole_font_checksum_is_checked_in_addition_to_each_table(self):
        original = (ROOT / 'capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf').read_bytes()
        font = (ROOT / 'capabilities/profiles/T13-fonts/FolioT13Rectangle.ttf').read_bytes()
        self.assertEqual(b'head', font[60:64])
        self.assertEqual((172).to_bytes(4, 'big'), font[68:72])
        corrupt = bytearray(font)
        corrupt[180] ^= 1  # head.checkSumAdjustment is excluded from the table checksum.
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            self.observe(directory, ROOT / 'capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf',
                         'pass', scope='fonts')
            pdf = directory / 'font-checksum-adjustment.pdf'
            pdf.write_bytes(original.replace(font.hex().encode('ascii'), corrupt.hex().encode('ascii')))
            self.observe(directory, pdf, 'fail', 'font-program-checksum', scope='fonts')

    def test_truetype_tables_have_valid_tags_ranges_alignment_and_zero_padding(self):
        original = (ROOT / 'capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf').read_bytes()
        font = (ROOT / 'capabilities/profiles/T13-fonts/FolioT13Rectangle.ttf').read_bytes()
        self.assertEqual(bytes.fromhex('00000128'), font[20:24])
        self.assertEqual(b'\x00\x00', font[226:228])
        variants = (
            ('invalid-tag', 12, b' O/2'),
            ('unaligned-table', 20, (297).to_bytes(4, 'big')),
            ('table-outside-file', 20, len(font).to_bytes(4, 'big')),
            ('nonzero-padding', 226, b'\x01'),
        )
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, offset, replacement in variants:
                with self.subTest(field=name):
                    corrupt = font[:offset] + replacement + font[offset + len(replacement):]
                    pdf = directory / (name + '.pdf')
                    pdf.write_bytes(original.replace(font.hex().encode('ascii'), corrupt.hex().encode('ascii')))
                    self.observe(directory, pdf, 'fail', 'font-program-layout', scope='fonts')

    def test_truetype_directory_search_fields_and_record_order_are_validated(self):
        original = (ROOT / 'capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf').read_bytes()
        font = (ROOT / 'capabilities/profiles/T13-fonts/FolioT13Rectangle.ttf').read_bytes()
        variants = []
        for name, offset in (('search-range', 6), ('entry-selector', 8), ('range-shift', 10)):
            corrupt = bytearray(font)
            corrupt[offset:offset + 2] = b'\xff\xff'
            variants.append((name, corrupt))
        variants.append(('record-order', font[:12] + font[28:44] + font[12:28] + font[44:]))
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, corrupt in variants:
                with self.subTest(field=name):
                    pdf = directory / (name + '.pdf')
                    pdf.write_bytes(original.replace(font.hex().encode('ascii'), corrupt.hex().encode('ascii')))
                    self.observe(directory, pdf, 'fail', 'font-program-layout', scope='fonts')

    def test_reserved_cff_dictionary_operators_cannot_be_silently_ignored(self):
        with tempfile.TemporaryDirectory() as temporary:
            pdf = ROOT / 'capabilities/profiles/T13-standards/review-fixtures/cff-reserved-topdict-operator.pdf'
            values = self.observe(Path(temporary), pdf, 'indeterminate', scope='fonts')
            self.assertIn('CFF DICT operator', values['finding'])

    def test_cff_charset_cannot_leave_declared_glyphs_unobserved(self):
        original = (ROOT / 'capabilities/profiles/T13-standards/review-fixtures/cff-explicit-charset-4097.pdf').read_bytes()
        self.assertEqual(1, original.count(b'\nstream\n01000404'))
        start = original.index(b'\nstream\n01000404') + len(b'\nstream\n')
        end = original.index(b'>', start)
        # CFF 5176 Appendix C: predefined ISOAdobe charset contains 229 names.
        # This independent single-font layout places its CharStrings INDEX at 26.
        prefix = bytes.fromhex('0100040400010101024100010101088b0f8b8b12a51100000000')
        self.assertEqual(26, len(prefix))
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for count, expected in ((229, 'pass'), (230, 'fail')):
                with self.subTest(glyphs=count):
                    index = count.to_bytes(2, 'big') + b'\x02'
                    index += b''.join(offset.to_bytes(2, 'big') for offset in range(1, count + 2))
                    font = prefix + index + b'\x0e' * count
                    encoded = font.hex().encode('ascii')
                    self.assertLess(len(encoded), end - start)
                    pdf = directory / ('charset-' + str(count) + '.pdf')
                    pdf.write_bytes(original[:start] + encoded.ljust(end - start) + original[end:])
                    self.observe(directory, pdf, expected,
                                 'font-program-data' if expected == 'fail' else None, scope='fonts')

    def test_cff_glyph_budget_observes_the_index_count_before_charset_expansion(self):
        with tempfile.TemporaryDirectory() as temporary:
            pdf = ROOT / 'capabilities/profiles/T13-standards/review-fixtures/cff-explicit-charset-4097.pdf'
            values = self.observe(Path(temporary), pdf, 'indeterminate', scope='fonts')
            self.assertIn('glyph count exceeds', values['finding'])

    def test_duplicate_truetype_table_tags_cannot_hide_an_unchecked_table(self):
        with tempfile.TemporaryDirectory() as temporary:
            pdf = ROOT / 'capabilities/profiles/T13-standards/review-fixtures/ttf-duplicate-hides-bad-checksum.pdf'
            values = self.observe(Path(temporary), pdf, 'indeterminate', scope='fonts')
            self.assertIn('Duplicate TrueType table', values['finding'])

    def test_unimplemented_font_data_produces_an_indeterminate_record(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name in ('cff-unknown-charset-format', 'cidcff-unknown-charset-format'):
                with self.subTest(font=name):
                    pdf = ROOT / ('capabilities/profiles/T13-standards/review-fixtures/' + name + '.pdf')
                    values = self.observe(directory, pdf, 'indeterminate', scope='fonts')
                    self.assertIn('NotImplementedError', values['finding'])

    def test_cff_missing_required_charstrings_produces_a_failure_record(self):
        with tempfile.TemporaryDirectory() as temporary:
            pdf = ROOT / 'capabilities/profiles/T13-standards/review-fixtures/cff-missing-charstrings.pdf'
            self.observe(Path(temporary), pdf, 'fail', 'font-program-data', scope='fonts')

    def test_cff_compatible_minor_versions_remain_readable(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name in ('cff-minor-one', 'cidcff-minor-one'):
                with self.subTest(font=name):
                    pdf = ROOT / ('capabilities/profiles/T13-standards/review-fixtures/' + name + '.pdf')
                    self.observe(directory, pdf, 'pass', scope='fonts')

    def test_font_checksums_cannot_be_qualified_with_python_assertions_disabled(self):
        original = (ROOT / 'capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf').read_bytes()
        font = (ROOT / 'capabilities/profiles/T13-fonts/FolioT13Rectangle.ttf').read_bytes()
        corrupt = bytearray(font)
        corrupt[16] ^= 1
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for level, data in ((0, corrupt), (1, corrupt), (2, font)):
                with self.subTest(optimization=level):
                    pdf = directory / ('optimized-' + str(level) + '.pdf')
                    pdf.write_bytes(original.replace(font.hex().encode('ascii'), data.hex().encode('ascii')))
                    environment = dict(os.environ, PYTHONOPTIMIZE=str(level))
                    values = self.observe(directory, pdf, 'indeterminate' if level else 'fail',
                                          scope='fonts', environment=environment)
                    self.assertEqual(str(level), values['python-optimization-level'])

    def test_font_data_qualification_refuses_missing_or_forged_fonttools(self):
        original = (ROOT / 'capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf').read_bytes()
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            forged = directory / 'fonttools-4.59.2-py3-none-any.whl'
            with zipfile.ZipFile(forged, 'w') as archive:
                archive.writestr('fontTools/__init__.py', '__version__ = "4.59.2"\n')
            for name, wheel in (('forged', forged), ('missing', directory / 'missing.whl')):
                pdf = directory / (name + '.pdf')
                pdf.write_bytes(original)
                environment = dict(os.environ, FOLIO_FONTTOOLS_WHEEL=str(wheel))
                values = self.observe(directory, pdf, 'indeterminate', scope='fonts', environment=environment)
                self.assertNotIn('fonttools-version', values)

    def test_valid_font_headers_cannot_hide_invalid_checksums_or_missing_cff_dictionaries(self):
        original = (ROOT / 'capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf').read_bytes()
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name in ('FolioT13Rectangle.ttf', 'FolioT13Rectangle.cff', 'FolioT13RectangleCID.cff'):
                with self.subTest(font=name):
                    font = (ROOT / 'capabilities/profiles/T13-fonts' / name).read_bytes()
                    corrupt = bytearray(font)
                    if name.endswith('.ttf'):
                        self.assertEqual(b'OS/2', font[12:16])
                        corrupt[16] ^= 1
                    else:
                        self.assertEqual(bytes.fromhex('0001010115'), font[4:9])
                        corrupt[29:31] = b'\x00\x00'
                    self.assertEqual(font[:4], corrupt[:4])
                    encoded = font.hex().encode('ascii')
                    self.assertEqual(1, original.count(encoded))
                    pdf = directory / (name + '.pdf')
                    pdf.write_bytes(original.replace(encoded, corrupt.hex().encode('ascii')))
                    self.observe(directory, pdf, 'fail', 'font-program-data', scope='fonts')

    def test_embedded_font_headers_match_the_declared_binary_format(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name in ('nested-split-type3', 'marked-structure', 'embedded-font-kinds',
                         'embedded-font-inheritance', 'uncertain-geometry'):
                pdf = ROOT / ('capabilities/profiles/T13-text/fixtures/' + name + '.pdf')
                values = self.observe(directory, pdf, 'pass', scope='fonts')
                self.assertEqual('4' if name.startswith('embedded-font') else '0', values['observed-font-programs'])
                self.assertEqual('4.59.2', values['fonttools-version'])
                self.assertEqual('8bd0f759020e87bb5d323e6283914d9bf4ae35a7307dafb2cbd1e379e720ad37',
                                 values['fonttools-wheel-sha256'])
            profile = ROOT / 'capabilities/profiles/T13-standards'
            for name, control in json.loads((profile / 'font-program-controls.json').read_text()).items():
                self.observe(directory, profile / control['path'], 'fail', control['rule'], scope='fonts')

    def test_encoding_and_tounicode_cannot_decode_the_same_prefix_with_different_lengths(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            pdf = ROOT / 'capabilities/profiles/T13-standards/review-fixtures/mixed-conflicting-prefixes.pdf'
            self.observe(directory, pdf, 'fail', 'cmap-font-domain')

    def test_inherited_source_budget_allows_overrides_and_rejects_the_first_new_excess(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for count, expected in ((4096, 'pass'), (4097, 'indeterminate')):
                pdf = ROOT / ('capabilities/profiles/T13-standards/fixtures/program-boundary-inherited-sources-'
                              + str(count) + '.pdf')
                values = self.observe(directory, pdf, expected)
                if expected == 'indeterminate':
                    self.assertIn('bound', values['finding'])

    def test_tounicode_mapping_sources_must_belong_to_the_owning_encoding_domain(self):
        original = (ROOT / 'capabilities/profiles/T13-standards/fixtures/program-boundary-codespaces-256.pdf').read_bytes()
        before = b'<0041> <005A>\nendbfchar'
        self.assertEqual(1, original.count(before))
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            pdf = directory / 'unicode-code-outside-encoding.pdf'
            pdf.write_bytes(original.replace(before, b'<0141> <005A>\nendbfchar', 1))
            self.observe(directory, pdf, 'fail', 'cmap-font-domain')

    def test_tounicode_codespaces_match_the_owning_simple_or_composite_font(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for kind in ('simple-two-byte', 'type0-one-byte'):
                with self.subTest(kind=kind):
                    pdf = ROOT / ('capabilities/profiles/T13-standards/fixtures/program-binding-' + kind + '.pdf')
                    self.observe(directory, pdf, 'fail', 'cmap-font-domain')

    def test_encoding_character_collection_matches_its_cidfont_without_requiring_equal_supplement(self):
        original = (ROOT / 'capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf').read_bytes()
        marker = b'\n18 0 obj\n'
        start = original.index(marker) + len(marker)
        end = original.index(b'\nendobj\n', start)
        dictionary = original[start:end]
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, before, after, expected in (
                    ('different-registry', b'/Registry (Folio)', b'/Registry (Other)', 'fail'),
                    ('different-ordering', b'/Ordering (T13)', b'/Ordering (XYZ)', 'fail'),
                    ('different-supplement', b'/Supplement 0', b'/Supplement 1', 'pass')):
                with self.subTest(name=name):
                    self.assertEqual(1, dictionary.count(before))
                    self.assertEqual(len(before), len(after))
                    pdf = directory / (name + '.pdf')
                    pdf.write_bytes(original[:start] + dictionary.replace(before, after) + original[end:])
                    self.observe(directory, pdf, expected, 'cmap-font-collection' if expected == 'fail' else None)

    def test_no_codespace_block_can_follow_a_mapping_block_even_when_empty(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            pdf = ROOT / 'capabilities/profiles/T13-standards/review-fixtures/codespace-after-mapping.pdf'
            with self.subTest(kind='nonempty'):
                self.observe(directory, pdf, 'fail', 'cmap-codespace')
            data = pdf.read_bytes()
            before = b'1 begincidchar\n<0041> 1\nendcidchar'
            self.assertIn(before, data)
            empty = directory / 'codespace-after-empty-mapping.pdf'
            empty.write_bytes(data.replace(before, b'0 begincidchar\nendcidchar'.ljust(len(before)), 1))
            with self.subTest(kind='empty'):
                self.observe(directory, empty, 'fail', 'cmap-codespace')

    def test_empty_mapping_blocks_still_require_a_preceding_codespace(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            pdf = ROOT / 'capabilities/profiles/T13-standards/review-fixtures/zero-block-before-codespace.pdf'
            self.observe(directory, pdf, 'fail', 'cmap-codespace')

    def test_tounicode_can_select_font_zero_and_rejects_a_different_font_number(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            pdf = ROOT / 'capabilities/profiles/T13-standards/review-fixtures/unicode-usefont-zero.pdf'
            self.observe(directory, pdf, 'pass')
            data = pdf.read_bytes()
            self.assertEqual(1, data.count(b'0 usefont'))
            invalid = directory / 'unicode-usefont-one.pdf'
            invalid.write_bytes(data.replace(b'0 usefont', b'1 usefont'))
            self.observe(directory, invalid, 'fail', 'cmap-usefont-zero')

    def test_cmap_optional_identifiers_cannot_hide_unchecked_declarations(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            fixtures = ROOT / 'capabilities/profiles/T13-standards/review-fixtures'
            for name, outcome, rule in (('xuid-string', 'fail', 'cmap-declaration-type'),
                                        ('xuid-executable', 'indeterminate', None)):
                with self.subTest(name=name):
                    self.observe(directory, fixtures / (name + '.pdf'), outcome, rule)
            original = (fixtures / 'xuid-executable.pdf').read_bytes()
            before = b'/XUID unknownExecutable def'
            self.assertEqual(1, original.count(before))
            for name, after, outcome in (
                    ('xuid-private', b'/XUID [1000000 1] def', 'pass'),
                    ('xuid-nested-code', b'/XUID [unknown] def', 'indeterminate'),
                    ('version-number', b'/CMapVersion 1.0 def', 'pass'),
                    ('version-string', b'/CMapVersion (bad) def', 'fail'),
                    ('uid-integer', b'/UIDOffset 0 def', 'pass'),
                    ('uid-string', b'/UIDOffset (bad) def', 'fail')):
                with self.subTest(name=name):
                    self.assertLessEqual(len(after), len(before))
                    pdf = directory / (name + '.pdf')
                    pdf.write_bytes(original.replace(before, after.ljust(len(before))))
                    self.observe(directory, pdf, outcome, 'cmap-declaration-type' if outcome == 'fail' else None)

    def test_cmap_program_names_and_comments_follow_postscript_lexical_rules(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            fixtures = ROOT / 'capabilities/profiles/T13-standards/review-fixtures'
            for name, outcome, rule in (('hash-name-mismatch', 'fail', 'cmap-dictionary-program'),
                                        ('hash-name-legal', 'pass', None),
                                        ('ff-comment-hidden-bfchar', 'fail', 'cmap-operator-family')):
                with self.subTest(name=name):
                    self.observe(directory, fixtures / (name + '.pdf'), outcome, rule)

    def test_indirect_font_subtype_cannot_hide_an_invalid_encoding(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            pdf = ROOT / 'capabilities/profiles/T13-standards/review-fixtures/indirect-subtype-negative-cid.pdf'
            self.observe(directory, pdf, 'fail', 'cmap-cid-domain')

    def observe(self, directory, pdf, expected, rule=None, scope='cmaps', environment=None):
        output = directory / pdf.stem
        result = subprocess.run([sys.executable, str(SCRIPT), str(ROOT), str(pdf), scope, str(output)],
                                capture_output=True, text=True, timeout=40, env=environment)
        self.assertEqual(0, result.returncode, result.stderr)
        values = dict(line.split('=', 1) for line in (output / 'result.properties').read_text().splitlines())
        self.assertEqual(expected, values['standards'], pdf.name + ': ' + values['finding'])
        self.assertEqual(hashlib.sha256(pdf.read_bytes()).hexdigest(), values['input-sha256'])
        self.assertEqual(hashlib.sha256(SCRIPT.read_bytes()).hexdigest(), values['observer-sha256'])
        self.assertEqual(hashlib.sha256(Path(sys.executable).resolve().read_bytes()).hexdigest(),
                         values['python-executable-sha256'])
        self.assertEqual('12.4.0', values['qpdf-version'])
        self.assertEqual(hashlib.sha256((output / 'qpdf.json').read_bytes()).hexdigest(), values['qpdf-json-sha256'])
        if rule is not None:
            self.assertEqual(rule, values['rule'])
        return values

    def test_direct_fonts_are_observed_and_program_work_is_bounded_before_expansion(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, expected in (('direct-font', 'pass'), ('direct-font-bad', 'indeterminate'),
                                   ('codespaces-256', 'pass'), ('codespaces-257', 'indeterminate'),
                                   ('mappings-4096', 'pass'), ('mappings-4352', 'indeterminate')):
                pdf = ROOT / ('capabilities/profiles/T13-standards/fixtures/program-boundary-' + name + '.pdf')
                values = self.observe(directory, pdf, expected)
                if name.startswith(('codespaces', 'mappings')) and expected == 'indeterminate':
                    self.assertIn('bound', values['finding'])

    def test_authored_cmap_program_controls_and_inherited_range_positives_qualify(self):
        authority = ROOT / 'capabilities/profiles/T13-standards'
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            cases = [(name, authority / ('fixtures/program-positive-' + name + '.pdf'), 'pass', None)
                     for name in ('usecmap', 'usefont', 'cidrange', 'bfrange-string', 'bfrange-array')]
            for name, control in json.loads((authority / 'program-controls.json').read_text()).items():
                pdf = authority / control['path']
                self.assertEqual(control['sha256'], hashlib.sha256(pdf.read_bytes()).hexdigest())
                cases.append((name, pdf, 'fail', control['rule']))
            for name, pdf, expected, rule in cases:
                values = self.observe(directory, pdf, expected)
                if rule is not None:
                    self.assertEqual(rule, values['rule'])

    def test_cmap_qualification_distinguishes_legal_variants_from_unsupported_programs(self):
        original = ROOT / 'capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf'
        variants = (
            ('dictionary-capacity', b'12 dict begin', b'13 dict begin', 'pass'),
            ('comments', b'/CMapType 1 def\n', b'/CMapType 1 def%', 'pass'),
            ('unqualified-envelope', b'/CIDInit /ProcSet findresource begin',
             b'/CIDInit/ProcSet findresource begin', 'pass'),
            ('legacy-type', b'/CMapType 1 def', b'/CMapType 0 def', 'indeterminate'),
        )
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, before, after, expected in variants:
                data = original.read_bytes()
                self.assertIn(before, data)
                pdf = directory / (name + '.pdf')
                pdf.write_bytes(data.replace(before, after.ljust(len(before)), 1))
                values = self.observe(directory, pdf, expected)

    def test_cmap_dictionary_agreement_and_inheritance_are_observed(self):
        root = ROOT / 'capabilities/profiles/T13-text/fixtures'
        variants = (
            ('name-agreement', 'embedded-font-kinds', b'/Type /CMap /CMapName /FolioT13-H',
             b'/Type /CMap /CMapName /FolioT13-X', 'cmap-dictionary-program'),
            ('ros-agreement', 'embedded-font-kinds',
             b'/CIDSystemInfo << /Registry (Folio) /Ordering (T13) /Supplement 0 >> /WMode 0',
             b'/CIDSystemInfo << /Registry (Other) /Ordering (T13) /Supplement 0 >> /WMode 0', 'cmap-dictionary-program'),
            ('mode-agreement', 'embedded-font-kinds', b'/WMode 0 /Length', b'/WMode 1 /Length', 'cmap-dictionary-program'),
            ('parent-cycle', 'embedded-font-inheritance', b'/UseCMap 16 0 R', b'/UseCMap 17 0 R', 'cmap-inheritance-cycle'),
            ('missing-parent-space', 'embedded-font-inheritance', b'/UseCMap 16 0 R', b'', 'cmap-codespace'),
        )
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, source, before, after, rule in variants:
                data = (root / (source + '.pdf')).read_bytes()
                self.assertIn(before, data)
                self.assertLessEqual(len(after), len(before))
                pdf = directory / (name + '.pdf')
                pdf.write_bytes(data.replace(before, after.ljust(len(before)), 1))
                self.observe(directory, pdf, 'fail', rule)

    def test_cmap_codespaces_and_mapping_domains_reject_invalid_ranges_and_unicode(self):
        original = ROOT / 'capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf'
        variants = (
            ('reversed', b'<0000> <FFFF>', b'<FFFF> <0000>', 'cmap-codespace'),
            ('unequal-length', b'<0000> <FFFF>', b'<00> <FFFF>', 'cmap-codespace'),
            ('reversed-byte', b'<0000> <FFFF>', b'<00FF> <0100>', 'cmap-codespace'),
            ('overlap', b'1 begincodespacerange\n<0000> <FFFF>',
             b'2 begincodespacerange\n<0><F><8><9>', 'cmap-codespace-overlap'),
            ('mapping-code', b'<0041> 1\nendcidchar', b'<41> 1\nendcidchar', 'cmap-mapping-domain'),
            ('negative-cid', b'<0041> 1\nendcidchar', b'<0041>-1\nendcidchar', 'cmap-cid-domain'),
            ('surrogate', b'<0041> <0041>\nendbfchar', b'<0041> <D800>\nendbfchar', 'cmap-unicode'),
        )
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, before, after, rule in variants:
                data = original.read_bytes()
                self.assertIn(before, data)
                self.assertLessEqual(len(after), len(before))
                pdf = directory / (name + '.pdf')
                pdf.write_bytes(data.replace(before, after.ljust(len(before)), 1))
                self.observe(directory, pdf, 'fail', rule)

    def test_cmap_program_types_and_operators_match_the_owning_font_entry(self):
        original = ROOT / 'capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf'
        variants = (
            ('encoding-type', b'/CMapType 1 def', b'/CMapType 2 def', 'cmap-program-type'),
            ('unicode-type', b'/CMapType 2 def', b'/CMapType 1 def', 'cmap-program-type'),
            ('encoding-bfchar', b'1 begincidchar\n<0041> 1\nendcidchar\n',
             b'1 beginbfchar\n<0041> 1\nendbfchar\n', 'cmap-operator-family'),
            ('unicode-cidchar', b'1 beginbfchar\n<0041> <0041>\nendbfchar\n',
             b'1 begincidchar\n<0041> 1\nendcidchar\n', 'cmap-operator-family'),
            ('missing-begin', b'begincmap\n', b'', 'cmap-envelope'),
            ('missing-end', b'endcmap\n', b'', 'cmap-envelope'),
        )
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, before, after, rule in variants:
                data = original.read_bytes()
                self.assertIn(before, data)
                self.assertLessEqual(len(after), len(before))
                pdf = directory / (name + '.pdf')
                pdf.write_bytes(data.replace(before, after.ljust(len(before)), 1))
                self.observe(directory, pdf, 'fail', rule)

    def test_original_cmaps_pass_and_an_incorrect_mapping_count_fails(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)

            for name in ('nested-split-type3', 'marked-structure', 'embedded-font-kinds',
                         'embedded-font-inheritance', 'uncertain-geometry'):
                self.observe(directory, ROOT / 'capabilities/profiles/T13-text/fixtures' / (name + '.pdf'), 'pass')
            original = ROOT / 'capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf'
            data = original.read_bytes()
            before = b'1 begincidchar\n<0041> 1\nendcidchar'
            self.assertEqual(2, data.count(before))
            changed = directory / 'wrong-count.pdf'
            changed.write_bytes(data.replace(before, b'2 begincidchar\n<0041> 1\nendcidchar', 1))
            self.observe(directory, changed, 'fail', 'cmap-block-count')


if __name__ == '__main__':
    unittest.main()
