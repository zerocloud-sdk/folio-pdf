# T73 metadata semantic evidence index

Capability: `document.metadata.outlines-destinations-attachments`
Acceptance Profile: `T11-metadata-outlines-destinations-attachments`
Profile record: `capabilities/evidence/T11-metadata-outlines-destinations-attachments.md`
Release train: `0.1.0-SNAPSHOT`
Chain: `semantic`
Result: `pass`
Producer kind: `project-test`
Producer: `folio-pdf-t11`
Producer version: `0.1.0`

Independent reopened observations compare each Native and fixed-IN_PROCESS
Facade product with the frozen corpus: exact information and XMP values,
unknown-content preservation, outline shape, destinations and nullable
operands, attachment metadata and independently expected payload hashes, and
merge/split retargeting and collision behavior. Wrong-target, wrong-operand,
wrong-packet, wrong-payload and wrong-retained-value controls must fail. The
same record retains adversarial XML access canaries, malformed-packet outcomes,
and Native and Facade signed-document protection evidence.

The [current Foundation inventory](../foundation-evidence.yaml) is the authority
for the eight candidate-specific environment/execution records. It also binds
the complete 73-test public consumer and actual-jar contract run for each tuple.
This index cannot pre-certify a later candidate. The frozen procedure is in the
[T11 certification contract](../../docs/t11-certification.md).
