#!/usr/bin/env python3
"""Author T15 structural fixtures without Folio, PDFBox or a signature engine.

The fixed signature contents are placeholders, not cryptographic signatures.
Expected permissions come from the published T15 policy and ADR-0037.
"""
import hashlib
import json
from pathlib import Path
import re
import sys


REFERENCE = ('<< /Type /SigRef /TransformMethod /DocMDP '
             '/TransformParams << /Type /TransformParams /P 3 /V /1.2 >> >>')
SIGNATURE = ('<< /Type /Sig /Filter /Adobe.PPKLite /SubFilter /adbe.pkcs7.detached '
             '/ByteRange [@@@@@@@@@@ @@@@@@@@@@ @@@@@@@@@@ @@@@@@@@@@] '
             '/Contents <' + '00' * 128 + '>')


def stream(dictionary, program):
    return '<< ' + dictionary + ' /Length ' + str(len(program.encode('ascii'))) + ' >>\nstream\n' + program + 'endstream'


def objects():
    return [
        '<< /Type /Catalog /Pages 2 0 R /AcroForm 6 0 R /Perms << /DocMDP 8 0 R >> /FolioKeep 11 0 R >>',
        '<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>',
        '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] /Resources << >> /Contents 5 0 R /Annots [9 0 R 7 0 R] >>',
        '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] /Resources << >> /Contents 5 0 R >>',
        stream('', '0 0 1 rg 10 10 20 20 re f\n'),
        '<< /Fields [7 0 R] /SigFlags 3 >>',
        '<< /Type /Annot /Subtype /Widget /Rect [0 0 1 1] /F 2 /P 3 0 R /NM (signature-widget) /FT /Sig /T (FolioSignature) /V 8 0 R >>',
        SIGNATURE + ' /Reference [' + REFERENCE + '] >>',
        '<< /Type /Annot /Subtype /Stamp /Rect [40 40 60 60] /NM (existing) /Contents (Original) /F 4 /Name /Approved /P 3 0 R /AP << /N 10 0 R >> >>',
        stream('/Type /XObject /Subtype /Form /FormType 1 /BBox [0 0 20 20] /Resources << >>', '1 0 0 rg 0 0 20 20 re f\n'),
        '<< /Token (Unrelated) /Numbers [1 2 3] >>',
    ]


def serialize(values):
    output = bytearray(b'%PDF-1.7\n%FolioT15OriginalFixture\n')
    offsets = [0]
    for number, value in enumerate(values, 1):
        offsets.append(len(output))
        output.extend((str(number) + ' 0 obj\n' + value + '\nendobj\n').encode('ascii'))
    xref = len(output)
    output.extend(('xref\n0 ' + str(len(offsets)) + '\n0000000000 65535 f \n').encode('ascii'))
    for offset in offsets[1:]:
        output.extend(('%010d 00000 n \n' % offset).encode('ascii'))
    output.extend(('trailer\n<< /Size ' + str(len(offsets)) + ' /Root 1 0 R >>\nstartxref\n'
                   + str(xref) + '\n%%EOF\n').encode('ascii'))
    pattern = rb'/ByteRange \[@{10} @{10} @{10} @{10}\]'
    for match in list(re.finditer(pattern, output)):
        contents = re.match(rb' /Contents (<[0-9A-F]+>)', output[match.end():])
        start, end = (match.end() + contents.start(1), match.end() + contents.end(1)) if contents else (1, 2)
        replacement = ('/ByteRange [%010d %010d %010d %010d]' % (0, start, end, len(output) - end)).encode('ascii')
        size = len(replacement)
        output[match.start():match.start() + size] = replacement
    return bytes(output)


