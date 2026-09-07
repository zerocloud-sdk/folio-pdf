# T31 independent final review

Baseline and HEAD: `060cee07a230ab1c8194efce265615934d619ff2`, branch `main`.
The user explicitly required the complete uncommitted working tree. The
[final scope](T31-review-scope-final.json) includes all 1,599 delivery files,
with empty staging and no intervening commit; an empty three-dot diff did not
omit new implementation. Both reviewers began independently of the implementing
agent and retained separate Standards and Spec axes throughout the review.

The two final reports below are preserved verbatim. Their [original files and
audits](T31-final-review-retention.json), the [reviewed unchecked ledger](T31-ledger-before-final-review.md)
and [pre-closure workspace receipt](T31-workspace-before-final-review.json)
preserve the state on which the gates closed. Only mechanical review retention,
ledger status/link changes and workspace-receipt refresh follow this review.

The initial [Standards assertion finding](T31-standards-review-initial.md)
was repaired and independently rechecked. The initial [Spec RAW findings](T31-spec-review-initial.md)
were repaired through retained Red→Green→Refactor regressions and independent
reruns of the original probes. Their histories and actual verification logs
remain in the delivery record. Code review does not certify standards or
compatibility: those evidence gates remain INDETERMINATE.

## Standards

Independent final Standards gate: **PASS**. No documented-standard blocker remains in the reviewed T31/#32 implementation and evidence.

Reviewed `T31-review-scope-final.json`: all 1,599 file hashes match, comprising 22 tracked changes and 1,577 untracked files. The scope receipt itself is explicitly excluded. Baseline/HEAD remain `060cee07a230ab1c8194efce265615934d619ff2`; commits and staging are empty. Required working-tree, staged and unstaged diffs plus the complete untracked list were refreshed. The empty three-dot diff did not exclude uncommitted implementation.

- The original serialization-dependent test-contract finding remains resolved. Public Workflow observations, private backend boundaries, Java 8 distribution and unchanged provenance remain consistent with `CONTRIBUTING.md:42–45` and ADRs 0010/0013.
- Independent parsing confirms root verification and each sequential JDK 8/11/17/21 run: 1,161 tests, zero failures/errors, four unchanged opt-in skips, and all 76 T31 cases executed. Complete matrix output and its recorded script exit zero agree. All 841 frozen inputs still match the validation copy. The original worktree differs only in the two declared final inventory metadata files.
- `capabilities/capability-matrix.yaml` and its generated view register the observed syntax/semantic/visual chains accurately. Final validate/generate/check logs and file hashes pass. Facade exclusions remain unchanged, consistent with ADR 0029. Certification and compatible-status Dependency Gates remain explicitly INDETERMINATE; the capability remains experimental, consistent with ADR 0023.
- All 1,439 actual-tool artifacts, 450 source declarations and 2,336 prior evidence files remain intact. Earlier independent pixel/negative-control audits remain applicable; all seven retained review outputs match their originals. The 34-row ledger links the required observations, commands, skips, limits and uncommitted state.

**Optional — possible Duplicated Code:** `BarcodePdf417Encoder.java:42` uses `error < bestError - 1e-12 || Math.abs(error - bestError) <= 1e-12 && area < bestArea`; `BarcodePdf417Grid.java:56` repeats that ranking shape. A shared private rule remains a nonblocking heuristic, not a documented-standard breach.

Only mechanical retention of final reports/aggregate, completion-status/link updates and workspace-receipt refresh remain. Any implementation, test, profile, inventory or tool-artifact change requires further validation/review. I changed no delivery file or criterion and ran no Maven/product probe. Audit: `.build-cache/t31/review-standards/final-audit.json`.

## Spec

Independent final Spec gate: CLOSED for the reviewed T31/#32 scope. No remaining missing or partial implementation requirement, scope creep, or incorrect implemented behavior was found against the approved goal, issue #32 and parent specification #1. Both original RAW correctness findings remain resolved.

The final review includes all 1,599 declared delivery files: 22 tracked changes and 1,577 untracked additions, excluding the scope receipt itself. Live hashes, complete fixed-point working-tree diff, unstaged patch and empty staging match the retained scope. HEAD remains `060cee07a230ab1c8194efce265615934d619ff2` on `main`, with no subsequent commits. An empty three-dot diff did not exclude the implementation from review.

The root verification remains the previously reviewed PASS. Independent recalculation of each sequential JDK 8/11/17/21 log confirms 1,161 tests, zero failures/errors and four unchanged opt-in skips; all 76 T31-related cases run without skips in every version. The complete script records exit zero. All 841 frozen inputs match the matrix copy. The only subsequent root build-input changes are the reviewed T31 evidence registration and generated capability view, covered by passing final inventory validate/generate/check receipts.

All 450 recording-source declarations and 1,439 actual-tool artifacts remain unchanged from the independent artifact review. Its 448 qualified path/pixel cases, AE-zero comparisons, 19 negative controls and literal RAW regression observations remain applicable. All 2,336 earlier evidence files are preserved, and retained independent review outputs match their originals.

The delivery ledger maps all 34 criteria to evidence, records commands, skips, limits and commit state, and still leaves every checkbox unchecked. Its 124 local links resolve. Capability status remains experimental; standards certification and compatible-status Dependency Gates remain INDETERMINATE, as required.

Only the scope receipt's permitted bookkeeping remains: retain both independent final reports and their aggregate, update ledger checkboxes/status/links after both reviews close, and refresh the final workspace receipt. Any product, test, profile, inventory or actual-tool artifact change requires renewed validation and review. No Maven, product probe or delivery-file edit was performed by this reviewer. Read-only audit source/results are in `.build-cache/t31/review-spec/final-audit.py` and `final-audit.json`.

Final findings: Standards — 0 hard blockers, 1 optional duplicated-ranking heuristic (nonblocking); Spec — 0 remaining findings.
