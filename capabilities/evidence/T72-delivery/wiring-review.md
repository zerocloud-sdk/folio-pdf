# Interim validation-wiring review

This is the independent review of T72's T03/T09 baseline-validation work only.
It is not the final review or a completed #72 gate. Both reviewers used clean
contexts and reviewed the working-tree diff against
`e5053749e35e517fe74f81bd3ebb39d8b41e28ce` plus untracked files. No commits exist
after the fixed baseline. Neither reviewer edited files or reran Maven.

## Standards

Reviewer: `t72_wiring_standards`.

No actionable Standards findings in this interim diff.

Documented standards: no violations found. The Maven hunk,
`<excludedGroups>${acceptance.excludedGroups}</excludedGroups>`, confines
ordinary-build exclusion to the four annotated test methods; the explicit
profile removes that exclusion. The recorder and Foundation runner retain their
independent-chain requirements, consistent with ADR-0023.

The T03 catch records `result.setProperty("syntax", "indeterminate")`, preserves
available diagnostics, and continues recording controls. Final aggregation still
rejects the run. The new regression exercises the public recorder boundary and
checks retained aggregate, Native, Facade, and negative-control observations.

The documentation distinguishes ordinary verification, explicit tool tests, and
candidate certification consistently with ADR-0040. The work log explicitly
leaves #72 unfinished. Heuristic smells: none actionable.

Retained logs show ordinary missing-tool regression success, explicit-profile
rejection with unavailable chains, and 17 focused tests passing without skips.
Full verification and the JDK matrix remain pending.

## Spec

Reviewer: `t72_wiring_spec`.

No actionable Spec findings in the current Step 3 wiring.

Missing/partial interim requirements: none found. The change satisfies
“保持普通构建与独立认证的边界，不把缺工具或跳过执行计为认证通过”: ordinary Maven
verification excludes four explicitly categorized tests, while the profile
restores their execution. Direct T03/T09 commands and Foundation collection still
require passing mandatory chains and detected negative controls.

Scope creep: none found. Changes stay within acceptance tooling, test selection,
related documentation, provenance, and development evidence. No product runtime
dependency, Facade mapping, capability promotion, or tracker mutation appears.

Incorrect implementations: none found. Missing qpdf execution now retains an
INDETERMINATE syntax control and permits final result recording. That control
cannot satisfy the aggregate PASS gate. Existing missing-rule and missing-checker
tests remain enabled under explicit certification.

The retained logs support Red→Green→Refactor: the new unavailable-executable
regression fails before the implementation, passes after it, and passes again
following configuration deduplication. The focused explicit run records 17 tests
with zero failures, errors, or skips. The explicit missing-tool probe rejects
INDETERMINATE standards/visual chains; qpdf was available for that probe. These
remain development results, without candidate-certification claims.

Full-goal remaining work: freeze and implement Foundation page/merger/splitter
contracts, complete public-consumer equivalence tests, establish T10 independent
evidence and eight candidate-bound tuples, refresh affected evidence and
authorities, and run final verification, default JDK matrix, inventory checks,
and final reviews. These are explicitly unfinished; no completion criterion is
marked satisfied.

Findings: Standards 0; Spec 0. Neither axis approves the unfinished full #72 goal.
