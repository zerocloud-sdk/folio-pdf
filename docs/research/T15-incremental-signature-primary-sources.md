# T15 incremental publication and signature protection: primary-source research

Status: research input, not an authoritative Folio PDF policy, ADR, Capability
Matrix entry, or Acceptance Evidence record.

Researched: 2026-09-02 against repository fixed point
`918c8b8d18abb94d480e6998127dbba85faa0fb5`.

## Scope and source discipline

This note answers four implementation questions for T15:

1. What behavior do GitHub issues #1 and #16 require?
2. What does ISO 32000-1:2008 say about incremental revisions, existing
   signatures, `ByteRange`, and DocMDP?
3. Which malformed, contradictory, or unsupported states must Folio PDF
   distinguish before it can grant a mutation?
4. What does Apache PDFBox 3.0.8 actually do when loading, traversing,
   discovering signatures, and saving incrementally?

The repository had no general research-note directory or format at the fixed
point. Capability evidence belongs under `capabilities/evidence/`, but these
findings are design inputs rather than independently produced acceptance
evidence. This note therefore establishes `docs/research/` as a deliberately
non-authoritative location.

Only inputs permitted by [CONTRIBUTING.md](../../CONTRIBUTING.md) were used:
project specifications, a public PDF standard, and public Apache PDFBox API and
source. No iText source, resource, fixture, binary, output, decompiled detail,
closed add-on material, or proprietary behavioral evidence was consulted.

## Source inventory

