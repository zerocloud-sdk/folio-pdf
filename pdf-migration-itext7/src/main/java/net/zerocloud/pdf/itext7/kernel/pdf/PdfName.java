package net.zerocloud.pdf.itext7.kernel.pdf;

/** A decoded PDF name without its leading slash. @since 0.1.0 */
public final class PdfName extends PdfObject {
    private final net.zerocloud.pdf.PdfName value;

    /** @param value decoded name text */
    public PdfName(String value) {
        this.value = net.zerocloud.pdf.PdfName.of(value);
    }

    /** @return decoded name text */
    public String getValue() {
        return value.getValue();
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof PdfName && value.equals(((PdfName) candidate).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public byte getType() {
        return NAME;
    }

    @Override
    net.zerocloud.pdf.PdfName nativeValue() {
        return value;
    }
}
