"""Replay R21's exact method and ordered prefix without changing any test bound."""
import datetime
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import time

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
scope = delivery / 'text-timeout-diagnostics-r22'
source = delivery / 'diagnostics/T83TextReplayR22.java'
failure_scope = root / 'capabilities/evidence/foundation/T83-final-r21/text/jdk21-hardened_worker'


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def run(command, log, timeout):
    started = time.monotonic()
    with log.open('wb') as output:
        process = subprocess.run(command, stdout=output, stderr=subprocess.STDOUT, cwd=root, timeout=timeout)
    return process.returncode, time.monotonic() - started


if sys.argv[1:] == ['--inside']:
    preflight = json.loads((scope / 'preflight.json').read_text())
    original = preflight['original-command']
    java = original[original.index('java'):]
    split = java.index('org.junit.runner.JUnitCore')
    options = java[:split]
    classpath = options[options.index('-cp') + 1]
    classes = scope / 'classes'
    classes.mkdir()
    compile_command = ['javac', '-source', '8', '-target', '8', '-cp', classpath,
                       '-d', str(classes), str(source)]
    (scope / 'compile-command.json').write_text(json.dumps(compile_command, indent=2) + '\n')
    code, elapsed = run(compile_command, scope / 'compile.txt', 120)
    assert code == 0
    (scope / 'compile-result.json').write_text(json.dumps({'exit-code': code,
        'monotonic-duration-seconds': elapsed, 'class-sha256': sha(classes / 'T83TextReplayR22.class')}, indent=2) + '\n')
    options[options.index('-cp') + 1] = str(classes) + ':' + classpath
    results = []
    for mode, count, cases in (('method', 20, 1), ('prefix', 10, 18)):
        for number in range(1, count + 1):
            attempt = scope / (mode + '-' + str(number))
            attempt.mkdir()
            command = options + ['T83TextReplayR22', mode, preflight['test-class'], preflight['test-method']]
            (attempt / 'command.json').write_text(json.dumps(command, indent=2) + '\n')
            code, elapsed = run(command, attempt / 'tests.txt', 900)
            text = (attempt / 'tests.txt').read_text()
            expected = ('T83 DIAGNOSTIC mode=' + mode + ' tests=' + str(cases)
                        + ' failures=0 ignored=0 assumptions=0 target-timeout-millis=10000')
            row = {'mode': mode, 'attempt': number, 'exit-code': code,
                   'monotonic-duration-seconds': elapsed, 'tests': cases,
                   'required-execution': expected in text, 'test-timeout-millis': 10000,
                   'transcript-sha256': sha(attempt / 'tests.txt')}
            results.append(row)
            (scope / 'results.json').write_text(json.dumps(results, indent=2) + '\n')
            print(json.dumps(row), flush=True)
            assert code == 0 and row['required-execution']
    print('Diagnostics completed; no certification exported and no timeout or source input changed.', flush=True)
else:
    assert not sys.argv[1:] and not scope.exists()
    spec = importlib.util.spec_from_file_location('t83_timeout_r22', root / 'scripts/t03-foundation.py')
    driver = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(driver)
    import yaml
    contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
    frozen = json.loads((delivery / 'source-freeze-final-r10.json').read_text())
    assert driver.capture_build_inputs(root, contract) == {key: frozen[key] for key in ('inputs', 'contract-inputs')}
    receipt = driver.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
    preservation = json.loads((delivery / 'failed-text-certification-r21.json').read_text())
    assert preservation['certifications-exported'] == 0 and preservation['index-unchanged']
    assert preservation['prior-index']['sha256'] == driver.sha256(root / 'capabilities/foundation-evidence.yaml')
    failure = (failure_scope / 'contract-tests.txt').read_text()
    assert failure.splitlines()[1].split('E', 1)[0].count('.') == 18
    original = json.loads((failure_scope / 'contract-tests-command.json').read_text())
    command = original[:original.index('java')]
    mount = str(failure_scope) + ':' + '/workspace/' + failure_scope.relative_to(root).as_posix() + ':rw'
    index = command.index(mount)
    assert command[index - 1] == '--volume'
    del command[index - 1:index + 1]
    command[-1:-1] = ['--volume', str(scope) + ':/workspace/' + scope.relative_to(root).as_posix() + ':rw']
    command += ['/usr/bin/python3.12', '-I', '/workspace/' + Path(__file__).resolve().relative_to(root).as_posix(), '--inside']
    scope.mkdir()
    refs = [driver.reference(root, path) for path in (source, Path(__file__).resolve(),
        delivery / 'failed-text-certification-r21.json', delivery / 'failed-text-certification-r21.tsv.gz',
        failure_scope / 'contract-tests-command.json', failure_scope / 'contract-tests.txt')]
    index_ref = driver.reference(root, root / 'capabilities/foundation-evidence.yaml')
    driver.write_json(scope / 'preflight.json', {
        'recorded-at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'candidate': receipt['candidate'], 'index': index_ref, 'preserved-inputs': refs,
        'original-command': original, 'container-command': command,
        'test-class': 'net.zerocloud.pdf.consumer.TextStructureExtractionWorkflowTest',
        'test-method': 'predefinedUnicodeRangesRetainTheirDeclaredCarry',
        'target-timeout-millis': 10000, 'prefix-cases': 18,
        'diagnostics-exported-certifications': 0,
        'purpose': 'Twenty fresh exact-method parents and ten original-prefix parents; original annotation timeouts, image/options/cwd and staged closure unchanged.'})
    completed = subprocess.run(command, cwd=root, timeout=1800)
    assert all(item == driver.reference(root, root / item['path']) for item in refs)
    assert driver.reference(root, root / 'capabilities/foundation-evidence.yaml') == index_ref
    assert driver.capture_build_inputs(root, contract) == {key: frozen[key] for key in ('inputs', 'contract-inputs')}
    driver.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
    raise SystemExit(completed.returncode)
