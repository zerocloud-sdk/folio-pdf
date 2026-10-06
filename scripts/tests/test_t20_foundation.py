"""T20 collector rejection and scope contracts; no protocol fixture certifies a platform."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
import t20_foundation_reports as reports
from t13_foundation_reports import RetainedFiles


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def seal(run):
    manifest = run / 'retained-files.sha256'
    manifest.write_text(''.join(digest(path) + '  ' + path.relative_to(run).as_posix() + '\n'
        for path in sorted(run.rglob('*')) if path.is_file() and path.name not in ('result.properties', 'retained-files.sha256')))
    record = {'profile': reports.PROFILE, 'native-execution-profile': 'IN_PROCESS',
              'facade-execution-profile': 'IN_PROCESS', **dict.fromkeys(reports.CHAINS, 'pass'),
              'retained-files-sha256': digest(manifest)}
    (run / 'result.properties').write_text(''.join(key + '=' + value + '\n' for key, value in record.items()))


class T20FoundationTest(unittest.TestCase):
    def fixture(self, root):
        authority = root / 'capabilities/profiles/T20-hostile-input/coverage.json'
        authority.parent.mkdir(parents=True)
        authority.write_bytes((ROOT / authority.relative_to(root)).read_bytes())
        inventory = json.loads(authority.read_text())
        run = root / 'observations'
        run.mkdir()
        observed = {'execution-profile': 'IN_PROCESS', 'case-count': str(len(inventory['cases']))}
        for name, case in inventory['cases'].items():
            observed[name + '.result'] = observed[name + '.cleanup'] = 'pass'
            if case.get('code'):
                observed[name + '.code'] = case['code']
                observed[name + '.diagnostic'] = 'A fixed content-free diagnostic.'
            for key, value in case.get('observations', {}).items():
                observed[name + '.' + key] = value
        (run / 'observations.properties').write_text(''.join(key + '=' + value + '\n' for key, value in observed.items()))
        (run / 'facade-observations.properties').write_text(''.join(key + '=' + value + '\n'
            for key, value in inventory['facade-observations'].items()))
        seal(run)
        return run

    def test_closed_inventory_and_resealed_missing_wrong_or_undetected_cases(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            run = self.fixture(root)
            self.assertEqual(75, len(reports.closed_observations(root, RetainedFiles(run))['cases']))
            path = run / 'observations.properties'
            original = path.read_text()
            for before, after in (
                ('input-path-excess.code=WORKFLOW_INPUT_LIMIT_EXCEEDED\n', ''),
                ('concurrency-active.code=CONCURRENCY_LIMIT_EXCEEDED', 'concurrency-active.code=INVALID_REQUEST'),
                ('elapsed-equality.result=pass\n', ''),
                ('filter-patch-excess.cleanup=pass', 'filter-patch-excess.cleanup=fail'),
                ('publication-ordered-partial.receipts=COMMITTED:false,FAILED:true,NOT_ATTEMPTED:false',
                 'publication-ordered-partial.receipts=COMMITTED:false,COMMITTED:false,NOT_ATTEMPTED:false'),
                ('case-count=75', 'case-count=74')):
                self.assertIn(before, original)
                path.write_text(original.replace(before, after))
                seal(run)
                with self.assertRaises(ValueError): reports.closed_observations(root, RetainedFiles(run))
                path.write_text(original)
                seal(run)

    def test_missing_tools_or_execution_identity_cannot_become_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            run = self.fixture(root)
            for mode in (None, 'HARDENED_WORKER', 'IN_PROCESS'):
                with self.assertRaises((OSError, ValueError)):
                    reports.collect_reports(root, run, mode)
            pin = root / 'scripts/t20-evidence-pin.properties'
            pin.parent.mkdir()
            pin.write_text('.build-cache/missing-tool=' + '0' * 64 + '\n')
            with self.assertRaises((OSError, ValueError)):
                reports.collect_reports(root, run, 'IN_PROCESS')

    def test_failed_indeterminate_missing_chain_and_tampered_retention_reject(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            run = self.fixture(root)
            path = run / 'result.properties'
            original = path.read_text()
            for before, after in (('standards=pass', 'standards=indeterminate'), ('contract=pass\n', ''),
                                  ('syntax=pass', 'syntax=fail'), ('facade-execution-profile=IN_PROCESS', 'facade-execution-profile=HARDENED_WORKER')):
                path.write_text(original.replace(before, after))
                with self.assertRaises(ValueError): reports.collect_reports(root, run, 'IN_PROCESS')
            path.write_text(original)
            (run / 'observations.properties').write_text('altered raw findings\n')
            with self.assertRaises(ValueError): RetainedFiles(run)

    def test_scope_requires_four_in_process_tuples_and_separate_contract_producer(self):
        driver = reports.foundation()
        case = driver.certification_case('limits')
        self.assertEqual(('IN_PROCESS',), case['execution-profiles'])
        self.assertEqual(reports.CHAINS, case['chains'])
        self.assertEqual('folio-pdf-t20', driver.certification_producers(case)['contract'])
        self.assertEqual(133, case['test-count'])
        self.assertEqual(['-Dfolio.t09.executionProfile=IN_PROCESS'], case['additional-java-options'])
        tools = {tool['id']: tool for tool in driver.certification_tools()}
        self.assertEqual(['semantic', 'contract'], tools['folio-pdf-t20']['chains'])
        with self.assertRaises(ValueError):
            driver.execution_plan(Path('/repo'), Path('/repo/scope'), 'image', Path('/native/bin/helper'), 'classpath', case, 'HARDENED_WORKER')
        self.assertNotIn('execution-profiles', driver.certification_case('transactions'))
        self.assertEqual(('syntax', 'standards', 'semantic', 'visual'), driver.CHAINS)

    def test_original_candidate_environment_locale_and_complete_inputs_are_bound(self):
        driver = reports.foundation()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            scope = root / 'certifications/jdk8-in_process'
            scope.mkdir(parents=True)
            environment_dir = scope.parent / 'jdk8-environment'
            environment_dir.mkdir()
            import yaml
            profile = yaml.safe_load((ROOT / 'capabilities/foundation-environments.yaml').read_text())['profiles'][0]
            environments = root / 'capabilities/foundation-environments.yaml'
            environments.parent.mkdir()
            environments.write_text(json.dumps({'profiles': [profile]}))
            environment = {'schema-version': 1, 'profile': profile['id'], 'identity': profile['identity']}
            environment_path = environment_dir / 'environment.yaml'
            environment_path.write_text(json.dumps(environment))
            artifact = root / 'target/foundation-0.1.0/harness/protocol-fixture.jar'
            artifact.parent.mkdir(parents=True)
            artifact.write_bytes(b'identity protocol fixture; not a candidate artifact')
            build_record = artifact.parent.parent / 'build-inputs.json'
            build_record.write_text('{}\n')
            contract = {'environments': environments.relative_to(root).as_posix(), 'required-artifacts': []}
            receipt = {'harness': [{'path': artifact.relative_to(root).as_posix(), 'sha256': digest(artifact)}]}
            identities = {'Candidate': 'a' * 64, 'Contract': 'b' * 64}
            helper = root / 'native/bin/helper'
            case = driver.certification_case('limits')
            cp = '/workspace/' + artifact.relative_to(root).as_posix()
            plan = driver.execution_plan(root, scope, profile['identity']['image'], helper, cp, case, 'IN_PROCESS')
            config = {'schema-version': 1, 'candidate-sha256': identities['Candidate'],
                'environment-sha256': digest(environment_path), 'acceptance-profile': reports.PROFILE,
                'execution-profile': 'IN_PROCESS', 'command': plan['recorder-command'], 'java-options': plan['java-options'],
                'locale': 'en_US / C.UTF-8', 'timezone': 'UTC', 'settings': plan['settings'],
                'inputs': driver.certification_inputs(root, contract, receipt, case)}
            path = scope / 'execution.yaml'
            path.write_text(json.dumps(config))
            reports.bound_execution(root, scope, contract, receipt, identities, helper, driver)
            variants = []
            for key, value in (('candidate-sha256', 'c' * 64), ('environment-sha256', 'd' * 64),
                    ('timezone', 'Asia/Shanghai'), ('locale', 'zh_CN'), ('inputs', [])):
                variants.append({**config, key: value})
            changed_image = copy.deepcopy(config)
            changed_image['command'][changed_image['command'].index(profile['identity']['image'])] = 'unobserved-image'
            variants.append(changed_image)
            for altered in variants:
                path.write_text(json.dumps(altered))
                with self.assertRaises(ValueError):
                    reports.bound_execution(root, scope, contract, receipt, identities, helper, driver)
            path.write_text(json.dumps(config))
            artifact.write_bytes(b'changed current artifact')
            with self.assertRaises(ValueError):
                reports.bound_execution(root, scope, contract, receipt, identities, helper, driver)
            artifact.write_bytes(b'identity protocol fixture; not a candidate artifact')
            environment_path.write_text(json.dumps({**environment, 'profile': 'wrong-tuple'}))
            with self.assertRaises(ValueError):
                reports.bound_execution(root, scope, contract, receipt, identities, helper, driver)

    def test_complete_public_suite_and_its_actual_command_are_mandatory(self):
        with tempfile.TemporaryDirectory() as directory:
            scope = Path(directory)
            plan = {'contract-tests-command': ['protocol-only', 'IN_PROCESS'], 'required-test-count': 133}
            with self.assertRaises(OSError): reports.require_contract_tests(scope, plan)
            command = scope / 'contract-tests-command.json'
            command.write_text(json.dumps(plan['contract-tests-command']))
            transcript = scope / 'contract-tests.txt'
            clean = 'T20 required execution: tests=133, failures=0, ignored=0, assumptions=0\n'
            for result in ('OK (132 tests)', 'OK (183 tests)', 'FAILURES!!!\nOK (133 tests)', 'OK (0 tests)',
                           'OK (133 tests)\n' + clean.replace('assumptions=0', 'assumptions=1'),
                           'OK (133 tests)\n' + clean.replace('ignored=0', 'ignored=1'),
                           'OK (133 tests)'):
                transcript.write_text('JUnit version 4.13.2\n' + result + '\n')
                with self.assertRaises(ValueError): reports.require_contract_tests(scope, plan)
            transcript.write_text('JUnit version 4.13.2\n' + '.' * 133 + '\nTime: 0.1\n\nOK (133 tests)\n' + clean)
            reports.require_contract_tests(scope, plan)
            command.write_text(json.dumps(['protocol-only', 'HARDENED_WORKER']))
            with self.assertRaises(ValueError): reports.require_contract_tests(scope, plan)

    def test_resealing_changed_original_findings_does_not_replace_live_observations(self):
        driver = reports.foundation()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            original = self.fixture(root)
            (original / 'syntax.txt').write_text('original independent finding\n')
            seal(original)
            replay = root / 'replay'
            import shutil
            shutil.copytree(original, replay / 'observations')
            reports.compare_originals(root, RetainedFiles(original), replay, {}, driver)
            (original / 'syntax.txt').write_text('changed finding hidden by a resealed manifest\n')
            seal(original)
            with self.assertRaises(ValueError):
                reports.compare_originals(root, RetainedFiles(original), replay, {}, driver)

    def test_changed_producer_or_missing_contract_cannot_merge_a_passing_tuple(self):
        import yaml
        driver = reports.foundation()
        for serialize, suffix in ((json.dumps, '.json'), (yaml.safe_dump, '.yaml')):
            with self.subTest(format=suffix), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                def ref(name, value):
                    path = root / (name + suffix)
                    path.write_text(serialize(value))
                    return {'path': path.name, 'sha256': digest(path)}
                environment = ref('environment', {'identity': 'protocol fixture only'})
                config = ref('configuration', {'candidate-sha256': 'a' * 64, 'environment-sha256': environment['sha256']})
                records = []
                for chain in reports.CHAINS:
                    records.append(ref(chain, {'obligation': 'limits', 'execution-profile': 'IN_PROCESS',
                        'candidate-sha256': 'a' * 64, 'contract-sha256': 'b' * 64, 'environment-sha256': environment['sha256'],
                        'execution-configuration-sha256': config['sha256'], 'result': 'pass', 'chain': chain,
                        'producer': driver.certification_producers(driver.certification_case('limits'))[chain]}))
                index = {'candidate': {}, 'environments': [{'profile': 'fixture', 'record': environment}],
                         'certifications': [{'obligation': 'limits', 'environment': 'fixture', 'execution-profile': 'IN_PROCESS',
                                             'configuration': config, 'records': records}]}
                fresh = {**index, 'certifications': []}
                identities = {'Candidate': 'a' * 64, 'Contract': 'b' * 64}
                self.assertEqual(1, len(driver.merge_evidence(root, index, fresh, identities)['certifications']))
                altered = copy.deepcopy(index)
                altered['certifications'][0]['records'] = records[:-1]
                self.assertFalse(driver.merge_evidence(root, altered, fresh, identities)['certifications'])
                record = yaml.safe_load((root / ('standards' + suffix)).read_text())
                record['producer'] = 'qpdf'
                altered['certifications'][0]['records'] = records
                altered['certifications'][0]['records'][1] = ref('standards', record)
                self.assertFalse(driver.merge_evidence(root, altered, fresh, identities)['certifications'])

    def test_raw_observer_roles_preserve_composed_hashes_and_reject_tampering(self):
        driver = reports.foundation()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)

            def ref(name, value):
                path = root / name
                path.write_text(json.dumps(value))
                return driver.reference(root, path)

            environment = ref('environment.json', {'identity': 'protocol fixture only'})
            config = ref('configuration.json', {'candidate-sha256': 'a' * 64,
                'environment-sha256': environment['sha256']})
            report = {'chain': 'contract', 'result': 'pass', 'findings': []}
            for name in ('before', 'after', 'original'):
                observer_dir = root / name
                observer_dir.mkdir()
                # Actual observer payloads contain container-local installation paths.
                ref(name + '/native-observation.json', {
                    'executable': {'path': '/workspace/scripts/container-bin/native', 'sha256': 'c' * 64}})
                driver.append_environment_observations(root, observer_dir, report)
            self.assertEqual(3, len(report['environment-observations']))
            report_ref = ref('contract-report.json', report)
            records = []
            for chain in reports.CHAINS:
                record = {'obligation': 'limits', 'execution-profile': 'IN_PROCESS',
                    'candidate-sha256': 'a' * 64, 'contract-sha256': 'b' * 64,
                    'environment-sha256': environment['sha256'],
                    'execution-configuration-sha256': config['sha256'], 'result': 'pass', 'chain': chain,
                    'producer': driver.certification_producers(driver.certification_case('limits'))[chain]}
                if chain == 'contract':
                    record['report'] = report_ref
                records.append(ref(chain + '.json', record))
            index = {'candidate': {}, 'environments': [{'profile': 'fixture', 'record': environment}],
                'certifications': [{'obligation': 'limits', 'environment': 'fixture',
                    'execution-profile': 'IN_PROCESS', 'configuration': config, 'records': records}]}
            empty = {**index, 'certifications': []}
            identities = {'Candidate': 'a' * 64, 'Contract': 'b' * 64}

            def accepted():
                return driver.merge_evidence(root, index, empty, identities)['certifications']

            self.assertEqual(1, len(accepted()))
            raw = root / 'after/native-observation.json'
            original = raw.read_bytes()
            raw.write_text('changed raw observation')
            self.assertFalse(accepted())
            raw.unlink()
            self.assertFalse(accepted())
            raw.write_bytes(original)
            self.assertEqual(1, len(accepted()))
            # The raw-payload exception is confined to the existing report role.
            report['findings'] = report.pop('environment-observations')
            record = json.loads((root / 'contract.json').read_text())
            record['report'] = ref('contract-report.json', report)
            records[-1] = ref('contract.json', record)
            self.assertFalse(accepted())

    def test_original_corpus_reproduces_without_pdf_implementation(self):
        spec = importlib.util.spec_from_file_location('t20_corpus', ROOT / 'scripts/generate-t20-corpus.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            module.create(output)
            for path in output.iterdir():
                self.assertEqual((ROOT / 'capabilities/profiles/T20-hostile-input' / path.name).read_bytes(), path.read_bytes())
