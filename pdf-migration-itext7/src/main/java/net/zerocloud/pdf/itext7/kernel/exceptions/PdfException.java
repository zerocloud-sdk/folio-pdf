package net.zerocloud.pdf.itext7.kernel.exceptions;

import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;

/**
 * Unchecked facade failure for document operations whose reference call shape
 * does not declare a checked exception.
 *
 * @since 0.1.0
 */
public final class PdfException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    static {
        initializeFacadeGuard();
    }

    /**
     * Creates a facade failure with a safe message and retained cause.
     *
     * @param message the safe failure message
     * @param cause the originating failure
     */
    public PdfException(String message, Throwable cause) {
        super(message, cause);
    }

    private static void initializeFacadeGuard() {
        try {
            Class.forName(
                    PdfWriter.class.getName(),
                    true,
                    PdfWriter.class.getClassLoader());
        } catch (ClassNotFoundException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
}
