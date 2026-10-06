# T22 Worker recovery and bounded-scale implementation evidence

Status: `experimental`

Capability: `document.hardened-worker.recovery-scale`

Acceptance Profile: `T22-worker-recovery-scale`

Release train: `0.1.0-SNAPSHOT`

Fixed point: `7c12104960045f3069843f850c4e782cb4d63c77`

This is clean-room implementation and controlled-scale evidence. It is not a
compatible-status certification, independent conformance result, benchmark,
or certified-platform claim.

## Recovery and publication observations

- `HardenedWorkerRecoveryTest` primarily uses
  `DocumentWorkflow.execute`, `lookupTransaction`, public outcomes, failures,
  statuses, receipts, and files. It observes bounded URL-safe identities,
  Worker-only admission, count- and per-record-modeled-metadata-bounded
  environment-local non-evicting retention, pre-admission rejection of an
  oversized Target declaration without consuming a ledger slot, duplicate
  `RUNNING` rejection, final-state no-replay, request-envelope mismatch
  rejection, reconstructed byte-source content identity, and weak retention of
  caller-owned resource identities. Byte-source content is digested with its
  defensive declaration copy; bounded fingerprint work occurs outside the
  synchronized ledger.
  Retrying a consumed caller-owned Source remains the caller's responsibility:
  the same stream or channel must be restored to its original readable position.
- Pre-start and active cancellation and elapsed Worker timeout become
  `RECOVERABLE` only while all ordered receipts remain `NOT_ATTEMPTED`; a retry
  under the same identity can supply fresh attempt controls and completes once
  capacity is available. Existing Path bytes remain unchanged before retry.
- A simulated lost caller acknowledgement after both Path Targets commit is
  resolved by lookup as `COMPLETED`, with the two `COMMITTED` receipts in
  declaration order. The same final rule applies after a stream Target fully
  commits. Retrying either final identity fails before callback or publication.
- A stream that fails after possibly emitting bytes is retained as `FAILED`
  with `partialOutputPossible=true`; its byte count does not change on a
  rejected replay. This is explicitly terminal because Folio PDF cannot inspect
  or roll back a caller-owned stream.
- `HardenedWorkerRecoveryFaultTest` uses only the package-private fault seam
  needed to terminate the real child or inject an authenticated malformed
  response. Each produces a deterministic stable failure, a retained
  `RECOVERABLE` state with not-attempted receipts, Target preservation, owned-
  root cleanup, and a successful retry through the public workflow seam.

The focused recovery run observed 14 passing tests: twelve public recovery
contracts and two real-child fault contracts.

## Authenticated large-value observations

- Every physical frame retains the version-1 length bound, direction-local
  sequence, and HMAC-SHA-256 tag. A multi-frame declaration fixes the original
  application opcode, direction-unique 128-bit transfer identity, total length, exact
  chunk count, and SHA-256 digest; each chunk repeats the identity and ordered
  index.
- `WorkerProtocolBoundaryTest` observes exact aggregate receive admission with
  a 96-byte frame bound: a 272-byte logical payload succeeds at a 368-byte
  modeled receive peak, while 273 bytes fails before any wire output. Missing,
  reordered, duplicate, raw-corrupt, valid-HMAC wrong-identity, declared-
  length mismatch, valid-HMAC digest mismatch, and first-excess transfers fail
  safely and release owned buffers and reservations. An authenticated trailing
  chunk must acquire the same 96-byte frame scratch before its payload is
  allocated, even though it is outside an active transfer.
- `HardenedWorkerWorkflowTest` sends 4-KiB metadata through a Worker configured
  with a 2-KiB message bound, returns it through a second multi-frame transfer,
  publishes the document, reopens it, and observes the original public value.
- `WorkerCodecContractTest` observes the authenticated resource-usage control
  as exactly 72 bytes and rejects its first excess, wrong version, negative
  observations, and malformed elapsed fields.

A broader focused Worker regression run observed 134 passing tests across
`WorkflowExecutionProfileContractTest`, `HardenedWorkerWorkflowTest`, both
recovery suites, `WorkerCodecContractTest`, and
`WorkerProtocolBoundaryTest`.

## Controlled scale observations

Invocation:

```text
./scripts/t22-scale all
```

