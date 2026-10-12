"""Recheck archived candidate bytes and immutable failed/partial evidence."""
import gzip
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import zipfile

import yaml

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('t83_preserved_r20', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)


def validate():
    seen = set()

    def bind(ref):
        key = (ref['path'], ref['sha256'])
        if key not in seen:
            assert ref == driver.reference(root, root / ref['path']), key
            seen.add(key)
        return root / ref['path']

    previous = json.loads((delivery / 'source-freeze-final-r9.json').read_text())
    preservation = json.loads((delivery / 'relaunch-preservation.json').read_text())
    references = preservation['retained'] + sum(
        (previous['superseded-candidate' + str(number)] for number in (6, 7, 8)), [])
    for ref in references:
        bind(ref)
    archives = []
    archived_sources = {}
    for number in (6, 7, 8, 9):
        directory = delivery / ('candidate-final-r' + str(number))
        receipt = json.loads((directory / 'build-inputs.json').read_text())
        expected = {item['path']: item['sha256'] for item in
                    receipt['candidate']['inputs'] + receipt['candidate']['artifacts']
                    + receipt['contract-inputs'] + receipt['harness']}
        expected.update({'target/foundation-0.1.0/' + name: driver.sha256(directory / name)
                         for name in ('build-inputs.json', 'build-command.json', 'build.txt')})
        entries_seen = {}
        for name in ('source-inputs.zip', 'artifacts-contracts-harness.zip'):
            manifest_path = directory / (name + '-parts.json')
            manifest = json.loads(manifest_path.read_text())
            entries = {item['path']: item['sha256'] for item in manifest['entries']}
            assert len(entries) == len(manifest['entries'])
            with tempfile.TemporaryFile(dir=root / '.build-cache') as joined:
                digest = hashlib.sha256()
                offset = 0
                for part in manifest['parts']:
                    path = directory / part['path']
                    assert path.is_file() and not path.is_symlink()
                    assert part['offset'] == offset and 0 < part['bytes'] <= 48 * 1024 * 1024
                    assert path.stat().st_size == part['bytes'] and driver.sha256(path) == part['sha256']
                    with path.open('rb') as stream:
                        while chunk := stream.read(1024 * 1024):
                            joined.write(chunk)
                            digest.update(chunk)
                    offset += part['bytes']
                assert offset == manifest['bytes'] and digest.hexdigest() == manifest['sha256']
                joined.seek(0)
                with zipfile.ZipFile(joined) as archive:
                    assert archive.namelist() == sorted(entries)
                    for path, recorded in entries.items():
                        assert recorded == expected[path]
                        with archive.open(path) as stream:
                            assert hashlib.file_digest(stream, 'sha256').hexdigest() == recorded
                        assert path not in entries_seen or entries_seen[path] == recorded
                        entries_seen[path] = recorded
            archives.append({'candidate-number': number, 'manifest': driver.reference(root, manifest_path),
                             'entries': len(entries), 'parts': len(manifest['parts']),
                             'bytes': offset, 'sha256': manifest['sha256']})
        assert entries_seen == expected
        archived_sources[number] = entries_seen
        print('Verified immutable candidate' + str(number) + ' source/stage archive entries.', flush=True)

    # Historical source/build references resolve to the archived candidate8
    # bytes, never to the now-corrected working source or freshly staged candidate.
    historical_seen = set()

    def historical(ref, kind=None, visiting=frozenset()):
        key = (ref['path'], ref['sha256'], kind)
        if key in historical_seen:
            return
        if ref['path'] in archived_sources[8]:
            assert archived_sources[8][ref['path']] == ref['sha256'], key
            historical_seen.add(key)
            return
        path = bind(ref)
        historical_seen.add(key)
        if kind == 'observation' or path.suffix not in ('.json', '.yaml'):
            return
        assert path not in visiting
        value = yaml.safe_load(path.read_text())

        def walk(node):
            if isinstance(node, dict):
                if set(node) == {'path', 'sha256'}:
                    historical(node, visiting=visiting | {path})
                else:
                    for name, child in node.items():
                        if node is value and kind == 'certification' and name == 'report':
                            historical(child, 'report', visiting | {path})
                        elif node is value and kind == 'report' and name == 'environment-observations':
                            for observation in child:
                                historical(observation, 'observation', visiting | {path})
                        else:
                            walk(child)
            elif isinstance(node, list):
                for child in node:
                    walk(child)

        walk(value)

    prior_path = delivery / 'refresh-final-r20-prior-index.yaml'
    original_prior = delivery / 'refresh-final-r16-prior-index.yaml'
    assert driver.sha256(prior_path) == driver.sha256(original_prior)
    prior = yaml.safe_load(prior_path.read_text())
    receipt8 = json.loads((delivery / 'candidate-final-r8/build-inputs.json').read_text())
    assert prior['candidate'] == receipt8['candidate']
    assert len(prior['certifications']) == 48
    assert sum(len(item['records']) for item in prior['certifications']) == 192
    for item in prior['environments']:
        historical(item['record'])
    for item in prior['certifications']:
        historical(item['configuration'])
        for ref in item['records']:
            historical(ref, 'certification')

    closeout = json.loads((delivery / 'refresh-final-r15/text-closeout/result.json').read_text())
    assert closeout['result'] == 'pass' and closeout['originals-preserved']
    text_paths = set()
    with gzip.open(bind(closeout['original-file-manifest']), 'rt', encoding='utf-8') as stream:
        for line in stream:
            digest, path = line.rstrip('\n').split('\t')
            assert path not in text_paths and path.startswith('capabilities/evidence/foundation/T83-final-r14/text/')
            bind({'path': path, 'sha256': digest})
            text_paths.add(path)
    assert len(text_paths) == closeout['original-file-count'] == 157778

    summaries = {}
    for label, filename in (('barcode', 'barcode-elapsed-preservation-r1.py'),
                            ('unicode', 'unicode-elapsed-preservation-r2.py')):
        spec = importlib.util.spec_from_file_location('t83_historical_' + label, delivery / 'diagnostics' / filename)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        summaries[label] = module.validate()
        assert summaries[label]['certifications-exported'] == 0
    assert summaries['barcode'] == json.loads((delivery / 'diagnostics/full-verify-barcode-elapsed-r1/sealed-diagnostic.json').read_text())
    receipt9 = json.loads((delivery / 'candidate-final-r9/build-inputs.json').read_text())
    failed = []
    for gate_number, directory, expected_method in (
            (10, 'full-verify-barcode-elapsed-r1', 'literalCode128FunctionsKeepAutoControlSwitchesDecodable[HARDENED_WORKER]'),
            (11, 'full-verify-unicode-elapsed-r1', 'sevenProfilesMatchTheIndependentGlyphGeometryInBothExecutionModes'),
            (12, 'full-verify-unicode-elapsed-r3', 'sevenProfilesMatchTheIndependentGlyphGeometryInBothExecutionModes')):
        path = delivery / 'diagnostics' / directory / 'preservation.json'
        record = json.loads(path.read_text())
        assert record['candidate'] == receipt9['candidate']
        assert record['source-staged-index-unchanged'] and record['diagnostics-exported-certifications'] == 0
        assert record['failed-method'] == expected_method
        assert record['original-index-sha256'] == driver.sha256(prior_path)
        for ref in record['original-files'] + record['retained-inputs']:
            bind(ref)
        gate_path = delivery / 'validation' / ('full-verify-final-r' + str(gate_number) + '-result.json')
        gate = json.loads(gate_path.read_text())
        assert gate['exit-code'] == 1
        bind(gate['log'])
        failed.append({'gate': driver.reference(root, gate_path),
                       'preservation': driver.reference(root, path), 'original-reports': len(record['original-files'])})
    assert not (delivery / 'refresh-final-r19/results.json').exists()
    return {'diagnostics-exported-certifications': 0, 'originals-preserved': True,
            'candidate9-superseded': True, 'verified-archives': archives,
            'previous-index': driver.reference(root, prior_path), 'previous-tuples': 48, 'previous-chains': 192,
            'candidate8-text-originals-rehashed': len(text_paths),
            'candidate8-transitive-references-verified': len(historical_seen),
            'failed-full-verifies': failed, 'diagnostic-validators': summaries,
            'verified-references': len(seen), 'unexecuted-r19-is-not-evidence': True}


if __name__ == '__main__':
    import sys
    destination = delivery / sys.argv[1]
    assert not destination.exists()
    driver.write_json(destination, validate())
    print('Superseded candidates/failed verifies and original text evidence are intact; zero new certifications.', flush=True)
