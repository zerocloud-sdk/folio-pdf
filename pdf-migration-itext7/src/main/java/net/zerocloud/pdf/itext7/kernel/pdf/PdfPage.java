package net.zerocloud.pdf.itext7.kernel.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.zerocloud.pdf.Annotation;
import net.zerocloud.pdf.AnnotationAppearance;
import net.zerocloud.pdf.GoToAction;
import net.zerocloud.pdf.PageActions;
import net.zerocloud.pdf.command.UpdateActions;

/**
 * Retains a page's identity within its owning document's Native Session.
 *
 * @since 0.1.0
 */
public final class PdfPage {

    static {
        FacadeClasspathGuard.requireSingleEdition();
    }

    private final PdfDocument document;
    private PdfIndirectReference reference;
    private PdfDictionary dictionary;

    PdfPage(PdfDocument document) {
        this.document = document;
    }

    PdfPage(PdfDocument document, PdfIndirectReference reference) {
        this.document = document;
        this.reference = reference;
    }

    void bind(PdfIndirectReference reference) {
        this.reference = reference;
    }

    /** @return the page dictionary through the validated Values interface */
    public PdfDictionary getPdfObject() {
        if (dictionary == null) {
            if (reference == null) {
                document.materializePageHandles();
            }
            dictionary = (PdfDictionary) reference.getRefersTo();
        }
        return dictionary;
    }

    /**
     * Reads this page's detached annotations in annotation-array order.
     * Limits apply to the complete document inspection before page selection.
     * @param maximumAnnotations nonnegative document-wide count bound
     * @param maximumAppearanceBytes nonnegative decoded appearance-byte bound
     * @param maximumAttachmentBytes nonnegative decoded attachment-byte bound
     * @return immutable selected annotations
     */
    public List<Annotation> getAnnotations(int maximumAnnotations,
            long maximumAppearanceBytes, long maximumAttachmentBytes) {
        int page = pageNumber();
        List<Annotation> selected = new ArrayList<Annotation>();
        for (Annotation annotation : document.getAnnotations(maximumAnnotations,
                maximumAppearanceBytes, maximumAttachmentBytes)) {
            if (annotation.getProperties().getPageNumber() == page) {
                selected.add(annotation);
            }
        }
        return Collections.unmodifiableList(selected);
    }

    /**
     * Creates or replaces an annotation on this handle's current page.
     * Its identifier is document-wide; replacing an identifier on another
     * page moves that annotation here. Other properties are retained.
     * @param annotation immutable annotation; its page number is adapted
     * @return this page
     */
    public PdfPage addAnnotation(Annotation annotation) {
        Objects.requireNonNull(annotation, "annotation");
        Annotation selected = FacadeAnnotations.onPage(annotation, pageNumber());
        document.updateAnnotations(Collections.singletonList(selected), Collections.<String>emptyList());
        return this;
    }

    /**
     * Replaces one normal appearance, retaining all other annotation properties.
     * Uses a document-wide read bound of 100,000 annotations and 8 MiB each
     * of decoded appearance and attachment bytes before the atomic update.
     * Replacement follows Native annotation ordering: the selected item is
     * appended after the retained items on its page.
     * @param identifier nonempty identifier belonging to this page
     * @param appearance non-null resource-free normal appearance
     * @return this page
     * @throws IllegalArgumentException if the identifier does not belong to this page
     */
    public PdfPage setNormalAppearance(String identifier, AnnotationAppearance appearance) {
        Objects.requireNonNull(appearance, "appearance");
        Annotation selected = annotation(identifier);
        return addAnnotation(FacadeAnnotations.withAppearance(selected, appearance));
    }

    /**
     * Removes an annotation belonging to this handle's current page.
     * Uses the same bounded document inspection as normal appearance editing.
     * @param identifier nonempty identifier belonging to this page
     * @return this page
     * @throws IllegalArgumentException if the identifier does not belong to this page
     */
    public PdfPage removeAnnotation(String identifier) {
        annotation(identifier);
        document.updateAnnotations(Collections.<Annotation>emptyList(), Collections.singletonList(identifier));
        return this;
    }

    private Annotation annotation(String identifier) {
        Objects.requireNonNull(identifier, "identifier");
        for (Annotation annotation : getAnnotations(100000, 8L << 20, 8L << 20)) {
            if (identifier.equals(annotation.getProperties().getIdentifier())) {
                return annotation;
            }
        }
        throw new IllegalArgumentException("The annotation identifier does not belong to this page.");
    }

    /**
     * Sets or removes one inert local GoTo binding on this handle's current page.
     * @param key only O (open) or C (close)
     * @param action the immutable local Action, or null to remove
     * @return this page
     * @throws IllegalArgumentException if the event key is not O or C
     */
    public PdfPage setAdditionalAction(PdfName key, GoToAction action) {
        String event = Objects.requireNonNull(key, "key").getValue();
        if (!"O".equals(event) && !"C".equals(event)) {
            throw new IllegalArgumentException("Only page-open O and page-close C Actions are supported.");
        }
        int page = pageNumber();
        UpdateActions.Builder update = UpdateActions.version1();
        if ("O".equals(event)) {
            if (action == null) {
                update.removePageOpenAction(page);
            } else {
                update.setPageOpenAction(page, action);
            }
        } else if (action == null) {
            update.removePageCloseAction(page);
        } else {
            update.setPageCloseAction(page, action);
        }
        document.executeDocument(update.build());
        return this;
    }

    /**
     * Reads this page's detached Actions under a document-wide binding bound.
     * @param maximumActions nonnegative total catalog/page binding bound
     * @return this page's bindings, or empty when it has neither binding
     */
    public Optional<PageActions> getAdditionalActions(int maximumActions) {
        int page = pageNumber();
        for (PageActions actions : document.getActions(maximumActions).getPageActions()) {
            if (actions.getPageNumber() == page) {
                return Optional.of(actions);
            }
        }
        return Optional.empty();
    }

    private int pageNumber() {
        if (reference == null) {
            document.materializePageHandles();
        }
        return document.pageNumber(reference);
    }
}
