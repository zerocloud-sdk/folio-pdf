"""Public recorder-artifact collection contracts; synthetic data are never certifications."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import hashlib
import shutil


RUNNER = Path(__file__).resolve().parents[1] / 't03-foundation.py'


class EncodedStreamPreservationTest(unittest.TestCase):
    def test_final_revision_must_preserve_encoded_bytes_even_when_old_prefix_and_decoded_bytes_match(self):
        root = RUNNER.parents[1]
        fixture = root / 'capabilities/profiles/T09-values/fixtures'
        for reencoded in (False, True):
            with self.subTest(reencoded=reencoded), tempfile.TemporaryDirectory(dir=root / '.build-cache') as temporary:
                run = Path(temporary)
                for product in ('source', 'native-rewrite', 'native-incremental', 'facade'):
                    (run / product).mkdir()
                    name = 'reencoded-incremental.pdf' if reencoded and product == 'native-incremental' else 'values.pdf'
                    shutil.copyfile(fixture / name, run / product / 'values.pdf')
                result = subprocess.run([sys.executable, str(RUNNER), 'preservation', str(run)],
                                        text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
                report = run / 'raw-preservation/result.json'
                self.assertTrue(report.exists(), result.stdout)
                observed = json.loads(report.read_text())
                self.assertEqual('fail' if reencoded else 'pass', observed['result'])
                self.assertEqual('fail', observed['negative-control'])
                self.assertEqual(0 if not reencoded else 1, result.returncode, result.stdout)
                for product in ('source', 'native-rewrite', 'native-incremental', 'facade', 'negative'):
                    self.assertTrue((run / ('raw-preservation/' + product + '.json')).is_file())


class EvidenceIndexMergeTest(unittest.TestCase):
    def test_values_plan_runs_complete_public_suites_and_records_real_facade_mode(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'capabilities').mkdir()
            (root / 'target/foundation-0.1.0').mkdir(parents=True)
            helper = root / 'helper/bin/folio-harfbuzz'
            helper.parent.mkdir(parents=True)
            helper.write_text('not executed by a read-only plan')
            helper.chmod(0o755)
            (root / 'capabilities/foundation-release.yaml').write_text(json.dumps({'release': '0.1.0',
                'environments': 'capabilities/environments.yaml', 'required-artifacts': ['target/foundation-0.1.0/artifacts/pdf-document-0.1.0.jar']}))
            shutil.copyfile(RUNNER.parents[1] / 'capabilities/foundation-environments.yaml', root / 'capabilities/environments.yaml')
            (root / 'target/foundation-0.1.0/build-inputs.json').write_text(json.dumps({'harness': [{'path': 'harness/native-tests.jar'}]}))
            import os
            environment = dict(os.environ, FOLIO_HARFBUZZ_HELPER=str(helper))
            result = subprocess.run([sys.executable, str(RUNNER), 'plan', 'planned', '--root', str(root), '--obligation', 'values'],
                env=environment, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
            self.assertEqual(0, result.returncode, result.stdout)
            plan = json.loads((root / 'planned/plan.json').read_text())
            self.assertEqual('unverified-plan', plan['status'])
            self.assertEqual(8, len(plan['executions']))
            for execution in plan['executions']:
                command = execution['contract-tests-command']
                for name in ('net.zerocloud.pdf.consumer.PdfValueWorkflowTest', 'net.zerocloud.pdf.itext7.consumer.PdfValuesFacadeTest',
                             'net.zerocloud.pdf.migration.itext7.contract.JarContractIT', 'net.zerocloud.pdf.migration.itext7.contract.ClasspathExclusivityIT'):
                    self.assertIn(name, command)
                self.assertIn('net.zerocloud.pdf.acceptance.T09EvidenceCommand', execution['recorder-command'])
                self.assertEqual(83, execution['required-test-count'])
                self.assertIn('IN_PROCESS', execution['settings']['providers'])
                selected = [option for option in command if option.startswith('-Dfolio.t09.executionProfile=')]
                self.assertEqual(1, len(selected))
            self.assertIn('capabilities/profiles/T09-standards', plan['configuration-paths'])
            self.assertFalse((root / 'capabilities/foundation-evidence.yaml').exists())

    def test_refresh_preserves_valid_other_obligations_with_their_original_records(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            previous, fresh, identities = self.indices(root)
            merged = self.publish(root, previous, fresh, identities)
            self.assertEqual(['values', 'transactions'], [item['obligation'] for item in merged['certifications']])
            self.assertEqual(previous['certifications'][0], merged['certifications'][0])
            self.assertEqual(1, len(merged['environments']))

    def test_changed_candidate_contract_or_nested_evidence_is_never_relabelled(self):
        for changed in ('candidate', 'contract', 'report', 'environment'):
            with self.subTest(changed=changed), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                previous, fresh, identities = self.indices(root)
                if changed == 'candidate':
                    previous['candidate']['release'] = 'old'
                elif changed == 'contract':
                    identities['Contract'] = 'c' * 64
                    fresh = self.index(root, 'transactions', identities)
                elif changed == 'report':
                    (root / 'values-observed.pdf').write_bytes(b'tampered after checking')
                else:
                    fresh = self.index(root, 'transactions', identities, 'changed-environment')
                merged = self.publish(root, previous, fresh, identities)
                self.assertEqual(['transactions'], [item['obligation'] for item in merged['certifications']])

    def test_refresh_replaces_its_own_old_scope_without_duplicates(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            previous, fresh, identities = self.indices(root)
            merged = self.publish(root, previous, previous, identities)
            self.assertEqual(previous['certifications'], merged['certifications'])

    def test_environment_observations_bind_raw_tool_paths_without_resolving_them_in_the_repository(self):
        for changed in (False, True):
            with self.subTest(changed=changed), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                previous, fresh, identities = self.indices(root)
                observation = self.ref(root, 'observed-tool.json', {'schema_version': 1,
                    'observer': {'path': '/workspace/scripts/observer.py', 'sha256': 'a' * 64},
                    'installation': {'helper': {'path': 'bin/native-helper', 'sha256': 'b' * 64}}})
                report_path = 'transactions-syntax-report.json'
                report = json.loads((root / report_path).read_text())
                report['environment-observations'] = [observation]
                record = json.loads((root / 'transactions-syntax.json').read_text())
                record['report'] = self.ref(root, report_path, report)
                fresh['certifications'][0]['records'][0] = self.ref(root, 'transactions-syntax.json', record)
                pending, authority = self.prepare(root, previous, fresh, identities)
                before = authority.read_bytes()
                if changed:
                    (root / observation['path']).write_text('changed after observation')
                result = self.invoke(root, pending)
                if changed:
                    self.assertNotEqual(0, result.returncode, result.stdout)
                    self.assertEqual(before, authority.read_bytes())
                else:
                    self.assertEqual(0, result.returncode, result.stdout)
                    self.assertEqual(['values', 'transactions'],
                        [item['obligation'] for item in json.loads(authority.read_text())['certifications']])

    def test_stale_authority_or_incomplete_new_evidence_never_truncates_the_index(self):
        for changed in ('authority', 'new-evidence'):
            with self.subTest(changed=changed), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                previous, fresh, identities = self.indices(root)
                pending, authority = self.prepare(root, previous, fresh, identities)
                if changed == 'authority':
                    authority.write_bytes(authority.read_bytes() + b'\n')
                else:
                    (root / 'transactions-observed.pdf').unlink()
                before = authority.read_bytes()
                result = self.invoke(root, pending)
                self.assertNotEqual(0, result.returncode, result.stdout)
                self.assertEqual(before, authority.read_bytes())
                self.assertEqual([], list(authority.parent.glob('.foundation-evidence-*.tmp')))

    def test_observation_field_elsewhere_keeps_repository_references_recursive(self):
        for location in ('configuration', 'record', 'report-finding'):
            with self.subTest(location=location), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                previous, fresh, identities = self.indices(root)
                ordinary = self.ref(root, 'ordinary.json', {'input': {'path': 'missing.pdf', 'sha256': 'd' * 64}})
                certification = fresh['certifications'][0]
                if location == 'configuration':
                    name = certification['configuration']['path']
                    configuration = json.loads((root / name).read_text())
                    configuration['environment-observations'] = [ordinary]
                    certification['configuration'] = self.ref(root, name, configuration)
                    for index, reference in enumerate(certification['records']):
                        record = json.loads((root / reference['path']).read_text())
                        record['execution-configuration-sha256'] = certification['configuration']['sha256']
                        certification['records'][index] = self.ref(root, reference['path'], record)
                else:
                    reference = certification['records'][0]
                    record = json.loads((root / reference['path']).read_text())
                    if location == 'record':
                        record['environment-observations'] = [ordinary]
                    else:
                        name = record['report']['path']
                        report = json.loads((root / name).read_text())
                        report['findings'] = [{'environment-observations': [ordinary]}]
                        record['report'] = self.ref(root, name, report)
                    certification['records'][0] = self.ref(root, reference['path'], record)
                pending, authority = self.prepare(root, previous, fresh, identities)
                before = authority.read_bytes()
                result = self.invoke(root, pending)
                self.assertNotEqual(0, result.returncode, result.stdout)
                self.assertEqual(before, authority.read_bytes())

    def publish(self, root, previous, fresh, identities):
        pending, authority = self.prepare(root, previous, fresh, identities)
        result = self.invoke(root, pending)
        self.assertEqual(0, result.returncode, result.stdout)
        return json.loads(authority.read_text())

    @staticmethod
    def invoke(root, pending):
        return subprocess.run([sys.executable, str(RUNNER), 'merge-index', str(pending), '--root', str(root)],
                              text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)

    @staticmethod
    def prepare(root, previous, fresh, identities):
        (root / 'capabilities').mkdir()
        authority = root / 'capabilities/foundation-evidence.yaml'
        authority.write_text(json.dumps(previous))
        pending = root / 'observations'
        pending.mkdir()
        (pending / 'observed-index.json').write_text(json.dumps(fresh))
        (pending / 'identities.json').write_text(json.dumps(identities))
        (pending / 'prior-index.sha256').write_text(hashlib.sha256(authority.read_bytes()).hexdigest())
        return pending, authority

    @staticmethod
    def ref(root, name, value):
        (root / name).write_text(json.dumps(value))
        return {'path': name, 'sha256': hashlib.sha256((root / name).read_bytes()).hexdigest()}

    def index(self, root, obligation, identities, environment_name='environment'):
        environment = self.ref(root, environment_name + '.json', {'profile': 'jdk8', 'identity': environment_name})
        candidate = {'release': '0.1.0', 'inputs': [], 'artifacts': []}
        configuration = self.ref(root, obligation + '-execution.json', {'candidate-sha256': identities['Candidate'],
            'environment-sha256': environment['sha256'], 'execution-profile': 'IN_PROCESS'})
        pdf = root / (obligation + '-observed.pdf')
        pdf.write_bytes(b'synthetic unit-test product')
        records = []
        for chain in ('syntax', 'standards', 'semantic', 'visual'):
            report = self.ref(root, obligation + '-' + chain + '-report.json', {'chain': chain,
                'products': [{'path': pdf.name, 'sha256': hashlib.sha256(pdf.read_bytes()).hexdigest()}]})
            records.append(self.ref(root, obligation + '-' + chain + '.json', {'candidate-sha256': identities['Candidate'],
                'contract-sha256': identities['Contract'], 'environment-sha256': environment['sha256'],
                'execution-configuration-sha256': configuration['sha256'], 'execution-profile': 'IN_PROCESS',
                'obligation': obligation, 'chain': chain, 'result': 'pass', 'report': report}))
        certification = {'obligation': obligation, 'environment': 'jdk8', 'execution-profile': 'IN_PROCESS',
                         'configuration': configuration, 'records': records}
        return {'schema-version': 1, 'candidate': candidate, 'environments': [{'profile': 'jdk8', 'record': environment}],
                'certifications': [certification]}

    def indices(self, root):
        identities = {'Candidate': 'a' * 64, 'Contract': 'b' * 64}
        return self.index(root, 'values', identities), self.index(root, 'transactions', identities), identities


class ValuesEvidenceCollectionTest(unittest.TestCase):
    def test_collects_three_products_source_and_all_negative_inputs(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            run = self.observations(root)
            result = self.collect(root, run)
            self.assertEqual(0, result.returncode, result.stdout)
            reports = json.loads(result.stdout)
            for chain in ('syntax', 'standards', 'semantic', 'visual'):
                self.assertEqual(4 if chain == 'visual' else 3, len(reports[chain]['products']))
            self.assertIn('run/source/values.pdf', [item['path'] for item in reports['visual']['products']])
            self.assertIn('run/negative/invalid.pdf', [item['path'] for item in reports['syntax']['negative-controls']])
            self.assertIn('run/negative/unchanged-values.pdf', [item['path'] for item in reports['semantic']['negative-controls']])
            (run / 'native-incremental/values.pdf').write_bytes(b'changed after observation')
            changed = self.collect(root, run)
            self.assertNotEqual(0, changed.returncode)
            self.assertIn('changed', changed.stdout)

    def test_missing_chain_or_undetected_control_is_not_collected(self):
        for negative in (False, True):
            with self.subTest(negative=negative), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                run = self.observations(root)
                target = run / ('negative/result.properties' if negative else 'result.properties')
                target.write_text(target.read_text().replace('visual=fail' if negative else 'visual=pass', 'visual=indeterminate'))
                result = self.collect(root, run)
                self.assertNotEqual(0, result.returncode)
                self.assertIn('visual', result.stdout)

    @staticmethod
    def collect(root, run):
        return subprocess.run([sys.executable, str(RUNNER), 'collect', str(run), '--root', str(root),
                               '--obligation', 'values'], text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)

    @staticmethod
    def observations(root):
        run = root / 'run'
        run.mkdir()
        passing = 'syntax=pass\nstandards=pass\nsemantic=pass\nvisual=pass\n'
        (run / 'result.properties').write_text(passing)
        for product in ('native-rewrite', 'native-incremental', 'facade', 'source', 'negative'):
            directory = run / product
            directory.mkdir()
            pdf = directory / 'values.pdf'
            pdf.write_bytes(('synthetic collection fixture: ' + product).encode())
            result = passing.replace('pass', 'fail') if product == 'negative' else passing
            (directory / 'result.properties').write_text(result + 'input-sha256=' + hashlib.sha256(pdf.read_bytes()).hexdigest() + '\n')
            for chain in ('syntax', 'standards', 'semantic', 'visual'):
                (directory / (chain + '.txt')).write_text('synthetic ' + product + ' ' + chain)
            if product in ('source', 'negative'):
                continue
            for checker in ('pdfcpu', 'arlington'):
                tool = directory / checker
                tool.mkdir()
                (tool / 'standards.properties').write_text('result=pass\ncovered-rules=unit-rule\n')
                for name in ('findings.txt', 'negative-unit-rule.txt', 'control-unit-rule.pdf'):
                    (tool / name).write_text('synthetic rule qualification')
        for name in ('invalid.pdf', 'unchanged-values.pdf'):
            (run / 'negative' / name).write_bytes(b'synthetic negative artifact')
        return run


if __name__ == '__main__':
    unittest.main()
