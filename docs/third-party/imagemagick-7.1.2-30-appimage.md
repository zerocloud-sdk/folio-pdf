# ImageMagick 7.1.2-30 AppImage validation-tool notice manifest

This manifest applies only to the operator-supplied
`ImageMagick-7.1.2-30-gcc-x86_64.AppImage` used by the T07 repository-only
Acceptance Evidence path. It is tied to the direct asset/executable SHA-256
`372af8a3fd61ef5f15c6331cde3e21f840eb165d8b533f34ed05d68736dd682e`.
The AppImage is stored only in the ignored `.build-cache/` directory and is
not included in a Folio PDF artifact.

Extraction with the AppImage's own `--appimage-extract` operation inventories
the following executable and shared-library payload. Headers, static
ImageMagick libraries, configuration, icons, and manuals in the same image are
covered by the corresponding component rows. The profile compares PNGs only;
it does not invoke a PDF delegate.

| Payload component | Upstream origin | License |
| --- | --- | --- |
| AppImage type-2 runtime and `AppRun` packaging | [AppImageKit](https://github.com/AppImage/AppImageKit) | MIT |
| ImageMagick, `magick`, MagickCore, and MagickWand | [ImageMagick 7.1.2-30](https://github.com/ImageMagick/ImageMagick/tree/7.1.2-30) | ImageMagick License |
| OpenEXR 2.5 (`Half`, `Iex`, `IlmImf`, `IlmThread`, `Imath`) | [OpenEXR](https://github.com/AcademySoftwareFoundation/openexr) | BSD-3-Clause |
| `libXau` | [libXau](https://gitlab.freedesktop.org/xorg/lib/libxau) | MIT |
| `libXdmcp` | [libXdmcp](https://gitlab.freedesktop.org/xorg/lib/libxdmcp) | MIT |
| `libaom` | [Alliance for Open Media AOM](https://aomedia.googlesource.com/aom/) | BSD-2-Clause |
| Brotli (`libbrotlicommon`, `libbrotlidec`) | [Brotli](https://github.com/google/brotli) | MIT |
| `libbsd` | [libbsd](https://gitlab.freedesktop.org/libbsd/libbsd) | permissive ISC, MIT, and BSD-family terms; see upstream `COPYING` |
| bzip2 | [bzip2](https://sourceware.org/bzip2/) | bzip2-1.0.6 |
| dav1d | [dav1d](https://code.videolan.org/videolan/dav1d) | BSD-2-Clause |
| libde265 | [libde265](https://github.com/strukturag/libde265) | LGPL-3.0-or-later |
| libdeflate | [libdeflate](https://github.com/ebiggers/libdeflate) | MIT |
| DjVuLibre | [DjVuLibre](https://sourceforge.net/projects/djvu/) | GPL-2.0-or-later |
| GLib | [GLib](https://gitlab.gnome.org/GNOME/glib) | LGPL-2.1-or-later |
| GNU OpenMP runtime (`libgomp`) | [GCC](https://gcc.gnu.org/git/) | GPL-3.0-or-later WITH GCC-exception-3.1 |
| Graphite2 | [Graphite2](https://github.com/silnrsi/graphite) | LGPL-2.1-or-later OR MPL-2.0 OR GPL-2.0-or-later OR MIT |
| libheif | [libheif](https://github.com/strukturag/libheif) | LGPL-3.0-or-later |
| ICU (`libicudata`, `libicuuc`) | [ICU](https://github.com/unicode-org/icu) | Unicode-3.0 |
| JBIG-KIT (`libjbig`) | [JBIG-KIT](https://www.cl.cam.ac.uk/~mgk25/jbigkit/) | GPL-2.0-or-later |
| libjpeg-turbo (`libjpeg`) | [libjpeg-turbo](https://github.com/libjpeg-turbo/libjpeg-turbo) | IJG, BSD-3-Clause, and Zlib terms identified by upstream |
| Little CMS (`liblcms2`) | [Little CMS](https://github.com/mm2/Little-CMS) | MIT |
| Liquid Rescale (`liblqr`) | [liblqr](https://github.com/carlobaldassi/liblqr) | LGPL-3.0-or-later for the library; GPL-3.0-or-later for other project files |
| XZ Utils (`liblzma`) | [XZ Utils](https://github.com/tukaani-project/xz) | public domain for the bundled generation; current upstream uses 0BSD |
| `libmd` | [libmd](https://gitlab.freedesktop.org/libbsd/libmd) | permissive BSD-family and ISC terms; see upstream `COPYING` |
| `libnuma` | [numactl](https://github.com/numactl/numactl) | LGPL-2.1-only |
| OpenJPEG (`libopenjp2`) | [OpenJPEG](https://github.com/uclouvain/openjpeg) | BSD-2-Clause |
| PCRE | [PCRE](https://github.com/PCRE2Project/pcre1) | BSD-3-Clause |
| libpng | [libpng](https://github.com/pnggroup/libpng) | Libpng-2.0 |
| Raqm | [libraqm](https://github.com/HOST-Oman/libraqm) | MIT |
| libtiff | [libtiff](https://gitlab.com/libtiff/libtiff) | libtiff |
| libwebp | [libwebp](https://chromium.googlesource.com/webm/libwebp/) | BSD-3-Clause |
| x265 | [x265](https://bitbucket.org/multicoreware/x265_git/) | GPL-2.0-or-later |
| libxml2 | [libxml2](https://gitlab.gnome.org/GNOME/libxml2) | MIT |
| Zstandard (`libzstd`) | [zstd](https://github.com/facebook/zstd) | BSD-3-Clause OR GPL-2.0-only |

On Linux, `ldd` against the extracted `magick` payload and its MagickCore
library additionally resolves these host components. They are not copied by
the provisioner and their exact versions come from the validation host:

| Host component | Upstream origin | License |
| --- | --- | --- |
| GNU C Library (`libc`, `libm`, loader) | [glibc](https://sourceware.org/git/glibc.git) | LGPL-2.1-or-later |
| GCC runtimes (`libstdc++`, `libgcc_s`) | [GCC](https://gcc.gnu.org/git/) | GPL-3.0-or-later WITH GCC-exception-3.1 |
| Fontconfig | [fontconfig](https://gitlab.freedesktop.org/fontconfig/fontconfig) | MIT |
| FreeType | [FreeType](https://gitlab.freedesktop.org/freetype/freetype) | FTL OR GPL-2.0-only |
| libX11 | [libX11](https://gitlab.freedesktop.org/xorg/lib/libx11) | MIT |
| zlib | [zlib](https://github.com/madler/zlib) | Zlib |
| HarfBuzz | [HarfBuzz](https://github.com/harfbuzz/harfbuzz) | MIT |
| FriBidi | [FriBidi](https://github.com/fribidi/fribidi) | LGPL-2.1-or-later |
| Expat | [Expat](https://github.com/libexpat/libexpat) | MIT |
| libxcb | [libxcb](https://gitlab.freedesktop.org/xorg/lib/libxcb) | MIT |

The AppImage includes its ImageMagick license text and selected package
copyright files. Upstream component notices, rather than this summary, control
their respective terms. The repository neither modifies nor redistributes the
AppImage.
