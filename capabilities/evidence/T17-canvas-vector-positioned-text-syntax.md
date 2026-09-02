# T17 qpdf syntax evidence

Capability: `composition.canvas.draw-positioned-text`

Acceptance Profile: `T17-canvas-vector-positioned-text`

Profile record: `capabilities/evidence/T17-canvas-vector-positioned-text.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `syntax`

Result: `pass`

Producer kind: `external-tool`

Producer: `qpdf`

Producer version: `12.4.0`

Tool distribution SHA-256: `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`

Input ID-neutral SHA-256: `d4fc631672475f8d4f6f18e30065f1b15648b7b307c5ec177e9699085f29a5b4`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Final determination: `pass`

## Findings and artifact

- Product: [`artifacts/T17-canvas-vector-positioned-text.pdf`](artifacts/T17-canvas-vector-positioned-text.pdf)
- qpdf findings: [`artifacts/T17-canvas-vector-positioned-text-qpdf.txt`](artifacts/T17-canvas-vector-positioned-text-qpdf.txt)
- qpdf completed `--check` for the T17 product with exit code `0`.

This syntax chain does not establish PDF standards conformance. The standards and visual chains remain absent.
