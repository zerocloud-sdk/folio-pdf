# T74 annotation visual evidence index

Capability: `document.annotations-actions.manage`
Acceptance Profile: `T12-annotations-document-actions`
Profile record: `capabilities/evidence/T12-annotations-document-actions.md`
Release train: `0.1.0-SNAPSHOT`
Chain: `visual`
Result: `pass`
Producer kind: `external-tool`
Producer: `pdfium-cli`
Producer version: `v0.11.2-pdfium-chromium-7881`

Pinned PDFium projects the appearances of each unchanged product into an acceptance-only PDF and renders every page at 144 DPI, including standalone Widget appearance. Both hashes and commands are retained. Original opaque 240 by 200 sRGB rasters use zero ImageMagick AE/fuzz and zero secondary-renderer disagreement; PDFBox renders the original PDF. Actual wrong-color, wrong-placement and lost-retained-paint products must fail. Original reference PDFs independently qualify all 26 source/product page expectations. A separate one-pixel original-raster control must measure AE=1 while the identical positive control measures AE=0; the frozen threshold stays zero.

The [current Foundation inventory](../foundation-evidence.yaml) is the authority
for the eight candidate-specific environment/execution records, exact product
and tool identities, raw reports and controls. This index cannot pre-certify
a later candidate. The [frozen T12 contract](../../docs/t12-certification.md)
defines the procedure and boundaries.
