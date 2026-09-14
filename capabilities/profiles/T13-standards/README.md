# Original T13 standards controls under qualification

`scripts/generate-t13-standards.py` authors isolated original controls and legal
positives, and reuses 84 applicable page, Form, stream and annotation rules from
the prior catalogs. `assignments.json` and `required-rules.txt` currently bind
334 qualified declaration rules. This is an incomplete union for the text slice.

| Assignment | Rules | Observed scope |
| --- | ---: | --- |
| pdfcpu | 60 | Reused core document rules |
| arlington-core | 24 | Reused page, Form and annotation rules |
| arlington-fonts | 8 | Owning-font names and W2 triples |
| arlington-text | 81 | UserUnit, Type3 declarations/glyph metrics, simple-font declarations and required nulls |
| arlington-cid | 50 | Type0/CIDFont declarations and metric array shapes |
| arlington-descriptors | 94 | Descriptor fields and font-stream declarations |
| arlington-cmaps | 17 | Encoding/ToUnicode stream dictionary declarations |

Controls pin exact PDF bytes, expected findings and source clauses. Mutations
either preserve xref offsets or rebuild the known original serialization with
correct stream lengths and xref entries. The separate `program-controls.json`
catalog contains 33 decoded CMap defects; these are not part of the 334-rule
Arlington/pdfcpu union. Legal operator variants, direct Font dictionaries and
work-boundary fixtures qualify the new independent program observer separately.

The first actual tool observations are retained in
`capabilities/evidence/T75-delivery/font-corpus-r1/t75-font-standards-current-tool-probes`.
Pinned pdfcpu 0.15.0 accepts both positives but also accepts all six illegal
controls, so it cannot cover these rules. The existing T12 Arlington executable
misreads legal array-form W2 and cannot evaluate FontName's owning-font equality;
its noisy positives and negative findings cannot qualify this T13 coverage.
The inherited positive also exposes the generic/typed CMap stream context issue.
The separate T13 Arlington supplement now qualifies all eight font controls,
including a shared Type1/MMType1 descriptor and optional Type3 names. Both
named and unnamed Type3 descriptors are legal under PDF 2.0 Table 120.
The main marked Source supplies matching optional names; its unnamed form
remains a legal Arlington positive and a retained pdfcpu false-positive case.
The two core checker assignments pass on all five original cases under the
PDF 2.0 publication policy; the nested case has a separately authored PDF 2.0
qualification positive. Actual reports and strict Red/Green history are in
`capabilities/evidence/T75-delivery/standards-r1`. These observations do not
qualify the still-missing content and structure predicates or certify any final
candidate environment. Later declaration and program observations are retained
under `standards-r2` through `standards-r5` in the same delivery area.

`scripts/t13-program-standards.py` uses pinned qpdf 12.4.0 decoded objects and
its own literal PostScript grammar. It imports neither Folio nor authoring code.
It observes nested direct Font dictionaries and indirect Type/Subtype values,
Encoding versus ToUnicode operators, code domains, UTF-16BE, dictionary/program
agreement and declared inheritance. Owning-font checks compare CIDFont and
Encoding Registry/Ordering, allow Supplement differences, require simple-font
single-byte sources, and reject conflicting mixed-length prefixes or ToUnicode
sources outside the owning Encoding. The review fixtures preserve independent
false-PASS and false-FAIL discoveries separately from the authoring corpus.
Optional identifier checks establish literal value types, not registry uniqueness.

Qualification bounds are 16 MiB per input/tool output/decoded program, 100,000
tokens, lexical nesting 32, 100,000 direct graph values, direct/indirect graph
depth 128, 128 CMap-role nodes, 256 codespace declarations and 4,096 expanded
mapping declarations per CMap. The effective source set, including inherited
sources, also has a 4,096-entry bound checked before copying or inserting;
overriding an existing source does not enlarge that set. Mapping declarations
still charge even when overridden.
The Python process has a 5-second CPU soft limit, a 10-second hard limit and
512 MiB address space; each qpdf invocation has a 15-second wall deadline.
Out-of-bound or unevaluated programs remain INDETERMINATE. Predefined programs,
cross-final-byte mapping ranges and arbitrary PostScript execution are outside
this observer's current qualified grammar. Complete binary font programs,
content and structure remain pending. The 61 retained CLI observations under
`standards-r5/program-cmaps-r1` exercise all five Sources, all 33 negative
controls, five operator positives, eight direct/work boundary cases and ten
independent review fixtures. Incremental Standards and Spec findings are closed;
these reports do not constitute the final review or environment certification.

