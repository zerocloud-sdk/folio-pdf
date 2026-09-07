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

Source declaration SHA-256: `363252a35e3942965c7300eb810243434d999ea8242eea4eb3680301197f625e`

Reference font SHA-256: `b85c38ecea8a7cfb39c24e395a4007474fa5a4fc864f6ee33309eb4948d232d5`

ZXing core version: `3.5.3`

ZXing JAR SHA-256: `8d8064c1636fdaef7189dd9055c7d59950a8940a12f2293956446ec3c109fd82`

OkapiBarcode version: `0.5.6`

OkapiBarcode JAR SHA-256: `fc07c5e28f200a53b980e36719901e095e06d1432d7ae959a22456f838765f2a`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Reference PDF SHA-256: `8c541fe5df1b7052fc0cbfb4df7585570d05d1f79ae46166401900ecafb903a7`

Reference ID-neutral SHA-256: `fde75914feae8804deca09956698305cbf221389b97a8065cb86013ba82d287d`

[Source declarations](artifacts/T30-one-dimensional-barcodes-sources.sha256)

Decoded pages: `232`

Verified labels: `230`

Every observed PDF path is compared to independent modules and point geometry. Actual scanlines are decoded, including checks, raw symbols and supplements. Labels are compared character by character to independent source-font metrics.

Execution profile: `IN_PROCESS`

Actual PDF SHA-256: `932d39e2162b5d76c490f229d759f42451c2c22310d94f3ed8a8bcc010595f75`

Input ID-neutral SHA-256: `371f5b24f655e7c93ef1b1df8d3a9cdeb975fb68c841459efb075d817ba94507`

[Actual PDF](artifacts/T30-one-dimensional-barcodes-in-process.pdf)

[Reopened path decoding, geometry and labels](artifacts/T30-one-dimensional-barcodes-in-process-semantic.txt)

Execution profile: `HARDENED_WORKER`

Actual PDF SHA-256: `c777ec83dec35415c46cf1d33cb14618628999c1293e07d6ae6b98f8653c0cfa`

Input ID-neutral SHA-256: `371f5b24f655e7c93ef1b1df8d3a9cdeb975fb68c841459efb075d817ba94507`

[Actual PDF](artifacts/T30-one-dimensional-barcodes-hardened-worker.pdf)

[Reopened path decoding, geometry and labels](artifacts/T30-one-dimensional-barcodes-hardened-worker-semantic.txt)

