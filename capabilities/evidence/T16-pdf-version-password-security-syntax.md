# T16 qpdf syntax evidence

Capability: `document.version-password-security`

Acceptance Profile: `T16-pdf-version-password-security`

Profile record: `capabilities/evidence/T16-pdf-version-password-security.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `syntax`

Result: `pass`

Producer kind: `external-tool`

Producer: `qpdf`

Producer version: `12.4.0`

Tool distribution SHA-256: `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`

Security observation set SHA-256: `57cd8ecf39b982c7c10adf0c2cf462471917eb5781c93351abfa928ed76b8d5a`

Input hash policy: `SHA-256 of the project-observed non-secret version, Standard-handler profile, scope, permission word, and public-reopen page count; randomized credential entries, file identifiers, and ciphertext are excluded`

Final determination: `pass`

## Findings and artifacts

- Front product: [`artifacts/T16-password-security-pdf17.pdf`](artifacts/T16-password-security-pdf17.pdf)
- Back product: [`artifacts/T16-password-security-pdf20.pdf`](artifacts/T16-password-security-pdf20.pdf)
- qpdf findings: [`artifacts/T16-pdf-version-password-security-qpdf.txt`](artifacts/T16-pdf-version-password-security-qpdf.txt)
- qpdf completed `--check` for both T16 products with exit code `0`.

This syntax chain does not establish PDF standards conformance. The mandatory standards, semantic, and visual chains remain absent.
