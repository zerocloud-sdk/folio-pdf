"""Public collector rejection and reproducible original-corpus contracts.

The retained archive is development protocol data, never certification of the
candidate being tested. Real tools and actual environment runs are separate.
"""
import hashlib
import importlib.util
import json
import os
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


class T14FoundationTest(unittest.TestCase):
    def cli(self, root, mode='IN_PROCESS'):
        return subprocess.run([sys.executable, str(ROOT / 'scripts/t03-foundation.py'), 'collect', 'run',
            '--root', str(root), '--obligation', 'images', '--execution-profile', mode],
            capture_output=True, text=True, timeout=30)

    def fixture(self, root):
        paths = {'scripts/t14-evidence-pin.properties'}
        paths.update(name for name in parse_properties((ROOT / 'scripts/t14-evidence-pin.properties').read_bytes())
                     if not name.startswith('.build-cache/'))
        core = parse_properties((ROOT / 'capabilities/profiles/T14-standards/pdfcpu.properties').read_bytes())
        for key, value in core.items():
            if key.endswith('.path'):
                paths.add((ROOT / 'capabilities/profiles/T14-standards' / value).resolve().relative_to(ROOT).as_posix())
        for name in paths:
            target = root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / name, target)
        run = root / 'run'
        with zipfile.ZipFile(ROOT / 'scripts/tests/fixtures/t14-collector.zip') as archive:
            for name in archive.namelist():
                target = run / name
                self.assertTrue(target.resolve().is_relative_to(run))
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(archive.read(name))
        return run

    def test_public_collect_validates_all_products_and_separate_chains(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.fixture(root)
            completed = self.cli(root)
            self.assertEqual(0, completed.returncode, completed.stderr)
            reports = json.loads(completed.stdout)
            self.assertEqual({'syntax': 10, 'standards': 8, 'semantic': 10, 'visual': 8},
                             {chain: len(value['products']) for chain, value in reports.items()})
            self.assertTrue(all(value['findings'] and value['negative-controls'] for value in reports.values()))
            self.assertNotEqual(0, self.cli(root, 'HARDENED_WORKER').returncode)

    def test_collect_rejects_changed_files_and_missing_controls_even_with_resealed_manifest(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run = self.fixture(root)
            mutations = [
                ('result.properties', 'visual=pass', 'visual=indeterminate'),
                ('facade-formats/publication.properties', 'execution-profile=IN_PROCESS', 'execution-profile=HARDENED_WORKER'),
                ('native-inventory/observation.properties', 'resources.6.image.width=2', 'resources.6.image.width=3'),
                ('native-inventory/observation.properties', 'resources.6.identity=5', 'resources.6.identity=0'),
                ('native-inventory/reopened.properties', 'resources.6.image.width=2', 'resources.6.image.width=3'),
                ('native-filters/syntax/qpdf.command.json', '"exit-code": 0', '"exit-code": 137'),
                ('native-colors/standards/images/result.json', '"result": "pass"', '"result": "fail"'),
                ('native-inventory/semantic/result.json', '"graph-preserved": true', '"graph-preserved": false'),
                ('native-filters/visual/compare-1.stderr', '0 (0)', '1 (0.1)'),
                ('negative/standards/images/width-zero/result.json', '"rule": "image-dimensions"', '"rule": "image-type"'),
                ('negative/semantic/usage/result.json', 'resources.6.pages', 'resources.0.pages'),
                ('negative/visual/changed-samples/result.json', '"absolute-error": "800"', '"absolute-error": "0"'),
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
            path = run / 'negative/standards/images/width-zero/control.pdf'
            path.unlink()
            seal(run)
            self.assertNotEqual(0, self.cli(root).returncode)

    def test_public_collect_rejects_authority_and_tool_pin_changes(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.fixture(root)
            for name in ('scripts/t14-evidence-pin.properties', 'scripts/qpdf-pin.properties',
                         'capabilities/profiles/T14-images/corpus.json'):
                path = root / name
                original = path.read_bytes()
                path.write_bytes(original + b'\n')
                self.assertNotEqual(0, self.cli(root).returncode, name)
                path.write_bytes(original)

    def test_original_corpus_and_standards_controls_reproduce_without_product_or_observer(self):
        for script, directory in (('generate-t14-corpus.py', 'T14-images'),
                                  ('generate-t14-standards.py', 'T14-standards')):
            with tempfile.TemporaryDirectory() as temporary:
                output = Path(temporary) / 'authored'
                completed = subprocess.run([sys.executable, str(ROOT / 'scripts' / script), str(output)],
                                           capture_output=True, text=True, timeout=30)
                self.assertEqual(0, completed.returncode, completed.stderr)
                for path in output.rglob('*'):
                    if path.is_file():
                        self.assertEqual((ROOT / 'capabilities/profiles' / directory / path.relative_to(output)).read_bytes(),
                                         path.read_bytes(), str(path))

    def test_images_plan_selects_native_profile_and_actual_facade_execution(self):
        spec = importlib.util.spec_from_file_location('foundation', ROOT / 'scripts/t03-foundation.py')
        foundation = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(foundation)
        case = foundation.certification_case('images')
        self.assertEqual(38, case['test-count'])
        self.assertEqual('pdfcpu', case['standards-producer'])
        self.assertEqual(4, len(case['test-classes']))
        for mode in ('IN_PROCESS', 'HARDENED_WORKER'):
            plan = foundation.execution_plan(ROOT, ROOT / 'run', 'pinned-image', ROOT / 'helper/bin/folio-harfbuzz',
                                             'staged-classpath', case, mode)
            self.assertIn('-Dfolio.t14.executionProfile=' + mode, plan['java-options'])
            self.assertIn('net.zerocloud.pdf.acceptance.T14EvidenceCommand', plan['recorder-command'])
            self.assertIn('Stable Facade execution is IN_PROCESS', plan['settings']['providers'])

    def test_image_corpus_can_be_bound_by_the_public_evidence_index_without_ambiguous_references(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)

            def write(name, value):
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(value if isinstance(value, bytes) else (json.dumps(value) + '\n').encode())
                return {'path': name, 'sha256': digest(path)}

            corpus = ROOT / 'capabilities/profiles/T14-images'
            inputs = [write('corpus/' + name, (corpus / name).read_bytes()) for name in
                      ('corpus.json', 'inventory.pdf', 'filters.pdf', 'formats.pdf', 'colors.pdf', 'classifications.pdf')]
            environment = write('environment.json', {'identity': 'fixture'})
            candidate, contract = 'a' * 64, 'b' * 64
            configuration = write('execution.json', {'candidate-sha256': candidate,
                'environment-sha256': environment['sha256'], 'inputs': inputs})
            chains = []
            for chain in ('syntax', 'standards', 'semantic', 'visual'):
                report = write(chain + '-report.json', {'chain': chain, 'result': 'pass', 'products': inputs[1:]})
                chains.append(write(chain + '.json', {'obligation': 'images', 'execution-profile': 'IN_PROCESS',
                    'candidate-sha256': candidate, 'contract-sha256': contract,
                    'environment-sha256': environment['sha256'],
                    'execution-configuration-sha256': configuration['sha256'], 'chain': chain,
                    'result': 'pass', 'report': report}))
            fresh = {'schema-version': 1, 'candidate': {'inputs': inputs},
                'environments': [{'profile': 'fixture', 'record': environment}],
                'certifications': [{'obligation': 'images', 'environment': 'fixture',
                    'execution-profile': 'IN_PROCESS', 'configuration': configuration, 'records': chains}]}
            previous = {**fresh, 'certifications': []}
            authority = root / 'capabilities/foundation-evidence.yaml'
            write('capabilities/foundation-evidence.yaml', previous)
            previous_bytes = authority.read_bytes()
            write('observed/observed-index.json', fresh)
            write('observed/identities.json', {'Candidate': candidate, 'Contract': contract})
            (root / 'observed/prior-index.sha256').write_text(hashlib.sha256(previous_bytes).hexdigest() + '\n')
            command = [sys.executable, str(ROOT / 'scripts/t03-foundation.py'), 'merge-index', 'observed',
                       '--root', str(root)]
            completed = subprocess.run(command, capture_output=True, text=True, timeout=30)
            self.assertEqual(0, completed.returncode, completed.stderr)
            self.assertEqual(fresh, json.loads(authority.read_text()))
            for name in ('corpus.json', 'inventory.pdf'):
                authority.write_bytes(previous_bytes)
                path = root / 'corpus' / name
                original = path.read_bytes()
                path.write_bytes(original + b'\n')
                rejected = subprocess.run(command, capture_output=True, text=True, timeout=30)
                self.assertNotEqual(0, rejected.returncode, name)
                self.assertEqual(previous_bytes, authority.read_bytes())
                path.write_bytes(original)
