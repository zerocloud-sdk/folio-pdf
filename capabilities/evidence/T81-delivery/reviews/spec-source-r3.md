# Spec source review r3 — issue #81

Baseline and observed HEAD: `05e7f546f5885680e333ad8d3dea3645b25d26b7`.
Read-only source follow-up against `/workspace/contracts/issue-81-contract.md`, covering the guard changes after `spec-source-r2.md`. Product implementation and build contracts remain unchanged. This report does not assert final artifact/environment certification.

No Spec findings:

- For “failed/current/later receipts accurately report COMMITTED/FAILED/NOT_ATTEMPTED” and “stream failures correctly identify possible partial output,” `T20ResourceContracts.java:473` observes nonempty adjacent staging, cancels before either Path is committed, asserts both receipts are NOT_ATTEMPTED with no partial output, and preserves both sentinels. `coverage.json` agrees with this public result. This is consistent with the existing Path commitment boundary.
- For “All mandatory #81 certification cases, tuples, rules and chains execute without skips,” the `limits` case now adds `-Dfolio.t09.executionProfile=IN_PROCESS`; `scripts/t03-foundation.py:1180` threads that option into recorder and regression commands. The existing public `PdfValueWorkflowTest` parameter selection uses that property, so the selected 50 value tests plus the other required consumer/artifact tests total 133 without introducing Worker certification.
- For “missing records, mismatched identities, altered findings … cannot produce PASS,” `scripts/t20_foundation_reports.py:116` requires the original exact bound regression command and a successful 133-test transcript. `:125` reruns that same suite in the independently re-observed actual environment before the live recorder. Original and replay command/transcript files are retained in every chain's findings. `scripts/tests/test_t20_foundation.py:173` rejects missing records, 132/183/zero counts, failure markers and changed execution commands.
- The accepted 75-case coverage, distinct shared-environment workflows, primary/Command/donor/product accounting, independently modeled publication lifetimes, original candidate/environment closure, five chains and justified T03/T10 profile reuse remain intact. The Native-only mapping, cooperative guarantee, existing encryption and excluded downstream scope remain preserved.

No tests were executed by this reviewer. Pending ordered refresh, promotion and final delivery are outside this source review.
