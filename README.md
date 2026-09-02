# Folio PDF by ZeroCloud

> **Independent project:** Folio PDF by ZeroCloud is not affiliated with,
> endorsed by, or sponsored by LibrePDF OpenPDF or Apryse/iText.

Folio PDF is an Apache-2.0 Java component suite under the `net.zerocloud`
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

T04 packages the Stable and Experimental Migration Facades. The first mapped
document-creation workflow remains experimental, so it is available only from
`pdf-migration-itext7-preview`; `pdf-migration-itext7` intentionally contains
no public mapping or unsupported stub. The two artifacts are mutually
exclusive.

T05 establishes the Capability Provider contract for in-process Java, native
linkage, local subprocess, and explicitly authorized remote engines. The
default Workflow Environment registers no Provider, remains offline, and
performs no implicit network access. A generic bounded subprocess adapter is
included, but no external engine is bundled.

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

The repository-only T06/T07 Acceptance Evidence path is separate from the
normal build and published artifacts. Supply the pinned release assets locally
and run the offline provisioners before recording evidence:

```text
./scripts/provision-qpdf /path/to/qpdf-12.4.0-bin-linux-x86_64.zip
./scripts/provision-pdfium /path/to/pdfium-webassembly-linux-amd64
./scripts/provision-imagemagick /path/to/ImageMagick-7.1.2-30-gcc-x86_64.AppImage
./scripts/acceptance capabilities/evidence
```

This records qpdf syntax, project semantic, and PDFium/ImageMagick visual
findings for the same Document Workflow-produced PDF. PDFium is the independent
renderer; ImageMagick receives fixed-size PNG rasters only. The tools remain in
ignored local caches and never enter the normal Maven build, BOM, Native
Interface, or published artifacts. See
[capabilities/README.md](capabilities/README.md) for exact versions, hashes,
licenses and bundled-component notice manifests, the documented ID-neutral
input-hash policy, profile settings, output artifacts, and conservative
determination rules.

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
select versions through `net.zerocloud:pdf-bom`. The BOM manages
`pdf-provider-contract`, `pdf-document`, `pdf-conversion`,
`pdf-migration-itext7`, and `pdf-migration-itext7-preview`.

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

Migration callers must select exactly one facade artifact. Use
`pdf-migration-itext7` for compatible mappings only, or use
`pdf-migration-itext7-preview` for the strict superset that also includes
experimental mappings:

```xml
<!-- Stable: currently contains no T04 mapping because the capability is experimental. -->
<dependency>
  <groupId>net.zerocloud</groupId>
  <artifactId>pdf-migration-itext7</artifactId>
</dependency>
```

```xml
<!-- Preview: contains the first experimental document-creation mapping. -->
<dependency>
  <groupId>net.zerocloud</groupId>
  <artifactId>pdf-migration-itext7-preview</artifactId>
</dependency>
```

Do not place both facade artifacts on one classpath. Every mapped preview
public class checks the packaged edition markers when initialized and fails
with an explicit conflict if both are present, independent of jar order.

The snapshot is not published to Maven Central yet. Build it locally before
using these coordinates from another project.

## Capability Providers

Applications register explicitly installed Provider adapters through the
immutable Workflow Environment. Registration order is deterministic; a
`ProviderPreference` either accepts the first eligible registration or names
one Provider ID. `WorkflowEnvironment.getProviderMetadata()` exposes only
immutable identity, capability, version, execution-mode, availability, limit,
license, and distribution facts—never executable Provider instances.

```java
import net.zerocloud.pdf.WorkflowEnvironment;
import net.zerocloud.pdf.provider.CapabilityProvider;

CapabilityProvider installedProvider = obtainInstalledProvider();
WorkflowEnvironment environment = WorkflowEnvironment.builder()
        .provider(installedProvider)
        .build();
```

A workflow request can make a capability-scoped preference. The resulting
`WorkflowOutcome.getProviderSelections()` reports the selected metadata. T05
selection does not itself invoke an engine; capability-specific modules invoke
the selected Provider at the real Provider seam.

Remote disclosure is absent by default and is never implied by registration
or preference. A workflow must call
`authorizeRemoteDisclosure(capabilityId)` for the same capability before a
remote Provider is eligible. Direct `ProviderRequest` execution has the same
explicit authorization gate. The deterministic remote tests use an in-memory
adapter and contact no network service.

`pdf-provider-contract` contains the project-owned metadata, request/result,
selection, execution, limit, and stable failure types. `pdf-conversion`
contains `SubprocessCapabilityProvider`, which launches a fixed argument list
without a shell, enforces input/output/time bounds, terminates stalled or
oversized processing, discards child diagnostics, and removes its private
per-run staging directory. It is not the Hardened Worker Profile and must not
be used as hard isolation for hostile multi-tenant input.

See the [Capability Provider guide](docs/capability-providers.md) for the
selection and subprocess protocol contracts and [SECURITY.md](SECURITY.md) for
the disclosure and isolation boundaries.

## Migration Facade

For a mapped surface, replace the package prefix `com.itextpdf.*` with
`net.zerocloud.pdf.itext7.*`. T04 maps only the preview blank-document flow:

