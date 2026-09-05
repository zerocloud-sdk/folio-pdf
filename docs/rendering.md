# Bounded page rendering

`Rendering` is the project-owned Conversion capability `conversion.rendering`.
Its public seam is `DocumentWorkflow.execute` with the version-1 `RenderPage`
Query. The bundled default Provider is `folio.pdfbox-renderer` (PDFBox 3.0.8).
PDFBox types and the Worker transport are private implementation details.
The capability remains **experimental**; see the [evidence record](../capabilities/evidence/T23-page-rendering.md).

```java
WorkflowRequest request = WorkflowRequest.builder()
        .source("input", DocumentSource.path(Paths.get("input.pdf")))
        .primarySource("input")
        .saveMode(SaveMode.REWRITE)
        .build();

new DocumentWorkflow().execute(request, session -> {
    RenderOptions options = RenderOptions.builder().dpi(144).build();
    try (RenderedPage page = session.query(RenderPage.version1(1, options))) {
        page.writePngTo(callerOwnedOutputStream);
    }
    return null;
});
```

Use `executionProfile(HARDENED_WORKER)` on the same Workflow Request for hostile
multi-tenant documents. Resource policy, deadlines, cancellation, credentials,
Provider preferences, and shared-environment concurrency admission retain their
existing contracts. Rendering requires extraction permission for a password-
protected document opened with a user credential. It does not authorize edits
to a signed document or change PDF publication security.

## Selection, ordering, and lifetime

Pages are one-based. Each Query renders exactly the current selected page and
observes all earlier completed Commands, including font-subset finalization.
`Rendering.renderPages(session, pages, options, consumer)` evaluates pages in
the caller's declared order, including duplicates. It closes each result after
the caller-thread consumer returns, then renders the next page. The array must
not be changed during the call. An empty selection performs no rendering.
Each Query is its own ordering barrier: Commands run by a consumer affect later
pages. Selection is not a document snapshot taken before all consumers run.

The streaming guarantee is bounded **page-at-a-time** rasterization and PNG
consumption. It is not tile rendering or an unlimited-size-page guarantee.
One page's complete raster is needed while rendering; its PNG is then stored
in transaction-owned temporary storage. `writePngTo` uses an 8-KiB buffer. A
caller may hold multiple results, and their staging remains charged until each
is closed or the callback ends. Width, height, page number, and diagnostic
metadata are detached; byte access is thread-confined and expires at either
boundary with `RENDER_RESULT_EXPIRED`. Close is idempotent. Unclosed results
are expired before `WORK_COMPLETED` progress and before PDF publication.
If `close()` is called while an already-started `writePngTo` is copying on the
owner thread, later consumption is rejected immediately, the active copy may
finish, and the PNG staging is released when that final reader closes.

Consumption never closes or flushes the caller's stream. I/O failure reports
`RENDER_OUTPUT_FAILED` and possible partial PNG bytes. Caller runtime exceptions
propagate unchanged. Previously consumed bytes cannot be rolled back. This is
immediate consumption, not a Publication Target; it creates no Publication
Receipt. Any PDF Targets in the surrounding Workflow still use staged
validation, Path replacement, stream partial-write reporting, and ordered
`COMMITTED`/`FAILED`/`NOT_ATTEMPTED` receipts. A rendering failure escaping the
callback aborts those publications.

## Geometry and declared profiles

| Option | Default | Version-1 behavior |
| --- | --- | --- |
| DPI | 72 | Positive finite dots per physical inch |
| scale | 1 | Positive finite multiplier in addition to DPI |
| page box | `CROP` | Effective CropBox, or effective MediaBox with `MEDIA` |
| crop | absent | Explicit unrotated user-space x/y/width/height, wholly inside the selected box |
| color | `RGB` | sRGB samples; `GRAY` stores equal sRGB components using the luma rule below |
| background | `0xffffff` | A 24-bit sRGB value used by `OPAQUE` |
| alpha | `OPAQUE` | `PRESERVE` keeps straight alpha and ignores the background option |
| annotations | `SHOW` | Visible existing normal appearances in viewing intent; `HIDE` omits all annotations |

