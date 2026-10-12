"""T22 guard controls are synthetic; they never certify a workload or environment."""
import copy
import gzip
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
import t22_foundation_reports as reports
import t21_foundation_reports as worker
from t13_foundation_reports import RetainedFiles
from t20_foundation_reports import compare_originals
from t22_semantics import blank_graph


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


EXIT_GRACE_DIAGNOSTIC = ('T22_WORKER_EXIT_GRACE The Worker did not terminate after completing its response. '
    'elapsedSinceFinishedMillis=30014; exitGraceSeconds=30; childAlive=true; waitIterations=1190; '
    'maximumPollMillis=68; childExitCode=unavailable; childPid=2203; childState=S; threadStates=S; sampledThreads=1\n')


def properties(path, values):
    path.write_text(''.join(name + '=' + str(value) + '\n' for name, value in values.items()))


def synthetic_scale_artifacts(directory, profile):
    names = {'pages': ('commands.txt', 'generated-5000-pages.pdf'),
             'input': ('consumed-input.pdf.gz', 'header.bin', 'tail.bin'),
             'concurrency': ('commands.txt', 'concurrent-0.pdf', 'concurrent-1.pdf', 'concurrent-excess.pdf')}
    for name in names[profile]:
        (directory / name).write_bytes(b'synthetic source binding guard; never a scale certification\n')


def seal(run, result=None):
    manifest = run / 'retained-files.sha256'
    manifest.write_text(''.join(digest(path) + '  ' + path.relative_to(run).as_posix() + '\n'
        for path in sorted(run.rglob('*')) if path.is_file() and path not in (run / 'result.properties', manifest)))
    result = result or {'profile': reports.PROFILE, 'native-execution-profile': 'HARDENED_WORKER',
        'facade-execution-profile': 'IN_PROCESS', **dict.fromkeys(reports.CHAINS, 'pass')}
    properties(run / 'result.properties', {**result, 'retained-files-sha256': digest(manifest)})


