# T24 qpdf syntax evidence

Capability: `composition.layout.paragraph-areas`

Acceptance Profile: `T24-paragraph-composition`

Profile record: `capabilities/evidence/T24-paragraph-composition.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `syntax`

Result: `pass`

Producer kind: `external-tool`

Producer: `qpdf`

Producer version: `12.4.0`

Tool distribution SHA-256: `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`

Input ID-neutral SHA-256: `ecb27d14cd1996e4f1b31b424533fb5536f08929a8f83d20f452ee069473815a`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Final determination: `pass`

## Findings and artifact

- Product: [`artifacts/T24-paragraph-composition.pdf`](artifacts/T24-paragraph-composition.pdf)
- qpdf findings: [`artifacts/T24-paragraph-composition-qpdf.txt`](artifacts/T24-paragraph-composition-qpdf.txt)
- qpdf completed `--check` for the T24 product with exit code `0`.

This syntax chain does not establish PDF standards conformance.
