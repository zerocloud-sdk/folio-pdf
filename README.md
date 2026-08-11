# Open PDF by ZeroCloud

> **Independent project:** Open PDF by ZeroCloud is not affiliated with,
> endorsed by, or sponsored by LibrePDF OpenPDF or Apryse/iText.

Open PDF is an Apache-2.0 Java component suite under the `net.zerocloud`
namespace. Its Native Interface is being built as a clean-room implementation
for Java 8 and is tested on JDK 8, 11, 17, and 21. The project is currently
experimental and has not published a Foundation Release.

T03 provides the complete trusted in-process Document Workflow transaction:
uniquely named Sources, an explicit primary Source, ordered named Path and
stream Targets, explicit Save Mode, cancellation, deadlines, sanitized
progress, stable failures, immutable execution outcomes, and one Publication
Receipt per Target. An immutable Workflow Environment owns deadline time.
PDFBox is an internal implementation dependency and is never part of the
public interface.

## Build

No system Maven installation is required. The wrapper uses Maven 3.9.16:

```text
./mvnw -B -ntp verify
```

Run the repository-owned container matrix with Podman:

```text
./scripts/verify-jdk-matrix.sh
```

Both commands compile shipped code for Java release 8. The local matrix runs
the same Maven verification contract on JDK 8, 11, 17, and 21.

Validate or regenerate the machine-readable compatibility inventories and
their cross-linked human-readable views with:

```text
./scripts/inventory validate
./scripts/inventory generate
```

The YAML authorities, state and evidence rules, exact output paths, and drift
contract are documented in
[capabilities/README.md](capabilities/README.md).

## Maven coordinates

All first-party artifacts use the `0.1.0-SNAPSHOT` Release Train. Consumers
select versions through `net.zerocloud:pdf-bom` and use the Document Engine as
`net.zerocloud:pdf-document`.

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>net.zerocloud</groupId>
      <artifactId>pdf-bom</artifactId>
      <version>0.1.0-SNAPSHOT</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>net.zerocloud</groupId>
    <artifactId>pdf-document</artifactId>
  </dependency>
</dependencies>
```

The snapshot is not published to Maven Central yet. Build it locally before
using these coordinates from another project.

## Document Workflow

```java
import java.nio.file.Path;
import java.nio.file.Paths;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.query.PageCount;

Path output = Paths.get("blank.pdf");
DocumentWorkflow workflow = new DocumentWorkflow();

WorkflowRequest create = WorkflowRequest.builder()
        .target("primary-output", PublicationTarget.path(output))
        .saveMode(SaveMode.REWRITE)
        .build();

workflow.execute(create, session -> {
    session.execute(AddBlankPage.INSTANCE);
    return null;
});

WorkflowRequest inspect = WorkflowRequest.builder()
        .source("published", DocumentSource.path(output))
        .primarySource("published")
        .saveMode(SaveMode.REWRITE)
        .build();

WorkflowOutcome<Integer> inspected = workflow.execute(
        inspect,
        session -> session.query(PageCount.INSTANCE));

if (inspected.getResult().intValue() != 1) {
    throw new IllegalStateException("Expected one page");
}
```

The Native Interface uses only `net.zerocloud.pdf` and JDK types. Sources may
be Paths, caller-owned streams, caller-owned channels, or bounded bytes.
Targets may be Paths or caller-owned streams. Caller-owned resources are never
closed.

`REWRITE` is the supported publication mode. `INCREMENTAL` is represented
but returns the stable `SAVE_MODE_UNSUPPORTED` failure until T15. Each
successful or partially attempted publication reports Targets in declaration
order as `COMMITTED`, `FAILED`, or `NOT_ATTEMPTED`; there is no
cross-Target atomicity, and a failed stream may contain partial output.
Operational failures use checked `DocumentFailure` values with stable codes,
safe diagnostics, and available per-Target receipts.

T03 does not yet detect or protect signed documents. Do not submit a signed
document to a mutating `REWRITE` workflow; signed-document read-only policy,
DocMDP decisions, and signature-safe incremental publication remain T15.

Successful `WorkflowOutcome` values identify the capability, the in-process
execution profile, the selected Save Mode, safe diagnostics, and every Target
receipt. A deterministic deadline Clock is configured through
`WorkflowEnvironment.withClock(clock)` and supplied when constructing a workflow;
`DocumentWorkflow` does not accept a raw Clock.

Callers moving from the T01 request factories should follow the
[0.x T03 migration note](docs/migrations/0.x-t03-document-workflow.md): every
factory call now receives an explicit `SaveMode`.

## Project information

- Repository: <https://github.com/zerocloud-sdk/open-pdf>
- Issue tracker: <https://github.com/zerocloud-sdk/open-pdf/issues>
- Temporary public and security contact: <mabaiqiu@gmail.com>
- License: [Apache License 2.0](LICENSE)
- Chinese usage guide: [docs/zh-CN/getting-started.md](docs/zh-CN/getting-started.md)
- Contribution and clean-room rules: [CONTRIBUTING.md](CONTRIBUTING.md)
- Dependency licenses: [DEPENDENCIES.md](DEPENDENCIES.md)

The authoritative specifications, Javadoc, ADRs, and capability records are in
English. Translations are usage guidance and defer to those English contracts.
