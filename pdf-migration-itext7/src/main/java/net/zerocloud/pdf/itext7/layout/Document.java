package net.zerocloud.pdf.itext7.layout;

import java.io.Closeable;
import java.util.Objects;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;

/**
 * Layout document shape that owns and closes its associated facade document.
 *
 * <p>Layout content operations remain outside T04.</p>
 *
 * @since 0.1.0
 */
public final class Document implements Closeable {

    static {
        initializeFacadeGuard();
    }

    private final PdfDocument pdfDocument;
    private boolean closed;

    /**
     * Creates a layout document for a facade PDF document.
     *
     * @param pdfDocument the associated PDF document
     */
    public Document(PdfDocument pdfDocument) {
        this.pdfDocument = Objects.requireNonNull(pdfDocument, "pdfDocument");
    }

    /**
     * Closes this layout document and its associated PDF document.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        pdfDocument.close();
    }

    private static void initializeFacadeGuard() {
        try {
            Class.forName(
                    PdfWriter.class.getName(),
                    true,
                    PdfWriter.class.getClassLoader());
        } catch (ClassNotFoundException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
}
