# T79 clear-metadata syntax chain

Capability: `document.version-password-security.clear-metadata`
Acceptance Profile: `T32-password-clear-metadata`
Profile record: `capabilities/evidence/T79-password-clear-metadata.md`
Release train: `0.1.0-SNAPSHOT`
Chain: `syntax`
Result: `pass`
Producer kind: `external-tool`
Producer: `qpdf-t78-r1`
Producer version: `12.4.0-folio-t78-r1`

Actual original encrypted inputs and Native/Facade products must pass syntax with exact credentials. The retained truncated-file control must fail.

The [T79 profile](../../docs/t79-certification.md) defines qualification and tool
limitations. Current source, contract, candidate and actual environment identities
are exclusively bound by [Foundation evidence](../foundation-evidence.yaml).
Prose labels, tool exit status and historical records cannot certify a changed
candidate. Native modes remain distinct; Facade is actual IN_PROCESS.
