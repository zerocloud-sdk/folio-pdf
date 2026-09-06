# Table pagination and incremental composition

T27 versioned contract for issue #28. These rules and independent examples
were fixed before implementation. The [delivery record](../capabilities/evidence/T27-table-pagination.md)
links verification and independent acceptance; the capability remains experimental.

## Version boundary

`Table.version1` and version-3 flow/command/limits retain the T26 single-area,
immediate-release contract. `Table.version2` opts into pagination in
`ParagraphFlow.version4`, `ComposeParagraphs.version4` and
`CompositionLimits.version4`. Version 4 also admits version-1 tables and
version-1/2 ordinary paragraphs. All bounds remain finite and explicit.
Buffered and immediate composition retain transactional publication.

## Pagination rules

Areas and pages are finite, explicit and visited in declaration order. Column
widths use the T26 FIXED/AUTO rules at the current area's width; AUTO uses the
whole admitted table's intrinsic constraints. A fragment may skip an area that
cannot satisfy its geometry. Greedy candidates precede shorter candidates in
the bounded search. No extra page template is generated implicitly.

Rows fill each area in order. A row that cannot fit may split into cell-content
prefixes at whole line boundaries. Different cells may consume different
numbers of lines; an inline graphic is atomic. Continuation cells retain their
column spans and remaining row spans. Every emitted cell fragment has its own
inside borders and padding, including at an area boundary. Thus splitting may
increase total occupied height. A row minimum is a total minimum across its
fragments, not a minimum repeated on every area. A fragment must make content
or row-height progress. A cell's content is never duplicated or discarded to
make a fragment fit.

Headers and footers are separate complete grids with the same declared columns.
Spans cannot cross body/header/footer boundaries. Each nonempty body fragment
paints header, body, footer in that order. Headers and footers remain whole.
`skipFirstHeader` and `skipLastFooter` default to false; when enabled they omit
only the first header or final footer respectively. A footer immediately follows
the body fragment; it is not pinned to the area's bottom. No empty header-only
or footer-only fragment is emitted. Repeated lines count against output limits.

Table keep-together is hard: the complete table, applicable header and footer
must share an area. Keep-with-next binds the final fragment to the next flow
item's first fragment. A preceding paragraph's keep-with-next binds to the
first table fragment. Disabling row splitting keeps each body row whole.
An unsatisfiable keep fails explicitly without relaxing the rule.

WRAP, REJECT and VISIBLE have the T25 horizontal meanings inside cells. VISIBLE
preserves overlong content and ink; it does not clip. No mode admits a vertical
line/graphic larger than its usable fragment. Exhausted finite areas or
impossible table geometry report `TABLE_CONSTRAINT_UNSATISFIED`; malformed spans
report `TABLE_INVALID_SPAN`. Invalid declarations and finite-budget failures
retain `COMPOSITION_INVALID` and `COMPOSITION_LIMIT_EXCEEDED` respectively.

## Retention and large tables

Buffered version-4 flows support `RelayoutParagraphs` with replacement pages,
using the original prepared font selection. Replacement is atomic: failed
layout leaves the last successful pages intact and consumes a relayout attempt.
`FlushParagraphs`, a successful later mutation, publication and Session expiry
seal the retained layout under the existing T25 lifecycle rules.

Incremental composition accepts a fixed table declaration, explicit fonts,
finite pages and cumulative budgets, then bounded batches of rows. Flush writes
releasable complete row groups into document-owned content and drops their
semantic declarations before all rows have arrived. Unfinished row spans and
an undecided final fragment remain charged to finite retained-state bounds.
Completion declares the final row and resolves final-footer omission. Flush
never publishes a target. Unsafe relayout after the first flush is rejected.
Continued append, incomplete spans, retries, relayout and Worker transport must
not reset or bypass cumulative or retained-state limits.

