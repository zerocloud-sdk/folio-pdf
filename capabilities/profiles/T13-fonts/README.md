# Original T13 acceptance fonts

These Apache-2.0 project fixtures were authored for #75 by Codex in the
authorized implementation session. They contain an empty `.notdef` and one
original rectangular glyph: bounds `[0 0 400 600]`, advance 500, at 1000 units
per em. No external glyph outline, iText material or system font was used.

`FolioT13Rectangle.ttf` contains a TrueType `A` and its simple-font `cmap`.
`FolioT13RectangleCID.ttf` preserves the glyph IDs, outlines and metrics in a
separate program without `cmap`, for CIDFontType2. `FolioT13Rectangle.cff`
contains a name-keyed Type1C `A`. `FolioT13RectangleCID.cff` contains CID 1
under the original `Folio/T13/0` character collection, with one font dictionary.
The checked `fonts.json` records their exact byte identities and literal metrics.

The authoring recipe is `scripts/generate-t13-fonts.py`, using the existing
MIT-licensed fontTools 4.59.2 acceptance tool documented in
`docs/third-party/fonttools-4.59.2.md`. Its reviewed pure-Python wheel SHA-256 is
`8bd0f759020e87bb5d323e6283914d9bf4ae35a7307dafb2cbd1e379e720ad37`.
Reproduction uses a fresh destination and the pinned fontTools installation;
the script refuses an existing directory. The TrueType timestamps are fixed.
Public CLI tests parse and check the literal outlines, widths, mapping and
reproducible bytes. These fonts and fontTools remain outside product runtime.

The PDF corpus generator consumes hash-checked font bytes directly and requires
only Python's standard library. Successful font authoring and extraction tests
do not certify any renderer or establish final Foundation certification.

`embedded-font-kinds-reference.pdf` is the original direct-CMap PDF from the
corpus authoring recipe. Its SHA-256 is
`e016ccb3e411f70ee07b9e583ab9791d9664bbb1f0c8c5efa7874cb89cff2ada`.
`embedded-font-kinds-reference.png` has SHA-256
`a835cd6affd09b91f34d189aaa109d96aa36833b796578d5bdaa6f11f412e788`.
The pinned existing PDFium CLI produces it with:

```sh
scripts/container-bin/pdfium render \
  capabilities/profiles/T13-fonts/embedded-font-kinds-reference.pdf \
  /tmp/t13-font-reference.png --dpi 144 --file-type png --pages first
```

The reference encodes the seven independently specified glyph placements and
the original fonts directly. It was prepared before a Folio direct-font rewrite
was observed. Font smoothing comes from the pinned renderer; no Folio raster or
extracted value supplies this reference. The corpus generator verifies both
reference identities and byte equality of its authored source and reference PDF.

The CID TrueType split follows the directly checked ISO 32000-1:2008 §9.9 rule
that CIDFont font programs omit `cmap`. Independent Spec review also found this
rule in the search-indexed Adobe 2017 PDF 2.0 draft §9.9.1. The corresponding
2020 final text was not directly available; no claim about that unobserved text
is made. The conservative original fixture satisfies the checked rule without
changing character-to-glyph IDs or extraction expectations. A fresh pinned
PDFium rendering of the revised reference is byte-identical to the prior PNG.
The prior corpus and all standards controls remain archived under
`T75-delivery/font-corpus-r2`, with old and new identities retained.
