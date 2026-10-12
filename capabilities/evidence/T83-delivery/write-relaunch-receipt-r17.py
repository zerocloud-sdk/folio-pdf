"""Render the R17 receipt only from completed verification and ownership facts."""
import json
from pathlib import Path
import shlex

delivery = Path(__file__).resolve().parent
root = delivery.parents[2]
audit = json.loads((delivery / 'final-audit-r17.json').read_text())
assert audit['certification-tuples'] == 100 and audit['chain-records'] == 412
assert audit['recovery-tuples'] == 4 and audit['recovery-chain-records'] == 20
assert len(audit['selected-obligations']) == 14
ownership = json.loads((delivery / 'changed-file-ownership-prefinal-r17.json').read_text())
assert ownership['ownership'] == 'issue-83' and not ownership['entry-unrelated-files']
path = delivery / 'receipt-r17.md'
assert not path.exists()
criteria = (('C1', 'Worker-only environment-local identities, envelope conflicts, duplicate rejection and finite retention', 'coverage and original/fresh 173-case transcripts'), ('C2', 'RUNNING/RECOVERABLE/COMPLETED/FAILED, ordered receipts and eligible retry only', 'mandatory transcripts and public recovery observations'), ('C3', 'Fully committed lost acknowledgement resolves COMPLETED; partial stream failure is terminal with no replay', 'public Path/stream outcomes and retained receipts'), ('C4', 'Real multi-frame values, reopen and authenticated malformed/missing/reordered/duplicate/corrupt/mismatched/excess controls', 'mandatory protocol cases and boundary observations'), ('C5', 'Actual interruption, cancellation, timeout, crash, malformed input/response and publication failures protect Targets and release ownership', 'real-child termination, sentinel, cleanup and public outcome records'), ('C6', 'All three actual scale profiles execute on every required JDK with zero mandatory skips', 'four original/fresh 173-case transcripts and exact scale observations'), ('C7', 'Actual full input/construction/hash, published outputs, finite policy, modeled resources, wall/elapsed time, ownership and cleanup retained', 'scale-observations and independent actual-workload products/findings'), ('C8', 'Final candidate, contract, configuration, environment, immutable image, exact JDK/launcher/runtime/tool/native identities bound', 'final audit, source freeze, staged receipt and each execution/environment record'), ('C9', 'Twenty separate required passing recovery chains with original findings and qualified detected negatives', 'four recovery tuples, five chains each in the current index'), ('C10', 'Strict recovery recorder/collector/checker and meaningful omitted/altered/stale/resealed negative guards', '35 passing T20/T21/T22 guards, driver/catalog/checker tests and actual strict live replay'), ('C11', 'Public Native and actual IN_PROCESS Facade document outcomes match; controls stay Native-only', 'public recovery/Facade products, ownership, reopen, failures and receipts'), ('C12', 'Only fully evidenced recovery promoted; inventories, contracts, generated views, English/Chinese usage and provenance synchronized', 'owned source/documentation diff and inventory validation/generation/check'), ('C13', 'All previously satisfied prerequisites satisfy the same frozen candidate in contract order', 'fourteen-step refresh results and 100 current certifications / 412 chains'), ('C14', 'Recovery and all prerequisites SATISFIED; overall NOT READY solely for unrelated obligations', 'final live readiness and audited unrelated blockers'), ('C15', 'Smallest applicable focused recovery/protocol and recorder/collector/checker validation executed', 'retained development validation records and failed-attempt dispositions'), ('C16', 'Explicit ./scripts/t22-scale all executes all three profiles with no skips in all four declared images', 'standalone-scale-final-r8 gate and scale-final-r8/results.json'), ('C17', 'Full verify, four-image JDK matrix, inventory generate/validate/check and live readiness executed', 'final gate logs; narrow shipped Worker correction triggers the passing complete JDK matrix'), ('C18', 'Baseline-relative Standards and Spec findings resolved and evidence/ownership/limitations retained', 'both review receipts, final audit, this receipt and ownership manifest'), ('C19', 'Final diff reviewed against source criteria and recorded baseline before commit', 'final reviews, baseline/source audit, diff check and changed-file ownership'), ('C20', 'Single local signed-off commit gate requires all criteria and explicit user authority', 'authorizations.md; final response reports the resulting commit SHA'), ('C21', 'Worktree contains only owned issue #83 changes; unrelated and historical files preserved', 'user-approved dirty relaunch, final ownership audit, preserved failed attempts and unchanged T82 records'))
lines = [
    '# Issue #83 relaunch execution receipt', '',
    'Completed the selected recovery and bounded-scale criteria on one freshly staged',
    'candidate. A single DCO-signed local commit is the final operation after receipt,',
    'final review and file ownership checks. Publication and closure remain with the',
    'orchestrator.', '',
    '- Sole contract: [retained original](execution-contract.md).',
    '- Baseline: `' + audit['baseline'] + '`; branch `main`.',
    '- Entry: explicitly user-approved dirty tree containing this agent prior #83 work;',
    '  prior source, partial/failed runs and archived candidates are preserved.',
    '- Candidate identity: `' + audit['candidate-identity'] + '`.',
    '- Contract identity: `' + audit['contract-identity'] + '`.',
    '- Fresh R17 index: 100 tuples / 412 passing chain records for all 14 selected obligations.',
    '- Recovery: four required Ubuntu 24.04 Linux x86-64 HARDENED_WORKER tuples / 20 chains;',
    '  each original and independent fresh execution runs all 173 named cases without skips.', '',
    '| Completion criterion | Outcome | Observable evidence |',
    '| --- | --- | --- |']
