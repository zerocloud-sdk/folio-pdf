package net.zerocloud.pdf.itext7.kernel.pdf;

/** The document Catalog mapped to its project-owned dictionary view. @since 0.1.0 */
public final class PdfCatalog {
    static {
        FacadeClasspathGuard.requireSingleEdition();
    }

    private final PdfDictionary dictionary;

    PdfCatalog(PdfDictionary dictionary) {
        this.dictionary = dictionary;
    }

    /** @return the active Catalog dictionary */
    public PdfDictionary getPdfObject() {
        return dictionary;
    }
}
