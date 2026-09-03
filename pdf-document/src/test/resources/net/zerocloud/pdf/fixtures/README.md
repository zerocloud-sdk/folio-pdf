# T19 project-authored fonts

`FolioPrimary.ttf.base64` and `FolioFallback.ttf.base64` are textual,
base64-encoded TrueType fixtures authored for Folio PDF T19. Their outlines,
character maps, metrics, names, timestamps, table ordering, and bytes were
constructed from project-owned values without copying a third-party font.

- `FolioPrimary.ttf`: 972 decoded bytes; SHA-256
  `e2fbb634c3c0fe78efb449bde4426d10a93aa813596ff5cf3360f02ec97673fb`;
  glyphs `.notdef`, space, `A` (600 units), `B` (650), and unrelated `Z`
  (620), at 1000 units per em.
- `FolioFallback.ttf`: 1028 decoded bytes; SHA-256
  `ced760bc126036779fa84ad3da4638733032512457bf599c44d8c455360f75e1`;
  glyphs `.notdef`, space, `A` (800 units), and Greek capital omega U+03A9
  (700), at 1000 units per em.

The fixtures are Apache-2.0 project test data under the repository license.
They are not runtime resources and are not copied into the `pdf-document` jar.
