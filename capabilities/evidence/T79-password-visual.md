# T79 clear-metadata visual chain

Capability: `document.version-password-security.clear-metadata`
Acceptance Profile: `T32-password-clear-metadata`
Profile record: `capabilities/evidence/T79-password-clear-metadata.md`
Release train: `0.1.0-SNAPSHOT`
Chain: `visual`
Result: `pass`
Producer kind: `external-tool`
Producer: `pdfium-cli`
Producer version: `v0.11.2-pdfium-chromium-7881`

Private authenticated derivatives render at 144 dpi with annotations; exact authored grids use AE=0 and zero fuzz. Scope-specific changed paint and unchanged baseline one-pixel controls must fail.

The [T79 profile](../../docs/t79-certification.md) defines qualification and tool
limitations. Current source, contract, candidate and actual environment identities
are exclusively bound by [Foundation evidence](../foundation-evidence.yaml).
Prose labels, tool exit status and historical records cannot certify a changed
candidate. Native modes remain distinct; Facade is actual IN_PROCESS.
