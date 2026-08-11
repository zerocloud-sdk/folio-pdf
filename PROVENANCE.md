# Clean-room provenance record

This repository is an independent Apache-2.0 implementation. The accepted
program specification, context glossaries, ADRs, public standards, public API
documentation, and project-owned tests are the permitted design inputs.

## T01 bootstrap record

- Authorship and certification: OpenAI Codex generated and integrated the T01
  implementation and documentation at repository operator MaBaiqiu's direction.
  MaBaiqiu authorized the commit and is recorded as its author and committer,
  with DCO certification supplied through the `Signed-off-by` trailer.
- Project inputs: issue #1, issue #2, the repository context map and Document
  Engine glossary, and ADR-0002, 0004, 0006, 0008–0010, 0012–0015, 0025,
  0029, 0030, and 0032.
- Runtime and test dependencies: Apache PDFBox 3.0.8 and its required
  transitives are Apache-2.0; JUnit 4.13.2 is EPL-1.0, Hamcrest Core 1.3 is
  BSD-3-Clause, and SnakeYAML 2.2 is Apache-2.0. Exact coordinates and roles are
  recorded in [DEPENDENCIES.md](DEPENDENCIES.md).
- Build tooling: Maven Wrapper 3.3.4 scripts were generated from the official
  Apache Maven plugin under Apache-2.0. The wrapper retrieves Apache Maven
  3.9.16 under Apache-2.0 and verifies the declared distribution SHA-256. Maven
  build plugins declared by the project are Apache-2.0.
- CI tooling: `actions/checkout` at commit `11d5960a326750d5838078e36cf38b85af677262`
  and `actions/setup-java` at commit `cf277c60eb25467037889841efdb72551f06f6c3`
  are official MIT-licensed GitHub Actions.
- Local matrix tooling: the Apache-2.0 Podman CLI runs official Eclipse Temurin
  JDK 8, 11, 17, and 21 images. The image definitions are Apache-2.0 and the
  OpenJDK binaries use GPL-2.0 with the Classpath Exception. These tools are
  validation inputs and are not redistributed in project artifacts.
- Test fixtures: T01 creates temporary blank documents entirely through the
  project-owned Native Interface; no external PDF fixture is included.
- Excluded inputs: no iText source, resource, fixture, decompiled or
  binary-derived implementation detail, closed add-on material, or proprietary
  differential evidence was used.
- Compatibility Curator evidence: none; the role is vacant and the T01
  capability remains `experimental`.

## T02 inventory-authority record

- Authorship and certification: OpenAI Codex generated and integrated the T02
  validator, generator, fixtures, inventories, and documentation at repository
  operator MaBaiqiu's direction. MaBaiqiu authorized this commit and is recorded
  as its author and committer, with DCO certification supplied through the
  `Signed-off-by` trailer.
- Project inputs: issue #1, issue #3, the Migration and Document Engine
  glossaries, ADR-0011, 0020, 0023, and 0029, the accepted T01 authorities and
  evidence, and the repository contribution and release contracts.
- Repository tooling: SnakeYAML 2.2 (Apache-2.0) safely parses YAML and remains
  outside shipped product artifacts. Exec Maven Plugin 3.6.3 (Apache-2.0) is
  version-pinned to expose the Java 8 inventory command through Maven and bind
  its drift check to `verify`. Coordinates, roles, and licenses are recorded in
  [DEPENDENCIES.md](DEPENDENCIES.md).
- Test fixtures: every positive and negative inventory, evidence record, and
  generated-document input under `build-tools/inventory/src/test/resources` is
  project-authored for T02. No external PDF, facade source, or product artifact
  is included.
- Generated documentation: `docs/generated/capability-matrix.md` and
  `docs/generated/facade-surface.md` are deterministic renderings of the two
  YAML authorities and contain no external copied content.
- Excluded inputs: no iText source, resource, fixture, decompiled or
  binary-derived implementation detail, closed add-on material, or proprietary
  differential evidence was used.
- Scope: T02 adds only repository validation and documentation tooling. It
  introduces no Migration Facade type or stub, changes no T01 compatibility
  claim, and leaves independent T06 Acceptance Evidence outstanding.

## T03 transaction-contract record

- Authorship: OpenAI Codex generated and integrated the T03 implementation,
  tests, and documentation at repository operator MaBaiqiu's direction. No
  T03 commit was created in this execution.
- Project inputs: GitHub issues #1 and #4, the Document Engine glossary,
  ADR-0013, ADR-0024, ADR-0025, CONTRIBUTING.md, the accepted T01/T02
  implementation and evidence, and the repository Capability Matrix and
  Facade Surface Manifest.
- Runtime and test dependencies: T03 adds no dependency. Apache PDFBox 3.0.8
  remains the private Apache-2.0 implementation dependency; JUnit 4.13.2
  remains the test dependency. Their coordinates, roles, and licenses remain
  recorded in [DEPENDENCIES.md](DEPENDENCIES.md).
