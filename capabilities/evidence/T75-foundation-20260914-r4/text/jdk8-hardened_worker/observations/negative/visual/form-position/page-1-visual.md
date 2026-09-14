# T13 PDFium visual evidence

Capability: `document.text-structure.extract`

Acceptance Profile: `T13-text-logical-structure`

Profile record: `capabilities/evidence/T13-text-logical-structure.md`

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

Input exact SHA-256: `018e4de3b700801ba34be4be1bc97e6ca863bca702fbb5c8de71cff83392c9d5`

Input hash policy: `SHA-256 of the exact unmodified PDF bytes`

Expected raster SHA-256: `8332ac67f70cc70b314c027087c8899afe6fbaeb37704ca66edcf5671c0b1b88`

PDFium raster SHA-256: `04679f30fa47a78069223f341b82b3ff758057756367a461f407af7db537d1a7`

Implementation raster SHA-256: `a1e073d37fe663fe557d755067412b72acde681d34365c176a0b3028633f6c1d`

Expected comparison AE: `32`

Renderer agreement AE: `0`

Review required: `false`

Final determination: `fail`

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
- Expected-raster authority: [`../expected/nested-split-type3-page-1.png`](../../../../../../../../profiles/T13-text/expected/nested-split-type3-page-1.png)
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
