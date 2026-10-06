# T03 Document Workflow transaction evidence

Status: `compatible`

Capability: `document.blank.create-publish-reopen`

Acceptance Profile: `T03-document-workflow-transaction`

Release train: `0.1.0-SNAPSHOT`

T03 extends the T01 public `DocumentWorkflow.execute` seam into a complete
transaction contract in both IN_PROCESS and HARDENED_WORKER. Requests declare uniquely named Sources,
select one primary Source, declare ordered named publication Targets, and
select a Save Mode. An immutable Workflow Environment owns deadline time.
Successful outcomes report the capability, selected execution profile,
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

T70 supplies the final four-chain certification described in
[the executable certification contract](../../docs/t03-certification.md).
The current authority is [Foundation Evidence](../foundation-evidence.yaml),
which binds the exact source, compiled candidate, profile contract, runtime
configuration, observed environment, reports and negative controls. Every JDK
8/11/17/21 × IN_PROCESS/HARDENED_WORKER tuple runs the same 27 Native contracts,
5 Stable consumer contracts and 2 actual-jar contracts. Separate observations
check each Native and Stable one-page output through all four chains.
The Facade retains its existing IN_PROCESS default and adds no configuration mapping.

The standards scope is the decoded Catalog, Pages and Page requirements in
ISO 32000-1 clauses 7.7.2–7.7.3 for the minimal blank product. The
[22 required rules and qualified negative fixtures](../profiles/T03-standards/README.md)
are checked by pdfcpu strict and Arlington together. Neither tool alone covers
this profile. Their union is enforced independently of each checker's subset.
Physical serialization is the separate qpdf syntax chain. This is not a claim
of general ISO, PDF/A or PDF/UA conformance for unrelated PDF features.
The public semantic inspector rejects additional Catalog/Page-tree/Page keys,
nonempty resources, content, extra pages and other structures outside that scope.

T06/T07 remain historical observations against their original output. Their
ID-neutral hash policy and missing-standards determination are not relabeled as
T70 candidate certification. T70 retains exact SHA-256 identities for every
new PDF and all input/output bytes; its record may also show the legacy
ID-neutral diagnostic hash. No expected raster is adopted from product output.

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
Evidence and does not itself supply independent Acceptance Evidence; T70 is the later certification authority.
