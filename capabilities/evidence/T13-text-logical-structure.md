# T13 text and logical-structure extraction evidence

Status: `experimental`

Capability: `document.text-structure.extract`

Acceptance Profile: `T13-text-logical-structure`

Release train: `0.1.0-SNAPSHOT`

T13 exposes one immutable, backend-neutral, version-1 Document Query for
bounded page text, source-code mapping evidence, marked content, and Tagged PDF
logical structure. The complete result is detached from its Document Session;
PDFBox and FontBox remain private implementation details.

## Implementation evidence

- `TextStructureExtractionWorkflowTest` drives every observation through
  `DocumentWorkflow.execute`. Project-authored fixtures cover same-Session
  command/query ordering; detached untagged text; multi-page, multi-stream,
  repeated-query, and reopen determinism; translated, transformed, rotated,
  spaced, and non-identity nested-Form geometry and ordering; explicit,
  code-specific `Differences` inference with absent or unknown bases,
  contradictory, and missing mappings with exact defensive source bytes;
  bounded hostile-CMap rejection; and query-only source and publication non-
  mutation.
- Tagged and nested marked-content fixtures expose begin-order sequences,
  nesting, parent and text-item associations, outer ActualText precedence,
  MCID-to-page and logical-content links, catalog,
  ancestor, and directly declared language behavior, PDF 1.7 standard and
  RoleMap-resolved custom roles, unresolved PDF 2.0-only names, Alt, and
  distinct ActualText replacement behavior.
  MarkInfo, page StructParents, the ParentTree, and structure-parent links make
  it a fully linked Tagged PDF fixture. All logical children retain `/K` order.
- Every one of the fifteen mandatory `ExtractionLimits` accepts an exact
  boundary and fails on its first excess with
  `EXTRACTION_LIMIT_EXCEEDED`, capability
  `document.text-structure.extract`, and one fixed safe diagnostic. Nested
  Form tests independently exercise stream occurrence, decoded-byte, and
  depth accounting, including the exact version-1 Form depth ceiling of 32 and
  its first excess. Bounded iterative page-tree traversal rejects deep excess,
  false, negative, or oversized counts, inconsistent parents, cycles, and
  repeated nodes,
  and a 4,096-level tree resolves inherited page attributes without backend
  recursion; raw `/Contents` arrays are bounded and type-checked before backend
  traversal. A valid hexadecimal token split across two Contents members
  proves combined-stream parsing, while unterminated content, trailing orphan
  operands, malformed supported-operator operands, text operators outside
  `BT`/`ET`, or unbalanced
  page/Form `BT`/`ET` and `q`/`Q` state fail instead of publishing a valid-
  looking prefix. Malformed inherited
  page geometry and direct UserUnit values,
  malformed Form type, box, matrix, resources, form type, and cross-stream
  marked-content endings, repeated/shared structure elements, inconsistent
  structure parents, oversized MCIDs, OBJR and Form-stream MCR children, and
  structure namespaces all fail before backend coercion. A two-hop RoleMap
  resolves to its standard role and an authored RoleMap cycle fails
  repeatedly. Authored cyclic Form and
  logical-structure graphs fail repeatedly
  with stable `QUERY_FAILED` values, while a 4,096-level logical hierarchy
  accepts its exact element, item, and depth boundary through iterative
  traversal and fails safely one level below it. All terminate within policy
  and leave source bytes unchanged.
- A hostile four-byte `ToUnicode` range proves that caller-bounded mapping
  preflight, including decimal count syntax and signed four-byte range
  endpoints, rejects eager expansion before backend font construction. Direct
  and public cases also prove the carrying scalar-range materialization used
  by PDFBox's non-strict embedded-font construction path, the `endcmap` stop,
  exact declared mapping counts and terminators, non-reversed source ranges,
  acceptance and public mapping evidence for a valid paired-surrogate
  destination, and rejection of name, empty, odd-byte, or unpaired-surrogate
  destinations before backend coercion. Exact and exhausted byte bounds cover
  decoded embedded font data, while a cached entry bound covers distinct
  simple-font `Differences` arrays even when they are shared by encoding
  dictionaries. Exact code-specific overrides remain inferred with an absent
  or unknown base, while unoverridden codes stay missing; an oversized
  character-code integer fails before it can wrap into fabricated inferred
  Unicode. The same mandatory font-data-entry bound covers raw simple
  and CID width arrays plus the entries that compact CID ranges would
  materialize; hostile ranges are rejected before font construction. Present
  simple character ranges, CID default widths, selectors, and scalar metrics
  are validated without fractional coercion, and a large single text string
  proves prompt deterministic text-item exhaustion before result publication.
  Missing or invalid font types and subtypes, recursive Type 0 descendants,
  non-stream embedded-font entries, invalid `CIDToGIDMap` names, missing or
  arbitrary Type 0 encodings,
  embedded font headers that contradict their descendant subtype, Type 3 glyph
  programs, and missing or stream-masquerading named Font, XObject, ExtGState,
  or marked-content Property resources fail repeatedly through the stable
  public diagnostic before backend parsing. The subtype-repair case observes
  the live low-level value before and after the rejected query and verifies a
  subsequent target rewrite retains the original value. A standard-font
  fixture with no declared Encoding remains `MISSING` instead of acquiring
  confidence from embedded, substituted, or system-font data.
