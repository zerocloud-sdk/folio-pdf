package net.zerocloud.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.zerocloud.pdf.provider.ProviderSelection;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

final class PdfBoxWorkflowEngine {

    static final String CAPABILITY_ID = "document.blank.create-publish-reopen";

    private PdfBoxWorkflowEngine() {
    }

    static <R> WorkflowOutcome<R> execute(
            WorkflowRequest request,
            DocumentWork<R> work,
            Clock clock,
            List<ProviderSelection> providerSelections) throws DocumentFailure {
        try {
            return executeChecked(request, work, clock, providerSelections);
        } catch (DocumentFailure executionFailure) {
            if (!request.getPublicationTargets().isEmpty()
                    && executionFailure.getPublicationReceipts().isEmpty()) {
                throw failure(
                        executionFailure.getCode(),
                        executionFailure.getCapabilityId(),
                        executionFailure.getDiagnostic(),
                        PublicationReceipt.notAttempted(
                                request.getPublicationTargets()));
            }
            throw executionFailure;
        }
    }

    private static <R> WorkflowOutcome<R> executeChecked(
            WorkflowRequest request,
            DocumentWork<R> work,
            Clock clock,
            List<ProviderSelection> providerSelections) throws DocumentFailure {
        if (request.getSaveMode() == SaveMode.INCREMENTAL) {
            throw failure(
                    DocumentFailureCode.SAVE_MODE_UNSUPPORTED,
                    "INCREMENTAL publication is not supported until T15.");
        }
        if (request.getSaveMode() != SaveMode.REWRITE) {
            throw failure(
                    DocumentFailureCode.INVALID_REQUEST,
                    "The workflow request must select REWRITE.");
        }
        requireExecutionAllowed(request, clock);
        emit(request, WorkflowProgressPhase.STARTED);
        if (request.getSources().isEmpty()
                && request.getPublicationTargets().isEmpty()) {
            throw failure(
                    DocumentFailureCode.INVALID_REQUEST,
                    "The workflow request has no source or publication target.");
        }
        List<PublicationTargetAdapter> publicationTargets =
                preflightTargets(request.getPublicationTargets());

        PDDocument document;
        if (request.getSources().isEmpty()) {
            document = createDocument();
        } else {
            DocumentSource source = request.getSources().get(
                    request.getPrimarySourceName());
            if (source == null) {
                throw failure(
                        DocumentFailureCode.INVALID_REQUEST,
                        "The workflow request must select a declared primary source.");
            }
            document = openDocument(source);
        }

        return executeWithDocument(
                document,
                request,
                publicationTargets,
                work,
                clock,
                providerSelections);
    }

    static DocumentFailure failure(DocumentFailureCode code, String diagnostic) {
        return new DocumentFailure(code, CAPABILITY_ID, diagnostic);
    }

    private static DocumentFailure failure(
            DocumentFailureCode code,
            String diagnostic,
            List<PublicationReceipt> receipts) {
        return new DocumentFailure(code, CAPABILITY_ID, diagnostic, receipts);
    }

    private static DocumentFailure failure(
            DocumentFailureCode code,
            String capabilityId,
            String diagnostic,
            List<PublicationReceipt> receipts) {
        return new DocumentFailure(code, capabilityId, diagnostic, receipts);
    }

