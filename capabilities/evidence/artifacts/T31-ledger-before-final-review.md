# T31 two-dimensional barcode delivery record

Status: `experimental`  
Capability: `composition.barcodes.two-dimensional`  
Acceptance Profile: `T31-two-dimensional-barcodes`  
Release train: `0.1.0-SNAPSHOT`

Issue [#32](https://github.com/zerocloud-sdk/folio-pdf/issues/32), governed by
[parent specification #1](https://github.com/zerocloud-sdk/folio-pdf/issues/1).
Implementation and review baseline:
`060cee07a230ab1c8194efce265615934d619ff2` on `main`.
No commit, push, PR, merge, tracker mutation or T33 release work is authorized.

The complete root verification, full JDK 8/11/17/21 script, dedicated actual-tool
recording and final inventory validate/generate/check have passed. Every
completion criterion remains unchecked pending the two independent final
reviews of the complete delivery scope. The [root receipt](artifacts/T31-root-verification.json)
and [matrix receipt](artifacts/T31-jdk-matrix-verification.json) each record
1,161 tests per run, zero failures/errors and four pre-existing opt-in skips;
no T31 test was skipped. The [final actual-tool record](artifacts/T31-actual-tools.json)
passes syntax, semantic and visual chains: 448 path and actual PDFium pixel
decodes, 448 full-page AE-zero comparisons, and 19 rejected negative PDFs and
rasters. The [artifact audit](artifacts/T31-r1-artifact-audit.json) verifies 450
source declarations, 23 PDFs and 1,382 PNGs; the
[coverage index](T31-coverage.md) links every declared page in both profiles.
The earlier 424-page development probe is historical evidence from before the
RAW repairs.

The [fixed profile](../profiles/T31-two-dimensional-barcodes.md) declares 224
pages per execution profile and 19 actual corrupted PDFs. The
[English contract](../../docs/two-dimensional-barcodes.md),
[Chinese usage guide](../../docs/zh-CN/getting-started.md), public Javadoc,
[encoder provenance](../../docs/third-party/okapibarcode-0.5.6.md),
[independent decoder provenance](../../docs/third-party/zxing-3.5.3.md) and
[character-set provenance](../../docs/third-party/barcode-character-sets.md)
record the implemented scope and its boundaries.

Independent standards certification and compatible-status Dependency Gates
remain INDETERMINATE. The capability stays experimental; implementation
completion will not certify compatibility, scanners or a Foundation release.

## Behavior and independence

The public declarations are immutable `Barcode2D` values. Versioned
`DrawBarcode2D` Commands and `MeasureBarcode2D` Queries execute through
`DocumentWorkflow.execute`; measurement reports detached dimensions without
painting. Published and reopened PDFs expose reusable indirect Forms containing
paths, including cross-page reuse and independent placement. Both IN_PROCESS
and the existing Linux HARDENED_WORKER envelope carry the same declaration,
result, failure and publication contracts.

The corpus covers QR numeric/alphanumeric/byte/Kanji segmentation, exact
L/M/Q/H correction and versions 1–40; all 30 ECC200 sizes, eight compaction
modes and typed controls; PDF417 AUTO/BINARY/RAW, all nine ECC levels, Macro,
fixed and automatic sizing and aspect ratio. All 18 supported encodings are
decoded in all three families. Capacity boundaries, units, RGB color, quiet
margins, translation, scale and rotation have literal expectations.

Independent assertions read public PDF Values and actual raster pixels. They
validate function patterns, format/version information, placement, control
metadata, data, padding and ECC with zero corrections before a matrix can
define a geometry reference. A separate Canvas painter uses only this qualified
matrix and the declared geometry. The raster decoder receives only a PNG and
its literal fixture, reconstructs fresh modules, and repeats qualification.
Full-page ImageMagick comparisons detect additional paint outside the symbol.
Nineteen actual damaged PDFs test the observers, including an invalid ECC200
pad with independently recomputed valid ECC and unchanged decoded payload.

## Reproduction and development observations

Provision the pinned tools as described in the [inventory guide](../README.md),
then choose an unused absolute output path:

```text
./mvnw -B -ntp -pl pdf-acceptance -am -Pacceptance-t31-record -DskipTests \
  -Dacceptance.output=/new/t31-evidence-directory verify
```

The dedicated entry runs the repository-only recorder with the full independent
decoder classpath in a separate JVM. `-DskipTests` in this command selects the
explicit evidence action; separate complete Maven and JDK-matrix runs remain
required. Missing tools record INDETERMINATE; a present tool's failed render or
decode records FAIL. Existing evidence cannot be replaced by the recorder.

[Development observations](artifacts/T31-development-validation.json) index
[79 original logs](artifacts/T31-development-logs.txt), retaining Reds, passing
focused checks and failed intermediate attempts. The command-entry Red is a
failed Python assertion despite an inner Maven success: the requested T31
profile did not exist. Its first implementation exposed Charset SPI visibility
under `exec:java`; the separate-JVM entry passes. The aggregate slice 26 and
capacity/resource regression slice 29 are not claimed as new Red seams.
The independent padding Red proves that successful decoding and valid ECC
alone are insufficient.

The [first complete verification attempt](artifacts/T31-root-initial-tmp-failure.txt)
used an isolated checkout under `/tmp`. The existing Worker read policy denies
that transaction-parent tree before its runtime allowlist, so Worker launches
failed. This is retained as a failed environment setup, not a passing run or a
T31 product repair. Moving the byte-identical copy to a normal project path
passes the [both-profile Worker check](artifacts/T31-worker-relocation-check.txt).
The first root and matrix runs after relocation were deliberately interrupted
when independent review found two RAW correctness defects. Their
[root log](artifacts/T31-pre-repair-48-root-verify.txt),
[matrix log](artifacts/T31-pre-repair-49-jdk-matrix.txt) and
[interruption receipt](artifacts/T31-pre-repair-50-interrupted-checks.json)
are retained; neither run passed the complete verification gate. Fresh complete
runs use the original worktree and the
[3,232-file identical validation copy](artifacts/T31-validation-copy-r2.json).
The root run has [passed in 32:38](artifacts/T31-root-verification.txt).
The [complete sequential JDK 8/11/17/21 script](artifacts/T31-jdk-matrix-verification.json)
passed with 1,161 test records per version and exit code zero. Its 841 frozen
build inputs match the original worktree and validation copy without exception. The post-repair final T31 tool recording has
[passed in 46:51](artifacts/T31-actual-tools.txt) in the fresh `T31-r1` directory.

The initial independent [Spec report](artifacts/T31-spec-review-initial.md)
identified PDF417 byte-compaction state loss at ECI controls and DataMatrix
RAW EDIFACT interpretation changing with symbol capacity. Both were repaired
through failing public Workflow regressions, minimal fixes and passing
refactor checks. The reviewer reran the unchanged original probes in both
execution profiles and confirmed the repairs, including preservation of
publication targets on failure. The [repair report](artifacts/T31-spec-review-repairs.md)
links the retained [probe sources, outputs and hashes](artifacts/T31-spec-repair-probes/README.md).
The initial [Standards report](artifacts/T31-standards-review-initial.md)
also identified serialization-dependent test assertions. The
[focused repair report](artifacts/T31-standards-review-repairs.md) confirms their
replacement with semantic observations. Its then-pending recorder Refactor
subsequently passed, followed by the updated 448-page recorder regression.
Both final review gates remain open.

The frozen-source [Standards delta review](artifacts/T31-standards-review-source-r2.md)
and [Spec delta review](artifacts/T31-spec-review-source-r2.md) found no remaining
hard finding. Their [132-file scope](artifacts/T31-review-scope-r2.json) explicitly
includes all tracked changes and untracked additions despite an empty three-dot
diff. Optional PDF417 rectangle-ranking duplication remains nonblocking. The retained-artifact [Standards review](artifacts/T31-standards-review-artifacts-r1.md)
and [Spec review](artifacts/T31-spec-review-artifacts-r1.md) also found no
additional issue. Their [unchanged source reports and audit outputs](artifacts/T31-artifact-review-retention.json)
verify all artifact/source hashes, the literal RAW regression pages, actual
pixel results and complete root output. The complete matrix and final inventory registration have now passed;
independent final scope closure remains required.

[Remediation observations](artifacts/T31-remediation-validation.json) retain
the separate Reds, Greens and Refactor checks for both RAW validators, both
semantic test repairs and the twelve-case corpus extension. Existing recorder
and entry count updates are recorded as maintenance regressions. The original
79-log development record remains separate and unchanged.

The [environment record](artifacts/T31-verification-environment.json) identifies
actual tool binaries, container images, host Maven/JDK and the explicitly
selected [audited complete HarfBuzz installation](artifacts/T31-harfbuzz-audit.json).
[Post-repair build inputs](artifacts/T31-build-inputs-r2.json) and
[all 2,336 pre-existing evidence files](artifacts/T31-prior-evidence-before.json)
are captured for the final preservation audit.

## Handoff criterion map

The independent review gate has not closed. Every row remains unchecked until
all required observations and the two independent final review reports exist.

| # | Criterion | Evidence / pending gate |
| --- | --- | --- |
| 1 | [ ] QR Reference Suite modes | [Fixed mode/option inventory](../profiles/T31-two-dimensional-barcodes.md#reference-suite-inventory), [coverage pages 1–50](T31-coverage.md), [independent semantic observations](T31-r1/T31-two-dimensional-barcodes-semantic.md) |
| 2 | [ ] DataMatrix Reference Suite modes | [Fixed mode/option inventory](../profiles/T31-two-dimensional-barcodes.md#reference-suite-inventory), [coverage pages 51–112](T31-coverage.md), [independent semantic observations](T31-r1/T31-two-dimensional-barcodes-semantic.md) |
| 3 | [ ] PDF417 Reference Suite modes | [Fixed mode/option inventory](../profiles/T31-two-dimensional-barcodes.md#reference-suite-inventory), [coverage pages 113–152](T31-coverage.md), [independent semantic observations](T31-r1/T31-two-dimensional-barcodes-semantic.md) |
| 4 | [ ] Published and reopened reusable vectors | [Public Workflow tests](../../pdf-document/src/test/java/net/zerocloud/pdf/consumer/Barcode2DWorkflowTest.java), [independent path/geometry/reuse observations](T31-r1/T31-two-dimensional-barcodes-semantic.md), cross-page indirect Forms |
| 5 | [ ] Independent payload recovery for every mode/encoding | [Literal corpus](../../pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/T31BarcodeProfile.java), [all 224 cases in both profiles](T31-coverage.md), including the 54 family/encoding combinations on pages 153–206 |
| 6 | [ ] Actual PDFium pixels independently decode | [Final visual chain](T31-r1/T31-two-dimensional-barcodes-visual.md), 448 independent raster decodes |
| 7 | [ ] Module matrices satisfy the declared profile | [Independent matrix oracle](../../pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/T31MatrixOracle.java), [semantic chain and damaged PDF controls](T31-r1/T31-two-dimensional-barcodes-semantic.md) |
| 8 | [ ] Quiet zones | [Literal margins](../profiles/T31-two-dimensional-barcodes.md#fixed-input-and-geometry-expectations), [path/pixel observations and negative controls](T31-r1/T31-two-dimensional-barcodes-visual.md) |
| 9 | [ ] Error correction configuration and results | [Fixed levels/capacities](../profiles/T31-two-dimensional-barcodes.md), [independent ECC with zero corrections and damaged ECC controls](T31-r1/T31-two-dimensional-barcodes-semantic.md) |
| 10 | [ ] Output dimensions | [Measurement/drawing contract](../../docs/two-dimensional-barcodes.md#drawing-and-measuring), [public measurement tests](../../pdf-document/src/test/java/net/zerocloud/pdf/consumer/Barcode2DWorkflowTest.java), [reopened geometry and actual pixels](T31-r1/T31-two-dimensional-barcodes-visual.md) |
| 11 | [ ] Placement coordinates | [Literal affine matrices](../profiles/T31-two-dimensional-barcodes.md#fixed-input-and-geometry-expectations), [page operators and fresh raster inverse placement](T31-r1/T31-two-dimensional-barcodes-visual.md) |
| 12 | [ ] Scale | [Per-family geometry fixtures, pages 207–224](T31-coverage.md), [actual raster comparisons and damaged scale control](T31-r1/T31-two-dimensional-barcodes-visual.md) |
| 13 | [ ] Rotation | [Per-family geometry fixtures, pages 207–224](T31-coverage.md), [actual raster comparisons and damaged rotation control](T31-r1/T31-two-dimensional-barcodes-visual.md) |
| 14 | [ ] Unsupported encodings fail clearly and stably | [Public Workflow negative tests](../../pdf-document/src/test/java/net/zerocloud/pdf/consumer/Barcode2DWorkflowTest.java), unsupported names/ECIs, undefined ISO-8859-12 and lossy input |
| 15 | [ ] Invalid dimensions fail clearly and stably | [Public Workflow tests](../../pdf-document/src/test/java/net/zerocloud/pdf/consumer/Barcode2DWorkflowTest.java) for bounds, nonfinite values, impossible rectangles and placement |
| 16 | [ ] UTF-8 boundary documented and independently proved | [Strict ECI contract](../../docs/two-dimensional-barcodes.md#strict-character-encodings-and-eci), [all three families and actual pixels](T31-r1/T31-two-dimensional-barcodes-visual.md) |
| 17 | [ ] Public Workflow seam and both execution profiles | [Public tests](../../pdf-document/src/test/java/net/zerocloud/pdf/consumer/Barcode2DWorkflowTest.java), [unskipped complete root results](artifacts/T31-root-verification.json), [all four JDK results](artifacts/T31-jdk-matrix-verification.json) |
| 18 | [ ] No private backend or encoder self-proof in tests | Public PDF Values, [independent matrix oracle](../../pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/T31MatrixOracle.java), [independent source review](artifacts/T31-spec-review-source-r2.md); final review pending |
| 19 | [ ] Failed barcodes preserve existing publication targets | [Public Workflow negative tests](../../pdf-document/src/test/java/net/zerocloud/pdf/consumer/Barcode2DWorkflowTest.java), NOT_ATTEMPTED receipts, [independently rerun RAW rejection probes](artifacts/T31-spec-repair-probes/README.md) |
| 20 | [ ] Resource, signature, permission and publication contracts | [Public Workflow policy/incremental/resource tests](../../pdf-document/src/test/java/net/zerocloud/pdf/consumer/Barcode2DWorkflowTest.java) in both profiles, [stable failure contract](../../docs/two-dimensional-barcodes.md#failures-publication-and-execution-profiles) |
| 21 | [ ] Accurate Capability Matrix | [Authority](../capability-matrix.yaml), [generated view](../../docs/generated/capability-matrix.md), [validated final chain registration](artifacts/T31-final-inventory-validation.json); experimental and standards/Dependency Gates indeterminate |
| 22 | [ ] Accurate Facade Surface without unsupported stable stubs | [Explicit T31 exclusion](../facade-surface.yaml), [reference mapping boundaries](../profiles/T31-two-dimensional-barcodes.md#reference-suite-inventory), file-ID translation and bitmap-only option explained |
| 23 | [ ] English, public Javadoc and applicable Chinese usage agree | [English contract](../../docs/two-dimensional-barcodes.md), [public declaration](../../pdf-document/src/main/java/net/zerocloud/pdf/composition/Barcode2D.java), [Chinese guide](../../docs/zh-CN/getting-started.md) |
| 24 | [ ] Provenance, versions, licenses and distribution boundaries | [PROVENANCE](../../PROVENANCE.md), [NOTICE](../../NOTICE), [Okapi](../../docs/third-party/okapibarcode-0.5.6.md), [ZXing](../../docs/third-party/zxing-3.5.3.md), [character sets](../../docs/third-party/barcode-character-sets.md) |
| 25 | [ ] Retained actual syntax, semantic and visual evidence with hashes | [Final recording/source/artifact audit](artifacts/T31-r1-artifact-audit.json), [actual command receipt](artifacts/T31-actual-tools.json); standards stay INDETERMINATE |
| 26 | [ ] Inventory validate | [Final inventory validate PASS](artifacts/T31-final-inventory-validation.json) |
| 27 | [ ] Inventory generate and check | [Final inventory generate/check PASS](artifacts/T31-final-inventory-validation.json) |
| 28 | [ ] Complete root Maven verify | [Post-repair PASS](artifacts/T31-root-verification.json), 1,161 tests, zero failures/errors, four pre-existing opt-in skips |
| 29 | [ ] JDK 8/11/17/21 matrix | [Complete 8/11/17/21 PASS receipt and raw log](artifacts/T31-jdk-matrix-verification.json), all 841 input hashes preserved in both workspaces |
| 30 | [ ] Independent Standards and Spec review; defects repaired | [Initial finding repairs](artifacts/T31-remediation-validation.json), [Standards artifact review](artifacts/T31-standards-review-artifacts-r1.md), [Spec artifact review](artifacts/T31-spec-review-artifacts-r1.md); final separate reports pending |
| 31 | [ ] Review includes staged, unstaged and all untracked changes | [Frozen-source scope](artifacts/T31-review-scope-r2.json), [final complete scope](artifacts/T31-review-scope-final.json), [workspace manifest](artifacts/T31-final-workspace-audit.json); independent final review pending |
| 32 | [ ] Commit authorization respected | [Final baseline, branch and empty staging audit](artifacts/T31-final-workspace-audit.json); no commit authorization and all changes remain uncommitted |
| 33 | [ ] Only intentional T31 delivery changes remain | [Final workspace/preservation audit](artifacts/T31-final-workspace-audit.json), all 2,336 prior evidence files and all 1,439 final tool artifacts preserved |
| 34 | [ ] Per-criterion report, commands, results, skips, limits and commit state | This ledger and the [actual command](artifacts/T31-actual-tools.json), [development](artifacts/T31-development-validation.json) and [repair](artifacts/T31-remediation-validation.json) receipts; close only after independent final review |

## Required command receipts

The complete root and JDK-matrix runs explicitly select the audited installation:
`FOLIO_HARFBUZZ_HELPER=/home/ubuntu/IdeaProjects/open-pdf/.build-cache/harfbuzz/10.2.0-final/bin/folio-harfbuzz`.

| Command | Result / receipt |
| --- | --- |
| `./mvnw -B -ntp verify` | [PASS, complete raw output and parsed observations](artifacts/T31-root-verification.json), 32:38 |
| `./scripts/verify-jdk-matrix.sh` | [PASS, full sequential 8/11/17/21 output and observations](artifacts/T31-jdk-matrix-verification.json) |
| `./scripts/inventory validate` | [PASS, final metadata validation/generation/check](artifacts/T31-final-inventory-validation.json) |
| `./scripts/inventory generate` | [PASS, final metadata validation/generation/check](artifacts/T31-final-inventory-validation.json) |
| `./scripts/inventory check` | [PASS, final metadata validation/generation/check](artifacts/T31-final-inventory-validation.json) |
| `./mvnw -B -ntp -pl pdf-acceptance -am -Pacceptance-t31-record -DskipTests -Dacceptance.output=/home/ubuntu/IdeaProjects/open-pdf/capabilities/evidence/T31-r1 verify` | [PASS, dedicated actual-tool output](artifacts/T31-actual-tools.json), 46:51 |
| `python3 -m unittest scripts.tests.test_t31_acceptance_entry` | [PASS within remediation receipts](artifacts/T31-remediation-validation.json) |

The full root run and each JDK-matrix version record 1,161 tests, zero
failures/errors and four pre-existing
opt-in skips: three T22 scale-certification cases require `folio.pdf.t22.scale`,
and one T30 pinned-raster case requires `t30.raster=true`. No T31 test is skipped;
all 76 T31-related public Workflow and acceptance cases run. The final T31
record separately executes the actual pinned tools for every declared fixture
and negative control. The [complete matrix receipt](artifacts/T31-jdk-matrix-verification.json)
retains each version's counts, elapsed times, skipped classes and reasons. No full verification command uses a skip flag.

## Residual limits and workspace state

The implementation remains experimental. [Independent standards certification](T31-r1/T31-two-dimensional-barcodes-standards.md)
and compatible-status Dependency Gates are INDETERMINATE. The UTF-8 claim is
strict ECI encoding and independent payload recovery within the declared
profile; it does not certify every scanner. HARDENED_WORKER evidence applies to
the existing Linux execution envelope. The [contract](../../docs/two-dimensional-barcodes.md)
states finite resource/geometry bounds, already-compacted RAW semantics, and
the caller's responsibility for page fit, a clear quiet zone and suitable
background contrast. The Java 8 product boundary remains `net.zerocloud`; ZXing
and acceptance charset providers remain repository-only.

All changes remain uncommitted on `main` at the stated baseline; nothing has
been staged, committed, pushed, opened as a PR, merged or changed in the issue
tracker. No #33 release action has been performed. The [scope manifest](artifacts/T31-review-scope-final.json) and
[workspace/preservation audit](artifacts/T31-final-workspace-audit.json) identify
the intentional T31 delivery files. Ignored native/build
caches and the owned validation checkout are execution material, outside the
delivery diff; no unrelated files have been cleaned up.


The final [inventory receipt](artifacts/T31-final-inventory-validation.json)
follows all full builds. Its only post-build input changes are the three
observed acceptance-chain registrations and evidence links in
`capabilities/capability-matrix.yaml`, plus the corresponding generated
`docs/generated/capability-matrix.md` view. Product code, tests, the fixed
profile, dependencies and native-tool inputs remain byte-identical to the
verified 841-file snapshot. The final workspace audit enforces this boundary;
focused inventory validate/generate/check covers the metadata changes.

The independent final review scope freezes the delivery files with all 34
criteria still unchecked. Closing the gate permits only retention of the two
review reports and their aggregate, completion-status/link updates in this
ledger, and refresh of the workspace receipt. Any product, test, profile,
inventory or actual-tool artifact change would require further validation and
independent review.
