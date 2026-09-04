# T21 Hardened Worker implementation evidence

Status: `experimental`

Capability: `document.hardened-worker`

Acceptance Profile: `T21-hardened-worker`

Release train: `0.1.0-SNAPSHOT`

T21 adds an explicitly selected, authenticated local Worker boundary beneath
the existing `DocumentWorkflow.execute(request, work)` callback contract. The
callback remains in the caller process and operates on a proxy Session; the
closed set of library-owned Commands, Queries, PDF Values, and results accepted
at fixed point `5d5cb89f8d25f7ad55a77560f2402a5d93d90065` is transported to a
separate process. The parent retains Provider execution, actual publication
Targets, progress callbacks, and final publication authority.

## Implementation evidence

- `HardenedWorkerWorkflowTest` selects `HARDENED_WORKER` only through the
  public request and workflow API. It proves both a memory-negotiated atomic
  page-structure batch and an authenticated ordered batch declaration with
  Worker-requested indexed preflights, details, and items; one logical batch
  completion; deferred per-item encoding; Query barriers; optional embedded-
  file and descriptor-only output-policy transport; on-demand password-output
  and Source-credential transport; aggregate transport owned-memory
  enforcement; result and profile reporting; parent-owned publication;
  incremental output; Document Info, text/structure, and image/resource result
  transport; bounded lazy `InspectObject` access and expiry; optional TIFF
  capability discovery on the minimal Worker class path; and safe rejection of
  arbitrary caller-defined Commands and Queries.
- `WorkflowExecutionProfileContractTest` applies one parameterized public
  contract to `IN_PROCESS` and `HARDENED_WORKER`. Both profiles preserve result
  identity in the caller, ordering, Session expiry and thread confinement,
  callback exception identity, Source and Target ownership, REWRITE and
  INCREMENTAL Save Modes, successful and partial Publication Receipts,
  authenticated internal progress order and listener failure identity,
  parent-Clock deadline checks after staging, pre-start and active
  cancellation, stable safe command/query/resource failures, lazy reference
  fonts, exact aggregate font-source first excess, lossless invalid-surrogate
  validation, recoverable invalid lazy-view indexes, caller-thread-only
  environment Clock observation, use of a same-Session lazy value by a later
  Command, post-publication cleanup receipts, checked-primary cleanup
  suppression, and target preservation.
  Focused ordering regressions prove protected-document and signed-Widget
  failures before later oversized values, Canvas semantic and preservation
  failures before later image transport, exact once-only preservation decoding
  for both Canvas Command versions, lowered workflow nesting limits for nested
  transparency, and terminal resource-failure precedence after the callback
  catches the immediate failure.
- `HardenedWorkerIsolationTest` observes a caller callback and Worker with
  distinct runtime process identities. Real Worker probes reject outbound and
  listening INET sockets, Unix-domain connects and listeners, and descendant
  execution. Concurrent Workers place real, distinct marker data in their live
  private roots; each Worker is denied both read and write access to the
  other's marker and neither value changes. The test also verifies owner-only
  root permissions, random names unrelated to input names, sequential reuse
  without residue, cleanup, hard elapsed-time termination, preservation of an
  existing Target, exact project/dependency artifact acceptance, and rejection
  of mixed, extra, multi-release, delimiter-bearing, or wildcard-bearing
  classpath inputs and modified dependency JARs.
- `WorkerProtocolBoundaryTest` exercises the real framed endpoint and Worker
  process only. The inclusive configured payload boundary succeeds and the
  first excess is rejected from the header before payload allocation.
  Exact-length memory controls, receive grants, child reserve/release reports,
  synchronization at atomic and indexed wait states, and progress
  acknowledgement are enforced across the authenticated process boundary.
  Negative, overflowing, truncated, trailing, unknown-version, unknown-opcode,
  tampered, replayed, missing-key, and wrong-key inputs fail without
  publication. Java Serialization is rejected, and valid allowlisted Command
  opcodes reject arbitrary class, reflection-target, script, and URI strings as
  operation selectors. Every hostile case preserves the sentinel product.
- `WorkerCodecContractTest` separately exercises the internal explicit codecs,
  not the protocol-boundary claim. It proves a fixed eight-byte allowlisted
  finished control and its exact first-excess boundary, malformed scalar
  rejection, indexed later-item message failure, and iterative nested PDF
  Value, outline, and logical-structure round trips at the full version-1
  nesting ceiling.
- Version-1 frames contain a magic, version, opcode, monotonic sequence,
  payload length, payload, and HMAC-SHA-256 tag. A fresh 256-bit key is written
  through the inherited protocol stream and never placed in arguments, the
  environment, paths, files, results, failures, or logs. Protocol, credential,
  font, and transient transport arrays are cleared at their owned lifecycle
  boundaries.
