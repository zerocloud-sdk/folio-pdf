# Trusted in-process hostile-input policy

T20 applies one finite, transaction-wide `WorkflowResourcePolicy` to every
`DocumentWorkflow.execute` call. It protects the trusted in-process profile
against accidental or hostile resource amplification in work owned by Folio
PDF. It is cooperative enforcement, not a security sandbox.

The public capability identifier is `document.hostile-input-limits`. A request
may supply a complete immutable policy with
`WorkflowRequest.Builder.resourcePolicy`. Otherwise the executing
`WorkflowEnvironment` supplies its immutable default. Applications that share
one environment also share its concurrency gate and temporary-storage root.

```java
WorkflowResourcePolicy policy = WorkflowResourcePolicy.builder()
        .maximumInputBytes(64L << 20)
        .maximumPages(1_000)
        .maximumObjects(500_000L)
        .maximumNestingDepth(4_096)
        .maximumDecompressedBytes(256L << 20)
        .maximumDecodedPixels(100_000_000L)
        .maximumOwnedMemoryBytes(128L << 20)
        .maximumTemporaryStorageBytes(1L << 30)
        .maximumElapsedTime(Duration.ofMinutes(2))
        .maximumConcurrentWorkflows(2)
        .build();

WorkflowEnvironment environment = WorkflowEnvironment.builder()
        .defaultResourcePolicy(policy)
        .temporaryDirectory(Paths.get("/service-owned/pdf-work"))
        .build();
```

Every version-1 field is mandatory, finite, and nonnegative. Zero means that
the corresponding resource cannot be consumed. Numeric and elapsed-time
boundaries are inclusive: exact use succeeds and the first excess fails.
Concurrency is an admission ceiling, so exactly that many workflows may be
active. Durations that cannot be represented by the version-1 exact
nanosecond bound and nesting declarations above the hard version-1 ceiling
are rejected while building the policy.
The ten maxima are otherwise independent: a smaller bound in one dimension
is a valid stricter policy, not a contradictory declaration.

## Finite defaults

`WorkflowResourcePolicy.safeDefaults()` and an otherwise unconfigured
`WorkflowEnvironment` use these values:

| Dimension | Default |
| --- | ---: |
| Accepted Source bytes | 1 GiB |
| Pages | 5,000 |
| Indirect PDF objects | 2,000,000 |
| Container and object-graph nesting | 16,384 |
| Supported filter-stage output | 4 GiB |
| Decoded pixels | 1,000,000,000 |
| Accounted owned memory | 256 MiB |
| Temporary storage | 4 GiB |
| Elapsed time | 5 minutes |
| Concurrent workflows per environment | 4 |

The version-1 public nesting ceiling is 16,384. Traversal used for this bound
is iterative, as are the T21 codecs for nested PDF Values, outline trees, and
logical-structure trees. Operations with a smaller existing stack-safe or
semantic bound keep that smaller bound. This is the general composition rule:
a workflow-wide bound and an operation-local bound both apply, and the first
one exhausted supplies its own stable failure.

## Accounting boundaries

All counters belong to one execution and are never recreated between Commands,
Queries, Patches, named Sources, split products, or publication Targets.

- Input bytes are the actual bytes copied from every primary and additional
  Path, stream, channel, or byte-array Source. Every Source is snapshotted once
  before parsing. A stream, channel, or byte Source's caller-declared bound
  also applies, so the stricter bound wins. Byte Sources additionally retain
  their defensive-copy size in accounted owned memory.
- Pages are distinct page dictionaries observed across the primary document,
  named documents when opened, mutations, and split products. A valid
  over-limit input fails before caller work can observe a Session. Malformed
  page trees retain the more specific failure of the Command or Query that
  owns their interpretation.
- Objects are distinct indirect objects observed by the iterative whole-
  document walk. The xref size is checked before materializing the complete
  walk, and objects introduced in later products continue the same count.
- Nesting is the deepest iteratively observed container and indirect-object
  path, plus supported operation-local content/resource nesting and public
  Patch-value nesting. Cycles are identity-deduplicated. Malformed structures
  are still rejected by their owning operation rather than relabeled merely
  because they are malformed.
- Decompression is the sum of bytes emitted by every successfully supported
  declared filter stage. Intermediate filter results use quota-controlled
  temporary files. Each distinct PDF stream is preflighted once, while later
  project-owned lazy byte reads charge their newly emitted bytes again; a
  repeated read therefore cannot reset the budget. A successful Patch to a
  stream dictionary invalidates its preflight decision so the changed stream is
  checked again. Unsupported or malformed filter declarations retain their
  established format/operation result.
