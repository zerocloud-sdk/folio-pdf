# T28 independent seven-profile visual evidence

Capability: `composition.layout.paragraph-areas`

Acceptance Profile: `T28-unicode`

Profile record: `capabilities/evidence/T28-unicode.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `visual`

Result: `pass`

Producer kind: `external-tool`

Producer: `pdfium-cli`

Producer version: `v0.11.2-pdfium-chromium-7881`

Input ID-neutral SHA-256: `473f03a4331432670d1fb2b8ad8a30e3b52564323817d4e62f55b15ab34e87f6`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

ICU4J implementation version: `77.1`

Geometry tolerance in points: `0.0001`

Producer owned-memory budget in bytes: `2147483648`

Reference PDF SHA-256: `4992be611d8a3b22bbcd18676a9829270cfcc3743176ac4c2e29fbdaa8bce26d`

T28-corpus.properties SHA-256: `eeea1f4c28a704d7c55b409ffb4c4a8b8bbc029cbf5ec5287d7ff1f3f2d7d2b5`

T28-glyphs.tsv SHA-256: `170fce06d3baffdab6aa350255097b85b5401db6e616f127b05a30e6281dbc19`

T28-reference-receipt.json SHA-256: `da5f6a859ef587daa845f88531044d8a707d934eb61d348f29083bffb0314daa`

Font manifest SHA-256: `99e9747a1d4fbd756a5318c87e0ed0f2a53b0a0e68caa4af734f52b33b97e653`


Every page must pass the original 144-DPI opaque-white sRGB profile: zero-fuzz AE 0 and zero changed RGB pixels against the independent raw PDF/fontTools reference. Secondary renderer disagreement is limited to 12000 changed pixels. Missing tools remain INDETERMINATE.

- [latin](T28-unicode-latin-visual.md)
- [greek](T28-unicode-greek-visual.md)
- [cyrillic](T28-unicode-cyrillic-visual.md)
- [cjk-sc](T28-unicode-cjk-sc-visual.md)
- [cjk-tc](T28-unicode-cjk-tc-visual.md)
- [cjk-jp](T28-unicode-cjk-jp-visual.md)
- [cjk-kr](T28-unicode-cjk-kr-visual.md)

[Independent reference PDF](artifacts/T28-unicode-reference.pdf)

Final determination: `pass`
