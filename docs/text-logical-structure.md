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
A sequence also identifies its content stream: zero for the owning page's
combined Contents, or a positive extraction-local identifier for a Form.
Repeated Form invocations retain distinct sequence occurrences and share that
stream identifier. They expose the tag, optional `MCID`, optional parent sequence, directly
declared `Lang`, `Alt`, and `ActualText`, plus the page-local indices of every
enclosed text item. A `TextItem` also lists all enclosing sequence identifiers
from outermost to innermost.

Logical roots and each element's `LogicalStructureItem` children retain the
document's `/K` order. Version 1 supports nested structure elements, direct
integer MCIDs, page/Form MCR dictionaries, and OBJRs for page-associated
annotations and XObjects. A `MarkedContentReference` exposes its page, stream,
MCID, optional extracted sequence identifier and optional `StmOwn` identity. Annotation
owners must contain the referenced Form in their appearance dictionary and
belong to the declared page. An omitted or null `StmOwn` remains absent in the
detached value; the page's annotation appearance dictionaries can establish
the Form's association without that optional entry. Unexecuted referenced appearances supply no Page
Text; their bounded MCID definitions, ancestry and nested `Do` relationships
still determine whether an MCR is valid. Whole-object unexecuted Forms use the
same definition traversal. A `LogicalObjectReference` retains the referenced object's opaque
Session-owned identity, page and subtype; it can be inspected in that Session
and compared after close without executing actions or rendering annotations.

When Form MCRs or XObject OBJRs are present, normal appearance roots are
registered in page and annotation order, including roots without their own
structure item. Their direct and transitive Form calls share the invocation
checks with page content.
Whole Form OBJRs can likewise obtain their page association from direct
appearances or actual nested calls. A Form declared only in an appearance's
resources must be reached by a `Do` path to establish that association.
Every page reached through normal appearances or their actual nested calls
requires its own whole-object OBJR; repeated calls on one page share that reference.
Discovery, decoded programs and relationships remain bounded by the existing
Query limits; this validation does not publish appearance content as Page Text.

Each structure element is visited once, and its required `P` backlink must
identify the parent implied by `/K`. ParentTree entries are traversed iteratively
under the structure-item bound, and structural content must carry a matching
`StructParent` or `StructParents` backlink. Repeated/shared elements, inconsistent
parents, duplicate MCIDs within a content-stream invocation, and cross-stream
marked-content endings fail safely. A Form with internal structure-linked MCIDs
cannot be invoked repeatedly (ISO 32000-1 §14.7.4.2); ordinary repeated Form
marked content and repeated whole-object OBJRs remain supported. Structural
content items remain leaves: nested MCR claims and overlaps between marked
content and whole-object items reject in either `/K` order. A whole structural
Form cannot invoke another whole structural XObject. A structure
element's `Type` may be absent or `StructElem`; MCR and OBJR dictionaries require
their respective explicit types. PDF 2.0 namespace identities and cross-namespace
role chains follow [the required Foundation profile](t13-certification.md).
The [Foundation evidence authority](../capabilities/foundation-evidence.yaml)
binds certification to the actual candidate, environment and execution mode.

An element exposes its declared role and role-resolution result. Unqualified
PDF 1.7 standard structure types resolve as `STANDARD`; from PDF 1.5 onward an
initial `/RoleMap` entry takes precedence even for a standard-looking name.
A transitive chain reaching a supported standard type resolves as `ROLE_MAP`.
An unmapped custom role, or a circular unqualified association with no recognized
role, remains `UNRESOLVED`. Circular RoleMap associations are permitted by
ISO 32000-1 §14.7.3; they do not imply a malformed structure-tree cycle.
In PDF 2.0, the complete undefined-namespace RoleMap chain is applied before
entering the default namespace. A chain with no terminal type remains unresolved.