- A low-limit ExtGState fixture retains its validated optional `Font` setting
  while a 4,096-entry line-dash array is ignored through a detached one-key
  view, proving that extraction does not delegate unrelated graphics-state
  arrays to the backend or mutate the source.
- `PublicApiLeakageIT` reflectively checks all public and protected signatures,
  including the query, result, limits, mapping, diagnostic, geometry, marked-
  content, and logical-structure types, for backend leakage. `JarContractIT`
  verifies the stable module name, Java 8 class-file version, notices, and
  absence of bundled PDFBox classes.
- The repository-owned acceptance command creates one multi-page page-text
  artifact and one tagged-structure artifact only through public workflows,
  runs a same-Session public Query probe before publication, and submits the
  unmodified products to pinned qpdf 12.4.0. Its repeat-run regression compares
  PDF bytes under the repository's ID-neutral trailer policy plus exact
  evidence metadata, hashes, invocations, exit codes, and raw findings.

## Execution record — 2026-09-02

- Fixed review point:
  `daf244af801516b6b595498819995a3797d61c4c`.
- The final focused T13 run passes 58 tests: 44 public workflow tests, 9 CMap
  preflight tests, and 5 font-metric preflight tests. The complete Maven
  reactor passes all ten modules, including 253 Document Engine unit tests,
  both Document Engine contract integration tests, 20 acceptance tests, 6
  inventory tests, and 11 release tests.
- `scripts/verify-jdk-matrix.sh` passes the complete ten-module reactor on JDK
  8, 11, 17, and 21 with the same final T13 test counts. Inventory generation
  and checking report 7 capabilities, 12 facade surfaces, and 6 exclusions,
  with generated documentation current; `git diff --check` also passes.
- The canonical acceptance command ran twice into separately created clean
  temporary directories. Both runs report the T13 qpdf 12.4.0 syntax chain as
  `pass`; their syntax records and raw qpdf findings are byte-for-byte equal
  to each other and to the checked-in records. Under the documented
  ID-neutral trailer policy, both runs reproduce page-text hash
  `f6b0752b4573347c3c5d371ec2bc5406f29dff1e4c158541b0f1ba4881414a06`,
  tagged-structure hash
  `96c26835680700b5a49ff0d62887fec6c022e034334f97a0be88a7b604e949ea`,
  and product-set hash
  `95336a44f958a36a6a0e760e1a9588b0f036976d5dc3f3be6ad793d6ec6eb7c5`.
- Parallel Standards and Spec reviews compared the final implementation and
  evidence with repository policy and the fixed-point ticket. Their
  actionable findings were addressed and rechecked; both final verdicts were
  `CLEAN`.

Version 1 preserves content execution order rather than inferred visual or
semantic reading order. It does not synthesize whitespace, line breaks, OCR,
layout, or font-program mappings. Composite-font encodings are limited to
`Identity-H` and `Identity-V`, and Type 3 fonts are unsupported;
marked content inside Forms, Form-stream MCRs, OBJR structure children, and
PDF 2.0 structure namespaces fail safely instead of being approximated.
Query-specific bounds do not replace T20's comprehensive memory, time, image,
decompression, and concurrency policy or T21's separate opt-in Worker
isolation.

This record is implementation evidence, not independent Acceptance Evidence.
The separate T13 qpdf record supplies a passing syntax chain only after the
canonical evidence command produces it; qpdf syntax success is not a PDF
standards-conformance claim. Mandatory standards, semantic, and visual
Acceptance Evidence remain absent. The T09 Dependency Gate is open because
that prerequisite remains `experimental`, and T06 remains a promotion gate.
T13 therefore remains `experimental`, with no compatibility or certified-
platform claim.