The fixed reference is the public [iText 7.2.6 Table API](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/layout/element/Table.html):
large tables use fixed layout with a retained table width; AUTO is unsupported.
Folio PDF keeps those restrictions explicit in its own declarations. Its
incremental API cannot remove the table width. No iText source or output is an
implementation input or a correctness oracle.

The incremental command sequence is `BeginLargeTable.version1(flow, limits,
maximumRetainedRows)`, `AppendTableRows.version1(rows...)`, `FlushTable.version1()`
and `CompleteTable.version1()`. The version-4 flow contains exactly one
version-2 FIXED table, initially with no body rows, and its finite page
declarations. Its table width cannot be AUTO or absent. Header/footer grids and
fonts belong to the initial declaration. Body rows arrive only through append.
Limits apply cumulatively to the one logical table; flushing never resets them.
The additional positive retained-row bound and Workflow owned-memory limit
apply before retaining a new batch, including incomplete row-span groups.

Retained-memory admission includes the complete inline graphic declaration:
nested group programs, encoded/raw image bytes, explicit/soft masks, ICC and
color-space arrays, glyph bytes and fixed metadata. The scan uses byte-length
accessors without copying payloads or opening resources, observes the declared
group/workflow nesting bounds, and rejects arithmetic excess before admission.
Shared occurrences are conservatively charged separately. Initial repeated
sections remain reserved until completion; body reservations follow the retained
rows through append and flush. Temporary layout/transport reservations can
overlap this retained state. These are modeled owned-memory bounds, not JVM
heap or RSS measurements. Exceeding the Workflow budget produces
`MEMORY_LIMIT_EXCEEDED` with target publication `NOT_ATTEMPTED`.
Each atomic Composition graphic owns its declaration cache only while painting.
Nested resource sharing is preserved within that drawing; separate placements,
including repeated sections, can materialize separate PDF resources under the
cumulative object/storage policy. Emitted pages retain PDF resources without
retaining the original graphic declaration graph.

`InspectLargeTable.version1()` observes a detached `LargeTableState`, including
accepted body rows, retained body rows, fully flushed body rows and lifecycle
stage. A flush plans the currently complete row-span groups, writes confirmed
fragments and retains the final fragment for later append/completion. Released
rows and their cell-text prefixes become unreachable from table state. A row
group intersecting an undecided fragment remains retained as a whole.
Unfinished spans remain admitted but cannot bypass finite retention. Complete
requires a complete grid, emits the remaining content and releases table state.
The final-footer omission is decided only at complete; an earlier flushed
footer cannot be retracted. Complete without an unnecessary final flush can
therefore yield fewer pages, consistently with the fixed public reference.
An open large table rejects unrelated mutations and `RelayoutParagraphs`;
buffered `ComposeParagraphs.version4` provides the relayout lifecycle.
Workflow success requires complete, including workflows with no target.
An incomplete workflow fails with `COMPOSITION_INVALID` and leaves target
publication `NOT_ATTEMPTED`.

## Independent examples and thresholds

These expectations precede the T27 paginator. Reuse only the project-authored
T19 FolioPrimary and FolioFallback fonts with the existing pinned hashes.
At size 10, A advances 6, B 6.5, space 2.5 and omega 7 points; ascents are 7
and 7.2. Leading is 12, padding 2 and inside borders 1 unless stated otherwise.

1. Whole rows: five identical `AA | BBBB` rows in a 100-point table each have
   height 18. FIXED columns `[40, AUTO]` resolve `[40,60]`; AUTO columns
   `[AUTO,AUTO]` resolve `[43,57]`. Two 100x36 areas on page 1 and the first
   such area on page 2 contain rows `[1,2]`, `[3,4]`, `[5]`. On a 240x112 page
   with margins 20 and relative areas `[0,36,100,72]`, `[100,36,200,72]`, the
   first-column text begins at x=23 and 123; baselines are 82 and 64. Page 2
   restarts at `(23,82)`. Extraction is `AABBBBAABBBBAABBBBAABBBB` / `AABBBB`.
