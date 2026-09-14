Independent Spec review receipt

Reviewer: clean-context Codex sub-agent `/root/inventory_bound_spec`.
Repository: `/home/ubuntu/IdeaProjects/open-pdf`.
Packet: `/tmp/t75-inventory-bound-spec-review-cblpVj`.
Result: zero actionable Spec findings in the five-file inventory reader fix.

Scope and authority

- Read the original goal first, before the skill or repository material. Its
  original tool output is retained in `000-original-goal-tool-receipt.json`.
- Applied the Spec brief of `/home/ubuntu/.codex/skills/code-review/SKILL.md`:
  missing/partial requirements, unrequested behavior, and apparently implemented
  but incorrect behavior; the separate report is under 400 words.
- Read AGENTS.md, CONTRIBUTING.md, CONTEXT.md, domain and issue-tracker guidance,
  relevant ADRs including 0035, 0040, 0023, 0016 and 0029, and Foundation readiness
  documentation. Read #75 and governing #1 with `gh issue view --comments`,
  retaining complete bodies, comments and labels. #75 remained OPEN at review.
- The parent relayed the user's subsequent real-name DCO commit/push/close
  authorization, conditional on every technical gate. This review performs no
  such action and provides no final-candidate approval.

Fixed point and adaptation

`git rev-parse 5b1603c435f11c40368f75b7b9a2777c9c5e9761 HEAD` resolves both to
the same fixed baseline. Canonical
`git diff 5b1603c435f11c40368f75b7b9a2777c9c5e9761...HEAD` and
`git log 5b1603c435f11c40368f75b7b9a2777c9c5e9761..HEAD --oneline` are empty.
The explicitly required precommit review uses the WIP adaptation
`git diff BASE -- <five paths>` (full output in `012-wip-diff.stdout`) and the
exact incremental comparison:

```
git diff --no-index -- /tmp/t75-inventory-read-bound-review-freeze-j1zhjsip/before /tmp/t75-inventory-read-bound-review-freeze-j1zhjsip/source
```

That incremental comparison exits 1 because files differ, as expected; complete
output is retained in `009-increment.stdout`. The reviewed paths are
InventoryYaml.java, FoundationEvidence.java, FoundationReadinessCommandTest.java,
docs/foundation-readiness.md and PROVENANCE.md, with full repository-relative
paths and hashes in `five-file-source-identity-audit.json`.

The before tree comprises three originally retained sources and two exact
inverse reconstructions. `before-source-reconstruction.json` preserves that
distinction. The independent final identity audit confirms every old SHA equals
the independently retained r3 candidate input SHA; reconstructed files are not
misrepresented as original snapshots. The current five files remained equal to
the supplied source freeze at review close. The r3 input audit covered all 1,933
source inputs and found exactly the five scoped changes; all 30 contract input
hashes remained current. See `final-source-identity-audit.json`.

Prior review lineage was read from
`capabilities/evidence/T75-delivery/final-delivery-review-r3/README.md` and
`worker-batch-timeout/final-reviewed-source-identity-delta.json`. This packet adds
only the new five-file increment; it does not redo or replace historical raw
certification review, retained annotations controls, or prior full-source review.

Behavior examined

- `FoundationEvidence.verifyChains` changes only the reader selection. Existing
  schema, obligation/profile/release/execution/candidate/contract/environment/
  configuration identity matching, required/distinct chain and report checks,
  qualified producer checks, retained-control reference checks and hash checks
  remain in the same execution path.
- Chain reading retains at most 16,777,216 bytes, fails before retaining a block
  that would exceed that budget, and invokes parsing only after complete EOF.
  Comments and bytes after the YAML document therefore count toward the byte
  limit. Parser code points remain independently finite at 16,777,216.
- Ordinary authorities, environment observations and execution configurations
  still use the existing 3,000,000-code-point loader. Both paths share unchanged
  duplicate-key, recursive-key, collection-alias and depth-30 settings.
- The new tests invoke the public InventoryCommand in actual Java subprocesses.
  They verify readiness, generated recorded completeness, exact byte success,
  first-excess failure, ordinary authority rejection, changed negative-control
  bytes and a failed chain result. Control-reference integrity is distinguished
  from semantic control qualification performed by the unchanged recorder.
