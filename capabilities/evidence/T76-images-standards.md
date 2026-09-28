# T76 image extraction standards evidence index

Capability: `document.images-resources.extract`
Acceptance Profile: `T14-image-resource-extraction`
Profile record: `capabilities/evidence/T76-image-resource-certification.md`
Release train: `0.1.0-SNAPSHOT`
Chain: `standards`
Result: `pass`
Producer kind: `external-tool`
Producer: `pdfcpu`
Producer version: `0.15.0`

Strict offline pdfcpu checks 46 qualified core rules. Independent image predicates over pinned qpdf graphs cover 28 additional image/font/resource rule groups, with 56 original illegal controls. All 102 controls and the complete positive rule union are mandatory. This is a closed extraction declaration profile, not general PDF conformance.

The [T14 contract](../../docs/t14-certification.md) defines the original corpus,
qualified controls and precise scope. Candidate-specific records are governed
by the [Foundation evidence authority](../foundation-evidence.yaml). Each of
the eight Ubuntu JDK/Native-profile tuples must bind actual source, artifact,
environment, tool and configuration identities. Missing or altered receipts
reject; this index does not pre-certify another candidate. Historical T14
implementation and syntax-only receipts are unchanged.
