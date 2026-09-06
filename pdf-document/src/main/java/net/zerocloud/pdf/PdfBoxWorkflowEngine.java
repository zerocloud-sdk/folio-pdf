package net.zerocloud.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.zerocloud.pdf.composition.FontSource;
import net.zerocloud.pdf.provider.ProviderSelection;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

final class PdfBoxWorkflowEngine {

    static final String CAPABILITY_ID = "document.blank.create-publish-reopen";
    static final String INCREMENTAL_CAPABILITY_ID =
            "document.incremental-signature.protect";
    static final String VERSION_SECURITY_CAPABILITY_ID =
            "document.version-password-security";

    private PdfBoxWorkflowEngine() {
    }

    static <R> WorkflowOutcome<R> execute(ExecutionContext<R> context)
            throws DocumentFailure {
        try {
            return executeChecked(context);
        } catch (DocumentFailure executionFailure) {
            if (!context.request.getPublicationTargets().isEmpty()
                    && executionFailure.getPublicationReceipts().isEmpty()) {
                throw failure(
                        executionFailure.getCode(),
                        executionFailure.getCapabilityId(),
                        executionFailure.getDiagnostic(),
                        PublicationReceipt.notAttempted(
                                context.request.getPublicationTargets()));
            }
            throw executionFailure;
        }
    }

    private static <R> WorkflowOutcome<R> executeChecked(
            ExecutionContext<R> context) throws DocumentFailure {
        WorkflowRequest request = context.request;
        if (request.getSaveMode() == SaveMode.INCREMENTAL) {
            if (request.getSources().isEmpty()) {
                throw new DocumentFailure(
                        DocumentFailureCode.INCREMENTAL_SOURCE_REQUIRED,
                        INCREMENTAL_CAPABILITY_ID,
                        "INCREMENTAL publication requires an existing primary Source.");
            }
        }
        if (request.getSaveMode() != SaveMode.REWRITE
                && request.getSaveMode() != SaveMode.INCREMENTAL) {
            throw failure(
                    DocumentFailureCode.INVALID_REQUEST,
                    "The workflow request must select a supported Save Mode.");
        }
        requireExecutionAllowed(context);
        emit(context, WorkflowProgressPhase.STARTED);
        if (request.getSources().isEmpty()
                && request.getPublicationTargets().isEmpty()) {
            throw failure(
                    DocumentFailureCode.INVALID_REQUEST,
                    "The workflow request has no source or publication target.");
        }
        List<PublicationTargetAdapter> publicationTargets =
                preflightTargets(
                        request.getPublicationTargets(),
                        context.resources);

        PDDocument document;
        long sourceLength = -1L;
        SourceFingerprint sourceFingerprint = null;
        PasswordCredential primaryCredential = null;
        InputStream ownedPrimaryPath = null;
        PdfVersion declaredHeaderVersion;
        if (request.getSources().isEmpty()) {
            document = createDocument(context.resources);
            declaredHeaderVersion = PdfVersion.PDF_1_7;
        } else {
            String primarySourceName = request.getPrimarySourceName();
            DocumentSource source = request.getSources().get(
                    primarySourceName);
            if (source == null) {
                throw failure(
                        DocumentFailureCode.INVALID_REQUEST,
                        "The workflow request must select a declared primary source.");
            }
            source = context.resolveSource(primarySourceName, source);
            OpenedDocument opened = openPrimaryDocument(
                    source,
                    primarySourceName,
                    true,
                    context);
            primaryCredential = opened.credential;
            document = opened.document;
            sourceLength = opened.sourceLength;
            sourceFingerprint = opened.fingerprint;
            declaredHeaderVersion = opened.declaredHeaderVersion;
            ownedPrimaryPath = opened.ownedPathInput;
        }

        try {
            context.resources.audit(document);
        } catch (DocumentFailure preflightFailure) {
            closeQuietly(document);
            closeQuietly(ownedPrimaryPath);
            throw preflightFailure;
        }

        PdfBoxSignaturePolicy signaturePolicy;
        PdfVersionInfo versionInfo;
        PasswordSecurityInfo securityInfo;
        PdfVersion outputVersion;
        PasswordSecurityPolicy requestedSecurity = null;
        PdfBoxPasswordSecurity.PreparedOutput outputSecurity = null;
        try {
            versionInfo = PdfBoxVersionPolicy.inspect(
                    document,
                    declaredHeaderVersion);
            securityInfo = PdfBoxPasswordSecurity.inspect(
                    document,
                    primaryCredential,
                    context.resources);
            PdfBoxPasswordSecurity.requireCompatibleInput(
                    document,
                    versionInfo,
                    securityInfo);
            if (securityInfo.isPasswordProtected()
                    && primaryCredential == null) {
                throw credentialFailure(true);
            }
            outputVersion = outputVersion(request, versionInfo);
            signaturePolicy = PdfBoxSignaturePolicy.inspect(
                    document, sourceLength, context.resources);
            requestedSecurity = request.getPublicationTargets().isEmpty()
                    || request.getOutputPolicy() == null
                    ? null
                    : request.getOutputPolicy().getPasswordSecurity();
            if (!request.getPublicationTargets().isEmpty()
                    && securityInfo.isPasswordProtected()
                    && request.getSaveMode() == SaveMode.REWRITE
                    && securityInfo.getCredentialAuthority()
                            != CredentialAuthority.OWNER) {
                throw versionFailure(
                        DocumentFailureCode.DOCUMENT_PERMISSION_DENIED,
                        "Protected rewrite requires proven owner authority.");
            }
            if (!request.getPublicationTargets().isEmpty()
                    && securityInfo.isPasswordProtected()
                    && requestedSecurity == null
                    && request.getSaveMode() == SaveMode.REWRITE) {
                throw versionFailure(
                        DocumentFailureCode.PASSWORD_SECURITY_POLICY_REQUIRED,
                        "Rewriting a protected Source requires an explicit password-security output policy.");
            }
            if (!request.getPublicationTargets().isEmpty()
                    && securityInfo.isPasswordProtected()
                    && requestedSecurity == null
                    && request.getSaveMode() == SaveMode.INCREMENTAL) {
                outputSecurity =
                        PdfBoxPasswordSecurity
                                .preserveForIncrementalValidation(
                                        primaryCredential,
                                        context.resources);
            } else {
                requestedSecurity = context.resolveOutputSecurity(
                        requestedSecurity);
                outputSecurity = PdfBoxPasswordSecurity.prepare(
                        requestedSecurity,
                        outputVersion,
                        request.getSaveMode(),
                        request.getLegacySecurityMode(),
                        context.resources);
            }
            outputSecurity.preflight(document);
        } catch (DocumentFailure policyFailure) {
            if (outputSecurity != null) {
                outputSecurity.close();
            }
            closeQuietly(document);
            closeQuietly(ownedPrimaryPath);
            throw policyFailure;
        }

        PdfVersion publicationVersion = request.getPublicationTargets().isEmpty()
                ? null
                : request.getSaveMode() == SaveMode.INCREMENTAL
                        ? versionInfo.getEffectiveVersion() : outputVersion;
        PasswordEncryptionAlgorithm publicationAlgorithm = null;
        PasswordEncryptionScope publicationScope = null;
        if (!request.getPublicationTargets().isEmpty()) {
            if (request.getSaveMode() == SaveMode.INCREMENTAL
                    && securityInfo.isPasswordProtected()) {
                publicationAlgorithm = securityInfo.getAlgorithm().get();
                publicationScope = securityInfo.getEncryptionScope();
            } else if (requestedSecurity != null) {
                publicationAlgorithm = requestedSecurity.getAlgorithm();
                publicationScope = requestedSecurity.getEncryptionScope();
            }
        }

        Map<String, PreparedNamedSource> preparedSources;
        try {
            preparedSources = prepareNamedSources(
                    request,
                    publicationVersion,
                    publicationAlgorithm,
                    publicationScope,
                    context);
        } catch (DocumentFailure preparationFailure) {
            outputSecurity.close();
            closeQuietly(document);
            closeQuietly(ownedPrimaryPath);
            throw preparationFailure;
        } catch (RuntimeException callerFailure) {
            outputSecurity.close();
            closeQuietly(document);
            closeQuietly(ownedPrimaryPath);
            throw callerFailure;
        }

        return executeWithDocument(
                document,
                signaturePolicy,
                versionInfo,
                securityInfo,
                outputVersion,
                outputSecurity,
                preparedSources,
                publicationVersion,
                publicationAlgorithm,
                publicationScope,
                publicationTargets,
                context,
                sourceFingerprint,
                ownedPrimaryPath);
    }

