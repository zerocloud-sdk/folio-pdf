# Image extraction and Document Resource Inventory

T14 exposes bounded image extraction and a complete inventory of resources
declared by effective page Resources and nested Form XObject Resources. Call
`DocumentSession.query(ExtractImagesAndResources.version1(limits, byteAccess))`
inside `DocumentWorkflow.execute`. The query and every returned value use only
Folio PDF or JDK types.

`DocumentResourceInventory` is immutable and detached. Its metadata and any
explicitly selected byte data remain usable after the Document Session ends.
Byte arrays are copied both into and out of the result. The query materializes
all selected bytes before returning, so it publishes either one complete
result or a checked failure and never a successful prefix.

## Inventory scope and ordering

Version 1 starts with pages in one-based page-tree order and uses each page's
effective, possibly inherited, Resources dictionary. Resource-dictionary
categories and named-category dictionary keys are compared by decoded PDF name
in ascending Unicode order; `ProcSet` members retain declared array order.
Traversal is depth-first pre-order: a Form record is followed immediately by the records
reachable from that Form before the next sibling declaration. An image record
is followed by a newly discovered explicit image mask and then a newly
discovered soft mask. Repeated queries over the same Session state and a
rewrite followed by reopen retain this semantic order; serialized object
numbers and backend iteration order do not determine it.

The inventory records every declaration in the standard `ExtGState`,
`ColorSpace`, `Pattern`, `Shading`, `XObject`, `Font`, `ProcSet`, and
`Properties` categories. Unknown categories are retained as `OTHER`, and an
unknown XObject subtype is retained as `XOBJECT_OTHER`. A dictionary-valued
unknown category contributes its named members; another unknown value
contributes one category declaration. Version 1 recursively follows only Form
XObject Resources. An explicit PDF null category or named member is equivalent
to an omitted dictionary entry: it produces no declaration, record, or limit
charge, while non-null siblings retain their normal order. The optional `Type`
entry may be absent from an Image or
Form XObject, but when present it must be `XObject`; `Subtype` remains
mandatory. Pattern content, inline images in content streams, page
thumbnails, annotation appearance resources, and resources reachable only
from later capability contexts are not page/Form resource declarations and
are outside this query.

An indirect resource is represented by the existing Session-scoped
`ObjectReference`. All declarations of that same indirect object are folded
into its first ordered record, and its Page Usage is the ascending union of
pages that reach any declaration. The reference remains usable for equality
after Session closure under the normal Object Reference contract.

A direct resource has no Object Reference. Version 1 emits one record for each
ordered page/Form resource declaration occurrence and does not infer identity
from a backend object instance. A direct mask embedded in a deduplicated image
is instead the one deterministic target of that owning relationship and
accumulates the relationship's reachable declarations. Its first
`ResourceDeclaration` is a stable location in this result, not fabricated PDF
object identity. A declaration contains its page
number and an ordered path of category/name segments from the page Resources
dictionary through any Forms or image-mask relationship. Declarations of a
deduplicated indirect resource retain encounter order.

Page Usage means declaration reachability. It does not parse page content to
prove that a `Do`, `Tf`, color, pattern, or other operator executes the
resource. An indirect resource declared in an inherited page Resources
dictionary therefore lists every page that inherits it. Each direct
declaration occurrence remains a separate page-local record. A resource in a
shared Form lists every page from which that Form is reachable when its stable
indirect identity permits those occurrences to be folded.

## Image metadata, filters, and color

`ImageResource` reports positive integral width and height, the declared bits
per component, and the resolved number of color components when version 1 can
determine it. An Image Mask has one component and one bit per component. For a
JPX image that legitimately omits PDF-level component or bit-depth data,
the unavailable field remains explicitly empty; Folio PDF does not decode an
unbounded codestream merely to invent metadata. Other missing, non-integral,
out-of-range, or contradictory dimensions and bit-depth values are malformed.
`CCITTFaxDecode` and `JBIG2Decode` require one-bit samples;
`RunLengthDecode` and `DCTDecode` require eight-bit samples. A Flate or LZW
predictor above 1 must declare the image's bit depth, component count, and
width as its effective `BitsPerComponent`, `Colors`, and `Columns` geometry.
These constraints are checked even when decoded bytes are not selected or the
codec itself is outside version-1 decoding.

