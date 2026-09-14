# T10 PDFium visual evidence

Capability: `document.page.manipulate-merge-split`

Acceptance Profile: `T10-page-manipulation-merge-split`

Profile record: `capabilities/evidence/T10-page-manipulation-merge-split.md`

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

Input exact SHA-256: `657cca907b0844ea6ff80f41b8326def571a1e5c47e928296cf5e61f042b2448`

Input hash policy: `SHA-256 of the exact unmodified PDF bytes`

Expected raster SHA-256: `304f1fa0d5f5c120dad71a78a1c43595832359b1f663a33a9c92cf3248c3d03b`

PDFium raster SHA-256: `db40b08c282192d7294e2e992f0e8fd3048f763c04a876967f8dbaca290563ec`

Implementation raster SHA-256: `186c8688161d33fb73fc2f0183d3caa56f672e9e8d5b40392d5df5f09cdf702a`

Expected comparison AE: `0`

Renderer agreement AE: `0`

Review required: `false`

Final determination: `pass`

## Visual profile

- Page box: effective CropBox [5 15 155 185] points.
- Page selection: `3` of `5`.
- DPI: `144`.
- Color policy: sRGB, opaque 8-bit RGB PNG after compositing over opaque white.
- Font policy: not applicable; the artifact has no text or font resources and uses no system fonts.
- Antialiasing policy: pinned PDFium default smoothing; vector edges are axis-aligned.
- Background: opaque white (#ffffff).
- Raster dimensions: `300x340`.
- Comparison metric: ImageMagick AE magnitude with fuzz 0 percent; exact changed RGB pixels additionally enforce the same bounds.
- Capability threshold: `0` changed pixels.
- Renderer-agreement threshold: `0` changed pixels.

## Findings and artifacts

- Input PDF: [`pages.pdf`](pages.pdf)
- Expected-raster authority: [`../expected/C.png`](../../../../../../profiles/T10-pages/expected/C.png)
- PDFium notice manifest: [`docs/third-party/pdfium-cli-v0.11.2.md`](../../../../../../../docs/third-party/pdfium-cli-v0.11.2.md)
- ImageMagick notice manifest: [`docs/third-party/imagemagick-7.1.2-30-appimage.md`](../../../../../../../docs/third-party/imagemagick-7.1.2-30-appimage.md)
- Raster artifact: [`page-3-expected.png`](page-3-expected.png)
- Raster artifact: [`page-3-pdfium.png`](page-3-pdfium.png)
- Raster artifact: [`page-3-implementation.png`](page-3-implementation.png)
- Raster artifact: [`page-3-difference.png`](page-3-difference.png)
- Raster artifact: [`page-3-renderer-difference.png`](page-3-renderer-difference.png)
- Raw findings: [`page-3-visual.txt`](page-3-visual.txt)
- The PDFium raster matched the project-owned expectation at AE `0`, and the secondary renderer agreement AE was `0`.

ImageMagick receives only validated PNG raster paths in both comparison invocations; it is never given the PDF. Apache PDFBox Renderer is secondary disagreement evidence only and cannot make this chain pass.
