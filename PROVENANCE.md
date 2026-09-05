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

## T12 annotations-document-actions record

- Authorship: OpenAI Codex generated and integrated the T12 implementation,
  tests, inventories, and documentation at repository operator MaBaiqiu's
  direction. No T12 commit was created in this execution.
- Project inputs: GitHub issues #1 and #13, `CONTEXT.md`, ADR-0005, 0006,
  0009, 0011, 0012, 0013, 0014, 0016, 0017, 0019, 0023, 0025, 0029, and
  0034, the repository inventory contract, ISO 32000-2:2020 annotation,
  appearance-stream, Action, destination, and embedded-file clauses, and the
  accepted T03 and T09 through T11 public workflow seams and evidence.
- Runtime and test dependencies: T12 adds no third-party dependency. Apache
  PDFBox 3.0.8 remains the private Apache-2.0 implementation dependency, and
  JUnit 4.13.2 remains the existing test dependency. The no-new-dependency
  statement is recorded in [DEPENDENCIES.md](DEPENDENCIES.md).
- Project-authored fixtures: T12 consumer tests construct their own Text,
  Stamp, Highlight, FileAttachment, standalone Widget, and Link annotations;
  resource-free normal appearances; direct and named destinations; catalog
  and page Actions; unsupported URI, JavaScript, Launch, and chained Action
  graphs; mixed managed and proven-safe legacy Text annotation arrays,
  including copied legacy identifier suffixing and an unrenamable merge
  collision; a filesystem marker that would expose Action execution; and
  malformed appearance, destination, Action, and annotation variants,
  including scalar update and flattening inputs.
  Every update, query, flatten, retarget, preservation or rejection probe,
  reopen, and semantic assertion crosses `DocumentWorkflow.execute`; no
  externally sourced fixture or product artifact is included.
- Acceptance artifacts: the repository-owned acceptance command creates two
  checked-in T12 split products through the public workflow seam after adding
  all six supported annotation types, document and page Actions, a named
  destination, flattening a Stamp annotation, and copying and splitting pages.
  Newly created split products use a project-authored, streaming content-
  derived trailer identifier when the splitter supplies no identifier;
  existing identifiers and non-split publication are unchanged. An acceptance
  regression executes independent runs and compares the exact T12 product
  bytes and evidence metadata.
  The official pinned qpdf 12.4.0 distribution checks both products; the
  syntax record retains their hashes, invocations, exit codes, and raw
  findings. This is syntax evidence only and makes no standards-conformance
  claim.
- Public implementation references: Apache PDFBox 3.0.8 source and Javadocs
  were consulted for its public annotation, appearance, Action, destination,
  file-specification, page import, and content-stream behavior. They were
  obtained from the official Maven artifact and used only behind project-owned
  interfaces.
- Generated documentation: `docs/generated/capability-matrix.md` and
  `docs/generated/facade-surface.md` are deterministic renderings of the two
  YAML authorities and contain no copied external content.
- Excluded inputs: no iText source, resource, fixture, binary, decompiled or
  binary-derived implementation detail, closed add-on material, proprietary
  differential evidence, Reference Suite output, or external behavioral
  oracle was used.
- Scope: T12 adds immutable version-1 values, Commands, and bounded Queries
  for six annotation types, resource-free normal appearances, non-Widget
  annotation flattening, local direct or named GoTo destinations, and catalog,
  page, and Link Action bindings. Managed targets integrate with T10 and T11
  page move, copy, merge, split, and removal behavior. T12 does not add T13 or
  T14 extraction, T15 incremental or signed-document behavior, T16 encryption,
  T17 canvas composition, T20 comprehensive hostile-input policy, T21 Worker
  codecs, T34/T35 AcroForm field behavior, T36 XFDF, form Actions or form
  flattening, or a Migration Facade mapping. Unsupported Action and appearance
  graphs are preserved only when no interpretation is required and otherwise
  rejected before mutation.
- Compatibility Curator evidence: none; the role remains vacant. The T12
  syntax chain passes, while standards, semantic, and visual Acceptance
  Evidence remain absent. Its value, page, and metadata Dependency Gates and
  the T06 promotion gate remain open, so the capability stays `experimental`
  with no certified-platform claim.

## T13 text-logical-structure extraction record

- Authorship: OpenAI Codex generated and integrated the T13 implementation,
  tests, inventories, evidence, and documentation at repository operator
  MaBaiqiu's direction. No T13 commit was created in this execution.
- Project inputs: GitHub issues #1 and #14, `CONTEXT.md`, ADR-0004, 0005,
  0009, 0011, 0013, 0016, 0017, 0019, 0023, 0025, 0029, and the T13
  decision in ADR-0035, the repository inventory contract, and the accepted
  T03 and T09 public workflow and PDF Value seams.
- Public standards inputs: the freely available ISO 32000-1:2008 document
  published by Adobe, the PDF Association's ISO 32000-2 overview and errata,
  and the PDF Association's Well-Tagged PDF guidance were consulted for text
  showing, `ToUnicode`, marked-content, `MCID`, `ActualText`, `Alt`, structure
  children, role mapping, and language semantics. These public materials were
  used as specification inputs, not copied fixtures or implementation code.
- Public implementation references: Apache PDFBox 3.0.8 public source and
  Javadocs for `PDFStreamEngine`, text and marked-content operators, font
  encodings, embedded font loading, simple and CID width handling, Form
  XObjects, and FontBox `CMapParser` numeric and token behavior were consulted
  to keep the private adapter aligned with its supported API. PDFBox output or
  coercive Unicode results are not a behavioral oracle; T13 independently
  preflights and parses mapping evidence, derives simple-font inference only
  from declared PDF encoding data and the public Adobe Glyph List, and tests
  only Native Interface observations.
- Runtime and test dependencies: T13 adds no third-party dependency. Apache
  PDFBox 3.0.8 and transitive FontBox remain private Apache-2.0
  implementation dependencies, and JUnit 4.13.2 remains the existing test
  dependency. The repository-only acceptance path reuses pinned qpdf 12.4.0.
  Exact facts and roles remain recorded in
  [DEPENDENCIES.md](DEPENDENCIES.md).
- Project-authored fixtures: public-workflow consumer tests construct
  untagged, multi-page, multi-stream, transformed, spaced, rotated, explicitly
  mapped, contradictory, unmapped, marked-content, tagged-structure, nested-
  Form, hostile-CMap, bounded embedded-font/encoding, bounded and hostile CID-
  metric and scalar, malformed-resource, page-geometry, Form-dictionary,
  page-tree, content-array, cross-member content-token, truncated-content,
  trailing-orphan-operand, malformed-operator, unbalanced page/Form operator-
  state, bounded-ExtGState, full-width-integer,
  font-kind, font-data-kind, Type 0 encoding,
  Type 3 glyph-program,
  deep Form and logical-structure, repeated and parent-inconsistent structure,
  namespace, OBJR and Form-stream MCR, transitive and cyclic RoleMap,
  invalid Unicode CMap destination, malformed CMap count and range,
  embedded CMap carry, embedded Type 0 subtype-repair, large-text-item, nested
  marked-content, and exact-limit PDFs from project-owned values. Transparent
  minimal PDF syntax supplies only the cyclic graphs, fully linked tagged
  hierarchy, and hostile structures needed to make those contracts
  observable; it calls no other PDF implementation.
  The
  acceptance command
  rewrites its project-owned fully tagged source and creates the checked-in
  T13 page-text product through `DocumentWorkflow.execute`, validates both
  public Query observations, and submits those exact products to pinned qpdf.
