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
- Scope and determination: T06 records the syntax and semantic chains for the
  built-in blank-document profile, and both pass for the checked-in artifact.
  T07 now supplies the passing independent visual chain. Standards evidence
  remains absent, so the overall determination is still `indeterminate`, the
  T06 promotion gate remains open, and the capability remains `experimental`.

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

## T10 page-manipulation-merge-split record

- Authorship: OpenAI Codex generated and integrated the T10 implementation,
  tests, inventories, and documentation at repository operator MaBaiqiu's
  direction. This record accompanies the repository's one-ticket T10 commit.
- Project inputs: GitHub issues #1 and #11, `CONTEXT.md`, ADR-0013, 0017,
  0019, and 0025, the repository inventory contract, and the accepted T03 and
  T09 public workflow seams and evidence.
- Runtime and test dependencies: T10 adds no third-party dependency. Apache
  PDFBox 3.0.8 remains the private Apache-2.0 implementation dependency, and
  JUnit 4.13.2 remains the existing test dependency. The no-new-dependency
  statement is recorded in [DEPENDENCIES.md](DEPENDENCIES.md).
- Project-authored fixtures: T10 consumer tests construct their own marker
  documents, preservation-sensitive catalog/page/annotation relationship
  structures, custom and external-file content streams, nested content arrays,
  malformed inherited page attributes, structurally inconsistent, missing-type,
  and direct-node page trees, duplicate-scalar indirect objects, caller-failing
  Sources, and a raw nested-page-tree PDF with inherited page boxes,
  rotation, resources, annotations, and content. Every operation,
  terminal-split probe, query non-mutation probe, rejection, reopen,
  rewrite-isolation probe, and semantic assertion crosses
  `DocumentWorkflow.execute`; no externally sourced fixture or product artifact
  is included.
- Acceptance artifacts: the repository-owned acceptance command creates the
  two checked-in T10 split products through all six T10 Commands. The official
  pinned qpdf 12.4.0 distribution checked both products with exit code 0; the
  syntax record retains their hashes, invocations, and raw findings. This is
  syntax evidence only and makes no standards-conformance claim.
- Public implementation references: Apache PDFBox 3.0.8 source and Javadocs
  were consulted for its public page import, split, merge, page-tree, and
  resource behavior. They were obtained from the official Maven artifact and
  used only behind project-owned interfaces.
- Generated documentation: `docs/generated/capability-matrix.md` and
  `docs/generated/facade-surface.md` are deterministic renderings of the two
  YAML authorities and contain no copied external content.
- Excluded inputs: no iText source, resource, fixture, binary, decompiled or
  binary-derived implementation detail, closed add-on material, proprietary
  differential evidence, Reference Suite output, or external behavioral oracle
  was used.
- Scope: T10 adds version-1 page insertion, removal, movement, copying,
  ordered multi-Source merge, exact Target-to-range split, and a page Object
  Reference query. It does not add T11 metadata, outline, destination, or
  attachment management; T12 action management; T15 incremental or signed-
  document behavior; T20 hostile-input policy; T21 Worker codecs; or a
  Migration Facade mapping. Page commands conservatively reject these
  downstream or otherwise unproven catalog, page-tree, page, and annotation
  structures rather than silently altering them.
- Compatibility Curator evidence: none; the role remains vacant. The T10
  syntax chain passes, while standards, semantic, and visual Acceptance
  Evidence remain absent. The blank-document Dependency Gate and T06 promotion
  gate remain open, so the capability stays `experimental` with no certified-
  platform claim.

## T11 metadata-outlines-destinations-attachments record

- Authorship: OpenAI Codex generated and integrated the T11 implementation,
  tests, inventories, and documentation at repository operator MaBaiqiu's
  direction. This record accompanies the repository's one-ticket T11 commit.
- Project inputs: GitHub issues #1 and #12, `CONTEXT.md`, ADR-0013, 0017, and
  0019, the repository inventory contract, ISO 32000-2:2020 document catalog,
  name tree, outline, file specification, and XMP clauses, and the accepted
  T03, T09, and T10 public workflow seams and evidence.
- Runtime and test dependencies: T11 adds no third-party dependency. Apache
  PDFBox 3.0.8 remains the private Apache-2.0 implementation dependency, and
  JUnit 4.13.2 remains the existing test dependency. The no-new-dependency
  statement is recorded in [DEPENDENCIES.md](DEPENDENCIES.md).
- Project-authored fixtures: T11 consumer tests construct their own raw
  marker documents carrying document information entries, XMP packets, flat
  name-tree destinations, outline trees with explicit and named destinations,
  embedded-file specifications and streams, and malformed variants of each
  structure, plus unproven catalog subtrees for the preservation rejection
  matrix. Every command, query, conflict rejection, retarget, reopen, and
  semantic assertion crosses `DocumentWorkflow.execute`; no externally
  sourced fixture or product artifact is included.
