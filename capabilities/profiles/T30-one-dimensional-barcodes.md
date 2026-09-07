# T30 one-dimensional barcode Acceptance Profile

This declaration is written before T30 product generation. It fixes the
Reference Suite inventory and independent oracles for #31. The public test
seam, already approved in #1 and the execution handoff, is
`DocumentWorkflow.execute`, including publication, reopened public Queries,
and both applicable execution profiles. It is not an encoder unit-test seam.
No completion or compatibility determination is made by this declaration.

## Inventory and input contracts

The Native Interface will declare immutable `Barcode1D` data and a versioned
`DrawBarcode1D` Command. All dimensions are PDF default-user-space points.
Every mode has PDF path output; no barcode font or bitmap is used for bars.
Human-readable text is optional and requires explicitly supplied fonts.

| Mode | Valid input and variants | Checksum contract | Independent decoding |
| --- | --- | --- | --- |
| CODE128 | ASCII, explicit FNC1–4; AUTO, A, B, C code sets | Mandatory weighted modulo 103; stop is generated | ZXing Code128Reader, including raw codewords |
| GS1_128 | Bracketed application identifiers, e.g. `[01]09501101530003`; AUTO/A/B/C where representable | GS1 AI validation and initial/separating FNC1; modulo 103 | ZXing with GS1 hint, raw codewords and literal AI payload |
| CODE128_RAW | Explicit start 103/104/105 followed by contextual codewords 0–102; shifts, latches and FNC1–4 | Caller omits checksum and stop; both are generated; malformed state transitions fail | ZXing payload and complete codeword sequence |
| CODE39 | `0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. $/+%` | NONE or generated modulo 43 | ZXing Code39Reader with matching check-digit setting |
| CODE39_EXTENDED | ASCII 0–127, expanded as Full ASCII Code39 | NONE or modulo 43 over the expanded symbols | ZXing extended Code39Reader |
| CODABAR | A–D start/stop, at least one interior `0123456789-$:/.+` | NONE or generated modulo 16, inserted before stop | ZXing CodaBarReader retaining guards, independent check value |
| EAN13 | Exactly 12 data digits or 13 including a verified check digit | GS1 modulo 10 | ZXing EAN13Reader |
| EAN8 | Exactly 7 data digits or 8 including a verified check digit | GS1 modulo 10 | ZXing EAN8Reader |
| UPCA | Exactly 11 data digits or 12 including a verified check digit | GS1 modulo 10 | ZXing UPCAReader |
| UPCE | Number system 0 or 1 plus six compressed digits, optionally a verified check digit; only valid compression forms | Modulo 10 over the expanded UPC-A body | ZXing UPCEReader and expansion |
| SUPPLEMENT2 | Exactly two digits, including leading zeroes | Modulo-4 parity; no appended digit | Independent EAN add-on parity decoder |
| SUPPLEMENT5 | Exactly five digits, including leading zeroes | Weighted modulo-10 parity; no appended digit | Independent EAN add-on parity decoder |
| EAN/UPC + supplement | Each of the four retail modes with either supplement length | Both component contracts apply | ZXing retail and independent add-on decoder |
| INTERLEAVED_2_OF_5 | Even digit count without checksum; odd data count with generated checksum | NONE or GS1 modulo 10; no silently inserted zero | ZXing ITFReader and independent check value |
| MSI | Nonempty digits | NONE or generated Luhn modulo 10 | Independent MSI pulse-width and BCD decoder |
| POSTNET | 5, 9 or 11 data digits | Mandatory digit making the sum divisible by ten | Independent two-of-five height decoder |
| PLANET | 11 or 13 data digits | Mandatory digit making the sum divisible by ten | Independent three-of-five height decoder |

The implementation must reject unsupported mode/option combinations, invalid
characters and lengths, invalid raw symbols and transitions, incorrect
supplied retail checksums, invalid GS1 fields, nonfinite geometry, degenerate
placement, inadequate quiet zones, and invalid text declarations. No trimming,
non-digit filtering, truncation, or padding is implicit. Finite input and
generated-content bounds compose with the Workflow Resource Policy.

## Geometry and literal expectations

The primary fixture module is 1 point, bar height 48 points and quiet zones
10 modules on each side (11 for EAN13). Two-width symbologies use ratio 2;
ratio 3 is a separate profile. Supplements are separated from retail bars by
9 modules. Guard extension is an explicit retail option. Text is below the
bars by a 3-point gap, at 8 points, with declared alignment and font metrics;
an above-bar profile and hidden/checksum/start-stop/alternate-text profiles
are required. Text must never overlap the bars or silently lose a character.