- Generated documentation: `docs/generated/capability-matrix.md` and
  `docs/generated/facade-surface.md` are deterministic renderings of the YAML
  authorities and contain no copied external content.
- Excluded inputs: no iText source, resource, binary, fixture, output,
  decompiled or binary-derived implementation detail, closed add-on material,
  proprietary differential evidence, OCR engine output, or Reference Suite
  behavioral oracle was used.
- Scope: T13 adds only bounded detached page text, source-code mapping
  evidence, marked content, and logical structure through a version-1
  Document Query. It adds no image/resource extraction (T14), incremental or
  signature-aware publication (T15), encryption (T16), drawing (T17),
  comprehensive hostile-input policy (T20), Worker codecs (T21), or Migration
  Facade surface.
- Compatibility Curator evidence: none; the role remains vacant. T13 has a
  passing syntax record only. Mandatory standards, semantic, and visual chains
  remain absent, and its T09 Dependency Gate remains open while that capability
  is `experimental`, so T13 remains `experimental` with no certified-platform
  claim.

## T14 image-resource extraction record

- Authorship: OpenAI Codex generated and integrated the T14 implementation,
  tests, inventories, evidence, and documentation at repository operator
  MaBaiqiu's direction. No T14 commit was created in this execution.
- Project inputs: GitHub issues #1 and #15, `CONTEXT.md`, ADR-0005, 0009, 0011,
  0014, 0016, 0017, 0019, 0023, 0025, 0029, and the T14 byte-lifecycle decision
  in ADR-0036, the repository inventory and evidence contracts, and the
  accepted T03, T09, and T13 public workflow, PDF Value, Object Reference,
  page-tree, and extraction-limit seams.
- Public standards inputs: the freely available ISO 32000-1:2008 document
  published by Adobe was consulted for Resource dictionaries, XObject and
  Form traversal, Image dictionaries, filters and decode parameters, color
  spaces, Image Masks, explicit masks, subsidiary and JPX-embedded soft masks,
  Font dictionaries, font descriptors, embedded font programs, and subset
  naming. These public
  materials were used as specification inputs, not copied fixtures or
  implementation code.
- Public implementation references: Apache PDFBox 3.0.8 public source and
  Javadocs for COS dictionaries, arrays, names, object wrappers, streams,
  public Filter implementations, and raw/decoded stream access were consulted
  to keep the private adapter aligned with its supported API. PDFBox object
  wrappers, image objects, decoded metadata, renderer output, and repaired or
  coercive values are not a behavioral oracle; T14 independently traverses and
  classifies raw PDF values, strictly validates the supported bounded filter
  inputs, and tests only Native Interface observations.
- Runtime and test dependencies: T14 adds no third-party dependency. Apache
  PDFBox 3.0.8 remains the private Apache-2.0 implementation dependency, and
  JUnit 4.13.2 remains the existing test dependency. The repository-only
  acceptance path reuses pinned qpdf 12.4.0. Exact facts and roles remain
  recorded in [DEPENDENCIES.md](DEPENDENCIES.md).
- Project-authored fixtures: public-workflow consumer tests construct
  inherited and direct Resources, shared indirect images and fonts, nested and
  cyclic Forms, direct resources, Image Masks, explicit image and color-key
  masks, subsidiary and JPX-embedded soft masks, named and composite color
  spaces, supported and unsupported filter sequences and effective decode
  parameters, external stream declarations, embedded and unembedded subset
  fonts, repeated hostile
  graphs, malformed dimensions, filters, compressed data, masks, fonts,
  resource categories, colors and page trees, decoded-size overflow, and exact
  page, node, value, depth, pixel, decompression, and returned-byte boundaries
  from project-owned values or transparent minimal PDF syntax. The acceptance
  command rewrites two project-owned sources through
  `DocumentWorkflow.execute`, validates the detached public Query results, and
  submits those exact filtered-image/font and nested-Form/soft-mask products to
  pinned qpdf.
- Generated documentation: `docs/generated/capability-matrix.md` and
  `docs/generated/facade-surface.md` are deterministic renderings of the YAML
  authorities and contain no copied external content.
- Excluded inputs: no iText source, resource, binary, fixture, output,
  decompiled or binary-derived implementation detail, closed add-on material,
  proprietary differential evidence, OCR or renderer output, system font, or
  Reference Suite behavioral oracle was used.
- Scope: T14 adds only bounded, detached page and nested-Form resource
  inventory, image metadata and explicitly selected bytes, and font identity,
  embedding, subset, declaration, and Page Usage through one version-1
  Document Query. It adds no incremental or signature-aware publication (T15),
  encryption (T16), drawing (T17), image embedding (T18), comprehensive
  hostile-input policy (T20), Worker isolation/codecs (T21), or Migration
  Facade surface.
- Compatibility Curator evidence: none; the role remains vacant. T14 has a
  passing syntax record only. Mandatory standards, semantic, and visual chains
  remain absent, and its T09 Dependency Gate remains open while that capability
  is `experimental`, so T14 remains `experimental` with no certified-platform
  claim.

## T15 incremental-signature-protection record

- Authorship: OpenAI Codex generated and integrated the T15 implementation,
  tests, research, evidence, and documentation at repository operator
  MaBaiqiu's direction. No T15 commit was created in this execution.
- Project inputs: GitHub issues #1, #10, and #16; `CONTEXT.md`; ADR-0005,
  0009, 0011, 0013, 0016, 0017, 0019, 0023, 0025, and 0029; the repository
  workflow, annotation, inventory, and acceptance contracts; and the accepted
  T03 and T09 through T14 implementation and evidence.
- Public standards and API inputs: ISO 32000-1:2008 clauses 7.5.6, 12.8.1,
  12.8.2.2, and 12.8.4 and Tables 252, 254, and 258 supplied the public
  incremental-update, signature dictionary, ByteRange, DocMDP, and permissions
  contracts. Official Apache PDFBox 3.0.8 Javadocs and Apache-2.0 source for
  `PDDocument.saveIncremental`, signature discovery, COS loading, and update
  tracking supplied public backend behavior and its limitations. Exact URLs,
  source revisions, retrieval results, line citations, hashes, licenses,
  uncertainties, and derived decisions are recorded in
  `docs/research/T15-incremental-signature-primary-sources.md`.
- Runtime and test dependencies: T15 adds no dependency. Apache PDFBox 3.0.8
  remains the private Apache-2.0 implementation dependency and JUnit 4.13.2
  remains the test dependency; their existing coordinates and licenses remain
  recorded in [DEPENDENCIES.md](DEPENDENCIES.md).
- Project-authored fixtures: every signed fixture is deterministic minimal PDF
  syntax authored for T15 and contains placeholder structural signature bytes,
  not a cryptographically valid third-party signature. Fixtures cover inherited
  fields, optional signature Type, general ByteRange pairs, ordinary and
  DocMDP P=1/P=2/P=3 policies, multiple restrictions, unsupported transforms,
  invalid ranges, cycles, permission contradictions, indirect critical
  DocMDP values, and local field, signature-dictionary, ByteRange, and
  signature-reference policy-limit exhaustion. Acceptance artifacts are
  produced only through the project-owned
  `DocumentWorkflow.execute` seam.
