# T74 qpdf syntax evidence

Capability: `document.annotations-actions.manage`

Acceptance Profile: `T12-annotations-document-actions`

Profile record: `capabilities/evidence/T12-annotations-document-actions.md`

Release train: `0.1.0`

Chain: `syntax`

Result: `pass`

Producer kind: `external-tool`

Producer: `qpdf`

Producer version: `12.4.0`

Tool distribution SHA-256: `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`

Input exact SHA-256: `3bf2f82b1e583c26c8f6a1a95c2d3d5b0ffe9ec736acfb99c35770436c5d803b`

Input hash policy: `SHA-256 of the exact unmodified PDF bytes`

Final determination: `pass`

## Findings and artifact

- Product: [`annotations.pdf`](annotations.pdf)
- qpdf findings: [`syntax.txt`](syntax.txt)
- qpdf completed `--check` for the T74 product with exit code `0`.

This syntax chain does not establish PDF standards conformance.
