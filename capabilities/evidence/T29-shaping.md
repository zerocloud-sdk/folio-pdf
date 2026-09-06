# T29 explicit HarfBuzz shaping delivery record

Status: `experimental`
Capability: `composition.shaping.harf-buzz`
Acceptance Profile: `T29-shaping`
Release train: `0.1.0-SNAPSHOT`
Result: `indeterminate`

Issue [#30](https://github.com/zerocloud-sdk/folio-pdf/issues/30), parent
[#1](https://github.com/zerocloud-sdk/folio-pdf/issues/1). The fixed review base is
`dd4cda28c24a304258e75f0854d86852275f5320`. This is an unfinished, uncommitted
delivery. Windows x86-64, macOS x86-64 and macOS arm64 execution evidence is
unavailable. No completion criterion is marked satisfied before the required
independent Standards and Spec reviews.

The [delivery receipt](T29-delivery.md) maps each contract requirement to
observed evidence, records actual validation commands and preserves failed
verification attempts.

The [English contract](../../docs/harfbuzz-shaping.md) and
[Chinese guide](../../docs/zh-CN/getting-started.md) describe explicit Provider
selection, font ownership, native limits, grapheme-safe reshaping at actual
line boundaries, embedded glyph programs, logical text observations and the
existing Worker envelope. The C helper belongs to this project. HarfBuzz
10.2.0 is separately installed and no native binary enters a default Maven
artifact. No unofficial Java wrapper or Migration Facade stub is introduced.

## Independent references

The [frozen reference](../profiles/T29-shaping-reference.md) predates product
comparisons. Official `hb-shape` supplies glyph IDs, UTF-16 clusters, advances,
offsets and direction. A separate fontTools/raw-PDF writer supplies the fixed
reference PDF and rasters without Folio, PDFBox, ICU or the project adapter.
The complete static Noto fonts have pinned versions, source URLs, hashes and
OFL notices in the [font manifest](../../pdf-acceptance/src/main/resources/net/zerocloud/pdf/acceptance/fonts/noto/README.md).

| Profile | Fixed scope |
| --- | --- |
| Arabic | Two pages; joining, lam-alef ligature, combining marks, mixed Latin, narrow lines and explicit fallback |
| Hebrew | Two pages; RTL order, marks, mixed Latin, narrow lines and explicit fallback |
| Devanagari | Two pages; conjuncts, reordered vowel/mark glyphs, mixed Latin, narrow lines and explicit fallback |
| Thai | Two pages; combining-mark positioning, mixed Latin, narrow lines and explicit fallback |

Every native number must match exactly in source font units. Reopened glyph
geometry uses a 0.0001-point tolerance. Every 240 by 192 point page has a fixed
144-DPI, opaque-white sRGB reference: primary zero-fuzz AE 0 and secondary
renderer disagreement at most 3000 changed pixels. The independent subset
verifier follows painted CIDToGID mappings and checks source outlines,
composite dependencies and ToUnicode clusters in the published PDF.

## Required platform observations

| Required platform | Current determination | Worker applicability |
| --- | --- | --- |
| Linux x86-64 | Six local chains, full root and all four JDK gates PASS on current source | Required within the existing Linux/JDK 8,11,17,21 envelope |
| Windows x86-64 | INDETERMINATE; no actual runner or native execution receipt | Outside the existing Worker OS envelope; native and IN_PROCESS evidence remain required |
| macOS x86-64 | INDETERMINATE; no actual runner or native execution receipt | Outside the existing Worker OS envelope; native and IN_PROCESS evidence remain required |
| macOS arm64 | INDETERMINATE; no actual runner or native execution receipt | Outside the existing Worker OS envelope; native and IN_PROCESS evidence remain required |

Linux success cannot fill another platform's row. An installation receipt
records a build, not compatibility. Native metrics, reopened semantics,
syntax, subsets and all eight visual pages must pass together with traceable
installation/loaded-engine observations for each required platform. Existing
Linux-only qpdf, PDFium and ImageMagick assets do not run on other platforms
by implication. The live engine observer currently implements Linux only.

The recorder preserves IN_PROCESS observations if a required Linux Worker is
unavailable, but the combined product chains remain INDETERMINATE and retain
the `WORKER_UNAVAILABLE` reason. Native calls occur in a parent-side subprocess
in both modes and are not fully contained by the PDF Worker.

## Retained Linux run

The current-source run finished on 2026-09-06 at 16:38:23 UTC. Its
[run manifest](artifacts/T29-linux-run.json) identifies the exact command,
environment, built reactor JARs and all 136 archived files. The
[source snapshot](artifacts/T29-verified-source-snapshot.json) hashes 663
compilation and acceptance inputs, verified unchanged before and after the
run. Its canonical SHA-256 is
`94b628481751378665a40a3b033430b1224ec0adc817f811386bc44db59ef24f`.
The records and generated products were archived without changing any bytes.

This snapshot includes the [font snapshot repair](artifacts/T29-font-cache-repair.json),
which shares byte-identical private font data while charging each retained
declaration. All 80 PNG files match the previously visually inspected run
byte for byte. Zeroing only each PDF's two hexadecimal trailer ID values gives identical
products in both modes and in the prior run; the manifest records the exact
normalization and hash. The [historical archive](artifacts/T29-linux-before-font-cache-repair.json)
preserves all 136 prior files and their original source/run identities. The
intermediate [Javadoc-only revision](artifacts/T29-final-javadoc-delta.json)
is historical; it is included in the current fully rerun source snapshot.

| Chain | Linux determination and record |
| --- | --- |
| Native metrics | [PASS](T29-shaping-native.md), exact independent GIDs, clusters, directions and positions |
| Installation and loaded engine | [PASS](T29-shaping-installation.md), explicit pinned build and separate live startup observation |
| Reopened semantics and geometry | [PASS](T29-shaping-semantic.md), both execution modes |
| Syntax | [PASS](T29-shaping-syntax.md), pinned qpdf, both modes |
| Actual embedded subsets | [PASS](T29-shaping-subsets.md), independent qpdf/fontTools, both modes |
| Eight-page independent raster | [PASS](T29-shaping-visual.md), all 16 mode/page observations |

All primary rasters have zero changed RGB pixels against their frozen
references; secondary renderer comparisons remain inside the fixed 3000-pixel
bound. The eight IN_PROCESS PDFium pages were visually inspected in the prior run; current image bytes are identical
and preserve that check for missing content, clipping, layout and script rendering. Both modes
produce the same ID-neutral PDF hash. This is Linux evidence only.

## Evidence and promotion boundaries

Repository tests cover public Workflow publication/reopening, the actual
external Provider protocol, preserved unshaped positioned text, registration
without preference, exact limits, borrowed font ownership, buffered relayout
and incremental table release. Negative controls reject wrong clusters,
direction, offsets, fallback resources and missing painted glyphs. Installation
tests build the real helper, relocate it and verify effective tool selection.
Live observation tests reject changed helper/engine bytes and ambiguous
preload interposition.

The [Capability Matrix](../capability-matrix.yaml) remains experimental with
explicit dependency and promotion gates. The
[Facade Surface](../facade-surface.yaml) records an exclusion: current Preview
`layout.Document` does not map the shaping contract, and no approved facade
mapping exists. Independent standards evidence and compatible prerequisite
states remain separate requirements for compatibility promotion.

Independent Standards and Spec reviews inspected the whole fixed-baseline
working-tree change and relevant untracked files. Their three initial source findings
(PDF 1.5 admission, repeated-relayout reservations and scalar fallback counts)
were repaired with public Red/Green/Refactor regressions. The subsequent
snapshot repair and per-declaration accounting also have public Red/Green
proof and 58 passing focused cases. Current [full root verification](artifacts/T29-verified-root-verification.json)
and [JDK 8/11/17/21 verification](artifacts/T29-verified-jdk-matrix.json) each
pass 1019 tests
with zero failures/errors and only three pre-existing optional scale skips.
The earlier JDK 8 Worker heap failure is preserved with its diagnosis and
repair evidence. [Standards](artifacts/T29-standards-review.md) and
[Spec](artifacts/T29-spec-review.md) records retain the complete independent
review and the missing-platform requirements.
No commit, push, PR, tracker write, merge or artifact publication is authorized
by this delivery contract.
