# ZXing core 3.5.3 — repository acceptance only

`com.google.zxing:core:3.5.3` supplies independent barcode readers and public
module writers to T30 consumer tests and the repository-only acceptance
module. It is never a product runtime dependency, fallback encoder, Worker
dependency or correctness callback.

- Origin: [versioned upstream tree](https://github.com/zxing/zxing/tree/zxing-3.5.3)
  and [immutable Maven version](https://repo.maven.apache.org/maven2/com/google/zxing/core/3.5.3/).
- License: Apache-2.0, copyright ZXing authors. No upstream source is copied
  or shaded into Folio artifacts. The repository Apache-2.0 LICENSE supplies
  the license text; the core JAR contains no additional NOTICE file.
- Binary SHA-256: `8d8064c1636fdaef7189dd9055c7d59950a8940a12f2293956446ec3c109fd82`.
- Source SHA-256: `f983454400d73652ad5236413ba7eba48072e042982a84b10e1468506f4b28ae`.
- Runtime: Java 8 class-file version 52; no required transitive dependencies.

Code128, Code39, Codabar, EAN/UPC and ITF readers consume actual PDF-derived
scanlines. Their public writers define independent expected modules. Original
project readers cover standalone supplements, MSI and legacy postal modes;
neither the reference writer nor any decoder uses Okapi or iText output.
