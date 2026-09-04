# Text and logical-structure extraction

T13 exposes bounded page text, marked content, and Tagged PDF logical
structure through the Native Interface. Call
`DocumentSession.query(ExtractTextAndStructure.version1(limits))` inside
`DocumentWorkflow.execute`. The query and every returned value use only Folio
PDF or JDK types. `TextStructureExtraction` is immutable, fully detached, and
remains usable after the Document Session ends.

## Ordering and text

Pages are returned in one-based page-tree order. Within a page, content
streams are processed in `/Contents` array order; operators, Form XObject
invocations, text-showing operands, and encoded source codes retain execution
order. Array members are parsed as one newline-separated content stream, so a
token may span adjacent members exactly as it does in the PDF content model.
Repeated queries and reopen produce the same order. Version 1 does not
sort by coordinates or infer reading order, spaces, line breaks, columns, or
paragraphs.

Each `TextItem` represents one encoded source code, not necessarily one
Unicode code point. `PageText.getText()` concatenates selected mappings
without fabricated separators. A marked-content `ActualText` value replaces
the enclosed items once at the matching end operator; those items remain
inspectable but have empty aggregate-text contributions. `Alt` is exposed on
marked content and structure elements and is never substituted into Page
Text.

`TextItem.getRenderingMode()` reports the effective PDF text rendering mode
for that source code as a project-owned `TextRenderingMode`. This includes all
eight fill, stroke, invisible, and clipping combinations and supports public
reopen verification of T17 positioned text; it does not describe color,
alpha, or an ink outline.

## Geometry

`TextGeometry` reports the effective text-rendering matrix at the start of an
item as `a`, `b`, `c`, `d`, `e`, `f`. It maps a text-space point `(x, y)` to
`(a*x + c*y + e, b*x + d*y + f)`. Form matrices, the current transformation
matrix, text matrix, font size, horizontal scaling, and text rise are already
represented. `advanceX` and `advanceY` are the transformed font glyph
displacement. They are not an ink bounding box or the complete distance to the
next item: character spacing, word spacing, and later `TJ` adjustments appear
in subsequent items' start matrices instead.

The values are in the page's unrotated default user space. Page display
rotation is returned separately by `PageText.getRotation()` and is not
applied to the matrix. The crop box uses the same coordinates, and
`getUserUnit()` gives the physical scale in multiples of 1/72 inch.
Before geometry is exposed, the page preflight requires an effective
four-finite-number `MediaBox`, validates any `CropBox`, requires `Rotate` to
be an integer multiple of 90, validates any `Resources` as a dictionary, and
accepts a direct `UserUnit` only from greater than zero through 75,000.

## Unicode mapping evidence

`CharacterMapping` retains the exact encoded source bytes and independent
mapping observations:

- `EXPLICIT`: a valid `/ToUnicode` CMap supplies a value and any independently
  derived standard observation agrees;
- `INFERRED`: an explicit simple-font `Differences` entry for that code, or
  otherwise an explicitly declared recognized encoding name or recognized
  `BaseEncoding`, maps through the public Adobe Glyph List and `/ToUnicode`
  has no value for that code;
- `CONTRADICTORY`: explicit and standard observations disagree, so both remain
  inspectable and no Unicode value is selected; and
- `MISSING`: neither supported source supplies a defensible value.

Contradictory and missing items contribute no text and produce ordered
`ExtractionDiagnostic` values with a stable code, page number, page-local item
index, and defensive copy of the source bytes. Backend text coercions,
font-program guesses, OCR, and visual guesses never become confident results.
An explicit `Differences` entry remains code-specific evidence when the base
is absent or unknown. Such a base supplies no fallback for any code without an
override and never authorizes embedded, substituted, or system-font inference.
Composite-font mapping requires
`/ToUnicode` in version 1; broader character-collection inference is
unsupported.

## Marked content and logical structure

Each page owns its `MarkedContentSequence` values in begin-operator order.
They expose the tag, optional `MCID`, optional parent sequence, directly
declared `Lang`, `Alt`, and `ActualText`, plus the page-local indices of every
enclosed text item. A `TextItem` also lists all enclosing sequence identifiers
from outermost to innermost.

