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

Input exact SHA-256: `588d6206ea7101793ffdc8712dfe735a7473b6114191ff14ec3db785edec4e9f`

Input hash policy: `SHA-256 of the exact unmodified PDF bytes`

Expected raster SHA-256: `8e1bab0e2e3f021e6ea8bae7040f67d062ba58f8c1c69b4c5b96aaffb76882fc`

PDFium raster SHA-256: `7b31149cd924b70c414fc898c80d0d480238c0b11fda3322398d74ad42e349f2`

Implementation raster SHA-256: `5683fe8f21ff3e92eced5c8a7f686b003c2a6f62c081c8c59d26fd27c6006fea`

Expected comparison AE: `0`

Renderer agreement AE: `0`

Review required: `false`

Final determination: `pass`

## Visual profile

- Page box: effective CropBox [10 20 190 140] points.
- Page selection: `3` of `4`.
- DPI: `144`.
- Color policy: sRGB, opaque 8-bit RGB PNG after compositing over opaque white.
- Font policy: not applicable; the artifact has no text or font resources and uses no system fonts.
- Antialiasing policy: pinned PDFium default smoothing; vector edges are axis-aligned.
- Background: opaque white (#ffffff).
- Raster dimensions: `240x360`.
- Comparison metric: ImageMagick AE magnitude with fuzz 0 percent; exact changed RGB pixels additionally enforce the same bounds.
- Capability threshold: `0` changed pixels.
- Renderer-agreement threshold: `0` changed pixels.

## Findings and artifacts

- Input PDF: [`pages.pdf`](pages.pdf)
- Expected-raster authority: [`../expected/copy-A.png`](../../../../../../profiles/T10-pages/expected/copy-A.png)
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
