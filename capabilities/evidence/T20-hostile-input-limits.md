# T20 trusted in-process hostile-input policy evidence

Status: `experimental`

Capability: `document.hostile-input-limits`

Acceptance Profile: `T20-hostile-input-limits`

Release train: `0.1.0-SNAPSHOT`

T20 applies one finite-default, request-overridable resource policy throughout
the trusted in-process Document Workflow. T21 separately composes that policy
with the opt-in Hardened Worker Profile; T20 itself makes no hard-isolation
claim.

## Implementation evidence

- `HostileInputWorkflowTest` uses only `DocumentWorkflow.execute` and public
  project/JDK values. Project-authored minimal PDFs and deterministic clocks,
  streams, outputs, and latches prove exact boundaries and first excess for
  input bytes, pages, objects, nesting, filter-stage output, decoded pixels,
  accounted owned memory, temporary storage, elapsed time, and shared
  concurrency. Owned-memory checks include exact source- and request-number
  serialization boundaries. It also proves aggregate named-Source and
  repeated-decode accounting, Patch nesting, mid-read cancellation and
  deadline stops, partial-stream receipts, terminal poisoning, private cleanup,
  and safe temporary-root failure without depending on PDFBox identities or
  filenames.
- `WorkflowTransactionContractTest`, `WorkflowResourceOwnershipTest`, and
  `PdfValueWorkflowTest` retain the established Save Mode, Target receipt,
  caller ownership, Session lifetime, and PDF Value behavior under the shared
  policy. The complete document-engine and composition suite remains the
  regression contract for operation-local limits and specific malformed-input
  diagnostics.
- Every Source form is copied through an actual-byte counter to an environment-
  owned private transaction root before parsing. The PDFBox cache is temp-only
  and quota-accounted; filter intermediates, staged products, and target commit
  files consume the same transaction quota and are cleaned on every exit.
- Iterative preflight accounts valid page trees, indirect objects, graph depth,
  supported filter stages, and materializable image dimensions before caller
  work. Later Commands, Queries, Patches, split products, and publication
  continue the same counters. Existing smaller operation-local bounds continue
  to compose by failing first.
- Cooperative checkpoints cover owned input reads, traversals and preflights,
  mutation/query barriers, backend-cache I/O, staging, incremental validation,
  credential copying and password bridges, derived text accumulation, and
  publication.
  Policy exhaustion poisons the transaction even if a callback catches it;
  all stable failures use fixed content-free diagnostics and accurate receipts.
- Public API, artifact, and cross-JDK contracts retain Java 8 signatures and
  bytecode, module identity, notices, and a private unshaded PDFBox backend.

## Evidence and status boundary

No independent Acceptance Evidence chain is claimed for T20. The syntax,
standards, semantic, and visual chains remain absent, and the T03 and T09
Dependency Gates remain open because both prerequisite capabilities are still
`experimental`. T06 remains a promotion gate. T20 therefore remains
`experimental`, with no compatible or certified-platform claim.

The owned-memory model covers declared Folio byte lifetimes, not all JVM,
caller, PDFBox, ImageIO, native, or operating-system allocations. Cancellation
and time enforcement are cooperative and cannot terminate arbitrary callback
or backend code. Malformed inputs retain the owning operation's stable format
failure when no resource limit is exhausted. Hostile multi-tenant use must
select T21's separate Hardened Worker Profile for its documented process
isolation, hard Worker termination, descendant-process denial, and network
denial.
