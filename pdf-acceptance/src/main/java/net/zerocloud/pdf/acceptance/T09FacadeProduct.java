package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.nio.file.Path;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfArray;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfBoolean;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDictionary;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfName;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfNull;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfNumber;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfReader;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfStream;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfString;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;

/** Produces the T09 edit through the mapped public Facade only. */
final class T09FacadeProduct {
    private T09FacadeProduct() {
    }

    static void create(Path source, Path output) throws Exception {
        try (PdfReader reader = new PdfReader(source.toString()); PdfDocument document = new PdfDocument(reader, new PdfWriter(output.toString()))) {
            PdfDictionary root = document.getCatalog().getPdfObject();
            PdfDictionary application = (PdfDictionary)
                    ((PdfDictionary) root.get(name("PieceInfo"))).get(name("FolioPDF"));
            PdfDictionary values = (PdfDictionary) application.get(name("Private"));
            values.put(name("Null"), PdfBoolean.FALSE);
            values.put(name("Null"), PdfNull.PDF_NULL);
            values.put(name("Flag"), PdfBoolean.TRUE);
            ((PdfNumber) values.get(name("Number"))).setValue(7.25);
            values.put(name("String"), new PdfString(new byte[] {0, 40, 41, 92, (byte) 255}));
            values.put(name("Name"), name("After /# Ω"));
            PdfArray array = (PdfArray) values.get(name("Array"));
            array.remove(0);
            array.add(0, name("Inserted"));
            array.set(2, new PdfNumber(7.25));
            array.set(1, PdfBoolean.TRUE);
            ((PdfDictionary) array.get(5)).put(name("Nested"), new PdfNumber(2));
            PdfDictionary dictionary = (PdfDictionary) values.get(name("Dictionary"));
            dictionary.put(name("Value"), new PdfNumber(2));
            dictionary.remove(name("Obsolete"));
            dictionary.put(name("Added"), new PdfString("kept"));
            ((PdfNumber) values.get(name("Reference"))).setValue(43);
            values.put(name("Reference"), values.get(name("RetainedStream"), false));
            ((PdfStream) values.get(name("Stream"))).setData(new byte[] {8, 9, 0, (byte) 255});
        }
        try (PdfReader reader = new PdfReader(output.toString()); PdfDocument document = new PdfDocument(reader)) {
            if (document.getNumberOfPages() != 1) {
                throw new IOException("Unexpected T09 Facade reopen result");
            }
        }
    }

    private static PdfName name(String value) {
        return new PdfName(value);
    }

}
