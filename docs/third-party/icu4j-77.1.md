# ICU4J 77.1 — internal Unicode processing

Coordinate: `com.ibm.icu:icu4j:77.1` (no transitive Maven dependencies).
Distribution: [Maven Central JAR](https://repo.maven.apache.org/maven2/com/ibm/icu/icu4j/77.1/icu4j-77.1.jar).
Full JAR SHA-256:
`b3640b9f416a4411fd33c59abbeea8fd57d024c23e1819bf9673220a97499fe3`.
Byte length: `14663227`.

The version is fixed by ADR-0010 and #29. This is a Java 8 compatible,
unbundled implementation dependency of pdf-document. ICU types are confined
to internal implementation; they are not Native Interface values or a backend
SPI. The Hardened Worker admits only the complete hash-pinned JAR using the
same exact-code-source validation as the existing dependencies.

ICU Unicode segmentation and bidi processing do not provide glyph shaping,
kerning, ligature substitution or combining-mark attachment. HarfBuzz and the
Foundation's complete script/platform certification remain separate work.
Tests and acceptance fonts are separately licensed offline resources, never
runtime font defaults.

License: Unicode License V3, copyright © 2016–2025 Unicode, Inc., plus the
upstream consolidated third-party notices. The complete unmodified
[upstream 77.1 LICENSE](https://github.com/unicode-org/icu/blob/release-77-1/LICENSE)
is retained in [icu4j-77.1-LICENSE.txt](icu4j-77.1-LICENSE.txt), SHA-256
`451167c55c0fa447cc2d5632714f5e3c567fe4f1e1badefab2c1333852198aca`.
The root NOTICE identifies the dependency and these notice locations.
