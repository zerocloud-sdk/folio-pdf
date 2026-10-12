# Issue #83 candidate10 execution receipt

All selected certification, verification and precommit review gates passed.
One local DCO-signed commit is authorized after closing ownership checks.
The final Git commit supplies its own identity; this precommit receipt avoids a
self-referential commit hash. Push, release publication and issue closure remain
with the orchestrator.

- Sole contract: [retained original](execution-contract.md).
- Baseline: `42fe5c797711070b319b9419e14fcb7adfa98735`; branch `main`.
- Entry: explicitly approved dirty tree containing this agent prior #83 work.
- Candidate: `f554e856b8da919a948cf02be86120f380b6b56f3150bcab79cb178e4abddc34`.
- Contract: `77496c1b1f7973d220824315954207b9e38487d1310b014f7b23ee19cf5f3ab4`.
- Fresh index: **100 tuples / 412 passing chains for fourteen obligations**.
- Recovery: **four Ubuntu 24.04 Linux x86-64 tuples / twenty chains**.
  Each original and fresh independent execution runs **180 mandatory cases**
  with zero failures, ignored cases, assumptions or duplicates.

| Completion criterion | Outcome | Observable evidence |
| --- | --- | --- |
| C1: Worker-only environment-local identity, material conflicts, duplicates, finite non-evicting retention and oversized admission | PASS | 180 named cases and public identity/retention observations |
| C2: RUNNING/RECOVERABLE/COMPLETED/FAILED, ordered receipts and eligible retry only | PASS | original and fresh public transaction transcripts |
| C3: Committed acknowledgement loss resolves COMPLETED; partial stream publication is terminal and cannot replay | PASS | Path/stream publication, lookup, receipts and byte observations |
| C4: Real bidirectional multi-frame transfer/reopen and authenticated malformed/missing/reordered/duplicate/corrupt/mismatched/excess controls | PASS | mandatory protocol cases and boundary products |
| C5: Interruption/cancellation/timeout/crash/malformed input or response/publication failure preserve Targets and release ownership | PASS | real-child termination, sentinels, ordered receipts and cleanup |
| C6: Actual 5000-page, 1073741824-byte and two-admission/excess/retry profiles run on all four JDKs; no mandatory skips | PASS | four original and four fresh 180-case executions with actual scale products |
| C7: Actual input constructions/lengths/hashes, output identities, finite policies, modeled resources, time and cleanup retained | PASS | scale observations and independent actual-workload findings; read-only output absence explicit |
| C8: Final candidate/contract/configuration/environment/image/exact JDK/launcher/runtime/tool/native identities bound | PASS | freeze, staged receipt, portable archives and every execution/environment record |
| C9: Four HARDENED_WORKER recovery tuples retain twenty independent passing records and qualified negatives | PASS | syntax, standards, semantic, visual and contract records in current index |
| C10: Strict recovery recorder/collector/checker rejects missing/altered/stale/resealed evidence | PASS | 16 T22 and 19 prerequisite guard tests, full-reactor checker tests and actual live replay |
| C11: Public Native/Facade outcomes, reopen, ownership, receipts and safe failures observed at actual profile | PASS | Native HARDENED_WORKER and Facade IN_PROCESS; recovery controls remain Native-only |
| C12: Recovery promotion, authorities, generated views, English/Chinese contracts and provenance agree | PASS | frozen documentation and final inventory generate/validate/check |
| C13: All thirteen previously satisfied prerequisites satisfy this same new candidate | PASS | fourteen fresh dependency-ordered certifications, 100 tuples and 412 chains |
| C14: Selected recovery/prerequisites SATISFIED; overall NOT READY only for unrelated obligations | PASS | live readiness and individually audited unrelated blockers |
| C15: Focused development behavior and strict evidence validation executed | PASS | 271-test Ubuntu suite, wall-step red, guard tests and preserved failed-attempt dispositions |
| C16: Explicit ./scripts/t22-scale all actually runs all three profiles in every declared image | PASS | standalone-scale-final-r9 and four-environment scale ledger |
| C17: Full verify, JDK 8/11/17/21 matrix and inventory generation/validation/freshness/readiness run | PASS | successful full/matrix/scale gate logs, counts and final inventory gates |
| C18: Standards/Spec findings resolved; evidence, ownership and limitations retained | PASS | prefreeze and final baseline-relative review conclusions and final audit |
| C19: Final diff reviewed against criteria and baseline before commit | PASS | final reviews, diff-check and owned-file manifests |
| C20: One local DCO-signed commit is authorized only after selected criteria pass | AUTHORIZED; commit follows | all precommit gates pass; resulting commit SHA is reported by Git in final delivery |
| C21: Only issue-83 changes are captured; unrelated work, scratch, contracts and historical evidence preserved | PASS | user-approved dirty relaunch, exact ownership manifest and predecessor revalidation |

The table binds [the final audit](final-audit-r23.json),
[fresh execution ledger](refresh-final-r23/results.json),
[current index](../../foundation-evidence.yaml),
[mandatory case inventory](../../profiles/T22-worker-recovery-scale/mandatory-tests.txt),
[coverage authority](../../profiles/T22-worker-recovery-scale/coverage.json),
[source freeze](source-freeze-final-r10.json) and
[portable candidate archives](candidate-final-r10/README.md).

