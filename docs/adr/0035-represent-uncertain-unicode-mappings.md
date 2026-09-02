# Represent uncertain Unicode mappings instead of guessing

Page Text extraction follows the ISO 32000 mapping sources while preserving each source character code and the evidence used. A missing mapping yields no invented Unicode value, and contradictory explicit and independently derivable standard mappings retain both observations without selecting either as certain; `ActualText` remains a separate replacement-text source rather than a per-character mapping. Backend coercions and font-program guesses therefore never become confident Native Interface results.

Inference is limited to an explicit simple-font `Differences` entry for the
specific code, or otherwise to an explicitly declared recognized `Encoding`
name or recognized `BaseEncoding`, mapped through the public Adobe Glyph List.
An absent or unknown base supplies no fallback, but does not erase a declared
code-specific `Differences` entry; neither case authorizes consulting an
embedded, substituted, or system font.
Before an embedded `ToUnicode` CMap reaches FontBox, Folio PDF tokenizes strict
PDF numbers, including signed and leading-fraction forms, then applies the
backend-compatible numeric-to-integer conversion and bounds every materialized
`bfchar` and expanded `bfrange` entry under a mandatory caller limit. This is
intentionally stricter than FontBox tokenization for leading fractions. It also
bounds only operators before FontBox's `endcmap` stop and charges scalar ranges
using the carrying increment used by PDFBox's non-strict embedded-font CMap
construction path. The separate public mapping observation uses strict inline-
CMap parsing, so the construction-safety charge is deliberately conservative
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
Type 0 fonts must name `/Identity-H` or `/Identity-V` and contain exactly one
`CIDFontType0` or `CIDFontType2` descendant, preventing arbitrary predefined
or embedded CMap loading and recursive composite-font acceptance. An embedded
Type 0 program whose detected header contradicts that descendant subtype is
rejected before PDFBox can repair the live COS graph. Type 3
fonts are outside version 1, preventing their separate `/CharProcs` streams
from bypassing decoded-byte accounting. Before backend traversal, an
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
through a detached one-key dictionary; unrelated ExtGState arrays and graphs
are ignored rather than delegated to unbounded backend traversal.
