# T82 Worker visual evidence

Capability: `document.hardened-worker`
Acceptance Profile: `T21-hardened-worker`
Profile record: `capabilities/evidence/T21-hardened-worker.md`
Release train: `0.1.0-SNAPSHOT`
Chain: `visual`
Result: `pass`
Producer kind: `external-tool`
Producer: `pdfium-cli`
Producer version: `v0.11.2-pdfium-chromium-7881`

The pinned PDFium executable renders each of the seven actual outcomes. The qualified ImageMagick comparator applies the T03 144-DPI opaque-sRGB/white/AE-zero/zero-fuzz profile against the independent expected raster and detects the one-pixel control. PDFBox is not the visual oracle.

Current candidate certification is defined by the
[Foundation evidence index](../foundation-evidence.yaml). Each record binds the
actual candidate, contract, immutable environment, complete execution inputs,
tools, original findings and detected controls. Collection obtains fresh
observations and rejects altered supporting data, missing mandatory coverage,
skips, forged producer identities or mismatched artifact/configuration identities.
Historical and failed attempts retain their original bytes.

| Actual Ubuntu 24.04 / Linux x86-64 tuple | Native / Facade mode | Final record |
| --- | --- | --- |
| ubuntu-24.04-linux-x86-64-jdk8 | HARDENED_WORKER / IN_PROCESS | [record](foundation/T82-final/worker/jdk8-hardened_worker/visual.yaml) |
| ubuntu-24.04-linux-x86-64-jdk11 | HARDENED_WORKER / IN_PROCESS | [record](foundation/T82-final/worker/jdk11-hardened_worker/visual.yaml) |
| ubuntu-24.04-linux-x86-64-jdk17 | HARDENED_WORKER / IN_PROCESS | [record](foundation/T82-final/worker/jdk17-hardened_worker/visual.yaml) |
| ubuntu-24.04-linux-x86-64-jdk21 | HARDENED_WORKER / IN_PROCESS | [record](foundation/T82-final/worker/jdk21-hardened_worker/visual.yaml) |

[Coverage inventory](../profiles/T21-hardened-worker/coverage.json),
[named-case authority](../profiles/T21-hardened-worker/mandatory-tests.txt),
[certification contract](../../docs/t21-certification.md).

Only these four Linux/JDK tuples are certified. Windows and macOS remain
uncertified; #83 recovery/scale remains experimental. Modeled owned accounting,
heap/direct ceilings, CPU/descriptors and forced elapsed termination remain
distinct from full RSS/native-memory, kernel/container or physical-erasure claims.
