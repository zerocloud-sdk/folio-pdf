# T31 two-dimensional barcode Acceptance Profile

Declared before T31 product generation for #32, against implementation/review
baseline `060cee07a230ab1c8194efce265615934d619ff2`. This is an acceptance
declaration, not a compatibility determination. The approved product seam is
`DocumentWorkflow.execute`: immutable declarations, Commands and Queries,
publication, reopened public PDF Values and independent decoding, in both
IN_PROCESS and HARDENED_WORKER. No encoder internals are a test seam.

## Reference Suite inventory

The following inventory comes only from the public 7.2.6 API documentation.
It specifies behavior to implement independently, not reference algorithms or
byte-identical output. No iText source, resources, binaries or output are used.

| Family | Required generation modes and controls | Native behavior / evidence obligation |
| --- | --- | --- |
| QR | Automatic numeric, alphanumeric, byte and Shift_JIS Kanji segmentation; L/M/Q/H error correction; character-set selection | All four segment kinds decoded independently; exact selected ECC (no automatic strengthening); explicit ECI for nondefault byte encodings |
| QR character sets | Cp437, Shift_JIS, ISO-8859-1 through -16, UTF-8 | Each defined character set needs a non-ASCII round trip. ISO-8859-12 was never defined and is an explicit unsupported encoding. No replacement of unrepresentable text; malformed Unicode fails |
| DataMatrix ECC200 | AUTO, ASCII, C40, TEXT, Base256, X12, EDIFACT, RAW | Explicit compaction choices and caller-supplied data codewords; generated padding, ECC and placement; raw input must end in ASCII state |
| DataMatrix extensions | ECI, Macro 05/06, FNC1, Structured Append, reader programming | Typed Native Interface declarations preserve control semantics without adopting the reference extension mini-language; independently observe payload and control codewords/metadata |
| DataMatrix size | Auto, width-constrained, height-constrained and exact width/height; whitespace | All 30 ECC200 sizes below; invalid sizes and insufficient capacity fail before publication |
| DataMatrix test-only option | Validate and report dimensions without painting | A public Query performs the same bounded generation and reports detached dimensions; no page mutation |
| PDF417 | Automatic text/numeric/byte compaction, forced binary, raw codewords, Macro PDF417 | Independently decode each form, all byte values in binary input, and segment/file metadata |
| PDF417 correction and size | Auto ECC or explicit 0–8; aspect-ratio sizing, fixed columns, fixed rows, fixed rectangle; relative row height | Exact explicit ECC; finite geometry and deterministic sizing; no silent loss of requested bounds |
| PDF417 bitmap inversion | Affects the reference raw bitmap only | No bitmap API is introduced by this vector ticket; record as unmapped in Facade Surface, with this reason |
| All three | Module dimensions, reusable Form XObjects, explicit affine placement and color | Published/reopened Forms contain PDF paths with bounded boxes, no barcode images/fonts; repeated equivalent declarations reuse a resource across placements/pages |

The public Native Interface is project-owned and immutable. Runtime encoder
selection remains OkapiBarcode 0.5.6, supplemented only where its public
extension points cannot express a required mode. ZXing 3.5.3 remains solely
a test/acceptance dependency. Any supplemental implementation must record its
permissively licensed or original provenance and must have independent tests.

## Fixed input and geometry expectations

Dimensions use PDF default-user-space points. The origin is the lower-left
corner of the complete box, including quiet zones. Defaults are module width
1, module height 1 (PDF417 row height 3), and quiet zones on every side of
4 points for QR, 1 for DataMatrix and 2 for PDF417. QR modules are square;
DataMatrix module width/height can be selected independently. A PDF417 row
must be at least three module widths high. Quiet-zone minima scale with the
module width (and with DataMatrix module height vertically).

