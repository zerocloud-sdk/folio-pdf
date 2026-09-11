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

Expected raster SHA-256: `c83dfe741e112e0605ab19ec6033299ab6b8b2f0ab8260985ae13a74e7272081`

PDFium raster SHA-256: `86d6e6650b3352d22afcb5300195dd18fd892c071b14c6218b32de7b4d07002a`

Implementation raster SHA-256: `0510d51710e584e22b6d2e99bfaabddcd042dafb1730eccfe8909deef6a231fd`

Expected comparison AE: `0`

Renderer agreement AE: `0`

Review required: `false`

Final determination: `pass`

## Visual profile

- Page box: effective CropBox [0 0 120 100] points.
- Page selection: `4` of `4`.
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
- Expected-raster authority: [`../expected/D.png`](../../../../../../profiles/T11-metadata/expected/D.png)
- PDFium notice manifest: [`docs/third-party/pdfium-cli-v0.11.2.md`](../../../../../../../docs/third-party/pdfium-cli-v0.11.2.md)
- ImageMagick notice manifest: [`docs/third-party/imagemagick-7.1.2-30-appimage.md`](../../../../../../../docs/third-party/imagemagick-7.1.2-30-appimage.md)
- Raster artifact: [`page-4-expected.png`](page-4-expected.png)
- Raster artifact: [`page-4-pdfium.png`](page-4-pdfium.png)
- Raster artifact: [`page-4-implementation.png`](page-4-implementation.png)
- Raster artifact: [`page-4-difference.png`](page-4-difference.png)
- Raster artifact: [`page-4-renderer-difference.png`](page-4-renderer-difference.png)
- Raw findings: [`page-4-visual.txt`](page-4-visual.txt)
- The PDFium raster matched the project-owned expectation at AE `0`, and the secondary renderer agreement AE was `0`.

ImageMagick receives only validated PNG raster paths in both comparison invocations; it is never given the PDF. Apache PDFBox Renderer is secondary disagreement evidence only and cannot make this chain pass.
