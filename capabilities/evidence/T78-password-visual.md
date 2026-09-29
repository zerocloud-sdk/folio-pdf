# T78 baseline visual evidence index

Capability: `document.version-password-security.baseline`
Acceptance Profile: `T32-password-baseline`
Profile record: `capabilities/evidence/T78-password-baseline.md`
Release train: `0.1.0-SNAPSHOT`
Chain: `visual`
Result: `pass`
Producer kind: `external-tool`
Producer: `pdfium-cli`
Producer version: `v0.11.2-pdfium-chromium-7881`

Pinned PDFium at 144 DPI with annotations and ImageMagick AE=0, fuzz=0 compare products to independent original grids. Changed paint and one changed pixel must fail.

The [profile](../../docs/t78-certification.md) fixes the observation and controls.
The [Foundation authority](../foundation-evidence.yaml) must bind all eight
actual tuples to the current candidate and original ciphertext artifacts.
This index does not certify another candidate or replace its raw reports.
