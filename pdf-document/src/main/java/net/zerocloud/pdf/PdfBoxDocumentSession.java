package net.zerocloud.pdf;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.command.FlattenAnnotations;
import net.zerocloud.pdf.command.ReplaceOutlineTree;
import net.zerocloud.pdf.command.SetNamedDestinations;
import net.zerocloud.pdf.command.SplitDocument;
import net.zerocloud.pdf.command.UpdateActions;
import net.zerocloud.pdf.command.UpdateAnnotations;
import net.zerocloud.pdf.composition.FontSource;
import net.zerocloud.pdf.composition.command.DrawCanvas;
import net.zerocloud.pdf.composition.command.DrawPositionedUnicodeText;
import net.zerocloud.pdf.composition.query.InspectCanvasImageCapabilities;
import net.zerocloud.pdf.query.DocumentRootReference;
import net.zerocloud.pdf.query.DocumentSecurity;
import net.zerocloud.pdf.query.DocumentVersion;
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
    private final PdfBoxTextStructureExtractionOperations extractionOperations;
    private final PdfBoxImageResourceExtractionOperations
            imageResourceExtractionOperations;
    private final PdfBoxCanvasOperations canvasOperations;
    private final PdfBoxPositionedTextOperations positionedUnicodeTextOperations;
    private final PdfBoxPageOperations pageOperations;
    private final SaveMode saveMode;
    private final PdfBoxSignaturePolicy signaturePolicy;
    private final PdfVersionInfo versionInfo;
    private final PasswordSecurityInfo securityInfo;
    private final WorkflowResourceContext resources;
    private String outcomeCapabilityId;
    private boolean mutationOccurred;
    private volatile boolean active;

    PdfBoxDocumentSession(
            PDDocument document,
            Map<String, PdfBoxWorkflowEngine.PreparedNamedSource> sources,
            boolean libraryOwnedDocument,
            Map<String, PublicationTarget> publicationTargets,
            SaveMode saveMode,
            PdfBoxSignaturePolicy signaturePolicy,
            PdfVersionInfo versionInfo,
            PasswordSecurityInfo securityInfo,
            PdfVersion publicationVersion,
            PasswordEncryptionAlgorithm publicationAlgorithm,
            PasswordEncryptionScope publicationScope,
            List<FontSource> referenceFonts,
            WorkflowResourceContext resources) throws DocumentFailure {
        this.document = Objects.requireNonNull(document, "document");
        this.owner = Thread.currentThread();
        this.valueAdapter = new PdfBoxValueAdapter(
                document,
                this,
                resources);
        this.metadataOperations = new PdfBoxMetadataOperations(
                document,
                resources);
        this.annotationOperations = new PdfBoxAnnotationOperations(
                document,
                metadataOperations,
                resources);
        this.annotationPageOperations = new PdfBoxAnnotationPageOperations(
                document,
                metadataOperations,
                annotationOperations,
                resources);
        this.extractionOperations =
                new PdfBoxTextStructureExtractionOperations(
                        document,
                        resources);
        this.imageResourceExtractionOperations =
                new PdfBoxImageResourceExtractionOperations(
                        document,
                        valueAdapter,
                        resources);
        this.canvasOperations = new PdfBoxCanvasOperations(
                document,
                valueAdapter,
                resources);
        this.positionedUnicodeTextOperations = new PdfBoxPositionedTextOperations(
                document,
                referenceFonts,
                publicationVersion,
                resources);
        this.pageOperations = new PdfBoxPageOperations(
                document,
                sources,
                libraryOwnedDocument,
                publicationTargets,
                publicationVersion,
                publicationAlgorithm,
                publicationScope,
                valueAdapter,
                metadataOperations,
                annotationPageOperations,
                resources);
        this.saveMode = Objects.requireNonNull(saveMode, "saveMode");
        this.signaturePolicy = Objects.requireNonNull(
                signaturePolicy,
                "signaturePolicy");
        this.versionInfo = Objects.requireNonNull(versionInfo, "versionInfo");
        this.securityInfo = Objects.requireNonNull(securityInfo, "securityInfo");
        this.resources = Objects.requireNonNull(resources, "resources");
        this.outcomeCapabilityId = PdfBoxWorkflowEngine.CAPABILITY_ID;
        this.active = true;
    }

    @Override
    public void execute(DocumentCommand command) throws DocumentFailure {
        requireActiveOwner();
        Objects.requireNonNull(command, "command");
        try {
            resources.checkpoint();
            executeChecked(command);
            resources.audit(document);
            resources.checkpoint();
        } catch (DocumentFailure failure) {
            resources.rethrowTerminalFailure();
            throw failure;
        } catch (RuntimeException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw failure;
        }
    }

    private void executeChecked(DocumentCommand command)
            throws DocumentFailure {
        positionedUnicodeTextOperations.finalizeFonts();
        pageOperations.requireCommandAllowed();
        boolean canvasCommand = canvasOperations.supports(command);
        boolean positionedUnicodeTextCommand =
                positionedUnicodeTextOperations.supports(command);
        if (saveMode == SaveMode.REWRITE
                && signaturePolicy.hasExistingSignatures()) {
            throw signatureFailure(command);
        }
        if (saveMode == SaveMode.INCREMENTAL
                && !PdfBoxIncrementalCommandPolicy.supports(command)) {
            throw PdfBoxWorkflowEngine.incrementalFailure(
                    DocumentFailureCode.INCREMENTAL_COMMAND_REJECTED,
                    "The command is not supported for INCREMENTAL publication.");
        }
        if (saveMode == SaveMode.INCREMENTAL
                && !signaturePolicy.permits(command)) {
            throw signatureFailure(command);
        }
        if (saveMode == SaveMode.INCREMENTAL
                && signaturePolicy.requiresNonWidgetAnnotationPolicy(command)) {
            annotationOperations.requireNonWidgetSignatureUpdate(
                    (UpdateAnnotations) command);
        }

        if (command == AddBlankPage.INSTANCE) {
            PdfBoxPermissionPolicy.requireAssembly(securityInfo);
            try {
                PDPage page = new PDPage();
                page.setResources(new PDResources());
                document.addPage(page);
                pageOperations.makeLibraryOwnedPageIndirect(page);
                mutationOccurred = true;
                return;
            } catch (RuntimeException backendFailure) {
                resources.rethrowResourceOrTerminalFailure(backendFailure);
                throw PdfBoxWorkflowEngine.failure(
                        DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                        "The blank page could not be added.");
            }
        }

        if (pageOperations.supports(command)) {
            if (command instanceof SplitDocument) {
                PdfBoxPermissionPolicy.requireExtraction(securityInfo);
            } else {
                PdfBoxPermissionPolicy.requireAssembly(securityInfo);
            }
            outcomeCapabilityId = PdfBoxPageOperations.CAPABILITY_ID;
            pageOperations.execute(command);
            mutationOccurred = true;
            return;
        }

        if (metadataOperations.supports(command)) {
            if (command instanceof ReplaceOutlineTree) {
                PdfBoxPermissionPolicy.requireAssembly(securityInfo);
            } else {
                PdfBoxPermissionPolicy.requireModification(securityInfo);
            }
            if (command instanceof SetNamedDestinations) {
                annotationPageOperations.requireNamedDestinationRemovalSafe(
                        ((SetNamedDestinations) command).getRemovedNames());
            }
            outcomeCapabilityId = PdfBoxMetadataOperations.CAPABILITY_ID;
            metadataOperations.execute(command);
            mutationOccurred = true;
            return;
        }

        if (annotationOperations.supports(command)) {
            if (command instanceof UpdateActions) {
                PdfBoxPermissionPolicy.requireModification(securityInfo);
            } else {
                PdfBoxPermissionPolicy.requireAnnotationModification(
                        securityInfo);
                if (command instanceof FlattenAnnotations) {
                    PdfBoxPermissionPolicy.requireModification(securityInfo);
                }
            }
            outcomeCapabilityId = PdfBoxAnnotationOperations.CAPABILITY_ID;
            annotationOperations.execute(command);
            mutationOccurred = true;
            return;
        }

        if (canvasCommand) {
            PdfBoxCanvasOperations.requireModificationPermission(
                    securityInfo,
                    (DrawCanvas) command);
            outcomeCapabilityId = canvasOperations.capabilityId(
                    (DrawCanvas) command);
            canvasOperations.execute((DrawCanvas) command);
            mutationOccurred = true;
            return;
        }

        if (positionedUnicodeTextCommand) {
            PdfBoxPositionedTextOperations.requireModificationPermission(
                    securityInfo);
            outcomeCapabilityId = PdfBoxPositionedTextOperations.CAPABILITY_ID;
            positionedUnicodeTextOperations.execute(
                    (DrawPositionedUnicodeText) command);
            mutationOccurred = true;
            return;
        }

        if (command instanceof DocumentPatch) {
            PdfBoxPermissionPolicy.requireModification(securityInfo);
            PdfBoxPermissionPolicy.requireAnnotationModification(securityInfo);
            PdfBoxPermissionPolicy.requireAssembly(securityInfo);
            outcomeCapabilityId = PdfBoxValueAdapter.CAPABILITY_ID;
            try {
                valueAdapter.apply((DocumentPatch) command);
                mutationOccurred = true;
                return;
            } catch (RuntimeException backendFailure) {
                resources.rethrowResourceOrTerminalFailure(backendFailure);
                throw PdfBoxValueAdapter.failure(
                        DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                        "The Document Patch could not be applied.");
            }
        }

        throw PdfBoxWorkflowEngine.failure(
                DocumentFailureCode.COMMAND_REJECTED,
                "The command is not supported by this workflow version.");
    }

    private static DocumentFailure signatureFailure(DocumentCommand command) {
        if (command instanceof DrawCanvas) {
            return PdfBoxCanvasOperations.signatureFailure(
                    (DrawCanvas) command);
        }
        if (command instanceof DrawPositionedUnicodeText) {
            return PdfBoxPositionedTextOperations.signatureFailure();
        }
        return PdfBoxWorkflowEngine.signaturePolicyFailure();
    }

    void finalizeFonts() throws DocumentFailure {
        try {
            positionedUnicodeTextOperations.finalizeFonts();
        } catch (DocumentFailure failure) {
            resources.rethrowTerminalFailure();
            throw failure;
        } catch (RuntimeException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw failure;
        }
    }

    boolean hasMutationOccurred() {
        return mutationOccurred;
    }

    @Override
    public <R> R query(DocumentQuery<R> query) throws DocumentFailure {
        requireActiveOwner();
        Objects.requireNonNull(query, "query");
        try {
            resources.checkpoint();
            R result = evaluate(query);
            resources.audit(document);
            resources.checkpoint();
            return result;
        } catch (DocumentFailure failure) {
            resources.rethrowTerminalFailure();
            throw failure;
        } catch (RuntimeException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw failure;
        }
    }

    private <R> R evaluate(DocumentQuery<R> query) throws DocumentFailure {
        positionedUnicodeTextOperations.finalizeFonts();

        if (query == PageCount.INSTANCE) {
            try {
                return pageCountResult(document.getNumberOfPages());
            } catch (RuntimeException backendFailure) {
                resources.rethrowResourceOrTerminalFailure(backendFailure);
                throw PdfBoxWorkflowEngine.failure(
                        DocumentFailureCode.QUERY_FAILED,
                        "The page count could not be evaluated.");
            }
        }

        if (query == DocumentVersion.INSTANCE) {
            outcomeCapabilityId =
                    PdfBoxWorkflowEngine.VERSION_SECURITY_CAPABILITY_ID;
            return queryResult(versionInfo);
        }

        if (query == DocumentSecurity.INSTANCE) {
            outcomeCapabilityId =
                    PdfBoxWorkflowEngine.VERSION_SECURITY_CAPABILITY_ID;
            return queryResult(securityInfo);
        }

        if (query instanceof InspectCanvasImageCapabilities) {
            outcomeCapabilityId =
                    PdfBoxCanvasResourceOperations.CAPABILITY_ID;
            return queryResult(PdfBoxCanvasResourceOperations.capabilities());
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
                resources.rethrowResourceOrTerminalFailure(backendFailure);
                throw PdfBoxValueAdapter.failure(
                        DocumentFailureCode.QUERY_FAILED,
                        "The document root could not be inspected.");
            }
        }

        if (query instanceof InspectObject) {
            PdfBoxPermissionPolicy.requireExtraction(securityInfo);
            outcomeCapabilityId = PdfBoxValueAdapter.CAPABILITY_ID;
            InspectObject inspection = (InspectObject) query;
            try {
                return queryResult(valueAdapter.inspect(
                        inspection.getReference(),
                        inspection.getLimits()));
            } catch (RuntimeException backendFailure) {
                resources.rethrowResourceOrTerminalFailure(backendFailure);
                throw PdfBoxValueAdapter.failure(
                        DocumentFailureCode.QUERY_FAILED,
                        "The PDF object could not be inspected.");
            }
        }

        if (metadataOperations.supportsQuery(query)) {
            PdfBoxPermissionPolicy.requireExtraction(securityInfo);
            outcomeCapabilityId = PdfBoxMetadataOperations.CAPABILITY_ID;
            return queryResult(metadataOperations.evaluate(query));
        }


        if (annotationOperations.supportsQuery(query)) {
            PdfBoxPermissionPolicy.requireExtraction(securityInfo);
            outcomeCapabilityId = PdfBoxAnnotationOperations.CAPABILITY_ID;
            return queryResult(annotationOperations.evaluate(query));
        }

        if (extractionOperations.supportsQuery(query)) {
            PdfBoxPermissionPolicy.requireExtraction(securityInfo);
            outcomeCapabilityId =
                    PdfBoxTextStructureExtractionOperations.CAPABILITY_ID;
            return queryResult(extractionOperations.evaluate(
                    (net.zerocloud.pdf.query.ExtractTextAndStructure) query));
        }

        if (imageResourceExtractionOperations.supportsQuery(query)) {
            PdfBoxPermissionPolicy.requireExtraction(securityInfo);
            outcomeCapabilityId =
                    PdfBoxImageResourceExtractionOperations.CAPABILITY_ID;
            return queryResult(imageResourceExtractionOperations.evaluate(
                    (net.zerocloud.pdf.query.ExtractImagesAndResources) query));
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
