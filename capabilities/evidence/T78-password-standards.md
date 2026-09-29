# T78 baseline standards evidence index

Capability: `document.version-password-security.baseline`
Acceptance Profile: `T32-password-baseline`
Profile record: `capabilities/evidence/T78-password-baseline.md`
Release train: `0.1.0-SNAPSHOT`
Chain: `standards`
Result: `pass`
Producer kind: `external-tool`
Producer: `arlington`
Producer version: `0.81`

Unchanged Arlington with frozen input/output models, separate pdfcpu security supplement and pypdf/PyCryptodome credential/Perms checks provide qualified required-rule coverage. The original pdfcpu checks each private plaintext derivative. Every applicable rule and negative control must be observed.

The [profile](../../docs/t78-certification.md) fixes the observation and controls.
The [Foundation authority](../foundation-evidence.yaml) must bind all eight
actual tuples to the current candidate and original ciphertext artifacts.
This index does not certify another candidate or replace its raw reports.
