#!/usr/bin/env python3
"""Author original isolated T12 standards controls before tool qualification.

The rule union and assignments are fixed independently of Folio products.
Existing core/Form/destination/file rules retain their qualified assignments.
New annotation and local Action rules are assigned to Arlington.
Apache-2.0, Folio PDF by ZeroCloud contributors.
"""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import posixpath
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('t12_original', Path(__file__).with_name('generate-t12-corpus.py'))
AUTHOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUTHOR)


def original():
    data = AUTHOR.pdf_source(AUTHOR.product_definitions()['created'], AUTHOR.page_definitions())
    matches = re.findall(rb'(?m)^([0-9]+) 0 obj\n(.*?)\nendobj\n', data, re.DOTALL)
    if [int(number) for number, value in matches] != list(range(1, len(matches) + 1)):
        raise ValueError('The original object sequence must be contiguous')
    return [value.decode('ascii') for number, value in matches]


def controls(base):
    result = []

    def add(rule, number, before, after, requirement):
        result.append((rule, number, before, after, 'ISO 32000-2:2020 ' + requirement))

    def annotation(identifier):
        marker = '/NM <' + identifier.encode().hex() + '>'
        candidates = [index for index, value in enumerate(base, 1) if marker in value]
        if len(candidates) != 1:
            raise ValueError('Original annotation identity is ambiguous: ' + identifier)
        return candidates[0]

    note, stamp, highlight, file, widget = [annotation(name) for name in ('note', 'stamp', 'highlight', 'file', 'widget')]
    direct, action = annotation('direct'), annotation('action')
    ap = re.search(r'/AP << /N [0-9]+ 0 R >>', base[note - 1]).group()
    add('annotation-ap-dictionary', note, ap, '/AP 42', '12.5.2, Table 166: AP is a dictionary')
    add('appearance-normal-required', note, ap, '/AP << >>', '12.5.5, Table 170: N is required')
    add('appearance-normal-type', note, ap, '/AP << /N 42 >>', '12.5.5, Table 170: N is a stream or subdictionary')
    for family, number in [('text', note), ('stamp', stamp), ('highlight', highlight), ('file', file), ('widget', widget)]:
        ap = re.search(r'/AP << /N [0-9]+ 0 R >>', base[number - 1]).group()
        add(family + '-appearance-required', number, ap, '',
            '12.5.2, Table 166: a nonzero-area ' + family + ' annotation requires AP in PDF 2.0')
    add('annotation-flags-reserved', note, '/F 4', '/F 2048', '12.5.3, Table 167: undefined annotation flag bits are zero')
    add('annotation-page-owner', note, '/P 4 0 R', '/P 5 0 R', '12.5.2, Table 166: P identifies the containing page')
    add('annotation-id-unique', stamp, '/NM <7374616d70>', '/NM <6e6f7465>',
        '12.5.2, Table 166: NM identifies an annotation uniquely among annotations on its page')
    add('text-name-name', note, '/Name /Note', '/Name 42', '12.5.6.4, Table 173: Text Name is a name')
    add('text-open-boolean', note, '/Open false', '/Open 42', '12.5.6.4, Table 173: Text Open is boolean')
    add('stamp-name-name', stamp, '/Name /Approved', '/Name 42', '12.5.6.12, Table 183: Stamp Name is a name')
    quad = '/QuadPoints [50 80 75 80 50 65 75 65]'
    for suffix, replacement, requirement in [
            ('required', '', 'QuadPoints is required'), ('array', '/QuadPoints 42', 'QuadPoints is an array'),
            ('count', '/QuadPoints [1 2 3 4 5 6 7]', 'QuadPoints has eight numbers per quadrilateral'),
            ('numbers', '/QuadPoints [50 80 75 80 50 65 75 /Bad]', 'QuadPoints coordinates are numbers')]:
        add('highlight-quads-' + suffix, highlight, quad, replacement, '12.5.6.10, Table 181: ' + requirement)
    for suffix, replacement, requirement in [
            ('array', '42', 'C is an array'), ('count', '[1 1]', 'C has zero, one, three or four components'),
            ('numbers', '[1 /Bad 0]', 'C components are numbers'), ('range', '[1 2 0]', 'C components lie from zero to one')]:
        add('highlight-color-' + suffix, highlight, '/C [1 1 0]', '/C ' + replacement, '12.5.2, Table 166: ' + requirement)
    fs = re.search(r'/FS [0-9]+ 0 R', base[file - 1]).group()
    add('file-annotation-fs-required', file, fs, '', '12.5.6.15, Table 187: FileAttachment FS is required')
    add('file-annotation-fs-type', file, fs, '/FS 42', '12.5.6.15, Table 187: FS is a file specification')
    add('file-icon-name', file, '/Name /Paperclip', '/Name 42', '12.5.6.15, Table 187: FileAttachment Name is a name')
    add('link-destination-type', direct, '/Dest [4 0 R /FitR 5 10 100 90]', '/Dest 42',
        '12.5.6.5, Table 176: Dest is a name, string or explicit destination array')
    go_to = '/A << /S /GoTo /D [5 0 R /XYZ null 90 1.25] >>'
    add('link-action-type', action, go_to, '/A 42', '12.5.6.5, Table 176: A is an Action dictionary')
    add('link-activation-exclusive', action, go_to, '/Dest [4 0 R /Fit] ' + go_to,
        '12.5.6.5, Table 176: Dest and A are mutually exclusive')
    open_action = '/OpenAction << /S /GoTo /D <736861726564> >>'
    add('catalog-openaction-type', 1, open_action, '/OpenAction 42',
        '7.7.2, Table 29: OpenAction is an explicit destination array or Action dictionary')
    page_open = '/AA << /O << /S /GoTo /D [5 0 R /Fit] >> >>'
    add('page-aa-dictionary', 4, page_open, '/AA 42', '12.6.3, Tables 31 and 194: page AA is a dictionary')
    add('page-open-action-type', 4, page_open, '/AA << /O 42 >>', '12.6.3, Table 194: O is an Action dictionary')
    add('page-close-action-type', 5, '/AA << /C << /S /GoTo /D <736861726564> >> >>', '/AA << /C 42 >>',
        '12.6.3, Table 194: C is an Action dictionary')
    for suffix, replacement, requirement in [
            ('s-required', '<< /D <736861726564> >>', 'S is required'),
            ('s-name', '<< /S 42 /D <736861726564> >>', 'S is a name'),
            ('d-required', '<< /S /GoTo >>', 'D is required'),
            ('d-type', '<< /S /GoTo /D 42 >>', 'D is a name, string or explicit destination array'),
            ('named-target', '<< /S /GoTo /D (absent) >>', 'a named D resolves in the destination name tree')]:
        add('goto-' + suffix, 1, open_action, '/OpenAction ' + replacement, '12.6.4.2, Table 202 and 12.3.2.3: ' + requirement)
    return result


