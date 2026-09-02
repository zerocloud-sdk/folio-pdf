package net.zerocloud.pdf;

/**
 * A PDF specification version supported by the Document Workflow.
 *
 * <p>The value is represented without a floating-point number so callers can
 * compare declarations exactly.</p>
 *
 * @since 0.1.0
 */
public enum PdfVersion {
    /** PDF 1.0. */ PDF_1_0(1, 0),
    /** PDF 1.1. */ PDF_1_1(1, 1),
    /** PDF 1.2. */ PDF_1_2(1, 2),
    /** PDF 1.3. */ PDF_1_3(1, 3),
    /** PDF 1.4. */ PDF_1_4(1, 4),
    /** PDF 1.5. */ PDF_1_5(1, 5),
    /** PDF 1.6. */ PDF_1_6(1, 6),
    /** PDF 1.7. */ PDF_1_7(1, 7),
    /** PDF 2.0. */ PDF_2_0(2, 0);

    private final int major;
    private final int minor;

    PdfVersion(int major, int minor) {
        this.major = major;
        this.minor = minor;
    }

    /** @return the specification major version */
    public int getMajor() {
        return major;
    }

    /** @return the specification minor version */
    public int getMinor() {
        return minor;
    }

    @Override
    public String toString() {
        return major + "." + minor;
    }

    static PdfVersion from(int major, int minor) {
        for (PdfVersion version : values()) {
            if (version.major == major && version.minor == minor) {
                return version;
            }
        }
        return null;
    }

    float backendValue() {
        return Float.parseFloat(toString());
    }
}
