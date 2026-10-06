# T20 trusted in-process hostile-input policy evidence

Status: `compatible`

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

The original T20 implementation record declared no independent Acceptance
Evidence and recorded the then-open T03/T09 gates. Those historical statements
describe that implementation milestone; T03 and T09 now have their own
certification records. T06 was satisfied for this slice by the complete initial qualification.

The #81 [certification contract](../../docs/t20-certification.md) and
[closed inventory](../profiles/T20-hostile-input/coverage.json) add fixed
independently authored operands, complete public Native/Facade observations
and a separate resource-enforcement contract chain. Successful resource-free
PDF outcomes explicitly reuse the qualified T03 syntax, standards, semantic
and visual rules and controls. The collector checks original artifacts and
findings against fresh live replay, retaining exact hashes and actual
environment/producer identities. Current certification is established only
by the artifact-bound Foundation evidence index; implementation tests or a
plan do not establish certification.

The owned-memory model covers declared Folio byte lifetimes, not all JVM,
caller, PDFBox, ImageIO, native, or operating-system allocations. Cancellation
and time enforcement are cooperative and cannot terminate arbitrary callback
or backend code. Malformed inputs retain the owning operation's stable format
failure when no resource limit is exhausted. Hostile multi-tenant use must
select T21's separate Hardened Worker Profile for its documented process
isolation, hard Worker termination, descendant-process denial, and network
denial.

## Independent certification

Issue #81 qualified all four actual Ubuntu 24.04 / Linux x86-64 JDK
8/11/17/21 tuples, Native `IN_PROCESS`, with corresponding existing Facade
`IN_PROCESS` operations. Every tuple retains 75 fixed resource experiments,
133 public/artifact executions with zero failures, ignored tests or assumption
skips, and eight successfully published blank outcomes. Collection repeats
the public suite and recorder in the exact image and compares original
artifacts and findings with the fresh observations.

The separate [contract chain](T81-limits-contract.md) owns enforcement.
Affected PDF outcomes retain [syntax](T81-limits-syntax.md),
[standards](T81-limits-standards.md), [semantic](T81-limits-semantic.md)
and [visual](T81-limits-visual.md) chains. These initial qualification records
are historical evidence; the [Foundation index](../foundation-evidence.yaml)
exclusively defines current candidate identities and certification. Promotion
and any other bound-input change require restaging and fresh affected evidence.
The final ordered refresh and delivery receipt are retained in
[T81-delivery](T81-delivery/receipt.md).

The approved Native-only resource-policy mapping decision is retained.
Windows x86-64 and macOS x86-64/arm64 remain explicitly uncertified and are not
Foundation 0.1.0 blockers. The guarantee remains cooperative modeled usage,
with no JVM heap, process RSS, arbitrary termination or sandbox claim.
