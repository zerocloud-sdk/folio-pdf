# T14 qpdf syntax evidence

Capability: `document.images-resources.extract`

Acceptance Profile: `T14-image-resource-extraction`

Profile record: `capabilities/evidence/T14-image-resource-extraction.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `syntax`

Result: `pass`

Producer kind: `external-tool`

Producer: `qpdf`

Producer version: `12.4.0`

Tool distribution SHA-256: `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`

Input ID-neutral set SHA-256: `5114983f25709d75f341b5ae72c963dacdaae58844054ce18ebe97ddf22f5171`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Final determination: `pass`

## Findings and artifacts

- Front product: [`artifacts/T14-image-font-inventory.pdf`](artifacts/T14-image-font-inventory.pdf)
- Back product: [`artifacts/T14-form-mask-inventory.pdf`](artifacts/T14-form-mask-inventory.pdf)
- qpdf findings: [`artifacts/T14-image-resource-extraction-qpdf.txt`](artifacts/T14-image-resource-extraction-qpdf.txt)
- qpdf completed `--check` for both T14 products with exit code `0`.

This syntax chain does not establish PDF standards conformance. The mandatory standards, semantic, and visual chains remain absent.
