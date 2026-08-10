package net.zerocloud.pdf;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

final class PdfBoxWorkflowEngine {

    private static final String CAPABILITY_ID = "document.blank.create-publish-reopen";

    private PdfBoxWorkflowEngine() {
    }

    static <R> WorkflowOutcome<R> execute(
            WorkflowRequest request,
            DocumentWork<R> work) throws DocumentFailure {
        if (request.getPublicationTarget().isPresent()) {
            return createAndPublish(request.getPublicationTarget().get(), work);
        }
        if (request.getSource().isPresent()) {
            return openAndInspect(request.getSource().get(), work);
        }
        throw failure(
                DocumentFailureCode.INVALID_REQUEST,
                "The workflow request has no source or publication target.");
    }

    static DocumentFailure failure(DocumentFailureCode code, String diagnostic) {
        return new DocumentFailure(code, CAPABILITY_ID, diagnostic);
    }

    private static <R> WorkflowOutcome<R> createAndPublish(
            Path requestedTarget,
            DocumentWork<R> work) throws DocumentFailure {
        Path target = requestedTarget.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw failure(
                    DocumentFailureCode.INVALID_REQUEST,
                    "The publication target parent must be an existing directory.");
        }

        PDDocument document = createDocument();
        PdfBoxDocumentSession session = new PdfBoxDocumentSession(document);
        Path staged = null;

        try {
            R result = work.perform(session);
            session.invalidate();

            try {
                staged = Files.createTempFile(parent, ".open-pdf-", ".pdf");
                document.save(staged.toFile());
            } catch (IOException | RuntimeException failure) {
                throw failure(
                        DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                        "The document could not be staged for publication.");
            }

            closeForSuccess(document);
            document = null;
            validate(staged);
            publish(staged, target);
            staged = null;

            PublicationReceipt receipt = new PublicationReceipt(
                    requestedTarget,
                    PublicationStatus.COMMITTED);
            return new WorkflowOutcome<R>(result, Collections.singletonList(receipt));
        } finally {
            session.invalidate();
            closeQuietly(document);
            deleteQuietly(staged);
        }
    }

    private static <R> WorkflowOutcome<R> openAndInspect(
            Path requestedSource,
            DocumentWork<R> work) throws DocumentFailure {
        Path source = requestedSource.toAbsolutePath().normalize();
        PDDocument document;
        try {
            document = Loader.loadPDF(source.toFile());
        } catch (IOException | RuntimeException failure) {
            throw failure(
                    DocumentFailureCode.SOURCE_READ_FAILED,
                    "The source could not be opened as a PDF document.");
        }

        PdfBoxDocumentSession session = new PdfBoxDocumentSession(document);
        try {
            R result = work.perform(session);
            session.invalidate();
            closeForSuccess(document);
            document = null;
            return new WorkflowOutcome<R>(
                    result,
                    Collections.<PublicationReceipt>emptyList());
        } finally {
            session.invalidate();
            closeQuietly(document);
        }
    }

    private static void validate(Path staged) throws DocumentFailure {
        try {
            if (Files.size(staged) <= 0L) {
                throw failure(
                        DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                        "The staged document is empty.");
            }
        } catch (IOException | RuntimeException failure) {
            throw failure(
                    DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                    "The staged document could not be validated.");
        }

        PDDocument validationDocument = null;
        try {
            validationDocument = Loader.loadPDF(staged.toFile());
            validationDocument.close();
            validationDocument = null;
        } catch (IOException | RuntimeException failure) {
            throw failure(
                    DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                    "The staged document is not a parseable PDF document.");
        } finally {
            closeQuietly(validationDocument);
        }
    }

    private static void publish(Path staged, Path target) throws DocumentFailure {
        try {
            try {
                Files.move(
                        staged,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException failure) {
            throw failure(
                    DocumentFailureCode.PUBLICATION_FAILED,
                    "The validated document could not be committed to its target.");
        }
    }

    private static void closeForSuccess(PDDocument document) throws DocumentFailure {
        try {
            document.close();
        } catch (IOException | RuntimeException failure) {
            throw failure(
                    DocumentFailureCode.RESOURCE_CLOSE_FAILED,
                    "A library-owned document resource could not be closed cleanly.");
        }
    }

    private static PDDocument createDocument() throws DocumentFailure {
        try {
            return new PDDocument();
        } catch (RuntimeException failure) {
            throw failure(
                    DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                    "A new document could not be initialized.");
        }
    }

    private static void closeQuietly(PDDocument document) {
        if (document == null) {
            return;
        }
        try {
            document.close();
        } catch (IOException | RuntimeException ignored) {
            // A primary failure is already propagating; do not expose backend details.
        }
    }

    private static void deleteQuietly(Path staged) {
        if (staged == null) {
            return;
        }
        try {
            Files.deleteIfExists(staged);
        } catch (IOException | RuntimeException ignored) {
            // Best-effort cleanup must not replace the safe primary diagnostic.
        }
    }
}
