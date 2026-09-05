# T26 qpdf syntax evidence

Capability: `composition.layout.tables`

Acceptance Profile: `T26-table-composition`

Profile record: `capabilities/evidence/T26-table-composition.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `syntax`

Result: `pass`

Producer kind: `external-tool`

Producer: `qpdf`

Producer version: `12.4.0`

Tool distribution SHA-256: `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`

Input ID-neutral SHA-256: `483a67ded611b2768595b079d7bf4a13634e78175ca479759c140262a435cdc9`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Final determination: `pass`

## Findings and artifact

- Product: [`artifacts/T26-table-composition.pdf`](artifacts/T26-table-composition.pdf)
- qpdf findings: [`artifacts/T26-table-composition-qpdf.txt`](artifacts/T26-table-composition-qpdf.txt)
- qpdf completed `--check` for the T26 product with exit code `0`.

This syntax chain does not establish PDF standards conformance.
