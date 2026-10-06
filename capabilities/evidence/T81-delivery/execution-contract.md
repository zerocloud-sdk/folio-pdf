**Readiness checklist**

- [x] Source is agent-ready: required decisions and completion conditions are established.
- [x] Selected work is unblocked: #71 is CLOSED.
- [x] Exactly one bounded frontier is selected: #81.
- [x] Pre-implementation HEAD is recorded as the comparison baseline.
- [x] Every acceptance criterion is classified below.
- [x] Validation commands are grounded in repository scripts, CI, documentation and tests.
- [x] User-established permissions and workspace boundaries are preserved.
- [x] Every completion criterion is decidable from observable evidence.

**Session recommendation**

- Session: fresh
- Capability: Advanced
- Intensity: High
- Reason: Security-sensitive certification joins aggregate quotas, concurrent lifecycle behavior, cooperative stops and independent evidence bound to exact artifacts and environments.

```markdown
## Goal

Complete GitHub issue #81, “[T32.13] Certify trusted in-process hostile-input limits,” in zerocloud-sdk/folio-pdf.

Certify `document.hostile-input-limits`, Acceptance Profile `T20-hostile-input-limits`, and Foundation obligation `limits` for actual Ubuntu 24.04 / Linux x86-64 environments on JDK 8, 11, 17 and 21 using IN_PROCESS execution. Reuse the existing Native implementation and independent acceptance infrastructure. Complete demonstrated gaps, matching behavior through existing Migration Facade operations, and reproducible certification.

Deliver the reviewed worktree, retained evidence and execution receipt. The user authorizes at most one optional local Signed-off-by commit after completion. Publication remains with the orchestrator.

## Current state

- Branch: `main`, in `/workspace/folio-pdf`.
- Remote: `https://github.com/zerocloud-sdk/folio-pdf.git`.
- HEAD (comparison baseline): `05e7f546f5885680e333ad8d3dea3645b25d26b7`.
- Dirty / untracked files to protect: none observed; tracked and staged diffs were empty. Recheck at execution entry and protect any subsequently introduced unrelated work.
- DCO identity: `mabaiqiu <mabaiqiu@gmail.com>`.
- Readiness: #81 is OPEN with no comments. Its explicit blocker, #71, is CLOSED. #80 is CLOSED and its implementation is already in the recorded HEAD. The `limits` readiness entry describes unfinished certification, not an outstanding prerequisite ticket.

Evidenced complete:

- `WorkflowResourcePolicy`, `WorkflowResourceContext`, hostile-input preflight and the shared-environment concurrency gate already implement the finite cooperative Native policy.
- Existing public tests cover immutable defaults, overrides, all Source forms, exact boundaries and excesses, aggregate accounting, repeated decoding, Patch nesting, cancellation, deadlines, terminal resource failures, cleanup and publication protection.
- The retained #80 full verification log reports all 19 `HostileInputWorkflowTest` tests passing without skips. Retained #80 full verification and the four-JDK matrix passed. These records establish the inspected baseline; they do not certify #81.
- English contracts, public Javadoc and the domain glossary explicitly distinguish modeled usage from JVM heap, process RSS and hard isolation.
- Foundation `limits` requires IN_PROCESS and five chains: syntax, standards, semantic, visual and contract. Its dependencies are `transactions` and `values`.
- The Foundation authority and Facade manifest already justify Native-Interface-only resource-policy controls because they have no Reference Suite counterpart. Underlying document operations retain their matching Facade obligations.
- The current evidence index retains eleven predecessor obligations, each with eight Native environment/profile tuples. Existing Facade observations run IN_PROCESS.
- #80’s embedded-files-only encryption, the existing security aggregate and their evidence are established predecessor work.

Acceptance-criterion classification:

| Issue criterion | Classification at baseline | Evidence / remaining gap |
| --- | --- | --- |
| AC1: certify every policy bound across workflow phases | Demonstrably incomplete | Public implementation tests exist; no independent T20 chains or environment certifications are declared. |
| AC2: aggregate accounting, precedence, stops, cleanup and diagnostics | Unverified as a complete certification obligation | Substantial public tests exist; complete independently controlled phase and lifecycle coverage remains to be demonstrated. |
| AC3: explicit cooperative guarantee | Evidenced complete | Policy guide, Javadoc and glossary state the required boundary. Preserve it. |
| AC4: Native-only mapping decision and separate PDF standards evidence | Demonstrably incomplete | Mapping decision exists; slice-specific PDF standards evidence is absent. |
| AC5: artifact/environment-bound independent records and controls | Demonstrably incomplete | Capability acceptance evidence and certified platforms are empty; the Foundation index contains zero `limits` certifications. |
| AC6: matching public Native/Facade outcomes | Unverified for this slice | Existing mappings and public tests exist; complete hostile-input equivalence through those mappings is not certified. |
| AC7: coordinated inventories, contracts, usage material and provenance | Demonstrably incomplete | Capability/profile remain experimental and generated readiness reports `limits` blocked. |
| AC8: smallest checks, full verification, JDK compatibility and reviews | Demonstrably incomplete for #81 | Prior gates passed; the new slice needs its own final validation and baseline-relative reviews. |
| AC9: Java 8, ownership, clean-room licensing and non-goals | Evidenced complete at baseline | Existing contracts and artifact verification establish these invariants. Preserve them in the changed candidate. |

Known gaps:

- `document.hostile-input-limits` remains experimental, with an experimental profile, empty `acceptance-evidence` and empty `certified-platforms`.
- `scripts/t03-foundation.py` has no `limits` obligation. Its planning/certification loops hardcode both Native profiles, its record producer mapping handles only four PDF chains, and its completion count assumes eight tuples. Extend these seams for the declared four IN_PROCESS `limits` tuples and the separate contract chain while preserving predecessor behavior.
- The current T20 evidence/provenance narrative contains historical statements that T03/T09 dependency gates were open. Keep historical records intelligible and add accurate current certification statements.
- Existing implementation-calibrated boundary probes, such as temporary-quota discovery by binary search, are useful regression evidence but do not alone provide the independently controlled certification required by AC1.

Existing failures:

- No unresolved implementation failure was established from inspected baseline records.
- Overall Foundation readiness is intentionally NOT READY because this and later obligations remain unfinished.
- Compilation was read-only; validation records were inspected, not rerun. Fresh validation is required during execution.

## Execution order

1. Verify the branch, HEAD and worktree against the recorded baseline. Read the approved issue, relevant repository instructions, policy contracts and Foundation authorities. Preserve settled scope and mapping decisions.
2. Build a closed coverage inventory for every declared bound and lifecycle requirement. Map existing tests and independent corpora to it; identify uncovered cases without rebuilding evidenced implementation.
3. Add independently controlled fixtures and public Native/Facade observations for the gaps. Fix product behavior only where a demonstrated in-scope failure requires it.
4. Extend the existing recorder, collector and Foundation runner for `limits`, its IN_PROCESS-only scope and five required chains. Add focused rejection tests and qualify actual independent tools against the affected PDF outcomes.
5. Coordinate inventories, English contracts/Javadoc, Chinese usage and provenance. Freeze the final source, contracts, profiles, harness and tool inputs before staging and recording.
6. Stage an unsigned local candidate using existing machinery. Refresh invalidated predecessor certifications in their established order, then record `limits` on all four required environments. Use fresh directories and preserve historical evidence.
7. Run final verification, applicable JDK and inventory gates, independent collection and baseline-relative Standards/Spec review. Restage and rerun affected evidence if any bound input changes.
8. Deliver the evidence receipt and reviewed diff. Optionally create one local DCO commit after every selected criterion passes.

## Completion criteria

