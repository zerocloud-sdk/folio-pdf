# Issue #77 fixed-baseline review

Baseline: `cd978a5cb311211c71050024d067f8d1d2dfacec`, initially clean and equal
 to HEAD. There are no new commits. Review used `git diff <baseline>` and
read the new untracked Java, Python, corpus-authoring and documentation files.
This worktree comparison replaces the skill's committed three-dot comparison
because the authorized result is reviewable local work.

## Standards

Two passes by a separate read-only reviewer found zero documented-standard
violations or material smell findings. The review checked CONTRIBUTING,
AGENTS, the domain vocabulary and applicable ADRs, Java 8/backend-free public
contracts, ownership, public-seam tests, clean-room provenance and separate
independent observers. Duplication between public Native/Facade observations
and between recording/collection serves distinct responsibilities.

The targeted second pass covered the signed empty-update guard, its public
regressions, and the six new inventory mappings (166 surfaces, 15 exclusions).
No Maven process was run by the reviewer.

## Spec

The separate read-only Spec reviewer found one P1 issue: an empty
`UpdateAnnotations` selection could set the mutation flag and manufacture a
signed incremental revision, contrary to AC3–4. A public pre-fix probe returned
`EMPTY_UPDATE_PUBLICATION=COMMITTED`. The bounded signature admission check
now rejects empty selections in both execution paths. The post-fix probe
returns `EMPTY_UPDATE_REFUSAL=SIGNATURE_POLICY_REJECTED`.

The reviewer rechecked the fix and passing public regressions: unchanged
Source/Path bytes, zero stream writes, ordered NOT_ATTEMPTED receipts and
stable safe failure identity. Both Native modes pass. No other implementation
finding or scope creep remained. The six Facade mappings, Source-version
inheritance, restriction matrix, independent revision/graph/raster checks and
collector rejection behavior were reviewed.

## Validation boundary

These reviews address implementation against the sole issue contract and
policy. Final staging, all required environment observations, refreshed prior
obligations and complete validation outcomes are recorded separately in the
execution receipt and Foundation index. This review alone is no certification.

## Final acceptance-entry regression

The first staged T15 certification attempt passed its 36 consumer/artifact
tests, then failed before launching the independent observer: the T15 Java
entry requested 900,000 ms from a shared runner whose maximum is 300,000 ms.
No certification index was published. The failed attempt is retained under
`T77-incremental-r1` and is not passing evidence.

A new `IndependentTools` test invokes the complete public entry point and
checks all four chains for all twelve products, actual modes and the retained
manifest digest. It reproduced the exact pre-launch guard failure before the
fix. The timeout now uses the existing 300,000 ms limit, matching T14. The
pinned Ubuntu/JDK 8 regression passed all three tests, including the complete
observer and controls, in about 100 seconds. No tool limit or threshold changed.

Both reviewers rechecked this two-file acceptance-only delta and found no
additional Standards or Spec finding. All four JDK gates and the independent-certification suite passed again for
that correction, followed by fresh staging and observations.

## Manifest/index integration regression

The r2 attempt completed all eight public contract suites and all mandatory
chains, but published no index: the existing transitive checker interpreted
a corpus-local `path`/`sha256` pair in `products.json` as a repository evidence
reference. A new public collect-through-merge regression reproduced the
rejection. The narrow fix uses `file` for local control filenames in the
author, recorder and collector; `merge_evidence` and `publish_index` remain
unchanged from baseline. No integrity check, rule or threshold was relaxed.

Fresh development products and observations requalified the pinned manifest
and rebuilt the explicitly non-certifying protocol fixture. All 34 Python
regressions pass, including changed/deleted inputs, products, controls and
configuration, unchanged index after rejection, and a resealed dangling
report reference. Both separate reviewers rechecked this final delta and
reported zero unresolved Standards or Spec findings. Fresh staging and
certification use the corrected final inputs; r2 remains historical.

## Prior-profile count and validation applicability

After the r3 incremental observations passed and entered the index, the first
transactions refresh executed all 34 required tests but exposed an unintended
runner-metadata change: its required count had become 36. The count is restored
to the baseline 34. Both reviewers independently confirmed that all seven prior
raw and resolved certification profiles now match the baseline exactly;
`incremental` is the sole new profile. The complete 34-test Python regression
selection passes again. No existing suite, rule or threshold was weakened.

Both reviewers accepted the retained broad Java results for this isolated
Python count correction after checking the actual rebuilt payloads. All 1,517
class/resource/test files exactly match the successful JDK 17 full-verification
trees; shared modules also match the independent-suite trees. The staged runtime,
acceptance and test JARs contain those exact payloads with no missing, unexpected
or duplicate entries. Additional archive entries are explicitly enumerated and
hashed manifests and Maven POM metadata. All 30 contract inputs and 698 of 699
harness inputs are unchanged; only the acceptance JAR archive hash differs.

See [entrywise proof](validation/validation-applicability-r4.json). This establishes
applicability to unchanged executable inputs; it does not claim identical archive
bytes or that Maven was rerun after the correction. Both Standards and Spec have
zero unresolved findings. Fresh r4 certification must still bind the actual
restaged artifact identities and exercise the corrected runner.

## Final evidence and readiness review

Fresh r4 certification completed for all eight selected obligations. Both
reviewers independently checked the final authoritative index: 64 execution
configurations and 256 passing chain records bind the same final candidate and
contract. Each obligation has exactly eight tuples. Incremental covers only the
four approved Ubuntu environments, with both Native modes and actual Facade
IN_PROCESS execution recorded separately.

The final live readiness log and generated report mark all eight selected
obligations satisfied. Remaining blockers concern excluded obligations; no
broader release or platform certification is claimed. Inventory validation,
generated-file checking, whitespace and the final workspace audit passed.
Tracked historical evidence remains unchanged. The final applicability proof
continues to distinguish executable payload equality from archive byte identity.

Final Standards/scope review: **zero findings**. Final Spec review: **zero
unresolved findings**. Both reviews were read-only. Their only remaining
housekeeping note was to finalize the receipt's pending labels; the
[completed receipt](receipt.md) now records every criterion and final outcome.
