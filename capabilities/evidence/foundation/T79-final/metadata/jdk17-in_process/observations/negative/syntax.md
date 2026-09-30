# T73 negative control qpdf syntax evidence

Capability: `document.metadata.outlines-destinations-attachments`

Acceptance Profile: `T11-metadata-outlines-destinations-attachments`

Profile record: `capabilities/evidence/T11-metadata-outlines-destinations-attachments.md`

Release train: `0.1.0`

Chain: `syntax`

Result: `fail`

Producer kind: `external-tool`

Producer: `qpdf`

Producer version: `12.4.0`

Tool distribution SHA-256: `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`

Input exact SHA-256: `77e86253cccbe5ceb0f1a411a494e7e448bb5fdd81f3a8f5379a128a40d9903f`

Input hash policy: `SHA-256 of the exact unmodified PDF bytes`

Final determination: `fail`

## Findings and artifact

- Product: [`invalid.pdf`](invalid.pdf)
- qpdf findings: [`syntax.txt`](syntax.txt)
- qpdf reported warnings or errors for a T73 negative control product.

This syntax chain does not establish PDF standards conformance.
