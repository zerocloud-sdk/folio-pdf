# T23 PDFium visual evidence

Capability: `conversion.rendering`

Acceptance Profile: `T23-page-rendering`

Profile record: `capabilities/evidence/T23-page-rendering.md`

Release train: `0.1.0-SNAPSHOT`

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

Input ID-neutral SHA-256: `0fb21c7dca14068cbebeafac3d08df6a0d16fb3286fe785c4bd0deb47beaa72c`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Expected raster SHA-256: `ba2aa587629d5e67d7659ca3ac34933fb15e7d9fdff0fa76976f1aceebc6a4e9`

PDFium raster SHA-256: `e2944b73056f1a6052cceaf2e9fe546370216705151154f649a967652fa6c952`

Implementation raster SHA-256: `e0287913112a5bf7b95ac15a308c72c93eade39bc716091c3452a586b0e47f86`

Expected comparison AE: `0`

Renderer agreement AE: `0`

Review required: `false`

Final determination: `pass`

## Visual profile

- Page box: effective CropBox; CropBox is absent, so MediaBox [0 0 612 792] points is used.
- DPI: `144`.
- Color policy: sRGB, opaque 8-bit RGB PNG after compositing over opaque white.
- Font policy: not applicable; the artifact has no text or font resources and uses no system fonts.
- Antialiasing policy: pinned PDFium default smoothing; image interpolation is disabled and vector edges are axis-aligned.
- Background: opaque white (#ffffff).
- Raster dimensions: `1224x1584`.
- Comparison metric: ImageMagick absolute error count (AE) with fuzz 0 percent.
- Capability threshold: `0` changed pixels.
- Renderer-agreement threshold: `0` changed pixels.

## Findings and artifacts

- Input PDF: [`artifacts/T23-page-rendering.pdf`](artifacts/T23-page-rendering.pdf)
- Expected-raster authority: [`../expected/T23-page-rendering-144dpi-srgb.png`](../expected/T23-page-rendering-144dpi-srgb.png)
- PDFium notice manifest: [`docs/third-party/pdfium-cli-v0.11.2.md`](../../docs/third-party/pdfium-cli-v0.11.2.md)
- ImageMagick notice manifest: [`docs/third-party/imagemagick-7.1.2-30-appimage.md`](../../docs/third-party/imagemagick-7.1.2-30-appimage.md)
- Raster artifact: [`artifacts/T23-page-rendering-expected.png`](artifacts/T23-page-rendering-expected.png)
- Raster artifact: [`artifacts/T23-page-rendering-pdfium.png`](artifacts/T23-page-rendering-pdfium.png)
- Raster artifact: [`artifacts/T23-page-rendering-implementation.png`](artifacts/T23-page-rendering-implementation.png)
- Raster artifact: [`artifacts/T23-page-rendering-difference.png`](artifacts/T23-page-rendering-difference.png)
- Raster artifact: [`artifacts/T23-page-rendering-renderer-difference.png`](artifacts/T23-page-rendering-renderer-difference.png)
- Raw findings: [`artifacts/T23-page-rendering-visual.txt`](artifacts/T23-page-rendering-visual.txt)
- The PDFium raster matched the project-owned expectation at AE `0`, and the public Rendering agreement AE was `0`.

ImageMagick receives only validated PNG raster paths in both comparison invocations; it is never given the PDF. The public Rendering output is the implementation under test and must satisfy the renderer-agreement ceiling; only the independent PDFium comparison against the project-owned expectation determines the primary capability threshold.
