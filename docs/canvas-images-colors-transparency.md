# Canvas images, color, and transparency

T18 extends the existing low-level Canvas seam without adding a module or a
backend SPI. A caller builds an immutable `CanvasProgram.version2()` and
executes `DrawCanvas.version2(pageNumber, program, limits)` inside
`DocumentWorkflow.execute`. Version 2 retains the closed T17 vector and
positioned-glyph instructions and adds project-owned declarations for images,
painting color, alpha/blend state, and reusable transparency groups. Public
signatures contain only Folio PDF primitives and JDK byte arrays; mutable
inputs and outputs are defensively copied.

The command appends to the selected one-based page. It accepts no raw content
operators, caller command implementation, executable callback, arbitrary URI,
ImageIO provider object, PDFBox object, or implicit network input.

## Coordinates and added operations

Coordinates remain PDF default user space as defined by the T17 contract:
the origin is the lower-left of the unrotated page, one unit is 1/72 inch
before `UserUnit`, and page display rotation is not folded into the Canvas
matrix. `drawImage(image, matrix)` maps the image's unit square through the
six-number affine matrix. A matrix `(width, 0, 0, height, x, y)` therefore
places its lower-left corner at `(x, y)` with the declared width and height.
`drawTransparencyGroup` uses the same placement rule for the group's bounding
box coordinate system.

Version 2 adds this closed instruction set:

| Operation | Meaning |
| --- | --- |
| `setFillColor` | Set the nonstroking color used by later fill and text painting. |
| `setStrokeColor` | Set the stroking color used by later stroke and text painting. |
| `setTransparency` | Set nonstroking alpha, stroking alpha, and one standard blend mode. |
| `drawImage` | Paint one declared image through an explicit matrix. |
| `drawTransparencyGroup` | Paint one reusable Form XObject with a Transparency Group dictionary through an explicit matrix. |

As in version 1, the complete program must contain 1 through 10,000
instructions, matrices and coordinates must be finite with absolute value at
most 1,000,000,000, and the path, text, and graphics-state state machine must
balance before any page mutation. A version-1 command accepts only a
version-1 program; a version-2 command accepts only a version-2 program.

## Image input and PDF stream policy

`CanvasImage` owns a defensive copy of encoded or raw sample bytes. Equal
image declarations within one command share one indirect Image XObject.
`CanvasImage.existing` instead carries a same-Session indirect
`ObjectReference` and borrows that resource without copying or transcoding it.

| Input | Accepted profile | PDF handling | Observable output |
| --- | --- | --- | --- |
| JPEG | Structurally bounded 8-bit SOF0 baseline, SOF1 extended sequential, or SOF2 progressive data with one, three, or four components and positive dimensions. | The exact encoded bytes are retained behind one `DCTDecode` filter. Recognized JFIF/Adobe color-transform metadata is made explicit when present; four-component samples use the PDF inverse decode convention. No ImageIO decoder is invoked. | Original encoded stream bytes, dimensions, bit depth, DeviceGray/RGB/CMYK family, filter, decode metadata, and indirect identity survive reopen. |
| PNG | A single raster accepted by the JDK ImageIO PNG reader after PNG signature and IHDR checks. | Decoded through ImageIO and normalized through `BufferedImage.getRGB` to row-major 8-bit DeviceRGB samples, then encoded with `FlateDecode`. Source precision and non-sRGB color information may be lost. If any pixel is nonopaque, its normalized 8-bit opacity plane becomes a DeviceGray soft-mask Image XObject. Embedded source compression and ancillary chunks are not passed through. | RGB image dimensions and a subsidiary soft-mask relationship when nonopaque alpha exists. |
| TIFF | Exactly one raster accepted by an installed ImageIO TIFF reader after TIFF byte-order and magic checks. Multi-image TIFF is unsupported. | Decoded and normalized to the same 8-bit DeviceRGB plus optional DeviceGray soft-mask representation as PNG, then encoded with `FlateDecode`. Source precision, color information, TIFF compression, and metadata are not preserved. | RGB image dimensions, optional soft mask, and the stable optional-codec capability result. |
| Raw samples | Positive width and height, exactly 8 bits per component, a supported Canvas Color Space, and exactly `width * height * components` row-major bytes. | Samples are encoded losslessly with `FlateDecode`; no color conversion occurs. | Declared dimensions, bit depth, color components and color/profile identity. |
| Existing Image Resource | A live same-Session indirect Image XObject with positive integral dimensions, no external-file `F`, and either no filter, one `FlateDecode`, or one `DCTDecode` (a name or one-name array). | The original indirect stream, dictionary, encoded bytes, filters, color, Decode values, and mask relationships are borrowed unchanged. No decoding, fallback, or re-encoding occurs. | The T14 inventory retains the existing Object Reference and metadata; repeated placement adds operators, not new image objects. |

