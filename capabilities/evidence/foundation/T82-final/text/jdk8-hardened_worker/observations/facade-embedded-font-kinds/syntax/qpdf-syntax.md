# T75 qpdf syntax evidence

Capability: `document.text-structure.extract`

Acceptance Profile: `T13-text-logical-structure`

Profile record: `capabilities/evidence/T13-text-logical-structure.md`

Release train: `0.1.0`

Chain: `syntax`

Result: `pass`

Producer kind: `external-tool`

Producer: `qpdf`

Producer version: `12.4.0`

Tool distribution SHA-256: `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`

Input exact SHA-256: `42739ab6a69bad744268186e2c51969df0378b78e2a8be228b88cef3025438a2`

Input hash policy: `SHA-256 of the exact unmodified PDF bytes`

Final determination: `pass`

## Findings and artifact

- Product: [`extraction.pdf`](extraction.pdf)
- qpdf findings: [`qpdf-syntax.txt`](qpdf-syntax.txt)
- qpdf completed `--check` for the T75 product with exit code `0`.

This syntax chain does not establish PDF standards conformance.