- Evidence boundary: the acceptance command verifies exact Source-prefix
  retention, a non-empty appended revision, a committed receipt, and public
  reopen as an implementation precondition, then submits the unmodified
  original and incremental products to pinned qpdf 12.4.0. Only qpdf's result
  is recorded as independent syntax Acceptance Evidence. The reproducibility
  hash replaces every hexadecimal two-value trailer `/ID` with equal-length
  ASCII zeroes; every other byte remains significant and tool inputs are never
  normalized or rewritten.
- Generated documentation: `docs/generated/capability-matrix.md` and
  `docs/generated/facade-surface.md` are deterministic renderings of the YAML
  authorities and contain no copied external content.
- Excluded inputs: no iText source, resource, fixture, binary, output,
  decompiled or binary-derived implementation detail, closed add-on material,
  proprietary differential evidence, external signed PDF, certificate,
  private key, or Reference Suite behavioral oracle was used.
- Scope: T15 recognizes and protects Existing Signatures, authorizes only the
  narrow version-1 DocMDP P=3 non-Widget annotation footprint, and appends
  Source-preserving revisions. It does not implement encryption (T16),
  comprehensive raw hostile-input enforcement (T20), Worker isolation (T21),
  Forms or signature creation (T34+), or cryptographic signature and trust
  validation (T38+).
- Compatibility Curator evidence: T15 has a passing qpdf syntax record only.
  Mandatory standards, semantic, and visual chains remain absent, its T09
  Dependency Gate remains open while T09 is `experimental`, and T06 remains a
  promotion gate. T15 therefore remains `experimental` with no compatible or
  certified-platform claim.

## T16 pdf-version-password-security record

- Authorship: OpenAI Codex generated and integrated the T16 implementation,
  tests, research, evidence, and documentation at repository operator
  MaBaiqiu's direction. No T16 commit was created in this execution.
- Project inputs: GitHub issues #1 and #17; `CONTEXT.md`, `SECURITY.md`, and
  `CONTRIBUTING.md`; ADR-0002, 0005, 0006, 0010, 0011, 0012, 0013, 0016,
  0017, 0019, 0020, 0021, 0023, 0025, 0028, 0029, and 0037; the repository
  workflow, value, page, metadata, annotation, extraction, inventory, and
  acceptance contracts; the accepted T03 through T15 implementation; and the
  fixed review point `0d74da80c9607d3fbe50cd12fbd13810d69bfc8c`.
- Public standards inputs: the Adobe-hosted authorized ISO 32000-1:2008 copy
  supplied the PDF 1.0–1.7 header/catalog, Standard security-handler,
  crypt-filter, password, permission, and extension contracts. Current ISO
  and PDF Association metadata and EC3 resolutions, plus explicitly
  provisional public ISO/FDIS 32000-2 clause material, supplied corroborated
  PDF 2.0 version and R6 behavior. Adobe's public Extension Level 3 supplement
  supplied R5/AESV3 history. A PDF Association presentation identifies ADBE
  Extension Level 8 as the PDF 1.7 interoperability signal for corrected R6,
  but is not treated as normative evidence. Exact URLs, hashes, copyrights,
  uncertainties, and clause/table mappings are recorded in
  `docs/research/T16-pdf-version-password-security-primary-sources.md`.
- Backend inputs: official Apache PDFBox tag `3.0.8`, peeled commit
  `9286e47d89d6877005c9d2d0f2fd38793a62519a`, and source JAR SHA-256
  `eaed642d27599c78229857e4ab571805979f828f5ec8c695e3135ca933766132`
  were inspected under Apache License 2.0. Public loader, version, encryption,
  permission, incremental-save, and security-handler behavior established the
  private adapter boundary. No PDFBox source was copied or adapted.
- Runtime and test dependencies: T16 adds no dependency. Apache PDFBox 3.0.8
  remains the private Apache-2.0 runtime dependency and JUnit 4.13.2 remains
  the test dependency; their existing coordinates and licenses remain recorded
  in [DEPENDENCIES.md](DEPENDENCIES.md).
- Project-authored fixtures: version and structural encrypted-signature
  fixtures are deterministic minimal PDF
  syntax. Legacy protected fixtures implement the public ISO password padding,
  MD5, RC4, owner/user entry, file-key, and permission algorithms for the exact
  admitted V1/R2, V1/R3, V2/R3, and V4/R4 profiles, including the V4 metadata-
  clear file-key exception. No downloaded protected PDF, standard example, or
  third-party output became a fixture. Test-only credential literals are
  controlled inputs and are not copied into evidence.
- Output validation: staged products are inspected for their exact effective
  version and security state and reopened through public user and owner
  credentials and compared with the requested detached version, authority,
  algorithm, scope, and permissions. Project-owned raw checks verify exact
  header/catalog markers, V/R/Length/CFM/filter entries, the permission word,
  R6 `Perms` integrity, and absence of PDFBox's noncanonical crypt-filter
  Length value. These implementation checks are not independent Acceptance
  Evidence.
- Evidence boundary: the acceptance command creates PDF 1.7 ADBE Extension
  Level 8 and PDF 2.0 AES-256 products through `DocumentWorkflow.execute`,
  verifies public user and owner reopen, then gives both unchanged encrypted
  products to pinned qpdf 12.4.0. qpdf's version, R=6, permission, AESv3, and
  parse findings are independent syntax evidence only. qpdf receives the user
  credential through a deleted temporary password file; its password-valued
  output and the temporary path are redacted. The repeatable security-
  observation hash includes only non-secret version/profile/scope/permission
  and page-count observations and excludes randomized authentication entries,
  identifiers, and ciphertext. No standards, semantic, visual, cryptographic,
  or permission-enforcement claim is inferred from qpdf.
- Generated documentation: `docs/generated/capability-matrix.md` and
  `docs/generated/facade-surface.md` are deterministic renderings of the YAML
  authorities and contain no copied external content.
- Excluded inputs: no iText source, resource, fixture, binary, output,
  decompiled or binary-derived implementation detail, closed add-on material,
  proprietary differential evidence, third-party protected PDF, unapproved
  black-box result, private key, or Reference Suite behavioral oracle was used.
- Scope: T16 adds exact version observation, PDF 1.7/PDF 2.0 publication,
  Standard password authentication, AES-256 secure output, exact documented
  legacy profiles and version minima, credential ownership, independently
  proven owner authority, permission enforcement, named-Source preflight and
  anti-downgrade rules, protected rewrite, and encrypted incremental and
  Existing Signature preservation. It does not implement T17
  drawing, T20 comprehensive hostile-input enforcement, T21 Worker isolation,
  T37 public-key encryption, T38+ signing/trust, metadata-clear or attachment-
  only output, or FIPS validation.
- Compatibility Curator evidence: none; the role remains vacant. T16 has one
  passing qpdf syntax record only. Mandatory standards, semantic, and visual
  chains remain absent, its T09 Dependency Gate remains open while T09 is
  `experimental`, and T06 remains a promotion gate. T16 therefore remains
  `experimental` with no compatible or certified-platform claim.

## T17 canvas-vector-positioned-text record

- Authorship: OpenAI Codex generated and integrated the T17 implementation,
  tests, evidence, and documentation at repository operator MaBaiqiu's
  direction. No T17 commit was created in this execution.
- Project inputs: GitHub issues #1, #18, #19, and #20; `CONTEXT.md`,
  `CONTRIBUTING.md`, the repository issue/domain instructions, and ADR-0004,
  0005, 0009, 0011, 0013, 0014, 0016, 0017, 0019, 0020, 0023, 0025, 0029,
  and 0034; the accepted T03, T09, T12, T13, T14, T15, and T16 public
  transaction, PDF Value, content-preflight, extraction, resource,
  publication, signature, and permission seams; and the fixed review point
  `cbd493376c0c8ea3676f47be3e708ba4c03249fd`.
