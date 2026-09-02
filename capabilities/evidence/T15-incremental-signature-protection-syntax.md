# T15 qpdf syntax evidence

Capability: `document.incremental-signature.protect`

Acceptance Profile: `T15-incremental-signature-protection`

Profile record: `capabilities/evidence/T15-incremental-signature-protection.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `syntax`

Result: `pass`

Producer kind: `external-tool`

Producer: `qpdf`

Producer version: `12.4.0`

Tool distribution SHA-256: `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`

Input revision-ID-neutral set SHA-256: `aed7e84bbe8353ab581114b6f53c47754839b8df82110d193915705d35e4ee08`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing every hexadecimal two-value trailer /ID with equal-length ASCII zeroes`

Final determination: `pass`

## Findings and artifacts

- Front product: [`artifacts/T15-incremental-original.pdf`](artifacts/T15-incremental-original.pdf)
- Back product: [`artifacts/T15-incremental-output.pdf`](artifacts/T15-incremental-output.pdf)
- qpdf findings: [`artifacts/T15-incremental-signature-protection-qpdf.txt`](artifacts/T15-incremental-signature-protection-qpdf.txt)
- qpdf completed `--check` for both T15 products with exit code `0`.

This syntax chain does not establish PDF standards conformance. The mandatory standards, semantic, and visual chains remain absent.
