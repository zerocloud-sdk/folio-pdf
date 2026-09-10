package net.zerocloud.pdf.itext7.kernel.utils;

import java.util.Objects;
import net.zerocloud.pdf.PageRange;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;

/**
 * Selects the complete group of page-range products published on document close.
 * Obtain this view from {@code PdfDocument.getSplitter()}.
 * @since 0.1.0
 */
public abstract class PdfSplitter {

    static {
        FacadeClasspathInitializer.requireSingleEdition();
    }

    @SuppressWarnings("unused")
    private final PdfDocument owner;

    /**
     * Binds a splitter view to its owning document.
     *
     * @param owner the document that publishes every declared split target
     */
    protected PdfSplitter(PdfDocument owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    /**
     * Defines exactly one range for every declared publication Target.
     * Success is terminal for Commands; queries remain available until close.
     * @param targetNames explicit Target selections, in any order
     * @param ranges corresponding one-based inclusive page ranges
     */
    public abstract void extractPageRanges(String[] targetNames, PageRange[] ranges);
}
