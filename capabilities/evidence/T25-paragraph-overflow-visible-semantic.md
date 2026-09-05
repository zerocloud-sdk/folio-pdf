# T25 independent semantic evidence

Capability: `composition.layout.paragraph-pagination`

Acceptance Profile: `T25-paragraph-overflow-visible`

Profile record: `capabilities/evidence/T25-paragraph-overflow-visible.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `semantic`

Result: `pass`

Producer kind: `project-test`

Producer: `folio-pdf-t25-semantic-assertions`

Producer version: `0.1.0-SNAPSHOT`

Input ID-neutral SHA-256: `050d2d58b606970851447a4cd0a7f245f591a41b71ba97253546780a9e89e16c`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Primary font SHA-256: `e2fbb634c3c0fe78efb449bde4426d10a93aa813596ff5cf3360f02ec97673fb`

Fallback font SHA-256: `ced760bc126036779fa84ad3da4638733032512457bf599c44d8c455360f75e1`

Expected declaration SHA-256: `5df5de0cf6d56e6d8ba43ea5e1cb8878dc6ace350ce78bb3cbbcf37d93d5d74a`

Geometry tolerance in points: `1.0E-4`


Expected declaration: `pages=2;MediaBox=[0,0,612,792];fontSize=40;tolerance=0.0001;rule=overflow-visible;runs=(1,AAAA,72.0,692.0)(1,AAAA,72.0,652.0)(1,AAAA,232.0,692.0)(1,AAAA,232.0,652.0)(2,AAAA,72.0,692.0)`

The observer reopens the PDF through public queries and checks the independent numeric oracle.

[Detailed observations](artifacts/T25-paragraph-overflow-visible-semantic.txt)

Final determination: `pass`
