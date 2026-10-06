# T81 limits syntax evidence

Capability: `document.hostile-input-limits`
Acceptance Profile: `T20-hostile-input-limits`
Profile record: `capabilities/evidence/T20-hostile-input-limits.md`
Release train: `0.1.0-SNAPSHOT`
Chain: `syntax`
Result: `pass`
Producer kind: `external-tool`
Producer: `qpdf`
Producer version: `12.4.0`

Qualified observer: qpdf 12.4.0.

The actual pinned qpdf executable checks all eight original published PDF outcomes. The retained truncated-file control must fail.

These are the complete initial qualification records, retained with their original hashes. Current certification is defined by the [Foundation evidence index](../foundation-evidence.yaml); later source, contract or artifact changes invalidate earlier candidate identities.

| Actual Ubuntu 24.04 / Linux x86-64 tuple | Native / Facade mode | Original record |
| --- | --- | --- |
| ubuntu-24.04-linux-x86-64-jdk8 | IN_PROCESS / IN_PROCESS | [record](foundation/T81-initial/limits-envelope-r1/jdk8-in_process/syntax.yaml) |
| ubuntu-24.04-linux-x86-64-jdk11 | IN_PROCESS / IN_PROCESS | [record](foundation/T81-initial/limits-envelope-r1/jdk11-in_process/syntax.yaml) |
| ubuntu-24.04-linux-x86-64-jdk17 | IN_PROCESS / IN_PROCESS | [record](foundation/T81-initial/limits-envelope-r1/jdk17-in_process/syntax.yaml) |
| ubuntu-24.04-linux-x86-64-jdk21 | IN_PROCESS / IN_PROCESS | [record](foundation/T81-initial/limits-envelope-r1/jdk21-in_process/syntax.yaml) |

[Closed coverage inventory](../profiles/T20-hostile-input/coverage.json), [certification contract](../../docs/t20-certification.md).
