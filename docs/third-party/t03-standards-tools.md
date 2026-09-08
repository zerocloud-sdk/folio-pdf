# T03 independent standards tools

These tools are separately installed, acceptance-only processes. No executable,
upstream source/model, font or dependency from either installation enters a
product jar, POM dependency graph, BOM or source archive. The recorder itself
uses only existing acceptance code and Java 8 APIs. No iText implementation,
fixture, resource, binary-derived information or proprietary add-on material
was consulted for this work.

| Identity | pdfcpu | Arlington TestGrammar |
| --- | --- | --- |
| Version | 0.15.0, commit `f2686555`, upstream Go 1.26.5 build | 0.81, source commit `fe4a1a8897ec07f674c73160c35d748b29052f8f` |
| Source | [official release](https://github.com/pdfcpu/pdfcpu/releases/tag/v0.15.0) | [fixed public source](https://github.com/pdf-association/arlington-pdf-model/tree/fe4a1a8897ec07f674c73160c35d748b29052f8f) |
| Download | `pdfcpu_0.15.0_Linux_x86_64.tar.xz` | source tar.gz for the fixed commit |
| Download SHA-256 | `652830db95e81868dbe38fbb3f506365511c99a1831b5e8955deac38f3f645f8` | `587265b2af48561079147da04868ed5cd7f9753d1fa339c0ffb8cbf12cc6abc4` |
| Executable SHA-256 | `5d1a9ff691ae1d720ba822fdc93d48ffc306aadd9497a858bf270b780e7d7c7d` | `45de36669a3aad3087346335acdfbe716a27beb27f3a39202d3f8f4997ba7458` |
| Model | built into executable | `tsv/latest`, SHA-256 `334aa8d6ccd88c96cf01c463971f3079206a47f5cf2101bf6d8cf81f374d5408` |
| License | Apache-2.0; upstream Go dependencies retain their own licenses | Apache-2.0 software, with upstream NOTICE identifying other documentation as CC-BY-4.0; embedded PDFium and components retain their licenses |

The authoritative operational settings are
[pdfcpu-pin.properties](../../scripts/pdfcpu-pin.properties) and
[arlington-pin.properties](../../scripts/arlington-pin.properties).
pdfcpu always receives `--mode strict --conf disable --offline`; no user
configuration, fonts or link-validation option is selected. Arlington uses its
bundled PDFium parser, the hashed TSV model, `--force 1.7 --no-color`, and no
extensions. Its PDF-file path has no network service. Certification runs also
disable container networking. Each invocation is limited to 10 seconds and
1 MiB of combined stdout/stderr; rule sets have at most 128 entries. The
recorder retains bounded partial output on limit failure and returns an
INDETERMINATE observation.

Tool qualification is separate from installation and from final candidate
certification. The [22-rule profile](../../capabilities/profiles/T03-standards/README.md)
and [original qualification findings](../../capabilities/evidence/T70-standards-qualification/README.md)
record what was actually detected, including each tool's gaps. A PDF/A or
PDF/UA validation result is not substituted for ordinary-PDF coverage.

## Recreate the acceptance installations

Obtain the exact archives from the public links above and check their SHA-256
before extracting or executing them. Extract the pdfcpu archive with its single
top-level directory stripped into `.build-cache/pdfcpu/0.15.0`. Extract the
Arlington source archive similarly into `.build-cache/arlington/fe4a1a8`,
excluding `TestGrammar/pdfix/` and `TestGrammar/bin/`. Select only PDFium when
building; the unused PDFix SDK is not part of this installation.

The recorded Linux build uses CMake 3.28.3 and GCC 13.3.0
(`Ubuntu 13.3.0-6ubuntu2~24.04.1`). This is the actual build command, run from
the repository root after offline extraction:

```python
from pathlib import Path
import subprocess

root = Path('.build-cache/arlington/fe4a1a8').resolve()
flags = (f'-ffile-prefix-map={root}=/arlington '
         '-Wno-builtin-macro-redefined '
         '-D__DATE__=\'"Sep  8 2026"\' -D__TIME__=\'"00:00:00"\' ')
subprocess.run(['cmake', '-S', str(root / 'TestGrammar'),
                '-B', str(root / 'build'), '-DPDFSDK_PDFIUM=ON',
                '-DCMAKE_BUILD_TYPE=Release', '-DCMAKE_C_FLAGS=' + flags,
                '-DCMAKE_CXX_FLAGS=' + flags], check=True)
subprocess.run(['cmake', '--build', str(root / 'build'), '-j', '4'], check=True)
```

Verify the resulting executable against the pin. A different compiler or
binary requires a reviewed new pin and new qualification/certification; an
installation receipt alone cannot make it acceptable. The model digest hashes
sorted UTF-8 `FILENAME SHA256\n` lines for every immediate `.tsv` file. Symlinks,
missing files and an empty model cannot satisfy the recorder.

Preserve the upstream license/notice files in the local installations. The
pdfcpu [go.mod](https://github.com/pdfcpu/pdfcpu/blob/v0.15.0/go.mod) identifies
its exact dependencies; its official archive supplies `LICENSE.txt`.
Arlington supplies `LICENSE`, `NOTICE.txt`, and Sarge's BSD-3-Clause license;
its public source contains its modified BSD-licensed PDFium copy and the
embedded codec/font components' copyright/license declarations. The selected
build dynamically uses the environment's libstdc++, libgcc, libm and libc;
the immutable certification image binds those system libraries. None of these
third-party materials is redistributed by this contribution.
# Private comparator runtime

The first actual T70 JDK 8 run retained visual `INDETERMINATE` because the pinned
Ubuntu JDK image lacked ImageMagick host dependencies. The existing AppImage,
visual profile, expected PNG, and thresholds remain unchanged. Its wrapper now
supports a separately pinned private runtime; when present or explicitly
selected with `IMAGEMAGICK_RUNTIME_DIRECTORY`, missing, changed, or extra entries
fail before the comparator starts. Without that directory it retains the
existing host-library behavior.

The following unmodified libraries were copied from Ubuntu 24.04 packages on the
validation host into ignored `.build-cache/imagemagick/7.1.2-30/runtime/`.
[The SHA-256 manifest](../../scripts/imagemagick-runtime.sha256) fixes every byte;
the package notices and actual four-image version probes are retained in
`capabilities/evidence/T70-comparator-runtime/`.

| Library | Observed Ubuntu package/version | Packaged license |
| --- | --- | --- |
| libX11.so.6 | libx11-6 2:1.8.7-1build1 | MIT-family X11 terms |
| libxcb.so.1 | libxcb1 1.15-1ubuntu2 | MIT |
| libharfbuzz.so.0 | libharfbuzz0b 8.3.0-2build2 | MIT |
| libgraphite2.so.3 | libgraphite2-3 1.3.14-2ubuntu0.24.04.1 | LGPL-2.1-or-later OR MPL-1.1 OR GPL-2.0-or-later; packaging LGPL-2.1-or-later |
| libfribidi.so.0 | libfribidi0 1.0.13-3build1 | LGPL-2.1-or-later |

For the exact Ubuntu packages above, provision from the repository root:

```sh
set -e
runtime_dir=.build-cache/imagemagick/7.1.2-30/runtime
mkdir -p "$runtime_dir"
while read -r expected_digest library_name; do
    printf '%s  %s\n' "$expected_digest" "/lib/x86_64-linux-gnu/$library_name" | sha256sum --check --status
    cp -L "/lib/x86_64-linux-gnu/$library_name" "$runtime_dir/$library_name"
done < scripts/imagemagick-runtime.sha256
scripts/container-bin/imagemagick --version
```

These dependencies remain acceptance-only, are absent from product artifacts,
and are not redistributed by the project. The comparator's private HarfBuzz 8.3.0
is distinct from Folio's explicitly selected and independently observed 10.2.0
native installation. The fixed Ubuntu/JDK image digests are not changed.
