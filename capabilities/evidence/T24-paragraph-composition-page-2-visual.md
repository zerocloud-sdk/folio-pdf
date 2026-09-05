# T24 PDFium visual evidence

Capability: `composition.layout.paragraph-areas`

Acceptance Profile: `T24-paragraph-composition`

Profile record: `capabilities/evidence/T24-paragraph-composition.md`

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

Input ID-neutral SHA-256: `ecb27d14cd1996e4f1b31b424533fb5536f08929a8f83d20f452ee069473815a`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Expected raster SHA-256: `3f1862180e89dae9c30757c3fea836695d276b8ceaa0eb49b19fc2b32277f8ce`

PDFium raster SHA-256: `3f1862180e89dae9c30757c3fea836695d276b8ceaa0eb49b19fc2b32277f8ce`

Implementation raster SHA-256: `e5f034f6fab76565a0e2a7857468024813b8cd45c942cfb5c89099777dc5a8fc`

Expected comparison AE: `0`

Renderer agreement AE: `59.9137`

Review required: `false`

Final determination: `pass`

## Visual profile

- Page box: effective CropBox; CropBox is absent, so MediaBox [0 0 612 792] points is used.
- Page selection: `2` of `2`.
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

- Input PDF: [`artifacts/T24-paragraph-composition.pdf`](artifacts/T24-paragraph-composition.pdf)
- Expected-raster authority: [`../expected/T24-paragraph-composition-page-2-144dpi-srgb.png`](../expected/T24-paragraph-composition-page-2-144dpi-srgb.png)
- PDFium notice manifest: [`docs/third-party/pdfium-cli-v0.11.2.md`](../../docs/third-party/pdfium-cli-v0.11.2.md)
- ImageMagick notice manifest: [`docs/third-party/imagemagick-7.1.2-30-appimage.md`](../../docs/third-party/imagemagick-7.1.2-30-appimage.md)
- Raster artifact: [`artifacts/T24-paragraph-composition-page-2-expected.png`](artifacts/T24-paragraph-composition-page-2-expected.png)
- Raster artifact: [`artifacts/T24-paragraph-composition-page-2-pdfium.png`](artifacts/T24-paragraph-composition-page-2-pdfium.png)
- Raster artifact: [`artifacts/T24-paragraph-composition-page-2-implementation.png`](artifacts/T24-paragraph-composition-page-2-implementation.png)
- Raster artifact: [`artifacts/T24-paragraph-composition-page-2-difference.png`](artifacts/T24-paragraph-composition-page-2-difference.png)
- Raster artifact: [`artifacts/T24-paragraph-composition-page-2-renderer-difference.png`](artifacts/T24-paragraph-composition-page-2-renderer-difference.png)
- Raw findings: [`artifacts/T24-paragraph-composition-page-2-visual.txt`](artifacts/T24-paragraph-composition-page-2-visual.txt)
- The PDFium raster matched the project-owned expectation at AE `0`, and the secondary renderer agreement AE was `59.9137`.

ImageMagick receives only validated PNG raster paths in both comparison invocations; it is never given the PDF. Apache PDFBox Renderer is secondary disagreement evidence only and cannot make this chain pass.
