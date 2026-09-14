# T13 PDFium visual evidence

Capability: `document.text-structure.extract`

Acceptance Profile: `T13-text-logical-structure`

Profile record: `capabilities/evidence/T13-text-logical-structure.md`

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

Input exact SHA-256: `5f494acbbdedc685448fdd2dbec891d71408ae82af464e773fb6ce899d311d0b`

Input hash policy: `SHA-256 of the exact unmodified PDF bytes`

Expected raster SHA-256: `ed8d814148dd00bbfd0d6dfd2d80ea584161f639ec7bc42602d7ba0559672a05`

PDFium raster SHA-256: `e0261236a3bd76aceb01b36b59d5879d151041b191818b3fe55c1cd2abe43f7c`

Implementation raster SHA-256: `fd8accd180d82be2efb1c68b22bacf361c0eafcec6ba284779cba1cccdeec86f`

Expected comparison AE: `0`

Renderer agreement AE: `0`

Review required: `false`

Final determination: `pass`

## Visual profile

- Page box: effective CropBox [0 0 120 100] points.
- DPI: `144`.
- Color policy: sRGB, opaque 8-bit RGB PNG after compositing over opaque white.
- Font policy: original embedded Type3 rectangle glyph; no system fonts or substitution.
- Antialiasing policy: pinned PDFium default smoothing; vector edges are axis-aligned.
- Background: opaque white (#ffffff).
- Raster dimensions: `240x200`.
- Comparison metric: ImageMagick AE magnitude with fuzz 0 percent; exact changed RGB pixels additionally enforce the same bounds.
- Capability threshold: `0` changed pixels.
- Renderer-agreement threshold: `0` changed pixels.

## Findings and artifacts

- Input PDF: [`extraction.pdf`](extraction.pdf)
- Expected-raster authority: [`../expected/marked-structure-page-1.png`](../../../../../../../profiles/T13-text/expected/marked-structure-page-1.png)
- PDFium notice manifest: [`docs/third-party/pdfium-cli-v0.11.2.md`](../../../../../../../../docs/third-party/pdfium-cli-v0.11.2.md)
- ImageMagick notice manifest: [`docs/third-party/imagemagick-7.1.2-30-appimage.md`](../../../../../../../../docs/third-party/imagemagick-7.1.2-30-appimage.md)
- Raster artifact: [`page-1-expected.png`](page-1-expected.png)
- Raster artifact: [`page-1-pdfium.png`](page-1-pdfium.png)
- Raster artifact: [`page-1-implementation.png`](page-1-implementation.png)
- Raster artifact: [`page-1-difference.png`](page-1-difference.png)
- Raster artifact: [`page-1-renderer-difference.png`](page-1-renderer-difference.png)
- Raw findings: [`page-1-visual.txt`](page-1-visual.txt)
- The PDFium raster matched the project-owned expectation at AE `0`, and the secondary renderer agreement AE was `0`.

ImageMagick receives only validated PNG raster paths in both comparison invocations; it is never given the PDF. Apache PDFBox Renderer is secondary disagreement evidence only and cannot make this chain pass.
