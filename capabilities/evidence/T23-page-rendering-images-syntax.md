# T23 qpdf syntax evidence

Capability: `conversion.rendering`

Acceptance Profile: `T23-page-rendering-images`

Profile record: `capabilities/evidence/T23-page-rendering.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `syntax`

Result: `pass`

Producer kind: `external-tool`

Producer: `qpdf`

Producer version: `12.4.0`

Tool distribution SHA-256: `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`

Input ID-neutral SHA-256: `acafb2dd244e34c49feedc7eff7db5fd063d1b834049d83228704ee4cbde32f6`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Final determination: `pass`

## Findings and artifact

- Product: [`artifacts/T23-page-rendering-images.pdf`](artifacts/T23-page-rendering-images.pdf)
- qpdf findings: [`artifacts/T23-page-rendering-images-qpdf.txt`](artifacts/T23-page-rendering-images-qpdf.txt)
- qpdf completed `--check` for the T23 product with exit code `0`.

This syntax chain does not establish PDF standards conformance.
