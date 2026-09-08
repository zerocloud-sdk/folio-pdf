# T71 values visual evidence index

Capability: `document.value.inspect-patch`
Acceptance Profile: `T09-document-value-inspection-patch`
Profile record: `capabilities/evidence/T09-document-value-inspection-patch.md`
Release train: `0.1.0-SNAPSHOT`
Chain: `visual`
Result: `pass`
Producer kind: `external-tool`
Producer: `pdfium-cli`
Producer version: `v0.11.2-pdfium-chromium-7881`

Independent PDFium rasters of the Source and all three products match the project-authored 144 DPI golden at zero-fuzz AE 0; a one-pixel control fails.

The [current Foundation inventory](../foundation-evidence.yaml) is the authority
for the eight candidate-specific environment/execution records. Their exact
candidate, contract, configuration, environment, products, raw reports and
negative controls must validate together. This index does not replace those
checks, certify other obligations, or pre-certify a later candidate.

The earlier [staged-product qualification](T71-prequalification/README.md)
retains its own observed scope and identity. The final requirements and commands
are in [the T09 certification contract](../../docs/t09-certification.md).