- Public standards input: the Adobe-hosted authorized ISO 32000-1:2008 copy
  already inventoried for T15/T16, SHA-256
  `9de0ca9e8570d6209e8bd48a355be8eb6ec376acfc3fc3ae97cd8730351417ff`,
  supplied the public graphics-state, current-transformation-matrix, path
  construction/painting/clipping, text-object, text-state, text-matrix,
  rendering-mode, Font-resource, content-stream-array, and page-resource
  concepts. The PDF is copyright Adobe/ISO, all rights reserved, was not
  redistributed, and supplied specification semantics only. Existing project
  contracts were used for the narrower fail-closed version-1 policy; no
  standards-conformance claim is inferred.
- Backend inputs: the existing Apache PDFBox 3.0.8 public COS and parser APIs
  for dictionaries, arrays, indirect objects, streams, names,
  `PDFStreamParser`, and content `Operator` values establish the private
  adapter surface. The official tag is `3.0.8`, peeled commit
  `9286e47d89d6877005c9d2d0f2fd38793a62519a`; its already inventoried source
  JAR SHA-256 is
  `eaed642d27599c78229857e4ab571805979f828f5ec8c695e3135ca933766132`.
  PDFBox is Apache-2.0. No source was copied or adapted, and PDFBox identity,
  serialization choices, text mapping, or renderer output is not the T17
  behavioral oracle.
- Runtime and test dependencies: T17 adds no dependency. Apache PDFBox 3.0.8
  and transitive FontBox remain private Apache-2.0 implementation
  dependencies; JUnit 4.13.2 remains the EPL-1.0 test dependency. Pinned qpdf
  12.4.0 remains an acceptance-only Apache-2.0 tool. Their exact coordinates,
  roles, pins, and licenses remain recorded in
  [DEPENDENCIES.md](DEPENDENCIES.md) and the existing qpdf pin authority.
- Project-authored fixtures and resources: consumer tests create deterministic
  minimal PDF syntax for pages, content, resources, unsigned incremental
  input, structural Existing Signatures, and password permissions. The
  acceptance source is a project-authored one-page PDF containing one short
  path, a private marker resource, and an unembedded standard `/Helvetica`
  Type1 Font declaration; it contains no downloaded font program and performs
  no system-font or network lookup. Glyph code `41` hexadecimal and every
  expected path, matrix, rendering mode, and geometry value are project-owned
  test inputs.
- Output validation and evidence boundary: consumer tests publish and reopen
  products only through `DocumentWorkflow.execute` and public PDF Value, T13,
  and T14 queries. The acceptance command retains the exact workflow product,
  computes the existing ID-neutral hash, and sends the unchanged bytes to
  pinned qpdf 12.4.0 for syntax evidence. A distinct project-test semantic
  producer reopens the same artifact through public queries and compares
  semantic operations, state balance, resource reuse, preservation, all eight
  rendering modes, and glyph geometry with the project-owned Canvas Program.
  Neither chain uses Reference Suite output, PDFBox object identity, a private
  call, or incidental dictionary/resource ordering as an oracle. qpdf does
  not establish standards, semantic, visual, or rendering conformance.
- Generated documentation: `docs/generated/capability-matrix.md` and
  `docs/generated/facade-surface.md` are deterministic renderings of the YAML
  authorities and contain no copied external content.
- Excluded inputs: no iText source, resource, fixture, binary, output,
  decompiled or binary-derived detail, closed add-on material, proprietary
  differential evidence, unauthorized black-box observation, external PDF,
  third-party font, system font, image, renderer output, or Reference Suite
  behavioral oracle was used.
- Scope: T17 adds backend-neutral low-level vector paths, affine transforms,
  clipping, nested graphics state, and explicit existing-resource positioned
  glyphs with Canvas-specific types under the Composition-context Native
  Interface namespace and the shared rendering-mode value below that context,
  while retaining the existing deep Document Engine artifact. It adds no T18
  image embedding; T19 font discovery, loading,
  embedding, subsetting, mapping, or fallback; T20 comprehensive hostile-input
  policy; T21 Worker isolation; T23 rendering; layout; barcodes; Forms;
  tagged-document construction; SVG conversion; redaction; public backend
  SPI; Migration Facade mapping; or new Maven artifact.
- Compatibility Curator evidence: none; the role remains vacant. T17 has
  passing syntax and project-owned semantic chains only. Mandatory standards
  and visual evidence remain absent, its T09 Dependency Gate remains open
  while T09 is `experimental` regardless of the closed implementation issue,
  and T06 remains a promotion gate. T17 therefore remains `experimental` with
  no compatible or certified-platform claim.

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

## T18 canvas-images-colors-transparency record

- Authorship: OpenAI Codex generated and integrated the T18 implementation,
  tests, evidence, inventories, and documentation at repository operator
  MaBaiqiu's direction. No T18 commit was created in this execution.
- Project inputs: GitHub issues #1, #18, #19, and #20; `CONTEXT.md`,
  `CONTRIBUTING.md`, repository issue/domain instructions; ADR-0005, 0010,
  0011, 0017, 0023, 0025, and 0036; the accepted T03, T09, T13, T14, T15,
  T16, and T17 public transaction, value, extraction, identity, publication,
  security, preservation, and Canvas seams; and pre-implementation fixed point
  `182a168e70c7b830de568829dff484e16b1b6cbb`.
- Public standards input: the Adobe-hosted authorized ISO 32000-1:2008 copy
  already inventoried by prior tickets, SHA-256
  `9de0ca9e8570d6209e8bd48a355be8eb6ec376acfc3fc3ae97cd8730351417ff`,
  supplied public Image XObject, color-space, Decode, mask, ExtGState, blend-
  mode, Form Transparency Group, resource, and content-placement concepts. It
  was not redistributed and supplied specification semantics only; no
  standards-conformance claim is inferred.
- Backend inputs: existing Apache PDFBox 3.0.8 public COS, stream, save, and
  renderer APIs remain private implementation/secondary-evidence surfaces.
  Java 8 ImageIO and `java.awt.color.ICC_Profile` provide platform raster and
  profile parsing behind project-owned bounds. No backend type, serialization
  choice, provider exception, or renderer output defines Native Interface
  semantics.
- Optional codec: TwelveMonkeys ImageIO 3.14.0, tag
  `twelvemonkeys-3.14.0`, peeled commit
  `62f6e2fba80b3eee99707985ebf4a4fd33abf07b`, supplies the optional TIFF
  ImageIO provider under BSD-3-Clause. The exact root/transitive coordinates,
  JAR hashes, roles, distribution behavior, copyright, and license text are in
  [DEPENDENCIES.md](DEPENDENCIES.md) and the
  [TwelveMonkeys manifest](docs/third-party/twelvemonkeys-imageio-tiff-3.14.0.md).
  `pdf-document` marks it optional; no Folio PDF artifact shades or bundles
  it. The repository-only acceptance module selects it directly to exercise
  the available path. Provider absence is a first-class tested result.
- Project-authored fixtures: consumer and acceptance tests synthesize their
  raster pixels, JPEG/PNG/TIFF encodings, raw samples, masks, ICC data from the
  JDK sRGB profile, minimal existing-image PDF syntax, signature/password
  inputs, limits, and expected semantic values. No external image, PDF,
  profile, proprietary output, system font, or Reference Suite fixture was
  copied.
