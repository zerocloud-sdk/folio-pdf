# T74 negative control qpdf syntax evidence

Capability: `document.annotations-actions.manage`

Acceptance Profile: `T12-annotations-document-actions`

Profile record: `capabilities/evidence/T12-annotations-document-actions.md`

Release train: `0.1.0`

Chain: `syntax`

Result: `fail`

Producer kind: `external-tool`

Producer: `qpdf`

Producer version: `12.4.0`

Tool distribution SHA-256: `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`

Input exact SHA-256: `50425b27c7887304ff3a494dfeeec58d060f6133f775471fcee78075f569cbcd`

Input hash policy: `SHA-256 of the exact unmodified PDF bytes`

Final determination: `fail`

## Findings and artifact

- Product: [`invalid.pdf`](invalid.pdf)
- qpdf findings: [`syntax.txt`](syntax.txt)
- qpdf reported warnings or errors for a T74 negative control product.

This syntax chain does not establish PDF standards conformance.