- [ ] A source-traceable, closed coverage inventory covers every declared Workflow Resource Policy bound across staging, operations, validation and publication. Each required case has a fixture, public observation and retained result; applicability is justified rather than silently omitted.
- [ ] Independently controlled hostile-input and first-excess fixtures prove all ten dimensions: aggregate input bytes, pages, indirect objects, nesting, supported filter-stage decompression, decoded pixels, modeled owned memory, temporary storage, elapsed time and shared-environment concurrency. Preserve finite defaults, zero semantics, invalid/overflow declaration rejection, inclusive numeric/elapsed boundaries and the version-1 nesting ceiling.
- [ ] Public observations demonstrate request-policy precedence over the environment default, composition with stricter operation-local limits, and aggregate accounting across primary/additional Sources, Commands, Queries, Patches, products and Targets. Cover repeated decoding, relevant stream-dictionary changes, retained/working memory lifetimes and temporary-storage high-water behavior without resetting budgets between phases.
- [ ] Deterministic Clock, stream and latch controls demonstrate cancellation and deadline handling at owned checkpoints, elapsed-bound equality versus first excess, deadline expiry at equality, backward-Clock behavior, and concurrency admission against both incoming and active ceilings. Permits are released after success, checked failure and caller exceptions.
- [ ] Resource exhaustion remains terminal when caught by caller code. Existing malformed/unsupported-input and operation-local failures retain their owning semantics when no workflow limit is exhausted. Safe failures retain the declared code, capability identifier, content-free diagnostics and public receipt behavior.
- [ ] Public lifecycle observations prove workflow-owned documents, credentials, descriptors, snapshots, cache/spill, staged products and target-adjacent staging are released on terminal paths; caller-owned streams, channels and outputs remain open; Session views expire.
- [ ] Publication observations prove validation precedes publication, pre-commit failure preserves existing Path Targets, earlier committed Targets remain committed, failed/current/later receipts accurately report COMMITTED/FAILED/NOT_ATTEMPTED, and stream failures correctly identify possible partial output. Preserve primary failure and cleanup semantics.
- [ ] Existing public Migration Facade operations demonstrate matching supported document outcomes, reopened files, ownership, lifecycle, safe failures and Publication Receipts. Record their actual IN_PROCESS execution. Retain the approved Native-only policy-control decision consistently in the Foundation authority, Facade manifest and public contracts; add no artificial Reference Suite control API or unsupported Stable stub.
- [ ] Resource-enforcement observations have a separate retained contract chain. Affected successful PDF outcomes have separate independent syntax, standards, semantic and visual records with qualified negative controls. Reuse existing independent corpora and pinned tools where applicable; extend required rule coverage where outcomes demand it.
- [ ] Missing tools, uncovered required rules, undetected controls, missing records, mismatched identities, altered findings or changed producer labels cannot produce PASS. Collection validates actual retained artifacts and observations, with live replay where applicable. Test failure/indeterminate paths as well as successful collection.
- [ ] Four complete `limits` certifications cover actual Ubuntu 24.04 / Linux x86-64 × JDK 8/11/17/21, Native IN_PROCESS, with corresponding Facade IN_PROCESS observations. Each binds exact image, OS, JDK vendor/build/executable, execution settings, source/contracts, candidate artifacts, harness, corpus and actual tool/native identities. Retain original findings and artifact hashes.
- [ ] Candidate changes invalidate prior evidence honestly. Refresh already certified predecessors as required for the same candidate in this order: transactions, values, pages, metadata, annotations, text, images, incremental, password-baseline, password-clear-metadata, password-attachments. Preserve their declared execution coverage and historical records; use existing implementations and recorders.
- [ ] Capability Matrix, Facade Surface, Foundation release/requirements/evidence authorities, generated/public English contracts, Javadoc, Chinese usage material and clean-room provenance agree. Mark `document.hostile-input-limits` compatible only after complete evidence and satisfied dependencies; introduce no unrelated promotion.
- [ ] Live inventory evaluation reports `limits` and its prerequisites satisfied with current identities. Overall Foundation readiness may remain NOT READY for concrete later obligations; retain those diagnostics without removing requirements or changing labels to manufacture release readiness.
- [ ] Ran the smallest applicable initial validation: `./mvnw -B -ntp -pl pdf-document -am -Dtest=HostileInputWorkflowTest -Dsurefire.failIfNoSpecifiedTests=false test`. Run additional focused Native/Facade, recorder, collector and real-tool tests as the changed coverage requires.
- [ ] Ran repository-mandated full verification: `./mvnw -B -ntp verify`. Shipped-code/build-compatibility changes also pass `./scripts/verify-jdk-matrix.sh`. All mandatory #81 certification cases, tuples, rules and chains execute without skips.
- [ ] Ran applicable collector checks using the repository route: `PYTHONPATH=.build-cache/foundation-host-python python3 -B -m unittest discover -s scripts/tests -p 'test_*foundation.py'`; ran `./scripts/inventory validate`, `generate`, `check` and live `readiness`. Record expected unrelated readiness failures separately from failed slice requirements.
- [ ] Reviewed the final diff against the source criteria and recorded baseline. Resolve applicable Standards and Spec findings; retain review results identifying baseline `05e7f546f5885680e333ad8d3dea3645b25d26b7`.
- [ ] The execution receipt maps every issue criterion to observable evidence and records exact certification identities, validation commands/results, reviews, remaining unrelated Foundation blockers, worktree state and optional commit status.
- [ ] Commit only after all selected criteria pass and the source context or user authorizes a commit. The user has authorized at most one optional local commit: use `git commit -s` with `mabaiqiu <mabaiqiu@gmail.com>` and verify its Signed-off-by trailer.
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
- Validation breadth: full
  Reason: #81 explicitly requires full verification, and the scope concerns security-sensitive resource accounting, concurrent lifecycle and publication behavior.
