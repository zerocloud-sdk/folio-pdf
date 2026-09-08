package net.zerocloud.pdf.itext7.kernel.pdf;

/** An immutable PDF boolean. @since 0.1.0 */
public final class PdfBoolean extends PdfObject {
    /** Shared true value. */
    public static final PdfBoolean TRUE = new PdfBoolean(true);
    /** Shared false value. */
    public static final PdfBoolean FALSE = new PdfBoolean(false);
    private final net.zerocloud.pdf.PdfBoolean value;

    /** @param value the boolean value */
    public PdfBoolean(boolean value) {
        this.value = net.zerocloud.pdf.PdfBoolean.of(value);
    }

    /** @return the primitive boolean */
    public boolean getValue() {
        return value.booleanValue();
    }

    @Override
    public byte getType() {
        return BOOLEAN;
    }

    @Override
    net.zerocloud.pdf.PdfBoolean nativeValue() {
        return value;
    }
}
