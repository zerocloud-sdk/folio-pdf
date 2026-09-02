package net.zerocloud.pdf;

/** Immutable standard password-security permission bits. */
public final class DocumentPermissions {

    private static final int PRINT = 3;
    private static final int MODIFY = 4;
    private static final int EXTRACT = 5;
    private static final int ANNOTATE = 6;
    private static final int FILL_FORMS = 9;
    private static final int ACCESSIBILITY = 10;
    private static final int ASSEMBLE = 11;
    private static final int FAITHFUL_PRINT = 12;

    private final int standardMask;

    private DocumentPermissions(int standardMask) {
        this.standardMask = standardMask;
    }

    /** Begins with every optional user permission denied. */
    public static Builder builder() {
        return new Builder();
    }

    /** @return a permission set granting every standard user permission */
    public static DocumentPermissions unrestricted() {
        return new DocumentPermissions(~3);
    }

    /** @return the signed 32-bit {@code /P} value defined by the PDF standard */
    public int getStandardMask() {
        return standardMask;
    }

    public boolean canPrint() { return bit(PRINT); }
    public boolean canModify() { return bit(MODIFY); }
    public boolean canExtractContent() { return bit(EXTRACT); }
    public boolean canModifyAnnotations() { return bit(ANNOTATE); }
    public boolean canFillForms() { return bit(FILL_FORMS); }
    public boolean canExtractForAccessibility() { return bit(ACCESSIBILITY); }
    public boolean canAssembleDocument() { return bit(ASSEMBLE); }
    public boolean canPrintFaithfully() { return bit(FAITHFUL_PRINT); }

    private boolean bit(int bit) {
        return (standardMask & (1 << (bit - 1))) != 0;
    }

    static DocumentPermissions fromStandardMask(int standardMask) {
        return new DocumentPermissions(standardMask);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DocumentPermissions
                && standardMask == ((DocumentPermissions) other).standardMask;
    }

    @Override
    public int hashCode() {
        return standardMask;
    }

    @Override
    public String toString() {
        return "DocumentPermissions[standardMask=" + standardMask + "]";
    }

    /** Builds the eight standard user-permission choices. */
    public static final class Builder {

        private int mask = ~3;

        private Builder() {
            mask = set(mask, PRINT, false);
            mask = set(mask, MODIFY, false);
            mask = set(mask, EXTRACT, false);
            mask = set(mask, ANNOTATE, false);
            mask = set(mask, FILL_FORMS, false);
            mask = set(mask, ACCESSIBILITY, false);
            mask = set(mask, ASSEMBLE, false);
            mask = set(mask, FAITHFUL_PRINT, false);
        }

        public Builder allowPrinting(boolean allow) {
            mask = set(mask, PRINT, allow); return this;
        }
        public Builder allowModification(boolean allow) {
            mask = set(mask, MODIFY, allow); return this;
        }
        public Builder allowContentExtraction(boolean allow) {
            mask = set(mask, EXTRACT, allow); return this;
        }
        public Builder allowAnnotationModification(boolean allow) {
            mask = set(mask, ANNOTATE, allow); return this;
        }
        public Builder allowFormFilling(boolean allow) {
            mask = set(mask, FILL_FORMS, allow); return this;
        }
        public Builder allowAccessibilityExtraction(boolean allow) {
            mask = set(mask, ACCESSIBILITY, allow); return this;
        }
        public Builder allowDocumentAssembly(boolean allow) {
            mask = set(mask, ASSEMBLE, allow); return this;
        }
        public Builder allowFaithfulPrinting(boolean allow) {
            mask = set(mask, FAITHFUL_PRINT, allow); return this;
        }

        public DocumentPermissions build() {
            return new DocumentPermissions(mask);
        }

        private static int set(int mask, int bit, boolean value) {
            return value
                    ? mask | (1 << (bit - 1))
                    : mask & ~(1 << (bit - 1));
        }
    }
}
