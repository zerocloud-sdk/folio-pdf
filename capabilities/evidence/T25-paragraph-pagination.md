# T25 advanced paragraph pagination

Capability: `composition.layout.paragraph-pagination`

Acceptance Profile: `T25-paragraph-pagination`

Status: `experimental`

Release train: `0.1.0-SNAPSHOT`

Independent standards evidence, compatibility Dependency
Gates and Foundation font/platform certification remain open. A passing implementation
build is not a compatible or overall Foundation PASS claim.

## Contract and independent profiles

Issue [#26](https://github.com/zerocloud-sdk/folio-pdf/issues/26) extends T24 with
version 2 declarations, hard pagination constraints, finite search and retained
relayout/flush state. The [English contract](../../docs/paragraph-pagination.md)
is authoritative. Existing version 1 factories and behavior remain available;
there is no public source break. No table, Unicode layout, shaping or facade stub
is part of this change.

| Independent Acceptance Profile | Required observation |
| --- | --- |
| [Indentation](T25-paragraph-indentation.md) | Left/right/first-line offsets across columns and pages |
| [Tabs](T25-paragraph-tabs.md) | LEFT/CENTER/RIGHT/anchor/absent-anchor/default-grid positions on both pages |
| [Keep with next](T25-paragraph-keep-next.md) | Heading and following fragment stay together; impossible keeps fail |
| [Keep together](T25-paragraph-keep-together.md) | Whole paragraph moves; impossible whole-area fit fails |
| [Widows](T25-paragraph-widow.md) | At least two continuation lines; width-dependent recalculation |
| [Orphans](T25-paragraph-orphan.md) | At least two outgoing lines; all intermediate fragments obey both minima |
| [WRAP overflow](T25-paragraph-overflow-wrap.md) | Scalar fallback, complete text and finite page progression |
| [REJECT overflow](T25-paragraph-overflow-reject.md) | Intact word skips insufficient areas; no-fit boundary fails |
| [VISIBLE overflow](T25-paragraph-overflow-visible.md) | Full overlong ink/text retained; vertical flow stays bounded |
| [Relayout](T25-paragraph-relayout.md) | Changed breakpoints replace current pages; failures preserve prior content |
| [Immediate flush](T25-paragraph-immediate-flush.md) | Stable rejection without early publication |
| [Publication](T25-paragraph-publication.md) | Expired Session and reopened-output rejection |

The two-font corpus is project-authored Apache-2.0, not a Foundation Noto bundle:

- FolioPrimary SHA-256: `e2fbb634c3c0fe78efb449bde4426d10a93aa813596ff5cf3360f02ec97673fb`.
- FolioFallback SHA-256: `ced760bc126036779fa84ad3da4638733032512457bf599c44d8c455360f75e1`.

Every page is 612 x 792 points. Independent expectations fix every emitted
scalar, matrix, advance, baseline and page box at a 0.0001-point geometry
bound. The reference path uses T19 positioned text without paragraph layout.
Twenty-four page profiles pin 144 DPI, opaque white sRGB, raster hashes,
zero-fuzz ImageMagick AE 0 and the secondary-renderer bound of 2500. Exact
changed RGB pixel counts additionally enforce those same numerical bounds.
The oracle and thresholds were fixed before observing paragraph rasters.

## Reproduction and evidence

```text
./mvnw -B -ntp -pl pdf-acceptance -am -Dtest='*Paragraph*' -Dsurefire.failIfNoSpecifiedTests=false test
./scripts/acceptance /tmp/folio-t25/acceptance-final
./scripts/inventory validate
./scripts/inventory generate
./scripts/inventory check
./mvnw -B -ntp verify
./scripts/verify-jdk-matrix.sh
```

Only T25 records and artifacts from the independent acceptance output are
retained here; earlier ticket evidence remains unchanged. The hash policy
normalizes only the two trailer ID strings for evidence identity, while qpdf
and the renderers inspect the original PDF bytes. The tests also verify that
moved/missing content is rejected and missing tools stay INDETERMINATE.

After the reviewed resource-admission correction, the T25 recorder entry point
was rerun using a frozen copy of the final verified host runtime while the JDK
matrix rebuilt the workspace. All twelve syntax, semantic and visual profiles
passed again. The retained files are from that reviewed run; all 63 recorder
Markdown records are identical to the preceding complete recorder run, and
every PDFium raster still matches its fixed reference hash. The largest exact
secondary difference is 2096 pixels against the unchanged 2500 bound.

- [Aggregate syntax chain](T25-paragraph-pagination-syntax.md)
- [Aggregate semantic/structure chain](T25-paragraph-pagination-semantic.md)
- [Aggregate visual chain](T25-paragraph-pagination-visual.md)

## Comparator interpretation and retained reproduction

The first recording run matched all 24 independent references at ImageMagick
AE 0, but marked some secondary comparisons INDETERMINATE. Its retained
[raw reproduction](artifacts/T25-comparator-normalized-status.txt) contains
exit status 0 with AE `1.27843` and normalized distortion `6.59388e-07`.
The exact changed RGB pixel count for that pair is 262.

The [pinned ImageMagick 7.1.2-30 command](https://github.com/ImageMagick/ImageMagick/blob/7.1.2-30/MagickWand/compare.c)
uses a normalized distortion cutoff of 1e-6 for its status while scaling the
printed AE by image area. The acceptance adapter now applies that status
contract and still compares the original measured AE with the unchanged
profile thresholds. T25 additionally checks exact changed RGB pixels against
the same bounds, because this tool's AE is a magnitude rather than an integer
count. The reference images, DPI, fuzz, primary zero threshold and secondary
2500 bound were not changed. Two acceptance regressions protect both a
nonzero-primary rejection and a usable-small-secondary result.

## Implementation baseline and verification

Execution began on a clean `main` worktree at
`3a3b9feae4b5069cb77969f5e9e75fe400895149`. Issue #26 had no comments and its
sole native prerequisite #25 was closed. A fresh baseline full verify passed
on 2026-09-05; its complete log and test reports were retained under
`/tmp/folio-t25/baseline-verify.log` and `/tmp/folio-t25/baseline-reports`.
The historical CI run
[33951171715](https://github.com/zerocloud-sdk/folio-pdf/actions/runs/33951171715)
reported a rendering failure on all JDKs and two Worker timeout-code failures
on JDK 21. They did not reproduce in that local baseline. No unrelated fix or
failure suppression is included.

Final `./mvnw -B -ntp verify` passed on the reviewed code on 2026-09-05 at
16:23:10 +08:00. `./scripts/verify-jdk-matrix.sh` then passed on JDK 8, 11, 17
and 21, finishing at 08:45:26 UTC. Each final reactor recorded 792 cases with
zero failures or errors and the same three opt-in scale-profile skips as the
fresh baseline. Every run includes 64 T25 cases (32 per execution profile),
42 T24 cases (21 per execution profile), and the affected font, Canvas,
permission, lifecycle, resource and Worker contracts. No paragraph case skipped.
The historical rendering and timeout failures also passed in these final runs.

The [verification receipt](artifacts/T25-verification.txt) records exact runtime
image identities, completed gate totals and timestamps, full local log hashes,
the independent evidence audit and review dispositions. T25 checks caught and
resolved an omitted Worker inventory SHA update and an obsolete boundary test
that treated the new outcome token as unknown. The reviewed resource-admission
correction and comparator reproduction are recorded separately below and above.

Implementation verification and review completed before submission. The user
subsequently authorized a DCO-signed commit, a GitHub push and closure of #26.
The fixed point and verification receipts describe the reviewed worktree before
that submission.

Submission preparation removed a trailing separator space from the empty-page
semantic observation. The existing observer tests and all twelve T25 evidence
profiles passed again. All 63 recorder Markdown records and the fixed raster
identities remain unchanged; the retained artifacts include this report-format
correction. The verification receipt records the follow-up commands and hashes.


## Standards

The parallel Standards reviewer checked the tracked diff and all relevant new
files against fixed point `3a3b9feae4b5069cb77969f5e9e75fe400895149`, using
AGENTS.md, CONTRIBUTING.md, CONTEXT.md, the relevant ADRs and the code-review
skill's smell baseline. No commit was created for review.

One resource-admission finding was corrected: relayout now scans the immutable
replacement page declarations and reserves their modeled footprint before
constructing replacement lists. Both copy loops checkpoint. The public
`excessiveRelayoutDeclarationsPreserveContentAndConsumeTheAttempt` case passes
in IN_PROCESS and HARDENED_WORKER. The reviewer rechecked the fix and found no
remaining documented-standard violation.

The sole nonblocking judgement is possible Data Clumps in the five coupled
buffered-state fields. Grouping them in a retained-flow object could simplify
future ownership changes; no current lifecycle defect was identified and no
refactor is required for this ticket.

## Spec

The independent Spec reviewer checked issue #26, parent #1 and the supplied
goal, then rechecked the resource-admission correction. No missing, partial,
incorrect or out-of-scope implementation requiring a change remains. The review
confirmed joint pagination constraints, width recalculation, retained font
selection, failure preservation, query barriers, flush/publication boundaries
and the independent numeric/positioned-text oracle. The separate build and
evidence receipts remain required gates; review does not replace them.

Review totals: Standards has 0 unresolved required findings and 1 nonblocking
Data Clumps judgement; Spec has 0 findings.