2. Split row: one `A\nB\nomega` cell in a 40-point table and two 40x30
   areas consumes two lines then one. Fragment heights are 30 and 18. At
   top `(20,80)` the baselines are 70 and 58; continuation starts at 69.8.
   Its border boxes are `[20,50,60,80]` and `[20,62,60,80]` on their pages.
3. Repetition: header A, three body rows B, footer omega, each height 18,
   in 40x72 areas gives two pages: `ABBomega` / `ABomega`. The first page
   has baselines 70,52,34,15.8 at top 80. With skip-first-header and
   skip-last-footer enabled, the same three body rows fit one 72-point area
   and extract `BBB`; its body height is 54.
4. Spans: columns `[40,40,40]`; row 1 declares `A\nB` spanning two rows,
   then `BB` spanning two columns; row 2 declares A and omega. Two 120x18
   areas emit `ABB` / `BAomega`. The spanning cell's fragments are both
   40x18; its single top-aligned line on each page starts at x=23, y=70.
   The other cells start at x=63 and 103. Omega's baseline is 69.8.
5. Keeps: preceding ordinary paragraph A (12 high), then a two-row B table
   (36 high), then ordinary omega (12 high), in two 40x48 areas. Table
   keep-with-next yields `AB` / `Bomega`; table keep-together alone yields
   `ABB` / `omega`. Both table keeps together produce `A` / `BBomega`.
   A single 40x36 area cannot hold the 48-point kept table-plus-next pair.
6. Relayout: three 18-point rows in two 40x36 areas give `[2,1]` rows;
   replacement with one 40x54 area gives `[3]`. Replacement with one 40x35
   area fails and preserves the last successful layout.
7. Overflow: the word AAAA (24 points) in an 18-point border box has 12
   usable points. WRAP gives `AA` / `AA` (height 30); REJECT fails; VISIBLE
   emits all four glyphs on one line (height 18), starting 3 points inward.
8. A fragment can end inside a row after complete earlier rows. With first row
   A (18 high), second row `B\nB\nomega` (42 high), and two 40x48 areas,
   page 1 contains A then two B lines (18+30 high); page 2 contains omega
   (18 high). At top `(20,80)`, baselines are 70,52,40 / 69.8. Border boxes
   are `[20,62,60,80]`, `[20,32,60,62]` / `[20,62,60,80]`.
9. Two single-A rows in one FIXED 40-point AUTO column, each occupying one
   40x18 area, use four flow attempts and 39 table-work units: 23 for the
   first candidate, 16 for the second. The inherited T26 work units count
   column/row/cell visits, candidate line suffixes and positioned lines;
   pagination additionally counts examined row-prefix and split-line candidates.
   Bounds 4/39 succeed; 3 attempts or 38 work units fail. Final lines, rows,
   cells, grid slots and inlines are each exactly 2.
10. Incremental release: FIXED width 40, header A, footer omega, final footer
    omitted, and 40x72 areas at top `(20,80)`. Begin with retained-row bound 3;
    append three B rows and flush. Page 1 is `ABBomega`, with baselines
    70,52,34,15.8; accepted/retained/flushed body rows are 3/1/2. The target
    still contains its pre-workflow sentinel. Append two B rows and complete:
    page 2 is `ABBB`, with baselines 70,52,34,16. Final counters are 5/0/5.
    Declaration totals including the header/footer are 7 rows/cells/inlines/
    scalars/grid slots; actual emitted lines are 8. A final flush before complete
    may commit a nonfinal footer and leave a third page; no earlier footer is
    silently removed after flush.
    Each preparation visits primary A and three primary B entries once and
    visits two candidates for omega: six fallback checks. Flush plus complete
    therefore consumes twelve checks, including the retained rows and the
    declared final footer. A bound of twelve succeeds; eleven fails before
    completing and preserves the already flushed page and the target sentinel.
