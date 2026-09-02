# T13 qpdf syntax evidence

Capability: `document.text-structure.extract`

Acceptance Profile: `T13-text-logical-structure`

Profile record: `capabilities/evidence/T13-text-logical-structure.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `syntax`

Result: `pass`

Producer kind: `external-tool`

Producer: `qpdf`

Producer version: `12.4.0`

Tool distribution SHA-256: `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`

Input ID-neutral set SHA-256: `9bf763bbade9dba9ec74d454b0f6473412fd5b46a9c39ad7e09073c45a49ab60`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Final determination: `pass`

## Findings and artifacts

- Front product: [`artifacts/T13-page-text.pdf`](artifacts/T13-page-text.pdf)
- Back product: [`artifacts/T13-tagged-structure.pdf`](artifacts/T13-tagged-structure.pdf)
- qpdf findings: [`artifacts/T13-text-logical-structure-qpdf.txt`](artifacts/T13-text-logical-structure-qpdf.txt)
- qpdf completed `--check` for both T13 products with exit code `0`.

This syntax chain does not establish PDF standards conformance. The mandatory standards, semantic, and visual chains remain absent.
