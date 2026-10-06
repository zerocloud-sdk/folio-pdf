# Standards review — delivery timeout recovery

Baseline: `df2df726a2695267bf63a7c9df49a4a7481f0169`. Focused read-only review of `resume-attachments.py`, `finish-refresh.py`, `run-gate.py`, `audit-final.py`, `write-receipt.py` and `failed-attempts.md`, including the retained original wrapper, timeout diagnosis and seven reuse-guard outcomes. No build, test, certification or index mutation was performed by the reviewer.

Standards: CONTRIBUTING.md, CONTEXT.md, applicable Worker/ownership/evidence ADRs and ADR-0040, interpreted under the authoritative execution contract, especially its exact-identity reuse and historical-retention requirements. This is not the final C19 review.

## Documented-standard breaches

None found in the focused recovery changes.

- `resume-attachments.py:57` requires the exact six unique completed scopes, validates all chains/transitive identities through the existing merge validator, and separately checks the qualified producer identities. Original plans, configurations and test commands are checked before reuse. Lines 140–146 reobserve each declared environment and require exact equality; JDK21 executes both modes in fresh directories. Final source/staged identity checks and unchanged-index guard remain in place before merging. The original six records are referenced without rewriting their hashes or labels, consistent with execution order step 5 and ADR-0040.
- `run-gate.py:38` records the increased delivery-only timeout and wrapper hash. Timeout becomes a retained nonzero result rather than losing its result record. `finish-refresh.py:23` preserves the interrupted attempt and continues attachments → limits → worker, stopping on failure. Worker resource policy and frozen certification inputs remain separate.
- `failed-attempts.md:25` accurately classifies the original timeout as an orchestration failure without promoting incomplete observations. Audit/receipt helpers require retained final gates and explicitly mark final review pending in draft mode; they introduce no shipping artifact or public API.

## Judgment findings and disposition

Possible **Duplicated Code**: `resume-attachments.py:154–187` repeats the frozen driver's tuple-observation/record-writing sequence. Accepted bounded disposition: this delivery-only continuation uses the unchanged driver's primitives and exact plans, avoiding a general-runner edit that would invalidate completed frozen certifications. It should remain a ticket-specific recovery artifact, not become a second reusable certification implementation.

Counts: 0 documented breaches, 1 accepted judgment finding, 0 unresolved applicable focused findings. Final continuation, gates, ownership and receipt review remains pending.
