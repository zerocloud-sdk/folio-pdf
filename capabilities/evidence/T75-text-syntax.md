# T75 extraction syntax evidence index

Capability: `document.text-structure.extract`
Acceptance Profile: `T13-text-logical-structure`
Profile record: `capabilities/evidence/T13-text-logical-structure.md`
Release train: `0.1.0-SNAPSHOT`
Chain: `syntax`
Result: `pass`
Producer kind: `external-tool`
Producer: `qpdf`
Producer version: `12.4.0`

Pinned qpdf checks all five frozen extraction products through Native and
Facade publication. Each report retains the unchanged input, process output,
exit status and original producer receipt. A truncated PDF control must fail.
The complete independent development qualification and original observations
are retained under [T75 delivery evidence](T75-delivery/README.md).

The [Foundation evidence authority](../foundation-evidence.yaml) governs actual
candidate-specific environment/execution records. This qualification index
does not certify a later candidate. The [T13 contract](../../docs/t13-certification.md)
requires eight actual Native tuples and records the Facade as IN_PROCESS.