| Fixture | Literal expectation before product generation |
| --- | --- |
| Code128 B `AB`, raw `[104,33,34]` | Check 102; 57 modules; run widths `2112141113231311234111312331112` |
| Code128 A `AB` | Check 101; 57 modules |
| Code128 C `123456` | Data words 12,34,56; check 44; 68 modules |
| GS1 `[01]09501101530003`, C/AUTO | Initial FNC1; decoded GS1 element string `0109501101530003`; 134 modules |
| Code39 `FOLIO` | 90 modules at ratio 2; 111 at ratio 3; modulo-43 G adds one symbol |
| Extended Code39 `abc` | Expanded `+A+B+C`; check R; 103 modules without check, 116 with check |
| EAN13 `590123412345` | Complete `5901234123457`; 95 modules |
| EAN8 `9638507` | Complete `96385074`; 67 modules |
| UPC-A `04210000526` | Complete `042100005264`; 95 modules |
| UPC-E `0425261` | Complete `04252614`; expanded `042100005264`; 51 modules |
| Supplements `05`, `51234` | 20 and 47 modules; parity AB and AABAB respectively |
| Interleaved 2-of-5 `12345` with check | Complete `123457`; 50 modules at ratio 2, 63 at ratio 3 |
| MSI `1234567` with check | Complete `12345674`; 103 modules |
| POSTNET `12345` | Check 5, 32 bars, height bits for 1 `00011`, 0 `11000` |
| POSTNET `123456789`, `12345678901` | Checks 5 and 4; 52 and 62 bars |
| PLANET `40123456789`, `4012345678901` | Checks 1 and 0; 62 and 72 bars; digit patterns complement POSTNET |

The literal backslash prefix `\<FNC1>` remains seven data characters, even
when followed by explicit FNC4 markers. The following four AUTO cases fix
the code-set latch before FNC4. The common codeword prefix is
`[104,60,28,38,46,35,17,30]`; the suffixes below omit the generated check
and stop. Escapes in this table denote Unicode character values.

| Fixture | Input after literal prefix | Decoded payload after prefix | Codeword suffix | Check |
| --- | --- | --- | --- | --- |
| literal-fnc4-a-single | FNC4, `\u0001A` | `\u0081A` | `[101,101,65,33]` | 27 |
| literal-fnc4-a-pair | FNC4, FNC4, `\u0001A` | `\u0081\u00c1` | `[101,101,101,65,33]` | 2 |
| literal-fnc4-b-single | `\u0001`, FNC4, `\u007fA` | `\u0001\u00ffA` | `[101,65,100,100,95,33]` | 93 |
| literal-fnc4-b-pair | `\u0001`, FNC4, FNC4, `\u007fA` | `\u0001\u00ff\u00c1` | `[101,65,100,100,100,95,33]` | 82 |

Their labels retain the literal prefix, show each explicit marker as
`<FNC4>`, and render the original control characters as `\x01` / `\x7F`.

Four additional AUTO fixtures exercise FNC4 on numeric runs. AUTO restricts
these inputs to A/B so each numeric character retains the shift/latch state.
The following complete words omit the generated check and stop. Expected
payloads follow GS1 section 5.4.3.4.2; checks use its weighted modulo-103 rule.

| Fixture | Input (`F` means explicit FNC4) | Decoded payload | Words | Check | Modules |
| --- | --- | --- | --- | --- | --- |
| fnc4-auto-single-digits | `F123456` | `\u00b1` + `23456` | `[104,100,17,18,19,20,21,22]` | 27 | 112 |
| fnc4-auto-pair-digits | `FF123456` | `\u00b1\u00b2\u00b3\u00b4\u00b5\u00b6` | `[104,100,100,17,18,19,20,21,22]` | 35 | 123 |
| fnc4-auto-digits-before | `123456F123456` | `123456` + `\u00b1` + `23456` | `[104,17,18,19,20,21,22,100,17,18,19,20,21,22]` | 5 | 178 |
| fnc4-auto-shift-and-unlatch | `FF12F34FF56` | `\u00b1\u00b2` + `3` + `\u00b4` + `56` | `[104,100,100,17,18,100,19,20,100,100,21,22]` | 34 | 156 |

Their captions preserve caller digits and display every explicit marker as
`<FNC4>`; decoded extended characters do not replace human-readable input.

Postal fixtures use 1.44-point bars, 3.24-point pitch, 9-point full height,
3.6-point short height and 9-point quiet zones. Their painted width is
`(barCount - 1) * 3.24 + 1.44`. Frame bars are full height in both modes.
These are legacy-symbol profiles, not a claim of current postal eligibility.

Unrotated placement is `(1,0,0,1,36,144)`. The rotation fixture is
`(0,1,-1,0,240,72)`: a local point `(x,y)` must become `(240-y,72+x)`.
The additional scale/translation fixture is `(2,0,0,0.5,24,96)`:
`(x,y)` becomes `(24+2*x,96+0.5*y)`. Every bar corner and text matrix is
checked to 0.0001 point. Physical scanning certification is outside the
Native Interface's arbitrary affine-placement contract.

