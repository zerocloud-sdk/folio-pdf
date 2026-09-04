# T12 annotations and document Actions evidence

Status: `experimental`

Capability: `document.annotations-actions.manage`

Acceptance Profile: `T12-annotations-document-actions`

Release train: `0.1.0-SNAPSHOT`

T12 exposes immutable backend-neutral version-1 annotations, local navigation
targets, and inert GoTo Actions through `DocumentWorkflow.execute`. It manages
Text, Stamp, Highlight, FileAttachment, standalone Widget, and Link
annotations, catalog open Actions, page open and close Actions, resource-free
normal appearances, and non-Widget annotation flattening. It never executes
an Action and does not expose PDFBox or another backend type.

## Implementation evidence

- `AnnotationWorkflowTest` drives every behavior through
  `DocumentWorkflow.execute`. Reopen probes cover all six annotation types,
  geometry, flags, contents, appearance bytes, highlight quads and color,
  attachment relationship and content, direct and Action Link activation,
  catalog and page Action bindings, atomic update, move, removal, and
  flattening. Publication Receipts and public Queries provide every output
  observation.
- Public fixtures prove that direct page targets follow page identity through
  move and copy, that copied annotations receive deterministic `-N`
  identifiers, that merge offsets direct page targets and follows T11 named-
  destination collision renames, and that split filters or rewrites bindings
  by target survival. Mixed annotation arrays prove that managed entries are
  retargeted in either position while proven-safe legacy Text entries remain
  preserved, copied legacy identifiers receive the same deterministic `-N`
  suffix without changing their originals, and colliding unrenamable legacy
  identifiers reject merge before target mutation. Target-orphaning removal
  fails with
  `DESTINATION_CONFLICT` before mutation. Removing a named destination also
  fails atomically with that code while a managed Link or Action refers to it.
- URI, JavaScript, Launch, and chained Action dictionaries are treated as
  inert input. Filesystem markers, an executable-process sentinel, and a
  loopback socket prove that reading and rewriting neither evaluates a script,
  launches a process, nor performs network access. Unsupported graphs remain
  structurally unchanged during an annotation-only rewrite that need not
  interpret them; bounded Action Queries and page mutations that would need
  their semantics reject them safely with `QUERY_FAILED` or
  `PRESERVATION_UNSUPPORTED`.
- Version-1 normal appearances are Form XObjects with an identity matrix,
  caller bounding box, empty Resources dictionary, at most 1 MiB of decoded
  content, and a resource-free graphics-operator allowlist. Commands reject
  malformed, unbalanced, text, image, external-object, resource-dependent, or
  oversized programs with `ANNOTATION_INVALID`; Queries reject unproven
  appearance graphs with `QUERY_FAILED`.
- `FlattenAnnotations` validates every requested non-Widget annotation and
  its normal appearance before changing a page, then isolates all pre-existing
  page content with dedicated `q` and `Q` streams and adds the Form and its
  geometry transform before removing the annotation. Missing identifiers,
  Widgets, missing or malformed appearances, and unsafe page resource graphs
  produce stable safe failures without partial mutation.
- Annotation Queries require caller limits for count, decoded appearance
  bytes, and decoded attachment bytes. Command validation covers annotation
  identifiers, rectangles, page ranges, named targets, color components,
  quadrilaterals, attachment metadata, and Action bindings before mutation.
  Public malformed-graph probes show that annotation reads, annotation
  removals, and page copies reject a missing required rectangle with stable,
  capability-scoped failures before changing the document. Scalar annotation
  entries likewise produce the documented exact update and flattening command
  failures without mutation.
  Complete managed-graph command and page-operation passes additionally share
  fixed document-wide 8 MiB decoded appearance and 8 MiB decoded attachment
  bounds and fail closed when either is exceeded.
- `PublicApiLeakageIT` reflectively checks every public and protected
  signature, including all T12 Commands, Queries, annotations, Actions,
  appearances, geometry, and color values, for PDFBox types. `JarContractIT`
  verifies the stable module name, Java 8 class-file version, notices, and
  absence of bundled PDFBox classes.
- The repository-owned acceptance command creates representative T12 products
  solely through the public workflow seam. It adds every supported annotation
  type and Action binding, flattens a Stamp annotation, copies pages, and
  publishes two exact split products. New split products without an inherited
  trailer identifier receive a streaming content-derived identifier from a
  fixed-placeholder first serialization, and the acceptance regression proves
  that independent runs reproduce both exact PDF byte sequences and their
  evidence metadata. Pinned qpdf 12.4.0 checks both products; the PDFs, hashes,
  invocations, exit codes, and raw findings accompany the separate syntax
  record.

## Execution record — 2026-09-01

- Fixed review point:
  `3fe239e8d7082abcc088db4865bcf16b57b1f396`.
- Test-first public-workflow cycles added and hardened each version-1 value,
  Command, Query, failure, Action safety rule, appearance constraint,
  flattening path, and T10/T11 integration behavior. The focused public-
  consumer regression run passed all 33 `AnnotationWorkflowTest`, 55
  `DocumentMetadataWorkflowTest`, 52 `PageManipulationWorkflowTest`, and 21
  `PdfValueWorkflowTest` cases.
- The acceptance-command unit suite passed all 20 cases, including passing,
  failing, unavailable, unpinned, and undocumented qpdf outcomes for T12.
- The repository acceptance command passed twice and reproduced exact split-
  product SHA-256 values
  `20b875eb01ec2b3560d70c166a0a5570bd939ebf6224d9d81481fa6fef673e66`
  and
  `52ff3543db6162d4b2e12462233bc1b7a1460e7d6f04f8e350fa4b944e87731a`;
  pinned qpdf 12.4.0 returned exit code 0 for both products.
- The final ten-module `./mvnw -B -ntp verify` reactor passed, including all
  195 Document Engine unit tests and both Document Engine contract tests.
  `./scripts/verify-jdk-matrix.sh` then passed the complete reactor on JDK 8,
  11, 17, and 21.

T12 operates in `REWRITE` and, for unsigned Sources, T15-classified
`INCREMENTAL` workflows. Version 1 allows only local GoTo
Action dictionaries with a direct page or existing named-destination target,
bound to catalog open, page open or close, or Link activation. All Actions are
inert. Widget support is annotation-only and does not create or flatten an
AcroForm field. Resource-bearing appearance programs, form Actions, form
flattening, extraction, encryption, incremental publication, signatures,
comprehensive hostile-input enforcement, and Worker codecs remain outside
T12 itself; T20 and T21 now compose those latter policy and transport layers
with the T12 contract.

This record is implementation evidence, not independent Acceptance Evidence.
The separate T12 qpdf record supplies a passing syntax chain only; qpdf syntax
success is not a PDF standards-conformance claim. Standards, semantic, and
visual Acceptance Evidence remain absent. The value-inspection, page, and
metadata Dependency Gates are open because those prerequisites remain
`experimental`. T12 therefore remains `experimental`, with T06 still required
before a compatibility or certified-platform claim.