The separate `fonts` scope uses the existing SHA-pinned MIT fontTools 4.59.2
wheel to read CFF and TrueType binaries. `font-program-controls.json` contains
45 controls for headers, CFF kind, directory/INDEX layout, checksums, outlines,
program names, decoded length, descriptor bounds, units per em and CID font
dictionary selection. CFF
major version 1 permits compatible minor versions; its ROS declaration must
match the PDF stream Subtype. The Top DICT requires CharStrings; reserved or
unknown DICT operators remain unqualified. The raw glyph count is checked
before charset expansion and the charset must account for all indexed glyphs.
TrueType tables are read with checksum checking; duplicate table tags cannot
hide an earlier record. Limits are 128 embedded programs, 128 TrueType tables
and 4,096 glyphs per font. Optimized Python execution, unimplemented font data,
legacy TrueType signatures and other embedded formats remain INDETERMINATE.
Missing required data and malformed readable tables produce FAIL records.
Independent font review PDFs retain the original failures and legal
minor-version variants. The sfnt directory checks derived search fields, tag
order and syntax, alignment, byte ranges and zero inter-table padding. Both
table and whole-font checksums are required. CFF INDEX offsets are bounded;
only Type 2 CharStrings using hmoveto, hlineto and terminal endchar are currently
qualified, with actual outline drawing after operand/path validation. Simple
TrueType glyph bounds include all coordinate points, including off-curve points.
After that header check, curved glyphs remain INDETERMINATE: exact drawn bounds
currently qualify simple polygons only. Polygon scaling and TrueType advances
retain exact integer ratios to unitsPerEm, avoiding floating-point boundary failures.
PDF FontName must match the program name; conflicting TrueType platform names
remain INDETERMINATE. FontFile2 Length1 must match the decoded bytes.
Composite TrueType glyphs and other charstring grammars remain unqualified.
Raw incremental review probes and closure are retained under `standards-r7`.
Complete font metadata and owning-font relations remain outside this increment.

The continuing descriptor qualification compares independently drawn outline
bounds with FontBBox, resolving indirect coordinates and normalizing either
diagonal corner order. TrueType unitsPerEm must be in the standard 16–16,384
range before scaling to 1000-unit PDF glyph space. CID CFF requires FDArray
and FDSelect before glyph selection. CFF outline bounds currently qualify only
implicit default matrices: an explicit Top DICT or selected FD FontMatrix
returns INDETERMINATE. This avoids treating unqualified matrix composition or
floating-point boundary estimates as evidence. All original corpus CFF fonts
use the qualified implicit matrices. Both incremental review axes closed their
findings; the original and closure records are retained under `standards-r8`.

The `font-metrics` scope compares PDF simple
Widths and CID W/DW with independently read embedded glyph advances. Simple
selection currently covers nonsymbolic WinAnsi ASCII; TrueType requires one
Windows Unicode (3,1) cmap. Name-keyed CFF uses the pinned Adobe Glyph List
mapping. CID CFF uses its charset CIDs; CID TrueType currently requires Identity
CIDToGIDMap. Both CID W array and range groups are read, with 4,096 expanded
declarations and no overlapping CID assignments. Other selection schemes
remain INDETERMINATE. These metric checks supplement the declaration and
font-program scopes; they do not claim arbitrary font-format conformance.
The CFF width profile requires integer DICT defaultWidthX and nominalWidthX;
real DICT widths remain INDETERMINATE after float decoding. That boundary
does not affect the separate `fonts` scope. All required original corpus
fonts use integer DICT widths. `font-metric-controls.json` contains nine
isolated simple/CID W/DW mismatches, each bound to its original source clause.
Both review axes closed the exact-ratio and explicit numeric-boundary findings;
75 public program, authoring and Foundation-plan/collection tests pass with
zero skips. Raw originals, closure probes and historical observer bytes are
retained under `standards-r9`; this is development qualification only.

The separate `content` scope qualifies operand types, PDF lexical rules,
properly nested text/graphics/marked pairs, original-byte resource lookup,
selected-font state, marked property types/MCIDs and original Type3 rectangular
ink bounds. `content-controls.json` contains 55 isolated negative controls;
all five Sources and all controls have actual expected observations. Earlier
Form resource inheritance remains unqualified, while PDF 2.0 requires local
directly named resources. Detailed bounds, sources, review findings and
retained original observations are in `T75-delivery/standards-r10` and
`docs/t13-certification.md`. These predicates do not yet qualify the complete
logical-structure graph.

The Arlington publication qualification is PDF 2.0. Retained legacy PDF 1.7
required-field probes expose additional upstream conditional-predicate gaps;
this catalog does not claim those legacy cases are qualified.

All PDFs are original Apache-2.0 project data, using the original T13 fonts and
project serializer. No Folio-generated product, Folio parser or iText material
supplies a standards control or its normative classification.