Filters are returned in the exact declared sequence. Each
`ImageResource.Filter` retains the declared filter name, whether version 1
can decode it under Folio PDF's bounded adapter, and the effective predictor,
color, bit, column, or `EarlyChange` values that participate in supported
Flate decoding or describe a declared LZW stream. Inline-image filter
abbreviations are not valid on Image XObject streams and fail the query. A
malformed filter or `DecodeParms` shape fails the query rather than silently
changing the sequence. An empty filter array is a valid unfiltered sequence;
an accompanying parameter array must then also be empty. When exactly one
filter is declared, its parameters are one dictionary even if `Filter` uses
array syntax. Multiple filters use a same-length parameter array whose entries
are dictionaries or null. Within a Flate or LZW parameter dictionary, an
explicit PDF null for `Predictor`, `Colors`, `BitsPerComponent`, `Columns`, or
`EarlyChange` has the same effective default as an omitted entry.

Version 1 bounded decoded-byte access supports an unfiltered stream and
sequences composed of `ASCIIHexDecode`, `ASCII85Decode`, `RunLengthDecode`,
and `FlateDecode`.
`DCTDecode`, `JPXDecode`, `CCITTFaxDecode`, `JBIG2Decode`, and `Crypt` remain
inventory-visible but report decoded bytes as `UNSUPPORTED_FILTER`;
`LZWDecode` is likewise inventory-visible in version 1 while its bounded
strict decoder remains unavailable. Unknown
filter names are malformed. This restriction avoids invoking codecs whose
internal pixel or native allocation cannot be stopped by the query's output
stream limit. It is an explicit version-1 limitation, not a claim that the
underlying PDF filter is invalid.

Color information separates a declared name from the resolved family and
reports `SUPPORTED`, `UNSUPPORTED`, or `MALFORMED`. Version 1 recognizes
DeviceGray, DeviceRGB, DeviceCMYK, CalGray, CalRGB, Lab, ICCBased, Indexed,
Separation, and DeviceN families, resolves named page/Form color
resources without mutating them, and reports their component count when the
declared graph proves it. Calibrated dictionaries, ICC component/Alternate/
Range entries, and string-valued Indexed lookup lengths are structurally
validated. ICCBased remains `UNSUPPORTED` because version 1 does not parse and
certify the ICC profile payload or prove that its header and ranges agree with
the wrapper dictionary. An Indexed stream lookup is `UNSUPPORTED` because
version 1 does not decode that auxiliary stream merely to certify its length.
Separation and DeviceN retain their recognized family and component count but
are `UNSUPPORTED` after bounded outer tint-function validation because version
1 does not recursively certify or evaluate arbitrary PDF functions. Thus
`UNSUPPORTED` means that version 1 has not established complete structural
validity or interpretation for the declared graph; it is not a whole-graph
well-formedness claim. Unknown families are also `UNSUPPORTED`; missing names,
invalid arity or scalar kinds, impossible component counts, known special
spaces used where Separation/DeviceN alternates require a device or CIE-based
space, invalid DeviceN attribute subtypes, duplicate or forbidden DeviceN
component names, malformed known entries, the
inline-image-only `I` color-space abbreviation, Pattern color spaces (which
Image XObjects cannot use), unresolved aliases, and cyclic aliases are
`MALFORMED`.
DeviceGray, DeviceRGB, and DeviceCMYK are supported both as names and in their
standards-defined one-element array forms. Extra array elements make those
otherwise parameterless families malformed.
Malformed color information remains safely classified on its image record with
no proven component count; it does not turn backend coercion into a supported
result.

Decoded image bytes are the bytes after the declared PDF filter sequence.
They are not an RGB raster, a reconstructed JPEG/JPX file, rendered pixels,
or data with color conversion, `/Decode`, masks, or matte processing applied.
Consequently a safely supported filter sequence may expose decoded sample
bytes even when higher-level color interpretation is unsupported; callers
must consult both fields.

## Masks and relationships

