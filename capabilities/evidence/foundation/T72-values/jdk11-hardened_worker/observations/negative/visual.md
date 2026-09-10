# T09 PDFium visual evidence

Capability: `document.value.inspect-patch`

Acceptance Profile: `T09-document-value-inspection-patch`

Profile record: `capabilities/evidence/T09-document-value-inspection-patch.md`

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

Input exact SHA-256: `4b5279cf8ae980beac2738e8a3df0be8f5e19d638000e46e385e424bf4bf241f`

Input hash policy: `SHA-256 of the exact unmodified PDF bytes`

Expected raster SHA-256: `3f27a9b7b545a0eeb15af8710f8adf504ee1df8638fc40da7b0e1a83cbdfdbf2`

PDFium raster SHA-256: `6daa314aec76d2e06c78e7d4247ff3b8d113caf8f5a38120a2e4c629c55a8be6`

Implementation raster SHA-256: `6a64639c39384610005a7908371f083cb8920cdaba0eddb7cb93d207ffd0030c`

Expected comparison AE: `1`

Renderer agreement AE: `0`

Review required: `false`

Final determination: `fail`

## Visual profile

- Page box: effective CropBox; CropBox is absent, so MediaBox [0 0 612 792] points is used.
- DPI: `144`.
- Color policy: sRGB, opaque 8-bit RGB PNG after compositing over opaque white.
- Font policy: not applicable; the artifact has no text or font resources and uses no system fonts.
- Antialiasing policy: pinned PDFium default smoothing; vector edges are axis-aligned.
- Background: opaque white (#ffffff).
- Raster dimensions: `1224x1584`.
- Comparison metric: ImageMagick absolute error count (AE) with fuzz 0 percent.
- Capability threshold: `0` changed pixels.
- Renderer-agreement threshold: `0` changed pixels.

## Findings and artifacts

- Input PDF: [`values.pdf`](values.pdf)
- Expected-raster authority: [`one-pixel-control.png`](one-pixel-control.png)
- PDFium notice manifest: [`docs/third-party/pdfium-cli-v0.11.2.md`](../../../../../../../docs/third-party/pdfium-cli-v0.11.2.md)
- ImageMagick notice manifest: [`docs/third-party/imagemagick-7.1.2-30-appimage.md`](../../../../../../../docs/third-party/imagemagick-7.1.2-30-appimage.md)
- Raster artifact: [`T09-values-expected.png`](T09-values-expected.png)
- Raster artifact: [`T09-values-pdfium.png`](T09-values-pdfium.png)
- Raster artifact: [`T09-values-implementation.png`](T09-values-implementation.png)
- Raster artifact: [`T09-values-difference.png`](T09-values-difference.png)
- Raster artifact: [`T09-values-renderer-difference.png`](T09-values-renderer-difference.png)
- Raw findings: [`visual.txt`](visual.txt)
- The PDFium raster exceeded the capability threshold with AE `1`; the raster difference is retained.

ImageMagick receives only validated PNG raster paths in both comparison invocations; it is never given the PDF. Apache PDFBox Renderer is secondary disagreement evidence only and cannot make this chain pass.