- Output validation and evidence boundary: public workflows create, publish,
  reopen, and query every product. T14 observations prove dimensions, filters,
  masks, color/profile identity, Object References, Page Usage, and resource
  counts. The repository acceptance command sends the unchanged ID-neutral-
  hashed T18 artifact to pinned qpdf 12.4.0 and PDFium v0.11.2/Chromium 7881;
  ImageMagick 7.1.2-30 compares validated fixed-size PNGs only. The
  project-owned expected raster is the visually reviewed 144-DPI PDFium
  baseline, SHA-256
  `4027a0a929494c49051a3039be5bd1c06d2a6624ba7c161acb8c1bfe0780024a`.
  Expected comparison requires AE 0; secondary PDFBox Renderer disagreement
  must not exceed AE 2,500 and can never make the chain pass. Repeat-run tests
  require identical records, findings, ID-neutral artifact hashes, and raster
  hashes.
- Generated documentation: `docs/generated/capability-matrix.md` and
  `docs/generated/facade-surface.md` are deterministic renderings of the YAML
  authorities and contain no copied external content.
- Excluded inputs: no iText source, resource, fixture, binary, output,
  decompiled or binary-derived implementation detail, closed add-on material,
  proprietary differential evidence, unauthorized black-box observation,
  downloaded image/PDF/profile, arbitrary URI, or Reference Suite behavioral
  oracle was used.
- Scope: T18 deepens the existing Canvas/Document Engine seam with bounded
  JPEG/PNG/TIFF/raw/existing image declaration and placement, Device/
  calibrated/ICCBased painting color, alpha, explicit and soft masks, standard
  blend modes, reusable transparency groups, deterministic resource reuse,
  optional-codec discovery, stable failures, and T14 ICC profile identity. It
  adds no T19 font acquisition, T20 comprehensive hostile-input enforcement,
  T21 Worker isolation, T23 runtime rendering, layout, Forms, SVG, redaction,
  optimization, Migration Facade surface, backend SPI, or new product module.
- Compatibility Curator evidence: none; the role remains vacant. T18 has
  passing syntax, project-owned semantic, and independent visual chains, but
  mandatory standards evidence remains absent. Its T14 and T17 compatible-
  status Dependency Gates remain open while both capabilities are
  `experimental`, and T06 remains a promotion gate. T18 therefore remains
  `experimental` with no compatible or certified-platform claim.

## T19 font-loading-embedding-subsetting record

- Authorship: OpenAI Codex generated and integrated the T19 implementation,
  tests, evidence, inventories, and documentation at repository operator
  MaBaiqiu's direction. No T19 commit was created or authorized in this
  execution.
- Project inputs: GitHub issues #1, #18, and #20; `CONTEXT.md`,
  `CONTRIBUTING.md`, repository issue/domain instructions; ADR-0002, 0005,
  0006, 0009, 0011, 0013, 0014, 0016, 0022, 0023, 0025, 0026, 0029, and
  0035; the accepted T13, T14, T15, T17, and T18 extraction, resource,
  publication, security, preservation, and borrowed-Canvas-Font seams; and
  pre-implementation fixed point
  `f18bc3fc495bf1357ccfd65318125c2e964b5814`.
- Public standards input: the Adobe-hosted authorized ISO 32000-1:2008 copy
  already inventoried by prior tickets, SHA-256
  `9de0ca9e8570d6209e8bd48a355be8eb6ec376acfc3fc3ae97cd8730351417ff`,
  supplied public Type 0 Font, CIDFontType2, Identity-H, width, embedded-font,
  subset-name, and ToUnicode concepts. Public OpenType specification tables
  supplied sfnt-directory, `head`, `hhea`, `maxp`, `hmtx`, `loca`, `glyf`,
  `name`, `post`, Unicode `cmap`, and OS/2 structure and embedding-permission
  semantics. These sources supplied specification
  semantics only and were not redistributed; no standards-conformance claim
  is inferred.
- Backend inputs: existing Apache PDFBox and FontBox 3.0.8 public loading,
  TrueType parsing, subsetting, COS, content-stream, and save APIs remain
  private implementation/secondary-evidence surfaces. No backend type,
  provider exception, resource name, serialization choice, or renderer output
  defines Native Interface semantics, and T19 introduces no dependency.
- Project-authored fixtures: consumer tests and repository-only Acceptance
  Evidence use `FolioPrimary.ttf` (972 bytes, SHA-256
  `e2fbb634c3c0fe78efb449bde4426d10a93aa813596ff5cf3360f02ec97673fb`)
  and `FolioFallback.ttf` (1,028 bytes, SHA-256
  `ced760bc126036779fa84ad3da4638733032512457bf599c44d8c455360f75e1`).
  The project generated these minimal Apache-2.0 TrueType programs from
  declared tables, mappings, and metrics; their scoped READMEs record origin,
  license, contents, and hashes. They are test/evidence data only and are
  absent from the `pdf-document` main resources and published artifacts.
- Output validation and evidence boundary: public workflows create, publish,
  reopen, and query every product. T13/T14 observations prove explicit source
  text, mapping confidence, geometry, embedded subset resources, identity,
  page usage, and bounded public-object font/ToUnicode details. The repository
  acceptance command sends the unchanged ID-neutral-hashed T19 artifact to
  pinned qpdf 12.4.0 and PDFium v0.11.2/Chromium 7881; ImageMagick 7.1.2-30
  compares validated fixed-size PNGs only. The project-owned expected raster
  is the visually reviewed 144-DPI PDFium baseline, SHA-256
  `d5a0c880a7a58bd0de6a1b7e887b6fe6b5a73f14608355434ee8cf022eb04a31`.
  Expected comparison requires AE 0; secondary PDFBox Renderer disagreement
  must not exceed AE 2,500 and can never make the chain pass. Repeat-run tests
  require identical records, findings, ID-neutral artifact hashes, and raster
  hashes.
- Generated documentation: `docs/generated/capability-matrix.md` and
  `docs/generated/facade-surface.md` are deterministic renderings of the YAML
  authorities and contain no copied external content.
- Excluded inputs: no iText source, resource, fixture, binary, output,
  decompiled or binary-derived implementation detail, closed add-on material,
  proprietary differential evidence, unauthorized black-box observation,
  downloaded font/PDF, installed-font scan, implicit network lookup, native
  shaping library, or Reference Suite behavioral oracle was used.
- Scope: T19 adds bounded explicit byte/path/stream/channel font sources, an
  ordered configured Reference Font Set, a closed TrueType version-1 profile,
  embedding-permission enforcement, deterministic Unicode glyph fallback,
  horizontal metrics, subsetting or documented full embedding, ToUnicode,
  resource reuse, positioned Unicode text, stable failures, and public
  semantic/syntax/visual evidence. It preserves the existing borrowed Canvas
  Font path and adds no T20 hostile-input policy, T21 Worker isolation, T23
  runtime rendering, T24 layout, T28 shaping, T29 HarfBuzz, T33 Asian resource
  profile, Migration Facade surface, backend SPI, system-font acquisition, or
  product-bundled font.
- Compatibility Curator evidence: none; the role remains vacant. T19 has
  passing syntax, project-owned semantic, and independent visual chains, but
  mandatory standards evidence remains absent. Its T13, T14, and T17
  compatible-status Dependency Gates and T06 Promotion Gate remain open. T19
  therefore remains `experimental` with no compatible or certified-platform
  claim.

## T20 trusted-in-process hostile-input-policy record