- One synchronized parent ledger accounts its live allocations and all child
  allocations reported through authenticated reserve/release controls, without
  a static parent/child split. The parent grants the exact allocation before
  every nonempty application frame and, after every quiescent response,
  completes an empty synchronization round trip before its next allocation.
  Atomic eligibility includes the larger of the parent's growable encoder plus
  retained-copy peak and simultaneous parent/child payload ownership plus the
  retained/received completion control. Exact and first-excess regressions for
  both dominating shapes force staged selection before that peak can poison the
  transaction. Active-context encoded and received application payloads,
  decoded String/byte copies,
  conservatively modeled collection capacity, credential copies, and normal
  failure envelopes reserve T20 modeled owned memory before allocation and
  release it at their actual lifecycle boundary. Authenticated memory reserve,
  release, and grant controls are the sole active control-plane exception:
  their fixed 12-byte payloads remain outside the ledger to avoid recursive
  accounting while retaining message-bound and authentication checks.
  Pre-context and post-context
  failures use a fixed eight-byte allowlisted descriptor because no transaction
  ledger is live; the post-context finished control is likewise fixed at eight
  bytes and contains only a version and allowlisted capability token. Counts
  are checked against remaining wire
  bytes and charged before capacity-bearing allocation. The child bootstrap
  payload and decoded copies are capped by the parent-supplied owned-memory
  limit before the child resource context is available, and initialization
  collection capacity remains retained after `READY`. Direct regressions prove
  aggregate exact/first-excess behavior, returned-request decode leases through
  their consumer boundary, credential destruction before reservation release,
  resolver cleanup before context close, the collection-capacity exact
  boundary, failed-recovery precedence that keeps the already-observed failure
  authoritative while making the transaction terminal, that a closed context
  reports only the bounded fixed failure control, and that a context already
  poisoned by memory exhaustion can still report its authenticated stable cause
  through emergency use of that codec.
- The Linux launcher requires `/usr/bin/prlimit`, clears the environment, and
  sets Worker heap, direct-memory, stack, CPU-time, and descriptor ceilings.
  The Worker security boundary denies INET and Unix-domain network use,
  descendant execution, filesystem links, and access outside its transaction
  and runtime roots. Caller-thread checkpoints retain the original environment
  Clock and deadline semantics; a monotonic parent watchdog supplies the hard
  Worker-lifetime ceiling and forcibly ends the Worker before cleanup.
- The primary Source is copied before launch under the T20 byte, memory,
  temporary-storage, cancellation, and time accounting rules into an owner-
  restricted transaction root. Other named Sources are requested, copied, and
  classified one at a time in declaration order during child initialization,
  so an earlier failure leaves later one-shot Sources unopened. Reference Font
  Set entries and explicit Font Sources instead remain
  parent-owned and are opened only at the selecting command boundary, where the
  same source-count, aggregate-byte, first-excess, and one-shot reuse rules are
  applied. The Command carries opaque source identifiers; each program is
  requested in declaration order and returned in its own bounded frame. Worker
  products are random internal
  handles and remain cumulatively temporary-storage-accounted. Only after an
  authenticated completion and confirmed Worker exit does the parent reuse the
  existing validation/publication contract against the caller's real Targets;
  abnormal Worker loss never authorizes blind publication.
- Public API and artifact tests retain Java 8 source/bytecode compatibility,
  project-owned public signatures, module identity, notices, and an unshaded
  private backend. The Worker class path requires the two digest-pinned exact
  first-party class-name inventories and complete-byte SHA-256 matches for the
  four required dependency JARs plus any installed six-JAR TIFF closure; the
  authorities are recorded in `docs/hardened-worker.md` and
  `DEPENDENCIES.md`. It excludes application and test entries and rejects
  ambiguous classpath syntax before launch. The repository JDK matrix exercises
  JDK 8, 11, 17, and 21. T21 adds no dependency or product module.

## Evidence and status boundary

This record contains implementation evidence, not independent Acceptance
Evidence. The syntax, standards, semantic, and visual chains are absent. The
T03 and T20 compatible-status Dependency Gates remain open while both
capabilities are `experimental`, and T06 remains the promotion gate. T21
therefore remains `experimental`, with no compatible or certified-platform
claim.

The documented implementation envelope is Linux plus `/usr/bin/prlimit` and
JDK 8, 11, 17, or 21. Java permissions and process resource ceilings do not
claim a certified kernel sandbox, container boundary, full RSS/native-memory
limit, physical secure erasure, or resistance to defects in the hosting JVM or
operating system. Capability Providers remain parent-brokered and retain their
own operating and Remote Disclosure Authorization contracts.

T21 deliberately excludes remote Workers, arbitrary extensions, rendering,
idempotent transaction identities, automatic retry, lost-acknowledgement or
uncertain-publication recovery, large-value staging/chunking, and the 5,000-page
or 1-GiB scale certification assigned to T22 and later work.
