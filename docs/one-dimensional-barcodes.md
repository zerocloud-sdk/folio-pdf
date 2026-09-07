# One-dimensional vector barcodes

`Barcode1D` and `DrawBarcode1D.version1` add one barcode to an existing,
one-based page through `DocumentWorkflow.execute`. The capability is
`composition.barcodes.one-dimensional`. Bars are black PDF paths authored by
Folio PDF; OkapiBarcode 0.5.6 supplies private symbol encoding. Backend types,
barcode fonts, raster images and decoder dependencies are absent from the
Native Interface.

The complete mode, input, checksum and independent-decoder inventory is in the
[T30 Acceptance Profile](../capabilities/profiles/T30-one-dimensional-barcodes.md).
This capability remains experimental. Passing local tests does not satisfy
the independent standards or compatible-status Dependency Gates.

## Drawing

```java
FontLimits fonts = FontLimits.builder()
        .maximumFontSources(1).maximumSourceBytes(1024 * 1024)
        .maximumCodePoints(128).maximumFallbackChecks(128)
        .maximumGeneratedContentBytes(65536).build();
BarcodeText label = BarcodeText.builder(
        FontSelection.explicit(FontSource.path(fontPath)), fonts, 8)
        .position(BarcodeText.Position.BELOW).gap(3).build();
Barcode1D barcode = Barcode1D.builder(Barcode1D.Mode.EAN13, "590123412345")
        .moduleWidth(1).barHeight(48).quietZone(11)
        .guardExtension(5).supplement("05").humanReadable(label).build();
new DocumentWorkflow().execute(WorkflowRequest.create(output, SaveMode.REWRITE), session -> {
    session.execute(AddBlankPage.INSTANCE);
    session.execute(DrawBarcode1D.version1(1, barcode,
            CanvasMatrix.of(1, 0, 0, 1, 36, 144)));
    return null;
});
```

The example uses types from `net.zerocloud.pdf`, `net.zerocloud.pdf.command`,
`net.zerocloud.pdf.composition` and its `command` package. `fontPath` is the
caller's selected font and `output` is a publication path. Omitting
`humanReadable` emits bars alone and accesses no font source.

Declarations are immutable. Builders record intent; workflow execution
validates input before applying the command. No whitespace trimming, digit
filtering, implicit zero padding or replacement of invalid input occurs.

## Encoding and checks

Code128 accepts ASCII 0–127 and the explicit `Barcode1D.FNC1` through `FNC4`
markers. Backslash text such as `\\<FNC1>` remains literal. A, B and C constrain
the available code set; AUTO may switch sets. C requires representable digit
pairs and function positions. At least one data character is required. FNC4
must precede an ASCII data character, or pair with another FNC4 followed by
data; a pair changes the extended-character state. AUTO uses A/B throughout
input containing explicit FNC4, preserving upper shifts and latches across
numeric runs; those digits are not compressed into C pairs. A dangling
function shift or latch is rejected. The weighted modulo-103 check and stop
are generated.

GS1-128 uses bracketed application identifiers, for example
`[01]09501101530003[10]LOT`. Each field must contain data. Okapi 0.5.6's fixed
AI dictionary validates field structure, character classes and lengths;
Folio does not certify GS1 business identifiers, their internal check digits,
dates or conformance to later dictionary editions. Initial and necessary
separating FNC1 symbols are encoded. AUTO/A/B/C remain subject to
representability in the selected set.

Raw Code128 accepts start 103/104/105 and contextual words 0–102 through
`Barcode1D.rawCode128(104, 33, 34)`. The caller omits checksum and stop. SHIFT
must be followed by a data symbol in the alternate A/B set; code-set latches
and FNC4 transitions must have a following operand. Raw arrays are copied.
They represent symbols rather than UTF-16 characters. Raw labels require
explicit alternate text.

`generateChecksum(true)` applies only to Code39/Full ASCII Code39 (modulo 43),
Codabar (modulo 16 including input guards), Interleaved 2-of-5 (GS1 modulo 10)
and MSI (Luhn modulo 10). It is false by default. ITF requires even input
length without the check and odd input length with it. No other MSI check
schemes are declared by this profile.

EAN-13, EAN-8, UPC-A and UPC-E accept either their exact data length or the
complete number with a verified check digit. UPC-E includes number system 0
or 1 and six compressed digits; invalid compression forms are rejected.
Two- and five-digit supplements preserve leading zeroes and encode their
parity without appending a digit. Every retail mode can append either
supplement length. The same input contracts apply to each component.

POSTNET accepts 5, 9 or 11 data digits; PLANET accepts 11 or 13. Both generate
the digit that makes the digit sum divisible by ten, and full-height frame
bars. These are legacy symbol formats; the profile does not determine postal
service eligibility.

## Dimensions and placement

All dimensions are PDF default-user-space points before placement. The local
origin is the left edge of the reserved horizontal box at the bar baseline.
The first bar starts at `quietZone`; bars rise toward positive y. A command
reserves geometric margins; callers place it where existing page ink and
subsequent content preserve those margins.

