package net.zerocloud.pdf;

import java.util.Objects;

/**
 * One immutable name/value pair obtained from a PDF dictionary traversal.
 *
 * @since 0.1.0
 */
public final class PdfDictionaryEntry {

    private final PdfName name;
    private final PdfValue value;

    PdfDictionaryEntry(PdfName name, PdfValue value) {
        this.name = Objects.requireNonNull(name, "name");
        this.value = Objects.requireNonNull(value, "value");
    }

    /**
     * Returns the dictionary entry name.
     *
     * @return the immutable PDF name
     */
    public PdfName getName() {
        return name;
    }

    /**
     * Returns the dictionary entry value.
     *
     * @return the PDF Value
     */
    public PdfValue getValue() {
        return value;
    }
}
