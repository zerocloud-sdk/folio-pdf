# Paragraph composition across explicit page areas

T24 provides `ComposeParagraphs.version1(flow, limits)` through
`DocumentWorkflow.execute`. The closed, immutable `ParagraphFlow` declares
new pages and an ordered sequence of semantic paragraphs and area breaks.
The same declaration executes in `IN_PROCESS` and `HARDENED_WORKER`.
The capability is `composition.layout.paragraph-areas`; its state remains
**experimental**. This is the T24 slice, not the full Foundation Composition
Profile required by ADR-0022.

## Pages, margins and areas

`LayoutPage.version1(width, height, margins, areas...)` declares an unrotated
page in PDF points with origin `(0, 0)`. Width and height must be finite,
positive and at most 14,400 points. `PageMargins.of(top, right, bottom, left)`
declares finite nonnegative insets. The remaining margin box must have
positive width and height.

With no explicit areas, the complete margin box is one area. Otherwise each
`CanvasRectangle` is relative to the **bottom-left of the margin box** and
must fit completely inside that box. Areas are consumed in declaration order;
the engine does not sort columns or infer reading order. Areas may overlap
when explicitly declared that way. Content starts at an area's top-left.

Pages are appended to the current document. Only the prefix reached by the
flow is appended; unused trailing page declarations do not create pages.
An item that does not fit advances to the next declared area and recomputes
the line for its width. Skipped earlier pages remain in the appended prefix.
The finite page list never repeats implicitly. `areaBreak()` advances exactly
one area, including from an empty area, and fails if no next area exists.

All original page dimensions and content are preserved. Version 1 composes
into newly declared pages; it does not fill arbitrary existing page regions.

## Paragraphs and inline content

`Paragraph.version1(leading)` begins a semantic paragraph. Its builder accepts:

- `text(unicode, fontSize)`: nonempty unshaped Unicode at an explicit positive
  size, using the flow's ordered `FontSelection`.
- `graphic(group, width, height)`: an atomic `CanvasTransparencyGroup`, with
  its local bounding box scaled to the declared positive dimensions. Its
  bottom edge sits on the text baseline. The existing Canvas group contract
  provides clipping, graphics-state isolation and explicit resource ownership.
- `alignment(LEFT | CENTER | RIGHT | JUSTIFIED)`: left is the default.
- `maximumWidth(points)`: caps the available paragraph width; zero, the
  default, uses the area's full width. The cap is anchored at the area's left.

Numbers must be finite and within the existing Canvas absolute bound of
1,000,000,000; page geometry has the tighter bounds above. Full numeric and
content validation occurs during command execution. Null required values and
incomplete or negative limit declarations are programming errors.

Version 1 uses greedy advance-width wrapping. ASCII space is a preferred
break opportunity, as is the boundary after an inline graphic. A word wider
than a line can break at Unicode scalar boundaries. Graphics never split.
Spaces are preserved, including at automatic line boundaries; there is no
whitespace collapse or silently discarded trailing text. LF is an explicit
line break and contributes no PDF glyph. Consecutive LFs reserve empty lines.
A terminal LF finishes its current line and does not create another empty
line. Other ISO control characters, including tab and CR, are rejected.
Unpaired surrogates are rejected.

Width and height fits allow `0.000000001` point for floating-point rounding;
fractional advances and leading accumulate with compensation. This numerical
tolerance is separate from resource limits, whose byte and count bounds are exact.

Font selection, source staging, embedding permissions, missing glyphs,
ToUnicode, subset rebuilding and exact integral PDF CID widths are reused
from [T19](font-loading.md). Each source is staged once for the entire flow;
path contents are observed at command execution and caller streams/channels
remain caller-owned. The same one-shot source declaration is cached for the
Session. No system font discovery, network lookup, normalization, bidi,
shaping, kerning, language-specific breaking or hyphenation occurs.

For each line, ascent and descent are the maximum selected source-font
`head` bounds scaled by the explicit text sizes, with graphic height included
in ascent. The baseline is at the current area top minus ascent. The line
box reserves `max(leading, ascent + descent)` points. The next line starts
below that box; paragraphs add no implicit before/after spacing. Equal
content extents therefore produce baseline distances controlled by leading.
Content taller than the specified leading enlarges the line box.

LEFT, CENTER and RIGHT place the natural advance width against the available
width. JUSTIFIED distributes remaining width after nonterminal ASCII spaces
on automatic, nonfinal lines. Final lines and lines ended by LF remain left
aligned. A line without an expandable space also remains left aligned.

## Finite limits and failure behavior

Every `CompositionLimits` builder field is mandatory:

