# T78 baseline semantic evidence index

Capability: `document.version-password-security.baseline`
Acceptance Profile: `T32-password-baseline`
Profile record: `capabilities/evidence/T78-password-baseline.md`
Release train: `0.1.0-SNAPSHOT`
Chain: `semantic`
Result: `pass`
Producer kind: `project-test`
Producer: `folio-pdf-t78`
Producer version: `0.1.0`

Independent qpdf graph and decoded-content projections are compared with literal authored versions, security dictionaries, page/title hashes and public reopened observations. Altered public observations and page paint must fail.

The [profile](../../docs/t78-certification.md) fixes the observation and controls.
The [Foundation authority](../foundation-evidence.yaml) must bind all eight
actual tuples to the current candidate and original ciphertext artifacts.
This index does not certify another candidate or replace its raw reports.
