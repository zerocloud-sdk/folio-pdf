# Worker recovery and bounded Foundation scale certification

Issue #83 owns `document.hardened-worker.recovery-scale`, Acceptance Profile
`T22-worker-recovery-scale` and Foundation obligation `recovery`. The existing
[Worker guide](hardened-worker.md), public Workflow contracts and ADR-0038 govern
recovery. This certification reuses the Native implementation and the qualified
T03/T21 acceptance assets. It adds no product dependency or public test seam.

The [coverage inventory](../capabilities/profiles/T22-worker-recovery-scale/coverage.json)
and [180 mandatory named cases](../capabilities/profiles/T22-worker-recovery-scale/mandatory-tests.txt)
include every T21 boundary case, the existing recovery and resource-usage codec
cases, all three actual scale profiles, real caller interruption and malformed
Source failures. The shared runner rejects missing, repeated, ignored,
assumption-skipped or failed cases before certification. Ordinary CI leaves the
three scale tests opt-in; its skips never qualify a certification run.

Real Worker regressions cover a managed child that stays alive beyond one second
after FINISHED and exits within the 30-second exit grace, and a parent checkpoint
delayed while the child exits successfully. The supervisor rechecks liveness
before declaring grace expiry. JVM teardown, kernel exit and parent reaper
observation can be delayed on a stalled host; this grace does not extend the
caller deadline or the overall five-minute default. Cancellation, deadline,
elapsed expiry and interruption still prevent publication and confirm cleanup.
Success observes COMMITTED publication, COMPLETED lookup and public reopen.
An actual 30-second expiry retains elapsed time since FINISHED, child liveness,
wait iterations, maximum poll time and bounded available Linux process/thread
state before forced termination. Missing PID/procfs observations are explicitly
unavailable; command lines, environment, filenames, thread names and raw
exceptions are never retained. Failure observes ordered NOT_ATTEMPTED receipts,
RECOVERABLE lookup, unchanged Target sentinels and owned-storage cleanup.
Every post-FINISHED failure path retains these diagnostics and any available
exit code while preserving its original failure code/capability and receipts.
A real nonzero managed-process exit records the observed exit code with no
publication; cancellation, deadline and elapsed failures retain their established
operational identities.
The full-grace regression has a 60-second JUnit timeout; the other new exit cases
use 10 seconds. A genuinely stuck teardown can exceed an existing ordinary
10-second test timeout, while product callers remain bounded by their request
policy. The caller-side launcher and Clock injection are acceptance fixtures,
excluded from the qualified product runtime closure.
The interruption fixture first asserts that the public workflow preserved the
caller's interrupt flag, then temporarily clears and restores that flag around
its own NIO sentinel/storage inspection so the checks also execute on Java 8.

Public requests and lookup observe Worker-only, environment-local identities,
finite non-evicting record retention, modeled per-record envelope admission,
material-envelope conflicts and duplicate rejection before callback/publication.
Lookup distinguishes RUNNING, RECOVERABLE, COMPLETED and FAILED with ordered
Publication Receipts. Fresh attempt controls permit an eligible recoverable
retry only while every Target is NOT_ATTEMPTED. Lost acknowledgement after all
Path or borrowed-stream commits remains COMPLETED. Possibly partial stream
publication remains terminal FAILED; replay invokes no callback or further
publication. Real process and authenticated protocol controls exercise crashes,
interruption, cancellation, elapsed expiry, malformed input/responses and
missing/reordered/duplicate/corrupt/mismatched/malformed/excess multi-frame data.
Uncommitted Target sentinels, stable failures, child termination, caller
ownership and owned-storage cleanup remain observable obligations.

Each of the four declared environments executes these exact workloads:

| Profile | Actual input and result | Independent observation |
| --- | --- | --- |
| pages | 5,000 ordered AddBlankPage commands; one published/reopened 5,000-page PDF | Syntax and qualified standards on the entire file; independent graph traversal of every page; visual pages 1, 2,500 and 5,000 |
| input | Exactly 1,073,741,824 consumed PDF bytes; public one-page observation; no Target and no published PDF | Every producer reads the complete original input, rehydrated losslessly from retained gzip; visual page 1 |
| concurrency | Policy concurrency two; two admitted one-page publications; first excess rejected before callback/publication; successful recoverable retry after capacity release | Every actual published/reopened one-page file is independently observed |

