# T26 PDFium visual evidence

Capability: `composition.layout.tables`

Acceptance Profile: `T26-table-composition`

Profile record: `capabilities/evidence/T26-table-composition.md`

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

Input ID-neutral SHA-256: `483a67ded611b2768595b079d7bf4a13634e78175ca479759c140262a435cdc9`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Expected raster SHA-256: `f12b3618baeb2f5670fdca9ec6e346854c9f68e772ed9f63226ffdd97973a246`

PDFium raster SHA-256: `f12b3618baeb2f5670fdca9ec6e346854c9f68e772ed9f63226ffdd97973a246`

Implementation raster SHA-256: `7e2d5b1d202d7efbdfae6dff23d4e9d32faa26bf80e026474fb4520d8460dd9b`

Expected comparison AE: `0`

Renderer agreement AE: `40.7373`

Review required: `false`

Final determination: `pass`

## Visual profile

- Page box: effective CropBox; CropBox is absent, so MediaBox [0 0 612 792] points is used.
- Page selection: `3` of `3`.
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

- Input PDF: [`artifacts/T26-table-composition.pdf`](artifacts/T26-table-composition.pdf)
- Expected-raster authority: [`../expected/T26-table-composition-page-3-144dpi-srgb.png`](../expected/T26-table-composition-page-3-144dpi-srgb.png)
- PDFium notice manifest: [`docs/third-party/pdfium-cli-v0.11.2.md`](../../docs/third-party/pdfium-cli-v0.11.2.md)
- ImageMagick notice manifest: [`docs/third-party/imagemagick-7.1.2-30-appimage.md`](../../docs/third-party/imagemagick-7.1.2-30-appimage.md)
- Raster artifact: [`artifacts/T26-table-composition-page-3-expected.png`](artifacts/T26-table-composition-page-3-expected.png)
- Raster artifact: [`artifacts/T26-table-composition-page-3-pdfium.png`](artifacts/T26-table-composition-page-3-pdfium.png)
- Raster artifact: [`artifacts/T26-table-composition-page-3-implementation.png`](artifacts/T26-table-composition-page-3-implementation.png)
- Raster artifact: [`artifacts/T26-table-composition-page-3-difference.png`](artifacts/T26-table-composition-page-3-difference.png)
- Raster artifact: [`artifacts/T26-table-composition-page-3-renderer-difference.png`](artifacts/T26-table-composition-page-3-renderer-difference.png)
- Raw findings: [`artifacts/T26-table-composition-page-3-visual.txt`](artifacts/T26-table-composition-page-3-visual.txt)
- The PDFium raster matched the project-owned expectation at AE `0`, and the secondary renderer agreement AE was `40.7373`.

ImageMagick receives only validated PNG raster paths in both comparison invocations; it is never given the PDF. Apache PDFBox Renderer is secondary disagreement evidence only and cannot make this chain pass.
