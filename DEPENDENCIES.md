# Dependency and license information

T01 has one direct runtime dependency: Apache PDFBox 3.0.8. Maven resolves the
following required runtime artifacts. All are permissively licensed and remain
implementation details of `pdf-document`.

| Maven coordinate | Relationship | License |
| --- | --- | --- |
| `org.apache.pdfbox:pdfbox:3.0.8` | direct | Apache License 2.0 |
| `org.apache.pdfbox:pdfbox-io:3.0.8` | transitive | Apache License 2.0 |
| `org.apache.pdfbox:fontbox:3.0.8` | transitive | Apache License 2.0 |
| `commons-logging:commons-logging:1.4.0` | transitive | Apache License 2.0 |

PDFBox declares Bouncy Castle support as optional; T01 does not select or use
those artifacts.

The verification and repository build-tool dependency graph does not enter the
published runtime:

| Maven coordinate | Relationship | License |
| --- | --- | --- |
| `junit:junit:4.13.2` | direct test | Eclipse Public License 1.0 |
| `org.hamcrest:hamcrest-core:1.3` | transitive test | BSD 3-Clause License |
| `org.yaml:snakeyaml:2.2` | direct repository build tool | Apache License 2.0 |

SnakeYAML parses the two checked-in YAML authorities through its safe
constructor. The internal `pdf-inventory-tool` is skipped for Maven install and
deploy and is not selected by the BOM.

T02 adds one version-pinned build plugin:

| Maven coordinate | Role | License |
| --- | --- | --- |
| `org.codehaus.mojo:exec-maven-plugin:3.6.3` | invoke the repository-only inventory command during validation and generation | Apache License 2.0 |

T04 adds no third-party runtime, test, or build-tool dependency. The resource-
only stable Migration Facade has no dependencies. The preview facade depends
only on the existing first-party `pdf-document` artifact at runtime and uses
the existing JUnit dependency for tests.

T05 adds no third-party runtime, test, build-tool, native, or remote-service
dependency. `pdf-provider-contract` uses only Java 8 platform types;
`pdf-document` and `pdf-conversion` depend on that first-party artifact. The
controlled subprocess fixture is project-authored test code launched with the
JDK already running the test suite, and existing JUnit 4.13.2 remains the only
test dependency. No external engine is bundled or downloaded.

T09 adds no third-party runtime, test, build-tool, native, or external-tool
dependency. The backend-neutral PDF Value and Document Patch surface reuses
Apache PDFBox 3.0.8 only behind private implementation types and uses the
existing JUnit 4.13.2 dependency for public-workflow consumer tests. No
Acceptance Evidence executable, external fixture, or Migration Facade
dependency is added.

T10 adds no third-party runtime, test, build-tool, native, or external-tool
dependency. Page operations, ordered named-Source merge, and range split reuse
Apache PDFBox 3.0.8 only behind private implementation types and use the
existing JUnit 4.13.2 dependency for public-workflow consumer tests. The
project-authored nested-page-tree fixture is generated in test code; no
external PDF fixture or Migration Facade dependency is added. T10 reuses the
existing repository-only pinned qpdf 12.4.0 acceptance path and adds no new
external executable or runtime dependency.

T11 adds no third-party runtime, test, build-tool, native, or external-tool
dependency. Document information, XMP metadata, outline, named destination,
page destination, and embedded-file management reuses Apache PDFBox 3.0.8
only behind private implementation types and uses the existing JUnit 4.13.2
dependency for public-workflow consumer tests. Metadata fixtures are
generated in test code; no external PDF fixture or Migration Facade
dependency is added. T11 reuses the existing repository-only pinned qpdf
12.4.0 acceptance path and adds no new external executable or runtime
dependency.

T06 uses one external executable only in the opt-in, repository-only
Acceptance Evidence path:

| Tool | Role | Source and integrity | License |
| --- | --- | --- | --- |
| qpdf 12.4.0 official Linux x86-64 binary archive | independent `--check` syntax chain | `https://github.com/qpdf/qpdf/releases/download/v12.4.0/qpdf-12.4.0-bin-linux-x86_64.zip`; archive SHA-256 `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`; `bin/qpdf` SHA-256 `9ac787a28597e8428289a12ba3fedafd74bdfb4b4da1be814722faf76f14f21b` | Apache License 2.0 for qpdf |

The SHA-pinned upstream archive contains these shared libraries. They are
local validation-tool prerequisites, not Folio PDF runtime dependencies:

