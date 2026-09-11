# T73 metadata visual evidence index

Capability: `document.metadata.outlines-destinations-attachments`
Acceptance Profile: `T11-metadata-outlines-destinations-attachments`
Profile record: `capabilities/evidence/T11-metadata-outlines-destinations-attachments.md`
Release train: `0.1.0-SNAPSHOT`
Chain: `visual`
Result: `pass`
Producer kind: `external-tool`
Producer: `pdfium-cli`
Producer version: `v0.11.2-pdfium-chromium-7881`

Pinned PDFium renders every page of each exact Native and Facade product at
144 DPI in the frozen RGB policy. ImageMagick AE compares those pixels with
independently authored expected rasters using zero fuzz and threshold zero; a
secondary renderer must agree and a real one-pixel control must fail.

The [current Foundation inventory](../foundation-evidence.yaml) is the authority
for the eight candidate-specific environment/execution records, exact raster
findings, renderer identities and controls. This index cannot pre-certify a
later candidate. The frozen procedure is in the
[T11 certification contract](../../docs/t11-certification.md).
