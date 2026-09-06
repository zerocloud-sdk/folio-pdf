# Bounded table composition

T26 adds `ParagraphFlow.version3`, `ComposeParagraphs.version3` and
`CompositionLimits.version3`. A flow admits version 1/2 paragraphs, area
breaks and `table(Table)` items. Existing version 1/2 contracts are unchanged.
The capability is `composition.layout.tables`, experimental.
Version 3 is immediate: its semantic flow cannot be relaid out or flushed
incrementally. It still publishes only after `DocumentWorkflow.execute`
returns successfully.

## Table and cell declarations

`Table.version1(layout, width, columns...)` declares a finite rectangular
grid, where layout is `FIXED` or `AUTO`. `TableWidth.points(n)` and
`TableWidth.percentage(n)` specify widths; `TableWidth.auto()` is permitted
only for columns. A table's percentage is relative to the current Layout
Area's width; column and cell percentages are relative to the resolved table
width. Table and explicit column widths must be positive; percentages must
be at most 100. No scaling or implicit normalization occurs.

`TableRow.version1(minimumHeight, cells...)` declares cells in reading order
and a nonnegative minimum row height in points. The overload without a height
uses zero. Each `TableCell.version1()` builder declares zero or more version-1
paragraphs, positive `rowspan` and `colspan` (both default to 1),
`CellPadding.of(top, right, bottom, left)`,
`TableBorders.of(top, right, bottom, left)` and an optional nonnegative
`minimumWidth(TableWidth)`. Padding and borders default to zero. Cell minimum
width defaults to zero points; AUTO is invalid for a cell minimum. Empty cells
are explicit and legal. Nested tables and version-2 cell paragraphs are outside
this representation. The existing T24 text, graphics, alignment, leading,
font, whitespace and [Unicode cluster/line rules](unicode-composition.md) apply inside each cell.

Rows are visited in declaration order. Within a row the next cell starts at
the first unoccupied column; its rectangular span must fit within declared
rows and columns and may not overlap an earlier cell. Every slot must be
covered exactly once. A completely covered continuation row may declare no
cells. Holes, overfull rows, overlap, nonpositive spans and spans extending
beyond the grid fail with `TABLE_INVALID_SPAN` before opening font sources.
No phantom or padded cells are inserted. Painting emits complete cell contents
in row/cell/paragraph declaration order, including a spanning cell before later
cells in its starting row. PDF extraction therefore preserves this order;
this is semantic input order, not Tagged PDF table structure.

## Width resolution

In FIXED layout, explicit column widths are exact. AUTO columns equally divide
the remaining table width. With no AUTO column, explicit widths must sum to
the table width. Every resolved column must be positive. Each cell must satisfy
its minimum width and contain its largest complete grapheme advance or atomic graphic,
padding and borders. Failure to fit is never clipping or silent truncation.

AUTO keeps explicit columns exact. For each cell, intrinsic minimum is its
largest complete grapheme advance or graphic width plus horizontal padding and borders,
raised to its declared minimum width. Preferred width is the largest sum of
advances between forced line separators (LF, U+2028 or U+2029) plus these insets,
raised to intrinsic minimum. Paragraph
width caps affect line layout, not these intrinsic measurements.

Start AUTO columns at zero and explicit columns at their exact widths. Visit
cell constraints ordered by ending column, then decreasing starting column,
then declaration order. For each minimum deficit, grow the rightmost AUTO
column in the span. With only lower interval constraints and exact fixed
columns this constructs a minimum-total feasible allocation. A deficit in a
span containing no AUTO column is unsatisfiable. Copy this minimum allocation
to the preferred allocation; visit the same ordered cells, sharing each
preferred deficit equally among the span's AUTO columns. Preferred deficits
in an entirely fixed span do not change its columns.

Let M and P be the total minimum and preferred widths and W the table width.
If M > W, the area cannot fit the table. If M <= W < P, interpolate each
column by `(W-M)/(P-M)` from minimum to preferred. If W >= P, distribute
`W-P` equally among all AUTO columns. With no AUTO column the exact sum rule
still applies. A zero column or nonpositive content box cannot fit. Fits allow
the existing `0.000000001`-point arithmetic tolerance; count and byte limits
remain exact.

