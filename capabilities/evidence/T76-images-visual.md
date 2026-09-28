# T76 image extraction visual evidence index

Capability: `document.images-resources.extract`
Acceptance Profile: `T14-image-resource-extraction`
Profile record: `capabilities/evidence/T76-image-resource-certification.md`
Release train: `0.1.0-SNAPSHOT`
Chain: `visual`
Result: `pass`
Producer kind: `external-tool`
Producer: `pdfium-cli`
Producer version: `v0.11.2-pdfium-chromium-7881`

Pinned PDFium renders the eight conforming products at 144 DPI against authored literal pixel grids. Pinned ImageMagick AE uses fuzz 0 and an exact zero threshold; original rasters, difference images and raw metrics are retained. Complementing an authored sample rectangle yields AE 800; changing one white pixel to black yields AE 1. Both controls must be detected. ImageMagick AE is channel-error magnitude, not a general changed-pixel count.

The [T14 contract](../../docs/t14-certification.md) defines the original corpus,
qualified controls and precise scope. Candidate-specific records are governed
by the [Foundation evidence authority](../foundation-evidence.yaml). Each of
the eight Ubuntu JDK/Native-profile tuples must bind actual source, artifact,
environment, tool and configuration identities. Missing or altered receipts
reject; this index does not pre-certify another candidate. Historical T14
implementation and syntax-only receipts are unchanged.