| Fixture | Expectation fixed before product generation |
| --- | --- |
| QR `01234567`, `FOLIO 31`, `folio-31`, Shift_JIS `漢字` | Numeric, alphanumeric, byte and Kanji respectively; version 1 has a 21×21 matrix; L/M/Q/H selected independently |
| QR version 1 with 1-point modules and quiet zone 4 | 29×29-point box; three 7×7 finders, separators, timing, dark module and independently valid format/ECC/data bits |
| QR UTF-8 `Folio 二维 😀` | Exact Unicode scalar sequence recovered with ECI 26; version 3 fixed for this fixture; no normalization or decoder charset hint |
| DataMatrix ASCII `AB` | 10×10; data words 66,67,129; five ECC words; 12×12-point full box |
| DataMatrix ASCII `123456` | 10×10; data words 142,164,186; five ECC words |
| DataMatrix C40 `ABCABC`, TEXT `abcabc`, X12 `ABC>12`, EDIFACT `ABCD12`, Base256 bytes 0,128,255 | Explicit mode latch 230/239/238/240/231; exact payload; 16×16 fixed matrix for each compaction fixture |
| DataMatrix RAW `[66,67]` | Same payload as ASCII `AB`; padding and ECC generated independently of caller words |
| DataMatrix Macro 05/06 `ABC` | Decoded prefix `[)>` RS `05`/`06` GS, payload `ABC`, suffix RS EOT |
| DataMatrix Structured Append | Positions 1 and 2 of 2, file identifier 75; exact sequence/control words and separate `PART1`/`PART2` payloads |
| PDF417 AUTO `FOLIO 31`, numeric `12345678901234567890123456789012345678901234`, BINARY bytes 0–255 | Exact payload recovered; text/numeric/byte codewords independently observed |
| PDF417 RAW `[1]` | Text compaction pair `AB`; descriptor, padding, ECC and row indicators generated |
| PDF417 fixed 3 columns × 8 rows, ECC 2, `FOLIO 31` | Matrix 120 modules wide including start/stop/indicators, 8 rows; 8 ECC words; full box 124×28 points at width 1, row height 3, quiet zone 2 |
| PDF417 Macro | File ID `001075`, segments 0 and 1 of 2; `PART1`/`PART2`; last-segment marker only on segment 1 |

DataMatrix sizes (width×height) and data/ECC word capacities are the standard
ECC200 set: 10×10 3/5; 12×12 5/7; 18×8 5/7; 14×14 8/10;
32×8 10/11; 16×16 12/12; 26×12 16/14; 18×18 18/14;
20×20 22/18; 36×12 22/18; 22×22 30/20; 36×16 32/24;
24×24 36/24; 26×26 44/28; 48×16 49/28; 32×32 62/36;
36×36 86/42; 40×40 114/48; 44×44 144/56; 48×48 174/68;
52×52 204/84; 64×64 280/112; 72×72 368/144; 80×80 456/192;
88×88 576/224; 96×96 696/272; 104×104 816/336;
120×120 1050/408; 132×132 1304/496; 144×144 1558/620.
Each fixed size must decode a literal payload; capacity boundaries need both
successful and overflowing examples. QR versions range from 1 through 40.
PDF417 bounds are 1–30 columns, 3–90 rows and at most 928 total codewords.

Native PDF417 aspect ratio is physical matrix height/width, excluding quiet
zones. Zero retains the encoder's compact automatic sizing. A positive ratio
selects the fitting legal rectangle with the smallest absolute ratio error,
then the smallest codeword area (ratio ties within 1e-12). It is exclusive of
fixed row/column controls. For `AB`, ECC 0 and default units, automatic sizing
is 1 column × 4 rows; fixed 2 columns gives 3 rows; fixed 8 rows gives 1 column.
Explicit aspect ratios 0.25, 0.5 and 1 give 3 columns × 10, 20 and 40 rows
respectively, with 120-module matrix width. These expectations precede the
aspect-ratio implementation; no reference output is used.

The non-ASCII QR inputs for ISO-8859 parts 1–11 and 13–16 are respectively
`Folio é`, `Folio Ł`, `Folio Ħ`, `Folio ĸ`, `Folio Ж`, `Folio ع`,
`Folio Ω`, `Folio א`, `Folio ğ`, `Folio ĸŊ`, `Folio ก`, `Folio Ė`,
`Folio Ẁ`, `Folio €`, `Folio Ș`. Each uses version 2, ECC L, a 25×25
matrix, ECI part+2 except the default ISO-8859-1, and a 33×33-point box.

Each family is placed using identity, `(1,0,0,1,36,144)`, rotation
`(0,1,-1,0,240,72)` and scale `(2,0,0,0.5,24,96)`. Thus local `(x,y)`
becomes `(240-y,72+x)` in the rotation profile and `(24+2*x,96+0.5*y)`
in the scale profile. Coordinate/box tolerance is 0.0001 point. Physical
scanner certification of arbitrary affine transforms is not implied.

## Independent evidence and failures

Reopened public Queries must establish Form reuse, path-only content, boxes,
module grids and placement. An independent decoder must consume those actual
modules, validate the data/ECC and recover the literal expected payload.
Multiple valid masks/automatic segmentations are permitted; a matrix must
satisfy the declared family/version/compaction/control/ECC rules, rather than
match the chosen encoder's private pattern tables. Negative controls corrupt
payload, ECC, finder/timing patterns, quiet zones and geometry and must fail.

The actual PDFium raster is also independently decoded, including transformed
symbols, without reading captions or product metadata for the answer. Visual
geometry uses 288 DPI, a 612×792-point page, opaque white sRGB, one-pixel
maximum module-edge displacement and zero unexpected painted modules. Pinned
ImageMagick comparisons retain their inputs, threshold and difference images.
Syntax evidence invokes pinned qpdf. The fresh T31 evidence directory retains
tool versions, source/input/actual artifact SHA-256, PDFs, rendered PNGs,
actual findings and chain records; existing evidence is preserved.

