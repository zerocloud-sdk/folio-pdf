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

Future changes append or update a scoped record and supply the pull-request
provenance statement required by [CONTRIBUTING.md](CONTRIBUTING.md).
