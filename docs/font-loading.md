# Explicit font loading and positioned Unicode text

T19 adds deterministic font acquisition to the Composition Native Interface
without changing the borrowed, encoded-glyph `CanvasFont` contract. Call
`DocumentSession.execute(DrawPositionedUnicodeText.version1(...))` inside
`DocumentWorkflow.execute`. The command accepts one nonempty Unicode text run,
one explicit text matrix, font size and Text Rendering Mode, a font selection,
and complete caller-declared limits. It creates a horizontal Type 0 Font with a
CIDFontType2 descendant, embeds the selected TrueType programs, writes source
font metrics and ToUnicode data, and publishes through the normal Document
Workflow lifecycle.

The font size must be positive. It and every text-matrix component must be
finite with absolute value at most `1,000,000,000`, matching the bounded Canvas
numeric domain.

The T19 command accepts only the four non-clipping Text Rendering Modes:
`FILL` (0), `STROKE` (1), `FILL_STROKE` (2), and `INVISIBLE` (3). The four
clipping modes (4–7) fail with `POSITIONED_TEXT_INVALID`; the command's
isolated save/restore scope cannot expose a clipping path to later painting.

This is positioned, unshaped text. Version 1 does not perform bidi,
normalization, script shaping, kerning, line breaking, paragraph layout, or
automatic page placement. The input String is consumed in Unicode scalar order
and must be nonempty and free of unpaired surrogates. Font changes caused by
fallback retain the PDF text position established by the preceding glyph
advances.

## Font sources and ownership

`FontSource` is a closed declaration with four version-1 forms:

| Form | When bytes are observed | Ownership |
| --- | --- | --- |
| `FontSource.bytes(byte[])` | The declaration defensively copies the array immediately. The workflow copies it again into command-local staging. | The caller retains the original array. Later caller changes cannot affect the declaration. |
| `FontSource.path(Path)` | The path is opened when the command executes. | Folio PDF opens and closes the handle. A later command observes the then-current file bytes. |
| `FontSource.stream(InputStream)` | The caller-owned stream is read to EOF when the command executes. | Folio PDF never closes it. Reuse of the identical declaration in the same Document Session reuses its staged bytes instead of reading it again. |
| `FontSource.channel(ReadableByteChannel)` | The caller-owned channel is read to EOF when the command executes. | Folio PDF never closes it. Reuse of the identical declaration in the same Document Session reuses its staged bytes instead of reading it again. |

Every declared source is staged, format-checked, parsed, and embedding-checked
in declaration order before selection, including a fallback source that the
text does not ultimately use. A source occurrence consumes the source-count
and aggregate-byte limits even when its bytes equal an earlier occurrence.
Folio PDF never scans installed-font directories, asks a backend font mapper
for a substitute, resolves a URI, or performs a network request.

`FontSelection.explicit(...)` accepts all four forms. A
`ReferenceFontSet` is immutable, declaration ordered, and may contain only the
reusable byte and path forms; streams and channels cannot be installed in the
shared `WorkflowEnvironment`. Configure it with
`WorkflowEnvironment.builder().referenceFontSet(set).build()` and select it
with `FontSelection.referenceFontSet()`. System defaults contain an empty
Reference Font Set.

## Closed version-1 profile matrix