- Acceptance artifacts: the repository-owned acceptance command creates the
  two checked-in T11 split products through every T11 Command applied across
  a merge and split. The official pinned qpdf 12.4.0 distribution checked
  both products with exit code 0; the syntax record retains their hashes,
  invocations, and raw findings. This is syntax evidence only and makes no
  standards-conformance claim.
- Public implementation references: Apache PDFBox 3.0.8 source and Javadocs
  were consulted for its public merge, split, name-tree, outline, metadata,
  and embedded-file behavior. They were obtained from the official Maven
  artifact and used only behind project-owned interfaces.
- Generated documentation: `docs/generated/capability-matrix.md` and
  `docs/generated/facade-surface.md` are deterministic renderings of the two
  YAML authorities and contain no copied external content.
- Excluded inputs: no iText source, resource, fixture, binary, decompiled or
  binary-derived implementation detail, closed add-on material, proprietary
  differential evidence, Reference Suite output, or external behavioral
  oracle was used.
- Scope: T11 adds version-1 document information, XMP metadata, named
  destination, outline, and embedded-file commands and queries with
  destination retargeting across the T10 page operations. It does not add T12
  annotation or action management, T13 or T14 extraction, T15 incremental or
  signed-document behavior, T16 encryption, T20 hostile-input policy, T21
  Worker codecs, or a Migration Facade mapping. Structures outside the proven
  safe invariants remain preserved intact or rejected with
  `PRESERVATION_UNSUPPORTED`.
- Compatibility Curator evidence: none; the role remains vacant. The T11
  syntax chain passes, while standards, semantic, and visual Acceptance
  Evidence remain absent. The value-inspection Dependency Gate and the T06
  promotion gate remain open, so the capability stays `experimental` with no
  certified-platform claim.

## T07 independent-visual-evidence record

- Authorship: OpenAI Codex generated and integrated the T07 repository-only
  profile, provisioning, harness, tests, evidence, and documentation at
  repository operator MaBaiqiu's direction. No T07 commit was created in this
  execution.
- Project inputs: GitHub issues #1, #7, and #8, `CONTEXT.md`, ADR-0002, 0005,
  0007, 0011, 0023, and 0027, the accepted T03 and T06 public-workflow and
  Acceptance Evidence seams, the repository Capability Matrix, and the clean-
  room and dependency authorities.
- Independent renderer: the pdfium-cli v0.11.2 WebAssembly Linux amd64 release
  from `https://github.com/klippa-app/pdfium-cli`, embedding PDFium Chromium
  build 7881, is pinned as both release-asset and executable SHA-256
  `3ef3375c429ce665e834f933a028225bf28ac837695aaa69c6fc21facf6780ab`.
  pdfium-cli and go-pdfium are MIT; PDFium is BSD-3-Clause with its documented
  third-party notices. The linked Go modules and the exact PDFium 7881 archive
  notice set are recorded in
  `docs/third-party/pdfium-cli-v0.11.2.md`, tied to the executable hash and the
  source `pdfium-wasm.tgz` SHA-256
  `added6e8ac024f71cb61cf2b77a205d178e2bdde2e4048fbcd916f68b7264d56`.
  The executable is operator-supplied, verified offline, and retained only in
  the ignored local validation cache.
- Raster comparator: the official ImageMagick 7.1.2-30 GCC x86-64 AppImage
  from `https://github.com/ImageMagick/ImageMagick` is pinned as both release-
  asset and executable SHA-256
  `372af8a3fd61ef5f15c6331cde3e21f840eb165d8b533f34ed05d68736dd682e`
  under the ImageMagick License. The origin and license of every extracted
  shared-library payload component are recorded in
  `docs/third-party/imagemagick-7.1.2-30-appimage.md`, tied to that exact
  AppImage hash. It is operator-supplied, verified offline, and retained only
  in the ignored local validation cache.
- Project-owned expectation: the blank-page expected raster is defined as
  `1224x1584` opaque white sRGB RGB pixels by the T03 visual profile and was
  encoded with the pinned ImageMagick tool using metadata stripping. Its
  SHA-256 is
  `c7bbf03603aee1dba4ef80c9eee9abb93b7f3adfb94b84e4abf0203d78f89011`.
  It is a project-authored expectation derived from page geometry and contains
  no Reference Suite or third-party document content.
- Secondary evidence: Apache PDFBox Renderer 3.0.8, already present under
  Apache-2.0 as the private Document Engine dependency, renders the same
  workflow artifact only to detect renderer disagreement. It cannot make the
  visual chain pass and exposes no backend type through the Native Interface.
