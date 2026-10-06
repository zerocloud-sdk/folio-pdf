# T81 limits semantic evidence

Capability: `document.hostile-input-limits`
Acceptance Profile: `T20-hostile-input-limits`
Profile record: `capabilities/evidence/T20-hostile-input-limits.md`
Release train: `0.1.0-SNAPSHOT`
Chain: `semantic`
Result: `pass`
Producer kind: `project-test`
Producer: `folio-pdf-t20`
Producer version: `0.1.0`

Qualified observer: folio-pdf-t20, independent project-owned expectations.

Independent T03 expectations check the published blank model through pinned external qpdf observations. The two-page control must fail. Original and live public outcomes, findings and verified file hashes must agree.

These are the complete initial qualification records, retained with their original hashes. Current certification is defined by the [Foundation evidence index](../foundation-evidence.yaml); later source, contract or artifact changes invalidate earlier candidate identities.

| Actual Ubuntu 24.04 / Linux x86-64 tuple | Native / Facade mode | Original record |
| --- | --- | --- |
| ubuntu-24.04-linux-x86-64-jdk8 | IN_PROCESS / IN_PROCESS | [record](foundation/T81-initial/limits-envelope-r1/jdk8-in_process/semantic.yaml) |
| ubuntu-24.04-linux-x86-64-jdk11 | IN_PROCESS / IN_PROCESS | [record](foundation/T81-initial/limits-envelope-r1/jdk11-in_process/semantic.yaml) |
| ubuntu-24.04-linux-x86-64-jdk17 | IN_PROCESS / IN_PROCESS | [record](foundation/T81-initial/limits-envelope-r1/jdk17-in_process/semantic.yaml) |
| ubuntu-24.04-linux-x86-64-jdk21 | IN_PROCESS / IN_PROCESS | [record](foundation/T81-initial/limits-envelope-r1/jdk21-in_process/semantic.yaml) |

[Closed coverage inventory](../profiles/T20-hostile-input/coverage.json), [certification contract](../../docs/t20-certification.md).
