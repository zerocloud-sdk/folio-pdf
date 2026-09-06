# T28 PDFium visual evidence

Capability: `composition.layout.paragraph-areas`

Acceptance Profile: `T28-unicode-cjk-tc`

Profile record: `capabilities/evidence/T28-unicode-cjk-tc.md`

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

Input ID-neutral SHA-256: `473f03a4331432670d1fb2b8ad8a30e3b52564323817d4e62f55b15ab34e87f6`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Expected raster SHA-256: `05ebb165da6aab64297bc14b8cee893e7a2763ca941d59435b7718e745226bc9`

PDFium raster SHA-256: `05ebb165da6aab64297bc14b8cee893e7a2763ca941d59435b7718e745226bc9`

Implementation raster SHA-256: `38342c5dec033801bd78318d6762ad8f07132773a63cbc52e1a238908bd88697`

Expected comparison AE: `0`

Renderer agreement AE: `730.537`

Review required: `false`

Final determination: `pass`

## Visual profile

- Page box: effective CropBox; CropBox is absent, so MediaBox [0 0 612 792] points is used.
- Page selection: `5` of `7`.
- DPI: `144`.
- Color policy: sRGB, opaque 8-bit RGB PNG after compositing over opaque white.
- Font policy: only the explicit embedded hash-pinned Noto subsets; no system fonts.
- Antialiasing policy: pinned PDFium default text smoothing.
- Background: opaque white (#ffffff).
- Raster dimensions: `1224x1584`.
- Comparison metric: ImageMagick AE magnitude with fuzz 0 percent; exact changed RGB pixels additionally enforce the same bounds.
- Capability threshold: `0` changed pixels.
- Renderer-agreement threshold: `12000` changed pixels.

## Findings and artifacts

- Input PDF: [`artifacts/T28-unicode.pdf`](artifacts/T28-unicode.pdf)
- Expected-raster authority: [`../expected/T28-unicode-cjk-tc-144dpi-srgb.png`](../expected/T28-unicode-cjk-tc-144dpi-srgb.png)
- PDFium notice manifest: [`docs/third-party/pdfium-cli-v0.11.2.md`](../../docs/third-party/pdfium-cli-v0.11.2.md)
- ImageMagick notice manifest: [`docs/third-party/imagemagick-7.1.2-30-appimage.md`](../../docs/third-party/imagemagick-7.1.2-30-appimage.md)
- Raster artifact: [`artifacts/T28-unicode-cjk-tc-expected.png`](artifacts/T28-unicode-cjk-tc-expected.png)
- Raster artifact: [`artifacts/T28-unicode-cjk-tc-pdfium.png`](artifacts/T28-unicode-cjk-tc-pdfium.png)
- Raster artifact: [`artifacts/T28-unicode-cjk-tc-implementation.png`](artifacts/T28-unicode-cjk-tc-implementation.png)
- Raster artifact: [`artifacts/T28-unicode-cjk-tc-difference.png`](artifacts/T28-unicode-cjk-tc-difference.png)
- Raster artifact: [`artifacts/T28-unicode-cjk-tc-renderer-difference.png`](artifacts/T28-unicode-cjk-tc-renderer-difference.png)
- Raw findings: [`artifacts/T28-unicode-cjk-tc-visual.txt`](artifacts/T28-unicode-cjk-tc-visual.txt)
- The PDFium raster matched the project-owned expectation at AE `0`, and the secondary renderer agreement AE was `730.537`.

ImageMagick receives only validated PNG raster paths in both comparison invocations; it is never given the PDF. Apache PDFBox Renderer is secondary disagreement evidence only and cannot make this chain pass.
