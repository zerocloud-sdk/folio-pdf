# Standards review — runtime and Facade corrections

Baseline and HEAD: `df2df726a2695267bf63a7c9df49a4a7481f0169`; commit list empty. Reviewed the baseline-relative source changes and corrections since R2. No build, certification or source mutation was performed; this file is the review record. Final evidence and gate review remains pending.

Standards: CONTRIBUTING.md:21–34 and 41–48, CONTEXT.md, docs/agents/domain.md, applicable ADRs 0016/0023/0024/0025/0031/0038/0040 and the authoritative execution contract.

## Documented-standard breaches

None found; no unresolved applicable documented source findings.

- `T20FacadeContracts.java:80` validates and serializes actual receipt target names, statuses and all partial-output flags for successful and failed public Facade publication. The shared acceptance observer and T20/T21 expectations remain outside shipping artifacts. This preserves CONTRIBUTING.md:42–45 and ADR-0025 rather than manufacturing a Worker Facade mapping.
- `scripts/t03-foundation.py:1177` records actual runtime vendor/build separately from release `IMPLEMENTOR`; the pinned OS/JDK/executable authority remains unchanged. `scripts/t21_foundation_reports.py:153` compares child runtime properties with the independently observed runtime and still requires the declared build and executable hash. This matches the exact-observation principle in ADR-0040 and the glossary's Certified Environment definition; the JDK 8 Temurin runtime label is not substituted into historical records or release identity.
- `FoundationEvidence.java:62` validates the new runtime and absolute launcher witnesses within the closed inventory schema. Lines 228–253 require both witnesses and exactly one nonempty prerequisite command for Worker certification, without imposing the new fields on historical predecessor schemas. Validation remains owned by the repository inventory tool and introduces no public backend/testing seam.
- `FoundationReadinessCommandTest.java:100` uses explicitly synthetic fixtures to exercise valid runtime/launcher fields and reject missing, empty and malformed witnesses/commands. The corrected hash diagnostic assertion agrees with the existing validator contract. No product class inventory, shipping main source or module POM changed.

## Judgment disposition

The R1 pin-helper duplication remains a bounded accepted judgment choice recorded in `source-dispositions.md`. The new changes present no additional Fowler smell requiring action.

Counts: 0 new documented breaches, 0 unresolved applicable source findings. Completion claims remain subject to final four-tuple certification and required gates.