class T22FoundationTest(unittest.TestCase):
    def authority(self, root):
        import shutil
        for name in ('T21-hardened-worker', 'T22-worker-recovery-scale'):
            destination = root / ('capabilities/profiles/' + name)
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copytree(ROOT / destination.relative_to(root), destination)

    def test_inventory_requires_every_case_and_actual_profile(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); self.authority(root)
            self.assertEqual(180, len(reports.mandatory_tests(root)[0]))
            path = root / reports.TESTS; original = path.read_text(); lines = original.splitlines()
            for value in ('\n'.join(lines[:-1]) + '\n', original + lines[0] + '\n', original.replace('[HARDENED_WORKER]', '[UNDECLARED]')):
                path.write_text(value)
                with self.assertRaises(ValueError): reports.mandatory_tests(root)
            path.write_text(original)
            path = root / reports.INVENTORY; inventory = json.loads(path.read_text())
            for value in (['pages', 'input'], ['pages', 'input', 'substitute'], []):
                path.write_text(json.dumps({**inventory, 'scale-profiles': value}))
                with self.assertRaises(ValueError): reports.mandatory_tests(root)

    def test_skips_duplicates_and_resealed_failed_named_execution_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); self.authority(root)
            scope = root / 'scope'; scope.mkdir()
            plan = {'contract-tests-command': ['synthetic-command'], 'required-test-count': 180}
            (scope / 'contract-tests-command.json').write_text(json.dumps(plan['contract-tests-command']))
            boundary = scope / 'boundary-observations'; boundary.mkdir()
            (boundary / 'witness.txt').write_text('synthetic boundary guard\n')
            seal(boundary, {'profile': 'T22-recovery-scale-boundary', 'result': 'pass', 'required-case-count': '180'})
            tests, _ = reports.mandatory_tests(root)
            transcript = 'JUnit version 4.13.2\n' + ''.join('T22 CASE ' + case + ' = PASS\n' for case in sorted(tests))
            transcript += 'T22 required execution: tests=180, failures=0, ignored=0, assumptions=0, duplicates=0\nOK (180 tests)\n'
            transcript += 'T22 boundary retained-sha256=' + digest(boundary / 'retained-files.sha256') + '\n'
            transcript += EXIT_GRACE_DIAGNOSTIC
            path = scope / 'contract-tests.txt'; path.write_text(transcript)
            reports.require_contract_tests(root, scope, plan)
            case = 'T22 CASE ' + sorted(tests)[0] + ' = PASS\n'
            for variant in (transcript.replace(case, ''), transcript + case, transcript.replace(case, case.replace('PASS', 'FAIL')),
                    transcript.replace('ignored=0', 'ignored=1'), transcript.replace('assumptions=0', 'assumptions=1'),
                    transcript.replace('duplicates=0', 'duplicates=1'), transcript.replace('T22 CASE', 'T21 CASE'),
                    transcript.replace('retained-sha256=', 'unbound-sha256='),
                    transcript.replace(EXIT_GRACE_DIAGNOSTIC, ''), transcript + EXIT_GRACE_DIAGNOSTIC,
                    transcript.replace('elapsedSinceFinishedMillis=30014', 'elapsedSinceFinishedMillis=1000'),
                    transcript.replace('elapsedSinceFinishedMillis=30014', 'elapsedSinceFinishedMillis=60000'),
                    transcript.replace('exitGraceSeconds=30', 'exitGraceSeconds=1'),
                    transcript.replace('childAlive=true', 'childAlive=false'),
                    transcript.replace('waitIterations=1190', 'waitIterations=0'),
                    transcript.replace('maximumPollMillis=68', 'maximumPollMillis=40000'),
                    transcript.replace('childExitCode=unavailable', 'childExitCode=0'),
                    transcript.replace('childPid=2203', 'childPid=unavailable'),
                    transcript.replace('childState=S', 'childState=unavailable'),
                    transcript.replace('threadStates=S', 'threadStates=unavailable'),
                    transcript.replace('sampledThreads=1', 'sampledThreads=2'),
                    transcript.replace('sampledThreads=1', 'sampledThreads=9'),
                    transcript.replace('sampledThreads=1', 'sampledThreads=1; commandLine=secret')):
                path.write_text(variant)
                with self.assertRaises(ValueError): reports.require_contract_tests(root, scope, plan)

    def test_archive_is_exact_consumed_bytes_and_a_miniature_never_qualifies(self):
        with tempfile.TemporaryDirectory() as directory:
            run = Path(directory); length = 4096
            header = (ROOT / 'capabilities/profiles/T22-worker-recovery-scale/input-header.bin').read_bytes()
            tail = b'synthetic exact-tail guard\n'
            data = header + b'\n' * (length - len(header) - len(tail)) + tail
            (run / 'header.bin').write_bytes(header); (run / 'tail.bin').write_bytes(tail)
            archive = run / 'consumed-input.pdf.gz'; archive.write_bytes(gzip.compress(data, mtime=0))
            values = {'input-length': str(length), 'input-sha256': hashlib.sha256(data).hexdigest(),
                'input-tail-offset': str(length - len(tail)), 'header-sha256': hashlib.sha256(header).hexdigest(),
                'tail-sha256': hashlib.sha256(tail).hexdigest()}
            seal(run); reports.archived_input(ROOT, RetainedFiles(run), run, values, required_length=length)
            with self.assertRaises(ValueError): reports.archived_input(ROOT, RetainedFiles(run), run, values)
            for changed in (data[:-1], data + b'\n', data[:100] + b'X' + data[101:]):
                archive.write_bytes(gzip.compress(changed, mtime=0)); seal(run)
                # Rehashing and resealing altered data does not repair its construction.
                forged = {**values, 'input-sha256': hashlib.sha256(changed).hexdigest()}
                with self.assertRaises(ValueError): reports.archived_input(ROOT, RetainedFiles(run), run, forged, required_length=length)
            archive.unlink()
            with self.assertRaises(ValueError): RetainedFiles(run)

    def test_actual_graph_controls_count_resources_geometry_and_parent(self):
        graph = {'pages': [1], 'qpdf': [{}, {'trailer': {'value': {'/Root': '1 0 R'}},
            'obj:1 0 R': {'value': {'/Type': '/Catalog', '/Pages': '2 0 R'}},
            'obj:2 0 R': {'value': {'/Type': '/Pages', '/Count': 1, '/Kids': ['3 0 R']}},
            'obj:3 0 R': {'value': {'/Type': '/Page', '/Parent': '2 0 R', '/MediaBox': [0, 0, 612, 792], '/Resources': {}}}}]}
        self.assertTrue(blank_graph(graph, 1)); self.assertFalse(blank_graph(graph, 5000))
        for key, value in (('/Resources', {'/Font': {}}), ('/MediaBox', [0, 0, 72, 72]),
                           ('/MediaBox', [False, 0, 612, 792]), ('/Parent', '9 0 R'), ('/Contents', '4 0 R')):
            altered = copy.deepcopy(graph); altered['qpdf'][1]['obj:3 0 R']['value'][key] = value
            with self.assertRaises(ValueError): blank_graph(altered, 1)
        for count in (True, 1.0):
            altered = copy.deepcopy(graph); altered['qpdf'][1]['obj:2 0 R']['value']['/Count'] = count
            with self.assertRaises(ValueError): blank_graph(altered, 1)

    def test_original_findings_artifacts_and_controls_cannot_be_resealed(self):
        import shutil
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); original = root / 'observations'; original.mkdir()
            for name in ('syntax.txt', 'negative.txt', 'consumed-input.pdf.gz'):
                (original / name).write_bytes(b'synthetic original guard bytes\n')
            seal(original)
            replay = root / 'replay'; shutil.copytree(original, replay / 'observations')
            compare_originals(root, RetainedFiles(original), replay, {}, worker.foundation(), reports.normalize_extra)
            for name in ('syntax.txt', 'negative.txt', 'consumed-input.pdf.gz'):
                (original / name).write_bytes(b'altered but resealed\n'); seal(original)
                with self.assertRaises(ValueError): compare_originals(root, RetainedFiles(original), replay, {}, worker.foundation(), reports.normalize_extra)
                (original / name).write_bytes(b'synthetic original guard bytes\n'); seal(original)

    def test_resealed_non_trailer_id_findings_cannot_be_normalized_away(self):
        import shutil
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); original = root / 'observations'; (original / 'semantic').mkdir(parents=True)
            trailer = {'/ID': ['b:original', 'b:original'], '/Root': '1 0 R', '/Type': '/XRef'}
            graph = {'qpdf': [{}, {'trailer': {'value': trailer},
                'obj:1 0 R': {'value': {'/Type': '/Catalog'}},
                'obj:4 0 R': {'stream': {'dict': copy.deepcopy(trailer)}}}]}
            path = original / 'semantic/qpdf.json'; path.write_text(json.dumps(graph))
            seal(original); replay = root / 'replay'; shutil.copytree(original, replay / 'observations')
            # Legitimate trailer IDs differ together in the logical and physical XRef trailer.
            for item in (graph['qpdf'][1]['trailer']['value'], graph['qpdf'][1]['obj:4 0 R']['stream']['dict']):
                item['/ID'] = ['b:fresh', 'b:fresh']
            path.write_text(json.dumps(graph)); seal(original)
            compare_originals(root, RetainedFiles(original), replay, {}, worker.foundation(), reports.normalize_extra)
            graph['qpdf'][1]['obj:1 0 R']['value']['/ID'] = ['forged catalog finding']
            path.write_text(json.dumps(graph)); seal(original)
            with self.assertRaisesRegex(ValueError, 'findings/observations differ'):
                compare_originals(root, RetainedFiles(original), replay, {}, worker.foundation(), reports.normalize_extra)

    def test_resealed_json_scalar_types_cannot_be_normalized_away(self):
        import shutil
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); original = root / 'observations'; (original / 'semantic').mkdir(parents=True)
            graph = {'qpdf': [{}, {'trailer': {'value': {'/Root': '1 0 R'}},
                'obj:2 0 R': {'value': {'/Count': 1, '/MediaBox': [0, 0, 612, 792]}}}]}
            path = original / 'semantic/qpdf.json'; path.write_text(json.dumps(graph)); seal(original)
            replay = root / 'replay'; shutil.copytree(original, replay / 'observations')
            compare_originals(root, RetainedFiles(original), replay, {}, worker.foundation(), reports.normalize_extra)
            for count, coordinate in ((True, 0), (1.0, 0), (1, False), (1, 0.0)):
                altered = copy.deepcopy(graph)
                altered['qpdf'][1]['obj:2 0 R']['value'].update({'/Count': count, '/MediaBox': [coordinate, 0, 612, 792]})
                path.write_text(json.dumps(altered)); seal(original)
                with self.assertRaisesRegex(ValueError, 'findings/observations differ'):
                    compare_originals(root, RetainedFiles(original), replay, {}, worker.foundation(), reports.normalize_extra)

    def test_visual_authority_links_bind_same_file_across_replay_depth(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); authority = root / 'expected.png'; authority.write_bytes(b'synthetic authority guard')
            other = root / 'other.png'; other.write_bytes(b'altered authority')
            original = root / 'original/observations'; original.mkdir(parents=True)
            replay = root / 'replay/nested'; fresh = replay / 'observations'; fresh.mkdir(parents=True)
            name = 'scale/pages/page-1/visual.md'
            for run in (original, fresh):
                path = run / name; path.parent.mkdir(parents=True)
                path.write_text('[authority](' + os.path.relpath(authority, path.parent) + ')\n')
                seal(run)
            compare_originals(root, RetainedFiles(original), replay, {}, worker.foundation(), reports.normalize_extra)
            path = original / name
            path.write_text('[authority](' + os.path.relpath(other, path.parent) + ')\n'); seal(original)
            with self.assertRaisesRegex(ValueError, 'findings/observations differ'):
                compare_originals(root, RetainedFiles(original), replay, {}, worker.foundation(), reports.normalize_extra)

    def test_identical_original_pdf_hashes_can_have_distinct_replay_ids(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); original = root / 'observations'; original.mkdir()
            replay = root / 'replay'; fresh = replay / 'observations'; fresh.mkdir(parents=True)
            for index in range(2):
                for run, identifier in ((original, b'1111'), (fresh, b'2222' if index == 0 else b'3333')):
                    scope = run / ('product-' + str(index)); scope.mkdir()
                    product = scope / 'blank.pdf'
                    product.write_bytes(b'%PDF-1.7\n/Count 1\n/ID[<' + identifier + b'><' + identifier + b'>]\n%%EOF\n')
                    properties(scope / 'standards.properties', {'input-sha256': digest(product), 'result': 'pass'})
                seal(original); seal(fresh)
            compare_originals(root, RetainedFiles(original), replay, {}, worker.foundation(), reports.normalize_extra)
            # Changing document meaning remains a failure even with a resealed tree.
            product = original / 'product-0/blank.pdf'
            product.write_bytes(product.read_bytes().replace(b'/Count 1', b'/Count 2'))
            properties(product.parent / 'standards.properties', {'input-sha256': digest(product), 'result': 'pass'})
            seal(original)
            with self.assertRaisesRegex(ValueError, 'PDF/control differs'):
                compare_originals(root, RetainedFiles(original), replay, {}, worker.foundation(), reports.normalize_extra)

    def test_resealed_recovery_child_findings_keep_exact_artifact_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            run = Path(directory)
            for index, name in enumerate(reports.PRODUCTS):
                scope = run / name; scope.mkdir()
                product = scope / 'blank.pdf'
                identifier = ('%04x' % index).encode()
                product.write_bytes(b'%PDF-1.7\n/ID[<' + identifier + b'><' + identifier + b'>]\n%%EOF\n')
                exact = digest(product)
                properties(scope / 'result.properties', {'input-sha256': exact, **dict.fromkeys(reports.CHAINS[:-1], 'pass')})
                for checker in ('pdfcpu', 'arlington'):
                    child = scope / checker; child.mkdir()
                    properties(child / 'standards.properties', {'result': 'pass', 'input-sha256': exact})
                for chain in reports.CHAINS[:-1]:
                    (scope / (chain + '.txt')).write_text('Input exact SHA-256: ' + exact + '\nsynthetic exact-identity guard\n')
            seal(run); reports.published_document_observations(run, RetainedFiles(run))
            first, other = (run / name for name in reports.PRODUCTS[:2])
            path = first / 'arlington/standards.properties'
            properties(path, {'result': 'pass', 'input-sha256': digest(other / 'blank.pdf')}); seal(run)
            with self.assertRaisesRegex(ValueError, 'child standards identity'):
                reports.published_document_observations(run, RetainedFiles(run))
            properties(path, {'result': 'pass', 'input-sha256': digest(first / 'blank.pdf')})
            for chain in reports.CHAINS[:-1]:
                path = first / (chain + '.txt')
                original = path.read_text()
                path.write_text(original.replace(digest(first / 'blank.pdf'), digest(other / 'blank.pdf'))); seal(run)
                with self.assertRaisesRegex(ValueError, 'raw finding identifies another artifact'):
                    reports.published_document_observations(run, RetainedFiles(run))
                path.write_text(original)

    def test_negative_graph_findings_bind_raw_json_and_actual_producer(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); run = root / 'observations'; run.mkdir()
            scripts = root / 'scripts'; (scripts / 'container-bin').mkdir(parents=True)
            for name in ('t22_semantics.py', 'container-bin/qpdf'):
                (scripts / name).write_text('synthetic producer identity guard\n')
            artifact = run / 'blank.pdf'; artifact.write_bytes(b'synthetic input guard\n')
            scope = run / 'semantic-wrong-count'; scope.mkdir()
            graph = {'pages': [1], 'qpdf': [{}, {'trailer': {'value': {'/Root': '1 0 R'}},
                'obj:1 0 R': {'value': {'/Type': '/Catalog', '/Pages': '2 0 R'}},
                'obj:2 0 R': {'value': {'/Type': '/Pages', '/Count': 1, '/Kids': ['3 0 R']}},
                'obj:3 0 R': {'value': {'/Type': '/Page', '/Parent': '2 0 R', '/MediaBox': [0, 0, 612, 792], '/Resources': {}}}}]}
            (scope / 'qpdf.json').write_text(json.dumps(graph))
            values = {'result': 'fail', 'input-sha256': digest(artifact), 'page-count': 2,
                'all-pages-inspected': False, 'exit-code': 0,
                'observer-sha256': digest(scripts / 't22_semantics.py'),
                'qpdf-sha256': digest(scripts / 'container-bin/qpdf'), 'qpdf-json-sha256': digest(scope / 'qpdf.json'),
                'command': ['/workspace/scripts/container-bin/qpdf', '--json=2', '--json-key=qpdf', '--json-key=pages',
                    '/workspace/' + artifact.relative_to(root).as_posix()]}
            path = scope / 'result.json'; path.write_text(json.dumps(values)); seal(run)
            reports.semantic_observation(root, RetainedFiles(run), scope, artifact, digest(artifact), 2, False)
            for key, changed in (('qpdf-json-sha256', '0' * 64), ('observer-sha256', '0' * 64),
                    ('qpdf-sha256', '0' * 64), ('command', []), ('input-sha256', '0' * 64), ('exit-code', 2)):
                path.write_text(json.dumps({**values, key: changed})); seal(run)
                with self.assertRaisesRegex(ValueError, 'graph findings/producer/identity'):
                    reports.semantic_observation(root, RetainedFiles(run), scope, artifact, digest(artifact), 2, False)

    def test_scale_counters_and_artifacts_remain_bound_to_actual_stdout(self):
        with tempfile.TemporaryDirectory() as directory:
            scope = Path(directory); run = scope / 'observations'; run.mkdir()
            transcript = []
            for profile in ('pages', 'input', 'concurrency'):
                source = scope / 'scale-observations' / profile; source.mkdir(parents=True)
                target = run / 'scale' / profile; target.mkdir(parents=True)
                values = {'wall-nanos': '1234567', **{'policy.' + key: value for key, value in reports.POLICY.items()}}
                dimensions = {'input': 'acceptedInput', 'pages': 'observedPages', 'objects': 'observedObjects',
                    'decompressed': 'decompressed', 'pixels': 'decodedPixels', 'memory': 'peakMemory',
                    'temporary': 'peakTemporary', 'elapsed': 'accountedElapsed'}
                stdout = {'profile': profile, 'wallMillis': '1'}
                for key, field in dimensions.items():
                    values['usage.' + key] = stdout[field] = 'PT1S' if key == 'elapsed' else '2'
                stdout.update({'policy' + key.capitalize(): value for key, value in reports.POLICY.items()})
                for location in (source, target):
                    properties(location / 'observations.properties', values)
                    synthetic_scale_artifacts(location, profile)
                transcript.append('T22_SCALE ' + ' '.join(key + '=' + value for key, value in stdout.items()))
            log = scope / 'contract-tests.txt'; log.write_text('\n'.join(transcript) + '\n')
            seal(run); reports.bind_scale_execution(scope, RetainedFiles(run))
            for location in (scope / 'scale-observations/pages', run / 'scale/pages'):
                path = location / 'observations.properties'
                path.write_text(path.read_text().replace('usage.memory=2', 'usage.memory=3'))
            seal(run)
            with self.assertRaisesRegex(ValueError, 'original stdout'):
                reports.bind_scale_execution(scope, RetainedFiles(run))
            log.write_text('\n'.join(transcript[:-1]) + '\n')
            with self.assertRaisesRegex(ValueError, 'stdout omitted'):
                reports.bind_scale_execution(scope, RetainedFiles(run))

    def test_modeled_observations_cannot_be_normalized_away(self):
        for key in ('usage.memory', 'usage.temporary', 'usage.input', 'usage.pages', 'output.0.objects'):
            left, right = reports.normalize_extra('scale/pages/observations.properties', {key: '1'}, {key: '2'}, None, None, None)
            self.assertNotEqual(left, right)
        self.assertEqual(({}, {}), reports.normalize_extra('scale/pages/observations.properties',
            {'wall-nanos': '1', 'usage.elapsed': 'PT1S'}, {'wall-nanos': '2', 'usage.elapsed': 'PT2S'}, None, None, None))

    def test_independently_bound_input_memory_peaks_can_differ(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            scopes = [root / 'original', root / 'replay']
            for scope, memory in zip(scopes, (1060, 1068)):
                transcript = []
                for profile in ('pages', 'input', 'concurrency'):
                    policy = {**reports.POLICY, 'concurrency': '2' if profile == 'concurrency' else '4'}
                    usage = {'input': '1073741824' if profile == 'input' else '0',
                             'pages': '5000' if profile == 'pages' else '1',
                             'objects': '5001' if profile == 'pages' else '3',
                             'decompressed': '0', 'pixels': '0',
                             'memory': str(memory) if profile == 'input' else '1068',
                             'temporary': '1073741824' if profile == 'input' else '1000',
                             'elapsed': 'PT1S'}
                    values = {'profile': profile, 'wall-nanos': '1234567',
                              **{'policy.' + k: v for k, v in policy.items()},
                              **{'usage.' + k: v for k, v in usage.items()}}
                    for selected in (scope / 'scale-observations', scope / 'observations/scale'):
                        target = selected / profile
                        target.mkdir(parents=True)
                        properties(target / 'observations.properties', values)
                        synthetic_scale_artifacts(target, profile)
                    dimensions = {'input': 'acceptedInput', 'pages': 'observedPages',
                                  'objects': 'observedObjects', 'decompressed': 'decompressed',
                                  'pixels': 'decodedPixels', 'memory': 'peakMemory',
                                  'temporary': 'peakTemporary', 'elapsed': 'accountedElapsed'}
                    stdout = {'profile': profile, 'wallMillis': '1',
                              **{dimensions[k]: v for k, v in usage.items()},
                              **{'policy' + k.capitalize(): v for k, v in policy.items()}}
                    transcript.append('T22_SCALE ' + ' '.join(k + '=' + v for k, v in stdout.items()))
                (scope / 'contract-tests.txt').write_text('\n'.join(transcript) + '\n')
                seal(scope / 'observations')
            original, fresh = [RetainedFiles(scope / 'observations') for scope in scopes]
            normalize = reports.bound_scale_normalizer(scopes[0], original, scopes[1], fresh)
            compare_originals(root, original, scopes[1], {}, worker.foundation(), normalize)
            for files, expected in zip((original, fresh), ('1060', '1068')):
                self.assertEqual(expected, files.properties(files.directory / 'scale/input/observations.properties')['usage.memory'])

            # A retained copy cannot replace a missing actual producer artifact.
            for scope in scopes:
                for profile in ('pages', 'input', 'concurrency'):
                    source = scope / 'scale-observations' / profile
                    for path in sorted(source.iterdir()):
                        data = path.read_bytes()
                        path.unlink()
                        with self.subTest(missing_source=str(path)):
                            with self.assertRaisesRegex(ValueError, 'source artifact inventory'):
                                reports.bound_scale_normalizer(scopes[0], original, scopes[1], fresh)
                        path.symlink_to(scope / 'observations/scale' / profile / path.name)
                        with self.subTest(aliased_source=str(path)):
                            with self.assertRaisesRegex(ValueError, 'source artifact inventory'):
                                reports.bound_scale_normalizer(scopes[0], original, scopes[1], fresh)
                        path.unlink()
                        path.write_bytes(data)
                    unexpected = source / 'undeclared.txt'
                    unexpected.write_text('synthetic extra-source guard')
                    with self.assertRaisesRegex(ValueError, 'source artifact inventory'):
                        reports.bound_scale_normalizer(scopes[0], original, scopes[1], fresh)
                    unexpected.unlink()
                    saved = {path: path.read_bytes() for path in source.iterdir()}
                    for path in saved:
                        path.unlink()
                    with self.assertRaisesRegex(ValueError, 'source artifact inventory'):
                        reports.bound_scale_normalizer(scopes[0], original, scopes[1], fresh)
                    for path, data in saved.items():
                        path.write_bytes(data)

            # A bound normalizer cannot hide altered values or other modeled fields.
            name = 'scale/input/observations.properties'
            left, right = [files.properties(files.directory / name) for files in (original, fresh)]
            for key in ('usage.temporary', 'usage.input', 'usage.pages', 'usage.objects',
                        'policy.memory', 'input-sha256', 'output.0.memory'):
                with self.subTest(strict_field=key):
                    changed = {**right, key: 'altered'}
                    a, b = normalize(name, dict(left), changed, None, None, None)
                    self.assertNotEqual(a, b)
            with self.assertRaisesRegex(ValueError, 'independently bound'):
                normalize(name, left, {**right, 'usage.memory': '1076'}, None, None, None)
            a, b = normalize('other/observations.properties', left, right, None, None, None)
            self.assertNotEqual(a, b)

            # Even resealing both copied and source properties cannot manufacture
            # a valid memory observation. Each must match its execution and policy.
            path = fresh.directory / name
            source = scopes[1] / 'scale-observations/input/observations.properties'
            original_log = (scopes[1] / 'contract-tests.txt').read_text()
            def change_input_stdout(old, new):
                return '\n'.join(line.replace(old, new) if 'profile=input ' in line else line
                                 for line in original_log.splitlines()) + '\n'
            for value in (None, 'invalid', '0', '-1', '268435457'):
                with self.subTest(invalid_memory=value):
                    changed = dict(right)
                    if value is None:
                        changed.pop('usage.memory')
                    else:
                        changed['usage.memory'] = value
                    properties(path, changed)
                    properties(source, changed)
                    (scopes[1] / 'contract-tests.txt').write_text(change_input_stdout('peakMemory=1068', 'peakMemory=' + str(value)))
                    seal(fresh.directory)
                    with self.assertRaises((ValueError, KeyError)):
                        reports.bound_scale_normalizer(scopes[0], original, scopes[1], RetainedFiles(fresh.directory))
            properties(path, right)
            properties(source, right)
            (scopes[1] / 'contract-tests.txt').write_text(change_input_stdout('peakMemory=1068', 'peakMemory=1076'))
            seal(fresh.directory)
            with self.assertRaisesRegex(ValueError, 'original stdout'):
                reports.bound_scale_normalizer(scopes[0], original, scopes[1], RetainedFiles(fresh.directory))
            (scopes[1] / 'contract-tests.txt').write_text(original_log)
            properties(path, {**right, 'usage.memory': '1076'})
            seal(fresh.directory)
            with self.assertRaisesRegex(ValueError, 'actual mandatory execution'):
                reports.bound_scale_normalizer(scopes[0], original, scopes[1], RetainedFiles(fresh.directory))

            # A self-consistent forged policy still cannot widen the finite bound.
            changed = {**right, 'policy.memory': '536870912'}
            properties(path, changed)
            properties(source, changed)
            (scopes[1] / 'contract-tests.txt').write_text(change_input_stdout('policyMemory=268435456', 'policyMemory=536870912'))
            seal(fresh.directory)
            with self.assertRaisesRegex(ValueError, 'finite policy'):
                reports.bound_scale_normalizer(scopes[0], original, scopes[1], RetainedFiles(fresh.directory))

    def test_scope_and_producers_are_exact_and_cannot_be_relabeled(self):
        driver = worker.foundation(); case = driver.certification_case('recovery')
        self.assertEqual(('HARDENED_WORKER',), case['execution-profiles'])
        self.assertEqual(reports.CHAINS, case['chains']); self.assertEqual(180, case['test-count'])
        inventory = json.loads((ROOT / reports.INVENTORY).read_text())
        self.assertEqual(set(inventory['test-classes']),
                         set(case['test-classes']) | set(driver.ARTIFACT_CONTRACT_TESTS))
        self.assertEqual({'syntax': 'qpdf', 'standards': 'arlington', 'semantic': 'folio-pdf-t22',
                         'visual': 'pdfium-cli', 'contract': 'folio-pdf-t22'}, driver.certification_producers(case))
        with self.assertRaises(ValueError): driver.execution_plan(Path('/repo'), Path('/repo/scope'), 'image', Path('/native/bin/helper'), 'cp', case, 'IN_PROCESS')
        with tempfile.TemporaryDirectory() as directory:
            run = Path(directory); seal(run)
            with self.assertRaises(ValueError): reports.collect_reports(run, run, 'IN_PROCESS')
            values = worker.foundation().properties(run / 'result.properties'); values['profile'] = worker.PROFILE
            properties(run / 'result.properties', values)
            with self.assertRaises(ValueError): reports.collect_reports(run, run, 'HARDENED_WORKER')

    def test_candidate_configuration_runtime_and_all_inputs_are_bound(self):
        import yaml
        driver = worker.foundation()
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, {'FOLIO_FOUNDATION_PYTHON_ROOT': ''}):
            root = Path(directory); self.authority(root)
            scope = root / 'certifications/jdk8-hardened_worker'; scope.mkdir(parents=True)
            environment_dir = scope.parent / 'jdk8-environment'; environment_dir.mkdir()
            profile = yaml.safe_load((ROOT / 'capabilities/foundation-environments.yaml').read_text())['profiles'][0]
            env_path = root / 'capabilities/foundation-environments.yaml'; env_path.write_text(json.dumps({'profiles': [profile]}))
            environment = {'schema-version': 1, 'profile': profile['id'], 'identity': profile['identity']}
            environment_path = environment_dir / 'environment.yaml'; environment_path.write_text(json.dumps(environment))
            artifact = root / 'target/foundation-0.1.0/harness/synthetic.jar'; artifact.parent.mkdir(parents=True)
            artifact.write_bytes(b'synthetic binding guard only'); base = artifact.parent.parent
            (base / 'build-inputs.json').write_text('{}\n')
            contract = {'environments': env_path.relative_to(root).as_posix(), 'required-artifacts': []}
            receipt = {'harness': [{'path': artifact.relative_to(root).as_posix(), 'sha256': digest(artifact)}]}
            identities = {'Candidate': 'a' * 64, 'Contract': 'b' * 64}; helper = root / 'native/bin/helper'
            plan = driver.execution_plan(root, scope, profile['identity']['image'], helper, '/workspace/' + artifact.relative_to(root).as_posix(), case=driver.certification_case('recovery'), execution='HARDENED_WORKER')
            config = {'schema-version': 1, 'candidate-sha256': identities['Candidate'], 'environment-sha256': digest(environment_path),
                'acceptance-profile': reports.PROFILE, 'execution-profile': 'HARDENED_WORKER', 'command': plan['recorder-command'],
                'java-options': plan['java-options'], 'locale': 'en_US / C.UTF-8', 'timezone': 'UTC', 'settings': plan['settings'],
                'inputs': driver.certification_inputs(root, contract, receipt, driver.certification_case('recovery'))}
            path = scope / 'execution.yaml'; path.write_text(json.dumps(config))
            worker.bound_execution(root, scope, contract, receipt, identities, helper, driver, 'recovery')
            for key, value in (('candidate-sha256', 'c' * 64), ('environment-sha256', 'd' * 64), ('locale', 'zh_CN'),
                    ('timezone', 'Asia/Shanghai'), ('execution-profile', 'IN_PROCESS'), ('inputs', []), ('command', [])):
                path.write_text(json.dumps({**config, key: value}))
                with self.assertRaises(ValueError): worker.bound_execution(root, scope, contract, receipt, identities, helper, driver, 'recovery')
            path.write_text(json.dumps(config)); artifact.write_bytes(b'changed staged artifact')
            with self.assertRaises(ValueError): worker.bound_execution(root, scope, contract, receipt, identities, helper, driver, 'recovery')


if __name__ == '__main__':
    unittest.main()
