# T72 implementation work log

Status: in progress. This is development evidence, not a completion receipt or
candidate certification. No completion criterion has been marked satisfied.
The complete goal remains the maintainer's #72 instruction, including all
Foundation page mappings and eight candidate-bound environment/execution tuples.

## Baseline and scope

- Fixed review baseline and starting HEAD: `e5053749e35e517fe74f81bd3ebb39d8b41e28ce`.
- Initial branch: `main`, tracking `origin/main`; the worktree was clean.
- Read #72/#33/#1/#11 and their comments with `gh`; #70 is CLOSED. No tracker
  mutation, commit, push, PR, merge or publication is authorized.
- Read repository contribution/domain instructions and ADRs
  0004/0005/0011/0013/0017/0020/0023/0025/0029/0040.
- Keep all existing Native page implementations. The current 52-case
  `PageManipulationWorkflowTest` run passed with zero skips
  (`development/native-baseline.txt`). The six Commands, one-based inclusive
  ranges, post-removal move positions, original-sequence copy positions, exact
  split Target coverage, terminal split and ordered receipts remain authoritative.
- Foundation page, merger and splitter mappings and T10 independent evidence
  remain unfinished. Page mapping member/ownership design must be frozen before
  implementing those mappings. Existing Stable/Preview Values mappings and
  source snapshot ownership must continue to work.

## T03/T09 baseline diagnosis

