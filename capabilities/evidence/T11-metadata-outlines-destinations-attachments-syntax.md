# T11 qpdf syntax evidence

Capability: `document.metadata.outlines-destinations-attachments`

Acceptance Profile: `T11-metadata-outlines-destinations-attachments`

Profile record: `capabilities/evidence/T11-metadata-outlines-destinations-attachments.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `syntax`

Result: `pass`

Producer kind: `external-tool`

Producer: `qpdf`

Producer version: `12.4.0`

Tool distribution SHA-256: `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`

Input set SHA-256: `4465760eda668c84bf0507ab035588329fb9b9784b74afb377220362f2c808db`

Final determination: `pass`

## Findings and artifacts

- Front product: [`artifacts/T11-metadata-front.pdf`](artifacts/T11-metadata-front.pdf)
- Back product: [`artifacts/T11-metadata-back.pdf`](artifacts/T11-metadata-back.pdf)
- qpdf findings: [`artifacts/T11-metadata-outlines-destinations-attachments-qpdf.txt`](artifacts/T11-metadata-outlines-destinations-attachments-qpdf.txt)
- qpdf completed `--check` for both T11 products with exit code `0`.

This syntax chain does not establish PDF standards conformance. The mandatory standards, semantic, and visual chains remain absent.