| Font data or profile | Version-1 outcome |
| --- | --- |
| Standalone TrueType sfnt with `00010000` or `true` scaler and exactly the ten version-1 tables described below | Supported as a horizontal Type 0/CIDFontType2 Font. |
| OpenType CFF or CFF2 (`OTTO`, `CFF `, or `CFF2`) | `FONT_FORMAT_UNSUPPORTED`. |
| TrueType/OpenType Collection (`ttcf`) | `FONT_FORMAT_UNSUPPORTED`. |
| WOFF 1 or WOFF 2 (`wOFF` or `wOF2`) | `FONT_FORMAT_UNSUPPORTED`. |
| Recognizable raw Type 1, CFF/CFF2, CIDFont, or PDF Font dictionary data | `FONT_FORMAT_UNSUPPORTED`; malformed or unrecognized bytes are `FONT_SOURCE_INVALID`. |
| Variable, color, SVG, or bitmap-only profiles (`fvar`, `COLR`, `CBDT`, `CBLC`, `sbix`, or `SVG `) | `FONT_FORMAT_UNSUPPORTED`; version 1 does not silently choose or flatten a presentation. |
| Font without the required sfnt directory, quadratic outlines, horizontal metrics, PostScript name, or usable Unicode cmap | `FONT_SOURCE_INVALID` when malformed; otherwise `FONT_FORMAT_UNSUPPORTED` when structurally valid but outside the closed profile. |
| Vertical writing or a caller-supplied encoding/CMap | Unsupported by this interface version. |

Unknown scaler signatures are unsupported only when they identify a known
font container; truncated, overlapping, out-of-range, duplicate-table, or
otherwise inconsistent sfnt structures are corrupt font data.

For the supported row, version 1 requires exactly `OS/2`, `cmap`, `glyf`,
`head`, `hhea`, `hmtx`, `loca`, `maxp`, `name`, and `post`; every additional
table is outside the profile. The ascending unique directory has contiguous
zero-filled aligned ranges, exact offset-table search fields, exact table
lengths where defined, and valid per-table and whole-font checksums. `head`,
`maxp`, and `hhea` use version 1 with their fixed lengths. Their magic,
reserved fields, units, outline-only global bounds, location format,
horizontal extrema, style bits, and applicable `head` flags must agree with
the glyphs and OS/2 data. Declared glyph maxima are enforced as upper bounds;
conservative overestimates are supported. `head` flags 2 through 10 must be
clear; flag 14
is the unsupported Last Resort profile and reserved flag 15 is corrupt.
Version 1 is instruction-free: glyph instruction lengths and the `maxp`
twilight, storage, function, instruction-definition, stack, and
instruction-size maxima must all be zero. Short and long `loca` are supported.
`hmtx` has the exact full or compact length implied by `numberOfHMetrics`,
including the shared final advance for trailing compact entries. `loca` has
its exact length, and every glyph location is ordered, word-aligned, and in
range. Simple `glyf` points agree exactly with their
declared bounds. Composite glyphs use the backend-safe `-1` contour marker and
may contain only XY translation components; other negative contour markers,
instruction-bearing, transformed/scaled, point-attached, or `USE_MY_METRICS`
components are outside the profile. Composite bounds and component IDs are
checked exactly;
points, contours, component counts, and depth must remain within their
declared maxima, and dependency graphs are checked iteratively and must be
acyclic.

`name` is format 0 with sorted, unique BMP-only Windows platform 3, encoding
1, English-US records. Its UTF-16BE storage immediately follows the records and
contains each nonempty string once in record order with no gaps or trailing
data. The closed profile admits exactly name IDs 1 through 6; other assigned
or font-specific IDs are outside the profile, while reserved ID 15, IDs 26
through 255, and IDs above 32767 are corrupt. Name ID 5 contains a decimal
major and minor version, each below 65,535, and name ID 6 is a valid nonempty
PostScript name. Format 1 and padded or
unreferenced-storage layouts are outside the profile. `post` accepts version 2
or 3; custom version-2 glyph names contain only ASCII letters, digits, period,
and underscore and are at most 63 bytes. OS/2 accepts the full
version 0 through 5 layouts of 78, 86, 96, 96, 96, or 100 bytes, respectively;
the historical 68-byte version-0 layout is outside the profile. OS/2 class,
style, cmap character bounds, permissions, and version-5 optical-size fields
must be consistent, and reserved Unicode-range and code-page-range bits are
clear, including code-page bit 8 while it is unassigned in OS/2 version 1.
Version 1 accepts only the no-classification value zero in
`sFamilyClass`. `achVendID` is either four zero bytes, four spaces, or a Tag
with one through four printable non-space bytes and trailing-space-only padding;
registered nonzero family classes are outside the closed profile. For OS/2
versions 0 and 1, permission bits outside `0x000e` are
outside this backend-safe profile; No Subsetting and Bitmap Embedding Only are
interpreted only in versions 2 and newer.

