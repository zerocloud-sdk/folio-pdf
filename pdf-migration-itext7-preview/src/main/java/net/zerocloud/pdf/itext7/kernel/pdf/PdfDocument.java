package net.zerocloud.pdf.itext7.kernel.pdf;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PublicationReceipt;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.itext7.kernel.exceptions.PdfException;

/**
 * Preview mapping of the first create, publish, reopen, and inspect workflow.
 *
 * @since 0.1.0
 */
public final class PdfDocument implements Closeable {

    static {
        FacadeClasspathGuard.requirePreviewOnly();
    }

    private final Path publicationTarget;
    private int pageCount;
    private boolean closed;

    /**
     * Opens a new document in writing mode.
     *
     * @param writer the destination declaration
     */
    public PdfDocument(PdfWriter writer) {
        this.publicationTarget = Objects.requireNonNull(writer, "writer").getTarget();
    }

    /**
     * Opens a detached document view in reading mode.
     *
     * @param reader the validated source reader
     */
    public PdfDocument(PdfReader reader) {
        this.publicationTarget = null;
        this.pageCount = Objects.requireNonNull(reader, "reader").getPageCount();
    }

    /**
     * Adds one blank page to a document opened for writing.
     *
     * @return the added page
     */
    public PdfPage addNewPage() {
        requireOpen();
        if (publicationTarget == null) {
            throw new IllegalStateException(
                    "A read-only facade document cannot add a page.");
        }
        pageCount++;
        return new PdfPage();
    }

    /**
     * Returns the number of pages observed or queued in this document.
     *
     * @return the page count
     */
    public int getNumberOfPages() {
        requireOpen();
        return pageCount;
    }

    /**
     * Publishes a writing document and closes this facade view.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (publicationTarget == null) {
            return;
        }

        try {
            WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(
                    WorkflowRequest.create(publicationTarget, SaveMode.REWRITE),
                    session -> {
                        for (int page = 0; page < pageCount; page++) {
                            session.execute(AddBlankPage.INSTANCE);
                        }
                        return null;
                    });
            requireCommittedReceipt(outcome.getPublicationReceipts());
        } catch (DocumentFailure failure) {
            throw new PdfException(
                    failure.getCode().name() + ": " + failure.getDiagnostic(),
                    failure);
        }
    }

    private void requireCommittedReceipt(List<PublicationReceipt> receipts) {
        if (receipts.size() != 1
                || receipts.get(0).getStatus() != PublicationStatus.COMMITTED
                || !receipts.get(0).getPathTarget().isPresent()
                || !publicationTarget.equals(receipts.get(0).getPathTarget().get())) {
            throw new IllegalStateException(
                    "The Native Interface did not commit the declared publication target.");
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("The facade document is closed.");
        }
    }
}
