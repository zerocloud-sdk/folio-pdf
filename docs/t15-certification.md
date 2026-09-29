# T15 Foundation incremental certification

This profile certifies the Foundation `incremental` obligation owned by #77.
The [policy](incremental-signature-policy.md) and ADR-0037 remain authoritative
for permission intersection, parsed-graph limits and safe failure codes.
It makes no signature authenticity, trust, signing or Forms claim.

## Frozen public surface

Stable and inherited Preview expose six append-selection mappings:
`StampingProperties()`, its copy constructor, `useAppendMode()`,
`PdfDocument(PdfReader,PdfWriter,StampingProperties)`, the Folio named
Source/Target constructor with `PdfVersion,StampingProperties`, and
`PdfDocument.isAppendMode()`. Properties default to REWRITE and the Document
captures the selected mode at construction. The reader/writer append overload
inherits the Source version; the named overload retains its explicit version. Existing readers, writers,
Annotations, PDF Values and Publication Receipts retain their contracts.
The Facade executes IN_PROCESS. No Worker Facade mode is claimed.

## Products and independent expectations

`capabilities/profiles/T15-signatures/products.json` is the literal authority.
Each API produces six fresh products: an unsigned three-page first revision,
its four-page second revision, and separate signed P=3 creation, replacement,
movement and removal of a non-Widget Stamp. Original two-page paint, private
catalog data, signature dictionary, field roots and hidden Widget remain
unchanged. Each product is reopened through public APIs. Both unsigned
revisions preserve the complete immediate Source, including older revisions.

The 88-case matrix covers unsigned/empty fields, inherited signatures,
approval signatures, default/P=1/P=2/P=3 DocMDP, UR/UR3, FieldMDP, unknown
handlers/transforms, shared and distinct restrictions, intersection,
contradictory references, wrong types, critical indirection, all ByteRange
shapes and field cycles/parents. Exact/first-excess Native fixtures cover
4,096 field nodes/queued children, depth 64, 16 permission entries, 64
signature entries, 256 range entries, 64 transform references, and one
indirect resolution. Public consumer tests run in each Native mode; Facade
tests cover safe mappings, ownership, lifecycle and ordered receipts.

## Four mandatory chains

Every product requires all four chains. Acceptance imports neither product
code nor fixture-authoring code when deriving independent observations.

| Chain | Independent observation and qualification |
| --- | --- |
| Syntax | Pinned qpdf 12.4.0 `--check`, with a truncated-PDF control. |
| Standards | Pinned pdfcpu 0.15.0 strict/offline validation plus independent predicates over pinned qpdf JSON and raw bytes; 19 original core PDF defects and eleven incremental/signature rule groups must be detected. |
| Semantic | Independent qpdf decoded object graph versus literal page/Annotation/value expectations and appearance SHA-256; protected page paint, permissions, signature field/Widget and private values must match the immediate Source. Four detached-value controls and an altered protected stream must fail. |
| Visual | Pinned PDFium CLI v0.11.2/chromium-7881 at 144 DPI with `--render-annotations`, compared against authored sRGB grids by ImageMagick 7.1.2-30; AE=0, fuzz=0%. Changed paint must produce AE=1600 on each original page and zero on the added blank page. One changed pixel must produce AE=1. |

`products.json.rules` gives exact ISO 32000-1:2008 clause/table attribution:
7.5.6 (prefix, nonempty revision, immediate `/Prev`), 12.7.3.1/Table 220
(fields), 12.8.1/Table 252 (signature dictionary, ByteRange, direct critical
values), 12.8.2.1/Table 253 (references), 12.8.2.2/Table 254 (DocMDP), and
12.8.4/Table 258 (catalog permission references/coherent certification).
These are closed predicates for the original corpus and classic xref
products, not comprehensive ISO conformance or cryptographic verification.

The recorder retains exact process commands, exit statuses, bounded stdout
and stderr, product/source hashes, before/after tool identities and every
control finding. Missing tools, warnings on positive products, changed inputs,
unqualified rules, time/output limits and undetected controls abort collection.
The collector reconstructs verdicts from those observations rather than
accepting aggregate pass labels. Protocol-fixture tests deliberately reseal
tampered reports and require rejection. The fixture archive is test data,
never a Foundation certification receipt.

## Final candidate and environment binding

`scripts/t03-foundation.py` stages unsigned Maven artifacts and the separate
acceptance harness, then binds source, contract, artifact, corpus, tool,
Python/native runtime and execution configuration hashes. The required
environments are exactly the four immutable images in
`capabilities/foundation-environments.yaml`: Ubuntu 24.04 Linux x86-64 on
JDK 8, 11, 17 and 21, each with Native IN_PROCESS and HARDENED_WORKER.
Each observation records the actual Facade IN_PROCESS mode separately.
No Windows/macOS or release-publication claim is made.

With the repository-documented pinned tools provisioned, set
`FOLIO_HARFBUZZ_HELPER` to a complete explicit installation and
`FOLIO_FOUNDATION_PYTHON_ROOT` to the provisioned Python 3.12 runtime:

```bash
python3 scripts/t03-foundation.py stage
python3 scripts/t03-foundation.py certify capabilities/evidence/T77-incremental-r1 --obligation incremental
```

`certify` performs all eight tuples and merges their index only after every
observation passes. It rejects changed staged inputs. Refresh transactions,
values, pages, metadata, annotations, text and images using fresh directories
after final candidate changes. Historical evidence is never relabeled.
Live `./scripts/inventory readiness` decides whether the current index binds
this candidate; unrelated obligations can keep global readiness nonzero.
