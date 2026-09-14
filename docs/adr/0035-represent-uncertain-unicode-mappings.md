# Represent uncertain Unicode mappings instead of guessing

Page Text extraction follows the ISO 32000 mapping sources while preserving each source character code and the evidence used. A missing mapping yields no invented Unicode value, and contradictory explicit and independently derivable standard mappings retain both observations without selecting either as certain; `ActualText` remains a separate replacement-text source rather than a per-character mapping. Backend coercions and font-program guesses therefore never become confident Native Interface results.

Inference is limited to an explicit simple-font `Differences` entry for the
specific code, or otherwise to an explicitly declared recognized `Encoding`
name or recognized `BaseEncoding`, mapped through the public Adobe Glyph List.
An absent or unknown base supplies no fallback, but does not erase a declared
code-specific `Differences` entry; neither case authorizes consulting an
embedded, substituted, or system font.
Before constructing a font, Folio PDF tokenizes its embedded `ToUnicode` using strict
PDF numbers, including signed and leading-fraction forms, then applies the
backend-compatible numeric-to-integer conversion and bounds every materialized
`bfchar` and expanded `bfrange` entry under a mandatory caller limit. This is
intentionally stricter than FontBox tokenization for leading fractions. It also
bounds only operators before FontBox's `endcmap` stop and charges scalar ranges
using the carrying increment used by PDFBox's non-strict embedded-font CMap
construction path. The public mapping observation uses strict inline-
CMap semantics, so the source-cardinality charge is deliberately conservative
when those modes differ. Every accepted destination must be a nonempty,
well-formed UTF-16BE sequence with paired surrogates. Declared `bfchar` and
`bfrange` counts require an exact matching terminator, and reversed source
ranges fail before FontBox can ignore them. It also
bounds and caches each distinct simple-font `Differences` array and accounts
decoded embedded font-program data before backend font construction. The same
mandatory font-data-entry budget bounds simple `/Widths`, CID `/W`, `/W2`, and
`/DW2` traversal plus every width that a compact `/W` range would materialize.
Reached font dictionaries must explicitly declare their font type and a
version-1-supported subtype; simple character-range scalars and CID default,
selector, range, and metric values are validated before backend construction.
Present embedded-font entries must be streams, while `CIDToGIDMap` accepts a
stream or the standard `Identity` name only.
Type 0 fonts contain exactly one `CIDFontType0` or `CIDFontType2` descendant,
preventing recursive composite-font acceptance. Their initial Identity-only
Encoding boundary is extended by the bounded #75 profile below. An embedded
Type 0 program whose detected header contradicts that descendant subtype is
rejected before PDFBox can repair the live COS graph. The initial version-1
Type 3 exclusion is replaced by the bounded declared-metric profile below.
Before backend traversal, an
iterative, cycle-safe page-tree preflight enforces a mandatory node-occurrence
bound and exact parent and count invariants, then supplies detached leaf views
with validated inherited page attributes; raw page `/Contents` arrays are
count- and type-checked. Page counts, encoding-difference character codes,
Form types, and public MCIDs are range-checked as full PDF integers before
narrowing. Decoded page-array members are then syntax-checked as the same
newline-separated combined stream PDFBox consumes; a terminal probe prevents
an unterminated composite token or trailing orphan operands from being
mistaken for clean EOF. Logical-
structure descent also uses an explicit stack
under its element, item, and depth limits rather than the JVM call stack, and
rejects repeated elements or inconsistent required parent backlinks.
Nested Form execution retains a version-1 depth ceiling of 32 because PDFBox's
Form processing itself is recursive. Form type, bounding box, optional matrix,
optional resources, and optional form type are raw-validated before backend
construction. The text-item bound is enforced before source-code mapping
evidence is published.
Resource-dependent `Tf`, `Do`, `gs`, and `BDC` operators with missing or
malformed named resources fail through the stable query diagnostic before
backend operator code runs. All supported extraction operators also have
their arity, operand kinds, and relevant finite numeric ranges validated so a
malformed trailing operator cannot publish an earlier valid-looking prefix.
Text-object `BT`/`ET` and graphics-state `q`/`Q` pairs must also balance within
the page and independently within each Form before publication; text operators
outside a text object are rejected.
The `gs` adapter applies only its validated optional two-item `Font` setting
through a bounded font view or detached one-key dictionary; unrelated ExtGState arrays and graphs
are ignored rather than delegated to unbounded backend traversal.

The #75 structure extension preserves these mapping rules. Page and Form marked
content retain distinct stream scopes and execution occurrences. Each stream
invocation independently balances marked-content operators and forbids duplicate
MCID definitions. MCRs may identify Form streams and annotation appearance
owners; OBJRs retain detached Session-owned object identity without executing
or rendering the referenced object. Structural backlinks use bounded iterative
ParentTree traversal. Internally structure-linked Forms cannot be invoked
repeatedly, while ordinary repeated Forms and whole-object OBJRs retain their
separate ISO 32000-1 relationships. Structural content items remain leaves,
including unexecuted appearance definitions and nested whole-object content.

