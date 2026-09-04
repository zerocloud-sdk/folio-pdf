# Hardened Worker profile

T21 adds an opt-in local process boundary to the existing Document Workflow.
Callers keep the same callback shape and explicitly select
`WorkflowExecutionProfile.HARDENED_WORKER` on the request. The callback,
progress listener, caller result, caller-owned streams and channels, Capability
Provider selection, and final publication all remain in the caller JVM. Only
library-owned Commands, Queries, and closed project values cross the boundary.

```java
WorkflowRequest request = WorkflowRequest.builder()
        .source("upload", DocumentSource.path(upload))
        .primarySource("upload")
        .target("result", PublicationTarget.path(result))
        .saveMode(SaveMode.REWRITE)
        .executionProfile(WorkflowExecutionProfile.HARDENED_WORKER)
        .build();

WorkflowOutcome<Integer> outcome = new DocumentWorkflow().execute(
        request,
        session -> {
            session.execute(AddBlankPage.INSTANCE);
            return session.query(PageCount.INSTANCE);
        });
```

A successful outcome reports `HARDENED_WORKER`. `IN_PROCESS` remains the
default for source and binary compatibility.

## Protocol and ordering

The parent starts one Worker for one transaction and communicates over the
child's private standard-input/standard-output pipes. A fresh 256-bit key is
written through the pipe before framing and is never placed in an argument,
environment variable, filename, diagnostic, or log. Every frame contains the
`FPW1` magic, protocol version 1, a direction-local monotonic sequence number,
an opcode, a signed length, a bounded payload, and HMAC-SHA-256 authentication.
Magic, version, sequence, negative length, and the configured per-message limit
are checked before payload allocation. Missing, wrong, replayed, or modified
authentication fails closed.

The default payload maximum is 64 MiB and is configurable with immutable
`HardenedWorkerSettings` on `WorkflowEnvironment`. There is no Java object
serialization, dynamic class name, lambda, reflection target, script, raw PDF
operator, or arbitrary URI field. Version-1 codecs enumerate all Commands and
Queries owned by `PdfBoxDocumentSession` at the T21 fixed point. Unknown
versions, opcodes, value tags, enum values, trailing data, and caller-defined
Command or Query implementations are rejected with stable failures. Protocol
Strings carry length-bounded UTF-16 code units rather than a replacement-prone
character conversion, so invalid surrogate input reaches the same public
domain validator and stable failure in both profiles.

`DocumentSession.executeBatch` has two closed transport shapes. A batch made
only of `AddBlankPage`, `InsertBlankPage`, `CopyPages`, `MovePages`, and
`RemovePages` has a fixed encoded length. The parent models the complete atomic
transport peak as the larger of growable encoder capacity plus retained payload
and simultaneous parent/child payload ownership plus the retained/received
completion control. When that peak fits the current message and aggregate
owned-memory bounds, the parent offers the encoded length. The Worker accepts
only when the complete payload fits its current modeled owned-memory allowance.
An accepted batch crosses in one authenticated atomic `COMMANDS` frame. If the
batch is ineligible for an offer, the offer is deferred, or the batch contains
any other Command, the parent sends one authenticated declaration containing
the version and complete ordered count. The Worker then requests each indexed
preflight, any required preflight details, and the corresponding Command only
after its predecessor succeeds. One final acknowledgement completes either
logical batch.

The staged path does not encode or materialize a later Command—including a
one-shot Font Source or lazy PDF Value—after an earlier failure. If local
encoding itself fails, an authenticated abort closes the declared batch while
leaving its successfully executed prefix observable. If that recovery exchange
cannot complete, the Session becomes terminal while the already-observed
encoding failure remains authoritative. Both paths preserve the in-process
rule that Commands take effect in declaration order, execution stops at the
first failure, and a later encoding or resource failure cannot replace an
earlier semantic or backend failure. The synchronous
`DocumentSession.execute` method delegates its singleton batch through the same
selection. A Query is a round-trip ordering barrier and observes every earlier
completed Command or batch prefix.
Bounded `InspectObject` containers and streams remain lazy: individual view
operations use explicit authenticated messages and expire with the proxy
Session. The Worker services those view messages while awaiting a deferred
batch item, so a same-Session lazy value remains valid input to a later
Command.

