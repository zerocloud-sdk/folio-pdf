package net.zerocloud.pdf;

/**
 * The nine low-level value kinds defined by the PDF object model.
 *
 * @since 0.1.0
 */
public enum PdfValueKind {
    /** The PDF null value. */
    NULL,
    /** A PDF boolean. */
    BOOLEAN,
    /** A PDF integer or real number. */
    NUMBER,
    /** A PDF byte string. */
    STRING,
    /** A PDF name. */
    NAME,
    /** A PDF array. */
    ARRAY,
    /** A PDF dictionary. */
    DICTIONARY,
    /** A PDF stream. */
    STREAM,
    /** An indirect reference to an object in the current Session. */
    INDIRECT_REFERENCE
}
