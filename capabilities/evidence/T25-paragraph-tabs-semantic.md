# T25 independent semantic evidence

Capability: `composition.layout.paragraph-pagination`

Acceptance Profile: `T25-paragraph-tabs`

Profile record: `capabilities/evidence/T25-paragraph-tabs.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `semantic`

Result: `pass`

Producer kind: `project-test`

Producer: `folio-pdf-t25-semantic-assertions`

Producer version: `0.1.0-SNAPSHOT`

Input ID-neutral SHA-256: `cf23b39fe636355445b96d658abcd3cde22f899e695decb0e26b56a0f392fdc7`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Primary font SHA-256: `e2fbb634c3c0fe78efb449bde4426d10a93aa813596ff5cf3360f02ec97673fb`

Fallback font SHA-256: `ced760bc126036779fa84ad3da4638733032512457bf599c44d8c455360f75e1`

Expected declaration SHA-256: `3f01a670d77a3b459249887878807e8453f922a055344567b75be3c6ca6fdbf9`

Geometry tolerance in points: `1.0E-4`


Expected declaration: `pages=2;MediaBox=[0,0,612,792];fontSize=40;tolerance=0.0001;rule=tabs;runs=(1,A,72.0,692.0)(1,AB,168.0,692.0)(1,A,72.0,652.0)(1,AB,143.0,652.0)(1,A,72.0,612.0)(1,AB,118.0,612.0)(1,A,72.0,572.0)(1,AB,144.0,572.0)(1,A,72.0,532.0)(1,AB,118.0,532.0)(1,A,72.0,492.0)(1,AB,216.0,492.0)(2,A,72.0,692.0)(2,AB,168.0,692.0)(2,A,72.0,652.0)(2,AB,143.0,652.0)(2,A,72.0,612.0)(2,AB,118.0,612.0)(2,A,72.0,572.0)(2,AB,144.0,572.0)(2,A,72.0,532.0)(2,AB,118.0,532.0)(2,A,72.0,492.0)(2,AB,216.0,492.0)`

The observer reopens the PDF through public queries and checks the independent numeric oracle.

[Detailed observations](artifacts/T25-paragraph-tabs-semantic.txt)

Final determination: `pass`
