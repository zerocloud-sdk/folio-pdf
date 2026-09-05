# T25 qpdf syntax evidence

Capability: `composition.layout.paragraph-pagination`

Acceptance Profile: `T25-paragraph-overflow-reject`

Profile record: `capabilities/evidence/T25-paragraph-overflow-reject.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `syntax`

Result: `pass`

Producer kind: `external-tool`

Producer: `qpdf`

Producer version: `12.4.0`

Tool distribution SHA-256: `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`

Input ID-neutral SHA-256: `55486e8f13f6081a3581f30ae628487b1dfc2daa87e9178605e2d2276e7bfab3`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Final determination: `pass`

## Findings and artifact

- Product: [`artifacts/T25-paragraph-overflow-reject.pdf`](artifacts/T25-paragraph-overflow-reject.pdf)
- qpdf findings: [`artifacts/T25-paragraph-overflow-reject-qpdf.txt`](artifacts/T25-paragraph-overflow-reject-qpdf.txt)
- qpdf completed `--check` for the T25 product with exit code `0`.

This syntax chain does not establish PDF standards conformance.