def build(destination):
    destination.mkdir(parents=True, exist_ok=True)
    cases = {}

    def case(name, permission, edits=(), extra=(), rule=''):
        values = objects()
        for index, old, new in edits:
            if old is None:
                values[index - 1] = new
            else:
                assert old in values[index - 1], (name, old)
                values[index - 1] = values[index - 1].replace(old, new, 1)
        values.extend(extra)
        data = serialize(values)
        path = destination / (name + '.pdf')
        path.write_bytes(data)
        cases[name] = {'file': path.name, 'sha256': hashlib.sha256(data).hexdigest(),
                       'permission': permission, 'rule': rule}

    no_policy = [(1, ' /Perms << /DocMDP 8 0 R >>', ''), (8, ' /Reference [' + REFERENCE + ']', '')]
    case('p3', 'annotations')
    case('unsigned', 'unsigned', [(1, ' /AcroForm 6 0 R /Perms << /DocMDP 8 0 R >>', ''),
         (3, '[9 0 R 7 0 R]', '[9 0 R]')])
    case('empty-field', 'unsigned', no_policy + [(7, ' /V 8 0 R', '')])
    case('null-field', 'unsigned', no_policy + [(7, '/V 8 0 R', '/V null')])
    case('approval', 'denied', no_policy + [(8, '/Type /Sig ', '')])
    case('inherited-approval', 'denied', no_policy + [(7, ' >>', ' /Kids [12 0 R] >>')],
         ['<< /Parent 7 0 R /T (Inherited) >>'])
    case('inherited-p3', 'annotations', [(7, ' >>', ' /Kids [12 0 R] >>')],
         ['<< /Parent 7 0 R /T (Inherited) >>'])
    for permission in (1, 2):
        case('p' + str(permission), 'denied', [(8, '/P 3', '/P ' + str(permission))])
    case('default-p', 'denied', [(8, ' /P 3', '')])
    case('default-v', 'annotations', [(8, ' /V /1.2', '')])
    case('no-sigflags', 'annotations', [(6, ' /SigFlags 3', '')])
    case('usage-rights-only', 'denied', [(1, ' /AcroForm 6 0 R', ''), (1, '/DocMDP', '/UR3'),
         (8, ' /Reference [' + REFERENCE + ']', '')])
    case('unknown-handler-only', 'denied', [(1, ' /AcroForm 6 0 R', ''),
         (1, '/DocMDP 8 0 R', '/FolioUnknown << >>')])
    case('legacy-usage-rights', 'denied', no_policy + [(1, ' /FolioKeep', ' /Perms << /UR 8 0 R >> /FolioKeep')])
    case('p3-approval', 'denied', [(6, '[7 0 R]', '[7 0 R 12 0 R]')],
         ['<< /FT /Sig /T (Approval) /V 13 0 R >>', SIGNATURE + ' >>'])
    case('p3-usage-rights', 'denied', [(1, '/DocMDP 8 0 R', '/DocMDP 8 0 R /UR3 12 0 R')],
         [SIGNATURE + ' >>'])
    case('p3-shared-usage-rights', 'denied', [(1, '/DocMDP 8 0 R', '/DocMDP 8 0 R /UR3 8 0 R')])
    case('p3-unknown-handler', 'denied', [(1, '/DocMDP 8 0 R', '/DocMDP 8 0 R /FolioUnknown << >>')])
    for method in ('FieldMDP', 'FolioUnknown', 'UR'):
        case('p3-' + method.lower(), 'denied', [(8, REFERENCE + ']', REFERENCE
             + ' << /TransformMethod /' + method + ' >>]')])
        case(method.lower() + '-only', 'denied', no_policy + [(8, ' >>', ' /Reference [<< /TransformMethod /'
             + method + ' >>] >>')])
    case('nonzero-offset', 'denied', [(8, '/ByteRange [@@@@@@@@@@ @@@@@@@@@@ @@@@@@@@@@ @@@@@@@@@@]', '/ByteRange [1 1]')])
    case('missing-parameters', 'denied', [(8, ' /TransformParams << /Type /TransformParams /P 3 /V /1.2 >>', '')])
    case('scalar-parameters', 'denied', [(8, '<< /Type /TransformParams /P 3 /V /1.2 >>', '17')])
    case('array-parameters', 'denied', [(8, '<< /Type /TransformParams /P 3 /V /1.2 >>', '[]')])
    case('no-docmdp-reference', 'denied', [(8, ' /Reference [' + REFERENCE + ']', '')])

    # Each defect has a literal expected structural failure. Unrecognized but
    # well-formed restrictions above deliberately have a different expectation.
    defects = {
        'acroform-type': (1, '6 0 R', '17', 'field-tree'),
        'fields-type': (6, '[7 0 R]', '17', 'field-tree'),
        'field-type': (7, '/FT /Sig', '/FT 17', 'field-tree'),
        'field-value-type': (7, '/V 8 0 R', '/V 17', 'field-tree'),
        'kids-type': (7, ' >>', ' /Kids 17 >>', 'field-tree'),
        'root-parent': (7, ' >>', ' /Parent 7 0 R >>', 'field-tree'),
        'repeated-field': (6, '[7 0 R]', '[7 0 R 7 0 R]', 'field-tree'),
        'cyclic-field': (7, ' >>', ' /Kids [7 0 R] >>', 'field-tree'),
        'signature-type': (8, '/Type /Sig ', '/Type /Other ', 'signature-dictionary'),
        'contents-type': (8, '<' + '00' * 128 + '>', '17', 'signature-dictionary'),
        'contents-missing': (8, ' /Contents <' + '00' * 128 + '>', '', 'signature-dictionary'),
        'reference-type': (8, '[' + REFERENCE + ']', '17', 'signature-reference'),
        'reference-member-type': (8, REFERENCE, '17', 'signature-reference'),
        'method-missing': (8, ' /TransformMethod /DocMDP', '', 'signature-reference'),
        'method-type': (8, '/TransformMethod /DocMDP', '/TransformMethod 17', 'signature-reference'),
        'parameters-type-name': (8, '/Type /TransformParams', '/Type /Other', 'docmdp-parameters'),
        'permission-zero': (8, '/P 3', '/P 0', 'docmdp-parameters'),
        'permission-four': (8, '/P 3', '/P 4', 'docmdp-parameters'),
        'permission-real': (8, '/P 3', '/P 3.5', 'docmdp-parameters'),
        'permission-name': (8, '/P 3', '/P /Three', 'docmdp-parameters'),
        'permission-boolean': (8, '/P 3', '/P true', 'docmdp-parameters'),
        'version-unsupported': (8, '/V /1.2', '/V /1.1', 'docmdp-parameters'),
        'version-type': (8, '/V /1.2', '/V (1.2)', 'docmdp-parameters'),
        'perms-type': (1, '<< /DocMDP 8 0 R >>', '17', 'catalog-permissions'),
        'perms-type-name': (1, '<< /DocMDP', '<< /Type /Other /DocMDP', 'catalog-permissions'),
        'docmdp-type': (1, '/DocMDP 8 0 R', '/DocMDP 17', 'catalog-permissions'),
        'docmdp-missing-field': (6, '[7 0 R]', '[]', 'certification-link'),
        'docmdp-missing-catalog': (1, ' /Perms << /DocMDP 8 0 R >>', '', 'certification-link'),
        'docmdp-repeated-reference': (8, REFERENCE + ']', REFERENCE + ' ' + REFERENCE + ']', 'certification-link'),
    }
    for name, (index, old, new, rule) in defects.items():
        case(name, 'invalid', [(index, old, new)], rule=rule)
    for name, byte_range in {
        'type': '17', 'empty': '[]', 'short': '[0]', 'odd': '[0 1 2]', 'real': '[0 1.5]',
        'boolean': '[0 true]', 'negative-offset': '[-1 1]', 'negative-length': '[0 -1]',
        'overlap': '[0 8 7 1]', 'unordered': '[10 1 0 1]', 'out-of-bounds': '[0 99999999]',
        'overflow': '[9223372036854775807 1]',
    }.items():
        case('range-' + name, 'invalid', [(8, '[@@@@@@@@@@ @@@@@@@@@@ @@@@@@@@@@ @@@@@@@@@@]', byte_range)], rule='byte-range')
    case('range-missing', 'invalid', [(8, ' /ByteRange [@@@@@@@@@@ @@@@@@@@@@ @@@@@@@@@@ @@@@@@@@@@]', '')], rule='byte-range')
    case('range-element-indirect', 'invalid', [(8, '[@@@@@@@@@@ @@@@@@@@@@ @@@@@@@@@@ @@@@@@@@@@]', '[12 0 R 1]')], ['0'], 'direct-values')
    for name, old, value in [
        ('signature-type', '/Type /Sig', '/Sig'), ('contents', '<' + '00' * 128 + '>', '<00>'),
        ('range', '[@@@@@@@@@@ @@@@@@@@@@ @@@@@@@@@@ @@@@@@@@@@]', '[0 1]'),
        ('reference-array', '[' + REFERENCE + ']', '[' + REFERENCE + ']'),
        ('reference-member', REFERENCE, REFERENCE), ('method', '/DocMDP', '/DocMDP'),
        ('parameters', '<< /Type /TransformParams /P 3 /V /1.2 >>', '<< /P 3 /V /1.2 >>'),
        ('permission', '/P 3', '3'), ('version', '/V /1.2', '/1.2'),
        ('parameter-type', '/Type /TransformParams', '/TransformParams'),
    ]:
        replacement = (old.split(' ')[0] + ' ' if name in ('permission', 'version', 'parameter-type', 'signature-type') else '') + '12 0 R'
        case('indirect-' + name, 'invalid', [(8, old, replacement)], [value], 'direct-values')
    case('child-parent-missing', 'invalid', [(7, ' >>', ' /Kids [12 0 R] >>')], ['<< /T (Child) >>'], 'field-tree')
    case('child-parent-contradiction', 'invalid', [(7, ' >>', ' /Kids [12 0 R] >>')],
         ['<< /Parent 11 0 R /T (Child) >>'], 'field-tree')
    case('conflicting-docmdp', 'invalid', [(1, '/DocMDP 8 0 R', '/DocMDP 12 0 R')],
         [SIGNATURE + ' >>'], 'certification-link')
    case('two-certifications', 'invalid', [(6, '[7 0 R]', '[7 0 R 12 0 R]')],
         ['<< /FT /Sig /T (Second) /V 13 0 R >>', SIGNATURE + ' /Reference [' + REFERENCE + '] >>'], 'certification-link')
    case('usage-rights-type', 'invalid', [(1, '/DocMDP 8 0 R', '/DocMDP 8 0 R /UR3 17')], rule='catalog-permissions')

    (destination / 'cases.properties').write_text(''.join(name + '=' + definition['permission'] + '\n'
                                                for name, definition in sorted(cases.items())))
    (destination / 'corpus.json').write_text(json.dumps({'profile': 'T15-incremental-signature-protection',
        'origin': 'Original Apache-2.0 Folio PDF byte-level authoring; placeholder signature contents',
        'cases': cases}, indent=2, sort_keys=True) + '\n')
    print(str(len(cases)) + ' original T15 structural fixtures authored in ' + str(destination))


if __name__ == '__main__':
    build(Path(sys.argv[1]))