PDF rectangle coordinates are converted to binary32, matching the document's
geometry representation. Converted rectangles must remain finite and have
positive width and height. With `u` the effective page UserUnit,
`s = binary32(dpi / 72 * scale * u)`. Each unrotated dimension is
`max(1, floor(binary32(points * s)))`. A 90- or 270-degree page rotation swaps
the resulting axes. Thus a 20-by-10 point page at 144 DPI and scale 1.25 is
50-by-25 pixels; a 7.5-by-4.5 crop at scale 1.5 is 11-by-6 pixels before rotation.
There is no implicit fit-to-width or aspect-ratio adjustment. A dimension or
pixel product outside the positive Java raster address space fails before
allocation with `RENDER_DIMENSIONS_EXCEEDED`.

Invalid page numbers produce `PAGE_RANGE_INVALID`. Nonfinite or nonpositive
DPI/scale, invalid 24-bit backgrounds, and nonfinite, empty, or out-of-box crops
produce `RENDER_OPTIONS_INVALID`. These operational validations occur at the
Workflow seam in both profiles. Null object arguments remain programming errors.

The page is rendered onto transparent ARGB, then the PNG writer applies the
declared output policy. Opaque channels use integer straight-alpha composition
`(channel * alpha + background * (255 - alpha) + 127) / 255`. Gray uses
`(299*r + 587*g + 114*b + 500) / 1000` after any background composition. PNGs
contain 8-bit RGB or RGBA, an sRGB chunk, no interlacing, and no ImageIO output
cache. Raster dimensions and pixels are the contract; compressed byte identity
across JVM implementations is not.

Hidden, NoView, and Invisible annotations are omitted. Print-only visibility
is not selected. A visible annotation without a usable existing normal
appearance is omitted with `ANNOTATION_APPEARANCE_MISSING`; Folio does not
request new appearances merely to display missing ones. A rotated NoRotate
appearance for which the backend requires transparency reconstruction fails
with `RENDER_FAILED`, preserving the original appearance. Actions are inert
document data and are never executed.

## Diagnostics and limits

`RenderedPage.getDiagnostics()` returns a deduplicated closed vocabulary, also
aggregated into `WorkflowOutcome.getDiagnostics()`. Executed nonembedded or
damaged fonts report `FONT_SUBSTITUTED`; missing executed glyph outlines report
`GLYPH_SUBSTITUTED`. JPEG/JPX/JBIG2 decoding (including image masks) reports
`PLATFORM_IMAGE_CODEC`, explicitly disclosing platform codec selection and any
installed replacement rather than implying a pinned runtime decoder. The safe
profile permits one such platform codec as the terminal image filter. Its
encoded header dimensions must equal the admitted PDF declaration before the
platform decoder runs; inline images, resource images, and masks use the same
rule. Resource streams begin at raw bytes. Every filter before the terminal
platform codec writes to quota-controlled temporary staging and charges its
complete emitted output before the encoded header is inspected. A successful
Document Patch to an image stream invalidates its earlier format decision and
causes this admission to run again; newly emitted prefix bytes remain
cumulative. A codec or rendering failure is `RENDER_FAILED`; it is not accepted
as a silently blank page. Checked rendering hooks reject known incomplete
backend states, including a transparency group without a bounding box,
independently of logger configuration.

Diagnostics contain no font/resource names, filenames, document text,
annotation contents, or backend exception objects. During the active rendering
thread the default JUL integration also filters PDFBox and FontBox warning
records and converts the recognized fallback-font warning to the closed
diagnostic. Applications that replace JUL, install a different logging bridge,
or add handlers concurrently must apply equivalent log redaction at that
boundary. Logger delivery is never used as the sole semantic validation.
Missing-font rendering is not compatibility evidence: independent profiles use
only embedded, hash-pinned project fonts. No network engine or font download is
implicit.

All operations share the Workflow ledger. Every rendered viewport consumes
width times height decoded pixels, even for a duplicate page or a later Query.
Existing decoded image resources keep their prior shared accounting. Pixel
charges are cumulative, not refunded when a PNG is closed. The requested ARGB
page buffer is reserved at four bytes per pixel for its holding period; PNG
encoding reserves three 8-KiB buffers and a 13-byte header; consumption reserves
8 KiB; each open result accounts 512 bytes of modeled metadata. Provider and
Worker envelopes, copies, and transfer peaks are additionally charged. Raster
storage is released before the Query returns. This remains the established
**modeled Folio-owned memory** contract, not full JVM heap, PDFBox internal
allocation, ImageIO/native allocation, or RSS isolation.