`getDeclaredNamespaceName()` and `getNamespaceReference()` expose an explicit
namespace URI and dictionary identity; `getResolvedNamespaceName()` identifies
the resolved standard vocabulary. Namespace declarations are not inherited.
Root `Namespaces` entries may be direct dictionaries under the same shared
namespace/mapping budget. Element `NS` values and role destinations retain
their required indirect dictionary identities.
Unqualified resolved roles use the PDF 1.7 default namespace. An explicit
PDF 2.0 namespace also recognizes its own roles such as `Title` and `H7`.
Unknown custom vocabularies retain their declared names and uncertainty.
RoleMapNS name destinations identify the default namespace; two-item arrays
identify a target role and indirect namespace dictionary. Namespace URIs are
never fetched. Direct mappings within an explicitly identified standard namespace
fail safely. Unknown cross-namespace cycles terminate as unresolved extraction
observations without claiming PDF conformance.

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
  each distinct Form definition reached through unexecuted MCR/OBJR and `Do`
  relationships, each distinct font
  dictionary's reached Encoding and `/ToUnicode` inheritance programs, and each distinct
  embedded font-program or CID-to-GID stream reached by extraction;
- text items count encoded source codes;
- Unicode code points count mapping observations and extracted metadata as
  they are accepted; equal explicit and inferred observations for one item are
  charged once, and duplicated aggregate-text views are not charged again;
- `ToUnicode` mappings count each `bfchar` entry and every character code that
  an expanded `bfrange` would materialize, across distinct font dictionaries.
  Scalar ranges charge every entry PDFBox's embedded-font CMap path would
  materialize, including carries across an `FF` target byte, and parsing stops
  at `endcmap`. The public mapping uses strict inline-CMap semantics, so this
  conservative source-cardinality charge can exceed the retained mappings.
  Stream-dictionary `UseCMap` inheritance is iterative and bounded by node,
  mapping, decoded-byte and Workflow nesting limits. Exact source bytes,
  including their length, select the nearest local definition. Duplicate and
  overridden declarations still charge; reselecting the same font does not.
  Codes covered by an undefined local scalar-range destination do not recover
  an overridden ancestor mapping. Inheriting CMaps cannot redeclare codespace
  ranges. Textual `usecmap` must precede mapping operations, occur once and
  identify the same parent as the stream dictionary. Named parents are limited
  to the pinned bundled `Adobe-CNS1-UCS2`, `Adobe-GB1-UCS2`,
  `Adobe-Japan1-UCS2` and `Adobe-Korea1-UCS2` resources; their decoded bytes and
  declarations charge under the same bounds. Their published scalar ranges use
  full carrying increments, including across an `FF` destination byte; this
  differs from PDF inline ToUnicode's final-byte rule. Original Adobe cid2code
  data independently verifies those boundary mappings. No name is resolved through a
  filesystem path, URI or system font. For PDF 2.0, present stream-dictionary
  Type, CMapName, WMode and CIDSystemInfo entries must agree with the program:
  Type is CMap, WMode is an integer 0 or 1, and the character-collection
  registry, ordering and supplement match. This check applies to every embedded
  inheritance node. PDF 1.7 does not use those optional ToUnicode header entries.
  Character-collection metadata accepts a literal dictionary or the conventional
  three-entry dict/dup/begin form, with bounded literal or hexadecimal strings;
- font-data entries count every item inspected once in each distinct
  simple-font `/Differences` array; every raw item in reached `/Widths`, `/W`,
  `/W2`, and `/DW2` arrays, including nested arrays; and every CID width that a
  compact `/W` range would materialize. A descendant shared by distinct selected
  Type0 font dictionaries charges its validated metric cost for each font
  construction; reusing the same selected font does not recharge it.
  Each selected font's `ToUnicode` CMap
  node in its inheritance graph and each codespace-range declaration also consume one entry before
  construction; codespace declarations do not expand their code intervals.
  Encoding nodes and codespaces follow the same rule. Each declared cidchar
  or notdefchar and each source code covered by a cidrange or notdefrange
  consumes one entry, including duplicate and overridden declarations.
  The pinned Identity-H costs 65,538 entries and 7,889 decoded bytes; Identity-V
  adds its own node and 2,688 decoded bytes. Metadata arrays such as XUID
  do not consume mapping entries.
  Each Type 3 `/CharProcs` dictionary entry
  charges once per distinct selected font, including aliases. Its decoded
  program bytes charge once per distinct stream across the Query;
