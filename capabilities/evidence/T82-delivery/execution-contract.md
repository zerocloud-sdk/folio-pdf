**Readiness checklist — compiler evidence, outside the execution block**

- [x] Approved source is agent-ready: live #82, its comments, #1, #33, repository contracts and relevant ADRs were read.
- [x] Selected work is unblocked: GitHub’s native dependency relationship confirms #81 is CLOSED.
- [x] Exactly one bounded frontier is selected: #82.
- [x] Pre-implementation HEAD is recorded.
- [x] Every acceptance criterion is classified below.
- [x] Validation commands were discovered from repository scripts, POMs, CI, documentation and existing tests.
- [x] User permissions, workspace boundaries, publication restrictions and DCO requirements are preserved.
- [x] Completion is decidable from retained observations, validation results, inventory state and baseline-relative review.

```markdown
## Goal

Complete GitHub issue #82, “[T32.14] Certify the Hardened Worker boundary on the declared Linux profiles,” in `zerocloud-sdk/folio-pdf`.

Certify capability `document.hardened-worker`, Acceptance Profile `T21-hardened-worker`, and Foundation obligation `worker` on the actual declared Ubuntu 24.04 / Linux x86-64 environments with JDK 8, 11, 17 and 21. Reuse the existing Native implementation and independent acceptance infrastructure. Complete the missing certification, documentation and any behavior gaps demonstrated by that certification.

Deliver the reviewed worktree, retained evidence and an execution receipt for parent-orchestrator review. The parent handles publication.

Contract destination: `/workspace/contracts/issue-82-contract.md`. The orchestrator saves the compiler’s final response there. This compilation session is read-only: it does not implement, commit, create branches or modify repository files.

## Current state

- Repository: `/workspace/folio-pdf`.
- Branch: `main`, tracking `origin/main`.
- HEAD (comparison baseline): `df2df726a2695267bf63a7c9df49a4a7481f0169`.
- Latest commit: `feat: certify trusted in-process hostile-input limits (#81)`.
- Dirty / untracked files to protect: none observed; staged and tracked diffs were empty. Final read-only status and diff checks confirmed the same HEAD and clean worktree. Recheck at execution entry and protect subsequent unrelated work and existing ignored caches.
- DCO identity for any later authorized local commit: `mabaiqiu <mabaiqiu@gmail.com>`.
- Live issue: #82 is OPEN, with no comments. Its sole native GitHub blocker is #81, which is CLOSED as completed.
- Scope amendment: Foundation 0.1.0 requires only the declared, actually exercised Ubuntu 24.04/Linux x86-64 environments. Windows x86-64 and macOS x86-64/arm64 remain explicitly uncertified and are not release blockers for this version.

Evidenced complete:

- `HardenedWorkerEngine`, `HardenedWorkerMain`, `WorkerProtocol`, the closed codecs and `HardenedWorkerSecurityManager` already implement the local process boundary.
- `WorkflowExecutionProfileContractTest` applies the same public contract to IN_PROCESS and HARDENED_WORKER.
- Existing Worker tests cover public ordering, query barriers, ownership, publication, safe failures, real-process isolation, hostile framing, aggregate transport accounting, hard elapsed termination and crash injection. Their presence is implementation evidence; fresh T21 certification remains required.
- The launcher uses absolute Java and `/usr/bin/prlimit`, clears the child environment, restricts the runtime classpath, applies process/JVM ceilings and installs the Worker Security Manager.
- Foundation `worker` already declares four HARDENED_WORKER environment tuples, five chains—syntax, standards, semantic, visual and contract—and dependencies `transactions` and `limits`.
- Foundation and Facade authorities already justify Native-Interface-only Worker controls because authenticated transport, launch policy and ordering controls have no Reference Suite counterpart. Underlying document behavior retains its matching Facade obligations.
- The current Foundation index contains twelve previously certified obligations: transactions, values, pages, metadata, annotations, text, images, incremental, password-baseline, password-clear-metadata, password-attachments and limits. There are 92 certifications and 372 chain records; limits has four IN_PROCESS tuples, and the other eleven have eight Native environment/profile tuples each.
- Retained #81 validation reports full Ubuntu verification passing. These records establish predecessor evidence, not completion of #82.
- Java 8 compatibility, explicit resource ownership, clean-room licensing and separation of acceptance-only material from product runtime are established baseline contracts.

Acceptance-criterion classification:

| Issue criterion | Classification at baseline | Evidence / remaining gap |
| --- | --- | --- |
| AC1: shared Document Workflow contract in both profiles on declared Linux/JDK environments with pinned artifacts and launcher | Demonstrably incomplete for T21 | Shared tests and predecessor production-artifact observations exist; T21 lacks its complete launcher-bound certification. |
| AC2: authenticated bounded protocol, ordering, rejection, termination and access restrictions | Demonstrably incomplete as certification | Implementation and real-process tests exist, but no independent T21 contract chain is indexed. |
| AC3: failure/cancellation/crash diagnostics, receipts and cleanup | Unverified as a complete T21 obligation | Public lifecycle and real-process fault tests express substantial coverage; fresh complete observations are required. |
| AC4: actual prerequisites and WORKER_UNAVAILABLE in unsupported environments | Demonstrably incomplete | Prerequisites are documented, but classpath authority documentation is stale and complete prerequisite-failure certification is absent. |
| AC5: artifact/environment-bound independent chains and negative controls | Demonstrably incomplete | The Foundation index contains zero worker certifications; capability Acceptance Evidence and certified platforms are empty. |
| AC6: matching public Native/Facade behavior at the highest available seam | Unverified for T21 | Existing paired document observations and the Native-only control decision exist; complete slice-specific evidence remains required. |
| AC7: coordinated inventories, English contracts, Chinese material and provenance | Demonstrably incomplete | Worker capability/profile remain experimental; T21 narrative and launcher tables need current certification updates. |
| AC8: smallest checks, full verification, conditional JDK matrix and Standards/Spec review | Demonstrably incomplete for this execution | Prior gates passed; #82 needs final validation and reviews against the recorded baseline. |
| AC9: Java 8, ownership, clean-room licensing and non-goals | Evidenced complete at baseline | Preserve these invariants in all changes; final validation must establish no regression. |

Known gaps:

- `document.hardened-worker` and its profile remain experimental, with empty `acceptance-evidence` and `certified-platforms`.
- `capabilities/evidence/T21-hardened-worker.md` explicitly records implementation evidence and absent independent chains. Its statements about open predecessor gates are historical.
- `scripts/t03-foundation.py` has no `worker` certification case or collector. At baseline, `--obligation worker` is unsupported.
- The Worker guide lists outdated first-party inventory identities. Actual baseline resources and launcher constants agree on:
  - document inventory: 754 entries, SHA-256 `99cba401304fe7d1bbc279f8afd1cbac30bf6dc0609e2747966c276b51933297`;
  - Provider contract inventory: 25 entries, SHA-256 `56340dc06714414d32db2af86d87db696cbe05de93b4ece4571bb3b412a76f16`.
  The guide instead lists 722 and 20 entries with older hashes. Its runtime dependency description must also match the actual enforced closure, including ICU4J and OkapiBarcode.
- Candidate and contract identities cover broad source/documentation authorities. Changes for this ticket invalidate affected predecessor certifications; old records cannot be retained merely by replacing hashes or labels.
- Current shell prerequisites are incomplete: Podman is available, but Java is absent from PATH, host Python lacks PyYAML, and the Foundation candidate/build receipt is not staged. Retained Foundation Python and HarfBuzz installations exist under `.build-cache`; validate them before use.

Existing failures and limits:

- No new product failure was established by this read-only compilation. Tests and certification were inspected, not rerun.
- #81’s review records intermittent Worker child-exit/time-out failures during predecessor refreshes. Diagnose applicable failures encountered here and retain original attempts; do not treat rerunning until success as proof of correctness.
- Overall Foundation readiness remains NOT READY because later obligations are unfinished.
- `document.hardened-worker.recovery-scale` / obligation `recovery` is separate #83 work and remains experimental.

## Execution order

1. Recheck instructions, branch, HEAD, status and dependency state. Record the execution-entry worktree and confirm this baseline remains applicable. Establish a working JDK/Python build environment and verify the declared immutable images, existing tools and native installation. Preserve unrelated work.

2. Freeze a T21 coverage inventory from #82, the current public contracts, Worker profile and applicable limitation classifications. Map each required behavior to an observable experiment, expected outcome, negative control and retained record. Reuse existing public contracts and real-process probes before adding coverage.

3. Implement the missing repository-only Worker recorder and collector. Extend the Foundation runner for the declared four HARDENED_WORKER tuples and five chains. Retain shared IN_PROCESS/HARDENED_WORKER contract execution on every declared JDK. Reject incomplete, skipped, altered, mislabeled or identity-mismatched evidence. Fix product code only when a concrete required behavior fails.

4. Run focused validation during development. Complete paired Native/Facade document observations, actual launch/isolation controls, protocol negatives, termination and cleanup evidence. Update applicable authorities, English contracts, Chinese usage material, provenance and generated documentation together. Correct stale launcher inventory/dependency documentation without weakening its authority.

5. Freeze final sources and contracts, including the proposed completed inventory state, then stage the unsigned local production candidate and acceptance harness. Refresh affected predecessor observations in the established order:
   transactions → values → pages → metadata → annotations → text → images → incremental → password-baseline → password-clear-metadata → password-attachments → limits → worker.
   Reuse a retained certification only when all candidate, contract, environment, configuration and transitive report identities still match. Otherwise obtain fresh observations. Record Worker evidence in fresh ticket-owned directories.

6. Run required final gates and Standards/Spec review against the baseline. Resolve applicable findings. Any subsequent source, contract, artifact, harness, tool or configuration change requires restaging and fresh affected certification. Finish with a receipt mapping all issue criteria to evidence, validation, review results and remaining unrelated release blockers.

## Completion criteria

- [ ] C1 — Shared public Document Workflow behavior passes in IN_PROCESS and HARDENED_WORKER on all four declared Ubuntu/JDK profiles using the staged production artifacts. Retain actual test execution, outcome/profile, public reopen, ownership, lifecycle, progress, failure and Publication Receipt observations.

- [ ] C2 — Mandatory certification tests execute completely with zero failures, ignored tests or assumption skips. Freeze and verify the expected test/case inventory; an aggregate JUnit success count alone is insufficient.

- [ ] C3 — Actual framed Worker experiments prove authentication and fail-closed rejection of missing/wrong keys, tampering, replay, unknown versions/opcodes, malformed/truncated/negative/overflowing lengths, trailing data, prohibited operation selectors and arbitrary serialization. Inclusive message boundaries succeed and first excess is rejected before unbounded payload allocation. Existing sentinel products remain unchanged.

- [ ] C4 — Public workflows prove command declaration order, query barriers, successful batch-prefix visibility, stop-at-first-failure behavior and failure precedence. Later one-shot inputs remain unconsumed after an earlier failure. Preserve both existing batch transport forms without making private-call or batching-shape assertions the behavioral oracle.

- [ ] C5 — Real Worker observations establish a separate child, effective restricted roots, owner-only permissions, denial of unauthorized filesystem reads/writes, cross-transaction access, links, descendant execution, INET networking and Unix-domain networking. Paired permitted-operation controls establish that observed denial comes from the Worker boundary rather than an unavailable service or outer container restriction.

- [ ] C6 — Record actual Java executable/vendor/build, `/usr/bin/prlimit` executable identity, launcher arguments, installed Security Manager, dependency/class-inventory identities and effective resource controls. Demonstrate relevant aggregate modeled-memory and temporary-storage admission plus enforced Worker termination. State accurately the heap/direct-memory, CPU, descriptor, time and ownership guarantees and their limits.

- [ ] C7 — Failure, cancellation, deadline/elapsed-time expiry, malformed protocol and deliberate child crash retain stable safe diagnostics and correct ordered Publication Receipts. Before publication, Targets remain intact. Partial stream publication and committed/unattempted Targets remain distinguishable. Confirm child termination before cleanup and establish no live owned child or owned temporary data remains afterward.

- [ ] C8 — Cleanup failure cannot produce a successful outcome. Preserve checked-primary failure precedence, caller exception identity and committed receipts when cleanup follows publication. Caller-owned resources remain open; module-owned resources and Session views obey their declared lifetimes.

- [ ] C9 — Controlled unsupported-launch/prerequisite cases report `WORKER_UNAVAILABLE`, preserve Targets and clean owned resources. Documentation identifies the real Linux launcher, Security Manager and resource-enforcement prerequisites. Unsupported OS/JDK/artifact configurations receive no inferred certification or artificial Reference Suite Facade.

- [ ] C10 — Matching document behavior is observed through the public Native Interface and existing public Migration Facade, with reopened products, receipts and safe failures. Keep the justified Native-only Worker-control decision. Record the Facade’s actual IN_PROCESS execution; do not relabel it as HARDENED_WORKER or add an unsupported Worker-control mapping.

- [ ] C11 — Each of the four required Worker certifications has separate passing syntax, standards, semantic, visual and contract records, with qualified producers, raw findings and detected negative controls. Use existing qualified independent PDF profiles wherever their rules fit the produced outcomes. qpdf syntax does not establish standards compliance; PDFBox does not serve as the independent visual oracle.

- [ ] C12 — Evidence binds the actual candidate, contract, OS image, JDK, launcher, tools, corpus, policies, configuration and every transitive report/control by their observed identities. Missing tools, rules, cases, controls or mismatched identities cannot become PASS through relabeling. Collection detects altered supporting data and does not trust a summary PASS.

- [ ] C13 — `capabilities/foundation-evidence.yaml` retains current Worker evidence and valid predecessor certifications. Live readiness reports `worker`, `transactions` and `limits` satisfied, and this change does not invalidate previously satisfied obligations. Overall readiness may remain nonzero only for explicitly identified unrelated obligations.

- [ ] C14 — Promote only the completed, dependency-satisfied `document.hardened-worker` capability/profile. Its certified environments are exactly those actually exercised. Recovery/scale and other downstream capabilities retain their prior scope/status. Windows and macOS remain explicitly uncertified.

- [ ] C15 — Update the applicable Capability Matrix, Facade Surface exclusion rationale, profile narrative, Foundation evidence/traceability, generated public reports, English Worker contract/README, Chinese usage material and `PROVENANCE.md` together. Correct documented inventory counts, hashes, runtime closure and prerequisites to the final tested candidate. Introduce no unsupported Stable stub.

- [ ] C16 — Collector and evidence-validation tests meaningfully reject missing/duplicate coverage, skipped mandatory tests, altered findings/controls/products, forged producer identities and candidate/environment/configuration mismatches. The final CLI collection path reproduces and validates actual observations.

- [ ] C17 — Run and retain the smallest applicable validation during development. Use the focused public/real-process Worker suite, affected recorder/collector tests and inventory tests rather than relying only on full-build success.

- [ ] C18 — Run `./mvnw -B -ntp verify` before submission, `./scripts/inventory validate`, `./scripts/inventory generate`, `./scripts/inventory check`, and live `./scripts/inventory readiness`. Shipped-code or build-compatibility changes also pass `./scripts/verify-jdk-matrix.sh`. T21 certification on all four JDK profiles is mandatory regardless of whether that conditional build matrix is triggered.

- [ ] C19 — Review the final diff against #82/#1/#33, applicable repository standards and baseline `df2df726a2695267bf63a7c9df49a4a7481f0169`. Retain Standards and Spec findings/dispositions with no unresolved applicable findings.

- [ ] C20 — Produce a ticket-owned execution receipt containing baseline and final HEAD/status, changed-file ownership, criterion-to-evidence mapping, candidate/contract identities, actual environment/tool/launcher identities, commands and results, reviews, retained failed attempts and any residual risks. Distinguish completed #82 work from unrelated release blockers.

- [ ] Commit only after all selected criteria pass and the source context or user authorizes a commit. Any such local commit uses `git commit -s` with identity `mabaiqiu <mabaiqiu@gmail.com>`.
- [ ] Workspace is clean except for this ticket's changes (unrelated dirty or untracked files untouched).

## Constraints

- do not push, open a pull request, merge, close issues, or edit tracker state
- do not modify unrelated dirty or untracked files
- do not implement downstream work early
- use the agreed validation seam and prefer behavior evidence over implementation details
- always run the smallest applicable validation during development
- require broad or full validation only when repository gates demand it, the user explicitly requests it, or the change affects core logic, security, data consistency, concurrency, or a known bug regression
- for low-risk non-behavioral work, allow tests to be skipped only when there is no relevant test seam or non-test validation is sufficient; still require the smallest applicable validation, and require the execution report to state why tests were skipped and identify any residual risk
- review the final diff against the source criteria and recorded baseline before committing; use any available review tool only when it adds value
- commit only after all selected criteria pass and the source context or user authorizes a commit
- Do not publish releases, upload a Central bundle, deploy artifacts or access publication credentials. This explicit user orchestration restriction assigns all publication to the parent.
- A local commit is not required by this contract. Any later authorized local commit must be DCO signed off as `mabaiqiu <mabaiqiu@gmail.com>`; a repository-local invocation is `git -c user.name=mabaiqiu -c user.email=mabaiqiu@gmail.com commit -s`.
- Keep changes within #82 and necessary current-evidence maintenance in `/workspace/folio-pdf`; use repository-local ignored build caches and `/tmp` for local build/provisioning scratch.
- Keep shipped code compatible with Java 8 language and runtime APIs. Preserve explicit resource ownership, checked safe failures, parent-side callbacks/Providers/publication and the closed transport registry.
- Preserve exact runtime classpath authority. Do not expand acceptance/application code into the Worker, relax hashes/inventories or expose a new public backend/testing seam to obtain passing evidence.
- Preserve the distinction between modeled Folio-owned resource accounting and JVM heap, process RSS, native allocations or kernel/container isolation. Make no physical secure-erasure claim.
- Acceptance-only tools, fonts, fixtures and observers stay outside product runtime and published artifacts. Establish origins/licenses and maintain clean-room provenance; do not copy or adapt iText material.
- Do not implement or certify #83 recovery/scale early. Existing recovery fault injection may establish #82 crash/cleanup behavior without promoting recovery. Do not add Forms, Trust signing, Conformance, SVG/XML, OCR, Sanitization or Office work.
- Preserve historical evidence. Use fresh output directories and retain original failed attempts. Do not repair stale certification by replacing identities or producer labels.
- Retain all current indexed reports and transitive references for review. Follow existing multipart archival conventions if a ticket-owned archive exceeds repository publication limits; record hashes and reassembly instructions. The parent decides publication selection.
- Validation breadth: full
  Reason: This is security-boundary certification; #82 and CONTRIBUTING require full verification. Development remains focused, and the existing JDK build matrix is additionally mandatory when shipped code or build compatibility changes.

## Context

Approved source:

- Live #82: https://github.com/zerocloud-sdk/folio-pdf/issues/82 — body and comments read; no comments.
- Governing specification #1: https://github.com/zerocloud-sdk/folio-pdf/issues/1 — body and comments read.
- Parent #33: https://github.com/zerocloud-sdk/folio-pdf/issues/33 — body and comments read; its whole-release obligations do not enlarge this ticket.
- Closed blocker #81: https://github.com/zerocloud-sdk/folio-pdf/issues/81 — body, completion comment and native dependency state read.
- Snapshot fallback: `/workspace/contracts/issue-82-issue.json`.
- User orchestration instructions: compiler read-only; executor cannot publish or mutate tracker state; later local commits require the specified DCO identity.

Design and repository guidance:

- `AGENTS.md`, `docs/agents/issue-tracker.md`, `docs/agents/domain.md`, `CONTEXT.md`, `CONTRIBUTING.md`, `PROVENANCE.md`, `DEPENDENCIES.md`.
- ADR-0016 hostile input; ADR-0023 independent chains; ADR-0024 Worker Session proxy; ADR-0025 ownership/failures; ADR-0031 data/secrets; ADR-0038 transaction recovery boundary; ADR-0040 observed Foundation environments.
- `docs/hardened-worker.md`, `docs/hostile-input-policy.md`, `docs/foundation-readiness.md`, `docs/t20-certification.md`.
- `capabilities/evidence/T21-hardened-worker.md`, `capabilities/capability-matrix.yaml`, `capabilities/facade-surface.yaml`, and the Foundation release/requirements/environments/evidence authorities.
- Current predecessor evidence: `capabilities/evidence/foundation/T81-final-r2/`.
- Retained setup/validation examples: `capabilities/evidence/T81-delivery/validation-results.json`, `identity-summary.json`, `run-final-gate.py`, `run-ubuntu-gate.py`. Inspect these as examples; do not execute helpers that write into #81’s delivery tree.
- #81 publication/review notes: `/workspace/contracts/issue-81-review.md`.

Agreed validation seam:

- Primary: public `DocumentWorkflow.execute` using public requests, Commands and Queries; outcomes, reopened files, receipts, safe failures, ownership and lifecycle.
- Migration: existing public Facade operations and the same document outcomes in their actual supported mode.
- Boundary-specific: the actual framed Worker endpoint and real child process for hostile messages, isolation, launch controls, termination and crash experiments.
- Provider-specific tests only at an actual applicable external Provider seam.

Inspect first:

- `git status --short --branch`, `git rev-parse HEAD`, `git diff`, `git diff --cached`.
- `WorkflowExecutionProfileContractTest`, `HardenedWorkerWorkflowTest`, `HardenedWorkerIsolationTest`, `WorkerProtocolBoundaryTest`, `HardenedWorkerRecoveryFaultTest`.
- `HardenedWorkerEngine`, `HardenedWorkerMain`, `HardenedWorkerSecurityManager`, `WorkerProtocol`, `WorkerMessages`, `HardenedWorkerSettings` and both `META-INF/folio-pdf/*-worker-classes` inventories.
- `scripts/t03-foundation.py`, `scripts/t20_foundation_reports.py`, `scripts/tests/test_t03_foundation.py`, `scripts/tests/test_t20_foundation.py`, `T20ContractTestCommand` and existing recorder/collector negative tests.
- `.github/workflows/ci.yml`, `scripts/verify-jdk-matrix.sh`, module POMs and `FoundationReadinessCommandTest`.

Concrete validation routes, after prerequisites are established:

- Focused Worker development:
  `./mvnw -B -ntp -pl pdf-document -am -Dtest=WorkflowExecutionProfileContractTest,HardenedWorkerWorkflowTest,HardenedWorkerIsolationTest,WorkerProtocolBoundaryTest,HardenedWorkerRecoveryFaultTest -Dsurefire.failIfNoSpecifiedTests=false test`
- Existing collector checks:
  `python3 -B -m unittest discover -s scripts/tests -p 'test_*foundation.py'`
- Inventory seam:
  `./mvnw -B -ntp -pl build-tools/inventory test`
- Local unsigned candidate staging:
  `python3 -B scripts/t03-foundation.py stage`
- After implementing the missing Worker case:
  `python3 -B scripts/t03-foundation.py certify capabilities/evidence/foundation/T82-final/worker --obligation worker`
- Add and document the corresponding Worker collection invocation; verify its exact declared profile behavior and negative-control rejection.
- Final gates are those in C18.

Run build commands in an established JDK environment or the exact declared container route. Configure validated HarfBuzz/Python installations and required Python packages explicitly. Record the actual command/environment; do not infer certification from cached tool presence.

Environment authority: `capabilities/foundation-environments.yaml`, using the immutable image digests and executable/OS hashes recorded there:

| Profile suffix | Vendor | Required build |
| --- | --- | --- |
| jdk8 | Eclipse Adoptium | 1.8.0_502-b07 |
| jdk11 | Eclipse Adoptium | 11.0.32+9 |
| jdk17 | Eclipse Adoptium | 17.0.20+8 |
| jdk21 | Eclipse Adoptium | 21.0.12+8-LTS |

The current runner checks staged source/artifact/harness identities before certification and rechecks source/environment/tool identities around observations. Extend those protections for Worker evidence; neither generated readiness nor a passing local build substitutes for live certification.
```

**Session recommendation**

- Session: fresh
- Capability: Advanced
- Intensity: High
- Reason: Security-boundary certification requires real-process negative controls, lifecycle and termination evidence, and consistent artifact/environment identities across independent chains.