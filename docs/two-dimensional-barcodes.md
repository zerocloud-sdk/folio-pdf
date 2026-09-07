# Two-dimensional vector barcodes

`Barcode2D`, `DrawBarcode2D.version1` and `MeasureBarcode2D.version1` provide
QR Code Model 2, DataMatrix ECC200 and standard PDF417 through
`DocumentWorkflow.execute`. The capability is
`composition.barcodes.two-dimensional`. Each symbol is an indirect, reusable
PDF Form XObject containing filled paths. Declarations and detached size
results expose no backend, barcode font, image or decoder types.

This capability remains experimental. The
[T31 Acceptance Profile](../capabilities/profiles/T31-two-dimensional-barcodes.md)
records the complete Reference Suite mode/option inventory, literal inputs,
geometry and independent checks. Its public 7.2.6 API references supply that
inventory only. The implementation and acceptance oracles use no iText source,
resources, binaries or output. Passing the implementation profile does not
satisfy independent standards certification or compatible-status Dependency
Gates, and does not certify physical scanners.

## Drawing and measuring

```java
Barcode2D barcode = Barcode2D.builder(Barcode2D.Mode.QR, "Folio 二维 😀")
        .encoding("UTF-8")
        .qrErrorCorrection(Barcode2D.QrErrorCorrection.Q)
        .qrVersion(3)
        .moduleWidth(2).moduleHeight(2).quietZone(8)
        .build();
new DocumentWorkflow().execute(WorkflowRequest.create(output, SaveMode.REWRITE), session -> {
    Barcode2DSize size = session.query(MeasureBarcode2D.version1(barcode));
    session.execute(AddBlankPage.INSTANCE);
    session.execute(DrawBarcode2D.version1(1, barcode,
            CanvasMatrix.of(1, 0, 0, 1, 36, 144)));
    return size;
});
```

Types come from `net.zerocloud.pdf`, its `command` package,
`net.zerocloud.pdf.composition`, and the composition `command` and `query`
packages. `output` is the caller's publication path. Pages are one-based and
must exist before drawing. Measurement needs no page, paints nothing, and
returns versioned, detached matrix dimensions and complete point dimensions,
including quiet margins. It performs the same bounded encoding and validation
as drawing.

Builders record intent and `build()` takes an immutable snapshot, including
copies of arrays. Null references are rejected at construction. Other input,
mode and numeric validation occurs during Workflow execution before the page
changes. Input text is exact: there is no trimming, normalization, silent
replacement, implicit charset fallback or function-escape mini-language.
Equivalent encoded vectors with equal local geometry and color share a Form
within the Document Session, including across pages. Placement does not enter
the reuse identity. Reopening a published PDF preserves those shared indirect
references. Callers can draw separate text with existing Composition commands.

## Strict character encodings and ECI

| Character set | Native names / ECI |
| --- | --- |
| IBM code page 437 | `Cp437`, `IBM437`; ECI 2 |
| ISO-8859-1 through -11 and -13 through -16 | `ISO-8859-n`; ECI n+2 |
| Shift_JIS | `Shift_JIS`, `Shift-JIS`; ECI 20 |
| UTF-8 | `UTF-8`; ECI 26 |

Names are case-insensitive and underscores normalize to hyphens. The default
is ISO-8859-1, ECI 3; its redundant ECI marker is omitted. Nondefault character
sets emit their explicit assignment. `.eci(number)` selects the same closed
set by assignment, replacing the encoding selection; the last `encoding` or
`eci` call wins. `getEncoding()` returns the recorded name or `ECI:number`,
not a canonicalized result. ISO-8859-12 is undefined and rejected, as are
unsupported names, assignments and inapplicable options.