| Command | Outcome | Monotonic seconds | Retained transcript |
| --- | --- | --- | --- |
| `./mvnw -B -ntp verify` | PASS (exit 0) | 1298.276 | [full-verify-final-r13](validation/full-verify-final-r13.txt) |
| `./scripts/verify-jdk-matrix.sh` | PASS (exit 0) | 5562.087 | [jdk-matrix-final-r6](validation/jdk-matrix-final-r6.txt) |
| `python3 -B capabilities/evidence/T83-delivery/standalone-scale.py scale-final-r9` | PASS (exit 0) | 173.903 | [standalone-scale-final-r9](validation/standalone-scale-final-r9.txt) |
| `python3 -B capabilities/evidence/T83-delivery/freeze-relaunch-r20.py` | PASS (exit 0) | 13.516 | [source-freeze-final-r10](validation/source-freeze-final-r10.txt) |
| `python3 -B scripts/t03-foundation.py stage` | PASS (exit 0) | 23.337 | [stage-final-r10](validation/stage-final-r10.txt) |
| `python3 -B capabilities/evidence/T83-delivery/archive-candidate.py candidate-final-r10` | PASS (exit 0) | 9.536 | [archive-candidate-final-r10](validation/archive-candidate-final-r10.txt) |
| `./mvnw -B -ntp -pl pdf-document -am -Dtest=WorkflowElapsedTimeContractTest,WorkerInitializationTimingTest,HardenedWorkerRecoveryTest,HardenedWorkerRecoveryFaultTest,WorkflowTransactionContractTest,WorkflowExecutionProfileContractTest,HardenedWorkerWorkflowTest,WorkerProtocolBoundaryTest,WorkerCodecContractTest,HardenedWorkerTerminationWaitTest,HostileInputWorkflowTest,EmbeddedFilesPasswordWorkflowTest,WorkflowResourceOwnershipTest,RenderingWorkflowContractTest -Dsurefire.failIfNoSpecifiedTests=false test` | PASS (exit 0) | 125.950 | [monotonic-recovery-protocol-green-r3](validation/monotonic-recovery-protocol-green-r3.txt) |
| `env -u FOLIO_FOUNDATION_PYTHON_ROOT python3 -B -m unittest discover -s scripts/tests -p test_t22_foundation.py -v` | PASS (exit 0) | 0.565 | [monotonic-python-plan-guards-r20](validation/monotonic-python-plan-guards-r20.txt) |
| `env -u FOLIO_FOUNDATION_PYTHON_ROOT python3 -B -m unittest discover -s scripts/tests -p 'test_t2[01]_foundation.py' -v` | PASS (exit 0) | 0.417 | [monotonic-python-prerequisite-guards-r20](validation/monotonic-python-prerequisite-guards-r20.txt) |
| `python3 -B capabilities/evidence/T83-delivery/preserve-superseded-r20.py superseded-preservation-r20.json` | PASS (exit 0) | 327.842 | [preserve-superseded-r20](validation/preserve-superseded-r20.txt) |
| `./scripts/inventory generate` | PASS (exit 0) | 77.877 | [inventory-generate-final-r23](validation/inventory-generate-final-r23.txt) |
| `./scripts/inventory validate` | PASS (exit 0) | 5.188 | [inventory-validate-final-r23](validation/inventory-validate-final-r23.txt) |
| `./scripts/inventory check` | PASS (exit 0) | 37.481 | [inventory-check-final-r23](validation/inventory-check-final-r23.txt) |
| `./scripts/inventory readiness` | NOT READY, unrelated obligations only (exit 1) | 40.221 | [inventory-readiness-final-r23](validation/inventory-readiness-final-r23.txt) |
| `podman --log-level=error ps --format '{{.ID}} {{.Names}} {{.Status}}'` | PASS (exit 0) | 0.165 | [post-final-r23-container-check](validation/post-final-r23-container-check.txt) |
| `git diff --check` | PASS (exit 0) | 14.327 | [diff-check-final-r23](validation/diff-check-final-r23.txt) |
| `/usr/bin/python3 -B /workspace/folio-pdf/capabilities/evidence/T83-delivery/retain-relaunch-verification-counts-r20.py full-verify-final-r13` | PASS (exit 0) | 0.114 | [retain-full-verify-final-r13-counts-r20](validation/retain-full-verify-final-r13-counts-r20.txt) |
| `/usr/bin/python3 -B /workspace/folio-pdf/capabilities/evidence/T83-delivery/retain-relaunch-verification-counts-r20.py jdk-matrix-final-r6` | PASS (exit 0) | 0.064 | [retain-jdk-matrix-final-r6-counts-r20](validation/retain-jdk-matrix-final-r6-counts-r20.txt) |
| `python3 -B capabilities/evidence/T83-delivery/diagnose-t13-cpu-r21.py` | PASS (exit 0) | 7.628 | [t13-cpu-diagnostic-r21](validation/t13-cpu-diagnostic-r21.txt) |
| `podman --log-level=error ps --format '{{.ID}} {{.Names}} {{.Status}}'` | PASS (exit 0) | 0.165 | [pre-r21-container-check](validation/pre-r21-container-check.txt) |
| `python3 -B capabilities/evidence/T83-delivery/preserve-failed-text-r21.py` | PASS (exit 0) | 17.285 | [preserve-failed-text-r21](validation/preserve-failed-text-r21.txt) |
| `podman --log-level=error ps --format '{{.ID}} {{.Names}} {{.Status}}'` | PASS (exit 0) | 0.165 | [post-r21-failure-container-check](validation/post-r21-failure-container-check.txt) |
| `podman --log-level=error ps --format '{{.ID}} {{.Names}} {{.Status}}'` | PASS (exit 0) | 0.215 | [post-r22-diagnostic-container-check](validation/post-r22-diagnostic-container-check.txt) |
| `python3 -B capabilities/evidence/T83-delivery/prove-staged-content-r23.py` | PASS (exit 0) | 1.467 | [staged-content-proof-r23](validation/staged-content-proof-r23.txt) |
| `python3 -B scripts/t03-foundation.py certify capabilities/evidence/foundation/T83-final-r20/transactions --obligation transactions` | PASS (exit 0) | 302.953 | [refresh-final-r20-transactions](validation/refresh-final-r20-transactions.txt) |
| `python3 -B scripts/t03-foundation.py certify capabilities/evidence/foundation/T83-final-r20/values --obligation values` | PASS (exit 0) | 494.164 | [refresh-final-r20-values](validation/refresh-final-r20-values.txt) |
| `python3 -B scripts/t03-foundation.py certify capabilities/evidence/foundation/T83-final-r20/pages --obligation pages` | PASS (exit 0) | 1151.186 | [refresh-final-r20-pages](validation/refresh-final-r20-pages.txt) |
| `python3 -B scripts/t03-foundation.py certify capabilities/evidence/foundation/T83-final-r20/metadata --obligation metadata` | PASS (exit 0) | 1164.554 | [refresh-final-r20-metadata](validation/refresh-final-r20-metadata.txt) |
| `python3 -B scripts/t03-foundation.py certify capabilities/evidence/foundation/T83-final-r20/annotations --obligation annotations` | PASS (exit 0) | 3212.898 | [refresh-final-r20-annotations](validation/refresh-final-r20-annotations.txt) |
| `python3 -B /workspace/folio-pdf/capabilities/evidence/T83-delivery/complete-text-relaunch-r23.py` | PASS (exit 0) | 1135.806 | [refresh-final-r23-text](validation/refresh-final-r23-text.txt) |
| `python3 -B scripts/t03-foundation.py certify capabilities/evidence/foundation/T83-final-r20/images --obligation images` | PASS (exit 0) | 652.999 | [refresh-final-r23-images](validation/refresh-final-r23-images.txt) |
| `python3 -B scripts/t03-foundation.py certify capabilities/evidence/foundation/T83-final-r20/incremental --obligation incremental` | PASS (exit 0) | 1016.869 | [refresh-final-r23-incremental](validation/refresh-final-r23-incremental.txt) |
| `python3 -B scripts/t03-foundation.py certify capabilities/evidence/foundation/T83-final-r20/password-baseline --obligation password-baseline` | PASS (exit 0) | 5910.310 | [refresh-final-r23-password-baseline](validation/refresh-final-r23-password-baseline.txt) |
| `python3 -B scripts/t03-foundation.py certify capabilities/evidence/foundation/T83-final-r20/password-clear-metadata --obligation password-clear-metadata` | PASS (exit 0) | 7550.934 | [refresh-final-r23-password-clear-metadata](validation/refresh-final-r23-password-clear-metadata.txt) |
| `python3 -B scripts/t03-foundation.py certify capabilities/evidence/foundation/T83-final-r20/password-attachments --obligation password-attachments` | PASS (exit 0) | 9949.437 | [refresh-final-r23-password-attachments](validation/refresh-final-r23-password-attachments.txt) |
| `python3 -B scripts/t03-foundation.py certify capabilities/evidence/foundation/T83-final-r20/limits --obligation limits` | PASS (exit 0) | 967.424 | [refresh-final-r23-limits](validation/refresh-final-r23-limits.txt) |
| `python3 -B scripts/t03-foundation.py certify capabilities/evidence/foundation/T83-final-r20/worker --obligation worker` | PASS (exit 0) | 1202.524 | [refresh-final-r23-worker](validation/refresh-final-r23-worker.txt) |
| `python3 -B scripts/t03-foundation.py certify capabilities/evidence/foundation/T83-final-r20/recovery --obligation recovery` | PASS (exit 0) | 3058.522 | [refresh-final-r23-recovery](validation/refresh-final-r23-recovery.txt) |

