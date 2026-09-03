# T19 PDFium visual evidence

Capability: `composition.fonts.load-embed-subset-fallback`

Acceptance Profile: `T19-font-loading-embedding-subsetting`

Profile record: `capabilities/evidence/T19-font-loading-embedding-subsetting.md`

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

Input ID-neutral SHA-256: `c476b39cd5106f6270d815aebdcbb38d450ae18d89601da6aa6a02f1bd5c46a7`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Expected raster SHA-256: `d5a0c880a7a58bd0de6a1b7e887b6fe6b5a73f14608355434ee8cf022eb04a31`

PDFium raster SHA-256: `d5a0c880a7a58bd0de6a1b7e887b6fe6b5a73f14608355434ee8cf022eb04a31`

Implementation raster SHA-256: `7678803633b077789eab639293b287c6a80d6a64bca8db83b9feed65a1cde43f`

Expected comparison AE: `0`

Renderer agreement AE: `231.827`

Review required: `false`

Final determination: `pass`

## Visual profile

- Page box: effective CropBox; CropBox is absent, so MediaBox [0 0 612 792] points is used.
- DPI: `144`.
- Color policy: sRGB, opaque 8-bit RGB PNG after compositing over opaque white.
- Font policy: only the two embedded project-authored subsets; no system fonts.
- Antialiasing policy: pinned PDFium default text smoothing.
- Background: opaque white (#ffffff).
- Raster dimensions: `1224x1584`.
- Comparison metric: ImageMagick absolute error count (AE) with fuzz 0 percent.
- Capability threshold: `0` changed pixels.
- Renderer-agreement threshold: `2500` changed pixels.

## Findings and artifacts

- Input PDF: [`artifacts/T19-font-loading-embedding-subsetting.pdf`](artifacts/T19-font-loading-embedding-subsetting.pdf)
- Expected-raster authority: [`../expected/T19-font-loading-embedding-subsetting-144dpi-srgb.png`](../expected/T19-font-loading-embedding-subsetting-144dpi-srgb.png)
- PDFium notice manifest: [`docs/third-party/pdfium-cli-v0.11.2.md`](../../docs/third-party/pdfium-cli-v0.11.2.md)
- ImageMagick notice manifest: [`docs/third-party/imagemagick-7.1.2-30-appimage.md`](../../docs/third-party/imagemagick-7.1.2-30-appimage.md)
- Raster artifact: [`artifacts/T19-font-loading-embedding-subsetting-expected.png`](artifacts/T19-font-loading-embedding-subsetting-expected.png)
- Raster artifact: [`artifacts/T19-font-loading-embedding-subsetting-pdfium.png`](artifacts/T19-font-loading-embedding-subsetting-pdfium.png)
- Raster artifact: [`artifacts/T19-font-loading-embedding-subsetting-implementation.png`](artifacts/T19-font-loading-embedding-subsetting-implementation.png)
- Raster artifact: [`artifacts/T19-font-loading-embedding-subsetting-difference.png`](artifacts/T19-font-loading-embedding-subsetting-difference.png)
- Raster artifact: [`artifacts/T19-font-loading-embedding-subsetting-renderer-difference.png`](artifacts/T19-font-loading-embedding-subsetting-renderer-difference.png)
- Raw findings: [`artifacts/T19-font-loading-embedding-subsetting-visual.txt`](artifacts/T19-font-loading-embedding-subsetting-visual.txt)
- The PDFium raster matched the project-owned expectation at AE `0`, and the secondary renderer agreement AE was `231.827`.

ImageMagick receives only validated PNG raster paths in both comparison invocations; it is never given the PDF. Apache PDFBox Renderer is secondary disagreement evidence only and cannot make this chain pass.
