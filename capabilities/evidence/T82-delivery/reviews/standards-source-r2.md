# Standards review — corrected source

Baseline and HEAD: `df2df726a2695267bf63a7c9df49a4a7481f0169`; commit list empty. This read-only follow-up covers source corrections after R1. No Maven or certification command was run by the reviewer; final four-tuple evidence and gates remain pending.

Standards remain CONTRIBUTING.md, CONTEXT.md, docs/agents/domain.md and ADRs 0016/0023/0024/0025/0031/0038/0040, interpreted under the authoritative execution contract.

## Documented-standard breaches

None found; no unresolved applicable documented findings.

- `T21WorkflowContracts.java:192`, `T21UnavailableLaunchCommand.java:35` and `T21BoundaryObservationCases.java:316` now validate and serialize actual target names, status and partial-output flags. These remain public Workflow/Publication Receipt observations under CONTRIBUTING.md:42 and ADR-0025; guard tests reject contradictory records.
- `T21BoundaryObservationCases.java:69` observes a nonempty set of actual staged files and their exact 0600 modes. Lines 103–114 pair permitted hard/symbolic link access with denial by the actual production Worker. The separate creation/Unix-permission qualification remains explicitly distinct, respecting ADR-0031 and the closed runtime classpath in ADR-0024.
- `T21PolicyQualificationCommand.java:27` moves the permitted AF_UNIX socket to a short temporary path and removes it before installing the policy. Runtime API discovery remains reflection-based and Java 8 compatible.
- `HardenedWorkerIsolationTest.java:670` accepts either exploded production directories or staged production JARs when constructing hostile artifact fixtures. The alteration stays in tests, preserves the tested rejection behavior and adds no shipping hook or dependency.
- `scripts/tests/test_t10_foundation.py:55` aligns the closed producer catalog with the new acceptance-only T21 semantic/contract roles, without assigning syntax or visual authority to the product backend (ADR-0023).

## Judgment disposition

R1's possible duplicated pin helper is resolved by the explicit rationale in `source-dispositions.md`: retain the small, profile-local, pinned helper to avoid modifying a predecessor recorder. This is a reasonable bounded choice, not a documented breach. No further smell finding warrants action.

Counts: 0 new documented breaches, 0 unresolved applicable findings. Final evidence/gate review is pending.
