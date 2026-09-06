# T28 qpdf syntax evidence

Capability: `composition.layout.paragraph-areas`

Acceptance Profile: `T28-unicode`

Profile record: `capabilities/evidence/T28-unicode.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `syntax`

Result: `pass`

Producer kind: `external-tool`

Producer: `qpdf`

Producer version: `12.4.0`

Tool distribution SHA-256: `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`

Input ID-neutral SHA-256: `473f03a4331432670d1fb2b8ad8a30e3b52564323817d4e62f55b15ab34e87f6`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Final determination: `pass`

## Findings and artifact

- Product: [`artifacts/T28-unicode.pdf`](artifacts/T28-unicode.pdf)
- qpdf findings: [`artifacts/T28-unicode-qpdf.txt`](artifacts/T28-unicode-qpdf.txt)
- qpdf completed `--check` for the T28 product with exit code `0`.

This syntax chain does not establish PDF standards conformance.
