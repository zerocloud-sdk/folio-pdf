package net.zerocloud.pdf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdfparser.PDFStreamParser;

/** Detects content syntax that PDFBox would otherwise mistake for clean EOF. */
final class PdfBoxContentStreamPreflight {

    private static final String TERMINAL_OPERATOR_NAME =
            "__folio_pdf_content_stream_terminal_probe__";
    private static final byte[] TERMINAL_OPERATOR = ("\n"
            + TERMINAL_OPERATOR_NAME + "\n").getBytes(
                    StandardCharsets.US_ASCII);

    private PdfBoxContentStreamPreflight() {
    }

    static void validate(byte[] content) throws IOException {
        if (content == null) {
            throw new NullPointerException("content");
        }
        if (content.length > Integer.MAX_VALUE - TERMINAL_OPERATOR.length) {
            throw new IOException("Content stream is too large to validate");
        }
        byte[] probe = Arrays.copyOf(
                content, content.length + TERMINAL_OPERATOR.length);
        System.arraycopy(
                TERMINAL_OPERATOR,
                0,
                probe,
                content.length,
                TERMINAL_OPERATOR.length);

        PDFStreamParser parser = new PDFStreamParser(probe);
        boolean terminalReached = false;
        boolean pendingOperands = false;
        try {
            Object token;
            while ((token = parser.parseNextToken()) != null) {
                if (token instanceof Operator) {
                    terminalReached = TERMINAL_OPERATOR_NAME.equals(
                            ((Operator) token).getName())
                            && !pendingOperands;
                    pendingOperands = false;
                } else {
                    terminalReached = false;
                    pendingOperands = true;
                }
            }
        } finally {
            parser.close();
        }
        if (!terminalReached) {
            throw new IOException(
                    "Content stream ended inside a token or with operands");
        }
    }
}
