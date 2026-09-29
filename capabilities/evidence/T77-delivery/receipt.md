# Issue #77 execution receipt

Status: COMPLETE. Every execution Completion criterion in the sole contract is
satisfied. The result is reviewable, uncommitted work on `main`.

The final Foundation index contains 64 fresh r4 tuples and 256 passing chain
records for incremental and the seven previously certified obligations. Live
readiness and the generated report mark all eight obligations satisfied, with
no release-wide identity blocker. Unrelated obligations still block the parent
release, as expected by the contract.

## Scope and baseline

- Sole contract: `/workspace/contracts/issue-77-contract.md`.
- Baseline/start HEAD: `cd978a5cb311211c71050024d067f8d1d2dfacec`.
- The initial worktree was clean; no branch or commit has been created.
- The work implements only #77 and refreshes existing Foundation evidence.
- No push, PR, merge, issue edit/closure or release publication was performed.
- Certification is limited to the four pinned Ubuntu 24.04 Linux x86-64
  environments, each with Native IN_PROCESS and HARDENED_WORKER. The actual
  Facade mode is IN_PROCESS.

## Implementation

The Stable and inherited Preview Facades expose six explicit append-selection
mappings through `StampingProperties` and `PdfDocument`. Reader/writer append
inherits the Source version; existing REWRITE defaults and explicit named
versions remain intact. Native publication, signature permission intersection,
ownership and safe failures are reused.

Two demonstrated Native gaps were closed: critical indirect DocMDP parameters
now retain structural-failure identity, and empty annotation selections cannot
manufacture a signed revision. Public Native/Facade tests cover the 88-case
original corpus, all four permitted non-Widget annotation operations, denial
under additional restrictions, malformed evidence and policy bounds.

Independent syntax, standards, semantic and visual observers inspect actual
products against original literal expectations. Qualified negative controls,
raw process findings and immutable identities are required by the collector.
These are revision-preservation and conservative parsed-graph protection
claims, with no cryptographic validity, trust or Forms claim.

## Validation environment and commands

The local host is Debian 13 and inherits an X11 display. An initial broad host
run encountered sandbox Unix-domain socket restrictions and an unavailable X11
connection. That run was stopped and is not counted as passing. Repository
scripts provisioned the complete explicit HarfBuzz installation, pinned
acceptance tools, Ubuntu Python 3.12 runtime and PyYAML. Broad validation then
used the declared immutable Ubuntu images with no inherited display.

Maven uses `.build-cache/maven/repository`. The matrix script's supported
`PODMAN_COMMAND` hook supplies separate target mounts for each JDK while
mounting source read-only. Two JDK jobs run concurrently; each executes the
script's unchanged full `./mvnw -B -ntp -Dmaven.repo.local=... verify` command.
Original commands and outputs are retained with the validation records.

