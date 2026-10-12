"""Preserve the unindexed R21 text attempt before any diagnostic replay."""
import datetime
import gzip
import hashlib
import importlib.util
import json
from pathlib import Path
import sys

import yaml

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
scope = root / 'capabilities/evidence/foundation/T83-final-r21/text'
summary = delivery / 'failed-text-certification-r21.json'
manifest_path = delivery / 'failed-text-certification-r21.tsv.gz'
spec = importlib.util.spec_from_file_location('t83_preserve_text_r21', root / 'scripts/t03-foundation.py')
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)
contract = yaml.safe_load((root / 'capabilities/foundation-release.yaml').read_text())
frozen_path = delivery / 'source-freeze-final-r10.json'
frozen = json.loads(frozen_path.read_text())
assert driver.capture_build_inputs(root, contract) == {key: frozen[key] for key in ('inputs', 'contract-inputs')}
receipt = driver.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
ledger_path = delivery / 'refresh-final-r21/results.json'
steps = json.loads(ledger_path.read_text())
assert len(steps) == 6 and all(row['exit-code'] == 0 for row in steps[:5])
failed = steps[-1]
assert failed['obligation'] == 'text' and failed['exit-code'] == 1
index = driver.reference(root, root / 'capabilities/foundation-evidence.yaml')
assert index['sha256'] == failed['current-index-sha256'] == failed['prior-index']['sha256']
assert failed['certifications'] == 40 and failed['chain-records'] == 160
failure_path = scope / 'jdk21-hardened_worker/contract-tests.txt'
failure = failure_path.read_text()
assert 'predefinedUnicodeRangesRetainTheirDeclaredCarry' in failure
assert 'TestTimedOutException: test timed out after 10000 milliseconds' in failure
assert 'HardenedWorkerEngine.execute(HardenedWorkerEngine.java:210)' in failure
assert 'Tests run: 126,  Failures: 1' in failure


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


files = byte_count = 0
if sys.argv[1:] == ['--validate']:
    metadata = json.loads(summary.read_text())
    assert driver.reference(root, manifest_path) == metadata['manifest']
    for key in ('gate', 'ledger', 'prior-index', 'failure', 'source-freeze', 'refresh-driver', 'preflight'):
        item = metadata[key]
        assert driver.reference(root, root / item['path']) == item
    with gzip.open(manifest_path, 'rt', encoding='utf-8') as stream:
        assert next(stream) == 'sha256\tbytes\tpath\n'
        for line in stream:
            expected, length, name = line.rstrip('\n').split('\t')
            path = root / name
            assert path.is_file() and not path.is_symlink()
            assert path.stat().st_size == int(length) and digest(path) == expected
            files += 1
            byte_count += int(length)
    assert files == metadata['original-files'] and byte_count == metadata['original-bytes']
else:
    assert not sys.argv[1:] and not summary.exists() and not manifest_path.exists()
    with manifest_path.open('wb') as output:
        with gzip.GzipFile(filename='', mode='wb', fileobj=output, mtime=0) as compressed:
            compressed.write(b'sha256\tbytes\tpath\n')
            for path in sorted(scope.rglob('*')):
                assert not path.is_symlink()
                if not path.is_file():
                    continue
                length = path.stat().st_size
                compressed.write((digest(path) + '\t' + str(length) + '\t' + path.relative_to(root).as_posix() + '\n').encode())
                files += 1
                byte_count += length
    metadata = {
        'recorded-at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'candidate': receipt['candidate'], 'exit-code': 1, 'certifications-exported': 0,
        'index-unchanged': True, 'observed-index': index, 'prior-index': failed['prior-index'],
        'gate': failed['gate-result'], 'ledger': driver.reference(root, ledger_path),
        'failure': driver.reference(root, failure_path), 'source-freeze': driver.reference(root, frozen_path),
        'refresh-driver': driver.reference(root, delivery / 'refresh-relaunch-r21.py'),
        'preflight': driver.reference(root, delivery / 'refresh-final-r21/preflight.json'),
        'manifest': driver.reference(root, manifest_path), 'original-files': files, 'original-bytes': byte_count,
        'failure-kind': 'JUnit 10000ms timeout during real Worker INITIALIZE/READY exchange',
        'monotonic-duration-seconds': failed['monotonic-duration-seconds'],
        'wall-duration-seconds': failed['wall-duration-seconds'],
        'disposition': 'Keep all originals and failed ledger unchanged; diagnose with unchanged test timeout and frozen candidate before a fresh entire text certification.'}
    driver.write_json(summary, metadata)
assert driver.reference(root, root / 'capabilities/foundation-evidence.yaml') == index
assert driver.capture_build_inputs(root, contract) == {key: frozen[key] for key in ('inputs', 'contract-inputs')}
driver.require_staged_build(root, contract, root / 'target/foundation-0.1.0/build-inputs.json')
print('Preserved R21 text originals: ' + str(files) + ' files / ' + str(byte_count) + ' bytes; zero text certifications; frozen source/stage/index unchanged.')