| Archive member | Upstream origin | License |
| --- | --- | --- |
| `libqpdf.so.30.4.0` | [qpdf](https://github.com/qpdf/qpdf) | Apache License 2.0 |
| `libffi.so.8` | [libffi](https://github.com/libffi/libffi) | MIT |
| `libgnutls.so.30` | [GnuTLS](https://gitlab.com/gnutls/gnutls) | LGPL-2.1-or-later for the main library |
| `libnettle.so.8`, `libhogweed.so.6` | [Nettle](https://git.lysator.liu.se/nettle/nettle) | LGPL-3.0-or-later OR GPL-2.0-or-later |
| `libidn2.so.0` | [GNU Libidn2](https://gitlab.com/libidn/libidn2) | LGPL-3.0-or-later OR GPL-2.0-or-later |
| `libjpeg.so.8` | [libjpeg-turbo](https://github.com/libjpeg-turbo/libjpeg-turbo) | IJG, modified BSD, and zlib-style terms documented by upstream |
| `libp11-kit.so.0` | [p11-kit](https://github.com/p11-glue/p11-kit) | BSD-3-Clause |
| `libtasn1.so.6` | [GNU Libtasn1](https://gitlab.com/gnutls/libtasn1) | LGPL-2.1-or-later |
| `libunistring.so.2` | [GNU libunistring](https://www.gnu.org/software/libunistring/) | LGPL-3.0-or-later OR GPL-2.0-or-later |

On Linux, the bundle additionally links to host-provided GNU C Library
(`libc`, `libm`, and the loader; LGPL-2.1-or-later), GCC runtimes (`libstdc++`
and `libgcc_s`; GPL-3.0-or-later with GCC Runtime Library Exception 3.1), zlib
(`libz`; zlib License), and GNU MP (`libgmp`; LGPL-3.0-or-later OR
GPL-2.0-or-later). These remain host components and are not copied by the
provisioner.

The official archive includes its dynamically linked runtime libraries. It is
operator-supplied, verified, expanded only into the ignored `.build-cache/`
directory, and neither committed nor redistributed by this repository. qpdf
and those bundled runtime libraries do not enter the Maven dependency graph,
the BOM, or any shipped Folio PDF artifact. The normal build has no network or
system-qpdf requirement.

T07 adds two external executables only to the same opt-in, repository-only
Acceptance Evidence path:

| Tool | Role | Source and integrity | License |
| --- | --- | --- | --- |
| pdfium-cli v0.11.2 WebAssembly Linux amd64, embedding PDFium Chromium build 7881 | independent PDF-to-PNG renderer | `https://github.com/klippa-app/pdfium-cli/releases/download/v0.11.2/pdfium-webassembly-linux-amd64`; direct release-asset and executable SHA-256 `3ef3375c429ce665e834f933a028225bf28ac837695aaa69c6fc21facf6780ab` | MIT for pdfium-cli and go-pdfium; BSD-3-Clause for PDFium |
| ImageMagick 7.1.2-30 GCC x86-64 AppImage | fixed-size PNG AE comparison and difference-raster generation | `https://github.com/ImageMagick/ImageMagick/releases/download/7.1.2-30/ImageMagick-7.1.2-30-gcc-x86_64.AppImage`; direct release-asset and executable SHA-256 `372af8a3fd61ef5f15c6331cde3e21f840eb165d8b533f34ed05d68736dd682e` | ImageMagick License |

The exact linked Go modules and the PDFium engine's archived `licenses/`
inventory are identified by the pinned
[pdfium-cli notice manifest](docs/third-party/pdfium-cli-v0.11.2.md). The
PDFium 151.0.7881.0 source engine archive is additionally pinned as
`pdfium-wasm.tgz`, SHA-256
`added6e8ac024f71cb61cf2b77a205d178e2bdde2e4048fbcd916f68b7264d56`.
Every shared-library payload component extracted from the exact ImageMagick
AppImage is identified by the pinned
[ImageMagick AppImage notice manifest](docs/third-party/imagemagick-7.1.2-30-appimage.md).
Those delegates are local validation-tool components and are never invoked on
PDF input by this profile. Both single-file distributions are supplied by the
operator, verified offline, and copied only into ignored `.build-cache/`
directories. The ImageMagick notice manifest also records every host library
resolved by the extracted executable; those host components are not copied by
the provisioner. Neither external tool is redistributed.

T07 also uses the existing Apache PDFBox 3.0.8 dependency inside the
repository-only `pdf-acceptance` module to produce secondary implementation-
renderer disagreement evidence. PDFBox cannot make the visual chain pass and
remains Apache-2.0. No PDFium, ImageMagick, PDFBox Renderer, or acceptance-tool
type enters the Native Interface, BOM, or a published product artifact. Normal
Maven builds and tests do not execute or require the two external tools.

Review the resolved graph with:

```text
./mvnw -B -ntp -pl pdf-provider-contract,pdf-document,pdf-conversion,pdf-acceptance,build-tools/inventory dependency:tree
```

Formal releases additionally require generated SBOM, license, vulnerability,
and reproducibility evidence as described in [RELEASING.md](RELEASING.md).
