# Bounded structure-content qualification

This increment continues #75 step 4. It is not final Foundation certification
or the final clean-context review, and no completion checkbox is marked.
The overall review baseline remains
`5b1603c435f11c40368f75b7b9a2777c9c5e9761`.

The first independently reviewed observer is
`observer-before-content-review.py`, SHA256
`1b73ff6d9a424973f15ffbfb89d7fa84e26d611782ccc52544779abb9da5660b`.
Standards performed 48 valid original CLI observations and Spec performed 53.
Their actual PDFs, qpdf graphs, commands, output and identity checks remain
in `standards-original.tar.xz` (460 files) and `spec-original.tar.xz` (501
files). The separate primary-source page-association assessment is retained
in an 8-file archive. `original-review-archives.json` identifies every member;
the retention recipe read back and compared each archived byte to its source.
The Standards review's helper import-shadowing error is retained as a harness
error and excluded from its valid observations.

The reviews exposed optional Form Type misclassification, missing whole OBJR
coverage of actual rendering pages, false repetition failures for alternative
appearances, and missed normal appearance roots whose only structural item
was a descendant. Each fix began with an actual public CLI RED and has a
GREEN log. The appearance-root first GREEN attempt exposed precedence of an
unqualified program over an already provably wrong StmOwn reference; the
second GREEN moved explicit ownership validation before program traversal.

The source interpretation also tightens two explicit relationship boundaries:
a valid unexecuted Form definition without an observed content/AP page
association is INDETERMINATE; whole annotation and internal appearance-item
overlap is INDETERMINATE. The old definition-only positive expectations are
retained in their historical logs; typed optional-Form positives now include
an actual AP association. Resource dictionary reachability is not required
as a universal page-association test. Reverse completeness of extra unused
ParentTree slots is outside the K-to-parent-slot predicate.

The new immutable observer is `observer-content-closure.py`, SHA256
`27377172a2552a30855ff216ad166d03c0cc9829a7ddbe339f206b020564a689`.
Its matching contract and structure-test snapshots are alongside it. All 26
structure tests pass in 54.650 seconds; all 60 shared program tests pass in
175.947 seconds with the qualified fontTools path. Standards first closure
completed 62 observations; Spec first closure completed 67 and found three
adjacent AP issues: the owner P backlink, hidden appearance visibility and
page font state masking an AP's unknown inherited font. Both first-closure
archives are retained with byte-verified member manifests.

Each subsequent correction has an actual RED/GREEN log. Normal appearance
test controls now use F=0; original F=2 records remain unchanged. The final
incremental observer is `observer-appearance-closure-r2.py`, SHA256
`b016df86f5cf2735aa272d539b721a288da42ba2edc00192db084c9cf7fce8e6`.
Both independent second closures report CLEAN: Standards has 31 new-snapshot
observations plus 4 old-snapshot RED comparisons; Spec has 31 observations.
Their archives retain 336 and 297 files respectively, every member read back
and compared with its original. A citation-only contract correction assigns
ISO1 Tables 164 and 165 to sections 12.5.2 and 12.5.3 respectively; the reviewed
snapshot retains the original typo, while the current contract is corrected.

The 29 structure and 60 shared program tests pass together in 233.083 seconds.
The authoring command now retains 23 original structure negatives, all of
which FAIL under their literal declared public rules. All 436 prior generated
files remained byte-identical. The combined program qualification profile
binds eight scopes, 42 rules and 165 negative PDFs; all 25 authoring tests
pass. These catalogs supply the pending program recorder. Declaration recorder
work and the final whole-task clean-context review remain separate increments.