The label fixture is the existing OFL Noto Sans Regular asset, SHA-256
`b85c38ecea8a7cfb39c24e395a4007474fa5a4fc864f6ee33309eb4948d232d5`.
Its OpenType `head` declares 1000 units per em and vertical bounds -389/1067;
the independently read `hmtx` advances of A/B are 639/650. At 8 points,
`AB` advances 10.312 points. In the 77-point Code128 B box, a centered label
starts at local x=33.344, with below baseline -11.536. The declared rotation
therefore puts its first character at (251.536,105.344), advancing
(0,5.112); the scale fixture starts at (90.688,90.232), advancing (10.224,0).
Above labels use gap 4 and baseline 55.112; left/center/right start at local
x=0/33.344/66.688. A separate alternate caption is `Ω AB`.

The custom POSTNET fixture uses width 2, pitch 4, full height 12, short
height 5 and quiet zone 9 points. Four retail fixtures extend guard bars
5 points below baseline: EAN13 module regions [0,3), [45,50), [92,95);
EAN8 [0,3), [31,36), [64,67); UPC-A [0,10), [45,50), [85,95);
UPC-E [0,3), [45,51). The EAN13 guard fixture also carries supplement 05.
Supplement bars retain their normal baseline. Shown optional checks are
FOLIOG, *FOLIOG*, *abcR*, Codabar 12345, MSI 12345674 and ITF 123457.
Together with a bars-only fixture, these declarations add 18 layout cases
to the 98 encoding cases: 116 independently decoded pages, 115 labels per
execution profile.

## Independent evidence

ZXing core 3.5.3 is an Apache-2.0, Java-8-compatible test/acceptance dependency.
It is independent of OkapiBarcode 0.5.6. Original add-on, MSI and postal
decoders must consume actual bars/raster scanlines and validate framing,
symbol structure and checksum/parity. They must not import Okapi, product
encoder code, generated encoder patterns, or obtain the answer from text.
Negative controls must corrupt bars, checksums and geometry and fail.

The recorder must generate into a fresh directory, retain actual PDFs,
reopened geometry/text observations, independent decode results, qpdf syntax
output, pinned PDFium renders, tool versions and SHA-256 hashes. Visual
profiles use white sRGB at 288 DPI. Module boundaries must lie within one
pixel of their predeclared transformed geometry; raster decoding must recover
the same payloads. Independent reference rasters, if used, must come from
standards/decoder-side expectations, never the product encoder's output.
Missing required tools or evidence are INDETERMINATE, including standards
evidence and open compatible-status Dependency Gates. Independent decoding
gaps do not satisfy #31.

The independent reference PDF is authored from the ZXing/standards module
oracle and calculated font positions using existing Canvas/T19 commands.
It never invokes `DrawBarcode1D`. Both reference and product are rendered by
the pinned PDFium v0.11.2 / Chromium 7881 tool into 2448x3168 opaque 8-bit
RGB PNGs on a 612x792-point page. ImageMagick 7.1.2-30 compares every pair
with AE, fuzz 0%, threshold 0; red/white difference images are retained.
This is stricter than the one-pixel boundary envelope above. Every product
raster is separately decoded on a transformed scanline at half bar height;
postal decoding observes the upper/lower sampling grid and mandatory sum.
The reference shares only the existing Canvas/font capabilities, whose
compatible-status gates remain open; product encoder output is never a
reference input.

## Permitted sources

- [Reference Suite 7.2.6 barcode public API](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/barcodes/package-summary.html): inventory only; no source, resources, binary inspection or output golden.
- [GS1 General Specifications 21.0.1](https://ref.gs1.org/standards/genspecs/21.0.1/): fixed public encoding tables, retail checksums, add-on parity and Code128 codewords.
- [USPS archived DMM 204](https://pe.usps.com/Archive/NHTML/DMMArchive20171106/204.htm) and [Postal Bulletin 22083](https://about.usps.com/postal-bulletin/2002/pb22083.pdf): legacy POSTNET/PLANET structure and dimensions.
- [OkapiBarcode 0.5.6](https://repo.maven.apache.org/maven2/uk/org/okapibarcode/okapibarcode/0.5.6/): Apache-2.0 encoder, including its public/protected extension points; never an acceptance oracle.
- [ZXing 3.5.3](https://github.com/zxing/zxing/tree/zxing-3.5.3): independent permissively licensed readers and symbol tables.
- Microsoft OpenType [cmap](https://learn.microsoft.com/en-us/typography/opentype/spec/cmap), [hmtx](https://learn.microsoft.com/en-us/typography/opentype/spec/hmtx), and [head](https://learn.microsoft.com/en-us/typography/opentype/spec/head): independent source-font character mappings, advances and bounds.