- The existing negative matrix continues to cover missing tools/chains/controls,
  mismatched candidate/contract/environment/configuration identities, unqualified
  or reused producers, source changes, missing outputs and unsupported mappings.
  No runtime source or dependency is part of the incremental diff.

Original validation evidence

All records below were inspected as retained original observations; this reviewer
did not rerun Maven, regenerate repository files, or execute new certification.
Exact commands, PIDs, start/end times and exits are retained in the original
command/result/log files and `original-process-receipts-audit.json`.

| Observation | Actual exit | Interpretation |
| --- | ---: | --- |
| r3 inventory generate / validate / check | 0 / 0 / 0 | Structural/current-view success did not satisfy the text criterion. |
| explicit r3 text-satisfied assertion | 1 | Real text blocker retained, with eight 3,000,000-code-point diagnostics. |
| initial trailing-comment reproduction | 0 | Non-reproducing; never counted as RED or closure. |
| leading-comment reproduction | 1 | Actual public readiness failure before the loader change. |
| same leading-comment test after parser change | 0 | GREEN; before/after test sources compare equal. |
| first-excess byte regression with parser-only change | 1 | Exact bound passed; excess was wrongly accepted before bounded reading. |
| byte-bound and leading-comment regressions after read fix | 0 | Both tests passed. |
| final three strict regressions | 0 | Three tests; zero failures/errors/skips. |
| actual r3 chain records with fixed reader | 0 | Generate parsed all records; exact five stale source identities remained. |
| complete inventory module verify | 0 | 17 tests, zero failures/errors/skips; finished 2026-09-13T13:57:43Z. |

The retained complete module XML independently confirms 10 Foundation readiness
tests plus 7 inventory tests. Its original logs and XML are included under
`snapshots/original/complete-inventory-module-verify/`. Intermediate Maven reports
were not captured for every run; retained original logs/results, captured phase
sources and the complete module reports have distinct roles. No missing original
report has been reconstructed or relabeled.

Independent review audits

`audit_frozen_sources_and_evidence.py` copied and verified 97 original/frozen
files into this packet, hashed all 192 indexed historical r3 chain records and
parsed their metadata. There are 48 certification tuples and 192 recorded PASS
chains. Each of eight text standards records carries 18,252 retained-control
references and has a size between 5,290,339 and 5,399,862 bytes. Its full UTF-8
code-point count also exceeds the old limit. These are record identity and size
observations, not new acceptance-tool qualification or certification.

The first reviewer helper (`021`) failed at its final presentation-diagnostic
assertion because it searched for obligation `- Blocker:` lines, while stale
source diagnostics are global lines. The earlier completed audits and original
script, stdout, stderr and exit 1 are retained unchanged. `027` records the actual
diagnostic lines. The separate `finish_diagnostic_audit.py` correctly extracts
the diagnostic text and passes (`029`); it neither modifies the implementation
nor substitutes different product evidence. The final report has zero YAML read
errors and precisely the expected five source identity errors.

The separate source identity audit (`035`) passes; scoped `git diff --check`
(`032`) passes. Every command's complete stdout, stderr, argv, working directory,
actual exit and timestamps is retained through `run_review.py`, after the
read-first bootstrap. Each successful artifact is separately named; no failed
audit, original reproduction or prior summary was overwritten.

Outstanding task-wide gates

The documented reader change invalidates the r3 candidate. Refresh all 48 actual
certification tuples against the final frozen/staged candidate, prove text
satisfied while retaining previously completed obligations, complete full host
and JDK-matrix validation and final inventory/identity/review gates, and only then
perform authorized DCO delivery. Historical r3 PASS labels alone do not satisfy
those gates. This review marks no completion criterion and grants no global
Foundation readiness claim.

Retention and workspace effects

All reviewer-created files reside in this exclusively created `/tmp` packet.
No repository file was modified; no external state was mutated. The final
`file-sha256-manifest.json` covers every packet file except itself. Its SHA and
the report/receipt SHAs are returned separately to the parent.
