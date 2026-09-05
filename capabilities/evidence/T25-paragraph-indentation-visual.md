# T25 independent visual evidence

Capability: `composition.layout.paragraph-pagination`

Acceptance Profile: `T25-paragraph-indentation`

Profile record: `capabilities/evidence/T25-paragraph-indentation.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `visual`

Result: `pass`

Producer kind: `external-tool`

Producer: `pdfium-cli`

Producer version: `v0.11.2-pdfium-chromium-7881`

Input ID-neutral SHA-256: `ee665f9acd428ccc47704c1717f48d4f2e41a23f5d9fd76e1f43f308b0447006`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Primary font SHA-256: `e2fbb634c3c0fe78efb449bde4426d10a93aa813596ff5cf3360f02ec97673fb`

Fallback font SHA-256: `ced760bc126036779fa84ad3da4638733032512457bf599c44d8c455360f75e1`

Expected declaration SHA-256: `4e676bc6833aaef0e7f7fbf9a6f7ba3b0e2f99fe23379a4d562eb07b6b3a3fa9`

Geometry tolerance in points: `1.0E-4`


Expected declaration: `pages=2;MediaBox=[0,0,612,792];fontSize=40;tolerance=0.0001;rule=indentation;runs=(1,A,120.0,692.0)(1,AA,96.0,652.0)(1,AA,256.0,692.0)(1,AA,256.0,652.0)(2,AA,96.0,692.0)(2,AA,96.0,652.0)(2,AA,96.0,612.0)`

Both pinned page comparisons are mandatory. Expected rasters are rendered from the hand-positioned reference, which never calls paragraph composition.

- [Page 1](T25-paragraph-indentation-page-1-visual.md)
- [Page 2](T25-paragraph-indentation-page-2-visual.md)
- [Independent reference](artifacts/T25-paragraph-indentation-reference.pdf)

Final determination: `pass`
