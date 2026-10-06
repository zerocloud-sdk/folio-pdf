# Spec source review r4 — issue #81

Baseline and observed HEAD: `05e7f546f5885680e333ad8d3dea3645b25d26b7`.
Read-only source follow-up against `/workspace/contracts/issue-81-contract.md`, covering the guard changes after `spec-source-r3.md`. Product runtime remains unchanged. This report does not assert final artifact/environment certification.

No Spec findings:

- For “All mandatory #81 certification cases, tuples, rules and chains execute without skips,” `pdf-acceptance/src/test/java/net/zerocloud/pdf/acceptance/T20ContractTestCommand.java:26` requires the exact execution count, zero failures, zero ignored tests and zero assumption failures, returning nonzero otherwise. Its public JUnit listener observes assumption skips even when JUnit prints a passing count. `T20ContractTestCommandTest.java:15` independently controls passing/count-mismatch, assumption-skip and ignored-test rejection.
- For “Acceptance tools, fonts and fixtures stay outside product runtime,” `scripts/t03-foundation.py:977` places that runner in the repository-only `acceptance-tests.jar`. The existing receipt-bound harness classpath includes it; `:145` and `:1201` invoke it only for T20 with the required 133-test count. Predecessor suites retain their existing JUnit entry point and declared execution coverage.
- For “missing records, mismatched identities, altered findings … cannot produce PASS,” `scripts/t20_foundation_reports.py:116` requires the exact bound command and a single complete zero-failure/ignored/assumption marker in addition to the 133-test success transcript. `:146` enforces the same checks after actual live suite execution. `scripts/tests/test_t20_foundation.py:173` rejects missing markers, ignored/assumption markers, wrong counts, failures and changed commands.
- For “target-adjacent staging [is] released on terminal paths,” `T20ResourceContracts.java:151` rejects every remaining output path unless explicitly registered as caller-created, across all product directories. `:475` snapshots the target directory and cancels after observing a newly created nonempty regular file, without depending on an implementation naming prefix. The existing sentinel, receipt and global cleanup assertions still apply.
- The accepted 75-case coverage, Native-only mapping, four IN_PROCESS tuple scope, cooperative guarantee, separate five chains and T03/T10 reuse remain intact.

No tests were executed by this reviewer. Pending ordered refresh, promotion and final delivery are outside this source review.
