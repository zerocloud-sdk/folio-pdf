# T25 independent semantic evidence

Capability: `composition.layout.paragraph-pagination`

Acceptance Profile: `T25-paragraph-keep-together`

Profile record: `capabilities/evidence/T25-paragraph-keep-together.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `semantic`

Result: `pass`

Producer kind: `project-test`

Producer: `folio-pdf-t25-semantic-assertions`

Producer version: `0.1.0-SNAPSHOT`

Input ID-neutral SHA-256: `690dbc8593ba4408f05d5fbf0ae3ecb671a8e2b608f476437b2b4e36003eb89d`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Primary font SHA-256: `e2fbb634c3c0fe78efb449bde4426d10a93aa813596ff5cf3360f02ec97673fb`

Fallback font SHA-256: `ced760bc126036779fa84ad3da4638733032512457bf599c44d8c455360f75e1`

Expected declaration SHA-256: `c62d348231ebf60e065d18b4c2c3318216c8a2833e1b8096e92bc58d25b3e8bb`

Geometry tolerance in points: `1.0E-4`


Expected declaration: `pages=2;MediaBox=[0,0,612,792];fontSize=40;tolerance=0.0001;rule=keep-together;runs=(1,A,72.0,692.0)(1,A,72.0,652.0)(1,A,232.0,692.0)(2,B,72.0,692.0)(2,B,72.0,652.0)`

The observer reopens the PDF through public queries and checks the independent numeric oracle.

[Detailed observations](artifacts/T25-paragraph-keep-together-semantic.txt)

Final determination: `pass`
