# T79 clear-metadata standards chain

Capability: `document.version-password-security.clear-metadata`
Acceptance Profile: `T32-password-clear-metadata`
Profile record: `capabilities/evidence/T79-password-clear-metadata.md`
Release train: `0.1.0-SNAPSHOT`
Chain: `standards`
Result: `pass`
Producer kind: `external-tool`
Producer: `arlington`
Producer version: `0.81`

Original encryption dictionaries, filter roles and units, revision/version constraints, metadata-sensitive R4 keys, exact credentials, independent owner proof and every required AES-256 Perms prefix byte must agree. Required scope controls and unchanged baseline controls must be detected.

The [T79 profile](../../docs/t79-certification.md) defines qualification and tool
limitations. Current source, contract, candidate and actual environment identities
are exclusively bound by [Foundation evidence](../foundation-evidence.yaml).
Prose labels, tool exit status and historical records cannot certify a changed
candidate. Native modes remain distinct; Facade is actual IN_PROCESS.
