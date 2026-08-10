# Dependency and license information

T01 has one direct runtime dependency: Apache PDFBox 3.0.8. Maven resolves the
following required runtime artifacts. All are permissively licensed and remain
implementation details of `pdf-document`.

| Maven coordinate | Relationship | License |
| --- | --- | --- |
| `org.apache.pdfbox:pdfbox:3.0.8` | direct | Apache License 2.0 |
| `org.apache.pdfbox:pdfbox-io:3.0.8` | transitive | Apache License 2.0 |
| `org.apache.pdfbox:fontbox:3.0.8` | transitive | Apache License 2.0 |
| `commons-logging:commons-logging:1.4.0` | transitive | Apache License 2.0 |

PDFBox declares Bouncy Castle support as optional; T01 does not select or use
those artifacts.

The verification and repository build-tool dependency graph does not enter the
published runtime:

| Maven coordinate | Relationship | License |
| --- | --- | --- |
| `junit:junit:4.13.2` | direct test | Eclipse Public License 1.0 |
| `org.hamcrest:hamcrest-core:1.3` | transitive test | BSD 3-Clause License |
| `org.yaml:snakeyaml:2.2` | direct repository build tool | Apache License 2.0 |

SnakeYAML parses the two checked-in YAML authorities through its safe
constructor. The internal `pdf-inventory-tool` is skipped for Maven install and
deploy and is not selected by the BOM.

T02 adds one version-pinned build plugin:

| Maven coordinate | Role | License |
| --- | --- | --- |
| `org.codehaus.mojo:exec-maven-plugin:3.6.3` | invoke the repository-only inventory command during validation and generation | Apache License 2.0 |

Review the resolved graph with:

```text
./mvnw -B -ntp -pl pdf-document,build-tools/inventory dependency:tree
```

Formal releases additionally require generated SBOM, license, vulnerability,
and reproducibility evidence as described in [RELEASING.md](RELEASING.md).
