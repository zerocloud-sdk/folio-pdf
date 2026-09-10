# T72 pages syntax evidence index

Capability: `document.page.manipulate-merge-split`
Acceptance Profile: `T10-page-manipulation-merge-split`
Profile record: `capabilities/evidence/T10-page-manipulation-merge-split.md`
Release train: `0.1.0-SNAPSHOT`
Chain: `syntax`
Result: `pass`
Producer kind: `external-tool`
Producer: `qpdf`
Producer version: `12.4.0`

Pinned qpdf checks each exact edited, merged, left-split and right-split
Native and Facade PDF. A real non-PDF control must fail in every certification.

The [current Foundation inventory](../foundation-evidence.yaml) is the authority
for the eight candidate-specific environment/execution records. Those records
bind the exact candidate, contract, configuration, environment, products, raw
reports and negative controls together. This index cannot pre-certify a later
candidate. The frozen procedure is in the
[T10 certification contract](../../docs/t10-certification.md).