def inherited():
    assignments = {}
    for label, groups in [('T10', ['pdfcpu', 'arlington']), ('T09', ['pdfcpu', 'arlington']),
                          ('T11', ['arlington-metadata'])]:
        for group in groups:
            authority = ROOT / 'capabilities/profiles' / (label + '-standards') / (group + '.properties')
            values = dict(line.split('=', 1) for line in authority.read_text().splitlines() if line and not line.startswith('#'))
            for rule in values['required-rules'].split(','):
                if rule in assignments:
                    continue
                if label == 'T09' and not rule.startswith(('stream-', 'trailer-info-')):
                    continue
                if label == 'T11' and not rule.startswith(('filespec-', 'embedded-', 'destination-', 'nametree-')):
                    continue
                prefix = 'negative.' + rule + '.'
                assignments[rule] = {
                    'checker': 'pdfcpu' if group == 'pdfcpu' else 'arlington',
                    'path': posixpath.normpath('../' + label + '-standards/' + values[prefix + 'path']),
                    'sha256': values[prefix + 'sha256'], 'finding': values[prefix + 'finding'].replace('PDF 1.7', 'PDF 2.0'),
                    'authority': 'ISO 32000 core/Form/navigation/file requirement, qualified by the frozen ' + label + ' catalog'}
    return assignments


