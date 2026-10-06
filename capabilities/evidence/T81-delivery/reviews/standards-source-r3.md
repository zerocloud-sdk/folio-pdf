# Standards source review r3 — issue #81

Comparison baseline and HEAD: `05e7f546f5885680e333ad8d3dea3645b25d26b7`.
Read-only source follow-up to r2 covering the strict test runner, retained
zero-skip observations, acceptance-test staging and filesystem cleanup controls.
Pending certifications and final delivery evidence remain unassessed.

**Hard violations: none found. Optional smells: none warrant a change.**

- `pdf-acceptance/src/test/java/net/zerocloud/pdf/acceptance/T20ContractTestCommand.java:26`
  checks the exact execution count, failures, ignored tests and assumption
  failures. Its assumption listener closes the r2 no-skips audit concern.
  `T20ContractTestCommandTest.java:14`, `:20` and `:31` exercise count mismatch,
  assumption and ignored-test negative controls. The collector requires the
  exact retained zero-skip marker for both original and live executions
  (`scripts/t20_foundation_reports.py:116`, `:149`). This strengthens the
  independent acceptance architecture of ADR-0023 without product changes.
- The runner resides under `src/test`; JUnit remains a test dependency.
  `scripts/t03-foundation.py:975` stages deterministic `acceptance-tests.jar`
  alongside the existing observer harness and includes its identity in the
  configuration closure. Acceptance/test artifacts remain outside production
  runtime, consistent with ADR-0009 and `CONTRIBUTING.md:41–43`.
- `T20ResourceContracts.java:151` requires all remaining paths to be explicitly
  caller-created paths, while `:474` detects nonempty target-adjacent staging
  from a directory snapshot. These observations use public filesystem effects,
  preserve actual receipts and avoid implementation filename dependencies,
  consistent with the public behavior seam and ADR-0013/0025 ownership rules.
- All 57 non-tool frozen inputs matched their pins. Java remains Java 8
  compatible; no product source, product dependency, POM/build configuration
  or CI change was found in the inspected scope.

`CONTRIBUTING.md:46` still mandates full verification. Its matrix rule at
`:47–48` applies to shipped-code/build-compatibility changes; the isolated
acceptance-test harness extension does not trigger that condition. The four
actual JDK certifications, zero-skips requirement and final contract gates
remain required, and this review claims no pending result.

Only this report was written. Findings: **0 hard violations, 0 actionable
optional smells**.