| Property | Default and contract |
| --- | --- |
| Module width | 1 point; postal modes 1.44 points |
| Full bar height | 48 points; postal modes 9 points |
| Horizontal quiet zone | 10 points at each side; EAN-13 11 points; postal modes 9 points |
| Minimum quiet zone | 10 modules, EAN-13 11 modules; postal modes 9 points |
| Wide-to-narrow ratio | 2; configurable from 2 through 3 for Code39, Full ASCII Code39, Codabar and ITF |
| Retail guard extension | 0; `guardExtension` extends the main symbol's guards below y=0 |
| Supplement gap | 9 modules between the two painted components; supplement bars use the declared full height |
| Postal pitch | 3.24 points between successive bar starts; must exceed module width |
| Postal short height | 3.6 points; must be positive and below full bar height |

Module width, bar height, quiet zone, postal dimensions and font size must be
finite, positive and at most 1,000,000 points. Guard extension and label gap
are finite, nonnegative and at most 1,000,000. Mode-specific options on other
modes are rejected. Dimensions are explicit: changing module width does not
automatically increase quiet zones or postal pitch.

Placement is any finite, nondegenerate `CanvasMatrix` admitted by Canvas's
numeric bounds. `(1,0,0,1,36,144)` translates the local origin;
`(0,1,-1,0,240,72)` maps `(x,y)` to `(240-y,72+x)`. The same transform applies
to bars and label. Arbitrary scaling, shear and rotation describe PDF geometry;
scanner certification and fitting within the page are caller concerns.

## Human-readable text

`BarcodeText` requires a `FontSelection`, complete `FontLimits` and point size.
It reuses the [T19 font contract](font-loading.md), including explicit ordered
fallback, source ownership, embedding, subsetting and Unicode mappings.
Labels use unshaped scalar-order text even when paragraph shaping is selected.
No system or network fonts are discovered.

The default label is centered below the horizontal bar box, including quiet
zones. LEFT and RIGHT align its advance-width box to the corresponding edge;
a caption wider than the bar box may extend outside it. The default gap is
3 points from the closest bar edge to the selected fonts' bounding box.
Below-bar placement accounts for retail guard extension and font ascent;
above-bar placement accounts for full bar height and font descent. Changing
the label never changes the encoded bars.

| Mode | Default label and options |
| --- | --- |
| Code128 | Caller text; controls become `\xNN`, explicit functions become `<FNCn>` |
| GS1-128 | Bracketed AI notation rendered with parentheses |
| Raw Code128 | `alternateText` is required |
| Code39 / Full ASCII | Original data; `showChecksum(true)` includes an optional generated check; `showStartStop(true)` adds `*` guards |
| Codabar | Data without guards; `showChecksum(true)` includes a generated check; `showStartStop(true)` retains caller guards |
| Retail | Complete digits including mandatory check |
| Supplements | Exact digits; combined retail labels join both components with one space |
| ITF / MSI | Original digits; `showChecksum(true)` includes a generated optional check |
| POSTNET / PLANET | Data and mandatory generated check digit |

Full ASCII Code39 controls use the same printable `\xNN` notation.
`alternateText` replaces the whole caption, including combined supplements,
without changing encoding. It must be nonempty and fit the same label bounds.
Start/stop display is valid only for Code39 and Codabar families. Mandatory
checks remain visible regardless of the optional-check display flag.

## Failure and workflow behavior

Version 1 admits at most 256 input UTF-16 units or 256 raw words, 1,600 bars
per encoded component, and 1,024 caption UTF-16 units. Okapi's additional
ordinary Code128/GS1 capacity is an 80-unit encoding-cost budget (including
the initial code-set selection, data and shifts) and 170 units after its
input preprocessing; GS1's generated initial FNC1 is additional. For example,
forced B admits 79 ordinary ASCII letters and rejects 80. Capacity and
representability rejection by that fixed encoder is BARCODE_INPUT_INVALID.
Raw symbols and literal-backslash Code128 use the bounded explicit-symbol
path instead: raw input has at most 256 caller words and literal text at most
256 caller units, subject to the generated-bar bound. These are separate,
documented capacity profiles. Existing Canvas, font, preservation and
Workflow Resource Policy bounds also apply.

`BARCODE_INPUT_INVALID`, `BARCODE_MODE_INVALID`, `BARCODE_GEOMETRY_INVALID`
and `BARCODE_LIMIT_EXCEEDED` classify barcode-specific failures. Invalid page
selection, signature policy and modification permission failures carry the
barcode capability. Reused font, Canvas preservation, resource-policy and
terminal failures retain their existing codes and capability identities.
Backend exception messages are not exposed.

Bars and labels are prepared before the target page is changed. A propagated
failure leaves publication targets unattempted and preserves their existing
bytes. A caller that catches a nonterminal label failure can observe unchanged
page contents and choose a different command. This does not relax terminal
resource failure behavior.

Both IN_PROCESS and the existing Linux HARDENED_WORKER envelope support the
command, explicit fonts and bounded transport. Unsigned INCREMENTAL
publication preserves the source revision. Existing signatures and password
modification permissions are enforced before font bytes are read. The
Migration Facade does not yet map this Native Interface capability.
