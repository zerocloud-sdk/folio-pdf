# Standards review — preliminary source

Baseline and HEAD: `df2df726a2695267bf63a7c9df49a4a7481f0169`; commit list empty.
Reviewed the baseline-relative worktree diff and new T21 Java/Python/profile/documentation files, including proposed inventory promotion and chain narratives. No Maven or certification command was run by this reviewer. Final generated reports, certification records and gates remain outside this preliminary review.

Standards: root AGENTS.md/CLAUDE.md, CONTRIBUTING.md, CONTEXT.md, docs/agents/domain.md and applicable ADRs 0016/0023/0024/0025/0031/0038/0040; the execution contract controls worktree delivery.

## Documented-standard breaches

None found in the source reviewed. CONTRIBUTING.md:41–45 requires Java 8 compatibility, public Workflow behavior, backend-neutral signatures, coordinated capability evidence and separate Facade coverage. New observers stay in the acceptance module; shipping runtime code, class inventories and module POMs are unchanged. Behavioral assertions use public workflows; boundary-specific probes reuse existing internal seams as explicitly allowed by the execution contract. Reflection qualifies unavailable Java Unix APIs without introducing a Java 16 API dependency. Actual Facade IN_PROCESS scope remains explicit. PROVENANCE.md identifies authorship, references and the origin/license of new material as required by CONTRIBUTING.md:21–34. No ADR conflict found.

## Judgment findings

- Possible **Duplicated Code**, not a documented breach: `pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/T21EvidenceCommand.java:58` copies the pin-observation helper in `T20EvidenceCommand.java:71`, including the same `pin.load(input)` → path/hash validation → authority self-hash sequence. Their recorder/seal scaffolding also repeats. The code-review smell baseline recommends extracting repeated shapes. A repository-only shared pin helper would keep these safety checks synchronized; explicit profile-specific observation orchestration can remain separate. The executor may instead retain the bounded duplication with a recorded justification.

Counts: 0 documented breaches, 1 judgment finding. Final evidence/gate review is pending.
