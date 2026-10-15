"""Publish navigable views of immutable tool reports without changing sealed trees."""
import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import time
from urllib.parse import quote, unquote, urlsplit, urlunsplit


INLINE = re.compile(r'\[[^\]]*\]\(\s*(<[^>]+>|[^\s)]+)(?:\s+[\"\'][^\n]*?[\"\'])?\s*\)')
REFERENCE = re.compile(r'(?m)^\s{0,3}\[[^\]]+\]:\s*(<[^>]+>|\S+)')
FENCE = re.compile(r'(?ms)^```[^\n]*\n.*?^```[^\n]*$')
PDF_ID = re.compile(rb'/ID\s*\[\s*<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>\s*\]')
VIEW_ROOT = 'capabilities/evidence/T84-delivery/report-views/'
HELPER = 'capabilities/evidence/T84-delivery/retain-report-views.py'
PROOF = 'capabilities/evidence/T84-delivery/report-link-retention.json'
README = VIEW_ROOT + 'README.md'
AUDIT = 'capabilities/evidence/T84-delivery/validation/publication-staged-audit.json'
NOTICES = {
    '../../docs/third-party/pdfium-cli-v0.11.2.md': 'docs/third-party/pdfium-cli-v0.11.2.md',
    '../../docs/third-party/imagemagick-7.1.2-30-appimage.md': 'docs/third-party/imagemagick-7.1.2-30-appimage.md',
}
LOCAL_ARTIFACTS = {
    'artifacts/blank.pdf': 'blank.pdf',
    'artifacts/syntax.txt': 'syntax.txt',
    'artifacts/T06-document-blank-output.pdf': 'blank.pdf',
    '../expected/T03-document-blank-144dpi-srgb.png': 'T07-document-blank-expected.png',
    'artifacts/T07-document-blank-expected.png': 'T07-document-blank-expected.png',
    'artifacts/T07-document-blank-pdfium.png': 'T07-document-blank-pdfium.png',
    'artifacts/T07-document-blank-implementation.png': 'T07-document-blank-implementation.png',
    'artifacts/T07-document-blank-difference.png': 'T07-document-blank-difference.png',
    'artifacts/T07-document-blank-renderer-difference.png': 'T07-document-blank-renderer-difference.png',
    'artifacts/T07-document-blank-visual.txt': 'visual.txt',
    'T22-scale-visual.txt': 'visual.txt',
}
AUTHORITIES = set(NOTICES.values()) | {'capabilities/expected/T03-document-blank-144dpi-srgb.png'}
RASTERS = {
    'expected': 'Expected raster SHA-256',
    'pdfium': 'PDFium raster SHA-256',
    'implementation': 'Implementation raster SHA-256',
    'difference': 'Difference raster SHA-256',
    'renderer-difference': 'Renderer difference raster SHA-256',
}
COMPRESSED_INPUTS = {}


