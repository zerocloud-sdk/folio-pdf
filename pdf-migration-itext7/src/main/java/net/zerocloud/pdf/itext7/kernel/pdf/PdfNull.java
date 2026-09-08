package net.zerocloud.pdf.itext7.kernel.pdf;

/** The PDF null value. @since 0.1.0 */
public final class PdfNull extends PdfObject {
    /** Shared immutable null value. */
    public static final PdfNull PDF_NULL = new PdfNull();

    /** Creates a null value. */
    public PdfNull() {
    }

    @Override
    public byte getType() {
        return NULL;
    }

    @Override
    net.zerocloud.pdf.PdfNull nativeValue() {
        return net.zerocloud.pdf.PdfNull.INSTANCE;
    }
}
