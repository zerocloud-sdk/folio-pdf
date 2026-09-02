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
