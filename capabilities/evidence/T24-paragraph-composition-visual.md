# T24 independent visual evidence

Capability: `composition.layout.paragraph-areas`

Acceptance Profile: `T24-paragraph-composition`

Profile record: `capabilities/evidence/T24-paragraph-composition.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `visual`

Result: `pass`

Producer kind: `external-tool`

Producer: `pdfium-cli`

Producer version: `v0.11.2-pdfium-chromium-7881`

Input ID-neutral SHA-256: `ecb27d14cd1996e4f1b31b424533fb5536f08929a8f83d20f452ee069473815a`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Primary font SHA-256: `e2fbb634c3c0fe78efb449bde4426d10a93aa813596ff5cf3360f02ec97673fb`

Fallback font SHA-256: `ced760bc126036779fa84ad3da4638733032512457bf599c44d8c455360f75e1`

Expected declaration SHA-256: `54cf4bab0b32ba250a137fc82733c14d451862894f37ab444ee72abf9def784d`

Geometry tolerance in points: `1.0E-4`


Expected declaration: `pages=2; MediaBox=[0,0,612,792]; fontSize=40; runs=(1,AA ,72,604),(1,AA ,72,564),(1,BΩ ,232,563.2),(2,BΩ BΩ,72,691.2); graphic=(1,[232,600,264,632],rgb[0.2,0.4,0.8]); advances=A:24,B:26,space:10,omega:28; tolerance=0.0001`

Both pages must pass their pinned PDFium/ImageMagick profiles. Expected rasters come from the separate hand-positioned reference PDF; that reference never invokes paragraph composition.

- [Page 1 profile and differences](T24-paragraph-composition-page-1-visual.md)
- [Page 2 profile and differences](T24-paragraph-composition-page-2-visual.md)
- [Hand-positioned reference PDF](artifacts/T24-paragraph-composition-reference.pdf)

Final determination: `pass`
