# T07 PDFium visual evidence

Capability: `document.blank.create-publish-reopen`

Acceptance Profile: `T03-document-workflow-transaction`

Profile record: `capabilities/evidence/T03-document-workflow-transaction.md`

Release train: `0.1.0`

Chain: `visual`

Result: `indeterminate`

Producer kind: `external-tool`

Producer: `pdfium-cli`

Producer version: `v0.11.2-pdfium-chromium-7881`

PDFium CLI version: `v0.11.2`

PDFium engine version: `chromium-7881`

PDFium engine distribution: `pdfium-wasm.tgz`

PDFium engine distribution SHA-256: `added6e8ac024f71cb61cf2b77a205d178e2bdde2e4048fbcd916f68b7264d56`

PDFium distribution: `pdfium-webassembly-linux-amd64`

PDFium distribution SHA-256: `3ef3375c429ce665e834f933a028225bf28ac837695aaa69c6fc21facf6780ab`

PDFium executable SHA-256: `3ef3375c429ce665e834f933a028225bf28ac837695aaa69c6fc21facf6780ab`

PDFium distribution license: `MIT`

PDFium engine license: `BSD-3-Clause`

PDFium notice manifest: `docs/third-party/pdfium-cli-v0.11.2.md`

ImageMagick version: `unavailable`

ImageMagick distribution: `ImageMagick-7.1.2-30-gcc-x86_64.AppImage`

ImageMagick distribution SHA-256: `372af8a3fd61ef5f15c6331cde3e21f840eb165d8b533f34ed05d68736dd682e`

ImageMagick executable SHA-256: `372af8a3fd61ef5f15c6331cde3e21f840eb165d8b533f34ed05d68736dd682e`

ImageMagick distribution license: `ImageMagick`

ImageMagick notice manifest: `docs/third-party/imagemagick-7.1.2-30-appimage.md`

Implementation renderer version: `3.0.8`

Input ID-neutral SHA-256: `9fa3077b3f3ae8fae8789556bb1d5e77c934f0945bec3c2b2078b683b2bce219`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Expected raster SHA-256: `c7bbf03603aee1dba4ef80c9eee9abb93b7f3adfb94b84e4abf0203d78f89011`

PDFium raster SHA-256: `e9d0376a37a8578fe71c97d5503a1460cd2e97f8b563bf2b8a2ada84c3373705`

Implementation raster SHA-256: `64fa0753c83cf20bf83da68c476f741497d9cab65d55ee06e6f3745e426683d0`

Expected comparison AE: `unavailable`

Renderer agreement AE: `unavailable`

Review required: `false`

Final determination: `indeterminate`

## Visual profile

- Page box: effective CropBox; CropBox is absent, so MediaBox [0 0 612 792] points is used.
- DPI: `144`.
- Color policy: sRGB, opaque 8-bit RGB PNG; grayscale and alpha are disabled.
- Font policy: not applicable; the blank document has no text or font resources and uses no system fonts.
- Antialiasing policy: pinned PDFium default smoothing; no marks are present for antialiasing to affect.
- Background: opaque white (#ffffff).
- Raster dimensions: `1224x1584`.
- Comparison metric: ImageMagick absolute error count (AE) with fuzz 0 percent.
- Capability threshold: `0` changed pixels.
- Renderer-agreement threshold: `0` changed pixels.

## Findings and artifacts

- Input PDF: [`artifacts/T06-document-blank-output.pdf`](artifacts/T06-document-blank-output.pdf)
- Expected-raster authority: [`../expected/T03-document-blank-144dpi-srgb.png`](../expected/T03-document-blank-144dpi-srgb.png)
- PDFium notice manifest: [`docs/third-party/pdfium-cli-v0.11.2.md`](../../docs/third-party/pdfium-cli-v0.11.2.md)
- ImageMagick notice manifest: [`docs/third-party/imagemagick-7.1.2-30-appimage.md`](../../docs/third-party/imagemagick-7.1.2-30-appimage.md)
- Raster artifact: [`artifacts/T07-document-blank-expected.png`](artifacts/T07-document-blank-expected.png)
- Raster artifact: [`artifacts/T07-document-blank-pdfium.png`](artifacts/T07-document-blank-pdfium.png)
- Raster artifact: [`artifacts/T07-document-blank-implementation.png`](artifacts/T07-document-blank-implementation.png)
- Raw findings: [`artifacts/T07-document-blank-visual.txt`](artifacts/T07-document-blank-visual.txt)
- Expected pinned ImageMagick version `7.1.2-30`; observed `unavailable`.

ImageMagick receives only validated PNG raster paths in both comparison invocations; it is never given the PDF. Apache PDFBox Renderer is secondary disagreement evidence only and cannot make this chain pass.
