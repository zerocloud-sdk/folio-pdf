# T30 qpdf syntax evidence

Capability: `composition.barcodes.one-dimensional`

Acceptance Profile: `T30-one-dimensional-barcodes`

Profile record: `capabilities/evidence/T30-one-dimensional-barcodes.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `syntax`

Result: `pass`

Producer kind: `external-tool`

Producer: `qpdf`

Producer version: `12.4.0`

Tool distribution SHA-256: `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`

Input ID-neutral SHA-256: `371f5b24f655e7c93ef1b1df8d3a9cdeb975fb68c841459efb075d817ba94507`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Final determination: `pass`

## Findings and artifact

- Product: [`artifacts/T30-one-dimensional-barcodes-in-process.pdf`](artifacts/T30-one-dimensional-barcodes-in-process.pdf)
- qpdf findings: [`artifacts/T30-one-dimensional-barcodes-in-process-qpdf.txt`](artifacts/T30-one-dimensional-barcodes-in-process-qpdf.txt)
- qpdf completed `--check` for the T30 product with exit code `0`.

This syntax chain does not establish PDF standards conformance.