- Authorship: OpenAI Codex generated and integrated the T20 implementation,
  tests, inventories, evidence record, and documentation at repository
  operator MaBaiqiu's direction. No T20 commit was created or authorized in
  this execution.
- Project inputs: GitHub issues #1 and #21; `CONTEXT.md`, `CONTRIBUTING.md`,
  repository issue/domain instructions; ADR-0013, 0016, 0018, 0019, 0025,
  0031, and 0033; the accepted T03 and T09 transaction, ownership, publication,
  PDF Value, and failure seams; existing operation-local limit contracts; and
  pre-implementation fixed point
  `854fa233b16abf76afcc57ee99c374c2c78f0bd1`.
- Public standards input: the Adobe-hosted authorized ISO 32000-1:2008 copy
  already inventoried by prior tickets, SHA-256
  `9de0ca9e8570d6209e8bd48a355be8eb6ec376acfc3fc3ae97cd8730351417ff`,
  supplied public document, indirect-object, container, page-tree, stream-
  filter, and Image XObject concepts. It was not redistributed and supplied
  specification semantics only; no standards-conformance claim is inferred.
- Backend and platform inputs: existing Apache PDFBox 3.0.8 public loading,
  COS, filter, stream-cache, scratch-file, save, and split APIs remain private
  implementation surfaces. Java 8 `Clock`, `Duration`, NIO storage, streams,
  channels, synchronization, and POSIX permission APIs supply platform
  contracts. T20 adds no dependency; all existing coordinates, roles, and
  licenses remain recorded in [DEPENDENCIES.md](DEPENDENCIES.md).
- Project-authored fixtures: `HostileInputWorkflowTest` constructs every PDF
  byte sequence in memory and supplies deterministic clocks, bounded and
  cancelling streams, cancelling outputs, and latch-controlled workflows.
  Its files live only in temporary test directories. The updated snapshot-
  permission regression inspects only files created beneath the current test
  process's temporary directory. No external PDF, image, font, hostile-input
  corpus, system file, or Reference Suite output is included.
- Accounting and storage boundary: every Source is copied through actual-byte
  accounting before PDFBox opens it; iterative parsed-graph preflight accounts
  valid pages, objects, nesting, supported filter output, and materializable
  image dimensions. PDFBox cache is temp-only under the environment-owned
  transaction root. Source snapshots, filter spill, staged products, and
  target-adjacent commit files share one quota and cleanup lifecycle. The
  owned-memory policy models integrated Folio byte lifetimes and makes no
  JVM-wide or backend-allocation hard-limit claim.
- Generated documentation: `docs/generated/capability-matrix.md` and
  `docs/generated/facade-surface.md` are regenerated deterministically from
  the two YAML authorities and contain no copied external content.
- Excluded inputs: no iText source, resource, fixture, binary, output,
  decompiled or binary-derived implementation detail, closed add-on material,
  proprietary differential evidence, unauthorized black-box observation,
  downloaded hostile document, or Reference Suite behavioral oracle was used.
- Scope: T20 adds only finite-default cooperative policy enforcement for the
  trusted in-process Document Workflow. It does not implement T21 Worker IPC,
  separate-process memory/CPU/time/filesystem/network isolation, hard
  termination, T23 rendering, OCR, LibreOffice, redaction, Migration Facade
  surface, or a new product module, and it makes no global publication-
  atomicity or physical secure-erasure claim.
- Compatibility Curator evidence: none; the role remains vacant. All four
  mandatory Acceptance Evidence chains are absent, the T03 and T09 compatible-
  status Dependency Gates remain open, and T06 remains a Promotion Gate. T20
  therefore remains `experimental` with no compatible or certified-platform
  claim.

## T21 hardened-worker record

- Authorship: OpenAI Codex generated and integrated the T21 implementation,
  tests, inventories, evidence record, and documentation at repository
  operator MaBaiqiu's direction. No T21 commit was created or authorized in
  this execution.
- Project inputs: GitHub issues #1 and #22; `CONTEXT.md`, `CONTRIBUTING.md`,
  repository issue/domain instructions; ADR-0013, 0016, 0018, 0024, 0025,
  and 0031; the accepted T03 callback, ordering, lifecycle, ownership,
  publication, result, receipt, progress, and failure seams; the T20 resource
  and temporary-root contract; the complete library-owned Command and Query
  set accepted by `PdfBoxDocumentSession`; and pre-implementation fixed point
  `5d5cb89f8d25f7ad55a77560f2402a5d93d90065`.
- Public platform inputs: Java SE 8 process, stream, NIO, permissions,
  `SecurityManager`, HMAC-SHA-256, `SecureRandom`, concurrency, duration, and
  resource-ownership contracts plus the Linux `/usr/bin/prlimit` CPU and open-
  file resource controls supplied public interface semantics. These were
  platform contracts only; no external Worker implementation, IPC protocol,
  sandbox profile, fixture, product, or output was used as a behavioral
  oracle.
- Backend and dependency inputs: existing Apache PDFBox 3.0.8 public loading,
  COS, command/query, cache, validation, and save APIs remain private Worker
  implementation surfaces. T21 adds no dependency or product module. Its
  launcher treats the complete-byte SHA-256 values of the four required JARs
  and six optional TIFF-closure JARs as the exact runtime authority; their
  coordinates, roles, hashes, notices, and licenses are recorded in
  [DEPENDENCIES.md](DEPENDENCIES.md).
- Project-authored fixtures: workflow tests construct blank and minimal PDF
  inputs through project APIs or fixed in-memory bytes and use only temporary
  directories, service-free local INET and Unix-domain socket attempts,
  sentinel Target bytes,
  deterministic authentication keys at the package-private protocol seam,
  malformed project-authored frames, distinct marker bytes in concurrently
  live transaction roots, the existing project-authored Apache-2.0 T19 font
  fixture, clocks, tokens, callbacks, and latches.
  No external PDF, hostile corpus, font, network service, system document,
  Reference Suite output, or secret credential is included.
- Protocol and isolation boundary: version-1 framing, opcodes, Command/Query/
  value codecs, HMAC tags, sequence rules, and safe failure mapping are
  project-authored. Each execution creates a fresh key and owner-restricted
  random root, stages the primary Source before launch, requests other named
  Sources one at a time in declaration order under T20 accounting, and resolves
  reusable or explicit fonts lazily at their selecting Command. Commands carry
  opaque font-source identifiers; each program is requested in order and sent
  in a separate bounded frame under the same font-source limits. Eligible
  fixed-size page-structure batches use a memory-negotiated atomic frame only
  when the full encoder, retained/cross-process payload, and completion-control
  peak fits; other, parent-ineligible, or Worker-deferred batches use
  one authenticated count declaration followed by Worker-requested indexed
  preflights, details, and items so later inputs remain unmaterialized after the
  first failure. The launcher clears the child
  environment, restricts the Worker class path to digest-pinned first-party
  class-name inventories and exact-hash dependency JARs, and rejects mixed,
  incomplete, extended, delimiter-bearing, or wildcard-bearing entries before
  launch. It runs one local Worker under heap, direct-memory, stack, CPU, and
  descriptor bounds; denies INET and Unix-domain network, descendant process,
  link, and outside-root filesystem access; samples the environment Clock only
  on the caller thread; and applies a monotonic hard Worker-lifetime watchdog.
  Transport-buffer owned-memory accounting remains in the parent's synchronized
  transaction ledger without evaluating caller time policy on its reader
  thread. The parent retains
  callbacks, Providers, actual Targets, and publication and confirms Worker
  exit before committing a product.
