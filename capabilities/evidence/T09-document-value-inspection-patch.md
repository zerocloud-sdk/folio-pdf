# T09 PDF Value inspection and Document Patch evidence

Status: `experimental`

Capability: `document.value.inspect-patch`

Acceptance Profile: `T09-document-value-inspection-patch`

Release train: `0.1.0-SNAPSHOT`

T09 exposes all nine PDF Value kinds without exposing PDFBox object identity,
mutability, types, or exceptions. Scalar values are immutable detached values.
Arrays, dictionaries, and decoded stream bytes are bounded lazy Session views,
and an opaque Object Reference identifies an indirect object only within its
issuing Session. `DocumentRootReference` and `InspectObject` are library-owned,
versioned queries; `DocumentPatch` is a library-owned, ordered version-1
command applied only after Document Engine validation.

## Implementation evidence

- `PdfValueWorkflowTest` drives every assertion through
  `DocumentWorkflow.execute`. Separate rewrite-and-reopen tracers cover null,
  boolean, number, string, name, array, dictionary, stream, and indirect
  reference values. The number tracer preserves a high-precision decimal
  exactly, and dictionary traversal discovers names without backend knowledge.
- The same public workflow contract proves that repeated inspection and Patch
  use of one indirect object produce equal Object References within a Session,
  and a successful value workflow reports the T09 capability identifier.
- Retained array and stream views fail with `PDF_VALUE_VIEW_EXPIRED` after the
  callback ends. Cumulative container access and decoded-stream reads fail with
  `PDF_VALUE_LIMIT_EXCEEDED` when their declared limits are exhausted.
- Foreign Object References, self-cycles and cycles closed through the existing
  object graph, engine-owned stream metadata changes, and non-library
  `PdfValue` implementations are rejected with distinct stable codes, T09
  capability identity, safe fixed diagnostics, and no retained backend cause.
  Every change is prepared before mutation; a later rejected value therefore
  leaves earlier Patch entries unapplied even when the caller catches the
  checked failure and continues to publication.
- `PublicApiLeakageIT` reflectively checks every public and protected signature
  for PDFBox types. `JarContractIT` verifies the stable module name, Java 8
  class-file version, notices, and absence of bundled PDFBox classes.
- `./scripts/inventory check`, `./mvnw -B -ntp verify`, and
  `./scripts/verify-jdk-matrix.sh` are the repository gates for inventory
  drift, full verification, and JDK 8/11/17/21 execution.

T09 changes only custom low-level values attached through a validated Patch;
it does not change the built-in blank-page command, page resources, or the T06
blank-document artifact and evidence records. T15 separately classifies a
validated `DocumentPatch` as representable for unsigned incremental Sources;
signed Sources do not authorize it. T20 now supplies the comprehensive
hostile-input policy, and T21 separately transports the T09 value and Patch
contract through the opt-in Hardened Worker.

This record is implementation evidence, not independent Acceptance Evidence.
No syntax, standards, semantic, or visual chain is recorded for this capability,
so `acceptance-evidence` remains empty. Its blank-document Dependency Gate is
also open because that prerequisite remains `experimental`. T09 therefore
remains `experimental`, with T06 still required before a compatibility or
certified-platform claim.
