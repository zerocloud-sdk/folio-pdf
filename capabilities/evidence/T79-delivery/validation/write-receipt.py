"""Render a completed delivery only after the final evidence audit succeeds."""
import hashlib
import json
from pathlib import Path

ROOT = Path('/workspace/folio-pdf')
OUT = ROOT / 'capabilities/evidence/T79-delivery'
audit = json.loads((OUT / 'validation/final-evidence-audit.json').read_text())
assert audit['result'] == 'pass'
source = Path('/workspace/contracts/issue-79-contract.md')
contract = source.read_text()
criteria = contract.split('## Completion criteria\n', 1)[1].split('\n## Constraints', 1)[0]
criteria = [value.strip() for value in criteria.split('- [ ] ')[1:]]
assert len(criteria) == 21
links = {
    'profile': 'docs/research/T79-clear-metadata-profile-audit.md',
    'contract': 'docs/pdf-version-password-security.md',
    'certification': 'docs/t79-certification.md',
    'index': 'capabilities/foundation-evidence.yaml',
    'audit': 'capabilities/evidence/T79-delivery/validation/final-evidence-audit.json',
    'freeze': 'capabilities/evidence/T79-delivery/source-freeze.json',
    'commands': 'capabilities/evidence/T79-delivery/validation/commands.jsonl',
    'collector': 'capabilities/evidence/T79-delivery/validation/collector-all-final.txt',
    'live': 'capabilities/evidence/T79-delivery/validation/live-qualification.txt',
    'native': 'capabilities/evidence/T79-delivery/validation/native-baseline-resumed.txt',
    'regression': 'capabilities/evidence/T79-delivery/validation/review-regressions-1.txt',
    'worker': 'capabilities/evidence/T79-delivery/validation/review-worker-regressions-1.txt',
    'standards': 'capabilities/evidence/T79-delivery/reviews/standards-resume.md',
    'spec': 'capabilities/evidence/T79-delivery/reviews/spec-resume.md',
    'worktree': 'capabilities/evidence/T79-delivery/validation/final-worktree-status.txt',
    'provenance': 'PROVENANCE.md',
    'inventories': 'capabilities/capability-matrix.yaml',
    'facade': 'capabilities/facade-surface.yaml',
}
mapping = [
    ['profile', 'certification', 'freeze'], ['contract', 'audit', 'index'], ['profile', 'certification', 'audit'],
    ['profile', 'audit', 'collector'], ['contract', 'regression', 'worker', 'audit'],
    ['contract', 'regression', 'worker', 'audit'], ['regression', 'worker', 'audit'],
    ['facade', 'regression', 'audit'], ['certification', 'audit', 'index'],
    ['collector', 'live', 'audit', 'commands'], ['audit', 'index'], ['inventories', 'audit'],
    ['freeze', 'facade', 'inventories', 'provenance', 'commands'], ['audit', 'index'],
    ['native', 'regression', 'worker'], ['commands', 'audit', 'collector', 'live'],
    ['standards', 'spec', 'freeze', 'worktree'], ['standards', 'spec', 'regression', 'worker', 'audit'],
    ['worktree', 'audit'], ['worktree', 'freeze'], ['audit', 'freeze', 'commands'],
]


def reference(name):
    path = ROOT / links.get(name, name)
    return {'path': path.relative_to(ROOT).as_posix(), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}


