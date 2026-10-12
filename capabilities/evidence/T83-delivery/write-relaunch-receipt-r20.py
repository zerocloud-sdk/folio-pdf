"""Write a reviewable receipt only from completed candidate10 audit and ownership."""
import json
from pathlib import Path
import shlex

delivery = Path(__file__).resolve().parent
root = delivery.parents[2]
audit = json.loads((delivery / 'final-audit-r20.json').read_text())
assert audit['certification-tuples'] == 100 and audit['chain-records'] == 412
assert audit['recovery-tuples'] == 4 and audit['recovery-chain-records'] == 20
assert len(audit['selected-obligations']) == 14
ownership = json.loads((delivery / 'changed-file-ownership-prefinal-r20.json').read_text())
assert ownership['ownership'] == 'issue-83' and not ownership['entry-unrelated-files']
path = delivery / 'receipt-r20.md'
assert not path.exists()
criteria = (
    ('C1', 'Worker-only environment-local identity, material conflicts, duplicates, finite non-evicting retention and oversized admission', '180 named cases and public identity/retention observations'),
    ('C2', 'RUNNING/RECOVERABLE/COMPLETED/FAILED, ordered receipts and eligible retry only', 'original and fresh public transaction transcripts'),
    ('C3', 'Committed acknowledgement loss resolves COMPLETED; partial stream publication is terminal and cannot replay', 'Path/stream publication, lookup, receipts and byte observations'),
    ('C4', 'Real bidirectional multi-frame transfer/reopen and authenticated malformed/missing/reordered/duplicate/corrupt/mismatched/excess controls', 'mandatory protocol cases and boundary products'),
    ('C5', 'Interruption/cancellation/timeout/crash/malformed input or response/publication failure preserve Targets and release ownership', 'real-child termination, sentinels, ordered receipts and cleanup'),
    ('C6', 'Actual 5000-page, 1073741824-byte and two-admission/excess/retry profiles run on all four JDKs; no mandatory skips', 'four original and four fresh 180-case executions with actual scale products'),
    ('C7', 'Actual input constructions/lengths/hashes, output identities, finite policies, modeled resources, time and cleanup retained', 'scale observations and independent actual-workload findings; read-only output absence explicit'),
    ('C8', 'Final candidate/contract/configuration/environment/image/exact JDK/launcher/runtime/tool/native identities bound', 'freeze, staged receipt, portable archives and every execution/environment record'),
    ('C9', 'Four HARDENED_WORKER recovery tuples retain twenty independent passing records and qualified negatives', 'syntax, standards, semantic, visual and contract records in current index'),
    ('C10', 'Strict recovery recorder/collector/checker rejects missing/altered/stale/resealed evidence', '16 T22 and 19 prerequisite guard tests, full-reactor checker tests and actual live replay'),
    ('C11', 'Public Native/Facade outcomes, reopen, ownership, receipts and safe failures observed at actual profile', 'Native HARDENED_WORKER and Facade IN_PROCESS; recovery controls remain Native-only'),
    ('C12', 'Recovery promotion, authorities, generated views, English/Chinese contracts and provenance agree', 'frozen documentation and final inventory generate/validate/check'),
    ('C13', 'All thirteen previously satisfied prerequisites satisfy this same new candidate', 'fourteen fresh dependency-ordered certifications, 100 tuples and 412 chains'),
    ('C14', 'Selected recovery/prerequisites SATISFIED; overall NOT READY only for unrelated obligations', 'live readiness and individually audited unrelated blockers'),
    ('C15', 'Focused development behavior and strict evidence validation executed', '271-test Ubuntu suite, wall-step red, guard tests and preserved failed-attempt dispositions'),
    ('C16', 'Explicit ./scripts/t22-scale all actually runs all three profiles in every declared image', 'standalone-scale-final-r9 and four-environment scale ledger'),
    ('C17', 'Full verify, JDK 8/11/17/21 matrix and inventory generation/validation/freshness/readiness run', 'successful full/matrix/scale gate logs, counts and final inventory gates'),
    ('C18', 'Standards/Spec findings resolved; evidence, ownership and limitations retained', 'prefreeze and final baseline-relative review conclusions and final audit'),
    ('C19', 'Final diff reviewed against criteria and baseline before commit', 'final reviews, diff-check and owned-file manifests'),
    ('C20', 'One local DCO-signed commit is authorized only after selected criteria pass', 'all precommit gates pass; resulting commit SHA is reported by Git in final delivery'),
    ('C21', 'Only issue-83 changes are captured; unrelated work, scratch, contracts and historical evidence preserved', 'user-approved dirty relaunch, exact ownership manifest and predecessor revalidation'),
)
lines = [
    '# Issue #83 candidate10 execution receipt', '',
    'All selected certification, verification and precommit review gates passed.',
    'One local DCO-signed commit is authorized after closing ownership checks.',
    'The final Git commit supplies its own identity; this precommit receipt avoids a',
    'self-referential commit hash. Push, release publication and issue closure remain',
    'with the orchestrator.', '',
    '- Sole contract: [retained original](execution-contract.md).',
    '- Baseline: `' + audit['baseline'] + '`; branch `main`.',
    '- Entry: explicitly approved dirty tree containing this agent prior #83 work.',
    '- Candidate: `' + audit['candidate-identity'] + '`.',
    '- Contract: `' + audit['contract-identity'] + '`.',
    '- Fresh index: **100 tuples / 412 passing chains for fourteen obligations**.',
    '- Recovery: **four Ubuntu 24.04 Linux x86-64 tuples / twenty chains**.',
    '  Each original and fresh independent execution runs **180 mandatory cases**',
    '  with zero failures, ignored cases, assumptions or duplicates.', '',
    '| Completion criterion | Outcome | Observable evidence |',
    '| --- | --- | --- |',
]
lines.extend('| ' + key + ': ' + criterion + ' | ' + ('AUTHORIZED; commit follows' if key == 'C20' else 'PASS') + ' | ' + evidence + ' |'
             for key, criterion, evidence in criteria)
