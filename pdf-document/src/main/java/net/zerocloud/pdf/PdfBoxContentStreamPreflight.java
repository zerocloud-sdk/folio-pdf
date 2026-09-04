package net.zerocloud.pdf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    static void validate(
            byte[] content,
            WorkflowResourceContext resources) throws IOException {
        if (content == null) {
            throw new NullPointerException("content");
        }
        resources.checkpointAsIOException();
        if (content.length > Integer.MAX_VALUE - TERMINAL_OPERATOR.length) {
            throw new IOException("Content stream is too large to validate");
        }
        try (WorkflowResourceContext.OwnedByteAccumulator accumulated =
                        resources.ownedByteAccumulator()) {
            accumulated.write(content, 0, content.length);
            accumulated.write(
                    TERMINAL_OPERATOR, 0, TERMINAL_OPERATOR.length);
            try (WorkflowResourceContext.OwnedBytes probe =
                    accumulated.finishWorkingAsIOException()) {
                PDFStreamParser parser = new PDFStreamParser(
                        probe.getBytes());
                boolean terminalReached = false;
                boolean pendingOperands = false;
                try {
                    Object token;
                    while ((token = parser.parseNextToken()) != null) {
                        resources.checkpointAsIOException();
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
                resources.checkpointAsIOException();
                if (!terminalReached) {
                    throw new IOException(
                            "Content stream ended inside a token or with operands");
                }
            }
        }
    }
}