    private static <R> WorkflowOutcome<R> executeWithDocument(
            PDDocument initialDocument,
            WorkflowRequest request,
            List<PublicationTargetAdapter> publicationTargets,
            DocumentWork<R> work,
            Clock clock,
            List<ProviderSelection> providerSelections) throws DocumentFailure {
        PDDocument document = initialDocument;
        PdfBoxDocumentSession session = new PdfBoxDocumentSession(
                document,
                request.getSources(),
                request.getPrimarySourceName(),
                request.getPublicationTargets());
        List<Path> stagedDocuments = new ArrayList<Path>();
        Map<String, PDDocument> splitDocuments = null;

        try {
            requireExecutionAllowed(request, clock);
            if (!request.getSources().isEmpty()) {
                emit(request, WorkflowProgressPhase.SOURCE_OPENED);
            }
            emit(request, WorkflowProgressPhase.WORK_STARTED);
            R result;
            try {
                result = work.perform(session);
            } catch (DocumentFailure workFailure) {
                throw failure(
                        workFailure.getCode(),
                        workFailure.getCapabilityId(),
                        workFailure.getDiagnostic(),
                        notAttempted(publicationTargets));
            }
            session.invalidate();
            splitDocuments = session.getSplitDocuments();
            emit(request, WorkflowProgressPhase.WORK_COMPLETED);
            requireExecutionAllowed(request, clock);

            if (publicationTargets.isEmpty()) {
                closeForSuccess(document);
                document = null;
                emit(request, WorkflowProgressPhase.COMPLETED);
                return outcome(
                        result,
                        session.getOutcomeCapabilityId(),
                        request,
                        Collections.<PublicationReceipt>emptyList(),
                        providerSelections);
            }

            try {
                if (splitDocuments == null) {
                    Path staged = Files.createTempFile(".folio-pdf-", ".pdf");
                    stagedDocuments.add(staged);
                    document.save(staged.toFile());
                } else {
                    for (PublicationTargetAdapter target : publicationTargets) {
                        Path staged = Files.createTempFile(
                                ".folio-pdf-",
                                ".pdf");
                        stagedDocuments.add(staged);
                        PdfBoxSplitProductWriter.save(
                                splitDocuments.get(target.getTargetName()),
                                staged);
                    }
                }
            } catch (IOException | RuntimeException failure) {
                throw failure(
                        DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                        "The document could not be staged for publication.");
            }
            emit(request, WorkflowProgressPhase.STAGED);
            requireExecutionAllowed(request, clock);

            closeForSuccess(document);
            document = null;
            closeForSuccess(splitDocuments);
            for (Path staged : stagedDocuments) {
                validate(staged);
            }
            emit(request, WorkflowProgressPhase.VALIDATED);
            requireExecutionAllowed(request, clock);

            emit(request, WorkflowProgressPhase.PUBLICATION_STARTED);
            List<PublicationReceipt> receipts =
                    publishAll(
                            stagedDocuments,
                            publicationTargets,
                            request,
                            clock);
            emit(request, WorkflowProgressPhase.COMPLETED);
            return outcome(
                    result,
                    session.getOutcomeCapabilityId(),
                    request,
                    receipts,
                    providerSelections);
        } finally {
            session.invalidate();
            closeQuietly(document);
            closeQuietly(splitDocuments == null
                    ? session.getSplitDocuments()
                    : splitDocuments);
            deleteQuietly(stagedDocuments);
        }
    }

    private static <R> WorkflowOutcome<R> outcome(
            R result,
            String capabilityId,
            WorkflowRequest request,
            List<PublicationReceipt> receipts,
            List<ProviderSelection> providerSelections) {
        return new WorkflowOutcome<R>(
                result,
                capabilityId,
                WorkflowExecutionProfile.IN_PROCESS,
                request.getSaveMode(),
                Collections.<String>emptyList(),
                receipts,
                providerSelections);
    }

    private static List<PublicationTargetAdapter> preflightTargets(
            Map<String, PublicationTarget> publicationTargets)
            throws DocumentFailure {
        List<PublicationTargetAdapter> prepared =
                new ArrayList<PublicationTargetAdapter>(
                        publicationTargets.size());
        for (Map.Entry<String, PublicationTarget> target
                : publicationTargets.entrySet()) {
            prepared.add(PublicationTargetAdapter.prepare(target));
        }
        return prepared;
    }

