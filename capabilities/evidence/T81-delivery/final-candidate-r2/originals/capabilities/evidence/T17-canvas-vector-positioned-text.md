# T17 Canvas vector graphics and positioned-text evidence

Status: `experimental`

Capability: `composition.canvas.draw-positioned-text`

Acceptance Profile: `T17-canvas-vector-positioned-text`

Release train: `0.1.0-SNAPSHOT`

T17 adds one immutable, versioned Canvas Program and one library-owned,
page-targeted command at the public Document Workflow seam. It covers vector
paths, both fill and clip winding rules, affine transformations, nested
graphics state, all eight text rendering modes, and explicitly positioned
already encoded glyphs using existing Session-owned Font references.

## Implementation evidence

- `CanvasWorkflowTest` drives every operation through
  `DocumentWorkflow.execute`, publishes and reopens products through public
  queries, covers both save modes, resource reuse and preservation, and proves
  stable failures, unchanged Sources and Targets, and `NOT_ATTEMPTED` receipts.
- The command validates the entire closed Canvas state machine, finite numeric
  bounds, glyph widths, Font object shape, existing content, effective
  resources, and bounded inheritance before changing the page. Existing and
  generated content receive separate balanced isolation scopes.
- `TextItem.getRenderingMode()` and existing T13 geometry observations expose
  the positioned-text result after reopen. T09 inspection and T14 resource
  inventory expose project-owned path/content and resource observations; no
  PDFBox type or object identity is asserted.
- Incremental and Existing Signature policies explicitly classify
  `DrawCanvas`; T16 permission enforcement explicitly requires general
  modification authority. Public API and artifact tests retain Java 8 class
  files, module identity, notices, and unbundled PDFBox and FontBox runtimes.
- The repository acceptance command creates one project-authored product
  through two Canvas commands, preserving existing content and resources while
  exercising lines, a cubic curve, stroke, both fill rules, transformations,
  both clipping rules, nested state, all eight rendering modes, explicit text
  matrices and glyphs, and Font declaration reuse.

## Independent evidence and status boundary

The T17 qpdf record supplies a passing syntax chain over the unchanged
workflow-produced artifact. The separate project semantic record reopens that
same artifact through public Folio PDF queries and compares its path, state,
resource, preservation, rendering-mode, and glyph-geometry observations with
project-owned Canvas Program expectations. It does not use PDFBox behavior,
identity, or serialized byte ordering as an oracle.

Mandatory standards and visual Acceptance Evidence remain absent. The
`document.value.inspect-patch` Dependency Gate remains open because T09 is
still `experimental`; closing its implementation issue does not satisfy the
required `compatible` status. T06 remains a promotion gate. T17 therefore
remains `experimental`, with no compatible or certified-platform claim.

T17 makes no color, transparency, image, font-acquisition, layout, rendering,
Forms, tagging, SVG, redaction, hostile-input, or Worker-isolation claim.
