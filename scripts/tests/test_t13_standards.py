"""Original T13 standards controls through the public authoring command."""
from collections import Counter
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'scripts/generate-t13-standards.py'


class T13StandardsTest(unittest.TestCase):
    def test_program_qualification_profile_binds_all_scopes_and_negative_control_files(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'controls'
            generated = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, generated.returncode, generated.stderr)
            profile = dict(line.split('=', 1) for line in (output / 'program-qualification.properties').read_text().splitlines())
            self.assertEqual('T13-text-logical-structure', profile['profile'])
            self.assertEqual({'cmaps', 'content', 'fonts', 'font-metrics', 'structure-content',
                              'structure-hierarchy', 'structure-parent-tree', 'structure-namespaces'},
                             set(profile['scopes'].split(',')))
            required = profile['required-rules'].split(',')
            self.assertEqual(42, len(required))
            self.assertEqual(42, len(set(required)))
            controls = profile['controls'].split(',')
            self.assertEqual(165, len(controls))
            self.assertEqual(165, len(set(controls)))
            for control in controls:
                prefix = 'negative.' + control
                scope = profile[prefix + '.scope']; rule = profile[prefix + '.rule']
                self.assertIn(scope + '.' + rule, required)
                data = (output / profile[prefix + '.path']).read_bytes()
                self.assertEqual(hashlib.sha256(data).hexdigest(), profile[prefix + '.sha256'])
            self.assertEqual('structure-content', profile['negative.structure-content.content-page-key.rule'])
            self.assertEqual('fixtures/structure-content-page-key.pdf', profile['negative.structure-content.content-page-key.path'])

    def test_structure_catalog_covers_hierarchy_parent_slots_namespace_and_content_relationships(self):
        expected = {'structure-hierarchy': 5, 'structure-text': 2, 'structure-parent-tree': 4,
                    'structure-namespace': 4, 'structure-content': 8}
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'controls'
            generated = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, generated.returncode, generated.stderr)
            controls = json.loads((output / 'structure-controls.json').read_text())
            self.assertEqual(expected, dict(Counter(entry['rule'] for entry in controls.values())))
            self.assertEqual(23, len({entry['sha256'] for entry in controls.values()}))
            for name, entry in controls.items():
                data = (output / entry['path']).read_bytes()
                self.assertTrue(entry['scope'].startswith('structure-'))
                self.assertTrue(entry['authority'].startswith(('ISO 32000-1:2008', 'ISO 32000-2:2020')))
                self.assertEqual(entry['sha256'], hashlib.sha256(data).hexdigest())
                offset = int(data.rsplit(b'startxref\n', 1)[1].splitlines()[0])
                self.assertEqual(b'xref', data[offset:offset + 4], name)
            self.assertIn(b'/ParentTreeNextKey 0', (output / controls['parent-next-key']['path']).read_bytes())
            self.assertIn(b'/StructParents 9', (output / controls['content-page-key']['path']).read_bytes())
            self.assertIn(b'/NS 13 0 R', (output / controls['namespace-unlisted']['path']).read_bytes())

    def test_content_catalog_covers_lexical_state_resource_property_and_type3_body_rules(self):
        expected = {'content-operands': 6, 'program-token': 5, 'content-state': 18,
                    'content-resource': 11, 'content-properties': 8, 'type3-program': 5, 'type3-bounds': 2}
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'controls'
            generated = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, generated.returncode, generated.stderr)
            controls = json.loads((output / 'content-controls.json').read_text())
            self.assertEqual(expected, dict(Counter(entry['rule'] for entry in controls.values())))
            self.assertEqual(55, len({entry['sha256'] for entry in controls.values()}))
            for name, entry in controls.items():
                data = (output / entry['path']).read_bytes()
                self.assertEqual('content', entry['scope'])
                self.assertTrue(entry['authority'].startswith(('ISO 32000-1:2008', 'ISO 32000-2:2020')))
                self.assertEqual(entry['sha256'], hashlib.sha256(data).hexdigest())
                offset = int(data.rsplit(b'startxref\n', 1)[1].splitlines()[0])
                self.assertEqual(b'xref', data[offset:offset + 4], name)

    def test_content_controls_isolate_operand_defects_in_the_original_page_and_forms(self):
        expected = {'text-matrix-name': b'1 0 0 1 /x 20 Tm', 'text-matrix-arity': b'1 0 0 1    20 Tm',
                    'font-size-name': b'/F1 /x Tf', 'show-name': b'/AA Tj',
                    'form-name': b'(xxx) Do', 'rgb-name': b'0/x 1 rg'}
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'controls'
            generated = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, generated.returncode, generated.stderr)
            controls = json.loads((output / 'content-controls.json').read_text())
            controls = {name: entry for name, entry in controls.items() if entry['rule'] == 'content-operands'}
            self.assertEqual(set(expected), set(controls))
            self.assertEqual(6, len({entry['sha256'] for entry in controls.values()}))
            for name, token in expected.items():
                entry = controls[name]
                data = (output / entry['path']).read_bytes()
                self.assertEqual('content', entry['scope'])
                self.assertEqual('content-operands', entry['rule'])
                self.assertIn('ISO 32000-1:2008', entry['authority'])
                self.assertEqual(entry['sha256'], hashlib.sha256(data).hexdigest())
                self.assertIn(token, data)
                offset = int(data.rsplit(b'startxref\n', 1)[1].splitlines()[0])
                self.assertEqual(b'xref', data[offset:offset + 4])

    def test_font_metric_controls_isolate_simple_and_cid_advance_disagreements(self):
        expected = {'type1-widths': (5, b'/Widths [499]'),
                    'mmtype1-widths': (6, b'/Widths [499]'),
                    'truetype-widths': (7, b'/Widths [499]')}
        for kind, number in (('cid-cff', 18), ('cid-truetype', 21)):
            for form, token in (('array', b'/W [1 [499]]'), ('range', b'/W [1 1 499]'), ('default', b'/DW 499')):
                expected[kind + '-widths-' + form] = number, token
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'controls'
            result = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            controls = json.loads((output / 'font-metric-controls.json').read_text())
            self.assertEqual(set(expected), set(controls))
            self.assertEqual(9, len({entry['sha256'] for entry in controls.values()}))
            for name, (number, token) in expected.items():
                with self.subTest(control=name):
                    entry = controls[name]
                    self.assertEqual('font-metrics', entry['scope'])
                    self.assertEqual('font-widths', entry['rule'])
                    self.assertTrue(entry['authority'])
                    data = (output / entry['path']).read_bytes()
                    self.assertEqual(hashlib.sha256(data).hexdigest(), entry['sha256'])
                    start = data.index(('\n' + str(number) + ' 0 obj\n').encode('ascii'))
                    end = data.index(b'\nendobj', start)
                    self.assertIn(token, data[start:end])
                    if name.endswith('-default'):
                        self.assertIn(b'/W []', data[start:end])

    def test_font_layout_outline_and_binding_controls_have_isolated_public_recipes(self):
        expected = {
            'truetype-search-range': 'font-program-layout',
            'truetype-entry-selector': 'font-program-layout',
            'truetype-range-shift': 'font-program-layout',
            'truetype-table-order': 'font-program-layout',
            'truetype-table-tag': 'font-program-layout',
            'truetype-unaligned-table': 'font-program-layout',
            'truetype-table-outside-file': 'font-program-layout',
            'truetype-nonzero-padding': 'font-program-layout',
            'truetype-table-checksum': 'font-program-data',
            'truetype-whole-checksum': 'font-program-checksum',
            'truetype-glyph-bounds': 'font-program-outline',
            'truetype-decoded-length': 'font-program-length',
        }
        for kind in ('cff', 'cid-cff', 'truetype', 'cid-truetype'):
            expected[kind + '-descriptor-bounds'] = 'font-descriptor-bounds'
        for units in (0, 15, 16385):
            expected['truetype-units-per-em-' + str(units)] = 'font-program-units'
        for field in ('fdarray', 'fdselect'):
            expected['cid-cff-missing-' + field] = 'font-program-data'
        for kind in ('cff', 'cid-cff'):
            for field in ('first-offset-zero', 'first-offset-two', 'glyph-first-offset-two', 'last-offset-outside-file'):
                expected[kind + '-' + field] = 'font-program-layout'
            for field in ('missing-moveto', 'wrong-moveto-arity', 'missing-endchar'):
                expected[kind + '-' + field] = 'font-program-outline'
            expected[kind + '-topdict-count'] = 'font-program-data'
        for kind in ('cff', 'cid-cff', 'truetype'):
            expected[kind + '-program-name'] = 'font-program-name'
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'controls'
            result = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            controls = json.loads((output / 'font-program-controls.json').read_text())
            for name, rule in expected.items():
                with self.subTest(control=name):
                    self.assertIn(name, controls)
                    entry = controls[name]
                    self.assertEqual(rule, entry['rule'])
                    self.assertEqual('fonts', entry['scope'])
                    self.assertTrue(entry['authority'])
                    data = (output / entry['path']).read_bytes()
                    self.assertEqual(entry['sha256'], hashlib.sha256(data).hexdigest())
                    offset = int(data.rsplit(b'startxref\n', 1)[1].splitlines()[0])
                    self.assertEqual(b'xref', data[offset:offset + 4])

    def test_cff_kind_controls_keep_the_declared_subtype_and_swap_only_the_binary_program(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'controls'
            result = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            controls = json.loads((output / 'font-program-controls.json').read_text())
            for name, number, kind, binary in (
                    ('cff-as-cid', 24, b'/CIDFontType0C', 'FolioT13Rectangle.cff'),
                    ('cid-as-cff', 22, b'/Type1C', 'FolioT13RectangleCID.cff')):
                entry = controls[name]
                self.assertEqual('font-program-kind', entry['rule'])
                data = (output / entry['path']).read_bytes()
                self.assertEqual(entry['sha256'], hashlib.sha256(data).hexdigest())
                body = data.split(('\n' + str(number) + ' 0 obj\n').encode('ascii'), 1)[1].split(b'\nendobj\n', 1)[0]
                self.assertIn(b'/Subtype ' + kind, body)
                font = (ROOT / 'capabilities/profiles/T13-fonts' / binary).read_bytes()
                self.assertIn(b'\nstream\n' + font.hex().encode('ascii') + b'>\n', body)

    def test_binary_font_controls_change_the_program_header_with_valid_pdf_storage(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'controls'
            result = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            controls = json.loads((output / 'font-program-controls.json').read_text())
            self.assertEqual({'cff-header', 'cid-cff-header', 'truetype-header'},
                             {name for name in controls if name.endswith('-header')})
            for name, entry in controls.items():
                if not name.endswith('-header'):
                    continue
                data = (output / entry['path']).read_bytes()
                self.assertEqual(entry['sha256'], hashlib.sha256(data).hexdigest())
                self.assertEqual('fonts', entry['scope'])
                self.assertEqual('font-program-header', entry['rule'])
                offset = int(data.rsplit(b'startxref\n', 1)[1].splitlines()[0])
                self.assertEqual(b'xref', data[offset:offset + 4])
                self.assertIn(b'\nstream\n00020000' if name == 'truetype-header' else b'\nstream\n00000401', data)

    def test_tounicode_owner_controls_preserve_valid_xref_and_change_only_the_character_domain(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'controls'
            result = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            for name in ('simple-two-byte', 'type0-one-byte'):
                data = (output / ('fixtures/program-binding-' + name + '.pdf')).read_bytes()
                offset = int(data.rsplit(b'startxref\n', 1)[1].splitlines()[0])
                self.assertEqual(b'xref', data[offset:offset + 4])
            simple = (output / 'fixtures/program-binding-simple-two-byte.pdf').read_bytes()
            self.assertIn(b'/Encoding /WinAnsiEncoding /ToUnicode 19 0 R', simple)
            composite = (output / 'fixtures/program-binding-type0-one-byte.pdf').read_bytes()
            self.assertIn(b'<00> <FF>\nendcodespacerange\n1 beginbfchar\n<41> <0041>', composite)

    def test_program_qualification_authors_direct_font_and_exact_budget_boundaries(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'controls'
            result = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            for name in ('direct-font', 'direct-font-bad', 'codespaces-256', 'codespaces-257',
                         'mappings-4096', 'mappings-4352', 'inherited-sources-4096', 'inherited-sources-4097'):
                data = (output / ('fixtures/program-boundary-' + name + '.pdf')).read_bytes()
                offset = int(data.rsplit(b'startxref\n', 1)[1].splitlines()[0])
                self.assertEqual(b'xref', data[offset:offset + 4])
            data = (output / 'fixtures/program-boundary-direct-font-bad.pdf').read_bytes()
            self.assertIn(b'/F4 << /Type /Font /Subtype /Type0', data)
            self.assertIn(b'/ToUnicode 22 0 R', data)

    def test_decoded_program_controls_and_legal_inheritance_examples_are_original(self):
        required = {'cmap-' + suffix for suffix in ('block-count', 'encoding-type', 'unicode-type',
                    'encoding-bfchar', 'unicode-cidchar', 'missing-begin', 'missing-end', 'reversed',
                    'unequal-length', 'reversed-byte', 'overlap', 'mapping-code', 'negative-cid', 'surrogate',
                    'name-agreement', 'ros-agreement', 'mode-agreement', 'parent-cycle', 'missing-parent-space',
                    'usecmap-late', 'usecmap-name', 'usecmap-missing-parent', 'usefont-nonzero', 'range-cardinality',
                    'inherited-codespace', 'late-codespace', 'xuid-string', 'version-string', 'uid-string',
                    'font-registry', 'font-ordering', 'font-simple-domain', 'font-type0-domain')}
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'controls'
            result = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            controls = json.loads((output / 'program-controls.json').read_text())
            self.assertEqual(required, set(controls))
            for rule, entry in controls.items():
                data = (output / entry['path']).read_bytes()
                self.assertEqual(entry['sha256'], hashlib.sha256(data).hexdigest())
                self.assertEqual('cmaps', entry['scope'])
                self.assertIn('ISO 32000', entry['authority'])
                self.assertTrue(entry['rule'].startswith('cmap-'))
            for name, field in (('usecmap', b'/FolioT13-H usecmap'), ('usefont', b'0 usefont'),
                                ('cidrange', b'1 begincidrange'), ('bfrange-string', b'1 beginbfrange'),
                                ('bfrange-array', b'[<0041>]')):
                self.assertIn(field, (output / ('fixtures/program-positive-' + name + '.pdf')).read_bytes())

    def test_cmap_dictionary_controls_distinguish_encoding_and_tounicode_requirements(self):
        required = {'cmap-' + kind + '-' + suffix for kind in ('encoding', 'unicode') for suffix in
                    ('type-name', 'type-value', 'name-name', 'ros-dictionary', 'wmode-integer', 'wmode-value', 'parent-type')}
        required.update('cmap-encoding-' + suffix for suffix in ('type-required', 'name-required', 'ros-required'))
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'controls'
            result = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            controls = json.loads((output / 'text-controls.json').read_text())
            self.assertTrue(required.issubset(controls), sorted(required - set(controls)))
            profile = (output / 'arlington-cmaps.properties').read_text()
            for rule in required:
                entry = controls[rule]
                self.assertEqual('arlington-cmaps', entry['checker'])
                self.assertEqual(entry['sha256'], hashlib.sha256((output / entry['path']).read_bytes()).hexdigest())
                self.assertIn('ISO 32000', entry['authority'])
                self.assertIn('negative.' + rule + '.sha256=' + entry['sha256'], profile)

    def test_font_descriptors_and_program_streams_have_source_bound_controls(self):
        required = {kind + '-descriptor-' + suffix for kind in ('type1', 'truetype', 'cid0', 'cid2', 'type3')
                    for suffix in ('type-required', 'type-name', 'flags-required', 'flags-integer',
                                   'fontname-name', 'bbox-numbers', 'italicangle-required', 'italicangle-number',
                                   'ascent-number', 'descent-number', 'descent-nonpositive', 'capheight-number', 'stemv-number')}
        required.update(kind + '-descriptor-' + suffix for kind in ('type1', 'truetype', 'cid0', 'cid2')
                        for suffix in ('fontname-required', 'bbox-required', 'ascent-required', 'descent-required', 'stemv-required'))
        required.update(('type1-fontfile-stream', 'truetype-fontfile-stream', 'cid0-fontfile-stream', 'cid2-fontfile-stream',
                         'type1-program-subtype-required', 'type1-program-subtype-value',
                         'cid0-program-subtype-required', 'cid0-program-subtype-value', 'truetype-program-length1-integer'))
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'controls'
            result = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            controls = json.loads((output / 'text-controls.json').read_text())
            self.assertTrue(required.issubset(controls), sorted(required - set(controls)))
            profile = (output / 'arlington-descriptors.properties').read_text()
            for rule in required:
                entry = controls[rule]
                self.assertEqual('arlington-descriptors', entry['checker'])
                self.assertEqual(entry['sha256'], hashlib.sha256((output / entry['path']).read_bytes()).hexdigest())
                self.assertIn('ISO 32000', entry['authority'])
                self.assertIn('negative.' + rule + '.sha256=' + entry['sha256'], profile)

    def test_required_simple_font_entries_treat_null_as_missing(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'controls'
            result = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            controls = json.loads((output / 'text-controls.json').read_text())
            for kind in ('type1', 'mmtype1', 'truetype'):
                for label, key in (('basefont', 'BaseFont'), ('firstchar', 'FirstChar'), ('lastchar', 'LastChar'),
                                   ('widths', 'Widths'), ('descriptor', 'FontDescriptor')):
                    entry = controls[kind + '-' + label + '-null']
                    data = (output / entry['path']).read_bytes()
                    self.assertIn(('/' + key + ' null').encode(), data)
                    self.assertIn('7.3.9', entry['authority'])
                    self.assertIn('required key does not exist: ' + key, entry['finding'])
                    self.assertEqual(entry['sha256'], hashlib.sha256(data).hexdigest())
                    offset = int(data.rsplit(b'startxref\n', 1)[1].splitlines()[0])
                    self.assertEqual(b'xref', data[offset:offset + 4])

    def test_cid_and_composite_declarations_have_separate_qualified_profiles(self):
        required = {kind + '-' + suffix for kind in ('cid0', 'cid2') for suffix in (
            'basefont-required', 'basefont-name', 'ros-required', 'ros-dictionary',
            'registry-required', 'registry-string', 'ordering-required', 'ordering-string',
            'supplement-required', 'supplement-integer', 'descriptor-required', 'descriptor-dictionary',
            'dw-number', 'w-array', 'w-numbers', 'dw2-array', 'dw2-cardinality', 'dw2-numbers',
            'w2-array', 'w2-numbers')}
        required.update('type0-' + suffix for suffix in (
            'basefont-required', 'basefont-name', 'encoding-required', 'encoding-type',
            'descendants-required', 'descendants-array', 'descendants-nonempty', 'tounicode-stream'))
        required.update(('cid2-map-type', 'cid2-map-name'))
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'controls'
            result = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            controls = json.loads((output / 'text-controls.json').read_text())
            self.assertTrue(required.issubset(controls), sorted(required - set(controls)))
            profile = (output / 'arlington-cid.properties').read_text()
            for rule in required:
                entry = controls[rule]
                self.assertEqual('arlington-cid', entry['checker'])
                self.assertEqual(entry['sha256'], hashlib.sha256((output / entry['path']).read_bytes()).hexdigest())
                self.assertIn('ISO 32000', entry['authority'])
                self.assertIn('negative.' + rule + '.sha256=' + entry['sha256'], profile)

    def test_simple_font_declarations_have_type_specific_controls(self):
        suffixes = ('basefont-required', 'basefont-name', 'firstchar-required', 'firstchar-integer',
                    'lastchar-required', 'lastchar-integer', 'widths-required', 'widths-array',
                    'widths-numbers', 'widths-cardinality', 'descriptor-required', 'descriptor-dictionary', 'encoding-type')
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'controls'
            result = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            controls = json.loads((output / 'text-controls.json').read_text())
            for kind in ('type1', 'mmtype1', 'truetype'):
                for suffix in suffixes:
                    entry = controls[kind + '-' + suffix]
                    data = (output / entry['path']).read_bytes()
                    self.assertEqual(entry['sha256'], hashlib.sha256(data).hexdigest())
                    self.assertIn('ISO 32000', entry['authority'])
                    self.assertGreater(len(entry['finding']), 25)

    def test_glyph_profile_keeps_legal_boundaries_separate_from_unobserved_mappings(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'controls'
            result = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            fields = {'delimiter': b'd1/Artifact', 'fractional': b'/Widths [.1]',
                      'comments': b'% glyph header', 'base-encoding': b'/BaseEncoding /WinAnsiEncoding',
                      'empty-differences': b'/Differences []', 'unmapped-glyph': b'/Differences [65 /.notdef]',
                      'numeric-range': b'/Widths [10000000000000000000000000000000000000000]'}
            for kind, field in fields.items():
                data = (output / ('fixtures/glyph-boundary-' + kind + '.pdf')).read_bytes()
                self.assertIn(field, data)
            controls = json.loads((output / 'text-controls.json').read_text())
            overlap = (output / controls['type3-differences-disjoint']['path']).read_bytes()
            self.assertIn(b'/Differences [65 /A 65 /A]', overlap)
            self.assertIn('9.6.6.1', controls['type3-differences-disjoint']['authority'])

    def test_type3_glyph_headers_and_declared_widths_have_independent_negative_controls(self):
        expected = {
            'type3-glyph-width-agreement': b'400 0 0 0 400 600 d1',
            'type3-glyph-zero-wy': b'500 1 0 0 400 600 d1',
            'type3-glyph-header-operator': b'500 0 0 0 400 600 m ',
            'type3-glyph-header-operands': b'500 0 0 0     600 d1',
        }
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'controls'
            result = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            controls = json.loads((output / 'text-controls.json').read_text())
            for rule, program in expected.items():
                entry = controls[rule]
                data = (output / entry['path']).read_bytes()
                self.assertIn(program, data)
                self.assertIn(b'/Widths [500]', data)
                self.assertIn('Table 113', entry['authority'])
                self.assertEqual(entry['sha256'], hashlib.sha256(data).hexdigest())

    def test_type3_dictionary_obligations_have_isolated_source_grounded_controls(self):
        required = {'type3-' + suffix for suffix in (
            'bbox-required', 'bbox-numbers', 'matrix-required', 'matrix-numbers', 'matrix-count',
            'charprocs-required', 'charprocs-dictionary', 'charproc-stream',
            'encoding-required', 'encoding-dictionary', 'firstchar-required', 'firstchar-integer',
            'lastchar-required', 'lastchar-integer', 'widths-required', 'widths-numbers',
            'widths-cardinality', 'resources-dictionary', 'tagged-descriptor-required')}
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'controls'
            result = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            controls = json.loads((output / 'text-controls.json').read_text())
            self.assertTrue(required.issubset(controls), sorted(required - set(controls)))
            profile = (output / 'arlington-text.properties').read_text()
            for rule in required:
                entry = controls[rule]
                data = (output / entry['path']).read_bytes()
                self.assertEqual(entry['sha256'], hashlib.sha256(data).hexdigest())
                self.assertIn('Table 112', entry['authority'])
                self.assertGreater(len(entry['finding']), 25)
                self.assertIn('negative.' + rule + '.sha256=' + entry['sha256'], profile)
                offset = int(data.rsplit(b'startxref\n', 1)[1].splitlines()[0])
                self.assertEqual(b'xref', data[offset:offset + 4])

    def test_userunit_rules_bind_original_positive_and_non_numeric_and_zero_controls(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'controls'
            result = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            controls = json.loads((output / 'text-controls.json').read_text())
            original = (ROOT / 'capabilities/profiles/T13-text/fixtures/uncertain-geometry.pdf').read_bytes()
            for rule, invalid_field in (('page-userunit-number', b'/UserUnit/X'), ('page-userunit-positive', b'/UserUnit 0')):
                data = (output / controls[rule]['path']).read_bytes()
                self.assertIn(invalid_field, data)
                self.assertEqual(len(original), len(data))
                self.assertEqual(controls[rule]['sha256'], hashlib.sha256(data).hexdigest())
                self.assertIn('Table 30', controls[rule]['authority'])
                profile = (output / 'arlington-text.properties').read_text()
                self.assertIn('negative.' + rule + '.sha256=' + controls[rule]['sha256'], profile)

    def test_type3_names_have_matching_and_optional_positives_and_an_illegal_mismatch(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'controls'
            result = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            named = (output / 'fixtures/positive-named-type3-fonts.pdf').read_bytes()
            unnamed = (output / 'fixtures/positive-unnamed-type3-fonts.pdf').read_bytes()
            self.assertIn(b'/Subtype /Type3 /Name /F1', named)
            self.assertIn(b'/FontName /F1', named)
            self.assertNotIn(b'/FontName', unnamed)
            font_only = (output / 'fixtures/positive-font-name-only-type3-fonts.pdf').read_bytes()
            descriptor_only = (output / 'fixtures/positive-descriptor-name-only-type3-fonts.pdf').read_bytes()
            self.assertIn(b'/Name /F1', font_only)
            self.assertNotIn(b'/FontName', font_only)
            self.assertIn(b'/FontName /F1', descriptor_only)
            self.assertNotIn(b'/Name /F1', descriptor_only)
            controls = json.loads((output / 'controls.json').read_text())
            invalid = (output / controls['type3-fontname-consistent']['path']).read_bytes()
            self.assertIn(b'/FontName /F2', invalid)
            self.assertIn(b'/Subtype /Type3 /Name /F1', invalid)

    def test_required_core_rules_retain_their_original_negative_identities(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'controls'
            result = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            assignments = json.loads((output / 'assignments.json').read_text())
            positive = (output / 'fixtures/positive-pdf20-nested-split-type3.pdf').read_bytes()
            self.assertIn(b'/Type /Catalog /Version /2.0', positive)
            inherited = json.loads((ROOT / 'capabilities/profiles/T12-standards/assignments.json').read_text())
            for rule in ('page-contents-array-member', 'form-matrix-count', 'annotation-page-owner', 'stream-filter-type'):
                self.assertEqual(inherited[rule]['sha256'], assignments[rule]['sha256'])
                self.assertEqual(inherited[rule]['finding'], assignments[rule]['finding'])
            self.assertNotIn('goto-named-target', assignments)
            self.assertNotIn('embedded-checksum-value', assignments)
            self.assertNotIn('highlight-quads-count', assignments)
            assigned = set()
            for checker in ('pdfcpu', 'arlington-core', 'arlington-fonts', 'arlington-text', 'arlington-cid', 'arlington-descriptors', 'arlington-cmaps'):
                values = dict(line.split('=', 1) for line in (output / (checker + '.properties')).read_text().splitlines())
                rules = set(values['required-rules'].split(','))
                self.assertFalse(assigned.intersection(rules))
                assigned.update(rules)
            self.assertEqual(set(assignments), assigned)
            self.assertEqual(assigned, set((output / 'required-rules.txt').read_text().splitlines()))

    def test_shared_descriptor_is_compared_to_the_second_owning_font(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'controls'
            result = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            controls = json.loads((output / 'controls.json').read_text())
            control = controls['shared-fontname-consistent']
            data = (output / control['path']).read_bytes()
            self.assertIn(b'/Subtype /Type1 /BaseFont /FolioT13RectangleCFF', data)
            self.assertIn(b'/Subtype /MMType1 /BaseFont /FolioT13Invalid', data)
            self.assertIn(b'/FontName /FolioT13RectangleCFF', data)
            self.assertEqual(control['sha256'], hashlib.sha256(data).hexdigest())

    def test_font_profile_requires_each_control_identity_and_a_specific_checker_diagnostic(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'controls'
            result = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            controls = json.loads((output / 'controls.json').read_text())
            values = dict(line.split('=', 1) for line in (output / 'arlington-fonts.properties').read_text().splitlines())
            self.assertEqual('T13-text-logical-structure', values['profile'])
            self.assertEqual('2.0', values['pdf-version'])
            self.assertEqual(set(controls), set(values['required-rules'].split(',')))
            self.assertEqual(values['required-rules'], values['covered-rules'])
            for rule, control in controls.items():
                prefix = 'negative.' + rule + '.'
                self.assertEqual(control['path'], values[prefix + 'path'])
                self.assertEqual(control['sha256'], values[prefix + 'sha256'])
                expected = ('FontDescriptor FontName does not match the owning Type3 font Name' if rule == 'type3-fontname-consistent' else
                            'ArrayOfCIDGlyphMetricsW2' if rule.endswith('w2-triples') else
                            'FontDescriptor FontName does not match the owning font BaseFont')
                self.assertIn(expected, values[prefix + 'finding'])

    def test_font_descriptor_and_vertical_width_defects_have_original_reproducible_pdf_controls(self):
        with tempfile.TemporaryDirectory() as temporary:
            outputs = [Path(temporary) / name for name in ('first', 'second')]
            for output in outputs:
                result = subprocess.run([sys.executable, str(SCRIPT), str(output)], capture_output=True, text=True)
                self.assertEqual(0, result.returncode, result.stderr)
            files = {p.relative_to(outputs[0]): p.read_bytes() for p in outputs[0].rglob('*') if p.is_file()}
            self.assertEqual(files, {p.relative_to(outputs[1]): p.read_bytes() for p in outputs[1].rglob('*') if p.is_file()})
            controls = json.loads((outputs[0] / 'controls.json').read_text())
            self.assertEqual({'type1-fontname-consistent', 'truetype-fontname-consistent', 'cid0-fontname-consistent',
                              'cid2-fontname-consistent', 'cid0-w2-triples', 'cid2-w2-triples',
                              'shared-fontname-consistent', 'type3-fontname-consistent'}, set(controls))
            original = (ROOT / 'capabilities/profiles/T13-text/fixtures/embedded-font-kinds.pdf').read_bytes()
            self.assertEqual(original, (outputs[0] / 'fixtures/positive-direct-fonts.pdf').read_bytes())
            inherited = (outputs[0] / 'fixtures/positive-inherited-fonts.pdf').read_bytes()
            self.assertIn(b'/UseCMap 16 0 R', inherited)
            for rule, control in controls.items():
                data = (outputs[0] / control['path']).read_bytes()
                self.assertNotEqual(original, data)
                source = (outputs[0] / 'fixtures/positive-named-type3-fonts.pdf').read_bytes() if rule == 'type3-fontname-consistent' else original
                self.assertEqual(len(source), len(data))
                self.assertEqual(control['sha256'], hashlib.sha256(data).hexdigest())
                self.assertIn('ISO 32000', control['authority'])
                self.assertEqual('unqualified', control['qualification'])
                offset = int(data.rsplit(b'startxref\n', 1)[1].splitlines()[0])
                self.assertEqual(b'xref', data[offset:offset + 4])
                if rule.endswith('w2-triples'):
                    self.assertIn(b'/W2 [1 [-1000 250    ]]', data)
                elif rule == 'type3-fontname-consistent':
                    self.assertIn(b'/FontName /F2', data)
                elif rule == 'shared-fontname-consistent':
                    self.assertIn(b'/BaseFont /FolioT13Invalid', data)
                else:
                    self.assertIn(b'/FontName /FolioT13Invalid', data)
            refused = subprocess.run([sys.executable, str(SCRIPT), str(outputs[0])], capture_output=True, text=True)
            self.assertNotEqual(0, refused.returncode)
            self.assertEqual(files, {p.relative_to(outputs[0]): p.read_bytes() for p in outputs[0].rglob('*') if p.is_file()})


if __name__ == '__main__':
    unittest.main()
