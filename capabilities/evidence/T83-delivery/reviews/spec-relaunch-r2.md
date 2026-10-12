# Spec review — complete post-FINISHED diagnostics

Baseline: `42fe5c797711070b319b9419e14fcb7adfa98735`; HEAD unchanged.
Read-only follow-up to spec-relaunch-r1 against the full sole contract and
user-selected Worker correction. No files, builds or tests changed.

The handler decorates every DocumentFailure leaving requireExited, including
policy stops and nonzero exit. Code, capability, receipts, transaction identity
and suppressed failures remain preserved, with elapsed time, liveness,
available exit code and bounded process/thread observations added.

The real exit-code-37 regression verifies publication prevention, unchanged
Targets, ordered receipts, recoverable lookup and cleanup. All eight exit
cases appear in the consistent 173-case inventory. The 30-second grace and
liveness recheck remain intact; cancellation, caller deadline and the overall
bound still apply. Actual full-grace expiry uses a 60-second JUnit timeout;
ordinary exit cases use 10 seconds. The collector qualifies original and fresh
diagnostics separately and requires `childExitCode=unavailable` for expiry.

Conclusion: zero applicable Spec findings in the incremental changes. Original
baseline-relative review remains applicable. Candidate7 freeze, full
verification, matrix, scale and fourteen-obligation certification remain
separate required gates; no review result substitutes for execution.
