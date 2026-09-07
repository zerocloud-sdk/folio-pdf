# Barcode character-set data

T31 uses the Unicode Consortium's ISO-8859-10, -14 and -16 mapping data
to make explicit barcode encoding independent of their availability in a
particular JDK. The runtime contains the upper 96 entries as Unicode literals
in the private `BarcodeIso8859` class; the lower 160 entries map identically.
No Charset provider is installed by the product and no dependency is added.

The repository-only ZXing decoding environment has a decoding-only Charset
provider for these names. It reads the original mapping files independently
of the product class. It cannot encode, so tests cannot accidentally supply
the implementation's missing encoder. Inputs and expected text are literal
project-owned fixtures.

The original files are table version 2.0, with headers updated 2015-12-02.
They are copyright 2015 Unicode, Inc., under Unicode License V3. The complete
permission notice accompanies the data in the root NOTICE, which is included
in binary and source artifacts.

| Source | SHA-256 of original file |
| --- | --- |
| [8859-10.TXT](https://www.unicode.org/Public/MAPPINGS/ISO8859/8859-10.TXT) | `3441c37377c6721586255282ef4302c0ca61796674361959b3a2591f823a0e67` |
| [8859-14.TXT](https://www.unicode.org/Public/MAPPINGS/ISO8859/8859-14.TXT) | `5e72e1c79b2907504111c0965953261c4584cb984465cd88d616452c12fd3782` |
| [8859-16.TXT](https://www.unicode.org/Public/MAPPINGS/ISO8859/8859-16.TXT) | `9164ce844b2bf3f56805c2e9ab8453cc53868757e7f75c4b8dcee7376894d55c` |

ISO-8859-12 was not defined; the Native Interface rejects that name. Other
defined ISO-8859 encodings, Cp437, Shift_JIS and UTF-8 use strict JDK encoding
with explicit ECI selection. No replacement, normalization or implicit UTF-8
fallback is permitted. UTF-8 means ECI 26 and is independently decoded per
symbology; ECI-ignoring scanners are outside this claim.
