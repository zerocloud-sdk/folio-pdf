# T31 independent semantic evidence

Capability: `composition.barcodes.two-dimensional`

Acceptance Profile: `T31-two-dimensional-barcodes`

Profile record: `capabilities/evidence/T31-two-dimensional-barcodes.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `semantic`

Result: `pass`

Producer kind: `project-test`

Producer: `folio-pdf-t31-semantic-assertions`

Producer version: `0.1.0-SNAPSHOT`

Source declaration SHA-256: `481ef339e87bdec30cf8103301810751c6ce7caff162e360b2fa52bb91fb189b`

ZXing core version: `3.5.3`

ZXing JAR SHA-256: `8d8064c1636fdaef7189dd9055c7d59950a8940a12f2293956446ec3c109fd82`

OkapiBarcode version: `0.5.6`

OkapiBarcode JAR SHA-256: `fc07c5e28f200a53b980e36719901e095e06d1432d7ae959a22456f838765f2a`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

[Source and input declarations](artifacts/T31-two-dimensional-barcodes-sources.sha256)

Decoded pages: `448`

Rejected PDF negative controls: `19`

Every published Form is decoded with independent ZXing algorithms and original structural assertions. Exact function modules, ECC without correction, compaction/control metadata, quiet margins, boxes, transforms, color and reuse are checked through public PDF Values.

Execution profile: `IN_PROCESS`

Actual PDF SHA-256: `77731e35a838e61888f8303856cdcbd54faa18d02b9d1653e915bffc0a45bc1e`

Input ID-neutral SHA-256: `e7970cedeaea670f2ab2404b835857cd1ec1b58a7728257d4d5b8c368eb1d398`

[Published PDF](artifacts/T31-two-dimensional-barcodes-in-process.pdf)

[Every reopened Form, module, ECC, payload, control, quiet zone and placement](artifacts/T31-two-dimensional-barcodes-in-process-semantic.txt)

Execution profile: `HARDENED_WORKER`

Actual PDF SHA-256: `6396f65d878322a81a1c8fa8c9da8bead7bc9a3b2c38a70510c5d9a176375137`

Input ID-neutral SHA-256: `e7970cedeaea670f2ab2404b835857cd1ec1b58a7728257d4d5b8c368eb1d398`

[Published PDF](artifacts/T31-two-dimensional-barcodes-hardened-worker.pdf)

[Every reopened Form, module, ECC, payload, control, quiet zone and placement](artifacts/T31-two-dimensional-barcodes-hardened-worker-semantic.txt)

- qr-payload: [corrupted PDF](artifacts/T31-negative-qr-payload.pdf), [actual rejection](artifacts/T31-negative-qr-payload-semantic.txt)
- qr-data: [corrupted PDF](artifacts/T31-negative-qr-data.pdf), [actual rejection](artifacts/T31-negative-qr-data-semantic.txt)
- qr-ecc: [corrupted PDF](artifacts/T31-negative-qr-ecc.pdf), [actual rejection](artifacts/T31-negative-qr-ecc-semantic.txt)
- qr-finder: [corrupted PDF](artifacts/T31-negative-qr-finder.pdf), [actual rejection](artifacts/T31-negative-qr-finder-semantic.txt)
- qr-timing: [corrupted PDF](artifacts/T31-negative-qr-timing.pdf), [actual rejection](artifacts/T31-negative-qr-timing-semantic.txt)
- qr-format: [corrupted PDF](artifacts/T31-negative-qr-format.pdf), [actual rejection](artifacts/T31-negative-qr-format-semantic.txt)
- qr-quiet: [corrupted PDF](artifacts/T31-negative-qr-quiet.pdf), [actual rejection](artifacts/T31-negative-qr-quiet-semantic.txt)
- qr-move: [corrupted PDF](artifacts/T31-negative-qr-move.pdf), [actual rejection](artifacts/T31-negative-qr-move-semantic.txt)
- qr-scale: [corrupted PDF](artifacts/T31-negative-qr-scale.pdf), [actual rejection](artifacts/T31-negative-qr-scale-semantic.txt)
- qr-rotation: [corrupted PDF](artifacts/T31-negative-qr-rotation.pdf), [actual rejection](artifacts/T31-negative-qr-rotation-semantic.txt)
- qr-box: [corrupted PDF](artifacts/T31-negative-qr-box.pdf), [actual rejection](artifacts/T31-negative-qr-box-semantic.txt)
- dm-finder: [corrupted PDF](artifacts/T31-negative-dm-finder.pdf), [actual rejection](artifacts/T31-negative-dm-finder-semantic.txt)
- dm-timing: [corrupted PDF](artifacts/T31-negative-dm-timing.pdf), [actual rejection](artifacts/T31-negative-dm-timing-semantic.txt)
- dm-ecc: [corrupted PDF](artifacts/T31-negative-dm-ecc.pdf), [actual rejection](artifacts/T31-negative-dm-ecc-semantic.txt)
- dm-padding: [corrupted PDF](artifacts/T31-negative-dm-padding.pdf), [actual rejection](artifacts/T31-negative-dm-padding-semantic.txt)
- pdf-start: [corrupted PDF](artifacts/T31-negative-pdf-start.pdf), [actual rejection](artifacts/T31-negative-pdf-start-semantic.txt)
- pdf-cluster: [corrupted PDF](artifacts/T31-negative-pdf-cluster.pdf), [actual rejection](artifacts/T31-negative-pdf-cluster-semantic.txt)
- pdf-ecc: [corrupted PDF](artifacts/T31-negative-pdf-ecc.pdf), [actual rejection](artifacts/T31-negative-pdf-ecc-semantic.txt)
- pdf-indicator: [corrupted PDF](artifacts/T31-negative-pdf-indicator.pdf), [actual rejection](artifacts/T31-negative-pdf-indicator-semantic.txt)
