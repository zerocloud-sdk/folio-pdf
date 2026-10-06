# Spec review — concrete raw-observer envelope recipe

Baseline and observed HEAD: `05e7f546f5885680e333ad8d3dea3645b25d26b7`.
Read-only prospective review of `capabilities/evidence/T81-delivery/repair-limits-envelope.py` against the sole issue-81 contract and `spec-observer-envelope-path.md`. No execution or final-certification claim.

No remaining execution blocker or Spec finding:

- For “preserve historical evidence,” the recipe hashes the entire original limits tree, writes only to a fresh output directory, and rechecks every original path/hash before publication and afterward. Original configurations and environment references remain unchanged. The audit records recipe/driver identities, original/new report and record references, and the exact moved references.
- For “altered findings … cannot produce PASS,” selection is restricted to exact files in the single retained replay's environment-before/after directories. Each envelope must contain exactly the same 20 moved path/hash pairs. A Counter proves the combined findings/environment-observations multiset is unchanged, all 11 original environment references remain, and every other report field is identical. Record changes are restricted to the corrected report reference; producer labels, identities, results and controls remain identical.
- For exact current binding, staged-build guards and independently evaluated Candidate/Contract identities must match the original inventory. Configuration/input closure and record candidate, environment, execution, configuration-hash and producer values are checked. The original fresh inventory has precisely the four declared IN_PROCESS scopes; every scope must retain exactly five distinct chains.
- Before replacement, unchanged `merge_evidence` verifies all four corrected scopes transitively and separately proves an exact 92-scope union retaining all 88 predecessors. Original-file and authority-byte guards precede unchanged `publish_index`, which retains its lock and stale-index checks. The final audit reports PASS only after the complete index is published and postchecks succeed.
- The repair does not change bound source, tools, settings, thresholds, corpus or underlying observations. It uses the existing raw-observer role and preserves repository-reference traversal for all other findings. The known collector/driver source defect remains a required subsequent fix, followed by meaningful controls, final source review, fresh staging and complete ordered recertification.

No tests were executed by this reviewer. Execution, resulting live inventory evaluation and archival evidence remain required.
