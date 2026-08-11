# T03 Document Workflow transaction evidence

Status: `experimental`

Capability: `document.blank.create-publish-reopen`

Acceptance Profile: `T03-document-workflow-transaction`

Release train: `0.1.0-SNAPSHOT`

T03 extends the T01 public `DocumentWorkflow.execute` seam into a complete
in-process transaction contract. Requests declare uniquely named Sources,
select one primary Source, declare ordered named publication Targets, and
select a Save Mode. An immutable Workflow Environment owns deadline time.
Successful outcomes report the capability, in-process execution profile,
Save Mode, safe diagnostics, and receipts. Path, caller-owned stream,
caller-owned channel, and bounded-byte Sources are covered. Path and
caller-owned stream Targets are covered.

## Implementation evidence

- `BlankDocumentWorkflowTest` preserves the T01 create, publish, reopen, and
  query tracer bullet.
- `WorkflowLifecycleTest` covers command/query ordering, unchanged caller
  runtime propagation, Session expiry, cross-thread rejection, library-owned
  command enforcement, and Path-source release across success and failures.
- `WorkflowTransactionContractTest` covers named primary selection, all T03
  Source forms and ownership, source limits, request invariants, explicit
  REWRITE, stable unsupported INCREMENTAL, successful multi-target
  publication, validation-before-publication, partial stream failure,
  cancellation, deterministic deadlines through Workflow Environment,
  immutable outcome information, nested-failure receipt isolation, and
  sanitized progress ordering.
- `WorkflowResourceOwnershipTest` covers module-opened Path descriptors and
  caller-owned stream, channel, and output ownership across T03 success,
  checked-failure, cancellation, deadline, validation, partial-publication,
  and caller-programming-error exits. Direct descriptor checks run where the
  Linux `/proc/self/fd` contract is available; caller-owned checks are
  platform-neutral.
- `PublicApiLeakageIT` reflectively checks every public and protected
  signature for backend types.
- `JarContractIT` verifies the stable module name and Java 8 class-file
  version.
- `./scripts/inventory check`, `./mvnw -B -ntp verify`, and
  `./scripts/verify-jdk-matrix.sh` are the repository gates for inventory
  drift, the full build, and JDK 8/11/17/21 execution.

Publication is deliberately not a transaction across Targets. A stream write
may be partially visible. On ordered publication failure, earlier Targets
remain `COMMITTED`, the failing Target is `FAILED`, and later Targets are
`NOT_ATTEMPTED`. Path replacement is staged and atomic where the platform
supports it. Caller-owned streams and channels are never closed.

T03 makes no signed-document preservation claim. It does not detect existing
signatures or decide DocMDP permissions; default read-only enforcement and
signature-safe incremental publication remain T15. Signed documents must not
be submitted to a mutating T03 REWRITE workflow.

The PDF fixtures are generated entirely through the project-owned Native
Interface. Apache PDFBox 3.0.8 remains behind project-owned public types. This
record is implementation evidence, not independent Acceptance Evidence. The
capability remains `experimental`; T06 is still required before promotion to
`compatible`.

## Execution record — 2026-08-10

- Focused public-seam validation passed with 27 consumer tests: 1 T01 tracer,
  7 lifecycle tests, 16 transaction tests, and 3 resource-ownership matrices.
- `./mvnw -B -ntp verify` passed with those 27 consumer tests, 2 artifact and
  public-API integration tests, and 6 inventory-tool tests (35 total).
- `./scripts/inventory validate`, generated-view regeneration through
  `./scripts/inventory generate`, and `./scripts/inventory check` passed.
- `./scripts/verify-jdk-matrix.sh` passed the same full verification contract
  on Eclipse Temurin JDK 8, 11, 17, and 21.
- Independent clean-context Standards and Spec reviews examined the complete
  T03 worktree diff against
  `54e33532baac35f6a78cb3c657605e457b1cf080`. After reviewed fixes and scoped
  T15 limitations, both final reviews reported no actionable findings.
- `git diff --check` passed. No T03 commit was created; HEAD remained at the
  fixed point.

The review above is implementation review, not T06 independent Acceptance
Evidence and does not change the capability's `experimental` status.
