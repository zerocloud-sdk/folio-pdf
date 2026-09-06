# T29 qpdf syntax evidence

Capability: `composition.shaping.harf-buzz`

Acceptance Profile: `T29-shaping`

Profile record: `capabilities/evidence/T29-shaping.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `syntax`

Result: `pass`

Producer kind: `external-tool`

Producer: `qpdf`

Producer version: `12.4.0`

Tool distribution SHA-256: `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`

Input ID-neutral SHA-256: `a6c1522148d25ec716119791bdc682b9c3b7682b0b9ebfcbbc69a8823964318c`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Final determination: `pass`

## Findings and artifact

- Product: [`artifacts/T29-shaping.pdf`](artifacts/T29-shaping.pdf)
- qpdf findings: [`artifacts/T29-shaping-qpdf.txt`](artifacts/T29-shaping-qpdf.txt)
- qpdf completed `--check` for the T29 product with exit code `0`.

This syntax chain does not establish PDF standards conformance.