| Limit | Scope |
| --- | --- |
| `maximumPages` | All page declarations, including unused trailing pages |
| `maximumAreas` | All declared areas; an implicit margin-box area counts as one |
| `maximumFlowItems` | Paragraphs and area breaks |
| `maximumInlines` | Total text and graphic declarations across the flow |
| `maximumLines` | All laid-out lines, including empty LF lines |
| `maximumGeneratedContentBytes` | Exact aggregate new page operator-stream bytes, excluding resource streams |
| `fontLimits` | One aggregate T19 source, byte, scalar, fallback-check and text-operator budget |
| `graphicLimits` | T18 resource limits applied separately to each atomic graphic |

The scalar budget includes LF; fallback visits exclude LF because it is not
drawn. Graphic resource streams are bounded by the per-graphic Canvas limits
and the finite aggregate inline count. The workflow's page, object, modeled
owned-memory, decompression, time and temporary-storage policy also applies.
Composition accounts its temporary text copies, atoms, areas and line plans
for the command lifetime, and uses the existing owned-byte staging paths.
These are modeled ownership limits, not measurements of JVM heap or RSS.

Exact boundaries succeed and the first excess fails. All layout is determined
before painting detached pages; pages join the document only after painting
and font finalization succeed. Operational failures abort publication when
propagated from the callback, leaving every target `NOT_ATTEMPTED`. As with
other commands, catching a recoverable failure inside the callback permits
the caller to continue with previously successful work.

| Stable code | Meaning |
| --- | --- |
| `COMPOSITION_INVALID` | Invalid page, margin, area, paragraph or inline declaration |
| `COMPOSITION_AREA_EXHAUSTED` | The remaining finite areas cannot hold the next content, or an area break has no destination |
| `COMPOSITION_LIMIT_EXCEEDED` | A declaration, line or aggregate page-operator bound was exceeded |
| T19 font failure codes | Font acquisition, selection, mapping, embedding or aggregate font limits failed |
| T17/T18 Canvas failure codes | An inline graphic or its resource budget failed |

Font and Canvas failures arising during composition retain their existing
fixed diagnostics and carry the T24 capability identifier. Diagnostics never
include source text, font bytes, paths, hashes or resource names. Workflow
resource, source, target and publication failures retain their existing codes
and capability identifiers.

Queries remain ordering barriers, batches stop at the first failed command,
and Sessions expire at workflow return. Unsigned REWRITE and INCREMENTAL
publication are supported. Existing Signature restrictions remain enforced
before caller font resources are opened. Password-authenticated user access
requires both document modification and assembly permission; protected
rewrite retains the existing owner-authority requirement. Font use also
retains T19's minimum PDF-version checks.

Worker commands carry only the project-defined versioned declarations and
opaque font-source identifiers. They do not transport callbacks, classes,
font paths or arbitrary code. Font programs are requested in declaration
order through the existing bounded resource transport. Worker message and
modeled memory bounds still apply to the complete declaration.

## Example

```java
ParagraphFlow flow = ParagraphFlow.version1(FontSelection.referenceFontSet())
        .page(LayoutPage.version1(612, 792, PageMargins.of(72, 72, 72, 72)))
        .page(LayoutPage.version1(612, 792, PageMargins.of(72, 72, 72, 72)))
        .paragraph(Paragraph.version1(16)
                .text("A paragraph using explicitly supplied fonts.", 12)
                .alignment(Paragraph.Alignment.LEFT)
                .build())
        .build();

// Configure an explicit ReferenceFontSet in the WorkflowEnvironment and
// supply complete CompositionLimits, FontLimits and CanvasResourceLimits.
workflow.execute(request, session -> {
    session.execute(ComposeParagraphs.version1(flow, compositionLimits));
    return session.query(PageCount.INSTANCE);
});
```

Only a font program admitted by the current T19 profile can be selected.
The project acceptance fonts deliberately cover a small alphabet; they are
not a runtime font bundle or the certified Foundation Noto Reference Font Set.

## Evidence and exclusions

[The T24 Acceptance Profile](../capabilities/evidence/T24-paragraph-composition.md)
records the public two-profile contracts, hand-calculated semantic/geometry
oracle, qpdf evidence and independent PDFium/ImageMagick comparison for both
pages. Geometry tolerance is specific to that pinned corpus, not a blanket
accuracy promise for arbitrary coordinates and fonts.

Indentation, tabs, keep, widow/orphan behavior and advanced overflow/relayout
are available through the opt-in version 2 [T25 contract](paragraph-pagination.md). Tables belong to #27/#28; Unicode layout belongs to #29.
No backend SPI, module cycle, placeholder artifact or Migration Facade stub
is introduced. The existing Preview `layout.Document` is not a paragraph
mapping. The Facade Surface Manifest records a T24 exclusion until an actual
mapping is implemented and evidenced. Missing standards evidence and open
compatibility Dependency Gates prevent promotion to `compatible`.