| Source | Exact material consulted | Provenance and boundary |
| --- | --- | --- |
| Folio PDF parent specification | [GitHub issue #1](https://github.com/zerocloud-sdk/folio-pdf/issues/1), including its body, labels, state, and comments, read with `gh issue view 1 --comments` | Project-owned specification by `mabaiqiu`; open and labelled `ready-for-agent` when read. The sole comment concerns naming and supplies no T15 behavior. |
| T15 ticket | [GitHub issue #16](https://github.com/zerocloud-sdk/folio-pdf/issues/16), including its body, labels, state, and comments, read with `gh issue view 16 --comments` | Project-owned ticket by `mabaiqiu`; open, labelled `ready-for-agent`, and without comments when read. No issue or tracker state was changed. |
| Declared T15 dependency status | [GitHub issue #10](https://github.com/zerocloud-sdk/folio-pdf/issues/10), metadata only, read with `gh issue view 10` | Project-owned ticket. Issue #16 still names #10 as its blocker; #10 was closed on 2026-08-11, so the declared issue dependency is no longer open. |
| PDF standard | Adobe-hosted authorized copy of [PDF 32000-1:2008, *Document management — Portable document format — Part 1: PDF 1.7*](https://opensource.adobe.com/dc-acrobat-sdk-docs/pdfstandards/PDF32000_2008.pdf), SHA-256 `9de0ca9e8570d6209e8bd48a355be8eb6ec376acfc3fc3ae97cd8730351417ff` | The copy's copyright notice says its technical material is identical to ISO 32000-1 and preserves section and page numbers. It is copyright Adobe/ISO, all rights reserved. It was downloaded only to a temporary directory for reading; it is not redistributed here. This note paraphrases facts and names clauses/tables. |
| PDFBox 3 migration guide | Apache PDFBox's [3.0 migration guide](https://pdfbox.apache.org/3.0/migration.html#use-loader-to-get-a-pdf-document) | Public first-party documentation. |
| PDFBox 3.0.8 source | Apache tag `3.0.8`, peeled commit [`9286e47d89d6877005c9d2d0f2fd38793a62519a`](https://github.com/apache/pdfbox/tree/9286e47d89d6877005c9d2d0f2fd38793a62519a); the locally resolved source JAR had SHA-256 `eaed642d27599c78229857e4ab571805979f828f5ec8c695e3135ca933766132` | Public first-party source under the [Apache License 2.0](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/LICENSE.txt). Source was inspected to establish backend behavior; no source was copied or adapted into Folio PDF. |

## Requirements established by issues #1 and #16

The parent specification's user stories 18–20 require callers to choose
`REWRITE` or `INCREMENTAL` explicitly, make signed documents read-only by
default, and allow incremental changes only when the applicable signature and
DocMDP rules permit them. Its implementation decisions add these boundaries:

- ordinary edits to unsigned documents default to `REWRITE`;
- unknown content is preserved when safe and the operation is rejected when
  preservation cannot be guaranteed;
- the Foundation Release recognizes and protects existing signatures but does
  not create or validate them; cryptographic signing and verification remain
  Trust-module work;
- checked failures use stable codes and safe diagnostics; and
- tests exercise observable behavior through `DocumentWorkflow.execute`,
  reopen outputs, and prove prior-revision preservation rather than global
  byte equality.

These are direct requirements of [issue #1](https://github.com/zerocloud-sdk/folio-pdf/issues/1),
not conclusions inferred from PDFBox.

[Issue #16](https://github.com/zerocloud-sdk/folio-pdf/issues/16) narrows the
slice to explicit incremental publication, preservation of prior revisions,
signed-document detection, default read-only behavior, and rejection of
unproven signature-affecting changes. Its acceptance criteria require:

- a parseable appended revision that retains the original revision bytes;
- default rejection of ordinary mutations on signed documents with a safe
  signature or policy reason;
- publication only for operations proven permissible under every applicable
  signature and DocMDP state;
- public-seam tests that avoid backend coupling; and
- simultaneous updates to behavior, documentation, inventories, provenance,
  and actual Acceptance Evidence, with no unsupported stable facade stub.

Issue #16 identifies #1 as its parent and #10 as its blocker. The ticket body
has not removed that declaration, but [issue #10](https://github.com/zerocloud-sdk/folio-pdf/issues/10)
is closed, so there is no remaining open native issue blocker.

The ticket therefore does **not** permit equating “PDFBox can serialize this
change” with “this change is signature-policy compliant.” Serialization and
authorization are separate gates.

## ISO 32000-1 findings

All clause and table references in this section are to the authorized
[PDF 32000-1:2008 copy](https://opensource.adobe.com/dc-acrobat-sdk-docs/pdfstandards/PDF32000_2008.pdf).

### Incremental revisions and preservation boundary

Clause 7.5.6, “Incremental Updates” ([PDF page 52](https://opensource.adobe.com/dc-acrobat-sdk-docs/pdfstandards/PDF32000_2008.pdf#page=52)),
requires an incremental update to append its changes at end-of-file while
leaving the original contents intact. The new cross-reference section contains
entries for changed, replaced, or deleted objects; deleted objects remain in
the prior bytes and are marked deleted in the new cross-reference data. The
new trailer points to the preceding cross-reference section through `Prev` and
ends with its own `%%EOF`. A reader resolves an updated object to its newest
copy.

Clause 12.8.1, “General,” note 1 ([PDF pages 474–475](https://opensource.adobe.com/dc-acrobat-sdk-docs/pdfstandards/PDF32000_2008.pdf#page=474)),
connects that file structure to signatures: an incremental save preserves the
bytes selected by the original signature's byte range, allowing the signed
revision to be reconstructed.

Implementation consequence: the strongest simple publication invariant is
that the entire source byte sequence is an exact prefix of the staged product,
the product is longer than the source, and the suffix is a parseable new
revision. This is stronger and easier to verify than comparing only the bytes
named by each signature. It does not authorize the semantic change; it only
proves the preservation mechanism.

### Where signatures and permissions are rooted

The standard defines several anchors that must be inspected rather than a
single universal “signed” flag:

- Clause 12.7.2, Tables 218–219 ([PDF pages 439–440](https://opensource.adobe.com/dc-acrobat-sdk-docs/pdfstandards/PDF32000_2008.pdf#page=439))
  places root fields in the catalog's `AcroForm/Fields` array. `SigFlags` is
  optional with default zero. Its `SignaturesExist` bit is a reader UI
  optimization, and `AppendOnly` warns against a full save. Because the entry
  is optional, neither a clear nor absent bit proves that no signed field
  exists.
- Clause 12.7.3.1 and Table 220 ([PDF pages 440–441](https://opensource.adobe.com/dc-acrobat-sdk-docs/pdfstandards/PDF32000_2008.pdf#page=440))
  define a hierarchical field tree. `FT` is required for terminal fields but
  inheritable, and `/Sig` identifies a signature field. `V` is also
  inheritable. A child has at most one parent; `Parent` and `Kids` must agree
  on the immediate hierarchy.
- Clause 12.7.4.5 ([PDF page 454](https://opensource.adobe.com/dc-acrobat-sdk-docs/pdfstandards/PDF32000_2008.pdf#page=454))
  says a signature field has `FT /Sig`; when `V` is present it is the signature
  dictionary. An empty signature field therefore is not by itself an existing
  signature value.
- Clause 12.8.1 and Tables 252–253 ([PDF pages 474–478](https://opensource.adobe.com/dc-acrobat-sdk-docs/pdfstandards/PDF32000_2008.pdf#page=474))
  allow one or more approval signatures in signature fields, at most one
  certification signature, and usage-rights signatures rooted at catalog
  `Perms/UR3` rather than a signature field. `Type /Sig` in the signature
  dictionary is optional, so a scan that relies only on `/Type /Sig` is
  incomplete.
- Clause 12.8.4 and Table 258 ([PDF pages 484–485](https://opensource.adobe.com/dc-acrobat-sdk-docs/pdfstandards/PDF32000_2008.pdf#page=484))
  root the permissions dictionary at catalog `Perms`. Its `DocMDP` value is an
  indirect reference to the certification signature dictionary, which must
  contain a `Reference` entry with a DocMDP transform and corresponding
  parameters. Consumer applications are directed to enforce its `P`
  permission and validate whether it has been violated.

Implementation consequence: discovery must traverse both the complete
AcroForm field hierarchy (honouring inheritance) and the catalog permissions
dictionary, then cross-check identity and structure. A convenience list of
local signature-field values is not a complete detector.

### Signature dictionaries and `ByteRange`

Clause 12.8.1 and Table 252 ([PDF pages 474–477](https://opensource.adobe.com/dc-acrobat-sdk-docs/pdfstandards/PDF32000_2008.pdf#page=474))
establish these structural facts:

- a signature-field signature and a `UR3` usage-rights signature require a
  `ByteRange`;
- `ByteRange` is an array of integer `(starting offset, length)` pairs that
  identifies the exact bytes used for the digest;
- `Contents` is required and, when `ByteRange` is present, must use the PDF
  hexadecimal-string form;
- the normal shape covers the whole signed revision except the `Contents`
  value, but the standard permits other ranges while discouraging them;
- multiple discontiguous ranges are permitted; the standard does not limit
  the array to exactly four integers;
- when `ByteRange` is present, values in the signature dictionary are direct
  objects; and
- later signatures can be ordered by their progressively longer byte ranges,
  because signing produces incremental saves.

For protection without cryptographic verification, structural validation
should at least distinguish all of the following before treating a value as
permission evidence:

- missing entry versus wrong type;
- an odd number of elements versus pairs;
- non-integer, negative, overflowing, out-of-file, overlapping, or
  non-monotonic ranges;
- an absent or wrongly typed required `Contents` value; and
- direct-object requirements versus unexpected indirect values.

The standard permits more than two pairs. If T15 version 1 deliberately
supports only the common four-integer form, any other structurally valid pair
sequence is “unsupported,” not automatically “malformed,” and must still fail
closed for mutation. In every accepted case, incremental output preserves the
whole original source, including all byte-range-covered regions.

`ByteRange` establishes which bytes a cryptographic digest purports to cover;
it does not prove that the digest is authentic or valid. T15's stated scope
must not describe structural inspection as signature verification.

### Signature reference dictionaries and DocMDP

Table 253 and clause 12.8.2.1 ([PDF pages 477–478](https://opensource.adobe.com/dc-acrobat-sdk-docs/pdfstandards/PDF32000_2008.pdf#page=477))
define `Reference` array members. `TransformMethod` is required and its
standard values are `DocMDP`, `UR`, and `FieldMDP`; `TransformParams` is
optional at the generic signature-reference level. A certification signature
must contain a DocMDP reference. Approval or certification signatures may
also carry FieldMDP references.

Clause 12.8.2.2 and Table 254 ([PDF pages 478–479](https://opensource.adobe.com/dc-acrobat-sdk-docs/pdfstandards/PDF32000_2008.pdf#page=478))
add the DocMDP rules:

- a document has at most one signature field containing a DocMDP transform,
  and it is the first signed field;
- `P=1` permits no document changes;
- `P=2` permits form filling, page-template instantiation, and signing only;
- `P=3` also permits annotation creation, deletion, and modification;
- every other change invalidates the certification signature;
- omitted `P` defaults to 2; and
- transform-parameter `V`, when present, has the sole valid value `/1.2` and
  otherwise defaults to `/1.2`.

The generic table makes `TransformParams` optional, while Table 258 describes
a catalog `Perms/DocMDP` certification signature as having a DocMDP reference
and corresponding transform parameters. For T15's “missing evidence fails
closed” contract, a useful narrow distinction is:

- a present, valid DocMDP parameters dictionary with omitted `P` has the
  standard default `P=2`; but
- an absent, wrongly typed, unsupported-version, or contradictory parameters
  structure supplies no permission grant for mutation.

P=3 is a category boundary, not blanket permission. At the fixed point,
`UpdateAnnotations` is the only current Document Command family whose stated
effect is directly within “annotation creation, deletion, and modification.”
`FlattenAnnotations` changes page contents and removes annotations, and
`UpdateActions` changes catalog or page actions; they are not established by
Table 254 as permitted P=3 changes. Page operations, metadata, outlines,
destinations, attachments, arbitrary `DocumentPatch` changes, and all other
non-form commands are likewise outside P=2. A P=3 implementation may authorize
`UpdateAnnotations` only after proving its complete mutation footprint remains
inside the annotation category and every other applicable restriction allows
it.

An ordinary approval signature without a proven DocMDP permission does not
itself grant any mutation. Issue #1's default-read-only rule therefore remains
the controlling product policy. P=1 allows no mutation; P=2 allows no current
non-form T15 command; P=3 can at most open the narrowly proven annotation
command path.

### Malformed, conflicting, cyclic, and unsupported evidence

ISO 32000-1 specifies conforming structures; it does not grant extra
permissions when a reader repairs a malformed one. The following constraints
make conflicts observable:

- Clause 7.3.7 ([PDF page 26](https://opensource.adobe.com/dc-acrobat-sdk-docs/pdfstandards/PDF32000_2008.pdf#page=26))
  prohibits duplicate keys within one dictionary.
- Table 15 ([PDF page 51](https://opensource.adobe.com/dc-acrobat-sdk-docs/pdfstandards/PDF32000_2008.pdf#page=51))
  and clause 7.7.2/Table 28 ([PDF pages 81–83](https://opensource.adobe.com/dc-acrobat-sdk-docs/pdfstandards/PDF32000_2008.pdf#page=81))
  require a trailer `Root` reference to a catalog and define the catalog
  `AcroForm` and `Perms` entry types.
- Clause 12.7.3.1/Table 220 makes the field relationship a tree with at most
  one parent per field.
- Clause 12.8.2.2 permits only one DocMDP signature and requires it to be the
  first signed field.
- Tables 252–254 and 258 prescribe the types, values, references, and defaults
  described above.

It follows that cycles in a field “tree,” repeated children under different
parents, parent/child mismatches, multiple DocMDP signatures, a catalog
DocMDP reference to a different signature dictionary, incompatible `P`
evidence, wrong types, unknown transforms or versions, and duplicate critical
keys cannot be interpreted as a more permissive policy. This fail-closed
result is a conservative Folio PDF inference from the standard and the issue
contract, not an ISO recovery algorithm.

A bounded traversal should track indirect-object identity, active recursion,
visited nodes, depth, node count, and array/value count. A cycle and a repeated
node are separately useful diagnostics, but both deny mutation. Every
signature and every signature-reference entry must be inspected; the effective
permission is no broader than the most restrictive applicable, supported
policy. An unknown or unsupported restriction never widens that intersection.

## Apache PDFBox 3.0.8 behavior

### Loading and source lifetime

PDFBox 3 removed all `PDDocument.load` methods. Its official
[3.0 migration guide](https://pdfbox.apache.org/3.0/migration.html#use-loader-to-get-a-pdf-document)
directs callers to `org.apache.pdfbox.Loader`. `Loader.loadPDF(byte[])` wraps
the bytes in `RandomAccessReadBuffer`; `Loader.loadPDF(File)` uses
`RandomAccessReadBufferedFile`. The [3.0.8 Loader source](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/Loader.java#L157-L242)
intentionally retains that random-access source after parsing because it may
be needed for signing. `PDDocument` stores it as `pdfSource` and closes it when
the document closes ([constructors and close](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/PDDocument.java#L120-L232),
[lines 1250–1280](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/PDDocument.java#L1250-L1280)).

`PDFParser.parse()` enables lenient mode by default. It may add a missing
catalog `/Type`, and `COSParser` may rebuild a trailer after a parse failure
([PDFParser lines 97–177](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdfparser/PDFParser.java#L97-L177),
[COSParser lines 247–313](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdfparser/COSParser.java#L247-L313)).
Successful `Loader.loadPDF` is therefore evidence of PDFBox readability, not
proof that signature-policy structures were strictly conforming on input.

### Trailer and catalog traversal is normalized and lossy

`COSParser` follows the `Prev` chain and detects a direct `Prev` loop, but in
default lenient mode that exception can lead to brute-force trailer rebuilding
([xref traversal lines 334–457](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdfparser/COSParser.java#L334-L457)).
`XrefTrailerResolver` then merges active trailer dictionaries so later values
overwrite earlier ones ([resolver lines 240–297](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdfparser/XrefTrailerResolver.java#L240-L297)).
Consequently `PDDocument.getDocument().getTrailer()` is a resolved COS view,
not a byte-faithful record of each original trailer.

`PDDocument.getDocumentCatalog()` guarantees a non-null result and constructs
a new catalog when the trailer's resolved `Root` is not a dictionary
([lines 767–788](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/PDDocument.java#L767-L788)).
That constructor installs the newly created catalog into the resolved trailer,
so the convenience getter can mutate the in-memory document while concealing
the bad input shape
([PDDocumentCatalog lines 68–80](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/PDDocumentCatalog.java#L68-L80)).
Likewise, `COSDictionary.getDictionaryObject` dereferences an indirect object
and maps `COSNull` to Java `null`, while `getCOSDictionary` returns `null` for
both absence and wrong type
([COSDictionary lines 140–223](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/cos/COSDictionary.java#L140-L223),
[lines 549–581](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/cos/COSDictionary.java#L549-L581)).

Implementation consequence: policy parsing must inspect `getItem` before
dereferencing, validate required indirect/direct shape explicitly, and avoid
using a null-returning typed convenience getter as proof of absence.

PDFBox's dictionary parser calls `COSDictionary.setItem`, whose backing map
replaces an earlier value for the same key. It also logs and recovers from some
bad dictionary syntax ([BaseParser lines 268–409](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdfparser/BaseParser.java#L268-L409)).
Duplicate critical keys may therefore be impossible to distinguish after the
COS model is built. If T15 promises to reject that exact malformed syntax, a
bounded source-syntax preflight is needed; COS-only inspection cannot prove
the promise.

The parser also normalizes both a literal PDF string and a hexadecimal PDF
string into `COSString`. Its hex path calls `COSString.parseHex`, which returns
the same default-form object used for parsed literal bytes; the original
delimiter/form is not retained
([BaseParser lines 537–548](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdfparser/BaseParser.java#L537-L548),
[BaseParser lines 690–755](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdfparser/BaseParser.java#L690-L755),
[COSString lines 129–193](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/cos/COSString.java#L129-L193)).
The Table 252 requirement that signature `Contents` be hexadecimal when
`ByteRange` is present is therefore another lexical property a COS-only check
cannot prove.

### Convenience signature discovery is not a policy validator

`PDDocument.getSignatureFields()` walks `getAcroForm(null).getFieldTree()`;
`getSignatureDictionaries()` then adds only a local field `V` that PDFBox sees
as a dictionary ([PDDocument lines 844–883](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/PDDocument.java#L844-L883)).
That has several consequences:

- it does not inspect catalog `Perms/DocMDP` or `Perms/UR3`;
- it does not honour an inherited `V` at this collection point;
- a wrong-type AcroForm or `Fields` value collapses to null/empty;
- non-dictionary field-array members and unrecognized field types are skipped
  ([PDAcroForm lines 368–431](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/interactive/form/PDAcroForm.java#L368-L431),
  [PDFieldFactory lines 50–147](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/interactive/form/PDFieldFactory.java#L50-L147)); and
- field traversal uses identity tracking to log and ignore a repeated/cyclic
  child instead of reporting a checked structural failure
  ([PDFieldTree lines 55–127](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/interactive/form/PDFieldTree.java#L55-L127)).

A returned empty list therefore does not prove that no signature-bearing or
malformed signature structure exists. T15 needs its own bounded COS-level
field and permissions traversal, with explicit failure instead of logging and
skipping.

`PDSignature.getByteRange()` returns an empty array when the entry is missing
or not an array; array members that are not numbers become `-1` through
`COSArray.getInt`. It performs no pair-count, ordering, bounds, overflow, or
coverage validation ([PDSignature lines 295–310](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/interactive/digitalsignature/PDSignature.java#L295-L310),
[COSArray lines 245–290](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/cos/COSArray.java#L245-L290)).
Similarly, `COSDictionary.getInt` accepts any `COSNumber` and calls
`intValue()`, so it can collapse a wrong/missing value to a default or truncate
a fractional number ([COSDictionary lines 953–1034](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/cos/COSDictionary.java#L953-L1034)).
Critical `ByteRange` and DocMDP `P` values must be checked from their raw COS
types and exact numeric values rather than these convenience conversions.

### `saveIncremental` preserves bytes but does not enforce DocMDP

`PDDocument.saveIncremental(OutputStream)` requires a document loaded from a
file or stream, because it needs the retained `pdfSource`. A newly constructed
`PDDocument` has no such source and causes `IllegalStateException`. The API
also warns never to point the output at the input file and requires a marked
update path from the catalog for changed objects; the overload accepting a set
of dictionaries can force selected objects into the revision
([PDDocument lines 1055–1135](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/PDDocument.java#L1055-L1135)).

For a normal incremental save, `COSWriter` buffers the new revision, copies
the retained source bytes unchanged to the destination, and writes the
buffered revision afterward ([COSWriter lines 857–871](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdfwriter/COSWriter.java#L857-L871)).
It begins the appended portion with a line break, writes incremental body and
xref/trailer material, and terminates it with `startxref` and `%%EOF`
([lines 1308–1361](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdfwriter/COSWriter.java#L1308-L1361)).

Nothing in this save path evaluates existing signature dictionaries, DocMDP
permissions, FieldMDP, command semantics, or cryptographic validity. The
Javadoc calls signed-file modification a typical use case but explicitly
places correct object selection on an experienced caller. Folio PDF must run
its own detection and command-authorization gates before mutation and
publication.

Operational consequences for the Document Workflow are:

- require an existing primary Source before caller work for `INCREMENTAL`;
- keep the loaded source alive until the staged incremental save completes;
- stage to a distinct temporary destination, never the source or final Path;
- make every allowed mutation produce a complete PDFBox update path;
- verify exact source-prefix preservation and a non-empty suffix before
  publication;
- reopen and independently syntax-check the staged product; and
- reuse the existing ordered publication and receipt transaction only after
  all policy and staging checks pass.

## Conservative version-1 decision outline

The sources support the following narrow decision order. This is an
implementation-oriented derivation, not yet the authoritative T15 policy:

1. Reject `INCREMENTAL` without an existing primary Source before invoking
   caller work.
2. Inspect the source's literal and resolved critical structures with explicit
   bounds. Distinguish absence, malformed data, contradiction, and a
   well-formed but unsupported feature.
3. Traverse all field roots and descendants with inheritance and identity
   checks. Traverse catalog `Perms` separately. Cross-check the certification
   signature object, first-signature ordering, every `Reference` member, and
   every structurally valid `ByteRange`.
4. Treat any existing signature as read-only unless a supported DocMDP policy
   proves the exact command family permissible. An ordinary signature grants
   nothing; P=1 grants nothing; P=2 grants no current non-form command; P=3 can
   at most grant a proven annotation-only update. Unsupported FieldMDP, UR,
   public extensions, unknown transforms, or contradictory structures deny
   mutation.
5. Intersect restrictions across all signatures and references. Missing or
   unsupported evidence never widens the result.
6. Authorize at the Document Command boundary before applying the mutation.
   Keep arbitrary `DocumentPatch` changes rejected for signed sources because
   their semantic category is not proven by the patch shape.
7. Serialize with PDFBox only after policy authorization. Stage, validate,
   prove the original byte prefix and appended revision, then enter the
   existing publication transaction.
8. Keep target-free read-only Queries separate from publication. A full
   `REWRITE` of a signed source does not preserve the prior byte revision and
   remains disallowed even when no semantic command ran.

## Uncertainties and explicit non-claims

- ISO DocMDP validation says to verify the byte-range digest before checking
  permitted modifications. T15 explicitly does not cryptographically validate
  signatures. It can conservatively enforce the structurally declared
  restriction, but it cannot claim that the signer or permission is authentic,
  that `Contents` is valid, or that the signature remains cryptographically
  valid.
- ISO 32000-1 allows a general sequence of byte-range pairs. Restricting T15
  version 1 to the usual two ranges is a product limitation that must be
  documented as such.
- `TransformParams` is optional in generic Table 253, while Table 258 expects
  corresponding parameters for catalog DocMDP. The fail-closed interpretation
  above intentionally refuses to derive a mutation grant from a missing
  parameters dictionary.
- Table 252 types `Reference` as an array, while Table 258's DocMDP prose
  describes that entry in the singular as a signature reference dictionary.
  The narrow reading used here requires the array to contain a valid DocMDP
  reference and inspects every array member; it does not ignore additional or
  conflicting transforms.
- “Annotation modification” in P=3 does not enumerate every transitive object
  a concrete library command may touch. Authorization depends on proving the
  complete `UpdateAnnotations` mutation footprint, not only its public name.
- PDFBox's lenient parser and map-based dictionaries erase some malformed
  syntax, especially duplicate keys and repaired trailer history. COS-only
  validation cannot honestly claim to detect those cases; a bounded raw-source
  preflight is required for that stronger promise.
- ISO 32000-1 does not define later ETSI/PAdES structures such as
  `/DocTimeStamp`. Encountering such signature-like extensions should be
  classified as unsupported and fail closed for mutation unless a later
  ticket adds a cited policy.
- No fixture was created, no signature was cryptographically generated or
  validated, and no acceptance result was produced during this research.
