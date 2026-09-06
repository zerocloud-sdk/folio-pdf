# T29 installation evidence

Capability: `composition.shaping.harf-buzz`

Acceptance Profile: `T29-shaping`

Profile record: `capabilities/evidence/T29-shaping.md`

Release train: `0.1.0-SNAPSHOT`

Chain: `installation`

Result: `pass`

Producer kind: `project-test`

Producer: `folio-pdf-t29-native-installation-observer`

Producer version: `0.1.0-SNAPSHOT`

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

Native observation source SHA-256: `51130d9e80f258de2ce23f0ca2c26f30ad616c4fe21c3674ef98ac3a2e53217a`


This separate invocation checks installed artifacts and observes the helper's live startup mappings in the inherited loader environment. It does not inspect every Workflow subprocess. Unimplemented platform observation remains INDETERMINATE.

[Installation and live engine observations](artifacts/T29-shaping-installation.txt)

Final determination: `pass`
