# Canvas vector graphics and positioned text

T17 exposes a backend-neutral, low-level Canvas Program through the Native
Interface. Canvas-specific values live under
`net.zerocloud.pdf.composition`, the command lives under its `command`
subpackage, and the shared observed/declared `TextRenderingMode` remains in
the Document Workflow root namespace. A caller builds an immutable
`CanvasProgram`, then executes
`DrawCanvas.version1(pageNumber, program)` inside
`DocumentWorkflow.execute`. The command appends drawing to the selected
one-based page. Callers supply only Folio PDF and JDK values: there is no raw
content-stream input, callback, custom command implementation, PDFBox object,
or public backend SPI.

T17 belongs to the Composition context, while the existing `pdf-document`
artifact remains the deep module that owns the transaction, PDF Value, page,
publication, and private-backend seams. No new artifact or dependency is
introduced.

## Coordinates and operations

Canvas coordinates use PDF default user space. Before any page content is
executed, `(0, 0)` is at the lower-left of the unrotated page and one unit is
1/72 inch, scaled by the page's `UserUnit` when present. Page display rotation
is not folded into Canvas coordinates.

`CanvasMatrix.of(a, b, c, d, e, f)` maps `(x, y)` to
`(a*x + c*y + e, b*x + d*y + f)`. `transform` concatenates that affine
transformation with the current transformation matrix. `beginText` and
`setTextMatrix` use the same six-value representation as a text matrix.

Version 1 supports this closed operation set:

| Canvas operation | Meaning |
| --- | --- |
| `saveState`, `restoreState` | Nest and restore the PDF graphics state. |
| `transform` | Concatenate an affine transformation. |
| `moveTo`, `lineTo` | Start a subpath and append a straight segment. |
| `curveTo` | Append a cubic Bezier segment with two controls and one endpoint. |
| `closePath` | Close the current subpath. |
| `stroke` | Stroke and consume the current path. |
| `fill(NONZERO)`, `fill(EVEN_ODD)` | Fill and consume the current path with the selected winding rule. |
| `clip(NONZERO)`, `clip(EVEN_ODD)` | Intersect the current clipping path, then consume the construction path. |
| `beginText`, `setTextMatrix`, `showGlyph`, `endText` | Show explicitly configured, already encoded glyph codes. |

Painting uses the graphics parameters already in effect in the isolated
Canvas scope. Version 1 does not expose colors, line style, calibrated or ICC
color, alpha, blend modes, masks, transparency groups, shading, patterns, or
image XObjects. T18 adds the closed version-2 image, color, mask, and
transparency operations documented in
[Canvas images, color, and transparency](canvas-images-colors-transparency.md);
it does not change version-1 behavior.

## Closed state machine

Validation covers the complete immutable program before the target page is
changed. The main state is one of:

- `IDLE`: no construction path and no text object;
- `PATH`: a path with a current point exists;
- `TEXT_READY`: a text object is open and an explicit matrix is ready for
  exactly one glyph; or
- `TEXT_NEEDS_MATRIX`: a glyph was shown and another glyph requires an
  explicit `setTextMatrix` first.

Graphics-state depth is an orthogonal counter from zero through 64. For a
constructed program, the table is exhaustive for version 1; any condition in
the Rejected column produces `CANVAS_PROGRAM_INVALID` when the command
executes. Required null value objects are rejected by the builder before a
program exists.