The font has exactly one contiguous cmap record and it must be one of Unicode
platform 0 encoding 1 or 3 with format 4, Unicode platform 0 encoding 4 with
format 12, or Windows platform 3 encoding 1 with format 4. A Windows platform
3 encoding 10 record, multiple records, and every other cmap profile are
outside version 1. Format-4 segments and format-12 groups must be ordered and
complete, contain valid Unicode scalars and glyph IDs, and provide at least
one nonzero mapping. Structurally valid profiles outside this exact set are
unsupported; inconsistent values are corrupt.

## Selection, mapping, metrics, and reuse

Selection visits input Unicode scalars in source order. For each scalar it
visits candidate fonts in declaration order and chooses the first usable
Unicode cmap entry whose glyph ID is nonzero and within the font's declared
glyph range. No name, style, locale, platform, filesystem, hash-map iteration,
or backend substitution participates. Equal source bytes and equal input order
therefore make the same selections on every execution and platform. A missing
glyph rejects the whole command; `.notdef` is never a successful fallback.

Version 1 encodes the selected glyph ID as the two-byte Identity-H character
code. A selected font may not map two distinct source Unicode scalars used in
one Document Session to the same glyph ID, because one PDF character code
cannot then preserve both source mappings. That case is rejected instead of
publishing ambiguous ToUnicode data. The generated ToUnicode mapping records
the actual selected source scalar. T13 continues to report missing or
contradictory source evidence as uncertain under ADR-0035 and never consults
the embedded font program to guess Unicode.

Horizontal advances come from `hmtx`. Version 1 computes each integral PDF CID
width with integer rational half-up rounding,
`(advance * 1000 + floor(unitsPerEm / 2)) / unitsPerEm`; no floating-point
intermediate participates. T19 writes those exact values into the descendant
Font both before use and after subset rebuilding. The generated text-showing
operators use those PDF widths for natural advance across a run and across
fallback switches. Version 1 applies no kerning, variation, substitution, or
layout adjustment. Public T13 geometry is compared with a `0.00001`
default-user-space tolerance because the PDF text processor carries the
standard PDF numeric pipeline through single-precision matrices.

Permitted fonts are subset by default. A subset contains glyph zero, every
selected glyph, and any transitive TrueType composite components required by
those glyphs; it excludes unrelated glyphs. Requested zero-width control
glyphs covered by the backend subset policy remain present but contour-free.
The deterministic six-uppercase-letter subset prefix is derived from the
selected glyph map, not from host state. Requested mappings take priority in
the generated ToUnicode CMap; PDFBox may also retain source-cmap mappings for
full fonts or unrequested composite dependencies. T19 writes exact width
entries for the selected glyphs. Before embedding, the generated
six-table `head`/`hhea`/`maxp`/`hmtx`/`loca`/`glyf` subset is revalidated,
its global outline bounds and horizontal extrema are normalized to its retained
glyphs, and its table and whole-font checksums are repaired and checked.

For an OS/2 `fsType` with Restricted License Embedding or Bitmap Embedding Only,
the command fails with `FONT_EMBEDDING_RESTRICTED`. A font declaring No
Subsetting is embedded in full and reopens as embedded but non-subset; this is
the documented limited outcome. Because full embedding uses the source cmap's
canonical reverse mapping, a requested scalar whose glyph has a different
canonical reverse mapping is rejected instead of emitting uncertain ToUnicode
data. Other installable, editable, or preview/print embedding settings are
accepted subject to their declared permissions.

Within one Document Session, source programs with identical bytes share one
loaded effective font and one indirect document Font resource. Repeated
commands and different source declarations with the same bytes reuse that
resource. Page Resource names are private; an existing alias for that exact
resource is reused, otherwise the first available deterministic
`FolioT19F<n>` name is declared. This identity guarantee does not claim that a
previously subset Font reopened in a later Document Session is byte-identical
to its original source program.