- marked-content count and depth cover every begun sequence and its nesting;
- structure-element count covers each returned element;
- structure-item count covers every root or element `/K` entry, ParentTree
  nodes, child relationships, key/value entries and MCID slots, plus Stm,
  StmOwn and Obj relationships and their page-membership/appearance traversal,
  structural-content ancestry and object-invocation overlap relationships, each
  distinct unexecuted stream/page definition root, each retained definition
  `Do` entry, each later visit to that relationship, and each root/relationship
  visited while aggregating definition invocation counts. Definition traversal
  validates MCID uniqueness for every visited Form, propagates repeated paths
  through ordinary Forms, and combines internally linked invocation counts with
  actual page execution. A redundant MCR validation start does not invent an
  invocation; an independently owned appearance remains a distinct entry point.
  Normal appearance roots retain separate annotation identity. When Form MCRs
  or XObject OBJRs are present, all direct `/N` streams and `/AS`-selected normal
  states are registered in page and annotation order, including roots without
  their own structure item. Their decoded programs and actual `Do` descendants
  participate in the bounded definition traversal. Owner indexing, scheduling, each resulting
  stream/page/owner root and whole-object appearance page coverage also charge
  the structure-item bound.
  Traversal is iterative and also respects Workflow nesting; it does not create
  or count executed content-stream occurrences;
- structure depth starts at one for a root element; logical-structure descent
  uses an explicit stack so exact high depth boundaries do not depend on the
  JVM call stack; and
- role mappings count each distinct namespace dictionary and each entry in a
  distinct `/RoleMap` or `/RoleMapNS` dictionary, once per Query; shared objects
  are charged once and distinct objects with equal text remain distinct.

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

For `gs`, version 1 applies only the optional two-item `Font` setting to the
extraction graphics state. Type 3 uses its bounded declared-metric font view.
Fonts with ToUnicode use a detached backend font dictionary with that entry
removed: declared Unicode is observed independently. Type0 fonts additionally
use the bounded Encoding plan for exact source-code decoding and CID selection,
then the descendant's declared W/W2/default metrics for geometry. A Query-owned
constant embedded Encoding connects already resolved CIDs to the backend;
the detached descendant omits ROS to prevent eager auxiliary collection or
UCS2 CMap loading. The original dictionary still controls validation and
mapping observations. The internal constant is not source CMap data; its stream
storage remains charged to the Workflow and closes with its cache on every exit.
Both `Tf` and `gs` reuse the Query-local font and
observe the original dictionary's encoding. Other supported fonts use a
detached one-key graphics-state view. Other
ExtGState entries are ignored without traversing their arrays or graphs.

