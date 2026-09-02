# T10 qpdf syntax evidence

Capability: `document.page.manipulate-merge-split`

Acceptance Profile: `T10-page-manipulation-merge-split`

Profile record: `capabilities/evidence/T10-page-manipulation-merge-split.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `syntax`

Result: `pass`

Producer kind: `external-tool`

Producer: `qpdf`

Producer version: `12.4.0`

Tool distribution SHA-256: `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`

Input set SHA-256: `dcaab71a7ab3184af61ee911e31ce5ccc68714da97347d7eae74980cce0ada0e`

Final determination: `pass`

## Findings and artifacts

- Front product: [`artifacts/T10-page-manipulation-front.pdf`](artifacts/T10-page-manipulation-front.pdf)
- Back product: [`artifacts/T10-page-manipulation-back.pdf`](artifacts/T10-page-manipulation-back.pdf)
- qpdf findings: [`artifacts/T10-page-manipulation-merge-split-qpdf.txt`](artifacts/T10-page-manipulation-merge-split-qpdf.txt)
- qpdf completed `--check` for both T10 products with exit code `0`.

This syntax chain does not establish PDF standards conformance. The mandatory standards, semantic, and visual chains remain absent.
