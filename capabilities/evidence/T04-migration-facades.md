# T04 Migration Facade implementation evidence

- Capability: `document.blank.create-publish-reopen`
- Release train: `0.1.0-SNAPSHOT`
- Status: `experimental`
- Ticket: T04 / GitHub issue #5

## Scope

T04 packages the mutually exclusive Stable and Experimental Migration Facades.
Because the capability remains `experimental`, `pdf-migration-itext7` contains
no public mapping. `pdf-migration-itext7-preview` maps only the first blank
document workflow under the `net.zerocloud.pdf.itext7.*` suffix convention.

The mapped call shape declares a Path writer, opens a PDF document for writing,
adds one blank page, associates the layout document, closes to publish, reopens
through a Path reader, and observes one page. Publication and inspection run
through the public Native Interface `DocumentWorkflow.execute` seam.

## Public-seam evidence

- `PreviewBlankDocumentFacadeTest` compiles and invokes only public facade
  types. It observes a non-empty published file, a reopened page count of one,
  the stable `SOURCE_READ_FAILED` code for a missing source, and a retained
  `NOT_ATTEMPTED` Native Publication Receipt for a failed publication.
- No facade test imports PDFBox, asserts an internal delegation class, or uses
  a backend object as an oracle.

## Artifact evidence

- The consolidated jar contract checks the resource-only stable artifact's
  Automatic Module Name, license and notice, edition marker, BOM/reactor
  membership, lack of classes, and lack of experimental mappings or stubs.
  It also checks the preview artifact's Automatic Module Name, Java 8
  class-file version, exact public mapped surface, license and notice, edition
  marker, BOM membership, and strict-superset relationship to stable mappings.
- The classpath contract starts a separate JVM for every mapped public class
  in both jar orders. Each class sees both edition resources and fails with the
  same explicit mutual-exclusion diagnostic.

## Evidence boundary

This record is project-owned implementation evidence, not independent
Acceptance Evidence. It does not promote the capability to `compatible`.
T06 records passing syntax and semantic chains, but its conservative final
determination remains `indeterminate` because the mandatory evidence set is
incomplete.

## Execution record — 2026-08-11

- The focused T04 reactor passed with 3 public-facade consumer tests and 2
  preview integration tests covering both artifacts, the exact public surface,
  Java 8 bytecode, and all-public-class mutual exclusion in both jar orders.
- `./scripts/inventory validate`, generated-view regeneration through
  `./scripts/inventory generate`, and `./scripts/inventory check` passed. The
  combined authorities report 12 facade surfaces, all belonging to T04; the
  former T04 exclusion for `document.blank.create-publish-reopen` is absent.
- `./mvnw -B -ntp verify` passed from the repository root, including the
  consumer, artifact, exclusivity, and generated-inventory drift contracts.
- `./scripts/verify-jdk-matrix.sh` passed the full repository verification on
  Eclipse Temurin JDK 8, 11, 17, and 21.
- Independent clean-context Standards and Spec reviews first examined the
  scoped T04 diff against the original fixed point
  `73145c71e85c6b17f95e4b187cd3750c932f53ac`. After T06 and T05 were committed
  independently, fresh reviewers examined the final uncommitted T04 delta
  relative to `c63ad0348a0c8aa93a750a268c8523fde7c944c6`. The review cycles
  identified incomplete public-class exclusivity enforcement, an unnecessary
  stable-artifact shell/dependency, duplicated contract-test support, stale
  execution wording, and T05 assertions left in the inventory test delta. The
  T04 implementation and evidence now address those findings.
- `git diff --check` passed. No T04 commit was created; HEAD is
  `c63ad0348a0c8aa93a750a268c8523fde7c944c6`, and every remaining worktree
  change is T04-scoped.