def generate(target):
    target.mkdir()
    (target / 'fixtures').mkdir()
    base = original()
    (target / 'fixtures/valid.pdf').write_bytes(AUTHOR._metadata.pdf(base))
    shared_name = [value.replace('/NM <646972656374>', '/NM <6e6f7465>') for value in base]
    (target / 'fixtures/positive-same-name-other-page.pdf').write_bytes(AUTHOR._metadata.pdf(shared_name))
    for label, null in [('optional-bindings', ''), ('null-bindings', 'null')]:
        positive = copy.deepcopy(base)
        for index, value in enumerate(positive):
            if '/NM <6e6f7465>' in value:
                positive[index] = value.replace('/P 4 0 R', '/P null' if null else '')
            if '/NM <616374696f6e>' in value:
                positive[index] = value.replace('/A << /S /GoTo /D [5 0 R /XYZ null 90 1.25] >>', '/A null' if null else '')
        (target / ('fixtures/positive-' + label + '.pdf')).write_bytes(AUTHOR._metadata.pdf(positive))
    catalog = {}
    for rule, number, before, after, authority in controls(base):
        objects = copy.deepcopy(base)
        if rule in catalog or objects[number - 1].count(before) != 1:
            raise ValueError('Original control context is not unique: ' + rule)
        objects[number - 1] = objects[number - 1].replace(before, after)
        data = AUTHOR._metadata.pdf(objects)
        path = 'fixtures/' + rule + '.pdf'
        (target / path).write_bytes(data)
        catalog[rule] = {'checker': 'arlington', 'path': path, 'sha256': hashlib.sha256(data).hexdigest(), 'authority': authority}
    (target / 'controls.json').write_text(json.dumps(catalog, indent=2, sort_keys=True) + '\n')
    assignments = inherited()
    if set(assignments).intersection(catalog):
        raise ValueError('New annotation rules must not replace an inherited assignment')
    findings = json.loads((ROOT / 'capabilities/expected/T12-standards-findings.json').read_text())
    if set(findings) != set(catalog):
        raise ValueError('Every new T12 rule requires one frozen checker diagnostic')
    for rule, entry in catalog.items():
        entry['finding'] = findings[rule]
    assignments.update(catalog)
    (target / 'assignments.json').write_text(json.dumps(assignments, indent=2, sort_keys=True) + '\n')
    (target / 'required-rules.txt').write_text(''.join(rule + '\n' for rule in sorted(assignments)))
    for group in ('pdfcpu', 'arlington-core', 'arlington-annotations'):
        selected = {rule: entry for rule, entry in sorted(assignments.items())
                    if ('pdfcpu' if entry['checker'] == 'pdfcpu' else
                        'arlington-annotations' if rule in catalog else 'arlington-core') == group}
        rules = ','.join(selected)
        text = 'profile=' + AUTHOR.PROFILE + '\npdf-version=2.0\nrequired-rules=' + rules + '\ncovered-rules=' + rules + '\n'
        for rule, entry in selected.items():
            for key in ('path', 'sha256', 'finding'):
                text += 'negative.' + rule + '.' + key + '=' + entry[key] + '\n'
        (target / (group + '.properties')).write_text(text)
    (target / 'rules.md').write_text('# Frozen T12 standards rule assignments\n\n'
                                    'New controls require independent checker qualification before evidence can pass.\n\n'
                                    '| Rule | Checker | Normative requirement |\n| --- | --- | --- |\n'
                                    + ''.join('| `' + rule + '` | ' + entry['checker'] + ' | ' + entry['authority'] + ' |\n'
                                              for rule, entry in sorted(assignments.items())))


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Usage: generate-t12-standards.py <fresh-output-directory>')
    generate(Path(sys.argv[1]))