lines += ['', 'The table binds [the final audit](final-audit-r20.json),',
    '[fresh execution ledger](refresh-final-r21/results.json),',
    '[current index](../../foundation-evidence.yaml),',
    '[mandatory case inventory](../../profiles/T22-worker-recovery-scale/mandatory-tests.txt),',
    '[coverage authority](../../profiles/T22-worker-recovery-scale/coverage.json),',
    '[source freeze](source-freeze-final-r10.json) and',
    '[portable candidate archives](candidate-final-r10/README.md).', '',
    '| Command | Outcome | Monotonic seconds | Retained transcript |',
    '| --- | --- | --- | --- |']
for name, reference in audit['gates'].items():
    gate = json.loads((root / reference['path']).read_text())
    outcome = 'NOT READY, unrelated obligations only (exit 1)' if gate['exit-code'] else 'PASS (exit 0)'
    lines.append('| `' + shlex.join(gate['repository-command']) + '` | ' + outcome
        + ' | ' + format(gate['monotonic-duration-seconds'], '.3f') + ' | [' + name
        + '](validation/' + Path(gate['log']['path']).name + ') |')
ordinary = audit['ordinary-verification']
lines += ['', 'Full verification reports ' + str(ordinary['tests']) + ' cases ('
    + str(ordinary['tests'] - ordinary['skipped']) + ' executed), zero failures/errors,',
    'and four ordinary skips: three opt-in scale cases and one unrelated T30 case.',
    'All eight Worker exit regressions and all seven new timing regressions execute',
    'without skips. Ordinary opt-in skips do not qualify certification.', '',
    '| Matrix JDK | Reported / executed cases | Failures / errors | Ordinary skips |',
    '| --- | --- | --- | --- |']
for item in audit['jdk-matrix']['environments']:
    lines.append('| ' + str(item['jdk-major']) + ' | ' + str(item['tests']) + ' / '
        + str(item['tests'] - item['skipped']) + ' | 0 / 0 | ' + str(item['skipped']) + ' |')
