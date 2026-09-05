# T23 bounded Rendering evidence

Status: `experimental`

Capability: `conversion.rendering`

Acceptance Profile: `T23-page-rendering`

Release train: `0.1.0-SNAPSHOT`

The project-owned `RenderPage.version1` Query and `RenderedPage.writePngTo`
consumer provide the production Rendering path in `DocumentWorkflow.execute`.
The bundled default is PDFBox 3.0.8. Public types contain no backend identity.
The [English contract](../../docs/rendering.md) defines the supported profile,
Provider envelope, ordering, lifecycle, ownership, and resource model.

## Implementation evidence

[RenderingWorkflowContractTest](../../pdf-document/src/test/java/net/zerocloud/pdf/consumer/RenderingWorkflowContractTest.java)
runs the same public behaviors in IN_PROCESS and HARDENED_WORKER:

- One-based selection, declared order and duplicates, earlier Commands,
  DPI/scale, CropBox/MediaBox, nonzero crop origin, UserUnit, quarter-turn
  rotation, tiny dimensions, binary32 floor rounding, and stable numeric errors.
- Fixed RGB/background/straight-alpha/gray pixels, half-alpha composition
  before gray conversion, annotation SHOW/HIDE and visibility, safe missing
  appearance and font substitution diagnostics.
- Inline and resource JPEG header dimensions are checked before platform
  decoding, filter-stage output is bounded, and cyclic image masks fail without
  recursive stack overflow.
- Exact cumulative viewport pixels plus existing image pixels, inclusive raster
  memory and temporary-storage boundaries and first excess, repeated pages,
  retained and streamed PNGs, terminal resource poisoning, elapsed time,
  absolute deadline, cancellation, and shared-environment concurrency.
- A deterministic noisy image produces PNGs larger than the configured 2-KiB
  Worker frame, exercising the actual Rendering transfer and shared ledger.
- Explicit/default/registered Providers, unavailable and missing identities,
  denied/authorized remote disclosure, byte limits and malformed replies;
  no exact preference silently falls back.
- Callback-scoped expiry before WORK_COMPLETED, caller-thread consumption,
  early close, caller output ownership, partial I/O failure, exact caller
  runtime identity, staging cleanup, unchanged Paths on abort, and ordered
  COMMITTED/FAILED/NOT_ATTEMPTED PDF receipts after PNG consumption.

The shared workflow/hostile-input suites cover the inherited transaction and
execution contracts. Worker codec and hostile-boundary tests exercise the
closed tokens, authenticated framing, fixed resource grants, and stable
failures. API/artifact checks retain Java 8 class files, module boundaries,
unshaded dependencies, and backend-private public signatures.

## Independent visual and syntax evidence

The repository acceptance command generates the PDFs through public Commands.
T23 invokes the actual public Rendering Query and PNG consumer for the
implementation output. Independently pinned PDFium v0.11.2 / chromium-7881
renders the same unchanged, ID-neutral-hashed PDFs with annotations enabled.
Pinned ImageMagick 7.1.2-30 performs the fixed comparisons. qpdf 12.4.0 checks
all three unchanged products. The authorities pin input/expected hashes, tool
versions/distribution hashes, fonts, page, geometry, DPI, scale, color,
background, alpha, annotations, diagnostics, and rounding.

All profiles select page 1, effective CropBox (absent, hence 612x792 MediaBox),
144 DPI, scale 1, 1224x1584 pixels, opaque white sRGB, and existing visible
normal annotations. AE uses zero fuzz. The base expected raster is analytical;
T18/T19 expected rasters and review ceilings are reused unchanged. Thresholds
were fixed before the first T23 comparison and were not relaxed.

| Input/profile | Expected-to-PDFium AE / ceiling | Public Rendering agreement AE / ceiling | Records |
| --- | --- | --- | --- |
| Page content and green stamp appearance | 0 / 0 | 0 / 0 | [syntax](T23-page-rendering-syntax.md), [visual](T23-page-rendering-visual.md) |
| T18 image/color/transparency corpus | 0 / 0 | 2120.4 / 2500 | [syntax](T23-page-rendering-images-syntax.md), [visual](T23-page-rendering-images-visual.md) |
| T19 embedded project-font corpus | 0 / 0 | 231.827 / 2500 | [syntax](T23-page-rendering-fonts-syntax.md), [visual](T23-page-rendering-fonts-visual.md) |

ImageMagick's pinned HDRI build reports fractional AE values; the recorded
metric is compared as a decimal, without rounding it to an integer. Image
and font differences remain visible in the retained renderer-difference PNGs.
The image profile explicitly expects PLATFORM_IMAGE_CODEC for its JPEG; the
other two profiles require no diagnostics. The font corpus uses only embedded,
pinned project fonts. These profiles make no claim for substituted system
fonts or for other independently untested render options.

The first base comparison omitted the PDFium CLI's explicit annotation flag
and correctly recorded INDETERMINATE. Inspection showed that PDFium had
omitted the green stamp while the public output matched the analytical
expectation. Adding `--render-annotations` aligned the declared SHOW policy;
the unchanged threshold then passed. The image profile initially rejected its
expected JPEG codec diagnostic; pinning that diagnostic resolved the profile
mismatch without changing image output or comparison thresholds. Recorder
regressions prove missing tools, mismatched input hashes, renderer conflicts,
wrong versions, and malformed rasters cannot be recorded as PASS.

Inputs, raw results, expected/public/PDFium PNGs, and both difference images
are retained in [artifacts](artifacts). Only T23 evidence was copied from the
isolated acceptance output; unrelated historical records were preserved.

## Open gates and limits

Mandatory independent standards and formal semantic evidence remain absent.
Public semantic tests are implementation evidence, not a substitute record.
The T05, T03/T07, T09, T18, T19, T20, T21, and T22 compatible-status Dependency
Gates remain open, as does T06 promotion. A closed implementation issue is
insufficient to satisfy any gate. T23 remains experimental and has no certified
platform or compatible claim.

Rendering is page-at-a-time rather than tiled. Folio-owned memory is modeled
requested raster/envelope/buffer/metadata storage, not whole JVM heap, PDFBox
or ImageIO internals, native allocations, or RSS. External Providers execute
caller-side even with a PDF Worker and retain their time/isolation obligations.
PNG consumption has immediate partial-write semantics. Existing signatures
and password extraction permissions keep the inherited public rules. No OCR,
Office conversion, paragraph layout, shaping, or Migration Facade mapping is
introduced. The Facade Manifest explicitly excludes this capability with T23.

Validation and fixed-base review receipts are recorded in
[T23 validation](T23-validation.md).
