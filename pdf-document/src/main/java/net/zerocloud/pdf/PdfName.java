package net.zerocloud.pdf;

import java.util.Objects;

/**
 * An immutable, detached PDF name.
 *
 * @since 0.1.0
 */
public final class PdfName implements PdfValue {

    private final String value;

    private PdfName(String value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    /**
     * Creates a PDF name from its decoded name text, without a leading slash.
     *
     * @param value the decoded name text
     * @return an immutable PDF name
     */
    public static PdfName of(String value) {
        return new PdfName(value);
    }

    /**
     * Returns the decoded name text.
     *
     * @return the name text
     */
    public String getValue() {
        return value;
    }

    @Override
    public PdfValueKind getKind() {
        return PdfValueKind.NAME;
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof PdfName
                && value.equals(((PdfName) candidate).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "/" + value;
    }
}