Text must encode and decode back to exactly the caller's Unicode sequence.
Malformed UTF-16, unrepresentable characters and lossy aliases such as
Shift_JIS yen/backslash substitutions fail. Portable Unicode mapping tables
supply ISO-8859-10, -14 and -16 independently of JDK availability. UTF-8 means
strict UTF-8 bytes with ECI 26, including supplementary Unicode characters.
All three families have independent Unicode round-trip evidence; this does
not promise interpretation by scanners that ignore ECI or apply normalization.
RAW declarations contain codewords, so they require empty text and the default
encoding declaration. A supported raw ECI control describes already compacted
bytes; it does not transcode them.

## QR

QR automatically selects numeric, alphanumeric, byte and Shift_JIS Kanji
segments. `qrErrorCorrection` selects exactly L, M, Q or H, default L; the
encoder does not silently strengthen it. `qrVersion(0)` chooses the smallest
fitting version. Versions 1–40 can be fixed explicitly, giving a matrix of
`17 + 4 * version` square modules. Insufficient capacity fails. QR exposes no
caller-selected mask or raw-codeword mode; any valid automatic mask and
segmentation may satisfy the declared profile.

## DataMatrix ECC200

`dataMatrixEncoding` defaults to AUTO. Explicit modes are ASCII, C40, TEXT,
X12, EDIFACT, BASE256 and RAW. ASCII includes digit pairs and upper shifts.
C40/TEXT use triplets and valid ASCII remainders; X12 and EDIFACT require their
restricted alphabets. BASE256 encodes strict bytes, including all values
0–255. The adapter handles exact-capacity terminal compaction rules and the
standard Base256 length transition at 250 bytes. Invalid compaction input
fails instead of silently selecting another forced mode.

