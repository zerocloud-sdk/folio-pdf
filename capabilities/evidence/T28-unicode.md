# T28 Unicode Composition delivery record

Status: `experimental`
Capability: `composition.layout.paragraph-areas`
Acceptance Profile: `T28-unicode`
Release train: `0.1.0-SNAPSHOT`

Issue [#29](https://github.com/zerocloud-sdk/folio-pdf/issues/29), parent
[#1](https://github.com/zerocloud-sdk/folio-pdf/issues/1). Fixed review base:
`875c140fc4617f8a9649f1c9bed8034c52bd6dfa`. The initial delivery was verified
and independently reviewed as an uncommitted working tree. The user
subsequently authorized a DCO-signed commit, pushing it to GitHub, and closing
issue #29 after the push.

All 29 completion criteria are satisfied within the scope of #29. Required
build/acceptance gates, the final delivery audit, and independent Standards
and Spec reviews have passed. Capability status remains experimental.

The [verification receipt](artifacts/T28-verification.txt) records exact commands,
log hashes, per-class test results, environment and the final source snapshot.
The [development receipt](artifacts/T28-development.txt) preserves Red/Green
observations, including setup failures that do not count as behavioral Reds.
The independent [Standards and Spec reports](T28-code-review.md) retain the
findings and their resolutions separately.

The [English contract](../../docs/unicode-composition.md) and
[Chinese guide](../../docs/zh-CN/getting-started.md) describe internal ICU4J
77.1 grapheme/word/line/script/bidi processing, explicit static fonts and the
separate shaping boundary. The existing public paragraph/table command
versions remain the interface; text queries observe visual painting order.

## Independent reference profiles

The [predeclared reference](../profiles/T28-unicode-reference.md) fixes all
inputs, manual lines, fonts and tolerances before producer comparisons.
The [offline raw PDF writer](../../scripts/t28-unicode-reference.py) uses
fontTools metrics and manually positioned glyphs without ICU or Folio layout.
The complete static [Noto test data](../../pdf-acceptance/src/main/resources/net/zerocloud/pdf/acceptance/fonts/noto/README.md)
retains versions, original/final SHA-256 values, source URLs and OFL notices.

| Profile | Independent coverage |
| --- | --- |
| Latin | Space-plus-combining cluster, hyphen opportunities, mixed LTR/RTL, digits, punctuation mirroring and mixed-script explicit fallback |
| Greek | Greek text and combining sequence |
| Cyrillic | Cyrillic text, punctuation and mixed word boundaries |
| CJK SC | Explicit SC before other regional candidates; Chinese punctuation and simplified native text |
| CJK TC | Explicit TC; punctuation and traditional native text |
| CJK JP | Explicit JP; punctuation, Han, hiragana and katakana |
| CJK KR | Explicit KR; punctuation, precomposed Hangul and indivisible unshaped Jamo clusters |

All seven pages require exact scalar order, original source glyph IDs,
embedded subset names, page boxes and matrices/advances/baselines within
0.0001 point. Each has a pinned 144-DPI opaque-white sRGB PDFium/ImageMagick
profile: primary AE and changed pixels zero, secondary changed pixels at most
12000. Syntax is a separate qpdf observation. Wrong clusters, direction,
regional programs and one-point shifts are rejected by public reopened
observations. Missing tools remain INDETERMINATE.

Final post-review observations (all seven semantic and geometry profiles PASS):

| Profile | Primary AE | Primary changed RGB pixels | Secondary changed RGB pixels (limit 12000) |
| --- | --- | --- | --- |
| latin | 0 | 0 | 3520 |
| greek | 0 | 0 | 1581 |
| cyrillic | 0 | 0 | 1897 |
| cjk-sc | 0 | 0 | 3925 |
| cjk-tc | 0 | 0 | 4092 |
| cjk-jp | 0 | 0 | 4417 |
| cjk-kr | 0 | 0 | 4229 |

## Reproduction commands and observed results

Run from the repository root. Full logs remain at the absolute paths recorded
in the verification receipt; their SHA-256 values and result excerpts are
retained in the repository. The root build and matrix include the new public
Unicode cases, acceptance contract tests, existing regressions, public API and
JAR checks. The external acceptance command runs the complete existing evidence
chain and T28 against a new output directory.

| ID | Actual command | Final observation |
| --- | --- | --- |
| V | `./mvnw -B -ntp verify` | 973 tests, 0 failures, 0 errors, 3 existing optional skips |
| J | `./scripts/verify-jdk-matrix.sh` | JDK 8/11/17/21 each pass: 973 tests, 0 failures, 0 errors, 3 existing optional skips |
| I | `./scripts/inventory validate`, then `./scripts/inventory generate`, then `./scripts/inventory check` | 20 capabilities, 12 facade surfaces, 19 exclusions; generated documentation current |
| U | `./mvnw -B -ntp -pl pdf-acceptance -am -Dtest=T28UnicodeEvidenceCommandTest -Dsurefire.failIfNoSpecifiedTests=false test` | 2 tests pass; seven-page positive creation/reopen in both execution modes; four effective IN_PROCESS oracle-negative controls; missing-tool records remain INDETERMINATE |
| P | `T28_UPSTREAM=/tmp/folio-t28/upstream PYTHONPATH=/tmp/folio-t28/python python3 -m unittest discover -s scripts/tests -v` | All 3 offline generation tests pass with pinned fontTools and explicit upstream inputs |
| A | `./scripts/acceptance /tmp/folio-t28/acceptance-final` | Complete evidence command succeeds; T28 syntax, semantic/geometry and all seven visual profiles PASS |
| W | `git diff --check 875c140fc4617f8a9649f1c9bed8034c52bd6dfa` plus tracked/untracked scope, whitespace and local-link audit | PASS; 30 tracked changes and 118 untracked T28 delivery paths, no whitespace errors or broken local links |

Only the existing `HardenedWorkerScaleProfileTest` contributes the three skips.
No T28 case is skipped. The 24 new Unicode test methods execute once per mode,
for 48 passing public contracts in each complete repository run. The initial
48-case focused log later failed in the acceptance module because its old
1 GiB combined budget was insufficient; it is not presented as a passing
combined build. Command U and the final complete builds supersede that failure
with the measured and documented 2 GiB combined policy.

## Original completion criteria

The numbering and scope below match the user's 29-item execution contract.
Each result is limited to the explicit fixtures, supported execution environment
and experimental capability status; no row grants broader compatibility.
Command IDs resolve to the actual commands above and the verification receipt.

| # | Result | Criterion and evidence |
| --- | --- | --- |
| 1 | PASS | ICU4J 77.1 performs grapheme, word, line, script and bidi processing. Commands V/J; [contract](../../docs/unicode-composition.md), dependency pin and Worker full-JAR admission; no unused placeholder integration. |
| 2 | PASS | Mixed LTR/RTL, digits, isolates, mirrored punctuation and physical tab anchors match the fixed expectations. Commands V/J/U; `mixedBidiRunsKeepNumbersAndPunctuationInTheReferenceOrder`, `rightToLeftBaseMirrorsPairedPunctuationWithoutReversingDigits`, tab tests and the independent Spec probes. |
| 3 | PASS | Space-plus-combining sequences across inline boundaries and Jamo clusters stay intact through wrapping and justification. Commands V/J/U; `combiningClusterAcrossInlinesCannotSplitAtItsSpace`, Jamo and table-cluster tests. |
| 4 | PASS | Word/script runs, hyphen and Unicode line opportunities, hard separators, and REJECT/VISIBLE line units produce the declared results. Commands V/J; line/word/script, nonwrapping overflow and preferred-table-width public tests. |
| 5 | PASS | SC/TC/JP/KR are selected by explicit ordered fallback. Commands V/J/U; `completeCjkFontsUseTheExplicitRegionAndPreserveSubsetMappings` and all four regional evidence pages inspect the chosen embedded program and original source GIDs. |
| 6 | PASS | Complete static Noto Sans 2.008, Hebrew 3.000 and CJK 2.004 SC/TC/JP/KR load through the actual public path. Commands V/J/P; [font manifest and source/OFL records](../../pdf-acceptance/src/main/resources/net/zerocloud/pdf/acceptance/fonts/noto/README.md). Each CJK instance retains all 65,535 glyphs. |
| 7 | PASS | Every Latin, Greek, Cyrillic and CJK SC/TC/JP/KR semantic profile passes. Commands U/A; [semantic evidence](T28-unicode-semantic.md) and per-glyph raw findings. |
| 8 | PASS | All seven profiles pass source-metric matrices, positions, advances and baselines at the predeclared 0.0001-point tolerance. Commands U/A; the independent TSV/raw PDF and [semantic/geometry findings](artifacts/T28-unicode-semantic.txt). |
| 9 | PASS | All seven independent PDFium/ImageMagick profiles pass primary AE 0, exact changed RGB pixels 0 and secondary changed pixels at most 12000. Command A; [visual aggregate](T28-unicode-visual.md) and seven page records/rasters. |
| 10 | PASS | Published seven-page product passes pinned qpdf 12.4.0 syntax inspection. Command A; [syntax evidence](T28-unicode-syntax.md) and [raw invocation/output](artifacts/T28-unicode-qpdf.txt). This is syntax evidence, not complete standards certification. |
| 11 | PASS | Public negative controls detect a separated combining mark, swapped Hebrew glyphs, wrong SC/TC font program and a one-point displacement. Commands U/V/J; `T28UnicodeEvidenceCommandTest` accepts the independent positive reference and rejects all four changed products in IN_PROCESS; positive seven-profile creation/reopen also passes in HARDENED_WORKER. |
| 12 | PASS | ICU types are excluded from public and protected interfaces, including wildcard/generic leakage. Commands V/J; `PublicApiLeakageIT` includes a negative guard probe, and `JarContractIT` verifies the dependency/artifact contract. |
| 13 | PASS | Documentation distinguishes segmentation and bidi from shaping, including unshaped marks/Jamo and omitted GSUB/GPOS behavior. [English contract](../../docs/unicode-composition.md), [Chinese guide](../../docs/zh-CN/getting-started.md) and independent Spec review. |
| 14 | PASS | New behavior is observed through `DocumentWorkflow.execute`, public commands/queries and committed output reopened in a fresh Workflow. Commands V/J/U; [Unicode public tests](../../pdf-document/src/test/java/net/zerocloud/pdf/consumer/UnicodeCompositionWorkflowTest.java) and [acceptance public tests](../../pdf-acceptance/src/test/java/net/zerocloud/pdf/acceptance/T28UnicodeEvidenceCommandTest.java). Expected layout is not read from backend internals. |
| 15 | PASS | New public contracts pass in IN_PROCESS and HARDENED_WORKER on the tested Linux environment. Commands V/J/U; individual cases keep default Worker settings, while the six-font combined transaction explicitly declares its finite larger heap/memory policy. |
| 16 | PASS | Scalar and full-font memory limits, invalid scalars/controls, GSUB references/versions/delta targets, vertical-table counts and failure-before-publication have effective public negatives. Commands V/J; [resource contract](../../docs/font-loading.md) and development Red/Green receipts; existing destination sentinels remain unchanged on failure. |
| 17 | PASS | Existing font loading, paragraph composition/pagination, table composition/pagination and large-table streaming regressions pass. Commands V/J; per-class counts in the verification receipt include all those suites. |
| 18 | PASS | [Capability Matrix](../capability-matrix.yaml) records T28 supplementary evidence and explicit remaining gates while retaining experimental status and the primary T24 paragraph profile. Command I and Standards/Spec review. |
| 19 | PASS | [Facade Surface](../facade-surface.yaml) gives the applicable paragraph exclusion reason and does not add an unsupported stable stub. Command I and independent review; counts remain 12 surfaces and 19 exclusions. |
| 20 | PASS | English contracts and the Chinese guide describe final behavior and migration from earlier scalar/ASCII wrapping and logical paint order. Public command versions remain unchanged. See Unicode, font, paragraph/pagination and table docs plus the Chinese guide. |
| 21 | PASS | [PROVENANCE](../../PROVENANCE.md), [DEPENDENCIES](../../DEPENDENCIES.md), [NOTICE](../../NOTICE), ICU/fontTools license texts and Noto OFL/source manifests agree with delivered bytes. Commands P/V/J and review. Font fixtures and acceptance tools are excluded from the default product runtime. |
| 22 | PASS | Required root `verify` succeeds: command V, 973 tests / 0 failures / 0 errors / 3 existing optional skips. |
| 23 | PASS | Required JDK 8/11/17/21 matrix succeeds: command J, each with 973 tests / 0 failures / 0 errors / 3 existing optional skips. Actual Linux/container identities are in the verification receipt; other platforms remain unverified. |
| 24 | PASS | Required inventory validate, generate and check succeed in order: command I; final generated output is current. |
| 25 | PASS | Separate clean-context Standards and Spec agents review all differences from the fixed base, including every untracked delivery file. [Two-axis review receipt](T28-code-review.md) includes source and final artifact review scope. |
| 26 | PASS | All required findings are fixed and reverified: Standards 3 resolved; Spec 2 P2 source findings and one report wording correction resolved. The Repeated Switches heuristic is resolved; the naming heuristic is explicitly accepted and nonblocking. See the separate review tables and development logs. |
| 27 | PASS | Command W passes and final scope contains only T28 delivery paths. Startup was clean; HEAD remains the fixed base and no unrelated file was introduced. |
| 28 | PASS | The initial verified and reviewed delivery remained uncommitted, with no push, PR, merge or tracker write. The user subsequently explicitly authorized a DCO-signed commit, GitHub push and closure of #29 after the push; that authorization applies to the publication follow-up. |
| 29 | PASS | This record reports all 29 results, actual command IDs, evidence locations and remaining limits; the final response links this receipt and reports the same outcome. |

## Scope and remaining compatibility gates

Only #29 is delivered. HarfBuzz (#30), the Asian resource product (#34), and
full Foundation certification (#33) remain out of scope. GSUB substitutions
and GPOS positioning are not applied. There is no mark attachment,
normalization, hyphenation, variation-sequence selection,
vertical text or logical Tagged PDF/ActualText reconstruction. The reference
Hebrew font is a bidi probe, not Hebrew shaping certification.

Individual public Unicode cases use the existing default Worker settings.
The seven-profile combined transaction explicitly reserves 2 GiB of modeled
owned memory and a 1 GiB Worker heap for the six complete programs. The raw
reference's retained GID domains require a finite 500000-font-entry, 4 MiB
decoded-data extraction budget. These are acceptance declarations and do not
change product defaults or claim JVM/RSS measurement.

Linux x86-64 and the completed JDK 8/11/17/21 checks do not certify Windows,
macOS x86-64/arm64 or the full Foundation font set. Independent PDF standards
evidence and compatible-status Dependency Gates remain open. The primary T24
paragraph Acceptance Profile remains in the matrix alongside the T28
supplementary Unicode evidence; neither establishes compatible status.
