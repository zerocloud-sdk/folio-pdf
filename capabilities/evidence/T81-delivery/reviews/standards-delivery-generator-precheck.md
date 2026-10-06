# Standards: delivery-generator precheck

Baseline: `05e7f546f5885680e333ad8d3dea3645b25d26b7`. Read-only prospective assessment; these generators were not executed and no final gate or review result is claimed.

Reviewed scripts and SHA-256 values:

- [Final audit](/tmp/t81-final-audit.py): `bfef5494ac00f0b0bb4bec447a21195a004fb39f3ad7530ea6a891de7f21c7fe`.
- [Worktree audit](/tmp/t81-worktree-audit.py): `4514a3ad71e57b7cd575c4fc7bbb7b2d934dacf5648006bf39afee561f503c49`.
- [Receipt writer](/tmp/t81-write-receipt.py): `2624054ad3bf4e8e14b28ed142745a15d31f0b5dba84a4fe1cfed36d44ea4488`.

No remaining concrete blocker found. All three scripts parse. Exact scope arithmetic is correct: eighty-eight predecessor certifications plus four limits certifications yield ninety-two scopes and 372 chain records. The thirty-one raw-observer references correctly comprise eleven originals plus twenty live-before/after files; exact path-set and cardinality checks exclude omissions, duplicates and ordinary-finding misclassification.

The updated audit selects the indexed replay and the successful, hash-bound CLI replay, retaining failed unindexed directories as history. It derives exactly forty-six raster comparison records from the original retained PNG paths and checks their original/live references, hashes, exact comparator commands, successful exits and AE-zero observations.

Two integrity gaps were found and corrected during this precheck. The audit now requires unchanged full transitive retention through the existing `merge_evidence` checks, rather than relying only on shallow reference hashing. The receipt writer now validates retained validation/readiness log hashes and the identity summary's current-index/staged-build references before using them, and rechecks the actual staged build. Changed logs cannot silently acquire fresh receipt hashes while retaining an earlier passing result. These safeguards preserve exact observed binding ([ADR-0040](/workspace/folio-pdf/docs/adr/0040-certify-only-observed-foundation-environments.md:3)).

All fixed historical receipt paths currently exist. Missing identity-summary, worktree and final-review outputs are expected prerequisites, not source findings. The non-draft receipt requires passing final review metadata against the baseline, passing required gates, satisfied selected obligations and no selected readiness blocker. It preserves overall NOT READY and reports unrelated blockers separately.

Run the generators only after their required observations and gates exist. Refresh the worktree snapshot once all final review/receipt/map filenames exist, then regenerate the final receipt so its counts and audit reference represent the delivered tree. Actual final evidence and baseline-relative reviews remain separately required by the [sole contract](/workspace/contracts/issue-81-contract.md:105).