PNG staging, Provider snapshots, existing PDFBox spill, and retained outputs
share temporary storage. The Worker obtains authenticated, bounded temporary-
storage grants from the parent before growth and releases them at the actual
holding boundary, just as it obtains memory grants. Parent-owned Source
snapshots are borrowed by the child without a duplicate storage charge. Result
transport uses the existing authenticated bounded/chunked value protocol; a
logical result still needs its bounded encoded payload in memory. Transport
cannot reset the page, pixel, memory, or temporary-storage budget.

Numeric resource boundaries are inclusive and first excess is terminal, even
if callback code catches the initial failure. The default path checks stop
state before and after rendering, during content operators, PNG rows/chunks,
and stream consumption. In-process backend and caller work remains cooperative;
the Hardened Worker adds the existing process watchdog. Rendering uses the
same shared-environment concurrency permit as the surrounding Workflow.

## Replacement Provider envelope

Register adapters through `WorkflowEnvironment.builder().provider(...)`. Explicit
Provider preferences never fall back. An unqualified rendering selection checks
registrations in order, skips unavailable or unauthorized remote registrations,
then uses the built-in renderer. The built-in ID is reserved. Registered metadata
is exposed by the environment as before; the built-in facts are available from
`Rendering.getDefaultProviderMetadata()`. The actual selection is reported in
the Workflow Outcome, including implicit default use.

Alternate engines execute in the caller process in both Workflow profiles.
PDF work needed to prepare their input remains inside the selected Workflow
boundary. A remote registration requires capability-scoped
`authorizeRemoteDisclosure(Rendering.CAPABILITY_ID)` before selection or content
disclosure. The common Provider contract also checks this on the actual request.
Adapters retain their own declared duration enforcement and isolation obligations;
a PDF Worker does not sandbox caller-side Provider code.

The byte format is project-owned, versioned, big-endian, and contains no class
names or executable values. The `ProviderRequest` capability is
`conversion.rendering`. Its payload is:

1. 32-bit magic `0x46525131` (`FRQ1`).
2. 32-bit expected raster width and height.
3. IEEE-754 64-bit effective scale (the exact binary32 scale promoted to double).
4. 32-bit annotation policy: 0 SHOW, 1 HIDE.
5. 32-bit PDF byte length followed by exactly that many bytes.

The PDF selects one page, has the effective rectangle as its MediaBox/CropBox,
preserves page rotation, and sets UserUnit to 1 because the effective scale
already includes it. It is an unencrypted rendering snapshot and can include
resources and objects reachable from that page, including linked page data;
disclosure consent applies to document content, not just visible pixels.
The Provider renders page 1 onto transparent sRGB, honoring annotation policy,
without applying the final background or gray conversion.

The `ProviderResult` payload is 32-bit magic `0x46525331` (`FRS1`), width,
height, a diagnostic bitmask, then exactly width*height*4 bytes of top-to-bottom,
left-to-right straight RGBA. Diagnostic bits follow `RenderDiagnostic` declaration
order (bits 0 through 3). Unknown bits, dimensions, magic, length, or samples
outside this fixed byte format fail. Providers must report substitutions through
the applicable bits. Folio validates the returned dimensions and byte envelope,
accounts adoption/copies, applies the same final color/background policy, and
stages the PNG. Its parser never accepts Provider-supplied diagnostic strings.

## Evidence and promotion

The [T23 contract suite](../pdf-document/src/test/java/net/zerocloud/pdf/consumer/RenderingWorkflowContractTest.java)
uses the same public behavior in both profiles. Independent acceptance renders
the actual public output, compares it to pinned PDFium with pinned ImageMagick,
and retains raw results and difference images. Three fixed profiles cover
colored page content plus annotation appearance, the existing T18 image/color/
transparency corpus, and the existing T19 embedded-font corpus. Missing tools
or unresolved renderer disagreement are `INDETERMINATE`, never `PASS`.
Independent standards evidence and compatible prerequisite/platform gates remain
open; tests and a passing visual profile do not promote this capability.