The deterministic allocation does not redistribute an already satisfied
minimum to eliminate zero columns. For example, a 12-point table with two
AUTO columns and one two-column cell whose minimum is 12 resolves `[0,12]`
and is rejected, even though another allocation could be positive. Declare
two explicit 6-point columns, or provide surplus table width (14 resolves
`[1,13]`), to admit that spanning cell.

## Rows, padding and borders

Layout every cell's paragraphs at its resolved span width minus its left/right
padding and borders, keeping all lines and computing their total height.
Initialize rows to their declared minimum heights. Apply single-row cells'
required heights first. Then visit spanning cells in increasing rowspan,
then declaration order, sharing any height deficit equally among their rows.
A cell's required height includes all lines and its top/bottom padding and
borders. Empty content requires just those insets. The top of a cell is the
top of its starting row; its height is the sum of its covered row heights.
Content aligns to the top-left inside its border and padding. There is no
implicit vertical centering or extra interparagraph spacing.

Each cell owns four solid black strips **inside** its rectangle. The top and
bottom strips cover its full width; left and right strips cover its full
height. Corners form their union. Adjacent cells retain both strips, so an
edge with widths 1 and 2 has a combined visible thickness of 3 points. Spans
have only their outer four strips, with no internal grid lines. Padding is
measured inward from the inner edge of the strip. This separate-border rule
does not collapse borders or use paint-order tie breaking.

The complete table must fit the unused height and full width of one declared
area. Otherwise the planner tries later areas and recalculates widths there.
It never splits rows or tables. The next flow item starts below the table.
A preceding paragraph's keep-with-next still prevents an intervening area
break. Tables have no keep, headers, footers, relayout or large-table streaming
options; those belong to #28.

The opt-in T27 version-4 flow and version-2 Table extension is described in
[Table pagination and incremental composition](table-pagination.md). Its
evidence retains the open compatibility gates. It preserves the version-3 contract above.

## Independent numeric examples fixed before implementation

All coordinates below use the existing project-authored T19 fonts. At size 10,
A advances 6 points, B 6.5, space 2.5 and omega 7; ascent is 7 for the primary
font and 7.2 for the fallback. Leading is 12 unless stated otherwise.

1. FIXED: table W=200, columns `[40pt,25%,AUTO]` resolves `[40,50,110]`.
   On a 240x160 page with margins 20, one row with padding 3, borders 1
   and text A/B/omega has height 20. Cell rectangles are
   `[20,120,60,140]`, `[60,120,110,140]`, `[110,120,220,140]`.
   Text starts at `(24,129)`, `(64,129)`, `(114,128.8)`.
2. AUTO: W=100, two AUTO columns, cells `AA` and `BBBB`, padding 2,
   borders 1. Intrinsic minima are `[12,12.5]`, preferred `[18,32]`;
   equal surplus gives `[43,57]`. At area top-left `(20,140)`, row height
   is 18 and baselines are `(23,130)` and `(66,130)`.
3. Spans: W=120, three 40-point fixed columns; row 1 declares
   `A(rowspan=2)` and `BB(colspan=2)`; row 2 declares B and omega.
   With padding 2 and borders 1 all ordinary rows are 18 points tall.
   Rectangles are `[20,104,60,140]`, `[60,122,140,140]`,
   `[60,104,100,122]`, `[100,104,140,122]`; baselines are
   `(23,130)`, `(63,130)`, `(63,112)`, `(103,111.8)`.
   Reading order is A, BB, B, omega: `ABBBΩ`.
   The row boundary at y=122 has no border across x=21..59 inside A.

Independent semantic assertions compare every scalar and cell-border
rectangle with a 0.0001-point tolerance. The separate reference PDF uses
hand-positioned T19 text and Canvas rectangles and never calls table
layout. Each 144-DPI opaque-white sRGB visual profile requires PDFium /
ImageMagick zero-fuzz AE 0, with secondary-renderer AE <= 2500.

## Finite limits and failure behavior

