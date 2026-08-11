package net.zerocloud.pdf;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * An immutable, detached PDF integer or real number.
 *
 * @since 0.1.0
 */
public final class PdfNumber implements PdfValue {

    private final BigDecimal value;

    private PdfNumber(BigDecimal value) {
        this.value = normalize(Objects.requireNonNull(value, "value"));
    }

    /**
     * Creates a PDF number with decimal semantics.
     *
     * @param value the finite decimal value
     * @return an immutable PDF number
     */
    public static PdfNumber of(BigDecimal value) {
        return new PdfNumber(value);
    }

    /**
     * Creates an integral PDF number.
     *
     * @param value the integer value
     * @return an immutable PDF number
     */
    public static PdfNumber of(long value) {
        return new PdfNumber(BigDecimal.valueOf(value));
    }

    /**
     * Returns the decimal value.
     *
     * @return the immutable decimal
     */
    public BigDecimal decimalValue() {
        return value;
    }

    @Override
    public PdfValueKind getKind() {
        return PdfValueKind.NUMBER;
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof PdfNumber
                && value.compareTo(((PdfNumber) candidate).value) == 0;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }

    private static BigDecimal normalize(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.signum() == 0 ? BigDecimal.ZERO : normalized;
    }
}
