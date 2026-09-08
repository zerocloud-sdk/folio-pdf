# T09 PDF Value inspection and Document Patch evidence

Status: `compatible`

Capability: `document.value.inspect-patch`

Acceptance Profile: `T09-document-value-inspection-patch`

Release train: `0.1.0-SNAPSHOT`

T09 exposes all nine PDF Value kinds without exposing PDFBox object identity,
mutability, types, or exceptions. Scalar values are immutable detached values.
Arrays, dictionaries, and decoded stream bytes are bounded lazy Session views,
and an opaque Object Reference identifies an indirect object only within its
issuing Session. `DocumentRootReference` and `InspectObject` are library-owned,
versioned queries; `DocumentPatch` is a library-owned, ordered command validated
and applied atomically by the Document Engine. Original dictionary-entry
requests retain version 1; the extended operations use version 2.

## Implementation evidence

- `PdfValueWorkflowTest` drives every assertion through
  `DocumentWorkflow.execute`. Separate rewrite-and-reopen tracers cover null,
  boolean, number, string, name, array, dictionary, stream, and indirect
  reference values. The number tracer preserves a high-precision decimal
  exactly, and dictionary traversal discovers names without backend knowledge.
- The same public workflow contract proves that repeated inspection and Patch
  use of one indirect object produce equal Object References within a Session,
  and a successful value workflow reports the T09 capability identifier.
- Both Native execution profiles exercise dictionary removal and insertion,
  nested paths, ordered array set/insert/remove, whole indirect value replacement
  and stream-data replacement with explicit unfiltered or Flate encoding. The
  published REWRITE and unsigned INCREMENTAL files reopen with stable aliases,
  correct values and preserved Source bytes. Incremental output retains the
  complete original Source prefix, including previously hidden indirect aliases.
- Retained array and stream views fail with `PDF_VALUE_VIEW_EXPIRED` after the
  callback ends. Cumulative container access and decoded-stream reads fail with
  `PDF_VALUE_LIMIT_EXCEEDED` when their declared limits are exhausted.
- Foreign Object References, self-cycles and cycles closed through the existing
  object graph, engine-owned stream metadata changes, and non-library
  `PdfValue` implementations are rejected with distinct stable codes, T09
  capability identity, safe fixed diagnostics, and no retained backend cause.
  Paths, core document structure and the final reference graph are validated as
  one Patch. A later ordinary rejection restores earlier changes even when the
  caller catches the failure and continues to query, publish and reopen.
- Stream data and engine-owned encoding metadata change together while keeping
  the stream's indirect identity, prior views and existing valid Owner links.
  Failed ordinary changes restore encoded bytes and original metadata, including
  metadata references. The existing hostile-input policy makes resource-limit
  failures terminal; actual stream I/O that prevents restoration also terminates
  the Session with `DOCUMENT_WRITE_FAILED`. Existing lazy views reject access
  after termination, and no target is published. Source bytes stay unchanged.
- Unknown encoded streams and private resources survive unrelated edits with
  their raw content, attributes and aliases intact. An explicit unsupported
  decode request yields the same safe `QUERY_FAILED` in both Native profiles.
  Version/security metadata, including indirect Version aliases and nested
  Extensions or encryption values, remain owned by the output-policy interface.
- `PublicApiLeakageIT` reflectively checks every public and protected signature
  for PDFBox types. `JarContractIT` verifies the stable module name, Java 8
  class-file version, notices, and absence of bundled PDFBox classes.
- `./scripts/inventory check`, `./mvnw -B -ntp verify`, and
  `./scripts/verify-jdk-matrix.sh` are the repository gates for inventory
  drift, full verification, and JDK 8/11/17/21 execution.

T09 exposes low-level values and validated changes; it does not certify the
downstream feature behavior of every PDF dictionary a caller can inspect. It
preserves unrelated content and resource declarations. T15 separately classifies a
validated `DocumentPatch` as representable for unsigned incremental Sources;
signed Sources do not authorize it. T20 now supplies the comprehensive
hostile-input policy, and T21 separately transports the T09 value and Patch
contract through the opt-in Hardened Worker.

## Frozen acceptance contract

The [executable T09 certification contract](../../docs/t09-certification.md)
requires eight current-candidate Native tuples: Ubuntu 24.04/Linux x86-64,
Temurin JDK 8/11/17/21, each in IN_PROCESS and HARDENED_WORKER. Actual Facade
operations remain IN_PROCESS. Each scope runs 50 Native value tests, 31 Facade
value tests and two actual Stable/Preview jar tests. The frozen family includes
all 17 public types and 89 declared members across lifecycle and PDF values.

The canonical project-authored Source is
[`values.pdf`](../profiles/T09-values/fixtures/values.pdf), SHA-256
`c94455cd71eaebf2e2f9f6c32f320bc04087f1e1477c58b596a48a2f106dbca6`.
It contains private data of all nine PDF value kinds, aliases, ASCIIHex/Flate
streams and a blue rectangle on one Letter page. Native REWRITE, unsigned
INCREMENTAL and Facade REWRITE products each undergo syntax, standards,
semantic and visual inspection. Source bytes and the original painting remain
unchanged. Full public suites additionally prove unknown encoded resource and
alias preservation, failure atomicity, resource ownership and bounded lifetimes.

The [51-rule standards profile](../profiles/T09-standards/README.md) extends
the 22 T03 structural rules with 29 T09 rules. It requires both independently
pinned checkers and a matching real illegal-control diagnostic for every rule.
Its [qualification observations](T71-standards-qualification/README.md) retain
tool gaps as well as positive controls. General private dictionaries admit any
PDF type; their exact expected values belong to the semantic chain. Physical
serialization belongs to qpdf. No general ISO/PDF/A/PDF/UA claim is made.

The [visual profile](../profiles/T09-values-visual.properties) fixes 144 DPI,
1224×1584 opaque sRGB, no text/fonts, white background and zero-fuzz AE 0.
The project-authored golden SHA-256 is
`f74af509a0267ddf480a6d1fb1d17110b68503f53f9120d48c905e9477976f4c`.
PDFium is independent; PDFBox is secondary disagreement evidence with a zero
ceiling. The Source and all products must match. A separate one-pixel golden
must fail without modifying the canonical expectation or thresholds.

Syntax uses a non-PDF negative control. Semantic inspection requires the
unchanged Source to reopen successfully and differ from the frozen changed
values. Execution failure is INDETERMINATE and cannot qualify that control.
Supplemental qpdf raw-stream observations follow the final effective object
graph: the retained encoded stream and page painting must have the same bytes
and attributes as the Source. An authored incremental re-encoding control keeps
the original prefix and decoded bytes but changes the current raw encoding, and
must fail. Every chain binds exact, unmodified PDF hashes and actual findings.

This profile defines required observations and implementation evidence. The
current [Foundation Evidence index](../foundation-evidence.yaml) determines
whether the final candidate has all eight qualified chains and its transactions
prerequisite. Development passes alone do not establish current-candidate qualification. The
compatible delivery contract requires those final observations and the independent
delivery review; other Foundation obligations remain outside this scope.
