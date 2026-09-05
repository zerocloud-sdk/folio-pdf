# T23 qpdf syntax evidence

Capability: `conversion.rendering`

Acceptance Profile: `T23-page-rendering`

Profile record: `capabilities/evidence/T23-page-rendering.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `syntax`

Result: `pass`

Producer kind: `external-tool`

Producer: `qpdf`

Producer version: `12.4.0`

Tool distribution SHA-256: `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`

Input ID-neutral SHA-256: `0fb21c7dca14068cbebeafac3d08df6a0d16fb3286fe785c4bd0deb47beaa72c`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Final determination: `pass`

## Findings and artifact

- Product: [`artifacts/T23-page-rendering.pdf`](artifacts/T23-page-rendering.pdf)
- qpdf findings: [`artifacts/T23-page-rendering-qpdf.txt`](artifacts/T23-page-rendering-qpdf.txt)
- qpdf completed `--check` for the T23 product with exit code `0`.

This syntax chain does not establish PDF standards conformance.