- Evidence artifact: PDFium renders the exact PDF created by
  `DocumentWorkflow.execute`; ImageMagick receives only validated fixed-size
  PNG paths. The passing record retains an ID-neutral input hash and exact
  raster hashes, profile settings, both AE metrics, tool identities and
  digests, raw findings, and reviewable difference artifacts. The input hash
  replaces only the fresh trailer `/ID` hex values with zeroes, consistently
  with issue #1's byte-equality exclusion; tools receive the unmodified PDF,
  and repeat-run tests require identical T06/T07 metadata. Independent
  standards evidence remains absent, so the overall T03 determination and
  capability remain
  `indeterminate` and `experimental`.
- Excluded inputs: no iText source, resource, fixture, output, decompiled or
  binary-derived implementation detail, closed add-on material, proprietary
  differential evidence, or system font was used.
- Scope: T07 adds repository-only visual Acceptance Evidence. It adds no T23
  runtime page rendering, Capability Provider, Native Interface or Migration
  Facade surface, stable mapping, or unsupported stub, and does not bundle or
  publish either external executable.

## T08 secure Maven Central rehearsal record

- Authorship: OpenAI Codex generated and integrated the T08 release profile,
  repository command, local Central-layout bundle assembler, validator, tests,
  workflow, and documentation at repository operator MaBaiqiu's direction. No
  T08 commit was created in this execution.
- Project inputs: GitHub issues #1 and #9, `CONTEXT.md`, ADR-0030, ADR-0031,
  ADR-0032, the existing Release Train POMs and BOM, the CI workflow,
  `RELEASING.md`, `SECURITY.md`, `DEPENDENCIES.md`, the inventory authorities,
  and accepted T01-T07/T09-T11 implementation and evidence.
- Public tool references: official Apache Maven Source, Javadoc, and GPG plugin
  documentation; official Sonatype Central Publisher Maven documentation;
  official CycloneDX Maven plugin documentation; official MojoHaus Flatten and
  License plugin documentation; and official OWASP Dependency-Check
  documentation and public issues #8715/#8716. These sources supplied public
  configuration and security contracts only.
- Build plugins and tools: the exact Maven coordinates, versions, roles, and
  licenses are recorded in [DEPENDENCIES.md](DEPENDENCIES.md). Maven 3.9.16,
  GnuPG 2.4.4, and rsync 3.2.7 are operational pins. Every GitHub Action
  reference is a full commit SHA. Release-only tooling is absent from
  `pdf-bom` and product runtime artifacts. Hosted vulnerability suppressions
  are disabled; the checked-in suppression file is the only release-gate
  exception authority.
- Project-authored tests and fixtures: T08 tests create a synthetic
  Central-layout repository from project-authored POM, JAR, SBOM, license, and
  vulnerability-report content. Each test invocation generates a temporary
  non-production RSA signing identity, signs the fixture artifacts with GnuPG,
  and invokes the repository command. Corruption cases remove an artifact,
  damage a signature or checksum, remove POM metadata, add an unexpected
  module, inject an unresolved high-severity vulnerability, or remove an audit
  report. No external binary fixture or private key is committed.
- Rehearsal identity boundary: the real command creates a fresh isolated
  one-day test identity, exports only its public key, and deletes its private
  temporary home. It also creates a temporary Maven settings file with dummy
  server id `central` credentials to satisfy Central Publisher without reading
  production credentials. The production workflow instead pins the approved
  fingerprint and obtains the existing key and passphrase only from the
  protected GitHub Environment.
- Capability and facade determination: T08 adds a repository release gate, not
  a PDF behavioral capability, Native Interface, Acceptance Profile, or
  Migration Facade mapping. The Capability Matrix YAML and generated view
  therefore remain unchanged. The Facade Surface authority remains unchanged,
  and no Stable Migration Facade stub is introduced. The concrete release-gate
  determination is recorded in the T08 evidence record.
- Excluded inputs: no iText source, resource, fixture, binary, decompiled or
  binary-derived implementation detail, closed add-on material, proprietary
  differential evidence, Reference Suite output, production credential,
  production private key, passphrase, generated Maven settings, Central
  deployment, tag, or GitHub Release was used or created.
- Scope: T08 builds and validates a non-publishing Maven Central rehearsal and
  supplies a protected, manually dispatched production staging contract. It
  does not publish, tag, certify the Foundation Release (T32), change product
  behavior, promote a capability, or perform the separate human Central
  publication step.

Future changes append or update a scoped record and supply the pull-request
provenance statement required by [CONTRIBUTING.md](CONTRIBUTING.md).