An existing resource is intentionally a narrow borrowing seam, not a decoder.
Applications that need its detailed color/filter/mask proof query
`ExtractImagesAndResources` first and pass the returned same-Session Object
Reference. Unfiltered, single-Flate, and single-DCT profiles are the complete
version-1 existing-resource set. Filter chains, JPX, LZW, CCITT, JBIG2, Crypt,
external streams, direct streams, and stale or foreign references are not
silently converted and are rejected.

`InspectCanvasImageCapabilities.version1()` returns a detached
`CanvasImageCapabilities` mapping. JPEG is `PASS_THROUGH`, PNG and an available
TIFF reader are `NORMALIZE_TO_DEVICE_RGB_8`, raw samples are `DIRECT_SAMPLES`, and
existing resources are `BORROWED_RESOURCE`. When no TIFF ImageIO reader is
installed, TIFF reports `OPTIONAL_CODEC_UNAVAILABLE`; executing a TIFF draw
then fails with `CANVAS_IMAGE_CODEC_UNAVAILABLE` before publication. The
optional TwelveMonkeys provider is discovered only through standard ImageIO
registration and its implementation type never enters the Native Interface.

## Color spaces

`CanvasColor` combines a `CanvasColorSpace` with exactly the required number
of finite components, each in the closed interval 0 through 1. The same
declaration can be used for fill, stroke, group color, or raw image samples.
Equal non-device declarations share one indirect resource or profile stream
within a command.

| Family | Components | Validation and PDF representation |
| --- | ---: | --- |
| DeviceGray | 1 | Direct device color operator/name. |
| DeviceRGB | 3 | Direct device color operator/name. |
| DeviceCMYK | 4 | Direct device color operator/name. |
| CalGray | 1 | Three-value WhitePoint with positive X/Z and Y exactly 1, nonnegative three-value BlackPoint, and positive finite scalar Gamma. |
| CalRGB | 3 | The same WhitePoint/BlackPoint rules, three positive finite Gamma values, and a finite nine-value Matrix. |
| ICCBased | 1, 3, or 4 as declared by the profile | At least 128 bytes; exact big-endian header length; `acsp` signature; JDK ICC parser acceptance; and Gray, RGB, or CMYK type/component agreement. The profile is embedded once with DeviceGray/RGB/CMYK Alternate. |

Malformed calibrated values, nonfinite or out-of-range components, malformed
ICC headers, and parser-invalid ICC data fail with
`CANVAS_GRAPHICS_INVALID`. A structurally valid ICC profile whose component
family is outside Gray/RGB/CMYK or is incompatible with the declared profile
shape fails with `CANVAS_RESOURCE_UNSUPPORTED`. The ICC bytes are charged
before parsing and are never included in a diagnostic.

T14 now validates bounded in-file ICCBased profile streams with the same
header, JDK parser, and component agreement rules. A valid profile makes the
image color status `SUPPORTED` and exposes an immutable `IccProfile` containing
the profile stream's optional Object Reference, exact decoded byte length, and
lowercase SHA-256. Invalid or externally referenced profiles retain T14's
`UNSUPPORTED` color outcome rather than exposing unverified identity. Profile
decoding consumes T14's decompressed-byte budget even when image bytes were
not selected; it does not consume the returned-image-byte budget.

## Alpha, masks, blend modes, and groups

`CanvasTransparencyState.version1(fillAlpha, strokeAlpha, blendMode)` accepts
finite alpha values from 0 through 1 and one of Normal, Multiply, Screen,
Overlay, Darken, Lighten, ColorDodge, ColorBurn, HardLight, SoftLight,
Difference, Exclusion, Hue, Saturation, Color, or Luminosity. Equal states
share one indirect ExtGState; setting a state again emits the required
operator but does not allocate another resource.

`CanvasMask.explicit` takes one packed, most-significant-bit-first sample per
pixel row (`ceil(width / 8) * height` bytes), records the requested normal or
inverted Decode convention, and creates a one-bit Image Mask. `CanvasMask.soft`
takes exactly `width * height` 8-bit opacity samples and creates a DeviceGray
soft mask. A mask must match its owning image dimensions. An image may have
one explicit mask or one soft mask, not both; decoded PNG/TIFF alpha also
occupies the soft-mask relationship. Equal declarations share the same mask
resource.

`CanvasTransparencyGroup.version1` owns a nonempty version-2 program, a finite
positive bounding rectangle, an explicit supported group color space, and
the isolated and knockout flags. It materializes as a reusable Form XObject
whose Group subtype is Transparency. Repeated placement of the same group
instance shares the Form, and nested groups are validated and materialized to
the caller's declared depth bound. Cyclic group programs, mismatched program
versions, and aggregate programs above 10,000 instructions are invalid.

## Mandatory resource limits

Every `CanvasResourceLimits` field is mandatory and nonnegative. Accounting
deduplicates equal reusable declarations before charging. An exact boundary
succeeds; the first excess and arithmetic overflow fail atomically with
`CANVAS_RESOURCE_LIMIT_EXCEEDED`.