    static DocumentFailure failure(DocumentFailureCode code, String diagnostic) {
        return new DocumentFailure(code, CAPABILITY_ID, diagnostic);
    }

    static DocumentFailure incrementalFailure(
            DocumentFailureCode code,
            String diagnostic) {
        return new DocumentFailure(code, INCREMENTAL_CAPABILITY_ID, diagnostic);
    }

    static DocumentFailure versionFailure(
            DocumentFailureCode code,
            String diagnostic) {
        return new DocumentFailure(
                code,
                VERSION_SECURITY_CAPABILITY_ID,
                diagnostic);
    }

    static DocumentFailure signaturePolicyFailure() {
        return incrementalFailure(
                DocumentFailureCode.SIGNATURE_POLICY_REJECTED,
                "The Existing Signature policy does not permit this workflow.");
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
            PdfBoxSignaturePolicy signaturePolicy,
            PdfVersionInfo versionInfo,
            PasswordSecurityInfo securityInfo,
            PdfVersion outputVersion,
            PdfBoxPasswordSecurity.PreparedOutput outputSecurity,
            Map<String, PreparedNamedSource> preparedSources,
            PdfVersion publicationVersion,
            PasswordEncryptionAlgorithm publicationAlgorithm,
            PasswordEncryptionScope publicationScope,
            List<PublicationTargetAdapter> publicationTargets,
            ExecutionContext<R> context,
            SourceFingerprint sourceFingerprint,
            InputStream ownedPrimaryPath) throws DocumentFailure {
        WorkflowRequest request = context.request;
        PDDocument document = initialDocument;
        PdfBoxDocumentSession session = null;
        List<Path> stagedDocuments = new ArrayList<Path>();
        Map<String, PDDocument> splitDocuments = null;

        try {
            session = new PdfBoxDocumentSession(
                    document,
                    preparedSources,
                    request.getSources().isEmpty(),
                    request.getPublicationTargets(),
                    request.getSaveMode(),
                    signaturePolicy,
                    versionInfo,
                    securityInfo,
                    publicationVersion,
                    publicationAlgorithm,
                    publicationScope,
                    context.referenceFonts,
                    context.resources);
            requireExecutionAllowed(context);
            if (signaturePolicy.hasExistingSignatures()
                    && request.getSaveMode() == SaveMode.REWRITE
                    && !publicationTargets.isEmpty()) {
                throw incrementalFailure(
                        DocumentFailureCode.SIGNED_REWRITE_REJECTED,
                        "A Source with an Existing Signature cannot be published with REWRITE.");
            }
            if (!request.getSources().isEmpty()) {
                emit(context, WorkflowProgressPhase.SOURCE_OPENED);
            }
            emit(context, WorkflowProgressPhase.WORK_STARTED);
            requireExecutionAllowed(context);
            R result;
            try {
                result = context.work.perform(session);
                session.requireCompleteComposition();
            } catch (DocumentFailure workFailure) {
                context.resources.rethrowTerminalFailure();
                throw failure(
                        workFailure.getCode(),
                        workFailure.getCapabilityId(),
                        workFailure.getDiagnostic(),
                        notAttempted(publicationTargets));
            } catch (RuntimeException callerFailure) {
                context.resources.rethrowResourceOrTerminalFailure(
                        callerFailure);
                throw callerFailure;
            }
            session.invalidate();
            splitDocuments = session.getSplitDocuments();
            emit(context, WorkflowProgressPhase.WORK_COMPLETED);
            requireExecutionAllowed(context);
            context.resources.audit(document);

            if (signaturePolicy.hasExistingSignatures()
                    && request.getSaveMode() == SaveMode.INCREMENTAL
                    && !publicationTargets.isEmpty()
                    && !signaturePolicy.permitsSignedPublication(
                            session.hasMutationOccurred())) {
                throw signaturePolicyFailure();
            }

            if (publicationTargets.isEmpty()) {
                closeForSuccess(document);
                document = null;
                emit(context, WorkflowProgressPhase.COMPLETED);
                return outcome(
                        result,
                        outcomeCapabilityId(request, session),
                        Collections.<PublicationReceipt>emptyList(),
                        context);
            }

            try {
                if (splitDocuments == null) {
                    if (outputVersion != null) {
                        PdfBoxVersionPolicy.setOutputVersion(
                                document,
                                outputVersion);
                    }
                    session.finalizeFonts();
                    outputSecurity.apply(document, context.resources);
                    context.resources.audit(document);
                    Path staged = context.resources.createTemporaryFile(
                            ".staged-document-",
                            ".pdf");
                    stagedDocuments.add(staged);
                    save(
                            document,
                            staged,
                            request.getSaveMode(),
                            context.resources);
                    if (request.getSaveMode() == SaveMode.INCREMENTAL) {
                        validateIncrementalRevision(
                                staged,
                                sourceFingerprint,
                                context.resources);
                    }
                } else {
                    for (PublicationTargetAdapter target : publicationTargets) {
                        requireExecutionAllowed(context);
                        Path staged = context.resources.createTemporaryFile(
                                ".staged-product-",
                                ".pdf");
                        stagedDocuments.add(staged);
                        if (outputVersion != null) {
                            PdfBoxVersionPolicy.setOutputVersion(
                                    splitDocuments.get(target.getTargetName()),
                                    outputVersion);
                        }
                        outputSecurity.apply(
                                splitDocuments.get(target.getTargetName()),
                                context.resources);
                        context.resources.audit(
                                splitDocuments.get(target.getTargetName()));
                        PdfBoxSplitProductWriter.save(
                                splitDocuments.get(target.getTargetName()),
                                staged,
                                context.resources);
                    }
                }
            } catch (DocumentFailure failure) {
                context.resources.rethrowTerminalFailure();
                throw failure;
            } catch (IOException | RuntimeException failure) {
                context.resources.rethrowResourceOrTerminalFailure(failure);
                throw failure(
                        DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                        "The document could not be staged for publication.");
            }
            emit(context, WorkflowProgressPhase.STAGED);
            requireExecutionAllowed(context);

            closeForSuccess(document);
            document = null;
            closeQuietly(ownedPrimaryPath);
            ownedPrimaryPath = null;
            closeForSuccess(splitDocuments);
            for (Path staged : stagedDocuments) {
                validate(
                        staged,
                        outputSecurity,
                        publicationVersion,
                        request.getSaveMode(),
                        securityInfo,
                        context.resources);
            }
            emit(context, WorkflowProgressPhase.VALIDATED);
            requireExecutionAllowed(context);

            emit(context, WorkflowProgressPhase.PUBLICATION_STARTED);
            List<PublicationReceipt> receipts =
                    publishAll(
                            stagedDocuments,
                            publicationTargets,
                            context.request,
                            context.resources);
            emit(context, WorkflowProgressPhase.COMPLETED);
            return outcome(
                    result,
                    outcomeCapabilityId(request, session),
                    receipts,
                    context);
        } finally {
            if (session != null) {
                session.invalidate();
            }
            closeQuietly(document);
            if (splitDocuments != null) {
                closeQuietly(splitDocuments);
            } else if (session != null) {
                closeQuietly(session.getSplitDocuments());
            }
            outputSecurity.close();
            closePreparedSources(preparedSources);
            releaseTemporaryFiles(context.resources, stagedDocuments);
            closeQuietly(ownedPrimaryPath);
        }
    }

