package net.zerocloud.pdf.itext7.kernel.utils;

import java.io.Closeable;
import java.util.Objects;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;

/**
 * Selects complete named Sources declared by the owning facade document.
 * Obtain this view from {@code PdfDocument.getMerger()}.
 * @since 0.1.0
 */
public abstract class PdfMerger implements Closeable {

    static {
        FacadeClasspathInitializer.requireSingleEdition();
    }

    private final PdfDocument owner;

    /**
     * Binds a merger view to its owning document.
     *
     * @param owner the document that publishes when this view closes
     */
    protected PdfMerger(PdfDocument owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    /**
     * Appends declared non-primary Sources in argument order.
     * @param sourceNames the explicit ordered selection of Source names
     * @return this merger
     */
    public abstract PdfMerger merge(String... sourceNames);

    /** Publishes and closes the owning document. */
    @Override
    public final void close() {
        owner.close();
    }
}