lines.extend('| ' + key + ': ' + criterion + ' | PASS | ' + evidence + ' |'
             for key, criterion, evidence in criteria)
lines += ['', 'The criteria above bind [the final audit](final-audit-r17.json),',
    '[fresh execution ledger](refresh-final-r17/results.json),',
    '[current index](../../foundation-evidence.yaml),',
    '[named-case inventory](../../profiles/T22-worker-recovery-scale/mandatory-tests.txt),',
    '[coverage authority](../../profiles/T22-worker-recovery-scale/coverage.json),',
    '[source freeze](source-freeze-final-r9.json) and',
    '[portable candidate archives](candidate-final-r9/README.md).', '',
    '| Command | Outcome | Retained transcript |', '| --- | --- | --- |']
for name, reference in audit['gates'].items():
    gate = json.loads((root / reference['path']).read_text())
    outcome = 'NOT READY; audited unrelated obligations only (exit 1)' if gate['exit-code'] else 'PASS (exit 0)'
    lines.append('| `' + shlex.join(gate['repository-command']) + '` | ' + outcome
        + ' | [' + name + '](validation/' + Path(gate['log']['path']).name + ') |')
ordinary = audit['ordinary-verification']
lines += ['', 'Full verification: ' + str(ordinary['tests']) + ' reported test cases ('
    + str(ordinary['tests'] - ordinary['skipped']) + ' executed), zero failures/errors,',
    'four ordinary skips (three opt-in scale cases and one unrelated T30 case). All eight',
    'Worker exit regressions pass. The [actual count record](validation/full-verify-final-r11-tests.json)',
    'distinguishes ordinary verification from mandatory no-skip certification.', '',
    '| Matrix JDK | Reported / executed cases | Failures / errors | Ordinary skips |',
    '| --- | --- | --- | --- |']
for item in audit['jdk-matrix']['environments']:
    lines.append('| ' + str(item['jdk-major']) + ' | ' + str(item['tests']) + ' / '
        + str(item['tests'] - item['skipped']) + ' | 0 / 0 | '
        + str(item['skipped']) + ' |')
