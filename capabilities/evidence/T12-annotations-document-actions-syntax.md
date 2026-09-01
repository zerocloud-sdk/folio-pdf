# T12 qpdf syntax evidence

Capability: `document.annotations-actions.manage`

Acceptance Profile: `T12-annotations-document-actions`

Profile record: `capabilities/evidence/T12-annotations-document-actions.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `syntax`

Result: `pass`

Producer kind: `external-tool`

Producer: `qpdf`

Producer version: `12.4.0`

Tool distribution SHA-256: `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`

Input set SHA-256: `c17d5934f5e217484ea8144fb7b441a392253cdcd1e6773bc76dab750fa0082d`

Final determination: `pass`

## Findings and artifacts

- Front product: [`artifacts/T12-annotations-actions-front.pdf`](artifacts/T12-annotations-actions-front.pdf)
- Back product: [`artifacts/T12-annotations-actions-back.pdf`](artifacts/T12-annotations-actions-back.pdf)
- qpdf findings: [`artifacts/T12-annotations-document-actions-qpdf.txt`](artifacts/T12-annotations-document-actions-qpdf.txt)
- qpdf completed `--check` for both T12 products with exit code `0`.

This syntax chain does not establish PDF standards conformance. The mandatory standards, semantic, and visual chains remain absent.
