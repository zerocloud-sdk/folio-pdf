# T19 explicit font loading, embedding, and subsetting evidence

Status: `experimental`

Capability: `composition.fonts.load-embed-subset-fallback`

Acceptance Profile: `T19-font-loading-embedding-subsetting`

Release train: `0.1.0-SNAPSHOT`

T19 adds a separate positioned-Unicode command without changing Canvas's
borrowed encoded-glyph Font contract. Callers supply command-local font
sources or configure a declaration-ordered Reference Font Set in the Workflow
Environment. The offline defaults contain no fonts, and production code does
not scan installed fonts or resolve network sources.

## Implementation evidence

- `FontLoadingWorkflowTest` drives every behavior through
  `DocumentWorkflow.execute`, publication, reopen, T13 text extraction, T14
  resource inventory, and bounded public PDF Value inspection. It covers byte,
  Path, stream, channel, and configured sources; defensive copy and caller
  ownership; strict fallback order; exact-rational source widths and
  positioned geometry; subset and full-embedding outcomes; ToUnicode
  extraction; equal-program resource reuse across query/command barriers;
  persistent Type 0, CIDFont, and Font Descriptor patch preservation;
  unsigned incremental publication and its PDF-version bounds; exact limits;
  atomic preflight; Existing Signature and password-permission policy; stable
  failures; receipts; and diagnostic redaction.
- Version 1 supports only the documented ten-table, instruction-free
  quadratic TrueType sfnt profile, including the exact name, post, OS/2, and
  single Unicode cmap variants. Every declared fallback is staged and
  validated. Public variant matrices prove supported alternatives retain
  source geometry and known outside-profile forms are rejected. Inconsistent
  headers, metrics, mappings, bounds, and dependency graphs (including cyclic
  composite glyphs), restricted embedding, missing glyphs, and ambiguous
  source mappings fail before publication with fixed input-free diagnostics.
- Permitted subset programs contain selected glyphs and required dependencies
  while excluding the unrelated project glyph. The `No Subsetting` permission
  embeds the complete program. Equal source bytes share one document Font
  resource within a Session, while generated width and ToUnicode data reopen
  through the existing T13/T14 contracts.
- Public API, artifact, and cross-JDK contracts retain Java 8 class files,
  module identity, notices, private PDFBox/FontBox types, and unshaded
  dependencies. The two small acceptance fonts are project-authored
  Apache-2.0 data with fixed decoded hashes and are absent from the shipped
  `pdf-document` jar.

## Independent evidence and status boundary

The qpdf chain validates the unchanged workflow-produced artifact. The
project semantic producer independently reopens that same artifact through
public T13/T14 and bounded inspection APIs, proving two embedded Type 0
subsets, deterministic primary-first fallback, exact 600/650/700-unit source
advances, explicit A/U+03A9/B mappings, unrelated-glyph exclusion, and primary
resource reuse. It does not use backend identity or another library's font
output as an oracle.

Pinned PDFium renders the same artifact at 144 DPI into a 1224x1584 opaque
sRGB PNG. Pinned ImageMagick requires an AE-0 match to the project-owned,
visually reviewed PDFium expected raster and records a secondary PDFBox
renderer comparison under the fixed 2,500-pixel review ceiling. The visual
profile explicitly permits only the two embedded project subsets and no
system font.

Mandatory independent standards evidence remains absent. The T13, T14, and
T17 Dependency Gates remain open because those capabilities are still
`experimental`, and T06 remains a promotion gate. T19 therefore remains
`experimental` with no compatible or certified-platform claim even though its
syntax, semantic, and visual chains pass.

T19 makes no claim for bidi, normalization, shaping, kerning, line breaking,
paragraph layout, runtime rendering, Forms UI, tagging, redaction,
optimization, comprehensive hostile-input policy, Worker isolation, Asian
resource profiles, implicit network access, system-font discovery, or a
Migration Facade mapping.