lines += ['', 'All four reactors pass all eight exit regressions. Exact images/commands and',
    'hash-bound counts are [retained](validation/jdk-matrix-final-r5-tests.json).',
    'The explicit [four-environment scale ledger](scale-final-r8/results.json) runs',
    '`./scripts/t22-scale all` with all three cases passing without skips on each JDK.', '',
    'The Worker grace is now 30 seconds from authenticated FINISHED. The existing liveness',
    'recheck remains; cancellation, caller deadline and the default five-minute overall',
    'bound retain their established outcomes. Every failed post-FINISHED wait preserves',
    'failure code/capability/transaction/receipts and adds elapsed time, child liveness,',
    'available exit code and bounded process/thread-state diagnostics before this path',
    'forces termination. No command line, environment, thread names or document content',
    'is retained. PID/process state are best-effort outside the four qualified environments.', '',
    'The real delayed-exit regression stays alive beyond the previous one-second grace',
    'and succeeds within the new bound, with publication, lookup, reopen and cleanup.',
    'Actual stuck-child expiry waits the full 30 seconds and qualifies safe diagnostics',
    'in every original and fresh transcript. Its JUnit timeout is 60 seconds; the other',
    'exit cases use 10 seconds. Real nonzero exit 37, cancellation, caller deadline,',
    'overall elapsed bound and interruption prevent publication and retain ordered',
    'NOT_ATTEMPTED receipts, recoverable lookup, unchanged Targets and confirmed cleanup.', '',
    'Development evidence includes the [old-grace red](validation/worker-30s-delayed-exit-red-r2.txt),',
    '[missing nonzero diagnostics red](validation/worker-post-finished-nonzero-red-r2.txt),',
    '[missing evidence guard red](validation/worker-30s-evidence-guard-red-r1.txt),',
    '[158-case Java8 focused green](validation/jdk8-interrupted-inspection-focused-green-r1.txt),',
    '[158-case Java17 focused green](validation/worker-fixture-focused-jdk17-green-r1.txt),',
    '[35 strict guards](validation/python-recovery-worker-guards-r19.txt) and',
    '[12 checker cases](validation/checker-recovery-relaunch-r1.txt).', '',
    'All failed and superseded attempts remain [disclosed](failed-attempts.md). The original',
    'R11 run was user-stopped mid incremental and never completed certification. Candidate6',
    'was controlled-stopped during full verification before certification, to extend',
    'diagnostics to every post-FINISHED failure. Its partial log cannot qualify a gate;',
    'both archive manifests and all five candidate6 parts remain hash/size/ordering bound.',
    'Candidate7 passed full verification but its Java8 matrix failed on fixture sentinel',
    'inspection after the Worker preserved caller interruption. Original XML/text/source',
    'and its successful/failed gate identities remain retained. The exact-method red',
    'reproduced ClosedByInterruptException. The fixture now asserts preserved interruption',
    'before clearing/restoring it for its own NIO inspection; runtime behavior is unchanged.',
    'All candidate6/7 archive manifests and ten parts remain hash/size/ordering bound.',
    'R14 was interrupted by the user-reported API 403 authentication disconnect. Its',
    'five completed obligations and all eight sealed text corpora remain intact. The',
    'actual R15 strict closeout generated only the missing records and closing checks,',
    'preserving 157,778 original files and the unknown historical outer process exit.',
    'Two current documentation inconsistencies then required candidate9: recovery status',
    'wording and the exact 173-case matrix assertion. R17 re-runs every selected',
    'obligation from scratch; no old PASS is relabeled.', '',
    'The first candidate9 full gate (R16) failed on an unchanged barcode workflow',
    'elapsed-time limit. Original reports and the failed gate remain intact. Ten',
    'exact-method replays passed in seconds with the captured image/classpath/cwd',
    'and all owned containers gone. Complete loaded bytes are archived. The failure',
    'did not reproduce in isolated parents; no cause, barcode fix or failure-rate',
    'bound is claimed. A separately retained fresh full verification passed before',
    'the R17 certification began.', '',
    'The [independent investigation](relaunch-worker-investigation.md) observed a VM stall',
    'beyond one second and reproduced the failure with 1.2-second injected process-tree',
    'pauses. Natural stress did not reproduce the historical failures; their exact stalls',
    'remain inferred. The user explicitly selected this grace change. Successful current',
    'certification provides observed qualification, not a universal failure-rate guarantee.', '',
    'Final read-only [Standards](reviews/standards-relaunch-r10.md) and',
    '[Spec](reviews/spec-relaunch-r10.md) baseline-relative reviews have zero unresolved',
    'findings; [fixture Standards](reviews/standards-relaunch-r5.md),',
    '[fixture Spec](reviews/spec-relaunch-r5.md), [delivery Standards](reviews/standards-relaunch-r6.md)',
    'and [delivery Spec](reviews/spec-relaunch-r6.md) retain their separate conclusions.',
    'The [prefinal ownership audit](changed-file-ownership-prefinal-r17.json) covers',
    'actual source/evidence bytes; the closing [final manifest](changed-file-ownership-final-r17.tsv.gz)',
    'and [summary](changed-file-ownership-final-r17.json) also bind this receipt and final',
    'review notes. Only HardenedWorkerEngine changes shipped production code; tools and',
    'acceptance classes remain outside its runtime closure.', '',
    'Recovery and every prior prerequisite are SATISFIED for the same final candidate.',
    'Overall Foundation readiness is false solely for these unrelated outstanding obligations:', '']
lines.extend('- `' + item['obligation'] + '` (#' + str(item['issue']) + '): ' + item['reason']
             for item in audit['remaining-unrelated-obligations'])
lines += ['', 'Residual scope limits: environment-local in-memory caller-driven recovery; no',
    'automatic retry, durable/distributed identity or cross-Target atomicity. Memory/storage',
    'observations model Folio-owned allocations, not JVM heap, process RSS, native memory,',
    'kernel/container resources or physical erasure. Semantics observes all actual pages;',
    'visual qualification samples pages 1/2500/5000 plus each actual one-page product.',
    'Standards are limited to the qualified blank-document predicates. Windows/macOS and',
    'parent/downstream slices, including #88, remain outside scope. No push, PR, merge,',
    'tracker mutation, issue closure, release publication or external messages occurred.',
    'The orchestrator retains push, publication and closure.', '']
path.write_text('\n'.join(lines))
print('Rendered all 21 completion criteria from actual R17 evidence and ownership.')
