"""Exercise the repository Foundation CLI for the frozen Annotation obligation."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import yaml

ROOT = Path(__file__).resolve().parents[2]
CHAINS = ('syntax', 'standards', 'semantic', 'visual')
PRODUCTS = ('created', 'changed', 'flattened', 'copied', 'merged', 'adopted', 'left', 'right')
CHANGES = ('order', 'identifier', 'rectangle', 'appearance', 'appearance-box', 'payload', 'icon',
           'direct-target', 'action-operand', 'named-target', 'copy-target', 'copy-external', 'merge-name',
           'split-survival', 'open-adoption', 'flatten-removal', 'flatten-placement', 'retained-paint')


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(value)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def props(path, values):
    write(path, ''.join(key + '=' + str(value) + '\n' for key, value in values.items()))


class T12FoundationTest(unittest.TestCase):
    def cli(self, root, *arguments):
        env = dict(os.environ, FOLIO_HARFBUZZ_HELPER=str(root / 'helper'))
        return subprocess.run(['/usr/bin/python3', str(ROOT / 'scripts/t03-foundation.py'), *arguments,
                               '--root', str(root), '--obligation', 'annotations'],
                              env=env, capture_output=True, text=True, timeout=30)

    def test_public_plan_declares_eight_actual_execution_combinations_and_frozen_contract(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            write(root / 'helper', '#!/bin/sh\nexit 0\n')
            (root / 'helper').chmod(0o700)
            write(root / 'capabilities/foundation-release.yaml', yaml.safe_dump({
                'release': '0.1.0', 'required-artifacts': [], 'environments': 'capabilities/environments.yaml'}))
            shutil.copyfile(ROOT / 'capabilities/foundation-environments.yaml', root / 'capabilities/environments.yaml')
            write(root / 'target/foundation-0.1.0/build-inputs.json', json.dumps({'harness': []}))
            completed = self.cli(root, 'plan', 'plan')
            self.assertEqual(0, completed.returncode, completed.stderr)
            plan = json.loads((root / 'plan/plan.json').read_text())
            self.assertEqual('unverified-plan', plan['status'])
            self.assertEqual(8, len(plan['executions']))
            for index, execution in enumerate(plan['executions']):
                mode = 'IN_PROCESS' if index % 2 == 0 else 'HARDENED_WORKER'
                self.assertEqual(47, execution['required-test-count'])
                self.assertIn('-Dfolio.t12.executionProfile=' + mode, execution['java-options'])
                self.assertIn('net.zerocloud.pdf.acceptance.T12EvidenceCommand', execution['recorder-command'])
                self.assertIn('net.zerocloud.pdf.consumer.AnnotationWorkflowTest', execution['contract-tests-command'])
                self.assertIn('net.zerocloud.pdf.itext7.consumer.AnnotationFacadeTest', execution['contract-tests-command'])
                self.assertIn('IN_PROCESS', execution['settings']['providers'])
            for path in ('capabilities/profiles/T12-annotations', 'capabilities/profiles/T12-standards',
                         'scripts/t12-arlington-pin.properties', 'scripts/t12-semantics.py', 'scripts/t12-safety-observer.py'):
                self.assertIn(path, plan['configuration-paths'])

    def test_public_collect_binds_complete_reports_and_rejects_missing_or_inconsistent_observations(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run = self.report_fixture(root)
            command = ('collect', 'run', '--execution-profile', 'HARDENED_WORKER')
            completed = self.cli(root, *command)
            self.assertEqual(0, completed.returncode, completed.stderr)
            reports = json.loads(completed.stdout)
            self.assertEqual(16, len(reports['syntax']['products']))
            for path in ('run/native-created/qpdf/qpdf.json', 'run/safety/effects/observation.json'):
                self.assertIn(path, {item['path'] for item in reports['semantic']['findings']})
            self.assertIn('run/native-created/page-1-appearance-projection.pdf',
                          {item['path'] for item in reports['visual']['findings']})
            for change in CHANGES:
                self.assertIn('run/negative/semantic/' + change + '.pdf',
                              {item['path'] for item in reports['semantic']['negative-controls']})
            self.assertNotEqual(0, self.cli(root, 'collect', 'run').returncode)
            substitutions = [
                ('result.properties', 'native-execution-profile=HARDENED_WORKER', 'native-execution-profile=IN_PROCESS'),
                ('facade-created/result.properties', 'execution-profile=IN_PROCESS', 'execution-profile=HARDENED_WORKER'),
                ('native-created/result.properties', 'input-sha256=', 'wrong-sha256='),
                ('native-created/arlington-annotations/standards.properties', 'covered-rules=', 'missing-rules='),
                ('negative/semantic/copy-target/result.properties', 'semantic=fail', 'semantic=indeterminate'),
                ('negative/visual/appearance/result.properties', 'page.1.visual=fail', 'page.1.visual=pass'),
                ('negative/visual/one-pixel/result.properties', 'negative-absolute-error=1', 'negative-absolute-error=0'),
                ('safety/result.properties', 'native.unknown.copy-code=PRESERVATION_UNSUPPORTED', 'native.unknown.copy-code=QUERY_FAILED'),
                ('safety/effects/observation.properties', 'qualification.process=pass', 'qualification.process=indeterminate'),
                ('safety/effects/observation.properties', 'effects.read=0', 'effects.read=1'),
            ]
            for path, before, after in substitutions:
                file = run / path
                original = file.read_text()
                self.assertIn(before, original)
                file.write_text(original.replace(before, after))
                rejected = self.cli(root, *command)
                self.assertNotEqual(0, rejected.returncode, path + ' was incorrectly accepted')
                file.write_text(original)
            for path in ('native-created/qpdf/qpdf.json', 'native-created/page-1-appearance-projection.pdf',
                         'negative/semantic/order.pdf', 'safety/effects/observation.json'):
                file = run / path
                original = file.read_bytes()
                file.unlink()
                self.assertNotEqual(0, self.cli(root, *command).returncode, path)
                file.write_bytes(original)

    def report_fixture(self, root):
        # These are collector protocol fixtures, never PDF/checker qualification evidence.
        run = root / 'run'
        overall = dict.fromkeys(CHAINS, 'pass')
        overall.update(safety='pass', **{'native-execution-profile': 'HARDENED_WORKER', 'facade-execution-profile': 'IN_PROCESS'})
        props(run / 'result.properties', overall)
        for name in ('t12-semantics.py', 't12-safety-observer.py'):
            write(root / 'scripts' / name, 'original collector fixture observer\n')
        shutil.copytree(ROOT / 'capabilities/profiles/T12-standards', root / 'capabilities/profiles/T12-standards')
        negative = run / 'negative'
        props(negative / 'result.properties', dict.fromkeys(CHAINS, 'fail'))
        for chain in CHAINS:
            write(negative / (chain + '.txt'), 'detected\n')
        for name in ('invalid.pdf', 'invalid-rectangle.pdf'):
            write(negative / name, 'invalid collector fixture\n')
        props(negative / 'standards/standards.properties', {'result': 'fail', 'input-sha256': digest(negative / 'invalid-rectangle.pdf')})
        write(negative / 'standards/findings.txt', 'invalid rectangle')
        for change in CHANGES:
            pdf = negative / 'semantic' / (change + '.pdf')
            write(pdf, change)
            props(pdf.parent / change / 'result.properties', {'semantic': 'fail', 'input-sha256': digest(pdf), 'finding': change})
        for change in ('appearance', 'rectangle', 'retained-paint'):
            pdf = negative / 'visual' / (change + '.pdf')
            write(pdf, change)
            props(pdf.parent / change / 'result.properties', {'visual': 'fail', 'page.1.visual': 'fail', 'input-sha256': digest(pdf)})
        pixel = negative / 'visual/one-pixel'
        expected = root / 'capabilities/profiles/T12-annotations/expected/created-page-1.png'
        expected.parent.mkdir(parents=True)
        shutil.copyfile(ROOT / 'capabilities/profiles/T12-annotations/expected/created-page-1.png', expected)
        write(pixel / 'one-pixel.png', 'different original protocol control')
        shutil.copyfile(expected, pixel / 'original.png')
        write(pixel / 'difference.png', 'protocol fixture')
        write(pixel / 'comparator.txt', 'qualified one pixel')
        props(pixel / 'result.properties', {'visual': 'fail', 'positive-absolute-error': 0, 'negative-absolute-error': 1,
            'threshold': 0, 'fuzz-percent': 0, 'metric': 'AE', 'original-sha256': digest(expected),
            'changed-sha256': digest(pixel / 'one-pixel.png')})
        safety = dict(overall, result='pass')
        for api in ('native', 'facade'):
            for graph in ('unknown', 'chained'):
                key = api + '.' + graph + '.'
                safety.update({key + name: 'pass' for name in ('preserved', 'replaced', 'atomic', 'source-unchanged')})
                safety[key + 'query-code'] = 'QUERY_FAILED'
                safety[key + 'copy-code'] = 'PRESERVATION_UNSUPPORTED'
                safety[key + 'execution-profile'] = 'HARDENED_WORKER' if api == 'native' else 'IN_PROCESS'
                for phase in ('source', 'preserved', 'replaced'):
                    pdf = run / 'safety/probe' / (api + '-' + graph + '-' + phase + '.pdf')
                    write(pdf, pdf.name)
                    safety[key + phase + '.sha256'] = digest(pdf)
                    safety[key + phase + '.receipt-status'] = 'COMMITTED'
                    safety[key + phase + '.partial-output-possible'] = 'false'
            safety[api + '.signed-code'] = 'SIGNED_REWRITE_REJECTED'
            safety[api + '.signed-target-unchanged'] = 'pass'
            write(run / 'safety/probe' / (api + '-signed-target.pdf'), 'FOLIO')
        write(run / 'safety/probe/signed-docmdp-p3.pdf', 'original signed protocol fixture')
        safety['signed-source-sha256'] = digest(run / 'safety/probe/signed-docmdp-p3.pdf')
        effects = {'result': 'pass', 'child-exit': '0', 'observer-sha256': digest(root / 'scripts/t12-safety-observer.py')}
        for kind in ('read', 'write', 'script', 'process', 'network'):
            effects['qualification.' + kind] = 'pass'
            effects['effects.' + kind] = '0'
        props(run / 'safety/effects/observation.properties', effects)
        write(run / 'safety/effects/observation.json', json.dumps({'result': 'pass', 'child_exit': 0,
            'observer_sha256': effects['observer-sha256'], 'qualification': dict.fromkeys(('read', 'write', 'script', 'process', 'network'), 'pass'),
            'effects': dict.fromkeys(('read', 'write', 'script', 'process', 'network'), 0)}))
        props(run / 'safety/probe/result.properties', safety)
        safety['effect-observer-sha256'] = digest(root / 'scripts/t12-safety-observer.py')
        safety['effect-observation-sha256'] = digest(run / 'safety/effects/observation.json')
        props(run / 'safety/result.properties', safety)
        for api in ('native', 'facade'):
            for name in PRODUCTS:
                edition = api + '-' + name
                count = {'created': 3, 'changed': 3, 'flattened': 3, 'copied': 5, 'merged': 4, 'adopted': 4, 'left': 2, 'right': 2}[name]
                product = run / edition
                write(product / 'annotations.pdf', 'collector fixture ' + edition)
                observed = dict(overall, **{'execution-profile': 'HARDENED_WORKER' if api == 'native' else 'IN_PROCESS',
                    'input-sha256': digest(product / 'annotations.pdf'), 'page-count': count, 'standards-rule-count': 174})
                observed.update({'page.' + str(page) + '.visual': 'pass' for page in range(1, count + 1)})
                props(product / 'result.properties', observed)
                props(run / (edition + '-publication.properties'), {'receipt-count': '1', 'execution-profile': observed['execution-profile'],
                    'receipt.0.target': name, 'receipt.0.status': 'COMMITTED', 'receipt.0.partial-output-possible': 'false',
                    'receipt.0.pdf-sha256': observed['input-sha256']})
                for chain in CHAINS:
                    write(product / (chain + '.txt'), edition + ' ' + chain)
                for suffix in ('visual.md', 'visual.txt', 'expected.png', 'pdfium.png', 'implementation.png',
                               'difference.png', 'renderer-difference.png', 'appearance-projection.pdf'):
                    for page in range(1, count + 1):
                        write(product / ('page-' + str(page) + '-' + suffix), suffix)
                for name in ('qpdf.json', 'observed.json', 'semantic.txt'):
                    write(product / 'qpdf' / name, '{}')
                props(product / 'qpdf/result.properties', {'semantic': 'pass', 'input-sha256': observed['input-sha256'],
                      'observer-sha256': digest(root / 'scripts/t12-semantics.py'), 'qpdf-json-sha256': digest(product / 'qpdf/qpdf.json')})
                props(product / 'semantic.properties', {'semantic': 'pass', 'input-sha256': observed['input-sha256']})
                write(product / 'semantic-command.txt', 'qualified collector protocol fixture')
                for checker in ('pdfcpu', 'arlington-core', 'arlington-annotations'):
                    rules = (ROOT / ('capabilities/profiles/T12-standards/' + checker + '.properties')).read_text()
                    required = next(line.partition('=')[2] for line in rules.splitlines() if line.startswith('required-rules='))
                    props(product / checker / 'standards.properties', {'result': 'pass', 'covered-rules': required, 'input-sha256': observed['input-sha256']})
                    write(product / checker / 'findings.txt', 'qualified protocol fixture')
                    for rule in required.split(','):
                        write(product / checker / ('negative-' + rule + '.txt'), 'detected')
                        write(product / checker / ('control-' + rule + '.pdf'), 'invalid fixture')
        return run


if __name__ == '__main__':
    unittest.main()