Every subsequent Document Command and every Document Query is an ordering
barrier for earlier T19 work. Pending subsets are finalized in deterministic
first-use order before that operation. If a later T19 command selects another
glyph from the same source, T19 rebuilds the effective subset behind the same
persistent Font dictionary and indirect resource instead of adding a
duplicate. It replaces only T19-managed entries in the persistent Type 0 Font,
CIDFont, and Font Descriptor dictionaries, preserving unknown entries written
there by earlier commands; a later command likewise cannot be erased by
deferred finalization.

## Limits

Every `FontLimits` version-1 field is mandatory and nonnegative. Each counter
is checked before the next value is accepted. An exact declared boundary
succeeds and the first excess fails atomically with `FONT_LIMIT_EXCEEDED`:

- `maximumFontSources` counts every source declaration in the selected ordered
  set, including duplicates and sources not chosen for a glyph;
- `maximumSourceBytes` counts the complete staged byte length of every source
  occurrence, including equal or reused sources;
- `maximumCodePoints` counts Unicode scalars in the nonempty input String;
- `maximumFallbackChecks` counts each ordered candidate-font visit for each
  input scalar, including a successful visit; and
- `maximumGeneratedContentBytes` counts the exact bytes in the new isolated
  page-content operator stream, including its save/restore and text-object
  delimiters but excluding retained existing streams and embedded font data.

These command-local bounds constrain T19 materialization. The comprehensive
process, memory, time, recursion, concurrency, temporary-storage, and hostile
multi-tenant policy remains T20/T21 scope.

## Preservation, publication, and failures

The command has the same conservative page-content/resource preservation
preconditions as Canvas drawing: existing Contents must be absent/null, one
indirect stream, or an array of indirect streams; retained streams cannot use
external-file data; combined decoded existing content is limited to 8 MiB and
must have valid syntax and balanced graphics, text, marked-content, and
compatibility scopes; and effective Resources inheritance is limited to 64
acyclic parent steps. All font input, selection, encoding, generated content,
and preservation checks finish before the page dictionary is changed.

Unsigned REWRITE and INCREMENTAL publication admit `DrawPositionedUnicodeText`.
Existing Signatures reject it under the same conservative T15 policy as
Canvas, and password-authenticated user authority requires general document
modification permission. A successful command reports
`composition.fonts.load-embed-subset-fallback`.

All positioned Unicode text requires published effective PDF 1.2 or newer,
the first PDF version that defines Type 0 fonts. An INCREMENTAL publication of
a PDF 1.0 or 1.1 source therefore fails before page mutation.
Supplementary Unicode mappings require a published effective PDF version of
1.5 or newer because their ToUnicode values use UTF-16 surrogate pairs.
Consequently, an INCREMENTAL publication of a PDF 1.2–1.4 source rejects a
command before page mutation when any selected source font has a supplementary
cmap mapping, even when the requested text is BMP-only. This conservative
boundary covers full embedding and unrequested retained composite dependencies;
BMP text remains admitted with selected BMP-only sources. REWRITE output is
already limited to PDF 1.7 or PDF 2.0.

Operational failures use that capability identifier and fixed diagnostics;
none includes font bytes, source text, a path, resource name, source hash, or
backend exception.

