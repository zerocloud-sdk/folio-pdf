# Hardened Worker boundary certification

Issue #82 owns `document.hardened-worker`, Acceptance Profile
`T21-hardened-worker` and Foundation obligation `worker`. The normative
[Worker guide](hardened-worker.md), public Document Workflow contracts and
ADRs 0016, 0023, 0024, 0025, 0031 and 0040 remain authoritative. This slice
reuses the implemented closed production Worker and introduces no runtime
dependency, new public test/backend seam or Migration Facade control.

The [coverage inventory](../capabilities/profiles/T21-hardened-worker/coverage.json)
and [137 mandatory named cases](../capabilities/profiles/T21-hardened-worker/mandatory-tests.txt)
freeze the shared public IN_PROCESS/HARDENED_WORKER contract, actual process
and protocol probes, existing Facade and artifact contracts, and retained
launcher/fault/prerequisite observations. `T21ContractTestCommand` checks the
runner's declared names before execution and every actual started/finished
case afterward. Missing, duplicate, ignored, assumption-skipped or failed
cases reject certification even when an aggregate JUnit count looks successful.

The real framed endpoint exercises missing/wrong keys, tampering, replay,
versions/opcodes, truncated/negative/excess/overflowing lengths, trailing data,
forbidden selectors and serialization. Inclusive message and aggregate modeled
memory boundaries and first excess use actual frames and unchanged sentinel
products. Public workflows supply ordered Commands, Query barriers, successful
batch-prefix visibility, first-failure and one-shot-input precedence. Both
existing batch transports remain available; private batching shape is not the
public behavioral oracle.

Retained process witnesses contain the actual Java executable/vendor/build,
`prlimit` identity, child arguments, cleared environment, production dependency
JAR hashes, first-party inventory hashes/counts, effective CPU and descriptor
limits and owner-only root permissions. The child is separately identified.
The release-file implementor and actual `java.vendor` property are retained
separately: this Temurin JDK 8 reports `Temurin` at runtime and `Eclipse Adoptium`
in its release file. Exact executable/image/build identities bind both observations.
At the public `TARGET_COMMITTED` progress boundary it is already absent while
the owned root still exists; a hard three-second elapsed control likewise
observes child termination before owned-root cleanup. Faults retain safe
`WORKER_TERMINATED` or `WORKER_PROTOCOL_REJECTED` diagnostics, ordered receipts,
unchanged pre-publication Targets and no live owned child/data afterward.
The shared contract additionally checks cancellation/deadline and cleanup
failure, checked-primary precedence, caller exception identity, committed
receipts, caller ownership and expired Session views.

Actual staged files are observed with mode 0600. Actual Worker probes deny
filesystem escape, parent-prepared hard/symbolic-link access and cross-transaction
marker access, descendants, INET, reflection/native escape and, on JDK 17/21,
Unix-domain connect/listen. Paired permitted controls exercise real Linux
filesystem, link, descendant, INET and AF_UNIX operations outside the Worker.
An acceptance-only JVM separately installs the exact shipped Security Manager
and qualifies link creation and Unix permission denial. It is explicitly
distinct from the production Worker. Java Unix-domain transport APIs are
unavailable on JDK 8/11; those records classify absence rather than claim an
attempted socket denial. The closed registry and denied descendant/native
escape remain enforced. No acceptance class enters the Worker's runtime closure.

Actual unsupported OS and missing-Java controls and a container-bound,
non-executable `/usr/bin/prlimit` control require `WORKER_UNAVAILABLE`, unchanged
Targets, unattempted receipts and empty owned storage. The positive environment
keeps its original launcher. Certification requires Linux POSIX permissions,
the exact unshaded production artifacts/dependencies, executable absolute Java
and `prlimit`, and an installable Worker Security Manager. The tested JDK and
dependency paths lie outside the transaction parent. Unsupported configurations
receive no inferred certification and no automatic IN_PROCESS fallback.

The five chains remain separate. Contract owns boundary/lifetime controls.
Seven actual resource-free, one-page published PDF outcomes use the original
qualified T03 syntax, 22-rule standards union, public graph/one-page semantics
and 144-DPI opaque-sRGB/white/AE-zero/zero-fuzz visual profile. qpdf is the syntax
producer; pdfcpu and Arlington retain separate raw standards findings and
rule-specific detected negatives. PDFium and ImageMagick supply the independent
visual oracle. Known-invalid PDF, two-page and one-pixel controls remain
detected. These outcomes establish the document predicates relevant to this
boundary, without certifying unrelated PDF capabilities.

Existing public Facade operations execute IN_PROCESS and retain reopen,
ownership, failure and ordered publication observations. Worker transport,
launch policy and ordering controls have no Reference Suite counterpart; the
Native-only decision stays justified. Underlying document behavior retains
its matching Facade obligations.

`T21EvidenceCommand` pins inputs/tools before and after observation and seals
original bytes. Collection checks every retained manifest, complete staged
candidate/contract/harness/configuration closure, original environment and
launcher identities, then executes fresh required tests, prerequisite controls
and recorder in the exact image. Original public properties, controls, producer
findings and products must match live observations. PDF trailer IDs use only the
existing documented normalization while exact originals remain retained; each
original raster is requalified with the pinned external pixel comparator.
For process witnesses only observed PIDs, private roots and output locations
are normalized; arguments, runtime hashes, limits and observed denials are
checked before comparison. A resealed manifest or changed producer label cannot
repair altered findings, missing rules, cases, products, tools or identities.

The required scope is exactly the four Ubuntu 24.04/Linux x86-64 images in
`capabilities/foundation-environments.yaml`, Temurin builds `1.8.0_502-b07`,
`11.0.32+9`, `17.0.20+8` and `21.0.12+8-LTS`. Current certification requires all
four HARDENED_WORKER tuples and their five producer-bound records in the
Foundation evidence index, plus satisfied `transactions` and `limits`.
Windows x86-64 and macOS x86-64/arm64 remain uncertified and nonblocking for
Foundation 0.1.0. Modeled Folio memory/storage accounting, JVM heap/direct
ceilings, CPU/descriptors and the monotonic Worker watchdog retain distinct
guarantees. This does not establish full process RSS/native-memory, kernel,
container, arbitrary-bytecode or physical secure-erasure guarantees. #83
recovery/scale and other downstream capabilities remain experimental.

Select the explicit validated HarfBuzz and Ubuntu Python installations and
writable Maven cache. Then use fresh output directories:

```sh
python3 -B scripts/t03-foundation.py stage
python3 -B scripts/t03-foundation.py certify capabilities/evidence/foundation/T82-final/worker --obligation worker
python3 -B scripts/t03-foundation.py collect capabilities/evidence/foundation/T82-final/worker/jdk17-hardened_worker/observations --obligation worker --execution-profile HARDENED_WORKER
```

Freeze final sources and proposed completed inventories before staging. Refresh
affected predecessors in order: transactions, values, pages, metadata,
annotations, text, images, incremental, password-baseline,
password-clear-metadata, password-attachments, limits, then worker. Reuse only
records whose candidate, contract, environment, configuration and transitive
identities still match. Any subsequent source/contract/artifact/harness/tool
change requires restaging and affected recertification. Preserve failed attempts
and original historical evidence. Final full verification, inventory gates,
live readiness and baseline-relative Standards/Spec review remain mandatory;
the existing JDK build matrix additionally applies to shipped-code/build changes.
This unsigned local route performs no publication or tracker mutation.
