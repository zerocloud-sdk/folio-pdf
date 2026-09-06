# T28 cjk-sc reference profile

Status: `experimental`
Capability: `composition.layout.paragraph-areas`
Acceptance Profile: `T28-unicode-cjk-sc`
Release train: `0.1.0-SNAPSHOT`

Page 4 of the shared seven-page product uses the [fixed manual corpus](../profiles/T28-unicode-reference.md)
and explicit pinned [Noto fonts](../../pdf-acceptance/src/main/resources/net/zerocloud/pdf/acceptance/fonts/noto/README.md).
The [semantic/geometry record](T28-unicode-semantic.md) checks every displayed
scalar, source glyph ID, font choice, baseline, advance and matrix within
0.0001 point. The shared [qpdf record](T28-unicode-syntax.md) checks syntax.

The [independent visual record](T28-unicode-cjk-sc-visual.md) passes at the original
144-DPI opaque-white sRGB profile: zero-fuzz primary AE and changed pixels
are both zero; secondary changed pixels are 3925, below 12000.
Expected pixels come from the separate raw PDF/fontTools writer, never ICU
or Folio layout. No threshold was relaxed. See the [delivery record](T28-unicode.md)
for overall verification status, review and remaining standards/dependency/
Foundation font-platform gates. This profile does not establish shaping or
compatible status.
