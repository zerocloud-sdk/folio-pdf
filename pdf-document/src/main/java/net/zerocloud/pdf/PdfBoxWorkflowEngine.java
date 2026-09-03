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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        long sourceLength = -1L;
        SourceFingerprint sourceFingerprint = null;
        DocumentSource primarySource = null;
        PdfVersion declaredHeaderVersion;
        if (request.getSources().isEmpty()) {
            document = createDocument();
            declaredHeaderVersion = PdfVersion.PDF_1_7;
        } else {
            DocumentSource source = request.getSources().get(
                    request.getPrimarySourceName());
            if (source == null) {
                throw failure(
                        DocumentFailureCode.INVALID_REQUEST,
                        "The workflow request must select a declared primary source.");
            }
            OpenedDocument opened = openPrimaryDocument(source, true);
            primarySource = source;
            document = opened.document;
            sourceLength = opened.sourceLength;
            sourceFingerprint = opened.fingerprint;
            declaredHeaderVersion = opened.declaredHeaderVersion;
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
                    primarySource == null
                            ? null : primarySource.getCredential());
            PdfBoxPasswordSecurity.requireCompatibleInput(
                    document,
                    versionInfo,
                    securityInfo);
            if (securityInfo.isPasswordProtected()
                    && primarySource.getCredential() == null) {
                throw credentialFailure(true);
            }
            outputVersion = outputVersion(request, versionInfo);
            signaturePolicy = PdfBoxSignaturePolicy.inspect(document, sourceLength);
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
                                        primarySource.getCredential());
            } else {
                outputSecurity = PdfBoxPasswordSecurity.prepare(
                        requestedSecurity,
                        outputVersion,
                        request.getSaveMode(),
                        request.getLegacySecurityMode());
            }
            outputSecurity.preflight(document);
        } catch (DocumentFailure policyFailure) {
            if (outputSecurity != null) {
                outputSecurity.close();
            }
            closeQuietly(document);
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
                    publicationScope);
        } catch (DocumentFailure preparationFailure) {
            outputSecurity.close();
            closeQuietly(document);
            throw preparationFailure;
        } catch (RuntimeException callerFailure) {
            outputSecurity.close();
            closeQuietly(document);
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
                request,
                publicationTargets,
                work,
                clock,
                providerSelections,
                sourceFingerprint);
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
            WorkflowRequest request,
            List<PublicationTargetAdapter> publicationTargets,
            DocumentWork<R> work,
            Clock clock,
            List<ProviderSelection> providerSelections,
            SourceFingerprint sourceFingerprint) throws DocumentFailure {
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
                    publicationScope);
            requireExecutionAllowed(request, clock);
            if (signaturePolicy.hasExistingSignatures()
                    && request.getSaveMode() == SaveMode.REWRITE
                    && !publicationTargets.isEmpty()) {
                throw incrementalFailure(
                        DocumentFailureCode.SIGNED_REWRITE_REJECTED,
                        "A Source with an Existing Signature cannot be published with REWRITE.");
            }
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
                emit(request, WorkflowProgressPhase.COMPLETED);
                return outcome(
                        result,
                        outcomeCapabilityId(request, session),
                        request,
                        Collections.<PublicationReceipt>emptyList(),
                        providerSelections);
            }

            try {
                if (splitDocuments == null) {
                    if (outputVersion != null) {
                        PdfBoxVersionPolicy.setOutputVersion(
                                document,
                                outputVersion);
                    }
                    outputSecurity.apply(document);
                    Path staged = Files.createTempFile(".folio-pdf-", ".pdf");
                    stagedDocuments.add(staged);
                    save(document, staged, request.getSaveMode());
                    if (request.getSaveMode() == SaveMode.INCREMENTAL) {
                        validateIncrementalRevision(staged, sourceFingerprint);
                    }
                } else {
                    for (PublicationTargetAdapter target : publicationTargets) {
                        Path staged = Files.createTempFile(
                                ".folio-pdf-",
                                ".pdf");
                        stagedDocuments.add(staged);
                        if (outputVersion != null) {
                            PdfBoxVersionPolicy.setOutputVersion(
                                    splitDocuments.get(target.getTargetName()),
                                    outputVersion);
                        }
                        outputSecurity.apply(
                                splitDocuments.get(target.getTargetName()));
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
                validate(
                        staged,
                        outputSecurity,
                        publicationVersion,
                        request.getSaveMode(),
                        securityInfo);
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
                    outcomeCapabilityId(request, session),
                    request,
                    receipts,
                    providerSelections);
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
            deleteQuietly(stagedDocuments);
        }
    }

    private static <R> WorkflowOutcome<R> outcome(
            R result,
            String capabilityId,
            WorkflowRequest request,
            List<PublicationReceipt> receipts,
            List<ProviderSelection> providerSelections) {
        String reportedCapabilityId = capabilityId;
        if (request.getSaveMode() == SaveMode.INCREMENTAL
                && !isCanvasCapability(capabilityId)) {
            reportedCapabilityId = INCREMENTAL_CAPABILITY_ID;
        }
        return new WorkflowOutcome<R>(
                result,
                reportedCapabilityId,
                WorkflowExecutionProfile.IN_PROCESS,
                request.getSaveMode(),
                Collections.<String>emptyList(),
                receipts,
                providerSelections);
    }

    private static String outcomeCapabilityId(
            WorkflowRequest request,
            PdfBoxDocumentSession session) {
        if (isCanvasCapability(session.getOutcomeCapabilityId())) {
            return session.getOutcomeCapabilityId();
        }
        return !request.getPublicationTargets().isEmpty()
                        && request.getOutputPolicy() != null
                ? VERSION_SECURITY_CAPABILITY_ID
                : session.getOutcomeCapabilityId();
    }

    private static boolean isCanvasCapability(String capabilityId) {
        return PdfBoxCanvasOperations.CAPABILITY_ID.equals(capabilityId)
                || PdfBoxCanvasResourceOperations.CAPABILITY_ID.equals(
                        capabilityId);
    }

    private static void save(
            PDDocument document,
            Path staged,
            SaveMode saveMode) throws IOException {
        if (saveMode == SaveMode.INCREMENTAL) {
            try (OutputStream output = Files.newOutputStream(staged)) {
                document.saveIncremental(output);
            }
        } else {
            document.save(staged.toFile());
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

    private static Map<String, PreparedNamedSource> prepareNamedSources(
            WorkflowRequest request,
            PdfVersion publicationVersion,
            PasswordEncryptionAlgorithm publicationAlgorithm,
            PasswordEncryptionScope publicationScope) throws DocumentFailure {
        Map<String, PreparedNamedSource> prepared =
                new LinkedHashMap<String, PreparedNamedSource>();
        try {
            for (Map.Entry<String, DocumentSource> entry
                    : request.getSources().entrySet()) {
                if (!entry.getKey().equals(request.getPrimarySourceName())) {
                    PreparedNamedSource source =
                            PreparedNamedSource.prepare(entry.getValue());
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
            throw callerFailure;
        }
    }

    private static Path snapshot(DocumentSource source)
            throws DocumentFailure {
        Path snapshot = null;
        try {
            snapshot = Files.createTempFile(
                    ".folio-pdf-source-",
                    ".pdf");
            switch (source.getKind()) {
                case PATH:
                    copyPathContents(
                            source.getPath().toAbsolutePath().normalize(),
                            snapshot);
                    break;
                case STREAM:
                    Files.write(
                            snapshot,
                            readCallerStream(
                                    source.getStream(),
                                    source.getMaximumBytes()));
                    break;
                case CHANNEL:
                    Files.write(
                            snapshot,
                            readCallerChannel(
                                    source.getChannel(),
                                    source.getMaximumBytes()));
                    break;
                case BYTES:
                    Files.write(
                            snapshot,
                            requireBoundedBytes(
                                    source.getBytes(),
                                    source.getMaximumBytes()));
                    break;
                default:
                    throw new IllegalStateException(
                            "Unsupported source kind.");
            }
            return snapshot;
        } catch (DocumentFailure failure) {
            deleteQuietly(snapshot);
            throw failure;
        } catch (IOException ioFailure) {
            deleteQuietly(snapshot);
            throw failure(
                    DocumentFailureCode.SOURCE_READ_FAILED,
                    "The source could not be opened as a PDF document.");
        } catch (RuntimeException callerFailure) {
            deleteQuietly(snapshot);
            throw callerFailure;
        }
    }

    private static void copyPathContents(Path source, Path snapshot)
            throws IOException {
        try (OutputStream output = Files.newOutputStream(snapshot)) {
            Files.copy(source, output);
        }
    }

    private static OpenedDocument openPrimaryDocument(
            DocumentSource source,
            boolean captureFingerprint)
            throws DocumentFailure {
        switch (source.getKind()) {
            case PATH:
                try {
                    Path path = source.getPath().toAbsolutePath().normalize();
                    long length = Files.size(path);
                    PdfVersion header = PdfBoxVersionPolicy.inspectHeader(path);
                    SourceFingerprint fingerprint = captureFingerprint
                            ? fingerprint(path, length) : null;
                    return new OpenedDocument(
                            loadPathDocument(path, source.getCredential()),
                            length,
                            fingerprint,
                            header);
                } catch (IOException | RuntimeException failure) {
                    throw failure(
                            DocumentFailureCode.SOURCE_READ_FAILED,
                            "The source could not be opened as a PDF document.");
                }
            case STREAM: {
                byte[] bytes = readCallerStream(
                        source.getStream(), source.getMaximumBytes());
                PdfVersion header = PdfBoxVersionPolicy.inspectHeader(bytes);
                return new OpenedDocument(
                        loadByteDocument(bytes, source.getCredential()),
                        bytes.length,
                        captureFingerprint ? fingerprint(bytes) : null,
                        header);
            }
            case CHANNEL: {
                byte[] bytes = readCallerChannel(
                        source.getChannel(), source.getMaximumBytes());
                PdfVersion header = PdfBoxVersionPolicy.inspectHeader(bytes);
                return new OpenedDocument(
                        loadByteDocument(bytes, source.getCredential()),
                        bytes.length,
                        captureFingerprint ? fingerprint(bytes) : null,
                        header);
            }
            case BYTES: {
                byte[] bytes = requireBoundedBytes(
                        source.getBytes(), source.getMaximumBytes());
                PdfVersion header = PdfBoxVersionPolicy.inspectHeader(bytes);
                return new OpenedDocument(
                        loadByteDocument(bytes, source.getCredential()),
                        bytes.length,
                        captureFingerprint ? fingerprint(bytes) : null,
                        header);
            }
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

    private static PDDocument loadPathDocument(
            Path source,
            PasswordCredential credential)
            throws DocumentFailure {
        char[] characters = credentialCharacters(credential);
        try {
            return loadPathDocument(source, characters);
        } finally {
            clear(characters);
        }
    }

    private static PDDocument loadPathDocument(
            Path source,
            char[] characters) throws DocumentFailure {
        try {
            Path path = source.toAbsolutePath().normalize();
            return characters == null
                    ? Loader.loadPDF(path.toFile())
                    : Loader.loadPDF(path.toFile(), new String(characters));
        } catch (InvalidPasswordException rejected) {
            throw credentialFailure(characters == null);
        } catch (IOException | RuntimeException backendFailure) {
            throw failure(
                    DocumentFailureCode.SOURCE_READ_FAILED,
                    "The source could not be opened as a PDF document.");
        }
    }

    private static PDDocument loadByteDocument(
            byte[] bytes,
            PasswordCredential credential)
            throws DocumentFailure {
        char[] characters = credentialCharacters(credential);
        try {
            return characters == null
                    ? Loader.loadPDF(bytes)
                    : Loader.loadPDF(bytes, new String(characters));
        } catch (InvalidPasswordException rejected) {
            throw credentialFailure(credential == null);
        } catch (IOException | RuntimeException backendFailure) {
            throw failure(
                    DocumentFailureCode.SOURCE_READ_FAILED,
                    "The source could not be opened as a PDF document.");
        } finally {
            clear(characters);
        }
    }

    private static char[] credentialCharacters(PasswordCredential credential)
            throws DocumentFailure {
        if (credential == null) {
            return null;
        }
        try {
            return credential.copyForExecution();
        } catch (IllegalStateException destroyed) {
            throw versionFailure(
                    DocumentFailureCode.CREDENTIAL_DESTROYED,
                    "A password credential was destroyed before execution.");
        }
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

    private static void clear(char[] characters) {
        if (characters != null) {
            Arrays.fill(characters, '\0');
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

    private static void validate(
            Path staged,
            PdfBoxPasswordSecurity.PreparedOutput outputSecurity,
            PdfVersion expectedVersion,
            SaveMode saveMode,
            PasswordSecurityInfo sourceSecurity)
            throws DocumentFailure {
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
            validationDocument = Loader.loadPDF(
                    staged.toFile(),
                    outputSecurity.validationPassword());
            if (validationDocument.getNumberOfPages() == 0) {
                throw failure(
                        DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                        "The staged document must contain at least one page.");
            }
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
                    PdfBoxPasswordSecurity.inspect(validationDocument);
            if (outputSecurity.preservesExistingSecurity()) {
                requireSameSecurity(sourceSecurity, actualSecurity);
            } else if (!outputSecurity.isPresent()
                    && actualSecurity.isPasswordProtected()) {
                throw versionFailure(
                        DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                        "The staged password-security state does not match the output policy.");
            }
            outputSecurity.validate(validationDocument);
            validationDocument.close();
            validationDocument = null;
            if (outputSecurity.isPresent()) {
                validationDocument = Loader.loadPDF(
                        staged.toFile(),
                        outputSecurity.ownerValidationPassword());
                if (validationDocument.getNumberOfPages() == 0) {
                    throw failure(
                            DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                            "The staged document must contain at least one page.");
                }
                outputSecurity.validateOwner(validationDocument);
                validationDocument.close();
                validationDocument = null;
            }
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw failure(
                    DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                    "The staged document is not a parseable PDF document.");
        } finally {
            closeQuietly(validationDocument);
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
            SourceFingerprint source) throws DocumentFailure {
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
            throw incrementalValidationFailure();
        }
    }

    private static SourceFingerprint fingerprint(Path source, long length)
            throws IOException {
        MessageDigest digest = sha256();
        try (InputStream input = Files.newInputStream(source)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return new SourceFingerprint(length, digest.digest());
    }

    private static SourceFingerprint fingerprint(byte[] source) {
        return new SourceFingerprint(source.length, sha256().digest(source));
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
            PDDocument document = new PDDocument();
            PdfBoxVersionPolicy.setOutputVersion(
                    document,
                    PdfVersion.PDF_1_7);
            return document;
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

    private static void closePreparedSources(
            Map<String, PreparedNamedSource> sources) {
        if (sources == null) {
            return;
        }
        for (PreparedNamedSource source : sources.values()) {
            source.close();
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

    private static final class OpenedDocument {

        private final PDDocument document;
        private final long sourceLength;
        private final SourceFingerprint fingerprint;
        private final PdfVersion declaredHeaderVersion;

        private OpenedDocument(
                PDDocument document,
                long sourceLength,
                SourceFingerprint fingerprint,
                PdfVersion declaredHeaderVersion) {
            this.document = document;
            this.sourceLength = sourceLength;
            this.fingerprint = fingerprint;
            this.declaredHeaderVersion = declaredHeaderVersion;
        }
    }

    static final class PreparedNamedSource implements AutoCloseable {

        private final Path snapshot;
        private final PdfVersionInfo versionInfo;
        private final PasswordSecurityInfo securityInfo;
        private char[] credentialCharacters;

        private PreparedNamedSource(
                Path snapshot,
                PdfVersionInfo versionInfo,
                PasswordSecurityInfo securityInfo,
                char[] credentialCharacters) {
            this.snapshot = snapshot;
            this.versionInfo = versionInfo;
            this.securityInfo = securityInfo;
            this.credentialCharacters = credentialCharacters;
        }

        private static PreparedNamedSource prepare(DocumentSource source)
                throws DocumentFailure {
            Path snapshot = null;
            char[] credentialCharacters = null;
            PDDocument document = null;
            boolean retained = false;
            try {
                snapshot = PdfBoxWorkflowEngine.snapshot(source);
                PdfVersion header = PdfBoxVersionPolicy.inspectHeader(snapshot);
                credentialCharacters = credentialCharacters(
                        source.getCredential());
                document = loadPathDocument(snapshot, credentialCharacters);
                PdfVersionInfo versionInfo = PdfBoxVersionPolicy.inspect(
                        document,
                        header);
                PasswordSecurityInfo securityInfo =
                        PdfBoxPasswordSecurity.inspect(
                                document,
                                credentialCharacters);
                PdfBoxPasswordSecurity.requireCompatibleInput(
                        document,
                        versionInfo,
                        securityInfo);
                PreparedNamedSource prepared = new PreparedNamedSource(
                        snapshot,
                        versionInfo,
                        securityInfo,
                        credentialCharacters);
                retained = true;
                return prepared;
            } finally {
                closeQuietly(document);
                if (!retained) {
                    clear(credentialCharacters);
                    deleteQuietly(snapshot);
                }
            }
        }

        PDDocument open() throws DocumentFailure {
            return loadPathDocument(snapshot, credentialCharacters);
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
            clear(credentialCharacters);
            credentialCharacters = null;
            deleteQuietly(snapshot);
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