| Check | Command / selection | Result |
| --- | --- | --- |
| Initial smallest Native gate | `./mvnw -B -ntp -pl pdf-document -am -Dtest=IncrementalSignatureWorkflowTest -Dsurefire.failIfNoSpecifiedTests=false test` | PASS, 16 tests before implementation; observed in the execution session. |
| Focused public behavior | `IncrementalSignatureWorkflowTest,IncrementalSignatureMatrixTest,IncrementalSignatureFacadeTest,T15IncrementalProductsTest` in pinned JDK 17 | PASS, 36 tests; [log](validation/focused-inprocess.txt). |
| Native Worker behavior | Native 22-test selection with `folio.t15.executionProfile=HARDENED_WORKER` | PASS; [log](validation/focused-worker.txt). |
| Packaged Stable/Preview surface and runtime contracts | `-pl pdf-migration-itext7,pdf-migration-itext7-preview -am` focused `verify` | PASS; [log](validation/facade-artifacts.txt). |
| Inventory checker | `./mvnw -B -ntp -pl build-tools/inventory test` | PASS, 17 tests; [log](validation/inventory-tests.txt). |
| Recorder/collector regression | `python -m unittest scripts.tests.test_t03_foundation scripts.tests.test_t09_foundation.EvidenceIndexMergeTest scripts.tests.test_t14_foundation scripts.tests.test_t15_foundation` | PASS, 34 tests; [final log](validation/python-foundation-final.txt). |
| Independent tools | `./mvnw -B -ntp -pl pdf-acceptance -am -Pindependent-certification test` in pinned Ubuntu/JDK 17 | PASS for unchanged Java inputs, 1,493 reported tests, zero failures/errors, four conditional skips; [log](validation/independent-tests-final.txt), [result](validation/independent-result-final.json), [exact container command](validation/independent-r4-command.json). |
| Full/JDK matrix | `./scripts/verify-jdk-matrix.sh 8`, `11`, `17`, `21`; each runs unfiltered `./mvnw -B -ntp -Dmaven.repo.local=... verify` | PASS for unchanged Java inputs on all four JDKs, each 1,559 reported tests, zero failures/errors, four conditional skips; [results](validation/matrix-results-final.json), [JDK 8](validation/matrix-jdk8-final.txt), [11](validation/matrix-jdk11-final.txt), [17](validation/matrix-jdk17-final.txt), [21](validation/matrix-jdk21-final.txt). |
| Final staging/certification | `scripts/t03-foundation.py stage` then `certify capabilities/evidence/T77-<obligation>-r4 --obligation <obligation>` for all eight selected obligations | PASS, 64 tuples, 256 independent chain records and 4,016 required consumer/artifact test executions; [summary](validation/final-certification-summary.json), [driver](validation/final-candidate-driver-r4.log). |
| Inventory validation/generation/check/readiness | `./scripts/inventory validate`, `generate`, `check`, `readiness` | Validate/generate/check PASS. Live readiness reports all eight selected obligations SATISFIED; exit 1 is solely for 31 unrelated obligations. [Results](validation/final-command-results.json), [live readiness](validation/readiness-final.txt). |
| Final candidate/workspace audit | `verify-delivery-r4.py`, `git diff --check` and fixed-baseline/worktree checks | PASS: actual staged bytes, source/contract, all tuples, actual modes, baseline and historical evidence checked; [certification audit](validation/final-certification-audit.txt), [workspace audit](validation/worktree-audit-final.json). |

The four conditional skips are the three opt-in `HardenedWorkerScaleProfileTest` workloads (pages, one-GiB input and concurrency), and T30 `pinnedRastersDecodeAndMatchEveryDeclaredVariant`, which requires `-Dt30.raster=true`. The ordinary build also excludes the repository's `IndependentTools` category; the separate passing independent-certification run includes that category. T15 certification does not skip its required controls or visuals.

The [broad-validation source/contract record](validation/source-inputs-r3.json) and [final source/contract record](validation/source-inputs-r4.json) each retain 2,168 source inputs and 30 contract inputs. Their only source difference is the restored T03 runner count described below; all contract inputs match.

The focused commands preceded a strengthening of existing structural-refusal
assertions. The complete matrix and final certification execute the final
assertions. No production behavior changed after those focused passes.

The first staged attempt (`T77-incremental-r1`) passed its 36 public/artifact tests but caught a pre-launch acceptance timeout configuration error; it published no certification. The new public recorder regression reproduced that error before the fix, then passed all three tests including all independent chains and controls after adopting the existing 300,000 ms maximum. See [red](validation/recorder-entry-red.txt), [green](validation/recorder-entry-green.txt), and the review record. All four JDK gates and the full independent-certification suite passed again for this two-file acceptance-only change. The r2 attempt used restaged artifacts.

## Review

Separate Standards and Spec reviews used the fixed baseline plus new files.
The sole P1 finding (empty signed annotation update) was reproduced, fixed and
rechecked. See [review](review.md) and the before/after public probe logs.
The final fixed-baseline worktree comparison, whitespace and scope audits pass.
Separate final Standards/Spec checks of the authorities and generated readiness
found no unresolved issue.

## Completion criteria

- [x] AC1: Unsigned explicit append preserves the complete immediate Source,
  older revisions, protected content and reopened semantics. Public tests cover
  Source forms, ownership, missing primary Source, SplitDocument and admission.
