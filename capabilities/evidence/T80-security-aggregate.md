# T80 security aggregate disposition

Status: `compatible`
Capability: `document.version-password-security`
Acceptance Profile: `T16-pdf-version-password-security`
Release train: `0.1.0-SNAPSHOT`

The existing Foundation aggregate `security` contains exactly password-baseline,
password-clear-metadata and password-attachments. Their compatible behaviors,
external gates and required dependencies are represented together. Aggregate
completeness requires current passing receipts for all three on the same candidate
and applicable environments; [Foundation evidence](../foundation-evidence.yaml)
and generated readiness exclusively establish that result. The four member chains
are retained separately. No fourth security slice is introduced. Historical T16,
T78 and T79 records retain their original meaning and cannot certify new artifacts.
Other incomplete Foundation obligations may block release readiness. Parent #33
release publication remains outside issue #80.
