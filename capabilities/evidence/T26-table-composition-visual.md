# T26 independent visual evidence

Capability: `composition.layout.tables`

Acceptance Profile: `T26-table-composition`

Profile record: `capabilities/evidence/T26-table-composition.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `visual`

Result: `pass`

Producer kind: `external-tool`

Producer: `pdfium-cli`

Producer version: `v0.11.2-pdfium-chromium-7881`

Input ID-neutral SHA-256: `483a67ded611b2768595b079d7bf4a13634e78175ca479759c140262a435cdc9`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Primary font SHA-256: `e2fbb634c3c0fe78efb449bde4426d10a93aa813596ff5cf3360f02ec97673fb`

Fallback font SHA-256: `ced760bc126036779fa84ad3da4638733032512457bf599c44d8c455360f75e1`

Expected declaration SHA-256: `1aa113522a83df202df124edcae295a5354c433b434c1c13605f1b7ea46966ca`

Geometry tolerance in points: `1.0E-4`


Expected declaration: `pages=3; MediaBox=[0,0,612,792]; size=10; fixed=[40,50,110],padding=3; auto=[43,57],padding=2; spans=[40,40,40],padding=2; borders=1 inside black; rows=[20],[18],[18,18]; runs=(1,A,76,709),(1,B,116,709),(1,omega,166,708.8),(2,AA,75,710),(2,BBBB,118,710),(3,A,75,710),(3,BB,115,710),(3,B,115,692),(3,omega,155,691.8); reading-order=ABomega/AABBBB/ABBBomega; tolerance=0.0001; PDFium=144dpi opaque-white sRGB; ImageMagick AE=0 fuzz=0%; secondary changed-pixels<=2500`

All three pages must pass their pinned PDFium/ImageMagick profiles. Expected rasters come from the separate hand-positioned reference PDF; that reference never invokes table composition.

- [Page 1 profile and differences](T26-table-composition-page-1-visual.md)
- [Page 2 profile and differences](T26-table-composition-page-2-visual.md)
- [Page 3 profile and differences](T26-table-composition-page-3-visual.md)
- [Hand-positioned reference PDF](artifacts/T26-table-composition-reference.pdf)

Final determination: `pass`