- Generated documentation: `docs/generated/capability-matrix.md` and
  `docs/generated/facade-surface.md` are regenerated deterministically from
  the two YAML authorities and contain no copied external content.
- Clean-room and excluded inputs: no iText source, resource, fixture, binary,
  output, decompiled or binary-derived implementation detail, proprietary
  differential evidence, closed add-on material, unauthorized black-box
  observation, third-party protocol or sandbox configuration, downloaded
  hostile input, or Reference Suite behavioral oracle was used. The public
  Reference Suite is not an implementation oracle and no Reference Suite
  Worker API or output defines Native Interface behavior.
- Scope: T21 adds only the opt-in local Hardened Worker profile for the fixed-
  point Document Workflow contract, including authenticated bounded framing,
  closed codecs, a caller-side proxy Session, parent-side Provider and
  publication brokerage, private staging, supported Linux/JDK resource and
  permission controls, hard Worker termination, cleanup, stable failures, and
  implementation evidence. It adds no remote Worker, arbitrary extension,
  public backend SPI, renderer, new Migration Facade surface, idempotent
  transaction identity, retry or uncertain-publication recovery, large-value
  chunking/staging protocol, 5,000-page or 1-GiB certification, full RSS or
  kernel-container guarantee, physical secure-erasure claim, or downstream
  T22-and-later behavior.
- Compatibility Curator evidence: none; the role remains vacant. All four
  mandatory Acceptance Evidence chains are absent, the T03 and T20 compatible-
  status Dependency Gates remain open, and T06 remains a Promotion Gate. T21
  therefore remains `experimental` with no compatible or certified-platform
  claim.

## T22 worker-recovery-scale record

- Authorship: OpenAI Codex generated and integrated the T22 implementation,
  tests, controlled profiles, inventories, evidence record, domain decision,
  and documentation at repository operator MaBaiqiu's direction. No T22 commit
  was created or authorized in this execution.
- Project inputs: GitHub issues #1 and #23; `CONTEXT.md`, `CONTRIBUTING.md`,
  repository issue/domain instructions; ADR-0013, 0016, 0018, 0023, 0024,
  0025, and 0031; the accepted T03 transaction, ownership, publication,
  receipt, outcome, failure, and callback seams; the T20 resource policy and
  accounting model; the T21 Worker, closed protocol, process isolation,
  classpath authority, parent-side publication, and cleanup contract; and
  pre-implementation/code-review fixed point
  `7c12104960045f3069843f850c4e782cb4d63c77`.
- Public platform inputs: Java SE 8 object identity, weak-reference, digest,
  stream, NIO, duration, synchronization, concurrency, process, and resource-
  ownership contracts plus the existing Linux `/usr/bin/prlimit` execution
  envelope supplied public interface semantics. These were platform contracts
  only; no external idempotency implementation, retry protocol, IPC transfer
  format, scale product, fixture, output, or benchmark was used as a behavioral
  oracle.
- Backend and dependency inputs: existing Apache PDFBox 3.0.8 public loading,
  COS, validation, cache, save, and reopen APIs remain private Worker
  implementation surfaces. T22 adds no dependency, product module, shaded
  code, or redistributed artifact. Existing dependency coordinates, origins,
  licenses, notices, and exact Worker-runtime hashes remain authoritative in
  [DEPENDENCIES.md](DEPENDENCIES.md).
- Project-authored recovery fixtures: public tests use temporary Paths and
  byte/stream Targets, latches, cancellation tokens, short elapsed policies,
  safe callback exceptions that simulate lost caller acknowledgement, a
  caller-owned partial-failure stream, and bounded application transaction
  identifiers. They also use an oversized project-authored Target name under a
  512-byte modeled per-record retention policy and reconstructed equal and
  unequal byte-source declarations. The lower-level fault seam only terminates the real project
  Worker or emits a project-authored authenticated malformed response. No
  external PDF, service, database, durable recovery system, hostile corpus,
  credential, authentication key, or Reference Suite output is included.
- Project-authored transfer fixtures: the multi-frame declaration and chunk
  format, transfer identity, total-length/chunk-count rules, SHA-256 integrity,
  aggregate payload-plus-frame memory admission, and stable failure mappings
  are project-authored. Hostile boundary tests synthesize missing, reordered,
  duplicate, corrupted, wrong-identity, wrong-length, wrong-digest, and excess
  frames with deterministic package-private keys, including an authenticated
  trailing chunk presented while less than one frame of modeled scratch is
  available. Public workflow tests create
  4-KiB metadata against a 2-KiB physical-message bound and validate it only
  through publish/reopen semantics.
- Project-authored scale fixtures: the 5,000-page profile constructs 5,000
  `AddBlankPage` commands through the public API. The exact 1-GiB profile uses
  a constant-memory clean-room InputStream that emits a short PDF header,
  newline fill, and a hand-authored valid one-page indirect-object/xref tail at
  byte 1,073,741,824. The concurrency profile uses two latch-held public
  workflows and one first-excess request. Every product and transaction root
  exists only beneath a test temporary directory and is removed; no large
  fixture or product is committed.
- Controlled observations: on 2026-09-04, Linux 6.8.0-136-generic amd64 and
  GraalVM Community Java 17.0.9 completed the opt-in page, input, and
  concurrency profiles. Their exact construction, complete policy, modeled
  memory and temporary-storage high-water marks, accounted elapsed time, wall
  time, and concurrency are recorded in
  [the T22 evidence record](capabilities/evidence/T22-worker-recovery-scale.md).
  These are implementation observations, not independent evidence, a
  benchmark, whole-process measurement, or certified-platform result.
- Generated documentation: `docs/generated/capability-matrix.md` and
  `docs/generated/facade-surface.md` are regenerated deterministically from
  the two YAML authorities and contain no copied external content. The exact
  first-party Worker inventory remains generated from project production
  classes and adds no artifact or external source.
- Clean-room and excluded inputs: no iText source, binary, resource, fixture,
  output, decompiled or binary-derived detail, closed add-on material,
  proprietary differential evidence, unauthorized black-box observation,
  downloaded large document, third-party recovery/transfer protocol, external
  scale corpus, or Reference Suite behavioral oracle was used. qpdf was
  already present locally, while PDFium and ImageMagick were absent from their
  documented cache paths; no external evidence tool was downloaded,
  provisioned, or used to convert an incomplete T22 evidence chain into a
  pass.
- Scope: T22 adds optional environment-local finite idempotency state and
  lookup, deterministic recoverable/final classification, no-replay Path and
  stream uncertainty semantics, bounded authenticated large-value chunking,
  authenticated public resource observations, and opt-in generated controlled
  scale profiles. It adds no remote Worker, durable or distributed recovery,
  automatic retry service, renderer, downstream-ticket behavior, public
  backend SPI, arbitrary extension mechanism, Migration Facade surface,
  dependency, product module, cross-Target atomicity, whole-process RSS/native-
  memory guarantee, kernel-container certification, or physical secure-
  erasure claim.
- Compatibility Curator evidence: none; the role remains vacant. The T22
  syntax, standards, semantic, and visual chains are `INDETERMINATE`; project-
  authored contracts and controlled observations are implementation evidence
  only. The T21 compatible-status Dependency Gate and T06 Promotion Gate
  remain open. T22 therefore remains `experimental` with no compatible or
  certified-platform claim.

## T23 bounded-rendering record

- Authorship: OpenAI Codex authored this contribution at the repository
  operator's direction. The work is an uncommitted implementation of #24;
  no DCO sign-off or commit has been created.