- User-established execution permission: modify the supplied worktree and optionally create one local Signed-off-by commit after completion. Do not publish releases, sign/upload release candidates, deploy artifacts, push, open/merge PRs or mutate GitHub issues. Those actions belong to the orchestrator after review.
- Preserve Java 8 runtime/API compatibility, explicit ownership and Apache-2.0 clean-room provenance. Acceptance tools, fonts and fixtures stay outside product runtime; do not copy/adapt iText source, resources, fixtures or proprietary implementation details.
- Preserve the Trusted In-process Profile's cooperative guarantee. Modeled usage establishes no JVM heap, process RSS, arbitrary callback/backend/native termination or sandbox claim. Hostile multi-tenant isolation remains the separately selected Hardened Worker boundary.
- The 2026-09-08 amendment requires only actual Ubuntu 24.04 / Linux x86-64 × JDK 8/11/17/21. Windows x86-64 and macOS x86-64/arm64 remain explicitly uncertified and are not Foundation 0.1.0 blockers.
- Preserve existing #80 encryption behavior and its predecessor contracts.
- Scope excludes parent #33 end-to-end release completion/publication, later T32 slices, Worker isolation/recovery/scale certification, and Forms / Trust signing / Conformance / SVG-XML / OCR / Sanitization / Office implementation. Existing broad regression gates and predecessor evidence refresh remain applicable.

## Context

- Approved source:
  - https://github.com/zerocloud-sdk/folio-pdf/issues/81 — complete live body read; no comments.
  - Fallback approved snapshot: `/workspace/contracts/issue-81-snapshot.json`.
  - Parent: https://github.com/zerocloud-sdk/folio-pdf/issues/33.
  - Governing specification: https://github.com/zerocloud-sdk/folio-pdf/issues/1, including comments.
  - Completed prerequisite: https://github.com/zerocloud-sdk/folio-pdf/issues/71.
  - Existing encryption boundary: https://github.com/zerocloud-sdk/folio-pdf/issues/80.
  - This goal preserves the user's platform amendment, bounded scope and orchestrator publication authority.

