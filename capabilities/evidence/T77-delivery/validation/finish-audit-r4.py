import json
import os
import re
import shutil
import subprocess
from pathlib import Path

root = Path('/workspace/folio-pdf')
base = root / '.build-cache/T77-preflight'
out = root / 'capabilities/evidence/T77-delivery/validation'
baseline = 'cd978a5cb311211c71050024d067f8d1d2dfacec'
selected = ['incremental', 'transactions', 'values', 'pages', 'metadata', 'annotations', 'text', 'images']
assert 'All selected validations and candidate certifications completed' in (base / 'final-candidate-driver-r4.log').read_text()
env = os.environ.copy()
env.update(MAVEN_USER_HOME=str(root / '.build-cache/maven'),
           MAVEN_ARGS='-Dmaven.repo.local=' + str(root / '.build-cache/maven/repository'),
           FOLIO_HARFBUZZ_HELPER=str(base / 'harfbuzz/bin/folio-harfbuzz'),
           FOLIO_FOUNDATION_PYTHON_ROOT=str(base / 'foundation-python'))
results = []

def run(command, name, passing=True):
    print('Starting ' + ' '.join(command), flush=True)
    with (out / name).open('wb') as log:
        completed = subprocess.run(command, cwd=root, env=env, stdout=log, stderr=subprocess.STDOUT)
    results.append({'command': command, 'exit': completed.returncode, 'log': name})
    print('Exit ' + str(completed.returncode) + ': ' + name, flush=True)
    if passing:
        assert completed.returncode == 0, name
    return completed.returncode, (out / name).read_text()

python = str(base / 'python/bin/python')
run([python, str(base / 'verify-delivery-r4.py')], 'final-certification-audit.txt')
run(['./scripts/inventory', 'validate'], 'inventory-validate-final.txt')
run(['./scripts/inventory', 'check'], 'inventory-check-final.txt')
code, readiness = run(['./scripts/inventory', 'readiness'], 'readiness-final.txt', passing=False)
assert code != 0 and 'Foundation 0.1.0: NOT READY' in readiness
assert 'BLOCKED release:' not in readiness, 'Unexpected global identity blocker'
for name in selected:
    assert re.search(r'^SATISFIED ' + re.escape(name) + r' \(#', readiness, re.M), name
    assert not re.search(r'^BLOCKED ' + re.escape(name) + r' ', readiness, re.M), name
    generated = (root / 'docs/generated/foundation-readiness.md').read_text()
    assert any(line.startswith('| [`' + name + '`]') and line.endswith('| satisfied |') for line in generated.splitlines()), name
summary = json.loads((out / 'final-certification-summary.json').read_text())
identity = summary['incremental']['identities']
assert 'Candidate identity: ' + identity['Candidate'] in readiness
assert 'Contract identity: ' + identity['Contract'] in readiness
diagnostics = [line for line in readiness.splitlines() if line.startswith('BLOCKED ')]
unrelated = sorted({re.match(r'BLOCKED ([^ ]+)', line).group(1) for line in diagnostics})
assert not set(unrelated) & set(selected)
(out / 'remaining-readiness-diagnostics.json').write_text(json.dumps({'obligations': unrelated, 'diagnostics': diagnostics}, indent=2) + '\n')

run(['git', 'diff', '--check'], 'diff-check-final.txt')
head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
branch = subprocess.check_output(['git', 'branch', '--show-current'], cwd=root, text=True).strip()
assert head == baseline and branch == 'main'
assert not subprocess.check_output(['git', 'diff', '--cached', '--name-only'], cwd=root, text=True).strip()
historical_changes = subprocess.check_output(['git', 'diff', '--name-only', baseline, '--', 'capabilities/evidence'], cwd=root, text=True)
assert not historical_changes.strip(), 'Tracked historical evidence changed'
status = subprocess.check_output(['git', 'status', '--porcelain=v1', '--untracked-files=normal'], cwd=root, text=True)
known = set(json.loads((base / 'ticket-paths-r4.json').read_text()))
assert all(line[3:] in known or line[3:].startswith('capabilities/evidence/T77-') for line in status.splitlines()), 'Unrelated worktree change'
(out / 'worktree-final.txt').write_text(status)
(out / 'worktree-audit-final.json').write_text(json.dumps({'result': 'pass', 'baseline': baseline,
    'head': head, 'branch': branch, 'new-commits': 0, 'staged-changes': False,
    'tracked-historical-evidence-changes': [], 'unrelated-worktree-changes': [],
    'scope': 'Reviewed fixed-baseline implementation and new ticket files; final generated/evidence changes remain within issue #77.'}, indent=2) + '\n')

for name in selected:
    shutil.copyfile(base / ('final-certify-' + name + '-r4.log'), out / ('certify-' + name + '-final.txt'))
for name in ['final-candidate-driver-r4.log', 'final-stage-r4.log', 'final-inventory-generate-after-cert-r4.log', 'final-inventory-check-after-cert-r4.log']:
    shutil.copyfile(base / name, out / name)
(out / 'final-command-results.json').write_text(json.dumps(results, indent=2) + '\n')
print('PASS: all selected obligations satisfied; remaining diagnostics are outside #77; baseline/worktree/history preserved.', flush=True)
