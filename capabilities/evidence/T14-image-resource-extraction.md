# T14 image and resource extraction evidence

Status: `experimental`

Capability: `document.images-resources.extract`

Acceptance Profile: `T14-image-resource-extraction`

Release train: `0.1.0-SNAPSHOT`

T14 exposes one immutable, backend-neutral, version-1 Document Query for a
bounded page and nested-Form Resource Inventory, detached image metadata and
explicitly selected image bytes, and font identity, embedding, subset, and
Page Usage. PDFBox remains a private implementation detail.

## Implementation evidence

- `ImageResourceExtractionWorkflowTest` drives every observation through
  `DocumentWorkflow.execute`. Project-authored fixtures cover inherited page
  Resources, nested Forms, category/name and depth-first order, same-Session
  repeated queries, reopen determinism, indirect-resource deduplication,
  direct-resource declaration records without fabricated identity, shared
  declaration paths and Page Usage, all declared resource categories, unknown
  categories and XObject subtypes, omitted null categories and members beside
  ordered non-null siblings, repeated acyclic graphs, and stable Session-scoped
  Object References.
- Image fixtures expose exact dimensions, bit depth, proven component counts,
  built-in and named color families, unsupported and malformed color
  classification, exact filter sequence and effective Flate predictor
  parameters including PDF-null default values, Image Masks, explicit image
  masks, subsidiary soft masks, JPX
  embedded and preblended soft-mask states, color-key ranges, and stable
  relationships to the same detached image records. External-file
  streams are inventoried without resolving their location. Public fixtures
  cover supported CalRGB and malformed calibrated dictionaries; malformed ICC
  stream, Alternate, and Range shapes plus an arbitrary payload whose bounded
  wrapper metadata remains classified `UNSUPPORTED`; supported and malformed
  string-valued Indexed lookups plus an unsupported stream lookup; malformed
  Separation and DeviceN tint functions; a bounded but unsupported Separation
  function; and an unsupported unknown family. Device spaces are supported as
  names and exact one-element arrays while wrong array arity is malformed.
  Public fixtures reject special color spaces in device/CIE-only tint-alternate
  positions, retain a valid Indexed/Separation base as `UNSUPPORTED`, and
  reject invalid DeviceN attribute subtypes.
  Pattern color spaces on images are classified as malformed. Recognized but
  unsupported DCT data keeps encoded availability while decoded availability
  is explicitly unsupported, and public fixtures enforce DCT, CCITT, JBIG2,
  RunLength, and predictor sample geometry. Internal and external empty filter
  arrays remain valid unfiltered sequences, and
  fixtures prove that parameter container shape follows filter count even when
  a single filter is written with array syntax.
- Explicit encoded and decoded selection is tested independently and together.
  Selected bytes are exact, defensively copied, and usable after the workflow
  callback and Session closure; unselected, unsupported, and external bytes
  are absent. A preceding public `DocumentPatch` is visible to the query in the
  same Session, while query-only workflows have no Publication Receipts and
  leave Source bytes unchanged. The repository acceptance command separately
  proves that an explicitly declared Target is committed after its public T14
  probe succeeds.
- Unfiltered, ASCIIHex, ASCII85, RunLength, and Flate paths use project-owned
  strict syntax validation before the private backend decoder. Truncated or
  malformed known inputs, invalid terminators, bad Flate checksums or trailing
  data, malformed predictor rows, unknown filters, invalid dimensions, masks,
  fonts, resource categories, and cycles fail atomically through one fixed safe
  diagnostic. Soft-mask fixtures cover `/None`, required bit depth, absent
  nested masks, valid and malformed Decode and Interpolate entries, and Matte
  component count, declared/default color-component ranges, and owner-dimension
  checks. JPX fixtures cover every `SMaskInData` state, malformed kinds and
  ranges, the nonzero subsidiary-mask conflict, and the ignored non-JPX entry.
  External-file fixtures cover strings, file-spec dictionaries, direct and
  indirect null, regular-filter fallback, empty external filter arrays, and
  malformed value kinds. Form, mask, and malformed soft-mask cases are queried
  repeatedly to prove stable termination and no Source mutation.
