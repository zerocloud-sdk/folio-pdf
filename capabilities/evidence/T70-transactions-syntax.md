# T70 transactions syntax evidence index

Capability: `document.blank.create-publish-reopen`
Acceptance Profile: `T03-document-workflow-transaction`
Profile record: `capabilities/evidence/T03-document-workflow-transaction.md`
Release train: `0.1.0-SNAPSHOT`
Chain: `syntax`
Result: `pass`
Producer kind: `external-tool`
Producer: `qpdf`
Producer version: `12.4.0`

qpdf checks physical serialization; the negative non-PDF must be rejected.

The [current Foundation inventory](../foundation-evidence.yaml) references each
separate syntax record for all eight pinned environment/execution tuples. Those
records bind the exact candidate, contract, environment, execution configuration,
raw reports and negative controls. This index is not a substitute for their hash
validation and cannot pre-certify a later candidate. See the
[certification scope and commands](../../docs/t03-certification.md).