    private static void requireExecutionAllowed(
            WorkflowRequest request,
            Clock clock)
            throws DocumentFailure {
        if (request.getCancellationToken().isCancellationRequested()) {
            throw failure(
                    DocumentFailureCode.WORKFLOW_CANCELLED,
                    "The workflow was cancelled.");
        }
        if (request.getDeadline() != null
                && !clock.instant().isBefore(request.getDeadline())) {
            throw failure(
                    DocumentFailureCode.DEADLINE_EXCEEDED,
                    "The workflow deadline has expired.");
        }
    }

    private static void emit(
            WorkflowRequest request,
            WorkflowProgressPhase phase) {
        request.getProgressListener().onProgress(phase);
    }

    private static List<PublicationReceipt> publishAll(
            List<Path> stagedDocuments,
            List<PublicationTargetAdapter> targets,
            WorkflowRequest request,
            Clock clock)
            throws DocumentFailure {
        List<PublicationReceipt> receipts =
                new ArrayList<PublicationReceipt>(targets.size());
        for (int index = 0; index < targets.size(); index++) {
            PublicationTargetAdapter target = targets.get(index);
            Path staged = stagedDocuments.size() == 1
                    ? stagedDocuments.get(0)
                    : stagedDocuments.get(index);
            try {
                requireExecutionAllowed(request, clock);
                target.publish(staged);
            } catch (DocumentFailure publicationFailure) {
                boolean executionStop = isExecutionStop(publicationFailure);
                receipts.add(target.receipt(
                        executionStop
                                ? PublicationStatus.NOT_ATTEMPTED
                                : PublicationStatus.FAILED,
                        !executionStop && target.isStream()));
                for (int later = index + 1; later < targets.size(); later++) {
                    receipts.add(targets.get(later).receipt(
                            PublicationStatus.NOT_ATTEMPTED,
                            false));
                }
                throw failure(
                        publicationFailure.getCode(),
                        publicationFailure.getDiagnostic(),
                        receipts);
            }
            receipts.add(target.receipt(
                    PublicationStatus.COMMITTED,
                    false));
            emit(request, WorkflowProgressPhase.TARGET_COMMITTED);
        }
        return receipts;
    }

    private static boolean isExecutionStop(DocumentFailure failure) {
        return failure.getCode() == DocumentFailureCode.WORKFLOW_CANCELLED
                || failure.getCode() == DocumentFailureCode.DEADLINE_EXCEEDED;
    }

    private static List<PublicationReceipt> notAttempted(
            List<PublicationTargetAdapter> publicationTargets) {
        List<PublicationReceipt> receipts =
                new ArrayList<PublicationReceipt>(publicationTargets.size());
        for (PublicationTargetAdapter target : publicationTargets) {
            receipts.add(target.receipt(
                    PublicationStatus.NOT_ATTEMPTED,
                    false));
        }
        return receipts;
    }