11. Generated stream: 125 independently generated A/B/omega rows, retained bound
    three, two 40x36 areas per 120x76 page with margins 20. Each area contains
    two 18-point rows. The first 31 pages contain four rows each, then page 32
    contains B. Page text cycles `ABomegaA`, `BomegaAB`, `omegaABomega`.
    Text origins are x=23/63, y=46/28 (omega baseline 0.2 lower). Append three
    initially, then two per flush: every intermediate flush retains exactly one
    body row. A fourth retained row fails. The Workflow page budget is exactly
    32; already emitted pages remain the same document-owned pages across flush.
12. Incremental work: two A rows in two 18-point areas consume the same 39
    table-work units as example 9 during flush. Completing the retained row
    adds the 14-unit one-row candidate, totaling 53 units and three area
    attempts. Bounds 53/3 and two emitted lines succeed; 52 work units, two
    attempts or one emitted line fail while preserving the first flushed page.
    Operator-byte boundaries are measured through public reopened content
    streams, then tested at exact aggregate size and one byte below it.

The independent acceptance corpus is fixed at 19 letter pages, all 612x792,
with table fragment tops at (72,720), before its producer is implemented:

| Pages | Declaration and independently expected content |
| --- | --- |
| 1–2 | Example 1 FIXED: rows 1–4 in two areas, row 5 on page 2; widths 40/60. |
| 3–4 | Example 1 AUTO: the same breaks with widths 43/57. |
| 5–6 | Example 4 spans: `ABB` / `BAomega`; 18-point fragments. |
| 7–8 | Example 3 repeating sections: `ABBomega` / `ABomega`. |
| 9–10 | Example 10 incremental flush/complete: `ABBomega` / `ABBB`. |
| 11–12 | Example 2 split row: `AB` / `omega`; fragment heights 30/18. |
| 13–14 | Example 5 both table keeps: ordinary A / table BB then ordinary omega. |
| 15 | Example 6 successful buffered relayout: three B rows, height 54. |
| 16 | VISIBLE `AAAA` in an 18-point table: all four characters at x=75, y=710. |
| 17–18 | Ordinary A, then unsplittable `A LF B` row: A / AB; final cell height 30. |
| 19 | Example 3 with first header and final footer omitted: BBB, height 54. |

Whole-row text baselines are 710, 692, 674, 656 at x=75, with omega 0.2
point lower. The second column starts at x=115 (FIXED) or 118 (AUTO);
the second area is translated 100 points right. Spanning columns on pages 5–6
start at x=75/115/155. Split-row baselines on page 11 are 710/698.
Ordinary A baselines are 713 at x=72, and the ordinary omega after the kept
table on page 14 is at (72,676.8). Each table cell has the previously declared
inside one-point borders. These coordinates are hand-declared inputs to the
reference writer, which may not call table/paragraph composition or the
incremental table Commands.

Semantic tolerance is 0.0001 point for all scalar, fragment and border
coordinates. Acceptance pages use explicit 612x792 boxes with the examples
translated to hand-declared areas. Reference PDFs use positioned T19 text and
Canvas rectangles only. Raster profiles require pinned PDFium at 144 DPI,
opaque-white sRGB and zero-fuzz ImageMagick AE 0; the existing secondary
renderer bound is at most 2500 changed RGB pixels per page. No threshold may
be widened to accommodate actual pagination. Missing text, extra repetitions,
and a one-point geometry shift must invalidate acceptance.

Resource verification distinguishes cumulative counts from retained semantic
state. Generated rows must demonstrate release during append/flush under a
finite public observation or execution bound; modeled owned memory is never
reported as JVM heap or RSS. Every exact count/work/byte boundary needs a
first-excess negative control. Final acceptance additionally requires both
Workflow profiles, prior T24/T25/T26 regressions, full Maven/JDK gates, inventory
validation and independent Standards and Spec reviews of the complete diff.
