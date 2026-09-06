# T29 delivery and verification receipt

## Publication follow-up — 2026-09-07

The maintainer subsequently authorized a signed-off commit, push to GitHub, repair
of the failing GitHub Actions run, and closure of #30 after CI verification.
This supersedes the earlier uncommitted-delivery restriction. The original
receipt below records the pre-commit observation; its baseline, source hashes,
test results and independent reviews remain historical evidence for those
exact inputs.

The [failed baseline CI run](https://github.com/zerocloud-sdk/folio-pdf/actions/runs/34022316650)
failed only on JDK 8, in
`T28UnicodeEvidenceCommandTest.sevenProfilesMatchTheIndependentGlyphGeometryInBothExecutionModes`,
with `The Worker could not complete the transaction.` JDK 11, 17 and 21 passed.
The failure occurred during acceptance tests after compilation. The delivery
includes the [font snapshot repair and public regression](artifacts/T29-font-cache-repair.json)
that addressed a locally reproduced failure at the same seam. Local diagnosis
found duplicate retained font snapshots contributing to a Worker heap failure;
the GitHub log alone does not establish that internal cause. The repaired
source passes the complete [root verification](artifacts/T29-verified-root-verification.json)
and [four-JDK matrix](artifacts/T29-verified-jdk-matrix.json).

Staging the formerly untracked evidence exposed whitespace in raw tool output
and unified-diff context lines. The publication delta adds narrowly scoped
Git attributes that preserve those bytes and exempt only their end-of-line
and end-of-file whitespace. Their hashes remain unchanged; source and prose
retain the ordinary whitespace checks. This Git metadata change and this
follow-up text postdate the frozen local-validation snapshot. No compiled
source, test, native installer or frozen reference input changed.

The pushed commit must also pass the repository's actual GitHub Actions
matrix. Its final run and commit links are to be recorded in the
[issue closure comment](https://github.com/zerocloud-sdk/folio-pdf/issues/30).
Closing the ticket at the maintainer's direction does not supply the missing
Windows x86-64, macOS x86-64 or macOS arm64 execution evidence. Those platform
results remain INDETERMINATE and the Capability Matrix remains experimental.

## Original pre-commit observation

Overall result: **INDETERMINATE; #30 is unfinished.** The working tree contains
uncommitted T29 delivery changes. Actual Windows x86-64, macOS x86-64 and
macOS arm64 evidence is missing. Linux observations cannot certify those
platforms. Current-source Linux acceptance, full root verification and all
four JDK gates pass. Missing platform evidence prevents completion.

The fixed review baseline and current HEAD are
`dd4cda28c24a304258e75f0854d86852275f5320`. The review includes
`git diff dd4cda28c24a304258e75f0854d86852275f5320 --` and relevant untracked
files. The committed range is empty and is not the review input. No commit,
push, PR, merge, tracker mutation, issue closure or artifact publication was
performed or authorized.

## Delivered behavior

The project-owned C adapter invokes an explicitly installed HarfBuzz 10.2.0
engine through the existing external Provider boundary. Composition uses
detached glyph IDs, input clusters, advances and offsets for Arabic, Hebrew,
Devanagari and Thai, reshapes candidates at original grapheme line boundaries,
and publishes the required glyphs in actual embedded subsets. Explicit font
selection, fallback, ICU segmentation/bidi, resource ownership and Workflow
publication remain within their existing boundaries. Direct positioned
Unicode text retains its unshaped behavior.

Both supported Workflow modes use the same shaping contract. Native calls are
brokered by the parent process and are not fully isolated by the PDF Worker.
The [English contract](../../docs/harfbuzz-shaping.md) and
[Chinese guide](../../docs/zh-CN/getting-started.md) document this scope,
required PDF version, limits and stable failures.

## Per-profile completion evidence

Each cell below is the actual **Linux x86-64** observation for the frozen
two-page profile. It does not close the corresponding cross-platform
requirement. The [reference profile](../profiles/T29-shaping-reference.md)
fixes exact font-unit results, 0.0001-point geometry tolerance, eight pages,
144-DPI opaque-white RGB output, primary zero changed pixels and secondary
at most 3000 changed pixels before product comparison.

| Required observation | Arabic | Hebrew | Devanagari | Thai | Evidence |
| --- | --- | --- | --- | --- | --- |
| Glyph IDs match independent expectations | PASS | PASS | PASS | PASS | [Native](T29-shaping-native.md), [reopened semantics](T29-shaping-semantic.md) |
| Clusters and input mappings match | PASS | PASS | PASS | PASS | [Native](T29-shaping-native.md), [reopened semantics](T29-shaping-semantic.md) |
| Advances meet fixed tolerance | PASS | PASS | PASS | PASS | [Native](T29-shaping-native.md), [reopened geometry](T29-shaping-semantic.md) |
| Offsets meet fixed tolerance | PASS | PASS | PASS | PASS | [Native](T29-shaping-native.md), [reopened geometry](T29-shaping-semantic.md) |
| Direction matches | PASS | PASS | PASS | PASS | [Native](T29-shaping-native.md), [reopened semantics](T29-shaping-semantic.md) |
| Ligature behavior matches the profile | PASS | PASS | PASS | PASS | [Native](T29-shaping-native.md), [fixed corpus](artifacts/T29-corpus.json) |
| Reordering matches the profile | PASS | PASS | PASS | PASS | [Native](T29-shaping-native.md), [reopened semantics](T29-shaping-semantic.md) |
| Combining marks match the profile | PASS | PASS | PASS | PASS | [Reopened geometry](T29-shaping-semantic.md), [rasters](T29-shaping-visual.md) |
| Explicit fallback selection and output match | PASS | PASS | PASS | PASS | [Reopened semantics](T29-shaping-semantic.md), [actual subsets](T29-shaping-subsets.md) |
| Reopened subsets contain required shaped glyphs | PASS | PASS | PASS | PASS | [Independent subset verification](T29-shaping-subsets.md) |
| Pagination, geometry and independent rendering meet fixed bounds | PASS | PASS | PASS | PASS | [Reopened geometry](T29-shaping-semantic.md), [all eight pages in both modes](T29-shaping-visual.md) |

Native observations contain 99 exact glyph matches; reopened semantics contain
198 across both modes. All 16 primary page comparisons have zero changed RGB
pixels. Secondary maxima are 1437, within the original 3000-pixel limit. Both
products have ID-neutral SHA-256
`a6c1522148d25ec716119791bdc682b9c3b7682b0b9ebfcbbc69a8823964318c`
under the exact trailer-ID normalization recorded in the run manifest.
All eight IN_PROCESS PDFium pages were visually inspected for layout, missing
content and clipping in the prior run. Every current PNG is byte-identical
to that inspected version; both current PDFs match after ID normalization. The [Linux manifest](artifacts/T29-linux-run.json)
preserves the exact command, environment, built artifacts and hashes of all
136 archived run files.

## Platform and dependency requirements

| Requirement | Determination and evidence |
| --- | --- |
| Linux x86-64 fixed-version execution | Six chains PASS: [native](T29-shaping-native.md), [installation/live engine](T29-shaping-installation.md), [semantics](T29-shaping-semantic.md), [syntax](T29-shaping-syntax.md), [subsets](T29-shaping-subsets.md), [visual](T29-shaping-visual.md). Full root and all four JDK gates pass. |
| Windows x86-64 fixed-version execution | INDETERMINATE: no actual host/runner or execution receipt. |
| macOS x86-64 fixed-version execution | INDETERMINATE: no actual host/runner or execution receipt. |
| macOS arm64 fixed-version execution | INDETERMINATE: no actual host/runner or execution receipt. |
| Project-owned adapter; no unofficial wrapper | C source and Java Provider are project-authored; [provenance](../../PROVENANCE.md), [dependency declarations](../../DEPENDENCIES.md), [five current runtime JAR inspections](artifacts/T29-verified-runtime-jar-inspection.json). |
| Explicit HarfBuzz installation; no default native bundle | [Offline installer and dependency record](../../docs/third-party/harfbuzz-10.2.0.md), actual [installation receipt](artifacts/T29-linux-native-installation.json) and [current JAR inspection](artifacts/T29-verified-runtime-jar-inspection.json). |
| Versions, sources, hashes and licenses | [Native/tool record](../../docs/third-party/harfbuzz-10.2.0.md), [Noto font manifest](../../pdf-acceptance/src/main/resources/net/zerocloud/pdf/acceptance/fonts/noto/README.md), [independent reference receipt](artifacts/T29-reference-receipt.json), PROVENANCE and DEPENDENCIES. |

The [current availability observation](artifacts/T29-verified-platform-availability.json)
records the accessible Linux environment, no supplied non-Linux endpoint and
zero repository self-hosted GitHub runners. It does not imply that all GitHub
hosted runner types are unavailable. Existing CI targets Ubuntu. The live
loaded-engine observer currently implements Linux only; an installation
recipe or successful Linux build does not supply another platform's evidence.
Non-Linux native and IN_PROCESS evidence remain required even though the
existing HARDENED_WORKER OS envelope applies only to Linux.

## Behavior, delivery and review requirements

| Requirement | Determination and evidence |
| --- | --- |
| Public Workflow/reopened-result tests; real external Provider only | Final focused ShapingCompositionWorkflowTest: 34 cases, zero failures/errors/skips; native adapter tests cross the real external Provider. [Development observations](artifacts/T29-development.txt), [current full root log](artifacts/T29-verified-root-verify.txt). |
| Same supported IN_PROCESS/HARDENED_WORKER contract | The current 34-case shaping suite, Linux semantic/subset/visual chains and all four complete JDK gates cover both supported modes. |
| Stable missing/mismatched engine, input and limit failures; ownership/no publication | Public regressions cover sentinels, borrowed font lifetime, selected Provider limits, PDF 1.5 admission, exact fallback checks and relayout reservations. Current focused and full root suites pass. |
| Negative controls detect clusters/direction/offset/fallback/missing glyph errors | Real native observations, public Workflow mutations and independent reopened subset assertions reject the declared negative artifacts. No private backend identity assertions provide product proof. |
| Focused and affected font/Unicode/paragraph/table regressions | Current focused font/shaping/T28 suite has 58 passes; complete root verify passes all affected classes. |
| Full root verify | PASS on current source: 1019 tests, zero failures/errors, three pre-existing optional scale skips; [receipt](artifacts/T29-verified-root-verification.json). |
| JDK 8/11/17/21 matrix | [PASS on all four current-source JDKs](artifacts/T29-verified-jdk-matrix.json): 1019 tests, zero failures/errors and three existing scale skips each. Earlier JDK 8 failures and the [diagnosis/public repair regressions](artifacts/T29-font-cache-repair.json) remain archived. |
| All mandatory T29 evidence; missing tools/platforms remain INDETERMINATE | INDETERMINATE because three required platforms lack actual evidence. No platform or profile was dropped. |
| Capability Matrix reflects evidence and dependency gates | [Authority](../capability-matrix.yaml) retains experimental status, no certified platforms and explicit compatible dependency/promotion gates; actual local syntax/semantic/visual PASS records are linked. |
| Facade mapping or explicit exclusion; no stable stub | [Facade authority](../facade-surface.yaml) excludes shaping because the existing Preview layout.Document surface has no approved shaping mapping. |
| English contract, Javadoc, migration and Chinese guide | Contracts document explicit selection, unshaped direct positioning, parent brokerage, limits, fallback and PDF version. [Javadoc delta](artifacts/T29-final-javadoc-delta.json) records strict validation with zero warnings. |
| Provenance/dependencies/evidence correspond to artifacts | Preserved source and run manifests link actual bytes; current source and historical runs are distinguished below. |
| Inventory check; generated documents do not drift | [Current PASS](artifacts/T29-verified-inventory-check.json): 21 capabilities, 12 facade surfaces, 20 exclusions. [Workspace and link checks](artifacts/T29-verified-workspace-check.json) pass. |
| Standards review has no unresolved mandatory finding | [Independent clean-context Standards review](artifacts/T29-standards-review.md) has no remaining mandatory source or archival finding. |
| Spec review covers every #30 criterion and has no unresolved mandatory finding | [Independent Spec coverage map](artifacts/T29-spec-review.md) covers every criterion. UNMET: the three required non-Linux platform receipts remain absent. Source findings are closed; there is no whole-issue signoff. |
| git diff --check | [PASS against the fixed baseline](artifacts/T29-verified-workspace-check.json). |
| T29-only final workspace; no task temporary files | [Verified T29 delivery scope](artifacts/T29-verified-workspace-check.json); HEAD remains the fixed baseline and the index is empty. Diagnostic runs, clones and the heap dump are outside the worktree; normal build outputs are ignored. |
| Per-criterion report, actual commands, platforms and commit authority | This report records each requirement and authorization boundary. No completion checkbox is marked. |

The three initial mandatory source findings were repaired with public regressions in
Red/Green/Refactor order: admission of shaped ActualText requires PDF 1.5;
initial shaping reservations follow layout/emission lifetime during buffered
relayout; fallback budgets count each actual scalar coverage probe. Failed
intermediate attempts remain failures in the development record. Frozen
profiles, tolerances and default resource limits were not relaxed.

## Current validation commands

The current root gate passed in the original worktree in 16:38 minutes:

```sh
FOLIO_HARFBUZZ_HELPER=/home/ubuntu/IdeaProjects/open-pdf/.build-cache/harfbuzz/10.2.0-final/bin/folio-harfbuzz \
./mvnw -B -ntp verify
```

The current Linux acceptance recorder passed in 1:25 minutes; this separate
command skips tests and is not the root test gate:

```sh
FOLIO_HARFBUZZ_HELPER=/home/ubuntu/IdeaProjects/open-pdf/.build-cache/harfbuzz/10.2.0-final/bin/folio-harfbuzz \
FOLIO_SHAPING_PYTHON=/usr/bin/python3 PYTHONPATH=/tmp/folio-t28/python \
./mvnw -B -ntp -pl pdf-acceptance -am -Pacceptance-t29-record \
-DskipTests -Dacceptance.output=/tmp/folio-t29/evidence-linux-font-cache-repair verify
```

The existing matrix script runs in two independent verification clones,
respectively /tmp/folio-t29/matrix-font-cache-8-11 and
/tmp/folio-t29/matrix-font-cache-17-21:

```sh
FOLIO_HARFBUZZ_HELPER=/home/ubuntu/IdeaProjects/open-pdf/.build-cache/harfbuzz/10.2.0-final/bin/folio-harfbuzz \
./scripts/verify-jdk-matrix.sh 8 11
```

```sh
FOLIO_HARFBUZZ_HELPER=/home/ubuntu/IdeaProjects/open-pdf/.build-cache/harfbuzz/10.2.0-final/bin/folio-harfbuzz \
./scripts/verify-jdk-matrix.sh 17
```

Current JDK 8, 11, 17 and 21 gates passed in 18:16, 18:08, 17:28 and
14:16 minutes. All three enclosing scripts exited 0. JDK 21 ran in
/tmp/folio-t29/matrix-font-cache-8-11 after the 8/11 command ended:

```sh
FOLIO_HARFBUZZ_HELPER=/home/ubuntu/IdeaProjects/open-pdf/.build-cache/harfbuzz/10.2.0-final/bin/folio-harfbuzz \
./scripts/verify-jdk-matrix.sh 21
```

The [matrix receipt](artifacts/T29-verified-jdk-matrix.json) records actual
Temurin versions 1.8.0_502-b07, 11.0.32+9, 17.0.20+8 and 21.0.12+8-LTS,
image identities unchanged before/after verification, exact logs and source
hashes for the root worktree and both clones.

Historical JDK 11, 17 and 21 passes refer to the earlier source snapshot. After the older 8/11 attempts
stopped on JDK 8, the older JDK 11 pass was obtained separately with
`./scripts/verify-jdk-matrix.sh 11`; those historical logs are retained.

`./scripts/inventory generate` and `./scripts/inventory check` both passed.
The final Javadoc command and successful recompilation are recorded exactly
in the [comment delta receipt](artifacts/T29-final-javadoc-delta.json).

The [first JDK 8 full attempt](artifacts/T29-final-jdk8-first-attempt.txt)
failed `T28UnicodeEvidenceCommandTest.sevenProfilesMatchTheIndependentGlyphGeometryInBothExecutionModes`
with a Worker transaction error. Its [failure XML](artifacts/T29-final-jdk8-t28-failure.xml)
is retained. A [same-JDK isolated rerun](artifacts/T29-final-jdk8-t28-reproduction.txt)
passed both modes without changing product code or resource defaults. The
pass is not represented as a fix or a complete matrix pass. The
[second complete attempt](artifacts/T29-final-jdk8-second-attempt.txt)
again failed the same test at 2026-09-06T15:32:01Z and stopped before JDK 11.
Its [failure XML](artifacts/T29-final-jdk8-t28-second-failure.xml) is preserved.
A narrowed acceptance-suite reproduction in a separate disposable clone
captured `OutOfMemoryError: Java heap space` in FontBox while loading a complete
CJK glyph table through the unshaped PDType0Font path. A separately instrumented
fixed-baseline acceptance suite passed once; this does not establish whether
T29 causes the intermittent failure. Heap observations identified duplicate
private font snapshots retained for distinct borrowed declarations. A
canonicalization prototype passed the same 41-case acceptance loop. The
[repair record](artifacts/T29-font-cache-repair.json) preserves the failed
liveness hypothesis, diagnostic logs, font-array hashes and public
Red/Green/Refactor regressions. The final repair also accounts for each
retained declaration. Its focused 58-case suite passes and covers both
supported modes. Complete current-source root and all four JDK gates now
pass, including the original full-suite T28 Worker trigger. Diagnostic-only
instrumentation is confined to disposable clones; heap and resource defaults
remain unchanged.

## Current source and historical verification

The [current snapshot](artifacts/T29-verified-source-snapshot.json) contains
663 compilation/reference inputs with canonical SHA-256
`94b628481751378665a40a3b033430b1224ec0adc817f811386bc44db59ef24f`.
It includes the getter documentation, private font snapshot sharing,
per-declaration accounting and the public regression. The
[fresh clone receipt](artifacts/T29-verified-clone-snapshots.json) records
1609 byte-identical delivered files at creation. Later narrative and evidence
archival are outside the frozen product/reference input scope.

The [earlier snapshot](artifacts/T29-final-source-snapshot.json) contains 663
compilation/reference inputs with canonical SHA-256
`39b761992f1677fe25ee7c4d5796c1505a024a890880d51aca95ed516024565f`.
Earlier Linux acceptance and root verification refer to this snapshot. The
earlier matrix clones also used it. Their [creation receipt](artifacts/T29-final-verification-snapshots.json)
records the historical equality of all 1567 delivered files at creation;
later narrative, evidence and Javadoc updates do not belong to that claim.

An intermediate documentation-only revision changed 17 getter Javadocs in
ShapingRequest.java and ShapingResult.java. The [exact patch](artifacts/T29-final-getter-javadoc.patch)
and [before/after identity receipt](artifacts/T29-final-javadoc-delta.json)
show that source outside Javadoc comments is byte-identical. Strict Javadoc
passes with zero warnings. After recompilation, all 32 Provider/conversion
class entries are present: 29 are byte-identical, three differ only in debug
LineNumberTable entries. Complete javap views retain and match constant pools,
instructions, signatures and other metadata; the [raw observations](artifacts/T29-final-javadoc-class-observations.json)
are retained. Full class-byte or JAR-byte identity is not claimed for the
changed debug metadata. That intermediate 663-input source hash was
`7d640b293ce2e3bc534d91ece203ac9f9203493377a6cddc594a9469dc891a16`.

The subsequent font snapshot and test changes are covered by the new
complete root, four-JDK and Linux acceptance gates above.
The [historical Linux archive](artifacts/T29-linux-before-font-cache-repair.json)
preserves all 136 original run files, source manifest and run manifest with
their original hashes. Historical passes do not establish a current-source
pass. None of these records supplies absent platform evidence or
authorize completion, submission or publication.
