# T80 syntax aggregate-security chain

Capability: `document.version-password-security`
Acceptance Profile: `T16-pdf-version-password-security`
Profile record: `capabilities/evidence/T80-security-aggregate.md`
Release train: `0.1.0-SNAPSHOT`
Chain: `syntax`
Result: `pass`
Producer kind: `external-tool`
Producer: `qpdf-t80-r1`
Producer version: `12.4.0-folio-t80-r1`

Aggregate evidence derives from current receipts for exactly password-baseline, password-clear-metadata and password-attachments on the same candidate. Each existing member supplies this separate qualified chain; no fourth member or release-wide completion is inferred.

Current candidate/environment identities and actual passing observations belong exclusively to [Foundation evidence](../foundation-evidence.yaml). Prose labels or a changed/resealed finding cannot certify a candidate. The collector replays actual tools; Native modes remain distinct and Facade is actual IN_PROCESS.
