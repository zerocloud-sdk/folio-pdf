# T12 PDFium visual evidence

Capability: `document.annotations-actions.manage`

Acceptance Profile: `T12-annotations-document-actions`

Profile record: `capabilities/evidence/T12-annotations-document-actions.md`

Release train: `0.1.0`

Chain: `visual`

Result: `pass`

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

ImageMagick version: `7.1.2-30`

ImageMagick distribution: `ImageMagick-7.1.2-30-gcc-x86_64.AppImage`

ImageMagick distribution SHA-256: `372af8a3fd61ef5f15c6331cde3e21f840eb165d8b533f34ed05d68736dd682e`

ImageMagick executable SHA-256: `372af8a3fd61ef5f15c6331cde3e21f840eb165d8b533f34ed05d68736dd682e`

ImageMagick distribution license: `ImageMagick`

ImageMagick notice manifest: `docs/third-party/imagemagick-7.1.2-30-appimage.md`

Implementation renderer version: `3.0.8`

Input exact SHA-256: `668acb8e36329c6639dd72361f35b5539de55444694767a85777563cd50af758`

Input hash policy: `SHA-256 of the exact unmodified PDF bytes`

Expected raster SHA-256: `56427ba6a35f238d418d29273a31620e315f8a40ab38e7e5e1393fe978f2af7b`

PDFium raster SHA-256: `62cda3610b5ea8e8ea823470e1f5e1911811c39ebef20b92c82dcede0f3870f8`

Implementation raster SHA-256: `ac675320671b7f613a7f850e44a8e7624cb6cee2d5534349ecc61ad9ccead8f1`

Expected comparison AE: `0`

Renderer agreement AE: `0`

Review required: `false`

Final determination: `pass`

## Visual profile

- Page box: effective CropBox [0 0 120 100] points.
- Page selection: `2` of `2`.
- DPI: `144`.
- Color policy: sRGB, opaque 8-bit RGB PNG after compositing over opaque white.
- Font policy: not applicable; the artifact has no text or font resources and uses no system fonts.
- Antialiasing policy: pinned PDFium default smoothing; vector edges are axis-aligned.
- Background: opaque white (#ffffff).
- Raster dimensions: `240x200`.
- Comparison metric: ImageMagick AE magnitude with fuzz 0 percent; exact changed RGB pixels additionally enforce the same bounds.
- Capability threshold: `0` changed pixels.
- Renderer-agreement threshold: `0` changed pixels.

## Findings and artifacts

- Input PDF: [`annotations.pdf`](annotations.pdf)
- Expected-raster authority: [`../expected/right-page-2.png`](../../../../../../profiles/T12-annotations/expected/right-page-2.png)
- PDFium notice manifest: [`docs/third-party/pdfium-cli-v0.11.2.md`](../../../../../../../docs/third-party/pdfium-cli-v0.11.2.md)
- ImageMagick notice manifest: [`docs/third-party/imagemagick-7.1.2-30-appimage.md`](../../../../../../../docs/third-party/imagemagick-7.1.2-30-appimage.md)
- Raster artifact: [`page-2-expected.png`](page-2-expected.png)
- Raster artifact: [`page-2-pdfium.png`](page-2-pdfium.png)
- Raster artifact: [`page-2-implementation.png`](page-2-implementation.png)
- Raster artifact: [`page-2-difference.png`](page-2-difference.png)
- Raster artifact: [`page-2-renderer-difference.png`](page-2-renderer-difference.png)
- Raw findings: [`page-2-visual.txt`](page-2-visual.txt)
- The PDFium raster matched the project-owned expectation at AE `0`, and the secondary renderer agreement AE was `0`.

ImageMagick receives only validated PNG raster paths in both comparison invocations; it is never given the PDF. Apache PDFBox Renderer is secondary disagreement evidence only and cannot make this chain pass.
