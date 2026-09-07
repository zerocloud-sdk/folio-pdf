# T30 one-dimensional barcode delivery record

Status: `experimental`  
Capability: `composition.barcodes.one-dimensional`  
Acceptance Profile: `T30-one-dimensional-barcodes`  
Release train: `0.1.0-SNAPSHOT`

Issue [#31](https://github.com/zerocloud-sdk/folio-pdf/issues/31), governed by
[parent specification #1](https://github.com/zerocloud-sdk/folio-pdf/issues/1).
Implementation and review base: `5508429ec032e8879d6d359a018f25d25efe878c` on
`main`. No commit, push, PR, merge, tracker mutation or publication is authorized.

Completion gate: **passed for the experimental T30 implementation**. All 25
handoff criteria are complete. The implementing agent marked them only after
the separate clean-context Standards and Spec reviewers completed their final
reviews of the validated worktree. Compatibility promotion remains gated as
recorded below.

The initial independent [Spec review](artifacts/T30-spec-review.md) found one
blocking AUTO/FNC4 numeric-run payload error outside the original fixed
fixtures. Its public Workflow regression failed in both profiles before the
repair and passed afterward. The [Standards review](artifacts/T30-standards-review.md)
found no hard violation and one optional geometry-readability observation.
Initial observations are explicitly retained as historical records. Current
acceptance, root verification and all four JDK matrix entries pass after the
repair. The [final independent reviews](artifacts/T30-independent-review-final.md)
close both axes with no unresolved hard violation or specification gap.

## Behavior and evidence

The [declared profile](../profiles/T30-one-dimensional-barcodes.md) contains
98 encoding cases and 18 geometry/text cases: 116 pages and 115 labels in each
execution profile. It covers every declared discrete variant of Code128
AUTO/A/B/C, GS1 and raw symbols, FNC1–4 including upper shifts/latches and literal
escape text, Code39/Full ASCII, Codabar, EAN13/8, UPCA/E, standalone/combined
supplements, ITF, MSI, POSTNET and PLANET.

Actual product PDFs are generated only through `DocumentWorkflow.execute`.
Independent PDF-path and raster readers use ZXing 3.5.3 and original GS1/MSI/USPS
readers. Checks, all bar corners, full glyph matrices, advances, visible text,
placement and rotation are observed from published products. The reference PDF
uses independent modules and Noto source metrics through existing Canvas/T19
commands; it never invokes barcode generation. Pinned PDFium renders at 288 DPI;
ImageMagick compares complete pages with AE 0, fuzz 0%. Negative controls remove
bars/labels, move or shear text, make text invisible, and corrupt actual raster
checks/parity. These controls must be rejected.

The [English contract](../../docs/one-dimensional-barcodes.md) records exact
inputs, all check modes, points/quiet zones/ratios/postal dimensions, labels,
encoder capacity and frontend bounds, stable failures, execution profiles,
atomic publication and incremental behavior. The
[Chinese guide](../../docs/zh-CN/getting-started.md) provides a Native Interface
example. Public types contain matching Javadoc.

Independent PDF standards evidence is **INDETERMINATE**. Required compatible
Canvas, font, resource-policy and worker gates and Foundation platform/font
certification remain open. These missing gates are not decoder gaps and are
not satisfied by syntax, pixels, tests or issue closure. The capability remains
experimental; no Migration Facade mapping or unsupported stable stub is added.

## Current recorded acceptance results

The post-review recording completed successfully in 20 minutes 51 seconds.
Current [syntax](T30-r2/T30-one-dimensional-barcodes-syntax.md),
[semantic](T30-r2/T30-one-dimensional-barcodes-semantic.md), and
[visual/raster](T30-r2/T30-one-dimensional-barcodes-visual.md) evidence pass.
Each execution profile has 116 independently decoded PDF pages, 116 decoded
actual rasters, 115 verified labels and 116 full-page AE-zero comparisons.
All four new numeric FNC4 variants participate in every applicable check.
[Standards](T30-r2/T30-one-dimensional-barcodes-standards.md) remain
INDETERMINATE, with the same open compatibility Dependency Gates.

The [current artifact audit](artifacts/T30-r2-artifact-audit.json) retains
713 new T30 files, including three PDFs and 696 PNGs, under `T30-r2/`.
All actual and reference PNG bytes match. All 318 source declarations match
current files; their manifest digest is
`363252a35e3942965c7300eb810243434d999ea8242eea4eb3680301197f625e`.
The audit also verifies that all 689 initial artifact hashes remain unchanged.
The [raw recording log](artifacts/T30-r2-actual-evidence-recording.txt) and
[new-fixture visual sample](artifacts/T30-r2-visual-sample.json) are retained.

## Initial recorded acceptance results (historical)

The initial recording command completed successfully in 21 minutes 9 seconds.
[Syntax](T30-one-dimensional-barcodes-syntax.md),
[semantic](T30-one-dimensional-barcodes-semantic.md) and
[visual/raster](T30-one-dimensional-barcodes-visual.md) chains all pass.
[Standards](T30-one-dimensional-barcodes-standards.md) remain indeterminate.
Both profiles pass 112 path decodes, 112 raster decodes and 111 label checks;
every visual comparison has AE 0. The
[artifact audit](artifacts/T30-artifact-audit.json) verifies all 318 source
hash declarations and retains identities for three PDFs and 672 PNGs.
[Raw recording output](artifacts/T30-actual-evidence-recording.txt) and
[two supplementary visual observations](artifacts/T30-visual-samples.json)
are retained. No pre-existing destination file was replaced.

## Reproduction and development history

After offline provisioning described in [the inventory guide](../README.md),
choose a new directory and run:

```text
./mvnw -B -ntp -pl pdf-acceptance -am -Pacceptance-t30-record -DskipTests \
  -Dacceptance.output=/new/t30-evidence-directory verify
```

Only `T30*` records and artifacts from that fresh output belong in this delivery.
Existing acceptance artifacts are retained. Ordinary Maven verification does not
require external raster tools; the explicit recorder executes their full chain.

[Development observations](artifacts/T30-development-validation.json) index
[100 saved logs](artifacts/T30-development-logs.txt): 47 numbered Red/Green pairs
and two first-green supplementary regressions, plus two test-maintenance Red/Green pairs. Missing declarations produce
compile-time Reds; behavioral Reds expose invalid publication, wrong geometry,
missing variants, weak observers or absent command output. Supplementary cycles
43/46 are not claimed as new Red/Green seams. The initial zero-test cycle-46
selector is excluded from pass evidence. Cycle 42 actually executed the former
216-page pinned raster inventory; final evidence uses the expanded inventory.

The [initial focused validation](artifacts/T30-focused-validation.json) public Workflow run passes 88 tests without skips. The initial
acceptance contract run passes its five enabled tests; its explicit full-raster
test is skipped in that ordinary invocation and covered by the actual recorder.
The command-line entry regression passes one test, including unavailable-tool
INDETERMINATE behavior. Root and container identities plus the explicitly
selected live HarfBuzz installation are retained in the
[environment record](artifacts/T30-verification-environment.json).

The first full root verification exposed a stale Worker protocol negative: token
17 became the valid barcode outcome. Only the negative fixture changed to an
unassigned integer; the complete 11-test Worker codec regression then passed.
The [first root attempt](artifacts/T30-root-verification.json) is retained as a
failed observation. No product/acceptance source or recorded PDF/PNG changed.

The [second root attempt](artifacts/T30-root-r2-verification.json) passed the
product, acceptance and facade modules, then exposed the inventory test’s stale
20-exclusion expectation. T30 adds the 21st explicit exclusion. Updating this
test and requiring T30 in both generated views passes all 17 inventory/release
tool tests. This attempt is also retained; product and acceptance sources remain
unchanged.

The [third root verification](artifacts/T30-root-r3-verification.json) runs the
complete `./mvnw -B -ntp verify` successfully: 1083 tests, zero failures/errors
and four skips (three existing optional scale tests and the separately executed
T30 pinned-raster test). All 787 non-evidence source/test/document declarations
remain byte-identical throughout the run. The
[raw root log](artifacts/T30-root-r3-verify.txt) records all reactor modules.

The initial complete [JDK matrix](artifacts/T30-jdk-matrix-verification.json) passes on
Temurin 8u502, 11.0.32, 17.0.20 and 21.0.12. Each executes 1083 tests with zero
failures/errors and the same four declared skips. The exact script runs without
version arguments, so all four gates execute in one invocation. Image identities
and all 787 non-evidence files remain unchanged; the build-input digest matches
the passing root run. [Raw matrix output](artifacts/T30-jdk-matrix-verify.txt)
retains every module result.

## Post-review remediation observations

The independent Spec finding was reproduced by a public Workflow test before
the repair: single FNC4 plus `123456` decoded as plain digits. Selecting
Okapi's existing A/B option for ordinary AUTO input containing explicit FNC4
preserves numeric upper shifts and latches. No dependency version changed.
The [focused independent follow-up](artifacts/T30-spec-review-fnc4-followup.md)
reran the original 240-case probe with zero failures and eight minimal
both-profile control pages with correct payloads. Permanent copies of the
reviewer's source and exact output are retained in the
[probe observations](artifacts/T30-independent-review-probe-observations.txt).

[Remediation cycles 52–54](artifacts/T30-remediation-validation.json) retain
six separate Red/Green logs: the public regression, the expanded independent
profile and CLI count maintenance. The current
[focused checks](artifacts/T30-r2-focused-validation.json) pass 90 public
Workflow tests, the five enabled acceptance tests, and one actual CLI entry
test. The sixth acceptance test is the explicit pinned-raster path, executed
by the separate recorder. The
[current recording JAR](artifacts/T30-r2-runtime-jar-inspection.json) contains
694 Java-8 class files and no bundled backend or decoder classes.

The expanded actual recording is complete and retained under `T30-r2/`.
The [preliminary Standards follow-up](artifacts/T30-standards-review-remediation.md)
finds no new hard violation or optional observation in the repair. The
[current full root verification](artifacts/T30-root-r4-verification.json)
passes 1085 tests with zero failures/errors and the same four declared skips.
All 787 non-evidence build inputs remain unchanged; their manifest digest is
`18bf5fd5285b998f0c27429aba5fa9f56ddf9fefdb0d6e92065b0520dd882826`.
The [raw current root log](artifacts/T30-root-r4-verify.txt) records the complete
reactor. The [current complete JDK matrix](artifacts/T30-jdk-matrix-r2-verification.json)
passes on JDK 8, 11, 17 and 21, each with 1085 tests, zero failures/errors and
four declared skips. The [raw matrix log](artifacts/T30-jdk-matrix-r2-verify.txt)
records the exact full script invocation. All 787 build inputs and all four
container image identities remain unchanged; the build-input digest matches
the passing current root run. The final independent reviews and workspace
closeout below cover this validated revision. The initial observations remain
unchanged.

## Independent review and workspace closeout

The [final Standards review](artifacts/T30-standards-review-final.md) finds zero
documented-standard violations and retains one optional acceptance-geometry
readability suggestion. The [final Spec review](artifacts/T30-spec-review-final.md)
finds zero unresolved specification gaps and zero scope issues; the initial P1
is resolved. Both axes are retained separately in the
[review aggregation](artifacts/T30-independent-review-final.md). The final Spec
reviewer also directly decoded the four new fixtures from both current PDFs and
their actual PNGs: 16 correct observations, with
[exact probe source and output retained](artifacts/T30-final-spec-probe-observations.txt).

The [final workspace audit](artifacts/T30-final-workspace-audit.json) verifies
the same 787 non-evidence build inputs, 318 current acceptance-source
declarations, all 713 current artifacts, all 689 initial artifacts, and unchanged
pre-existing evidence. It records that only the completed ledger, independent
review reports/probe copies and audit metadata changed after the review
snapshot. No product, test, contract, inventory or generated-view source changed
after successful verification. Task-specific Python bytecode was removed; no
nonignored task temporary files remain.

HEAD and local `origin/main` remain
`5508429ec032e8879d6d359a018f25d25efe878c` on `main`. There are no staged files or
new commits. The complete verified and reviewed T30 diff is intentionally left
uncommitted because no commit authorization was granted. No push, PR, merge,
tracker mutation or artifact publication was performed.

## Handoff criterion map

All rows were completed after the independent review gate passed. The evidence
links provide the per-criterion final report; experimental compatibility gates
are explicitly distinguished from completion of this implementation task.

| # | Criterion | Evidence |
| --- | --- | --- |
| 1 | [x] Complete mode inventory | [T30 profile](../profiles/T30-one-dimensional-barcodes.md) and `T30BarcodeProfile`: 98 encoding and 18 layout cases |
| 2 | [x] Native vector generation for every mode | [Public Workflow tests](../../pdf-document/src/test/java/net/zerocloud/pdf/consumer/BarcodeWorkflowTest.java); actual PDFs in [current semantic evidence](T30-r2/T30-one-dimensional-barcodes-semantic.md) |
| 3 | [x] Sizing, text, checksums and invalid-input contracts | [English contract](../../docs/one-dimensional-barcodes.md), public Javadoc and profile |
| 4 | [x] Independent payload decoding for every variant | [232 PDF-path decodes](T30-r2/T30-one-dimensional-barcodes-semantic.md) and [232 actual-raster decodes](T30-r2/T30-one-dimensional-barcodes-visual.md) across both profiles |
| 5 | [x] Independent check values | [Literal profile words/digits](../profiles/T30-one-dimensional-barcodes.md), independent decoder parity/check validation |
| 6 | [x] Modules and declared dimensions | [Every observed bar rectangle](T30-r2/T30-one-dimensional-barcodes-semantic.md) versus independent modules and transformed coordinates |
| 7 | [x] Text content and layout | [230 captions](T30-r2/T30-one-dimensional-barcodes-semantic.md) with per-character matrices/advances and [full-page pixels](T30-r2/T30-one-dimensional-barcodes-visual.md) |
| 8 | [x] Placement and rotation | [Literal matrices and coordinates](../profiles/T30-one-dimensional-barcodes.md); current path/text/pixel evidence |
| 9 | [x] Invalid symbols, lengths, modes and characters | [Public Workflow negatives](../../pdf-document/src/test/java/net/zerocloud/pdf/consumer/BarcodeWorkflowTest.java) in both profiles |
| 10 | [x] Failure/publication/receipt semantics | [Workflow tests](../../pdf-document/src/test/java/net/zerocloud/pdf/consumer/BarcodeWorkflowTest.java): unchanged targets, NOT_ATTEMPTED receipts and font/resource failure propagation |
| 11 | [x] Highest public seam and both profiles | [90-test focused receipt](artifacts/T30-r2-focused-validation.json): `DocumentWorkflow.execute`, published reopen Queries and worker transport |
| 12 | [x] Truthful Capability Matrix | [Authority](../capability-matrix.yaml): current T30 state, limitations, dependencies and evidence chains |
| 13 | [x] Separate Facade Surface and no unsupported stable stub | [Authority](../facade-surface.yaml): explicit T30 exclusion |
| 14 | [x] English/Javadoc/Chinese usage | [English contract](../../docs/one-dimensional-barcodes.md), [Barcode1D Javadoc](../../pdf-document/src/main/java/net/zerocloud/pdf/composition/Barcode1D.java), [BarcodeText Javadoc](../../pdf-document/src/main/java/net/zerocloud/pdf/composition/BarcodeText.java), README and [Chinese guide](../../docs/zh-CN/getting-started.md) |
| 15 | [x] Provenance, dependencies and notices | [PROVENANCE](../../PROVENANCE.md), [DEPENDENCIES](../../DEPENDENCIES.md), [NOTICE](../../NOTICE) and Okapi/ZXing manifests |
| 16 | [x] Reproducible actual evidence, identities and hashes | Recorder command, [713 retained current artifacts and source identities](artifacts/T30-r2-artifact-audit.json), PDFs/PNGs and raw tool logs |
| 17 | [x] Missing evidence stays INDETERMINATE; no decoder gaps | [Current standards record](T30-r2/T30-one-dimensional-barcodes-standards.md) and open dependency gates; complete 116-case independent decoding in both profiles |
| 18 | [x] Inventory validate | [Current inventory receipt](artifacts/T30-r2-inventory-verification.json) |
| 19 | [x] Inventory generate and check | [Current inventory receipt](artifacts/T30-r2-inventory-verification.json) and generated views |
| 20 | [x] Root Maven verify | [Current root receipt](artifacts/T30-root-r4-verification.json): 1085 tests, zero failures/errors, four declared skips; [raw log](artifacts/T30-root-r4-verify.txt) |
| 21 | [x] JDK 8/11/17/21 matrix | [Current complete matrix receipt](artifacts/T30-jdk-matrix-r2-verification.json): each JDK has 1085 tests, zero failures/errors and four declared skips; [raw log](artifacts/T30-jdk-matrix-r2-verify.txt) |
| 22 | [x] Independent Standards/Spec review on fixed base, all changes | [Final separate reports](artifacts/T30-independent-review-final.md): Standards 0 hard violations / 1 optional suggestion; Spec 0 unresolved gaps / 0 scope issues. [Full snapshot](artifacts/T30-final-review-scope.json) covers tracked and untracked changes |
| 23 | [x] T30-only diff and no temporary work files | [Final workspace audit](artifacts/T30-final-workspace-audit.json): preserved source/artifact identities, task-only scope and no nonignored task temporary files |
| 24 | [x] Commit authorization honored | [Final workspace audit](artifacts/T30-final-workspace-audit.json): HEAD and origin/main stay at the fixed base, no staged files or commits; validated and reviewed diff left uncommitted |
| 25 | [x] Per-criterion final report | This completed 25-row record, linked observations and separate review conclusions provide the detailed delivery report and actual commit state |