Logical roots and each element's `LogicalStructureItem` children retain the
document's `/K` order. Version 1 supports nested structure elements, direct
integer MCIDs, and page-content MCR dictionaries. A
`MarkedContentReference` exposes its one-based page, MCID, and the matching
page sequence when exactly one exists. Marked-content sequences inside Form
XObjects, MCRs carrying `Stm` or `StmOwn`, OBJR children, and PDF 2.0 structure
namespaces are outside version 1 and fail safely instead of being
approximated. Each structure element is visited once, and its required `P`
backlink must identify the parent implied by `/K`; repeated/shared elements
and inconsistent parents fail safely. A structure element's `Type` may be
absent as allowed by PDF 1.7 or may be `StructElem`; an MCR dictionary must
explicitly declare `Type` as `MCR`.

An element exposes its declared role and role-resolution result. Unqualified
PDF 1.7 standard structure types resolve as `STANDARD`; a finite transitive `/RoleMap`
chain ending at a supported standard type resolves as `ROLE_MAP`; an unmapped
custom role remains inspectable as `UNRESOLVED` with no claimed standard role.
Cyclic role maps fail safely. PDF 2.0-only names such as `Title` are
unresolved without a supported mapping; PDF 2.0 namespaces are outside
version 1.

`getDeclaredLanguage()` reports only the element's `/Lang` value.
`getEffectiveLanguage()` uses element, nearest ancestor, then catalog `/Lang`
precedence; `LanguageSource` reports `SELF`, `ANCESTOR`, `DOCUMENT`, or `NONE`.
Element `Alt` and `ActualText` remain distinct metadata and do not alter page
text.

## Limits and failures

Every version-1 `ExtractionLimits` field is mandatory and nonnegative. Content-
stream depth is additionally capped at
`ExtractionLimits.MAXIMUM_CONTENT_STREAM_DEPTH_VERSION_1` (`32`) because
PDFBox processes nested Forms through the JVM call stack. A zero allows an
empty corresponding dimension and rejects its first value. The limits have
these exact meanings:

- pages count all current pages before traversal;
- page-tree nodes count the root plus every `/Kids` entry. Folio PDF validates
  the tree iteratively under this bound, rejecting repeated or cyclic nodes,
  inconsistent parents or counts, negative counts, and malformed node types
  before PDFBox page traversal. Counts are range-checked as full PDF integers
  before conversion to the public integer model. It gives the backend detached leaf views with
  validated inherited `Resources`, `MediaBox`, `CropBox`, and `Rotate` values,
  plus a validated direct `UserUnit`, so backend access does not recurse
  through the live page tree or fabricate malformed geometry defaults;
- content streams count each page stream and each executed Form occurrence;
- content-stream depth is one for a page stream and increments for nested
  Forms; exact caller bounds through 32 succeed, the first excess fails, and a
  declaration above the version-1 ceiling is rejected;
- decoded bytes aggregate decoded page streams, each executed Form occurrence,
  each distinct font dictionary's `/ToUnicode` stream, and each distinct
  embedded font-program or CID-to-GID stream reached by extraction;
- text items count encoded source codes;
- Unicode code points count mapping observations and extracted metadata as
  they are accepted; equal explicit and inferred observations for one item are
  charged once, and duplicated aggregate-text views are not charged again;
- `ToUnicode` mappings count each `bfchar` entry and every character code that
  an expanded `bfrange` would materialize, across distinct font dictionaries.
  Scalar ranges charge every entry PDFBox's embedded-font CMap path would
  materialize, including carries across an `FF` target byte, and parsing stops
  at `endcmap`. The independently parsed public mapping uses strict inline-CMap
  semantics, so this construction-safety charge can conservatively exceed the
  mappings retained in the detached result;
- font-data entries count every item inspected once in each distinct
  simple-font `/Differences` array; every raw item in reached `/Widths`, `/W`,
  `/W2`, and `/DW2` arrays, including nested arrays; and every CID width that a
  compact `/W` range would materialize;
- marked-content count and depth cover every begun sequence and its nesting;
- structure-element count covers each returned element;
- structure-item count covers every root or element `/K` entry;
- structure depth starts at one for a root element; logical-structure descent
  uses an explicit stack so exact high depth boundaries do not depend on the
  JVM call stack; and
- role mappings count every `/RoleMap` entry.

An exact boundary succeeds. The first excess fails with
`EXTRACTION_LIMIT_EXCEEDED` and the capability
`document.text-structure.extract`. Malformed streams, mappings, font metrics,
named resources, marked-content nesting, page references, or logical-
structure values and cyclic or repeated Form/structure graphs terminate with
`QUERY_FAILED`. Raw page `/Contents` arrays are count-checked and type-checked
before PDFBox content traversal. Their decoded members are syntax-checked as
the same newline-separated combined stream the backend consumes, and a
private terminal probe rejects an array, dictionary, string, hexadecimal
string, or inline image that would otherwise make PDFBox mistake malformed
truncation for clean end-of-stream, as well as operands left without a
following operator. Arity and operand types for every
version-1 supported text, state, resource, and marked-content operator are
validated before backend processing. Text-object `BT`/`ET` and graphics-state
`q`/`Q` pairs must balance independently on the page and in each Form before
a result is published, and text operators may occur only inside a text object.
Missing or malformed named Font, XObject,
ExtGState, and marked-content Property resources likewise fail before the
backend operator handles them. Both failures use fixed safe diagnostics and
expose no backend exception or document data.