- [x] AC2: Six explicit Stable/Preview append-mode mappings are implemented and
  exercised against packaged artifacts; REWRITE defaults remain protected.
- [x] AC3: The 88-case original restriction matrix, exact/first-excess fixtures,
  four admitted P=3 Annotation operations and permission intersections pass.
- [x] AC4 and AC3–4: Structural and policy failures remain distinct and fail
  before publication with unchanged Sources/Paths, zero stream writes and
  ordered NOT_ATTEMPTED receipts. Signed queries remain available; unsupported
  commands, signed REWRITE and publication without admitted mutation are denied.
- [x] AC5: All eight final incremental tuples have four independent passing
  chains, qualified controls and complete candidate/contract/environment/tool
  binding; collector/index regressions reject tampering and false claims.
  See [tuple evidence](incremental-tuples.md).
- [x] AC6: Native public Workflow tests run in both modes; Facade public tests
  cover reopening, receipts, order, ownership, lifecycle and safe mapping.
  Both product and observer records report actual Facade IN_PROCESS execution.
- [x] AC7: Authorities, generated contracts, English/Chinese usage and provenance
  are synchronized. Live and generated readiness show incremental and all seven
  prior obligations satisfied with valid final-candidate evidence. Only the four
  approved environments are certified; tracked historical records are unchanged.
- [x] AC8 initial gate: The required smallest Native selection passed before
  implementation, followed by focused Native/Facade/Worker and artifact checks.
- [x] AC8 broad gates: Full verification on JDK 8/11/17/21, the independent suite,
  inventory checker tests and final Python regressions pass. The isolated runner
  correction and explicit applicability proof are documented below.
- [x] AC8–9: Separate Standards/Spec reviews have zero unresolved findings.
  Java 8 runtime payloads, backend-free signatures, explicit ownership and
  permissive clean-room provenance are retained. Acceptance tools, original
  fixtures and fonts remain outside product runtime; no downstream feature is
  introduced. See [runtime audit](validation/java8-product-audit.json).
- [x] Final fixed-baseline diff, inventory/readiness and workspace audits pass.
  HEAD remains the baseline on `main`; the worktree contains only this ticket's
  changes and authorized evidence. No staged changes or unrelated edits exist.
- [x] No branch or commit created. No push, PR, merge, tracker mutation, signing,
  deployment or release publication performed.

Historical evidence is preserved and is never relabelled for this candidate.
The compiler-only annotations in the handoff do not waive execution gates;
actual checks and repository-conditional skips are recorded above. Push and
tracker closure remain reserved for the orchestrator.

## Retained attempts and resolved validation failures

All eight r2 incremental tuples passed their 36 consumer/artifact tests and
four independent chains, but index publication correctly failed on an
ambiguous local-filename field inside the original product manifest. That
attempt is not current certification. The author, recorder and collector now
use `file` for corpus-local control filenames. The existing recursive index
checker is unchanged. Fresh development observations rebuilt the protocol
fixture, and 34 recorder/index regressions passed; see
[the reproduced index failure](validation/index-input-red.txt) and
[the final Python result](validation/python-foundation-final.txt).

The r3 source and contract were frozen and staged. Initial r3 JDK 8/11 and independent-suite runs overlapped the
staging `clean` operation. Staging replaced host target directories after
container mounts were created; inode inspection showed the affected
container class directory resolving to the rebuilt host directory rather
than its requested private target. Worker inventory checks rejected those
transient/incomplete outputs. These affected runs are retained and excluded
from passing validation. JDK 17/21 containers created after staging retained
their private mounts. JDK 8/11 and the independent suite passed after
staging, without a product change or relaxed Worker validation.


## Final runner correction and validation applicability

The r3 incremental index publication succeeded. Its subsequent transactions
refresh executed all 34 required tests but rejected an accidentally changed
expected count of 36. The runner now requires the original 34; a
[baseline audit](validation/prior-profile-baseline-audit.txt) confirms all seven
prior raw/resolved profile definitions are exactly unchanged from baseline.
The full Python regression selection passed again (34 tests). No Java, corpus,
observer, build or contract source changed.

