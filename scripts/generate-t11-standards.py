#!/usr/bin/env python3
"""Author isolated original T11 illegal controls; never inspect Folio output.

New rules are assigned to Arlington before checker qualification. Existing
core/name-tree rules retain their previously frozen checker assignments.
"""
import copy
import hashlib
import importlib.util
import json
import posixpath
from pathlib import Path
import sys
import zlib

SPEC = importlib.util.spec_from_file_location('t11_corpus', Path(__file__).with_name('generate-t11-corpus.py'))
CORPUS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CORPUS)


def original():
    objects = CORPUS.source_objects(CORPUS.definitions())
    objects[2] = ('<< /Title (Source title) /Author (Source author) /Subject (Subject) /Keywords (Keywords) '
                  '/Creator (Creator) /Producer (Producer) /CreationDate (D:20260910010203Z) '
                  '/ModDate (D:20260910020304Z) /Trapped /False /T73Keep (Preserved info) >>')
    return objects


def controls():
    # Each tuple declares one standards violation with its literal original context.
    # Table numbers below are from ISO 32000-1 where unchanged; AFRelationship is 32000-2.
    result = []
    def add(rule, number, before, after, clause):
        result.append((rule, number, before, after, 'ISO 32000-1:2008 ' + clause + '; ISO 32000-2:2020 corresponding requirement'))
    for key, before in [('Title', '(Source title)'), ('Author', '(Source author)'), ('Subject', '(Subject)'),
                        ('Keywords', '(Keywords)'), ('Creator', '(Creator)'), ('Producer', '(Producer)'),
                        ('CreationDate', '(D:20260910010203Z)'), ('ModDate', '(D:20260910020304Z)')]:
        add('info-' + key.lower() + '-string', 3, '/' + key + ' ' + before, '/' + key + ' 42',
            '14.3.3, Table 317: ' + key + ' is a text/date string')
    for key, before in [('CreationDate', '(D:20260910010203Z)'), ('ModDate', '(D:20260910020304Z)')]:
        add('info-' + key.lower() + '-date', 3, '/' + key + ' ' + before, '/' + key + ' (not-a-date)',
            '7.9.4 and Table 317: dates follow the PDF date syntax')
    add('info-trapped-name', 3, '/Trapped /False', '/Trapped false', 'Table 317: Trapped is a name')
    add('info-trapped-value', 3, '/Trapped /False', '/Trapped /Bad', 'Table 317: Trapped is True, False or Unknown')
    add('info-custom-string', 3, '/T73Keep (Preserved info)', '/T73Keep [1 2]', '14.3.3: custom Info values are text strings')
    add('info-dictionary', 3, None, '42', '7.5.5, Table 15: Info refers to a dictionary')
    add('metadata-stream', 1, '/Metadata 4 0 R', '/Metadata 3 0 R', 'Table 28: Metadata is a stream')
    for key, value in [('Type', 'Metadata'), ('Subtype', 'XML')]:
        add('metadata-' + key.lower() + '-required', 4, '/' + key + ' /' + value, '', 'Table 315: ' + key + ' is required')
        add('metadata-' + key.lower() + '-name', 4, '/' + key + ' /' + value, '/' + key + ' 42', 'Table 315: ' + key + ' is a name')
        add('metadata-' + key.lower() + '-value', 4, '/' + key + ' /' + value, '/' + key + ' /Bad', 'Table 315: ' + key + ' is ' + value)
    add('metadata-xml-wellformed', 4, None, CORPUS.stream('<broken>', '/Type /Metadata /Subtype /XML'), '14.3.2: metadata stream contents are XML')
    add('outline-dictionary', 1, '/Outlines 6 0 R', '/Outlines 42', 'Table 28: Outlines is a dictionary')
    add('outline-type-name', 6, '/Type /Outlines', '/Type 42', 'Table 152: Type is a name')
    add('outline-type-value', 6, '/Type /Outlines', '/Type /Bad', 'Table 152: Type, if present, is Outlines')
    for key, ref in [('First', 14), ('Last', 15)]:
        add('outline-' + key.lower() + '-required', 6, f'/{key} {ref} 0 R', '', 'Table 152: ' + key + ' is required for a nonempty outline')
        add('outline-' + key.lower() + '-indirect', 6, f'/{key} {ref} 0 R', f'/{key} << /Title (Bad) >>', 'Table 152: ' + key + ' is an indirect reference')
        add('outline-' + key.lower() + '-dictionary', 6, f'/{key} {ref} 0 R', f'/{key} 42', 'Table 152: ' + key + ' is a dictionary')
    add('outline-last-link', 6, '/Last 15 0 R', '/Last 14 0 R', 'Table 152: Last identifies the last top-level item')
    add('outline-count-required', 6, '/Count 4', '', 'Table 152: Count is required for visible items')
    add('outline-count-integer', 6, '/Count 4', '/Count 4.5', 'Table 152: Count is an integer')
    add('outline-count-nonnegative', 6, '/Count 4', '/Count -4', 'Table 152: Count is nonnegative')
    add('outline-count-value', 6, '/Count 4', '/Count 99', 'Table 152: Count equals the number of visible items')
    title = '/Title ' + CORPUS.string('Sections')
    add('outline-title-required', 14, title, '', 'Table 153: Title is required')
    add('outline-title-string', 14, title, '/Title 42', 'Table 153: Title is a text string')
    add('outline-parent-required', 14, '/Parent 6 0 R', '', 'Table 153: Parent is required')
    add('outline-parent-indirect', 14, '/Parent 6 0 R', '/Parent << >>', 'Table 153: Parent is an indirect reference')
    add('outline-parent-dictionary', 14, '/Parent 6 0 R', '/Parent 42', 'Table 153: Parent is a dictionary')
    add('outline-parent-link', 14, '/Parent 6 0 R', '/Parent 2 0 R', 'Table 153: Parent identifies the actual outline parent')
    add('outline-prev-link', 15, '/Prev 14 0 R', '/Prev 16 0 R', 'Table 153: Prev identifies the previous item at this level')
    add('outline-prev-required', 15, '/Prev 14 0 R', '', 'Table 153: Prev is required except on the first sibling')
    add('outline-next-cycle', 14, '/Next 15 0 R', '/Next 14 0 R', '12.3.3: outline siblings form an acyclic linked hierarchy')
    for key, number, ref in [('Next', 14, 15), ('Prev', 15, 14), ('First', 14, 16), ('Last', 14, 17)]:
        add('outline-item-' + key.lower() + '-indirect', number, f'/{key} {ref} 0 R', f'/{key} << /Title (Bad) >>', 'Table 153: ' + key + ' is an indirect reference')
        add('outline-item-' + key.lower() + '-dictionary', number, f'/{key} {ref} 0 R', f'/{key} 42', 'Table 153: ' + key + ' is a dictionary')
    add('outline-child-last-link', 14, '/Last 17 0 R', '/Last 16 0 R', 'Table 153: Last identifies the last immediate child')
    add('outline-child-count-required', 14, '/Count 2', '', 'Table 153: Count is required when descendants exist')
    add('outline-child-count-integer', 14, '/Count 2', '/Count 2.5', 'Table 153: Count is an integer')
    add('outline-child-count-value', 14, '/Count 2', '/Count 99', 'Table 153: absolute Count is the visible descendant count if opened')
    add('outline-destination-type', 16, '/Dest ' + CORPUS.string('shared'), '/Dest 42', 'Table 153: Dest is a name, string or array')
    add('outline-named-target', 16, '/Dest ' + CORPUS.string('shared'), '/Dest (absent)', '12.3.2.3: a named target resolves in Dests')
    views = [('XYZ', 'null 90 1.25', 'null 90', 'null /Bad 1.25'), ('FitB', '', ' 0', None),
             ('FitH', 'null', '', '/Bad'), ('FitV', 'null', '', '/Bad'),
             ('FitR', '1 2 80 90', '1 2 80', '1 null 80 90'), ('FitBH', 'null', '', '/Bad'), ('FitBV', '12', '', '/Bad')]
    for mode, good, short, bad in views:
        add('destination-' + mode.lower() + '-count', 5, None, '<< /Names [(shared) [8 0 R /' + mode + ' ' + short + ']] >>',
            'Table 151: ' + mode + ' has its exact prescribed array length')
        if bad is not None:
            add('destination-' + mode.lower() + '-operand', 5, None, '<< /Names [(shared) [8 0 R /' + mode + ' ' + bad + ']] >>',
                'Table 151: ' + mode + ' operands have their prescribed numeric/null kinds')
    for rule, body, clause in [
        ('nametree-kids-array', '<< /Kids 42 >>', 'Kids is an array'),
        ('nametree-kids-member', '<< /Kids [42] >>', 'Kids contains indirect dictionaries'),
        ('nametree-kids-cycle', '<< /Kids [5 0 R] >>', 'the tree is acyclic'),
        ('nametree-names-kids-exclusive', '<< /Names [(shared) [8 0 R /Fit]] /Kids [22 0 R] >>', 'leaf Names and intermediate Kids are exclusive'),
        ('nametree-child-limits-required', '<< /Names [(shared) [8 0 R /Fit]] >>', 'non-root nodes have Limits'),
        ('nametree-child-limits-array', '<< /Names [(shared) [8 0 R /Fit]] /Limits 42 >>', 'Limits is an array'),
        ('nametree-child-limits-count', '<< /Names [(shared) [8 0 R /Fit]] /Limits [(shared)] >>', 'Limits contains two strings'),
        ('nametree-child-limits-string', '<< /Names [(shared) [8 0 R /Fit]] /Limits [(shared) 42] >>', 'Limits contains two strings'),
        ('nametree-child-limits-value', '<< /Names [(shared) [8 0 R /Fit]] /Limits [(a) (z)] >>', 'Limits equal the actual first and last keys')]:
        add(rule, 22 if 'child-limits' in rule else 5, None, body, '7.9.6, Table 36: ' + clause)
    # Original fixture object 19 is a fully described file specification; 18 is its stream.
    for rule, number, before, after, clause in [
        ('names-embedded-tree', 1, '/EmbeddedFiles 7 0 R', '/EmbeddedFiles 42', 'Table 31: EmbeddedFiles is a name tree'),
        ('filespec-dictionary', 19, None, '42', '7.11.4: EmbeddedFiles maps to file specifications'),
        ('filespec-type-required', 19, '/Type /Filespec', '', 'Table 44: Type is required when EF is present'),
        ('filespec-type-name', 19, '/Type /Filespec', '/Type 42', 'Table 44: Type is a name'),
        ('filespec-type-value', 19, '/Type /Filespec', '/Type /Bad', 'Table 44: Type, if present, is Filespec'),
        ('filespec-f-string', 19, '/F ' + CORPUS.string('payload.txt'), '/F 42', 'Table 44: F is a byte string'),
        ('filespec-uf-string', 19, '/UF ' + CORPUS.string('payload.txt'), '/UF 42', 'Table 44: UF is a text string'),
        ('filespec-description-string', 19, '/Desc ' + CORPUS.string('Primary payload'), '/Desc 42', 'Table 44: Desc is a text string'),
        ('filespec-ef-dictionary', 19, '/EF << /F 18 0 R /UF 18 0 R >>', '/EF 42', 'Table 44: EF is a dictionary'),
        ('filespec-ef-f-stream', 19, '/EF << /F 18 0 R /UF 18 0 R >>', '/EF << /F 3 0 R /UF 18 0 R >>', '7.11.4: EF/F refers to an embedded file stream'),
        ('filespec-ef-uf-stream', 19, '/EF << /F 18 0 R /UF 18 0 R >>', '/EF << /F 18 0 R /UF 3 0 R >>', '7.11.4: EF/UF refers to an embedded file stream'),
        ('embedded-type-name', 18, '/Type /EmbeddedFile', '/Type 42', 'Table 45: Type is a name'),
        ('embedded-type-value', 18, '/Type /EmbeddedFile', '/Type /Bad', 'Table 45: Type, if present, is EmbeddedFile'),
        ('embedded-subtype-name', 18, '/Subtype /text#2Fplain', '/Subtype 42', 'Table 45: Subtype is a name'),
        ('embedded-subtype-mime', 18, '/Subtype /text#2Fplain', '/Subtype /text#2F', 'Table 45: unprefixed Subtype is a MIME media type'),
        ('embedded-params-dictionary', 18, '/Params << /Size 3 /CheckSum <900150983cd24fb0d6963f7d28e17f72> >>', '/Params 42', 'Table 45: Params is a dictionary'),
        ('embedded-size-integer', 18, '/Size 3', '/Size 3.5', 'Table 46: Size is an integer'),
        ('embedded-size-value', 18, '/Size 3', '/Size 4', 'Table 46: Size equals the uncompressed embedded-file size'),
        ('embedded-checksum-string', 18, '/CheckSum <900150983cd24fb0d6963f7d28e17f72>', '/CheckSum 42', 'Table 46: CheckSum is a string'),
        ('embedded-checksum-length', 18, '/CheckSum <900150983cd24fb0d6963f7d28e17f72>', '/CheckSum <00>', 'Table 46: CheckSum has sixteen bytes'),
        ('embedded-checksum-value', 18, '/CheckSum <900150983cd24fb0d6963f7d28e17f72>', '/CheckSum <00000000000000000000000000000000>', 'Table 46: CheckSum is the MD5 of the decoded bytes')]:
        add(rule, number, before, after, clause)
    for rule, replacement in [('filespec-relationship-name', '42'), ('filespec-relationship-value', '/Bad')]:
        result.append((rule, 19, '/AFRelationship /Data', '/AFRelationship ' + replacement,
                       'ISO 32000-2:2020 7.11.3, Table 43: AFRelationship is a name from the associated-file relationship vocabulary'))
    return result


