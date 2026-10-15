"""Verify the retained final evidence closure and the selected local commit bytes."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import runpy
import subprocess
import sys
import tempfile
import time
import zipfile
from urllib.parse import unquote, urlsplit

import yaml

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
sys.path.insert(0, str(root / 'scripts'))
import t05_foundation_reports

module = t05_foundation_reports.foundation()
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--staged', action='store_true')
args = parser.parse_args()
started = time.monotonic()
freeze = json.loads((delivery / 'source-freeze.json').read_text())
final_base = (root / freeze['final-evidence-base']).resolve(strict=True)
final_base.relative_to(root / 'capabilities/evidence/foundation')
assert final_base.name.startswith('T84-final-r')
final_prefix = final_base.relative_to(root).as_posix() + '/'
verified = {}
parsed = set()
external_markdown_prerequisites = {}


def verify_path(name, expected):
    path = root / name
    path.resolve(strict=True).relative_to(root)
    assert path.is_file(), 'Dangling file: ' + name
    actual = verified.setdefault(name, None)
    if actual is None:
        actual = module.sha256(path)
        verified[name] = actual
    assert actual == expected, 'Changed referenced bytes: ' + name
    return path


def references(value):
    if isinstance(value, dict):
        if set(value) == {'path', 'sha256'}:
            path = verify_path(value['path'], value['sha256'])
            parse(path)
        else:
            for name, child in value.items():
                if name == 'environment-observations':
                    # Raw observations contain declared container/install
                    # paths. Verify their retained payload bytes, then let
                    # live readiness validate the observed runtime facts.
                    for observation in child:
                        verify_path(observation['path'], observation['sha256'])
                else:
                    references(child)
    elif isinstance(value, list):
        for child in value:
            references(child)


def parse(path):
    name = path.relative_to(root).as_posix()
    if name in parsed or path.suffix not in ('.json', '.yaml', '.yml'):
        return
    # Candidate inputs include deliberately malformed parser-test fixtures.
    # Their bytes must match, but only actual evidence metadata is traversed.
    if not name.startswith(final_prefix) and name not in (
            'capabilities/foundation-evidence.yaml', 'target/foundation-0.1.0/build-inputs.json',
            'capabilities/evidence/T84-delivery/source-freeze.json'):
        return
    parsed.add(name)
    value = json.loads(path.read_text()) if path.suffix == '.json' else yaml.safe_load(path.read_text())
    references(value)


def markdown_targets(path, context=None):
    """Resolve local inline/image and reference-style links from delivery Markdown."""
    if context is not None:
        # Original tool narratives retain their invocation's output layout.
        # A separately verified, exact per-report projection resolves every
        # occurrence to retained bytes without changing sealed collections.
        assert context['original'] == {'path': path.relative_to(root).as_posix(), 'sha256': module.sha256(path)}
        for link in context['links']:
            yield verify_path(link['actual']['path'], link['actual']['sha256'])
        return
    source = re.sub(r'(?ms)^```[^\n]*\n.*?^```[^\n]*$', '', path.read_text())
    targets = re.findall(r'\[[^\]]*\]\(\s*(<[^>]+>|[^\s)]+)(?:\s+[\"\'][^\n]*?[\"\'])?\s*\)', source)
    targets += re.findall(r'(?m)^\s{0,3}\[[^\]]+\]:\s*(<[^>]+>|\S+)', source)
    for target in targets:
        target = re.sub(r':\d+(?=#|$)', '', target.strip('<>'))
        location = urlsplit(target)
        if location.scheme or location.netloc or not location.path:
            continue
        value = Path(unquote(location.path))
        resolved = (value if value.is_absolute() else path.parent / value).resolve(strict=True)
        try:
            resolved.relative_to(root)
        except ValueError:
            # Preserve the compiler's contract byte for byte. Its historical
            # skill is outside the authorized publication tree, so verify the
            # explicit installed prerequisite rather than copying or relinking it.
            assert path == delivery / 'execution-contract.md' and str(value) == '/home/box/.agents/skills/to-goal/SKILL.md', 'Unexpected outside-repository Markdown target: ' + target
            assert resolved.is_file(), 'Missing historical compiler skill: ' + target
            external_markdown_prerequisites[str(value)] = module.sha256(resolved)
            continue
        assert resolved.is_file() or resolved.is_dir(), 'Dangling Markdown link: ' + target
        yield resolved


contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
receipt = module.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
index = yaml.safe_load((root / 'capabilities/foundation-evidence.yaml').read_text())
assert index['candidate'] == receipt['candidate']
assert len(index['certifications']) == 108
assert sum(len(item['records']) for item in index['certifications']) == 452
assert len([item for item in index['certifications'] if item['obligation'] == 'providers']) == 8
parse(root / 'capabilities/foundation-evidence.yaml')
parse(root / 'target/foundation-0.1.0/build-inputs.json')
parse(delivery / 'source-freeze.json')

archives = []
archived = set()
archive_files = set()
for manifest_path in sorted((delivery / 'retained-inputs').glob('*-parts.json')):
    manifest = json.loads(manifest_path.read_text())
    whole = hashlib.sha256()
    count = 0
    part_paths = []
    with tempfile.TemporaryFile(dir='/tmp') as reassembled:
        for part in manifest['parts']:
            path = manifest_path.parent / part['path']
            data = path.read_bytes()
            assert count == part['offset'] and len(data) == part['bytes']
            assert hashlib.sha256(data).hexdigest() == part['sha256']
            assert len(data) <= 48 * 1024 * 1024
            whole.update(data)
            reassembled.write(data)
            count += len(data)
            part_paths.append(path)
        assert count == manifest['bytes'] and whole.hexdigest() == manifest['sha256']
        reassembled.seek(0)
        with zipfile.ZipFile(reassembled) as zipped:
            assert zipped.namelist() == [entry['path'] for entry in manifest['entries']]
            for entry in manifest['entries']:
                data = zipped.read(entry['path'])
                assert len(data) == entry['bytes'] and hashlib.sha256(data).hexdigest() == entry['sha256']
                path = verify_path(entry['path'], entry['sha256'])
                assert path.stat().st_size == entry['bytes']
                archived.add(entry['path'])
    archive_files.update(path.relative_to(root).as_posix() for path in part_paths + [manifest_path])
    archives.append({'manifest': manifest_path.relative_to(root).as_posix(),
                     'bytes': count, 'parts': len(part_paths), 'entries': len(manifest['entries'])})
assert len(archives) == 2

candidate_sha256 = json.loads((root / index['certifications'][0]['records'][0]['path']).read_text())['candidate-sha256']
result = {'result': 'pass', 'candidate-sha256': candidate_sha256,
          'referenced-files-verified': len(verified), 'metadata-files-traversed': len(parsed),
          'certification-tuples': 108, 'chain-records': 452, 'archives': archives}
if args.staged:
    selected = subprocess.check_output(['git', 'diff', '--cached', '--name-only', '-z'], cwd=root).decode().split('\0')
    selected = [name for name in selected if name]
    assert selected
    assert all(not name.startswith(('target/', '.build-cache/')) for name in selected)
    assert all(all(part != 'development' and not part.startswith('candidate-')
                   for part in Path(name).parts[:-1]) for name in selected), 'Development or candidate directory selected'
    assert all(not name.startswith('capabilities/evidence/foundation/T84-') or name.startswith(final_prefix) for name in selected)
    plan = json.loads((delivery / 'publication-plan.json').read_text())
    source_manifest = json.loads((delivery / 'publication-source-paths.json').read_text())
    assert plan['final-base'] == freeze['final-evidence-base']
    state = json.loads((root / plan['state']).read_text())
    assert state['result'] == 'pass' and not state['failed'] and not state['running']
    assert state['final-base'] == plan['final-base'] and state['candidate'] == receipt['candidate']
    assert set(state['completed']) == set(state['directories'])
    resume_path = final_base / 'recovery/resume-run3.json'
    assert plan['recovery-resume-proof'] == resume_path.relative_to(root).as_posix()
    resume = json.loads(resume_path.read_text())
    assert resume['result'] == 'pass' and resume['candidate-identities'] == freeze['final-identities']
    assert resume['certification-tuples'] == 4 and resume['chain-records'] == 20
    assert len(resume['preserved-completed-records']) == 10
    for record in resume['preserved-completed-records']:
        verify_path(record['path'], record['sha256'])
    assert plan['excluded-interrupted-replays'] == resume['excluded-interrupted-replays']
    excluded = [(root / name).resolve(strict=True) for name in resume['excluded-interrupted-replays']]
    assert excluded and len(excluded) == len(set(excluded))
    for attempt in excluded:
        assert attempt.parent == final_base / 'recovery/jdk17-hardened_worker'
        assert attempt.is_dir() and attempt.name.startswith('live-collection-')
        assert not any(name == attempt.relative_to(root).as_posix() or name.startswith(attempt.relative_to(root).as_posix() + '/') for name in selected), 'Interrupted replay selected'
    expected_directories = set(state['directories'].values()) | {
        (final_base / name).relative_to(root).as_posix() for name in ('providers', 'combined-index')}
    expected_adoptions = set()
    failed_directories = []
    for name in state['directories'].values():
        directory = root / name
        adoption_path = directory / 'adoption.json'
        if not adoption_path.exists():
            continue
        adoption = json.loads(adoption_path.read_text())
        assert adoption['result'] == 'pass' and adoption['candidate-identities'] == freeze['final-identities']
        adopted = adoption['adopted-unchanged-tuples']
        fresh = adoption['fresh-tuples']
        retained = adoption['retained-directories']
        assert len(adopted) == len(set(adopted)) == 7 and len(fresh) == len(set(fresh)) == 1
        assert set(adopted) <= set(retained) and len(retained) == len(set(retained))
        for fresh_name in fresh:
            (root / fresh_name).resolve(strict=True).relative_to(directory.resolve(strict=True))
        expected_directories.update(retained)
        expected_adoptions.add(adoption_path.relative_to(root).as_posix())
        failed_directories.append((root / adoption['failed-tuple-excluded']).resolve(strict=True))
    assert set(plan['tuple-adoption-proofs']) == expected_adoptions
    assert len(plan['final-directories']) == len(expected_directories) and set(plan['final-directories']) == expected_directories, 'Final directories differ from successful state and adoption proofs'
    for name in expected_directories:
        retained = (root / name).resolve(strict=True)
        retained.relative_to(final_base)
        for failed in failed_directories:
            failed.relative_to(final_base)
            assert failed != retained and retained not in failed.parents and failed not in retained.parents, 'Failed tuple overlaps publication selection'
    assert plan['baseline'] == source_manifest['baseline'] == 'dfe7eaea98533aa12449e677d9612fc4b964091a'
    assert set(plan['source-files']) == set(source_manifest['source-paths'])
    allowed = set(plan['source-files']) | {source_manifest['evidence-index']} | archive_files | {
        'capabilities/evidence/T84-delivery/retained-inputs/README.md'}
    allowed.update((delivery / name).relative_to(root).as_posix() for name in plan['curated-delivery-files'])
    for name in plan['final-directories']:
        directory = root / name
        directory.resolve(strict=True).relative_to(final_base)
        allowed.update(path.relative_to(root).as_posix() for path in directory.rglob('*')
                       if path.is_file() and not any(attempt in path.parents for attempt in excluded))
    assert set(selected) == allowed and len(selected) == plan['selected-file-count'], 'Index differs from explicit owned final publication selection'
    projection_module = runpy.run_path(str(delivery / 'retain-report-views.py'))
    projection = projection_module['verify_projection'](root, set(selected))
    references(projection)
    raw_contexts = {entry['original']['path']: entry for entry in projection['reports']}
    required = set(selected) | archive_files | {
        'capabilities/foundation-evidence.yaml',
        'capabilities/evidence/T84-delivery/source-freeze.json',
        'capabilities/evidence/T84-delivery/execution-contract.md',
    }
    linked_directories = set()
    markdown_links = 0
    for name in selected:
        if Path(name).suffix != '.md':
            continue
        for target in markdown_targets(root / name, raw_contexts.get(name)):
            markdown_links += 1
            relative = target.relative_to(root).as_posix()
            if target.is_file():
                required.add(relative)
            else:
                linked_directories.add(relative.rstrip('/') + '/')
    prerequisites = {}
    for tool in module.certification_tools():
        if tool['kind'] == 'external-tool':
            expected = module.properties(root / tool['pin'])[tool['hash-key']]
            verify_path(tool['path'], expected)
            prerequisites[tool['path']] = expected
    for name in verified:
        if name.startswith(('target/', '.build-cache/')):
            assert name in archived or prerequisites.get(name) == verified[name], 'Unretained runtime/cache input: ' + name
        else:
            required.add(name)
    assert subprocess.check_output(['git', 'rev-parse', '--show-object-format'], cwd=root).strip() == b'sha1'
    indexed = {}
    indexed_directories = set()
    with subprocess.Popen(['git', 'ls-files', '--stage', '-z'], cwd=root, stdout=subprocess.PIPE) as process:
        pending = b''
        while chunk := process.stdout.read(65536):
            rows = (pending + chunk).split(b'\0'); pending = rows.pop()
            for row in rows:
                header, name = row.split(b'\t', 1); name = name.decode()
                indexed_directories.update(prefix for prefix in linked_directories if name.startswith(prefix))
                if name in required:
                    mode, identity, stage = header.decode().split()
                    assert stage == '0' and mode in ('100644', '100755')
                    indexed[name] = identity
        assert not pending and process.wait() == 0
    assert required <= indexed.keys(), 'Referenced or selected files missing from resulting index: ' + str(sorted(required - indexed.keys())[:10])
    assert linked_directories <= indexed_directories, 'Linked directories absent from resulting Git tree: ' + str(sorted(linked_directories - indexed_directories))
    for name in sorted(required):
        path = root / name
        with path.open('rb') as stream:
            digest = hashlib.sha1(('blob ' + str(path.stat().st_size) + '\0').encode())
            while chunk := stream.read(1024 * 1024):
                digest.update(chunk)
        assert digest.hexdigest() == indexed[name], 'Staged bytes differ from reviewed working bytes: ' + name
    identities = set(indexed[name] for name in selected)
    output = subprocess.check_output(['git', 'cat-file', '--batch-check=%(objectname) %(objecttype) %(objectsize)'],
                                     input=('\n'.join(sorted(identities)) + '\n').encode(), cwd=root)
    blob_sizes = {}
    for line in output.decode().splitlines():
        identity, kind, size = line.split(); assert kind == 'blob'; blob_sizes[identity] = int(size)
    sizes = [blob_sizes[indexed[name]] for name in selected]
    # Identical qualification corpus bytes occur at several retained paths.
    # Git stores one blob per object identity; measure the distinct selected
    # blobs while retaining the logical path-byte total separately.
    selected_blob_bytes = sum(blob_sizes.values())
    assert max(sizes) < 100_000_000 and selected_blob_bytes < 2_000_000_000
    result['index-files-verified'] = len(required)
    result['local-markdown-links-verified'] = markdown_links
    result['local-markdown-directories-verified'] = len(linked_directories)
    result['immutable-tool-reports-with-explicit-link-context'] = len(raw_contexts)
    result['invocation-context-link-occurrences-resolved'] = projection['invocation-context-link-occurrences']
    result['derived-navigable-tool-report-views'] = len(projection['reports'])
    result['original-sealed-collections-verified'] = len(projection['sealed-collections'])
    result['explicit-external-markdown-prerequisites'] = external_markdown_prerequisites
    result['explicit-external-tool-prerequisites'] = prerequisites
    result['selected-files'] = len(selected)
    result['selected-bytes'] = sum(sizes)
    result['selected-distinct-blob-bytes'] = selected_blob_bytes
    result['selected-distinct-blobs'] = len(blob_sizes)
    result['largest-selected-file-bytes'] = max(sizes)
result['referenced-files-verified'] = len(verified)
result['metadata-files-traversed'] = len(parsed)
result['elapsed-monotonic-seconds'] = time.monotonic() - started
print(json.dumps(result, indent=2), flush=True)
