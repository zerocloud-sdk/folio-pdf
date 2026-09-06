# T29 visual evidence

Capability: `composition.shaping.harf-buzz`

Acceptance Profile: `T29-shaping`

Profile record: `capabilities/evidence/T29-shaping.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `visual`

Result: `pass`

Producer kind: `external-tool`

Producer: `pdfium-cli`

Producer version: `v0.11.2-pdfium-chromium-7881`

OS name: `Linux`

OS version: `6.8.0-136-generic`

Architecture: `amd64`

Java runtime version: `17.0.9+9-jvmci-23.0-b22`

Worker support envelope: `Linux; executable /usr/bin/prlimit; JDK 8,11,17,21 with legacy Security Manager`

Worker applicability: `required on this Linux host`

Expected HarfBuzz version: `10.2.0`

Native helper executable: `/home/ubuntu/IdeaProjects/open-pdf/.build-cache/harfbuzz/10.2.0-final/bin/folio-harfbuzz`

Native helper executable SHA-256: `169389e19e28bc96e3e878a9468671c31ccc6d5e7abbc3d38c4b8470526cdc65`

Native numeric tolerance in font units: `0`

Reopened geometry tolerance in points: `0.0001`

T29-corpus.json SHA-256: `29bd0d8090a78b1b6a6e11e95f35fa673614bd7bd2b448cb8fd36a9b980d2bf2`

T29-oracle.json SHA-256: `3f96696998c0bd8f0c521fbcbd0e20eda1e1a01bd4de92f9d025bb9ee7c7ed82`

T29-corpus.properties SHA-256: `00621fa88a8a2c159de40551008de4fa66e3d5aeccc110a42b4c36490e3a5c50`

T29-glyphs.tsv SHA-256: `a89dbb43a2d96375cd0cbf8f3a2b181eef4916ac8ee7535baf31f3c5864624ff`

T29-reference-receipt.json SHA-256: `d4402f407cbdefdbb63d062a787c29b3d109bd451fbb2814738f88c57c45e8a6`

T29-shaping-reference.pdf SHA-256: `fcfb9619614d6618a81396913bcc9d0e351d23adc9a7a815e80780946d02854a`

Font manifest SHA-256: `fb8a3aa15177106dd75a808afb637d4b8f823a54e7e59ad4bb5527949aef5905`

IN_PROCESS input ID-neutral SHA-256: `a6c1522148d25ec716119791bdc682b9c3b7682b0b9ebfcbbc69a8823964318c`

IN_PROCESS peak owned memory in bytes: `44280085`

IN_PROCESS outcome: `pass`

HARDENED_WORKER input ID-neutral SHA-256: `a6c1522148d25ec716119791bdc682b9c3b7682b0b9ebfcbbc69a8823964318c`

HARDENED_WORKER peak owned memory in bytes: `45340169`

HARDENED_WORKER outcome: `pass`

Input hash policy: `SHA-256 of the exact PDF bytes after replacing only the two hexadecimal trailer /ID values with ASCII zeroes`

Mode coverage: `IN_PROCESS,HARDENED_WORKER`


All eight pages in each mode require zero-fuzz AE 0 against the independent reference. The fixed secondary-renderer disagreement limit is 3000 changed pixels.

- [IN_PROCESS arabic 1](T29-shaping-arabic-page-1-visual.md)
- [IN_PROCESS arabic 2](T29-shaping-arabic-page-2-visual.md)
- [IN_PROCESS hebrew 1](T29-shaping-hebrew-page-1-visual.md)
- [IN_PROCESS hebrew 2](T29-shaping-hebrew-page-2-visual.md)
- [IN_PROCESS devanagari 1](T29-shaping-devanagari-page-1-visual.md)
- [IN_PROCESS devanagari 2](T29-shaping-devanagari-page-2-visual.md)
- [IN_PROCESS thai 1](T29-shaping-thai-page-1-visual.md)
- [IN_PROCESS thai 2](T29-shaping-thai-page-2-visual.md)
- [HARDENED_WORKER arabic 1](T29-shaping-arabic-page-1-worker-visual.md)
- [HARDENED_WORKER arabic 2](T29-shaping-arabic-page-2-worker-visual.md)
- [HARDENED_WORKER hebrew 1](T29-shaping-hebrew-page-1-worker-visual.md)
- [HARDENED_WORKER hebrew 2](T29-shaping-hebrew-page-2-worker-visual.md)
- [HARDENED_WORKER devanagari 1](T29-shaping-devanagari-page-1-worker-visual.md)
- [HARDENED_WORKER devanagari 2](T29-shaping-devanagari-page-2-worker-visual.md)
- [HARDENED_WORKER thai 1](T29-shaping-thai-page-1-worker-visual.md)
- [HARDENED_WORKER thai 2](T29-shaping-thai-page-2-worker-visual.md)

Final determination: `pass`