    private static <R> WorkflowOutcome<R> outcome(
            R result,
            String capabilityId,
            List<PublicationReceipt> receipts,
            ExecutionContext<R> context) {
        WorkflowRequest request = context.request;
        String reportedCapabilityId = capabilityId;
        if (request.getSaveMode() == SaveMode.INCREMENTAL
                && !isCompositionCapability(capabilityId)) {
            reportedCapabilityId = INCREMENTAL_CAPABILITY_ID;
        }
        return new WorkflowOutcome<R>(
                result,
                reportedCapabilityId,
                WorkflowExecutionProfile.IN_PROCESS,
                request.getSaveMode(),
                Collections.<String>emptyList(),
                receipts,
                context.providerSelections,
                request.getTransactionId().orElse(null));
    }

    private static String outcomeCapabilityId(
            WorkflowRequest request,
            PdfBoxDocumentSession session) {
        if (isCompositionCapability(session.getOutcomeCapabilityId())) {
            return session.getOutcomeCapabilityId();
        }
        return !request.getPublicationTargets().isEmpty()
                        && request.getOutputPolicy() != null
                ? VERSION_SECURITY_CAPABILITY_ID
                : session.getOutcomeCapabilityId();
    }

    private static boolean isCompositionCapability(String capabilityId) {
        return PdfBoxCanvasOperations.CAPABILITY_ID.equals(capabilityId)
                || PdfBoxCanvasResourceOperations.CAPABILITY_ID.equals(
                        capabilityId)
                || PdfBoxPositionedTextOperations.CAPABILITY_ID.equals(
                        capabilityId);
    }

    private static void save(
            PDDocument document,
            Path staged,
            SaveMode saveMode,
            WorkflowResourceContext resources)
            throws IOException, DocumentFailure {
        try (OutputStream output = resources.openTemporaryOutput(staged)) {
            if (saveMode == SaveMode.INCREMENTAL) {
                document.saveIncremental(output);
            } else {
                document.save(output);
            }
        }
    }

    private static PdfVersion outputVersion(
            WorkflowRequest request,
            PdfVersionInfo sourceVersion) throws DocumentFailure {
        if (request.getPublicationTargets().isEmpty()) {
            return null;
        }
        PdfOutputPolicy policy = request.getOutputPolicy();
        if (request.getSaveMode() == SaveMode.INCREMENTAL) {
            if (policy == null) {
                return null;
            }
            requireSupportedOutputVersion(policy.getVersion());
            if (policy.getVersion() != sourceVersion.getEffectiveVersion()) {
                throw versionFailure(
                        DocumentFailureCode.PDF_VERSION_UNSUPPORTED,
                        "INCREMENTAL publication cannot change the effective PDF version.");
            }
            return null;
        }
        PdfVersion version = policy == null
                ? PdfVersion.PDF_1_7 : policy.getVersion();
        requireSupportedOutputVersion(version);
        if (sourceVersion.getEffectiveVersion() == PdfVersion.PDF_2_0
                && version != PdfVersion.PDF_2_0) {
            throw versionFailure(
                    DocumentFailureCode.PDF_VERSION_UNSUPPORTED,
                    "A PDF 2.0 Source cannot be rewritten as PDF 1.7.");
        }
        return version;
    }

    private static void requireSupportedOutputVersion(PdfVersion version)
            throws DocumentFailure {
        if (version != PdfVersion.PDF_1_7
                && version != PdfVersion.PDF_2_0) {
            throw versionFailure(
                    DocumentFailureCode.PDF_VERSION_UNSUPPORTED,
                    "Only PDF 1.7 and PDF 2.0 output are supported.");
        }
    }

    private static List<PublicationTargetAdapter> preflightTargets(
            Map<String, PublicationTarget> publicationTargets,
            WorkflowResourceContext resources)
            throws DocumentFailure {
        List<PublicationTargetAdapter> prepared =
                new ArrayList<PublicationTargetAdapter>(
                        publicationTargets.size());
        for (Map.Entry<String, PublicationTarget> target
                : publicationTargets.entrySet()) {
            resources.checkpoint();
            prepared.add(PublicationTargetAdapter.prepare(target));
        }
        return prepared;
    }

    static WorkerPublicationPlan prepareWorkerPublication(
            WorkflowRequest request,
            WorkflowResourceContext resources) throws DocumentFailure {
        return new WorkerPublicationPlan(
                request,
                resources,
                preflightTargets(request.getPublicationTargets(), resources));
    }

    private static void requireExecutionAllowed(ExecutionContext<?> context)
            throws DocumentFailure {
        context.resources.checkpoint();
    }

    private static void emit(
            ExecutionContext<?> context,
            WorkflowProgressPhase phase) {
        context.request.getProgressListener().onProgress(phase);
    }

