# T19 qpdf syntax evidence

Capability: `composition.fonts.load-embed-subset-fallback`

Acceptance Profile: `T19-font-loading-embedding-subsetting`

Profile record: `capabilities/evidence/T19-font-loading-embedding-subsetting.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `syntax`

Result: `pass`

Producer kind: `external-tool`

Producer: `qpdf`

Producer version: `12.4.0`

Tool distribution SHA-256: `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`

Input ID-neutral SHA-256: `c476b39cd5106f6270d815aebdcbb38d450ae18d89601da6aa6a02f1bd5c46a7`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Final determination: `pass`

## Findings and artifact

- Product: [`artifacts/T19-font-loading-embedding-subsetting.pdf`](artifacts/T19-font-loading-embedding-subsetting.pdf)
- qpdf findings: [`artifacts/T19-font-loading-embedding-subsetting-qpdf.txt`](artifacts/T19-font-loading-embedding-subsetting-qpdf.txt)
- qpdf completed `--check` for the T19 product with exit code `0`.

This syntax chain does not establish PDF standards conformance.
