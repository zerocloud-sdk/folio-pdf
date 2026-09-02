# T10 page manipulation, merge, and split evidence

Status: `experimental`

Capability: `document.page.manipulate-merge-split`

Acceptance Profile: `T10-page-manipulation-merge-split`

Release train: `0.1.0-SNAPSHOT`

T10 exposes library-owned version-1 Commands for blank-page insertion, range
removal, range movement, range copying, ordered named-Source merge, and exact
named-Target range split. `PageRange` uses inclusive one-based page numbers.
Move destinations address the sequence after removing the selected range;
copy insertion positions address the original sequence. The version-1 split
command maps every declared publication Target exactly once.

## Implementation evidence

- `PageManipulationWorkflowTest` drives every assertion through
  `DocumentWorkflow.execute`. Reopen tracers prove deterministic insert,
  remove, move, copy, merge, and split page order without importing or
  inspecting PDFBox types.
- A project-authored nested-page-tree fixture proves that safe page movement,
  copying, merge, and split products retain inherited media and crop boxes,
  rotation, resources, basic Text annotations, their retargeted page reference,
  and content after publication and reopen.
- Public-only regression workflows prove that `PageObjectReference` shares the
  same Session identity as the matching indirect page-tree reference while
  distinct indirect objects containing the same interned scalar retain
  distinct identities. Missing-type and direct-node fixtures prove that the
  query rejects malformed input page trees with `QUERY_FAILED`, without
  constructing a backend page wrapper, manufacturing an indirect identity, or
  repairing the input. Parent- and count-inconsistent fixtures prove the raw
  query traversal also rejects noncanonical page-tree relationships without
  repair. Every node in a library-created, imported, or split product page tree
  becomes indirect immediately, so page references remain indirect,
  addressable, and composable before first publication. The tests also
  construct links, widgets, catalog name/form/outline/tag/thread entries,
  unknown page-tree or trailer data, mismatched page-tree parents and counts,
  repeated page-tree nodes, nonempty document information, cross-page
  separation information, annotation reply relationships, tagged-page
  references, page actions, thread beads, malformed inherited boxes, rotation,
  or resources, resource graphs that reach page or page-tree structures,
  nested content arrays, external-file content streams,
  non-Flate filters, filter arrays, decode parameters, malformed, trailing-
  data, or preset-dictionary Flate payloads, and content streams with custom
  metadata, then prove that T10 rejects each unproven structure before page
  mutation with the stable
  `PRESERVATION_UNSUPPORTED` failure. A strict Flate fixture proves that a
  parameter-free, valid zlib content stream remains supported and retains its
  decoded bytes through copy, split, publication, and reopen.
- Ordered merge consumes multiple named non-primary Sources in command order.
  Public reopen-and-rewrite probes patch an imported destination content stream
  and prove the original Source content graph remains unchanged. Additional
  caller-owned Source streams and channels remain open, and their unchecked
  runtime failures propagate unchanged.
- Split products are staged and validated before publication, reopen
  independently, and map one staged product to each ordered named Target.
  A newly created split product without a trailer identifier is first staged
  with a fixed placeholder and then receives a streaming content-derived
  identifier; existing identifiers and non-split publication are unchanged.
  Rewriting one product's imported content stream leaves the sibling product
  and original Source unchanged. A controlled publication failure proves
  `COMMITTED`, `FAILED`, and `NOT_ATTEMPTED` receipts for earlier, failing, and
  later Targets. A successful split is terminal for later Document Commands, so
  the visible Session state cannot diverge from the products selected for
  publication. The workflow reacquires and quietly closes session-owned split
  products on exceptional callback exits so cleanup cannot replace the caller's
  failure.
- Invalid page ranges and positions, invalid or duplicate merge Source names,
  and missing, extra, or duplicate split Target declarations fail with stable
  T10 failure codes before mutation or publication.
- `PublicApiLeakageIT` reflectively checks every public and protected signature
  for PDFBox types. `JarContractIT` verifies the stable module name, Java 8
  class-file version, notices, and absence of bundled PDFBox classes.
- `./scripts/inventory check`, `./mvnw -B -ntp verify`, and
  `./scripts/verify-jdk-matrix.sh` are the repository gates for inventory
  drift, full verification, and JDK 8/11/17/21 execution.
- The repository-owned acceptance command creates representative T10 products
  by exercising all six Commands through `DocumentWorkflow.execute`. Pinned
  qpdf 12.4.0 reports no syntax or stream-encoding errors for either product;
  the exact PDFs, hashes, invocations, exit codes, and raw findings accompany
  the independent syntax record.