[CI run 34289416825](https://github.com/zerocloud-sdk/folio-pdf/actions/runs/34289416825)
has four failed JDK jobs. Its ordinary workflow installs HarfBuzz but not the
independent qpdf, pdfcpu, Arlington, PDFium or ImageMagick tools. The failing
T03/T09 tests unconditionally required those tools during ordinary verification.

Reproduction used an isolated `/tmp/t72-no-tools` root containing the unchanged
T03 corpus, golden image, visual profile, five tool pins and `scripts/container-bin`
wrappers, with no tool caches. No repository pin was changed. This command
produced the same three INDETERMINATE chains and passing semantic chain:

```sh
./mvnw -B -ntp -pl pdf-acceptance -am \
  -DrepositoryRoot=/tmp/t72-no-tools \
  -Dtest=T03EvidenceCommandTest#recordsFourIndependentChainsForNativeAndStableProducts \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Ranked hypotheses were missing tool provisioning, mismatched tool identity/path,
and JDK-specific product behavior. Restoring only the isolated qpdf executable
path made only syntax PASS; standards and visual remained INDETERMINATE.
The unchanged full-tool baseline passed both positive T03/T09 recorder tests.
Logs: `development/no-tools-ci-red.txt`, `development/qpdf-only-probe.txt`,
`development/evidence-baseline.txt`.

The selected fix separates the existing real-tool tests with the JUnit
`IndependentTools` category and the explicit Maven `independent-certification`
profile. Ordinary tests still exercise missing tools; no missing-tool assumption
or synthetic checker supplies certification. The direct evidence commands and
Foundation runner always require the real chains, independently of this profile.

A second observed defect occurred when the qpdf executable itself was absent:
T03 aborted during its syntax negative control before writing the final
INDETERMINATE result. The public-command regression was RED before the fix
(`development/missing-executable-red.txt`). The minimal fix retains execution
failure/limit diagnostics and continues the other negative-control observations;
the test then passed (`development/missing-executable-green.txt`). The isolated
test-configuration setup was deduplicated after that pass and explicitly selects
missing paths so host provisioning cannot accidentally qualify a control.

After selection was wired, the ordinary no-tools T03 test ran and passed one
actual unavailable-tool regression, with zero skips
(`development/ordinary-no-tools-green.txt`). Explicitly selecting independent
certification in the same environment still failed with INDETERMINATE tool
chains (`development/opt-in-no-tools-rejected.txt`). These are development
selection checks, not evidence for the final candidate.

The actual provisioned tools then passed the focused explicit profile:

```sh
./mvnw -B -ntp -Pindependent-certification -pl pdf-acceptance -am \
  -Dtest=T03EvidenceCommandTest,T09EvidenceCommandTest,StandardsEvidenceCommandTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

All 17 tests passed, zero failures/errors/skips, in the current worktree
(`development/certification-tools-focused.txt`). This closes the local diagnostic
loop. The remote CI configuration fix still requires the final full build and
default JDK matrix, and these recorder tests do not certify T10 or a changed
candidate. The [interim independent review](wiring-review.md) found no actionable
Standards or Spec issues in this wiring. Final review of the complete #72 change
is still required.

## Native page execution selection

The baseline page suite hardcoded all requests to the default IN_PROCESS mode.
Adding assertions on the public creation/reopen `WorkflowOutcome` and running
with `-Dfolio.t10.executionProfile=HARDENED_WORKER` failed with
`expected:<HARDENED_WORKER> but was:<IN_PROCESS>` before request selection was
implemented. All page-suite requests now go through a test-only builder that
selects this property, defaulting to IN_PROCESS. The six production Commands are
unchanged. Both actual mode runs must pass before these tests can join T10's
candidate certification; a property or matrix label alone does not prove mode.

The focused Worker tracer then passed. The complete Worker-selected page suite
passed all 52 tests with zero failures/errors/skips; raw output is retained in
`development/native-worker.txt`. The RED/GREEN tracer logs are
`development/native-mode-red.txt` and `development/native-mode-green.txt`.
These runs use the development host, not the four pinned certification images.
The subsequent IN_PROCESS run also passed all 52 tests with zero
failures/errors/skips (`development/native-in-process.txt`). Both full runs used:

```sh
./mvnw -B -ntp -pl pdf-document -am -Dtest=PageManipulationWorkflowTest \
  -Dfolio.t10.executionProfile=IN_PROCESS \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

The Worker invocation substitutes `HARDENED_WORKER` for the profile value.
`./scripts/inventory validate` passed with the unchanged 23 capabilities,
89 Facade surfaces and 21 exclusions. `git diff --check` passed. No final build,
four-JDK matrix, T10 certification or final full-goal review has been run yet.
The Native test selection and current-status T10 prose correction postdate the
interim wiring review and must be included in the final independent review.

## Page mapping constraints located

The actual Facade owns a single Native Session from its first value operation
until document close (`docs/document-values.md`). It must retain those Values
view and ownership guarantees while adding page operations. Native named Sources
and Targets are immutable Workflow Request declarations. Merge consumes whole
named Sources, and range selection must preserve safe metadata/annotation
retargeting. Split must publish all declared Targets in one Native transaction to
retain declaration-ordered failure receipts. Silently restarting the Facade
Session, manually copying backend graphs, or splitting publication into unrelated
transactions would weaken existing contracts and is not an accepted design.

## Current delivery state

The page/merger/splitter member and ownership contract is frozen in
`docs/page-manipulation.md`. The bounded Facade, exact jar contracts, original
corpus, qualified standards union, four evidence chains, negative controls,
Foundation pages runner, machine authorities, and English/Chinese/provenance
documentation are implemented. These remain development results until the final
candidate is staged and all eight Native tuples are retained. No completion
criterion has been marked satisfied.

## Facade development slices

The codebase-design comparison considered constructor-declared named Sources and
Targets, a preparation-phase familiar-call adaptation, and a flexible
current-state import design requiring three Native extensions. The selected
contract declares all Sources and Targets upfront, retains one Native Session,
copies ranges within that Session, merges selected complete named Sources, and
splits the complete Target group in one transaction. It makes no claim for late
Source registration, current-state cross-document copying, or independent open
split-document lifecycles. The six Native Commands remain unchanged.

`PdfDocument` maps indexed insertion, page selection, single/range removal,
single/range movement and range copying. `PdfPage.getPdfObject()` exposes the
existing Values interface and retains page identity through reordering. Queued
blank-page handles bind before mutation. `FacadeDeclarations` validates all
declarations and Reader availability before ownership transfer, copies map
iteration order, reserves aggregate snapshot storage, and owns cleanup. Named
merger and splitter abstract views are document-owned; no public Session callback or
backend bridge is exposed. Actual immutable Native receipts are observable after
close, including a partially written failing stream.

Insertion, reorder/copy/remove, named merge, and complete split groups each
failed at their missing public boundary before passing. An interim independent
Standards probe then exposed a queued-handle initialization failure that could
leave the Native callback waiting. The regression failed with no receipt before
the correction; initialization failures now escape the callback, allow Native
cleanup to finish, and attach the actual receipt. All 46 related lifecycle,
Values, and page Facade tests passed. The independent correction probe confirmed
one receipt, no publication, and no remaining Facade worker. The bounded
[Standards](facade-standards-review.md) and [Spec](facade-spec-review.md) reviews
have no unresolved findings; neither replaces the final full-goal review.

Actual Stable and Preview artifacts are checked against exact hardcoded class and
method sets. The Stable manifest contains all 17 T10 mappings; Preview contains
the Stable superset and has no additions. Both mixed-classpath jar orders reject.
The manifest identifies direct mappings, adapted shapes, and Folio extensions;
the named declaration constructor, utility factories, and receipt accessor do
not invent Reference Suite member names.

## Original corpus and checker qualification

`scripts/generate-t10-corpus.py` passed public CLI RED→GREEN slices before
creating three PDF Sources and seven mathematically authored opaque-sRGB rasters
under `capabilities/profiles/T10-pages`. JSON SHA-256 is
`7044a1036e15a0c32465e1d818f5b38f37cfec0699ce1fac9d86c7927ab94c65`;
Java-8-readable Properties SHA-256 is
`e2777799c86dcfba25173fd3ed48cf99bc9d2472c68a2ad6dd5efc0320466293`.
Pinned PDFium rendered the five nonblank source pages at 144 DPI and ImageMagick
reported AE 0 against every preauthored raster. No expected image derives from a
product or renderer observation.

`scripts/generate-t10-standards.py` passed generator, family, and byte-order
RED→GREEN slices before creating one valid PDF and 56 isolated illegal
structures. They combine with 28 reused T03/T09 rules into an 84-rule union: 68
pdfcpu and 16 Arlington. Public ISO 32000-1:2008 Tables 28–36, 95, 151, and 164
provide the normative object rules.

The original 116 real pdfcpu/Arlington invocations accepted positive inputs and
exposed four missing diagnostics: destination array length, destination page
kind, name-tree ordering, and name-tree uniqueness. The rules were retained. An
Apache-2.0 acceptance-only Arlington source patch added those checks plus
high-byte ordering and alternate-encoding uniqueness controls under the distinct
version `0.81-folio-t10-r1`. Patch identity, executable identity, upstream source
commit/archive/model, all 122 follow-up invocations, unchanged input hashes, and
raw diagnostics are retained. `T10StandardsQualificationTest` passes all 84
assignments; a changed patch/profile identity, missing rule, or absent executable
cannot produce PASS.

## Native preservation correction and product evidence

The first complete semantic reopen exposed an observed Native gap: copying a
legacy Text annotation introduced PDFBox's default `/Name` entry. A new public
workflow regression was RED. `PdfBoxAnnotationPageOperations` now restores the
source annotation dictionary key scope after managed reconstruction, preserving
only the required `/P` retarget and `/NM` collision rename. That focused
regression and the full 53-case Native suite pass in both actual execution modes.

The T10 producer builds four exact products for each interface: edited, merged,
left, and right. Semantic reopen verifies page order, boxes, rotation, decoded
content and Form programs, annotations, destinations, collisions and isolation.
Pinned qpdf verifies syntax; qualified pdfcpu/Arlington verifies standards;
pinned PDFium plus ImageMagick verifies every fixed page at zero changed pixels,
with PDFBox only as secondary disagreement evidence. A real invalid PDF,
same-length wrong-order product, all 84 illegal standards controls, and a
one-pixel raster change must fail. The full actual-tool development command
passes all eight products and four chains.

## Foundation and authorities

Strict script tests added the `pages` obligation to `t03-foundation.py`. Each of
the four JDK images runs Native IN_PROCESS and HARDENED_WORKER, producing eight
tuples. A tuple runs 53 Native page tests under its selected actual mode, 10
Facade tests in fixed IN_PROCESS, and two actual-jar contract classes: 65 tests,
zero skips. Collection requires all eight exact PDFs, every chain report, all
per-page rasters/differences, complete checker/control trees, wrong-order PDF,
invalid PDF, and visual negative artifacts. Tool identity records include qpdf,
pdfcpu, original Arlington, Arlington T10 r1, PDFium, ImageMagick, and the three
project producers. Common tool identities remain stable while refreshing T03
and T09 on the same candidate.

The Capability Matrix declares T10 compatible, four passing aggregate records,
17 Stable mappings, four certified Ubuntu/JDK platform IDs, and no open T10
promotion gate. Foundation pages has no release-blocker limitation. Inventory
validation and generated-document drift checks pass. The final Foundation
Evidence authority contains exactly 24 certifications and 96 chain records:
eight tuples each for pages, refreshed transactions, and refreshed values. The
four environments and twelve IN_PROCESS/twelve HARDENED_WORKER records all bind
to the same staged candidate and contract identities.

## First candidate superseded by final-review correction

The first clean-context Standards review reported no findings. The independent
Spec review found that the two newly mapped utility types were absent from the
mixed-classpath probe and could initialize from both jar orders. The accepted
finding, strict RED/GREEN/refactor evidence, correction, and focused passing
tests are recorded in `first-final-review.md`.

Because the correction changes shipped code and the declared Facade surface,
the first stage receipt
`fa13cddedcc6314793a4aa52b4c268bc41d6785516b1bccd07b2966648e46695`,
its three Foundation obligation certifications, root verify, JDK matrix, and
first review verdicts are superseded. The corrected inventory validates 23
capabilities, 105 exact Facade surfaces, and 20 exclusions. A fresh candidate,
all 24 Foundation tuples, full root and JDK validation, and a second independent
two-axis review remain required. No commit, push, PR, issue mutation, merge, or
release has been performed.

## Second candidate superseded by root-test correction

The corrected utility-class candidate staged as
`69948a3c5679ea9333fe725903c01d7e55fe85455c4c437340ef14d2bf11988`
with contract identity
`0e8cc59037963c606a3f8a6fb823650f0e51a68936f0da0b65b08227cb94606c`.
All 24 certifications completed, but the subsequent full root verification
found four stale inventory test expectations: 103 Facade entries instead of
105, and 15 page mappings instead of 17. The product and authority values were
correct. The complete failing root receipt is retained in
`development/root-after-review-red.txt`.

`InventoryCommandTest` now asserts the exact current totals. Its focused suite
passes 7 tests with zero failures, errors, or skips; inventory validate,
generate, and check also pass with 23 capabilities, 105 Facade surfaces, and 20
exclusions. The focused receipts are
`development/inventory-count-after-review-green.txt` and the three
`development/inventory-*-after-count-fix.txt` logs. This test-source correction
changes the staged input set, so the second candidate and every certification
bound to it are superseded despite their successful executions. A third stage
and all 24 fresh certifications are required. No completion criterion is marked.

## Third candidate superseded by second-review documentation correction

The third staged candidate completed all 24 Foundation certifications and the
root/JDK gates. The second clean-context Standards review then found stale
human-readable Facade totals in `capabilities/README.md`,
`docs/t03-certification.md`, and `docs/t09-certification.md`. Those documents
now state the declared 19 public types, 105 exact members, and 17 T72 mappings.
Because the documentation is a staged source input, its correction supersedes
the third receipt, candidate, certifications, and gate logs. The Spec review
reported no separate implementation defect but required fresh current-candidate
gates and an updated worklog before a final verdict.

## Fourth candidate and final gates before final review

The corrected documentation is included in the fourth staged candidate. Its
stage receipt SHA-256 is
`31811b6a861a81aead269e4b162eed1164231e6909d4ec20729c983001a90daf`:
1,067 candidate inputs, 23 required artifacts, 30 contract inputs, and 24
acceptance-harness inputs. The candidate identity is
`2f82ec56970be1a8ce70b4d9a7e2288505bed6e20c3123cb736ed5b0003ef648`;
the contract identity remains
`0e8cc59037963c606a3f8a6fb823650f0e51a68936f0da0b65b08227cb94606c`.

Fresh pages, transactions, and values certification runs each completed all
four pinned Ubuntu/JDK environments and both required Native execution modes.
The authority has exactly 24 certifications and 96 passing chain records:
eight certifications per obligation, twelve IN_PROCESS and twelve
HARDENED_WORKER, and six records per environment. Every record binds the same
candidate and contract. The staged-input and full referenced-file hash audit
passes in `final-authority-and-stage-audit.txt`.

The first fourth-candidate root run correctly detected stale generated
`docs/generated/foundation-readiness.md`; the complete failure is retained in
`development/root-after-final-readiness-stale-red.txt`. That file is the one
declared source-identity exclusion, so regenerating it did not change the
candidate or invalidate its certifications. The replacement explicit-HarfBuzz
root `./mvnw -B -ntp verify` passed all ten reactor modules in 30:10.
Independent parsing of 78 class results gives 1,355 tests, zero failures, zero
errors, and four existing optional skips: three Worker scale cases and one T30
external-tool case. Its complete log SHA-256 is
`4d9f4c50d04a20ed7ec2a2c0e649b4f9d198a256d466df91bbdf8228a31958e0`.

The default pinned JDK 8/11/17/21 matrix passed four complete verifies. Each
environment independently has 78 class results, 1,355 tests, zero failures,
zero errors, and the same four optional skips. Their Maven times were 35:45,
33:42, 31:39, and 32:25 respectively. The complete sequential matrix log
SHA-256 is
`594d408bbf55d31636c1d2aff6ba6d5e810a91bf5be45cb65e83e5c17119ebb1`.

Final inventory validation and drift checking pass with 23 capabilities, 105
Facade surfaces, and 20 exclusions. Two consecutive generation runs produced
identical hashes for all three generated documents. Readiness exits one as
required: transactions #70, values #71, and pages #72 are SATISFIED; all
remaining blockers are future obligations beginning with metadata #73, so the
overall Foundation release remains NOT READY.

The required clean-context [Standards](final-standards-review.md) and
[Spec](final-spec-review.md) reviews of the complete current worktree both
report **No findings**. They independently verified the fourth candidate,
staged inputs and artifacts, all 24 certifications and 96 passing records,
actual Stable/Preview surfaces and mixed-classpath rejection, root and JDK
gates, inventory/readiness, documentation, provenance, ownership, and scope.
No completion criterion is marked in this record, and no commit, push, PR,
issue mutation, merge, publication, or release action has been taken.
