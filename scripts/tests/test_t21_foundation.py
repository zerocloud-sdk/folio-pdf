"""T21 collector guard tests; synthetic records never certify an environment."""
import copy
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
import t21_foundation_reports as reports
from t13_foundation_reports import RetainedFiles


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def properties(path, values):
    path.write_text(''.join(name + '=' + value + '\n' for name, value in values.items()))


def seal(run, result=None):
    manifest = run / 'retained-files.sha256'
    manifest.write_text(''.join(digest(path) + '  ' + path.relative_to(run).as_posix() + '\n'
        for path in sorted(run.rglob('*')) if path.is_file() and path.name not in ('result.properties', 'retained-files.sha256')))
    result = result or {'profile': reports.PROFILE, 'native-execution-profile': 'HARDENED_WORKER',
        'facade-execution-profile': 'IN_PROCESS', **dict.fromkeys(reports.CHAINS, 'pass')}
    properties(run / 'result.properties', {**result, 'retained-files-sha256': digest(manifest)})


class T21FoundationTest(unittest.TestCase):
    def authority(self, root):
        profile = root / 'capabilities/profiles/T21-hardened-worker'
        profile.mkdir(parents=True)
        for name in ('coverage.json', 'mandatory-tests.txt'):
            (profile / name).write_bytes((ROOT / profile.relative_to(root) / name).read_bytes())
        return json.loads((profile / 'coverage.json').read_text())

    def fixture(self, root):
        inventory = self.authority(root)
        run = root / 'observations'; run.mkdir()
        values = {'execution-profile': 'HARDENED_WORKER', 'case-count': str(len(inventory['cases']))}
        for name, case in inventory['cases'].items():
            values[name + '.result'] = values[name + '.cleanup'] = 'pass'
            if case.get('code'):
                values[name + '.code'] = case['code']; values[name + '.diagnostic'] = 'A fixed content-free diagnostic.'
            values.update((name + '.' + key, value) for key, value in case.get('observations', {}).items())
        properties(run / 'observations.properties', values)
        properties(run / 'facade-observations.properties', inventory['facade-observations'])
        for name in ('checked-failure', 'cancelled', 'deadline', 'caller-failure', 'native-later', 'facade-later'):
            (run / (name + '.sentinel')).write_bytes(bytes((1, 2, 3)) if name == 'facade-later' else bytes((17, 18, 19)))
        seal(run)
        return run

    def test_missing_duplicate_and_unpaired_expected_execution_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); self.authority(root)
            self.assertEqual(137, len(reports.mandatory_tests(root)[0]))
            path = root / reports.TESTS; original = path.read_text(); lines = original.splitlines()
            for value in ('\n'.join(lines[:-1]) + '\n', original + lines[0] + '\n', original.replace('[HARDENED_WORKER]', '[UNDECLARED]')):
                path.write_text(value)
                with self.assertRaises(ValueError): reports.mandatory_tests(root)
            path.write_text(original)

    def test_public_coverage_receipts_safe_failures_and_facade_identity_are_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); run = self.fixture(root)
            self.assertEqual(8, len(reports.closed_observations(root, RetainedFiles(run))['cases']))
            path = run / 'observations.properties'; original = path.read_text()
            for before, after in (
                ('ordered-prefix.result=pass\n', ''), ('ordered-prefix.barrier-pages=2', 'ordered-prefix.barrier-pages=1'),
                ('checked-failure.code=PAGE_POSITION_INVALID', 'checked-failure.code=WORKER_TERMINATED'),
                ('ordered-publication.receipts=first:COMMITTED:false,current:FAILED:true,later:NOT_ATTEMPTED:false',
                 'ordered-publication.receipts=first:COMMITTED:false,current:COMMITTED:false,later:NOT_ATTEMPTED:false'),
                ('checked-failure.receipts=result:NOT_ATTEMPTED:false', 'checked-failure.receipts=wrong:NOT_ATTEMPTED:false'),
                ('caller-ownership.receipts=result:COMMITTED:false', 'caller-ownership.receipts=result:COMMITTED:true'),
                ('case-count=8', 'case-count=7'), ('execution-profile=HARDENED_WORKER', 'execution-profile=IN_PROCESS'),
                ('owned-cleanup.empty=true', 'owned-cleanup.empty=false'),
                ('deadline.diagnostic=A fixed content-free diagnostic.', 'deadline.diagnostic=private.pdf PDFBox Exception')):
                self.assertIn(before, original); path.write_text(original.replace(before, after)); seal(run)
                with self.assertRaises(ValueError): reports.closed_observations(root, RetainedFiles(run))
            path.write_text(original + 'ordered-prefix.result=pass\n'); seal(run)
            with self.assertRaises(ValueError): reports.closed_observations(root, RetainedFiles(run))
            path.write_text(original); seal(run)
            facade = run / 'facade-observations.properties'; observed_facade = facade.read_text()
            for before, after in (
                ('stream-ownership.receipts=target:COMMITTED:false', 'stream-ownership.receipts=wrong:COMMITTED:false'),
                ('stream-ownership.receipts=target:COMMITTED:false', 'stream-ownership.receipts=target:COMMITTED:true'),
                ('partial-publication.receipts=first:COMMITTED:false,current:FAILED:true,later:NOT_ATTEMPTED:false',
                 'partial-publication.receipts=first:COMMITTED:true,current:FAILED:true,later:NOT_ATTEMPTED:false'),
                ('partial-publication.receipts=first:COMMITTED:false,current:FAILED:true,later:NOT_ATTEMPTED:false',
                 'partial-publication.receipts=first:COMMITTED:false,current:FAILED:false,later:NOT_ATTEMPTED:false'),
                ('partial-publication.receipts=first:COMMITTED:false,current:FAILED:true,later:NOT_ATTEMPTED:false',
                 'partial-publication.receipts=first:COMMITTED:false,current:FAILED:true,later:NOT_ATTEMPTED:true')):
                self.assertIn(before, observed_facade); facade.write_text(observed_facade.replace(before, after)); seal(run)
                with self.assertRaises(ValueError): reports.closed_observations(root, RetainedFiles(run))
            facade.write_text(observed_facade); seal(run)
            sentinel = run / 'native-later.sentinel'; sentinel.write_bytes(b'altered'); seal(run)
            with self.assertRaises(ValueError): reports.closed_observations(root, RetainedFiles(run))

    def test_summary_pass_missing_tools_or_mislabeled_profile_do_not_certify(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); run = self.fixture(root)
            for mode in (None, 'IN_PROCESS', 'HARDENED_WORKER'):
                with self.assertRaises((OSError, ValueError)): reports.collect_reports(root, run, mode)
            original = (run / 'result.properties').read_text()
            for before, after in (('contract=pass\n', ''), ('standards=pass', 'standards=indeterminate'),
                                  ('facade-execution-profile=IN_PROCESS', 'facade-execution-profile=HARDENED_WORKER')):
                (run / 'result.properties').write_text(original.replace(before, after))
                with self.assertRaises(ValueError): reports.collect_reports(root, run, 'HARDENED_WORKER')
            (run / 'result.properties').write_text(original)
            (run / 'observations.properties').write_text('altered original raw findings\n')
            with self.assertRaises(ValueError): RetainedFiles(run)

    def test_resealed_original_boundary_findings_cannot_replace_live_observations(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            old = root / 'capabilities/old'; fresh = root / 'capabilities/fresh'
            old.mkdir(parents=True); fresh.mkdir()
            original = {'pid': '123', 'root': '/tmp/owned/.folio-pdf-workflow-old', 'java-build': 'actual'}
            live = {**original, 'pid': '456', 'root': '/tmp/owned/.folio-pdf-workflow-new'}
            for path, data in ((old, original), (fresh, live)):
                (path / 'launcher').mkdir(); properties(path / 'launcher/actual.properties', data)
                properties(path / 'observations.properties', {'denied-inet': 'pass', 'permitted-inet': 'pass'})
                seal(path, {'profile': 'protocol-only', 'result': 'pass'})
            reports.compare_boundary(RetainedFiles(old), RetainedFiles(fresh), original, live)
            properties(old / 'observations.properties', {'denied-inet': 'fail', 'permitted-inet': 'pass'})
            seal(old, {'profile': 'protocol-only', 'result': 'pass'})
            with self.assertRaises(ValueError): reports.compare_boundary(RetainedFiles(old), RetainedFiles(fresh), original, live)
            properties(old / 'observations.properties', {'denied-inet': 'pass', 'permitted-inet': 'pass'})
            properties(old / 'launcher/actual.properties', {**original, 'java-build': 'forged'})
            seal(old, {'profile': 'protocol-only', 'result': 'pass'})
            with self.assertRaises(ValueError): reports.compare_boundary(RetainedFiles(old), RetainedFiles(fresh), original, live)

    def test_every_actual_case_and_zero_skips_are_required(self):
        driver = reports.foundation()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); self.authority(root)
            scope = root / 'scope'; scope.mkdir()
            plan = {'contract-tests-command': ['test-only-identity-fixture'], 'required-test-count': 137}
            (scope / 'contract-tests-command.json').write_text(json.dumps(plan['contract-tests-command']))
            boundary = scope / 'boundary-observations'; boundary.mkdir()
            (boundary / 'witness.txt').write_text('protocol fixture\n')
            seal(boundary, {'profile': 'T21-hardened-worker-boundary', 'result': 'pass', 'required-case-count': '137'})
            tests, _ = reports.mandatory_tests(root)
            transcript = 'JUnit version 4.13.2\n' + ''.join('T21 CASE ' + case + ' = PASS\n' for case in sorted(tests))
            transcript += 'T21 required execution: tests=137, failures=0, ignored=0, assumptions=0, duplicates=0\nOK (137 tests)\n'
            transcript += 'T21 boundary retained-sha256=' + digest(boundary / 'retained-files.sha256') + '\n'
            path = scope / 'contract-tests.txt'; path.write_text(transcript)
            reports.require_contract_tests(root, scope, plan)
            case = 'T21 CASE ' + sorted(tests)[0] + ' = PASS\n'
            for variant in (transcript.replace(case, ''), transcript + case,
                    transcript.replace(case, case.replace('PASS', 'FAIL')), transcript.replace('ignored=0', 'ignored=1'),
                    transcript.replace('assumptions=0', 'assumptions=1'), transcript.replace('duplicates=0', 'duplicates=1'),
                    transcript.replace('OK (137 tests)', 'OK (138 tests)'), transcript.replace('retained-sha256=', 'unbound-sha256=')):
                path.write_text(variant)
                with self.assertRaises(ValueError): reports.require_contract_tests(root, scope, plan)

    def test_candidate_environment_configuration_and_support_command_are_bound(self):
        driver = reports.foundation()
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, {'FOLIO_FOUNDATION_PYTHON_ROOT': ''}):
            root = Path(directory); self.authority(root)
            scope = root / 'certifications/jdk8-hardened_worker'; scope.mkdir(parents=True)
            environment_dir = scope.parent / 'jdk8-environment'; environment_dir.mkdir()
            import yaml
            profile = yaml.safe_load((ROOT / 'capabilities/foundation-environments.yaml').read_text())['profiles'][0]
            env_path = root / 'capabilities/foundation-environments.yaml'; env_path.write_text(json.dumps({'profiles': [profile]}))
            environment = {'schema-version': 1, 'profile': profile['id'], 'identity': profile['identity']}
            environment_path = environment_dir / 'environment.yaml'; environment_path.write_text(json.dumps(environment))
            artifact = root / 'target/foundation-0.1.0/harness/protocol-fixture.jar'; artifact.parent.mkdir(parents=True)
            artifact.write_bytes(b'not a production artifact; binding guard fixture')
            base = artifact.parent.parent
            (base / 'build-inputs.json').write_text('{}\n')
            contract = {'environments': env_path.relative_to(root).as_posix(), 'required-artifacts': []}
            receipt = {'harness': [{'path': artifact.relative_to(root).as_posix(), 'sha256': digest(artifact)}]}
            identities = {'Candidate': 'a' * 64, 'Contract': 'b' * 64}; helper = root / 'native/bin/helper'
            plan = driver.execution_plan(root, scope, profile['identity']['image'], helper, '/workspace/' + artifact.relative_to(root).as_posix(),
                                         driver.certification_case('worker'), 'HARDENED_WORKER')
            config = {'schema-version': 1, 'candidate-sha256': identities['Candidate'], 'environment-sha256': digest(environment_path),
                'acceptance-profile': reports.PROFILE, 'execution-profile': 'HARDENED_WORKER', 'command': plan['recorder-command'],
                'java-options': plan['java-options'], 'locale': 'en_US / C.UTF-8', 'timezone': 'UTC', 'settings': plan['settings'],
                'support-commands': plan['support-commands'], 'inputs': driver.certification_inputs(root, contract, receipt, driver.certification_case('worker'))}
            path = scope / 'execution.yaml'; path.write_text(json.dumps(config))
            reports.bound_execution(root, scope, contract, receipt, identities, helper, driver)
            for key, value in (('candidate-sha256', 'c' * 64), ('environment-sha256', 'd' * 64), ('locale', 'zh_CN'),
                               ('timezone', 'Asia/Shanghai'), ('execution-profile', 'IN_PROCESS'), ('support-commands', []), ('inputs', [])):
                path.write_text(json.dumps({**config, key: value}))
                with self.assertRaises(ValueError): reports.bound_execution(root, scope, contract, receipt, identities, helper, driver)
            path.write_text(json.dumps(config)); artifact.write_bytes(b'changed current artifact')
            with self.assertRaises(ValueError): reports.bound_execution(root, scope, contract, receipt, identities, helper, driver)

    def test_missing_chain_duplicate_chain_and_forged_producer_cannot_merge(self):
        import yaml
        driver = reports.foundation()
        for serialize, suffix in ((json.dumps, '.json'), (yaml.safe_dump, '.yaml')):
            with self.subTest(format=suffix), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                def ref(name, value):
                    path = root / (name + suffix)
                    path.write_text(serialize(value))
                    return {'path': path.name, 'sha256': digest(path)}
                environment = ref('environment', {'identity': 'binding guard fixture only'})
                config = ref('configuration', {'candidate-sha256': 'a' * 64, 'environment-sha256': environment['sha256']})
                records = []
                for chain in reports.CHAINS:
                    records.append(ref(chain, {'obligation': 'worker', 'execution-profile': 'HARDENED_WORKER',
                        'candidate-sha256': 'a' * 64, 'contract-sha256': 'b' * 64, 'environment-sha256': environment['sha256'],
                        'execution-configuration-sha256': config['sha256'], 'result': 'pass', 'chain': chain,
                        'producer': driver.certification_producers(driver.certification_case('worker'))[chain]}))
                index = {'candidate': {}, 'environments': [{'profile': 'fixture', 'record': environment}],
                         'certifications': [{'obligation': 'worker', 'environment': 'fixture', 'execution-profile': 'HARDENED_WORKER',
                                             'configuration': config, 'records': records}]}
                fresh = {**index, 'certifications': []}
                identities = {'Candidate': 'a' * 64, 'Contract': 'b' * 64}
                self.assertEqual(1, len(driver.merge_evidence(root, index, fresh, identities)['certifications']))
                for incomplete in (records[:-1], records + [records[0]]):
                    altered = copy.deepcopy(index)
                    altered['certifications'][0]['records'] = incomplete
                    self.assertFalse(driver.merge_evidence(root, altered, fresh, identities)['certifications'])
                changed = yaml.safe_load((root / ('standards' + suffix)).read_text())
                changed['producer'] = 'qpdf'
                altered = copy.deepcopy(index)
                altered['certifications'][0]['records'][1] = ref('standards', changed)
                self.assertFalse(driver.merge_evidence(root, altered, fresh, identities)['certifications'])

    def test_raw_findings_controls_and_products_cannot_be_resealed_as_live(self):
        import shutil
        driver = reports.foundation()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); original = root / 'observations'; original.mkdir()
            for name in ('syntax.txt', 'negative-control.txt', 'actual-product.bin'):
                (original / name).write_bytes(b'original observed bytes\n')
            seal(original)
            replay = root / 'replay'; shutil.copytree(original, replay / 'observations')
            reports.compare_originals(root, RetainedFiles(original), replay, {}, driver)
            for name in ('syntax.txt', 'negative-control.txt', 'actual-product.bin'):
                path = original / name; path.write_bytes(b'changed original with a new manifest\n'); seal(original)
                with self.assertRaises(ValueError):
                    reports.compare_originals(root, RetainedFiles(original), replay, {}, driver)
                path.write_bytes(b'original observed bytes\n'); seal(original)

    def test_declared_worker_scope_and_qualified_producers_are_exact(self):
        driver = reports.foundation(); case = driver.certification_case('worker')
        self.assertEqual(('HARDENED_WORKER',), case['execution-profiles'])
        self.assertEqual(reports.CHAINS, case['chains'])
        self.assertEqual({'syntax': 'qpdf', 'standards': 'arlington', 'semantic': 'folio-pdf-t21',
                         'visual': 'pdfium-cli', 'contract': 'folio-pdf-t21'}, driver.certification_producers(case))
        self.assertEqual(137, case['test-count'])
        self.assertEqual(['semantic', 'contract'], next(tool['chains'] for tool in driver.certification_tools() if tool['id'] == 'folio-pdf-t21'))
        with self.assertRaises(ValueError): driver.execution_plan(Path('/repo'), Path('/repo/scope'), 'image', Path('/native/bin/helper'), 'classpath', case, 'IN_PROCESS')


if __name__ == '__main__':
    unittest.main()