The executable corpus is declared in
[`T31BarcodeProfile.java`](../../pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/T31BarcodeProfile.java):
224 pages per execution profile, including all 40 QR versions, all 30 ECC200
sizes, all 18 supported encodings in each family, all explicit compactions,
control headers, nine PDF417 ECC levels, maximum-capacity inputs and the
literal placement/unit/color cases. The automatic PDF417 ECC fixture fixes
3 columns and 8 rows, isolating ECC selection from automatic sizing; separate
ECC-0 fixtures exercise the automatic size expectations above. Fixture source
and original character-set resources are hashed with each evidence run.

The final corpus also retains twelve literal RAW regressions from independent
review. Four ECC200 fixtures cover explicit EDIFACT termination with automatic,
fixed and trailing-group capacity: `[240,5,240]` is `A` at 12×12,
`[240,124,66]` is `A` at 12×12, and
`[66,67,240,4,32,196,5,240]` is `ABABCDA` at 32×8. Eight PDF417 fixtures
use 3 columns, 8 rows and ECC 2 to cover leading/consecutive ECI, charset
changes within literal bytes, complete 924 groups and an explicit text latch.
Their complete raw codeword prefixes and decoded payloads are literal source
declarations. The PDF417 fixture ECI field describes a leading ECI; in-stream
charset controls are separately proved by those full prefixes and independent
payload decoding. The regression inputs preceded their product repairs and
this extended declaration precedes the fresh retained recording.

Every matrix is qualified before it may define a visual reference. The
independent checks cover exact QR function modules, both BCH format/version
copies, unmasked remainder bits, segment/ECI declarations and padding;
ECC200 region borders, unused modules, independent placement/deinterleaving,
control headers and ECC; and PDF417 start/stop patterns, every row indicator,
cluster, descriptor and ECC. No symbol may require error correction during
qualification. All payloads must equal the literal corpus expectations.
Only then does a separate Canvas program paint row runs from those qualified
modules, using the literal profile's dimensions, color and placement. This
allows valid masks and automatic segmentations while keeping the geometry
reference independent of the barcode painter. It is not an unvalidated
product-output golden, and a corrupted matrix cannot be used as a reference.

The raster decoder receives only the actual PNG and its literal fixture. It
extracts a fresh matrix using inverse placement, repeats the independent
matrix qualification, and checks all pixels within the declared box, including
quiet margins and colors. Full-page ImageMagick comparisons detect paint
outside that box. These integer-aligned fixtures require AE 0 at fuzz 0%, a
stricter realization of the one-pixel maximum edge tolerance. Nineteen public
DocumentPatch negative PDFs cover a different valid payload, data and ECC
corruption, finder/timing/format modules, quiet-zone paint, movement, scale,
rotation, clipping, and PDF417 row clusters/indicators. One ECC200 control changes only randomized
padding and recomputes ECC with independent ZXing arithmetic: exact decoded
payload and zero corrections must still fail qualification. Controls must be rejected
semantically, fail independent raster decoding, and produce positive visual AE.

Unsupported encoding names/ECIs, unrepresentable characters, malformed Unicode,
inapplicable options, malformed raw words, invalid/nonfinite dimensions,
singular placement, capacity overflow and resource exhaustion must return
stable checked Document Failures with safe diagnostics. Failure tests retain
an existing publication target and verify its bytes are unchanged. Resource,
signature protection, permissions and publication contracts remain mandatory.

UTF-8 is explicitly ECI 26 over strict UTF-8 bytes, with exact Unicode scalar
round trips proved separately for each family. It is not a claim about scanners
which ignore ECI, Unicode normalization, or reference implementation output.
Missing mandatory tools are INDETERMINATE; undecoded required modes are an
implementation gap, never completion. Independent standards certification and
open compatible-status Dependency Gates remain INDETERMINATE/experimental.

## Permitted references

- [QR public API, 7.2.6](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/barcodes/BarcodeQRCode.html),
  [DataMatrix public API, 7.2.6](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/barcodes/BarcodeDataMatrix.html),
  [PDF417 public API, 7.2.6](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/barcodes/BarcodePDF417.html): mode/option inventory only.
- [OkapiBarcode 0.5.6](https://repo.maven.apache.org/maven2/uk/org/okapibarcode/okapibarcode/0.5.6/): Apache-2.0 encoding implementation and extension points; never an acceptance oracle.
- [ZXing 3.5.3](https://github.com/zxing/zxing/tree/zxing-3.5.3): Apache-2.0 independent decoder, symbol metadata and standards tables.