`dataMatrixSize(width,height)` uses module counts. Zero selects that dimension
automatically, so callers may constrain either dimension or both. The
[profile's 30-size ECC200 table](../capabilities/profiles/T31-two-dimensional-barcodes.md)
contains every admitted square and rectangular size and its data/ECC capacity.
ECC200 error correction follows the selected size; there is no arbitrary ECC
percentage setting or DMRE/Micro variant.

Typed headers replace the reference extension-string syntax:

| Declaration | Meaning and range |
| --- | --- |
| `dataMatrixMacro(MACRO_05 / MACRO_06)` | Encodes the standard macro control; decoding adds `[)>` RS `05`/`06` GS before the exact text and RS EOT after it |
| `dataMatrixFnc1(true)` | Adds a leading FNC1 without parsing or validating application identifiers; literal input otherwise remains exact |
| `dataMatrixReaderProgramming(true)` | Adds the reader-programming control, independently observed as metadata |
| `dataMatrixStructuredAppend(position,total,fileId)` | Position 1–total, total 2–16, file ID 1–64516; all zero disables the header |

The Native file ID is one-based. The reference API documents file IDs
0–64515: migrating that logical identifier requires adding one, and the
inverse mapping subtracts one. The Native sequence position remains one-based.
The payload of each segment stays separate; the library does not assemble or
split messages. Reader programming cannot combine with FNC1. Macro cannot
combine with reader programming or Structured Append. These typed headers
cannot also accompany RAW input. AUTO Macro combined with FNC1 or a nondefault
ECI uses ASCII compaction so every header is preserved; that combination does
not promise the smallest possible symbol. Explicit compactions remain explicit.

`Barcode2D.rawDataMatrix(66,67)` supplies data words for `AB`; padding, ECC and
placement are generated. Words are 0–255 and must form supported complete
ASCII/C40/TEXT/X12/EDIFACT/Base256 and header sequences ending in ASCII state.
Caller padding, stray unlatches, incomplete shifts/triplets/byte lengths,
unsupported ECIs and malformed control headers are rejected. RAW is a bounded
validated data stream, not arbitrary matrix modules or a bypass for malformed
symbols.

RAW EDIFACT termination depends on the selected data capacity: a decoder
implicitly returns to ASCII when at most two data words remain. Automatic
sizing reserves enough capacity to preserve an explicit EDIFACT sequence;
an incompatible fixed size fails with `BARCODE_INPUT_INVALID`. For example,
`[240,5,240]` represents `A` and selects 12×12 automatically. Fixing 10×10
would change its interpretation and is rejected.

## PDF417

`pdf417Encoding` defaults to AUTO, selecting text, numeric and byte compaction.
BINARY forces byte compaction, including all values 0–255. RAW accepts already
compacted data words through `Barcode2D.rawPdf417(...)`. For example, raw `[1]`
is text `AB`; `[901,0,128,255]` supplies three bytes. The library generates the
length descriptor, padding, row indicators, start/stop and ECC. Raw data words
exclude those generated fields. The validator admits supported complete text,
numeric, 901/924 byte, 913 byte-shift and 927 charset-ECI sequences. It rejects
truncated controls, invalid numeric/byte groups and embedded Macro controls;
use the typed Macro declaration with RAW data instead.

Charset ECI 927 preserves byte-compaction state. In mode 901 it cannot restart
grouping after a literal-byte tail; mode 924 still requires complete five-word
groups between charset changes. `[901,927,26,65]` decodes to `A`, while
`[901,65,927,26,300]` is rejected because 300 cannot be a literal byte.
An explicit 900 latch resumes text compaction.

`pdf417ErrorCorrection(-1)` selects automatic ECC (the default); 0–8 selects
exactly `2^(level+1)` ECC words. Automatic selection uses compacted payload and
Macro overhead: up to 40 words selects level 2, up to 160 level 3, up to 320
level 4, otherwise level 5. Capacity constraints still apply. Columns range
from 1–30, rows from 3–90, with at most 928 total codewords. Zero columns or
rows selects that dimension automatically. Fixing both selects an exact
rectangle; an insufficient rectangle fails rather than changing either bound.
The complete matrix is `69 + 17 * columns` modules wide and `rows` rows high.

`pdf417AspectRatio(ratio)` is the physical matrix height/width, excluding quiet
margins. Zero keeps compact automatic sizing. A positive finite ratio is
exclusive of fixed columns/rows; it selects the fitting legal rectangle with
the smallest absolute ratio error, then smallest codeword area for ties within
1e-12. Module and row dimensions participate in this calculation. This Native
geometry definition should be used explicitly when migrating sizing options;
reference bitmap heuristics and defaults are not a byte-identical contract.

`pdf417Macro(fileId,segment,count)` supports AUTO, BINARY and RAW, including
single-segment files. The file ID is concatenated numeric triplets `000`–`899`,
for example `001075`, not an arbitrary string. Segment is zero-based,
0–count-1; count is 1–99999. Segment count metadata is always emitted, and the
last-segment marker appears exactly on count-1. The file ID is limited to
2700 characters. The library does not split or assemble Macro files. The
reference bitmap-inversion option has no mapping in this vector interface;
standard PDF417 is supported, not MicroPDF417, truncated PDF417 or a bitmap API.

## Geometry, painting and resource bounds

All lengths are PDF default-user-space points before placement. Local `(0,0)`
is the lower-left corner of the complete box. Quiet margin is reserved equally
on all four sides. Drawing adds ink but does not paint a white background:
callers must place the box where earlier and later page content preserve its
quiet zone and contrast.

| Property | QR | DataMatrix | PDF417 |
| --- | --- | --- | --- |
| Default module width | 1 | 1 | 1 |
| Default module/row height | 1 | 1 | 3 |
| Shape constraint | Square modules | Independent width/height | Row height at least 3 module widths |
| Default quiet margin | 4 | 1 | 2 |
| Minimum quiet margin | 4 module widths | max(module width, module height) | 2 module widths |

Changing one length never implicitly rescales the others. Lengths and complete
boxes must be finite, positive and at most 1,000,000 points. Foreground defaults
to black; `foregroundRgb(r,g,b)` selects DeviceRGB components in [0,1]. There
is no alpha/background/ICC/spot-color declaration in this command. Sufficient
contrast and fitting on the page remain caller choices.

Placement uses a finite, nondegenerate `CanvasMatrix` within the existing
Canvas numeric bounds: components and transformed box corners have absolute
value at most 1,000,000,000, and determinant magnitude is at least 1e-12.
`(0,1,-1,0,240,72)` maps `(x,y)` to `(240-y,72+x)`; arbitrary affine scaling,
shear and rotation describe PDF geometry, with independent evidence for the
profile's translation, quarter-turn and nonuniform scale. A transformed symbol
need not remain physically scannable under arbitrary caller transformations.

Each declaration admits at most 8192 UTF-16 units or raw words and an encoding
name of at most 32 characters, in addition to family capacity limits. Encoding
uses an 8 MiB modeled owned-memory reservation, generated vector content is
bounded at 4 MiB, and each placement is bounded at 512 bytes. Session Form
cache entries are charged to Workflow resource accounting. The existing
Workflow Resource Policy, cancellation and deadline bounds remain effective;
barcode generation needs no decoded-image-pixel allowance.

## Failures, publication and execution profiles

| Failure code | Examples |
| --- | --- |
| `BARCODE_MODE_INVALID` | Unsupported charset/ECI, invalid ECC/header selection, or option on the wrong family |
| `BARCODE_INPUT_INVALID` | Empty/unrepresentable text, malformed raw controls, compaction or capacity overflow |
| `BARCODE_GEOMETRY_INVALID` | Invalid module/quiet dimensions, unsupported size, singular placement |
| `PAGE_RANGE_INVALID` | Drawing onto a page that does not exist |
| `BARCODE_LIMIT_EXCEEDED` | Declaration or generated-content bounds exceeded |

Invalid QR versions, DataMatrix sizes and PDF417 rows/columns produce
`BARCODE_GEOMETRY_INVALID`; invalid PDF417 ECC levels produce
`BARCODE_MODE_INVALID`. Callers should use the checked code and capability
rather than parse diagnostic text. Resource
failures preserve their existing Workflow codes and remain terminal even when
caught by a callback. Diagnostics do not expose private encoder exceptions or
input content.

Drawing and measurement support IN_PROCESS and the existing Linux
HARDENED_WORKER envelope. The Worker carries versioned declarations, commands,
queries and detached sizes, using its explicit class manifest and safe failure
catalog. No application callback, decoder, Charset provider or acceptance tool
is sent into the Worker. Existing modification permissions, signature
protection, save-mode and publication policies apply. Successful unsigned
incremental drawing retains the original revision. A propagated barcode
failure leaves existing publication targets unchanged with NOT_ATTEMPTED
receipts; caught nonterminal declaration failures do not leave partial paint.

## Independent evidence entry

The dedicated Maven profile runs only T31 recording:

```sh
./mvnw -B -ntp -pl pdf-acceptance -am -Pacceptance-t31-record \
  -Dacceptance.output="${PWD}/path/to/fresh-T31-evidence" verify
```

The aggregate `scripts/acceptance` entry also includes T31. Tool pins are
`scripts/qpdf-pin.properties`, `pdfium-pin.properties` and
`imagemagick-pin.properties`; the dedicated profile accepts `t31.qpdf.pin`,
`t31.pdfium.pin` and `t31.imagemagick.pin` overrides. The recorder runs in an
independent JVM with the full acceptance classpath so Java's charset SPI is
available to the independent decoders. The repository-only acceptance module
is not installed or deployed as a product artifact.

Missing mandatory tools are recorded as INDETERMINATE. A present tool's failed
rendering, undecoded required payload, structural mismatch or visual mismatch
fails the chain. Existing T31 records cannot be overwritten. Independent
standards and compatible-status Dependency Gates remain INDETERMINATE even
when syntax, semantic and visual chains pass. Recorder runs with tests skipped
exercise evidence production only; they do not replace the required unskipped
`./mvnw -B -ntp verify` and JDK 8/11/17/21 matrix.