- Every mandatory `ResourceExtractionLimits` field accepts its exact boundary
  and fails on the first excess with `EXTRACTION_LIMIT_EXCEEDED`, capability
  `document.images-resources.extract`, and one fixed safe diagnostic. Tests
  independently cover pages, page-tree nodes, traversed resource values,
  nested-Form depth, decoded pixels, every filter-stage decompressed byte, and
  aggregate encoded-plus-decoded returned bytes. Decoded-size multiplication
  is overflow-checked and capacity is rejected before stream materialization.
- Font fixtures cover supported simple, composite, and Type 3 classifications,
  unembedded and embedded programs, exact BaseFont names, six-letter subset
  prefixes, indirect identity, declarations, shared Page Usage, and malformed
  descriptors and glyph programs. A repeated-Form DAG proves exact-boundary
  per-declaration Type 3 CharProcs accounting while retaining one indirect font
  record. Public fixtures reject both CIDFont subtypes
  as direct Font resources while retaining them as validated Type 0
  descendants. The query never parses font programs or infers glyph use.
- `PublicApiLeakageIT` reflectively checks all public and protected signatures,
  including the query, limits, result, declaration, Image, Font, filter, color,
  mask, byte, and identity types, for backend leakage. `JarContractIT` verifies
  the stable module name, Java 8 class-file version, notices, and absence of
  bundled PDFBox classes.
- The repository-owned acceptance command creates one filtered named-color
  image/subset-font artifact and one nested-Form/soft-mask artifact through
  public workflows, evaluates each finished in-Session state through
  `ExtractImagesAndResources`, requires committed Target receipts, and submits
  the unmodified products to pinned qpdf 12.4.0. Its repeat-run regression
  compares PDF bytes under the repository's ID-neutral trailer policy plus
  exact evidence metadata, hashes, invocations, exit codes, and raw findings.

## Verification and reproducibility receipt

- Review and validation use pre-implementation fixed point
  `f393db2b158134fef7a31b20108af00551a87977`.
- The focused `ImageResourceExtractionWorkflowTest` suite passes 27 tests.
  The complete document module passes 280 unit tests plus its two contract
  integration tests, including public/protected API leakage and jar checks.
- `./mvnw -B -ntp verify` passes the complete reactor: the provider contract
  runs 7 tests, acceptance runs 20, conversion runs 8 plus 2 contract tests,
  preview-facade runs 3 plus 2 contract tests, inventory runs 6, and release
  tooling runs 11. `./scripts/verify-jdk-matrix.sh` repeats the complete
  successful reactor on JDK 8, 11, 17, and 21.
- Inventory generation, validation, and current-view checks pass with 8
  capabilities, 12 facade surfaces, and 7 explicit exclusions.
- Two runs from separate clean temporary directories reproduce the T14 syntax
  record and qpdf findings byte-for-byte. Their ID-neutral products also match
  the checked artifacts: image/font SHA-256
  `571f3b36e25b0ff67210b6f5fa3fb61c88d3beeb564bdcf2fb8f51327236d852`,
  Form/mask SHA-256
  `3765eb95a006784aa57702dcd623e218385aff0bde2af99c9988c6a02ac9a45d`,
  and ordered input-set SHA-256
  `5114983f25709d75f341b5ae72c963dacdaae58844054ce18ebe97ddf22f5171`.

Version 1 reports declaration reachability as Page Usage; it does not infer
whether a content stream executes a resource. Decoded byte access supports only
unfiltered, ASCIIHex, ASCII85, RunLength, and Flate image streams. Other known
filters remain metadata-visible and unavailable for decoding, and external
stream locations are never opened. Query-specific bounds do not replace T20's
comprehensive memory, time, decompression, and concurrency policy or T21's
separate opt-in Worker isolation.

This record is implementation evidence, not independent Acceptance Evidence.
The separate T14 qpdf record supplies a passing syntax chain only; qpdf syntax
success is not a PDF standards-conformance claim. Mandatory standards,
semantic, and visual Acceptance Evidence remain absent. The T09 Dependency
Gate is open because that prerequisite remains `experimental`, and T06 remains
a promotion gate. T14 therefore remains `experimental`, with no compatibility
or certified-platform claim.
