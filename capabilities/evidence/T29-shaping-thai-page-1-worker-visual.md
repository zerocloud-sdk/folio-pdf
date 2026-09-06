# T29 PDFium visual evidence

Capability: `composition.shaping.harf-buzz`

Acceptance Profile: `T29-shaping-thai-page-1`

Profile record: `capabilities/evidence/T29-shaping.md`

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

Input ID-neutral SHA-256: `a6c1522148d25ec716119791bdc682b9c3b7682b0b9ebfcbbc69a8823964318c`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Expected raster SHA-256: `7ac0d1028f6738306ef42dfb0100489ff5709ae668a6da05e2932cd7d79c96e6`

PDFium raster SHA-256: `7ac0d1028f6738306ef42dfb0100489ff5709ae668a6da05e2932cd7d79c96e6`

Implementation raster SHA-256: `f437bd76610dc1d412656c5ed4866a3f481cd83ddc1be4c9e5ed146876b52d50`

Expected comparison AE: `0`

Renderer agreement AE: `122.341`

Review required: `false`

Final determination: `pass`

## Visual profile

- Page box: effective CropBox; CropBox is absent, so MediaBox [0 0 240 192] points is used.
- Page selection: `7` of `8`.
- DPI: `144`.
- Color policy: sRGB, opaque 8-bit RGB PNG after compositing over opaque white.
- Font policy: only the explicit embedded hash-pinned Noto subsets; no system fonts.
- Antialiasing policy: pinned PDFium default text smoothing.
- Background: opaque white (#ffffff).
- Raster dimensions: `480x384`.
- Comparison metric: ImageMagick AE magnitude with fuzz 0 percent; exact changed RGB pixels additionally enforce the same bounds.
- Capability threshold: `0` changed pixels.
- Renderer-agreement threshold: `3000` changed pixels.

## Findings and artifacts

- Input PDF: [`artifacts/T29-shaping-worker.pdf`](artifacts/T29-shaping-worker.pdf)
- Expected-raster authority: [`../expected/T29-shaping-thai-page-1-144dpi-srgb.png`](../expected/T29-shaping-thai-page-1-144dpi-srgb.png)
- PDFium notice manifest: [`docs/third-party/pdfium-cli-v0.11.2.md`](../../docs/third-party/pdfium-cli-v0.11.2.md)
- ImageMagick notice manifest: [`docs/third-party/imagemagick-7.1.2-30-appimage.md`](../../docs/third-party/imagemagick-7.1.2-30-appimage.md)
- Raster artifact: [`artifacts/T29-shaping-thai-page-1-worker-expected.png`](artifacts/T29-shaping-thai-page-1-worker-expected.png)
- Raster artifact: [`artifacts/T29-shaping-thai-page-1-worker-pdfium.png`](artifacts/T29-shaping-thai-page-1-worker-pdfium.png)
- Raster artifact: [`artifacts/T29-shaping-thai-page-1-worker-implementation.png`](artifacts/T29-shaping-thai-page-1-worker-implementation.png)
- Raster artifact: [`artifacts/T29-shaping-thai-page-1-worker-difference.png`](artifacts/T29-shaping-thai-page-1-worker-difference.png)
- Raster artifact: [`artifacts/T29-shaping-thai-page-1-worker-renderer-difference.png`](artifacts/T29-shaping-thai-page-1-worker-renderer-difference.png)
- Raw findings: [`artifacts/T29-shaping-thai-page-1-worker-visual.txt`](artifacts/T29-shaping-thai-page-1-worker-visual.txt)
- The PDFium raster matched the project-owned expectation at AE `0`, and the secondary renderer agreement AE was `122.341`.

ImageMagick receives only validated PNG raster paths in both comparison invocations; it is never given the PDF. Apache PDFBox Renderer is secondary disagreement evidence only and cannot make this chain pass.
