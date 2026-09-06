# T27 PDFium visual evidence

Capability: `composition.layout.tables`

Acceptance Profile: `T27-table-pagination`

Profile record: `capabilities/evidence/T27-table-pagination.md`

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

Input ID-neutral SHA-256: `95e2fe473a70bc8d4204e30647ec32cbeccc9d9c49a939ee5d9a4a4d9dcfab70`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Expected raster SHA-256: `089b5862f456ca619631f3ecd48602d4a7740f0ad94986958fd4b8181ab619b7`

PDFium raster SHA-256: `089b5862f456ca619631f3ecd48602d4a7740f0ad94986958fd4b8181ab619b7`

Implementation raster SHA-256: `ef65e67c0775d55c0df3656c57406de41b58a3d5f9be2949c777810d3c81696c`

Expected comparison AE: `0`

Renderer agreement AE: `139.859`

Review required: `false`

Final determination: `pass`

## Visual profile

- Page box: effective CropBox; CropBox is absent, so MediaBox [0 0 612 792] points is used.
- Page selection: `1` of `19`.
- DPI: `144`.
- Color policy: sRGB, opaque 8-bit RGB PNG after compositing over opaque white.
- Font policy: only the two embedded project-authored subsets; no system fonts.
- Antialiasing policy: pinned PDFium default text smoothing.
- Background: opaque white (#ffffff).
- Raster dimensions: `1224x1584`.
- Comparison metric: ImageMagick AE magnitude with fuzz 0 percent; exact changed RGB pixels additionally enforce the same bounds.
- Capability threshold: `0` changed pixels.
- Renderer-agreement threshold: `2500` changed pixels.

## Findings and artifacts

- Input PDF: [`artifacts/T27-table-pagination.pdf`](artifacts/T27-table-pagination.pdf)
- Expected-raster authority: [`../expected/T27-table-pagination-page-1-144dpi-srgb.png`](../expected/T27-table-pagination-page-1-144dpi-srgb.png)
- PDFium notice manifest: [`docs/third-party/pdfium-cli-v0.11.2.md`](../../docs/third-party/pdfium-cli-v0.11.2.md)
- ImageMagick notice manifest: [`docs/third-party/imagemagick-7.1.2-30-appimage.md`](../../docs/third-party/imagemagick-7.1.2-30-appimage.md)
- Raster artifact: [`artifacts/T27-table-pagination-page-1-expected.png`](artifacts/T27-table-pagination-page-1-expected.png)
- Raster artifact: [`artifacts/T27-table-pagination-page-1-pdfium.png`](artifacts/T27-table-pagination-page-1-pdfium.png)
- Raster artifact: [`artifacts/T27-table-pagination-page-1-implementation.png`](artifacts/T27-table-pagination-page-1-implementation.png)
- Raster artifact: [`artifacts/T27-table-pagination-page-1-difference.png`](artifacts/T27-table-pagination-page-1-difference.png)
- Raster artifact: [`artifacts/T27-table-pagination-page-1-renderer-difference.png`](artifacts/T27-table-pagination-page-1-renderer-difference.png)
- Raw findings: [`artifacts/T27-table-pagination-page-1-visual.txt`](artifacts/T27-table-pagination-page-1-visual.txt)
- The PDFium raster matched the project-owned expectation at AE `0`, and the secondary renderer agreement AE was `139.859`.

ImageMagick receives only validated PNG raster paths in both comparison invocations; it is never given the PDF. Apache PDFBox Renderer is secondary disagreement evidence only and cannot make this chain pass.
