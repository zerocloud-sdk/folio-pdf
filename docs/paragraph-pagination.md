# Advanced paragraph pagination

T25 extends the [T24 paragraph contract](paragraph-composition.md) through
`ComposeParagraphs.version2`, `ParagraphFlow.version2`, `Paragraph.version2`
and `CompositionLimits.version2`. The capability identifier is
`composition.layout.paragraph-pagination`. It remains experimental; this
profile does not certify the complete Foundation Composition Profile.

## Declarations and defaults

Version 1 retains its existing behavior and rejects version 2 options. A
version 2 flow admits both paragraph versions. Pages, inline graphics, fonts,
leading, whitespace, alignment and maximum width retain the T24 contract.
No existing public signature is removed; migration is opt-in by selecting the
version 2 factories and supplying the two additional finite limits.

| Paragraph option | Default | Contract |
| --- | --- | --- |
| `indentation(left, right, firstLine)` | `0, 0, 0` | Insets in points inside the width cap. Left and right are nonnegative; firstLine is signed. Left plus firstLine must be nonnegative. The first-line offset applies once per paragraph, including after a skipped area, and never repeats on continuation lines. A hanging first line is supported. |
| `tabInterval(points)` | `36` | Positive repeating left tab grid relative to the paragraph's left inset. |
| `tabStop(stop)` | none | Strictly increasing positive positions relative to the same origin. Explicit stops take precedence; beyond the last explicit stop the repeating grid resumes. |
| `keepWithNext(boolean)` | `false` | The last line and the following paragraph's first fragment share an area. Chained keeps apply transitively. A final keep is vacuous; an intervening explicit area break is unsatisfiable. |
| `keepTogether(boolean)` | `false` | The entire paragraph must fit in one area. It may skip areas but cannot relax this constraint. |
| `widows(int)` | `1` | Minimum lines in every continuation fragment, including the final fragment. |
| `orphans(int)` | `1` | Minimum lines in every fragment that continues in another area. |
| `overflow(WRAP / REJECT / VISIBLE)` | `WRAP` | Horizontal treatment of overlong words, graphics and tab fields, described below. |

Widow and orphan minima must be positive. They do not force a short paragraph
to grow when it fits in one area. Rules apply at column/area boundaries as well
as page boundaries. Empty LF lines count. Inline graphics stay atomic and
their heights participate in every fit decision.

Tabs in version 2 text are positioning controls and emit no glyph. The field
after a tab extends to the next tab, forced line separator or paragraph end; it is atomic.
`TabStop.Alignment` supports LEFT, CENTER, RIGHT and ANCHOR. ANCHOR aligns the
first declared Unicode scalar in the field; an absent anchor aligns the field's
right edge. The anchor offset uses the field's visual bidi order. Fields retain
their physical declaration order, with bidi reordering applied within each
field. Stops never move the pen backwards: the first eligible stop whose
aligned field start is ahead of the pen is selected. Leading/consecutive tabs
are meaningful. A tabbed line remains left aligned, including under JUSTIFIED,
so alignment cannot displace absolute tab positions. Tab leaders are excluded.

WRAP uses Unicode line opportunities and complete-grapheme fallback under
the [T28 Unicode contract](unicode-composition.md).
REJECT preserves complete units between Unicode line opportunities. VISIBLE
uses those same boundaries and admits an overlong unit at the beginning
of a line, preserving its full text and ink outside the horizontal area.
Graphics and tab fields are atomic under all three policies. Ordinary vertical
overflow always advances through the finite areas; no mode repeats pages,
clips text, truncates content or permits a line taller than its area.

## Constraint conflicts and finite search

The planner tries the current area first and the largest legal fragment first,
then shorter legal fragments, then the next area. It recomputes line breaks
for each area's width. It may revisit earlier fragment choices to satisfy
later keeps and widow/orphan minima. No rule is silently relaxed. Indentation
leaving no positive width makes that area unusable.

Version 2 limits add mandatory `maximumLayoutAttempts` and `maximumRelayouts`.
Each candidate line computation and each search transition consumes a layout
attempt, including discarded candidates. The bound applies separately to each
composition/relayout command. All attempted relayout commands in one buffered
flow share the relayout count, including unsuccessful layout attempts.
The existing maximumLines bounds the final plan, not discarded candidates.
Owned memory and the Workflow Resource Policy also bound retained declarations,
search frames, fonts and detached pages. Search uses no caller recursion.

| Stable failure | Meaning |
| --- | --- |
| `COMPOSITION_INVALID` | Invalid version, numeric value, tab stop or declaration. |
| `COMPOSITION_AREA_EXHAUSTED` | The finite areas cannot hold the flow's content. |
| `COMPOSITION_CONSTRAINT_UNSATISFIED` | The finite areas cannot satisfy a keep or widow/orphan constraint. |
| `COMPOSITION_LIMIT_EXCEEDED` | A declaration, line, layout attempt, relayout count or generated byte bound is exceeded. |
| `COMPOSITION_RELAYOUT_UNSAFE` | There is no current buffered flow, or it has been flushed or sealed by a later mutation. |

When a constrained flow exhausts its search it reports the constraint failure;
when the attempt budget runs out first it reports the limit failure, without
claiming that the constraints are impossible. Diagnostics omit text and source
details. All T19, Canvas, permission and workflow failures retain their contracts.

## Buffering, flushing and relayout

`ComposeParagraphs.version2(flow, limits)` defaults to `FlushMode.BUFFERED`.
The overload taking `FlushMode.IMMEDIATE` seals the result on successful
composition. Both modes fully plan and paint detached pages before attaching
them; neither mode publishes early. Buffering retains the current flow's semantic declaration, limits and prepared
font selection under owned-memory accounting, allowing
`RelayoutParagraphs.version1(pages...)` to replace its appended pages using new
page declarations. The original semantic content and source selection are
reused without rereading font sources, even if a path changes; a failed relayout preserves the last successful page content.

Queries observe the current layout and do not seal it. Successful relayout
can increase or decrease the number of appended pages without altering earlier
pages. `FlushParagraphs.version1()` releases the current retained declaration
and seals the pages. Flush is idempotent. Every other successful mutation seals
the preceding flow; a new composition can establish a new buffered flow.
This prevents relayout from losing later page edits or invalidating destinations.
Version 1 composition is implicitly immediate.

Publication occurs only after the callback returns. The Session then expires;
relayout on that Session throws the existing `IllegalStateException` lifecycle
error in both execution profiles. Opening the published PDF creates a new
Session with no retained semantic flow and relayout returns
`COMPOSITION_RELAYOUT_UNSAFE`. Buffering is not persisted in PDF metadata.
Unsigned REWRITE and INCREMENTAL remain supported; signature and password
permissions are checked before font acquisition, including in Worker preflight.

The two execution profiles carry the same closed versioned declarations.
No backend objects, callbacks, arbitrary classes or font paths enter the Worker
protocol. No table, Unicode layout, shaping, new dependency or Migration Facade
mapping is added by T25.
