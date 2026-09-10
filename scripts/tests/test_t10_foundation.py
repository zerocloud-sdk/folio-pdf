"""Foundation orchestration contracts for the T10 pages obligation."""
import importlib.util
import hashlib
from pathlib import Path
import tempfile
import unittest


RUNNER = Path(__file__).resolve().parents[1] / 't03-foundation.py'
SPEC = importlib.util.spec_from_file_location('t03_foundation', RUNNER)


class PagesCertificationCaseTest(unittest.TestCase):
    def test_pages_case_runs_complete_public_suites_and_frozen_configuration(self):
        module = importlib.util.module_from_spec(SPEC)
        SPEC.loader.exec_module(module)

        case = module.certification_case('pages')

        self.assertEqual('T10-page-manipulation-merge-split', case['profile'])
        self.assertEqual('T10', case['label'])
        self.assertEqual(65, case['test-count'])
        self.assertEqual('IN_PROCESS', case['facade-execution-profile'])
        for name in (
                'net.zerocloud.pdf.consumer.PageManipulationWorkflowTest',
                'net.zerocloud.pdf.itext7.consumer.PageManipulationFacadeTest',
                'net.zerocloud.pdf.migration.itext7.contract.JarContractIT',
                'net.zerocloud.pdf.migration.itext7.contract.ClasspathExclusivityIT'):
            self.assertIn(name, case['test-classes'])
        for path in (
                'capabilities/profiles/T10-pages',
                'capabilities/profiles/T10-standards',
                'capabilities/profiles/T03-standards',
                'capabilities/profiles/T09-standards',
                'build-tools/acceptance/arlington/t10-r1.patch',
                'scripts/t10-arlington-pin.properties'):
            self.assertIn(path, case['configuration-paths'])
        self.assertIn('named Sources', case['workflow-policy'])
        self.assertIn('all declared Targets', case['workflow-policy'])
        self.assertIn('no fonts', case['fonts'])

    def test_common_environment_catalog_pins_original_and_t10_arlington(self):
        module = importlib.util.module_from_spec(SPEC)
        SPEC.loader.exec_module(module)

        tools = module.certification_tools()

        by_id = {item['id']: item for item in tools}
        self.assertEqual('scripts/arlington-pin.properties', by_id['arlington']['pin'])
        self.assertEqual('scripts/t10-arlington-pin.properties',
                         by_id['arlington-t10-r1']['pin'])
        self.assertEqual('0.81-folio-t10-r1', by_id['arlington-t10-r1']['version'])
        self.assertIn('.build-cache/arlington/t10-r1/',
                      by_id['arlington-t10-r1']['path'])
        self.assertEqual({'folio-pdf-t03', 'folio-pdf-t09', 'folio-pdf-t10'},
                         {item['id'] for item in tools if item['kind'] == 'project-test'})

    def test_pages_plan_has_eight_native_runs_and_fixed_facade_mode(self):
        import json
        import os
        import shutil
        import subprocess
        import sys
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'capabilities').mkdir()
            (root / 'target/foundation-0.1.0/artifacts').mkdir(parents=True)
            (root / 'target/foundation-0.1.0/harness').mkdir()
            artifact = 'target/foundation-0.1.0/artifacts/pdf-document-0.1.0.jar'
            harness = 'target/foundation-0.1.0/harness/native-tests.jar'
            self._touch(root / artifact)
            self._touch(root / harness)
            helper = root / 'helper/bin/folio-harfbuzz'
            helper.parent.mkdir(parents=True)
            self._touch(helper)
            helper.chmod(0o755)
            environments = RUNNER.parents[1] / 'capabilities/foundation-environments.yaml'
            shutil.copyfile(environments, root / 'capabilities/environments.yaml')
            (root / 'capabilities/foundation-release.yaml').write_text(json.dumps({
                'release': '0.1.0', 'environments': 'capabilities/environments.yaml',
                'required-artifacts': [artifact]}))
            (root / 'target/foundation-0.1.0/build-inputs.json').write_text(json.dumps({
                'harness': [{'path': harness}]}))
            result = subprocess.run([
                sys.executable, str(RUNNER), 'plan', 'planned', '--root', str(root),
                '--obligation', 'pages'], env=dict(os.environ,
                    FOLIO_HARFBUZZ_HELPER=str(helper)), text=True,
                stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
            self.assertEqual(0, result.returncode, result.stdout)
            plan = json.loads((root / 'planned/plan.json').read_text())
            self.assertEqual(8, len(plan['executions']))
            for execution in plan['executions']:
                self.assertEqual(65, execution['required-test-count'])
                self.assertIn('net.zerocloud.pdf.acceptance.T10EvidenceCommand',
                              execution['recorder-command'])
                self.assertIn('-Dfolio.t10.executionProfile=',
                              ' '.join(execution['contract-tests-command']))
                self.assertIn('Stable Facade execution is IN_PROCESS',
                              execution['settings']['providers'])

    @staticmethod
    def _touch(path):
        path.write_text(path.name)


class PagesEvidenceCollectionTest(unittest.TestCase):
    def test_collects_eight_exact_products_per_page_visuals_and_real_controls(self):
        module = importlib.util.module_from_spec(SPEC)
        SPEC.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run = root / 'run'
            run.mkdir()
            self._write(run / 'result.properties', self._results('pass'))
            negative = run / 'negative'
            negative.mkdir()
            self._write(negative / 'result.properties', self._results('fail'))
            for chain in module.CHAINS:
                self._write(negative / (chain + '.txt'), chain + ' negative detected\n')
            self._write(negative / 'invalid.pdf', 'invalid syntax control\n')
            self._write(negative / 'wrong-order.pdf', 'wrong semantic sequence\n')
            visual_control = negative / 'visual'
            visual_control.mkdir()
            for name in ('pages.pdf', 'one-pixel-control.png', 'one-pixel-control.properties',
                         'page-1-visual.md', 'page-1-visual.txt', 'page-1-pdfium.png',
                         'page-1-expected.png', 'page-1-implementation.png',
                         'page-1-difference.png', 'page-1-renderer-difference.png'):
                self._write(visual_control / name, 'visual control ' + name + '\n')

            for api in ('native', 'facade'):
                for product in ('edited', 'merged', 'left', 'right'):
                    directory = run / (api + '-' + product)
                    directory.mkdir()
                    pdf = directory / 'pages.pdf'
                    self._write(pdf, api + '-' + product + '-pdf\n')
                    self._write(directory / 'result.properties',
                                self._results('pass')
                                + 'input-sha256=' + self._sha(pdf)
                                + '\npage-count=1\npage.1.visual=pass\n')
                    for chain in module.CHAINS:
                        self._write(directory / (chain + '.txt'), chain + ' findings\n')
                    for name in ('page-1-visual.md', 'page-1-visual.txt', 'page-1-pdfium.png',
                                 'page-1-expected.png', 'page-1-implementation.png',
                                 'page-1-difference.png', 'page-1-renderer-difference.png'):
                        self._write(directory / name, api + '-' + product + '-' + name + '\n')
                    for checker in ('pdfcpu', 'arlington'):
                        tool = directory / checker
                        tool.mkdir()
                        self._write(tool / 'standards.properties',
                                    'result=pass\ncovered-rules=fixture-rule\n')
                        self._write(tool / 'findings.txt', 'qualified\n')
                        self._write(tool / 'negative-fixture-rule.txt', 'detected\n')
                        self._write(tool / 'control-fixture-rule.pdf', 'illegal\n')

            reports = module.collect_reports(root, run, 'pages')

            for chain in module.CHAINS:
                self.assertEqual(8, len(reports[chain]['products']))
            visual_findings = {item['path'] for item in reports['visual']['findings']}
            self.assertIn('run/native-edited/page-1-visual.md', visual_findings)
            self.assertIn('run/facade-right/page-1-renderer-difference.png', visual_findings)
            controls = {
                item['path'] for chain in module.CHAINS
                for item in reports[chain]['negative-controls']
            }
            self.assertIn('run/negative/invalid.pdf', controls)
            self.assertIn('run/negative/wrong-order.pdf', controls)
            self.assertIn('run/negative/visual/one-pixel-control.png', controls)
            self.assertIn('run/negative/visual/page-1-visual.md', controls)

            (run / 'native-left/pages.pdf').write_text('changed after observation')
            with self.assertRaisesRegex(ValueError, 'changed'):
                module.collect_reports(root, run, 'pages')

    @staticmethod
    def _results(value):
        return ''.join(chain + '=' + value + '\n'
                       for chain in ('syntax', 'standards', 'semantic', 'visual'))

    @staticmethod
    def _write(path, value):
        path.write_text(value)

    @staticmethod
    def _sha(path):
        return hashlib.sha256(path.read_bytes()).hexdigest()


if __name__ == '__main__':
    unittest.main()
