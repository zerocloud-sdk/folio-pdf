# T71 qpdf syntax evidence

Capability: `document.value.inspect-patch`

Acceptance Profile: `T09-document-value-inspection-patch`

Profile record: `capabilities/evidence/T09-document-value-inspection-patch.md`

Release train: `0.1.0`

Chain: `syntax`

Result: `pass`

Producer kind: `external-tool`

Producer: `qpdf`

Producer version: `12.4.0`

Tool distribution SHA-256: `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`

Input exact SHA-256: `4a4c69a936e7cf8b3b0b340ce7dcefec8d3372a84fa29fcd902ae20dfb9d9b46`

Input hash policy: `SHA-256 of the exact unmodified PDF bytes`

Final determination: `pass`

## Findings and artifact

- Product: [`values.pdf`](values.pdf)
- qpdf findings: [`syntax.txt`](syntax.txt)
- qpdf completed `--check` for the T71 product with exit code `0`.

This syntax chain does not establish PDF standards conformance.