```java
import java.nio.file.Path;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfReader;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;
import net.zerocloud.pdf.itext7.layout.Document;

Path output = java.nio.file.Paths.get("blank.pdf");

PdfDocument created = new PdfDocument(new PdfWriter(output.toString()));
Document layout = new Document(created);
created.addNewPage();
layout.close();

try (PdfReader reader = new PdfReader(output.toString());
        PdfDocument reopened = new PdfDocument(reader)) {
    if (reopened.getNumberOfPages() != 1) {
        throw new IllegalStateException("Expected one page");
    }
}
```

Closing a writing `PdfDocument` or its associated layout `Document` executes
the Native Interface transaction and returns only after the Path target has a
`COMMITTED` Publication Receipt. A reader failure is an `IOException` whose
safe message begins with the stable Document Failure code. Publication
failures are mapped to
`net.zerocloud.pdf.itext7.kernel.exceptions.PdfException`. The exact mapped
members and their limitations are generated from
[capabilities/facade-surface.yaml](capabilities/facade-surface.yaml).

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

`REWRITE` publishes a complete replacement. `INCREMENTAL` requires an existing
primary Source, preserves all Source bytes as an unchanged prefix, and appends
a non-empty revision for commands in the version-1 policy. Each
successful or partially attempted publication reports Targets in declaration
order as `COMMITTED`, `FAILED`, or `NOT_ATTEMPTED`; there is no
cross-Target atomicity, and a failed stream may contain partial output.
Operational failures use checked `DocumentFailure` values with stable codes,
safe diagnostics, and available per-Target receipts.

Existing signatures are protected conservatively: signed Sources remain
available to target-free queries, cannot be republished with `REWRITE`, and
reject mutation unless a sole coherent DocMDP P=3 policy permits a supported
non-Widget `UpdateAnnotations` command. Ordinary signatures and DocMDP P=1 or
P=2 permit no current mutation. See the authoritative
[incremental publication and Existing Signature guide](docs/incremental-signature-policy.md).

The Document Engine also exposes backend-neutral PDF Values and validated
Document Patches (T09), page manipulation/merge/split (T10), document
metadata/outlines/destinations/attachments (T11), and supported annotations
and inert local GoTo Actions (T12). T12 covers Text, Stamp, Highlight,
FileAttachment, standalone Widget, and Link annotations; bounded queries;
resource-free normal appearances; non-form flattening; and destination-aware
move, copy, merge, split, and removal behavior. It never executes an Action,
and unsupported Action graphs are preserved only when no semantic rewrite is
required or rejected before mutation. See the authoritative
[annotations and document Actions guide](docs/annotations-actions.md) for the
exact allowlist, appearance operators, Forms boundary, failure policy, and
page-operation rules.

T13 adds the bounded `ExtractTextAndStructure` Document Query. Its detached
results retain deterministic page/content execution order, unrotated page-
space geometry, exact source-code bytes with explicit mapping confidence,
marked-content nesting and MCIDs, and Tagged PDF hierarchy, role resolution,
language inheritance, Alt, and ActualText. Missing and contradictory Unicode
mappings remain uncertain and diagnostic; no reading order, whitespace, OCR,
font-program guess, unbounded page-tree or content-array traversal, font-
program decode, encoding or width-array traversal, CID width materialization,
malformed explicit Unicode destination, or unbounded `ToUnicode` range
expansion is accepted. See the
[text and logical-structure guide](docs/text-logical-structure.md) for the
coordinate system, ordering, all mandatory limits, safe failures, and the
version-1 unsupported cases.

T14 adds the bounded `ExtractImagesAndResources` Document Query. Its detached
inventory walks effective page and nested Form resources deterministically,
keeps existing indirect Object References while giving direct declarations no
fabricated identity, and reports declaration-reachable page usage. Image
records classify dimensions, color spaces, filters and effective decoding
parameters, explicit, subsidiary, and JPX-embedded masks, and encoded/decoded
availability; explicitly selected available bytes are bounded, defensive, and
usable after the Session closes.
Font records expose BaseFont, embedding, subset, declaration, and page-usage
information. See the authoritative
[image and resource extraction guide](docs/image-resource-extraction.md) for
ordering, identity, byte lifecycle, all mandatory limits, safe failures, and
the version-1 unsupported cases.

Successful `WorkflowOutcome` values identify the capability, the in-process
execution profile, the selected Save Mode, safe diagnostics, and every Target
receipt, plus any declaration-ordered Capability Provider selections. A
deterministic deadline Clock is configured through
`WorkflowEnvironment.withClock(clock)` or `WorkflowEnvironment.builder()` and
supplied when constructing a workflow; `DocumentWorkflow` does not accept a
raw Clock.

Callers moving from the T01 request factories should follow the
[0.x T03 migration note](docs/migrations/0.x-t03-document-workflow.md): every
factory call now receives an explicit `SaveMode`.

## Project information

- Repository: <https://github.com/zerocloud-sdk/folio-pdf>
- Issue tracker: <https://github.com/zerocloud-sdk/folio-pdf/issues>
- Temporary public and security contact: <mabaiqiu@gmail.com>
- License: [Apache License 2.0](LICENSE)
- Chinese usage guide: [docs/zh-CN/getting-started.md](docs/zh-CN/getting-started.md)
- Contribution and clean-room rules: [CONTRIBUTING.md](CONTRIBUTING.md)
- Dependency licenses: [DEPENDENCIES.md](DEPENDENCIES.md)

The authoritative specifications, Javadoc, ADRs, and capability records are in
English. Translations are usage guidance and defer to those English contracts.
