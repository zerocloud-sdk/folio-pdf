package net.zerocloud.pdf.itext7.kernel.pdf;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.query.PageCount;

/**
 * Opens a Path source through the Native Interface for the mapped inspection
 * workflow.
 *
 * @since 0.1.0
 */
public final class PdfReader implements Closeable {

    static {
        FacadeClasspathGuard.requirePreviewOnly();
    }

    private final int pageCount;

    /**
     * Opens and validates a PDF filename.
     *
     * @param filename the PDF filename
     * @throws IOException if the Native Interface cannot open the source
     */
    public PdfReader(String filename) throws IOException {
        Path source = Paths.get(Objects.requireNonNull(filename, "filename"))
                .toAbsolutePath()
                .normalize();
        try {
            WorkflowOutcome<Integer> outcome = new DocumentWorkflow().execute(
                    WorkflowRequest.open(source, SaveMode.REWRITE),
                    session -> session.query(PageCount.INSTANCE));
            this.pageCount = outcome.getResult().intValue();
        } catch (DocumentFailure failure) {
            throw new IOException(
                    failure.getCode().name() + ": " + failure.getDiagnostic(),
                    failure);
        }
    }

    int getPageCount() {
        return pageCount;
    }

    /**
     * Closes this detached reader view.
     *
     * @throws IOException retained for the mapped reference call shape
     */
    @Override
    public void close() throws IOException {
        // DocumentWorkflow owns and closes the source before construction returns.
    }
}