- Design docs:
  - `AGENTS.md`, `docs/agents/issue-tracker.md`, `docs/agents/domain.md`, `CONTEXT.md`, `CONTRIBUTING.md`.
  - `docs/hostile-input-policy.md`, `docs/document-values.md`, `docs/foundation-readiness.md`, `capabilities/profiles/foundation-release.md`.
  - ADR-0002, 0006, 0013, 0016, 0023, 0025, 0029 and 0040.
  - `docs/t03-certification.md`, `docs/t09-certification.md`, `docs/t80-certification.md`, `docs/third-party/t03-standards-tools.md`.
  - `README.md`, `docs/zh-CN/getting-started.md`, `PROVENANCE.md`.

- Agreed validation seam:
  - Native `DocumentWorkflow.execute` and existing public Migration Facade calls.
  - Observable results, detached `WorkflowResourceUsage`, reopened files, Publication Receipts, stable failures, ownership and lifecycle.
  - Existing independent qpdf syntax, qualified standards tools/rules, project-owned semantic expectations, and independent PDFium/ImageMagick visual checks.
  - Actual staged jars, artifact contracts and exact environment observations.
  - No backend identity/private-call assertions or artificial internal Provider seam; test real external Providers only where an applicable existing path uses one.

- Inspect first (commands / files):
  - `git --no-optional-locks status --short`, `git rev-parse HEAD`, `git diff`, `git diff --cached`.
  - `gh issue view 81 --repo zerocloud-sdk/folio-pdf --json title,body,state,comments,url`; use the approved snapshot if live access is unavailable.
  - `pdf-document/src/main/java/net/zerocloud/pdf/{WorkflowResourcePolicy,WorkflowResourceUsage,WorkflowResourceContext,WorkflowConcurrencyGate,DocumentWorkflow,PdfBoxHostileInputPreflight,PdfBoxWorkflowEngine}.java`.
  - `pdf-document/src/test/java/net/zerocloud/pdf/consumer/{HostileInputWorkflowTest,WorkflowTransactionContractTest,WorkflowResourceOwnershipTest,WorkflowLifecycleTest,PdfValueWorkflowTest}.java`.
  - `pdf-migration-itext7/src/main/java/net/zerocloud/pdf/itext7/kernel/pdf/{PdfDocument,FacadeSource,FacadeDeclarations,FacadeSession}.java` and the corresponding public consumer/artifact tests.
  - `capabilities/{capability-matrix,facade-surface,foundation-release,foundation-requirements,foundation-environments,foundation-evidence}.yaml`, `capabilities/evidence/T20-hostile-input-limits.md`.
  - Existing T03/T09 and applicable later independent corpora, profile controls and qualified observers under `capabilities/profiles`, `pdf-acceptance`, `scripts` and `build-tools/acceptance`.
  - `scripts/t03-foundation.py`, existing `scripts/*_foundation_reports.py`, `scripts/tests/test_*foundation.py`, `scripts/inventory`, `scripts/verify-jdk-matrix.sh`, `.github/workflows/ci.yml` and relevant POMs.
  - `capabilities/evidence/T80-delivery/receipt.md` and retained full-verification/JDK-matrix records.

- Execution prerequisites:
  - Use the existing pinned acceptance installations, Python runtime and complete explicit HarfBuzz installation. Set `FOLIO_HARFBUZZ_HELPER` to its documented absolute helper path.
  - `python3 scripts/t03-foundation.py stage` is the existing unsigned local staging route.
  - Implement and document the new `limits` recorder/collector invocation before using it; the baseline CLI does not yet accept `--obligation limits`.
  - Any changed bound source, contract, profile, threshold, harness, tool or artifact identity requires restaging and fresh affected observations.
```