# T78 baseline syntax evidence index

Capability: `document.version-password-security.baseline`
Acceptance Profile: `T32-password-baseline`
Profile record: `capabilities/evidence/T78-password-baseline.md`
Release train: `0.1.0-SNAPSHOT`
Chain: `syntax`
Result: `pass`
Producer kind: `external-tool`
Producer: `qpdf-t78-r1`
Producer version: `12.4.0-folio-t78-r1`

The separately pinned qpdf supplement checks original ciphertext through normal password authentication; warning-free positives and a detected truncated control are mandatory.

The [profile](../../docs/t78-certification.md) fixes the observation and controls.
The [Foundation authority](../foundation-evidence.yaml) must bind all eight
actual tuples to the current candidate and original ciphertext artifacts.
This index does not certify another candidate or replace its raw reports.
