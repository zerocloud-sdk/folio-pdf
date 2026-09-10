package net.zerocloud.pdf.itext7.kernel.pdf;

/**
 * Retains a page's identity within its owning document's Native Session.
 *
 * @since 0.1.0
 */
public final class PdfPage {

    static {
        FacadeClasspathGuard.requireSingleEdition();
    }

    private PdfDocument pendingOwner;
    private PdfIndirectReference reference;
    private PdfDictionary dictionary;

    PdfPage(PdfDocument pendingOwner) {
        this.pendingOwner = pendingOwner;
    }

    PdfPage(PdfIndirectReference reference) {
        this.reference = reference;
    }

    void bind(PdfIndirectReference reference) {
        this.reference = reference;
        pendingOwner = null;
    }

    /** @return the page dictionary through the validated Values interface */
    public PdfDictionary getPdfObject() {
        if (dictionary == null) {
            if (pendingOwner != null) {
                pendingOwner.materializePageHandles();
            }
            dictionary = (PdfDictionary) reference.getRefersTo();
        }
        return dictionary;
    }
}
