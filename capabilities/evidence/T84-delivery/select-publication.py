"""Select only complete final observations and explicitly curated delivery files."""
import argparse
import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parents[3]
delivery = Path(__file__).resolve().parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--state', required=True)
parser.add_argument('--paths-output', type=Path, required=True)
args = parser.parse_args()
state_path = delivery / args.state
state = json.loads(state_path.read_text())
assert state['result'] == 'pass' and not state['failed'] and not state['running']
manifest = json.loads((delivery / 'publication-source-paths.json').read_text())
index = json.loads((root / manifest['evidence-index']).read_text())
assert index['candidate'] == state['candidate']
assert len(index['certifications']) == 108
assert sum(len(item['records']) for item in index['certifications']) == 452
selected = set(manifest['source-paths']) | {manifest['evidence-index']}
final_base = (root / state['final-base']).resolve(strict=True)
final_base.relative_to(root / 'capabilities/evidence/foundation')
assert final_base.name.startswith('T84-final-r')
freeze = json.loads((delivery / 'source-freeze.json').read_text())
assert state['final-base'] == freeze['final-evidence-base']
directories = [root / value for value in state['directories'].values()]
adoptions = []
for directory in list(directories):
    adoption_path = directory / 'adoption.json'
    if not adoption_path.exists():
        continue
    adoption = json.loads(adoption_path.read_text())
    assert adoption['result'] == 'pass' and adoption['candidate-identities'] == freeze['final-identities']
    assert len(adoption['adopted-unchanged-tuples']) == 7 and len(adoption['fresh-tuples']) == 1
    extra = [root / name for name in adoption['retained-directories']]
    failed = root / adoption['failed-tuple-excluded']
    for retained in extra + [directory]:
        assert failed != retained and retained not in failed.parents and failed not in retained.parents, 'Failed tuple would be selected'
    directories.extend(extra)
    adoptions.append(adoption_path.relative_to(root).as_posix())
directories += [final_base / 'providers', final_base / 'combined-index']
assert len(directories) == len(set(directories))
resume_path = final_base / 'recovery/resume-run3.json'
resume = json.loads(resume_path.read_text())
assert resume['result'] == 'pass' and resume['candidate-identities'] == freeze['final-identities']
assert len(resume['preserved-completed-records']) == 10
for record in resume['preserved-completed-records']:
    assert hashlib.sha256((root / record['path']).read_bytes()).hexdigest() == record['sha256']
excluded = [root / name for name in resume['excluded-interrupted-replays']]
assert excluded and len(excluded) == len(set(excluded))
for path in excluded:
    assert path.parent == final_base / 'recovery/jdk17-hardened_worker'
    assert path.name.startswith('live-collection-') and path.is_dir()
for directory in directories:
    directory.resolve(strict=True).relative_to(final_base)
    assert directory.is_dir()
    for path in directory.rglob('*'):
        if path.is_file() and not any(attempt in path.parents for attempt in excluded):
            selected.add(path.relative_to(root).as_posix())

curated = ['README.md', 'receipt.md', 'execution-contract.md', 'source-freeze.json',
           'publication-source-paths.json', 'publication-plan.json', 'run-validation.py',
           'refresh-prerequisites.py', 'resume-refresh.py', 'archive-candidate.py',
           'audit-delivery.py', 'select-publication.py', 'analyse-readiness.py', 'check-source-pins.py',
           'freeze-candidate.py', 'complete-annotations.py', 'resume-recovery.py', 'continue-run3.py', args.state,
           'reviews/standards.md', 'reviews/spec.md', 'reviews/final-diff.md',
           'provider-plan-final-r2/plan.json', 'validation/readiness-analysis.json',
           'validation/runtime-byte-equivalence-final-r2.json']
projection = json.loads((delivery / 'report-link-retention.json').read_text())
assert projection['result'] == 'pass' and projection['candidate-identities'] == freeze['final-identities']
assert projection['report-count'] == len(projection['reports']) == 952
curated.extend(('retain-report-views.py', 'report-link-retention.json', 'report-views/README.md'))
for report in projection['reports']:
    path = root / report['view']['path']
    path.resolve(strict=True).relative_to(delivery / 'report-views')
    assert hashlib.sha256(path.read_bytes()).hexdigest() == report['view']['sha256']
    curated.append(path.relative_to(delivery).as_posix())
labels = ['full-verify-final-r3', 'jdk-matrix-final-r2', 'python-foundation-final',
          'python-providers-final', 'python-foundation-all-final', 'stage-final-r2',
          'archive-final-r2', 'inventory-generate-prefreeze-r2', 'inventory-validate-prefreeze-r2',
          'source-pin-check-final-r2-r2', 'source-freeze-final-r2', 'provider-plan-final-r2',
          'refresh-T84-final-r2-annotations-r3', 'inherited-refresh-run3',
          'recovery-resume-run3-r3', 'readiness-analysis-run3',
          'provider-certification-final-r2',
          'inventory-generate-final', 'inventory-validate-final', 'inventory-check-final',
          'readiness-final', 'report-view-retention-run3-r2', 'publication-closure-run3-projection']
for record in state['completed'].values():
    label = Path(record['log']).stem
    assert record['exit-code'] == 0
    labels.append(label)
for label in set(labels):
    curated.extend(('validation/' + label + '.txt', 'validation/' + label + '-result.json'))
# The proof from the first staged audit is retained after it has completed.
# A second read-only staged audit verifies the resulting full index before commit.
if (delivery / 'validation/publication-staged-audit.json').exists():
    curated.append('validation/publication-staged-audit.json')
for path in (delivery / 'retained-inputs').iterdir():
    if path.is_file():
        selected.add(path.relative_to(root).as_posix())
for name in curated:
    selected.add((delivery / name).relative_to(root).as_posix())
plan = {'baseline': manifest['baseline'], 'state': state_path.relative_to(root).as_posix(),
        'final-base': state['final-base'],
        'tuple-adoption-proofs': adoptions,
        'recovery-resume-proof': resume_path.relative_to(root).as_posix(),
        'excluded-interrupted-replays': resume['excluded-interrupted-replays'],
        'final-directories': [path.relative_to(root).as_posix() for path in directories],
        'curated-delivery-files': sorted(curated), 'source-files': sorted(manifest['source-paths']),
        'selected-file-count': len(selected),
        'selection': 'Complete final obligation directories, final Provider and combined index directories, owned sources and named curated delivery files only.'}
(delivery / 'publication-plan.json').write_text(json.dumps(plan, indent=2) + '\n')
for name in selected:
    path = root / name
    path.resolve(strict=True).relative_to(root)
    assert path.is_file() and not path.is_symlink(), 'Missing or symlinked selected file: ' + name
    assert not name.startswith(('target/', '.build-cache/')) and '/development/' not in name
    assert path.stat().st_size < 100_000_000, 'Selected file exceeds publication limit: ' + name
args.paths_output.write_bytes(b''.join(name.encode() + b'\0' for name in sorted(selected)))
print(json.dumps({'selected-files': len(selected), 'final-directories': len(directories),
                  'curated-delivery-files': len(curated), 'paths-output': str(args.paths_output)}), flush=True)
