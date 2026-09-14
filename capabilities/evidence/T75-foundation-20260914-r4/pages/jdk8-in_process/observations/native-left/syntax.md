# T72 qpdf syntax evidence

Capability: `document.page.manipulate-merge-split`

Acceptance Profile: `T10-page-manipulation-merge-split`

Profile record: `capabilities/evidence/T10-page-manipulation-merge-split.md`

Release train: `0.1.0`

Chain: `syntax`

Result: `pass`

Producer kind: `external-tool`

Producer: `qpdf`

Producer version: `12.4.0`

Tool distribution SHA-256: `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`

Input exact SHA-256: `f3369c51504304ec52795cb4141b0b66a81cead24893a6d9e5eb3dd7c6fa689a`

Input hash policy: `SHA-256 of the exact unmodified PDF bytes`

Final determination: `pass`

## Findings and artifact

- Product: [`pages.pdf`](pages.pdf)
- qpdf findings: [`syntax.txt`](syntax.txt)
- qpdf completed `--check` for the T72 product with exit code `0`.

This syntax chain does not establish PDF standards conformance.