The namespace extension retains declared namespace URI and dictionary identity
separately from the resolved standard role and namespace. Iterative RoleMapNS
traversal supports cross-namespace and default-namespace destinations without
fetching namespace URIs. Shared namespace and role-map objects charge once per
Query under the mandatory role-mapping bound. Unqualified standard-looking names
honor the document RoleMap from PDF 1.5 onward; circular unqualified associations
stop at a recognized role or remain unresolved, as ISO 32000-1 §14.7.3 permits.
This explicitly corrects version 1's earlier blanket rejection of those valid
associations.

The #75 Type 3 extension bounds each CharProcs dictionary entry per selected
font and decodes each distinct glyph stream once across the Query. Required
font geometry, code ranges, widths and encoding are validated before font
construction. A glyph stream must begin with finite d0/d1 metrics, zero vertical
width and a width consistent with its declared Widths entry. FontMatrix supplies
the horizontal text-space displacement; codes outside FirstChar–LastChar have
zero width. A private declared-metric font view prevents backend fallback to
glyph programs or FontDescriptor widths, for both Tf and gs selection. Glyph
painting instructions are not executed as Page Text. Encoding or ToUnicode
evidence supplies Unicode under the unchanged uncertainty rule.

The #75 ToUnicode extension follows embedded stream-dictionary UseCMap links
iteratively with cycle checks and mandatory node, codespace, mapping, decoded
byte and Workflow nesting bounds. Each selected font is constructed once per
Query from a detached dictionary with ToUnicode removed; explicit observations
come from the bounded project-owned mapping table, separately from backend code
decoding and geometry. Source length and bytes jointly identify the key, avoiding
the pinned backend's three/four-byte integer collision. Local mappings override
inherited mappings. Owned mapping text is reserved before allocation and released
with the Query; detached result text retains its separate ownership. Original
font identities continue to drive preflight and independent encoding evidence.
Undefined local scalar-range destinations mask inherited values instead of
restoring ancestor certainty. An inheriting CMap cannot redeclare its adopted
codespace. Textual usecmap occurs once before mapping operations and must name
the same parent declared by the embedded stream dictionary. Named parents are
limited to the pinned dependency's four Adobe CNS1/GB1/Japan1/Korea1 UCS2
resources, with the same per-font node, codespace, mapping and decoded-byte
charges. Their scalar ranges use the full carrying increments of those
published mapping resources, independently cross-checked against Adobe cid2code
data. Inline PDF ToUnicode retains its final-byte rule and undefined local masks.
Owned streaming buffers and the Workflow decompression bound cover
those fixed resources; their names are never paths or fetch instructions.
Malformed program boundaries, counts, operators and destinations cannot degrade
to a successful inferred prefix. On successful Query completion, each observed
mapping String's existing owned-memory reservation transfers once to the result;
unobserved mapping text and metadata release. A failed Query releases all of it.

The #75 Encoding implementation admits the fixed 61 standard names in ISO
32000-1 Table 118, including Identity-H/V, and bounded embedded programs.
Nodes, codespace declarations and every source code covered by CID or
notdef mappings charge font-data entries before construction. Codespace ranges
remain compact. Identity-H costs 65,538 entries; Identity-V adds one inherited
node. Decoded resource bytes charge per selected font, independently of backend
caches. Source length remains part of character-code identity and CID 0 remains
a valid selector. Both Encoding and ToUnicode validate mapping domains against
their adopted codespaces, reject overlap or prefix ambiguity, and require
codespace declarations before mappings. Source ranges use unsigned arithmetic.
Local CID mappings override inherited mappings; missing normal mappings select
notdef mappings, then CID 0. Font and Encoding Registry/Ordering must agree;
Supplement may differ. The metric adapter preserves the original numeric source code
for text-state word spacing while resolving declared descendant widths and
vertical vectors by CID. It uses a Query-owned constant embedded backend CMap
and a detached descendant without ROS to prevent auxiliary CMap loading. Both
the wrapper stream and supplied cache close on every Query exit.
Distinct selected Type0 dictionaries each charge their descendant's validated
metric-construction cost, including when they share the descendant object.
Repeated selection of the same font reuses its already charged construction.

Public controls cover every admitted predefined resource's exact byte and
entry bounds, unsigned mixed-length sources, CIDFontType0/2 horizontal and
vertical geometry, CID 0, inherited CID/notdef overrides and default fallback.
This is a declared-metric extraction profile; substitute glyph appearance or
missing-glyph equivalence across renderers is not claimed, and Unicode remains
independent of glyph substitution. No independent certification is claimed.
