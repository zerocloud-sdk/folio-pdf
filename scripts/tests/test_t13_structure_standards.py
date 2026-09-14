"""Qualify original logical-structure relationships through the public tool CLI."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'scripts/t13-program-standards.py'
SOURCE = ROOT / 'capabilities/profiles/T13-text/fixtures/marked-structure.pdf'


class T13StructureStandardsTest(unittest.TestCase):
    def test_retained_structure_controls_fail_their_declared_public_rules(self):
        authority = ROOT / 'capabilities/profiles/T13-standards'
        controls = json.loads((authority / 'structure-controls.json').read_text())
        self.assertEqual(23, len(controls))
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, entry in controls.items():
                with self.subTest(retained_control=name):
                    data = (authority / entry['path']).read_bytes()
                    self.assertEqual(entry['sha256'], hashlib.sha256(data).hexdigest())
                    self.observe(directory, name, data, 'fail', entry['rule'], entry['scope'])

    def test_appearance_programs_do_not_inherit_a_previous_page_invocation_font(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, invoked, explicit_font, expected in (
                    ('appearance-unknown-font', False, False, 'indeterminate'),
                    ('page-and-appearance-unknown-font', True, False, 'indeterminate'),
                    ('appearance-explicit-font', False, True, 'pass'),
                    ('page-and-appearance-explicit-font', True, True, 'pass')):
                with self.subTest(appearance_font_state=name):
                    page = b'/P<</MCID 0>>BDC EMC BT/F 10 Tf ET' + (b' /Fm Do' if invoked else b'')
                    form = b'BT' + (b'/F 10 Tf' if explicit_font else b'') + b'(A)Tj ET'
                    objects = self.structured_objects(page, form)
                    objects[2] = objects[2].replace(b'/Resources<<', b'/Resources<</Font<</F 10 0 R>>')
                    objects[4] = objects[4].replace(b'<</Type/MCR/Stm 7 0 R/MCID 0>>', b'<</Type/OBJR/Obj 7 0 R>>').replace(b'<</Type/OBJR/Obj 9 0 R>>', b'')
                    objects[6] = objects[6].replace(b'/StructParents 1', b'/StructParent 1').replace(b'/Resources<<>>', b'/Resources<</Font<</F 10 0 R>>>>')
                    objects[7] = objects[7].replace(b'1[5 0 R]', b'1 5 0 R ').replace(b'2 5 0 R', b'')
                    objects[8] = objects[8].replace(b'/StructParent 2', b'/AP<</N 7 0 R>>')
                    objects.append(b'<</Type/Font/Subtype/Type1/BaseFont/Courier>>')
                    self.observe(directory, name, self.pdf(objects), expected, scope='structure-content')

    def test_hidden_or_view_dependent_appearances_do_not_supply_qualified_invocation_counts(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, flags, invoked, expected in (
                    ('normal-appearance-only', 0, False, 'pass'),
                    ('normal-appearance-and-page', 0, True, 'fail'),
                    ('hidden-appearance-only', 2, False, 'indeterminate'),
                    ('hidden-appearance-and-page', 2, True, 'indeterminate'),
                    ('no-view-appearance-and-page', 32, True, 'indeterminate')):
                with self.subTest(appearance_visibility=name):
                    page = b'/P<</MCID 0>>BDC EMC' + (b' /Fm Do' if invoked else b'')
                    objects = self.structured_objects(page, b'/P<</MCID 0>>BDC EMC')
                    objects[4] = objects[4].replace(b'<</Type/OBJR/Obj 9 0 R>>', b'')
                    objects[7] = objects[7].replace(b'2 5 0 R', b'')
                    objects[8] = b'<</Type/Annot/Subtype/Text/P 3 0 R/Rect[0 0 10 10]/AP<</N 7 0 R>>/F ' + str(flags).encode('ascii') + b'>>'
                    self.observe(directory, name, self.pdf(objects), expected,
                                 'structure-content' if expected == 'fail' else None, scope='structure-content')

    def test_discovered_appearance_owners_agree_with_their_page_backlink(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, page_entry, expected in (
                    ('discovered-owner-correct-page', b'/P 3 0 R', 'pass'),
                    ('discovered-owner-no-page', b'', 'pass'),
                    ('discovered-owner-wrong-page', b'/P 2 0 R', 'fail')):
                with self.subTest(discovered_owner=name):
                    objects = self.structured_objects(b'/P<</MCID 0>>BDC EMC', b'/P<</MCID 0>>BDC EMC')
                    objects[4] = objects[4].replace(b'<</Type/OBJR/Obj 9 0 R>>', b'')
                    objects[7] = objects[7].replace(b'2 5 0 R', b'')
                    objects[8] = objects[8].replace(b'/StructParent 2', b'/AP<</N 7 0 R>>').replace(b'/P 3 0 R', page_entry).replace(b'/F 2', b'/F 0')
                    self.observe(directory, name, self.pdf(objects), expected,
                                 'structure-content' if expected == 'fail' else None, scope='structure-content')

    def test_unobserved_form_page_associations_and_annotation_leaf_overlap_remain_unqualified(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, whole, appearance, annotation_item, expected in (
                    ('appearance-only-mcr', False, True, False, 'pass'),
                    ('appearance-only-whole-form', True, True, False, 'pass'),
                    ('unobserved-mcr-association', False, False, False, 'indeterminate'),
                    ('unobserved-whole-form-association', True, False, False, 'indeterminate'),
                    ('whole-annotation-and-internal-appearance-item', False, True, True, 'indeterminate')):
                with self.subTest(qualified_association=name):
                    objects = self.structured_objects(b'/P<</MCID 0>>BDC EMC', b'/P<</MCID 0>>BDC EMC')
                    objects[2] = objects[2].replace(b'/XObject<</Fm 7 0 R>>', b'')
                    if not annotation_item:
                        objects[4] = objects[4].replace(b'<</Type/OBJR/Obj 9 0 R>>', b'')
                        objects[7] = objects[7].replace(b'2 5 0 R', b'')
                        objects[8] = objects[8].replace(b'/StructParent 2', b'')
                    if appearance:
                        objects[8] = objects[8][:-2] + b'/AP<</N 7 0 R>>>>'
                    if whole:
                        objects[4] = objects[4].replace(b'<</Type/MCR/Stm 7 0 R/MCID 0>>', b'<</Type/OBJR/Obj 7 0 R>>')
                        objects[6] = objects[6].replace(b'/StructParents 1', b'/StructParent 1')
                        objects[7] = objects[7].replace(b'1[5 0 R]', b'1 5 0 R ')
                    self.observe(directory, name, self.pdf(objects), expected, scope='structure-content')

    def test_normal_appearance_roots_are_observed_without_their_own_structure_reference(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, program, linked, expected in (
                    ('unreferenced-parent-single-child', b'/Child Do', True, 'pass'),
                    ('unreferenced-parent-repeated-child', b'/Child Do /Child Do', True, 'fail'),
                    ('unreferenced-parent-unlinked-child', b'/Child Do /Child Do', False, 'pass'),
                    ('unreferenced-parent-unknown-program', b'99 unknown', True, 'indeterminate')):
                with self.subTest(appearance_root=name):
                    objects = self.structured_objects(b'/P<</MCID 0>>BDC EMC', b'/P<</MCID 0>>BDC EMC')
                    objects[2] = objects[2].replace(b'/XObject<</Fm 7 0 R>>', b'')
                    objects[4] = objects[4].replace(b'<</Type/OBJR/Obj 9 0 R>>', b'')
                    objects[7] = b'<</Nums[0[5 0 R]1[5 0 R]]>>'
                    objects[8] = objects[8].replace(b'/StructParent 2', b'/AP<</N 10 0 R>>')
                    if not linked:
                        objects[4] = objects[4].replace(b'<</Type/MCR/Stm 7 0 R/MCID 0>>', b'')
                        objects[6] = objects[6].replace(b'/StructParents 1', b'')
                        objects[7] = objects[7].replace(b'1[5 0 R]', b'')
                    form = b'<</Type/XObject/Subtype/Form/BBox[0 0 10 10]/Resources<</XObject<</Child 7 0 R>>>>'
                    objects.append(form + ('/Length %d>>\nstream\n' % len(program)).encode('ascii') + program + b'\nendstream')
                    self.observe(directory, name, self.pdf(objects), expected,
                                 'structure-content' if expected == 'fail' else None, scope='structure-content')

    def test_alternative_annotation_appearances_do_not_prove_simultaneous_structured_invocations(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, appearance, second_owner, expected in (
                    ('normal-rollover-alternatives', b'/N 10 0 R/R 11 0 R', False, 'indeterminate'),
                    ('normal-down-alternatives', b'/N 10 0 R/D 11 0 R', False, 'indeterminate'),
                    ('normal-state-alternatives', b'/N<</On 10 0 R/Off 11 0 R>>', False, 'indeterminate'),
                    ('separate-normal-owners', b'/N 10 0 R', True, 'fail')):
                with self.subTest(appearance_alternatives=name):
                    objects = self.structured_objects(b'/P<</MCID 0>>BDC EMC', b'/P<</MCID 0>>BDC EMC')
                    root_items = b'<</Type/MCR/Stm 10 0 R/StmOwn 9 0 R/MCID 0>>'
                    root_items += b'<</Type/MCR/Stm 11 0 R/StmOwn ' + (b'12' if second_owner else b'9') + b' 0 R/MCID 0>>'
                    objects[4] = objects[4].replace(b'<</Type/OBJR/Obj 9 0 R>>', root_items)
                    objects[7] = b'<</Nums[0[5 0 R]1[5 0 R]3[5 0 R]4[5 0 R]]>>'
                    objects[3] = objects[3].replace(b'/ParentTreeNextKey 3', b'/ParentTreeNextKey 5')
                    objects[8] = objects[8].replace(b'/StructParent 2', b'/AP<<' + appearance + b'>>')
                    program = b'/P<</MCID 0>>BDC EMC /Child Do'
                    for key in (3, 4):
                        form = b'<</Type/XObject/Subtype/Form/BBox[0 0 10 10]/Resources<</XObject<</Child 7 0 R>>>>'
                        objects.append(form + ('/StructParents %d/Length %d>>\nstream\n' % (key, len(program))).encode('ascii') + program + b'\nendstream')
                    if second_owner:
                        objects[2] = objects[2].replace(b'/Annots[9 0 R]', b'/Annots[9 0 R 12 0 R]')
                        objects.append(b'<</Type/Annot/Subtype/Text/P 3 0 R/Rect[0 0 0 0]/F 0/AP<</N 11 0 R>>>>')
                    self.observe(directory, name, self.pdf(objects), expected,
                                 'structure-content' if expected == 'fail' else None, scope='structure-content')

    def test_whole_form_references_cover_every_observed_rendering_page(self):
        def stream(program):
            return ('<</Length %d>>\nstream\n' % len(program)).encode('ascii') + program + b'\nendstream'
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, page_b, declared, repeat, expected in (
                    ('render-a-declare-a', False, (3,), False, 'pass'),
                    ('render-a-declare-b', False, (9,), False, 'fail'),
                    ('render-both-declare-a', True, (3,), False, 'fail'),
                    ('render-both-declare-both', True, (3, 9), False, 'pass'),
                    ('same-page-repeat-one-reference', False, (3,), True, 'pass')):
                with self.subTest(rendering_page=name):
                    children = b''.join(b'<</Type/OBJR/Pg ' + str(page).encode('ascii') + b' 0 R/Obj 7 0 R>>' for page in declared)
                    objects = [b'<</Type/Catalog/Pages 2 0 R/StructTreeRoot 4 0 R>>',
                               b'<</Type/Pages/Count 2/Kids[3 0 R 9 0 R]>>',
                               b'<</Type/Page/Parent 2 0 R/MediaBox[0 0 100 100]/Resources<</XObject<</Fm 7 0 R>>>>/Contents 6 0 R>>',
                               b'<</Type/StructTreeRoot/K 5 0 R/ParentTree 8 0 R>>',
                               b'<</S/P/P 4 0 R/K[' + children + b']>>',
                               stream(b'/Fm Do' + (b' /Fm Do' if repeat else b'')),
                               b'<</Type/XObject/Subtype/Form/BBox[0 0 10 10]/Resources<<>>/StructParent 0/Length 3>>\nstream\nq Q\nendstream',
                               b'<</Nums[0 5 0 R]>>',
                               b'<</Type/Page/Parent 2 0 R/MediaBox[0 0 100 100]/Resources<</XObject<</Fm 7 0 R>>>>/Contents 10 0 R>>',
                               stream(b'/Fm Do' if page_b else b'')]
                    self.observe(directory, name, self.pdf(objects), expected,
                                 'structure-content' if expected == 'fail' else None, scope='structure-content')

    def test_whole_form_identity_uses_stream_kind_with_optional_xobject_type(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, invoked, kind, expected in (
                    ('executed-typed-form', True, 'typed', 'pass'),
                    ('executed-optional-type', True, 'omitted', 'pass'),
                    ('definition-typed-form', False, 'typed', 'pass'),
                    ('definition-optional-type', False, 'omitted', 'pass'),
                    ('dictionary-masquerading-as-image', False, 'dictionary', 'indeterminate')):
                with self.subTest(object_identity=name):
                    page = b'/P<</MCID 0>>BDC EMC' + (b' /Fm Do' if invoked else b'')
                    objects = self.structured_objects(page, b'q Q')
                    objects[4] = objects[4].replace(b'<</Type/MCR/Stm 7 0 R/MCID 0>>', b'<</Type/OBJR/Obj 7 0 R>>')
                    objects[6] = objects[6].replace(b'/StructParents 1', b'/StructParent 1')
                    objects[7] = objects[7].replace(b'1[5 0 R]', b'1 5 0 R ')
                    if not invoked and kind != 'dictionary':
                        objects[4] = objects[4].replace(b'<</Type/OBJR/Obj 9 0 R>>', b'')
                        objects[7] = objects[7].replace(b'2 5 0 R', b'')
                        objects[8] = objects[8].replace(b'/StructParent 2', b'/AP<</N 7 0 R>>')
                    if kind == 'omitted':
                        objects[6] = objects[6].replace(b'/Type/XObject', b'')
                    elif kind == 'dictionary':
                        objects[6] = b'<</Type/XObject/Subtype/Image/StructParent 1>>'
                    self.observe(directory, name, self.pdf(objects), expected, scope='structure-content')

    def test_referenced_appearance_definitions_cannot_repeat_a_structured_descendant(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, repeats, linked, root_number, expected in (
                    ('one-structured-descendant', 1, True, 10, 'pass'),
                    ('two-structured-descendants', 2, True, 10, 'fail'),
                    ('two-unlinked-descendants', 2, False, 10, 'pass'),
                    ('definition-discovery-order', 1, True, 80, 'pass')):
                with self.subTest(appearance_calls=name):
                    objects = self.structured_objects(b'/P<</MCID 0>>BDC EMC', b'/P<</MCID 0>>BDC EMC')
                    reference = ('%d 0 R' % root_number).encode('ascii')
                    root_mcr = b'<</Type/MCR/Stm ' + reference + b'/StmOwn 9 0 R/MCID 0>>'
                    objects[4] = objects[4].replace(b'/K[0', b'/K[0' + root_mcr).replace(b'<</Type/OBJR/Obj 9 0 R>>', b'')
                    objects[7] = b'<</Nums[0[5 0 R]1[5 0 R]3[5 0 R]]>>'
                    objects[3] = objects[3].replace(b'/ParentTreeNextKey 3', b'/ParentTreeNextKey 4')
                    objects[8] = objects[8].replace(b'/StructParent 2', b'/AP<</N ' + reference + b'>>')
                    if not linked:
                        objects[4] = objects[4].replace(b'<</Type/MCR/Stm 7 0 R/MCID 0>>', b'')
                        objects[6] = objects[6].replace(b'/StructParents 1', b'')
                        objects[7] = objects[7].replace(b'1[5 0 R]', b'')
                    program = b'/P<</MCID 0>>BDC EMC' + b' /Child Do' * repeats
                    root_form = b'<</Type/XObject/Subtype/Form/BBox[0 0 10 10]/Resources<</XObject<</Child 7 0 R>>>>/StructParents 3'
                    root_form += ('/Length %d>>\nstream\n' % len(program)).encode('ascii') + program + b'\nendstream'
                    objects.extend([b'null'] * (root_number - len(objects) - 1)); objects.append(root_form)
                    self.observe(directory, name, self.pdf(objects), expected,
                                 'structure-content' if expected == 'fail' else None, scope='structure-content')

    def test_unexecuted_referenced_forms_are_qualified_from_their_definitions(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, form, expected in (
                    ('unexecuted-form', b'/P<</MCID 0>>BDC EMC', 'indeterminate'),
                    ('unexecuted-appearance', b'/P<</MCID 0>>BDC EMC', 'pass'),
                    ('unexecuted-missing-mcid', b'/P<<>>BDC EMC', 'fail'),
                    ('unexecuted-different-mcid', b'/P<</MCID 1>>BDC EMC', 'fail'),
                    ('unexecuted-font-state', b'/P<</MCID 0>>BDC BT(A)Tj ET EMC', 'indeterminate'),
                    ('unexecuted-whole-form', b'q Q', 'indeterminate'),
                    ('unexecuted-whole-unsupported-program', b'99 unknown', 'indeterminate')):
                with self.subTest(definition=name):
                    objects = self.structured_objects(b'/P<</MCID 0>>BDC EMC', form)
                    if name == 'unexecuted-appearance':
                        objects[4] = objects[4].replace(b'<</Type/OBJR/Obj 9 0 R>>', b'').replace(
                            b'/Stm 7 0 R', b'/Stm 7 0 R/StmOwn 9 0 R')
                        objects[7] = b'<</Nums[0[5 0 R]1[5 0 R]]>>'
                        objects[8] = objects[8].replace(b'/StructParent 2', b'/AP<</N 7 0 R>>')
                    if name.startswith('unexecuted-whole'):
                        objects[4] = objects[4].replace(b'<</Type/MCR/Stm 7 0 R/MCID 0>>', b'<</Type/OBJR/Obj 7 0 R>>')
                        objects[6] = objects[6].replace(b'/StructParents 1', b'/StructParent 1')
                        objects[7] = objects[7].replace(b'1[5 0 R]', b'1 5 0 R ')
                    self.observe(directory, name, self.pdf(objects), expected,
                                 'structure-content' if expected == 'fail' else None, scope='structure-content')

    def test_stream_owners_identify_an_appearance_of_an_annotation_on_the_declared_page(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, expected in (('normal-appearance', 'pass'), ('state-appearance', 'pass'),
                                     ('owner-optional-type', 'pass'), ('owner-optional-null', 'pass'),
                                     ('wrong-appearance', 'fail'), ('missing-appearance', 'fail'),
                                     ('owner-not-on-page', 'fail'), ('owner-page-backlink', 'fail'),
                                     ('owner-without-stream', 'fail')):
                with self.subTest(stream_owner=name):
                    objects = self.structured_objects(b'/P<</MCID 0>>BDC EMC', b'/P<</MCID 0>>BDC EMC')
                    objects[4] = objects[4].replace(b'<</Type/OBJR/Obj 9 0 R>>', b'').replace(
                        b'/Stm 7 0 R', b'/Stm 7 0 R/StmOwn 9 0 R')
                    objects[7] = b'<</Nums[0[5 0 R]1[5 0 R]]>>'
                    objects[8] = objects[8].replace(b'/StructParent 2', b'/AP<</N 7 0 R>>')
                    if name == 'state-appearance':
                        objects[8] = objects[8].replace(b'/AP<</N 7 0 R>>', b'/AP<</N<</On 7 0 R>>>>')
                    elif name == 'owner-optional-type':
                        objects[8] = objects[8].replace(b'/Type/Annot', b'')
                    elif name == 'owner-optional-null':
                        objects[4] = objects[4].replace(b'/StmOwn 9 0 R', b'/StmOwn null')
                    elif name == 'wrong-appearance':
                        objects[8] = objects[8].replace(b'/N 7 0 R', b'/N 6 0 R')
                    elif name == 'missing-appearance':
                        objects[8] = objects[8].replace(b'/AP<</N 7 0 R>>', b'')
                    elif name == 'owner-not-on-page':
                        objects[2] = objects[2].replace(b'/Annots[9 0 R]', b'')
                    elif name == 'owner-page-backlink':
                        objects[8] = objects[8].replace(b'/P 3 0 R', b'/P 2 0 R')
                    elif name == 'owner-without-stream':
                        objects[4] = objects[4].replace(b'/Stm 7 0 R', b'')
                    self.observe(directory, name, self.pdf(objects), expected,
                                 'structure-content' if expected == 'fail' else None, scope='structure-content')

    def test_structural_pages_and_annotation_objects_belong_to_the_declared_document_page(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, expected in (('matching-page-and-annotation', 'pass'), ('annotation-optional-p', 'pass'),
                                     ('annotation-missing-from-page', 'fail'), ('annotation-p-mismatch', 'fail'),
                                     ('detached-page-reference', 'fail')):
                with self.subTest(page_membership=name):
                    objects = self.structured_objects(b'/P<</MCID 0>>BDC EMC /Fm Do', b'/P<</MCID 0>>BDC EMC')
                    if name == 'annotation-optional-p':
                        objects[8] = objects[8].replace(b'/P 3 0 R', b'')
                    elif name == 'annotation-missing-from-page':
                        objects[2] = objects[2].replace(b'/Annots[9 0 R]', b'')
                    elif name == 'annotation-p-mismatch':
                        objects[8] = objects[8].replace(b'/P 3 0 R', b'/P 2 0 R')
                    elif name == 'detached-page-reference':
                        objects = [b'<</Type/Catalog/Pages 2 0 R/StructTreeRoot 4 0 R>>',
                                   b'<</Type/Pages/Count 1/Kids[3 0 R]>>',
                                   b'<</Type/Page/Parent 2 0 R/MediaBox[0 0 100 100]/Resources<<>>>>',
                                   b'<</Type/StructTreeRoot/K 5 0 R>>', b'<</S/P/P 4 0 R/Pg 6 0 R>>',
                                   b'<</Type/Page/Parent 2 0 R/MediaBox[0 0 100 100]/Resources<<>>>>']
                    self.observe(directory, name, self.pdf(objects), expected,
                                 'structure-content' if expected == 'fail' else None, scope='structure-content')

    def test_structural_content_cannot_invoke_other_structural_form_content(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, enclosed, linked_parent, whole_form, transitive, expected in (
                    ('separate-items', False, True, False, False, 'pass'),
                    ('marked-item-invokes-structured-form', True, True, False, False, 'fail'),
                    ('unlinked-marked-parent', True, False, False, False, 'pass'),
                    ('separate-whole-form', False, True, True, False, 'pass'),
                    ('marked-item-invokes-whole-form', True, True, True, False, 'fail'),
                    ('transitive-structured-form-invocation', True, True, False, True, 'fail')):
                with self.subTest(content_invocation=name):
                    page = b'/P<</MCID 0>>BDC /Fm Do EMC' if enclosed else b'/P<</MCID 0>>BDC EMC /Fm Do'
                    objects = self.structured_objects(page, b'q Q' if whole_form else b'/P<</MCID 0>>BDC EMC')
                    if whole_form:
                        objects[4] = objects[4].replace(b'<</Type/MCR/Stm 7 0 R/MCID 0>>', b'<</Type/OBJR/Obj 7 0 R>>')
                        objects[6] = objects[6].replace(b'/StructParents 1', b'/StructParent 1')
                        objects[7] = objects[7].replace(b'1[5 0 R]', b'1 5 0 R ')
                    if not linked_parent:
                        objects[4] = objects[4].replace(b'/K[0', b'/K[')
                        objects[2] = objects[2].replace(b'/StructParents 0', b'')
                        objects[7] = objects[7].replace(b'0[5 0 R]', b'')
                    if transitive:
                        objects[2] = objects[2].replace(b'/Fm 7 0 R', b'/Fm 10 0 R')
                        objects.append(b'<</Type/XObject/Subtype/Form/BBox[0 0 10 10]/Resources<</XObject<</In 7 0 R>>>>/Length 6>>\nstream\n/In Do\nendstream')
                    self.observe(directory, name, self.pdf(objects), expected,
                                 'structure-content' if expected == 'fail' else None, scope='structure-content')

    def test_structural_marked_content_is_leaf_content_in_either_k_order(self):
        page = b'/P<</MCID 0>>BDC /Span<</MCID 1>>BDC EMC EMC /Fm Do'
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, child_ids, slots, expected in (
                    ('outer-claim-only', b'0', b'[5 0 R]', 'pass'),
                    ('inner-claim-only', b'1', b'[null 5 0 R]', 'pass'),
                    ('nested-claims', b'0 1', b'[5 0 R 5 0 R]', 'fail'),
                    ('reversed-nested-claims', b'1 0', b'[5 0 R 5 0 R]', 'fail')):
                with self.subTest(leaf_content=name):
                    objects = self.structured_objects(page, b'/P<</MCID 0>>BDC EMC')
                    objects[4] = objects[4].replace(b'/K[0', b'/K[' + child_ids)
                    objects[7] = b'<</Nums[0' + slots + b'1[5 0 R]2 5 0 R]>>'
                    self.observe(directory, name, self.pdf(objects), expected,
                                 'structure-content' if expected == 'fail' else None, scope='structure-content')

    def test_forms_with_structural_mcids_have_one_invocation_while_unlinked_repetitions_remain_valid(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, repeat, linked, repeated_object, expected in (
                    ('single-structured-form', False, True, False, 'pass'),
                    ('repeated-structured-form', True, True, False, 'fail'),
                    ('repeated-unlinked-form', True, False, False, 'pass'),
                    ('repeated-whole-annotation-reference', False, True, True, 'pass')):
                with self.subTest(invocation=name):
                    page = b'/P<</MCID 0>>BDC EMC /Fm Do' + (b' /Fm Do' if repeat else b'')
                    objects = self.structured_objects(page, b'/P<</MCID 0>>BDC EMC')
                    if not linked:
                        objects[4] = objects[4].replace(b'<</Type/MCR/Stm 7 0 R/MCID 0>>', b'')
                        objects[6] = objects[6].replace(b'/StructParents 1', b'')
                        objects[7] = b'<</Nums[0[5 0 R]2 5 0 R]>>'
                    if repeated_object:
                        objects[4] = objects[4].replace(b'<</Type/OBJR/Obj 9 0 R>>', b'<</Type/OBJR/Obj 9 0 R>>' * 2)
                    self.observe(directory, name, self.pdf(objects), expected,
                                 'structure-content' if expected == 'fail' else None, scope='structure-content')

    def structured_objects(self, page_program, form_program):
        def stream(dictionary, program):
            return dictionary[:-2] + ('/Length %d>>\nstream\n' % len(program)).encode('ascii') + program + b'\nendstream'
        return [b'<</Type/Catalog/Pages 2 0 R/StructTreeRoot 4 0 R>>',
                b'<</Type/Pages/Count 1/Kids[3 0 R]>>',
                b'<</Type/Page/Parent 2 0 R/MediaBox[0 0 100 100]/Resources<</XObject<</Fm 7 0 R>>>>/Contents 6 0 R/StructParents 0/Annots[9 0 R]>>',
                b'<</Type/StructTreeRoot/K 5 0 R/ParentTree 8 0 R/ParentTreeNextKey 3>>',
                b'<</Type/StructElem/S/P/P 4 0 R/Pg 3 0 R/K[0<</Type/MCR/Stm 7 0 R/MCID 0>><</Type/OBJR/Obj 9 0 R>>]>>',
                stream(b'<<>>', page_program),
                stream(b'<</Type/XObject/Subtype/Form/BBox[0 0 10 10]/Resources<<>>/StructParents 1>>', form_program),
                b'<</Nums[0[5 0 R]1[5 0 R]2 5 0 R]>>',
                b'<</Type/Annot/Subtype/Text/P 3 0 R/Rect[0 0 0 0]/F 0/StructParent 2>>']

    def test_structural_mcids_must_exist_in_the_declared_page_or_form_content(self):
        original = SOURCE.read_bytes()
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            self.observe(directory, 'declared-sequences-exist', original, 'pass', scope='structure-content')
            for name, number, after in (('page-different-mcid', 4, b'/MCID 1'),
                                         ('form-different-mcid', 7, b'/MCID 1'),
                                         ('page-without-mcid', 4, b'/XXID 0'),
                                         ('form-without-mcid', 7, b'/XXID 0')):
                with self.subTest(definition=name):
                    self.observe(directory, name, self.change(original, number, b'/MCID 0', after),
                                 'fail', 'structure-content', scope='structure-content')

    def test_structural_content_backlinks_match_each_mcid_and_whole_object_parent(self):
        original = SOURCE.read_bytes()
        nums = b'/Nums [0 [10 0 R] 1 [10 0 R] 2 10 0 R]'
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            self.observe(directory, 'matching-content-backlinks', original, 'pass', scope='structure-content')
            for name, number, before, after in (
                    ('missing-page-key', 3, b'/StructParents 0', b''),
                    ('missing-form-key', 7, b'/StructParents 1', b''),
                    ('missing-object-key', 12, b'/StructParent 2', b''),
                    ('unknown-page-key', 3, b'/StructParents 0', b'/StructParents 9'),
                    ('named-page-key', 3, b'/StructParents 0', b'/StructParents/X'),
                    ('real-page-key', 3, b'/StructParents 0 ', b'/StructParents .0'),
                    ('shared-page-form-key', 7, b'/StructParents 1', b'/StructParents 0'),
                    ('wrong-page-parent', 13, nums, b'/Nums [0 [11 0 R] 1 [10 0 R] 2 10 0 R]'),
                    ('wrong-form-parent', 13, nums, b'/Nums [0 [10 0 R] 1 [11 0 R] 2 10 0 R]'),
                    ('wrong-object-parent', 13, nums, b'/Nums [0 [10 0 R] 1 [10 0 R] 2 11 0 R]'),
                    ('missing-mcid-parent', 13, nums, b'/Nums [0 [null] 1 [10 0 R] 2 10 0 R]'),
                    ('page-parent-not-array', 13, nums, b'/Nums [0 10 0 R 1 [10 0 R] 2 10 0 R]'),
                    ('object-parent-not-reference', 13, nums, b'/Nums[0[10 0 R]1[10 0 R]2[10 0 R]]'),
                    ('page-mcid-beyond-array', 10, b'/K [0 ', b'/K [1 '),
                    ('form-mcid-beyond-array', 10, b'/MCID 0', b'/MCID 1'),
                    ('both-page-parent-keys', 3, b'/StructParents 0 /Annots [12 0 R]', b'/StructParents 0/StructParent 0')):
                with self.subTest(backlink=name):
                    self.observe(directory, name, self.change(original, number, before, after), 'fail',
                                 'structure-content', scope='structure-content')

    def test_content_references_have_typed_page_stream_object_and_mcid_fields(self):
        original = SOURCE.read_bytes()
        children = b'/K [0 << /Type /MCR /Pg 3 0 R /Stm 7 0 R /MCID 0 >> << /Type /OBJR /Pg 3 0 R /Obj 12 0 R >> 11 0 R]'
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for path in sorted(SOURCE.parent.glob('*.pdf')):
                with self.subTest(source=path.name):
                    self.observe(directory, 'source-' + path.stem, path.read_bytes(), 'pass', scope='structure-content')
            for name, child, expected in (
                    ('mcr-inherited-page', b'<</Type/MCR/Stm 7 0 R/MCID 0>>', 'pass'),
                    ('mcr-page-contents', b'<</Type/MCR/MCID 0>>', 'pass'),
                    ('mcr-missing-id', b'<</Type/MCR/Stm 7 0 R>>', 'fail'),
                    ('mcr-name-id', b'<</Type/MCR/Stm 7 0 R/MCID /N>>', 'fail'),
                    ('mcr-real-id', b'<</Type/MCR/Stm 7 0 R/MCID 0.5>>', 'fail'),
                    ('mcr-negative-id', b'<</Type/MCR/Stm 7 0 R/MCID -1>>', 'fail'),
                    ('mcr-boolean-id', b'<</Type/MCR/Stm 7 0 R/MCID true>>', 'fail'),
                    ('mcr-numeric-page', b'<</Type/MCR/Pg 3/MCID 0>>', 'fail'),
                    ('mcr-nonpage-reference', b'<</Type/MCR/Pg 12 0 R/MCID 0>>', 'fail'),
                    ('mcr-numeric-stream', b'<</Type/MCR/Stm 7/MCID 0>>', 'fail'),
                    ('mcr-dictionary-stream', b'<</Type/MCR/Stm 12 0 R/MCID 0>>', 'fail'),
                    ('mcr-numeric-owner', b'<</Type/MCR/Stm 7 0 R/StmOwn 12/MCID 0>>', 'fail'),
                    ('objr-inherited-page', b'<</Type/OBJR/Obj 12 0 R>>', 'pass'),
                    ('objr-missing-object', b'<</Type/OBJR>>', 'fail'),
                    ('objr-direct-object', b'<</Type/OBJR/Obj<<>>>>', 'fail'),
                    ('objr-null-object', b'<</Type/OBJR/Obj null>>', 'fail'),
                    ('objr-nonpage-reference', b'<</Type/OBJR/Pg 12 0 R/Obj 12 0 R>>', 'fail')):
                with self.subTest(content_reference=name):
                    self.observe(directory, name, self.change(original, 10, children, b'/K ' + child), expected,
                                 'structure-content' if expected == 'fail' else None, scope='structure-content')
            for name, number, before, after in (
                    ('element-nonpage-reference', 9, b'/Pg 3 0 R /Lang (fr)', b'/Pg 12 0 R/Lang(fr)'),
                    ('element-direct-page', 11, b'/Pg 3 0 R', b'/Pg<<>>')):
                with self.subTest(content_reference=name):
                    self.observe(directory, name, self.change(original, number, before, after), 'fail',
                                 'structure-content', scope='structure-content')

    def test_namespace_qualification_bounds_include_root_role_maps_without_namespaces(self):
        objects = [b'<</Type/Catalog/Pages 2 0 R/StructTreeRoot 4 0 R>>',
                   b'<</Type/Pages/Count 1/Kids[3 0 R]>>',
                   b'<</Type/Page/Parent 2 0 R/MediaBox[0 0 100 100]/Resources<<>>>>', b'']
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for count, expected in ((10000, 'pass'), (10001, 'indeterminate')):
                with self.subTest(role_map_entries=count):
                    mapping = b''.join(('/Role%d/P' % index).encode('ascii') for index in range(count))
                    objects[3] = b'<</Type/StructTreeRoot/RoleMap<<' + mapping + b'>>>>'
                    self.observe(directory, 'entries-' + str(count), self.pdf(objects), expected, scope='structure-namespaces')
            for count, expected in ((1000, 'pass'), (1001, 'indeterminate')):
                with self.subTest(namespace_count=count):
                    namespaces = b'<</NS(urn:folio:bound)>>' * count
                    objects[3] = b'<</Type/StructTreeRoot/Namespaces[' + namespaces + b']>>'
                    self.observe(directory, 'namespaces-' + str(count), self.pdf(objects), expected, scope='structure-namespaces')

    def test_structure_root_and_parent_edges_preserve_required_indirect_identity(self):
        objects = [b'<</Type/Catalog/Pages 2 0 R/StructTreeRoot 4 0 R>>',
                   b'<</Type/Pages/Count 1/Kids[3 0 R]>>',
                   b'<</Type/Page/Parent 2 0 R/MediaBox[0 0 100 100]/Resources<<>>>>',
                   b'<</Type/StructTreeRoot/K 5 0 R>>', b'<</S/P/P 4 0 R>>']
        empty = b'<</Type/StructTreeRoot>>'
        root = b'<</Type/StructTreeRoot/K 5 0 R>>'
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, header, catalog, parent, expected in (
                    ('indirect-identities', b'%PDF-2.0', objects[0], b'4 0 R', 'pass'),
                    ('pdf2-direct-root', b'%PDF-2.0', b'<</Type/Catalog/Pages 2 0 R/StructTreeRoot' + empty + b'>>', b'4 0 R', 'fail'),
                    ('legacy-direct-empty-root', b'%PDF-1.7', b'<</Type/Catalog/Pages 2 0 R/StructTreeRoot' + empty + b'>>', b'4 0 R', 'pass'),
                    ('catalog-overrides-header', b'%PDF-1.7', b'<</Type/Catalog/Pages 2 0 R/Version/2.0/StructTreeRoot' + empty + b'>>', b'4 0 R', 'fail'),
                    ('legacy-parent-value-copy', b'%PDF-1.7', b'<</Type/Catalog/Pages 2 0 R/StructTreeRoot' + root + b'>>', root, 'fail'),
                    ('parent-direct-value', b'%PDF-2.0', objects[0], root, 'fail'),
                    ('parent-wrong-identity', b'%PDF-2.0', objects[0], b'3 0 R', 'fail')):
                with self.subTest(identity=name):
                    changed = list(objects); changed[0] = catalog; changed[4] = b'<</S/P/P ' + parent + b'>>'
                    data = self.pdf(changed).replace(b'%PDF-2.0', header, 1)
                    self.observe(directory, name, data, expected, 'structure-hierarchy' if expected == 'fail' else None)

    def test_next_parent_tree_key_has_integer_type_even_without_content_or_a_parent_tree(self):
        objects = [b'<</Type/Catalog/Pages 2 0 R/StructTreeRoot 4 0 R>>',
                   b'<</Type/Pages/Count 1/Kids[3 0 R]>>',
                   b'<</Type/Page/Parent 2 0 R/MediaBox[0 0 100 100]/Resources<<>>>>',
                   b'<</Type/StructTreeRoot>>']
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, value, expected in (('name', b'/bad', 'fail'), ('real', b'0.5', 'fail'),
                                            ('boolean', b'false', 'fail'), ('integer', b'3', 'pass'),
                                            ('negative-integer', b'-1', 'pass'), ('null', b'null', 'pass')):
                with self.subTest(next_key=name):
                    objects[3] = b'<</Type/StructTreeRoot/ParentTreeNextKey ' + value + b'>>'
                    self.observe(directory, name, self.pdf(objects), expected,
                                 'structure-parent-tree' if expected == 'fail' else None, scope='structure-parent-tree')

    def test_element_child_arrays_are_nonempty_while_root_arrays_can_be_empty(self):
        objects = [b'<</Type/Catalog/Pages 2 0 R/StructTreeRoot 4 0 R>>',
                   b'<</Type/Pages/Count 1/Kids[3 0 R]>>',
                   b'<</Type/Page/Parent 2 0 R/MediaBox[0 0 100 100]/Resources<<>>>>',
                   b'<</Type/StructTreeRoot/K 5 0 R>>', b'<</S/P/P 4 0 R>>', b'[]']
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, number, body, expected in (
                    ('element-absent-k', 5, b'<</S/P/P 4 0 R>>', 'pass'),
                    ('element-null-k', 5, b'<</S/P/P 4 0 R/K null>>', 'pass'),
                    ('element-empty-k', 5, b'<</S/P/P 4 0 R/K[]>>', 'fail'),
                    ('element-indirect-empty-k', 5, b'<</S/P/P 4 0 R/K 6 0 R>>', 'fail'),
                    ('root-empty-k', 4, b'<</Type/StructTreeRoot/K[]>>', 'pass'),
                    ('root-indirect-empty-k', 4, b'<</Type/StructTreeRoot/K 6 0 R>>', 'pass')):
                with self.subTest(children=name):
                    changed = list(objects); changed[number - 1] = body
                    self.observe(directory, name, self.pdf(changed), expected,
                                 'structure-hierarchy' if expected == 'fail' else None)

    def test_structure_dictionary_positions_reject_stream_objects(self):
        objects = [b'<</Type/Catalog/Pages 2 0 R/StructTreeRoot 4 0 R>>',
                   b'<</Type/Pages/Count 1/Kids[3 0 R]>>',
                   b'<</Type/Page/Parent 2 0 R/MediaBox[0 0 100 100]/Resources<<>>>>',
                   b'<</Type/StructTreeRoot/K 5 0 R/ParentTree 7 0 R/Namespaces[9 0 R]/RoleMap 10 0 R>>',
                   b'<</Type/StructElem/S/P/P 4 0 R/K 6 0 R/NS 9 0 R>>',
                   b'<</Type/MCR/Pg 3 0 R/MCID 0>>',
                   b'<</Kids[8 0 R]>>', b'<</Nums[0[5 0 R]]/Limits[0 0]>>',
                   b'<</Type/Namespace/NS(urn:folio:test)/RoleMapNS 11 0 R>>',
                   b'<</Custom/P>>', b'<</Custom/P>>']
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for scope in ('structure-hierarchy', 'structure-parent-tree', 'structure-namespaces'):
                with self.subTest(ordinary_dictionary=scope):
                    self.observe(directory, 'ordinary-' + scope, self.pdf(objects), 'pass', scope=scope)
            for name, number, scope, rule in (
                    ('root', 4, 'structure-hierarchy', 'structure-hierarchy'),
                    ('element', 5, 'structure-hierarchy', 'structure-hierarchy'),
                    ('mcr', 6, 'structure-hierarchy', 'structure-hierarchy'),
                    ('objr', 6, 'structure-hierarchy', 'structure-hierarchy'),
                    ('parent-tree-root', 7, 'structure-parent-tree', 'structure-parent-tree'),
                    ('parent-tree-leaf', 8, 'structure-parent-tree', 'structure-parent-tree'),
                    ('namespace', 9, 'structure-namespaces', 'structure-namespace'),
                    ('role-map', 10, 'structure-namespaces', 'structure-namespace'),
                    ('namespace-role-map', 11, 'structure-namespaces', 'structure-namespace')):
                with self.subTest(stream=name):
                    changed = list(objects)
                    if name == 'objr':
                        changed[number - 1] = b'<</Type/OBJR/Pg 3 0 R/Obj 3 0 R>>'
                    changed[number - 1] = changed[number - 1][:-2] + b'/Length 0>>\nstream\n\nendstream'
                    self.observe(directory, name, self.pdf(changed), 'fail', rule, scope=scope)

    def pdf(self, objects):
        data = bytearray(b'%PDF-2.0\n'); offsets = []
        for number, body in enumerate(objects, 1):
            offsets.append(len(data))
            data.extend(('%d 0 obj\n' % number).encode('ascii') + body + b'\nendobj\n')
        xref = len(data)
        data.extend(('xref\n0 %d\n0000000000 65535 f \n' % (len(objects) + 1)).encode('ascii'))
        for offset in offsets:
            data.extend(('%010d 00000 n \n' % offset).encode('ascii'))
        data.extend(('trailer\n<</Root 1 0 R/Size %d>>\nstartxref\n%d\n%%%%EOF\n' %
                     (len(objects) + 1, xref)).encode('ascii'))
        return bytes(data)

    def test_namespaces_bind_element_identifiers_and_typed_role_map_destinations(self):
        original = SOURCE.read_bytes()
        namespaces = b'/Namespaces [14 0 R 15 0 R 16 0 R]'
        mapping = b'/RoleMapNS << /Custom [/Intermediate 16 0 R] >>'
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for path in sorted(SOURCE.parent.glob('*.pdf')):
                with self.subTest(source=path.name):
                    self.observe(directory, 'source-' + path.stem, path.read_bytes(), 'pass', scope='structure-namespaces')
            for name, number, before, after, expected in (
                    ('missing-namespace-array', 8, namespaces, b'', 'fail'),
                    ('invalid-namespace-array', 8, namespaces, b'/Namespaces /Other', 'fail'),
                    ('unlisted-element-namespace', 8, namespaces, b'/Namespaces [15 0 R 16 0 R]', 'fail'),
                    ('numeric-element-namespace', 9, b'/NS 14 0 R', b'/NS 1234', 'fail'),
                    ('non-namespace-dictionary', 9, b'/NS 14 0 R', b'/NS 12 0 R', 'fail'),
                    ('namespace-type', 14, b'/Type /Namespace', b'/Type /Other', 'fail'),
                    ('namespace-optional-type', 14, b'/Type /Namespace', b'', 'pass'),
                    ('namespace-name-type', 14, b'/NS (urn:folio:t13:roles)', b'/NS 1234', 'fail'),
                    ('namespace-map-type', 14, mapping, b'/RoleMapNS 1234', 'fail'),
                    ('namespace-destination-type', 14, mapping, b'/RoleMapNS<</Custom 1234>>', 'fail'),
                    ('namespace-destination-arity', 14, mapping, b'/RoleMapNS<</Custom[/P]>>', 'fail'),
                    ('namespace-destination-role', 14, mapping, b'/RoleMapNS<</Custom[12 15 0 R]>>', 'fail'),
                    ('namespace-destination-identity', 14, mapping, b'/RoleMapNS<</Custom[/P 12 0 R]>>', 'fail'),
                    ('namespace-default-destination', 14, mapping, b'/RoleMapNS<</Custom/P>>', 'pass'),
                    ('root-role-map-type', 8, b'/ParentTreeNextKey 3', b'/RoleMap 1234', 'fail'),
                    ('root-role-destination', 8, b'/ParentTreeNextKey 3', b'/RoleMap<</X 123>>', 'fail'),
                    ('root-role-map-name', 8, b'/ParentTreeNextKey 3', b'/RoleMap<</X/P>>', 'pass')):
                with self.subTest(namespace=name):
                    self.observe(directory, name, self.change(original, number, before, after), expected,
                                 'structure-namespace' if expected == 'fail' else None, scope='structure-namespaces')
            before = b'/Type /Namespace /NS (urn:folio:t13:roles) ' + mapping
            after = b'/NS(http://iso.org/pdf2/ssn)/RoleMapNS<</P[/P 14 0 R]>>'
            self.observe(directory, 'direct-standard-namespace-remap', self.change(original, 14, before, after),
                         'fail', 'structure-namespace', scope='structure-namespaces')

    def test_parent_tree_child_ranges_match_ordered_disjoint_leaf_keys(self):
        original = SOURCE.read_bytes()
        nums = b'/Nums [0 [10 0 R] 1 [10 0 R] 2 10 0 R]'
        data = self.change(original, 13, nums, b'/Kids [18 0 R 19 0 R]')
        position = int(data.rsplit(b'startxref\n', 1)[1].splitlines()[0])
        leaf_a = b'18 0 obj\n<</Nums[0[10 0 R]1[10 0 R]]/Limits[0 1]>>\nendobj\n'
        leaf_b = b'19 0 obj\n<</Nums[2 10 0 R]/Limits[2 2]>>\nendobj\n'
        tail = data[position:]; self.assertTrue(tail.startswith(b'xref\n0 18\n'))
        tail = tail.replace(b'xref\n0 18\n', b'xref\n0 20\n', 1)
        extra = ('%010d 00000 n \n%010d 00000 n \n' % (position, position + len(leaf_a))).encode('ascii')
        tail = tail.replace(b'trailer\n', extra + b'trailer\n', 1).replace(b'/Size 18', b'/Size 20')
        tail = tail.replace(('startxref\n%d\n' % position).encode('ascii'),
                            ('startxref\n%d\n' % (position + len(leaf_a) + len(leaf_b))).encode('ascii'))
        tree = data[:position] + leaf_a + leaf_b + tail
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            with self.subTest(number_tree='multi-level'):
                self.observe(directory, 'multi-level', tree, 'pass', scope='structure-parent-tree')
            for name, number, before, after in (
                    ('missing-limits', 18, b'/Limits[0 1]', b''),
                    ('incorrect-limits', 18, b'/Limits[0 1]', b'/Limits[0 2]'),
                    ('overlapping-ranges', 19, b'/Nums[2 10 0 R]/Limits[2 2]', b'/Nums[1 10 0 R]/Limits[1 1]'),
                    ('unordered-children', 13, b'/Kids [18 0 R 19 0 R]', b'/Kids [19 0 R 18 0 R]'),
                    ('cyclic-tree', 13, b'/Kids [18 0 R 19 0 R]', b'/Kids [18 0 R 13 0 R]')):
                with self.subTest(number_tree=name):
                    self.observe(directory, name, self.change(tree, number, before, after),
                                 'fail', 'structure-parent-tree', scope='structure-parent-tree')
            for name, replacement in (('root-limits', b'/Kids[18 0 R 19 0 R]/Limits[0 2]'),
                                       ('mixed-kids-and-nums', b'/Kids[18 0 R 19 0 R]/Nums[]'),
                                       ('direct-tree-child', b'/Kids[<</Nums[]/Limits[0 0]>>]')):
                with self.subTest(number_tree=name):
                    self.observe(directory, name, self.change(tree, 13, b'/Kids [18 0 R 19 0 R]'.ljust(len(nums)), replacement),
                                 'fail', 'structure-parent-tree', scope='structure-parent-tree')

    def test_parent_tree_has_ordered_integer_keys_structural_values_and_a_fresh_next_key(self):
        original = SOURCE.read_bytes()
        nums = b'/Nums [0 [10 0 R] 1 [10 0 R] 2 10 0 R]'
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for path in sorted(SOURCE.parent.glob('*.pdf')):
                with self.subTest(source=path.name):
                    self.observe(directory, 'source-' + path.stem, path.read_bytes(), 'pass', scope='structure-parent-tree')
            for name, number, before, after, expected in (
                    ('missing-parent-tree', 8, b'/ParentTree 13 0 R', b'', 'fail'),
                    ('numeric-parent-tree', 8, b'/ParentTree 13 0 R', b'/ParentTree 1234', 'fail'),
                    ('missing-number-tree-entries', 13, nums, b'', 'fail'),
                    ('odd-key-values', 13, nums, b'/Nums [0]', 'fail'),
                    ('unordered-keys', 13, nums, b'/Nums [1 [10 0 R] 0 [10 0 R] 2 10 0 R]', 'fail'),
                    ('duplicate-keys', 13, nums, b'/Nums [0 [10 0 R] 0 [10 0 R] 2 10 0 R]', 'fail'),
                    ('boolean-key', 13, nums, b'/Nums [false 10 0 R]', 'fail'),
                    ('real-key', 13, nums, b'/Nums [0.0 10 0 R]', 'fail'),
                    ('numeric-parent-value', 13, nums, b'/Nums [0 42]', 'fail'),
                    ('non-element-parent-value', 13, nums, b'/Nums [0 [12 0 R]]', 'fail'),
                    ('used-next-key', 8, b'/ParentTreeNextKey 3', b'/ParentTreeNextKey 2', 'fail'),
                    ('name-next-key', 8, b'/ParentTreeNextKey 3', b'/ParentTreeNextKey/X', 'fail'),
                    ('absent-next-key', 8, b'/ParentTreeNextKey 3', b'', 'pass'),
                    ('unused-null-slot', 13, nums, b'/Nums[0[10 0 R null]1[10 0 R]2 10 0 R]', 'pass')):
                with self.subTest(parent_tree=name):
                    self.observe(directory, name, self.change(original, number, before, after), expected,
                                 'structure-parent-tree' if expected == 'fail' else None, scope='structure-parent-tree')

    def test_hierarchy_qualification_admits_exact_depth_and_item_bounds(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, count, nested, expected in (('depth-128', 128, True, 'pass'),
                                                   ('depth-129', 129, True, 'indeterminate'),
                                                   ('items-10000', 10000, False, 'pass'),
                                                   ('items-10001', 10001, False, 'indeterminate')):
                with self.subTest(boundary=name):
                    children = b'5 0 R' if nested else b'[' + b' '.join(
                        ('%d 0 R' % (5 + index)).encode('ascii') for index in range(count)) + b']'
                    objects = [b'<</Type/Catalog/Pages 2 0 R/StructTreeRoot 4 0 R>>',
                               b'<</Type/Pages/Count 1/Kids[3 0 R]>>',
                               b'<</Type/Page/Parent 2 0 R/MediaBox[0 0 100 100]/Resources<<>>>>',
                               b'<</Type/StructTreeRoot/K ' + children + b'>>']
                    for index in range(count):
                        parent = 4 + index if nested else 4
                        child = ('/K %d 0 R' % (6 + index)).encode('ascii') if nested and index + 1 < count else b''
                        objects.append(('<</Type/StructElem/S/P/P %d 0 R' % parent).encode('ascii') + child + b'>>')
                    data = bytearray(b'%PDF-2.0\n'); offsets = []
                    for number, body in enumerate(objects, 1):
                        offsets.append(len(data)); data.extend(('%d 0 obj\n' % number).encode('ascii') + body + b'\nendobj\n')
                    xref = len(data); data.extend(('xref\n0 %d\n0000000000 65535 f \n' % (len(objects) + 1)).encode('ascii'))
                    for offset in offsets:
                        data.extend(('%010d 00000 n \n' % offset).encode('ascii'))
                    data.extend(('trailer\n<</Root 1 0 R/Size %d>>\nstartxref\n%d\n%%%%EOF\n' % (len(objects) + 1, xref)).encode('ascii'))
                    self.observe(directory, name, data, expected)

    def test_structure_language_and_replacement_metadata_are_text_strings(self):
        original = SOURCE.read_bytes()
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, number, before, after, expected in (
                    ('catalog-numeric-language', 1, b'/Lang (en)', b'/Lang 123', 'fail'),
                    ('element-name-language', 9, b'/Lang (fr)', b'/Lang /FR', 'fail'),
                    ('element-boolean-language', 11, b'/Lang (ja)', b'/Lang true', 'fail'),
                    ('element-name-alternate', 9, b'/Alt (RootAlt)', b'/Alt /RootAlt', 'fail'),
                    ('element-boolean-replacement', 9, b'/ActualText (RootActual)', b'/ActualText false', 'fail'),
                    ('element-empty-language', 9, b'/Lang (fr)', b'/Lang ()', 'pass'),
                    ('element-hex-language', 9, b'/Lang (fr)', b'/Lang <66>', 'pass'),
                    ('element-null-language', 9, b'/Lang (fr)', b'/Lang null', 'pass'),
                    ('element-null-replacement', 9, b'/ActualText (RootActual)', b'/ActualText null', 'pass')):
                with self.subTest(metadata=name):
                    self.observe(directory, name, self.change(original, number, before, after), expected,
                                 'structure-text' if expected == 'fail' else None)

    def observe(self, directory, name, data, expected, rule=None, scope='structure-hierarchy'):
        pdf = directory / (name + '.pdf'); pdf.write_bytes(data)
        output = directory / name
        run = subprocess.run([sys.executable, str(SCRIPT), str(ROOT), str(pdf), scope, str(output)],
                             capture_output=True, text=True, timeout=40)
        self.assertEqual(0, run.returncode, run.stderr)
        result = dict(line.split('=', 1) for line in (output / 'result.properties').read_text().splitlines())
        self.assertEqual(expected, result['standards'], name + ': ' + result['finding'])
        self.assertEqual(hashlib.sha256(data).hexdigest(), result['input-sha256'])
        self.assertEqual(hashlib.sha256(SCRIPT.read_bytes()).hexdigest(), result['observer-sha256'])
        if rule is not None:
            self.assertEqual(rule, result['rule'])
        return result

    def change(self, source, number, before, after):
        marker = ('\n%d 0 obj\n' % number).encode('ascii')
        self.assertEqual(1, source.count(marker))
        self.assertLessEqual(len(after), len(before))
        start = source.index(marker) + len(marker); end = source.index(b'\nendobj\n', start)
        body = source[start:end]; self.assertEqual(1, body.count(before))
        return source[:start] + body.replace(before, after.ljust(len(before))) + source[end:]

    def test_structure_hierarchy_has_typed_children_unique_elements_and_matching_parent_backlinks(self):
        original = SOURCE.read_bytes()
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for path in sorted(SOURCE.parent.glob('*.pdf')):
                with self.subTest(source=path.name):
                    self.observe(directory, 'source-' + path.stem, path.read_bytes(), 'pass')
            for name, number, before, after, expected in (
                    ('root-type', 8, b'/Type /StructTreeRoot', b'/Type /Other', 'fail'),
                    ('root-content-child', 8, b'/K 9 0 R', b'/K 0', 'fail'),
                    ('root-null-is-absent', 8, b'/K 9 0 R', b'/K null', 'pass'),
                    ('root-null-array-child', 8, b'/K 9 0 R', b'/K[null]', 'fail'),
                    ('root-absent-children', 8, b'/K 9 0 R', b'', 'pass'),
                    ('element-root-backlink', 9, b'/P 8 0 R', b'/P 7 0 R', 'fail'),
                    ('element-child-backlink', 11, b'/P 10 0 R', b'/P 9 0 R', 'fail'),
                    ('element-cycle', 9, b'/K [10 0 R]', b'/K [9 0 R]', 'fail'),
                    ('element-unknown-kind', 11, b'/Type /StructElem', b'/Type /Other', 'fail'),
                    ('element-optional-type', 11, b'/Type /StructElem', b'', 'pass'),
                    ('element-missing-role', 9, b'/S /Custom', b'', 'fail'),
                    ('element-numeric-role', 11, b'/S /Span', b'/S 12345', 'fail'),
                    ('element-null-is-absent', 11, b'/ActualText ()', b'/K null', 'pass'),
                    ('element-null-array-child', 11, b'/ActualText ()', b'/K [null]', 'fail'),
                    ('element-numeric-children', 11, b'/ActualText ()', b'/K 0.5', 'fail')):
                with self.subTest(hierarchy=name):
                    self.observe(directory, name, self.change(original, number, before, after), expected,
                                 'structure-hierarchy' if expected == 'fail' else None)
            children = b'/K [0 << /Type /MCR /Pg 3 0 R /Stm 7 0 R /MCID 0 >> << /Type /OBJR /Pg 3 0 R /Obj 12 0 R >> 11 0 R]'
            self.observe(directory, 'shared-child', self.change(original, 10, children, b'/K [11 0 R 11 0 R]'),
                         'fail', 'structure-hierarchy')


if __name__ == '__main__':
    unittest.main()
