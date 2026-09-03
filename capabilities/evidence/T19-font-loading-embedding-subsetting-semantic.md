# T19 project semantic evidence

Capability: `composition.fonts.load-embed-subset-fallback`

Acceptance Profile: `T19-font-loading-embedding-subsetting`

Profile record: `capabilities/evidence/T19-font-loading-embedding-subsetting.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `semantic`

Result: `pass`

Producer kind: `project-test`

Producer: `folio-pdf-t19-semantic-assertions`

Producer version: `0.1.0-SNAPSHOT`

Input ID-neutral SHA-256: `c476b39cd5106f6270d815aebdcbb38d450ae18d89601da6aa6a02f1bd5c46a7`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Final determination: `pass`

## Finding and artifact

- Product: [`artifacts/T19-font-loading-embedding-subsetting.pdf`](artifacts/T19-font-loading-embedding-subsetting.pdf)
- Semantic findings: [`artifacts/T19-font-loading-embedding-subsetting-semantic.txt`](artifacts/T19-font-loading-embedding-subsetting-semantic.txt)
- The public workflow reported T19 and reopened the exact embedded subsets, explicit Unicode mappings, source metrics, ordered fallback, and resource reuse.

Expected values are project-owned font declarations observed only through public APIs.