| Instruction | Accepted state and transition | Rejected condition |
| --- | --- | --- |
| `saveState` | `IDLE -> IDLE`, depth increases by one | `PATH`, either text state, or depth already 64 |
| `restoreState` | `IDLE -> IDLE`, depth decreases by one | `PATH`, either text state, or depth zero |
| `transform(matrix)` | `IDLE -> IDLE` | `PATH`, either text state, or invalid matrix value |
| `moveTo(x,y)` | `IDLE/PATH -> PATH` | either text state or invalid coordinate |
| `lineTo(x,y)` | `PATH -> PATH` | `IDLE`, either text state, or invalid coordinate |
| `curveTo(c1x,c1y,c2x,c2y,x,y)` | `PATH -> PATH` | `IDLE`, either text state, or invalid coordinate |
| `closePath` | `PATH -> PATH` | `IDLE` or either text state |
| `stroke` | `PATH -> IDLE` | `IDLE` or either text state |
| `fill(rule)` | `PATH -> IDLE` | `IDLE` or either text state |
| `clip(rule)` | `PATH -> IDLE` | `IDLE` or either text state |
| `beginText(font,size,mode,matrix)` | `IDLE -> TEXT_READY` | `PATH`, either text state, or invalid size/matrix |
| `setTextMatrix(matrix)` | `TEXT_NEEDS_MATRIX -> TEXT_READY` | `IDLE`, `PATH`, `TEXT_READY`, or invalid matrix |
| `showGlyph(code)` | `TEXT_READY -> TEXT_NEEDS_MATRIX` | `IDLE`, `PATH`, `TEXT_NEEDS_MATRIX`, or a zero-length or over-four-byte code |
| `endText` | `TEXT_NEEDS_MATRIX -> IDLE` | `IDLE`, `PATH`, or `TEXT_READY` (an empty text object) |
| end of program | remains `IDLE` at graphics depth zero | empty program, unconsumed path, open text object, or unclosed graphics-state scope |

A program contains from 1 through 10,000 instructions. Its generated content
is at most 1 MiB. Every number must be finite and have absolute value no
greater than 1,000,000,000; font size must additionally be greater than zero
and no greater than 1,000,000. Builder null checks reject absent value objects
before a program can be built. Cross-instruction and numeric errors are
deliberately reported together at command execution so the workflow can
return its checked failure and publication receipts.

## Explicit positioned text and font ownership

Every `beginText` supplies all four declarations needed for the next glyph:

- a `CanvasFont` naming an existing indirect Font object exposed by the
  current `DocumentSession`;
- a positive font size;
- one `TextRenderingMode`; and
- an explicit text matrix.

`showGlyph` accepts one already encoded character code and consumes the ready
matrix. A later glyph in the same text object must be preceded by
`setTextMatrix`; implicit glyph advance is never used as placement intent.
All PDF text rendering modes are supported: `FILL`, `STROKE`, `FILL_STROKE`,
`INVISIBLE`, `FILL_CLIP`, `STROKE_CLIP`, `FILL_STROKE_CLIP`, and `CLIP`.
`TextItem.getRenderingMode()` exposes the observed mode when the published
page is reopened through the public T13 query, while `TextGeometry` exposes
the resulting positioned geometry.

`CanvasFont.version1(reference)` is a borrowed declaration, not ownership of
font bytes. The reference must have been exposed in the same live Session and
must resolve to an indirect `/Type /Font` dictionary. Version 1 admits:

- simple `Type1`, `MMType1`, and `TrueType` fonts with `BaseFont`, using
  exactly one code byte per glyph; and
- `Type0` fonts with `BaseFont`, `Identity-H` or `Identity-V`, and exactly one
  `CIDFontType0` or `CIDFontType2` descendant, using exactly two code bytes.

Type 3 fonts and other encodings are rejected. T17 does not discover, load,
embed, subset, map, synthesize `ToUnicode`, or fall back between fonts; those
are T19 concerns. Up to 256 distinct Canvas Font declarations may occur in
one program. If the page's effective Font resources already name the same
indirect font object, that name is reused. Otherwise Folio adds one private
page-local declaration. Later commands using that object reuse the declaration
instead of creating unbounded aliases. Callers must not depend on the private
resource name.

## Preservation and publication

Canvas output is appended in its own balanced save/restore scope. Existing
content is retained ahead of it in a separate save/restore scope, and any
construction path left by otherwise valid existing content is explicitly
discarded before Canvas drawing begins. Existing page resources are retained;
inherited resources are shallow-copied to the page only when a new Font name
must be added.

Preservation is accepted only when Folio can prove all of the following before
changing the page:

- `Contents` is absent/null, one indirect stream, or an array containing only
  indirect streams;
- no retained stream uses an external-file `F` entry;
- combined decoded existing content is at most 8 MiB and passes the bounded
  content syntax preflight;
- `q/Q`, `BT/ET`, `BMC` or `BDC`/`EMC`, and `BX/EX` scopes balance, and text
  operators occur only inside `BT/ET`; and
- the effective `Resources` and optional `Font` values are dictionaries, with
  inheritance bounded to 64 acyclic parent steps.

