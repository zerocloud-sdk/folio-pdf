# T11 metadata, outlines, destinations, and attachments evidence

Status: `experimental`

Capability: `document.metadata.outlines-destinations-attachments`

Acceptance Profile: `T11-metadata-outlines-destinations-attachments`

Release train: `0.1.0-SNAPSHOT`

T11 exposes library-owned version-1 Commands and Queries that read, create,
update, and preserve the document information dictionary, XMP metadata,
outlines, named destinations, page destinations, and embedded files as
backend-neutral values on the `DocumentWorkflow.execute` public seam. Managed
destinations follow page identity across the T10 page reorder, copy, merge,
and split operations: move and copy retarget by page-dictionary identity,
merge offsets source destinations by the appended page base and renames
colliding destination and embedded-file names with a deterministic `-N`
suffix, and split keeps only the destinations that target pages surviving in
each product, rewritten to the product page sequence. A page removal that
would orphan any managed destination fails with the stable
`DESTINATION_CONFLICT` code before mutation.

## Implementation evidence

- `DocumentMetadataWorkflowTest` drives every assertion through
  `DocumentWorkflow.execute`. Reopen tracers prove round-trips of document
  information entries, bounded XMP packets, outline trees with explicit and
  named destinations, name-tree destinations in unsigned byte order, and
  embedded-file summaries and content without importing or inspecting PDFBox
  types.
- Bounded queries reject oversized outline trees, name trees, embedded-file
  lists, decoded packets, and embedded-file payloads with the stable
  `METADATA_LIMIT_EXCEEDED` code; malformed trees, streams, file
  specifications, and packets fail with safe `QUERY_FAILED` or
  `COMMAND_REJECTED` diagnostics that never expose backend detail.
- Destination, outline, and attachment structures are validated before any
  page mutation: a document carrying an unproven catalog name-tree subtree,
  catalog-level destinations, page labels, or open actions is rejected by the
  T10 preflight with `PRESERVATION_UNSUPPORTED` attributed to the page
  capability, while proven managed structures are preserved and retargeted.
- Move, copy, merge, and split reopen probes prove that named and explicit
  destinations follow the same page dictionary rather than the page position,
  that merge keeps the primary XMP packet and fills only missing information
  entries, and that split products carry detached information dictionaries,
  cloned embedded-file streams, filtered outlines, and a product-owned XMP
  stream rather than shared source references.
- `PublicApiLeakageIT` reflectively checks every public and protected
  signature, including every T11 command, query, and value type, for PDFBox
  types. `JarContractIT` verifies the stable module name, Java 8 class-file
  version, notices, and absence of bundled PDFBox classes.
- `./scripts/inventory check`, `./mvnw -B -ntp verify`, and
  `./scripts/verify-jdk-matrix.sh` are the repository gates for inventory
  drift, full verification, and JDK 8/11/17/21 execution.
- The repository-owned acceptance command creates representative T11 products
  by exercising every metadata Command through merge and split on
  `DocumentWorkflow.execute`. Pinned qpdf 12.4.0 reports no syntax or
  stream-encoding errors for either product; the exact PDFs, hashes,
  invocations, exit codes, and raw findings accompany the independent syntax
  record.

## Execution record — 2026-08-14

- Fixed review point: `d1e8490d3654a74db6b0fb0d2cbafa8f7eb36916` (one
  commit after the T10 baseline `1422289`).
- TDD cycles added the metadata command and query surface one behavior at a
  time: failing public-seam tests preceded each engine slice, covering
  document information, XMP packets, named destinations, outlines, embedded
  files, the preservation preflight opening, destination-conflict rejection,
  and merge and split retargeting. The focused public-consumer suite then
  passed all 55 `DocumentMetadataWorkflowTest` cases alongside the existing
  52 `PageManipulationWorkflowTest` cases and the remaining document-engine
  suites.
- `./scripts/acceptance capabilities/evidence` ran the provisioned official
  qpdf 12.4.0 binary. Both T11 products returned exit code 0: front SHA-256
  `d329459dc777034fa6a9d3fe1aeb46e34cf2290aa7fb2ad849fdf884b9cee43b` and back
  SHA-256 `7e750cfd5acb4dfaf3931151c3553387473953994f8b3731ef1257d0eca07d04`.
- Independent Standards and Spec review cycles were completed against the
  fixed review point by clean-context reviewers; every finding with a public
  observation was reproduced and remediated before the final review gate.

T11 operates only in `REWRITE` workflows. Outlines are written all-open with
positive visible-item counts, name trees are written as one flat sorted array
matched by unsigned key-byte order, XMP packets are accepted only when they
are well-formed XML carrying the XMP root marker within a 64 MiB command
bound, embedded-file MIME subtypes are restricted to printable ASCII, and
embedded-file digests are recorded as MD5 parameters with SHA-256 summaries
at the public seam. Managed annotations and local GoTo Actions now integrate
through T12; extraction remains T13 and T14, incremental publication and
signatures remain T15, encryption remains T16, comprehensive
hostile-input policy remains T20, and the Hardened Worker Profile and codecs
remain T21. T11 makes no source-byte-layout or cross-Session object-identity
claim. `INCREMENTAL` remains `SAVE_MODE_UNSUPPORTED`.

This record is implementation evidence, not independent Acceptance Evidence.
The separate T11 qpdf record supplies a passing syntax chain only; qpdf
syntax success is not a PDF standards-conformance claim. Standards, semantic,
and visual Acceptance Evidence remain absent. The value-inspection Dependency
Gate is also open because that prerequisite remains `experimental`. T11
therefore remains `experimental`, with T06 still required before a
compatibility or certified-platform claim.