The input generator retains its exact PDF 1.7 header and front object tree,
newline fill, tail and xref offset. The front object tree is frozen in the
profile authority; the full input still spans exactly one GiB with its xref
near the end. Gzip contains every byte actually consumed by the Worker. Collection
streams the full archive and checks its construction, length and SHA-256; a
miniature or resealed altered input fails. Command constructions, output
lengths/hashes, transaction identities, ordered receipts, reopen observations,
full finite policies, modeled high-water counters, wall/elapsed times, temporary
spill, caller-stream ownership and cleanup are retained. They are bound to the
original mandatory test artifacts and stdout before comparison with fresh runs.
Observed elapsed and wall time may vary between runs. Aggregate modeled owned-memory
peaks may also vary after each run's complete scale artifacts and stdout agree,
its full finite policy matches, and its modeled usage satisfies that policy.
Only `usage.memory` in the three scale observation files receives this allowance;
per-output memory, other counters, policies, constructions and artifact findings
remain exact. The source and artifact originals are never normalized in storage.

Elapsed limits, remaining Provider budgets and usage receipts use a monotonic
nanosecond ticker by default. Forward or backward wall-clock steps do not change
that budget. Absolute request deadlines remain wall-clock instants. Explicit
Clock-only configuration retains deterministic logical elapsed semantics; an
explicit `monotonicTicker` takes precedence regardless of builder setter order.
Mandatory public-workflow cases exercise both profiles with a forward wall step,
genuine elapsed excess with a frozen wall Clock, inclusive elapsed bounds across
ticker wraparound, backward wall time, real system nanoseconds and absolute
deadline expiry. A real long in-process workflow also exceeds the default
system ticker's one-second configured budget. A protocol negative rejects a
relative child deadline rather than converting it to a wall-clock timeout.

The full policy is input 1,073,741,824 bytes; 5,000 pages; 2,000,000 objects;
nesting 16,384; decompression 4,294,967,296 bytes; 1,000,000,000 decoded pixels;
268,435,456 modeled owned-memory bytes; 4,294,967,296 temporary-storage bytes;
five minutes; concurrency four, lowered to two for the concurrency profile.
Modeled Folio-owned memory/storage is distinct from JVM heap, process RSS,
native/backend allocations, OS caches, kernel/container limits and physical
secure erasure. Constant-memory input construction does not imply unbounded
Worker streaming or unbounded multi-frame values.

Five producer-bound chains stay separate. qpdf checks syntax of the actual PDF
bytes. pdfcpu and Arlington independently retain their original findings and
rule-specific detected negatives for the original qualified T03 22-rule blank
document union. All actual scale PDFs are resource-free blank PDF 1.7 documents,
so those predicates apply; no font, text-layout or broader conformance predicate
is claimed. T22 gives Arlington a finite 180-second, 16-MiB output budget to
retain the full 5,000-page traversal without truncation. An independent qpdf
JSON observer traverses every actual Page tree relationship, count, leaf,
geometry, Resources and absent Contents. An incorrect count control uses the
actual 5,000-page artifact. Original T03 semantic and standards negatives remain
required. PDFium and ImageMagick apply the original authored white raster at
144 DPI, opaque sRGB, AE zero and zero fuzz to the declared pages, with the
original two-page/one-pixel controls. This declared visual sample is not a
full-document visual comparison or #88 rendering certification.

Matching document outcomes use public Native workflows and the existing public
Migration Facade lifecycle operations, reopened files, ownership, ordered
publication failures and receipts. The Facade actually executes IN_PROCESS.
Transaction identity, recovery and resource controls remain justified Native-only
controls with no Reference Suite counterpart; no artificial mapping is added.
No external Provider seam is applicable to these blank workloads, and no font
input is required. The existing explicit HarfBuzz installation is nevertheless
observed as an environment input. Acceptance classes/tools remain outside the
exact production Worker runtime closure.

