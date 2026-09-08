# T70 transactions visual evidence index

Capability: `document.blank.create-publish-reopen`
Acceptance Profile: `T03-document-workflow-transaction`
Profile record: `capabilities/evidence/T03-document-workflow-transaction.md`
Release train: `0.1.0-SNAPSHOT`
Chain: `visual`
Result: `pass`
Producer kind: `external-tool`
Producer: `pdfium-cli`
Producer version: `v0.11.2-pdfium-chromium-7881`

PDFium/ImageMagick compare the original opaque sRGB 144 DPI raster at AE 0, with zero fuzz and a one-pixel negative.

The [current Foundation inventory](../foundation-evidence.yaml) references each
separate visual record for all eight pinned environment/execution tuples. Those
records bind the exact candidate, contract, environment, execution configuration,
raw reports and negative controls. This index is not a substitute for their hash
validation and cannot pre-certify a later candidate. See the
[certification scope and commands](../../docs/t03-certification.md).
