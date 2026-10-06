# T18 Canvas images, color, and transparency evidence

Status: `experimental`

Capability: `composition.canvas.images-colors-transparency`

Acceptance Profile: `T18-canvas-images-colors-transparency`

Release train: `0.1.0-SNAPSHOT`

T18 deepens the existing Composition Canvas seam with an immutable version-2
program and mandatory caller-declared resource limits. It supports bounded
JPEG pass-through, PNG and optional TIFF normalization to 8-bit DeviceRGB,
raw samples, same-Session existing-image borrowing, Device/calibrated/ICCBased
fill and
stroke color, explicit and soft masks, alpha, standard blend modes, and
reusable Transparency Groups. All public values are project-owned or JDK
values; mutable byte and array inputs are copied.

## Implementation evidence

- `CanvasImageColorTransparencyWorkflowTest` drives commands through
  `DocumentWorkflow.execute`, publishes and reopens outputs, and observes them
  through public T09/T14 queries. It proves representative JPEG, PNG alpha,
  TIFF, raw, unfiltered/Flate/DCT existing resources, Device/calibrated/ICC
  color, profile identity, both mask kinds, alpha/blend/group dictionaries,
  bounded reuse, every exact limit and first excess, optional-codec discovery
  and absence, provider selection/read/cleanup failure translation, stack-safe
  shared/deep group bounds, preservation-wrapper and hard generated-content
  accounting, safe preservation, both save modes, Existing Signature and
  password authority, stable failures, atomic targets, and defensive public
  values.
- JPEG bytes remain under DCTDecode; PNG/TIFF normalize to 8-bit RGB plus an
  optional grayscale soft mask and FlateDecode; raw samples retain their
  declared supported color space; existing resources retain their indirect
  identity, filters, color, masks, and bytes. No path silently falls back to a
  different conversion.
- T14 bounded ICC validation now exposes supported profile stream identity,
  exact decoded length, and SHA-256. Malformed or incompatible profiles retain
  a safe unsupported observation or fail T18 drawing before publication.
- The complete recursive program and every byte/pixel/profile/mask/content/
  resource/depth limit, preservation wrappers, and the hard 1 MiB generated-
  content ceiling are validated before page mutation. Equal images, masks,
  color spaces/profiles, and states—and repeated placement of the same group
  instance—reuse one indirect resource; repeated
  placement changes operator count rather than resource count.
- Public API, artifact, and cross-JDK contracts retain Java 8 class files,
  module identity, notices, private backend/provider types, and an unshaded
  optional TwelveMonkeys dependency.

## Independent evidence and status boundary

The T18 qpdf record supplies a passing syntax chain over the unchanged
workflow-produced artifact. A separate project semantic producer reopens that
artifact through public Folio PDF queries and verifies exact per-format
dimensions, filters, color/sample values, ICC digest/object identity,
mask/alpha bytes, calibrated parameters, graphics-state/blend values,
Transparency Group, reuse, and preservation expectations. Pinned PDFium
independently renders that same artifact at 144
DPI to a fixed 1224x1584 opaque sRGB PNG. Pinned ImageMagick requires the
PDFium raster to match the project-owned expected raster at AE 0 and records a
secondary PDFBox-renderer agreement metric under the T18 ceiling of 2,500;
PDFBox cannot make the visual chain pass. Repeat-run tests cover evidence and
ID-neutral artifact/raster reproducibility.

Mandatory standards Acceptance Evidence remains absent. The
`document.images-resources.extract` and
`composition.canvas.draw-positioned-text` Dependency Gates remain open because
T14 and T17 are still `experimental`; closing implementation issues does not
satisfy the required `compatible` status. T06 remains a promotion gate. T18
therefore remains `experimental`, with no compatible or certified-platform
claim.

T18 makes no font acquisition, comprehensive hostile-input, Worker-isolation,
runtime-rendering, layout, Forms, SVG, redaction, optimization, arbitrary
filter-chain, implicit-network, public backend-SPI, or Migration Facade claim.
