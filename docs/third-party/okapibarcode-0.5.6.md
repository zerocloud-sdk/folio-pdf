# OkapiBarcode 0.5.6

T30 uses `uk.org.okapibarcode:okapibarcode:0.5.6`, selected in ADR-0010,
solely as a private symbol encoder. Folio PDF owns validation, point geometry,
font selection, PDF paths and workflow publication. No Okapi type is exposed
by the Native Interface and the dependency is not shaded into Folio artifacts.

- Origin: [official project](https://github.com/woo-j/OkapiBarcode),
  [Maven Central version directory](https://repo.maven.apache.org/maven2/uk/org/okapibarcode/okapibarcode/0.5.6/).
- License: Apache-2.0, as declared by the versioned upstream POM and source
  headers. Copyright holders include Robin Stuart, Daniel Gredler and Robert
  Elliott. The existing repository Apache-2.0 LICENSE supplies the license
  text. No upstream source is copied into this repository.
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