Full verification reports 1677 cases (1673 executed), zero failures/errors,
and four ordinary skips: three opt-in scale cases and one unrelated T30 case.
All eight Worker exit regressions and all seven new timing regressions execute
without skips. Ordinary opt-in skips do not qualify certification.

| Matrix JDK | Reported / executed cases | Failures / errors | Ordinary skips |
| --- | --- | --- | --- |
| 8 | 1677 / 1673 | 0 / 0 | 4 |
| 11 | 1677 / 1673 | 0 / 0 | 4 |
| 17 | 1677 / 1673 | 0 / 0 | 4 |
| 21 | 1677 / 1673 | 0 / 0 | 4 |

Every matrix reactor executes the eight Worker exit and seven timing regressions.
The [full counts](validation/full-verify-final-r13-tests.json) and
[matrix counts](validation/jdk-matrix-final-r6-tests.json) bind their original logs.
The [explicit scale ledger](scale-final-r9/results.json) observes all three actual
profiles with zero skips on Temurin `1.8.0_502-b07`, `11.0.32+9`, `17.0.20+8`,
and `21.0.12+8-LTS` in their declared immutable images.

The Worker now grants thirty seconds for child exit after authenticated FINISHED.
Its liveness recheck, cancellation, caller deadline and overall elapsed bound still
apply. Failed waits retain stable safe failures, transaction identity and ordered
receipts, and add bounded elapsed/liveness/exit/process/thread-state diagnostics.
No document content, command line, environment or thread names are recorded.
Delayed success, actual thirty-second expiry, exit 37, cancellation, deadline,
elapsed excess and interruption qualify publication safety, lookup and cleanup
in all four original and four fresh recovery executions.

Workflow elapsed limits, remaining Provider budgets and usage now default to an
explicit injectable `System.nanoTime` ticker. Signed subtraction handles wraparound.
Explicit Clock-only test environments preserve their existing logical-time semantics;
an explicit ticker takes precedence in either setter order. Absolute deadlines and
receipt timestamps keep wall-clock meaning. The Worker initialization decoder rejects
a relative child deadline instead of converting it through `Instant.now`.
The [complete timing audit](timing-audit-r20.md) and
[supplemental shell/helper scan](timing-audit-supplement-r23.md) list the measurements and decisions.

The deterministic [wall-step red](validation/monotonic-wall-step-red-r1.txt) failed
with the original elapsed-time exception. New regressions prove forward wall steps
cannot exhaust elapsed budgets, genuine ticker or real-time excess does, the inclusive
boundary survives wraparound/backward wall steps, and wall deadlines remain effective.
The focused Ubuntu suite passed 271 tests during an observed wall jump: monotonic 
125.950 seconds, wall 
1307.688 seconds, difference 
1181.738 seconds.
Every new gate retains paired durations; Maven/Surefire wall-derived durations remain
unaltered diagnostics. No universal performance or failure-rate guarantee is inferred.

All [failed and superseded attempts](failed-attempts.md) remain intact. Candidate9
and full verifies r10/r11/r12 are superseded failures, with original reports/source
and diagnostic replays retained. The independent wall-step investigation establishes
the product timing defect and the approved fix; those failed gates do not certify
candidate10. The unexecuted R19 draft supplies no evidence. All fourteen
obligations were run fresh for candidate10; no old PASS was relabeled.
R20 collected the first five obligations, then stopped on an independent T13
observer CPU guard during Python version reporting before input inspection.
That partial text scope remained unindexed; all 58,245 originals were preserved
and rehashed. Twenty exact-control diagnostics succeeded under unchanged
soft5s/hard10s CPU limits and exported no certification. Text was then run
in fresh R21 scopes. Seven complete profiles passed; the eighth JDK21 Worker
suite hit its existing 10000ms JUnit watchdog during INITIALIZE/READY.
All 138,077 R21 originals (900,290,576 bytes) remain preserved and rehashed.
The R22 diagnostic completed twenty standalone methods and three ordered
prefixes, then failed prefix four at the same watchdog during a query.
Six planned prefixes never executed. That failed loop exports no certification
and establishes neither the cause nor a failure-rate bound.
The first R22 completion helper stopped before fresh test execution because
its dynamic import omitted the repository scripts directory. Its seventeen
partial files, failed log and unchanged index were preserved separately.
The corrected R23 helper adds that import path. The R23 completion revalidated
seven complete same-candidate original profiles
at their exact original paths and executed the missing eighth tuple fresh.
All eight tuples qualified through the unchanged collector and atomic publisher.
Every test timeout, production policy and independent checker limit remained
unchanged. The remaining eight obligations ran fresh in unused R20 destinations.
The five current-candidate passes remain unaltered in the combined R23 ledger.
The cause of the isolated CPU guard event or delayed Worker responses is not
established by its traceback or the successful diagnostics. See
[failure preservation](failed-text-certification-r20.json) and
[exact-control measurements](t13-cpu-diagnostics-r21/results.json).
The [JUnit failure preservation](failed-text-certification-r21.json),
[failed bounded diagnostic](text-timeout-diagnostics-r22/results.json) and
[complete text preflight](../foundation/T83-final-r23/text/preflight.json)
retain the separate actual outcomes and mixed original/new record identities.
[Completion helper failure](failed-text-completion-r22.json) remains failed.
Candidates6/7/8/9 portable
archives and historical candidate8 references are revalidated. The successful R15
text closeout retains all 157,778 original R14 text files and its disclosed unknown
historical outer exit. Host-fixture and sandbox/prelaunch failures are also disclosed.