    private static Path normalizedPathTarget(Path requestedTarget)
            throws DocumentFailure {
        Path target = requestedTarget.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw failure(
                    DocumentFailureCode.INVALID_REQUEST,
                    "The publication target parent must be an existing directory.");
        }
        return target;
    }

    static PDDocument openDocument(DocumentSource source)
            throws DocumentFailure {
        switch (source.getKind()) {
            case PATH:
                return loadPathDocument(source.getPath());
            case STREAM:
                return loadByteDocument(readCallerStream(
                        source.getStream(),
                        source.getMaximumBytes()));
            case CHANNEL:
                return loadByteDocument(readCallerChannel(
                        source.getChannel(),
                        source.getMaximumBytes()));
            case BYTES:
                return loadByteDocument(requireBoundedBytes(
                        source.getBytes(),
                        source.getMaximumBytes()));
            default:
                throw new IllegalStateException("Unsupported source kind.");
        }
    }

    private static byte[] readCallerStream(
            InputStream stream,
            long maximumBytes) throws DocumentFailure {
        try {
            return readBounded(stream, maximumBytes);
        } catch (SourceLimitExceeded sourceLimit) {
            throw failure(
                    DocumentFailureCode.SOURCE_LIMIT_EXCEEDED,
                    "The source exceeds its declared byte limit.");
        } catch (IOException ioFailure) {
            throw failure(
                    DocumentFailureCode.SOURCE_READ_FAILED,
                    "The source could not be opened as a PDF document.");
        }
    }

    private static byte[] readCallerChannel(
            ReadableByteChannel channel,
            long maximumBytes) throws DocumentFailure {
        try {
            return readBounded(channel, maximumBytes);
        } catch (SourceLimitExceeded sourceLimit) {
            throw failure(
                    DocumentFailureCode.SOURCE_LIMIT_EXCEEDED,
                    "The source exceeds its declared byte limit.");
        } catch (IOException ioFailure) {
            throw failure(
                    DocumentFailureCode.SOURCE_READ_FAILED,
                    "The source could not be opened as a PDF document.");
        }
    }

    private static byte[] requireBoundedBytes(
            byte[] bytes,
            long maximumBytes) throws DocumentFailure {
        try {
            requireWithinLimit(bytes.length, maximumBytes);
            return bytes;
        } catch (SourceLimitExceeded sourceLimit) {
            throw failure(
                    DocumentFailureCode.SOURCE_LIMIT_EXCEEDED,
                    "The source exceeds its declared byte limit.");
        }
    }

    private static PDDocument loadPathDocument(Path source)
            throws DocumentFailure {
        try {
            Path path = source.toAbsolutePath().normalize();
            return Loader.loadPDF(path.toFile());
        } catch (IOException | RuntimeException backendFailure) {
            throw failure(
                    DocumentFailureCode.SOURCE_READ_FAILED,
                    "The source could not be opened as a PDF document.");
        }
    }

    private static PDDocument loadByteDocument(byte[] bytes)
            throws DocumentFailure {
        try {
            return Loader.loadPDF(bytes);
        } catch (IOException | RuntimeException backendFailure) {
            throw failure(
                    DocumentFailureCode.SOURCE_READ_FAILED,
                    "The source could not be opened as a PDF document.");
        }
    }

    private static byte[] readBounded(InputStream stream, long maximumBytes)
            throws IOException {
        BoundedByteAccumulator bytes =
                new BoundedByteAccumulator(maximumBytes);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = stream.read(buffer)) != -1) {
            bytes.append(buffer, read);
        }
        return bytes.toByteArray();
    }

    private static byte[] readBounded(
            ReadableByteChannel channel,
            long maximumBytes) throws IOException {
        BoundedByteAccumulator bytes =
                new BoundedByteAccumulator(maximumBytes);
        ByteBuffer buffer = ByteBuffer.allocate(8192);
        while (channel.read(buffer) != -1) {
            bytes.append(buffer.array(), buffer.position());
            buffer.clear();
        }
        return bytes.toByteArray();
    }

    private static long checkedTotal(long total, int read, long maximumBytes)
            throws SourceLimitExceeded {
        if (maximumBytes < 0L || total > maximumBytes - read) {
            throw new SourceLimitExceeded();
        }
        return total + read;
    }

    private static void requireWithinLimit(long size, long maximumBytes)
            throws SourceLimitExceeded {
        if (maximumBytes < 0L || size > maximumBytes) {
            throw new SourceLimitExceeded();
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
            if (validationDocument.getNumberOfPages() == 0) {
                throw failure(
                        DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                        "The staged document must contain at least one page.");
            }
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

    private static void publishPath(Path staged, Path target)
            throws DocumentFailure {
        Path targetStage = null;
        try {
            targetStage = Files.createTempFile(
                    target.getParent(),
                    ".folio-pdf-",
                    ".pdf");
            Files.copy(staged, targetStage, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(
                        targetStage,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(
                        targetStage,
                        target,
                        StandardCopyOption.REPLACE_EXISTING);
            }
            targetStage = null;
        } catch (IOException | RuntimeException failure) {
            throw failure(
                    DocumentFailureCode.PUBLICATION_FAILED,
                    "The validated document could not be committed to its target.");
        } finally {
            deleteQuietly(targetStage);
        }
    }

    private static void publishStream(Path staged, OutputStream output)
            throws DocumentFailure {
        try (InputStream input = Files.newInputStream(staged)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read > 0) {
                    output.write(buffer, 0, read);
                }
            }
            output.flush();
        } catch (IOException failure) {
            throw failure(
                    DocumentFailureCode.PUBLICATION_FAILED,
                    "The validated document could not be written to its stream target.");
        }
    }

    private static void closeForSuccess(PDDocument document)
            throws DocumentFailure {
        try {
            document.close();
        } catch (IOException | RuntimeException failure) {
            throw failure(
                    DocumentFailureCode.RESOURCE_CLOSE_FAILED,
                    "A library-owned document resource could not be closed cleanly.");
        }
    }

    private static void closeForSuccess(Map<String, PDDocument> documents)
            throws DocumentFailure {
        if (documents == null) {
            return;
        }
        for (PDDocument document : documents.values()) {
            closeForSuccess(document);
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

    private static void closeQuietly(Map<String, PDDocument> documents) {
        if (documents == null) {
            return;
        }
        for (PDDocument document : documents.values()) {
            closeQuietly(document);
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

    private static void deleteQuietly(List<Path> stagedDocuments) {
        for (Path staged : stagedDocuments) {
            deleteQuietly(staged);
        }
    }

    private static final class PublicationTargetAdapter {

        private final String targetName;
        private final Path pathTarget;
        private final Path normalizedPathTarget;
        private final OutputStream streamTarget;

        private PublicationTargetAdapter(
                String targetName,
                Path pathTarget,
                Path normalizedPathTarget,
                OutputStream streamTarget) {
            this.targetName = targetName;
            this.pathTarget = pathTarget;
            this.normalizedPathTarget = normalizedPathTarget;
            this.streamTarget = streamTarget;
        }

        private static PublicationTargetAdapter prepare(
                Map.Entry<String, PublicationTarget> entry)
                throws DocumentFailure {
            PublicationTarget target = entry.getValue();
            switch (target.getKind()) {
                case PATH:
                    return new PublicationTargetAdapter(
                            entry.getKey(),
                            target.getPath(),
                            normalizedPathTarget(target.getPath()),
                            null);
                case STREAM:
                    return new PublicationTargetAdapter(
                            entry.getKey(),
                            null,
                            null,
                            target.getStream());
                default:
                    throw new IllegalStateException(
                            "Unsupported publication target kind.");
            }
        }

        private void publish(Path staged) throws DocumentFailure {
            if (isStream()) {
                publishStream(staged, streamTarget);
            } else {
                publishPath(staged, normalizedPathTarget);
            }
        }

        private PublicationReceipt receipt(
                PublicationStatus status,
                boolean partialOutputPossible) {
            return new PublicationReceipt(
                    targetName,
                    pathTarget,
                    status,
                    partialOutputPossible);
        }

        private boolean isStream() {
            return streamTarget != null;
        }

        private String getTargetName() {
            return targetName;
        }
    }

    private static final class SourceLimitExceeded extends IOException {

        private static final long serialVersionUID = 1L;
    }

    private static final class BoundedByteAccumulator {

        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final long maximumBytes;
        private long total;

        private BoundedByteAccumulator(long maximumBytes) {
            this.maximumBytes = maximumBytes;
        }

        private void append(byte[] buffer, int length)
                throws SourceLimitExceeded {
            if (length <= 0) {
                return;
            }
            total = checkedTotal(total, length, maximumBytes);
            bytes.write(buffer, 0, length);
        }

        private byte[] toByteArray() {
            return bytes.toByteArray();
        }
    }
}
