package net.zerocloud.pdf.itext7.kernel.pdf;

import java.util.Optional;
import net.zerocloud.pdf.GoToAction;
import net.zerocloud.pdf.command.UpdateActions;

/** The document Catalog mapped to its project-owned dictionary view. @since 0.1.0 */
public final class PdfCatalog {
    static {
        FacadeClasspathGuard.requireSingleEdition();
    }

    private final PdfDictionary dictionary;
    private final PdfDocument document;

    PdfCatalog(PdfDocument document, PdfDictionary dictionary) {
        this.document = document;
        this.dictionary = dictionary;
    }

    /** @return the active Catalog dictionary */
    public PdfDictionary getPdfObject() {
        return dictionary;
    }

    /**
     * Sets an inert local document-open GoTo Action, or removes it when null.
     * An invalid target fails before changing the existing binding.
     * @param action the immutable local Action, or null to remove
     * @return this Catalog
     */
    public PdfCatalog setOpenAction(GoToAction action) {
        UpdateActions.Builder update = UpdateActions.version1();
        if (action == null) {
            update.removeDocumentOpenAction();
        } else {
            update.setDocumentOpenAction(action);
        }
        document.executeDocument(update.build());
        return this;
    }

    /**
     * Reads the local document-open Action under a document-wide binding bound.
     * @param maximumActions nonnegative total catalog/page binding bound
     * @return the detached Action, or empty when no binding exists
     */
    public Optional<GoToAction> getOpenAction(int maximumActions) {
        return document.getActions(maximumActions).getDocumentOpenAction();
    }
}
