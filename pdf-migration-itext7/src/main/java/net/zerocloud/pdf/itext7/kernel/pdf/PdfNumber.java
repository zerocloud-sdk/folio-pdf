package net.zerocloud.pdf.itext7.kernel.pdf;

import java.math.BigDecimal;
import net.zerocloud.pdf.DocumentPatch;

/** A mapped number; changes to an inspected number use a validated Patch. @since 0.1.0 */
public final class PdfNumber extends PdfObject {
    private net.zerocloud.pdf.PdfNumber value;
    private final FacadeSession session;
    private FacadeLocation location;

    /** @param value integral value */
    public PdfNumber(int value) {
        this(net.zerocloud.pdf.PdfNumber.of(value), null, null);
    }

    /** @param value finite real value */
    public PdfNumber(double value) {
        this(net.zerocloud.pdf.PdfNumber.of(BigDecimal.valueOf(value)), null, null);
    }

    PdfNumber(net.zerocloud.pdf.PdfNumber value, FacadeSession session, FacadeLocation location) {
        this.value = value;
        this.session = session;
        this.location = location;
        if (location != null) {
            location.number = value;
        }
    }

    /** @return the value converted to an int */
    public int intValue() {
        return (int) doubleValue();
    }

    /** @return the value converted to a double */
    public double doubleValue() {
        return nativeValue().decimalValue().doubleValue();
    }

    /** @param value replacement integral value */
    public void setValue(int value) {
        replace(net.zerocloud.pdf.PdfNumber.of(value));
    }

    /** @param value replacement finite real value */
    public void setValue(double value) {
        replace(net.zerocloud.pdf.PdfNumber.of(BigDecimal.valueOf(value)));
    }

    private void replace(net.zerocloud.pdf.PdfNumber replacement) {
        if (location != null && location.isAttached()) {
            session.call(nativeSession -> {
                nativeSession.execute(DocumentPatch.builder().replaceValue(location.path(), replacement).build());
                location.number = replacement;
                return null;
            });
        } else {
            location = null;
        }
        value = replacement;
    }

    @Override
    public byte getType() {
        return NUMBER;
    }

    @Override
    net.zerocloud.pdf.PdfNumber nativeValue() {
        return location == null ? value : location.number;
    }
}
