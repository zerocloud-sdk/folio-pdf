# T81 limits visual evidence

Capability: `document.hostile-input-limits`
Acceptance Profile: `T20-hostile-input-limits`
Profile record: `capabilities/evidence/T20-hostile-input-limits.md`
Release train: `0.1.0-SNAPSHOT`
Chain: `visual`
Result: `pass`
Producer kind: `external-tool`
Producer: `pdfium-cli`
Producer version: `v0.11.2-pdfium-chromium-7881`

Qualified observer: PDFium v0.11.2/chromium-7881 and ImageMagick 7.1.2-30.

Independent PDFium renders every positive at 144 DPI, opaque sRGB and white background. The pinned ImageMagick comparator requires AE zero with zero fuzz, and must detect the retained one-pixel control. Collection compares every original raster with the live counterpart and retains its external command and result.

These are the complete initial qualification records, retained with their original hashes. Current certification is defined by the [Foundation evidence index](../foundation-evidence.yaml); later source, contract or artifact changes invalidate earlier candidate identities.

| Actual Ubuntu 24.04 / Linux x86-64 tuple | Native / Facade mode | Original record |
| --- | --- | --- |
| ubuntu-24.04-linux-x86-64-jdk8 | IN_PROCESS / IN_PROCESS | [record](foundation/T81-initial/limits-envelope-r1/jdk8-in_process/visual.yaml) |
| ubuntu-24.04-linux-x86-64-jdk11 | IN_PROCESS / IN_PROCESS | [record](foundation/T81-initial/limits-envelope-r1/jdk11-in_process/visual.yaml) |
| ubuntu-24.04-linux-x86-64-jdk17 | IN_PROCESS / IN_PROCESS | [record](foundation/T81-initial/limits-envelope-r1/jdk17-in_process/visual.yaml) |
| ubuntu-24.04-linux-x86-64-jdk21 | IN_PROCESS / IN_PROCESS | [record](foundation/T81-initial/limits-envelope-r1/jdk21-in_process/visual.yaml) |

[Closed coverage inventory](../profiles/T20-hostile-input/coverage.json), [certification contract](../../docs/t20-certification.md).
