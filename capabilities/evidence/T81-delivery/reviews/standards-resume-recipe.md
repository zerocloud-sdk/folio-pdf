# Standards assessment — concrete attachment resume recipe

Baseline/HEAD: `05e7f546f5885680e333ad8d3dea3645b25d26b7`.
Reviewed `capabilities/evidence/T81-delivery/resume-attachments.py`, SHA-256
`74a5cd951011ddc0fefa0c2b6c8a143d96ed2486713b34fe98b3c36094aa0b32`,
against the unchanged Foundation driver and prospective resume safeguards.

**No remaining execution blocker or documented-standard violation found.**

- The initial raw-native-hash comparison was corrected. `:76–82` compares
  passing results and stable native identities, retaining original raw bytes
  in the full-tree seal. Read-only checks confirmed those stable fields match
  across original JDK 8 observations while timestamps differ. This preserves
  actual-environment attribution under ADR-0040.
- `:88–113` retains the three original scopes directly and checks their
  commands, configuration inputs, Candidate/Contract/environment identities,
  complete chains and producer labels. `:58–60` and `:166` seal and recheck
  all original files, including failed attempts. No record is relabeled.
- `:114–152` executes the five remaining full suite/recorder/collector scopes
  in fresh directories using the existing `execution_plan` and collector.
  `:153–168` retains staged/source checks and actual before/after environment
  equality, including JDK 11's previously missing closing observation.
- `:164–188` requires exactly eight declared scopes, preflights retention of
  all 80 predecessors and exact combined scopes, then calls unchanged locked
  `publish_index` with its stale-authority and transitive-reference audit.
  Both concerns identified during recipe review are resolved.

The recipe is outside the bound source roots; it changes no product, threshold,
policy, tool or execution setting. This remains consistent with ADR-0023/0040
and the approved contract's honest predecessor-refresh requirement. Guards use
assertions, so the reviewed ordinary Python invocation requires optimization
disabled; the inspected `python3 -B` runtime reported optimization level zero.

AST syntax validation passed. No recipe execution, pending certification or
final delivery result is claimed. Only this report was written; no actionable
optional smell warrants expanding this bounded recovery.
