# T25 independent visual evidence

Capability: `composition.layout.paragraph-pagination`

Acceptance Profile: `T25-paragraph-relayout`

Profile record: `capabilities/evidence/T25-paragraph-relayout.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `visual`

Result: `pass`

Producer kind: `external-tool`

Producer: `pdfium-cli`

Producer version: `v0.11.2-pdfium-chromium-7881`

Input ID-neutral SHA-256: `3b586e28020431ba8cf6f5ad817f499e49e194edb44e085b364a7d8b20a3b686`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Primary font SHA-256: `e2fbb634c3c0fe78efb449bde4426d10a93aa813596ff5cf3360f02ec97673fb`

Fallback font SHA-256: `ced760bc126036779fa84ad3da4638733032512457bf599c44d8c455360f75e1`

Expected declaration SHA-256: `273e3a59b158d87bed4a9f074ed241498bc3c744e681337bb97733824d022bdf`

Geometry tolerance in points: `1.0E-4`


Expected declaration: `pages=2;MediaBox=[0,0,612,792];fontSize=40;tolerance=0.0001;rule=relayout;runs=(1,AAAAAA,72.0,692.0)(2,AAAAAA,72.0,692.0)(2,A,72.0,652.0)`

Both pinned page comparisons are mandatory. Expected rasters are rendered from the hand-positioned reference, which never calls paragraph composition.

- [Page 1](T25-paragraph-relayout-page-1-visual.md)
- [Page 2](T25-paragraph-relayout-page-2-visual.md)
- [Independent reference](artifacts/T25-paragraph-relayout-reference.pdf)

Final determination: `pass`