`ImageResource.isImageMask()` distinguishes an image-mask stream from an
ordinary image. An ordinary image can separately expose one explicit mask and
one soft mask. An explicit mask is either a color-key range array or another
`ImageResource`; a soft mask always targets another `ImageResource`. The
target is the same detached inventory record returned in resource and image
order, so its optional Object Reference and declarations provide the stable
relationship without inventing identity for a direct stream.

For a JPX-filtered image, `ImageResource.getEmbeddedSoftMask()` reports the
validated `SMaskInData` state as `NONE`, `SOFT_MASK`, or
`PREBLENDED_SOFT_MASK`. Codes 1 and 2 cannot coexist with a subsidiary
`SMask`, and an Image Mask cannot declare an embedded soft mask. The entry is
meaningless for a non-JPX filter sequence and is therefore ignored and
reported as `NONE` there.

Mask image records inherit the Page Usage of every owning image occurrence.
Shared indirect masks are deduplicated normally. Invalid mask kinds, wrong-
arity, non-integral, out-of-bit-depth, or reversed color-key ranges,
ordinary images used as explicit masks, non-gray soft-mask images,
contradictory image-mask metadata, `/SMask /None`, a missing soft-mask bit
depth, nested `Mask` or `SMask` entries, malformed soft-mask Decode,
Interpolate, or Matte values, self masks, and cyclic mask graphs fail safely
before a result is returned. A soft-mask Matte array additionally requires the
mask dimensions to match its owning image, has the owning color space's
preblended component count, and keeps every value within that component's
declared or default range. Indexed images use the base color-space component
ranges rather than index values for this check.

## Byte selection, availability, and lifecycle

`ImageByteAccess` selects `NONE`, `ENCODED`, `DECODED`, or
`ENCODED_AND_DECODED` for the whole query. Selection and availability are
independent: every image reports encoded and decoded availability even when
the caller selects neither. An available but unselected representation has no
returned bytes. A selected unsupported representation remains empty and
retains its stable availability reason; it does not cause the query to return
backend output under a misleading label.

`AVAILABLE` describes source and a recognized version-1 byte path, not a
promise that an unselected compressed payload has already been decoded,
validated, or fits the caller's byte policy. Once selected, its content must be
valid for that path and the complete query must fit every applicable pixel,
decompression, and returned-byte limit or it fails atomically.

Encoded bytes are the raw encoded bytes of the stream held by the opened
document after any document-level decryption. They are not guaranteed to be a
byte slice of the original source file, and they exclude the PDF stream
dictionary and `stream` delimiters. An external-file stream is inventoried but
reports both byte representations as `EXTERNAL_STREAM`; Folio PDF does not
resolve a file specification, URI, or network location during inspection. The
external `F` entry is dereferenced and must be a string or file-specification
dictionary (whose optional `Type` is `Filespec`). A direct or indirect PDF
null is treated as absent, so the ordinary `Filter` and `DecodeParms` entries
remain authoritative for the in-file stream.

Selected supported bytes are eagerly detached under the query limits. Both
the metadata and byte copies work after Session closure. There is no lazy
backend stream, open file, Session callback, or PDFBox object retained in the
result.

## Fonts

Every Font resource is returned as `FontResource` with its declared subtype,
BaseFont name when present, embedding state, subset state, optional six-letter
subset prefix, declarations, optional Object Reference, and Page Usage.
Type 0 fonts obtain their embedding state from their one declared descendant;
Type 3 fonts are document-embedded through their CharProcs. FontFile,
FontFile2, and FontFile3 entries count as embedded only when they are streams.
CIDFontType0 and CIDFontType2 dictionaries are accepted only as the single
descendant of a Type 0 font; either subtype used directly in a resource Font
dictionary is malformed.
Unknown well-formed subtypes remain visible as `UNSUPPORTED` with embedding
`UNKNOWN`; malformed Type, descendant, descriptor, embedded-program, BaseFont,
or CharProcs structures terminate the query safely.

Subset identity is document metadata, not a font-file checksum. A BaseFont
name beginning with exactly six uppercase ASCII letters and `+` reports that
prefix and the remaining name. Other names are non-subset names. The stable
document identity of an indirect Font resource is its Object Reference; a
direct Font is location-only and is not assigned synthetic identity.

## Limits and failures

