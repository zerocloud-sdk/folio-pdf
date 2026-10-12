# Standards review — complete post-FINISHED diagnostics

Baseline: `42fe5c797711070b319b9419e14fcb7adfa98735`; HEAD unchanged.
Read-only follow-up to standards-relaunch-r1; no files, builds or tests changed.

Reviewed the incremental Worker failure decoration, real exit-code-37 case,
policy-stop assertions, strict diagnostic collector and 173-case inventory.
Every DocumentFailure leaving requireExited receives bounded diagnostics while
preserving code, capability, receipts, transaction identity and suppressed
failures. Snapshotting precedes this path's forced termination. The collector
requires `childExitCode=unavailable` for its live-child grace-expiry marker and
rejects an inconsistent successful-exit claim.

The new public regression verifies unchanged Targets, ordered receipts,
recoverable lookup, confirmed termination and cleanup. The prior timeout
finding remains resolved: ordinary cases use 10 seconds, actual 30-second
expiry uses 60 seconds. Runner, inventory and checker agree on 173 cases.

Conclusion: zero remaining actionable Standards findings. Original
baseline-relative scope is retained; fresh candidate verification and
certification remain separate required gates.