- Requirements and permitted references: GitHub #24 and parent #1, the
  repository instructions, CONTEXT.md, applicable ADRs, the existing T05
  Provider, T20 resource, T21/T22 Worker, T18 image/color, T19 font, and
  acceptance contracts, plus existing public workflow tests and fixtures.
- Backend reference: the existing Apache-2.0 PDFBox 3.0.8 source archive and
  official tagged [PDFRenderer.java](https://raw.githubusercontent.com/apache/pdfbox/3.0.8/pdfbox/src/main/java/org/apache/pdfbox/rendering/PDFRenderer.java),
  [PageDrawer.java](https://raw.githubusercontent.com/apache/pdfbox/3.0.8/pdfbox/src/main/java/org/apache/pdfbox/rendering/PageDrawer.java),
  PDFStreamEngine, PDFGraphicsStreamEngine, FontMappers, and FontMapperImpl
  were inspected to check geometry, cooperative operator hooks, existing
  annotation appearance behavior, font fallback, and image-codec boundaries.
  The integration calls/subclasses the approved private dependency; it copies
  no backend implementation into the public interface. Existing coordinates,
  licenses, artifact hashes, and notices remain in DEPENDENCIES.md.
- Public implementation and protocol: requests/results, numeric profile,
  fixed PNG assembly using JDK Deflater/CRC32, Provider FRQ1/FRS1 envelopes,
  enum diagnostics, result lifetime, bounded Worker values, and shared live
  temporary-storage grants are project-authored. Bounded ImageIO header reads
  verify terminal JPEG/JPX/JBIG2 dimensions before platform decoding. Java 8
  JDK APIs and the existing project codecs supply the foundations. No new production or Maven
  dependency, font, codec bundle, module, or public backend SPI is added.
- Test fixtures: the public Rendering suite authors small PDF object/xref
  documents, axis-aligned colors, normal/missing annotation appearances,
  explicit half alpha, a referenced nonembedded standard font for diagnostic
  behavior, and deterministic seeded raw image samples. These project-authored
  Apache-2.0 fixtures are generated in test memory and never use another
  implementation's output as the public-test oracle. Temporary files,
  failure streams, clocks, and Providers are also project-authored. Runtime
  system-font substitution is tested only for diagnostics, not compatibility.
- Independent visual inputs: the base T23 PDF is created through public
  Commands and contains red page content and a resource-free green stamp
  appearance. Its analytical expected raster is reproducible with
  [generate-t23-expected.py](scripts/generate-t23-expected.py), authored with
  already installed Pillow 10.2.0 (HPND license) as an offline fixture tool
  outside Maven and published artifacts. The expected SHA-256 is
  `ba2aa587629d5e67d7659ca3ac34933fb15e7d9fdff0fa76976f1aceebc6a4e9`.
  The image and font profiles reuse the existing project-owned T18/T19
  acceptance products and expected rasters unchanged. Their origins and
  licenses remain covered by the T18/T19 records and the
  [font fixture authority](pdf-document/src/test/resources/net/zerocloud/pdf/fixtures/README.md).
  The font profile uses only the two embedded project subsets with their
  pinned source hashes, never an installed font as evidence.
- Evidence tools: already provisioned, hash-checked qpdf 12.4.0, pdfium-cli
  v0.11.2 / PDFium chromium-7881, and ImageMagick 7.1.2-30 ran offline on
  2026-09-05. Exact distribution/component hashes, licenses, and notices remain
  in scripts/*-pin.properties and capabilities/README.md. The public
  RenderPage/RenderedPage implementation is under test; the acceptance-only
  ImplementationRenderer is not the T23 implementation. Raw command results,
  both renderer PNGs, expected PNGs, difference PNGs, input hashes, and fixed
  profiles are retained under capabilities/evidence and capabilities/profiles.
  Initial annotation disagreement was resolved by adding the documented
  PDFium --render-annotations option; no threshold was relaxed. The existing
  T18 JPEG produces the pinned PLATFORM_IMAGE_CODEC diagnostic.
- Inventory/documents: the two YAML authorities, their generated Markdown,
  Worker class inventory/hash, English contract, and Chinese usage guide
  describe this scoped behavior. Required independent standards and formal
  semantic evidence, compatible prerequisites, and promotion/platform gates
  remain open. The capability remains experimental.
- Clean-room statement: no iText source, binary-derived implementation,
  proprietary add-on material, restricted fixture, Reference Suite output,
  external hostile corpus, or Compatibility Curator evidence was used. No
  implicit network/font download or external evidence-tool installation was
  performed. The Compatibility Curator role remains vacant.

## T24 paragraph-composition record

- Author: OpenAI Codex acting on the user's issue #25 implementation request.
  The initial delivery was reviewed as uncommitted changes. The user subsequently
  authorized a commit with DCO sign-off using the repository's configured Git
  identity, a GitHub push, and closure of issue #25.
- References: GitHub issues #25 and #1, the supplied goal text, AGENTS.md,
  CONTRIBUTING.md, CONTEXT.md, the listed Composition/Workflow ADRs, existing
  T17/T18 Canvas, T19 font, T20 resource and Worker code and public-workflow
  tests, and the repository's acceptance/inventory/font documentation. Public
  PDF/OpenType foundations remain those recorded for T17/T18/T19. No new
  third-party source, fixture, font, resource or dependency was introduced.
- Code: the paragraph, flow, page, margin and limit declarations, deterministic
  wrapping and area progression, font-measurement integration, detached page
  painting, Worker codecs/catalogs and T24 tests were authored for this project
  under Apache-2.0. No backend SPI or placeholder module was added. Java 8 and
  the locked dependency versions are retained.
- Fonts: the existing Apache-2.0 project-authored FolioPrimary and FolioFallback
  fixtures have decoded SHA-256
  `e2fbb634c3c0fe78efb449bde4426d10a93aa813596ff5cf3360f02ec97673fb` and
  `ced760bc126036779fa84ad3da4638733032512457bf599c44d8c455360f75e1`.
  Tests and acceptance verify these hashes. These are test data, not a runtime
  font bundle, Foundation Noto profile or installed-font dependency.
- Graphics and expectations: the blue unit-square group, corpus, hand-calculated
  placements and reference PDF are project-authored Apache-2.0 data.
  `T24ParagraphExpectations` is independent of the layout algorithm.
  `T24ParagraphProducts.createReference` positions that oracle through existing
  Canvas/T19 commands and never calls paragraph composition. Expected PNGs
  were rendered from this reference using pinned PDFium and visually inspected
  on 2026-09-05; a T24-produced raster was not adopted as its own golden. Hashes
  and regeneration inputs are in the T24 profile/evidence. All new PDF, PNG and
  text evidence is project data under Apache-2.0.
- Tools: existing offline hash-verified qpdf 12.4.0, pdfium-cli v0.11.2 / PDFium
  chromium-7881 and ImageMagick 7.1.2-30. Distribution, executable, component and
  license authorities remain `scripts/*-pin.properties` and `docs/third-party/`.
  No tool or font download occurred. PDFBox is secondary disagreement evidence,
  not the independent visual oracle.
- Clean-room exposure: the implementing assistant did not access iText source,
  resources, binary-derived implementation, closed add-on information or curator
  black-box output during this work. Missing standards evidence, open Dependency
  Gates and lack of Foundation compatibility certification remain explicit.

Future changes append or update a scoped record and supply the pull-request
provenance statement required by [CONTRIBUTING.md](CONTRIBUTING.md).
