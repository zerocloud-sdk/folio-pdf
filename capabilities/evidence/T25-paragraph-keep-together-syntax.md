# T25 qpdf syntax evidence

Capability: `composition.layout.paragraph-pagination`

Acceptance Profile: `T25-paragraph-keep-together`

Profile record: `capabilities/evidence/T25-paragraph-keep-together.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `syntax`

Result: `pass`

Producer kind: `external-tool`

Producer: `qpdf`

Producer version: `12.4.0`

Tool distribution SHA-256: `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`

Input ID-neutral SHA-256: `690dbc8593ba4408f05d5fbf0ae3ecb671a8e2b608f476437b2b4e36003eb89d`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Final determination: `pass`

## Findings and artifact

- Product: [`artifacts/T25-paragraph-keep-together.pdf`](artifacts/T25-paragraph-keep-together.pdf)
- qpdf findings: [`artifacts/T25-paragraph-keep-together-qpdf.txt`](artifacts/T25-paragraph-keep-together-qpdf.txt)
- qpdf completed `--check` for the T25 product with exit code `0`.

This syntax chain does not establish PDF standards conformance.
