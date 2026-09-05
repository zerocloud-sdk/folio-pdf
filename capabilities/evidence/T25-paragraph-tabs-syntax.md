# T25 qpdf syntax evidence

Capability: `composition.layout.paragraph-pagination`

Acceptance Profile: `T25-paragraph-tabs`

Profile record: `capabilities/evidence/T25-paragraph-tabs.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `syntax`

Result: `pass`

Producer kind: `external-tool`

Producer: `qpdf`

Producer version: `12.4.0`

Tool distribution SHA-256: `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`

Input ID-neutral SHA-256: `cf23b39fe636355445b96d658abcd3cde22f899e695decb0e26b56a0f392fdc7`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Final determination: `pass`

## Findings and artifact

- Product: [`artifacts/T25-paragraph-tabs.pdf`](artifacts/T25-paragraph-tabs.pdf)
- qpdf findings: [`artifacts/T25-paragraph-tabs-qpdf.txt`](artifacts/T25-paragraph-tabs-qpdf.txt)
- qpdf completed `--check` for the T25 product with exit code `0`.

This syntax chain does not establish PDF standards conformance.
