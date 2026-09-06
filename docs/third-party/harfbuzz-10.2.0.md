# HarfBuzz 10.2.0: separately installed T29 engine

HarfBuzz is not a Maven dependency or a bundled product binary. T29 uses an
explicit native installation and a project-owned adapter to its public C API;
no unofficial Java HarfBuzz wrapper is used. The independent oracle invokes
the upstream `hb-shape` utility, separately from the project adapter.

- Upstream release: <https://github.com/harfbuzz/harfbuzz/releases/tag/10.2.0>
- Source: `harfbuzz-10.2.0.tar.xz`, SHA-256
  `620e3468faec2ea8685d32c46a58469b850ef63040b3565cde05959825b48227`.
- License: MIT-Old. The unchanged upstream
  [COPYING](harfbuzz-10.2.0-COPYING.txt) has SHA-256
  `ba8f810f2455c2f08e2d56bb49b72f37fcf68f1f4fade38977cfd7372050ad64`.
- C interface documentation: [buffers](https://harfbuzz.github.io/harfbuzz-hb-buffer.html)
  and [shaping](https://harfbuzz.github.io/harfbuzz-hb-shape.html).

The initial Linux x86-64 independent-oracle installation was built from the
unchanged archive on Ubuntu 24.04 with GCC 13.3.0, GNU binutils 2.42, Meson
1.3.2, Ninja 1.11.1, pkgconf 1.8.1 and Python 3.12.3. GCC/binutils use GPL
licenses with their applicable runtime exceptions; Meson and Ninja use
Apache-2.0, pkgconf uses ISC and Python uses PSF-2.0. These are local
build/acceptance tools, not shipped Java dependencies.

The [installation receipt](harfbuzz-10.2.0-linux-oracle-installation.json)
records executable and linked-library hashes, exact Ubuntu package versions,
available package download URLs and SHA-256 values, and hashes of installed
copyright records. The pre-existing libc6 and zlib1g package archives are no
longer in the configured mirror; their archive hashes are unavailable, while
the installed linked-file hashes and copyright records are recorded. It
includes observed GLib/FreeType and their linked dependencies.
Although `freetype=disabled` was passed, this upstream utility build enables
discovered FreeType integration; its actual linked closure is recorded.
The oracle explicitly selects `--font-funcs=ot --shapers=ot`, so it never asks
FreeType for font metrics or rasterization.

The initial recipe, substituting explicit source/build/install directories:

```sh
meson setup BUILD SOURCE --prefix=INSTALL --libdir=lib --buildtype=release \
  -Dglib=enabled -Dgobject=disabled -Dcairo=disabled -Dchafa=disabled \
  -Dicu=disabled -Dgraphite2=disabled -Dfreetype=disabled -Dtests=disabled \
  -Dintrospection=disabled -Ddocs=disabled -Dbenchmark=disabled -Dutilities=enabled
meson compile -C BUILD -j 2
meson install -C BUILD
```

The installed Linux utility has no RPATH. Its loader environment must select
this installation explicitly; running it against the host library is not the
reference recipe. After separately installing fontTools 4.59.2 into the
chosen Python environment, the independent-reference authoring and test commands are:

```sh
T29_NATIVE_INSTALLATION=/explicit/harfbuzz-10.2.0
export LD_LIBRARY_PATH="$T29_NATIVE_INSTALLATION/lib"
export T29_HB_SHAPE="$T29_NATIVE_INSTALLATION/bin/hb-shape"
python3 scripts/t29-shaping-reference.py "$T29_HB_SHAPE" /new/reference-directory
python3 -m unittest discover -s scripts/tests -p test_t29_shaping_reference.py
python3 -m unittest discover -s scripts/tests -p test_t29_reference_subsets.py
python3 scripts/t29-verify-subsets.py scripts/container-bin/qpdf \
  /new/reference-directory/T29-shaping-reference.pdf \
  /new/reference-directory/T29-oracle.json
```

This Linux authoring command rejects the official tool's linked-library
version-mismatch report, resolves the library with `ldd` in the same loader
environment, and records its file hash plus both generation-script hashes.
A project-authored Linux `LD_PRELOAD` negative fixture changes only the public
version report and must prevent reference publication. Its C source is a test
fixture, not an engine or runtime wrapper. Other platforms consume the frozen
independent oracle and still require their own native product-execution
receipts; these Linux authoring tests do not certify those platforms.

The installed `hb-shape` SHA-256 is
`efd07617a878dddf0d1ef86be848051db4282eee8cacb66253b437cdb6def3fb`;
`libharfbuzz.so.0.61020.0` is
`c1f95d16a61a8eb4f0c60f980522d57bf0c8fddbf62851c49cf77d2664b8044b`.
These are observations of this installation, not portable binary hashes for
other platforms. Each platform needs its own actual execution and linked
engine receipt. This receipt is **INDETERMINATE** for T29 product acceptance;
an installed reference tool alone proves no product behavior.

## Project adapter installation

[`scripts/install-harfbuzz.py`](../../scripts/install-harfbuzz.py) is the
separate product-helper build entry point. It consumes the same pinned source
archive and a new caller-selected directory; it downloads nothing and does
not run during Maven packaging. Its native Meson project uses the public
[executable install-rpath contract](https://mesonbuild.com/Reference-manual_functions_executable.html)
and [pkg-config dependency lookup](https://mesonbuild.com/Dependencies.html#pkg-config).
The C source and build/installation scripts are project-authored Apache-2.0.

The adapter build disables optional upstream bindings and utilities. It
isolates pkg-config search because HarfBuzz 10.2.0 otherwise probes for
FreeType even with `freetype=disabled`. HarfBuzz's built-in Unicode data and
OpenType shaper remain enabled. The independent oracle installation above
retains its original utility dependencies and hashes.

Each new installation records actual compiler/tool versions, executable
hashes, installed engine-library hashes, source and adapter hashes, build
commands, effective compiler options/environment and resolved linker hashes in
`installation.json`. Both compilation databases and the verbose build log are
retained and hashed, as is upstream COPYING. Inherited `DESTDIR` is cleared;
the installer selects the recorded PATH Ninja and pkg-config executables even
when the parent environment supplies overrides. Build-tool source and license authorities are
[Meson](https://github.com/mesonbuild/meson),
[Ninja](https://github.com/ninja-build/ninja),
[pkgconf](https://github.com/pkgconf/pkgconf),
[Python](https://www.python.org/psf/license/),
[GCC](https://gcc.gnu.org/), and
[GNU binutils](https://www.gnu.org/software/binutils/).
Observed Linux package versions, archive/executable hashes and notice hashes
remain in the initial installation receipt above. Other compiler/platform
receipts must identify their actual toolchain and notices before certification.

The installer command tests separately build the real native helper, including
relocation, inherited build-environment and caller-directory ownership cases:

```sh
export T29_HARFBUZZ_ARCHIVE=/explicit/harfbuzz-10.2.0.tar.xz
python3 -m unittest discover -s scripts/tests -p test_t29_native_installation.py
```

The Linux linker-selection fixture copies the installed GNU `ld` executable
into a temporary directory and appends a file-identity marker. GCC selects that
real linker through `-B`; the receipt must identify and hash that executable.
The temporary copy is removed after the test and is not distributed. This
uses the public GCC [linker-selection options](https://gcc.gnu.org/onlinedocs/gcc/Link-Options.html)
and [program-name query](https://gcc.gnu.org/onlinedocs/gcc/Developer-Options.html)
contracts, not copied compiler or linker implementation code.

## Observed Linux product-helper installation

The reviewed installer was executed on Linux x86-64 against the pinned source
archive on 2026-09-06. Its unchanged
[installation receipt](../../capabilities/evidence/artifacts/T29-linux-native-installation.json),
[verbose build log](../../capabilities/evidence/artifacts/T29-linux-native-build.txt),
[engine compilation database](../../capabilities/evidence/artifacts/T29-linux-engine-compile-commands.json)
and [adapter compilation database](../../capabilities/evidence/artifacts/T29-linux-adapter-compile-commands.json)
are retained. Receipt paths describe that actual installation; the evidence
copies use the linked filenames and retain identical bytes and hashes.

| Observed artifact | SHA-256 |
| --- | --- |
| Project helper | `169389e19e28bc96e3e878a9468671c31ccc6d5e7abbc3d38c4b8470526cdc65` |
| `libharfbuzz.so.0.61020.0` | `8a4c67dc9ead9b0752bcf256fa46c39eb3592e403a282bb341637ed989196e10` |
| Installation receipt | `b17c12c25135d2aea3708dabcbd436e8ca1f1b88ce5e14d6bc8ebd3f2ad1b178` |

The [live startup observation](../../capabilities/evidence/artifacts/T29-linux-native-observation.json)
passes for this helper and engine and records all mapped file hashes. It is a
separate invocation in the inherited loader environment. The project observer
uses the public Linux [procfs mapping interface](https://docs.kernel.org/filesystems/proc.html);
it downloads nothing, copies no kernel code and adds no runtime dependency.
Preload/audit interposition and other-platform observation remain
INDETERMINATE. A temporarily renamed real HarfBuzz SONAME is used only by the
negative test and is removed afterward. Full platform acceptance still needs
the product's numeric, semantic, subset, syntax and raster results.
