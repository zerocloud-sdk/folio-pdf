package net.zerocloud.pdf;

import java.util.Objects;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.query.PageCount;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

final class PdfBoxDocumentSession implements DocumentSession {

    private final PDDocument document;
    private final Thread owner;
    private volatile boolean active;

    PdfBoxDocumentSession(PDDocument document) {
        this.document = Objects.requireNonNull(document, "document");
        this.owner = Thread.currentThread();
        this.active = true;
    }

    @Override
    public void execute(DocumentCommand command) throws DocumentFailure {
        requireActiveOwner();
        Objects.requireNonNull(command, "command");

        if (command == AddBlankPage.INSTANCE) {
            try {
                document.addPage(new PDPage());
                return;
            } catch (RuntimeException failure) {
                throw PdfBoxWorkflowEngine.failure(
                        DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                        "The blank page could not be added.");
            }
        }

        throw PdfBoxWorkflowEngine.failure(
                DocumentFailureCode.COMMAND_REJECTED,
                "The command is not supported by this workflow version.");
    }

    @Override
    public <R> R query(DocumentQuery<R> query) throws DocumentFailure {
        requireActiveOwner();
        Objects.requireNonNull(query, "query");

        if (query == PageCount.INSTANCE) {
            try {
                return pageCountResult(document.getNumberOfPages());
            } catch (RuntimeException failure) {
                throw PdfBoxWorkflowEngine.failure(
                        DocumentFailureCode.QUERY_FAILED,
                        "The page count could not be evaluated.");
            }
        }

        throw PdfBoxWorkflowEngine.failure(
                DocumentFailureCode.QUERY_REJECTED,
                "The query is not supported by this workflow version.");
    }

    void invalidate() {
        active = false;
    }

    private void requireActiveOwner() {
        if (!active) {
            throw new IllegalStateException("Document Session is no longer active.");
        }
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("Document Session is thread-confined.");
        }
    }

    @SuppressWarnings("unchecked")
    private static <R> R pageCountResult(int pageCount) {
        return (R) Integer.valueOf(pageCount);
    }
}
