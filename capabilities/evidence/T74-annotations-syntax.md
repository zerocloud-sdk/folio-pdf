# T74 annotation syntax evidence index

Capability: `document.annotations-actions.manage`
Acceptance Profile: `T12-annotations-document-actions`
Profile record: `capabilities/evidence/T12-annotations-document-actions.md`
Release train: `0.1.0-SNAPSHOT`
Chain: `syntax`
Result: `pass`
Producer kind: `external-tool`
Producer: `qpdf`
Producer version: `12.4.0`

Pinned qpdf checks all eight product cases through both interfaces: created, changed, flattened, copied, merged, adopted, left and right. A truncated actual product must fail. Every check binds the unchanged product hash.

The [current Foundation inventory](../foundation-evidence.yaml) is the authority
for the eight candidate-specific environment/execution records, exact product
and tool identities, raw reports and controls. This index cannot pre-certify
a later candidate. The [frozen T12 contract](../../docs/t12-certification.md)
defines the procedure and boundaries.