For `gs`, version 1 copies only the optional two-item `Font` setting into a
detached one-key graphics-state view. Other ExtGState entries do not affect
text extraction and are ignored without traversing their arrays or graphs.

Version 1 rejects `ToUnicode` `usecmap`, nested CMap arrays/dictionaries,
premature or missing `bfchar`/`bfrange` terminators, reversed ranges, malformed
range arrays, name destinations, empty or odd-byte destinations, and
destinations that are not well-formed UTF-16BE with paired surrogates. A
Type 0 font accepts only `/Identity-H` or `/Identity-V` and exactly one
`CIDFontType0` or `CIDFontType2` dictionary; missing, arbitrary predefined,
and embedded `/Encoding` CMaps are outside version 1. If an embedded Type 0
font program's header contradicts its declared descendant subtype, the query
fails before PDFBox can repair the live document graph. Reached font
dictionaries must explicitly declare `Type` as `Font` and a supported
simple, composite, or CID-descendant subtype. Present simple `FirstChar`,
`LastChar`, and `Widths` values and CID `DW`, `DW2`, `W`, and `W2` selectors,
ranges, and metrics are type- and range-validated before construction.
Present `FontFile*` entries must be streams; `CIDToGIDMap` accepts only a
stream or the standard `Identity` name. Type 3 fonts are
outside version 1, so their separate `/CharProcs` glyph-program streams cannot
bypass the declared decoded-byte boundary. Decimal CMap counts use the same
integer conversion as the backend, and four-byte endpoints use its signed
representation, before their ranges are charged. Compact CID width ranges are
charged before PDFBox constructs a font; ranges whose terminal integer would
overflow PDFBox's loop fail safely.

Before a Form is constructed, version 1 requires explicit `Type XObject` and
`Subtype Form`, a four-finite-number `BBox`, an absent or six-finite-number
`Matrix`, an absent or dictionary `Resources`, and an absent or integer-1
`FormType`; the full PDF integer is checked before any narrowing. Structure
and marked-content integer MCIDs likewise must fit the nonnegative public
integer range. Missing `Matrix` has the standard identity meaning. Every
marked-content begin or end operator encountered inside a Form fails safely,
including an `EMC` that would otherwise close a page-level sequence. The
text-item limit is checked before source-code mapping evidence is
published.

The query observes all supported Commands that precede it in the same
Session. The Query itself is read-only: a workflow with no declared Target has
no publication receipts or output write, and extraction does not modify its
Source or the live object graph. In particular, indirect Form `Type` and
`Subtype` entries remain indirect after successful or failed backend Form
processing and through an explicitly requested rewrite. A workflow that
explicitly declares a Target still follows the normal publication policy after
its callback returns.

## Example

```java
ExtractionLimits limits = ExtractionLimits.builder()
        .maximumPages(100)
        .maximumPageTreeNodes(1000)
        .maximumContentStreams(1000)
        .maximumContentStreamDepth(16)
        .maximumDecodedBytes(64L * 1024L * 1024L)
        .maximumTextItems(1_000_000)
        .maximumUnicodeCodePoints(2_000_000)
        .maximumToUnicodeMappings(1_000_000)
        .maximumFontDataEntries(100_000)
        .maximumMarkedContentSequences(100_000)
        .maximumMarkedContentDepth(64)
        .maximumStructureElements(100_000)
        .maximumStructureItems(250_000)
        .maximumStructureDepth(128)
        .maximumRoleMappings(10_000)
        .build();

WorkflowOutcome<TextStructureExtraction> outcome = workflow.execute(
        request,
        session -> session.query(
                ExtractTextAndStructure.version1(limits)));
```

These are example application bounds, not universal safe defaults. They
compose with T20's finite transaction-wide trusted in-process policy. Hard
process, memory, CPU, network, and termination isolation remains T21 scope.
T13 adds no Migration
Facade mapping, OCR, image/resource extraction, layout reconstruction,
incremental publication, signature handling, or encryption behavior.
