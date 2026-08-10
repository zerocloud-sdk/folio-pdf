# T01 blank-document workflow evidence

Status: `experimental`

Capability: `document.blank.create-publish-reopen`

Acceptance Profile: `T01-blank-document-workflow`

Release train: `0.1.0-SNAPSHOT`

The consumer contract compiles at Java release 8 using JDK and
`net.zerocloud.pdf` public types. It creates one blank page through
`DocumentWorkflow.execute`, receives a committed path receipt, confirms a
non-empty file, then uses a separate execution to reopen the published PDF and
obtain the literal page count `1` through `PageCount`.

## Evidence

- `BlankDocumentWorkflowTest` — public create/publish/reopen/query behavior.
- `WorkflowLifecycleTest` — ordered command/query behavior, callback-failure
  publication abort, Session expiry, and rejection of caller-defined commands.
- `PublicApiLeakageIT` — reflective inspection of every public or protected jar
  signature, generic bound, exception, annotation type, and public constant.
- `JarContractIT` — `Automatic-Module-Name`, Java 8 class-file version, and jar
  boundary checks.
- `InventoryCommandTest` — live authority validation plus black-box state,
  evidence, dependency, facade-reference, generation, and drift fixtures.
- `./scripts/inventory check` — repository-owned validation and generated
  documentation drift contract.
- `./mvnw -B -ntp verify` — repository Maven verification contract.
- `./scripts/verify-jdk-matrix.sh` — the same contract on JDK 8, 11, 17, and 21.

The implementation uses Apache PDFBox 3.0.8 only behind project-owned public
types. Test fixtures are generated during the tests and are not derived from
iText or another product. Full independent Acceptance Evidence is downstream;
this capability must not be promoted to `compatible` before T06 completes.
