# T29 independent shaping reference

This declaration fixes the #30 acceptance inputs before implementing the
project-owned adapter or Composition shaping. The review baseline is
`dd4cda28c24a304258e75f0854d86852275f5320`. Evidence remains unproven until
independent Standards and Spec review, the required commands, and all four
platform executions have finished.

## Independent authority and native installation

The native engine is **HarfBuzz 10.2.0**, using the OpenType shaper, explicit
horizontal direction, script and language, font scale equal to units/em,
monotone grapheme clusters (level 0), and default OpenType features. Official
`hb-shape` supplies the independent numeric oracle; it must run before the
project adapter. Its output must never be replaced by output from Folio PDF.
Unicode 16 UAX #9, #14, #24 and #29 remain the bidi and boundary authorities.

The source archive is
<https://github.com/harfbuzz/harfbuzz/releases/download/10.2.0/harfbuzz-10.2.0.tar.xz>,
SHA-256 `620e3468faec2ea8685d32c46a58469b850ef63040b3565cde05959825b48227`.
HarfBuzz is separately installed under its MIT-Old license. The project adapter
will call the public C interface in a separately built local helper, supervised
through the existing bounded subprocess Provider contract. It will not embed
HarfBuzz or use an unofficial Java wrapper. Neither this subprocess nor its
native allocations are covered by the PDF Hardened Worker's containment.

## Explicit reference fonts

All fonts are complete, unmodified static hinted TrueType programs from
`notofonts/noto-fonts` commit
`ffebf8c1ee449e544955a7e813c54f9b73848eac`:

| Profile | Font version | SHA-256 |
| --- | --- | --- |
| Arabic | Noto Sans Arabic 2.009 | `ceea25b464a656dc3b26849bab9356740401af62aedf1bfa8b7f0d9b75925b1b` |
| Hebrew | Noto Sans Hebrew 3.000 | `a7fa16fffb27bedb060a0866267c29e9859aeb9c21cc33f5b3aaf6eb062eca85` |
| Devanagari | Noto Sans Devanagari 2.002 | `385e78e6359a9d88a0f243d53b1209d7548361ba2194e2b9ec779bcaa7e8949d` |
| Thai | Noto Sans Thai 2.000 | `404ddfb5ed0aaa6b6ec8a85700d682978992062d67da93903967b56cbd9a4acc` |

Every profile declares Noto Sans Regular 2.008 first, then its script font.
Latin and unmarked spaces select the first font. Complete script clusters,
including a space with script-specific marks, select the second font;
selection may not split a base and its marks across fonts. Source
URLs, byte lengths, glyph counts, hashes and OFL notices are in the existing
acceptance `fonts/noto` manifest. No system or online font discovery is allowed.

## Four distinct profiles

The machine-readable `shaping/T29-corpus.json` records logical paragraph inputs
and manually declared line membership and visual run order. Numeric glyph results come only
from the independent official tool with those explicit run declarations.

| Profile | Script / language / direction | Required probes |
| --- | --- | --- |
| Arabic | Arab / ar / rtl | Contextual forms in `السَّلَامُ`, lam-alef plus fatha `لَا`, mixed `A لَا B`, and wrapping repeated marked ligatures |
| Hebrew | Hebr / he / rtl | `שָׁלוֹם`, multiple marks on `שָׁ`, mixed `A שָׁ B`, and wrapping repeated marked bases |
| Devanagari | Deva / hi / ltr | Conjunct and pre-base matra `क्षि`, anusvara `किं`, mixed `A किं B`, and wrapping repeated syllables |
| Thai | Thai / th / ltr | Sara-am decomposition/reordering in `น้ำ`, stacked marks `กิ้`, mixed `A กิ้ B`, and wrapping repeated clusters |

Each profile additionally has a fifth paragraph: Arabic `ا \u064e`, Hebrew
`ש \u05b8`, Devanagari `क \u093f`, and Thai `ก \u0e34`. The strong prefix
fixes the paragraph direction/script; the following space and combining mark
form one grapheme. Noto Sans covers the space but not the mark, so the whole
grapheme must select the script font. This sixth line detects scalar fallback.
Devanagari also requires the independently observed inserted dotted-circle
glyph 134, even though U+25CC does not occur in the input.

Each profile uses two pages with MediaBox `[0 0 240 192]`, absent CropBox,
24-point margins, 12-point text and 48-point leading. The first three lines
fill the first page; the repeated-cluster probe wraps to two lines on the
second page; the dedicated fallback probe occupies its third line. The narrow widths are **12 points** for Arabic, Hebrew and Thai,
and **16 points** for Devanagari. Independent advances for one marked cluster
plus a Noto Sans space are respectively 10.104, 11.880, 10.320 and 15.372 points;
two clusters exceed each declared width. Fonts and source metrics determine ascent, descent
and baselines. Glyph IDs, UTF-16 cluster starts and input ranges, direction,
font selection, ligatures, reordering and page/line membership must match
exactly. Advances and offsets have **zero font-unit tolerance**; PDF coordinate
observations have **0.0001-point tolerance**.

An independent PDF writer uses the oracle's glyph IDs and positions plus
fontTools **4.59.2** to subset the original fonts by glyph ID. It must not call
Folio, PDFBox, ICU or the project native adapter. Reopened product subsets must
contain every required shaped glyph and its transitive composite components;
nominal cmap glyph coverage alone is insufficient. Multiple glyphs sharing a
cluster and glyphs without an independent cmap entry must be retained.
`scripts/t29-verify-subsets.py` reopens the reference with pinned qpdf and checks
complete source outlines and recursive composite dependencies with fontTools.
Negative PDFs omit Devanagari glyph 610 or component 607 of composite 619;
both must be rejected after reopening.

Visual comparisons use pinned PDFium CLI **v0.11.2 / chromium-7881**,
ImageMagick **7.1.2-30**, **144 DPI**, **480 × 384**, opaque white, 8-bit sRGB,
AE with zero fuzz and **0 changed primary pixels**. Secondary renderer
agreement allows at most **3,000 changed pixels** per page. The separate syntax
chain uses qpdf **12.4.0**. These tolerances may not be relaxed after observing
product output. Wrong cluster, direction, offset, fallback and omitted shaped
glyph controls must each fail the relevant oracle or published-PDF check.

## Platform and workflow evidence

Linux x86-64, Windows x86-64, macOS x86-64 and macOS arm64 each need independent
execution receipts recording the OS/architecture, JDK, source snapshot,
compiler/build tools, native engine version and hashes, font hashes, commands,
numeric oracle comparison, reopened subset/geometry assertions, syntax and
independent raster results. Cross-compilation is not execution evidence.
Absent platforms or tools remain **INDETERMINATE** and prevent #30 completion.

Both workflow execution profiles must satisfy the same public shaping
contract within the existing supported Worker envelope (Linux, JDK 8/11/17/21).
The direct positioned Unicode command retains its unshaped contract. Missing
or mismatched native installations, malformed input and exceeded bounds must
fail with stable safe diagnostics before publication, preserving caller-owned
resources and existing targets. Capability dependency gates still apply even
when the shaping observations pass.