receipt = {'schema-version': 1, 'issue': 79, 'result': 'complete-local-delivery',
    'sole-contract': str(source), 'sole-contract-sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
    'comparison-baseline': audit['comparison-baseline'], 'execution-starting-head': audit['comparison-baseline'],
    'candidate-sha256': audit['candidate-sha256'], 'contract-sha256': audit['contract-sha256'],
    'capability': 'document.version-password-security.clear-metadata', 'foundation-member': 'password-clear-metadata',
    'acceptance-profile': 'T32-password-clear-metadata', 'branch': 'main', 'optional-local-commit': None,
    'workspace': 'Resumed the preserved dirty issue-79 worktree at the recorded baseline; no git reset, git clean, or git stash, and no unrelated changes discarded.',
    'external-effects': 'No push, PR, tracker mutation, message to others, or release publication.',
    'certification': reference('audit'), 'remaining-foundation-blockers': audit['readiness']['remaining-obligations'],
    'verification-summary': audit['verification-summary'],
    'selected-certification-runs': audit['selected-certification-runs'],
    'resumed-execution': {
        'start': reference('capabilities/evidence/T79-delivery/validation/resume-start.json'),
        'environment-restoration': reference('capabilities/evidence/T79-delivery/validation/resume-environment-restoration.json'),
        'text-timeout-replays': reference('capabilities/evidence/T79-delivery/validation/diagnosis-text-timeouts/disposition.json'),
        'resolved-findings': [
            {'finding': 'Generated readiness had not been refreshed after the prior JarContractIT change.',
             'resolution': 'Regenerated from the existing authorities; retained the former view and failure.',
             'evidence': reference('capabilities/evidence/T79-delivery/validation/readiness-refresh-analysis.json')},
            {'finding': 'InventoryCommandTest still expected 214 Facade surfaces after adding the metadata selector.',
             'resolution': 'Updated exact counts to 215 and asserted the compatible child has 62 Stable mappings, zero Preview additions, and no exclusion.',
             'evidence': reference('capabilities/evidence/T79-delivery/validation/inventory-resumed-regression-r2.txt')},
        ],
    },
    'uncertified-nonblocking-platforms': ['Windows x86-64', 'macOS x86-64', 'macOS arm64'],
    'criteria': [{'criterion': index + 1, 'text': text, 'result': 'pass',
        'evidence': [reference(name) for name in mapping[index]]} for index, text in enumerate(criteria)],
    'limits': ['Closed source-traceable profile; PDF 1.7 R6/ADBE Level 8 is qualified interoperability, PDF 2.0 R6 is normative.',
        'Supplemental all-content metadata-StdCF original retains its known pdfcpu limitation; all required clear inputs and emitted products pass all four chains.',
        'Password-attachments/#80, aggregate security and parent #33 remain incomplete.'],
}
(OUT / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
environment_rows = '\n'.join('| JDK {jdk-major} | {jdk-vendor} / `{jdk-build}` | Pass | Pass |'.format(**value['identity'])
    for _, value in sorted(audit['environments'].items(), key=lambda item: item[1]['identity']['jdk-major']))
criterion_rows = '\n'.join('| ' + str(index + 1) + ' | Pass | ' + ', '.join('[' + name + '](../../../' + links[name] + ')'
    for name in mapping[index]) + ' |' for index in range(21))
readme = f'''# Issue #79 delivery receipt

Completed locally under the sole execution contract. The clear-metadata child is compatible and independently certified; aggregate security and #80 remain incomplete.

- Comparison and execution baseline: `{audit['comparison-baseline']}` on `main`.
- Candidate: `{audit['candidate-sha256']}`.
- Foundation contract: `{audit['contract-sha256']}`.
- Optional local commit: omitted. The worktree contains this ticket's changes.
- [Machine receipt](receipt.json), [source freeze](source-freeze.json), [final identity/evidence audit](validation/final-evidence-audit.json), [command ledger](validation/commands.jsonl).

This execution resumed the existing dirty worktree. The interrupted `full-verify-final.txt` remains unchanged and makes no passing claim. The successful replacement is [full-verify-resumed-r3.txt](validation/full-verify-resumed-r3.txt). The [resume snapshot](validation/resume-start.json) binds the starting files and unchanged contract. Java and the container runtime were restored; missing image layers were downloaded by exact digest into a separate task-local store. [Environment probes](validation/resume-environments.json) and subsequent certification records verify actual identities. The first resumed full run found stale generated readiness; the prior view and failing log remain retained, followed by regeneration and a passing focused inventory regression.

Native `ALL_EXCEPT_METADATA` and Facade `DO_NOT_ENCRYPT_METADATA=8` cover the audited R4 RC4-128/AES-128 and R5/R6 AES-256 inputs, R4 legacy and R6 outputs, PDF-version rules, exact credentials, independent owner authority, permissions, protected rewrite and incremental publication. Catalog XMP bytes remain clear; Info and stream-dictionary strings, component metadata, content and embedded data remain protected. All-content AES-256 stays the default. See the [profile audit](../../../docs/research/T79-clear-metadata-profile-audit.md) and [public contract](../../../docs/pdf-version-password-security.md).

## Observed certification

| Ubuntu 24.04 Linux x86-64 | Actual vendor/build | Native IN_PROCESS and HARDENED_WORKER | Facade IN_PROCESS |
| --- | --- | --- | --- |
{environment_rows}

Every tuple passed 23 public/artifact tests and four separate syntax, standards, semantic and visual chains. Each retained 78 randomized products, 37 original encrypted inputs, 117 scope controls and 95 baseline security controls, plus core-document and visual controls. The eight new tuples retain 624 product artifacts. Exact images, Java executables, native/tool hashes and settings are in the [fresh certification records](../foundation/T79-final/password-clear-metadata/).

Transactions, values, pages, metadata, annotations, text, images, incremental and password-baseline were refreshed first on the same candidate: 80 current certifications and 320 passing chain records in total. The baseline retains 544 product artifacts. Windows and macOS remain explicitly uncertified and nonblocking.

## Validation and review

The smallest baseline command passed 31 tests; [Native/Facade regressions](validation/review-regressions-1.txt) and [Worker regressions](validation/review-worker-regressions-1.txt) passed all applicable baseline and new scope tests. The exact smallest command was `./mvnw -B -ntp -pl pdf-document -am -Dtest=PdfVersionPasswordSecurityWorkflowTest -Dsurefire.failIfNoSpecifiedTests=false test`.

`./mvnw -B -ntp verify`, `./scripts/verify-jdk-matrix.sh`, the real-tool independent profile, all 84 Foundation collector tests, live replay with isolated resealed-finding/Native-mode/Facade-mode rejection, unsigned staging, all ten certification routes, and inventory validate/generate/check passed. [Exact commands, environments, durations, exit codes and log hashes](validation/commands.jsonl) and the [final audit](validation/final-evidence-audit.json) retain the evidence. Ordinary Maven's opt-in skips do not substitute for the passing independent profile or mandatory #79 chains.

The [verification summary](validation/verification-summary.json) records actual test counts and omissions. Ordinary verification leaves the three unrelated `HardenedWorkerScaleProfileTest` cases disabled unless `folio.pdf.t22.scale` is selected: concurrency stress, 5,000 pages and a 1-GiB input. It also omits the unrelated `T30BarcodeEvidenceCommandTest.pinnedRastersDecodeAndMatchEveryDeclaredVariant` unless `t30.raster` is selected. Required password-security tests and chains passed; the independent T78/T79 profile ran all nine tests without skips.

[Standards](reviews/standards-resume.md) and [Spec](reviews/spec-resume.md) reviews have no unresolved findings. Their source-review reports explicitly identified later validation gates; the final audit now proves those gates. Regression evidence covers preflight, explicit Crypt normalization, catalog/component separation, Identity defaults and unexpected checker diagnostics. Historical development failures remain retained as failures and are not final certification evidence.

The first text-certification attempt stopped on three JDK 8 worker timeouts. The unchanged focused replay, complete 126-test suite, and 20 repeated short-method runs all passed; source, staged artifacts, harness and environment identities matched. The exact cause was not established. No source or time limit changed. The failed attempt remains retained, and the complete fresh [text-r2 route](../foundation/T79-final/text-r2/) subsequently passed every required tuple. The [diagnosis](validation/diagnosis-text-timeouts/README.md) and [selected certification runs](validation/certification-selection.json) distinguish the attempts.

## Completion criteria

All 21 criteria pass. Their complete authoritative text and evidence hashes are in [receipt.json](receipt.json).

| Contract criterion | Result | Evidence |
| --- | --- | --- |
{criterion_rows}

## Remaining boundaries

Global Foundation readiness is **NOT READY** for unrelated obligations; the selected member, its dependencies and all previously certified members are satisfied. Remaining obligation IDs: {', '.join('`' + value + '`' for value in audit['readiness']['remaining-obligations'])}. The [readiness report](validation/inventory-final-readiness.txt) retains every blocker.

The [known supplemental checker limitation](qualification-limitations/all-content-metadata-stdcf/classification.json) concerns a baseline all-content original outside the owned clear input matrix. Its exact XMP rewrite regression remains implemented, and both APIs' emitted rewrites pass all four chains. No required #79 case is waived. The [provenance](../../../PROVENANCE.md) records original sources, fixtures, licenses and tool limits.

The final worktree contains only ticket changes. No local commit, push, PR, tracker mutation, external message or release publication occurred. The orchestrator retains review and publication authority.
'''
(OUT / 'README.md').write_text(readme)
print('Wrote the completed 21-criterion receipt and delivery README.')