| Code | Fixed diagnostic |
| --- | --- |
| `POSITIONED_TEXT_INVALID` | `The positioned Unicode text declaration is invalid.` |
| `PDF_VERSION_UNSUPPORTED` | `Positioned Unicode text requires PDF 1.2 or newer.` |
| `PDF_VERSION_UNSUPPORTED` | `Supplementary Unicode mappings require PDF 1.5 or newer.` |
| `FONT_SOURCE_INVALID` | `The font source could not be loaded safely.` |
| `FONT_FORMAT_UNSUPPORTED` | `The font format or profile is unsupported.` |
| `FONT_EMBEDDING_RESTRICTED` | `The font embedding permissions reject this operation.` |
| `FONT_GLYPH_MISSING` | `No declared font contains every requested Unicode scalar.` |
| `FONT_MAPPING_UNSUPPORTED` | `The requested Unicode mapping cannot be represented safely.` |
| `FONT_LIMIT_EXCEEDED` | `The font operation limit was exceeded.` |
| `POSITIONED_TEXT_PRESERVATION_UNSUPPORTED` | `The page content or resources cannot be preserved safely for positioned text.` |
| `PAGE_RANGE_INVALID` | `The positioned-text page selection is invalid.` |
| `DOCUMENT_PERMISSION_DENIED` | `The Source credential does not authorize positioned text.` |
| `SIGNATURE_POLICY_REJECTED` | `The Existing Signature policy does not permit positioned text.` |
| `DOCUMENT_WRITE_FAILED` | `The positioned Unicode text could not be applied.` |

Ordinary Source, Target, validation, cancellation, deadline, and publication
failures retain their existing workflow contracts. A T19 failure before
publication leaves every declared Target `NOT_ATTEMPTED` and exposes no
partially successful command result.

## Example

```java
ReferenceFontSet referenceFonts = ReferenceFontSet.version1(
        FontSource.path(primaryFont),
        FontSource.bytes(fallbackFontBytes));
WorkflowEnvironment environment = WorkflowEnvironment.builder()
        .referenceFontSet(referenceFonts)
        .build();

PositionedUnicodeText text = PositionedUnicodeText.version1(
        "A\u03a9",
        FontSelection.referenceFontSet(),
        12,
        TextRenderingMode.FILL,
        CanvasMatrix.of(1, 0, 0, 1, 36, 72));
FontLimits limits = FontLimits.builder()
        .maximumFontSources(2)
        .maximumSourceBytes(2 * 1024 * 1024L)
        .maximumCodePoints(2)
        .maximumFallbackChecks(3)
        .maximumGeneratedContentBytes(4096)
        .build();

new DocumentWorkflow(environment).execute(request, session -> {
    session.execute(DrawPositionedUnicodeText.version1(1, text, limits));
    return null;
});
```

T19 adds no Migration Facade mapping. It does not change
`CanvasFont.version1`, discover system fonts, bundle a runtime font, shape
complex scripts, render pages, or implement downstream layout capabilities.

## Evidence and status

`FontLoadingWorkflowTest` exercises byte, Path, stream, channel, and configured
Reference Font Set sources through `DocumentWorkflow.execute`, publication,
reopen, T13/T14 queries, and bounded public PDF Value inspection. It also
covers strict fallback, duplicate charging and resource reuse, exact-rational
source metrics, ToUnicode, subsets and the No Subsetting outcome, every caller
limit at its exact boundary and first excess, checksum and complete core-table
structural corruption (including composite cycles), query/command-barrier
subset expansion with nested dictionary preservation, clipping-mode and
pre-1.2/pre-1.5 mapping-version rejection, atomic failure, diagnostic redaction,
unsigned incremental publication, Existing Signature rejection, and
restricted-user permission rejection. The existing
T13, T14, T17, and T18 suites remain regression authority for their unchanged
public contracts.

The repository Acceptance Profile
`T19-font-loading-embedding-subsetting` records pinned qpdf syntax, public
project-semantic, and pinned PDFium/ImageMagick visual chains over one unchanged
workflow-produced artifact. The two small font fixtures and visual expectation
are project-authored evidence data with hashes and scope recorded in their
READMEs and `PROVENANCE.md`; no font is shipped by `pdf-document`.

Those three chains pass, but mandatory independent standards evidence remains
absent. The T13, T14, and T17 compatible-status Dependency Gates and the T06
Promotion Gate remain open, so the Capability Matrix truthfully keeps T19
`experimental` and makes no compatible or certified-platform claim.
