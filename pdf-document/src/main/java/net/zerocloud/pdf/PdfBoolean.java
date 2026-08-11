package net.zerocloud.pdf;

/**
 * An immutable, detached PDF boolean.
 *
 * @since 0.1.0
 */
public final class PdfBoolean implements PdfValue {

    private static final PdfBoolean TRUE = new PdfBoolean(true);
    private static final PdfBoolean FALSE = new PdfBoolean(false);

    private final boolean value;

    private PdfBoolean(boolean value) {
        this.value = value;
    }

    /**
     * Returns the immutable PDF boolean for the supplied value.
     *
     * @param value the boolean value
     * @return the corresponding PDF boolean
     */
    public static PdfBoolean of(boolean value) {
        return value ? TRUE : FALSE;
    }

    /**
     * Returns the boolean value.
     *
     * @return the primitive value
     */
    public boolean booleanValue() {
        return value;
    }

    @Override
    public PdfValueKind getKind() {
        return PdfValueKind.BOOLEAN;
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof PdfBoolean
                && value == ((PdfBoolean) candidate).value;
    }

    @Override
    public int hashCode() {
        return value ? 1231 : 1237;
    }

    @Override
    public String toString() {
        return Boolean.toString(value);
    }
}