The workloads are generated at run time. No large fixture, output, temporary
root, authentication key, credential, or transaction secret is retained in
the repository.

Observed platform on 2026-09-04:

- OS: Linux 6.8.0-136-generic
- architecture: amd64
- Java vendor: GraalVM Community
- Java version: 17.0.9

The safe-default policy was 1,073,741,824 input bytes; 5,000 pages;
2,000,000 objects; nesting depth 16,384; 4,294,967,296 decompressed bytes;
1,000,000,000 decoded pixels; 268,435,456 modeled Folio-owned-memory bytes;
4,294,967,296 temporary-storage bytes; five minutes; and four concurrent
workflows. The concurrency profile changed only its concurrency bound to two.

| Profile | Generated input and semantic result | Resource observations | Time |
| --- | --- | --- | --- |
| pages | 5,000 project-owned `AddBlankPage` commands; published and reopened as exactly 5,000 pages | accepted input 0; pages 5,000; objects 5,001; decompressed 0; pixels 0; peak modeled memory 105,676 B; peak temporary 391,570 B; concurrency 1 | accounted 34.934889449 s; wall 34,936 ms |
| input | constant-memory stream: PDF header, newline fill, and valid one-page xref tail at exactly 1,073,741,824 B; caller stream remained open; roots cleaned | accepted input 1,073,741,824 B; pages 1; objects 3; decompressed 0; pixels 0; peak modeled memory 1,072 B; peak temporary 1,073,741,824 B; concurrency 1 | accounted 4.960837881 s; wall 5,048 ms |
| concurrency | two held public workflows admitted at limit two; first excess rejected before callback/publication and retained recoverable; retry completed after release | retried workflow: accepted input 0; pages 1; objects 2; decompressed 0; pixels 0; peak modeled memory 784 B; peak temporary 12,893 B; concurrency 2 | accounted 0.45396107 s; aggregate wall 1,236 ms |

Every recorded workflow was within its declared modeled-memory, aggregate
temporary-storage, and elapsed-time bound. These public observations do not
measure backend/JVM allocation, process RSS, kernel memory, filesystem cache,
or platform-wide resource consumption.

## Project verification gates

The complete implementation diff passed these project-authored gates on
2026-09-04:

- `./mvnw -B -ntp verify`: all ten reactor modules passed. The document module
  ran 549 tests with zero failures or errors and skipped only the three
  explicitly opt-in T22 scale profiles; API leakage, packaged-JAR, acceptance
  harness, inventory-freshness, and release-policy checks also passed.
- `./scripts/verify-jdk-matrix.sh`: the same full reactor passed in the
  repository's Eclipse Temurin JDK 8, 11, 17, and 21 containers.
- `./scripts/inventory generate` followed by `./scripts/inventory validate`:
  generated views were current and validation passed with 16 capabilities,
  12 facade surfaces, and 15 exclusions.
- `./scripts/t22-scale all`: all three controlled scale profiles reported
  `PASSED`; their exact observations are recorded above.

These gates establish repository consistency and repeatability of the authored
contracts. They do not replace any independent acceptance chain below.

## Acceptance status boundary

| Mandatory chain | Result | Reason |
| --- | --- | --- |
| syntax | `INDETERMINATE` | No T22-specific independent syntax profile or recorded external-tool run exists. |
| standards | `INDETERMINATE` | No independent T22 standards review has been supplied. |
| semantic | `INDETERMINATE` | Project-authored public contract tests pass, but no independent T22 semantic chain has been supplied. |
| visual | `INDETERMINATE` | T22 adds no renderer and no independent T22 visual comparison was run. |

qpdf was already available locally, while the documented PDFium and
ImageMagick cache paths were unavailable. No external evidence tool was
downloaded or provisioned, and no incomplete chain is reported as a pass. The
`document.hardened-worker` compatible-status Dependency Gate and the T06
Promotion Gate remain open. Therefore this capability remains `experimental`
with an empty certified-platform list.

T22 makes no remote-Worker, durable-recovery, automatic-retry, renderer,
public-backend-SPI, arbitrary-extension, Migration-Facade, whole-process-RSS,
kernel-container, physical-secure-erasure, or cross-Target-atomicity claim.