## Execution record — 2026-08-11

- Fixed review point: `2ed08f71782419381784cea3019f8b934074be70`.
- TDD remediation reproduced unequal and transiently direct page identities,
  coalesced indirect scalar identities, unsafe cross-page and annotation
  relationships, unpreserved content-stream metadata or structure, direct page-
  tree nodes, post-split command divergence, caller runtime misclassification,
  backend-default normalization, silent preservation-sensitive mutation, and
  silent replacement of undecodable content, and backend normalization of
  malformed filter metadata or payloads, noncanonical query traversal, and
  excluded-page reachability through resource graphs. The focused public-
  consumer suite then passed all 52
  `PageManipulationWorkflowTest` cases after resolving page queries through the
  raw page-tree without constructing a mutating backend page wrapper, making
  every workflow-owned page-tree node indirect immediately, keeping
  dereferenced values out of the identity cache, enforcing terminal split
  ordering, propagating caller failures, and adding conservative preserve-or-
  reject preflight checks.
- `./scripts/inventory check` passed with four capabilities, twelve facade
  surfaces, and three explicit exclusions. `./mvnw -B -ntp verify` passed the
  full reactor; the Document Engine ran 107 unit tests and two contract
  integration tests, and the Acceptance Evidence module ran 11 tests.
- `./scripts/verify-jdk-matrix.sh` passed the complete reactor on JDK 8, 11,
  17, and 21.
- `./scripts/acceptance capabilities/evidence` ran the provisioned official
  qpdf 12.4.0 binary. Both products returned exit code 0: front SHA-256
  `995380335cb8be49176e379d393533c42d1ac14ffc13d9c75c5c29f7d597c0b5`
  and back SHA-256
  `4f462d46b3ee4e4e87f1056019499c4b95c9446e9a969e9e89f69ef562fe2245`.
- Independent Standards and Spec review cycles identified unsafe structure
  handling (including page-tree relationships and document information), query-
  induced page repair, direct and transient page identity, content-stream
  metadata loss, resource-graph page reachability, post-split command
  divergence, split page identity, exceptional split cleanup, successful-close
  precedence, and unchecked backend-failure normalization. Each finding was
  reproduced where a public observation exists and remediated before the final
  independent review gate.

T10 operates in `REWRITE` and, for unsigned primary Sources, T15-classified
`INCREMENTAL` workflows. It preserves the tested safe
page-owned and inherited semantics. Before any page command, it conservatively
rejects nonstructural trailer data, nonempty document information, catalog and
page-tree-node extensions outside the proven basic structure, inconsistent
parent links, descendant counts, cycles, repeated nodes or any direct input
page-tree node, missing effective media boxes, malformed box, rotation, or
resource entries, resource graphs that reach page or page-tree structures,
nested content arrays, external-file content streams,
filtered content streams unless they use one parameter-free `FlateDecode` or
`Fl` filter with a strictly valid zlib payload, content streams with non-engine
metadata, unproven indirect page extensions, separation information, and page
structures that PDFBox copy/split mechanics may rewrite or discard:
unsupported annotation relationships or Action graphs, tagged-page
references, and page-thread beads. The legacy proven annotation subset remains
basic Text annotation data attached to its owning page. T12 now validates,
preserves, and retargets its managed Text, Stamp, Highlight, FileAttachment,
standalone Widget, and Link annotations plus local GoTo Action bindings, even
when managed and proven-safe legacy Text entries coexist in either order.
Metadata, outline, destination, and attachment management belongs to T11;
forms and tagged-structure management remain downstream slices. T10 makes no
source-byte-layout claim beyond the content-derived identifier of a newly
created split product, and no cross-Session object-identity claim. T15 admits
the primary-document page commands for unsigned incremental Sources but rejects
`SplitDocument`; signed Sources authorize none of these commands. The
comprehensive hostile-input policy remains T20, and the Hardened Worker
Profile and codecs remain T21.

This record is implementation evidence, not independent Acceptance Evidence.
The separate T10 qpdf record supplies a passing syntax chain only; qpdf syntax
success is not a PDF standards-conformance claim. Standards, semantic, and
visual Acceptance Evidence remain absent. The blank-document Dependency Gate
is also open because that prerequisite remains `experimental`. T10 therefore
remains `experimental`, with T06 still required before a compatibility or
certified-platform claim.
