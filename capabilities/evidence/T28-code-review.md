# T28 independent code review receipt

Fixed base and review-time HEAD: `875c140fc4617f8a9649f1c9bed8034c52bd6dfa`.
Specification: issue #29, parent #1, and the user's execution contract.
Both axes were assigned to separate clean-context agents under the
`code-review` skill. The implementing agent aggregated their reports and
implemented corrections. No review agent edited the repository or tracker.

The committed comparison `git diff 875c140fc4617f8a9649f1c9bed8034c52bd6dfa...HEAD`
and `git log 875c140fc4617f8a9649f1c9bed8034c52bd6dfa..HEAD --oneline` are empty.
The actual review covers `git diff 875c140fc4617f8a9649f1c9bed8034c52bd6dfa --`
and every delivery path from `git ls-files --others --exclude-standard`.

The reviewed source/test snapshot is
`65aa626ea88b13972730d06d584b2830f8a0c1c642b3265e9f96b8e6b9d51ae1`.
Root verification and JDK 8/11/17/21 now each pass 973 tests with zero failures
and errors and three existing optional skips. Post-review external acceptance
passes all seven Unicode profiles; 10 generated records and 50 artifacts have
been refreshed. Both independent reviewers completed their final artifact-inclusive checks
with no required findings remaining.

## Standards

The completed independent Standards reviewer read the repository standards,
applicable ADRs and the skill's full smell baseline. Its final source report:

> No required Standards findings remain in the reviewed source. The GSUB and
> vertical-metric findings are resolved; Repeated Switches is resolved;
> Mysterious Name remains accepted and nonblocking.

| Finding | Resolution and observed verification |
| --- | --- |
| Delta-derived GSUB targets were not checked against the glyph domain. | Coverage now validates the modulo-65536 result. Public negative: 2 failures before the fix; 6 passing checks afterward, including positive Sans and supplementary JP. |
| Admitted vertical tables could declare a metric count inconsistent with the glyph count and table length. | Paired tables require the supported header, reserved fields, count and exact compact/full length. Public zero-count negative: 2 failures before the fix; 6 passing checks afterward, including all four regional fonts. |
| Evidence still reported 512 MiB after the producer budget changed. | Producer and recorder share `OWNED_MEMORY_BYTES`; a literal record assertion failed before the correction, then both acceptance tests passed. The combined transaction declares 2 GiB, supported by public peak observations. |
| Possible Repeated Switches in exact-pixel profile selection. | The description and recorder use `VisualProfile.requiresExactChangedPixels()`. |
| Possible Mysterious Name: `extended` / `isExtended()`. | Accepted nonblocking judgment call. The private flag distinguishes the documented static extension from the legacy ten-table profile. |

Preliminary documentation observations about ASCII-only overflow wording and
LF-only preferred table width were also corrected. A preliminary review agent
did not finish its report; it is not counted as the completed Standards review.

The final artifact-inclusive Standards determination is PASS. It covers all
30 tracked changes and 118 untracked delivery paths; all 10 refreshed records
and 50 artifacts match the final acceptance output exactly. All 35 raster
hashes, reference hashes, product identity and the 2 GiB metadata agree. Root,
four-JDK, external acceptance and final inventory log hashes/results were
independently verified, as were all 78 development-log hashes.

## Spec

The independent Spec reviewer inspected #29/#1 and all tracked/untracked
delivery changes. It reported two required P2 issues, then independently
reproduced their fixes through public Workflow and reopened results:

| Finding | Resolution and observed verification |
| --- | --- |
| Whole-line bidi reordering displaced physical tabs; anchors used logical offsets. | Fields retain physical declaration order. Their contents reorder by cluster, and the first logical anchor is positioned using its visual offset. Independent anchor and RTL-base probes both place the target at x=108. Public Red: 2 failures; Green: 16 checks. |
| REJECT/VISIBLE broke before an ASCII space without a Unicode line opportunity. | Complete Unicode line units include that space. For Noto `A B` at width 8, REJECT now fails atomically and VISIBLE preserves `A ` together. Public Red: 2 failures; Green: 8 checks. |

The final traceability check also corrected one required report wording issue:
the seven-profile positive creation/reopen test covers both execution modes,
while the independent reference positive and four oracle-negative controls run
in IN_PROCESS. The final report records that limit explicitly; no source change
was needed.

The reviewer's follow-up found no additional scope, shaping or resource-contract
issue. Its normal three-font version-1 public probe measured 540,656,281 bytes
in process and 584,571,171 in the default Worker. Both committed and reopened
`A骨` with the documented 1 GiB positive budget. The combined seven-profile
test separately measured 1,207,232,298 and 1,449,636,850 bytes with its 2 GiB
budget. These are modeled owned-memory observations, not JVM/RSS claims.

The final artifact-inclusive Spec determination is PASS. It covers the same
148 delivery paths, all 138 semantic rows, all 35 raster hashes and all final
log receipts. Independent pixel recounts reproduce primary zero differences
and secondary counts of 3520/1581/1897/3925/4092/4417/4229. The 29 criteria map
to the original contract, with no required omission or scope expansion.

[Development and focused verification receipts](artifacts/T28-development.txt)
retain the actual Red/Green log hashes and distinguish setup/selector failures
from behavioral failures. The original font sources, independent expectations
and fixed visual/geometry tolerances were unchanged by the review corrections.

Standards: 3 required findings resolved, 1 heuristic resolved and 1 accepted
nonblocking naming judgment; no required issue remains. Spec: 2 required P2
source findings and 1 required report wording correction resolved; no required
issue remains. Required build/acceptance gates and both final artifact-inclusive reviews have passed.

## Publication authorization follow-up

After the verified uncommitted delivery, the user authorized a DCO-signed
commit, a GitHub push and closure of issue #29 after the push. The only
follow-up document changes record that authorization and preserve the initial
review/audit state as historical evidence. Implementation, tests, reference
inputs, PDFs, PNGs and validation thresholds remain unchanged.
