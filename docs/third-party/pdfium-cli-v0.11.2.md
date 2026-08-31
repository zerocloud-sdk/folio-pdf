# pdfium-cli v0.11.2 validation-tool notice manifest

This manifest applies only to the operator-supplied
`pdfium-webassembly-linux-amd64` release asset used by the T07 repository-only
Acceptance Evidence path. It is tied to the direct asset/executable SHA-256
`3ef3375c429ce665e834f933a028225bf28ac837695aaa69c6fc21facf6780ab`.
The executable is stored only in the ignored `.build-cache/` directory and is
not included in a Folio PDF artifact.

## Go executable components

The release tag is
[`klippa-app/pdfium-cli@v0.11.2`](https://github.com/klippa-app/pdfium-cli/tree/v0.11.2)
(commit `260c846dbbd180fdc478a2771e9dae9914164846`). The executable's Go build
information identifies the following linked module graph; modules present only
in an upstream `go.mod` for unused implementations or tests are not linked into
this asset.

| Component and version | Upstream origin | License |
| --- | --- | --- |
| Go standard library (Go 1.26 build) | [Go](https://go.dev/LICENSE) | BSD-3-Clause |
| `github.com/klippa-app/pdfium-cli` v0.11.2 | [pdfium-cli](https://github.com/klippa-app/pdfium-cli/tree/v0.11.2) | MIT |
| `github.com/klippa-app/go-pdfium` v1.19.8 | [go-pdfium](https://github.com/klippa-app/go-pdfium/tree/v1.19.8) | MIT |
| `github.com/google/uuid` v1.6.0 | [google/uuid](https://github.com/google/uuid/tree/v1.6.0) | BSD-3-Clause |
| `github.com/jolestar/go-commons-pool/v2` v2.1.2 | [go-commons-pool](https://github.com/jolestar/go-commons-pool/tree/v2.1.2) | Apache-2.0 |
| `github.com/spf13/cobra` v1.10.2 | [Cobra](https://github.com/spf13/cobra/tree/v1.10.2) | Apache-2.0 |
| `github.com/spf13/pflag` v1.0.9 | [pflag](https://github.com/spf13/pflag/tree/v1.0.9) | BSD-3-Clause |
| `github.com/tetratelabs/wazero` v1.12.0 | [wazero](https://github.com/tetratelabs/wazero/tree/v1.12.0) | Apache-2.0 |
| `golang.org/x/net` v0.57.0 | [Go net](https://github.com/golang/net/tree/v0.57.0) | BSD-3-Clause |
| `golang.org/x/sys` v0.47.0 | [Go sys](https://github.com/golang/sys/tree/v0.47.0) | BSD-3-Clause |
| `golang.org/x/text` v0.40.0 | [Go text](https://github.com/golang/text/tree/v0.40.0) | BSD-3-Clause |

## Embedded PDFium WebAssembly engine

go-pdfium v1.19.8 embeds PDFium 151.0.7881.0 WebAssembly. Its source binary
distribution is
[`bblanchon/pdfium-binaries` tag `chromium/7881`](https://github.com/bblanchon/pdfium-binaries/releases/tag/chromium%2F7881),
asset `pdfium-wasm.tgz`, SHA-256
`added6e8ac024f71cb61cf2b77a205d178e2bdde2e4048fbcd916f68b7264d56`.
The archive's `licenses/` directory is the authoritative notice set for this
engine build and contains exactly these entries:

| Component | Upstream origin | License represented by the archived notice |
| --- | --- | --- |
| pdfium-binaries packaging | [bblanchon/pdfium-binaries](https://github.com/bblanchon/pdfium-binaries/tree/chromium/7881) | MIT |
| PDFium | [PDFium](https://pdfium.googlesource.com/pdfium/) | BSD-3-Clause |
| Abseil | [abseil-cpp](https://github.com/abseil/abseil-cpp) | Apache-2.0 |
| simdutf | [simdutf](https://github.com/simdutf/simdutf) | MIT |
| FreeType | [FreeType](https://gitlab.freedesktop.org/freetype/freetype) | FTL OR GPL-2.0-only |
| zlib | [zlib](https://github.com/madler/zlib) | Zlib |
| libjpeg-turbo | [libjpeg-turbo](https://github.com/libjpeg-turbo/libjpeg-turbo) | IJG, BSD-3-Clause, and Zlib terms identified by upstream |
| fast_float | [fast_float](https://github.com/fastfloat/fast_float) | MIT |
| Anti-Grain Geometry 2.3 | [AGG](https://agg.sourceforge.net/) | permissive AGG 2.3 notice (`LicenseRef-AGG-2.3`) |
| OpenJPEG | [OpenJPEG](https://github.com/uclouvain/openjpeg) | BSD-2-Clause |
| ICU | [ICU](https://github.com/unicode-org/icu) | Unicode-3.0 |
| libtiff | [libtiff](https://gitlab.com/libtiff/libtiff) | libtiff |
| libpng | [libpng](https://github.com/pnggroup/libpng) | Libpng-2.0 |
| Little CMS | [Little CMS](https://github.com/mm2/Little-CMS) | MIT |

The archived notice texts, rather than this summary, control the terms of the
engine components. The repository does not redistribute the engine archive or
the pdfium-cli executable.