Final [Standards](reviews/standards-relaunch-r23.md) and
[Spec](reviews/spec-relaunch-r23.md) reviews have zero unresolved actionable findings.
The supplemental [Standards](reviews/standards-closing-audit-r24.md) and
[Spec](reviews/spec-closing-audit-r24.md) reviews cover the delivery-only
baseline-count correction and unchanged commit controller.
The first closing audit remains failed and preserved: baseline readiness
has thirteen certified prerequisites plus the derived security aggregate.
The corrected audit requires that exact fourteen-row set and its current
SATISFIED statuses; it changes no product or certification input.
The [prefinal ownership audit](changed-file-ownership-prefinal-r25.json) records
actual source/evidence bytes. The closing [final manifest](changed-file-ownership-final-r25.tsv.gz)
and [summary](changed-file-ownership-final-r25.json) also bind this receipt and closing
review notes, with explicit self-exclusion for the manifest and its summary.
The [reviewed commit controller](local-commit-r25.py) verifies the exact bytes
of every staged Git blob against immutable ownership SHA-256/length rows,
including the two self-excluded audit files. The
[real Git negative proof](staged-content-proof-r23.json) rejects an equal-length artifact alteration.
The first local commit controller stopped before creating a commit when
index.lock already existed. Its [failure record](validation/local-commit-r23-failure.json)
preserves the original controller identity and exact error. The reviewed
[resume authority](local-commit-recovery-r25.json) binds the new guard, real-Git
negative proofs and supplemental [Standards](reviews/standards-local-commit-r25.md)
and [Spec](reviews/spec-local-commit-r25.md) conclusions. The staging guard retries
only that specific error for at most ninety monotonic seconds, preserves live
locks, verifies every partial staged blob, and skips only complete verified
groups. It still requires every final staged blob and both self-excluded files
to match the new final manifest before the sole, non-retried commit attempt.
This resolves the review finding about index changes during a commit retry.
The r26 staging guard also checks monotonic expiry before starting each retry,
preserving the last lock error when sleep or descheduling consumes the budget.
The original nine-case r25 proof remains intact; its fresh ten-case r26 successor
also proves that a lock released after expiry cannot permit a late retry.
Renewed ownership classifies paths against the baseline tree, so the admitted
newly staged ticket files retain their original new-file ownership category.
A temporary local
split-index setting reduces index writes and is restored after committing.
Seven existing production classes change; no runtime dependency/build change was added.
Acceptance-only tools/fonts/fixtures/classes remain outside the Worker runtime closure.

Live recovery and every prerequisite are SATISFIED. Overall Foundation remains
NOT READY solely for these unrelated obligations:

- `canvas` (#85): capability composition.canvas.draw-positioned-text is experimental, requires compatible
- `canvas` (#85): missing certified environment ubuntu-24.04-linux-x86-64-jdk8 on composition.canvas.draw-positioned-text
- `canvas` (#85): missing certified environment ubuntu-24.04-linux-x86-64-jdk11 on composition.canvas.draw-positioned-text
- `canvas` (#85): missing certified environment ubuntu-24.04-linux-x86-64-jdk17 on composition.canvas.draw-positioned-text
- `canvas` (#85): missing certified environment ubuntu-24.04-linux-x86-64-jdk21 on composition.canvas.draw-positioned-text
- `canvas` (#85): missing required Facade mapping set for kernel PdfCanvas paths and encoded glyphs
- `canvas` (#85): unresolved retained limitation: The split Canvas graphics surface requires #85/#86 reconciliation; missing required drawing behavior remains blocking.
- `canvas` (#85): unresolved retained limitation: The borrowed-font restrictions must cover the approved Foundation Canvas profile under #85.
- `canvas` (#85): missing certification ubuntu-24.04-linux-x86-64-jdk8/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `canvas` (#85): missing certification ubuntu-24.04-linux-x86-64-jdk8/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `canvas` (#85): missing certification ubuntu-24.04-linux-x86-64-jdk11/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `canvas` (#85): missing certification ubuntu-24.04-linux-x86-64-jdk11/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `canvas` (#85): missing certification ubuntu-24.04-linux-x86-64-jdk17/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `canvas` (#85): missing certification ubuntu-24.04-linux-x86-64-jdk17/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `canvas` (#85): missing certification ubuntu-24.04-linux-x86-64-jdk21/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `canvas` (#85): missing certification ubuntu-24.04-linux-x86-64-jdk21/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `graphics` (#86): capability composition.canvas.images-colors-transparency is experimental, requires compatible
- `graphics` (#86): incompatible Dependency Gate composition.canvas.draw-positioned-text
- `graphics` (#86): missing certified environment ubuntu-24.04-linux-x86-64-jdk8 on composition.canvas.images-colors-transparency
- `graphics` (#86): missing certified environment ubuntu-24.04-linux-x86-64-jdk11 on composition.canvas.images-colors-transparency
- `graphics` (#86): missing certified environment ubuntu-24.04-linux-x86-64-jdk17 on composition.canvas.images-colors-transparency
- `graphics` (#86): missing certified environment ubuntu-24.04-linux-x86-64-jdk21 on composition.canvas.images-colors-transparency
- `graphics` (#86): missing required Facade mapping set for image, color and transparency graphics
- `graphics` (#86): unresolved retained limitation: Image normalization and precision/color restrictions require #86 evidence for every promised image profile.
- `graphics` (#86): unresolved retained limitation: Borrowed-image filter restrictions require #86 reconciliation with required Foundation graphics.
- `graphics` (#86): unresolved retained limitation: Required graphics Facade and certification are still absent; downstream non-goals do not waive them.
- `graphics` (#86): missing certification ubuntu-24.04-linux-x86-64-jdk8/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `graphics` (#86): missing certification ubuntu-24.04-linux-x86-64-jdk8/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `graphics` (#86): missing certification ubuntu-24.04-linux-x86-64-jdk11/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `graphics` (#86): missing certification ubuntu-24.04-linux-x86-64-jdk11/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `graphics` (#86): missing certification ubuntu-24.04-linux-x86-64-jdk17/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `graphics` (#86): missing certification ubuntu-24.04-linux-x86-64-jdk17/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `graphics` (#86): missing certification ubuntu-24.04-linux-x86-64-jdk21/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `graphics` (#86): missing certification ubuntu-24.04-linux-x86-64-jdk21/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `graphics` (#86): incomplete prerequisite obligation canvas
- `fonts` (#87): capability composition.fonts.load-embed-subset-fallback is experimental, requires compatible
- `fonts` (#87): incompatible Dependency Gate composition.canvas.draw-positioned-text
- `fonts` (#87): missing certified environment ubuntu-24.04-linux-x86-64-jdk8 on composition.fonts.load-embed-subset-fallback
- `fonts` (#87): missing certified environment ubuntu-24.04-linux-x86-64-jdk11 on composition.fonts.load-embed-subset-fallback
- `fonts` (#87): missing certified environment ubuntu-24.04-linux-x86-64-jdk17 on composition.fonts.load-embed-subset-fallback
- `fonts` (#87): missing certified environment ubuntu-24.04-linux-x86-64-jdk21 on composition.fonts.load-embed-subset-fallback
- `fonts` (#87): missing required Facade mapping set for font factory, font and positioned text
- `fonts` (#87): unresolved retained limitation: Positioned-text clipping restrictions require #87 reconciliation with the required text/Canvas families.
- `fonts` (#87): unresolved retained limitation: Admitted static font formats and metadata restrictions require #87 certification for all Foundation reference fonts.
- `fonts` (#87): unresolved retained limitation: Scalar/glyph ambiguity restrictions and the shaping split require #87/#93 evidence for required multilingual behavior.
- `fonts` (#87): unresolved retained limitation: Required font Facade and certification are absent; later-release exclusions cannot waive them.
- `fonts` (#87): missing certification ubuntu-24.04-linux-x86-64-jdk8/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `fonts` (#87): missing certification ubuntu-24.04-linux-x86-64-jdk8/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `fonts` (#87): missing certification ubuntu-24.04-linux-x86-64-jdk11/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `fonts` (#87): missing certification ubuntu-24.04-linux-x86-64-jdk11/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `fonts` (#87): missing certification ubuntu-24.04-linux-x86-64-jdk17/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `fonts` (#87): missing certification ubuntu-24.04-linux-x86-64-jdk17/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `fonts` (#87): missing certification ubuntu-24.04-linux-x86-64-jdk21/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `fonts` (#87): missing certification ubuntu-24.04-linux-x86-64-jdk21/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `fonts` (#87): incomplete prerequisite obligation canvas
- `rendering` (#88): capability conversion.rendering is experimental, requires compatible
- `rendering` (#88): incompatible Dependency Gate conversion.capability-provider.select-execute
- `rendering` (#88): incompatible Dependency Gate composition.canvas.images-colors-transparency
- `rendering` (#88): incompatible Dependency Gate composition.fonts.load-embed-subset-fallback
- `rendering` (#88): missing certified environment ubuntu-24.04-linux-x86-64-jdk8 on conversion.rendering
- `rendering` (#88): missing certified environment ubuntu-24.04-linux-x86-64-jdk11 on conversion.rendering
- `rendering` (#88): missing certified environment ubuntu-24.04-linux-x86-64-jdk17 on conversion.rendering
- `rendering` (#88): missing certified environment ubuntu-24.04-linux-x86-64-jdk21 on conversion.rendering
- `rendering` (#88): missing required Facade mapping set for pdfRender page rendering
- `rendering` (#88): unresolved retained limitation: Annotation rendering restrictions require #88 evidence against the Foundation Rendering profile.
- `rendering` (#88): unresolved retained limitation: Font substitution and platform-codec diagnostics are not compatibility evidence; #88 must certify required font/image outcomes.
- `rendering` (#88): unresolved retained limitation: Independent rendering option coverage, standards/semantic chains and Facade mappings remain incomplete under #88.
- `rendering` (#88): missing certification ubuntu-24.04-linux-x86-64-jdk8/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `rendering` (#88): missing certification ubuntu-24.04-linux-x86-64-jdk8/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `rendering` (#88): missing certification ubuntu-24.04-linux-x86-64-jdk11/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `rendering` (#88): missing certification ubuntu-24.04-linux-x86-64-jdk11/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `rendering` (#88): missing certification ubuntu-24.04-linux-x86-64-jdk17/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `rendering` (#88): missing certification ubuntu-24.04-linux-x86-64-jdk17/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `rendering` (#88): missing certification ubuntu-24.04-linux-x86-64-jdk21/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `rendering` (#88): missing certification ubuntu-24.04-linux-x86-64-jdk21/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `rendering` (#88): incomplete prerequisite obligation providers
- `rendering` (#88): incomplete prerequisite obligation graphics
- `rendering` (#88): incomplete prerequisite obligation fonts
- `providers` (#84): capability conversion.capability-provider.select-execute is experimental, requires compatible
- `providers` (#84): missing certified environment ubuntu-24.04-linux-x86-64-jdk8 on conversion.capability-provider.select-execute
- `providers` (#84): missing certified environment ubuntu-24.04-linux-x86-64-jdk11 on conversion.capability-provider.select-execute
- `providers` (#84): missing certified environment ubuntu-24.04-linux-x86-64-jdk17 on conversion.capability-provider.select-execute
- `providers` (#84): missing certified environment ubuntu-24.04-linux-x86-64-jdk21 on conversion.capability-provider.select-execute
- `providers` (#84): missing certification ubuntu-24.04-linux-x86-64-jdk8/IN_PROCESS (required chains: syntax, standards, semantic, visual, contract)
- `providers` (#84): missing certification ubuntu-24.04-linux-x86-64-jdk8/HARDENED_WORKER (required chains: syntax, standards, semantic, visual, contract)
- `providers` (#84): missing certification ubuntu-24.04-linux-x86-64-jdk11/IN_PROCESS (required chains: syntax, standards, semantic, visual, contract)
- `providers` (#84): missing certification ubuntu-24.04-linux-x86-64-jdk11/HARDENED_WORKER (required chains: syntax, standards, semantic, visual, contract)
- `providers` (#84): missing certification ubuntu-24.04-linux-x86-64-jdk17/IN_PROCESS (required chains: syntax, standards, semantic, visual, contract)
- `providers` (#84): missing certification ubuntu-24.04-linux-x86-64-jdk17/HARDENED_WORKER (required chains: syntax, standards, semantic, visual, contract)
- `providers` (#84): missing certification ubuntu-24.04-linux-x86-64-jdk21/IN_PROCESS (required chains: syntax, standards, semantic, visual, contract)
- `providers` (#84): missing certification ubuntu-24.04-linux-x86-64-jdk21/HARDENED_WORKER (required chains: syntax, standards, semantic, visual, contract)
- `paragraphs` (#89): capability composition.layout.paragraph-areas is experimental, requires compatible
- `paragraphs` (#89): incompatible Dependency Gate composition.canvas.draw-positioned-text
- `paragraphs` (#89): incompatible Dependency Gate composition.canvas.images-colors-transparency
- `paragraphs` (#89): incompatible Dependency Gate composition.fonts.load-embed-subset-fallback
- `paragraphs` (#89): missing certified environment ubuntu-24.04-linux-x86-64-jdk8 on composition.layout.paragraph-areas
- `paragraphs` (#89): missing certified environment ubuntu-24.04-linux-x86-64-jdk11 on composition.layout.paragraph-areas
- `paragraphs` (#89): missing certified environment ubuntu-24.04-linux-x86-64-jdk17 on composition.layout.paragraph-areas
- `paragraphs` (#89): missing certified environment ubuntu-24.04-linux-x86-64-jdk21 on composition.layout.paragraph-areas
- `paragraphs` (#89): missing required Facade mapping set for layout Document, Paragraph, Text and AreaBreak
- `paragraphs` (#89): unresolved retained limitation: The full pinned Foundation font/typography profile remains uncertified under #87/#89/#93; four-platform wording is superseded only by ADR-0040.
- `paragraphs` (#89): unresolved retained limitation: Unicode/shaping/hyphenation/vertical-text restrictions must be reconciled with full required Foundation paragraph behavior under #89/#93.
- `paragraphs` (#89): unresolved retained limitation: Required advanced paragraphs/tables and migration mappings remain separate blocking obligations.
- `paragraphs` (#89): missing certification ubuntu-24.04-linux-x86-64-jdk8/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `paragraphs` (#89): missing certification ubuntu-24.04-linux-x86-64-jdk8/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `paragraphs` (#89): missing certification ubuntu-24.04-linux-x86-64-jdk11/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `paragraphs` (#89): missing certification ubuntu-24.04-linux-x86-64-jdk11/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `paragraphs` (#89): missing certification ubuntu-24.04-linux-x86-64-jdk17/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `paragraphs` (#89): missing certification ubuntu-24.04-linux-x86-64-jdk17/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `paragraphs` (#89): missing certification ubuntu-24.04-linux-x86-64-jdk21/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `paragraphs` (#89): missing certification ubuntu-24.04-linux-x86-64-jdk21/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `paragraphs` (#89): incomplete prerequisite obligation canvas
- `paragraphs` (#89): incomplete prerequisite obligation graphics
- `paragraphs` (#89): incomplete prerequisite obligation fonts
- `pagination` (#90): capability composition.layout.paragraph-pagination is experimental, requires compatible
- `pagination` (#90): incompatible Dependency Gate composition.layout.paragraph-areas
- `pagination` (#90): incompatible Dependency Gate composition.fonts.load-embed-subset-fallback
- `pagination` (#90): incompatible Dependency Gate composition.canvas.images-colors-transparency
- `pagination` (#90): missing certified environment ubuntu-24.04-linux-x86-64-jdk8 on composition.layout.paragraph-pagination
- `pagination` (#90): missing certified environment ubuntu-24.04-linux-x86-64-jdk11 on composition.layout.paragraph-pagination
- `pagination` (#90): missing certified environment ubuntu-24.04-linux-x86-64-jdk17 on composition.layout.paragraph-pagination
- `pagination` (#90): missing certified environment ubuntu-24.04-linux-x86-64-jdk21 on composition.layout.paragraph-pagination
- `pagination` (#90): missing required Facade mapping set for layout paragraph properties, relayout and flushing
- `pagination` (#90): unresolved retained limitation: Tab variants and excluded leaders require #90 audit against the complete Foundation paragraph contract.
- `pagination` (#90): unresolved retained limitation: Small-font independent pages do not certify all required Foundation reference fonts/environments under #90.
- `pagination` (#90): unresolved retained limitation: Required standards, compatible dependencies and paragraph Facade remain incomplete under #90.
- `pagination` (#90): missing certification ubuntu-24.04-linux-x86-64-jdk8/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `pagination` (#90): missing certification ubuntu-24.04-linux-x86-64-jdk8/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `pagination` (#90): missing certification ubuntu-24.04-linux-x86-64-jdk11/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `pagination` (#90): missing certification ubuntu-24.04-linux-x86-64-jdk11/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `pagination` (#90): missing certification ubuntu-24.04-linux-x86-64-jdk17/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `pagination` (#90): missing certification ubuntu-24.04-linux-x86-64-jdk17/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `pagination` (#90): missing certification ubuntu-24.04-linux-x86-64-jdk21/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `pagination` (#90): missing certification ubuntu-24.04-linux-x86-64-jdk21/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `pagination` (#90): incomplete prerequisite obligation paragraphs
- `pagination` (#90): incomplete prerequisite obligation fonts
- `pagination` (#90): incomplete prerequisite obligation graphics
- `tables` (#92): capability composition.layout.tables is experimental, requires compatible
- `tables` (#92): incompatible Dependency Gate composition.layout.paragraph-areas
- `tables` (#92): incompatible Dependency Gate composition.layout.paragraph-pagination
- `tables` (#92): incompatible Dependency Gate composition.fonts.load-embed-subset-fallback
- `tables` (#92): incompatible Dependency Gate composition.canvas.images-colors-transparency
- `tables` (#92): incomplete prerequisite obligation paragraphs
- `tables` (#92): incomplete prerequisite obligation pagination
- `tables` (#92): incomplete prerequisite obligation fonts
- `tables` (#92): incomplete prerequisite obligation graphics
- `tables` (#92): incomplete prerequisite obligation tables-base
- `tables` (#92): incomplete prerequisite obligation tables-pagination
- `shaping` (#93): capability composition.shaping.harf-buzz is experimental, requires compatible
- `shaping` (#93): incompatible Dependency Gate conversion.capability-provider.select-execute
- `shaping` (#93): incompatible Dependency Gate composition.fonts.load-embed-subset-fallback
- `shaping` (#93): incompatible Dependency Gate composition.layout.paragraph-areas
- `shaping` (#93): incompatible Dependency Gate composition.layout.paragraph-pagination
- `shaping` (#93): incompatible Dependency Gate composition.layout.tables
- `shaping` (#93): missing certified environment ubuntu-24.04-linux-x86-64-jdk8 on composition.shaping.harf-buzz
- `shaping` (#93): missing certified environment ubuntu-24.04-linux-x86-64-jdk11 on composition.shaping.harf-buzz
- `shaping` (#93): missing certified environment ubuntu-24.04-linux-x86-64-jdk17 on composition.shaping.harf-buzz
- `shaping` (#93): missing certified environment ubuntu-24.04-linux-x86-64-jdk21 on composition.shaping.harf-buzz
- `shaping` (#93): missing required Facade mapping set for layout typography and explicit shaping configuration
- `shaping` (#93): unresolved retained limitation: Admitted shaping/font/feature profiles require #93 evidence for every required representative writing system.
- `shaping` (#93): unresolved retained limitation: Cross-style grapheme/run restrictions must be reconciled with required Foundation shaping under #93.
- `shaping` (#93): unresolved retained limitation: Parent-side native resource limits and required Linux observations remain #93 responsibilities; Windows/macOS gates are removed by ADR-0040.
- `shaping` (#93): unresolved retained limitation: Missing actual required Ubuntu/JDK native observations still block #93; Windows/macOS absence is explicitly not a Foundation 0.1.0 blocker.
- `shaping` (#93): missing certification ubuntu-24.04-linux-x86-64-jdk8/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `shaping` (#93): missing certification ubuntu-24.04-linux-x86-64-jdk8/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `shaping` (#93): missing certification ubuntu-24.04-linux-x86-64-jdk11/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `shaping` (#93): missing certification ubuntu-24.04-linux-x86-64-jdk11/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `shaping` (#93): missing certification ubuntu-24.04-linux-x86-64-jdk17/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `shaping` (#93): missing certification ubuntu-24.04-linux-x86-64-jdk17/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `shaping` (#93): missing certification ubuntu-24.04-linux-x86-64-jdk21/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `shaping` (#93): missing certification ubuntu-24.04-linux-x86-64-jdk21/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `shaping` (#93): incomplete prerequisite obligation providers
- `shaping` (#93): incomplete prerequisite obligation fonts
- `shaping` (#93): incomplete prerequisite obligation paragraphs
- `shaping` (#93): incomplete prerequisite obligation pagination
- `shaping` (#93): incomplete prerequisite obligation tables
- `barcodes-1d` (#94): capability composition.barcodes.one-dimensional is experimental, requires compatible
- `barcodes-1d` (#94): incompatible Dependency Gate composition.canvas.draw-positioned-text
- `barcodes-1d` (#94): incompatible Dependency Gate composition.canvas.images-colors-transparency
- `barcodes-1d` (#94): incompatible Dependency Gate composition.fonts.load-embed-subset-fallback
- `barcodes-1d` (#94): missing certified environment ubuntu-24.04-linux-x86-64-jdk8 on composition.barcodes.one-dimensional
- `barcodes-1d` (#94): missing certified environment ubuntu-24.04-linux-x86-64-jdk11 on composition.barcodes.one-dimensional
- `barcodes-1d` (#94): missing certified environment ubuntu-24.04-linux-x86-64-jdk17 on composition.barcodes.one-dimensional
- `barcodes-1d` (#94): missing certified environment ubuntu-24.04-linux-x86-64-jdk21 on composition.barcodes.one-dimensional
- `barcodes-1d` (#94): missing required Facade mapping set for barcodes one-dimensional generation modes
- `barcodes-1d` (#94): unresolved retained limitation: All 116 cases require final candidate/environment evidence and compatible dependencies under #94.
- `barcodes-1d` (#94): unresolved retained limitation: Independent standards, required environment/font evidence and matching 1D Facade remain incomplete.
- `barcodes-1d` (#94): missing certification ubuntu-24.04-linux-x86-64-jdk8/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `barcodes-1d` (#94): missing certification ubuntu-24.04-linux-x86-64-jdk8/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `barcodes-1d` (#94): missing certification ubuntu-24.04-linux-x86-64-jdk11/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `barcodes-1d` (#94): missing certification ubuntu-24.04-linux-x86-64-jdk11/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `barcodes-1d` (#94): missing certification ubuntu-24.04-linux-x86-64-jdk17/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `barcodes-1d` (#94): missing certification ubuntu-24.04-linux-x86-64-jdk17/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `barcodes-1d` (#94): missing certification ubuntu-24.04-linux-x86-64-jdk21/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `barcodes-1d` (#94): missing certification ubuntu-24.04-linux-x86-64-jdk21/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `barcodes-1d` (#94): incomplete prerequisite obligation canvas
- `barcodes-1d` (#94): incomplete prerequisite obligation graphics
- `barcodes-1d` (#94): incomplete prerequisite obligation fonts
- `barcodes-2d` (#95): capability composition.barcodes.two-dimensional is experimental, requires compatible
- `barcodes-2d` (#95): incompatible Dependency Gate composition.canvas.draw-positioned-text
- `barcodes-2d` (#95): incompatible Dependency Gate composition.canvas.images-colors-transparency
- `barcodes-2d` (#95): missing certified environment ubuntu-24.04-linux-x86-64-jdk8 on composition.barcodes.two-dimensional
- `barcodes-2d` (#95): missing certified environment ubuntu-24.04-linux-x86-64-jdk11 on composition.barcodes.two-dimensional
- `barcodes-2d` (#95): missing certified environment ubuntu-24.04-linux-x86-64-jdk17 on composition.barcodes.two-dimensional
- `barcodes-2d` (#95): missing certified environment ubuntu-24.04-linux-x86-64-jdk21 on composition.barcodes.two-dimensional
- `barcodes-2d` (#95): missing required Facade mapping set for barcodes QR, DataMatrix and PDF417 modes
- `barcodes-2d` (#95): unresolved retained limitation: Typed control/file-ID migration differences need exact #95 Facade and semantic evidence.
- `barcodes-2d` (#95): unresolved retained limitation: Every Reference Suite PDF417 generation mode must be reconciled under #95; raster-only inversion and other exclusions cannot hide required vector modes.
- `barcodes-2d` (#95): unresolved retained limitation: The complete 224-page-per-mode corpus and damaged controls need final candidate/environment evidence under #95.
- `barcodes-2d` (#95): unresolved retained limitation: Independent standards, compatible dependencies, required environments and 2D Facade remain incomplete.
- `barcodes-2d` (#95): missing certification ubuntu-24.04-linux-x86-64-jdk8/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `barcodes-2d` (#95): missing certification ubuntu-24.04-linux-x86-64-jdk8/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `barcodes-2d` (#95): missing certification ubuntu-24.04-linux-x86-64-jdk11/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `barcodes-2d` (#95): missing certification ubuntu-24.04-linux-x86-64-jdk11/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `barcodes-2d` (#95): missing certification ubuntu-24.04-linux-x86-64-jdk17/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `barcodes-2d` (#95): missing certification ubuntu-24.04-linux-x86-64-jdk17/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `barcodes-2d` (#95): missing certification ubuntu-24.04-linux-x86-64-jdk21/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `barcodes-2d` (#95): missing certification ubuntu-24.04-linux-x86-64-jdk21/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `barcodes-2d` (#95): incomplete prerequisite obligation canvas
- `barcodes-2d` (#95): incomplete prerequisite obligation graphics
- `tables-base` (#91): missing independently certifiable subcapability composition.layout.tables.base (aggregate composition.layout.tables)
- `tables-base` (#91): missing required Facade mapping set for layout Table and Cell properties and lifecycle
- `tables-base` (#91): unresolved retained limitation: AUTO allocation restrictions require #91 proof against full required fixed/automatic table behavior.
- `tables-base` (#91): unresolved retained limitation: Cell-content and nesting restrictions require #91 reconciliation with the Foundation table profile.
- `tables-base` (#91): missing certification ubuntu-24.04-linux-x86-64-jdk8/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `tables-base` (#91): missing certification ubuntu-24.04-linux-x86-64-jdk8/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `tables-base` (#91): missing certification ubuntu-24.04-linux-x86-64-jdk11/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `tables-base` (#91): missing certification ubuntu-24.04-linux-x86-64-jdk11/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `tables-base` (#91): missing certification ubuntu-24.04-linux-x86-64-jdk17/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `tables-base` (#91): missing certification ubuntu-24.04-linux-x86-64-jdk17/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `tables-base` (#91): missing certification ubuntu-24.04-linux-x86-64-jdk21/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `tables-base` (#91): missing certification ubuntu-24.04-linux-x86-64-jdk21/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `tables-base` (#91): incomplete prerequisite obligation pagination
- `tables-base` (#91): incomplete prerequisite obligation fonts
- `tables-base` (#91): incomplete prerequisite obligation graphics
- `tables-pagination` (#92): missing independently certifiable subcapability composition.layout.tables.pagination (aggregate composition.layout.tables)
- `tables-pagination` (#92): missing required Facade mapping set for layout Table and Cell properties and lifecycle
- `tables-pagination` (#92): unresolved retained limitation: Restricted incremental large-table mode must cover every required Foundation flushing case under #92.
- `tables-pagination` (#92): unresolved retained limitation: T26/T27 evidence must be retained for the two independent subcapabilities on final required profiles.
- `tables-pagination` (#92): unresolved retained limitation: Required standards, dependency, Foundation typography/environment and Table/Cell Facade certification remain incomplete.
- `tables-pagination` (#92): missing certification ubuntu-24.04-linux-x86-64-jdk8/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `tables-pagination` (#92): missing certification ubuntu-24.04-linux-x86-64-jdk8/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `tables-pagination` (#92): missing certification ubuntu-24.04-linux-x86-64-jdk11/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `tables-pagination` (#92): missing certification ubuntu-24.04-linux-x86-64-jdk11/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `tables-pagination` (#92): missing certification ubuntu-24.04-linux-x86-64-jdk17/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `tables-pagination` (#92): missing certification ubuntu-24.04-linux-x86-64-jdk17/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `tables-pagination` (#92): missing certification ubuntu-24.04-linux-x86-64-jdk21/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `tables-pagination` (#92): missing certification ubuntu-24.04-linux-x86-64-jdk21/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `tables-pagination` (#92): incomplete prerequisite obligation tables-base
- `tables-pagination` (#92): incomplete prerequisite obligation pagination
- `tables-pagination` (#92): incomplete prerequisite obligation fonts
- `tables-pagination` (#92): incomplete prerequisite obligation graphics
- `environments` (#69): missing certification ubuntu-24.04-linux-x86-64-jdk8/REPOSITORY (required chains: contract)
- `environments` (#69): missing certification ubuntu-24.04-linux-x86-64-jdk11/REPOSITORY (required chains: contract)
- `environments` (#69): missing certification ubuntu-24.04-linux-x86-64-jdk17/REPOSITORY (required chains: contract)
- `environments` (#69): missing certification ubuntu-24.04-linux-x86-64-jdk21/REPOSITORY (required chains: contract)
- `java-artifacts` (#96): missing certification ubuntu-24.04-linux-x86-64-jdk8/REPOSITORY (required chains: contract, artifact)
- `java-artifacts` (#96): missing certification ubuntu-24.04-linux-x86-64-jdk11/REPOSITORY (required chains: contract, artifact)
- `java-artifacts` (#96): missing certification ubuntu-24.04-linux-x86-64-jdk17/REPOSITORY (required chains: contract, artifact)
- `java-artifacts` (#96): missing certification ubuntu-24.04-linux-x86-64-jdk21/REPOSITORY (required chains: contract, artifact)
- `facade-artifacts` (#96): missing certification ubuntu-24.04-linux-x86-64-jdk17/REPOSITORY (required chains: contract, artifact)
- `acceptance` (#70): missing certification ubuntu-24.04-linux-x86-64-jdk17/REPOSITORY (required chains: contract, review)
- `docs` (#96): missing certification ubuntu-24.04-linux-x86-64-jdk17/REPOSITORY (required chains: contract, review)
- `provenance` (#96): missing certification ubuntu-24.04-linux-x86-64-jdk17/REPOSITORY (required chains: review, supply-chain)
- `supply-chain` (#96): missing certification ubuntu-24.04-linux-x86-64-jdk17/REPOSITORY (required chains: supply-chain, review)
- `reproducibility` (#96): missing certification ubuntu-24.04-linux-x86-64-jdk17/REPOSITORY (required chains: reproducibility, artifact, signature)
- `reproducibility` (#96): incomplete prerequisite obligation integration
- `candidate` (#97): missing certification ubuntu-24.04-linux-x86-64-jdk17/REPOSITORY (required chains: artifact, signature, review)
- `candidate` (#97): incomplete prerequisite obligation reproducibility
- `candidate` (#97): incomplete prerequisite obligation publication-controls
- `publication-controls` (#97): missing certification ubuntu-24.04-linux-x86-64-jdk17/REPOSITORY (required chains: contract, review)
- `integration` (#96): missing certification ubuntu-24.04-linux-x86-64-jdk8/REPOSITORY (required chains: contract, review)
- `integration` (#96): missing certification ubuntu-24.04-linux-x86-64-jdk11/REPOSITORY (required chains: contract, review)
- `integration` (#96): missing certification ubuntu-24.04-linux-x86-64-jdk17/REPOSITORY (required chains: contract, review)
- `integration` (#96): missing certification ubuntu-24.04-linux-x86-64-jdk21/REPOSITORY (required chains: contract, review)
- `integration` (#96): incomplete prerequisite obligation canvas
- `integration` (#96): incomplete prerequisite obligation graphics
- `integration` (#96): incomplete prerequisite obligation fonts
- `integration` (#96): incomplete prerequisite obligation rendering
- `integration` (#96): incomplete prerequisite obligation providers
- `integration` (#96): incomplete prerequisite obligation paragraphs
- `integration` (#96): incomplete prerequisite obligation pagination
- `integration` (#96): incomplete prerequisite obligation tables
- `integration` (#96): incomplete prerequisite obligation shaping
- `integration` (#96): incomplete prerequisite obligation barcodes-1d
- `integration` (#96): incomplete prerequisite obligation barcodes-2d
- `integration` (#96): incomplete prerequisite obligation tables-base
- `integration` (#96): incomplete prerequisite obligation tables-pagination
- `integration` (#96): incomplete prerequisite obligation environments
- `integration` (#96): incomplete prerequisite obligation java-artifacts
- `integration` (#96): incomplete prerequisite obligation facade-artifacts
- `integration` (#96): incomplete prerequisite obligation acceptance
- `integration` (#96): incomplete prerequisite obligation docs
- `integration` (#96): incomplete prerequisite obligation provenance
- `integration` (#96): incomplete prerequisite obligation supply-chain

Residual limits: environment-local in-memory caller-driven recovery, no automatic
retry, durable/distributed identity or cross-Target atomicity. Memory/storage figures
model Folio-owned allocations rather than JVM heap, RSS, native memory, kernel/container
resources or physical erasure. Semantic observations cover all actual pages; visual
qualification samples pages 1/2500/5000 and every actual one-page product. Standards
coverage is limited to the qualified blank-document predicates. Windows/macOS and
parent/downstream slices, including #88, remain outside this certification.
No push, PR, merge, tracker edit, issue closure, release publication or external
message was performed. Push, publication and closure remain with the orchestrator.
