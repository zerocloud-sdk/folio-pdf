# T11 PDFium visual evidence

Capability: `document.metadata.outlines-destinations-attachments`

Acceptance Profile: `T11-metadata-outlines-destinations-attachments`

Profile record: `capabilities/evidence/T11-metadata-outlines-destinations-attachments.md`

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

Input exact SHA-256: `3c5ba96722813f625e49cb4cf8ff56b938b65346e48540de50a7c5bfb0316635`

Input hash policy: `SHA-256 of the exact unmodified PDF bytes`

Expected raster SHA-256: `cb53385f3a81b6b03d39d1c2318c242f3c79b2320c092583ced86e5166fffc8f`

PDFium raster SHA-256: `ba3e5cbcbaf3ae1bed7d7b44828e48a7ab35d138dd3962a38cc4e1c9eec89cdf`

Implementation raster SHA-256: `2c9f3a1117ad4034cc4d4aabb0c96c6ffa1c36773bf2a0813d27ddd42a6f0579`

Expected comparison AE: `0`

Renderer agreement AE: `0`

Review required: `false`

Final determination: `pass`

## Visual profile

- Page box: effective CropBox [0 0 120 100] points.
- Page selection: `2` of `4`.
- DPI: `144`.
- Color policy: sRGB, opaque 8-bit RGB PNG after compositing over opaque white.
- Font policy: not applicable; the artifact has no text or font resources and uses no system fonts.
- Antialiasing policy: pinned PDFium default smoothing; vector edges are axis-aligned.
- Background: opaque white (#ffffff).
- Raster dimensions: `240x200`.
- Comparison metric: ImageMagick AE magnitude with fuzz 0 percent; exact changed RGB pixels additionally enforce the same bounds.
- Capability threshold: `0` changed pixels.
- Renderer-agreement threshold: `0` changed pixels.

## Findings and artifacts

- Input PDF: [`metadata.pdf`](metadata.pdf)
- Expected-raster authority: [`../expected/B.png`](../../../../../../profiles/T11-metadata/expected/B.png)
- PDFium notice manifest: [`docs/third-party/pdfium-cli-v0.11.2.md`](../../../../../../../docs/third-party/pdfium-cli-v0.11.2.md)
- ImageMagick notice manifest: [`docs/third-party/imagemagick-7.1.2-30-appimage.md`](../../../../../../../docs/third-party/imagemagick-7.1.2-30-appimage.md)
- Raster artifact: [`page-2-expected.png`](page-2-expected.png)
- Raster artifact: [`page-2-pdfium.png`](page-2-pdfium.png)
- Raster artifact: [`page-2-implementation.png`](page-2-implementation.png)
- Raster artifact: [`page-2-difference.png`](page-2-difference.png)
- Raster artifact: [`page-2-renderer-difference.png`](page-2-renderer-difference.png)
- Raw findings: [`page-2-visual.txt`](page-2-visual.txt)
- The PDFium raster matched the project-owned expectation at AE `0`, and the secondary renderer agreement AE was `0`.

ImageMagick receives only validated PNG raster paths in both comparison invocations; it is never given the PDF. Apache PDFBox Renderer is secondary disagreement evidence only and cannot make this chain pass.
