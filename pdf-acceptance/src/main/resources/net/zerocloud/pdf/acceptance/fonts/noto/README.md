# T28 Noto reference data (OFL-1.1)

These complete fonts are test and independent acceptance inputs for #29. They
are packaged only by the repository-only pdf-acceptance artifact and copied
into pdf-document's **test** resources. They are never runtime defaults,
downloaded by a workflow, or included in the shipped pdf-document JAR.

`fonts.properties` pins each final static font's SHA-256, byte length, PostScript
name and full glyph count. `sources.json` pins immutable upstream URLs, original
SHA-256 values, lengths and the two unmodified OFL notices. `instances.json`
links each derived CJK file to its original and its generation settings.

| Fonts | Upstream version and commit | Preparation |
| --- | --- | --- |
| Noto Sans Regular (Latin/Greek/Cyrillic) | 2.008, noto-fonts `ffebf8c1ee449e544955a7e813c54f9b73848eac` | Unmodified static hinted TrueType, 3,748 glyphs |
| Noto Sans Hebrew Regular (bidi probe) | 3.000, same commit | Unmodified static hinted TrueType, 149 glyphs |
| Noto Sans CJK SC/TC/JP/KR Regular | 2.004, noto-cjk `523d033d6cb47f4a80c58a35753646f5c3608a78` (Sans2.004) | Full static `wght=400` instance of each region's official TrueType variable font, 65,535 glyphs each |

The static CJK instances retain all source glyphs, names and license metadata,
OpenType layout tables and vertical metrics. They have no variation axes and
are not pre-subset, stripped of layout data, reduced to the corpus alphabet,
or modified to fit the earlier T19 validator. The instancer updates names from
the source STAT declarations; the resulting regional PostScript abbreviations
are uppercase (`NotoSansCJKJP-Regular`, etc.). No shaping is performed by this
recipe. The original variable sources are not bundled or accepted as runtime
fonts by this slice.

## Offline reproduction

Fetch the immutable inputs in `sources.json` into an explicit directory before
running the recipe; downloads are not part of an acceptance execution. Install
fontTools **4.59.2** in an isolated test-tool environment. Its universal wheel
`fonttools-4.59.2-py3-none-any.whl` has SHA-256
`8bd0f759020e87bb5d323e6283914d9bf4ae35a7307dafb2cbd1e379e720ad37`.

For each region `sc`, `tc`, `jp`, `kr`, run from the repository root, substituting
the explicit source and a **new** destination path:

```sh
python3 scripts/t28-reference-fonts.py /explicit/upstream/NotoSansCJKjp-VF.ttf /new/output/NotoSansCJKjp-Regular.ttf
T28_UPSTREAM=/explicit/upstream python3 -m unittest discover -s scripts/tests -p test_t28_reference_fonts.py
```

The recipe rejects incorrect source SHA-256 values before writing, pins the
complete tool version, preserves source timestamps, sets only `wght=400`, and
reports the result's SHA-256. Compare it to `fonts.properties`/`instances.json`.
The regular Sans/Hebrew files and both OFL notices are copied without changes.

OFL copyright and permission notices are in `OFL-noto-fonts.txt` and
`OFL-noto-cjk.txt`; their terms govern these font files separately from the
project's Apache-2.0 source. The test-tool license is recorded under
`docs/third-party/`. No iText material was used.
