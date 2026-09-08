package net.zerocloud.pdf;

/**
 * Engine-owned encoding for replacement decoded stream data.
 *
 * @since 0.1.0
 */
public enum PdfStreamEncoding {
    /** Writes the supplied bytes without a PDF filter. */
    UNFILTERED,
    /** Compresses the supplied bytes with the PDF FlateDecode filter. */
    FLATE
}
