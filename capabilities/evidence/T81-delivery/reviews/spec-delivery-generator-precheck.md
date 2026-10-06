# Spec precheck — delivery audit and receipt generators

Baseline: `05e7f546f5885680e333ad8d3dea3645b25d26b7`. Prospective read-only review of `/tmp/t81-final-audit.py` and `/tmp/t81-write-receipt.py` against the sole issue-81 contract. Neither generator was executed by this reviewer; final evidence and reviews remain pending.

Two concrete audit concerns remain in the inspected version:

1. **Replay discovery treats history as certification.** The audit identifies the single indexed replay correctly, but subsequently requires every `live-collection-*` directory to contain a passing strict suite and complete observations. A preserved partial/unindexed failed CLI collection would block delivery despite intact indexed evidence and a successful required retry. The contract requires “preserve historical evidence,” not certification of every scratch attempt. Audit the indexed replay and explicitly bound successful CLI replay; retain other attempts as history.
2. **Raster verification accepts incomplete evidence.** The live-replay audit requires only a nonempty `compare-*.json` list and records its length. It does not verify complete raster coverage, comparator zero exit/AE result, or original/replay reference identities, particularly for additional CLI replay files outside indexed reports. This falls short of the audit's PASS claim under “Missing … records … or altered findings … cannot produce PASS.” Derive the expected raster set from the original/live retained manifests and require exact comparison coverage, valid results and matching hashes.

The requested 31-reference composition is sound: exactly one indexed replay contributes its 20 live raw references alongside 11 original references; each of the five chain reports must include precisely that complete set in environment-observations and exclude it from findings. Direct reference hashes are checked.

The receipt maps all 21 exact completion-criterion IDs and all nine issue criteria. Referenced historical failures, recovery audits, initial/final archives and existing focused validation paths exist; missing final identities/worktree/reviews are expected future inputs. Full-gate exit checks, expected unrelated readiness failures and required-scope SATISFIED checks are separate. Final mode requires both passing baseline-relative reviews and all evidence references. The conditional JDK-matrix explanation remains consistent with unchanged product/POM/build compatibility, subject to final Standards review.

No bound source was modified. These concerns affect delivery generators only and can be corrected without altering the frozen candidate or recertification sequence.
