"""T15 collector rejection contracts; the archive is protocol test data only."""
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
from t13_foundation_reports import parse_properties


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def seal(run):
    record, manifest = run / 'result.properties', run / 'retained-files.sha256'
    manifest.write_text(''.join(digest(path) + '  ' + path.relative_to(run).as_posix() + '\n'
        for path in sorted(run.rglob('*')) if path.is_file() and path not in (record, manifest)))
    lines = [line for line in record.read_text().splitlines() if not line.startswith('retained-files-sha256=')]
    record.write_text('\n'.join(lines) + '\nretained-files-sha256=' + digest(manifest) + '\n')


class T15FoundationTest(unittest.TestCase):
    def cli(self, root, mode='IN_PROCESS'):
        return subprocess.run([sys.executable, str(ROOT / 'scripts/t03-foundation.py'), 'collect', 'run',
            '--root', str(root), '--obligation', 'incremental', '--execution-profile', mode],
            capture_output=True, text=True, timeout=30)

    def fixture(self, root):
        paths = {'scripts/t15-evidence-pin.properties'}
        paths.update(name for name in parse_properties((ROOT / 'scripts/t15-evidence-pin.properties').read_bytes())
                     if not name.startswith('.build-cache/'))
        for name in paths:
            target = root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / name, target)
        run = root / 'run'
        with zipfile.ZipFile(ROOT / 'scripts/tests/fixtures/t15-collector.zip') as archive:
            for name in archive.namelist():
                target = run / name
                self.assertTrue(target.resolve().is_relative_to(run))
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(archive.read(name))
        return run

    def test_public_collect_requires_twelve_products_all_chains_and_actual_execution(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.fixture(root)
            result = self.cli(root)
            self.assertEqual(0, result.returncode, result.stderr)
            reports = json.loads(result.stdout)
            self.assertEqual(dict.fromkeys(('syntax', 'standards', 'semantic', 'visual'), 12),
                             {chain: len(value['products']) for chain, value in reports.items()})
            self.assertTrue(all(value['findings'] and value['negative-controls'] for value in reports.values()))
            self.assertNotEqual(0, self.cli(root, 'HARDENED_WORKER').returncode)

    def test_changed_or_resealed_labels_cannot_hide_failures_or_undetected_controls(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run = self.fixture(root)
            mutations = [
                ('result.properties', 'visual=pass', 'visual=indeterminate'),
                ('facade-create/publication.properties', 'execution-profile=IN_PROCESS', 'execution-profile=HARDENED_WORKER'),
                ('native-unsigned-second/publication.properties', 'save-mode=INCREMENTAL', 'save-mode=REWRITE'),
                ('native-create/observation.properties', 'annotations.count=2', 'annotations.count=1'),
                ('native-create/reopened.properties', 'annotations.count=2', 'annotations.count=1'),
                ('native-create/syntax/qpdf.command.json', '"exit-code": 0', '"exit-code": 137'),
                ('native-create/standards/incremental/result.json', '"result": "pass"', '"result": "fail"'),
                ('native-create/semantic/result.json', '"graph-preserved": true', '"graph-preserved": false'),
                ('native-create/visual/compare-1.stderr', '0 (0)', '1 (0.1)'),
                ('negative/standards/incremental/direct-values/result.json', '"rule": "direct-values"', '"rule": "byte-range"'),
                ('negative/semantic/placement/result.json', 'annotation.existing.page', 'pages.count'),
                ('negative/visual/changed-paint/result.json', '"absolute-error": "1600"', '"absolute-error": "0"'),
                ('negative/visual/one-pixel/result.json', '"absolute-error": "1"', '"absolute-error": "0"'),
                ('identities-after.json', '1643dacd9f', '0000000000')]
            for name, before, after in mutations:
                path = run / name
                original = path.read_text()
                self.assertIn(before, original, name)
                path.write_text(original.replace(before, after))
                self.assertNotEqual(0, self.cli(root).returncode, 'Unsealed mutation: ' + name)
                seal(run)
                self.assertNotEqual(0, self.cli(root).returncode, 'Resealed mutation: ' + name)
                path.write_text(original)
                seal(run)
            for name in ('negative/standards/incremental/byte-range/control.pdf',
                         'native-create/visual/render-1.command.json', 'identities-before.json'):
                path = run / name
                original = path.read_bytes()
                path.unlink()
                seal(run)
                self.assertNotEqual(0, self.cli(root).returncode, 'Missing tool/rule observation: ' + name)
                path.write_bytes(original)
                seal(run)

    def test_resealed_raw_process_result_is_rechecked_not_just_hashed(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run = self.fixture(root)
            stdout = run / 'native-create/standards/pdfcpu/pdfcpu.stdout'
            stderr = run / 'native-create/standards/pdfcpu/pdfcpu.stderr'
            receipt = stdout.with_name('pdfcpu.command.json')
            stdout.write_bytes(b'validation ok\n')
            stderr.write_bytes(b'validation error: original failure\n')
            original = json.loads(receipt.read_text())
            original.update({'stdout-sha256': digest(stdout), 'stderr-sha256': digest(stderr)})
            receipt.write_text(json.dumps(original))
            seal(run)
            self.assertNotEqual(0, self.cli(root).returncode)

    def test_recorder_missing_independent_tool_cannot_create_passing_evidence(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.fixture(root)
            output = root / 'missing-tool'
            output.mkdir()
            # The collector fixture intentionally contains no tool binaries.
            # Recording must reject the first missing executable before a
            # Python-version check or any observation could manufacture PASS.
            result = subprocess.run([sys.executable, str(root / 'scripts/t15-certification.py'),
                str(root), str(output), 'IN_PROCESS'], capture_output=True, text=True, timeout=30)
            self.assertNotEqual(0, result.returncode)
            self.assertIn('T15 pinned identity mismatch: .build-cache/', result.stderr)
            self.assertFalse((output / 'result.properties').exists())
            self.assertFalse((output / 'identities-before.json').exists())

    def test_authority_tool_identity_and_published_revision_changes_reject(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run = self.fixture(root)
            for name in ('scripts/t15-evidence-pin.properties', 'scripts/qpdf-pin.properties',
                         'capabilities/profiles/T15-signatures/products.json',
                         'run/native-create/incremental.pdf'):
                path = root / name
                original = path.read_bytes()
                path.write_bytes(original + b'\n')
                if path.is_relative_to(run):
                    seal(run)
                self.assertNotEqual(0, self.cli(root).returncode, name)
                path.write_bytes(original)
                seal(run)

    def test_original_assets_reproduce_without_product_parser_or_renderer(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'authored'
            for script in ('generate-t15-corpus.py', 'generate-t15-expectations.py'):
                result = subprocess.run([sys.executable, str(ROOT / 'scripts' / script), str(output)],
                                        capture_output=True, text=True, timeout=30)
                self.assertEqual(0, result.returncode, result.stderr)
            for path in output.iterdir():
                self.assertEqual((ROOT / 'capabilities/profiles/T15-signatures' / path.name).read_bytes(),
                                 path.read_bytes(), str(path))

    def test_public_index_binds_manifest_inputs_and_transitive_t15_observations(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run = self.fixture(root)
            collected = self.cli(root)
            self.assertEqual(0, collected.returncode, collected.stderr)
            reports = json.loads(collected.stdout)

            def reference(path):
                return {'path': path.relative_to(root).as_posix(), 'sha256': digest(path)}

            def write(name, value):
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(json.dumps(value) + '\n')
                return reference(path)

            inputs = [reference(path) for path in sorted(
                (root / 'capabilities/profiles/T15-signatures').iterdir()) if path.is_file()]
            environment = write('environment.json', {'identity': 'protocol test fixture'})
            candidate, contract = 'a' * 64, 'b' * 64
            configuration = write('execution.json', {'candidate-sha256': candidate,
                'environment-sha256': environment['sha256'], 'inputs': inputs})
            records = []
            for chain, report in reports.items():
                records.append(write(chain + '.json', {'obligation': 'incremental',
                    'execution-profile': 'IN_PROCESS', 'candidate-sha256': candidate,
                    'contract-sha256': contract, 'environment-sha256': environment['sha256'],
                    'execution-configuration-sha256': configuration['sha256'], 'chain': chain,
                    'result': 'pass', 'report': write(chain + '-report.json', report)}))
            fresh = {'schema-version': 1, 'candidate': {'inputs': inputs},
                'environments': [{'profile': 'fixture', 'record': environment}],
                'certifications': [{'obligation': 'incremental', 'environment': 'fixture',
                    'execution-profile': 'IN_PROCESS', 'configuration': configuration, 'records': records}]}
            write('capabilities/foundation-evidence.yaml', {**fresh, 'certifications': []})
            authority = root / 'capabilities/foundation-evidence.yaml'
            previous = authority.read_bytes()
            write('observed/observed-index.json', fresh)
            write('observed/identities.json', {'Candidate': candidate, 'Contract': contract})
            (root / 'observed/prior-index.sha256').write_text(hashlib.sha256(previous).hexdigest() + '\n')
            command = [sys.executable, str(ROOT / 'scripts/t03-foundation.py'), 'merge-index',
                       'observed', '--root', str(root)]
            published = subprocess.run(command, capture_output=True, text=True, timeout=30)
            self.assertEqual(0, published.returncode, published.stderr)
            self.assertEqual(fresh, json.loads(authority.read_text()))
            for path in (root / 'capabilities/profiles/T15-signatures/products.json',
                         root / 'capabilities/profiles/T15-signatures/p3.pdf',
                         run / 'native-create/incremental.pdf',
                         run / 'negative/standards/incremental/byte-range/control.pdf',
                         root / 'execution.json'):
                authority.write_bytes(previous)
                original = path.read_bytes()
                path.write_bytes(original + b'\n')
                rejected = subprocess.run(command, capture_output=True, text=True, timeout=30)
                self.assertNotEqual(0, rejected.returncode, str(path))
                self.assertEqual(previous, authority.read_bytes())
                path.unlink()
                missing = subprocess.run(command, capture_output=True, text=True, timeout=30)
                self.assertNotEqual(0, missing.returncode, str(path))
                self.assertEqual(previous, authority.read_bytes())
                path.write_bytes(original)
            # Ordinary repository references remain recursive, even if resealed.
            nested = write('nested.json', {'input': {'path': 'missing.pdf', 'sha256': 'c' * 64}})
            reports['syntax']['inputs'] = [nested]
            record = json.loads((root / 'syntax.json').read_text())
            record['report'] = write('syntax-report.json', reports['syntax'])
            replacement = write('syntax.json', record)
            records[:] = [replacement if item['path'] == 'syntax.json' else item for item in records]
            write('observed/observed-index.json', fresh)
            rejected = subprocess.run(command, capture_output=True, text=True, timeout=30)
            self.assertNotEqual(0, rejected.returncode, rejected.stderr)
            self.assertEqual(previous, authority.read_bytes())

    def test_incremental_plan_selects_native_mode_and_records_facade_in_process(self):
        spec = importlib.util.spec_from_file_location('foundation', ROOT / 'scripts/t03-foundation.py')
        foundation = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(foundation)
        case = foundation.certification_case('incremental')
        self.assertEqual(36, case['test-count'])
        self.assertEqual('pdfcpu', case['standards-producer'])
        self.assertEqual(5, len(case['test-classes']))
        self.assertIn('folio-pdf-t15', {tool['id'] for tool in foundation.certification_tools()})
        for mode in ('IN_PROCESS', 'HARDENED_WORKER'):
            plan = foundation.execution_plan(ROOT, ROOT / 'run', 'pinned-image', ROOT / 'helper/bin/folio-harfbuzz',
                                             'staged-classpath', case, mode)
            self.assertIn('-Dfolio.t15.executionProfile=' + mode, plan['java-options'])
            self.assertIn('net.zerocloud.pdf.acceptance.T15EvidenceCommand', plan['recorder-command'])
            self.assertIn('Stable Facade execution is IN_PROCESS', plan['settings']['providers'])
