# T06 project semantic evidence

Capability: `document.blank.create-publish-reopen`

Acceptance Profile: `T03-document-workflow-transaction`

Profile record: `capabilities/evidence/T03-document-workflow-transaction.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `semantic`

Result: `pass`

Producer kind: `project-test`

Producer: `folio-pdf-semantic-assertions`

Producer version: `0.1.0-SNAPSHOT`

Input ID-neutral SHA-256: `9fa3077b3f3ae8fae8789556bb1d5e77c934f0945bec3c2b2078b683b2bce219`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Final determination: `pass`

## Findings and artifacts

- Input PDF: [`artifacts/T06-document-blank-output.pdf`](artifacts/T06-document-blank-output.pdf)
- Semantic findings: [`artifacts/T06-document-blank-semantic.txt`](artifacts/T06-document-blank-semantic.txt)
- The public workflow committed the artifact and reopened the observed one-page sequence `[1]`.
