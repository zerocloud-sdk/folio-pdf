# T22 Worker recovery and bounded scale certification contract

Status: `compatible`
Capability: `document.hardened-worker.recovery-scale`
Acceptance Profile: `T22-worker-recovery-scale`
Release train: `0.1.0-SNAPSHOT`

Issue #83 certifies the bounded Native recovery and actual scale workloads under
[the current T22 contract](../../docs/t22-certification.md). Current candidate
identity and certification are exclusively defined by
[the Foundation evidence index](../foundation-evidence.yaml), with all previously
satisfied prerequisites refreshed for the same candidate. A status label alone
never supplies missing chain records.

The [coverage inventory](../profiles/T22-worker-recovery-scale/coverage.json) and
[180 mandatory named cases](../profiles/T22-worker-recovery-scale/mandatory-tests.txt)
require all three actual scale profiles and every T21 boundary case with zero
ignored tests, assumptions or duplicates. Public lookup, ordered receipts,
uncertain acknowledgement, terminal partial publication, recoverable retry,
real interruption/input failures and no final replay use the existing public
Workflow and protocol/process boundaries. Actual Facade document operations
remain IN_PROCESS; recovery/resource controls remain Native only.
The interruption regression qualifies the preserved caller flag before its
own sentinel/storage reads, clearing and restoring it only for that fixture
inspection because Java 8 NIO rejects reads on an interrupted thread.

The final candidate requires the four exact Ubuntu 24.04/Linux x86-64 Temurin
JDK 8/11/17/21 HARDENED_WORKER environments and twenty separate records:
[syntax](T83-recovery-syntax.md), [standards](T83-recovery-standards.md),
[semantic](T83-recovery-semantic.md), [visual](T83-recovery-visual.md), and
[contract](T83-recovery-contract.md), one of each per environment. Full actual
consumed input is retained in lossless gzip; the actual 5,000-page and
concurrency PDFs are retained. Lengths, hashes, constructions, full finite
policies, modeled high-water/spill counters, times, ownership and cleanup bind
original test artifacts/stdout and fresh observed execution.

The qualified T03 22-rule blank-document standards predicates apply to every
actual workload PDF, with original detected negatives. Independent semantics
inspect every blank page. Visual evidence compares actual pages 1, 2,500 and
5,000 and each other actual one-page PDF using the original authored white
144-DPI opaque-sRGB AE-zero/zero-fuzz profile and controls. This declared sample
is not full-document visual or #88 rendering certification. Modeled Folio
memory/storage is not JVM heap, RSS, native memory, kernel/container enforcement
or physical erasure. Windows/macOS remain explicitly uncertified. Durable,
distributed or automatic recovery and cross-Target atomicity remain excluded.

## Historical implementation observations

The [original September implementation record](T22-worker-recovery-scale-historical.md)
is preserved verbatim. Its experimental status, GraalVM environment and discarded
artifacts describe that historical fixed point and do not certify or override the
current candidate.
