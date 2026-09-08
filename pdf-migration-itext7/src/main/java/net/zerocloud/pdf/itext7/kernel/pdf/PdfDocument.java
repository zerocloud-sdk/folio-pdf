package net.zerocloud.pdf.itext7.kernel.pdf;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.CancellationToken;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PublicationReceipt;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.itext7.kernel.exceptions.PdfException;
import net.zerocloud.pdf.query.DocumentRootReference;
import net.zerocloud.pdf.query.PageCount;

/**
 * Lifecycle mapping of the create, publish, reopen, and inspect workflow.
 *
 * @since 0.1.0
 */
public final class PdfDocument implements Closeable {

    static {
        FacadeClasspathGuard.requireSingleEdition();
    }

    private final PublicationTarget publicationTarget;
    private final Path publicationPath;
    private final Thread owner = Thread.currentThread();
    private final FacadeSource source;
    private FacadeSession valueSession;
    private boolean valueSessionRequested;
    private PdfCatalog catalog;
    private int pageCount;
    private boolean closed;

    /**
     * Opens a new document in writing mode.
     *
     * @param writer the destination declaration
     */
    public PdfDocument(PdfWriter writer) {
        this.publicationTarget = Objects.requireNonNull(writer, "writer").getTarget();
        this.publicationPath = writer.getPath();
        this.source = null;
    }

    /**
     * Opens a detached document view in reading mode.
     *
     * @param reader the validated source reader
     */
    public PdfDocument(PdfReader reader) {
        this.publicationTarget = null;
        this.publicationPath = null;
        this.pageCount = Objects.requireNonNull(reader, "reader").getPageCount();
        this.source = reader.takeSource();
    }

    /**
     * Opens an existing document for validated changes and publication.
     * @param reader the validated source reader
     * @param writer the destination declaration
     */
    public PdfDocument(PdfReader reader, PdfWriter writer) {
        this.publicationTarget = Objects.requireNonNull(writer, "writer").getTarget();
        this.publicationPath = writer.getPath();
        this.pageCount = Objects.requireNonNull(reader, "reader").getPageCount();
        this.source = reader.takeSource();
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
        if (valueSession != null) {
            valueSession.call(session -> {
                session.execute(AddBlankPage.INSTANCE);
                return null;
            });
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
        return valueSession == null ? pageCount : valueSession.call(session -> session.query(PageCount.INSTANCE)).intValue();
    }

    /** @return the Catalog for inspection and validated low-level changes */
    public PdfCatalog getCatalog() {
        requireOpen();
        if (catalog == null) {
            openValueSession();
            catalog = valueSession.call(session -> {
                ObjectReference root = session.query(DocumentRootReference.INSTANCE);
                return new PdfCatalog((PdfDictionary) valueSession.referenceFor(root).inspect(session));
            });
        }
        return catalog;
    }

    private void openValueSession() {
        if (valueSession != null) {
            return;
        }
        if (valueSessionRequested) {
            throw new IllegalStateException("The facade workflow could not be initialized.");
        }
        valueSessionRequested = true;
        CancellationToken cancellation = CancellationToken.create();
        WorkflowRequest.Builder request = WorkflowRequest.builder().saveMode(SaveMode.REWRITE).cancellationToken(cancellation);
        if (source != null) {
            request.source("source", DocumentSource.path(source.path)).primarySource("source")
                    .resourcePolicy(FacadeSource.policyWithSnapshot(source.bytes));
        }
        if (publicationTarget != null) {
            request.target("target", publicationTarget);
        }
        int queuedPages = pageCount - (source == null ? 0 : source.pageCount);
        valueSession = new FacadeSession(request.build(), queuedPages, cancellation);
    }

    /**
     * Publishes a writing document and closes this facade view.
     */
    @Override
    public void close() {
        requireOwner();
        if (closed) {
            return;
        }
        closed = true;
        Throwable primaryFailure = null;
        try {
            finishPublication();
        } catch (RuntimeException | Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            if (source != null) {
                try {
                    source.close();
                } catch (IOException failure) {
                    PdfException mapped = new PdfException(failure.getMessage(), null);
                    if (primaryFailure == null) {
                        throw mapped;
                    }
                    primaryFailure.addSuppressed(mapped);
                }
            }
        }
    }

    private void finishPublication() {
        if (valueSession == null && !valueSessionRequested && source != null && publicationTarget != null) {
            openValueSession();
        }
        if (valueSession != null) {
            WorkflowOutcome<Void> outcome = valueSession.close();
            if (publicationTarget != null) {
                requireCommittedReceipt(outcome.getPublicationReceipts());
            }
            return;
        }
        if (valueSessionRequested) {
            return;
        }
        if (publicationTarget == null) {
            return;
        }

        try {
            WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(
                    WorkflowRequest.builder().saveMode(SaveMode.REWRITE).target("target", publicationTarget).build(),
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
                || !"target".equals(receipts.get(0).getTargetName())
                || !receipts.get(0).getPathTarget().equals(Optional.ofNullable(publicationPath))
                || receipts.get(0).isPartialOutputPossible()) {
            throw new IllegalStateException(
                    "The Native Interface did not commit the declared publication target.");
        }
    }

    private void requireOpen() {
        requireOwner();
        if (closed) {
            throw new IllegalStateException("The facade document is closed.");
        }
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("The facade document is thread-confined.");
        }
    }
}
