package net.zerocloud.pdf;

import java.util.Arrays;
import java.util.Objects;

/**
 * An immutable, detached PDF byte string.
 *
 * @since 0.1.0
 */
public final class PdfString implements PdfValue {

    private final byte[] bytes;

    private PdfString(byte[] bytes) {
        this.bytes = Arrays.copyOf(
                Objects.requireNonNull(bytes, "bytes"),
                bytes.length);
    }

    /**
     * Creates a PDF string from its exact bytes.
     *
     * @param bytes the PDF string bytes
     * @return an immutable PDF string
     */
    public static PdfString of(byte[] bytes) {
        return new PdfString(bytes);
    }

    /**
     * Returns a defensive copy of the PDF string bytes.
     *
     * @return the string bytes
     */
    public byte[] getBytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public PdfValueKind getKind() {
        return PdfValueKind.STRING;
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof PdfString
                && Arrays.equals(bytes, ((PdfString) candidate).bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "PdfString[" + bytes.length + " bytes]";
    }
}