The Worker emits authenticated `STAGED` and `VALIDATED` progress frames at the
same internal boundaries as in-process execution. The parent invokes the
caller's listener synchronously on the caller execution thread, then checks
the original environment Clock, deadline, and cancellation state before
allowing the Worker to continue. All other progress phases remain parent-owned.

Large-value staging, chunking, idempotent transaction identities, lost-
acknowledgement recovery, and uncertain-publication lookup are deliberately
not part of T21; those are T22 concerns. A caller must not treat an abnormal
process loss as safely retryable unless its own application protocol supplies
that guarantee.

## Files, publication, and credentials

Before launch, the parent copies the primary Source, when present, to a
randomly named file under T20's owner-only per-transaction root. The
initialization message identifies every other named Source as pending. During
child initialization those Sources are requested and copied one at a time in
declaration order through authenticated `SOURCE_REQUIRED` and
`SOURCE_MATERIALIZED` messages. A failure while classifying an earlier Source
therefore does not open or consume a later one-shot Source. Input names and
actual Source paths never become Worker filenames; the Worker receives only
single-segment handles within the private root.

Configured Reference Font Set entries and explicit Font Sources stay in the
parent until a positioned-text Command selects them. At that command boundary
the parent applies the declaration and source-count checks and puts only opaque
source identifiers in the Command frame. As the Worker evaluates those sources
in declaration order, it requests each font program separately; the parent
opens it under the same aggregate-byte, ownership, and first-excess rules and
returns one bounded `FONT_VALUE` frame. Font paths are therefore opened lazily,
unused missing reference paths do not fail startup, and identical one-shot
stream or channel declarations retain their Session-local reuse behavior.

The Worker publishes only to random parent-created product handles in the same
root. After the Worker has finished validation and exited, the parent accounts
those files and performs the existing ordered Path or caller-owned-stream
publication contract against the real Targets. The Worker never receives a
real Target path or output stream.

The parent preflights real Targets before caller work. Existing Targets remain
unchanged on Worker, protocol, caller, validation, or staging failure. Ordered
publication receipts, stream partial-output reporting, lack of cross-Target
atomicity, explicit Save Mode, and caller ownership are the same as for the
in-process profile.

The initialization message carries only password-security descriptors and
booleans that identify which Sources have credentials; credential characters
remain parent-side. The child requests each required Source, output-owner, or
output-user credential in protocol order through authenticated
`CREDENTIAL_REQUIRED` and `CREDENTIAL_MATERIALIZED` messages. Character arrays
and their protocol buffers are modeled-owned-memory-accounted, never converted
into protocol Strings, cleared after each transfer, and retained only in
destroyable child-local credentials until transaction teardown. The parent
also clears and releases the descriptor-only initialization payload after
authenticated `READY`. JVM/backend limitations on physical erasure remain
those documented in `SECURITY.md`.

The complete transaction root, including Source snapshots, child cache, staged
documents, product handles, and protocol-independent scratch data, is
recursively removed by parent cleanup after confirmed child termination on
success and failure. Committed Worker product handles remain cumulatively
charged until that cleanup, so multiple products cannot reset the shared
temporary-storage counter. A cleanup failure becomes a stable checked failure
rather than allowing a successful outcome to be reported. Owner-only POSIX
permissions and deletion are logical access and lifecycle controls; no
physical secure-erasure claim is made.

## Process isolation and limits

The current launcher is intentionally fail-closed and supported only on Linux
hosts that provide an executable `/usr/bin/prlimit` and a JDK whose legacy Java
Security Manager remains available. The repository verifies JDK 8, 11, 17,
and 21. No other operating system, JDK release, container runtime, or Linux
distribution is claimed compatible or certified.

The launcher:

- clears the inherited environment and invokes an absolute Java executable
  without a shell;
- derives an independent RLIMIT_CPU ceiling from the configured elapsed
  duration, limits open file descriptors, sets the configured `-Xmx` heap
  ceiling and the same maximum for direct buffers, and uses a fixed
  thread-stack bound;
- builds a dependency-only Worker class path from the document engine, Provider
  contract, PDFBox/FontBox/logging, and installed optional TIFF code-source
  closure rather than inheriting application or test entries. First-party code
  sources must match digest-pinned class-name inventories and the small
  allowlist of packaging metadata; dependency JAR bytes must match their exact
  SHA-256 authorities. The document engine and Provider contract must be
  separate, unshaded artifacts or project-only exploded class directories.
  Mixed, incomplete, extra, multi-release, manifest-extended, or ambiguously
  delimited/wildcard classpath entries, and any modified dependency JAR, fail
  with `WORKER_UNAVAILABLE`;
- samples the original environment Clock and deadline only at caller-thread
  checkpoints, while a monotonic watchdog enforces an independent hard Worker-
  lifetime ceiling and observes the explicitly thread-safe cancellation token;
- denies child process creation, which contains the process tree to the one
  launched JVM;
- installs a Worker I/O policy that permits reads only from the
  JDK runtime, dependency-only Worker class path, and transaction root,
  permits writes/deletes only in the transaction root, denies symbolic/hard
  links, and denies outbound, listening, accepted, and multicast INET sockets
  plus Unix-domain connect and listen access.

### Worker class-path authority

First-party code is authorized by the exact class-name inventory, not by a
package-prefix scan. This allows byte-for-byte build variation across supported
JDK compilers while rejecting any missing or additional class entry.

| Artifact | Inventory resource | Entries | Inventory SHA-256 |
| --- | --- | ---: | --- |
| `pdf-document` | `META-INF/folio-pdf/document-worker-classes` | 557 | `5cae2b530397a18823848b085499b9c88e6f8ec573eea38df3d7c92eba3dc220` |
| `pdf-provider-contract` | `META-INF/folio-pdf/provider-contract-worker-classes` | 20 | `c7a7bb193dcfa656ba13af311ce2d7654a5aaac962b8804481a77d14013a25b6` |

Every third-party entry must be a regular JAR whose complete bytes have the
listed digest. The optional TIFF closure is accepted only as the complete exact
set discovered at runtime.

| Runtime artifact | Accepted JAR SHA-256 |
| --- | --- |
| `org.apache.pdfbox:pdfbox:3.0.8` | `97647cfbde61ebcfc06b4cf8c9b0ffcaaee073396eceb4a7f6836a9b9128903c` |
| `org.apache.pdfbox:pdfbox-io:3.0.8` | `36a0e04001010b4c764857817412b96339930b19755e728959805cc0352061b2` |
| `org.apache.pdfbox:fontbox:3.0.8` | `a1915c24e3edbe0ecec93896dfbf6d41427810b663ade97bd4e8bae86ec3fdab` |
| `commons-logging:commons-logging:1.4.0` | `d175dbd751dd782a63bde28c7a039520e971f25e84b79c19b8435edc3603e0dc` |
| `com.twelvemonkeys.imageio:imageio-tiff:3.14.0` | `68aa1b4a176d1242b9e49334df188ebfbb7c9201f6071dfe42500d63486224b6` |
| `com.twelvemonkeys.imageio:imageio-core:3.14.0` | `a1b832b5090bd4677696f999b5ccb8954e987eb9674632a6286a6de2bb1c3c78` |
| `com.twelvemonkeys.imageio:imageio-metadata:3.14.0` | `03768fc012bd2573236da803099aba6961dfb29c190103f9790fc49ac27f84c1` |
| `com.twelvemonkeys.common:common-lang:3.14.0` | `8d4529d6f56a010bc7e130ebfcdaf14bc11586e9d9ae66f6dca66f91da7eafef` |
| `com.twelvemonkeys.common:common-io:3.14.0` | `ae01308bd48c68e76f6a1f76880cf7f4a3a004aa83d78e5448de358a4d957e8f` |
| `com.twelvemonkeys.common:common-image:3.14.0` | `9edb1afd32278d20ad660869bfa5b0a27cf9b3553b6eb3f8fc51a2fc13109b66` |