| Limit | Exact charge |
| --- | --- |
| encoded image bytes | Sum of distinct JPEG, PNG, and TIFF caller inputs. Raw and borrowed streams do not consume it. |
| decoded image pixels | `width * height` for each distinct encoded or raw image. Borrowed resources are not decoded or charged. |
| decoded image bytes | JPEG's proven `pixels * components`, PNG/TIFF's normalized `pixels * 3`, and exact raw sample bytes. |
| ICC profile bytes | Sum of distinct embedded profile byte arrays. |
| mask bytes | Explicit/soft caller samples and a nonopaque PNG/TIFF decoded alpha plane, once per equal mask. |
| generated content bytes | Exact bytes generated for the page, every distinct group content stream, and the preservation isolation wrappers when existing content is present. The aggregate also has a hard 1 MiB ceiling. |
| resource declarations | Each distinct image, mask, Cal color space or ICC color-space/profile pair, transparency state, version-2 Font declaration, and each group instance. Repeated placement is not charged again. |
| transparency-group depth | Root page depth is zero; each nested group adds one. The caller value cannot exceed the hard version-1 ceiling of 16. |

The existing T17 limits also remain active: 10,000 aggregate instructions,
256 distinct Fonts, 1 MiB generated content, 8 MiB existing decoded page
content, graphics-state depth 64, and effective-resource inheritance depth 64.
These ticket-local bounds compose with T20's finite transaction-wide trusted
in-process policy. Neither layer is T21 Worker isolation.

## Preservation, publication, and failures

Before creating a stream or changing page Resources, Folio validates the
complete recursive program, numeric and state rules, encoded headers,
decoded geometry, ICC profiles, masks, existing Object References, resource
limits, existing content syntax, and inherited resources. Existing content
must be absent/null, one indirect in-file stream, or an array of indirect
in-file streams that passes the T17 preflight. Existing and generated content
are kept in separate balanced isolation scopes. Effective resources are
shallow-copied to the page only when required, unknown entries are retained,
and new private names are selected without collision. If this cannot be
proved, `CANVAS_PRESERVATION_UNSUPPORTED` is returned before mutation.

Unsigned REWRITE and INCREMENTAL requests admit version-2 `DrawCanvas`.
INCREMENTAL retains the complete primary Source as an unchanged prefix.
Every Existing Signature rejects Canvas drawing under the established T15
policy. A password-authenticated owner may modify; user authority requires
the general-modification permission. Failures retain the Document Workflow
transaction contract: all target receipts are `NOT_ATTEMPTED`, preexisting
target bytes and Source bytes remain unchanged, no partial result is exposed,
and diagnostics contain no byte content, filenames, paths, credentials,
resource names, provider classes, or backend exception text.

| Failure code | Fixed diagnostic category |
| --- | --- |
| `CANVAS_IMAGE_INVALID` | Malformed image bytes, dimensions, samples, mask relationship, or existing Image reference. |
| `CANVAS_RESOURCE_UNSUPPORTED` | A recognized image/color profile is outside the closed version-1 support matrix. |
| `CANVAS_IMAGE_CODEC_UNAVAILABLE` | The optional TIFF codec is unavailable. |
| `CANVAS_GRAPHICS_INVALID` | Invalid color, calibrated/ICC declaration, alpha, blend state, or group rectangle. |
| `CANVAS_RESOURCE_LIMIT_EXCEEDED` | A mandatory T18 bound or overflow was exceeded. |
| existing Canvas/workflow codes | Invalid closed program/state/Font, unsafe preservation, page/range, signature, permission, source, or publication failure. |

## Example

```java
CanvasResourceLimits limits = CanvasResourceLimits.builder()
        .maximumEncodedImageBytes(16L * 1024L * 1024L)
        .maximumDecodedImagePixels(20_000_000L)
        .maximumDecodedImageBytes(64L * 1024L * 1024L)
        .maximumIccProfileBytes(4L * 1024L * 1024L)
        .maximumMaskBytes(20L * 1024L * 1024L)
        .maximumGeneratedContentBytes(1024L * 1024L)
        .maximumResourceDeclarations(256)
        .maximumTransparencyGroupDepth(8)
        .build();

CanvasImage logo = CanvasImage.png(pngBytes);
CanvasProgram program = CanvasProgram.version2()
        .setFillColor(CanvasColor.rgb(0.1, 0.3, 0.8))
        .setTransparency(CanvasTransparencyState.version1(
                0.8, 1.0, CanvasBlendMode.MULTIPLY))
        .drawImage(logo, CanvasMatrix.of(144, 0, 0, 72, 36, 648))
        .build();

session.execute(DrawCanvas.version2(1, program, limits));
```

The values are application examples, not universal safe defaults. T18 adds no
font discovery or embedding, layout, inline-image parsing, patterns, shading,
SVG, Forms UI, tagged-document construction, redaction, optimization, page
rendering API, hostile multi-tenant guarantee, Worker isolation,
Migration Facade mapping, or certified-platform claim.