Version 3 requires the existing complete Composition limits, an explicit
`maximumLayoutAttempts` and complete `tableLimits(TableLimits)`. Relayout is
not supported and its limit must remain zero. Table limits declare aggregate
maximumTables, maximumRows, maximumCells and maximumGridSlots (rows times
columns summed across tables), maximumColumns per table and maximumLayoutWork
for the entire command including discarded table candidates. Cell paragraphs
and their inlines, scalars, lines, fonts and graphics share the flow's existing
aggregate limits. No resource is implicitly unbounded.

The layout-work counter models deterministic operations rather than CPU time.
It charges one unit for each attempted table candidate; each visited column
when initializing, summing, locating flexible columns, distributing deficits,
interpolating or constructing x coordinates; each visited row when initializing,
summing, distributing span height or constructing y coordinates; and each cell
visit in the minimum, preferred, content, span-height and output passes.
Every candidate cell line charges one plus the number of atoms remaining from
its starting offset, including LF and graphics. Repositioning each retained
line charges one. These charges include failed area candidates. Declaration
scans, intrinsic atom measurement and stable sorting are separately bounded by
the admitted row, column, cell, inline and font limits and modeled owned memory.
For one FIXED AUTO column and one cell containing A with 2-point padding and
1-point borders, W=40, size=10 and leading=12, the table uses exactly 14 layout
work units and two flow layout attempts. The corresponding AUTO table uses
21 work units and two attempts. Limits of 13/20 or one attempt respectively
fail; the exact bounds succeed.

`TABLE_INVALID_SPAN` identifies malformed grids. `TABLE_CONSTRAINT_UNSATISFIED`
identifies an otherwise valid table that cannot be placed under the declared
geometry and any preceding paragraph keep. When the version-3 search is
exhausted, geometry and line-limit failures are attributed to the furthest
flow item reached in declaration order. A line limit encountered for that
item takes precedence over geometry; failures in discarded candidates for
earlier items do not mask a later item's failure. Paragraph geometry retains
the existing paragraph failure codes. `COMPOSITION_INVALID` identifies other invalid
declarations and version mismatches. `COMPOSITION_LIMIT_EXCEEDED` identifies
declaration, layout-work, line or generated-content limits. Resource policy,
font, Canvas, security, lifecycle and publication contracts continue to apply.
All diagnostics remain fixed and omit text, font identifiers and paths.
Propagated failures leave targets unchanged with NOT_ATTEMPTED receipts.

## Example and version migration

```java
Table table = Table.version1(Table.Layout.FIXED, TableWidth.percentage(100),
        TableWidth.points(80), TableWidth.auto())
        .row(TableRow.version1(
                TableCell.version1().paragraph(Paragraph.version1(16).text("A", 12).build()).build(),
                TableCell.version1().paragraph(Paragraph.version1(16).text("B", 12).build()).build()))
        .build();
ParagraphFlow flow = ParagraphFlow.version3(FontSelection.referenceFontSet())
        .page(LayoutPage.version1(612, 792, PageMargins.of(72, 72, 72, 72)))
        .table(table).build();
// Use explicitly supplied ReferenceFontSet resources in the WorkflowEnvironment.
// Provide complete CompositionLimits.version3() including TableLimits.
workflow.execute(request, session -> {
    session.execute(ComposeParagraphs.version3(flow, limits));
    return session.query(PageCount.INSTANCE);
});
```

No public signature is removed. Existing version 1/2 factories and behaviors
remain available. Opt into version 3 for table items and supply table bounds;
version 1/2 execution rejects table items instead of ignoring them. Exhaustive
switches over `ParagraphFlow.Item.Kind` should handle its added `TABLE` value.
Exhaustive switches over `DocumentFailureCode` should also handle the added
`TABLE_INVALID_SPAN` and `TABLE_CONSTRAINT_UNSATISFIED` values when recompiling
against this 0.x release.
Version 3 does not establish a buffered flow; attempts to relayout it receive
`COMPOSITION_RELAYOUT_UNSAFE`. Its success seals any earlier buffered paragraph
flow. The same Worker Command representation transports only closed project
values and opaque font-source identifiers; existing permission preflight and
resource staging remain in force.
