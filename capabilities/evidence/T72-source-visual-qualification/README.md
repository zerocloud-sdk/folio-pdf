# T72 source-raster qualification

This directory retains the development qualification of the five nonblank
source-page rasters used by the frozen T10 profile. The three PDFs and all
expected images are original Apache-2.0 project fixtures generated before any
product observation.

Pinned PDFium rendered pages A, B, C, D, and E from the original primary,
appendix, and cover Sources at 144 DPI. ImageMagick compared each rendered pixel
grid with its mathematically authored expected raster at zero fuzz and threshold
zero. Every `*-compare.txt` records `0 (0)`. The `*-difference.png` files retain
the corresponding zero-difference image, while `*-render.txt` records the actual
PDFium page selection and output.

Source PDF SHA-256 values:

- primary: `ce30bab5122750151dd5fa5dc4ba9db50ab4426c5297e8e6fbf16e5817c861b2`
- appendix: `2f6336e50ea1efae7763ba4db9c779f3d5e4c11515d4d0c6ab91e8e2b7f95027`
- cover: `10b41ddbd3eb4f6fa07e286611fbdd69419580a4c7c48be40ca2f335fd8256e5`

This qualification demonstrates that the preauthored source geometry and pixels
agree with the independent renderer. It is not candidate certification. Product
pages, tool identities, exact expected-raster hashes, secondary rendering, and
the one-pixel control are retained separately in every Foundation T10 run.