    private static List<PublicationReceipt> publishAll(
            List<Path> stagedDocuments,
            List<PublicationTargetAdapter> targets,
            WorkflowRequest request,
            WorkflowResourceContext resources) throws DocumentFailure {
        List<PublicationReceipt> receipts =
                new ArrayList<PublicationReceipt>(targets.size());
        for (int index = 0; index < targets.size(); index++) {
            PublicationTargetAdapter target = targets.get(index);
            Path staged = stagedDocuments.size() == 1
                    ? stagedDocuments.get(0)
                    : stagedDocuments.get(index);
            try {
                resources.checkpoint();
                target.publish(staged, resources);
            } catch (DocumentFailure publicationFailure) {
                boolean executionStop = isExecutionStop(publicationFailure);
                boolean partialStream = target.isStream()
                        && target.isStreamWriteAttempted();
                receipts.add(target.receipt(
                        executionStop && !partialStream
                                ? PublicationStatus.NOT_ATTEMPTED
                                : PublicationStatus.FAILED,
                        partialStream
                                || (!executionStop && target.isStream())));
                for (int later = index + 1; later < targets.size(); later++) {
                    receipts.add(targets.get(later).receipt(
                            PublicationStatus.NOT_ATTEMPTED,
                            false));
                }
                throw failure(
                        publicationFailure.getCode(),
                        publicationFailure.getCapabilityId(),
                        publicationFailure.getDiagnostic(),
                        receipts);
            }
            receipts.add(target.receipt(
                    PublicationStatus.COMMITTED,
                    false));
            request.getProgressListener().onProgress(
                    WorkflowProgressPhase.TARGET_COMMITTED);
            try {
                resources.checkpoint();
            } catch (DocumentFailure executionStop) {
                for (int later = index + 1; later < targets.size(); later++) {
                    receipts.add(targets.get(later).receipt(
                            PublicationStatus.NOT_ATTEMPTED,
                            false));
                }
                throw failure(
                        executionStop.getCode(),
                        executionStop.getCapabilityId(),
                        executionStop.getDiagnostic(),
                        receipts);
            }
        }
        if (targets.isEmpty()) {
            resources.checkpoint();
        }
        return receipts;
    }

