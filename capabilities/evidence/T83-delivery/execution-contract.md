## Readiness checklist — compiler record

- [x] Approved source and comments read; required decisions and completion conditions are defined.
- [x] Issue #83 is unblocked: live native dependencies identify only #82, which is CLOSED.
- [x] Exactly one bounded frontier selected: #83.
- [x] Pre-implementation HEAD recorded.
- [x] Every acceptance criterion classified against repository evidence.
- [x] Validation commands discovered from repository scripts, documentation, CI, and tests.
- [x] User permissions and workspace boundaries preserved.
- [x] Completion criteria are decidable from observable behavior and retained evidence.

## Goal

Complete [issue #83 — Certify Worker recovery and bounded Foundation scale](https://github.com/zerocloud-sdk/folio-pdf/issues/83) in `/workspace/folio-pdf`.

Certify `document.hardened-worker.recovery-scale`, Foundation obligation `recovery`, and Acceptance Profile `T22-worker-recovery-scale` on the four declared Ubuntu 24.04/Linux x86-64 JDK environments. Reuse the existing implementation; complete certification, necessary verification gaps, documentation, inventories, and provenance together.

This contract covers #83 only. It does not complete the parent Foundation release or downstream tickets.

## Current state

- **Branch:** `main`.
- **HEAD — comparison baseline:** `42fe5c797711070b319b9419e14fcb7adfa98735`.
- **Dirty / untracked files to protect:** none reported by the inspected worktree; tracked and staged diffs are empty. Recheck before execution.
- **Evidenced complete:** transaction identity/status, bounded environment-local retention, retry restrictions, publication tracking, authenticated multi-frame transport, resource usage, and opt-in scale profiles already have implementation and public contract tests. Preserve these rather than rebuilding them.
- **Prerequisite:** #82 is CLOSED. The Capability Matrix marks `document.hardened-worker` compatible. Its retained evidence contains passing syntax, standards, semantic, visual, and contract records for all four required HARDENED_WORKER environments.
- **Known gaps:** recovery-scale remains experimental, with empty acceptance-evidence and certified-platform lists. The Foundation evidence index contains zero `recovery` certification tuples. `scripts/t03-foundation.py` supports `worker` but has no `recovery` certification/collection route.
- **Historical observations:** the T22 record reports successful recovery, protocol, scale, full-reactor, and JDK-matrix runs from September 2026. Its scale environment was Linux/GraalVM Java 17, and generated artifacts were discarded. These observations do not certify the required current candidate and Ubuntu/Temurin environments.
- **Existing failures:** none established by this compilation. Tests and live readiness were not executed; retained PASS records are historical evidence, not fresh validation.

Acceptance-criterion classification:

| Issue criterion | Classification | Evidence / remaining work |
| --- | --- | --- |
| AC1: identity, retention, conflicts, lookup, retry | Demonstrably incomplete | Implementation/tests exist; candidate-bound certification is absent. |
| AC2: multi-frame and failure behavior | Demonstrably incomplete | Public round-trip, real-child fault, and protocol tests exist; #83 certification is absent. |
| AC3: actual scale profiles and retained identities | Demonstrably incomplete | Opt-in profiles exist; required environment runs and retained artifact evidence are absent. |
| AC4: exact resources, limits, environments | Demonstrably incomplete | Historical observations do not establish the declared #83 environments. |
| AC5: independent evidence and controls | Demonstrably incomplete | T22 records all four independent chains as INDETERMINATE. |
| AC6: Native and applicable Facade outcomes | Demonstrably incomplete | Native-only controls are justified; matching document outcomes still need current certification evidence. |
| AC7: inventories, documentation, provenance | Demonstrably incomplete | Current material correctly describes an experimental capability. |
| AC8: validation and Standards/Spec review | Unverified | No fresh #83 validation or baseline-relative review has run. |
| AC9: Java 8, ownership, licensing, exclusions | Evidenced complete at baseline | Preserve these established constraints throughout execution. |

## Execution order

1. Confirm HEAD and worktree ownership. Read the approved source and listed contracts; verify the existing prerequisite evidence and required tool/environment availability. Report baseline drift without resetting or overwriting work.
2. Define a source-traceable recovery/scale case inventory and candidate-bound recorder/collector using the existing certification patterns. Reuse qualified corpora and tools. Close only gaps needed by #83, running focused validation during development.
3. Prepare the proposed final profiles, inventories, English/Chinese documentation, and provenance. Generate public views, then freeze source and contract inputs before staging.
4. Stage the actual final candidate. Refresh invalidated prerequisite evidence in dependency order through `worker`, then certify `recovery` in all four declared environments, including every opt-in scale profile.
5. Collect strict, producer-bound records into the Foundation evidence index. Verify current identities and readiness, run mandatory verification, and review the final diff against this baseline and the source criteria.
6. Resolve applicable findings and repeat affected staging/certification when inputs change. Return the reviewable local changes and an evidence receipt to the parent orchestrator.

## Completion criteria

- [ ] Public requests and `lookupTransaction` certify environment-local, Worker-only identities; material-envelope conflict rejection; concurrent/final duplicate rejection before callback or publication; finite non-evicting record-count and modeled per-record retention bounds; and oversized-envelope rejection before admission.
- [ ] Retained status and declaration-ordered Publication Receipts distinguish RUNNING, RECOVERABLE, COMPLETED, and FAILED. A retry is admitted only for an eligible recoverable failure with every Target NOT_ATTEMPTED. Completed or failed transactions cannot replay output.
- [ ] Uncertain acknowledgement after fully committed Path or stream publication resolves through lookup as COMPLETED. Possibly partial stream publication remains terminal with `partialOutputPossible`; replay neither invokes caller work nor emits more bytes.
- [ ] Certification exercises real multi-frame values in both directions and verifies publication/reopen semantics. Authenticated missing, reordered, duplicate, corrupt, mismatched, malformed, and excess transfer controls demonstrate safe rejection and released ownership.
- [ ] Actual interruption, cancellation, timeout, Worker crash, malformed response/input, and publication failures produce stable safe outcomes. Evidence proves unchanged uncommitted Targets, ordered receipts, confirmed child termination, owned-storage cleanup, and no duplicate publication. Existing fault seams may trigger failures; assertions remain at public outcomes or the real protocol boundary.
- [ ] All three opt-in profiles actually execute in every required environment: exactly 5,000 published/reopened pages; exactly 1,073,741,824 consumed input bytes; and the existing configured-concurrency profile admitting two workflows, rejecting the first excess before callback/publication, then allowing its recoverable retry after capacity release. Mandatory certification cases have no ignored tests or assumption-based skips.
- [ ] Scale records retain input constructions, exact lengths and hashes, output identities where publication occurs, public semantic observations, full finite policies, modeled memory/storage high-water marks, elapsed/wall time, temporary spill, resource ownership, and cleanup evidence. Explicitly record the absence of output for a read-only profile. Independent observations must identify the actual workload artifacts; miniature substitutes do not certify scale.
- [ ] Every certification is bound to the actual final candidate, contract, execution configuration, and declared environment. Record immutable image, OS/kernel/architecture, exact JDK vendor/build/executable, launcher/runtime closure, tool/native identities, commands, locale/timezone, resource limits, and applicable font/Provider inputs or explicit absence.
- [ ] Retain separate syntax, standards, semantic, visual, and contract records for each of the four required HARDENED_WORKER tuples: **20 required chain records**. Preserve original artifacts/findings and qualified negative controls. Missing tools, uncovered required predicates, identity mismatches, failed controls, or skipped required execution cannot produce PASS.
- [ ] Add strict recovery certification/collection support and necessary checker coverage. Meaningful negative tests reject omitted cases/profiles, missing or altered artifacts/findings, stale identities, and relabeled or resealed invalid evidence. Reuse qualified standards and visual profiles only where their predicates apply to the actual artifacts.
- [ ] Matching document behavior is observed through public Native workflows and existing public Migration Facade operations, including reopened files, ownership, Publication Receipts, and safe failures. Record the Facade’s actual execution profile. Preserve the documented Native-only status of transaction/recovery/resource controls; introduce no artificial Reference Suite mapping or unsupported Stable stub.
- [ ] Update the Capability Matrix, Facade Surface, Foundation evidence/requirements where applicable, T22 acceptance contract, generated views, authoritative English contracts, Chinese usage material, and clean-room provenance together. Promote only the fully evidenced, dependency-satisfied recovery capability.
- [ ] All previously satisfied prerequisite obligations remain satisfied for the same final candidate. Source or contract changes require restaging and fresh affected certification; historical records remain intact and cannot be relabeled for a new candidate.
- [ ] Live `./scripts/inventory readiness` reports `recovery` and its prerequisites SATISFIED. Overall Foundation readiness may remain false solely for unrelated outstanding obligations; retain and explain that result without implementing those obligations.
- [ ] Run the smallest applicable validation during development, including the focused recovery/protocol tests below and relevant recorder/collector/checker tests.
- [ ] Run `./scripts/t22-scale all` with actual execution in all four declared certification environments; ordinary CI’s opt-in skips do not satisfy this requirement.
- [ ] Run `./mvnw -B -ntp verify`, inventory generation/validation/freshness checks, and final live readiness. If shipped code or build compatibility changes, also pass `./scripts/verify-jdk-matrix.sh`.
- [ ] Resolve applicable Standards and Spec review findings against baseline `42fe5c797711070b319b9419e14fcb7adfa98735`; retain review conclusions, validation results, evidence paths, ownership, and disclosed limitations in the execution receipt.
- [ ] Review the final diff against the source criteria and recorded baseline before any authorized commit.
- [ ] Commit only after all selected criteria pass and the source context or user authorizes a commit.
- [ ] Workspace is clean except for this ticket’s changes; unrelated dirty or untracked files remain untouched.

Compiler records, not execution to-dos:

- [x] Tests skipped because: this session was authorized only to compile a read-only contract.
- [x] Residual risk: baseline behavior and current environment/tool availability have not been freshly executed; the implementing session must perform the required verification above.

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
- **Validation breadth: full.** Reason: #83 and CONTRIBUTING require full verification; recovery, concurrency, publication integrity, and candidate-bound certification also require behavioral regression evidence.
- **User orchestration constraints:** the executing agent must not publish releases. The parent orchestrator handles all publication after review. This compilation grants no local commit authorization; any later authorized commit must use DCO sign-off with identity `mabaiqiu <mabaiqiu@gmail.com>` and `git commit -s`.
- Keep Java 8 runtime compatibility, explicit resource ownership, safe diagnostics, and clean-room licensing. Acceptance-only tools, fonts, fixtures, and classes stay outside product runtime and the Worker runtime closure.
- Preserve the ADR-0038 boundary: environment-local in-memory recovery, caller-maintained callback/path meaning, restored caller-owned Source positions for retry, and no automatic retry, durable recovery, distributed identity, or cross-Target atomicity.
- Describe modeled Folio-owned memory/storage precisely. Do not turn these observations into JVM-heap, process-RSS, native-memory, unbounded-streaming, kernel/container, or physical-erasure claims.
- Required certification is limited to the four declared Ubuntu environments. Windows x86-64 and macOS x86-64/arm64 remain explicitly uncertified.
- Do not implement downstream Forms, Trust signing, Conformance, SVG/XML, OCR, Sanitization, Office, or #88 rendering certification.
- Perform scoped implementation in `/workspace/folio-pdf`; use permitted temporary/cache locations for execution artifacts. Keep historical and failed evidence intact. No approval is implied to write outside authorized workspace boundaries.

## Context

- **Approved source:** [#83](https://github.com/zerocloud-sdk/folio-pdf/issues/83), governing [#33](https://github.com/zerocloud-sdk/folio-pdf/issues/33) and [#1](https://github.com/zerocloud-sdk/folio-pdf/issues/1), including comments and the September 8 environment amendment. [#23](https://github.com/zerocloud-sdk/folio-pdf/issues/23) records the implemented T22 foundation; [#82](https://github.com/zerocloud-sdk/folio-pdf/issues/82) records the completed prerequisite.
- **Snapshot:** `/workspace/contracts/issue-83-issue.json`; live GitHub reads matched it. Native dependencies confirmed #82 CLOSED.
- **Contract destination:** `/workspace/contracts/issue-83-contract.md`; the parent orchestrator saves this response. The compiler has not written that file or changed repository files.
- **Repository instructions:** [AGENTS.md](/workspace/folio-pdf/AGENTS.md), [issue tracker](/workspace/folio-pdf/docs/agents/issue-tracker.md), [domain guidance](/workspace/folio-pdf/docs/agents/domain.md), [CONTEXT.md](/workspace/folio-pdf/CONTEXT.md), and [CONTRIBUTING.md](/workspace/folio-pdf/CONTRIBUTING.md).
- **Design contracts:** [Hardened Worker guide](/workspace/folio-pdf/docs/hardened-worker.md), [T21 certification](/workspace/folio-pdf/docs/t21-certification.md), [Foundation readiness](/workspace/folio-pdf/docs/foundation-readiness.md), [T22 evidence](/workspace/folio-pdf/capabilities/evidence/T22-worker-recovery-scale.md), and relevant ADRs 0013, 0016, 0018, 0023, 0024, 0025, 0031, 0038, and 0040.
- **Authorities:** `capabilities/{capability-matrix,facade-surface,foundation-release,foundation-requirements,foundation-environments,foundation-evidence}.yaml`.
- **Declared JDK builds:** Temurin/Eclipse Adoptium `1.8.0_502-b07`, `11.0.32+9`, `17.0.20+8`, and `21.0.12+8-LTS`. Immutable image and executable hashes are authoritative in `foundation-environments.yaml`; observe actual runtime/vendor properties separately.
- **Agreed validation seam:** `DocumentWorkflow.execute`, `lookupTransaction`, public Commands/Queries, outcomes, receipts, files, safe failures, ownership, and lifecycle. Use existing real-child/protocol seams only for fault triggering and boundary qualification. Test external Providers only where an actual applicable Provider seam exists.
- **Inspect first:** transaction registry/fingerprint/status/policy classes; recovery, protocol, workflow, and scale tests; `scripts/t03-foundation.py`; `scripts/t21_foundation_reports.py`; T21 acceptance commands/profiles; `FoundationEvidence.java`; and retained `capabilities/evidence/foundation/T82-final/worker/` records.

Existing focused development command:

```sh
./mvnw -B -ntp -pl pdf-document -am \
  -Dtest=HardenedWorkerRecoveryTest,HardenedWorkerRecoveryFaultTest,WorkflowTransactionContractTest,WorkflowExecutionProfileContractTest,HardenedWorkerWorkflowTest,WorkerProtocolBoundaryTest,WorkerCodecContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Existing validation commands:

```sh
./scripts/t22-scale all
./scripts/inventory generate
./scripts/inventory validate
./scripts/inventory check
./scripts/inventory readiness
./mvnw -B -ntp verify
./scripts/verify-jdk-matrix.sh
```

The last command is conditional on shipped-code/build changes. Four-environment recovery/scale certification is mandatory regardless.

Use `python3 -B scripts/t03-foundation.py stage` for the existing candidate-staging pattern. A `--obligation recovery` route does not exist at the baseline and must be implemented and tested before use. Freeze inputs before staging; follow the existing prerequisite refresh order through `worker`, then `recovery`.

## Session recommendation

- **Session:** fresh implementation session.
- **Capability:** Advanced.
- **Intensity:** High.
- **Reason:** publication recovery, authenticated transport, concurrency, and strict candidate/environment evidence involve interacting invariants where plausible mistakes can duplicate output or falsely certify behavior.