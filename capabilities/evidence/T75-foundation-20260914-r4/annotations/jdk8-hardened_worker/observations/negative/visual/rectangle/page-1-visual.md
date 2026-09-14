# T12 PDFium visual evidence

Capability: `document.annotations-actions.manage`

Acceptance Profile: `T12-annotations-document-actions`

Profile record: `capabilities/evidence/T12-annotations-document-actions.md`

Release train: `0.1.0`

Chain: `visual`

Result: `fail`

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

Input exact SHA-256: `f1eff6eb9cfdde8ecbd3255f0fcb6ba39355c867c73c497ec6e3cd5dd48f29fa`

Input hash policy: `SHA-256 of the exact unmodified PDF bytes`

Expected raster SHA-256: `3b8f79d49438a5a59e9b052f5c8d5142c91d8ceb276060d541b828f2a85d0685`

PDFium raster SHA-256: `f3955a01836a1454cd6e62ce250cff7c2e7cc7c6eb851b2073d4b4a31a715066`

Implementation raster SHA-256: `09f4e8077980434768423027cb9f40b57dc0f72664941adc777a962e44079035`

Expected comparison AE: `20`

Renderer agreement AE: `0`

Review required: `false`

Final determination: `fail`

## Visual profile

- Page box: effective CropBox [0 0 120 100] points.
- Page selection: `1` of `3`.
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
- Expected-raster authority: [`../expected/created-page-1.png`](../../../../../../../../profiles/T12-annotations/expected/created-page-1.png)
- PDFium notice manifest: [`docs/third-party/pdfium-cli-v0.11.2.md`](../../../../../../../../../docs/third-party/pdfium-cli-v0.11.2.md)
- ImageMagick notice manifest: [`docs/third-party/imagemagick-7.1.2-30-appimage.md`](../../../../../../../../../docs/third-party/imagemagick-7.1.2-30-appimage.md)
- Raster artifact: [`page-1-expected.png`](page-1-expected.png)
- Raster artifact: [`page-1-pdfium.png`](page-1-pdfium.png)
- Raster artifact: [`page-1-implementation.png`](page-1-implementation.png)
- Raster artifact: [`page-1-difference.png`](page-1-difference.png)
- Raster artifact: [`page-1-renderer-difference.png`](page-1-renderer-difference.png)
- Raw findings: [`page-1-visual.txt`](page-1-visual.txt)
- The PDFium raster exceeded the fixed changed-pixel bound.

ImageMagick receives only validated PNG raster paths in both comparison invocations; it is never given the PDF. Apache PDFBox Renderer is secondary disagreement evidence only and cannot make this chain pass.