Version 1 rejects unsupported named `ToUnicode` parents, inconsistent textual
parent declarations, codespace redeclarations, cyclic inheritance, nested CMap arrays/dictionaries,
missing CMap program boundaries or mapping counts, invalid CID-mapping operators,
premature or missing `bfchar`/`bfrange` terminators, reversed ranges, malformed
range arrays, name destinations, empty or odd-byte destinations, and
destinations that are not well-formed UTF-16BE with paired surrogates. A
Type 0 font accepts a bounded embedded Encoding CMap or one of the fixed 61
predefined names in ISO 32000-1 Table 118, including Identity-H/V, and exactly
one `CIDFontType0` or `CIDFontType2` dictionary. Encoding programs use one-
through four-byte, nonoverlapping and prefix-unambiguous codespaces and
exact-length CID selectors. Encoding and ToUnicode mappings must lie inside
the adopted codespace; codespace declarations must precede mappings. Inherited
programs cannot redeclare codespaces. Source endpoints use unsigned arithmetic.
Local CID mappings override inherited mappings, including explicit CID 0;
absent normal mappings use notdef mappings, then CID 0. Invalid or truncated
source codes fail safely. Stream headers must match program metadata;
embedded Encoding requires its Type, name and character-collection headers.
Font and Encoding Registry/Ordering must agree; Supplement may differ.
Public controls cover all 61 named resources' literal construction bounds,
horizontal and vertical declared geometry, both descendant kinds, mixed source
lengths, inherited CID/notdef overrides and first-excess failures. If an embedded Type 0
font program's header contradicts its declared descendant subtype, the query
fails before PDFBox can repair the live document graph. Reached font
dictionaries must explicitly declare `Type` as `Font` and a supported
simple, composite, or CID-descendant subtype. Present simple `FirstChar`,
`LastChar`, and `Widths` values and CID `DW`, `DW2`, `W`, and `W2` selectors,
ranges, and metrics are type- and range-validated before construction.
Present `FontFile*` entries must be streams; `CIDToGIDMap` accepts only a
stream or the standard `Identity` name. Type 3 requires finite `FontBBox` and
`FontMatrix`, declared `FirstChar`, `LastChar`, `Widths`, `Encoding`, and stream
entries in `CharProcs`. Each glyph program is decoded under the Query and
Workflow bounds and syntax-checked before construction. Its first operator must
be `d0` or `d1` with finite operands, zero vertical width, and a horizontal width
consistent with the corresponding declared width. The declared FontMatrix
transforms the advance; only its horizontal component is used in text space.
Codes outside FirstChar–LastChar have zero advance, without consulting glyph
program or FontDescriptor fallback widths. Glyph programs are font data and
produce no additional Page Text items or marked-content occurrences, even when
their painting instructions contain text. Both `Tf` and `gs` use the same
declared metric rules. Decimal ToUnicode counts use the same
integer conversion as the backend; Encoding counts must be integers. The public
CMap parser retains source length and unsigned one- through four-byte endpoints
before charging ranges. Compact CID width ranges are
charged before PDFBox constructs a font; ranges whose terminal integer would
overflow PDFBox's loop fail safely.

Before a Form is constructed, version 1 requires `Subtype Form` and permits
`Type` to be omitted, null or the name `XObject`. Executed Forms, whole-object
references and referenced appearance definitions use the same rule. They
require a four-finite-number `BBox`, an absent or six-finite-number
`Matrix`, an absent or dictionary `Resources`, and an absent or integer-1
`FormType`; the full PDF integer is checked before any narrowing. Structure
and marked-content integer MCIDs likewise must fit the nonnegative public
integer range. Missing `Matrix` has the standard identity meaning. Marked-content
operators balance independently inside each Form; an `EMC` cannot close a
page-level sequence. The
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
compose with T20's finite transaction-wide policy. The same query is available
through T21's opt-in process, heap/direct-memory, CPU, network, filesystem, and
hard-termination boundary within its documented Linux/JDK envelope.
T13 adds no OCR, image/resource extraction, layout reconstruction, incremental
publication, signature handling, or encryption behavior.

## Matching Migration Facade

Six read-only members are available in both Stable and Preview Facade
editions. The
[frozen certification contract](t13-certification.md) records the complete
member set and all fifteen finite convenience bounds.

`PdfTextExtractor.getTextFromPage(page)` returns aggregate Page Text. Its
`(page, limits)` overload accepts the same immutable Native `ExtractionLimits`.
`PdfPage.getPageText(limits)` exposes glyphs, matrices, exact source bytes,
mapping observations and marked content. Page handles retain page identity
through preceding page changes.

`PdfDocument.getTextAndStructure(limits)` returns the complete detached Native
result. `PdfDocument.getStructTreeRoot()` and `(limits)` return ordered immutable
`List<LogicalStructureElement>` values, explicitly adapting the reference
return type. An absent tree returns an empty list without creating a tree.
Both convenience methods use the same documented finite profile; explicit
overloads allow caller-selected bounds.

Every member waits for complete document extraction before selecting a page or
the roots. Malformed content or an exceeded bound on an unselected page rejects
the operation. Operational failures retain the Native code and safe diagnostic
through `PdfException`. Expired document/page handles reject access; detached
values remain usable after close. The existing Facade Session uses IN_PROCESS,
and caller streams and publication receipts keep their existing ownership rules.