- Pixels are width multiplied by height for each distinct, materializable
  existing image stream and for image preparation performed by the Canvas
  resource path. Increasing an already observed stream's dimensions charges
  the previously unaccounted difference; decreasing them does not refund the
  transaction counter. A declaration whose dimensions cannot enter an existing
  in-process preparation path remains that path's format or local-limit error;
  no pixels are claimed as decoded. T23 Rendering additionally consumes width
  times height for every rendered viewport, including repeated page Queries.
  Before JPEG, JPX, or JBIG2 image decoding, a bounded header read must agree
  with the already admitted declaration; the platform codec must be the single
  terminal filter. Resource streams are read from raw bytes, and every prefix
  filter is decoded in declaration order with its original DecodeParms into
  quota-controlled temporary staging before header inspection. A previously
  rejected platform image can be checked again after its declaration changes;
  successful revalidation clears the format rejection, while emitted filter
  output remains cumulative.
  See [Rendering](rendering.md) for its requested ARGB buffer, bounded PNG
  encoder/consumer, Provider-envelope, and retained-result accounting.
- Owned memory is a model of byte arrays retained or allocated by Folio PDF in
  the bounded paths integrated by T20, including byte Sources, public decoded
  stream results, Canvas image preparation, execution-local credential copies
  and password bridge strings, extracted page-text and derived MIME text, and
  the modeled byte/character storage used to detach or materialize PDF-number
  lexemes. Reservations are made before project-owned strings, character
  arrays, or growable byte and UTF-16 buffers are materialized. They are
  released when their working lifetime ends; detached bytes and text, backend
  password strings, and backend number lexemes retained for the Session remain
  charged through the transaction. This is not a measurement or hard limit for
  the JVM heap, caller allocations, arbitrary callback code, or all transient
  allocations inside PDFBox or ImageIO.
  In the opt-in T21 Hardened Worker, active application frame payloads and
  codec output accumulators, decoded String and byte defensive-copy peaks,
  decoded collection capacity, and execution-local credential copies are also
  reserved before allocation and released at their actual lifetime. One
  synchronized parent ledger covers parent-live and reported child allocations
  without a static split: the parent grants each nonempty application payload,
  the child reports other reserve/release events, and an acknowledged empty
  synchronization follows each quiescent response before another parent
  allocation. The authenticated memory and temporary-storage reserve, release, and grant controls
  are the sole active control-plane exception: their fixed 12-byte payloads
  remain outside the ledger to prevent recursive accounting, while still being
  authenticated and message-bounded before allocation. Declared collection
  counts are checked against remaining wire
  bytes and charged conservatively before capacity-bearing allocation. The
  bootstrap payload and decoded String and collection copies are capped by the
  parent-supplied owned-memory limit before the child transaction context
  exists; retained initialization collection capacity remains charged after
  `READY`. Credential characters are transferred only on demand rather than in
  that initialization payload. Normal active-context failure envelopes are
  accounted. Pre-context and post-context failures use a fixed eight-byte
  allowlisted descriptor because no transaction ledger is live; an already
  poisoned or exhausted active context may use the same bounded codec solely
  to report the stable primary cause.
- Temporary storage includes Source snapshots, supported filter-stage spill,
  PDFBox cache growth, staged documents and split products, and target commit
  staging. Snapshots, spill, and product staging live beneath a private
  per-transaction directory in the environment-owned root. A Path Target's
  final commit file is necessarily created beside that Target so replacement
  can retain same-filesystem move semantics; it is still charged to the same
  quota and owned by the transaction. Rewritten files release their prior
  live-size charge before new bytes are charged. PDFBox uses temp-only cache,
  charged conservatively in cache-page increments, rather than an unbounded
  heap cache. In the T21 child, every committed Worker product remains charged
  through completion, so later products cannot reuse the earlier product's
  live bytes; the parent adopts the same files into its transaction accounting
  after confirmed Worker exit. T23 extends the live parent grant ledger to
  temporary storage before child file/cache growth. Parent-retained PNGs and
  child work therefore share the same current bound without a static split.
  Parent-owned Source snapshots are borrowed without duplicate child charges;
  rendered PNGs and external-Provider snapshots release storage at close.