    private static boolean isExecutionStop(DocumentFailure failure) {
        return failure.getCode() == DocumentFailureCode.WORKFLOW_CANCELLED
                || failure.getCode() == DocumentFailureCode.DEADLINE_EXCEEDED
                || failure.getCode()
                        == DocumentFailureCode.ELAPSED_TIME_LIMIT_EXCEEDED;
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

    private static Map<String, PreparedNamedSource> prepareNamedSources(
            WorkflowRequest request,
            PdfVersion publicationVersion,
            PasswordEncryptionAlgorithm publicationAlgorithm,
            PasswordEncryptionScope publicationScope,
            ExecutionContext<?> context) throws DocumentFailure {
        WorkflowResourceContext resources = context.resources;
        Map<String, PreparedNamedSource> prepared =
                new LinkedHashMap<String, PreparedNamedSource>();
        try {
            for (Map.Entry<String, DocumentSource> entry
                    : request.getSources().entrySet()) {
                if (!entry.getKey().equals(request.getPrimarySourceName())) {
                    DocumentSource materialized = context.resolveSource(
                            entry.getKey(),
                            entry.getValue());
                    PreparedNamedSource source =
                            PreparedNamedSource.prepare(
                                    entry.getKey(),
                                    materialized,
                                    context);
                    prepared.put(entry.getKey(), source);
                    source.requirePublicationCompatible(
                            publicationVersion,
                            publicationAlgorithm,
                            publicationScope);
                }
            }
            return prepared;
        } catch (DocumentFailure failure) {
            closePreparedSources(prepared);
            throw failure;
        } catch (RuntimeException callerFailure) {
            closePreparedSources(prepared);
            resources.rethrowResourceOrTerminalFailure(callerFailure);
            throw callerFailure;
        }
    }

    private static Path snapshot(
            DocumentSource source,
            WorkflowResourceContext resources)
            throws DocumentFailure {
        return snapshot(source, resources, Long.MAX_VALUE);
    }

    private static Path snapshot(
            DocumentSource source,
            WorkflowResourceContext resources,
            long temporaryStorageAllowance)
            throws DocumentFailure {
        Path snapshot = null;
        try {
            snapshot = resources.createTemporaryFile(
                    ".folio-pdf-source-",
                    ".pdf");
            try (OutputStream output = resources.openTemporaryOutput(snapshot)) {
                switch (source.getKind()) {
                    case PATH:
                        try (InputStream input = Files.newInputStream(
                                source.getPath().toAbsolutePath().normalize())) {
                            copySourceContents(
                                    input,
                                    output,
                                    source.getMaximumBytes(),
                                    temporaryStorageAllowance,
                                    resources);
                        }
                        break;
                    case STREAM:
                        copySourceContents(
                                source.getStream(),
                                output,
                                source.getMaximumBytes(),
                                temporaryStorageAllowance,
                                resources);
                        break;
                    case CHANNEL:
                        copySourceContents(
                                source.getChannel(),
                                output,
                                source.getMaximumBytes(),
                                temporaryStorageAllowance,
                                resources);
                        break;
                    case BYTES:
                        copySourceBytes(
                                source.getBytes(),
                                output,
                                source.getMaximumBytes(),
                                temporaryStorageAllowance,
                                resources);
                        break;
                    default:
                        throw new IllegalStateException(
                                "Unsupported source kind.");
                }
            }
            return snapshot;
        } catch (DocumentFailure failure) {
            resources.releaseTemporaryFile(snapshot);
            throw failure;
        } catch (IOException ioFailure) {
            resources.releaseTemporaryFile(snapshot);
            resources.rethrowResourceOrTerminalFailure(ioFailure);
            throw failure(
                    DocumentFailureCode.SOURCE_READ_FAILED,
                    "The source could not be opened as a PDF document.");
        } catch (RuntimeException callerFailure) {
            resources.releaseTemporaryFile(snapshot);
            resources.rethrowResourceOrTerminalFailure(callerFailure);
            throw callerFailure;
        }
    }

    static Path snapshotForWorker(
            DocumentSource source,
            WorkflowResourceContext resources) throws DocumentFailure {
        return snapshot(source, resources);
    }

    static Path snapshotForWorker(
            DocumentSource source,
            WorkflowResourceContext resources,
            long temporaryStorageAllowance) throws DocumentFailure {
        return snapshot(source, resources, temporaryStorageAllowance);
    }

    private static void copySourceContents(
            InputStream input,
            OutputStream output,
            long maximumBytes,
            long temporaryStorageAllowance,
            WorkflowResourceContext resources)
            throws IOException, DocumentFailure {
        byte[] buffer = new byte[8192];
        long total = 0L;
        while (true) {
            resources.checkpoint();
            int read = input.read(buffer);
            if (read == -1) {
                return;
            }
            if (read > 0) {
                try {
                    total = checkedTotal(total, read, maximumBytes);
                } catch (SourceLimitExceeded exhausted) {
                    throw failure(
                            DocumentFailureCode.SOURCE_LIMIT_EXCEEDED,
                            "The source exceeds its declared byte limit.");
                }
                requireTemporaryTransferWithinLimit(
                        total,
                        temporaryStorageAllowance,
                        resources);
                resources.consumeInputBytes(read);
                output.write(buffer, 0, read);
            }
        }
    }

    private static void copySourceContents(
            ReadableByteChannel channel,
            OutputStream output,
            long maximumBytes,
            long temporaryStorageAllowance,
            WorkflowResourceContext resources)
            throws IOException, DocumentFailure {
        ByteBuffer buffer = ByteBuffer.allocate(8192);
        long total = 0L;
        while (true) {
            resources.checkpoint();
            int read = channel.read(buffer);
            if (read == -1) {
                return;
            }
            if (read > 0) {
                try {
                    total = checkedTotal(total, read, maximumBytes);
                } catch (SourceLimitExceeded exhausted) {
                    throw failure(
                            DocumentFailureCode.SOURCE_LIMIT_EXCEEDED,
                            "The source exceeds its declared byte limit.");
                }
                requireTemporaryTransferWithinLimit(
                        total,
                        temporaryStorageAllowance,
                        resources);
                resources.consumeInputBytes(read);
                output.write(buffer.array(), 0, read);
                buffer.clear();
            }
        }
    }

    private static void copySourceBytes(
            byte[] bytes,
            OutputStream output,
            long maximumBytes,
            long temporaryStorageAllowance,
            WorkflowResourceContext resources)
            throws IOException, DocumentFailure {
        try {
            requireWithinLimit(bytes.length, maximumBytes);
        } catch (SourceLimitExceeded exhausted) {
            throw failure(
                    DocumentFailureCode.SOURCE_LIMIT_EXCEEDED,
                    "The source exceeds its declared byte limit.");
        }
        requireTemporaryTransferWithinLimit(
                bytes.length,
                temporaryStorageAllowance,
                resources);
        resources.consumeInputBytes(bytes.length);
        resources.writeBytesAsIOException(output, bytes);
    }

    private static void requireTemporaryTransferWithinLimit(
            long transferred,
            long temporaryStorageAllowance,
            WorkflowResourceContext resources) throws DocumentFailure {
        if (temporaryStorageAllowance < 0L
                || transferred > temporaryStorageAllowance) {
            throw resources.policyFailure(
                    DocumentFailureCode.TEMPORARY_STORAGE_LIMIT_EXCEEDED,
                    "The workflow temporary-storage limit was exceeded.");
        }
    }

    private static OpenedDocument openPrimaryDocument(
            DocumentSource source,
            String sourceName,
            boolean captureFingerprint,
            ExecutionContext<?> context)
            throws DocumentFailure {
        WorkflowResourceContext resources = context.resources;
        if (source.getKind() == DocumentSource.Kind.PATH) {
            InputStream ownedPathInput = null;
            try {
                Path path = source.getPath().toAbsolutePath().normalize();
                if (source.isWorkflowSnapshot()) {
                    long length = Files.size(path);
                    PdfVersion header = PdfBoxVersionPolicy.inspectHeader(path);
                    SourceFingerprint fingerprint = captureFingerprint
                            ? fingerprint(path, length, resources) : null;
                    PasswordCredential credential = context.resolveCredential(
                            sourceName,
                            source.getCredential());
                    return new OpenedDocument(
                            loadPathDocument(path, credential, resources),
                            length,
                            fingerprint,
                            header,
                            null,
                            credential);
                }
                ownedPathInput = Files.newInputStream(path);
                Path snapshot = snapshot(
                        ownedPathInput,
                        source.getMaximumBytes(),
                        resources);
                long length = Files.size(snapshot);
                PdfVersion header = PdfBoxVersionPolicy.inspectHeader(snapshot);
                SourceFingerprint fingerprint = captureFingerprint
                        ? fingerprint(snapshot, length, resources) : null;
                PasswordCredential credential = context.resolveCredential(
                        sourceName,
                        source.getCredential());
                return new OpenedDocument(
                        loadPathDocument(
                                snapshot,
                                credential,
                                resources),
                        length,
                        fingerprint,
                        header,
                        ownedPathInput,
                        credential);
            } catch (DocumentFailure failure) {
                closeQuietly(ownedPathInput);
                throw failure;
            } catch (IOException | RuntimeException failure) {
                closeQuietly(ownedPathInput);
                resources.rethrowResourceOrTerminalFailure(failure);
                throw failure(
                        DocumentFailureCode.SOURCE_READ_FAILED,
                        "The source could not be opened as a PDF document.");
            }
        }
        Path snapshot = snapshot(source, resources);
        try {
            long length = Files.size(snapshot);
            PdfVersion header = PdfBoxVersionPolicy.inspectHeader(snapshot);
            SourceFingerprint fingerprint = captureFingerprint
                    ? fingerprint(snapshot, length, resources) : null;
            PasswordCredential credential = context.resolveCredential(
                    sourceName,
                    source.getCredential());
            return new OpenedDocument(
                    loadPathDocument(
                            snapshot,
                            credential,
                            resources),
                    length,
                    fingerprint,
                    header,
                    null,
                    credential);
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw failure(
                    DocumentFailureCode.SOURCE_READ_FAILED,
                    "The source could not be opened as a PDF document.");
        }
    }

    private static Path snapshot(
            InputStream input,
            long maximumBytes,
            WorkflowResourceContext resources) throws DocumentFailure {
        Path snapshot = null;
        try {
            snapshot = resources.createTemporaryFile(
                    ".folio-pdf-source-",
                    ".pdf");
            try (OutputStream output = resources.openTemporaryOutput(snapshot)) {
                copySourceContents(
                        input,
                        output,
                        maximumBytes,
                        Long.MAX_VALUE,
                        resources);
            }
            return snapshot;
        } catch (DocumentFailure failure) {
            resources.releaseTemporaryFile(snapshot);
            throw failure;
        } catch (IOException failure) {
            resources.releaseTemporaryFile(snapshot);
            resources.rethrowResourceOrTerminalFailure(failure);
            throw PdfBoxWorkflowEngine.failure(
                    DocumentFailureCode.SOURCE_READ_FAILED,
                    "The source could not be opened as a PDF document.");
        }
    }

    private static PDDocument loadPathDocument(
            Path source,
            PasswordCredential credential,
            WorkflowResourceContext resources)
            throws DocumentFailure {
        WorkflowCredentialCharacters characters = credentialCharacters(
                credential, resources);
        try {
            return loadPathDocument(
                    source,
                    characters == null ? null : characters.get(),
                    resources);
        } finally {
            if (characters != null) {
                characters.close();
            }
        }
    }

    private static PDDocument loadPathDocument(
            Path source,
            char[] characters,
            WorkflowResourceContext resources) throws DocumentFailure {
        WorkflowResourceContext.MemoryReservation passwordMemory = null;
        try {
            Path path = source.toAbsolutePath().normalize();
            if (characters == null) {
                return Loader.loadPDF(
                        path.toFile(),
                        resources.streamCacheFactory());
            }
            passwordMemory = resources.reserveOwnedMemory(
                    2L * characters.length);
            resources.checkpoint();
            PDDocument loaded = Loader.loadPDF(
                    path.toFile(),
                    new String(characters),
                    resources.streamCacheFactory());
            try {
                resources.checkpoint();
                return loaded;
            } catch (DocumentFailure failure) {
                closeQuietly(loaded);
                throw failure;
            }
        } catch (InvalidPasswordException rejected) {
            throw credentialFailure(characters == null);
        } catch (IOException | RuntimeException backendFailure) {
            resources.rethrowResourceOrTerminalFailure(backendFailure);
            throw failure(
                    DocumentFailureCode.SOURCE_READ_FAILED,
                    "The source could not be opened as a PDF document.");
        } finally {
            if (passwordMemory != null) {
                passwordMemory.close();
            }
        }
    }

    private static WorkflowCredentialCharacters credentialCharacters(
            PasswordCredential credential,
            WorkflowResourceContext resources) throws DocumentFailure {
        return credential == null
                ? null
                : WorkflowCredentialCharacters.copyOf(credential, resources);
    }

    private static DocumentFailure credentialFailure(boolean missing) {
        return versionFailure(
                missing
                        ? DocumentFailureCode.CREDENTIAL_REQUIRED
                        : DocumentFailureCode.CREDENTIAL_REJECTED,
                missing
                        ? "The password-protected Source requires a credential."
                        : "The supplied Source credential was not accepted.");
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

    private static void validate(
            Path staged,
            PdfBoxPasswordSecurity.PreparedOutput outputSecurity,
            PdfVersion expectedVersion,
            SaveMode saveMode,
            PasswordSecurityInfo sourceSecurity,
            WorkflowResourceContext resources)
            throws DocumentFailure {
        resources.checkpoint();
        try {
            if (Files.size(staged) <= 0L) {
                throw failure(
                        DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                        "The staged document is empty.");
            }
        } catch (IOException | RuntimeException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw failure(
                    DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                    "The staged document could not be validated.");
        }

        PDDocument validationDocument = null;
        PdfBoxPasswordSecurity.PreparedPassword validationPassword = null;
        PdfBoxPasswordSecurity.PreparedPassword ownerPassword = null;
        try {
            validationPassword = outputSecurity.validationPassword(resources);
            validationDocument = Loader.loadPDF(
                    staged.toFile(),
                    validationPassword.get(),
                    resources.streamCacheFactory());
            if (validationDocument.getNumberOfPages() == 0) {
                throw failure(
                        DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                        "The staged document must contain at least one page.");
            }
            if (validationDocument.getNumberOfPages()
                    > resources.getPolicy().getMaximumPages()) {
                throw resources.policyFailure(
                        DocumentFailureCode.PAGE_LIMIT_EXCEEDED,
                        "The workflow page-count limit was exceeded.");
            }
            resources.requireObjectCount(
                    validationDocument.getDocument()
                            .getXrefTable().size());
            resources.checkpoint();
            PdfVersion header = PdfBoxVersionPolicy.inspectHeader(staged);
            PdfVersionInfo version = PdfBoxVersionPolicy.inspect(
                    validationDocument,
                    header);
            if (version.getEffectiveVersion() != expectedVersion
                    || (saveMode == SaveMode.REWRITE
                            && (header != expectedVersion
                                    || version.getCatalogVersion()
                                            .isPresent()))) {
                throw versionFailure(
                        DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                        "The staged PDF version does not match the output policy.");
            }
            PasswordSecurityInfo actualSecurity =
                    PdfBoxPasswordSecurity.inspect(
                            validationDocument, (char[]) null, resources);
            if (outputSecurity.preservesExistingSecurity()) {
                requireSameSecurity(sourceSecurity, actualSecurity);
            } else if (!outputSecurity.isPresent()
                    && actualSecurity.isPasswordProtected()) {
                throw versionFailure(
                        DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                        "The staged password-security state does not match the output policy.");
            }
            outputSecurity.validate(validationDocument, resources);
            validationDocument.close();
            validationDocument = null;
            validationPassword.close();
            validationPassword = null;
            if (outputSecurity.isPresent()) {
                ownerPassword = outputSecurity.ownerValidationPassword(
                        resources);
                validationDocument = Loader.loadPDF(
                        staged.toFile(),
                        ownerPassword.get(),
                        resources.streamCacheFactory());
                if (validationDocument.getNumberOfPages() == 0) {
                    throw failure(
                            DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                            "The staged document must contain at least one page.");
                }
                outputSecurity.validateOwner(validationDocument, resources);
                resources.checkpoint();
                validationDocument.close();
                validationDocument = null;
                ownerPassword.close();
                ownerPassword = null;
            }
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw failure(
                    DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                    "The staged document is not a parseable PDF document.");
        } finally {
            closeQuietly(validationDocument);
            if (validationPassword != null) {
                validationPassword.close();
            }
            if (ownerPassword != null) {
                ownerPassword.close();
            }
        }
    }

    private static void requireSameSecurity(
            PasswordSecurityInfo expected,
            PasswordSecurityInfo actual) throws DocumentFailure {
        if (expected.isPasswordProtected()
                        != actual.isPasswordProtected()
                || !expected.getAlgorithm().equals(actual.getAlgorithm())
                || expected.getSecurityHandlerRevision()
                        != actual.getSecurityHandlerRevision()
                || expected.getEncryptionScope()
                        != actual.getEncryptionScope()
                || !expected.getDeclaredUserPermissions().equals(
                        actual.getDeclaredUserPermissions())) {
            throw versionFailure(
                    DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                    "The staged password-security state does not preserve its Source.");
        }
    }

    private static void validateIncrementalRevision(
            Path staged,
            SourceFingerprint source,
            WorkflowResourceContext resources) throws DocumentFailure {
        if (source == null) {
            throw failure(
                    DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                    "The staged incremental revision could not be validated.");
        }
        try {
            if (Files.size(staged) <= source.length) {
                throw incrementalValidationFailure();
            }
            MessageDigest digest = sha256();
            long remaining = source.length;
            try (InputStream input = Files.newInputStream(staged)) {
                byte[] buffer = new byte[8192];
                while (remaining > 0L) {
                    resources.checkpoint();
                    int read = input.read(
                            buffer,
                            0,
                            (int) Math.min(buffer.length, remaining));
                    if (read < 0) {
                        throw incrementalValidationFailure();
                    }
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                        remaining -= read;
                    }
                }
            }
            if (!Arrays.equals(source.sha256, digest.digest())) {
                throw incrementalValidationFailure();
            }
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw incrementalValidationFailure();
        }
    }

    private static SourceFingerprint fingerprint(
            Path source,
            long length,
            WorkflowResourceContext resources)
            throws IOException, DocumentFailure {
        MessageDigest digest = sha256();
        try (InputStream input = Files.newInputStream(source)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                resources.checkpoint();
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return new SourceFingerprint(length, digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable.", unavailable);
        }
    }

    private static DocumentFailure incrementalValidationFailure() {
        return failure(
                DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                "The staged incremental revision does not preserve its Source prefix.");
    }

    private static void publishPath(
            Path staged,
            Path target,
            boolean retainTemporaryAccounting,
            WorkflowResourceContext resources)
            throws DocumentFailure {
        Path targetStage = null;
        try {
            targetStage = Files.createTempFile(
                    target.getParent(),
                    ".folio-pdf-",
                    ".pdf");
            resources.registerTemporaryFile(targetStage);
            try (InputStream input = Files.newInputStream(staged);
                    OutputStream output =
                            resources.openTemporaryOutput(targetStage)) {
                copyForPublication(input, output, resources, null);
            }
            resources.checkpoint();
            try {
                Files.move(
                        targetStage,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                resources.checkpoint();
                Files.move(
                        targetStage,
                        target,
                        StandardCopyOption.REPLACE_EXISTING);
            }
            if (retainTemporaryAccounting) {
                resources.transferTemporaryFile(targetStage, target);
            } else {
                resources.relinquishTemporaryFile(targetStage);
            }
            targetStage = null;
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw failure(
                    DocumentFailureCode.PUBLICATION_FAILED,
                    "The validated document could not be committed to its target.");
        } finally {
            resources.releaseTemporaryFile(targetStage);
        }
    }

    private static void publishStream(
            Path staged,
            OutputStream output,
            WorkflowResourceContext resources,
            PublicationTargetAdapter target)
            throws DocumentFailure {
        try (InputStream input = Files.newInputStream(staged)) {
            copyForPublication(input, output, resources, target);
            output.flush();
            resources.checkpoint();
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (IOException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw failure(
                    DocumentFailureCode.PUBLICATION_FAILED,
                    "The validated document could not be written to its stream target.");
        }
    }

    private static void copyForPublication(
            InputStream input,
            OutputStream output,
            WorkflowResourceContext resources,
            PublicationTargetAdapter target)
            throws IOException, DocumentFailure {
        byte[] buffer = new byte[8192];
        int read;
        while (true) {
            resources.checkpoint();
            read = input.read(buffer);
            if (read == -1) {
                return;
            }
            if (read > 0) {
                if (target != null) {
                    target.markStreamWriteAttempted();
                }
                output.write(buffer, 0, read);
                resources.checkpoint();
            }
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

    private static PDDocument createDocument(
            WorkflowResourceContext resources) throws DocumentFailure {
        try {
            resources.checkpoint();
            PDDocument document = new PDDocument(
                    resources.streamCacheFactory());
            PdfBoxVersionPolicy.setOutputVersion(
                    document,
                    PdfVersion.PDF_1_7);
            return document;
        } catch (RuntimeException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
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

    private static void closeQuietly(InputStream input) {
        if (input == null) {
            return;
        }
        try {
            input.close();
        } catch (IOException | RuntimeException ignored) {
            // A primary result or failure already owns the public contract.
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

    private static void closePreparedSources(
            Map<String, PreparedNamedSource> sources) {
        if (sources == null) {
            return;
        }
        for (PreparedNamedSource source : sources.values()) {
            source.close();
        }
    }

    private static void releaseTemporaryFiles(
            WorkflowResourceContext resources,
            List<Path> files) {
        for (Path file : files) {
            resources.releaseTemporaryFile(file);
        }
    }

    static final class ExecutionContext<R> {
        private final WorkflowRequest request;
        private final DocumentWork<R> work;
        private final List<ProviderSelection> providerSelections;
        private final List<FontSource> referenceFonts;
        private final WorkflowResourceContext resources;
        private final InputResolver inputResolver;

        ExecutionContext(
                WorkflowRequest request,
                DocumentWork<R> work,
                List<ProviderSelection> providerSelections,
                List<FontSource> referenceFonts,
                WorkflowResourceContext resources) {
            this(
                    request,
                    work,
                    providerSelections,
                    referenceFonts,
                    resources,
                    null);
        }

        ExecutionContext(
                WorkflowRequest request,
                DocumentWork<R> work,
                List<ProviderSelection> providerSelections,
                List<FontSource> referenceFonts,
                WorkflowResourceContext resources,
                InputResolver inputResolver) {
            this.request = Objects.requireNonNull(request, "request");
            this.work = Objects.requireNonNull(work, "work");
            this.providerSelections = Collections.unmodifiableList(
                    new ArrayList<ProviderSelection>(providerSelections));
            this.referenceFonts = Collections.unmodifiableList(
                    new ArrayList<FontSource>(referenceFonts));
            this.resources = Objects.requireNonNull(resources, "resources");
            this.inputResolver = inputResolver;
        }

        private DocumentSource resolveSource(
                String name,
                DocumentSource source) throws DocumentFailure {
            return inputResolver == null
                    ? source
                    : inputResolver.resolveSource(name, source, resources);
        }

        private PasswordCredential resolveCredential(
                String sourceName,
                PasswordCredential credential) throws DocumentFailure {
            return inputResolver == null
                    ? credential
                    : inputResolver.resolveSourceCredential(
                            sourceName,
                            credential,
                            resources);
        }

        private PasswordSecurityPolicy resolveOutputSecurity(
                PasswordSecurityPolicy security) throws DocumentFailure {
            return inputResolver == null || security == null
                    ? security
                    : inputResolver.resolveOutputSecurity(
                            security,
                            resources);
        }
    }

    interface InputResolver {

        void activate(WorkflowResourceContext resources)
                throws DocumentFailure;

        DocumentSource resolveSource(
                String name,
                DocumentSource source,
                WorkflowResourceContext resources) throws DocumentFailure;

        PasswordCredential resolveSourceCredential(
                String sourceName,
                PasswordCredential credential,
                WorkflowResourceContext resources) throws DocumentFailure;

        PasswordSecurityPolicy resolveOutputSecurity(
                PasswordSecurityPolicy security,
                WorkflowResourceContext resources) throws DocumentFailure;

        void close();
    }

    static final class WorkerPublicationPlan {

        private final WorkflowRequest request;
        private final WorkflowResourceContext resources;
        private final List<PublicationTargetAdapter> targets;

        private WorkerPublicationPlan(
                WorkflowRequest request,
                WorkflowResourceContext resources,
                List<PublicationTargetAdapter> targets) {
            this.request = request;
            this.resources = resources;
            this.targets = targets;
        }

        List<PublicationReceipt> publish(List<Path> stagedDocuments)
                throws DocumentFailure {
            if (stagedDocuments.size() != targets.size()
                    && !(stagedDocuments.size() == 1 && !targets.isEmpty())) {
                throw failure(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "The Worker product declaration is invalid.");
            }
            return publishAll(stagedDocuments, targets, request, resources);
        }
    }

    private static final class PublicationTargetAdapter {

        private final String targetName;
        private final Path pathTarget;
        private final Path normalizedPathTarget;
        private final OutputStream streamTarget;
        private final boolean retainTemporaryAccounting;
        private boolean streamWriteAttempted;

        private PublicationTargetAdapter(
                String targetName,
                Path pathTarget,
                Path normalizedPathTarget,
                OutputStream streamTarget,
                boolean retainTemporaryAccounting) {
            this.targetName = targetName;
            this.pathTarget = pathTarget;
            this.normalizedPathTarget = normalizedPathTarget;
            this.streamTarget = streamTarget;
            this.retainTemporaryAccounting = retainTemporaryAccounting;
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
                            null,
                            target.retainsTemporaryAccounting());
                case STREAM:
                    return new PublicationTargetAdapter(
                            entry.getKey(),
                            null,
                            null,
                            target.getStream(),
                            false);
                default:
                    throw new IllegalStateException(
                            "Unsupported publication target kind.");
            }
        }

        private void publish(
                Path staged,
                WorkflowResourceContext resources) throws DocumentFailure {
            if (isStream()) {
                publishStream(staged, streamTarget, resources, this);
            } else {
                publishPath(
                        staged,
                        normalizedPathTarget,
                        retainTemporaryAccounting,
                        resources);
            }
        }

        private void markStreamWriteAttempted() {
            streamWriteAttempted = true;
        }

        private boolean isStreamWriteAttempted() {
            return streamWriteAttempted;
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

    private static final class OpenedDocument {

        private final PDDocument document;
        private final long sourceLength;
        private final SourceFingerprint fingerprint;
        private final PdfVersion declaredHeaderVersion;
        private final InputStream ownedPathInput;
        private final PasswordCredential credential;

        private OpenedDocument(
                PDDocument document,
                long sourceLength,
                SourceFingerprint fingerprint,
                PdfVersion declaredHeaderVersion,
                InputStream ownedPathInput,
                PasswordCredential credential) {
            this.document = document;
            this.sourceLength = sourceLength;
            this.fingerprint = fingerprint;
            this.declaredHeaderVersion = declaredHeaderVersion;
            this.ownedPathInput = ownedPathInput;
            this.credential = credential;
        }
    }

    static final class PreparedNamedSource implements AutoCloseable {

        private final Path snapshot;
        private final PdfVersionInfo versionInfo;
        private final PasswordSecurityInfo securityInfo;
        private final WorkflowResourceContext resources;
        private WorkflowCredentialCharacters credentialCharacters;

        private PreparedNamedSource(
                Path snapshot,
                PdfVersionInfo versionInfo,
                PasswordSecurityInfo securityInfo,
                WorkflowCredentialCharacters credentialCharacters,
                WorkflowResourceContext resources) {
            this.snapshot = snapshot;
            this.versionInfo = versionInfo;
            this.securityInfo = securityInfo;
            this.credentialCharacters = credentialCharacters;
            this.resources = resources;
        }

        private static PreparedNamedSource prepare(
                String sourceName,
                DocumentSource source,
                ExecutionContext<?> context)
                throws DocumentFailure {
            WorkflowResourceContext resources = context.resources;
            Path snapshot = null;
            WorkflowCredentialCharacters credentialCharacters = null;
            PDDocument document = null;
            boolean retained = false;
            try {
                snapshot = source.isWorkflowSnapshot()
                        ? source.getPath().toAbsolutePath().normalize()
                        : PdfBoxWorkflowEngine.snapshot(source, resources);
                PdfVersion header = PdfBoxVersionPolicy.inspectHeader(snapshot);
                credentialCharacters = credentialCharacters(
                        context.resolveCredential(
                                sourceName,
                                source.getCredential()),
                        resources);
                document = loadPathDocument(
                        snapshot,
                        credentialCharacters == null
                                ? null : credentialCharacters.get(),
                        resources);
                resources.audit(document);
                PdfVersionInfo versionInfo = PdfBoxVersionPolicy.inspect(
                        document,
                        header);
                PasswordSecurityInfo securityInfo =
                        PdfBoxPasswordSecurity.inspect(
                                document,
                                credentialCharacters == null
                                        ? null : credentialCharacters.get(),
                                resources);
                PdfBoxPasswordSecurity.requireCompatibleInput(
                        document,
                        versionInfo,
                        securityInfo);
                PreparedNamedSource prepared = new PreparedNamedSource(
                        snapshot,
                        versionInfo,
                        securityInfo,
                        credentialCharacters,
                        resources);
                retained = true;
                return prepared;
            } finally {
                closeQuietly(document);
                if (!retained) {
                    if (credentialCharacters != null) {
                        credentialCharacters.close();
                    }
                    resources.releaseTemporaryFile(snapshot);
                }
            }
        }

        PDDocument open() throws DocumentFailure {
            resources.checkpoint();
            return loadPathDocument(
                    snapshot,
                    credentialCharacters == null
                            ? null : credentialCharacters.get(),
                    resources);
        }

        void requireMergeAllowed(
                PdfVersion publicationVersion,
                PasswordEncryptionAlgorithm publicationAlgorithm,
                PasswordEncryptionScope publicationScope)
                throws DocumentFailure {
            PdfBoxPermissionPolicy.requireMergeSource(securityInfo);
            requirePublicationCompatible(
                    publicationVersion,
                    publicationAlgorithm,
                    publicationScope);
        }

        private void requirePublicationCompatible(
                PdfVersion publicationVersion,
                PasswordEncryptionAlgorithm publicationAlgorithm,
                PasswordEncryptionScope publicationScope)
                throws DocumentFailure {
            if (publicationVersion == null) {
                return;
            }
            if (versionInfo.getEffectiveVersion().ordinal()
                    > publicationVersion.ordinal()) {
                throw versionFailure(
                        DocumentFailureCode.PDF_VERSION_UNSUPPORTED,
                        "A merged Source cannot be published with an older PDF version.");
            }
            if (!securityInfo.isPasswordProtected()) {
                return;
            }
            if (publicationAlgorithm == null) {
                throw versionFailure(
                        DocumentFailureCode.PASSWORD_SECURITY_POLICY_REQUIRED,
                        "A protected merged Source requires protected publication.");
            }
            PasswordEncryptionAlgorithm sourceAlgorithm =
                    securityInfo.getAlgorithm().get();
            if (securityStrength(publicationAlgorithm)
                            < securityStrength(sourceAlgorithm)
                    || !scopeProtects(
                            publicationScope,
                            securityInfo.getEncryptionScope())) {
                throw versionFailure(
                        DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                        "A merged Source cannot be published with weaker password security.");
            }
        }

        private static int securityStrength(
                PasswordEncryptionAlgorithm algorithm) {
            switch (algorithm) {
                case AES_256:
                    return 4;
                case AES_128:
                    return 3;
                case RC4_128:
                    return 2;
                case RC4_40:
                    return 1;
                default:
                    throw new IllegalStateException(
                            "Unsupported password-encryption algorithm.");
            }
        }

        private static boolean scopeProtects(
                PasswordEncryptionScope publicationScope,
                PasswordEncryptionScope sourceScope) {
            return publicationScope == PasswordEncryptionScope.ALL_CONTENT
                    || publicationScope == sourceScope;
        }

        @Override
        public void close() {
            if (credentialCharacters != null) {
                credentialCharacters.close();
            }
            credentialCharacters = null;
            resources.releaseTemporaryFile(snapshot);
        }
    }

    private static final class SourceFingerprint {

        private final long length;
        private final byte[] sha256;

        private SourceFingerprint(long length, byte[] sha256) {
            this.length = length;
            this.sha256 = sha256;
        }
    }

}
