# T83 recovery and bounded scale standards evidence

Capability: `document.hardened-worker.recovery-scale`
Acceptance Profile: `T22-worker-recovery-scale`
Profile record: `capabilities/evidence/T22-worker-recovery-scale.md`
Release train: `0.1.0-SNAPSHOT`
Chain: `standards`
Result: `pass`
Producer kind: `external-tool`
Producer: `arlington`
Producer version: `0.81`

Pinned pdfcpu and Arlington retain separate original findings and detected rule-specific negatives for the qualified T03 22-rule blank-document union on every actual workload PDF. The actual 5000-page traversal is retained within the finite T22 acceptance budget. No font/layout or broader conformance predicate is claimed.

Current candidate certification is defined by the
[Foundation evidence index](../foundation-evidence.yaml). Every record binds the
final candidate, contracts, complete staged harness/execution settings, exact
immutable environment and tool/native/launcher/runtime identities. Collection
checks original artifacts/findings/controls against fresh execution. Missing or
altered bytes, stale identities, omitted profiles/cases or skips reject PASS.
Historical and failed attempts retain their original bytes.

| Actual Ubuntu 24.04 / Linux x86-64 tuple | Native / Facade mode | Final record |
| --- | --- | --- |
| ubuntu-24.04-linux-x86-64-jdk8 | HARDENED_WORKER / IN_PROCESS | [record](foundation/T83-final-r20/recovery/jdk8-hardened_worker/standards.yaml) |
| ubuntu-24.04-linux-x86-64-jdk11 | HARDENED_WORKER / IN_PROCESS | [record](foundation/T83-final-r20/recovery/jdk11-hardened_worker/standards.yaml) |
| ubuntu-24.04-linux-x86-64-jdk17 | HARDENED_WORKER / IN_PROCESS | [record](foundation/T83-final-r20/recovery/jdk17-hardened_worker/standards.yaml) |
| ubuntu-24.04-linux-x86-64-jdk21 | HARDENED_WORKER / IN_PROCESS | [record](foundation/T83-final-r20/recovery/jdk21-hardened_worker/standards.yaml) |

[Coverage inventory](../profiles/T22-worker-recovery-scale/coverage.json),
[named-case authority](../profiles/T22-worker-recovery-scale/mandatory-tests.txt),
[certification contract](../../docs/t22-certification.md).

Only these four Linux/JDK tuples are certified. Windows/macOS remain uncertified.
Modeled Folio-owned memory/storage does not establish JVM heap, RSS, native-memory,
kernel/container or physical-erasure guarantees. Recovery is finite,
environment-local and caller-driven; no durable/automatic/distributed recovery or
cross-Target atomicity is claimed. Other Foundation slices retain their gates.
