# ZXing core 3.5.3 — repository acceptance only

`com.google.zxing:core:3.5.3` supplies independent barcode readers and public
module writers to T30/T31 consumer tests and the repository-only acceptance
module. It is never a product runtime dependency, fallback encoder, Worker
dependency or correctness callback.

- Origin: [versioned upstream tree](https://github.com/zxing/zxing/tree/zxing-3.5.3)
  and [immutable Maven version](https://repo.maven.apache.org/maven2/com/google/zxing/core/3.5.3/).
- License: Apache-2.0, copyright ZXing authors. No upstream source is shaded
  into product artifacts; T31's repository-only decoder adaptation is described below. The repository Apache-2.0 LICENSE supplies
  the license text; the core JAR contains no additional NOTICE file.
- Binary SHA-256: `8d8064c1636fdaef7189dd9055c7d59950a8940a12f2293956446ec3c109fd82`.
- Source SHA-256: `f983454400d73652ad5236413ba7eba48072e042982a84b10e1468506f4b28ae`.
- Runtime: Java 8 class-file version 52; no required transitive dependencies.

Code128, Code39, Codabar, EAN/UPC and ITF readers consume actual PDF-derived
scanlines. Their public writers define independent expected modules. Original
project readers cover standalone supplements, MSI and legacy postal modes;
no decoding algorithms or expected modules are derived from Okapi or iText
output. The decoders consume the actual product vectors and rasters.

## T31 ECC200 control metadata adaptation

ZXing 3.5.3's ECC200 text parser skips the Structured Append latch but then
interprets its three header words as payload. The repository-only
`T31DataMatrixDecoder`, `T31DataMatrixBitMatrixParser`,
`T31DataMatrixDataBlock`, `T31DataMatrixVersion` and
`T31DataMatrixDecodedBitStreamParser` adapt its decoder under the project
namespace, retaining every source copyright/license header. The change consumes
and validates all three Structured Append words and exposes their metadata;
Macro and reader-programming controls are also recorded. Placement extraction,
deinterleaving, ECC validation and payload decoding remain the independent
ZXing algorithms. This is not a barcode encoder and is never a Worker or
product runtime component.

Original files in the pinned source JAR have SHA-256 values:

| File | SHA-256 |
| --- | --- |
| Decoder.java | `c40ec827b70903ec26478ba72cfef70e0ef643230e26087c52c0f11449250cc9` |
| BitMatrixParser.java | `fe61553035166ba6d3c41ab3d46d195e5cbc0f2e584d74a043e1931c1a356ce6` |
| DataBlock.java | `d1cebf1cc53a77c31f064ada4d8bddc85f63a1bb79c6f7689f0860f22a0e8b30` |
| Version.java | `3a28942ad2073834a64d4e98791c5b288e70958d94b6685b5309484c4ad911d4` |
| DecodedBitStreamParser.java | `833c342065096822def05531442de30544d6a9fdc1a8840eeff7242b063bc5aa` |


## T31 complete matrix assertions

`T31MatrixOracle` adapts QR format/version coordinate reading and the paired
column traversal from ZXing's QR `BitMatrixParser.java` (source SHA-256
`f1a9bed7fdaf186df49a5048589142270a9102340cb15f2d97c39402d0969d5d`).
Its source retains the upstream copyright and Apache-2.0 notice. The surrounding
exact finder, separator, alignment, timing, BCH, mask remainder, compaction and
padding checks are original project assertions. ZXing's public version tables,
QR decoder, PDF417 symbol table, row decoder and independent GF(929) decoder
supply independent metadata and zero-correction evidence. The PDF417 cluster
formula is the published symbol interpretation also used by ZXing's
`PDF417ScanningDecoder.java` (source SHA-256
`bfba8b5211c1c572c5e60c72cd90f59cb8be13a8d39779d88d36a4e574dcf2cc`).
The ECC200 parser additionally records the padding boundary. Original assertions
check fixed unused placement modules and randomized padding without changing
payload extraction or error correction. A deliberately wrong-padding fixture
uses ZXing's independent ReedSolomonEncoder to recompute ECC, proving that
otherwise valid payload/ECC cannot hide invalid padding. This is negative
test data, never a fallback product encoder or a positive expected matrix.

The T31 decoding-only Charset provider reads the same original Unicode mapping
files described in [character-set provenance](barcode-character-sets.md).
Its SPI registration exists only in the repository-only acceptance artifact;
product artifacts and Workers do not install it.

Independent RAW review probes also use the unmodified public QR, DataMatrix
and PDF417 decoders to observe published product modules. The PDF417 decoder's
ECI/byte-compaction control rules and ECC200 decoder's remaining-capacity rule
informed original project validator repairs after those probes found payload
changes. The product validators do not copy these decoder implementations or
depend on ZXing at runtime. Exact probe sources, outputs, repair observations
and hashes are retained in the T31 delivery evidence.
