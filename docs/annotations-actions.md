# Annotations and document Actions

T12 exposes annotations and local document navigation as immutable Native Interface values, `DocumentCommand` updates, and bounded `DocumentQuery` reads. Every operation runs inside `DocumentWorkflow.execute`; no PDFBox type, live backend object, callback, or executable action crosses the public seam.

## Version-1 annotation model

Every supported `Annotation` has a document-wide nonempty identifier, a one-based containing page number, a nonempty rectangle, optional contents, supported flags, and an optional normal `AnnotationAppearance`. Version 1 supports:

- Text annotations with the standard Comment, Key, Note, Help, NewParagraph, Paragraph, and Insert icons and an initial open state.
- Stamp annotations with a nonempty stamp name.
- Highlight annotations with one or more `AnnotationQuad` values and a Gray, RGB, or CMYK device color.
- FileAttachment annotations with an `EmbeddedFile`, its description, MIME subtype, AF relationship, and a Graph, PushPin, Paperclip, or Tag icon.
- standalone Widget annotations. T12 preserves their annotation geometry and appearance only; it does not create an AcroForm field, manage field values, or flatten forms.
- Link annotations represented either by a `Dest` entry or by the supported local GoTo Action.

`UpdateAnnotations` atomically creates or replaces annotations by identifier, moves an annotation when its replacement names another page, and removes selected identifiers. `Annotations` returns detached values in page and annotation-array order and requires caller-declared limits for the annotation count, decoded appearance bytes, and decoded attachment bytes.

## Action allowlist and non-execution rule

The complete version-1 Action allowlist is one local GoTo action dictionary with exactly `S = GoTo` and one `D` Navigation Target. A Navigation Target is either an explicit `PageDestination` or a reference to an existing named destination. The supported bindings are:

- the catalog `OpenAction`;
- page additional-actions `O` and `C` (page open and close);
- a Link annotation's `A` entry; and
- a Link annotation's direct `Dest` entry, which carries the same Navigation Target without an Action dictionary.

`UpdateActions` updates or removes these bindings atomically, and the bounded `Actions` query reads them as `DocumentActions` and `PageActions`. Folio PDF treats every Action as inert data and never follows a link, opens a URI, launches a process, evaluates JavaScript, reads an external file, or performs implicit network access. URI, JavaScript, Launch, GoToR, SubmitForm, named-action, chained `Next`, additional-action keys outside the listed bindings, and every other Action form are unsupported.

An unsupported Action graph may remain structurally unchanged when a rewrite does not need to interpret it, such as an annotation-only update. This is not a source-byte preservation promise: enclosing objects and serialization may still change. A page operation that must reason about annotation or Action targets rejects such a graph with `PRESERVATION_UNSUPPORTED` before mutation. The `Actions` query rejects it with `QUERY_FAILED`; replacing a binding explicitly with `UpdateActions` is permitted when the remaining graph is safe.

## Appearance and flattening contract

`AnnotationAppearance.version1` represents only the normal appearance (`AP/N`) as a Form XObject with an identity matrix, the supplied bounding box, and an empty Resources dictionary. An omitted Matrix and an explicit numeric identity Matrix are equivalent; non-identity and malformed matrices remain unsupported. Caller content is limited to 1 MiB and to resource-free graphics operators: graphics-state save/restore and numeric state, matrices, paths, painting, clipping, line dashes, rendering intent, and DeviceGray/RGB/CMYK color operators. Numeric graphics-state operands are checked semantically: widths and dash lengths are nonnegative, line cap and join values are integers from 0 through 2, miter limits are at least 1, flatness is from 0 through 100, device-color components are from 0 through 1, and a nonempty dash array cannot contain only zero lengths. Text, fonts, external objects, shadings, patterns, extended graphics state, inline images, marked-content properties, and malformed or unbalanced programs are rejected with `ANNOTATION_INVALID`; the query rejects an unproven appearance with `QUERY_FAILED`.