If any proof fails, the command rejects the operation before page mutation.
The workflow operates on a private staging document in all cases, so a failed
command never publishes it and leaves every Source and Target unchanged.

Both unsigned `REWRITE` and unsigned `INCREMENTAL` publication explicitly
admit `DrawCanvas`. Incremental output retains the complete primary Source as
an unchanged prefix and appends a revision under the T15 rules. Any Existing
Signature rejects Canvas mutation in version 1, including DocMDP P=3; T17 does
not infer permission from an annotation exception. A password-authenticated
user must have general document-modification permission. Owner and unrestricted
authority follow the existing T16 policy. These checks intersect: encrypted,
signed incremental input must pass both policies, and no security or signature
state is silently changed.

Successful workflows that execute Canvas drawing report
`composition.canvas.draw-positioned-text` as the capability identifier.

## Failures

Operational failures are checked `DocumentFailure` values with the T17
capability identifier and fixed diagnostics once `DrawCanvas` is dispatched.
Diagnostics never include content bytes, glyphs, resource names, credentials,
paths, or backend exceptions.

| Code | Fixed diagnostic | Meaning |
| --- | --- | --- |
| `CANVAS_PROGRAM_INVALID` | `The Canvas Program is invalid.` | Invalid state transition, size, number, glyph length, instruction count, or generated-size bound. |
| `CANVAS_RESOURCE_INVALID` | `The Canvas Font resource is invalid.` | Wrong/expired Session reference, non-indirect or unsupported Font, wrong code width, or exhausted private Font aliases. |
| `CANVAS_PRESERVATION_UNSUPPORTED` | `The page content or resources cannot be preserved safely for Canvas drawing.` | Existing content/resource structure or preservation bound is unsafe. |
| `PAGE_RANGE_INVALID` | `The Canvas page selection is invalid.` | The one-based target page does not exist. |
| `DOCUMENT_PERMISSION_DENIED` | `The Source credential does not authorize Canvas drawing.` | T16 user authority lacks general modification permission. |
| `SIGNATURE_POLICY_REJECTED` | `The Existing Signature policy does not permit Canvas drawing.` | The Source has an Existing Signature. |
| `DOCUMENT_WRITE_FAILED` | `The Canvas Program could not be applied.` | Staging could not create or write the isolated content streams. |

Ordinary request, Source, validation, and Target-publication failures retain
their existing workflow codes, capability attribution, and safe diagnostics.
In particular, a signed `REWRITE` request with a Target is rejected by T15
before caller work can reveal a future command, using
`SIGNED_REWRITE_REJECTED` and `document.incremental-signature.protect`;
signed `INCREMENTAL` dispatch and target-free signed command dispatch identify
`DrawCanvas` and use the T17 `SIGNATURE_POLICY_REJECTED` row above. On a
pre-publication T17 failure, every declared receipt is `NOT_ATTEMPTED`. As with all workflow
publication, a failure while committing multiple Targets may leave an earlier
Target committed and reports the declaration-ordered receipts precisely; no
cross-Target atomicity is claimed.

## Example

```java
CanvasFont font = CanvasFont.version1(existingFontReference);
CanvasProgram program = CanvasProgram.version1()
        .saveState()
        .transform(CanvasMatrix.of(1, 0, 0, 1, 12, 18))
        .moveTo(10, 10)
        .curveTo(20, 40, 60, 40, 80, 10)
        .stroke()
        .beginText(
                font,
                12,
                TextRenderingMode.FILL,
                CanvasMatrix.of(1, 0, 0, 1, 24, 72))
        .showGlyph(new byte[] {65})
        .endText()
        .restoreState()
        .build();

session.execute(DrawCanvas.version1(1, program));
```

The Font reference is normally obtained through the public T14 resource
inventory in the same Session. The example deliberately uses an encoded glyph
code rather than Unicode text.

T17 itself adds no image embedding or transparency; those are the separate
T18 version-2 extension. It adds no T19 font acquisition; T20 comprehensive
hostile-input policy; T21 Worker isolation; T23 renderer; paragraph/table
layout; barcode generation; Forms; tagged-document construction; SVG
conversion; redaction; or Migration Facade mapping.