- Test fixtures: all PDFs, failing streams, cancellation signals, and
  deterministic clocks used by T03 tests are project-authored and created in
  memory or temporary test directories. Linux Path-ownership checks inspect
  only the current test process's `/proc/self/fd` links and retain no host
  data. No external PDF fixture is included.
- Excluded inputs: no iText source, resource, fixture, decompiled or
  binary-derived implementation detail, closed add-on material, or proprietary
  differential evidence was used.
- Scope: T03 implements only the trusted in-process transaction boundaries.
  It adds no T04 Migration Facade, T05 Capability Provider, T06 independent
  Acceptance Evidence, T15 incremental publication, T20 comprehensive
  hostile-input policy, or T21 Hardened Worker implementation.
- Compatibility Curator evidence: none; the role remains vacant, the
  capability remains `experimental`, and T06 remains its promotion gate.

## T04 migration-facade record

- Authorship: OpenAI Codex generated and integrated the T04 implementation,
  tests, inventories, and documentation at repository operator MaBaiqiu's
  direction. No T04 commit was created in this execution.
- Project inputs: GitHub issues #1 and #5, the Migration and Document Engine
  glossaries, ADR-0004, ADR-0015, ADR-0020, ADR-0029, the accepted T01–T03
  implementation and evidence, and the repository Capability Matrix and
  Facade Surface Manifest.
- Public interface inputs: the official iText 7.2.6 public Javadocs for
  `PdfWriter`, `PdfReader`, `PdfDocument`, `PdfPage`, layout `Document`, and
  `PdfException` were used only to identify public package, constructor,
  return, close, and checked-exception shapes. They were not used as an
  implementation or behavioral oracle.
- Runtime and test dependencies: T04 adds no third-party dependency. The
  resource-only stable facade has no dependencies. The preview facade depends
  only on the existing first-party `pdf-document` artifact, and JUnit 4.13.2
  remains its test dependency. Existing coordinates, roles, and licenses
  remain recorded in [DEPENDENCIES.md](DEPENDENCIES.md).
- Project-authored resources and fixtures: the stable and preview edition
  marker files, temporary output paths, blank PDFs produced through the Native
  Interface, and subprocess classpath probes are project-authored. No external
  PDF fixture or product artifact is included.
- Generated documentation: `docs/generated/capability-matrix.md` and
  `docs/generated/facade-surface.md` were regenerated deterministically from
  the two YAML authorities with the repository inventory tool.
- Excluded inputs: no iText source, resource, fixture, binary, decompiled or
  binary-derived implementation detail, closed add-on material, proprietary
  differential evidence, or Reference Suite output was used.
- Scope: T04 adds only the Stable and Experimental Migration Facade artifacts
  and the first blank-document workflow mapping. It adds no T05 Capability
  Provider contract, T06 independent Acceptance Evidence, or later facade
  capability mapping.
- Compatibility Curator evidence: none; the role remains vacant, the mapped
  capability remains `experimental`, and T06 remains its promotion gate.

## T05 capability-provider record

- Authorship: OpenAI Codex generated and integrated the T05 implementation,
  tests, inventories, and documentation at repository operator MaBaiqiu's
  direction. The T05 implementation is committed with this change.
- Project inputs: GitHub issues #1 and #6, `CONTEXT.md`, `CONTRIBUTING.md`,
  `SECURITY.md`, the repository inventory contract, ADR-0006, 0007, 0009,
  0011, 0013, 0014, 0016, 0018, 0024, 0025, 0029, and 0031, and the accepted
  T01–T03 implementation and evidence.
- Runtime and test dependencies: T05 adds no third-party dependency. The
  shipped Provider contract and subprocess adapter use Java 8 platform types;
  `pdf-document` and `pdf-conversion` use the first-party
  `pdf-provider-contract`. JUnit 4.13.2 remains the existing test dependency.
  The no-new-dependency statement is recorded in
  [DEPENDENCIES.md](DEPENDENCIES.md).
- Project-authored fixtures: deterministic in-process and remote test
  Providers, byte payloads, temporary staging roots, and the Java subprocess
  protocol fixture are authored for T05. The remote fixture is in memory and
  contacts no network service. The subprocess fixture runs on the test JDK;
  no external engine, document fixture, or binary is included.
- Generated documentation: `docs/generated/capability-matrix.md` and
  `docs/generated/facade-surface.md` are regenerated deterministically from
  the two YAML authorities and contain no copied external content.
- Excluded inputs: no iText source, resource, fixture, binary, decompiled or
  binary-derived implementation detail, closed add-on material, proprietary
  differential evidence, Reference Suite output, or external engine protocol
  was used.
- Scope: T05 adds only the project-owned Capability Provider contract,
  immutable Workflow Environment registration and selection configuration,
  explicit remote-disclosure authorization, and a bounded generic subprocess
  adapter. It adds no T06 Acceptance Evidence infrastructure, T20
  comprehensive hostile-input enforcement, T21 Hardened Worker, downstream
  engine adapter, Migration Facade mapping, external engine, or network call.
- Compatibility Curator evidence: none; the role remains vacant. The T03
  Dependency Gate and T06 independent Acceptance Evidence remain open, so the
  T05 capability stays `experimental` with no certified-platform claim.

