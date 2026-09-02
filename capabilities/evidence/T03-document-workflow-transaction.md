# T03 Document Workflow transaction evidence

Status: `experimental`

Capability: `document.blank.create-publish-reopen`

Acceptance Profile: `T03-document-workflow-transaction`

Release train: `0.1.0-SNAPSHOT`

T03 extends the T01 public `DocumentWorkflow.execute` seam into a complete
in-process transaction contract. Requests declare uniquely named Sources,
select one primary Source, declare ordered named publication Targets, and
select a Save Mode. An immutable Workflow Environment owns deadline time.
Successful outcomes report the capability, in-process execution profile,
Save Mode, safe diagnostics, and receipts. Path, caller-owned stream,
caller-owned channel, and bounded-byte Sources are covered. Path and
caller-owned stream Targets are covered.

## Implementation evidence

- `BlankDocumentWorkflowTest` preserves the T01 create, publish, reopen, and
  query tracer bullet.
- `WorkflowLifecycleTest` covers command/query ordering, unchanged caller
  runtime propagation, Session expiry, cross-thread rejection, library-owned
  command enforcement, and Path-source release across success and failures.
- `WorkflowTransactionContractTest` covers named primary selection, all T03
  Source forms and ownership, source limits, request invariants, explicit
  REWRITE, the stable missing-Source INCREMENTAL refusal, successful multi-target
  publication, validation-before-publication, partial stream failure,
  cancellation, deterministic deadlines through Workflow Environment,
  immutable outcome information, nested-failure receipt isolation, and
  sanitized progress ordering.
- `WorkflowResourceOwnershipTest` covers module-opened Path descriptors and
  caller-owned stream, channel, and output ownership across T03 success,
  checked-failure, cancellation, deadline, validation, partial-publication,
  and caller-programming-error exits. Direct descriptor checks run where the
  Linux `/proc/self/fd` contract is available; caller-owned checks are
  platform-neutral.
- `PublicApiLeakageIT` reflectively checks every public and protected
  signature for backend types.
- `JarContractIT` verifies the stable module name and Java 8 class-file
  version.
- `./scripts/inventory check`, `./mvnw -B -ntp verify`, and
  `./scripts/verify-jdk-matrix.sh` are the repository gates for inventory
  drift, the full build, and JDK 8/11/17/21 execution.

Publication is deliberately not a transaction across Targets. A stream write
may be partially visible. On ordered publication failure, earlier Targets
remain `COMMITTED`, the failing Target is `FAILED`, and later Targets are
`NOT_ATTEMPTED`. Path replacement is staged and atomic where the platform
supports it. Caller-owned streams and channels are never closed.

T03 itself makes no signed-document preservation claim. The separate T15
capability now supplies incremental prefix preservation, Existing Signature
recognition, signed-REWRITE refusal, and conservative DocMDP authorization
through the same transaction seam.

The PDF fixtures are generated entirely through the project-owned Native
Interface. Apache PDFBox 3.0.8 remains behind project-owned public types. This
record is implementation evidence, not independent Acceptance Evidence.

T06 records passing independent
[`syntax`](T06-document-blank-syntax.md) and
[`semantic`](T06-document-blank-semantic.md) chains against the same pinned
artifact. T07 records the passing independent
[`visual`](T07-document-blank-visual.md) chain after PDFium renders that exact
artifact and ImageMagick compares only fixed-size PNG rasters. The
[overall determination](T06-document-blank-determination.md) remains
`indeterminate` because independent standards evidence is still absent. qpdf
syntax success is not a standards-compliance claim, and visual success cannot
replace that missing chain. The capability therefore remains `experimental`.

The T06/T07 records identify the shared input with an ID-neutral SHA-256:
before hashing, only the two hexadecimal values in the PDF trailer `/ID` array
are replaced with ASCII zeroes. Issue #1 excludes byte-identical PDF output,
and a fresh PDF identifier is not a semantic or visual difference. All other
bytes remain hash-significant, while qpdf, the public reopen check, PDFium, and
the secondary renderer receive the exact unmodified workflow output. The
acceptance command's repeat-run test requires the records and raw findings to
reproduce byte-for-byte under this policy.

## T07 blank-document visual profile

The machine-consumed profile is
[`capabilities/profiles/T03-document-blank-visual.properties`](../profiles/T03-document-blank-visual.properties).
It fixes the effective page box to the MediaBox `[0 0 612 792]` points because
the blank artifact has no CropBox, renders at 144 DPI, and requires an opaque
8-bit sRGB RGB PNG of exactly `1224x1584` pixels on a white background. Fonts
are explicitly not applicable because the artifact contains no text or font
resources. Antialiasing is the pinned PDFium build's default smoothing and has
no marks to affect in this profile.

The project-owned expected raster is an all-white image defined by those
settings, not output from the Reference Suite. ImageMagick uses absolute error
count (`AE`) with fuzz `0%`; the capability threshold and the independent-to-
implementation renderer agreement threshold are both zero changed pixels. A
threshold mismatch retains a red/white difference raster and is `fail`. A
PDFium/PDFBox Renderer disagreement is review-required and `indeterminate`,
never `pass`. Missing or unpinned tools, unexpected process results, malformed
PNG data, and any wrong raster dimension are also `indeterminate`.

PDFium CLI v0.11.2 with embedded PDFium Chromium build 7881 is the independent
renderer and receives the PDF input. ImageMagick 7.1.2-30 receives only the
validated expected, PDFium, and secondary-renderer PNG paths. Apache PDFBox
Renderer 3.0.8 supplies secondary disagreement evidence only; it is not the
visual oracle. All three remain acceptance-only implementation details and no
T23 runtime page-rendering capability or Native Interface type is introduced.
The pinned component and license inventories are
[`docs/third-party/pdfium-cli-v0.11.2.md`](../../docs/third-party/pdfium-cli-v0.11.2.md)
and
[`docs/third-party/imagemagick-7.1.2-30-appimage.md`](../../docs/third-party/imagemagick-7.1.2-30-appimage.md).

## Execution record — 2026-08-10

- Focused public-seam validation passed with 27 consumer tests: 1 T01 tracer,
  7 lifecycle tests, 16 transaction tests, and 3 resource-ownership matrices.
- `./mvnw -B -ntp verify` passed with those 27 consumer tests, 2 artifact and
  public-API integration tests, and 6 inventory-tool tests (35 total).
- `./scripts/inventory validate`, generated-view regeneration through
  `./scripts/inventory generate`, and `./scripts/inventory check` passed.
- `./scripts/verify-jdk-matrix.sh` passed the same full verification contract
  on Eclipse Temurin JDK 8, 11, 17, and 21.
- Independent clean-context Standards and Spec reviews examined the complete
  T03 worktree diff against
  `54e33532baac35f6a78cb3c657605e457b1cf080`. After reviewed fixes and scoped
  T15 limitations, both final reviews reported no actionable findings.
- `git diff --check` passed. No T03 commit was created; HEAD remained at the
  fixed point.

The review above is implementation review, not T06 independent Acceptance
Evidence and does not change the capability's `experimental` status.
