# T28 independent reference declaration

This declaration fixes the reference inputs and acceptance thresholds before
executing the Unicode implementation. It concerns #29 under #1, against
`875c140fc4617f8a9649f1c9bed8034c52bd6dfa`. Completion and compatibility are
unproven until the independent review and evidence gates have been satisfied.

## Inputs and independent authority

Unicode 16.0 UAX #9 (bidi), #14 (line breaking), #24 (script), and #29
(grapheme/word boundaries) are the correctness authority. ICU4J 77.1 implements
those responsibilities internally. Shaping, ligatures, kerning, mark attachment,
normalization, hyphenation, vertical text and HarfBuzz remain separate work.
Reference expectations must never call ICU or the Folio PDF paragraph planner.

Fonts are explicit offline resources: Noto Sans 2.008, Noto Sans Hebrew 3.000
(a bidi probe, not Hebrew shaping certification), and Noto Sans CJK 2.004
SC/TC/JP/KR. Sources come from immutable upstream commits and retain OFL notices.
The four CJK TrueType references are complete Regular (`wght=400`) static
instances made with fontTools 4.59.2, retaining all 65,535 glyphs. They are not
reduced or sanitized to fit the earlier T19 profile. Original source hashes,
output hashes, tool identity, licenses and the reproducible offline recipe
belong in the font resource manifest. No test data enters pdf-document's JAR.

## Fixed behavior examples

- Graphemes: `Ae\u0301B` has clusters `A`, `e\u0301`, `B`, including when a
  text-inline boundary occurs between `e` and the combining acute. A line may
  never end between the base and its combining mark. A cluster wider than an
  area follows the declared WRAP/REJECT/VISIBLE overflow policy atomically.
- Word/line: `A-B C` admits line breaks after `-` and after the space; `A\u00a0B`
  has no ordinary line opportunity at NBSP. WRAP can use grapheme fallback for
  an overlong unbreakable unit. REJECT/VISIBLE preserve ordinary line units.
- Scripts: `A\u0301 Ω Я 骨` resolves its base letters to Latin, Greek, Cyrillic
  and Han, with the acute inheriting Latin. Script changes preserve text and
  font metrics; they do not authorize glyph substitution.
- Bidi, first-strong paragraph base with LTR fallback: logical `A אב 12, B`
  has left-to-right display `A 12 בא, B`; logical `אב (12) A` has display
  `A (12) בא`. Clusters retain internal scalar order during reordering.
  Bidi levels are resolved for the paragraph before individual line reordering.
- CJK: with four full-em advances available, `骨骨（骨骨）骨骨。` breaks into
  `骨骨（骨`, `骨）骨`, `骨。`. Closing punctuation cannot begin an ordinary
  line and an opening parenthesis cannot end it. The same Han input must select
  the explicitly first applicable SC/TC/JP/KR font, even if other regional
  fonts are also declared. Host language and installed fonts have no role.

## Fixed seven-profile evidence envelope

Profiles: Latin, Greek, Cyrillic, CJK SC, CJK TC, CJK JP and CJK KR. Each profile
must check published/reopened scalar mappings, font choice, baseline/advance
geometry, exact expected line membership, embedded subsets, and independent
visual output. Latin additionally carries combining, mixed-bidi, punctuation
and word probes. CJK pages include region-distinguishing U+9AA8 and native
SC/TC/JP/KR text. All geometry is independently computed from pinned source
`head`/`hmtx` metrics and manually declared lines and visual order.

All pages use MediaBox `[0 0 612 792]`, absent CropBox, 72-point margins,
12-point text and 48-point leading. Geometry tolerance is **0.0001 point** per
coordinate/advance. The independent reference writer embeds the explicit fonts
and positions the manually declared glyphs without invoking Composition.

The exact page declarations, in paragraph order, are below. A slash separates
manually expected lines and is not input text. An omitted width uses the
468-point margin box. Every page declares Noto Sans first, then its explicit
CJK region (JP on non-CJK pages), then Noto Sans Hebrew when a bidi probe needs
it. Regional-order negative controls replace the declared CJK program.

| Profile | Paragraph input and width | Expected lines in display order |
| --- | --- | --- |
| Latin | `A \u0301BB`, width 12 | `A \u0301` / `B` / `B` |
| Latin | `A-B C`, width 18 | `A-` / `B ` / `C` |
| Latin | `A אב 12, B` | `A 12 בא, B` |
| Latin | `A\u0301 Ω Я 骨` | `A\u0301 Ω Я 骨` |
| Latin | `אב (12) A` | `A (12) בא` |
| Greek | `Αλφα Βήτα.` | `Αλφα Βήτα.` |
| Greek | `Α\u0301 Ω` | `Α\u0301 Ω` |
| Cyrillic | `Привет, мир.` | `Привет, мир.` |
| Cyrillic | `Я-Я Я` | `Я-Я Я` |
| Each CJK region | `骨骨（骨骨）骨骨。`, width 48 | `骨骨（骨` / `骨）骨` / `骨。` |
| CJK SC | `简体中文 骨` | `简体中文 骨` |
| CJK TC | `繁體中文 骨` | `繁體中文 骨` |
| CJK JP | `日本語 かなカナ 骨` | `日本語 かなカナ 骨` |
| CJK KR | `한국어 한글 骨` | `한국어 한글 骨` |
| CJK KR | `\u1100\u1161\u1102\u1161`, width 24 | `\u1100\u1161` / `\u1102\u1161` |

Reference correction, made before executing the CJK punctuation probe: the
initial transcription incorrectly treated the parenthesized phrase as atomic.
UAX #14 permits a break between its Han characters. Independently, JDK
`java.text.BreakIterator.getLineInstance(Locale.ROOT)` yields offsets
`0, 1, 2, 4, 6, 7, 9`; greedy packing at four em gives the lines above.
No producer output or visual threshold was used to make this correction.

The Latin space-plus-acute probe is deliberate: the preferred break after
the space must move past the combining mark. Zero-advance marks on ordinary
letters alone would not detect the existing scalar-break defect. Hangul Jamo
clusters are rendered without syllable shaping; source hmtx advances are 920
units per Jamo at 1000 units/em, so a two-scalar cluster is 22.08 points.
At width 18 the same 22.08-point
cluster must fail atomically under WRAP/REJECT instead of splitting.

Visual settings are fixed at **144 DPI**, **1224 × 1584**, opaque white,
8-bit sRGB, pinned PDFium CLI v0.11.2 / chromium-7881 and ImageMagick 7.1.2-30.
Primary PDFium-to-reference comparison uses AE with zero fuzz and **0 changed
pixels**. Secondary renderer agreement allows **12,000 changed pixels** per
page. These thresholds precede rendering the implementation and must not be
relaxed to obtain a pass. All published products also require pinned qpdf
12.4.0 syntax success. Wrong cluster split, bidi order, regional font selection
and a one-point geometric displacement must each invalidate acceptance.

Linux x86-64 and JDK 8/11/17/21 evidence does not certify Windows or macOS,
HarfBuzz, or the full Foundation Composition Profile. Missing evidence is
INDETERMINATE. Capability dependency and standards gates remain in force.