Current certification is defined exclusively by
[foundation-evidence.yaml](../capabilities/foundation-evidence.yaml). Each tuple
binds the final source/artifact candidate, contracts, complete staged harness,
execution settings, commands, locale/timezone, immutable Ubuntu image, observed
OS/kernel/architecture, exact JDK vendor/build/executable, launcher/runtime
inventories and native/tool identities. Collection checks original manifests,
all mandatory predicates and controls, then re-executes the mandatory suite and
recorder in that exact image. Original findings, products and public outcomes
must match the fresh observations. Exact child finding identities are checked
before comparison. Both hashes of already-compared ID-neutral PDF bytes map to
the same comparison digest, including identical original concurrency products
whose fresh trailer IDs differ. Positive and negative raw graph digests and
producer commands remain independently checked. Canonical JSON comparison preserves
boolean, integer and floating-point distinctions. Scale visual report links to
external authorities compare their resolved file identities across replay directory
depths; links to local observed products remain exact. Existing PDF trailer-ID and pixel comparison
rules apply; private roots and PIDs are normalized only after real boundary
identities and controls are checked. Changed producer labels or resealed data
cannot repair missing cases, artifacts, findings, controls or stale identities.

Collection additionally requires exactly one `T22_WORKER_EXIT_GRACE` record in
each original and fresh mandatory transcript. It validates the real 30-second
expiry, declared grace, alive child, available positive PID and closed Linux
state characters, bounded task sample and consistent wait counters. Missing,
duplicate, shortened, malformed or unsafe diagnostics reject certification even
when named-case PASS labels remain intact. Each measured timing/PID/state stays
in its original transcript; fresh executions qualify their own observations.

The required scope is Ubuntu 24.04/Linux x86-64 with the exact Temurin builds
`1.8.0_502-b07`, `11.0.32+9`, `17.0.20+8` and `21.0.12+8-LTS`. All four
HARDENED_WORKER tuples require syntax, standards, semantic, visual and contract:
twenty records. Windows x86-64 and macOS x86-64/arm64 remain explicitly
uncertified and nonblocking for Foundation 0.1.0. Historical September GraalVM
observations remain historical; they are not relabeled for this candidate.

Select the explicit validated native helper, Ubuntu Python installation and
writable Maven cache. Freeze the proposed completed source/contracts/inventories
and generated views, then stage:

```sh
python3 -B scripts/t03-foundation.py stage
```

Refresh invalidated predecessors in this order: transactions, values, pages,
metadata, annotations, text, images, incremental, password-baseline,
password-clear-metadata, password-attachments, limits, worker; then recovery.
Use fresh destinations and preserve historical and failed observations:

```sh
python3 -B scripts/t03-foundation.py certify capabilities/evidence/foundation/T83-final-r9/recovery --obligation recovery
python3 -B scripts/t03-foundation.py collect capabilities/evidence/foundation/T83-final-r9/recovery/jdk17-hardened_worker/observations --obligation recovery --execution-profile HARDENED_WORKER
./scripts/t22-scale all
```

The standalone scale command must additionally run in each declared image.
`FOLIO_T22_OUTPUT` optionally selects an existing empty retention root. Any later
source, contract, artifact, harness, configuration or tool change requires fresh
staging and affected certification. Final full verification, inventory
generation/validation/freshness, live readiness and baseline-relative
Standards/Spec review remain required. Recovery and every previously satisfied
prerequisite must be SATISFIED for the same candidate. Other outstanding
Foundation obligations may keep global readiness false; #83 does not implement
or approve them. This unsigned local route performs no publication or tracker
mutation.

Recovery remains in-memory and caller-driven. Applications preserve callback
meaning and mutable Path contents and restore borrowed Source positions before
retry. There is no automatic retry, durable store, distributed identity,
cross-Target atomicity, remote Worker, arbitrary extension or downstream feature
certification.
