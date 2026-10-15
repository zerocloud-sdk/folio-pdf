# T70 qpdf syntax evidence

Capability: `document.blank.create-publish-reopen`

Acceptance Profile: `T03-document-workflow-transaction`

Profile record: `capabilities/evidence/T03-document-workflow-transaction.md`

Release train: `0.1.0`

Chain: `syntax`

Result: `pass`

Producer kind: `external-tool`

Producer: `qpdf`

Producer version: `12.4.0`

Tool distribution SHA-256: `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`

Input ID-neutral SHA-256: `9fa3077b3f3ae8fae8789556bb1d5e77c934f0945bec3c2b2078b683b2bce219`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Final determination: `pass`

## Findings and artifacts

- Input PDF: [`artifacts/blank.pdf`](artifacts/blank.pdf)
- qpdf findings: [`artifacts/syntax.txt`](artifacts/syntax.txt)
- qpdf completed `--check` with exit code `0`.