`FlattenAnnotations` accepts one or more identifiers atomically. Each selected annotation must be non-Widget and have a validated normal appearance. The engine adds that Form to page resources, encloses all pre-existing page content between dedicated `q` and `Q` streams so inherited graphics state cannot affect the new invocation, appends a geometry transform and `Do` invocation, and only then removes the annotation. Missing identifiers fail with `ANNOTATION_NOT_FOUND`; Widgets, missing appearances, malformed graphs, or unsafe page resources fail with `ANNOTATION_FLATTENING_UNSUPPORTED`. This is annotation flattening, not AcroForm field flattening.

## Page-operation policy

Supported annotation and Action graphs integrate with T10 and T11 as follows:

Page-operation validation classifies each annotation-array entry independently. Managed T12 annotations and T10's proven-safe legacy basic Text annotations may coexist in either order on one page: legacy entries remain structurally preserved while managed entries are decoded and retargeted. To retain document-wide uniqueness, copying a legacy Text entry with an optional identifier changes only the copy's identifier by the deterministic `-N` rule; a merge that encounters an unrenamable legacy identifier collision fails before mutating the target.

- insert and move preserve page identity, so containing-page and direct-target page numbers are resolved again after the operation;
- removal fails before mutation with `DESTINATION_CONFLICT` when a surviving managed annotation, catalog Action, or page Action targets a removed page; bindings owned by a removed page disappear with that page;
- copy keeps external targets on their original pages, retargets selected-range targets to the corresponding copies, copies page Actions, and gives copied annotation identifiers the first available deterministic `-N` suffix;
- merge offsets direct targets into each appended source, applies T11's deterministic named-destination collision renames to Link and Action targets, and applies the same `-N` collision rule to annotation identifiers. The primary document-open Action wins; otherwise the first appended source document-open Action is adopted;
- split keeps annotations and Action bindings only when both their containing page and target survive in that product. Direct targets are rewritten to product page numbers, named targets remain only when their named destination survives, and the document-open Action is omitted when its target is absent.

Removing a named destination with `SetNamedDestinations` fails atomically with `DESTINATION_CONFLICT` when a managed Link, catalog open Action, or page Action still refers to that name. Each complete managed-graph validation or retargeting pass used by annotation replacement or removal, page preservation, copy, merge, split, flattening, and this destination-removal check shares one document-wide budget and decodes at most 8 MiB of appearance content and 8 MiB of embedded-attachment content; exceeding either fixed bound fails safely before mutation.

T12 commands are available for unsigned `REWRITE` and `INCREMENTAL` workflows. Under T15's signed-Source `INCREMENTAL` policy, only `UpdateAnnotations` on supported non-Widget annotations is admitted by a sole coherent DocMDP P=3 permission; signed `REWRITE`, `UpdateActions`, and `FlattenAnnotations` are rejected. T12's local bounds compose with T20's transaction policy, and T21 transports the same closed commands and results when callers select the Hardened Worker profile. AcroForm behavior, form Actions, and form flattening remain downstream capabilities.

## Example

```java
Annotation link = Annotation.link(
        AnnotationProperties.version1(
                        "chapter-link",
                        1,
                        AnnotationRectangle.of(36, 700, 240, 724))
                .build(),
        LinkActivation.action(GoToAction.version1(
                NavigationTarget.toNamedDestination("chapter-two"))));

workflow.execute(request, session -> {
    session.execute(SetNamedDestinations.version1()
            .set("chapter-two", PageDestination.fit(2))
            .build());
    session.execute(UpdateAnnotations.version1().put(link).build());
    session.execute(UpdateActions.version1()
            .setDocumentOpenAction(GoToAction.version1(
                    NavigationTarget.toPage(PageDestination.fit(1))))
            .build());
    return session.query(Annotations.version1(64, 1024 * 1024, 1024 * 1024));
});
```

## Stable and inherited Preview Migration Facade

The mapped subset uses the existing immutable Native values (`Annotation`,
`AnnotationAppearance`, `GoToAction`, `NavigationTarget` and `PageActions`).
The [Facade Surface Manifest](../capabilities/facade-surface.yaml) distinguishes
adapted reference signatures from Folio extensions. Preview inherits the same
Stable source and tests; it adds no separate T12 members. Both editions execute
in `IN_PROCESS`, including when Native certification uses `HARDENED_WORKER`.

