# T13 PDFium visual evidence

Capability: `document.text-structure.extract`

Acceptance Profile: `T13-text-logical-structure`

Profile record: `capabilities/evidence/T13-text-logical-structure.md`

Release train: `test`

Chain: `visual`

Result: `indeterminate`

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

ImageMagick version: `unavailable`

ImageMagick distribution: `ImageMagick-7.1.2-30-gcc-x86_64.AppImage`

ImageMagick distribution SHA-256: `372af8a3fd61ef5f15c6331cde3e21f840eb165d8b533f34ed05d68736dd682e`

ImageMagick executable SHA-256: `372af8a3fd61ef5f15c6331cde3e21f840eb165d8b533f34ed05d68736dd682e`

ImageMagick distribution license: `ImageMagick`

ImageMagick notice manifest: `docs/third-party/imagemagick-7.1.2-30-appimage.md`

Implementation renderer version: `unavailable`

Input exact SHA-256: `c8671742fb673b13d1654a1f0a0dcd834a7c0cb1deed6bc2c81ac2a3418a577d`

Input hash policy: `SHA-256 of the exact unmodified PDF bytes`

Expected raster SHA-256: `e9f66a99024128e3b15d78e3e2aed1faf93cf92aed7a10f8c78885f71df154ed`

PDFium raster SHA-256: `37c85d3e47dacee9f6e30f4317e300b06f9f0a7788fa04f6e87576e7a980f2d8`

Implementation raster SHA-256: `unavailable`

Expected comparison AE: `unavailable`

Renderer agreement AE: `unavailable`

Review required: `false`

Final determination: `indeterminate`

## Visual profile

- Page box: effective CropBox [0 0 120 100] points.
- DPI: `144`.
- Color policy: sRGB, opaque 8-bit RGB PNG after compositing over opaque white.
- Font policy: original embedded Type1C, CID CFF and TrueType rectangle glyphs; no system fonts or substitution.
- Antialiasing policy: pinned PDFium default smoothing; vector edges are axis-aligned.
- Background: opaque white (#ffffff).
- Raster dimensions: `240x200`.
- Comparison metric: ImageMagick AE magnitude with fuzz 0 percent; exact changed RGB pixels additionally enforce the same bounds.
- Capability threshold: `0` changed pixels.
- Renderer-agreement threshold: `0` changed pixels.

## Findings and artifacts

- Input PDF: [`extraction.pdf`](extraction.pdf)
- Expected-raster authority: [`../expected/embedded-font-kinds-page-1.png`](../../home/ubuntu/IdeaProjects/open-pdf/capabilities/profiles/T13-text/expected/embedded-font-kinds-page-1.png)
- PDFium notice manifest: [`docs/third-party/pdfium-cli-v0.11.2.md`](../../home/ubuntu/IdeaProjects/open-pdf/docs/third-party/pdfium-cli-v0.11.2.md)
- ImageMagick notice manifest: [`docs/third-party/imagemagick-7.1.2-30-appimage.md`](../../home/ubuntu/IdeaProjects/open-pdf/docs/third-party/imagemagick-7.1.2-30-appimage.md)
- Raster artifact: [`page-1-expected.png`](page-1-expected.png)
- Raster artifact: [`page-1-pdfium.png`](page-1-pdfium.png)
- Raw findings: [`page-1-visual.txt`](page-1-visual.txt)
- The secondary implementation renderer ended unexpectedly.

ImageMagick receives only validated PNG raster paths in both comparison invocations; it is never given the PDF. Apache PDFBox Renderer is secondary disagreement evidence only and cannot make this chain pass.
