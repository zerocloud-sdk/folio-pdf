"""Replay the exact failed T13 control under its unchanged CPU qualification."""
import datetime
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import resource
import subprocess
import sys
import time


root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
scope = delivery / 't13-cpu-diagnostics-r21'
control = root / ('capabilities/evidence/foundation/T83-final-r20/text/jdk11-in_process/'
                  'observations/native-uncertain-geometry/programs/control-content.boolean-mcid.pdf')
observer = root / 'scripts/t13-program-standards.py'


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


if sys.argv[1:] == ['--inside']:
    expected = sha(control)
    assert not (scope / 'results.json').exists()
    results = []
    for number in range(1, 21):
        attempt = scope / ('attempt-' + str(number))
        attempt.mkdir()
        command = ['/usr/bin/python3.12', '-I', str(observer), str(root), str(control),
                   'content', str(attempt / 'observer')]
        (attempt / 'command.json').write_text(json.dumps(command, indent=2) + '\n')
        before = resource.getrusage(resource.RUSAGE_CHILDREN)
        started = time.monotonic()
        with (attempt / 'stdout.txt').open('wb') as output, (attempt / 'stderr.txt').open('wb') as error:
            process = subprocess.run(command, stdout=output, stderr=error, timeout=120, cwd=root)
        elapsed = time.monotonic() - started
        after = resource.getrusage(resource.RUSAGE_CHILDREN)
        record = attempt / 'observer/result.properties'
        values = dict(line.split('=', 1) for line in record.read_text().splitlines() if '=' in line) if record.exists() else {}
        row = {'attempt': number, 'exit-code': process.returncode,
               'monotonic-duration-seconds': elapsed,
               'child-user-cpu-seconds': after.ru_utime - before.ru_utime,
               'child-system-cpu-seconds': after.ru_stime - before.ru_stime,
               'result': values.get('standards'), 'rule': values.get('rule'),
               'finding': values.get('finding'), 'stderr-bytes': (attempt / 'stderr.txt').stat().st_size}
        results.append(row)
        (scope / 'results.json').write_text(json.dumps(results, indent=2) + '\n')
        print(json.dumps(row), flush=True)
        assert sha(control) == expected
    print('Diagnostic replay only; no certification exported and no CPU limit changed.', flush=True)
else:
    assert not scope.exists()
    scope.mkdir()
    spec = importlib.util.spec_from_file_location('t83_cpu_r21', root / 'scripts/t03-foundation.py')
    driver = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(driver)
    import yaml
    contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
    frozen = json.loads((delivery / 'source-freeze-final-r10.json').read_text())
    assert driver.capture_build_inputs(root, contract) == {key: frozen[key] for key in ('inputs', 'contract-inputs')}
    receipt = driver.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
    image = next(row['identity']['image'] for row in yaml.safe_load((root / 'capabilities/foundation-environments.yaml').read_text())['profiles']
                 if row['identity']['jdk-major'] == 11)
    helper = Path(os.environ['FOLIO_HARFBUZZ_HELPER'])
    command = driver.container_command(root, image, helper)
    relative = scope.relative_to(root).as_posix()
    command[-1:-1] = ['--volume', str(scope) + ':/workspace/' + relative + ':rw']
    command += ['/usr/bin/python3.12', '-I', '/workspace/' + Path(__file__).resolve().relative_to(root).as_posix(), '--inside']
    before = {'recorded-at': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'image': image,
              'control': driver.reference(root, control), 'observer': driver.reference(root, observer),
              'candidate': receipt['candidate'], 'index': driver.reference(root, root / 'capabilities/foundation-evidence.yaml'),
              'command': command, 'cpu-qualification': {'soft-seconds': 5, 'hard-seconds': 10},
              'purpose': 'Exact-control diagnostics only; no qualified result or certification is inferred.'}
    driver.write_json(scope / 'preflight.json', before)
    completed = subprocess.run(command, cwd=root, timeout=1800)
    assert driver.capture_build_inputs(root, contract) == {key: frozen[key] for key in ('inputs', 'contract-inputs')}
    driver.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
    assert driver.reference(root, root / 'capabilities/foundation-evidence.yaml') == before['index']
    assert driver.reference(root, control) == before['control']
    raise SystemExit(completed.returncode)
