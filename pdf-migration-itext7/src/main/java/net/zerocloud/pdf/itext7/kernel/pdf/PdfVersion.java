package net.zerocloud.pdf.itext7.kernel.pdf;

import java.util.Objects;

/** Exact PDF version value with the Reference Suite package suffix. @since 0.1.0 */
public final class PdfVersion implements Comparable<PdfVersion> {
    static { FacadeClasspathGuard.requireSingleEdition(); }

    public static final PdfVersion PDF_1_0 = new PdfVersion(net.zerocloud.pdf.PdfVersion.PDF_1_0);
    public static final PdfVersion PDF_1_1 = new PdfVersion(net.zerocloud.pdf.PdfVersion.PDF_1_1);
    public static final PdfVersion PDF_1_2 = new PdfVersion(net.zerocloud.pdf.PdfVersion.PDF_1_2);
    public static final PdfVersion PDF_1_3 = new PdfVersion(net.zerocloud.pdf.PdfVersion.PDF_1_3);
    public static final PdfVersion PDF_1_4 = new PdfVersion(net.zerocloud.pdf.PdfVersion.PDF_1_4);
    public static final PdfVersion PDF_1_5 = new PdfVersion(net.zerocloud.pdf.PdfVersion.PDF_1_5);
    public static final PdfVersion PDF_1_6 = new PdfVersion(net.zerocloud.pdf.PdfVersion.PDF_1_6);
    public static final PdfVersion PDF_1_7 = new PdfVersion(net.zerocloud.pdf.PdfVersion.PDF_1_7);
    public static final PdfVersion PDF_2_0 = new PdfVersion(net.zerocloud.pdf.PdfVersion.PDF_2_0);
    private static final PdfVersion[] VERSIONS = {PDF_1_0, PDF_1_1, PDF_1_2, PDF_1_3, PDF_1_4,
        PDF_1_5, PDF_1_6, PDF_1_7, PDF_2_0};
    final net.zerocloud.pdf.PdfVersion nativeVersion;

    private PdfVersion(net.zerocloud.pdf.PdfVersion version) { nativeVersion = version; }

    /** @param value exact PDF-M.m string @return the supported version */
    public static PdfVersion fromString(String value) {
        Objects.requireNonNull(value, "value");
        for (PdfVersion version : VERSIONS) {
            if (version.toString().equals(value)) { return version; }
        }
        throw new IllegalArgumentException("The PDF version is unsupported.");
    }

    /** @param value exact M.m PDF name @return the supported version */
    public static PdfVersion fromPdfName(PdfName value) {
        return fromString("PDF-" + Objects.requireNonNull(value, "value").getValue());
    }

    /** @return the M.m PDF name */
    public PdfName toPdfName() { return new PdfName(nativeVersion.toString()); }
    @Override public String toString() { return "PDF-" + nativeVersion; }
    @Override public int compareTo(PdfVersion other) { return nativeVersion.compareTo(other.nativeVersion); }
    @Override public boolean equals(Object other) {
        return other instanceof PdfVersion && nativeVersion == ((PdfVersion) other).nativeVersion;
    }
    @Override public int hashCode() { return nativeVersion.hashCode(); }
}
