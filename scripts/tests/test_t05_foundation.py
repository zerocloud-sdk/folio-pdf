"""Provider qualification guards; synthetic records never certify an environment."""
import copy
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
import t05_foundation_reports as reports
from t13_foundation_reports import RetainedFiles


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def properties(path, values):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(''.join(key + '=' + value + '\n' for key, value in values.items()))


def seal(run):
    manifest = run / 'retained-files.sha256'
    manifest.write_text(''.join(digest(path) + '  ' + path.relative_to(run).as_posix() + '\n'
        for path in sorted(run.rglob('*')) if path.is_file() and path.name not in ('result.properties', 'retained-files.sha256')))
    properties(run / 'result.properties', {'profile': reports.PROFILE, 'native-execution-profile': 'IN_PROCESS',
        'facade-execution-profile': 'IN_PROCESS', **dict.fromkeys(reports.CHAINS, 'pass'), 'retained-files-sha256': digest(manifest)})


class ProviderFoundationTest(unittest.TestCase):
    def authority(self, root):
        target = root / Path(reports.INVENTORY).parent; target.mkdir(parents=True)
        for name in ('coverage.json', 'mandatory-tests.txt'):
            (target / name).write_bytes((ROOT / target.relative_to(root) / name).read_bytes())
        return json.loads((target / 'coverage.json').read_text())

    def fixture(self, root):
        inventory = self.authority(root)
        run = root / 'capabilities/evidence/foundation/synthetic/providers/jdk17-in_process/observations'; run.mkdir(parents=True)
        values = {'execution-profile': 'IN_PROCESS', 'case-count': str(len(inventory['cases']))}
        for name, case in inventory['cases'].items():
            values[name + '.result'] = 'pass'
            if 'code' in case: values[name + '.code'] = case['code']
            values.update((name + '.' + key, value) for key, value in case['observations'].items())
            if 'sentinel-sha256' in case['observations']:
                (run / (name + '.sentinel' if name != 'native-partial-publication' else 'native-later.sentinel')).write_bytes(reports.PAYLOAD)
        (run / 'facade-later.sentinel').write_bytes(bytes((1, 2, 3)))
        properties(run / 'observations.properties', values); properties(run / 'facade-observations.properties', inventory['facade-observations'])
        properties(run / 'endpoint.properties', {'address': '127.0.0.1', 'port': '31415', 'requests': '2', 'disclosed-bytes': '8',
            'adapter-java-build': 'synthetic-build', 'protocol': 't05-synthetic-length-framed-v1', 'adapter-pid': '1234', 'parent-pid': '1234'})
        for index in range(2):
            (run / ('endpoint-request-' + str(index) + '.bin')).write_bytes(reports.PAYLOAD)
            (run / ('endpoint-response-' + str(index) + '.bin')).write_bytes(reports.RESPONSE)
        directory = run / 'subprocess'; directory.mkdir()
        inside = '/workspace/' + run.relative_to(root).as_posix()
        for mode, fixed in inventory['subprocesses'].items():
            started = fixed['direct-child-state'] == 'terminated'
            actual = directory / (mode + '-child.properties')
            properties(directory / (mode + '-lifecycle.properties'), {**fixed, 'mode': mode, 'elapsed-millis': '150',
                **({'direct-child-pid': '9876'} if started else {})})
            caller = directory / (mode + '-root/caller-owned.sentinel'); caller.parent.mkdir(); caller.write_bytes(reports.PAYLOAD)
            args = [inside + '/subprocess/startup-root/missing-program'] if mode == 'startup' else ['/opt/java/openjdk/bin/java', '-cp',
                '/workspace/target/foundation-0.1.0/harness/acceptance.jar', 'net.zerocloud.pdf.acceptance.T05ProcessFixture',
                'echo' if mode == 'first-excess-output' else mode, inside + '/subprocess/' + mode + '-child.properties']
            properties(directory / (mode + '-command.properties'), {'argument-count': str(len(args)),
                **{'argument.' + str(i): value for i, value in enumerate(args)}, 'maximum-input': '3' if mode == 'pre-start-input' else '1048576' if mode == 'stalled-input' else '4',
                'maximum-output': '3' if mode == 'first-excess-output' else '4', 'maximum-duration': 'PT5S', 'mode': 'SUBPROCESS'})
            child = {'pid': '9876', 'mode': 'echo' if mode == 'first-excess-output' else mode, 'java-version': 'synthetic-build',
                     'staging': inside + '/subprocess/' + mode + '-root/.folio-pdf-provider-12345'}
            if started:
                properties(actual, {**child, **({'capability': 'conversion.test.t05', 'input-length': '4',
                    'input-sha256': hashlib.sha256(reports.PAYLOAD).hexdigest()} if mode != 'stalled-input' else {})})
            if mode == 'inherited-pipe': properties(Path(str(actual) + '.descendant.properties'), {**child, 'pid': '9877', 'mode': 'hold-pipe'})
            if mode in ('echo', 'first-excess-output', 'wrong-magic', 'wrong-version', 'negative-length', 'oversized-length', 'truncated-header', 'truncated-payload', 'trailing-output'):
                raw = struct.pack('>I', 0x12345678 if mode == 'wrong-magic' else 0x4f504452)
                if mode != 'truncated-header':
                    raw += struct.pack('>Iq', 2 if mode == 'wrong-version' else 1, -1 if mode == 'negative-length' else 5 if mode == 'oversized-length' else 4)
                    if mode not in ('negative-length', 'oversized-length'): raw += reports.PAYLOAD[:-1] if mode == 'truncated-payload' else reports.PAYLOAD
                    if mode == 'trailing-output': raw += bytes((99,))
                Path(str(actual) + '.response.bin').write_bytes(raw)
        seal(run)
        return run, inventory

    def test_frozen_inventory_rejects_omission_duplicates_and_undeclared_classes(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); self.authority(root)
            tests, _ = reports.mandatory_tests(root); self.assertEqual(59, len(tests))
            path = root / reports.TESTS; original = path.read_text()
            for value in ('\n'.join(original.splitlines()[:-1]) + '\n', original + original.splitlines()[0] + '\n', original.replace('#', '#ignored-', 1)):
                path.write_text(value)
                with self.assertRaises(ValueError): reports.mandatory_tests(root)

    def test_named_execution_rejects_missing_repeated_ignored_assumed_or_failed_cases(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); self.authority(root); tests, _ = reports.mandatory_tests(root)
            scope = root / 'scope'; scope.mkdir(); command = ['actual-synthetic-command']; plan = {'required-test-count': 59, 'contract-tests-command': command}
            (scope / 'contract-tests-command.json').write_text(json.dumps(command))
            transcript = 'JUnit version 4.13.2\n' + ''.join('T05 CASE ' + name + ' = PASS\n' for name in sorted(tests)) + 'T05 required execution: tests=59, failures=0, ignored=0, assumptions=0, duplicates=0\nOK (59 tests)\n'
            path = scope / 'contract-tests.txt'; path.write_text(transcript); reports.contract_tests(root, scope, plan)
            case = next(iter(tests))
            for text in (transcript.replace('T05 CASE ' + case + ' = PASS\n', ''), transcript + 'T05 CASE ' + case + ' = PASS\n',
                         transcript.replace(' = PASS\n', ' = FAIL\n', 1), transcript.replace('ignored=0', 'ignored=1'),
                         transcript.replace('assumptions=0', 'assumptions=1'), transcript.replace('duplicates=0', 'duplicates=1')):
                path.write_text(text)
                with self.assertRaises(ValueError): reports.contract_tests(root, scope, plan)

    def test_closed_public_cases_safe_diagnostics_receipts_and_sentinels_survive_resealing(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); run, _ = self.fixture(root); reports.closed_observations(root, RetainedFiles(run), 'IN_PROCESS')
            path = run / 'observations.properties'; original = path.read_text()
            for before, after in [('offline.metadata-count=0', 'offline.metadata-count=1'), ('registration-only.requests=0', 'registration-only.requests=1'),
                                  ('remote-zero-disclosure.disclosed-bytes=0', 'remote-zero-disclosure.disclosed-bytes=4'),
                                  ('remote-exact-transfer.requests=1', 'remote-exact-transfer.requests=0'), ('detached-values.result=pass\n', ''),
                                  ('null-result.code=MALFORMED_OUTPUT', 'null-result.code=EXECUTION_FAILED'),
                                  ('runtime-failure.diagnostic=The Provider engine did not complete successfully.', 'runtime-failure.diagnostic=private.pdf engine token'),
                                  ('native-partial-publication.receipts=first:COMMITTED:false,current:FAILED:true,later:NOT_ATTEMPTED:false',
                                   'native-partial-publication.receipts=first:COMMITTED:false,current:COMMITTED:false,later:NOT_ATTEMPTED:false'),
                                  ('case-count=51', 'case-count=50')]:
                self.assertIn(before, original); path.write_text(original.replace(before, after)); seal(run)
                with self.assertRaises(ValueError): reports.closed_observations(root, RetainedFiles(run), 'IN_PROCESS')
            path.write_text(original + 'offline.result=pass\n'); seal(run)
            with self.assertRaises(ValueError): reports.closed_observations(root, RetainedFiles(run), 'IN_PROCESS')
            path.write_text(original); (run / 'native-later.sentinel').write_bytes(b'changed'); seal(run)
            with self.assertRaises(ValueError): reports.closed_observations(root, RetainedFiles(run), 'IN_PROCESS')

    def test_endpoint_authorization_identity_and_payload_cannot_be_relabeled_or_resealed(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); run, _ = self.fixture(root); reports.endpoint(RetainedFiles(run), 'synthetic-build')
            path = run / 'endpoint.properties'; original = path.read_text()
            for before, after in [('requests=2', 'requests=0'), ('disclosed-bytes=8', 'disclosed-bytes=0'), ('address=127.0.0.1', 'address=example.invalid'),
                                  ('adapter-pid=1234', 'adapter-pid=5678'), ('adapter-java-build=synthetic-build', 'adapter-java-build=stale-build')]:
                path.write_text(original.replace(before, after)); seal(run)
                with self.assertRaises(ValueError): reports.endpoint(RetainedFiles(run), 'synthetic-build')
            path.write_text(original); (run / 'endpoint-request-0.bin').write_bytes(b'forged'); seal(run)
            with self.assertRaises(ValueError): reports.endpoint(RetainedFiles(run), 'synthetic-build')

    def test_actual_frames_cleanup_timeout_and_inherited_pipe_are_qualified_after_resealing(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); run, inventory = self.fixture(root); reports.subprocesses(RetainedFiles(run), inventory, 'synthetic-build')
            path = run / 'subprocess/wrong-version-lifecycle.properties'; original = path.read_text()
            for before, after in [('result=MALFORMED_OUTPUT', 'result=pass'), ('owned-staging-removed=true', 'owned-staging-removed=false'),
                                  ('new-open-pipes=0', 'new-open-pipes=1'), ('direct-child-state=terminated', 'direct-child-state=running'),
                                  ('elapsed-millis=150', 'elapsed-millis=6000')]:
                path.write_text(original.replace(before, after)); seal(run)
                with self.assertRaises(ValueError): reports.subprocesses(RetainedFiles(run), inventory, 'synthetic-build')
            path.write_text(original)
            raw = run / 'subprocess/wrong-version-child.properties.response.bin'; before = raw.read_bytes(); raw.write_bytes(struct.pack('>IIq', 0x4f504452, 1, 4) + reports.PAYLOAD); seal(run)
            with self.assertRaises(ValueError): reports.subprocesses(RetainedFiles(run), inventory, 'synthetic-build')
            raw.write_bytes(before); (run / 'subprocess/inherited-pipe-child.properties.descendant.properties').unlink(); seal(run)
            with self.assertRaises(ValueError): reports.subprocesses(RetainedFiles(run), inventory, 'synthetic-build')

    def test_missing_changed_pdf_and_boundary_findings_fail_retained_correspondence(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); run, _ = self.fixture(root); pdf = run / 'actual-workload.pdf'; pdf.write_bytes(b'Synthetic placeholder, never certifiable'); seal(run)
            original = pdf.read_bytes(); pdf.write_bytes(b'changed')
            with self.assertRaises(ValueError): RetainedFiles(run)
            pdf.write_bytes(original); (run / 'endpoint-request-0.bin').unlink()
            with self.assertRaises((OSError, ValueError)): RetainedFiles(run)

    def test_missing_profiles_chains_or_tools_cannot_become_passing_summary(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); run, _ = self.fixture(root)
            for execution in (None, 'IN_PROCESS', 'HARDENED_WORKER'):
                with self.assertRaises((OSError, ValueError)): reports.collect_reports(root, run, execution)
            path = run / 'result.properties'; original = path.read_text()
            for value in (original.replace('contract=pass\n', ''), original.replace('standards=pass', 'standards=indeterminate'), original.replace('profile=' + reports.PROFILE, 'profile=T03-document-workflow-transaction')):
                path.write_text(value)
                with self.assertRaises(ValueError): reports.collect_reports(root, run, 'IN_PROCESS')

    def test_plan_keeps_loopback_local_and_worker_profile_distinct_from_provider_modes(self):
        module = reports.foundation(); case = module.certification_case('providers')
        with patch.object(module, 'container_command', return_value=['podman', 'run', '--network=none', 'pinned-image']):
            for execution in ('IN_PROCESS', 'HARDENED_WORKER'):
                plan = module.execution_plan(ROOT, ROOT / 'capabilities/evidence/foundation/synthetic/providers' / ('jdk17-' + execution.lower()), 'pinned-image', Path('/helper/bin/helper'), 'actual-closure', case, execution)
                self.assertIn('--network=none', plan['recorder-command']); self.assertIn('-Dfolio.t05.executionProfile=' + execution, plan['java-options'])
                self.assertIn('-Dfolio.t03.executionProfile=' + execution, plan['java-options']); self.assertEqual(59, plan['required-test-count'])
                self.assertIn('/workspace/' + reports.TESTS, plan['contract-tests-command']); self.assertIn('loopback', plan['settings']['providers'])
            with self.assertRaises(ValueError): module.execution_plan(ROOT, ROOT / 'scope', 'image', Path('/helper/bin/helper'), 'cp', case, 'REMOTE')

    def test_collection_rejects_resealed_changed_workload_and_original_findings(self):
        module = reports.foundation()
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); original = root / 'original'; original.mkdir(); replay = root / 'replay'; replay.mkdir()
            fresh = replay / 'observations'; fresh.mkdir()
            for directory in (original, fresh):
                (directory / 'actual.pdf').write_bytes(b'%PDF-1.7\nProject-authored synthetic comparison control\n')
                (directory / 'findings.txt').write_text('Actual synthetic findings\n'); seal(directory)
            reports.compare_originals(root, RetainedFiles(original), replay, {}, module)
            (original / 'actual.pdf').write_bytes(b'%PDF-1.7\nRelabeled different actual workload\n'); seal(original)
            with self.assertRaises(ValueError): reports.compare_originals(root, RetainedFiles(original), replay, {}, module)
            (original / 'actual.pdf').write_bytes((fresh / 'actual.pdf').read_bytes())
            (original / 'findings.txt').write_text('Forged passing original findings\n'); seal(original)
            with self.assertRaises(ValueError): reports.compare_originals(root, RetainedFiles(original), replay, {}, module)

    def test_provider_index_requires_all_eight_declared_tuples(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); (root / 'capabilities').mkdir()
            (root / 'capabilities/foundation-environments.yaml').write_bytes((ROOT / 'capabilities/foundation-environments.yaml').read_bytes())
            import yaml
            profiles = yaml.safe_load((root / 'capabilities/foundation-environments.yaml').read_text())['profiles']
            full = {'certifications': [{'obligation': 'providers', 'environment': profile['id'], 'execution-profile': execution}
                    for profile in profiles for execution in ('IN_PROCESS', 'HARDENED_WORKER')]}
            reports.require_complete_index(root, full)
            for invalid in (full['certifications'][:-1], full['certifications'] + [full['certifications'][0]],
                            [dict(item, **{'execution-profile': 'REMOTE'}) for item in full['certifications']]):
                with self.assertRaises(ValueError): reports.require_complete_index(root, {'certifications': invalid})

    def test_bound_configuration_rejects_stale_candidate_command_environment_or_input_closure(self):
        module = reports.foundation(); execution = 'IN_PROCESS'
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); (root / 'capabilities').mkdir(); scope = root / 'runs/jdk17-in_process'; scope.mkdir(parents=True)
            (root / 'capabilities/foundation-environments.yaml').write_bytes((ROOT / 'capabilities/foundation-environments.yaml').read_bytes())
            import yaml
            profile = next(p for p in yaml.safe_load((root / 'capabilities/foundation-environments.yaml').read_text())['profiles'] if p['identity']['jdk-major'] == 17)
            env = scope.parent / 'jdk17-environment/environment.yaml'; env.parent.mkdir()
            env.write_text(json.dumps({'schema-version': 1, 'profile': profile['id'], 'identity': profile['identity']}))
            contract = {'environments': 'capabilities/foundation-environments.yaml'}; identities = {'Candidate': 'a' * 64}; helper = Path('/helper/bin/helper')
            plan = {'recorder-command': ['actual-command'], 'java-options': ['-Xmx1024m'], 'settings': {'actual': 'settings'}}
            expected = {'schema-version': 1, 'candidate-sha256': identities['Candidate'], 'environment-sha256': digest(env),
                        'acceptance-profile': reports.PROFILE, 'execution-profile': execution, 'command': plan['recorder-command'],
                        'java-options': plan['java-options'], 'locale': 'en_US / C.UTF-8', 'timezone': 'UTC', 'settings': plan['settings'],
                        'inputs': [{'path': 'actual-input', 'sha256': 'b' * 64}]}
            path = scope / 'execution.yaml'
            with patch.object(module, 'certification_classpath', return_value=[root / 'actual.jar']), \
                    patch.object(module, 'certification_inputs', return_value=expected['inputs']), patch.object(module, 'execution_plan', return_value=plan):
                path.write_text(json.dumps(expected)); reports.bound_execution(root, scope, contract, {}, identities, helper, module, execution)
                for field, bad in [('candidate-sha256', 'c' * 64), ('environment-sha256', 'c' * 64), ('command', ['relabeled-command']),
                                   ('execution-profile', 'HARDENED_WORKER'), ('inputs', []), ('java-options', []), ('locale', 'unobserved')]:
                    path.write_text(json.dumps(dict(expected, **{field: bad})))
                    with self.assertRaises(ValueError): reports.bound_execution(root, scope, contract, {}, identities, helper, module, execution)


if __name__ == '__main__':
    unittest.main()
