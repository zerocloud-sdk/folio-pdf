"""Prove real Git staged-byte binding rejects an equal-length artifact alteration."""
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
baseline = '42fe5c797711070b319b9419e14fcb7adfa98735'
assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip() == baseline
assert not subprocess.check_output(['git', 'diff', '--name-only', '--cached'], cwd=root)
result_path = delivery / 'staged-content-proof-r23.json'
assert not result_path.exists()
original = b'original\n'
altered = b'tampered\n'
assert len(original) == len(altered)
with tempfile.TemporaryDirectory(prefix='t83-staged-content-', dir='/tmp') as temporary:
    repository = Path(temporary)
    subprocess.run(['git', 'init', '-q', str(repository)], check=True)
    file = repository / 'retained-artifact.txt'
    file.write_bytes(original)
    expected = (hashlib.sha256(original).hexdigest(), len(original))
    observations = []
    for payload, accepted in ((original, True), (altered, False)):
        file.write_bytes(payload)
        subprocess.run(['git', 'add', '--', file.name], cwd=repository, check=True)
        tokens = subprocess.check_output(['git', 'diff', '--cached', '--raw', '--no-abbrev',
                                         '--no-renames', '-z'], cwd=repository).split(b'\0')
        assert len(tokens) == 3 and tokens[-1] == b'' and tokens[1] == file.name.encode()
        raw = tokens[0].split()
        assert len(raw) == 5 and raw[1] == b'100644' and raw[4] == b'A'
        output = subprocess.check_output(['git', 'cat-file', '--batch'], cwd=repository, input=raw[3] + b'\n')
        header, body = output.split(b'\n', 1)
        fields = header.split()
        assert fields == [raw[3], b'blob', str(len(payload)).encode()]
        assert body == payload + b'\n'
        observed = (hashlib.sha256(body[:-1]).hexdigest(), len(body) - 1)
        assert (observed == expected) is accepted
        observations.append({'same-path': True, 'same-length': True,
                             'staged-sha256': observed[0], 'accepted': accepted})
    assert subprocess.run(['git', 'rev-parse', '--verify', 'HEAD'], cwd=repository,
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode != 0
assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip() == baseline
assert not subprocess.check_output(['git', 'diff', '--name-only', '--cached'], cwd=root)
value = {'result': 'pass', 'fixture-bytes': len(original), 'expected-sha256': expected[0],
         'observations': observations, 'isolated-commits-created': 0,
         'target-head-and-index-unchanged': True,
         'predicate': 'Compare SHA-256 and length of actual Git staged blobs to immutable ownership rows; a same-length evidence alteration must fail.'}
result_path.write_text(json.dumps(value, indent=2) + '\n')
print('Actual Git staged-byte proof PASS: original accepted; same-length alteration rejected; zero commits; target HEAD/index unchanged.')
