# T81 limits standards evidence

Capability: `document.hostile-input-limits`
Acceptance Profile: `T20-hostile-input-limits`
Profile record: `capabilities/evidence/T20-hostile-input-limits.md`
Release train: `0.1.0-SNAPSHOT`
Chain: `standards`
Result: `pass`
Producer kind: `external-tool`
Producer: `arlington`
Producer version: `0.81`

Qualified observer: Arlington 0.81 and pdfcpu 0.15.0.

The actual pinned Arlington and strict pdfcpu observations reuse the qualified T03 core model and every applicable rule-specific control. All eight positives are resource-free, one-page PDF 1.7 outcomes. No new output feature expands the required model.

These are the complete initial qualification records, retained with their original hashes. Current certification is defined by the [Foundation evidence index](../foundation-evidence.yaml); later source, contract or artifact changes invalidate earlier candidate identities.

| Actual Ubuntu 24.04 / Linux x86-64 tuple | Native / Facade mode | Original record |
| --- | --- | --- |
| ubuntu-24.04-linux-x86-64-jdk8 | IN_PROCESS / IN_PROCESS | [record](foundation/T81-initial/limits-envelope-r1/jdk8-in_process/standards.yaml) |
| ubuntu-24.04-linux-x86-64-jdk11 | IN_PROCESS / IN_PROCESS | [record](foundation/T81-initial/limits-envelope-r1/jdk11-in_process/standards.yaml) |
| ubuntu-24.04-linux-x86-64-jdk17 | IN_PROCESS / IN_PROCESS | [record](foundation/T81-initial/limits-envelope-r1/jdk17-in_process/standards.yaml) |
| ubuntu-24.04-linux-x86-64-jdk21 | IN_PROCESS / IN_PROCESS | [record](foundation/T81-initial/limits-envelope-r1/jdk21-in_process/standards.yaml) |

[Closed coverage inventory](../profiles/T20-hostile-input/coverage.json), [certification contract](../../docs/t20-certification.md).
