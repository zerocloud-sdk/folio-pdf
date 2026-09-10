# T72 pages visual evidence index

Capability: `document.page.manipulate-merge-split`
Acceptance Profile: `T10-page-manipulation-merge-split`
Profile record: `capabilities/evidence/T10-page-manipulation-merge-split.md`
Release train: `0.1.0-SNAPSHOT`
Chain: `visual`
Result: `pass`
Producer kind: `external-tool`
Producer: `pdfium-cli`
Producer version: `v0.11.2-pdfium-chromium-7881`

Independent PDFium renders every page of all eight Native and Facade products
against fixed project-authored 144 DPI opaque-sRGB rasters. ImageMagick uses
zero fuzz and AE 0; the secondary PDFBox renderer must also agree exactly. A
one-pixel change to the frozen expected raster must fail.

The [current Foundation inventory](../foundation-evidence.yaml) is the authority
for the eight candidate-specific environment/execution records. Those records
bind each per-page report, raster, tool identity, exact product and negative
control. This index cannot pre-certify a later candidate. The frozen procedure
is in the [T10 certification contract](../../docs/t10-certification.md).
