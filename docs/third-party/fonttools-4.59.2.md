# fontTools 4.59.2 — acceptance font preparation only

Distribution: `fonttools-4.59.2-py3-none-any.whl` from
[PyPI's immutable 4.59.2 metadata](https://pypi.org/pypi/fonttools/4.59.2/json).
Wheel SHA-256: `8bd0f759020e87bb5d323e6283914d9bf4ae35a7307dafb2cbd1e379e720ad37`.

The separately installed tool creates complete static Regular instances of
the four pinned Noto CJK 2.004 TrueType variable fonts. It is not a Maven
dependency, shipped runtime, font lookup service or raster renderer. The
offline recipe is [t28-reference-fonts.py](../../scripts/t28-reference-fonts.py).
Its source-hash rejection and full-font generation are exercised through that
command's public boundary in [the tests](../../scripts/tests/test_t28_reference_fonts.py).

The separate [Unicode reference writer](../../scripts/t28-unicode-reference.py)
uses fontTools source cmap/head/hmtx values and independently subsets reference
glyphs with their original IDs. A project-authored raw PDF writer positions
manually declared display lines. Neither the recipe nor its public command
tests call ICU or Folio layout; PDFium renders the resulting independent PDF.

License: MIT, copyright (c) 2017 Just van Rossum. The wheel's complete
[LICENSE](fonttools-4.59.2-LICENSE.txt) and
[LICENSE.external](fonttools-4.59.2-LICENSE.external.txt) notices are retained
verbatim. Font copyright and OFL-1.1 permissions are separate and retained
with [the Noto resources](../../pdf-acceptance/src/main/resources/net/zerocloud/pdf/acceptance/fonts/noto/README.md).

No iText code, resources, fixtures or output were used in this reference-data
recipe. The preparation preserves complete glyph coverage and does not execute
the product implementation.