| Owner | Mapped operations | Contract |
| --- | --- | --- |
| `PdfDocument` | `getAnnotations(int,long,long)`, `updateAnnotations(List<Annotation>,List<String>)` | Detached ordered reads and one atomic replacement/removal Command. |
| `PdfPage` | `getAnnotations(int,long,long)`, `addAnnotation(Annotation)`, `removeAnnotation(String)` | A page handle follows its original page through reorder; creation adapts the value's containing page. |
| `PdfPage` | `setNormalAppearance(String,AnnotationAppearance)` | Replaces AP/N and retains all other properties. |
| `PdfCatalog` | `setOpenAction(GoToAction)`, `getOpenAction(int)` | Stores an inert direct/named local GoTo; null removes it; the read returns `Optional<GoToAction>`. |
| `PdfPage` | `setAdditionalAction(PdfName,GoToAction)`, `getAdditionalActions(int)` | Supports only O/C; null removes the selected binding; the read returns `Optional<PageActions>`. |
| `PdfDocument` | `getActions(int)`, `flattenAnnotations(String...)` | Bounded catalog/page bindings and atomic non-Widget flattening. |

Page reads apply the supplied bounds to the complete document before selecting
that page. Page removal and appearance edits use fixed bounds of 100,000
annotations and 8 MiB each of decoded appearance and attachment bytes. A missing
or other-page identifier is `IllegalArgumentException` for these page-owned
selections. Document-wide removal and flattening retain
`ANNOTATION_NOT_FOUND`. Malformed input and operational failures retain their
Native code and safe diagnostic as the `PdfException` cause; expired and
read-only owners reject edits. Returned values remain detached after close.

The last value supplied for a repeated replacement identifier wins at its first
declaration position. Replacements follow unselected entries on each page, so
appearance replacement may change that annotation's array position. Creating
an existing identifier on another page moves it to the handle's current page.
Link activation remains part of the Annotation value; `getActions` counts only
catalog/page event bindings.

For PDF 2.0 Sources and associated-file relationships, choose the explicit
version constructor. The two-argument Reader/Writer constructor retains its
PDF 1.7 default. This example edits a Text annotation and stores local navigation:

```java
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import net.zerocloud.pdf.Annotation;
import net.zerocloud.pdf.AnnotationAppearance;
import net.zerocloud.pdf.AnnotationProperties;
import net.zerocloud.pdf.AnnotationRectangle;
import net.zerocloud.pdf.GoToAction;
import net.zerocloud.pdf.NavigationTarget;
import net.zerocloud.pdf.PageDestination;
import net.zerocloud.pdf.PdfVersion;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfName;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfPage;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfReader;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;

try (PdfReader reader = new PdfReader("input.pdf");
     PdfDocument document = new PdfDocument(
             Collections.singletonMap("input", reader), "input",
             Collections.singletonMap("result", new PdfWriter("annotated.pdf")),
             PdfVersion.PDF_2_0)) {
    Annotation note = Annotation.text(
            AnnotationProperties.version1("review-note", 1,
                    AnnotationRectangle.of(36, 700, 72, 730))
                    .contents("Review this section").build(),
            Annotation.TextIcon.NOTE, false);
    PdfPage page = document.getPage(1);
    page.addAnnotation(note).setNormalAppearance("review-note",
            AnnotationAppearance.version1(AnnotationRectangle.of(0, 0, 12, 10),
                    "q 0 0 1 rg 0 0 12 10 re f Q\n".getBytes(StandardCharsets.US_ASCII)));
    GoToAction local = GoToAction.version1(
            NavigationTarget.toPage(PageDestination.fit(1)));
    document.getCatalog().setOpenAction(local);
    page.setAdditionalAction(new PdfName("O"), local);
    // To bake this supported appearance into page paint:
    // document.flattenAnnotations("review-note");
}
```

The [T12 certification contract](t12-certification.md) defines the independent
original corpus, rule controls, exact visual expectations and environment
bindings. Actual candidate readiness comes from
[Foundation evidence](../capabilities/foundation-evidence.yaml), including
transactions, values, pages and metadata for the same candidate.
