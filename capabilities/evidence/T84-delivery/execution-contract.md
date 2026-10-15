## Readiness checklist — compiler record

- [x] Approved issue body, labels, and comments read from the supplied snapshot; comments are empty and the required decisions and completion conditions are defined.
- [x] Issue #84 is unblocked: its declared dependency #70 is CLOSED per the user; parent #33 is OPEN and does not block this child.
- [x] Exactly one bounded frontier selected: #84.
- [x] Pre-implementation HEAD recorded and confirmed equal to main and origin/main.
- [x] Every acceptance criterion classified against repository implementation, tests, and inventories.
- [x] Validation commands discovered from repository scripts, documentation, CI, and existing tests.
- [x] User permissions and workspace boundaries preserved: repository inspection only; the sole authorized write is this contract.
- [x] Completion criteria are decidable from public behavior, actual boundary observations, and retained evidence.

## Goal

Complete [issue #84 — Certify Capability Provider selection and disclosure contracts](https://github.com/zerocloud-sdk/folio-pdf/issues/84) in `/workspace/folio-pdf` in a separately authorized implementation session.

Certify `conversion.capability-provider.select-execute`, Foundation obligation `providers`, and Acceptance Profile `T05-capability-provider-contract` on the four declared Ubuntu 24.04/Linux x86-64 JDK environments, through both required Document Workflow execution profiles. Reuse the existing implementation and qualified independent corpora; close the local-endpoint, verification, recorder/collector, documentation, inventory, and provenance gaps.

This contract covers #84 only. It does not complete parent #33 or authorize implementation, commits, tracker changes, or publication in this compilation session.

## Current state

- **Branch:** `main`.
- **HEAD — comparison baseline:** `dfe7eaea98533aa12449e677d9612fc4b964091a`; `main == origin/main == HEAD` at inspection.
- **Dirty / untracked files to protect:** none reported by `git --no-optional-locks status --short --branch`; tracked and staged diffs are empty. Recheck before execution.
- **Recent context:** #83 recovery certification and its omitted chain narratives are already committed. Commit messages establish chronology, not Provider certification.
- **Evidenced implementation:** `ProviderCatalog` implements declaration-ordered selection, exact preferences, availability and authorization filtering, metadata-only selection, and real Provider execution. `CapabilityProvider` checks capability ownership, availability, input/result limits, request timeout policy, and remote authorization, and normalizes adapter failures. Request/result arrays are defensively copied. `SubprocessCapabilityProvider` implements a real bounded versioned protocol, elapsed deadline, direct-child termination, stream closure, and per-invocation staging cleanup.
- **Existing tests to preserve:** `ProviderCatalogTest`, `WorkflowCapabilityProviderTest`, and `SubprocessCapabilityProviderTest` cover these contracts; Provider API and jar integration tests cover project-owned signatures, Java 8 bytecode, module names, notices, unbundled dependencies, and BOM entries. None was run by this compiler.
- **Public seam boundary:** a Workflow Request preference validates and reports a selection; selection alone does not invoke Provider code. Generic execution belongs to the public Provider seam, not a generic Document Session locator. Provider selection and applicable execution remain parent-brokered in both Workflow profiles.
- **Prerequisite:** #70 is CLOSED per the user. The Capability Matrix marks `document.blank.create-publish-reopen` compatible. The current Foundation index retains eight `transactions` tuples with separate PASS syntax, standards, semantic, and visual records under `capabilities/evidence/foundation/T83-final-r20/transactions/`. These are inspected retained records, not a fresh live-readiness result.
- **Known gaps:** the Provider capability/profile remains experimental, has no acceptance-evidence or certified-platform entries, and has zero `providers` certifications in the Foundation index. `scripts/t03-foundation.py` has no `providers` certification/collection route. Existing remote tests use in-memory Providers and do not establish the required controlled local network endpoint behavior. Current workflow Provider tests do not certify the full two-profile/four-environment matrix.
- **Mapping already decided:** the Foundation authority and Facade Surface record an individual Native-Interface-only reason. Provider controls have no Reference Suite counterpart; rendering and shaping retain their separate required migration families. Direct Provider Stable/Preview mapping lists are intentionally empty.
- **Historical material:** the T05 evidence/provenance record describes the original implementation and still mentions an open T03 gate. #70 and the current inventory supersede that historical prerequisite observation; retain history and update the current certification account.
- **Existing failures:** no product failure established by this compilation. Tests, builds, independent tools, containers, and live readiness were not executed. The host `python3` lacks PyYAML; the implementing session must use or provision the intended recorder environment before invoking its YAML-dependent actions.

Acceptance-criterion classification:

| Issue criterion | Classification | Evidence / remaining work |
| --- | --- | --- |
| AC1: deterministic selection, preference, availability, metadata, offline default | Demonstrably incomplete | Implementation and public tests exist; complete candidate-bound certification across all eight required tuples is absent. |
| AC2: actual external seams, bounds, timeout, cleanup, detached values, safe failures | Demonstrably incomplete | Real subprocess tests and common Provider enforcement exist; retained #84 qualification and complete boundary coverage are absent. |
| AC3: controlled local endpoints and explicit disclosure authorization | Demonstrably incomplete | Existing remote fixtures are in memory; no controlled endpoint observations certify refusal or authorized transfer. |
| AC4: project-owned contracts, justified Native-only mapping, no downstream engines | Evidenced complete at baseline | ADR-0033, Provider signatures, Foundation Native-only reason, and Facade exclusion establish the chosen boundary. Preserve it. |
| AC5: actual artifact/environment identities, independent chains and negative controls | Demonstrably incomplete | T05 explicitly records no passing independent acceptance chains; no Provider certification tuples exist. |
| AC6: matching public Native and applicable Facade outcomes | Unverified | Native workflow and inherited Facade tests exist, but fresh #84 publication/reopen/ownership/failure evidence has not run. Direct Provider controls remain Native-only. |
| AC7: inventories, English/Chinese contracts, provenance, compatible status | Demonstrably incomplete | Material describes an experimental Provider contract; coordinated certification updates are required. |
| AC8: focused/full validation, conditional JDK matrix, Standards/Spec review | Unverified | Commands are known; no fresh validation or review against this baseline has run. |
| AC9: Java 8, ownership, clean-room licensing, acceptance/runtime separation, exclusions | Evidenced complete at baseline | Established module, jar/API tests, ownership contracts, provenance, and dependency declarations support these constraints. Preserve them and verify affected artifacts during execution. |

## Obligations

The existing Foundation authority defines the scope; do not weaken it to obtain readiness.

| Contract field | Required value |
| --- | --- |
| Owner / obligation | `conversion.capability-provider.select-execute` / `providers` |
| Acceptance Profile / kind | `T05-capability-provider-contract` / `control` |
| Dependency | `transactions`, owned by #70; do not invent additional issue blockers |
| Certified environments | `ubuntu-24.04-linux-x86-64-jdk8`, `jdk11`, `jdk17`, and `jdk21` with the full declared profile IDs and exact identities |
| Workflow profiles | `IN_PROCESS` and `HARDENED_WORKER`; Provider execution mode is a separate declaration |
| Evidence | Eight environment/profile tuples, each with syntax, standards, semantic, visual, and contract records: **40 required chain records** |
| Migration | Preserve the individual Native-only reason; verify inherited applicable public document behavior and its actual IN_PROCESS Facade execution |
| Source traceability | `spec-us-37/38/39/74`, `spec-id-19/32/47`, `spec-td-04`, `slice-84-1/2/3/4`, and all nine criteria in the supplied issue |

The approved JDK builds are Eclipse Adoptium/Temurin `1.8.0_502-b07`, `11.0.32+9`, `17.0.20+8`, and `21.0.12+8-LTS`. Immutable image digests, OS-release hashes, and Java executable hashes come from `foundation-environments.yaml`; actual runtime observations must independently match them.

Independent PDF chains must inspect the actual documents produced or consumed by this slice. Reuse the qualified transaction corpus, standards predicates, authored blank-page rasters, and controls only when they apply to those exact artifacts. A Provider byte exchange or a contract-test PASS does not itself supply PDF standards or visual evidence. Keep the Provider boundary contract observations separately identifiable.

## Execution order

1. Confirm the baseline and worktree ownership without resetting or overwriting work. Read the approved snapshot and listed authorities. Verify the existing transaction gate, intended Python/container/tool setup, exact environment identities, and actual staged-artifact availability.
2. Freeze a source-traceable Provider case inventory and certification profile. Reuse existing behavior/tests; add the controlled local endpoint cases and necessary public/boundary verification gaps. Implement strict `providers` recorder/collector support using existing Foundation patterns, with meaningful qualification and rejection tests.
3. Update the proposed final inventories, current T05 account, English contracts, Chinese usage material, and provenance. Generate public views and run focused validation. Preserve the Native-only mapping decision and existing behavior.
4. Freeze source, contracts, fixtures, policies, and tool configuration, then stage the actual final candidate. Refresh invalidated previously certified obligations in their existing dependency order, starting with `transactions`; do not implement other open slices.
5. Execute and collect all eight Provider tuples and their 40 chain records against that same candidate. Retain actual subprocess and loopback endpoint observations, original PDF artifacts/findings, environment/configuration identities, and qualified negative controls.
6. Run mandatory verification and live readiness, review Standards and Spec against the recorded baseline, resolve applicable findings, and repeat affected staging/certification after any input change. Return reviewable local changes and an execution receipt; publication and tracker work remain outside scope.

## Completion criteria

- [ ] A frozen case inventory covers every #84 criterion and its assigned Foundation requirements. Mandatory certification cases actually execute; missing, repeated, ignored, assumption-skipped, or failed cases cannot satisfy certification.
- [ ] Public `WorkflowEnvironment`, `WorkflowRequest`, `DocumentWorkflow.execute`, and detached `WorkflowOutcome` certify declaration-order determinism, repeated selection stability, exact preference without fallback, unavailable/missing/wrong-capability refusal, and eligibility filtering of unauthorized remote registrations. Record all version, execution-mode, availability, limits, license, and distribution facts.
- [ ] Public observations certify immutable registration/metadata and request/result values, consistent remote execution/distribution declarations, empty offline defaults, and no implicit discovery or network disclosure. Metadata discovery and selection do not execute adapters; registration does not enable a downstream capability.
- [ ] Actual `CapabilityProvider.execute` / `ProviderCatalog.execute` observations certify declared request/result byte bounds, exact-boundary acceptance and first-excess rejection, capability/preference agreement, positive timeout policy, and rejection of a requested timeout above the declared maximum before adapter execution. Preserve adapter-owned elapsed enforcement; do not claim arbitrary in-process code can be forcibly terminated.
- [ ] Real subprocess observations certify successful framed exchange, pre-start input rejection, bounded output, wrong magic/version, negative/oversized/truncated/trailing output, startup failure, nonzero exit/crash, stalled execution/I/O, and an inherited open pipe after the direct child exits. Retain stable failure categories, finite elapsed bounds, confirmed direct-child termination, closed streams, and removed owned per-run staging, including failure paths. Caller-owned staging roots and unrelated contents remain intact.
- [ ] Safe Provider failures retain only registered Provider/capability identity, stable code, and fixed safe diagnostics. Checked forged adapter failures, runtime failures, null/malformed results, and raw causes/suppressed details cannot expose engine/transport information or escape as successful results. Applicable workflow failures retain their documented Document Failure identity and ordered Publication Receipts.
- [ ] Controlled loopback endpoints exercise actual remote-adapter request/response behavior with project-authored synthetic payloads. Observe zero requests/disclosed bytes when authorization is absent, when only registration/preference exists, and when Workflow authorization is for a different capability. Observe the exact authorized payload/result identity after explicit authorization. A later unauthorized request does not inherit consent from an earlier request.
- [ ] Certify Workflow-level capability-scoped authorization and the separate direct `ProviderRequest` authorization check. Workflow selection alone must remain non-invoking. Endpoint fixtures stay acceptance-only; no real document is sent to an external service and no shipped remote-service adapter is introduced.
- [ ] All required cases execute for both Native Workflow profiles in each of the four declared environments. Record actual parent-brokered Provider execution where applicable; Worker networking and runtime-closure restrictions remain intact. Do not equate a Provider's `SUBPROCESS` or `REMOTE` mode with a Workflow profile.
- [ ] Matching document outcomes are observed through public Native workflows and existing public Stable/Preview Facade operations where applicable: published and reopened files, public page/structure observations, ownership, ordered COMMITTED/NOT_ATTEMPTED/failed receipts as appropriate, unchanged uncommitted Target sentinels, and safe failures. Record the Facade's actual IN_PROCESS execution; Provider-specific controls have no fabricated Facade counterpart.
- [ ] Each tuple retains separate syntax, standards, semantic, visual, and contract records bound to the actual final candidate, Acceptance Profile, exact environment, execution configuration, producers, tools, input/output lengths and hashes, and original findings. The **40 records** include qualified negative controls; distinct PDF chains have distinct records/reports and qualified producer identities.
- [ ] Actual run configurations retain immutable image, OS/kernel/architecture, exact JDK vendor/build/executable, command arguments, Java options, locale/timezone, finite policies, Provider metadata/configuration and fixture identities, tool/native identities, applicable fonts or explicit absence, and the staged harness/runtime closure. Expected profile declarations and installation receipts alone are not observations.
- [ ] Independent standards and visual predicates cover the actual workload artifacts. Missing tools, uncovered required rules, stale/mismatched identities, failed controls, or skipped execution yield FAIL/INDETERMINATE as appropriate; changing a label, producer name, or hash seal cannot produce PASS.
- [ ] Add strict `providers` certification/collection support and necessary checker coverage. Negative tests reject omitted cases/tuples/chains, missing or changed PDFs and endpoint/subprocess findings, stale candidate/configuration identities, invalid controls, and relabeled or resealed invalid evidence. Collection proves correspondence to original observations.
- [ ] Update Capability Matrix, Facade Surface, applicable Foundation requirements/evidence/limitation classifications, T05 profile/evidence, generated public views, authoritative English Provider contracts, Chinese usage material, and clean-room provenance together. Promote only the fully evidenced, dependency-satisfied Provider capability/profile and its four actually certified environments.
- [ ] Preserve every previously satisfied obligation for the same final candidate. Source, contract, fixture, or artifact changes require restaging and fresh affected evidence in dependency order; retain historical and failed evidence with its original identity.
- [ ] Live `./scripts/inventory readiness` reports `providers` and `transactions` SATISFIED with current actual bytes. Existing satisfied obligations remain satisfied. Global Foundation readiness may remain false solely for unrelated outstanding obligations; retain and explain that result without implementing them.
- [ ] Run the smallest applicable development validation below, the new recorder/collector/qualification tests, inventory generation/validation/freshness checks, and `./mvnw -B -ntp verify`. Shipped-code or build-compatibility changes also pass `./scripts/verify-jdk-matrix.sh`; eight-tuple Provider certification is mandatory regardless.
- [ ] Resolve applicable Standards and Spec review findings against `dfe7eaea98533aa12449e677d9612fc4b964091a`. The receipt identifies validation results, evidence paths/identities, review conclusions, ownership, actual limitations, and any unrelated global-readiness blockers.
- [ ] Review the final diff against the source criteria and recorded baseline before any separately authorized commit.
- [ ] Commit only after all selected criteria pass and the source context or user authorizes a commit.
- [ ] Workspace is clean except for this ticket's changes; unrelated dirty or untracked files remain untouched.

Compiler records, not execution to-dos:

- [x] Tests skipped because: this session was authorized only to compile a read-only contract; builds, tests, staging, inventory scripts, certification, and live readiness were not run.
- [x] Residual risk: static inspection and retained PASS records do not establish fresh runtime behavior, available candidate artifacts, or current tool/environment readiness. Execution must perform the validation and certification above.

## Validation

The following commands are for the separately authorized implementation session. They were discovered by reading repository scripts, POMs, CI, contracts, and existing tests; none was executed during compilation.

Existing focused Provider development command:

```sh
./mvnw -B -ntp -pl pdf-provider-contract,pdf-document,pdf-conversion -am \
  -Dtest=ProviderCatalogTest,WorkflowCapabilityProviderTest,SubprocessCapabilityProviderTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Existing applicable Facade and inventory checker commands:

```sh
./mvnw -B -ntp -pl pdf-migration-itext7,pdf-migration-itext7-preview -am \
  -Dtest=BlankDocumentFacadeTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -B -ntp -pl build-tools/inventory \
  -Dtest=FoundationReadinessCommandTest,InventoryCommandTest test
python3 -B -m unittest discover -s scripts/tests -p 'test_t03_foundation.py'
```

These selected-suite commands permit upstream modules without matching test names; that flag does not permit a mandatory certification case to skip. Run the additional named Provider endpoint/recorder/collector/qualification tests introduced by this slice through their real seams.

Existing mandatory repository gates:

```sh
./scripts/inventory generate
./scripts/inventory validate
./scripts/inventory check
./mvnw -B -ntp verify
./scripts/inventory readiness
./scripts/verify-jdk-matrix.sh
```

The JDK-matrix command is conditional on shipped-code/build changes. CI exercises JDK 8/11/17/21 on Ubuntu, but a CI platform label or floating JDK selection does not certify the exact Foundation images/builds. The local matrix requires Podman and the explicit complete native installation selected by `FOLIO_HARFBUZZ_HELPER`.

Use the existing staging entry point `python3 -B scripts/t03-foundation.py stage` only after freezing inputs and preparing its declared Python, tool, and native-helper requirements. Use `plan`, `certify`, and strict `collect` with `--obligation providers` after implementing and testing that route. **That route does not exist at this baseline.** Document its exact arguments and fresh output-directory handling; do not present nonexistent commands as baseline validation.

Generate source-bound public views before final staging. Retain original execution/tool stdout, findings, controlled-endpoint and subprocess observations, document artifacts, configuration records, negative controls, and final live-readiness output. A portable generated report is insufficient when local candidate/cache files are absent.

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
- **Validation breadth: full.** Reason: #84 AC8 and CONTRIBUTING require full verification; disclosure authorization, process lifecycle, publication integrity, and candidate-bound evidence also require behavioral regression evidence.
- **Compilation authorization:** the user's request makes `/workspace/folio-pdf` read-only and authorizes only writing `/workspace/contracts/issue-84-contract.md`. Do not implement, modify repository files, create branches, commit, push, run builds, or mutate tracker state during compilation. The implementation tasks above require a later session authorized for repository changes; this contract supplies scope and completion conditions, not that authorization.
- Preserve Java 8 runtime compatibility, explicit ownership, stable safe diagnostics, project-owned/JDK public values, clean-room provenance, and the existing module dependency direction. Any later authorized commit follows CONTRIBUTING's real-identity DCO sign-off with `git commit -s`; this contract supplies no commit identity.
- Acceptance-only tools, fonts, endpoint adapters, fixtures, and classes remain outside product artifacts and the qualified Worker runtime closure. Add no unproven licensing inputs or restricted Reference Suite material.
- Preserve parent-brokered Provider execution, capability-scoped opt-in disclosure, immutable availability snapshots, and the direct-child-only generic adapter guarantee. Rebuild an environment when availability changes; do not add implicit probing or remote discovery.
- Keep actual document publication/ownership contracts intact. Provider limits do not establish arbitrary code termination, descendant containment, hard memory/CPU/network/filesystem isolation, or cross-Target atomicity.
- Preserve the existing individual Native-Interface-only mapping decision and keep rendering/shaping migration requirements assigned to their owning slices. Add no unsupported Stable stub.
- Windows x86-64 and macOS x86-64/arm64 remain explicitly uncertified and are not Foundation 0.1.0 blockers under the issue's September 8 amendment and ADR-0040.
- Keep historical and failed observations intact; no label changes, producer renaming, copied expected environment values, or resealed artifacts may replace actual qualifying observations.

## Non-goals

- Completing or closing parent #33, the complete Foundation release, or another certification slice.
- Rebuilding already implemented Provider selection, values, failure handling, or subprocess behavior.
- New OCR/Tesseract, Office/LibreOffice, commercial, remote-service, rendering/PDFium, or shaping/HarfBuzz engine implementations; broad rendering or shaping certification belongs to its own ticket.
- Downstream Forms, Trust signing, Conformance, SVG/XML, OCR, Sanitization, or Office work.
- Generic Provider lookup/execution methods on Document Session, invented Reference Suite mappings, or a new Provider Migration Facade.
- Provider streaming/chunking, Worker recovery/scale redesign, descendant-process sandboxing, automatic retry, durable/distributed identity, or broad OS isolation claims.
- Release publication, Central staging, PR creation, pushes, merges, or tracker mutations.

## Context

- **Approved source:** [#84](https://github.com/zerocloud-sdk/folio-pdf/issues/84), supplied in `/workspace/contracts/issue-84-issue.json`: OPEN, label `ready-for-agent`, empty comments, nine acceptance criteria, declared blocker #70, and the September 8 environment amendment. The snapshot is authoritative for this compilation; no live GitHub read or state mutation was performed.
- **Dependency / parent state:** #70 CLOSED and #33 OPEN are supplied by the user. Governing #1/#33 requirements were inspected through the repository's source-traceable `foundation-requirements.yaml`, Foundation authorities, and ADR-0040; these are not represented as fresh remote issue/comment reads.
- **Compiler skill:** [to-goal](/home/box/.agents/skills/to-goal/SKILL.md). The user's explicit output-file request overrides its default prohibition on writing files solely for this contract.
- **Format reference / destination:** `/workspace/contracts/issue-83-contract.md` / `/workspace/contracts/issue-84-contract.md`.
- **Repository instructions:** [AGENTS.md](/workspace/folio-pdf/AGENTS.md), [issue tracker](/workspace/folio-pdf/docs/agents/issue-tracker.md), [domain guidance](/workspace/folio-pdf/docs/agents/domain.md), [CONTEXT.md](/workspace/folio-pdf/CONTEXT.md), and [CONTRIBUTING.md](/workspace/folio-pdf/CONTRIBUTING.md).
- **Design contracts:** [Provider guide](/workspace/folio-pdf/docs/capability-providers.md), [T05 evidence](/workspace/folio-pdf/capabilities/evidence/T05-capability-provider-contract.md), [Foundation readiness](/workspace/folio-pdf/docs/foundation-readiness.md), ADR-0007, ADR-0023, ADR-0033, and ADR-0040.
- **Authorities:** `capabilities/{capability-matrix,facade-surface,foundation-release,foundation-requirements,foundation-environments,foundation-evidence}.yaml`.
- **Agreed validation seam:** public Workflow Environment/Request/Outcome, `DocumentWorkflow.execute`, public Provider requests/results/failures, actual subprocess and controlled loopback endpoint boundaries, public Facade operations, reopened files, Publication Receipts, ownership, and cleanup. No backend identity or private-call assertions.
- **Inspect first:** Provider catalog/base/metadata/value classes; `WorkflowEnvironment`, `DocumentWorkflow`, `SubprocessCapabilityProvider`; the three existing Provider tests and `ProviderProcessFixture`; `BlankDocumentFacadeTest`; `scripts/t03-foundation.py` and its tests; T03 independent qualification assets; `FoundationEvidence.java` and `FoundationReadinessCommandTest`; current transaction records; Provider API/jar integration tests; `PROVENANCE.md`, `DEPENDENCIES.md`, and `docs/zh-CN/getting-started.md`.

## Session recommendation

- **Session:** fresh implementation session after repository-write authorization.
- **Capability:** Advanced.
- **Intensity:** High.
- **Reason:** disclosure authorization, real subprocess/network lifecycle, two Workflow profiles, and strict candidate/environment evidence have interacting invariants where plausible mistakes can disclose content or falsely certify behavior.
