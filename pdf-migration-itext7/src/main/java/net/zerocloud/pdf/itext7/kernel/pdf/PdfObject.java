package net.zerocloud.pdf.itext7.kernel.pdf;

/** Project-owned base for the mapped PDF Value family. @since 0.1.0 */
public abstract class PdfObject {
    /** PDF array kind. */
    public static final byte ARRAY = 1;
    /** PDF boolean kind. */
    public static final byte BOOLEAN = 2;
    /** PDF dictionary kind. */
    public static final byte DICTIONARY = 3;
    /** PDF indirect-reference kind. */
    public static final byte INDIRECT_REFERENCE = 5;
    /** PDF name kind. */
    public static final byte NAME = 6;
    /** PDF null kind. */
    public static final byte NULL = 7;
    /** PDF number kind. */
    public static final byte NUMBER = 8;
    /** PDF stream kind. */
    public static final byte STREAM = 9;
    /** PDF string kind. */
    public static final byte STRING = 10;

    static {
        FacadeClasspathGuard.requireSingleEdition();
    }

    PdfObject() {
    }

    private PdfIndirectReference indirectReference;

    /** @return this inspected object's Session identity, or null for a direct value */
    public PdfIndirectReference getIndirectReference() {
        return indirectReference;
    }

    final void attachReference(PdfIndirectReference reference) {
        indirectReference = reference;
    }

    final net.zerocloud.pdf.PdfValue valueForPatch() {
        return indirectReference == null ? FacadeValues.convert(this) : indirectReference.nativeValue();
    }

    /** @return the mapped PDF kind */
    public abstract byte getType();

    /** @return whether this value is a number */
    public boolean isNumber() {
        return getType() == NUMBER;
    }

    /** @return whether this value is a null */
    public boolean isNull() {
        return getType() == NULL;
    }

    /** @return whether this value is a boolean */
    public boolean isBoolean() {
        return getType() == BOOLEAN;
    }

    /** @return whether this value is a string */
    public boolean isString() {
        return getType() == STRING;
    }

    /** @return whether this value is a name */
    public boolean isName() {
        return getType() == NAME;
    }

    /** @return whether this value is a dictionary */
    public boolean isDictionary() {
        return getType() == DICTIONARY;
    }

    /** @return whether this value is an array */
    public boolean isArray() {
        return getType() == ARRAY;
    }

    /** @return whether this value is an indirect reference */
    public boolean isIndirectReference() {
        return getType() == INDIRECT_REFERENCE;
    }

    /** @return whether this value is a stream */
    public boolean isStream() {
        return getType() == STREAM;
    }

    abstract net.zerocloud.pdf.PdfValue nativeValue();
}