The [direct payload comparison](validation/validation-applicability-r4.json)
checks the actual r4 staged JARs, rejects duplicate or unexpected entries, and
records generated manifest/POM metadata separately. All 1,517 compiled classes,
resources and test files match the completed JDK 17 full-verification outputs;
shared modules also match the completed independent-suite outputs. All 30
contract inputs and 698 of 699 harness inputs are unchanged, including both
Native and Facade test JARs. Runtime, dependency and tool identities match.

The rebuilt product/acceptance archives have different hashes. Archive byte
identity and a post-correction Maven rerun are **not** claimed. Both reviewers
confirmed that the prior broad checks remain applicable to the unchanged
executable inputs. The Python regressions exercise the count correction, and
all 64 certifications passed afresh against the r4 archive identities.

## Final candidate and evidence

- Candidate: `2d8ef578da6599357ba0a1f47e7a42e8f9b2f641de1e23f21e43eb1e981ab556`.
- Contract: `3ee6319a39c8c66d3833a1d07bddb7d099a18cbdfaba803364637eb052014713`.
- The [final build receipt](validation/final-stage-inputs-r4.json) binds actual
  staged artifacts, the complete test harness, 2,168 source inputs and 30
  contract inputs. The final audit rechecked these bytes against the worktree.
- Every row below covers Ubuntu 24.04 Linux x86-64 on JDK 8/11/17/21, each with
  Native IN_PROCESS and HARDENED_WORKER. Incremental product and observer records
  separately confirm Facade IN_PROCESS in all eight tuples.

| Obligation | Tuples | Required tests per tuple | Passing chain records | Execution log |
| --- | --- | --- | --- | --- |
| incremental | 8 | 36 | 32 | [T15](validation/certify-incremental-final.txt) |
| transactions | 8 | 34 | 32 | [T03](validation/certify-transactions-final.txt) |
| values | 8 | 83 | 32 | [T09](validation/certify-values-final.txt) |
| pages | 8 | 65 | 32 | [T10](validation/certify-pages-final.txt) |
| metadata | 8 | 73 | 32 | [T11](validation/certify-metadata-final.txt) |
| annotations | 8 | 47 | 32 | [T12](validation/certify-annotations-final.txt) |
| text | 8 | 126 | 32 | [T13](validation/certify-text-final.txt) |
| images | 8 | 38 | 32 | [T14](validation/certify-images-final.txt) |

The [incremental tuple table](incremental-tuples.md) links exact configurations.
The [authoritative index](../../foundation-evidence.yaml) links every record,
report, observation and control. Staging and certification commands above are
the recorded executions; a later rerun must use fresh output directories.

## Residual scope and handoff

No unresolved #77 blocker remains. The protection boundary is the documented
parsed graph; placeholder signatures do not establish signature authenticity,
cryptographic validity or trust. Windows and macOS remain uncertified. Four
repository-conditional broad-suite skips are identified above; no required T15
certification test, observer or control was skipped.

Global Foundation readiness is NOT READY for 31 excluded obligations, with 405
diagnostics retained in [the diagnostic record](validation/remaining-readiness-diagnostics.json):
`acceptance`, `barcodes-1d`, `barcodes-2d`, `candidate`, `canvas`, `docs`,
`environments`, `facade-artifacts`, `fonts`, `graphics`, `integration`,
`java-artifacts`, `limits`, `pagination`, `paragraphs`, `password-attachments`,
`password-baseline`, `password-clear-metadata`, `provenance`, `providers`,
`publication-controls`, `recovery`, `rendering`, `reproducibility`, `security`,
`shaping`, `supply-chain`, `tables`, `tables-base`, `tables-pagination`, `worker`.
These are parent-release or downstream requirements, not reopened #77 work.

Commit: none. Branch and HEAD remain `main` at
`cd978a5cb311211c71050024d067f8d1d2dfacec`. The changes, original corpus and fresh
evidence remain in the worktree for review. No approval or product decision is
needed to review the completed result. Push, PR, merge, issue closure and release
publication are left to the orchestrator; none was performed.