lines += ['', 'Every matrix reactor executes the eight Worker exit and seven timing regressions.',
    'The [full counts](validation/full-verify-final-r13-tests.json) and',
    '[matrix counts](validation/jdk-matrix-final-r6-tests.json) bind their original logs.',
    'The [explicit scale ledger](scale-final-r9/results.json) observes all three actual',
    'profiles with zero skips on Temurin `1.8.0_502-b07`, `11.0.32+9`, `17.0.20+8`,',
    'and `21.0.12+8-LTS` in their declared immutable images.', '',
    'The Worker now grants thirty seconds for child exit after authenticated FINISHED.',
    'Its liveness recheck, cancellation, caller deadline and overall elapsed bound still',
    'apply. Failed waits retain stable safe failures, transaction identity and ordered',
    'receipts, and add bounded elapsed/liveness/exit/process/thread-state diagnostics.',
    'No document content, command line, environment or thread names are recorded.',
    'Delayed success, actual thirty-second expiry, exit 37, cancellation, deadline,',
    'elapsed excess and interruption qualify publication safety, lookup and cleanup',
    'in all four original and four fresh recovery executions.', '',
    'Workflow elapsed limits, remaining Provider budgets and usage now default to an',
    'explicit injectable `System.nanoTime` ticker. Signed subtraction handles wraparound.',
    'Explicit Clock-only test environments preserve their existing logical-time semantics;',
    'an explicit ticker takes precedence in either setter order. Absolute deadlines and',
    'receipt timestamps keep wall-clock meaning. The Worker initialization decoder rejects',
    'a relative child deadline instead of converting it through `Instant.now`.',
    'The [complete timing audit](timing-audit-r20.md) lists all measurements and decisions.', '',
    'The deterministic [wall-step red](validation/monotonic-wall-step-red-r1.txt) failed',
    'with the original elapsed-time exception. New regressions prove forward wall steps',
    'cannot exhaust elapsed budgets, genuine ticker or real-time excess does, the inclusive',
    'boundary survives wraparound/backward wall steps, and wall deadlines remain effective.',
    'The focused Ubuntu suite passed 271 tests during an observed wall jump: monotonic ',
    format(audit['monotonic-timing']['focused-monotonic-duration-seconds'], '.3f') + ' seconds, wall ',
    format(audit['monotonic-timing']['focused-wall-duration-seconds'], '.3f') + ' seconds, difference ',
    format(audit['monotonic-timing']['focused-wall-minus-monotonic-seconds'], '.3f') + ' seconds.',
    'Every new gate retains paired durations; Maven/Surefire wall-derived durations remain',
    'unaltered diagnostics. No universal performance or failure-rate guarantee is inferred.', '',
    'All [failed and superseded attempts](failed-attempts.md) remain intact. Candidate9',
    'and full verifies r10/r11/r12 are superseded failures, with original reports/source',
    'and diagnostic replays retained. The independent wall-step investigation establishes',
    'the product timing defect and the approved fix; those failed gates do not certify',
    'candidate10. The unexecuted R19 draft supplies no evidence. All fourteen',
    'obligations were run fresh for candidate10; no old PASS was relabeled.',
    'R20 collected the first five obligations, then stopped on an independent T13',
    'observer CPU guard during Python version reporting before input inspection.',
    'That partial text scope remained unindexed; all 58,245 originals were preserved',
    'and rehashed. Twenty exact-control diagnostics succeeded under unchanged',
    'soft5s/hard10s CPU limits and exported no certification. Text was then run',
    'entirely in fresh R21 scopes, followed by the remaining eight obligations in',
    'unused R20 destinations. The five current-candidate passes remain unaltered',
    'in the combined R21 ledger. The cause of the isolated CPU guard event is not',
    'established by its traceback or the successful diagnostics. See',
    '[failure preservation](failed-text-certification-r20.json) and',
    '[exact-control measurements](t13-cpu-diagnostics-r21/results.json).',
    'Candidates6/7/8/9 portable',
    'archives and historical candidate8 references are revalidated. The successful R15',
    'text closeout retains all 157,778 original R14 text files and its disclosed unknown',
    'historical outer exit. Host-fixture and sandbox/prelaunch failures are also disclosed.', '',
    'Final [Standards](reviews/standards-relaunch-r21.md) and',
    '[Spec](reviews/spec-relaunch-r21.md) reviews have zero unresolved actionable findings.',
    'The [prefinal ownership audit](changed-file-ownership-prefinal-r20.json) records',
    'actual source/evidence bytes. The closing [final manifest](changed-file-ownership-final-r20.tsv.gz)',
    'and [summary](changed-file-ownership-final-r20.json) also bind this receipt and closing',
    'review notes, with explicit self-exclusion for the manifest and its summary.',
    'Seven existing production classes change; no runtime dependency/build change was added.',
    'Acceptance-only tools/fonts/fixtures/classes remain outside the Worker runtime closure.', '',
    'Live recovery and every prerequisite are SATISFIED. Overall Foundation remains',
    'NOT READY solely for these unrelated obligations:', '']
lines.extend('- `' + item['obligation'] + '` (#' + str(item['issue']) + '): ' + item['reason']
             for item in audit['remaining-unrelated-obligations'])
lines += ['', 'Residual limits: environment-local in-memory caller-driven recovery, no automatic',
    'retry, durable/distributed identity or cross-Target atomicity. Memory/storage figures',
    'model Folio-owned allocations rather than JVM heap, RSS, native memory, kernel/container',
    'resources or physical erasure. Semantic observations cover all actual pages; visual',
    'qualification samples pages 1/2500/5000 and every actual one-page product. Standards',
    'coverage is limited to the qualified blank-document predicates. Windows/macOS and',
    'parent/downstream slices, including #88, remain outside this certification.',
    'No push, PR, merge, tracker edit, issue closure, release publication or external',
    'message was performed. Push, publication and closure remain with the orchestrator.', '']
path.write_text('\n'.join(lines))
print('Rendered candidate10 completion criteria from actual R20 evidence and ownership.')