Every version-1 `ResourceExtractionLimits` field is mandatory and
nonnegative. Resource graph depth is capped at
`ResourceExtractionLimits.MAXIMUM_RESOURCE_TRAVERSAL_DEPTH_VERSION_1` (`64`)
and uses bounded recursion under that hard ceiling rather than unbounded JVM
recursion.
A zero permits an empty corresponding dimension and rejects its first value.
The exact meanings are:

- pages and page-tree nodes have the same iterative, inherited-resource,
  parent, count, cycle, and exact-boundary semantics as T13;
- traversed resource values count every non-null page/Form Resources category
  entry, every non-null named resource, ProcSet member, and non-null unknown-
  category dictionary member;
  each inspected color node and color-array member, including DeviceN
  component names; every filter name, DecodeParms array member and parameter-
  dictionary entry; every external file-specification dictionary entry; every
  color-key mask value; every validated soft-mask Decode or Matte array
  member; and every validated Type 3 CharProcs entry. Other image and font
  scalar dictionary fields are validated but do not add a second charge beyond
  their resource declaration. A shared
  Form reached more than once is charged on
  each occurrence even though its indirect resource records remain
  deduplicated. Each non-null dictionary entry is checked against the remaining
  budget before it is added to the deterministic ordered copy;
- resource traversal depth is one for a declaration directly in page
  Resources and increments for each nested Form or image-mask relationship.
  Named and composite image color-space graphs are independently rooted at one
  and use the same ceiling for each alias or child color-space step;
- decoded pixels count `width * height` once for each returned image record
  whose decoded bytes are selected and supported, including mask images.
  Multiplication and aggregation are overflow-checked before decoding;
- decompressed bytes count every byte emitted by every supported filter stage.
  An unfiltered decoded stream is one stage. Intermediate stages therefore
  consume the budget even though only the final stage can be returned; and
- returned bytes count every encoded array and every final decoded array
  placed in the detached result. Selecting both representations charges both,
  even when an unfiltered stream makes their contents equal. Defensive copies
  made later by result getters do not consume Session policy again.

An exact declared boundary succeeds. The first excess, including arithmetic
overflow, fails with `EXTRACTION_LIMIT_EXCEEDED`, capability
`document.images-resources.extract`, and the fixed diagnostic “The image and
resource extraction limit was exceeded.” Filter stages write only through a
bounded output owned by Folio PDF, so an excess is observed before an
unbounded result buffer is materialized.

Malformed page trees, resource category shapes, Forms, filters, dimensions,
masks, fonts, or resource graphs, including active Form or mask cycles, fail
with `QUERY_FAILED` and the fixed diagnostic “The document images and
resources could not be extracted safely.” No backend exception, filename,
resource name, content, or partial result is exposed. Repeated acyclic graphs
remain legal but are charged on every traversal occurrence, so the mandatory
value bound terminates hostile fan-out deterministically.

The query observes all supported Commands that precede it in the same
Document Session. It is read-only, never resolves external stream locations,
does not mutate the live PDF graph, and does not publish unless the Workflow
Request explicitly names a Target. A no-Target workflow has no Publication
Receipt and leaves Source bytes unchanged.

## Example

```java
ResourceExtractionLimits limits = ResourceExtractionLimits.builder()
        .maximumPages(100)
        .maximumPageTreeNodes(1_000)
        .maximumTraversedResourceValues(100_000)
        .maximumResourceTraversalDepth(32)
        .maximumDecodedPixels(100_000_000L)
        .maximumDecompressedBytes(256L * 1024L * 1024L)
        .maximumReturnedBytes(256L * 1024L * 1024L)
        .build();

WorkflowOutcome<DocumentResourceInventory> outcome = workflow.execute(
        request,
        session -> session.query(ExtractImagesAndResources.version1(
                limits,
                ImageByteAccess.ENCODED_AND_DECODED)));
```

These are example application bounds, not universal safe defaults.
Comprehensive process, memory, time, concurrency, decompression-ratio, and
hostile-input enforcement remains T20/T21 scope. T14 adds no inline-image
content extraction, rendering, image embedding, optimization, drawing,
incremental publication, signature behavior, encryption, or Migration Facade
surface.