These values are also part of the repository's dependency authority in
[`DEPENDENCIES.md`](../DEPENDENCIES.md). Repacked dependency JARs and
dependency directories are intentionally unsupported by this profile.

These controls are defense in depth around a closed data protocol; they are
not a general-purpose sandbox for arbitrary bytecode or native libraries. The
heap/direct-buffer controls do not constitute a certified whole-process RSS or
native-memory ceiling, RLIMIT_CPU is not a wall clock, and the Java Security
Manager is unavailable in newer JDKs that permanently disable it. Deployment
must treat `WORKER_UNAVAILABLE` as a hard refusal, not silently fall back to
`IN_PROCESS`. Stronger OS container, cgroup, seccomp, or MAC profiles may be
layered by an operator, but Folio PDF does not certify one in T21.

The T20 `WorkflowResourcePolicy` still governs input, pages, objects, nesting,
decompression, pixels, modeled owned memory, aggregate temporary storage,
elapsed time, and shared concurrency. The child receives the same non-time
semantic bounds, with its temporary-storage allowance reduced by parent-owned
staging already live in the transaction. The parent remains authoritative for
elapsed time and deadlines because an injected Clock cannot be reconstructed
faithfully in another process. The injected Clock is sampled only on the caller
execution thread. The same configured maximum duration also supplies a
monotonic hard ceiling for the Worker lifetime, and RLIMIT_CPU is a separate
CPU ceiling.

One synchronized parent ledger accounts both parent-live allocations and all
child allocations reported by the Worker; there is no static parent/child
split. Before every nonempty parent-to-child application frame, the parent
reserves and grants the exact payload allocation. The child requests and
releases its other decoded or retained allocations with authenticated memory
control frames. At each quiescent application response, the parent sends an
empty memory-synchronization frame and waits for its acknowledgement before
making the next parent allocation. This makes child releases authoritative in
the aggregate ledger without running caller Clock policy on the reader thread.

Active-context application payload buffers, decoded String/byte defensive
copies, decoded collection capacity, credential copies, and normal failure
envelopes are reserved before allocation and released at their actual lifetime.
The authenticated memory reserve, release, and grant controls are the sole
active control-plane exception: each has a fixed 12-byte payload allocated
outside the ledger so accounting the ledger protocol cannot recurse, and each
is still authenticated and checked against the message limit before allocation.
Initialization is capped by the parent-supplied memory boundary before the
child resource context exists, and decoded initialization collection capacity
remains charged after `READY` for the active transaction. Counts are checked
against remaining wire bytes and charged conservatively before capacity-bearing
arrays or collections are created. Pre-context bootstrap failures and post-
context failures use a fixed eight-byte allowlisted descriptor, independently
message-bounded because no transaction ledger is live. The post-context
`FINISHED` control is likewise fixed at eight bytes and contains only a version
and allowlisted capability token. If a primary resource failure
has already poisoned or exhausted an active context, the fixed failure codec
may use the bounded emergency reporting path solely to preserve that cause. A
Worker crash, authentication failure, unsupported message, message-limit
failure, or unavailable launcher has a distinct stable `DocumentFailureCode`
and a fixed content-free diagnostic.

## Capability Providers and exclusions

Capability Provider discovery and selection remain parent-brokered. No
Provider executable object crosses the protocol. Remote Providers still
require capability-scoped Remote Disclosure Authorization; choosing the
Hardened Worker profile neither grants disclosure nor gives the Worker network
access.

T21 adds no remote Worker, public backend SPI, user extension mechanism,
rendering, crash recovery, scale certification, Migration Facade mapping, or
independent Acceptance Evidence. The capability remains `experimental` with
an empty certified-platform list.