def generate(target):
    target.mkdir()
    (target / 'fixtures').mkdir()
    base = original()
    (target / 'fixtures/valid.pdf').write_bytes(CORPUS.pdf(base))
    positive = copy.deepcopy(base)
    entries = base[4][len('<< /Names ['):-len('] >>')]
    second_child = entries.index(CORPUS.string('shared') + ' ')
    positive[4] = '<< /Kids [22 0 R 23 0 R] >>'
    positive.extend(['<< /Names [' + entries[:second_child] + '] /Limits [(b) (r)] >>',
                     '<< /Names [' + entries[second_child:] + '] /Limits [(shared) (za)] >>'])
    (target / 'fixtures/positive-kids.pdf').write_bytes(CORPUS.pdf(positive))
    closed = copy.deepcopy(base)
    closed[5] = closed[5].replace('/Count 4', '/Count 2')
    closed[13] = closed[13].replace('/Count 2', '/Count -2')
    (target / 'fixtures/positive-closed-outline.pdf').write_bytes(CORPUS.pdf(closed))
    filtered = copy.deepcopy(base)
    filtered[17] = CORPUS.stream(zlib.compress(b'abc', 9),
                                '/Type /EmbeddedFile /Subtype /text#2Fplain /Filter /FlateDecode '
                                '/Params << /Size 3 /CheckSum <900150983cd24fb0d6963f7d28e17f72> >>')
    (target / 'fixtures/positive-filtered-attachment.pdf').write_bytes(CORPUS.pdf(filtered))
    catalog = {}
    for rule, number, before, after, authority in controls():
        objects = copy.deepcopy(base)
        if number == 22 or rule == 'nametree-names-kids-exclusive':
            objects.append('<< /Names [(shared) [8 0 R /Fit]] /Limits [(shared) (shared)] >>')
        if number == 22:
            objects[4] = '<< /Kids [22 0 R] >>'
        if before is None:
            objects[number - 1] = after
        else:
            value = objects[number - 1]
            old, new = (before.encode('ascii'), after.encode('ascii')) if isinstance(value, bytes) else (before, after)
            if value.count(old) != 1:
                raise ValueError('Original control context is not unique: ' + rule)
            objects[number - 1] = value.replace(old, new)
        data = CORPUS.pdf(objects)
        relative = 'fixtures/' + rule + '.pdf'
        (target / relative).write_bytes(data)
        catalog[rule] = {'path': relative, 'sha256': hashlib.sha256(data).hexdigest(),
                         'authority': authority, 'checker': 'arlington'}
    (target / 'controls.json').write_text(json.dumps(catalog, indent=2, sort_keys=True) + '\n', encoding='ascii')
    assignments = copy.deepcopy(catalog)
    profiles = Path(__file__).resolve().parents[1] / 'capabilities/profiles'
    findings = json.loads((profiles.parent / 'expected/T11-standards-findings.json').read_text())
    if set(findings) != set(catalog):
        raise ValueError('The frozen T11 checker diagnostics must cover every new control')
    for rule in catalog:
        assignments[rule]['finding'] = findings[rule]
    for label in ['T03', 'T09', 'T10']:
        for checker in ['pdfcpu', 'arlington']:
            values = dict(line.split('=', 1) for line in (profiles / (label + '-standards') / (checker + '.properties')).read_text().splitlines()
                          if line and not line.startswith('#'))
            for rule in values['required-rules'].split(','):
                if rule in assignments:
                    continue
                if label == 'T10' and not rule.startswith(('catalog-names-', 'names-dests-', 'nametree-', 'destination-')):
                    continue
                prefix = 'negative.' + rule + '.'
                assignments[rule] = {'checker': checker,
                                     'path': posixpath.normpath('../' + label + '-standards/' + values[prefix + 'path']),
                                     'sha256': values[prefix + 'sha256'],
                                     'finding': values[prefix + 'finding'].replace('PDF 1.7', 'PDF 2.0'),
                                     'authority': 'ISO 32000 core/navigation requirement qualified by the frozen ' + label + ' rule catalog'}
    (target / 'required-rules.txt').write_text(''.join(rule + '\n' for rule in sorted(assignments)), encoding='ascii')
    (target / 'assignments.json').write_text(json.dumps(assignments, indent=2, sort_keys=True) + '\n', encoding='ascii')
    for group in ['pdfcpu', 'arlington-core', 'arlington-metadata']:
        selected = {rule: entry for rule, entry in sorted(assignments.items())
                    if (entry['checker'] == 'pdfcpu' and group == 'pdfcpu')
                    or (entry['checker'] == 'arlington'
                        and group == ('arlington-metadata' if rule in catalog else 'arlington-core'))}
        ids = ','.join(selected)
        text = 'profile=' + CORPUS.PROFILE + '\npdf-version=2.0\nrequired-rules=' + ids + '\ncovered-rules=' + ids + '\n'
        for rule, entry in selected.items():
            for key in ['path', 'sha256', 'finding']:
                text += 'negative.' + rule + '.' + key + '=' + entry[key] + '\n'
        (target / (group + '.properties')).write_text(text, encoding='ascii')
    (target / 'rules.md').write_text('# Frozen T11 metadata rule assignments\n\n'
                                   'All controls are original. New rules are assigned before product observation.\n\n'
                                   '| Rule | Checker | Normative requirement |\n| --- | --- | --- |\n'
                                   + ''.join('| `' + rule + '` | ' + entry['checker'] + ' | ' + entry['authority'] + ' |\n'
                                             for rule, entry in sorted(assignments.items())), encoding='ascii')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('usage: generate-t11-standards.py FRESH_OUTPUT_DIRECTORY')
    generate(Path(sys.argv[1]))
