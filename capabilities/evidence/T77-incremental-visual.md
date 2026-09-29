# T77 incremental visual evidence index

Capability: `document.incremental-signature.protect`
Acceptance Profile: `T15-incremental-signature-protection`
Profile record: `capabilities/evidence/T77-incremental-signature-certification.md`
Release train: `0.1.0-SNAPSHOT`
Chain: `visual`
Result: `pass`
Producer kind: `external-tool`
Producer: `pdfium-cli`
Producer version: `v0.11.2-pdfium-chromium-7881`

PDFium renders all actual pages with annotations at 144 DPI. Pinned ImageMagick compares authored sRGB grids with exact AE=0 and fuzz=0%; changed-paint and one-pixel controls qualify the chain.

The [T15 profile](../../docs/t15-certification.md) fixes scope and controls.
The [Foundation authority](../foundation-evidence.yaml) must bind fresh raw
findings for all eight required Ubuntu/JDK/Native-mode tuples to the actual
candidate, contract, artifacts, corpus, configuration and observed environment.
Facade execution is separately recorded as IN_PROCESS. Missing or altered
receipts reject; this index does not certify another candidate. Historical
T15 syntax-only receipts remain unchanged.
