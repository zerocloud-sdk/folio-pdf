package net.zerocloud.pdf.itext7.kernel.utils;

import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;

/** Initializes the package-private edition guard for public utility views. */
final class FacadeClasspathInitializer {

    private FacadeClasspathInitializer() {
    }

    static void requireSingleEdition() {
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
