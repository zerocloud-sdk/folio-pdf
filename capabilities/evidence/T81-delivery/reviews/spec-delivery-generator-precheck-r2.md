# Spec precheck r2 — corrected delivery generators

Baseline: `05e7f546f5885680e333ad8d3dea3645b25d26b7`. Read-only follow-up against the sole issue-81 contract, covering the actual corrected `/tmp/t81-final-audit.py` and `/tmp/t81-write-receipt.py`. Neither generator was executed by this reviewer; final certification remains in progress.

No remaining concrete blocker after both initial precheck findings were resolved:

- For “preserve historical evidence,” replay selection now uses the exact indexed report references and the hash-verified successful JDK17 CLI gate's returned report references. Required replay counts are explicit; other directories remain listed as unindexed history and do not need passing certification.
- For “Missing … records … or altered findings … cannot produce PASS,” each required replay must have exactly the 46 comparator filenames derived from the original retained raster names. The audit verifies every recorded command against the actual bound replay plan, zero exit, empty stdout, AE zero stderr, and exact original/replay path/hash references. Both retained trees and public/contract observations remain checked.
- Before reporting 92 scopes, unchanged `merge_evidence` verifies the full transitive evidence closure. Scope/chain/count/producer and current Candidate/Contract bindings remain required. Every limits chain must retain exactly the same 31 original/live observer references in the existing raw-observation role, with none misplaced in findings.
- The receipt writer now checks the recorded index/staged-build references, unchanged staged closure and every validation/readiness log hash. Required gates must pass; readiness must match current identities and show all selected obligations satisfied while retaining unrelated blockers. Final mode requires both baseline-relative reviews with no unresolved findings; draft review-dependent criteria and issue ACs remain pending.
- All 21 completion criteria and nine issue criteria still have evidence mappings. The approved Native-only mapping, cooperative guarantee, existing encryption and conditional JDK-matrix explanation remain unchanged.

No bound source edits or tests. Successful generator execution, complete final gates, actual archives/worktree evidence and final review remain required before delivery.
