# T13 Foundation extraction certification contract

This is the execution contract for [#75](https://github.com/zerocloud-sdk/folio-pdf/issues/75),
governed by [#1](https://github.com/zerocloud-sdk/folio-pdf/issues/1),
[ADR-0035](adr/0035-represent-uncertain-unicode-mappings.md) and
[ADR-0040](adr/0040-certify-only-observed-foundation-environments.md).
It freezes the required behavior and matching Facade subset before implementation.
It is not a certification record. The capability remains experimental until the
independent chains and all eight required Native environment/mode tuples pass.
The fixed implementation/review baseline is
`5b1603c435f11c40368f75b7b9a2777c9c5e9761`.

## Matching Migration Facade member set

The six members below expose the complete extraction value model. Every bounded
overload accepts the same immutable `ExtractionLimits` as the Native Query;
selection of one page or the structure roots happens only after the complete
document query succeeds. No new parsing engine, reading-order strategy or
callback execution surface is introduced. The Facade's actual execution mode
remains `IN_PROCESS`.

| Type beneath `net.zerocloud.pdf.itext7` | Member | Return | Mapping |
| --- | --- | --- | --- |
| `kernel.pdf.canvas.parser.PdfTextExtractor` | `getTextFromPage(PdfPage)` | `String` | Reference member; deterministic Page Text with documented finite defaults |
| `kernel.pdf.canvas.parser.PdfTextExtractor` | `getTextFromPage(PdfPage, ExtractionLimits)` | `String` | Folio extension for explicit bounds |
| `kernel.pdf.PdfPage` | `getPageText(ExtractionLimits)` | `PageText` | Folio extension exposing every glyph, matrix, source byte, mapping observation and marked-content occurrence |
| `kernel.pdf.PdfDocument` | `getTextAndStructure(ExtractionLimits)` | `TextStructureExtraction` | Folio extension exposing the complete detached Native result, including diagnostics and all logical child kinds |
| `kernel.pdf.PdfDocument` | `getStructTreeRoot()` | `List<LogicalStructureElement>` | Adapted reference member returning ordered detached roots under finite defaults |
| `kernel.pdf.PdfDocument` | `getStructTreeRoot(ExtractionLimits)` | `List<LogicalStructureElement>` | Folio extension for explicit bounds |

The public [7.2.6 PdfTextExtractor API](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/canvas/parser/PdfTextExtractor.html)
and [PdfDocument API](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/PdfDocument.html)
supply member shapes only. The adapted structure result follows the existing
Foundation Facade convention of returning project-owned detached values;
callers explicitly migrate the return type. `getStructTreeRoot` does not create
a missing tree. Absence returns an empty immutable list.

Every Native observation is available through `getTextAndStructure`, with the
same mapping uncertainty, geometry conventions, replacement-text precedence,
roles, languages, references and limits. This is the matching read-only subset;
mutable tag-tree construction, arbitrary content-operator registration and
coordinate-sorting extraction strategies have no counterpart in this Query.
They are not represented by Stable stubs. All six members must appear in the
Foundation obligation and Facade Surface Manifest and in both compiled editions.
Operational failures retain the Native `DocumentFailure` code and fixed safe
diagnostic through `PdfException`; expired document/page handles reject access.
Detached returned values remain usable after close. Source and publication
ownership follow the existing Facade contract.

Both no-limit members use one fixed default profile: 10,000 pages; 100,000
page-tree nodes; 100,000 content-stream occurrences; content depth 32; 64 MiB
decoded bytes; 1,000,000 text items; 2,000,000 Unicode code points; 1,000,000
ToUnicode mappings; 1,000,000 font-data entries; 100,000 marked-content sequences;
marked-content depth 128; 100,000 structure elements; 250,000 structure items;
structure depth 128; and 10,000 role/namespace entries. These finite convenience
bounds compose with the Workflow Resource Policy; callers needing other bounds
use the explicit overloads.

## Required successful extraction cases

The correctness sources are the authorized
[ISO 32000-1:2008 text](https://opensource.adobe.com/dc-acrobat-sdk-docs/pdfstandards/PDF32000_2008.pdf)
and the PDF Association's public
[PDF 2.0 document-interchange corrections](https://pdf-issues.pdfa.org/32000-2-2020/clause14.html).
Existing project-authored T13 fixtures and independent original expectations are
reused. New fixtures contain only original synthetic PDF objects/programs and
explicitly licensed acceptance fonts where a font program is necessary.

| Source | Successful Foundation cases | Required contrasting controls |
| --- | --- | --- |
| ISO 32000-1 §§7.8.2, 8.3, 8.10, 9.4 | Page order, split Contents tokens, repeated/nested Form invocations; text matrix, font advance, rise/scaling/spacing, `TJ`, vertical writing, display rotation and UserUnit | Truncated or orphan tokens, invalid operand kinds, unmatched page/Form state, cyclic Form execution |
| §§9.6, 9.7, 9.10; ADR-0035 | Type 1/MMType1/TrueType, horizontal and vertical Type 0 with CIDFontType0/2, Type 3 with declared metrics; simple named encodings/Differences, Identity, bounded embedded and standard predefined CMaps, bounded ToUnicode inheritance | Malformed widths/font kinds, contradictory embedded headers, cyclic or excessive CMap inheritance/materialization, invalid UTF-16BE; absent and contradictory mappings remain uncertain |
| §§14.6, 14.7.4, 14.9 | Nested page/Form marked content, stream-scoped MCIDs, outer ActualText precedence, Alt retained separately, ordered MCRs for page and Form streams (including Stm/StmOwn), ordered OBJRs | Cross-stream EMC, duplicate stream MCID definitions, missing/foreign referenced pages or objects, inconsistent StmOwn, cycles/shared elements and invalid parent backlinks |
| PDF 2.0 §§14.7.2–14.7.5, 14.8.6; PDF 1.7 §14.7.3 | Namespace identity, unqualified and namespaced role resolution, transitive cross-namespace mappings, unqualified standard-name remapping, bounded circular unqualified associations, unresolved custom vocabulary, direct/ancestor/catalog language | Invalid namespace dictionaries or mapping values, prohibited remapping within an explicitly identified standard namespace; an unknown or circular unqualified custom role stays unresolved |

Form marked content retains execution occurrences. Repeated Forms without
internally structure-linked marked content remain supported. A Form containing
structure-linked MCIDs may be invoked at most once. Repeated invocations
incorporated into Logical Structure require separately associated enclosing
MCIDs; the reused Form must contain no internally structure-linked sequences.
Violations fail the complete Query with `QUERY_FAILED` (ISO 32000-1 §14.7.4.2).
Whole-object OBJRs may cover repeated same-page rendering, as allowed separately
by §14.7.4.3; a rendering on another page requires its own OBJR.
A page MCID and a Form MCID with the same integer are distinct. A referenced
but unexecuted valid Form can have no extracted occurrence. Object references
retain detached document identity and the declared page relationship; they do
not execute actions, render an annotation or infer replacement text.

Type 3 glyph programs are bounded font data. They supply metrics under their
declared FontMatrix and do not become additional Page Text items. Font programs
and predefined character-to-CID encodings never justify Unicode certainty by
themselves. No font or CMap fetch may use a URI or ambient system font as a
mapping oracle. Invalid input remains all-or-nothing with the existing stable
`QUERY_FAILED` and `EXTRACTION_LIMIT_EXCEEDED` contracts.

Namespace observations extend `LogicalStructureElement` with
`getDeclaredNamespaceName()`, `getNamespaceReference()` and
`getResolvedNamespaceName()`. The declared URI and opaque dictionary identity
are optional together and are not inherited from parent elements. The resolved
namespace name accompanies a resolved standard role, including the default
`http://iso.org/pdf/ssn` for unqualified resolution. An unresolved role has no
resolved namespace. Namespace URIs are identifiers, never fetch instructions.
For explicit namespaces, extraction stops at the first supported standard role;
compatibility mappings to another vocabulary remain available in the original
dictionary. Direct mappings within an explicitly identified standard namespace
are rejected. An unknown cross-namespace cycle terminates as `UNRESOLVED`; that
observation is not a PDF conformance claim. The primary public sources do not
establish every final ISO 32000-2 rule for transitive returns between namespaces,
so WTPDF/PDF-UA restrictions are not silently imported into this Query.

These successful cases resolve the two recorded limitation blockers. Malformed
graphs and resource exhaustion remain explicit safe-failure boundaries. Broader
layout reconstruction, OCR, inferred whitespace, tagging authoring, accessibility
conformance, pattern/appearance rendering and downstream capability tickets are
outside this read-only query. An unsupported success case in the table cannot
be reclassified as a retained limitation merely because rejection is safe.

Implementing these cases requires an explicit revision of ADR-0035's existing
version-1 Type 3, Encoding CMap, inherited CMap and Form/namespace restrictions.
Its Unicode uncertainty rule and pre-construction safeguards remain binding.
The revision must land with successful and failure observations; this prospective
contract does not silently override the current ADR or claim that broader
behavior is already supported. Accounting is local to one Query and independent
of backend or process caches:

- For each distinct selected font dictionary, each distinct Encoding/ToUnicode
  CMap node in its inheritance graph consumes one font-data entry. A shared node
  is charged once within that font's graph, and again for a different font's
  construction. Predefined nodes use their fixed resource name as identity;
  embedded nodes use stream-object identity. Cycles reject before construction.
- Each distinct selected Type0 font also charges its descendant's validated
  metric-construction cost, including when separate selected fonts share the
  descendant object. Repeated selection of the same font reuses that cost.
- Every accepted Encoding or ToUnicode codespace-range declaration consumes one
  font-data entry before construction, under the same per-node, per-selected-font
  reuse rules. The declaration is charged once, without expanding its code space.
- Every Encoding-CMap `cidchar` entry and every source code denoted by a
  `cidrange` consumes one font-data entry before construction, even when the
  backend stores that range compactly. The same source-cardinality rule applies
  to notdefchar and notdefrange, whose ranges select a constant fallback CID.
  This is conservative source-cardinality
  accounting, not a claim of one backend allocation per code. Every ToUnicode
  `bfchar` entry and every source code expanded from a `bfrange` consumes one
  ToUnicode-mapping entry before allocation, retaining the existing conservative
  backend-materialization rules. Duplicate or subsequently overridden entries
  still charge. Reusing the selected font for more text does not recharge its
  construction inputs. Decoded CMap bytes charge once per node per distinct font.
  There is no Identity exemption: the pinned Identity-H requires 65,538 entries
  (one node, one codespace declaration, 65,536 mapped codes); Identity-V requires
  65,539 because its distinct node inherits Identity-H. Other font-data costs
  remain additional. Independent pre-implementation Spec review identified the
  codespace declaration gap and confirmed these counts from the pinned resources.

- Each Type 3 CharProcs dictionary entry consumes one font-data entry per
  distinct selected font. Aliases therefore count separately as entries; their
  decoded stream bytes charge once per distinct stream object across the Query.
- Each distinct namespace dictionary and each role-map dictionary entry consumes
  one role-mapping entry across the Query. Reusing the same namespace or role-map
  object does not recharge it; distinct objects with equal text still charge.
- Existing `/K` entries retain their structure-item charges. Each newly traversed
  Stm/StmOwn/Obj relationship and each ParentTree node or value entry additionally
  consumes one structure item; repeated visits to a relationship do not bypass
  the bound. Repeated Form execution continues to charge content-stream
  occurrences and decoded Form bytes on every invocation.

The standard predefined Encoding names are the fixed 61 names in ISO 32000-1
Table 118. Their pinned programs and inherited resources remain in FontBox;
document names never become arbitrary paths, URLs or system-resource lookups.
CIDFont and Encoding compatibility compares Registry and Ordering as required
by §9.7.3; Supplement need not be identical. Stream headers and their own CMap
program metadata must agree exactly. The internal constant backend Encoding is
an implementation bridge whose storage is Workflow-owned, not an input CMap
node; source programs retain their full per-font charges.

Geometry uses the normally mapped CID's declared widths and vertical vectors.
If a normal mapping is absent, notdef mapping then CID 0 supplies that selector.
No glyph-existence or substitute-font inference changes Unicode confidence.
The profile does not claim identical missing-glyph rendering or advances across
renderers; independent visual cases require explicitly qualified glyph data.

These graph traversals must be iterative and detect cycles before delegation;
zero-cost chains cannot bypass the caller's finite bounds. Cyclic object graphs
remain malformed. A CMap's local mappings override its inherited mappings for
the same exact source bytes, including all character/range combinations. This
follows ISO 32000-1 §9.7.5, Adobe Technical Note 5014 §5.4 and the public
[PDF 2.0 text corrections](https://pdf-issues.pdfa.org/32000-2-2020/clause09.html#9.10.3).
An inheritance override is not contradictory Unicode evidence: that confidence
compares independent declared encoding and ToUnicode observations. Undefined
local destinations mask ancestor values instead of restoring overridden
certainty. Inheriting CMaps cannot redeclare their adopted codespace, including
identical or disjoint declarations (Technical Note 5014 §§5.4 and 7.3).
Both Encoding and ToUnicode require each mapping source to lie within the
adopted codespace. Declarations precede character mappings, and codespaces
cannot overlap or make source lengths ambiguous (§§5.3 and 7.3). Encoding
counts are integers; source ranges use unsigned one- through four-byte values.
Inheritance
depth obeys the explicit Query and Workflow bounds. Technical Note 5014's
five-level statement does not establish an unrestricted PDF conformance claim
for deeper graphs; extraction of an acyclic bounded graph is recorded as an
extraction observation only.
Circular unqualified RoleMap associations are explicitly
allowed by ISO 32000-1 §14.7.3 Note 2: resolution stops at a recognized mapped
role or a repeated role, reporting `UNRESOLVED` when no standard role is reached.
Unqualified names honor their RoleMap entry even when the original spelling is
standard (since PDF 1.5, §14.7.3 Note 3). PDF 2.0 applies that undefined-namespace
mapping before entering the default namespace. This corrects the original
prospective blanket rejection, as identified by independent Spec review.

## Observers and independent evidence

The first original visual corpus case, `nested-split-type3`, uses a 120 by
100 point page with zero rotation. An original Type3 glyph paints the rectangle
`[0 0 400 600]` in a 0.001 FontMatrix and declares width 500. The first red
glyph uses 20-point text at `(10,20)`; its ink rectangle is `[10 20 8 12]` and
its advance is `(10,0)`. Its hexadecimal source token is split across two
Contents streams. A nested blue glyph uses 10-point text inside a leaf Form
with Matrix `[2 0 0 1 5 10]`, inside a middle Form translated by `(20,30)`.
Its page-space matrix is `[20 0 0 10 25 40]`, ink rectangle `[25 40 8 6]`,
and advance `(10,0)`. Both source bytes are `41`, inferred through the declared
glyph name A. The expected Page Text is `AA`. The original RGB pixel grid at
144 DPI is authored from those two literal rectangles, before any renderer or
Folio observation. The Type3 program is original Apache-2.0 fixture data; no
system font, external font asset or glyph-substitution expectation is involved.
Visual controls change only the glyph painting width from 400 to 300, or the
leaf Form's translation x from 5 to 6. The original expected pixels and zero
comparison thresholds remain unchanged. Each defect must produce a visual
failure, with its actual PDF, independent rendering and difference image retained;
the unchanged original must pass in the same control invocation.

`marked-structure` is a second original Type3 case. Three glyphs have matrices
`[10 0 0 10 10 10]`, `[10 0 0 10 20 10]` and `[10 0 0 10 20 30]`, each advancing
`(5,0)`. Their black rectangles are `[10 10 4 6]`, `[20 10 4 6]` and `[20 30 4 6]`.
The page's outer ActualText replaces its first two glyphs with `Outer`; the Form
replacement contributes `Form`. All source glyph contributions remain empty,
and their aggregate Page Text is `OuterForm`. The ordered page and Form MCIDs
both have value zero but distinct stream scopes. An ordered OBJR links a hidden
zero-area Text annotation. A custom namespace resolves transitively to PDF2
Document, which contains PDF2 P and then PDF2 Span. P inherits French from the
root; Span declares Japanese and an empty ActualText. P and Span share their
opaque namespace identity. The root's Alt and ActualText are retained separately
from Page Text replacement. The Tagged Type3 descriptor supplies the required
font metadata and the optional FontName `/F1`, matching the Type3 Name `/F1`.
The original unnamed form is retained as a legal standards-qualification
positive under PDF 2.0 Table 120. The valid parent relationship follows
[Annex L](https://pdf-issues.pdfa.org/32000-2-2020/clauseAnnexL.html);
the optional K is absent on the empty leaf as required by
[Table 355](https://pdf-issues.pdfa.org/32000-2-2020/clause14.html#14.7.2).

The original font cases use an empty `.notdef` and one rectangular glyph in
TrueType, name-keyed CFF and CID-keyed CFF. The simple TrueType and CIDFontType2
use separate programs: the latter omits `cmap`, preserving the same glyph IDs,
outlines and metrics. This satisfies the directly checked ISO 32000-1 §9.9 rule;
the final PDF 2.0 wording is not independently claimed. Both glyphs have width 500; font
programs and descriptors consistently declare fixed pitch. Ink bounds are
`[0 0 400 600]`, with units per em 1000 and StemV 400. The same CFF instance is
embedded as Type1 and MMType1; ISO 32000-1 §9.6.2.3 permits the latter's ordinary
Type1 instance snapshot. Two Type0 fonts use each CID descendant kind in H/V
writing. Explicit `W2 [1 [-1000 250 800]]` shifts vertical origins from `(40,80)`
and `(90,80)` to `(35,64)` and `(85,64)` at size 20, with advance `(0,-20)`.
Horizontal advances are `(10,0)`. The seven glyphs contribute `AAAZAZA`; the
first three mappings are inferred from WinAnsi, and four CID mappings are
explicit ToUnicode values. The independent original outlines never supply
Unicode inference for CID glyphs.

The per-case required chain assignments are frozen in `corpus.json`:

| Case | Required chains | Principal coverage |
| --- | --- | --- |
| `nested-split-type3` | syntax, standards, semantic, visual | Contents boundaries, nested execution, declared Type3 glyph metrics and matrices |
| `marked-structure` | syntax, standards, semantic, visual | replacements, language, ordered page/Form MCR and OBJR, namespace identities |
| `embedded-font-kinds` | syntax, standards, semantic, visual | Type1/MMType1/TrueType/CID0/CID2 glyphs, direct Encoding/ToUnicode, H/V geometry |
| `embedded-font-inheritance` | syntax, standards, semantic | embedded Encoding inheritance and ToUnicode local override, exact source bytes and public observations |
| `uncertain-geometry` | syntax, standards, semantic | all mapping confidences and diagnostics, explicit spaces, rise/scaling/spacing/TJ, unrotated geometry and separate rotation/UserUnit |

All four chains remain mandatory in every final environment run. The
inherited-CMap case's current independent renderers omit vertical glyphs or fail;
the retained visual INDETERMINATE record is not promoted. The direct case supplies
the font visual predicate. Its original raw PDF reference was authored before a
Folio direct-font product was observed; pinned PDFium renders its reference
pixels with fixed font smoothing. Primary AE/fuzz and exact RGB differences
remain zero. Only this new font profile allows a secondary disagreement bound
of 1120 pixels: seven 16 by 24 pixel ink rectangles, each with a one-pixel inner
and outer boundary shell of `18*26 - 14*22 = 160` pixels. This bound is geometric,
not fitted to an observation. Every pixel outside those original boundary shells
must also agree exactly; a total count alone could conceal a missing glyph.
Secondary-only missing-glyph, interior-hole and unrelated-mark controls must
fail, while an edge-only difference passes. Type3 profiles retain their zero secondary bound.
Actual one-point horizontal and vertical glyph displacement controls must fail
against unchanged reference pixels, alongside the original Type3 controls.

`uncertain-geometry` places the first three Type3 codes under CTM
`[2 0 0 3 5 7]`, font size 10, horizontal scaling 50%, rise 2, character spacing
2 and word spacing 4. The text matrix starts at `(10,20)` and `[(A) 100 ( A)] TJ`
produces matrices `[10 0 0 30 25 73]`, `[10 0 0 30 31 73]` and
`[10 0 0 30 39.5 73]`, with advances `(5,0)`, `(2.5,0)`, `(5,0)`.
The space is a real source code, not invented whitespace. After `Q`, three more
fonts provide explicit `A`, contradictory explicit `Z` versus inferred `A`, and
missing mapping for code `42` with an original non-AGL glyph name. Page Text is
`A AA`; items 5 and 6 retain uncertainty and their full ordered diagnostics.
The CropBox `[10 20 130 120]`, Rotate 90 and UserUnit 2 are observed separately.
Every mapping Optional retains its presence flag; the properties authority
escapes a leading space so Java cannot silently discard this source character.

The independent semantic chain combines a pinned qpdf decoded-graph observer
with complete detached public values matched to the literal corpus. The graph
comparison preserves all reachable object relationships, indirect aliasing,
dictionary fields, array/content order and decoded bytes. It normalizes object
numbering, numeric spelling, xref/trailer storage, decoded stream storage fields
and the explicitly selected output version. It does not claim equivalence of
arbitrary rewritten content programs. The original Sources remain frozen inputs;
neither the observer nor qpdf imports Folio or the fixture generator. Actual
reserialization positives and changed-PDF negatives qualify the graph check.
The Java recorder separately observes the selected Native mode through the
public Workflow, and binds the qualified script, Python executable and exact
PDF identity. The whole qpdf pin, the pin actually sourced by its wrapper,
binary and wrapper bytes and all eleven pinned runtime-library paths are
checked before and after observation. Empty/relative cache values use the
same defaults and working directory as the actual tool; wrapper links preserve
the invocation path. A substitute tool cannot qualify by reporting a genuine
version. Raw syntax output is retained separately from the final reports,
which include subsequent input/tool identity failures. Failed or unavailable
observations do not become certification by relabeling their producer.

Public Native observers use `DocumentWorkflow.execute` under both modes. They
prove all fifteen exact limit boundaries and first excesses, including decoded
font/CMap/Form bytes, source preservation, caller stream/channel ownership,
detached values, preceding Commands, aborted publication with no valid-looking
prefix, repeated-query determinism and explicit rewrite/reopen receipts.
Facade observers exercise all six actual public members and compare those same
outcomes in IN_PROCESS; no Facade result is labelled HARDENED_WORKER.

Independent syntax, standards, semantic and visual chains retain distinct tools,
reports and qualified negative controls. Required standards predicates have
source clauses and original legal/illegal cases. Semantic observers read an
independently parsed object graph and literal authored expectations without
importing the product or its fixture generator. Visual expectations are original
fixed rasters/programs, checked through independent pinned renderers and the
pinned comparator, with actual PDF defects and a comparator control. Missing
tools, uncovered rules, undetected controls or identity mismatches cannot pass.

The independent content-program scope qualifies the original corpus's PDF
lexical objects and operators, separately from PostScript CMap lexing. Its
predicates cover operator arity/types; BT/ET, q/Q and marked-sequence balance
within each page Contents sequence or Form; active Font/XObject/Properties
resource lookup by original name bytes; inherited/restored font selection;
and MCID uniqueness plus the string types of Lang, ActualText and Alt.
Graphics, text and marked pairs must nest properly for PDF 2.0 publication
qualification; that predicate alone does not establish legacy PDF 1.7
invalidity. In PDF 2.0, a Form's
directly named resources must be local; its selected font still comes from
the graphics state. Earlier-version Forms that need resource inheritance
remain INDETERMINATE. The effective version uses the greater of the header
and Catalog Version. Unsupported operators and cyclic Form execution remain
INDETERMINATE. The bounds are 128 content-stream occurrences, 1000 page-tree
nodes and 128 page-tree levels; each combined Contents or individual program
is bounded to 100000 lexical tokens and 16 MiB decoded bytes.
These original predicates follow ISO 32000-1 §§7.2–7.3, 7.8.3, 8.4.2, 8.10,
9.3–9.4 and 14.6–14.7, plus the approved PDF 2.0 corrections to
[§§7.2.2, 7.8.2–7.8.3](https://pdf-issues.pdfa.org/32000-2-2020/clause07.html)
and [Table 93](https://pdf-issues.pdfa.org/32000-2-2020/clause08.html).
PDF name lexing permits braces; qpdf JSON Unicode and binary name forms
are converted back to original bytes using its documented
[JSON v2 representation](https://qpdf.readthedocs.io/en/12.4/json.html).
These predicates do not yet qualify the complete structure graph.

The developing `structure-hierarchy` scope checks root/child object kinds, unique
indirect element identities, required matching parent backlinks, role name
types and the text-string types of language, replacement and alternate text.
Dictionary positions reject stream objects. Parent backlinks must be indirect;
PDF 2.0 also requires an indirect StructTreeRoot. Legacy empty direct roots
remain legal under the earlier Catalog declaration. Present element K arrays
must be nonempty, while root K arrays may be empty.
It is bounded to 10000 traversed children and 128 levels; direct structure
element identities remain outside this acceptance subset. Optional K null
uses the dictionary omission rule in §§7.3.7 and 7.3.9; actual null array
children fail under Tables 354–355. The interpretation follows the approved
[array-null correction](https://github.com/pdf-association/pdf-issues/issues/308)
and retains the ambiguity in broader physical conformance in the independent
review under `T75-delivery/standards-r11`. The `structure-parent-tree` scope
also validates the number-tree topology, disjoint ordered integer keys,
descendant Limits, structure-element parent values, unused null array slots
and optional NextKey. Its bounds are 128 levels, 1000 nodes and 10000 entries;
empty branches remain unqualified. Optional NextKey retains its integer type
requirement even when no ParentTree is present. These rules follow §7.9.7 Table 37 and
§14.7.4.4 Tables 322/326, with the PDF 2.0 Table 354/359 corrections.
The `structure-namespaces` scope checks namespace dictionary types, required
text-string names, listed indirect element namespace identifiers and the
name or name/namespace-array destinations of RoleMap and RoleMapNS. It rejects
direct remapping within the same standard namespace, and bounds traversal
to 1000 namespace dictionaries and 10000 total mapping entries, including a
root RoleMap without any Namespaces. Namespace declarations in the root array
may be direct dictionaries; element NS and role destination references must
be indirect. These predicates follow PDF 2.0 Tables 354–355 and namespace
declarations in §14.7.4; they do not assert complete Tagged PDF role resolution.
The developing `structure-content` scope qualifies typed MCR/OBJR fields,
document-page membership, matching StructParent(s) keys and exact parent
slots for every K content item, and the existence of each referenced MCID.
It does not qualify reverse completeness of unused ParentTree entries or
extra non-null slots that no K item claims. Annotation references must
belong to the declared page's Annots array and agree with an optional P
backlink. Annotation StmOwn references must identify the actual AP normal,
rollover or down stream, including a named appearance state. Other stream
owner kinds remain unqualified. The qualifier applies §14.7.4.1 leaf rules to
MCID ancestry and direct or transitive Do relationships. Whole Form references
must cover every observed page-content rendering page; one same-page OBJR may
cover repeated renderings. Form classification preserves stream kind and
permits the optional Type entry to be absent or null (Table 95).

Referenced Form definitions are checked even when they contribute no executed
page content. Their operand/resource/MCID grammar uses the same independent
PDF program reader, with the same total 128-stream and decoded-program bounds.
An unexecuted Form that needs unknown inherited font state remains
INDETERMINATE. Normal appearance roots are discovered through page Annots/AP
even when the root has no K reference or StmOwn declaration. Page and appearance entry points are traversed with
counts saturated at two; an internally structured Form cannot repeat across
those entry points. Definition discovery order does not manufacture an extra
entry point. Distinct alternative appearances of one annotation remain
INDETERMINATE for invocation counting; N/R/D or state alternatives are not
added together as proof of simultaneous rendering. A valid Form definition
without an observed page-content or annotation appearance association is
also INDETERMINATE for this relationship scope. A whole annotation item
overlapping a structural item in its appearance remains an explicit
INDETERMINATE leaf boundary, without equating AP ownership to a Do edge.
These predicates use ISO 32000-1 §14.7.4 Tables 324–326 and
§12.5.5 Table 168. Resource-table reachability alone is not a necessary page
association predicate: appearances can be referenced through AP. These new
content predicates are undergoing independent review; they do not qualify
arbitrary annotation behavior or complete Tagged PDF conformance.

For Type3 glyph bodies, the content scope observes every CharProcs program
within a total 4096-glyph bound. Its qualified grammar is d0/d1 followed by
rectangle/fill operations, with at most one rectangle in each fill. It checks
complete operands/paths and actual rectangular ink against d1 and FontBBox
declarations using exact integer ratios. All-zero FontBBox means no size
assumption, as required by Table 112. General glyph graphics and multiple
contours requiring winding analysis remain INDETERMINATE; all required
original glyphs use single rectangles or an empty d0 space. Header widths and
font declarations remain separately qualified by Arlington. The body rules
follow §§8.5.2–8.5.3 and 9.6.5, including Tables 112–113.

The text recorder must bind the final staged candidate, contract, immutable OS
image, observed JDK vendor/build, native helper/engine, exact execution settings,
tool identities and every product/control to each Ubuntu 24.04/Linux x86-64
JDK 8/11/17/21 × IN_PROCESS/HARDENED_WORKER tuple. Final candidate changes require
new text evidence and refreshed transactions, values, pages, metadata and
annotations evidence. Historical observations remain intact. Overall Foundation
readiness may remain NOT READY for unrelated unfinished obligations.

Required gates include focused behavior/Facade/recorder/tool tests (external
tests run with `-Pindependent-certification`), inventory validation and generation
checks, `./mvnw -B -ntp verify`, the full JDK matrix, independent Standards and
Spec reviews against the fixed baseline, and `git diff --check`. The user's
subsequent delivery instruction authorizes a real-name DCO commit, GitHub push
and closure of #75 only after the complete task and all these gates pass.
The original handoff did not authorize those final delivery actions.
