package net.zerocloud.pdf;

/**
 * The immutable, detached PDF null value.
 *
 * @since 0.1.0
 */
public final class PdfNull implements PdfValue {

    /** The single PDF null value. */
    public static final PdfNull INSTANCE = new PdfNull();

    private PdfNull() {
    }

    @Override
    public PdfValueKind getKind() {
        return PdfValueKind.NULL;
    }

    @Override
    public String toString() {
        return "null";
    }
}
