# T31 qpdf syntax evidence

Capability: `composition.barcodes.two-dimensional`

Acceptance Profile: `T31-two-dimensional-barcodes`

Profile record: `capabilities/evidence/T31-two-dimensional-barcodes.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `syntax`

Result: `pass`

Producer kind: `external-tool`

Producer: `qpdf`

Producer version: `12.4.0`

Tool distribution SHA-256: `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`

Input ID-neutral SHA-256: `e7970cedeaea670f2ab2404b835857cd1ec1b58a7728257d4d5b8c368eb1d398`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Final determination: `pass`

## Findings and artifact

- Product: [`artifacts/T31-two-dimensional-barcodes-hardened-worker.pdf`](artifacts/T31-two-dimensional-barcodes-hardened-worker.pdf)
- qpdf findings: [`artifacts/T31-two-dimensional-barcodes-hardened-worker-qpdf.txt`](artifacts/T31-two-dimensional-barcodes-hardened-worker-qpdf.txt)
- qpdf completed `--check` for the T31 product with exit code `0`.

This syntax chain does not establish PDF standards conformance.
