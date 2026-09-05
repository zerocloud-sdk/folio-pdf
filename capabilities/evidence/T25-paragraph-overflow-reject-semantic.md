# T25 independent semantic evidence

Capability: `composition.layout.paragraph-pagination`

Acceptance Profile: `T25-paragraph-overflow-reject`

Profile record: `capabilities/evidence/T25-paragraph-overflow-reject.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `semantic`

Result: `pass`

Producer kind: `project-test`

Producer: `folio-pdf-t25-semantic-assertions`

Producer version: `0.1.0-SNAPSHOT`

Input ID-neutral SHA-256: `55486e8f13f6081a3581f30ae628487b1dfc2daa87e9178605e2d2276e7bfab3`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Primary font SHA-256: `e2fbb634c3c0fe78efb449bde4426d10a93aa813596ff5cf3360f02ec97673fb`

Fallback font SHA-256: `ced760bc126036779fa84ad3da4638733032512457bf599c44d8c455360f75e1`

Expected declaration SHA-256: `a6087963004beeb42d47b1ab42e9dd5295f885f864c01faf591f1f519ab505f3`

Geometry tolerance in points: `1.0E-4`


Expected declaration: `pages=2;MediaBox=[0,0,612,792];fontSize=40;tolerance=0.0001;rule=overflow-reject;runs=(2,AAAA,72.0,692.0)`

The observer reopens the PDF through public queries and checks the independent numeric oracle.

[Detailed observations](artifacts/T25-paragraph-overflow-reject-semantic.txt)

Final determination: `pass`