- Elapsed time starts when the admitted transaction resource context opens and
  is measured by the environment `Clock`. Exact elapsed time is allowed; the
  first later checkpoint fails. A backwards-moving Clock contributes zero
  elapsed time until it catches up. An absolute request deadline remains
  expired when the Clock is equal to or later than it. Under T21 the parent
  keeps this original Clock authoritative, including after authenticated
  child `STAGED` and `VALIDATED` progress frames; the child's cooperative time
  policy is disabled to avoid substituting its system clock. The original
  Clock is sampled only on the caller execution thread. A monotonic parent
  watchdog applies the same configured duration as an independent hard Worker-
  lifetime ceiling, and RLIMIT_CPU remains an independent CPU ceiling.
- Concurrency is nonblocking admission shared by every `DocumentWorkflow`
  using the same `WorkflowEnvironment`. Admission must satisfy both the new
  request's ceiling and every already-active request's ceiling. Rejection does
  not start caller work. A permit is released on every success, checked
  failure, or caller exception.

The preflight deliberately counts resources without replacing established
format diagnostics. It is not a claim that malformed input is accepted.

## Stops, failures, and publication

Project-owned Source reads, graph and page-tree walks, filter-stage preflight,
metadata, annotation and extraction traversal, Patch, Canvas and positioned-
text work, backend-cache I/O, document staging, incremental validation, and
Path or stream publication contain cooperative cancellation, absolute-
deadline, and elapsed-time checkpoints. Caller callbacks and arbitrary
backend/native work cannot be forcibly interrupted in this profile.

Limit exhaustion is terminal for the transaction even if caller code catches
the first `DocumentFailure`. The stable T20 codes are:

| Dimension | `DocumentFailureCode` |
| --- | --- |
| Input bytes | `WORKFLOW_INPUT_LIMIT_EXCEEDED` |
| Pages | `PAGE_LIMIT_EXCEEDED` |
| Objects | `OBJECT_LIMIT_EXCEEDED` |
| Nesting | `NESTING_LIMIT_EXCEEDED` |
| Decompression | `DECOMPRESSION_LIMIT_EXCEEDED` |
| Pixels | `PIXEL_LIMIT_EXCEEDED` |
| Owned memory | `MEMORY_LIMIT_EXCEEDED` |
| Temporary storage | `TEMPORARY_STORAGE_LIMIT_EXCEEDED` |
| Elapsed time | `ELAPSED_TIME_LIMIT_EXCEEDED` |
| Concurrency | `CONCURRENCY_LIMIT_EXCEEDED` |

An absent, unusable, or non-directory environment storage root produces
`TEMPORARY_STORAGE_UNAVAILABLE`. Cancellation and absolute deadlines retain
the T03 codes and capability identity. Every diagnostic is fixed and contains
no Source or Target name, document data, local path, credential, or backend
exception.

A stop before a Path commit leaves an existing Target unchanged. Across
multiple Targets, earlier `COMMITTED` receipts remain committed, the current
Target is `FAILED` or `NOT_ATTEMPTED` according to whether commitment or a
stream write began, and later Targets are `NOT_ATTEMPTED`. A failed stream may
contain partial output. T20 does not add cross-Target atomicity or physical
secure erasure.

Documents, credentials, Source descriptors owned by the workflow, snapshots,
cache/spill files, staged files, and concurrency permits are released on every
terminal path. Caller-owned streams, channels, and output streams remain open.

## T21 boundary

This policy makes trusted desktop and controlled batch use finite by default.
On its own it does not isolate tenants, cap the complete JVM process, contain
process trees, deny network access, or hard-kill code that does not cooperate.

T21 now supplies the opt-in `HARDENED_WORKER` execution profile. It runs the
PDF engine in a separately launched local JVM, authenticates and bounds every
closed-schema message, applies heap/direct-memory and CPU/open-file limits,
uses caller-thread environment-Clock/deadline checkpoints plus a monotonic
hard-stop watchdog, denies descendant process creation and INET/Unix-domain
network access, confines Worker file access to a random owner-only transaction
root, and leaves actual Target publication in the parent. The same T20 policy
continues inside the child, with parent grants for shared live temporary-storage
capacity and parent authority for the logical time dimensions.

The in-process profile remains the backward-compatible default and is still
not a hostile multi-tenant boundary. Deployments selecting the Worker must meet
its fail-closed Linux/JDK requirements and must not silently fall back when it
is unavailable. The exact controls, limitations, Worker failure codes, and
absence of any certified-platform claim are documented in the
[Hardened Worker guide](hardened-worker.md).
