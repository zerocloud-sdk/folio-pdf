# T25 qpdf syntax evidence

Capability: `composition.layout.paragraph-pagination`

Acceptance Profile: `T25-paragraph-relayout`

Profile record: `capabilities/evidence/T25-paragraph-relayout.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `syntax`

Result: `pass`

Producer kind: `external-tool`

Producer: `qpdf`

Producer version: `12.4.0`

Tool distribution SHA-256: `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`

Input ID-neutral SHA-256: `3b586e28020431ba8cf6f5ad817f499e49e194edb44e085b364a7d8b20a3b686`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Final determination: `pass`

## Findings and artifact

- Product: [`artifacts/T25-paragraph-relayout.pdf`](artifacts/T25-paragraph-relayout.pdf)
- qpdf findings: [`artifacts/T25-paragraph-relayout-qpdf.txt`](artifacts/T25-paragraph-relayout-qpdf.txt)
- qpdf completed `--check` for the T25 product with exit code `0`.

This syntax chain does not establish PDF standards conformance.
