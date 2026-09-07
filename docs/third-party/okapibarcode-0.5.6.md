# OkapiBarcode 0.5.6

T30 and T31 use `uk.org.okapibarcode:okapibarcode:0.5.6`, selected in ADR-0010,
solely as a private symbol encoder. Folio PDF owns validation, point geometry,
font selection, PDF paths and workflow publication. No Okapi type is exposed
by the Native Interface and the dependency is not shaded into Folio artifacts.

- Origin: [official project](https://github.com/woo-j/OkapiBarcode),
  [Maven Central version directory](https://repo.maven.apache.org/maven2/uk/org/okapibarcode/okapibarcode/0.5.6/).
- License: Apache-2.0, as declared by the versioned upstream POM and source
  headers. Copyright holders include Robin Stuart, Daniel Gredler and Robert
  Elliott. The existing repository Apache-2.0 LICENSE supplies the license
  text. T30 copied no upstream source; T31's narrow source adaptations are
  identified below and retain the original notices.
- Binary JAR SHA-256: `fc07c5e28f200a53b980e36719901e095e06d1432d7ae959a22456f838765f2a`.
- Source JAR SHA-256: `e3c1dc7a5ca12c158ea4f5e48a339d67141002a815c45076a7655b91e6a6ec4b`.
- Runtime: Java 8 class-file version 52. The required JDK matrix must also
  execute product behavior with this dependency.
- The upstream runtime dependency `com.beust:jcommander:1.82` serves the
  upstream command-line application. Folio excludes it because it invokes
  only the encoder classes. No CLI, SVG, PostScript, Java2D renderer or
  network behavior is invoked by Folio.

Independent decoders are test/acceptance dependencies, never runtime
correctness callbacks or fallback encoders.

## T31 ECC200 extension

The dependency does not expose forced DataMatrix compaction or raw data
codewords. `BarcodeDataMatrixGrid` adapts its ECC200 padding, Reed–Solomon
interleaving, module placement and finder construction. `BarcodeDataMatrixSize`
adapts its size/capacity/region table into immutable project-owned records.
Both are package-private, retain Robin Stuart's 2014 copyright and Apache-2.0
header, and identify the modification. The source is `DataMatrix.java` in the
pinned source JAR, SHA-256
`0e3a17f2ef86b226b5b2dea623dfe90c8e9ba9335b6483288a33741f5d2d947e`.
The original compaction, debugging output and public upstream surface are not
copied. Automatic compaction and the public `ReedSolomon` primitive continue
to use the unmodified dependency. Folio's explicit compaction, validation,
geometry, PDF Forms and Worker transport are authored for this ticket.

The adapted grid and table ship inside `pdf-document`; the complete upstream
library remains an ordinary unbundled dependency. Independent tests use ZXing
3.5.3, expected literal words and zero corrected-codeword assertions.

## T31 PDF417 raw data extension

The public encoder does not accept raw PDF417 codewords. The private
`BarcodePdf417Patterns` adapts only `Pdf417.java`'s three-cluster symbol table,
retaining its Copyright 2014–2017 Robin Stuart, Daniel Gredler and Apache-2.0
header. The original source SHA-256 is
`91609f7f54b38cac762c2774677830bbb3d5f891c7f33cb8ce9b15fb2516e7dd`.
Folio's `BarcodePdf417Grid` supplies original descriptor/padding, GF(929)
polynomial arithmetic, sizing and row framing over that table. It generates
the ECC polynomial from roots 3^1 through 3^k instead of copying the upstream
coefficient table. Ordinary automatic and forced-byte compaction use the
unmodified dependency. Its Macro API suppresses control blocks when the
segment count is one. To preserve this required case, `BarcodePdf417Compaction`
adapts only the same source's text/numeric/byte block compaction and associated
ASCII tables for Macro input, retaining the original header. It exports
bounded data words to Folio's explicit Macro framer instead of copying the
upstream sizing, ECC, MicroPDF417 or splitting implementation. The adapted
classes and original grid ship inside `pdf-document`; no ZXing code is used
by the product encoder.

Independent workflow tests recover the words with ZXing's separate symbol
table, validate row indicators, require zero Reed–Solomon corrections at
every ECC level, and recover the expected literal payload with its reader.
