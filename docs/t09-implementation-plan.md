# T09 Foundation values implementation and certification contract

This is the execution contract for [#71](https://github.com/zerocloud-sdk/folio-pdf/issues/71),
under #33 and #1, against fixed review baseline
`543c582cb41104f7da43b9d801c629894dcec34a`. It records required work, not completion
or Acceptance Evidence. The starting worktree was clean. No #71 commit,
publication, or issue-edit authorization has been granted.

## Required behavior and source traceability

| Source requirement | Required implementation and evidence |
| --- | --- |
| spec-us-14, spec-id-21 | Inspect, change, publish and reopen all nine PDF Value kinds, including exact decimals, binary strings, names, nested containers, streams and indirect references. |
| spec-us-15, spec-id-23 | Ordered, completely validated Document Patches; failed operations leave the transaction queryable and publishable with its earlier state. No backend objects, types or exceptions cross either public interface. |
| spec-us-16, spec-us-17, spec-id-26 | Preserve Source bytes, unrelated page appearance, unknown dictionary entries and resources. Preserve untouched encoded streams; reject unprovable preservation and protected document changes. |
| spec-id-17 | Reject foreign references, caller-defined values, malformed operations, introduced cycles and invalid indices with the existing stable failure contract. |
| spec-id-22 | Session-stable Object References; bounded container and stream views; limits and expiry through Native and Facade public behavior. |
| slice-71-1 through slice-71-4 | Implement the complete operations and mapping subset below, then independently certify their outcomes in every required environment and execution profile. |
| #1 ownership, failure and testing decisions; #71 remaining acceptance criteria | Caller streams/channels stay open; module-opened resources close; safe failures, Publication Receipts, Java 8 compatibility, independent chains, inventories, English/Chinese contracts and provenance stay consistent. |

## Patch operations

The Foundation low-level edit scope includes dictionary insertion/replacement
and removal; array element replacement, insertion and removal; value replacement
at a dictionary entry, array element or indirect object; and stream data
replacement. It includes nested direct containers as well as indirect targets.
Replacing scalar values, containers and reference-valued entries provides the
successful mutation path for all nine kinds. Existing indirect identity stays
stable when its value changes.

An indirect object's body is a direct PDF value, so a bare indirect reference
cannot be supplied as its complete replacement body. Such a request fails with
`PATCH_VALUE_REJECTED`; direct self-reference retains `PATCH_CYCLE_REJECTED`.
Reference-valued dictionary entries and array elements remain supported success
paths. A project-owned grammar probe with a reference-only indirect body produces
`expected endobj` under pinned qpdf 12.4.0; the independent Spec review found no
source requirement for that malformed placement.

An immutable `PdfValuePath` starts at a Session Object Reference and selects
dictionary names and zero-based array indices. Paths describe locations, not
new object identities, and resolve in Patch declaration order. Dictionary
removal of an absent entry is a no-op. Array insertion accepts `0..size`; access,
replacement and removal accept `0..size-1`. Invalid paths/targets/indices reject
the whole Patch. New operations use Patch version 2; existing dictionary-only
version-1 requests retain their representation and behavior.

Stream changes supply decoded data and an explicit unfiltered or Flate encoding.
The engine owns Length, Filter and DecodeParms and changes these atomically with
the data. Existing supported filter chains can be decoded and replaced without
requiring callers to edit encoding metadata. Untouched streams, including
unknown encodings, preserve encoded content and attributes. External-file
streams are never resolved. Raw encoding-metadata changes remain rejected;
this is an invariant, not a substitute for successful stream editing.

Ordinary rejected Patches restore their prior value and stream state so the
caller may continue. The existing hostile-input policy makes resource-limit
failures terminal even when caught. If actual stream I/O prevents restoration,
the Session also terminates with a stable failure; existing lazy views cannot
observe that failed working state, and no target is published. Source bytes
remain unchanged in both cases.

Unsigned REWRITE and the existing unsigned INCREMENTAL contract apply. Existing
Signatures grant no low-level mutation authority. Version/security state remains
owned by the existing output-policy interface. Resulting core document structure
must remain valid; unrelated structures and resource declarations are preserved.
This ticket does not add downstream page, Forms, Trust or other feature behavior.

## Frozen Facade families and members

The final Facade Surface must enumerate these familiar kernel mappings under
`net.zerocloud.pdf.itext7.kernel.pdf`, with exact signatures and exception
contracts. Preview remains the union of Stable and Preview additions.

- `PdfDocument`: retain the existing constructors and lifecycle; add the
  reader/writer constructor and `getCatalog()`.
- `PdfCatalog`: `getPdfObject()`.
- `PdfObject`: the nine kind constants, `getType()`, the nine kind predicates,
  and `getIndirectReference()`.
- `PdfNull`: default constructor and `PDF_NULL`.
- `PdfBoolean`: boolean constructor, `getValue()`, `TRUE`, `FALSE`.
- `PdfNumber`: int/double constructors, `intValue()`, `doubleValue()` and
  `setValue(int/double)`.
- `PdfString`: String/byte-array constructors, `getValue()`, `getValueBytes()`.
- `PdfName`: String constructor and `getValue()`.
- `PdfArray`: empty/List constructors, `size()`, `get(int)`, `get(int,boolean)`,
  `set(int,PdfObject)`, `add(PdfObject)`, `add(int,PdfObject)`, `remove(int)`.
- `PdfDictionary`: empty constructor, `size()`, `get(PdfName)`,
  `get(PdfName,boolean)`, `keySet()`, `containsKey(PdfName)`,
  `put(PdfName,PdfObject)`, `remove(PdfName)`.
- `PdfStream`: byte-array constructor, `getBytes()`, `setData(byte[])`, plus
  the mapped dictionary operations. Decoded data is serialized with engine-owned
  encoding metadata; raw Filter/Length mutation is not a mapped bypass.
- `PdfIndirectReference`: `getRefersTo()`; equality/hash behavior reflects the
  owning Session and never backend identity or cross-document object numbers.
- `PdfReader`/`PdfWriter`: retain filename constructors; add caller stream
  constructors to prove the same explicit ownership contract.

Facade inspection and mutation operate within one owned Native Document Workflow
Session until `PdfDocument.close()`. The Facade continues to execute IN_PROCESS;
Native HARDENED_WORKER evidence must not be attributed to the Facade. Mapped
mutations validate immediately, including after a caught failure. Scalars may be
detached; Session container views and dereferencing expire on close. Project
limits and publication configuration remain Native Interface controls, with
explicit mapping notes rather than unsupported reference stubs.

`PdfString(String)` maps text to the PDFDocEncoding ASCII subset where possible,
otherwise to BOM-prefixed UTF-16BE. `getValue()` interprets PDFDocEncoding
(ISO 32000-1 Annex D.3, Table D.2), UTF-16BE with its BOM, or PDF 2.0 UTF-8 with
its BOM. Undefined PDFDocEncoding codes and malformed Unicode bytes decode to
U+FFFD; `getValueBytes()` always preserves the exact string data bytes. An
unpaired Java surrogate is rejected with a safe `IllegalArgumentException`
before a Patch is submitted. These are declared project text-conversion rules,
not a claim about undocumented reference-library default encoding or its
separate, unmapped `toUnicodeString()` method. The independent Spec reviewer
confirmed this contract direction against #1 and ADR-0029.

Detached mutable Facade containers are copied into project-owned Native values
when inserted. Java containment cycles are rejected immediately with a safe
`PdfException` whose message starts `PATCH_CYCLE_REJECTED`; this local rejection
has no fabricated Native cause. Shared acyclic children remain valid. Actual
Native operation failures retain their real `DocumentFailure` cause. Conversion
and attachment-path resolution are iterative; deeper converted values reach the
existing Native nesting guard. A replaced direct number becomes detached, while
a removed container view can no longer mutate another value at its old location.
Dictionary `keySet()` returns an immutable snapshot of names. Attached stream
`setData` replaces decoded data using engine-owned Flate metadata.

## Acceptance Profile and gates

T09 requires Ubuntu 24.04/Linux x86-64, immutable declared Temurin JDK 8/11/17/21
images, each in Native IN_PROCESS and HARDENED_WORKER. Every tuple includes actual
Facade IN_PROCESS observations and candidate-jar surface and exclusivity checks.
Source, contract, jar, harness, PDF, image, tool, JDK/image and execution identities
bind each observation. Transactions evidence must be refreshed for changed
candidate/contract identities; historical PASS records keep their old identity.

Each tuple retains separate qpdf syntax, independently qualified standards,
project-owned semantic, and independent PDFium/ImageMagick visual records. T09
standards rules must cover its actual scalar/container/reference/stream and
filter structures in addition to T03's Catalog/page-tree rules, with a known
illegal PDF and observed diagnostic for every claimed rule. Positive fixtures
exercise real low-level changes and unchanged visible content. Negative controls
must actually fail; missing tools/rules, mismatched identities and uncertainty
remain FAIL or INDETERMINATE. T03's minimal blank qualification is insufficient.

Development follows one public behavior test to RED, minimal implementation to
GREEN, then review/refactoring and focused validation. Approved seams are
DocumentWorkflow outcomes, reopened products, Publication Receipts, public
Facade calls, acceptance command outputs and inventory CLI results. There are no
backend/private-call testing seams. The starting focused command passed 21 tests
with zero failures/errors/skips on 2026-09-08; this is a baseline observation only.

Final gates are inventory generate/validate/check/readiness, full Maven verify,
the default full JDK matrix using the verified explicit HarfBuzz helper,
`git diff --check`, and clean-context independent Standards/Spec review covering
all changes since the fixed baseline including untracked files. No completion
criterion is checked before independent review. Values can become compatible
only with dependency-satisfied current evidence; global Foundation readiness
remains NOT READY while other required obligations are unfinished.
