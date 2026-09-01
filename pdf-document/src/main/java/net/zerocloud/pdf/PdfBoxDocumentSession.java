package net.zerocloud.pdf;

import java.util.Map;
import java.util.Objects;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.command.SetNamedDestinations;
import net.zerocloud.pdf.query.DocumentRootReference;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageCount;
import net.zerocloud.pdf.query.PageObjectReference;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;

final class PdfBoxDocumentSession implements DocumentSession {

    private final PDDocument document;
    private final Thread owner;
    private final PdfBoxValueAdapter valueAdapter;
    private final PdfBoxMetadataOperations metadataOperations;
    private final PdfBoxAnnotationOperations annotationOperations;
    private final PdfBoxAnnotationPageOperations annotationPageOperations;
    private final PdfBoxPageOperations pageOperations;
    private String outcomeCapabilityId;
    private volatile boolean active;

    PdfBoxDocumentSession(
            PDDocument document,
            Map<String, DocumentSource> sources,
            String primarySourceName,
            Map<String, PublicationTarget> publicationTargets) {
        this.document = Objects.requireNonNull(document, "document");
        this.owner = Thread.currentThread();
        this.valueAdapter = new PdfBoxValueAdapter(document, this);
        this.metadataOperations = new PdfBoxMetadataOperations(document);
        this.annotationOperations = new PdfBoxAnnotationOperations(
                document,
                metadataOperations);
        this.annotationPageOperations = new PdfBoxAnnotationPageOperations(
                document,
                metadataOperations,
                annotationOperations);
        this.pageOperations = new PdfBoxPageOperations(
                document,
                sources,
                primarySourceName,
                publicationTargets,
                valueAdapter,
                metadataOperations,
                annotationPageOperations);
        this.outcomeCapabilityId = PdfBoxWorkflowEngine.CAPABILITY_ID;
        this.active = true;
    }

    @Override
    public void execute(DocumentCommand command) throws DocumentFailure {
        requireActiveOwner();
        Objects.requireNonNull(command, "command");
        pageOperations.requireCommandAllowed();

        if (command == AddBlankPage.INSTANCE) {
            try {
                PDPage page = new PDPage();
                page.setResources(new PDResources());
                document.addPage(page);
                pageOperations.makeLibraryOwnedPageIndirect(page);
                return;
            } catch (RuntimeException backendFailure) {
                throw PdfBoxWorkflowEngine.failure(
                        DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                        "The blank page could not be added.");
            }
        }

        if (pageOperations.supports(command)) {
            outcomeCapabilityId = PdfBoxPageOperations.CAPABILITY_ID;
            pageOperations.execute(command);
            return;
        }

        if (metadataOperations.supports(command)) {
            if (command instanceof SetNamedDestinations) {
                annotationPageOperations.requireNamedDestinationRemovalSafe(
                        ((SetNamedDestinations) command).getRemovedNames());
            }
            outcomeCapabilityId = PdfBoxMetadataOperations.CAPABILITY_ID;
            metadataOperations.execute(command);
            return;
        }

        if (annotationOperations.supports(command)) {
            outcomeCapabilityId = PdfBoxAnnotationOperations.CAPABILITY_ID;
            annotationOperations.execute(command);
            return;
        }

        if (command instanceof DocumentPatch) {
            outcomeCapabilityId = PdfBoxValueAdapter.CAPABILITY_ID;
            try {
                valueAdapter.apply((DocumentPatch) command);
                return;
            } catch (RuntimeException backendFailure) {
                throw PdfBoxValueAdapter.failure(
                        DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                        "The Document Patch could not be applied.");
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
            } catch (RuntimeException backendFailure) {
                throw PdfBoxWorkflowEngine.failure(
                        DocumentFailureCode.QUERY_FAILED,
                        "The page count could not be evaluated.");
            }
        }

        if (query instanceof PageObjectReference) {
            outcomeCapabilityId = PdfBoxPageOperations.CAPABILITY_ID;
            return queryResult(pageOperations.pageReference(
                    (PageObjectReference) query));
        }

        if (query == DocumentRootReference.INSTANCE) {
            outcomeCapabilityId = PdfBoxValueAdapter.CAPABILITY_ID;
            try {
                return queryResult(valueAdapter.documentRootReference());
            } catch (RuntimeException backendFailure) {
                throw PdfBoxValueAdapter.failure(
                        DocumentFailureCode.QUERY_FAILED,
                        "The document root could not be inspected.");
            }
        }

        if (query instanceof InspectObject) {
            outcomeCapabilityId = PdfBoxValueAdapter.CAPABILITY_ID;
            InspectObject inspection = (InspectObject) query;
            try {
                return queryResult(valueAdapter.inspect(
                        inspection.getReference(),
                        inspection.getLimits()));
            } catch (RuntimeException backendFailure) {
                throw PdfBoxValueAdapter.failure(
                        DocumentFailureCode.QUERY_FAILED,
                        "The PDF object could not be inspected.");
            }
        }

        if (metadataOperations.supportsQuery(query)) {
            outcomeCapabilityId = PdfBoxMetadataOperations.CAPABILITY_ID;
            return queryResult(metadataOperations.evaluate(query));
        }


        if (annotationOperations.supportsQuery(query)) {
            outcomeCapabilityId = PdfBoxAnnotationOperations.CAPABILITY_ID;
            return queryResult(annotationOperations.evaluate(query));
        }

        throw PdfBoxWorkflowEngine.failure(
                DocumentFailureCode.QUERY_REJECTED,
                "The query is not supported by this workflow version.");
    }

    void invalidate() {
        active = false;
    }

    String getOutcomeCapabilityId() {
        return outcomeCapabilityId;
    }

    Map<String, PDDocument> getSplitDocuments() {
        return pageOperations.getSplitDocuments();
    }

    void requireActiveOwner() {
        if (!active) {
            throw new IllegalStateException("Document Session is no longer active.");
        }
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("Document Session is thread-confined.");
        }
    }

    void requireActiveValueView() throws DocumentFailure {
        if (!active) {
            throw PdfBoxValueAdapter.failure(
                    DocumentFailureCode.PDF_VALUE_VIEW_EXPIRED,
                    "The PDF Value view is no longer active.");
        }
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("Document Session is thread-confined.");
        }
    }

    @SuppressWarnings("unchecked")
    private static <R> R pageCountResult(int pageCount) {
        return (R) Integer.valueOf(pageCount);
    }

    @SuppressWarnings("unchecked")
    private static <R> R queryResult(Object result) {
        return (R) result;
    }
}
