# Unicode Composition

T28 (#29) applies ICU4J **77.1** inside the existing paragraph and table
Composition commands. `DocumentWorkflow.execute`, the public declarations,
and both execution profiles remain the entry points. No ICU type appears in
a public or protected signature. Direct `DrawPositionedUnicodeText` remains
an unshaped, scalar-order positioning command.

## Text, boundaries and direction

Paragraph text is supplied in logical Unicode order. All adjacent text inlines
are analyzed together, so a font-size or inline boundary does not split an
extended grapheme cluster. An inline graphic participates as U+FFFC while
retaining its declared dimensions. A literal U+FFFC in text remains text.

The internal processing uses Unicode 16 data and explicit root-locale rules:

| Responsibility | Composition behavior |
| --- | --- |
| Grapheme segmentation | Extended grapheme clusters are indivisible during emergency wrapping and visual reordering. A space followed by a combining mark is one cluster; justification expands after that cluster. |
| Word segmentation | Word boundaries delimit unshaped text runs; they are intersected with grapheme boundaries. They are not interchangeable with permitted line breaks. |
| Line segmentation | Greedy packing prefers Unicode line opportunities, including hyphens and CJK punctuation rules. WRAP falls back to complete clusters. REJECT and VISIBLE preserve the units between line opportunities. Graphics and tab fields remain atomic. |
| Script processing | Script changes delimit rendering runs. Common and Inherited characters inherit the preceding strong script within a word; a word boundary resets that context. This does not select a language, font region or shaping engine. |
| Bidirectional processing | Each Unicode paragraph resolves first-strong direction, falling back to LTR. Actual lines receive line-level whitespace reset and cluster-preserving visual reordering. Odd-level characters use Unicode mirror mappings, including paired parentheses. |

LF, U+2028 LINE SEPARATOR and U+2029 PARAGRAPH SEPARATOR force a line end and
emit no glyph. Consecutive separators reserve empty lines; a terminal separator
does not add a new empty line. Bidi controls affect analysis but emit no glyph
and request no fallback glyph. All those scalars still count toward the input
limit. Version-2 tabs retain explicit stops and atomic fields. CR and other
ISO controls remain invalid, as do unpaired surrogates. There is no whitespace
collapse. LEFT/CENTER/RIGHT remain physical horizontal alignment choices,
independent of paragraph direction.

For example, logical `A אב 12, B` paints in left-to-right order
`A 12 בא, B`; logical `אב (12) A` paints as `A (12) בא`. An input
`A \u0301BB` at the pinned 12-point Noto Sans size and width 12 wraps into
`A \u0301`, `B`, `B`. With full-em CJK glyphs and width 48, the input
`骨骨（骨骨）骨骨。` wraps into `骨骨（骨`, `骨）骨`, `骨。`.

`ExtractTextAndStructure` observes PDF painting order. Consequently its
published/reopened `PageText` contains this display order and the displayed
mirrored scalar mappings, without bidi controls or forced line separators.
This command does not reconstruct the original logical input and no logical
`ActualText` or Tagged PDF structure is added. Applications that need the
original logical paragraph must retain their declaration.

## Explicit fonts and shaping boundary

Declare `FontSelection.explicit(...)` in fallback order. Each scalar uses the
first declared usable cmap mapping; mirroring is resolved before that lookup.
Declare the intended SC, TC, JP or KR font before other regional candidates.
ICU script analysis, the host locale and installed fonts never choose a region.
Fallback remains scalar-based inside an indivisible cluster; it does not
promise one font or correct mark attachment for the whole cluster.

The static TrueType extension in [Font loading](font-loading.md) admits the
complete pinned Noto Sans and CJK reference programs. Test fonts reside only
in the repository's acceptance artifact/test resources and retain their own
OFL notices. They are not a runtime font bundle or default font source.

Segmentation, script classification and bidi do **not** perform shaping.
GSUB substitution and GPOS positioning are not applied. There is no ligature substitution, kerning, normalization,
mark attachment, contextual Arabic/Indic shaping, Hangul Jamo syllable
composition, variation-sequence glyph selection, hyphenation or vertical
writing. Precomposed supported glyphs can be drawn; a missing scalar mapping
retains the existing font failure. HarfBuzz (#30), the Asian resource product
(#34) and full Foundation certification (#33) remain separate work.

## Limits, execution and migration

Existing font/code-point, fallback-check, line, layout-work and generated-byte
limits remain exact. Font preflight additionally reserves conservative memory
for complete static outlines and retains parsed metadata reservations until
release. Repeated name/cmap records and eagerly read GSUB data count toward
that model; subset normalization has separate scratch space. Unicode input is validated and counted before
analysis; temporary strings, boundary/level storage, atoms and line plans use
the existing modeled-memory scope, with cancellation/deadline checkpoints.
These counters do not measure all JVM allocation or RSS. A cluster wider than
every finite area fails atomically under WRAP/REJECT. Propagated failures keep
publication targets unchanged and receipts `NOT_ATTEMPTED`.

The Worker transports the original validated strings in the existing closed
command versions. No new wire field is needed. Its code-source admission now
requires the complete hash-pinned ICU JAR, as documented in the
[dependency record](third-party/icu4j-77.1.md). Unicode processing runs within
the Worker. The seven-profile combined acceptance transaction declares a
2 GiB owned-memory budget and 1 GiB Worker heap because it retains six full
font programs until publication. Individual public Unicode tests also declare
1 GiB owned memory and use the default Worker settings. Explicit 32 MiB and
160 MiB negative cases verify atomic resource rejection. These declarations
do not change runtime defaults.

There is no source or binary interface break. Existing paragraph/table command
versions now produce Unicode-aware boundaries and visual bidi order; their
line/page counts and generated byte counts can therefore change. Applications
that previously reversed RTL input must pass logical text instead. Tests that
depended on scalar splits or ASCII-only opportunities must adopt the documented
cluster/line rules. The direct positioning command retains its own contract.

## Independent evidence and authority

The [reference declaration](../capabilities/profiles/T28-unicode-reference.md)
fixes inputs, lines and thresholds. The offline
[reference writer](../scripts/t28-unicode-reference.py) creates PDF objects and
coordinates from manual lines plus original fontTools metrics, without ICU,
Folio or a layout engine. Public reopened observations check all seven
profiles with a 0.0001-point geometry tolerance. Independent PDFium/ImageMagick
comparisons use 144 DPI, zero changed primary pixels and at most 12,000 secondary changed
pixels. qpdf supplies a separate syntax observation. Missing tools or evidence
remain INDETERMINATE. Standards, compatibility dependency gates and the other
Foundation font/platform requirements remain open; status is experimental.

Authoritative references are Unicode 16
[UAX #9](https://www.unicode.org/reports/tr9/tr9-50.html),
[UAX #14](https://www.unicode.org/reports/tr14/tr14-53.html),
[UAX #24](https://www.unicode.org/reports/tr24/tr24-38.html),
[UAX #29](https://www.unicode.org/reports/tr29/tr29-45.html), and the
[ICU boundary-analysis guide](https://unicode-org.github.io/icu/userguide/boundaryanalysis/).