## T06 acceptance-evidence record

- Authorship: OpenAI Codex generated and integrated the T06 repository-only
  pipeline, tests, evidence, and documentation at repository operator
  MaBaiqiu's direction. No T06 commit was created in this execution.
- Project inputs: GitHub issues #1 and #7, especially issue #1's Testing
  Decisions, the Document Engine glossary, ADR-0005, ADR-0011, ADR-0023, the
  accepted T03 public transaction seam, and the repository Capability Matrix.
- External syntax producer: the official qpdf 12.4.0 Linux x86-64 binary
  archive from
  `https://github.com/qpdf/qpdf/releases/download/v12.4.0/qpdf-12.4.0-bin-linux-x86_64.zip`,
  verified before use as SHA-256
  `a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`.
  Its `bin/qpdf` is additionally pinned as SHA-256
  `9ac787a28597e8428289a12ba3fedafd74bdfb4b4da1be814722faf76f14f21b`.
  `scripts/qpdf-pin.properties` is the single operational pin authority used
  by provisioning, execution, and evidence generation; the canonical Maven
  profile cannot override the repository wrapper or pin file.
- External-tool licenses: qpdf and libqpdf are Apache-2.0. The archive also
  contains libffi, GnuTLS, Nettle/Hogweed, GNU Libidn2, libjpeg-turbo,
  p11-kit, GNU Libtasn1, and GNU libunistring. Their upstream origins and
  exact license families, together with the host-provided runtime
  prerequisites, are recorded in [DEPENDENCIES.md](DEPENDENCIES.md). The
  archive remains in an ignored local validation cache and is not
  redistributed.
- Project semantic producer: `folio-pdf-semantic-assertions` at Release Train
  `0.1.0-SNAPSHOT` creates, publishes, reopens, and observes the one-page
  sequence and readable object graph entirely through
  `DocumentWorkflow.execute` and project-owned command/query types. Text order
  is explicitly not applicable because this profile emits a blank document.
  PDFBox remains behind that public seam.
- Evidence artifact: `T06-document-blank-output.pdf` is generated by the
  project workflow. Both chain records pin its SHA-256 and retain raw qpdf and
  semantic findings. No external PDF fixture is included.
- Finding incorporated into the implementation: qpdf 12.4.0 initially
  returned its documented warning status because the new blank page omitted
  a Resources dictionary. The internal PDFBox adapter now initializes empty
  page resources; no public API or compatibility surface changed.
- Excluded inputs: no iText source, resource, fixture, decompiled or
  binary-derived implementation detail, closed add-on material, or proprietary
  differential evidence was used.
- Scope and determination: T06 records only the syntax and semantic chains for
  the built-in blank-document profile. Both pass for the checked-in artifact,
  but standards and visual evidence are absent. The overall determination is
  therefore `indeterminate`, the T06 promotion gate remains open, and the
  capability remains `experimental`.

## T09 document-value-inspection-patch record

- Authorship: OpenAI Codex generated and integrated the T09 implementation,
  tests, inventories, and documentation at repository operator MaBaiqiu's
  direction. No T09 commit was created in this execution.
- Project inputs: GitHub issues #1 and #10, `CONTEXT.md`, ADR-0019 as the
  primary design authority, ADR-0011, 0013, 0017, 0023, 0025, and 0029, the
  repository inventory contract, and the accepted T01–T06 implementation and
  evidence.
- Runtime and test dependencies: T09 adds no third-party dependency. Apache
  PDFBox 3.0.8 remains the private Apache-2.0 implementation dependency, and
  JUnit 4.13.2 remains the existing test dependency. The no-new-dependency
  statement is recorded in [DEPENDENCIES.md](DEPENDENCIES.md).
- Project-authored fixtures: every T09 PDF is created, patched, published, and
  reopened through the project-owned Native Interface in temporary test
  directories. Test values, byte streams, invalid value implementations, and
  failure cases are authored for T09. No external PDF fixture or product
  artifact is included.
- Generated documentation: `docs/generated/capability-matrix.md` and
  `docs/generated/facade-surface.md` are deterministic renderings of the two
  YAML authorities and contain no copied external content.
- Excluded inputs: no iText source, resource, fixture, binary, decompiled or
  binary-derived implementation detail, closed add-on material, proprietary
  differential evidence, Reference Suite output, or external behavioral oracle
  was used.
- Scope: T09 adds bounded trusted in-process PDF Value inspection and validated
  version-1 dictionary-entry Patches only. It adds no downstream page editing,
  extraction, INCREMENTAL or signed-document behavior (T15), comprehensive
  hostile-input policy (T20), Hardened Worker or codec (T21), or Migration
  Facade mapping.
- Compatibility Curator evidence: none; the role remains vacant. The
  blank-document Dependency Gate and T06 promotion gate remain open, all T09
  Acceptance Evidence chains remain absent, and the capability stays
  `experimental` with no certified-platform claim.

Future changes append or update a scoped record and supply the pull-request
provenance statement required by [CONTRIBUTING.md](CONTRIBUTING.md).