def sha256(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def reference(root, path):
    assert path.resolve(strict=True) == path and path.is_file() and not path.is_symlink(), str(path)
    return {'path': path.relative_to(root).as_posix(), 'sha256': sha256(path)}


def occurrences(text):
    # Mask fenced findings without changing the offsets used for replacements.
    visible = FENCE.sub(lambda match: ' ' * len(match[0]), text)
    found = sorted([*INLINE.finditer(visible), *REFERENCE.finditer(visible)], key=lambda match: match.start(1))
    previous = 0
    for match in found:
        assert match.start(1) >= previous, 'Overlapping Markdown destinations'
        previous = match.end(1)
        raw = text[match.start(1):match.end(1)]
        location = urlsplit(raw.strip('<>'))
        if not location.scheme and not location.netloc and location.path:
            yield match.start(1), match.end(1), raw, location


def literal_target(report, location):
    value = Path(unquote(location.path))
    return Path(os.path.abspath(value if value.is_absolute() else report.parent / value))


def field(text, label):
    values = re.findall(r'(?m)^' + re.escape(label) + r': `([0-9a-f]{64})`$', text)
    assert len(values) == 1, 'Missing or ambiguous original field: ' + label
    return values[0]


def input_hash(pdf):
    if pdf.name.endswith('.gz'):
        assert pdf.name == 'consumed-input.pdf.gz' and pdf.parent.name == 'input'
        compressed = sha256(pdf)
        if compressed not in COMPRESSED_INPUTS:
            digest = hashlib.sha256()
            count = 0
            deadline = time.monotonic() + 120
            # The authored consumed input has no trailer ID. Its exact and
            # ID-neutral identities coincide; stream the retained 1-GiB PDF
            # without materializing it or writing into the sealed tree.
            with gzip.open(pdf, 'rb') as stream:
                while chunk := stream.read(1024 * 1024):
                    count += len(chunk)
                    assert count <= 1073741824 and time.monotonic() < deadline
                    digest.update(chunk)
            assert count == 1073741824
            COMPRESSED_INPUTS[compressed] = digest.hexdigest()
        actual = COMPRESSED_INPUTS[compressed]
        for name in ('observations.properties', 'document.properties'):
            assert re.findall(r'(?m)^input-sha256=([0-9a-f]{64})$', (pdf.parent / name).read_text()) == [actual]
        return actual
    data = bytearray(pdf.read_bytes())
    matches = list(PDF_ID.finditer(data))
    assert len(matches) == 1, 'Unexpected original PDF identifier count'
    for group in (1, 2):
        start, end = matches[0].span(group)
        assert (end - start) % 2 == 0
        data[start:end] = b'0' * (end - start)
    return hashlib.sha256(data).hexdigest()


def verify_observation(report, text, links, root):
    if text.startswith('# T70 qpdf syntax evidence\n'):
        assert report.name == 'syntax.md'
        assert [link['target'] for link in links] == ['artifacts/blank.pdf', 'artifacts/syntax.txt']
        pdf = report.parent / 'blank.pdf'
        assert input_hash(pdf) == field(text, 'Input ID-neutral SHA-256')
        properties = (report.parent / 'result.properties').read_text()
        assert re.findall(r'(?m)^input-sha256=([0-9a-f]{64})$', properties) == [sha256(pdf)]
        assert field((report.parent / 'syntax.txt').read_text(), 'Input ID-neutral SHA-256') == field(text, 'Input ID-neutral SHA-256')
        return
    assert report.name == 'visual.md'
    if text.startswith('# T07 PDFium visual evidence\n'):
        assert {link['target'] for link in links} == (set(LOCAL_ARTIFACTS) - {
            'artifacts/blank.pdf', 'artifacts/syntax.txt', 'T22-scale-visual.txt'}) | set(NOTICES)
        pdf = report.parent / 'blank.pdf'
        prefix = 'T07-document-blank-'
    else:
        assert text.startswith('# T22 PDFium visual evidence\n'), 'Unknown raw report kind'
        assert [link['target'] for link in links].count('T22-scale-visual.txt') == 1
        assert all(link['resolution'] == 'literal-original' or link['target'] == 'T22-scale-visual.txt' for link in links)
        pdf_links = [link for link in links if link['actual']['path'].endswith(('.pdf', '.pdf.gz'))]
        assert len(pdf_links) == 1
        if pdf_links[0]['actual']['path'].endswith('.gz'):
            assert pdf_links[0]['target'] == '../consumed-input.pdf.gz'
        pdf = root / pdf_links[0]['actual']['path']
        prefix = 'T22-scale-'
    assert input_hash(pdf) == field(text, 'Input ID-neutral SHA-256')
    findings = (report.parent / 'visual.txt').read_text()
    assert field(findings, 'Input ID-neutral SHA-256') == field(text, 'Input ID-neutral SHA-256')
    for suffix, label in RASTERS.items():
        actual = sha256(report.parent / (prefix + suffix + '.png'))
        assert actual == field(findings, label)
        if suffix in ('expected', 'pdfium', 'implementation'):
            assert actual == field(text, label)


def project(root, report, final_base, selected):
    raw = report.read_bytes()
    text = raw.decode('utf-8')
    links = []
    replacements = []
    view = root / VIEW_ROOT / report.relative_to(final_base)
    for start, end, target, location in occurrences(text):
        actual = literal_target(report, location)
        resolution = 'literal-original'
        if not actual.exists():
            assert location.path == target, 'Unsupported context target encoding'
            assert report.name in ('syntax.md', 'visual.md')
            if target in NOTICES:
                actual = root / NOTICES[target]
            else:
                assert target in LOCAL_ARTIFACTS, 'Unknown invocation-context target: ' + target
                actual = report.parent / LOCAL_ARTIFACTS[target]
            resolution = 'invocation-output-context'
        actual_reference = reference(root, actual)
        assert actual_reference['path'] in selected or actual_reference['path'] in AUTHORITIES, 'Target outside final selection: ' + actual_reference['path']
        relative = quote(Path(os.path.relpath(actual, view.parent)).as_posix(), safe='/.-_')
        replacement = urlunsplit(('', '', relative, location.query, location.fragment))
        if target.startswith('<'):
            replacement = '<' + replacement + '>'
        replacements.append((start, end, replacement))
        links.append({'target': target, 'resolution': resolution, 'actual': actual_reference})
    assert any(link['resolution'] == 'invocation-output-context' for link in links)
    verify_observation(report, text, links, root)
    for start, end, replacement in reversed(replacements):
        text = text[:start] + replacement + text[end:]
    data = text.encode('utf-8')
    return {'original': reference(root, report),
            'view': {'path': view.relative_to(root).as_posix(), 'sha256': hashlib.sha256(data).hexdigest()},
            'links': links}, data


def covered_reports(root, selected, final_base):
    result = []
    prefix = final_base.relative_to(root).as_posix() + '/'
    for name in sorted(selected):
        # This helper discovers raw tool narratives only. The staged audit
        # checks all source, delivery and derived-document links literally,
        # after these newly generated targets have been retained.
        if not name.startswith(prefix) or not name.endswith('.md'):
            continue
        report = root / name
        missing = [target for _, _, target, location in occurrences(report.read_bytes().decode('utf-8'))
                   if not literal_target(report, location).exists()]
        if not missing:
            continue
        assert report.name in ('syntax.md', 'visual.md'), 'Unknown raw-report context: ' + name
        assert all(target in LOCAL_ARTIFACTS or target in NOTICES for target in missing), 'Unknown missing raw target: ' + name
        result.append(report)
    assert len(result) == 952, 'Unexpected raw-report context coverage'
    return result


def sealed_collections(root, reports, final_base):
    sys.path.insert(0, str(root / 'scripts'))
    from t13_foundation_reports import RetainedFiles
    directories = set()
    for report in reports:
        for parent in report.parents:
            if not parent.is_relative_to(final_base):
                break
            if (parent / 'retained-files.sha256').is_file():
                directories.add(parent)
    # The unchanged verifier rejects any extra, missing, changed or linked file.
    return [RetainedFiles(directory) for directory in sorted(directories)]


def seal_records(root, collections):
    return [{'directory': item.directory.relative_to(root).as_posix(),
             'manifest': reference(root, item.directory / 'retained-files.sha256'),
             'result': reference(root, item.directory / 'result.properties'),
             'files-verified': len(item.expected)} for item in collections]


def selection_hash(selected):
    return hashlib.sha256(b''.join(name.encode() + b'\0' for name in sorted(selected))).hexdigest()


def index_markdown(reports):
    lines = ['# Navigable original tool reports', '',
             'These derived views replace only link destinations. The original tool',
             'narratives remain unchanged in their sealed observation trees. Their',
             'invocation-context links resolve through the [SHA-256 mapping](../report-link-retention.json)',
             'to the actual retained PDF, findings, raster or notice bytes. No artifact',
             'or directory is added to a sealed tree.', '', '| Original report | Navigable view |', '| --- | --- |']
    for entry in reports:
        source = Path(os.path.relpath(entry['original']['path'], VIEW_ROOT))
        view = Path(entry['view']['path']).relative_to(VIEW_ROOT)
        lines.append('| [' + entry['original']['path'] + '](' + source.as_posix() + ') | [view](' + view.as_posix() + ') |')
    return ('\n'.join(lines) + '\n').encode()


def verify_projection(root, selected):
    delivery = root / 'capabilities/evidence/T84-delivery'
    proof = json.loads((root / PROOF).read_text())
    freeze = json.loads((delivery / 'source-freeze.json').read_text())
    final_base = root / freeze['final-evidence-base']
    assert proof['result'] == 'pass' and proof['schema'] == 't84-report-link-views-v1'
    assert proof['candidate-identities'] == freeze['final-identities']
    reports = covered_reports(root, selected, final_base)
    assert [entry['original']['path'] for entry in proof['reports']] == [path.relative_to(root).as_posix() for path in reports]
    generated = {HELPER, PROOF, README} | {entry['view']['path'] for entry in proof['reports']}
    assert proof['base-selection-files'] == 463513
    assert re.fullmatch('[0-9a-f]{64}', proof['base-selection-paths-sha256'])
    assert generated <= selected
    for report, expected in zip(reports, proof['reports']):
        actual, data = project(root, report, final_base, selected)
        assert actual == expected, 'Changed or ambiguous report projection: ' + str(report)
        view = root / actual['view']['path']
        assert view.resolve(strict=True) == view and view.read_bytes() == data, 'Derived report text changed'
    assert (root / README).read_bytes() == index_markdown(proof['reports'])
    collections = sealed_collections(root, reports, final_base)
    assert seal_records(root, collections) == proof['sealed-collections']
    assert proof['original-sealed-trees-verified-before-and-after-generation'] is True
    assert proof['report-count'] == len(reports)
    assert proof['invocation-context-link-occurrences'] == sum(
        link['resolution'] == 'invocation-output-context' for entry in proof['reports'] for link in entry['links']) == 5432
    return proof


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--paths', type=Path, required=True, help='NUL-delimited original final selection')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[3]
    delivery = Path(__file__).resolve().parent
    freeze = json.loads((delivery / 'source-freeze.json').read_text())
    final_base = root / freeze['final-evidence-base']
    selected = set(args.paths.read_bytes().decode().split('\0')) - {''}
    assert len(selected) == 463513
    assert not (root / PROOF).exists() and not (root / VIEW_ROOT).exists(), 'Fresh projection outputs required'
    reports = covered_reports(root, selected, final_base)
    projected = [project(root, report, final_base, selected) for report in reports]
    collections = sealed_collections(root, reports, final_base)
    before = seal_records(root, collections)
    for entry, data in projected:
        path = root / entry['view']['path']
        path.parent.mkdir(parents=True, exist_ok=True)
        assert path.resolve() == path and path.is_relative_to(root / VIEW_ROOT)
        path.write_bytes(data)
    entries = [entry for entry, _ in projected]
    (root / README).write_bytes(index_markdown(entries))
    for collection in collections:
        collection.verify()
    assert before == seal_records(root, collections)
    for entry in entries:
        assert reference(root, root / entry['original']['path']) == entry['original']
        for link in entry['links']:
            assert reference(root, root / link['actual']['path']) == link['actual']
    proof = {'schema': 't84-report-link-views-v1', 'result': 'pass',
             'candidate-identities': freeze['final-identities'],
             'base-selection-files': len(selected), 'base-selection-paths-sha256': selection_hash(selected),
             'report-count': len(entries),
             'invocation-context-link-occurrences': sum(link['resolution'] == 'invocation-output-context'
                 for entry in entries for link in entry['links']),
             'original-sealed-trees-verified-before-and-after-generation': True,
             'sealed-collections': before, 'reports': entries}
    assert proof['invocation-context-link-occurrences'] == 5432
    (root / PROOF).write_text(json.dumps(proof, indent=2) + '\n')
    print(json.dumps({'result': 'pass', 'reports': len(entries),
                     'invocation-context-links': proof['invocation-context-link-occurrences'],
                     'sealed-collections': len(collections),
                     'sealed-files-verified-before-and-after': sum(len(item.expected) for item in collections)}, indent=2), flush=True)


if __name__ == '__main__':
    main()
