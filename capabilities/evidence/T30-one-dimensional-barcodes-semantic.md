# T30 independent semantic evidence

Capability: `composition.barcodes.one-dimensional`

Acceptance Profile: `T30-one-dimensional-barcodes`

Profile record: `capabilities/evidence/T30-one-dimensional-barcodes.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `semantic`

Result: `pass`

Producer kind: `project-test`

Producer: `folio-pdf-t30-semantic-assertions`

Producer version: `0.1.0-SNAPSHOT`

Source declaration SHA-256: `6420f7d7243e94ced5bb6bc2480bf65d1bed9f8c0e2831f7c7ac4cca9e329765`

Reference font SHA-256: `b85c38ecea8a7cfb39c24e395a4007474fa5a4fc864f6ee33309eb4948d232d5`

ZXing core version: `3.5.3`

ZXing JAR SHA-256: `8d8064c1636fdaef7189dd9055c7d59950a8940a12f2293956446ec3c109fd82`

OkapiBarcode version: `0.5.6`

OkapiBarcode JAR SHA-256: `fc07c5e28f200a53b980e36719901e095e06d1432d7ae959a22456f838765f2a`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Reference PDF SHA-256: `28701883c1bec2214f63ac4a5e43f1527b9d469efd4059fd6559123ea88ab76b`

Reference ID-neutral SHA-256: `abad564269a4646d3a6e8558718eee52237cb5a020f0c3d13604876069c9b29d`

[Source declarations](artifacts/T30-one-dimensional-barcodes-sources.sha256)

Decoded pages: `224`

Verified labels: `222`

Every observed PDF path is compared to independent modules and point geometry. Actual scanlines are decoded, including checks, raw symbols and supplements. Labels are compared character by character to independent source-font metrics.

Execution profile: `IN_PROCESS`

Actual PDF SHA-256: `73ea0f2c396f6795ff9d3d4515f6291f0a1f7d0714d40f0339b124d3a3973bde`

Input ID-neutral SHA-256: `fc616cdcbce5cdb9c65d6b3e7563d51910c94831f25fa18a1e57f2ba0290b061`

[Actual PDF](artifacts/T30-one-dimensional-barcodes-in-process.pdf)

[Reopened path decoding, geometry and labels](artifacts/T30-one-dimensional-barcodes-in-process-semantic.txt)

Execution profile: `HARDENED_WORKER`

Actual PDF SHA-256: `a5335d781dcf0e4fbe88261cea7bb6560cd2724ffdf56ceaa8456849f2bd994a`

Input ID-neutral SHA-256: `fc616cdcbce5cdb9c65d6b3e7563d51910c94831f25fa18a1e57f2ba0290b061`

[Actual PDF](artifacts/T30-one-dimensional-barcodes-hardened-worker.pdf)

[Reopened path decoding, geometry and labels](artifacts/T30-one-dimensional-barcodes-hardened-worker-semantic.txt)

