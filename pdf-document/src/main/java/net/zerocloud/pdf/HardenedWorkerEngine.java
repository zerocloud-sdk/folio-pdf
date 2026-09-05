package net.zerocloud.pdf;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import net.zerocloud.pdf.command.UpdateAnnotations;
import net.zerocloud.pdf.composition.command.DrawCanvas;
import net.zerocloud.pdf.composition.command.DrawPositionedUnicodeText;
import net.zerocloud.pdf.provider.ProviderSelection;
import org.apache.commons.logging.Log;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.pdmodel.PDDocument;

/** Parent-side controller for one authenticated local Worker transaction. */
final class HardenedWorkerEngine {

    static final String CAPABILITY_ID = "document.hardened-worker";

    private static final long RESPONSE_POLL_MILLIS = 25L;
    private static final int WORKER_OPEN_FILE_LIMIT = 64;
    private static final int MAXIMUM_CLASS_INVENTORY_BYTES = 64 * 1024;
    private static final String DOCUMENT_CLASS_INVENTORY =
            "META-INF/folio-pdf/document-worker-classes";
    private static final String DOCUMENT_CLASS_INVENTORY_SHA256 =
            "6755b30eae00e6c0c4ee773568ef85540aa2ba96b342499f98f5040553ed6d27";
    private static final String PROVIDER_CLASS_INVENTORY =
            "META-INF/folio-pdf/provider-contract-worker-classes";
    private static final String PROVIDER_CLASS_INVENTORY_SHA256 =
            "c7a7bb193dcfa656ba13af311ce2d7654a5aaac962b8804481a77d14013a25b6";
    private static final String PDFBOX_SHA256 =
            "97647cfbde61ebcfc06b4cf8c9b0ffcaaee073396eceb4a7f6836a9b9128903c";
    private static final String PDFBOX_IO_SHA256 =
            "36a0e04001010b4c764857817412b96339930b19755e728959805cc0352061b2";
    private static final String FONTBOX_SHA256 =
            "a1915c24e3edbe0ecec93896dfbf6d41427810b663ade97bd4e8bae86ec3fdab";
    private static final String COMMONS_LOGGING_SHA256 =
            "d175dbd751dd782a63bde28c7a039520e971f25e84b79c19b8435edc3603e0dc";
    private static final String TIFF_SHA256 =
            "68aa1b4a176d1242b9e49334df188ebfbb7c9201f6071dfe42500d63486224b6";
    private static final String IMAGEIO_CORE_SHA256 =
            "a1b832b5090bd4677696f999b5ccb8954e987eb9674632a6286a6de2bb1c3c78";
    private static final String IMAGEIO_METADATA_SHA256 =
            "03768fc012bd2573236da803099aba6961dfb29c190103f9790fc49ac27f84c1";
    private static final String COMMON_LANG_SHA256 =
            "8d4529d6f56a010bc7e130ebfcdaf14bc11586e9d9ae66f6dca66f91da7eafef";
    private static final String COMMON_IO_SHA256 =
            "ae01308bd48c68e76f6a1f76880cf7f4a3a004aa83d78e5448de358a4d957e8f";
    private static final String COMMON_IMAGE_SHA256 =
            "9edb1afd32278d20ad660869bfa5b0a27cf9b3553b6eb3f8fc51a2fc13109b66";

    private HardenedWorkerEngine() {
    }

    static IsolationProbe probeIsolation(DocumentSession session)
            throws DocumentFailure {
        return probeIsolation(session, null);
    }

    static IsolationProbe probeIsolation(
            DocumentSession session,
            String siblingRootName) throws DocumentFailure {
        if (!(session instanceof ProxyDocumentSession)) {
            throw new IllegalArgumentException(
                    "Isolation probes require a Hardened Worker Session.");
        }
        return ((ProxyDocumentSession) session).probeIsolation(
                siblingRootName);
    }

    static void probeOwnedMemory(
            DocumentSession session,
            long amount) throws DocumentFailure {
        if (!(session instanceof ProxyDocumentSession)) {
            throw new IllegalArgumentException(
                    "Memory probes require a Hardened Worker Session.");
        }
        ((ProxyDocumentSession) session).probeIsolation(null, amount);
    }

    static long probeOwnedMemoryBoundary(
            DocumentSession session,
            boolean firstExcess) throws DocumentFailure {
        if (!(session instanceof ProxyDocumentSession)) {
            throw new IllegalArgumentException(
                    "Memory probes require a Hardened Worker Session.");
        }
        return ((ProxyDocumentSession) session).probeOwnedMemoryBoundary(
                firstExcess);
    }

    static void terminateWorkerForTest(DocumentSession session) {
        if (!(session instanceof ProxyDocumentSession)) {
            throw new IllegalArgumentException(
                    "Worker termination probes require a Hardened Worker Session.");
        }
        ((ProxyDocumentSession) session).terminateWorkerForTest();
    }

    static Void requestMalformedResponseForTest(DocumentSession session)
            throws DocumentFailure {
        if (!(session instanceof ProxyDocumentSession)) {
            throw new IllegalArgumentException(
                    "Protocol fault probes require a Hardened Worker Session.");
        }
        ((ProxyDocumentSession) session).requestMalformedResponseForTest();
        return null;
    }

    static <R> WorkflowOutcome<R> execute(
            WorkflowRequest request,
            DocumentWork<R> work,
            List<ProviderSelection> providerSelections,
            WorkflowEnvironment environment,
            WorkflowResourceContext resources) throws DocumentFailure {
        if (request.getSaveMode() == SaveMode.INCREMENTAL
                && request.getSources().isEmpty()) {
            throw new DocumentFailure(
                    DocumentFailureCode.INCREMENTAL_SOURCE_REQUIRED,
                    PdfBoxWorkflowEngine.INCREMENTAL_CAPABILITY_ID,
                    "INCREMENTAL publication requires an existing primary Source.");
        }
        resources.checkpoint();
        request.getProgressListener().onProgress(WorkflowProgressPhase.STARTED);
        if (request.getSources().isEmpty()
                && request.getPublicationTargets().isEmpty()) {
            throw PdfBoxWorkflowEngine.failure(
                    DocumentFailureCode.INVALID_REQUEST,
                    "The workflow request has no source or publication target.");
        }
        requireOwnerRestrictedRoot(resources.getTemporaryRoot());

        PdfBoxWorkflowEngine.WorkerPublicationPlan publication =
                PdfBoxWorkflowEngine.prepareWorkerPublication(
                        request,
                        resources);
        Map<String, Path> sourcePaths = stagePrimarySource(request, resources);
        Map<String, Path> targetPaths = declareWorkerTargets(
                request,
                resources);
        HardenedWorkerSettings settings =
                environment.getHardenedWorkerSettings();
        byte[] initialization = WorkerMessages.encodeInitialization(
                request,
                workerPolicy(resources),
                sourcePaths,
                targetPaths,
                Collections.<Path>emptyList(),
                resources,
                settings.getMaximumMessageBytes());
        long bootstrapMemoryBytes = initializationMemoryGrant(
                initialization,
                request.getSources().size(),
                targetPaths.size(),
                0);
        boolean bootstrapMemoryRetained = false;
        boolean bootstrapMemoryOwnedByConnection = false;
        WorkerConnection connection = null;
        try {
            resources.retainOwnedMemory(bootstrapMemoryBytes);
            bootstrapMemoryRetained = true;
            connection = WorkerConnection.start(
                    environment,
                    resources,
                    request,
                    settings,
                    sourcePaths,
                    bootstrapMemoryBytes);
            bootstrapMemoryOwnedByConnection = true;
            WorkerProtocol.Frame ready = connection.exchange(
                    WorkerProtocol.INITIALIZE,
                    initialization);
            try {
                requireOpcode(ready, WorkerProtocol.READY);
                WorkerCodecIO.Input readyValue = WorkerCodecIO.input(
                        ready.getPayload());
                WorkerCommandCodec.requireVersion(readyValue.readInt(), 1);
                readyValue.requireFullyConsumed();
            } finally {
                ready.clear();
            }
            connection.finishBootstrapMemory();
            WorkerCodecIO.clearRetained(resources, initialization);
            initialization = null;

            if (!request.getSources().isEmpty()) {
                request.getProgressListener().onProgress(
                        WorkflowProgressPhase.SOURCE_OPENED);
            }
            request.getProgressListener().onProgress(
                    WorkflowProgressPhase.WORK_STARTED);
            resources.checkpoint();

            ProxyDocumentSession session = new ProxyDocumentSession(
                    connection,
                    resources,
                    environment.getReferenceFontSet().getSources(),
                    settings.getMaximumMessageBytes());
            R result;
            try {
                result = work.perform(session);
            } catch (DocumentFailure failure) {
                resources.rethrowTerminalFailure();
                connection.abortQuietly();
                throw failure;
            } catch (RuntimeException failure) {
                resources.rethrowResourceOrTerminalFailure(failure);
                connection.abortQuietly();
                throw failure;
            } finally {
                session.invalidate();
            }

            request.getProgressListener().onProgress(
                    WorkflowProgressPhase.WORK_COMPLETED);
            resources.checkpoint();
            WorkerProtocol.Frame finished = connection.exchange(
                    WorkerProtocol.FINISH,
                    new byte[0]);
            String capabilityId;
            try {
                requireOpcode(finished, WorkerProtocol.FINISHED);
                capabilityId = WorkerMessages.decodeFinished(
                        finished.getPayload(),
                        resources);
            } finally {
                finished.clear();
            }
            connection.requireExited();
            resources.mergeWorkerUsage(connection.requireWorkerResourceUsage());

            List<Path> products = new ArrayList<Path>(targetPaths.size());
            for (Path product : targetPaths.values()) {
                // The child received only this parent's remaining allowance
                // and accounted both its staged document and publication copy
                // before moving that copy to this handle. After confirmed exit
                // no child write can race this transfer into parent accounting.
                resources.registerTemporaryFile(product);
                products.add(product);
            }
            if (products.isEmpty()) {
                request.getProgressListener().onProgress(
                        WorkflowProgressPhase.COMPLETED);
                return outcome(
                        result,
                        capabilityId,
                        request,
                        Collections.<PublicationReceipt>emptyList(),
                        providerSelections);
            }

            request.getProgressListener().onProgress(
                    WorkflowProgressPhase.PUBLICATION_STARTED);
            List<PublicationReceipt> receipts = publication.publish(products);
            request.getProgressListener().onProgress(
                    WorkflowProgressPhase.COMPLETED);
            return outcome(
                    result,
                    capabilityId,
                    request,
                    receipts,
                    providerSelections);
        } finally {
            if (initialization != null) {
                WorkerCodecIO.clearRetained(resources, initialization);
            }
            if (connection != null) {
                connection.close();
            } else if (bootstrapMemoryRetained
                    && !bootstrapMemoryOwnedByConnection) {
                resources.releaseRetainedOwnedMemory(bootstrapMemoryBytes);
            }
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
                WorkflowExecutionProfile.HARDENED_WORKER,
                request.getSaveMode(),
                Collections.<String>emptyList(),
                receipts,
                providerSelections,
                request.getTransactionId().orElse(null));
    }

    private static Map<String, Path> stagePrimarySource(
            WorkflowRequest request,
            WorkflowResourceContext resources) throws DocumentFailure {
        Map<String, Path> paths = new LinkedHashMap<String, Path>();
        if (!request.getSources().isEmpty()) {
            String name = request.getPrimarySourceName();
            DocumentSource source = request.getSources().get(name);
            if (source != null) {
                paths.put(
                        name,
                        PdfBoxWorkflowEngine.snapshotForWorker(
                                source,
                                resources));
            }
        }
        return paths;
    }

    private static WorkflowResourcePolicy workerPolicy(
            WorkflowResourceContext resources) {
        WorkflowResourcePolicy policy = resources.getPolicy();
        return WorkflowResourcePolicy.builder()
                .maximumInputBytes(policy.getMaximumInputBytes())
                .maximumPages(policy.getMaximumPages())
                .maximumObjects(policy.getMaximumObjects())
                .maximumNestingDepth(policy.getMaximumNestingDepth())
                .maximumDecompressedBytes(policy.getMaximumDecompressedBytes())
                .maximumDecodedPixels(policy.getMaximumDecodedPixels())
                .maximumOwnedMemoryBytes(policy.getMaximumOwnedMemoryBytes())
                .maximumTemporaryStorageBytes(
                        policy.getMaximumTemporaryStorageBytes())
                .maximumElapsedTime(Duration.ofNanos(Long.MAX_VALUE))
                .maximumConcurrentWorkflows(policy.getMaximumConcurrentWorkflows())
                .build();
    }

    private static long initializationMemoryGrant(
            byte[] payload,
            int sourceCount,
            int targetCount,
            int fontCount) {
        long collectionEntries = (long) sourceCount
                + targetCount + fontCount;
        long payloadPeak = saturatingMultiply(payload.length, 3L);
        long collections = saturatingMultiply(
                collectionEntries,
                WorkerCodecIO.DECODED_COLLECTION_ENTRY_BYTES);
        return saturatingAdd(payloadPeak, collections);
    }

    private static long saturatingMultiply(long left, long right) {
        if (left < 0L || right < 0L
                || (left != 0L && right > Long.MAX_VALUE / left)) {
            return Long.MAX_VALUE;
        }
        return left * right;
    }

    private static long saturatingAdd(long left, long right) {
        return left > Long.MAX_VALUE - right
                ? Long.MAX_VALUE : left + right;
    }

    private static int maximumEncodedValueBytes(
            WorkflowResourceContext resources,
            int maximumMessageBytes) {
        long maximumChunkedPayload = resources.getPolicy()
                .getMaximumOwnedMemoryBytes() - maximumMessageBytes;
        long maximum = Math.max(
                (long) maximumMessageBytes,
                maximumChunkedPayload);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, maximum));
    }

    private static void writeLong(byte[] target, int offset, long value) {
        for (int index = 0; index < 8; index++) {
            target[offset + index] = (byte) (value >>> (56 - 8 * index));
        }
    }

    private static void requireOwnerRestrictedRoot(Path root)
            throws DocumentFailure {
        try {
            if (!Files.getPosixFilePermissions(root).equals(EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE))) {
                throw unavailable();
            }
        } catch (IOException | UnsupportedOperationException
                | SecurityException failure) {
            throw unavailable();
        }
    }

    private static Map<String, Path> declareWorkerTargets(
            WorkflowRequest request,
            WorkflowResourceContext resources) throws DocumentFailure {
        Map<String, Path> paths = new LinkedHashMap<String, Path>();
        int index = 0;
        for (String name : request.getPublicationTargets().keySet()) {
            resources.checkpoint();
            paths.put(
                    name,
                    resources.getTemporaryRoot().resolve(
                            ".worker-product-" + index++ + ".pdf"));
        }
        return paths;
    }

    private static void requireOpcode(
            WorkerProtocol.Frame frame,
            short expected) throws DocumentFailure {
        if (frame.getOpcode() != expected) {
            throw WorkerCodecIO.workerFailure(
                    DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    "The Worker response opcode is unsupported.");
        }
    }

    private static final class ProxyDocumentSession implements DocumentSession {

        private final WorkerConnection connection;
        private final WorkflowResourceContext resources;
        private final int maximumMessageBytes;
        private final int maximumValueBytes;
        private final Object sessionIdentity = new Object();
        private final WorkerReferenceRegistry references =
                WorkerReferenceRegistry.forProxy(sessionIdentity);
        private final Thread owner = Thread.currentThread();
        private final WorkerValueViewCodec.Remote valueRemote;
        private final WorkerFontSourceCache fontSources;
        private volatile boolean active = true;

        private ProxyDocumentSession(
                WorkerConnection connection,
                WorkflowResourceContext resources,
                List<net.zerocloud.pdf.composition.FontSource> referenceFonts,
                int maximumMessageBytes) {
            this.connection = connection;
            this.resources = resources;
            this.maximumMessageBytes = maximumMessageBytes;
            this.maximumValueBytes = maximumEncodedValueBytes(
                    resources,
                    maximumMessageBytes);
            this.fontSources = new WorkerFontSourceCache(
                    resources,
                    referenceFonts);
            connection.useFontSources(fontSources);
            this.valueRemote = new WorkerValueViewCodec.Remote() {
                @Override
                public byte[] request(byte[] request) throws DocumentFailure {
                    WorkerProtocol.Frame response = ProxyDocumentSession.this
                            .connection.exchange(
                                    WorkerProtocol.VALUE_VIEW,
                                    request);
                    try {
                        requireOpcode(
                                response,
                                WorkerProtocol.VALUE_VIEW_COMPLETED);
                        byte[] payload = response.getPayload();
                        resources.retainOwnedMemory(payload.length);
                        try {
                            return payload.clone();
                        } catch (RuntimeException | Error failure) {
                            resources.releaseRetainedOwnedMemory(
                                    payload.length);
                            throw failure;
                        }
                    } catch (DocumentFailure failure) {
                        throw ProxyDocumentSession.this.connection
                                .terminalIfWorkerFailure(failure);
                    } finally {
                        response.clear();
                    }
                }

                @Override
                public void requireActive() throws DocumentFailure {
                    requireActiveValueView();
                }

                @Override
                public DocumentFailure terminalIfWorkerFailure(
                        DocumentFailure failure) {
                    return ProxyDocumentSession.this.connection
                            .terminalIfWorkerFailure(failure);
                }

                @Override
                public WorkflowResourceContext resources() {
                    return ProxyDocumentSession.this.resources;
                }

                @Override
                public int maximumMessageBytes() {
                    return ProxyDocumentSession.this.maximumValueBytes;
                }
            };
        }

        @Override
        public void execute(DocumentCommand command) throws DocumentFailure {
            executeBatch(Collections.singletonList(command));
        }

        @Override
        public void executeBatch(
            List<? extends DocumentCommand> commands)
                throws DocumentFailure {
            requireActiveOwner();
            List<DocumentCommand> copied =
                    DocumentWorkflow.copyAndValidateCommands(commands);
            resources.checkpoint();
            int atomicLength = WorkerCommandCodec.atomicEncodedLength(
                    copied,
                    maximumMessageBytes,
                    resources.getRemainingOwnedMemoryBytes());
            if (atomicLength >= 0
                    && workerAcceptsAtomicBatch(atomicLength)) {
                executeAtomicBatch(copied, atomicLength);
                resources.checkpoint();
                return;
            }
            byte[] declaration = null;
            byte[] abort = null;
            byte[] preflight = null;
            byte[] item = null;
            WorkerProtocol.Frame response = null;
            try {
                abort = WorkerCodecIO.encode(
                        resources,
                        maximumMessageBytes,
                        output -> output.writeInt(1));
                declaration = WorkerCodecIO.encode(
                        resources,
                        maximumMessageBytes,
                        output -> {
                            output.writeInt(1);
                            output.writeInt(copied.size());
                        });
                response = connection.exchange(
                        WorkerProtocol.COMMAND_BATCH,
                        declaration);
                for (int index = 0; index < copied.size(); index++) {
                    requireCommandPreflightRequest(response, index);
                    response.clear();
                    response = null;
                    try {
                        preflight = WorkerCommandCodec.encodePreflight(
                                copied.get(index),
                                resources,
                                maximumValueBytes);
                    } catch (DocumentFailure failure) {
                        throw abortCommandBatchPreserving(abort, failure);
                    }
                    try {
                        response = connection.exchange(
                                WorkerProtocol.COMMAND_PREFLIGHT,
                                preflight);
                    } finally {
                        if (preflight != null) {
                            WorkerCodecIO.clearRetained(
                                    resources,
                                    preflight);
                            preflight = null;
                        }
                    }
                    while (response.getOpcode()
                            == WorkerProtocol
                                    .COMMAND_PREFLIGHT_DETAILS_REQUIRED) {
                        int detailKind =
                                requireCommandPreflightDetailsRequest(
                                        response,
                                        index);
                        response.clear();
                        response = null;
                        requireExpectedPreflightDetails(
                                copied.get(index),
                                detailKind);
                        try {
                            preflight = WorkerCommandCodec
                                    .encodePreflightDetails(
                                            copied.get(index),
                                            detailKind,
                                            fontSources,
                                            resources,
                                            maximumValueBytes);
                        } catch (DocumentFailure failure) {
                            throw abortCommandBatchPreserving(
                                    abort,
                                    failure);
                        }
                        try {
                            response = connection.exchange(
                                    WorkerProtocol
                                            .COMMAND_PREFLIGHT_DETAILS,
                                    preflight);
                        } finally {
                            WorkerCodecIO.clearRetained(
                                    resources,
                                    preflight);
                            preflight = null;
                        }
                    }
                    requireCommandRequest(response, index);
                    response.clear();
                    response = null;
                    try {
                        item = WorkerCommandCodec.encodeBatch(
                                Collections.singletonList(copied.get(index)),
                                references,
                                fontSources,
                                resources,
                                maximumValueBytes);
                    } catch (WorkerCommandCodec.CommandEncodingFailure failure) {
                        throw abortCommandBatchPreserving(
                                abort,
                                failure.getDocumentFailure());
                    } catch (DocumentFailure failure) {
                        throw abortCommandBatchPreserving(abort, failure);
                    }
                    try {
                        response = connection.exchange(
                                WorkerProtocol.COMMAND_ITEM,
                                item);
                    } finally {
                        WorkerCodecIO.clearRetained(resources, item);
                        item = null;
                    }
                }
                requireCommandCompleted(response);
            } finally {
                if (response != null) {
                    response.clear();
                }
                if (item != null) {
                    WorkerCodecIO.clearRetained(resources, item);
                }
                if (preflight != null) {
                    WorkerCodecIO.clearRetained(resources, preflight);
                }
                if (declaration != null) {
                    WorkerCodecIO.clearRetained(resources, declaration);
                }
                if (abort != null) {
                    WorkerCodecIO.clearRetained(resources, abort);
                }
            }
            resources.checkpoint();
        }

        private boolean workerAcceptsAtomicBatch(int encodedLength)
                throws DocumentFailure {
            byte[] offer = WorkerCodecIO.encode(
                    resources,
                    maximumMessageBytes,
                    output -> {
                        output.writeInt(1);
                        output.writeInt(encodedLength);
                    });
            WorkerProtocol.Frame response;
            try {
                response = connection.exchange(
                        WorkerProtocol.COMMANDS_OFFER,
                        offer);
            } finally {
                WorkerCodecIO.clearRetained(resources, offer);
            }
            try {
                if (response.getOpcode() != WorkerProtocol.COMMANDS_ACCEPTED
                        && response.getOpcode()
                                != WorkerProtocol.COMMANDS_DEFERRED) {
                    throw connection.terminalIfWorkerFailure(
                            WorkerCodecIO.workerFailure(
                                    DocumentFailureCode
                                            .WORKER_PROTOCOL_REJECTED,
                                    "The atomic Worker Command offer response is unsupported."));
                }
                WorkerCodecIO.Input input = WorkerCodecIO.input(
                        response.getPayload());
                WorkerCommandCodec.requireVersion(input.readInt(), 1);
                input.requireFullyConsumed();
                return response.getOpcode()
                        == WorkerProtocol.COMMANDS_ACCEPTED;
            } finally {
                response.clear();
            }
        }

        private void executeAtomicBatch(
                List<DocumentCommand> commands,
                int expectedLength) throws DocumentFailure {
            byte[] payload;
            try {
                payload = WorkerCommandCodec.encodeBatch(
                        commands,
                        references,
                        fontSources,
                        resources,
                        maximumMessageBytes);
            } catch (WorkerCommandCodec.CommandEncodingFailure failure) {
                throw connection.terminalIfWorkerFailure(
                        failure.getDocumentFailure());
            }
            if (payload.length != expectedLength) {
                WorkerCodecIO.clearRetained(resources, payload);
                throw connection.terminalIfWorkerFailure(
                        WorkerCodecIO.workerFailure(
                                DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                                "The atomic Worker Command batch length changed."));
            }
            WorkerProtocol.Frame response;
            try {
                response = connection.exchange(
                        WorkerProtocol.COMMANDS,
                        payload);
            } finally {
                WorkerCodecIO.clearRetained(resources, payload);
            }
            try {
                requireCommandCompleted(response);
            } finally {
                response.clear();
            }
        }

        private void requireCommandPreflightRequest(
                WorkerProtocol.Frame response,
                int expectedIndex) throws DocumentFailure {
            requireIndexedCommandRequest(
                    response,
                    WorkerProtocol.COMMAND_PREFLIGHT_REQUIRED,
                    expectedIndex);
        }

        private void requireCommandRequest(
                WorkerProtocol.Frame response,
                int expectedIndex) throws DocumentFailure {
            requireIndexedCommandRequest(
                    response,
                    WorkerProtocol.COMMAND_REQUIRED,
                    expectedIndex);
        }

        private int requireCommandPreflightDetailsRequest(
                WorkerProtocol.Frame response,
                int expectedIndex) throws DocumentFailure {
            try {
                requireOpcode(
                        response,
                        WorkerProtocol.COMMAND_PREFLIGHT_DETAILS_REQUIRED);
                WorkerCodecIO.Input value = WorkerCodecIO.input(
                        response.getPayload());
                WorkerCommandCodec.requireVersion(value.readInt(), 1);
                if (value.readInt() != expectedIndex) {
                    throw WorkerCodecIO.workerFailure(
                            DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                            "The Worker requested inapplicable Command preflight details.");
                }
                int kind = value.readInt();
                value.requireFullyConsumed();
                if (kind != WorkerCommandCodec.PREFLIGHT_DETAILS_ANNOTATIONS
                        && kind != WorkerCommandCodec
                                .PREFLIGHT_DETAILS_ANNOTATION_IDENTIFIERS
                        && kind != WorkerCommandCodec
                                .PREFLIGHT_DETAILS_CANVAS_PROGRAM
                        && kind != WorkerCommandCodec
                                .PREFLIGHT_DETAILS_POSITIONED_TEXT
                        && kind != WorkerCommandCodec
                                .PREFLIGHT_DETAILS_CANVAS) {
                    throw WorkerCodecIO.workerFailure(
                            DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                            "The Worker requested unsupported Command preflight details.");
                }
                return kind;
            } catch (DocumentFailure failure) {
                throw connection.terminalIfWorkerFailure(failure);
            }
        }

        private void requireExpectedPreflightDetails(
                DocumentCommand command,
                int kind) throws DocumentFailure {
            boolean expected = (kind
                            == WorkerCommandCodec
                                    .PREFLIGHT_DETAILS_ANNOTATIONS
                            && command instanceof UpdateAnnotations)
                    || (kind == WorkerCommandCodec
                                    .PREFLIGHT_DETAILS_ANNOTATION_IDENTIFIERS
                            && command instanceof UpdateAnnotations)
                    || (kind
                            == WorkerCommandCodec
                                    .PREFLIGHT_DETAILS_POSITIONED_TEXT
                            && command instanceof DrawPositionedUnicodeText)
                    || (kind == WorkerCommandCodec.PREFLIGHT_DETAILS_CANVAS
                            && command instanceof DrawCanvas);
            expected = expected
                    || (kind == WorkerCommandCodec
                                    .PREFLIGHT_DETAILS_CANVAS_PROGRAM
                            && command instanceof DrawCanvas);
            if (!expected) {
                throw connection.terminalIfWorkerFailure(
                        WorkerCodecIO.workerFailure(
                                DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                                "The Worker requested inapplicable Command preflight details."));
            }
        }

        private void requireIndexedCommandRequest(
                WorkerProtocol.Frame response,
                short expectedOpcode,
                int expectedIndex) throws DocumentFailure {
            try {
                requireOpcode(response, expectedOpcode);
                WorkerCodecIO.Input value = WorkerCodecIO.input(
                        response.getPayload());
                WorkerCommandCodec.requireVersion(value.readInt(), 1);
                if (value.readInt() != expectedIndex) {
                    throw WorkerCodecIO.workerFailure(
                            DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                            "The Worker requested an inapplicable Command item.");
                }
                value.requireFullyConsumed();
            } catch (DocumentFailure failure) {
                throw connection.terminalIfWorkerFailure(failure);
            }
        }

        private void requireCommandCompleted(WorkerProtocol.Frame response)
                throws DocumentFailure {
            try {
                requireOpcode(response, WorkerProtocol.COMMAND_COMPLETED);
                WorkerCodecIO.Input value = WorkerCodecIO.input(
                        response.getPayload());
                WorkerCommandCodec.requireVersion(value.readInt(), 1);
                value.requireFullyConsumed();
            } catch (DocumentFailure failure) {
                throw connection.terminalIfWorkerFailure(failure);
            }
        }

        private void abortCommandBatch(byte[] payload)
                throws DocumentFailure {
            WorkerProtocol.Frame response = connection.exchangeControl(
                    WorkerProtocol.COMMAND_BATCH_ABORT,
                    payload);
            try {
                requireOpcode(
                        response,
                        WorkerProtocol.COMMAND_BATCH_ABORTED);
                WorkerCodecIO.Input value = WorkerCodecIO.input(
                        response.getPayload());
                WorkerCommandCodec.requireVersion(value.readInt(), 1);
                value.requireFullyConsumed();
            } catch (DocumentFailure failure) {
                throw connection.terminalIfWorkerFailure(failure);
            } finally {
                response.clear();
            }
        }

        private DocumentFailure abortCommandBatchPreserving(
                byte[] payload,
                DocumentFailure primary) {
            try {
                abortCommandBatch(payload);
            } catch (DocumentFailure abortFailure) {
                if (abortFailure != primary) {
                    primary.addSuppressed(abortFailure);
                }
                resources.preferEarlierTerminalFailure(primary);
            }
            return connection.terminalIfWorkerFailure(primary);
        }

        @Override
        public <R> R query(DocumentQuery<R> query) throws DocumentFailure {
            requireActiveOwner();
            java.util.Objects.requireNonNull(query, "query");
            resources.checkpoint();
            if (query instanceof net.zerocloud.pdf.query.RenderPage
                    && !resources.rendering().usesDefault()) {
                net.zerocloud.pdf.query.RenderPage render = (net.zerocloud.pdf.query.RenderPage) query;
                try (RenderingSnapshot snapshot = query(new RenderSnapshotQuery(render))) {
                    @SuppressWarnings("unchecked")
                    R result = (R) resources.rendering().renderExternal(render, snapshot, resources);
                    return result;
                }
            }
            byte[] preflight = WorkerQueryCodec.encodePreflight(
                    query,
                    resources,
                    maximumValueBytes);
            WorkerProtocol.Frame preflightResponse;
            try {
                preflightResponse = connection.exchange(
                        WorkerProtocol.QUERY_PREFLIGHT,
                        preflight);
            } finally {
                WorkerCodecIO.clearRetained(resources, preflight);
            }
            try {
                requireOpcode(
                        preflightResponse,
                        WorkerProtocol.QUERY_PREFLIGHT_COMPLETED);
                WorkerCodecIO.Input value = WorkerCodecIO.input(
                        preflightResponse.getPayload());
                WorkerCommandCodec.requireVersion(value.readInt(), 1);
                value.requireFullyConsumed();
            } catch (DocumentFailure failure) {
                throw connection.terminalIfWorkerFailure(failure);
            } finally {
                preflightResponse.clear();
            }
            byte[] payload;
            try {
                payload = WorkerQueryCodec.encodeQuery(
                        query,
                        references,
                        resources,
                        maximumValueBytes);
            } catch (DocumentFailure failure) {
                throw connection.terminalIfWorkerFailure(failure);
            }
            WorkerProtocol.Frame response;
            try {
                response = connection.exchange(WorkerProtocol.QUERY, payload);
            } finally {
                WorkerCodecIO.clearRetained(resources, payload);
            }
            try {
                requireOpcode(response, WorkerProtocol.QUERY_COMPLETED);
                R result;
                if (query instanceof net.zerocloud.pdf.query.InspectObject) {
                    @SuppressWarnings("unchecked")
                    R inspected = (R) WorkerValueViewCodec.decodeRoot(
                            response.getPayload(),
                            references,
                            valueRemote);
                    result = inspected;
                } else {
                    result = WorkerQueryCodec.decodeResult(
                            query,
                            response.getPayload(),
                            references,
                            resources);
                }
                resources.checkpoint();
                return result;
            } catch (DocumentFailure failure) {
                throw connection.terminalIfWorkerFailure(failure);
            } finally {
                response.clear();
            }
        }

        private void invalidate() {
            if (active) {
                resources.expireRenderedPages();
                active = false;
                fontSources.close();
            }
        }

        private void requireActiveOwner() {
            if (!active) {
                throw new IllegalStateException(
                        "Document Session is no longer active.");
            }
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException(
                        "Document Session is thread-confined.");
            }
        }

        private void requireActiveValueView() throws DocumentFailure {
            if (!active) {
                throw PdfBoxValueAdapter.failure(
                        DocumentFailureCode.PDF_VALUE_VIEW_EXPIRED,
                        "The PDF Value view is no longer active.");
            }
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException(
                        "Document Session is thread-confined.");
            }
        }

        private IsolationProbe probeIsolation(String siblingRootName)
                throws DocumentFailure {
            return probeIsolation(siblingRootName, 0L);
        }

        private void terminateWorkerForTest() {
            requireActiveOwner();
            connection.terminateForTest();
        }

        private void requestMalformedResponseForTest()
                throws DocumentFailure {
            requireActiveOwner();
            WorkerProtocol.Frame response = connection.exchange(
                    WorkerProtocol.MALFORMED_RESPONSE_PROBE,
                    new byte[0]);
            try {
                requireOpcode(
                        response,
                        WorkerProtocol.MALFORMED_RESPONSE_COMPLETED);
            } catch (DocumentFailure failure) {
                throw connection.terminalIfWorkerFailure(failure);
            } finally {
                response.clear();
            }
        }

        private IsolationProbe probeIsolation(
                String siblingRootName,
                long ownedMemoryProbeBytes) throws DocumentFailure {
            requireActiveOwner();
            if (ownedMemoryProbeBytes < 0L
                    || ownedMemoryProbeBytes > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "Worker memory probes must fit one byte array.");
            }
            byte[] request = WorkerCodecIO.encode(
                    resources,
                    maximumMessageBytes,
                    output -> {
                        output.writeInt(1);
                        output.writeNullableString(siblingRootName);
                        output.writeLong(ownedMemoryProbeBytes);
                    });
            return exchangeIsolationProbe(request);
        }

        private long probeOwnedMemoryBoundary(boolean firstExcess)
                throws DocumentFailure {
            requireActiveOwner();
            byte[] request = WorkerCodecIO.encode(
                    resources,
                    maximumMessageBytes,
                    output -> {
                        output.writeInt(1);
                        output.writeNullableString(null);
                        output.writeLong(1L);
                    });
            long amount = resources.getRemainingOwnedMemoryBytes()
                    - request.length + (firstExcess ? 1L : 0L);
            if (amount <= 0L || amount > Integer.MAX_VALUE) {
                WorkerCodecIO.clearRetained(resources, request);
                throw new IllegalArgumentException(
                        "The memory-probe boundary is not representable.");
            }
            writeLong(request, 5, amount);
            exchangeIsolationProbe(request);
            return amount;
        }

        private IsolationProbe exchangeIsolationProbe(byte[] request)
                throws DocumentFailure {
            WorkerProtocol.Frame response;
            try {
                response = connection.exchange(
                        WorkerProtocol.NETWORK_PROBE,
                        request);
            } finally {
                WorkerCodecIO.clearRetained(resources, request);
            }
            try {
                requireOpcode(
                        response,
                        WorkerProtocol.NETWORK_PROBE_COMPLETED);
                WorkerCodecIO.Input input = WorkerCodecIO.accountedInput(
                        response.getPayload(),
                        resources);
                WorkerCommandCodec.requireVersion(input.readInt(), 1);
                IsolationProbe probe = new IsolationProbe(
                        input.readBoolean(),
                        input.readBoolean(),
                        input.readBoolean(),
                        input.readBoolean(),
                        input.readBoolean(),
                        input.readBoolean(),
                        input.readBoolean(),
                        input.readBoolean(),
                        input.readBoolean(),
                        input.readBoolean(),
                        input.readString(),
                        input.readString());
                input.requireFullyConsumed();
                return probe;
            } catch (DocumentFailure failure) {
                throw connection.terminalIfWorkerFailure(failure);
            } finally {
                response.clear();
            }
        }
    }

    static final class IsolationProbe {
        private final boolean outboundNetworkDenied;
        private final boolean listeningNetworkDenied;
        private final boolean unixDomainConnectDenied;
        private final boolean unixDomainListenDenied;
        private final boolean descendantProcessDenied;
        private final boolean filesystemEscapeDenied;
        private final boolean callerClassPathDenied;
        private final boolean deepReflectionDenied;
        private final boolean nativePathLoadDenied;
        private final boolean nativeLibraryLoadDenied;
        private final String workerProcessIdentity;
        private final String workerRootName;

        private IsolationProbe(
                boolean outboundNetworkDenied,
                boolean listeningNetworkDenied,
                boolean unixDomainConnectDenied,
                boolean unixDomainListenDenied,
                boolean descendantProcessDenied,
                boolean filesystemEscapeDenied,
                boolean callerClassPathDenied,
                boolean deepReflectionDenied,
                boolean nativePathLoadDenied,
                boolean nativeLibraryLoadDenied,
                String workerProcessIdentity,
                String workerRootName) {
            this.outboundNetworkDenied = outboundNetworkDenied;
            this.listeningNetworkDenied = listeningNetworkDenied;
            this.unixDomainConnectDenied = unixDomainConnectDenied;
            this.unixDomainListenDenied = unixDomainListenDenied;
            this.descendantProcessDenied = descendantProcessDenied;
            this.filesystemEscapeDenied = filesystemEscapeDenied;
            this.callerClassPathDenied = callerClassPathDenied;
            this.deepReflectionDenied = deepReflectionDenied;
            this.nativePathLoadDenied = nativePathLoadDenied;
            this.nativeLibraryLoadDenied = nativeLibraryLoadDenied;
            this.workerProcessIdentity = workerProcessIdentity;
            this.workerRootName = workerRootName;
        }

        boolean isOutboundNetworkDenied() {
            return outboundNetworkDenied;
        }

        boolean isListeningNetworkDenied() {
            return listeningNetworkDenied;
        }

        boolean isUnixDomainConnectDenied() {
            return unixDomainConnectDenied;
        }

        boolean isUnixDomainListenDenied() {
            return unixDomainListenDenied;
        }

        boolean isDescendantProcessDenied() {
            return descendantProcessDenied;
        }

        boolean isFilesystemEscapeDenied() {
            return filesystemEscapeDenied;
        }

        boolean isCallerClassPathDenied() {
            return callerClassPathDenied;
        }

        boolean isDeepReflectionDenied() {
            return deepReflectionDenied;
        }

        boolean isNativePathLoadDenied() {
            return nativePathLoadDenied;
        }

        boolean isNativeLibraryLoadDenied() {
            return nativeLibraryLoadDenied;
        }

        String getWorkerProcessIdentity() {
            return workerProcessIdentity;
        }

        String getWorkerRootName() {
            return workerRootName;
        }
    }

    private enum StopReason {
        CANCELLED,
        ELAPSED,
        COMPLETED
    }

    private static final class WorkerConnection implements AutoCloseable {

        private final Process process;
        private final WorkerProtocol.Endpoint endpoint;
        private final WorkflowResourceContext resources;
        private final int maximumMessageBytes;
        private final int maximumValueBytes;
        private final ExecutorService reader;
        private final ScheduledExecutorService watchdog;
        private final WorkflowRequest request;
        private final List<String> deferredSourceNames;
        private final Map<String, String> materializedSourceNames =
                new LinkedHashMap<String, String>();
        private final Set<String> materializedSourceCredentialNames =
                new LinkedHashSet<String>();
        private boolean materializedOutputOwnerCredential;
        private boolean materializedOutputUserCredential;
        private WorkerFontSourceCache fontSources;
        private WorkflowResourceUsage workerResourceUsage;
        private int nextDeferredSource;
        private long childOwnedMemoryBytes;
        private long childTemporaryBytes;
        private long bootstrapMemoryBytes;
        private long reportedChildMemoryBytes;
        private final long physicalStartedNanos;
        private final long physicalMaximumElapsedNanos;
        private final AtomicReference<StopReason> stopReason =
                new AtomicReference<StopReason>();
        private volatile boolean closed;

        private WorkerConnection(
                Process process,
                WorkerProtocol.Endpoint endpoint,
                WorkflowResourceContext resources,
                WorkflowRequest request,
                int maximumMessageBytes,
                Map<String, Path> sourcePaths,
                long bootstrapMemoryBytes) {
            this.process = process;
            this.endpoint = endpoint;
            this.resources = resources;
            this.request = request;
            this.maximumMessageBytes = maximumMessageBytes;
            this.maximumValueBytes = maximumEncodedValueBytes(
                    resources,
                    maximumMessageBytes);
            this.childOwnedMemoryBytes = bootstrapMemoryBytes;
            this.bootstrapMemoryBytes = bootstrapMemoryBytes;
            for (String name : sourcePaths.keySet()) {
                this.materializedSourceNames.put(name, name);
            }
            this.deferredSourceNames = new ArrayList<String>();
            for (String name : request.getSources().keySet()) {
                if (!sourcePaths.containsKey(name)) {
                    deferredSourceNames.add(name);
                }
            }
            this.physicalStartedNanos = System.nanoTime();
            this.physicalMaximumElapsedNanos = resources.getPolicy()
                    .getMaximumElapsedTime().toNanos();
            this.reader = Executors.newSingleThreadExecutor(
                    daemonThreadFactory("folio-pdf-worker-reader"));
            this.watchdog = Executors.newSingleThreadScheduledExecutor(
                    daemonThreadFactory("folio-pdf-worker-watchdog"));
            startErrorDrainer(process.getErrorStream());
            watchdog.scheduleAtFixedRate(
                    this::checkExecutionStop,
                    RESPONSE_POLL_MILLIS,
                    RESPONSE_POLL_MILLIS,
                    TimeUnit.MILLISECONDS);
        }

        private void checkExecutionStop() {
            if (request.getCancellationToken().isCancellationRequested()) {
                stop(StopReason.CANCELLED);
                return;
            }
            long physicalElapsed = System.nanoTime() - physicalStartedNanos;
            if (physicalElapsed >= 0L
                    && physicalElapsed > physicalMaximumElapsedNanos) {
                stop(StopReason.ELAPSED);
            }
        }

        static WorkerConnection start(
                WorkflowEnvironment environment,
                WorkflowResourceContext resources,
                WorkflowRequest request,
                HardenedWorkerSettings settings,
                Map<String, Path> sourcePaths,
                long bootstrapMemoryBytes) throws DocumentFailure {
            Path java = Paths.get(
                    System.getProperty("java.home"),
                    "bin",
                    isWindows() ? "java.exe" : "java")
                    .toAbsolutePath()
                    .normalize();
            Path prlimit = Paths.get("/usr/bin/prlimit");
            if (!isLinux()
                    || !Files.isExecutable(java)
                    || !Files.isExecutable(prlimit)) {
                throw unavailable();
            }
            long cpuSeconds = ceilingSeconds(
                    resources.getPolicy().getMaximumElapsedTime());
            List<String> command = new ArrayList<String>();
            command.add(prlimit.toString());
            command.add("--cpu=" + cpuSeconds);
            command.add("--nofile=" + WORKER_OPEN_FILE_LIMIT);
            command.add("--");
            command.add(java.toString());
            command.add("-Xmx" + settings.getMaximumHeapBytes());
            command.add("-XX:MaxDirectMemorySize="
                    + settings.getMaximumHeapBytes());
            command.add("-Xss1m");
            if (requiresSecurityManagerAllowOption()) {
                command.add("-Djava.security.manager=allow");
            }
            command.add("-Djava.io.tmpdir=" + resources.getTemporaryRoot());
            command.add("-cp");
            command.add(workerClassPath());
            command.add(HardenedWorkerMain.class.getName());
            command.add(resources.getTemporaryRoot().toString());
            command.add(Integer.toString(settings.getMaximumMessageBytes()));
            command.add(Long.toString(
                    resources.getPolicy().getMaximumOwnedMemoryBytes()));

            byte[] key = new byte[WorkerProtocol.AUTHENTICATION_KEY_BYTES];
            synchronized (environment.getSecureRandom()) {
                environment.getSecureRandom().nextBytes(key);
            }
            Process process = null;
            try {
                ProcessBuilder builder = new ProcessBuilder(command);
                builder.directory(resources.getTemporaryRoot().toFile());
                builder.environment().clear();
                process = builder.start();
                WorkerProtocol.writeAuthenticationKey(
                        process.getOutputStream(),
                        key);
                WorkerProtocol.Endpoint endpoint = WorkerProtocol.endpoint(
                        process.getInputStream(),
                        process.getOutputStream(),
                        key,
                        settings.getMaximumMessageBytes(),
                        resources);
                return new WorkerConnection(
                        process,
                        endpoint,
                        resources,
                        request,
                        settings.getMaximumMessageBytes(),
                        sourcePaths,
                        bootstrapMemoryBytes);
            } catch (IOException | RuntimeException failure) {
                if (process != null) {
                    terminateAndAwait(process);
                }
                throw unavailable();
            } finally {
                Arrays.fill(key, (byte) 0);
            }
        }

        WorkerProtocol.Frame exchange(short opcode, byte[] payload)
                throws DocumentFailure {
            return exchange(opcode, payload, true);
        }

        private void useFontSources(WorkerFontSourceCache value) {
            if (fontSources != null) {
                throw new IllegalStateException(
                        "Worker Font Sources are already attached.");
            }
            fontSources = value;
        }

        WorkerProtocol.Frame exchangeControl(short opcode, byte[] payload)
                throws DocumentFailure {
            return exchange(opcode, payload, false);
        }

        private WorkerProtocol.Frame exchange(
                short opcode,
                byte[] payload,
                boolean checkResourceState) throws DocumentFailure {
            return exchange(
                    opcode,
                    payload,
                    checkResourceState,
                    true);
        }

        private WorkerProtocol.Frame exchange(
                short opcode,
                byte[] payload,
                boolean checkResourceState,
                boolean synchronizeMemory) throws DocumentFailure {
            try {
                if (opcode != WorkerProtocol.INITIALIZE) {
                    grantChildMemory(
                            endpoint.requiredReceiveMemory(payload.length));
                }
                endpoint.send(opcode, payload);
            } catch (WorkerProtocol.ProtocolException failure) {
                throw protocolFailure(failure);
            } catch (IOException | RuntimeException failure) {
                throw stoppedOrTerminated();
            }
            CompletableFuture<WorkerProtocol.Frame> response = receive();
            int progressCount = 0;
            while (true) {
                try {
                    WorkerProtocol.Frame frame = response.get(
                            RESPONSE_POLL_MILLIS,
                            TimeUnit.MILLISECONDS);
                    if (frame.getOpcode() == WorkerProtocol.TEMPORARY_RESERVE
                            || frame.getOpcode() == WorkerProtocol.TEMPORARY_RELEASE) {
                        try {
                            long amount = WorkerMessages.decodeMemoryAmount(frame.getPayload());
                            if (frame.getOpcode() == WorkerProtocol.TEMPORARY_RESERVE) {
                                resources.reserveTemporaryBytes(amount);
                                childTemporaryBytes += amount;
                                byte[] granted = WorkerMessages.encodeMemoryAmount(amount);
                                try { endpoint.send(WorkerProtocol.TEMPORARY_GRANTED, granted); }
                                catch (WorkerProtocol.ProtocolException failure) { throw protocolFailure(failure); }
                                catch (IOException failure) { throw stoppedOrTerminated(); }
                                finally { Arrays.fill(granted, (byte) 0); }
                            } else {
                                if (amount > childTemporaryBytes) {
                                    throw WorkerCodecIO.workerFailure(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                                            "The Worker temporary-storage release is inapplicable.");
                                }
                                childTemporaryBytes -= amount;
                                resources.releaseTemporaryBytes(amount);
                            }
                        } finally { frame.clear(); }
                        response = receive();
                        continue;
                    }
                    if (frame.getOpcode() == WorkerProtocol.MEMORY_RESERVE) {
                        try {
                            grantChildMemory(WorkerMessages
                                    .decodeMemoryAmount(frame.getPayload()));
                        } finally {
                            frame.clear();
                        }
                        response = receive();
                        continue;
                    }
                    if (frame.getOpcode() == WorkerProtocol.MEMORY_RELEASE) {
                        try {
                            releaseChildMemory(WorkerMessages
                                    .decodeMemoryAmount(frame.getPayload()));
                        } finally {
                            frame.clear();
                        }
                        response = receive();
                        continue;
                    }
                    if (frame.getOpcode() == WorkerProtocol.RESOURCE_USAGE) {
                        try {
                            if (opcode != WorkerProtocol.FINISH
                                    || workerResourceUsage != null) {
                                throw WorkerCodecIO.workerFailure(
                                        DocumentFailureCode
                                                .WORKER_PROTOCOL_REJECTED,
                                        "The Worker resource usage is inapplicable.");
                            }
                            workerResourceUsage =
                                    WorkerMessages.decodeResourceUsage(
                                            frame.getPayload(),
                                            resources);
                        } finally {
                            frame.clear();
                        }
                        response = receive();
                        continue;
                    }
                    if (synchronizeMemory
                            && isQuiescentResponse(frame.getOpcode())) {
                        try {
                            synchronizeChildMemory(checkResourceState);
                        } catch (DocumentFailure | RuntimeException
                                | Error failure) {
                            frame.clear();
                            throw failure;
                        }
                    }
                    if (frame.getOpcode() == WorkerProtocol.SOURCE_REQUIRED) {
                        WorkerMessages.SourceRequest sourceRequest;
                        try {
                            requireInitializationInputRequest(opcode);
                            sourceRequest = WorkerMessages.decodeSourceRequest(
                                    frame.getPayload(),
                                    resources);
                        } finally {
                            frame.clear();
                        }
                        try (WorkerMessages.SourceRequest ownedRequest =
                                sourceRequest) {
                            serviceSourceRequest(ownedRequest);
                        }
                        response = receive();
                        continue;
                    }
                    if (frame.getOpcode()
                            == WorkerProtocol.CREDENTIAL_REQUIRED) {
                        WorkerMessages.CredentialRequest credentialRequest;
                        try {
                            requireInitializationInputRequest(opcode);
                            credentialRequest =
                                    WorkerMessages.decodeCredentialRequest(
                                            frame.getPayload(),
                                            resources);
                        } finally {
                            frame.clear();
                        }
                        try (WorkerMessages.CredentialRequest ownedRequest =
                                credentialRequest) {
                            serviceCredentialRequest(ownedRequest);
                        }
                        response = receive();
                        continue;
                    }
                    if (frame.getOpcode() == WorkerProtocol.FONT_REQUIRED) {
                        long fontIdentifier;
                        try {
                            if (opcode != WorkerProtocol.COMMAND_ITEM
                                    || fontSources == null) {
                                throw WorkerCodecIO.workerFailure(
                                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                                        "The Worker font-source request is inapplicable.");
                            }
                            fontIdentifier = WorkerMessages.decodeFontRequest(
                                    frame.getPayload(),
                                    resources);
                        } finally {
                            frame.clear();
                        }
                        serviceFontRequest(fontIdentifier);
                        response = receive();
                        continue;
                    }
                    if (frame.getOpcode() == WorkerProtocol.PROGRESS) {
                        WorkflowProgressPhase phase;
                        try {
                            if (opcode != WorkerProtocol.FINISH
                                    || request.getPublicationTargets().isEmpty()) {
                                throw WorkerCodecIO.workerFailure(
                                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                                        "The Worker progress frame is inapplicable.");
                            }
                            phase = WorkerMessages.decodeProgress(
                                    frame.getPayload(),
                                    resources);
                            WorkflowProgressPhase expected = progressCount == 0
                                    ? WorkflowProgressPhase.STAGED
                                    : WorkflowProgressPhase.VALIDATED;
                            if (progressCount >= 2 || phase != expected) {
                                throw WorkerCodecIO.workerFailure(
                                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                                        "The Worker progress sequence is invalid.");
                            }
                        } catch (DocumentFailure failure) {
                            process.destroyForcibly();
                            throw terminalIfWorkerFailure(failure);
                        } finally {
                            frame.clear();
                        }
                        try {
                            request.getProgressListener().onProgress(phase);
                            resources.checkpoint();
                            acknowledgeProgress(phase);
                            progressCount++;
                        } catch (DocumentFailure failure) {
                            process.destroyForcibly();
                            throw terminalIfWorkerFailure(failure);
                        }
                        response = receive();
                        continue;
                    }
                    if (frame.getOpcode() == WorkerProtocol.FAILURE) {
                        DocumentFailure workerFailure;
                        try {
                            workerFailure = WorkerMessages.decodeFailure(
                                    frame.getPayload(),
                                    resources);
                        } finally {
                            frame.clear();
                        }
                        if (synchronizeMemory
                                && isRecoverableFailureExchange(opcode)
                                && !isTerminalWorkerFailure(workerFailure)) {
                            synchronizeChildMemory(checkResourceState);
                        }
                        throw terminalIfWorkerFailure(workerFailure);
                    }
                    if (opcode == WorkerProtocol.FINISH
                            && !request.getPublicationTargets().isEmpty()
                            && progressCount != 2) {
                        try {
                            process.destroyForcibly();
                            throw terminalIfWorkerFailure(
                                    WorkerCodecIO.workerFailure(
                                            DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                                            "The Worker progress sequence is incomplete."));
                        } finally {
                            frame.clear();
                        }
                    }
                    return frame;
                } catch (TimeoutException retry) {
                    if (checkResourceState) {
                        try {
                            resources.checkpoint();
                        } catch (DocumentFailure failure) {
                            cancelReceive(response);
                            process.destroyForcibly();
                            throw failure;
                        }
                    }
                    if (!process.isAlive()) {
                        cancelReceive(response);
                        throw stoppedOrTerminated();
                    }
                } catch (InterruptedException failure) {
                    cancelReceive(response);
                    Thread.currentThread().interrupt();
                    process.destroyForcibly();
                    throw terminalIfWorkerFailure(
                            WorkerCodecIO.workerFailure(
                                    DocumentFailureCode.WORKER_TERMINATED,
                                    "The Worker wait was interrupted."));
                } catch (ExecutionException failure) {
                    Throwable cause = failure.getCause();
                    if (cause instanceof WorkerProtocol.ProtocolException) {
                        throw protocolFailure(
                                (WorkerProtocol.ProtocolException) cause);
                    }
                    throw stoppedOrTerminated();
                }
            }
        }

        private void synchronizeChildMemory(boolean checkResourceState)
                throws DocumentFailure {
            WorkerProtocol.Frame synchronizedFrame = exchange(
                    WorkerProtocol.MEMORY_SYNCHRONIZE,
                    new byte[0],
                    checkResourceState,
                    false);
            try {
                if (synchronizedFrame.getOpcode()
                        != WorkerProtocol.MEMORY_SYNCHRONIZED
                        || synchronizedFrame.getPayload().length != 0) {
                    throw WorkerCodecIO.workerFailure(
                            DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                            "The Worker memory synchronization response is unsupported.");
                }
            } finally {
                synchronizedFrame.clear();
            }
        }

        private WorkflowResourceUsage requireWorkerResourceUsage()
                throws DocumentFailure {
            if (workerResourceUsage == null) {
                throw terminalIfWorkerFailure(WorkerCodecIO.workerFailure(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "The Worker resource usage is missing."));
            }
            return workerResourceUsage;
        }

        private static boolean isQuiescentResponse(short opcode) {
            return opcode == WorkerProtocol.READY
                    || opcode == WorkerProtocol.COMMANDS_ACCEPTED
                    || opcode == WorkerProtocol.COMMANDS_DEFERRED
                    || opcode == WorkerProtocol.COMMAND_PREFLIGHT_REQUIRED
                    || opcode
                            == WorkerProtocol.COMMAND_PREFLIGHT_DETAILS_REQUIRED
                    || opcode == WorkerProtocol.COMMAND_REQUIRED
                    || opcode == WorkerProtocol.COMMAND_COMPLETED
                    || opcode == WorkerProtocol.QUERY_COMPLETED
                    || opcode == WorkerProtocol.VALUE_VIEW_COMPLETED
                    || opcode == WorkerProtocol.NETWORK_PROBE_COMPLETED
                    || opcode == WorkerProtocol.QUERY_PREFLIGHT_COMPLETED
                    || opcode == WorkerProtocol.COMMAND_BATCH_ABORTED
                    || opcode == WorkerProtocol.SOURCE_REQUIRED
                    || opcode == WorkerProtocol.CREDENTIAL_REQUIRED
                    || opcode == WorkerProtocol.FONT_REQUIRED
                    || opcode == WorkerProtocol.PROGRESS;
        }

        private static boolean isRecoverableFailureExchange(short opcode) {
            return opcode == WorkerProtocol.COMMANDS
                    || opcode == WorkerProtocol.COMMAND_BATCH
                    || opcode == WorkerProtocol.COMMAND_ITEM
                    || opcode == WorkerProtocol.COMMAND_PREFLIGHT
                    || opcode == WorkerProtocol.COMMAND_PREFLIGHT_DETAILS
                    || opcode == WorkerProtocol.COMMAND_BATCH_ABORT
                    || opcode == WorkerProtocol.QUERY
                    || opcode == WorkerProtocol.QUERY_PREFLIGHT
                    || opcode == WorkerProtocol.VALUE_VIEW
                    || opcode == WorkerProtocol.FONT_VALUE
                    || opcode == WorkerProtocol.FONT_VALUE_FAILED;
        }

        private static boolean isTerminalWorkerFailure(
                DocumentFailure failure) {
            switch (failure.getCode()) {
                case WORKER_UNAVAILABLE:
                case WORKER_AUTHENTICATION_FAILED:
                case WORKER_PROTOCOL_REJECTED:
                case WORKER_MESSAGE_LIMIT_EXCEEDED:
                case WORKER_TERMINATED:
                    return true;
                default:
                    return false;
            }
        }

        private void grantChildMemory(long amount)
                throws DocumentFailure {
            if (amount == 0L) {
                return;
            }
            long maximum = resources.getPolicy().getMaximumOwnedMemoryBytes();
            if (reportedChildMemoryBytes > maximum - amount) {
                throw WorkerCodecIO.workerFailure(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "The Worker memory reservation is inapplicable.");
            }
            long fromBootstrap = Math.min(bootstrapMemoryBytes, amount);
            long additional = amount - fromBootstrap;
            if (additional != 0L) {
                resources.retainOwnedMemory(additional);
                childOwnedMemoryBytes += additional;
            }
            bootstrapMemoryBytes -= fromBootstrap;
            reportedChildMemoryBytes += amount;
            byte[] granted = WorkerMessages.encodeMemoryAmount(amount);
            try {
                endpoint.send(WorkerProtocol.MEMORY_GRANTED, granted);
            } catch (WorkerProtocol.ProtocolException failure) {
                throw protocolFailure(failure);
            } catch (IOException | RuntimeException failure) {
                throw stoppedOrTerminated();
            } finally {
                Arrays.fill(granted, (byte) 0);
            }
        }

        private void releaseChildMemory(long amount)
                throws DocumentFailure {
            if (amount > reportedChildMemoryBytes
                    || amount > childOwnedMemoryBytes) {
                throw WorkerCodecIO.workerFailure(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "The Worker memory release is inapplicable.");
            }
            reportedChildMemoryBytes -= amount;
            childOwnedMemoryBytes -= amount;
            resources.releaseRetainedOwnedMemory(amount);
        }

        void finishBootstrapMemory() {
            if (bootstrapMemoryBytes == 0L) {
                return;
            }
            long unused = bootstrapMemoryBytes;
            bootstrapMemoryBytes = 0L;
            childOwnedMemoryBytes -= unused;
            resources.releaseRetainedOwnedMemory(unused);
        }

        private static void requireInitializationInputRequest(short opcode)
                throws DocumentFailure {
            if (opcode != WorkerProtocol.INITIALIZE) {
                throw WorkerCodecIO.workerFailure(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "The Worker input request is inapplicable.");
            }
        }

        private void serviceSourceRequest(
                WorkerMessages.SourceRequest sourceRequest)
                throws DocumentFailure {
            String name = sourceRequest.getName();
            if (nextDeferredSource >= deferredSourceNames.size()) {
                throw WorkerCodecIO.workerFailure(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "The Worker Source request is out of order.");
            }
            String canonicalName = deferredSourceNames.get(nextDeferredSource);
            if (!canonicalName.equals(name)) {
                throw WorkerCodecIO.workerFailure(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "The Worker Source request is out of order.");
            }
            DocumentSource source = request.getSources().get(canonicalName);
            if (source == null) {
                throw WorkerCodecIO.workerFailure(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "The Worker Source request is unavailable.");
            }
            Path path = PdfBoxWorkflowEngine.snapshotForWorker(
                    source,
                    resources,
                    sourceRequest.getTemporaryStorageAllowance());
            materializedSourceNames.put(canonicalName, canonicalName);
            nextDeferredSource++;
            byte[] responsePayload = WorkerMessages.encodeSourceResponse(
                    canonicalName,
                    path,
                    resources,
                    maximumValueBytes);
            sendInputResponse(
                    WorkerProtocol.SOURCE_MATERIALIZED,
                    responsePayload);
        }

        private void serviceCredentialRequest(
                WorkerMessages.CredentialRequest credentialRequest)
                throws DocumentFailure {
            int kind = credentialRequest.getKind();
            String sourceName = credentialRequest.getSourceName();
            PasswordCredential credential;
            if (kind == WorkerMessages.SOURCE_CREDENTIAL) {
                String canonicalName = materializedSourceNames.get(sourceName);
                if (canonicalName == null) {
                    throw WorkerCodecIO.workerFailure(
                            DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                            "The Worker Source credential is unavailable.");
                }
                if (!materializedSourceCredentialNames.add(canonicalName)) {
                    throw WorkerCodecIO.workerFailure(
                            DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                            "The Worker credential request was repeated.");
                }
                DocumentSource source = request.getSources().get(canonicalName);
                credential = source.getCredential();
                sourceName = canonicalName;
            } else {
                boolean repeated;
                if (kind == WorkerMessages.OUTPUT_OWNER_CREDENTIAL) {
                    repeated = materializedOutputOwnerCredential;
                    materializedOutputOwnerCredential = true;
                } else {
                    repeated = materializedOutputUserCredential;
                    materializedOutputUserCredential = true;
                }
                if (repeated) {
                    throw WorkerCodecIO.workerFailure(
                            DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                            "The Worker credential request was repeated.");
                }
                PdfOutputPolicy outputPolicy = request.getOutputPolicy();
                PasswordSecurityPolicy security = outputPolicy == null
                        ? null : outputPolicy.getPasswordSecurity();
                if (security == null
                        || (kind == WorkerMessages.OUTPUT_USER_CREDENTIAL
                                && !materializedOutputOwnerCredential)) {
                    throw WorkerCodecIO.workerFailure(
                            DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                            "The Worker output credential is unavailable.");
                }
                credential = kind == WorkerMessages.OUTPUT_OWNER_CREDENTIAL
                        ? security.getOwnerCredential()
                        : security.getUserCredential();
            }
            if (credential == null) {
                throw WorkerCodecIO.workerFailure(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "The Worker credential is unavailable.");
            }
            byte[] responsePayload = WorkerMessages.encodeCredentialResponse(
                    kind,
                    sourceName,
                    credential,
                    resources,
                    maximumValueBytes);
            sendInputResponse(
                    WorkerProtocol.CREDENTIAL_MATERIALIZED,
                    responsePayload);
        }

        private void serviceFontRequest(long identifier)
                throws DocumentFailure {
            try (WorkerFontSourceCache.FontValue value =
                    fontSources.readRemoteValue(identifier)) {
                byte[] responsePayload = WorkerMessages.encodeFontValue(
                        identifier,
                        value,
                        resources,
                        maximumValueBytes);
                sendInputResponse(WorkerProtocol.FONT_VALUE, responsePayload);
            } catch (DocumentFailure failure) {
                byte[] responsePayload = WorkerMessages.encodeFailure(
                        failure,
                        resources,
                        maximumMessageBytes);
                sendInputResponse(
                        WorkerProtocol.FONT_VALUE_FAILED,
                        responsePayload);
            }
        }

        private void sendInputResponse(short opcode, byte[] payload)
                throws DocumentFailure {
            try {
                grantChildMemory(
                        endpoint.requiredReceiveMemory(payload.length));
                endpoint.send(opcode, payload);
            } catch (WorkerProtocol.ProtocolException failure) {
                throw protocolFailure(failure);
            } catch (IOException | RuntimeException failure) {
                throw stoppedOrTerminated();
            } finally {
                WorkerCodecIO.clearRetained(resources, payload);
            }
        }

        private void acknowledgeProgress(WorkflowProgressPhase phase)
                throws DocumentFailure {
            byte[] payload = WorkerMessages.encodeProgress(
                    phase,
                    resources,
                    maximumMessageBytes);
            sendInputResponse(
                    WorkerProtocol.PROGRESS_ACKNOWLEDGED,
                    payload);
        }

        private CompletableFuture<WorkerProtocol.Frame> receive() {
            final CompletableFuture<WorkerProtocol.Frame> handoff =
                    new CompletableFuture<WorkerProtocol.Frame>();
            reader.execute(new Runnable() {
                @Override
                public void run() {
                    WorkerProtocol.Frame frame = null;
                    boolean handedOff = false;
                    try {
                        frame = endpoint.receive();
                        handedOff = handoff.complete(frame);
                    } catch (Throwable failure) {
                        handoff.completeExceptionally(failure);
                    } finally {
                        if (frame != null && !handedOff) {
                            frame.clear();
                        }
                    }
                }
            });
            return handoff;
        }

        private static void cancelReceive(
                CompletableFuture<WorkerProtocol.Frame> response) {
            if (response.cancel(true)) {
                return;
            }
            try {
                WorkerProtocol.Frame discarded = response.getNow(null);
                if (discarded != null) {
                    discarded.clear();
                }
            } catch (RuntimeException ignored) {
                // An exceptional completion owns no received Frame.
            }
        }

        void abortQuietly() {
            try {
                endpoint.send(WorkerProtocol.ABORT, new byte[0]);
            } catch (IOException | RuntimeException
                    | WorkerProtocol.ProtocolException ignored) {
                // Teardown below remains authoritative.
            }
        }

        private void terminateForTest() {
            process.destroyForcibly();
        }

        void requireExited() throws DocumentFailure {
            long waitUntil = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(1L);
            while (process.isAlive()) {
                try {
                    if (process.waitFor(
                            RESPONSE_POLL_MILLIS,
                            TimeUnit.MILLISECONDS)) {
                        break;
                    }
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    process.destroyForcibly();
                    throw WorkerCodecIO.workerFailure(
                            DocumentFailureCode.WORKER_TERMINATED,
                            "The Worker termination wait was interrupted.");
                }
                resources.checkpoint();
                if (System.nanoTime() - waitUntil >= 0L) {
                    process.destroyForcibly();
                    throw WorkerCodecIO.workerFailure(
                            DocumentFailureCode.WORKER_TERMINATED,
                            "The Worker did not terminate after completing its response.");
                }
            }
            if (process.exitValue() != 0) {
                throw stoppedOrTerminated();
            }
            if (!stopReason.compareAndSet(null, StopReason.COMPLETED)) {
                throw stoppedOrTerminated();
            }
        }

        private void stop(StopReason reason) {
            if (stopReason.compareAndSet(null, reason)) {
                process.destroyForcibly();
            }
        }

        private DocumentFailure stoppedOrTerminated() {
            StopReason reason = stopReason.get();
            if (reason == StopReason.CANCELLED) {
                return resources.executionFailure(
                        DocumentFailureCode.WORKFLOW_CANCELLED,
                        "The workflow was cancelled.");
            }
            if (reason == StopReason.ELAPSED) {
                return resources.policyFailure(
                        DocumentFailureCode.ELAPSED_TIME_LIMIT_EXCEEDED,
                        "The workflow elapsed-time limit was exceeded.");
            }
            return terminalIfWorkerFailure(
                    WorkerCodecIO.workerFailure(
                            DocumentFailureCode.WORKER_TERMINATED,
                            "The Worker terminated without a valid response."));
        }

        private DocumentFailure protocolFailure(
                WorkerProtocol.ProtocolException failure) {
            if (failure.getDocumentFailure() != null) {
                return failure.getDocumentFailure();
            }
            return terminalIfWorkerFailure(
                    WorkerCodecIO.workerFailure(
                            failure.getCode(),
                            failure.getMessage()));
        }

        private DocumentFailure terminalIfWorkerFailure(
                DocumentFailure failure) {
            switch (failure.getCode()) {
                case WORKFLOW_INPUT_LIMIT_EXCEEDED:
                case PAGE_LIMIT_EXCEEDED:
                case OBJECT_LIMIT_EXCEEDED:
                case NESTING_LIMIT_EXCEEDED:
                case DECOMPRESSION_LIMIT_EXCEEDED:
                case PIXEL_LIMIT_EXCEEDED:
                case MEMORY_LIMIT_EXCEEDED:
                case TEMPORARY_STORAGE_LIMIT_EXCEEDED:
                case TEMPORARY_STORAGE_UNAVAILABLE:
                case ELAPSED_TIME_LIMIT_EXCEEDED:
                case WORKFLOW_CANCELLED:
                case DEADLINE_EXCEEDED:
                case WORKER_UNAVAILABLE:
                case WORKER_AUTHENTICATION_FAILED:
                case WORKER_PROTOCOL_REJECTED:
                case WORKER_MESSAGE_LIMIT_EXCEEDED:
                case WORKER_TERMINATED:
                    return resources.terminalFailure(failure);
                default:
                    return failure;
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            watchdog.shutdownNow();
            closeQuietly(process.getOutputStream());
            if (process.isAlive()) {
                terminateAndAwait(process);
            }
            closeQuietly(process.getInputStream());
            closeQuietly(process.getErrorStream());
            shutdownAndAwait(reader);
            endpoint.close();
            if (childTemporaryBytes != 0L) {
                resources.releaseTemporaryBytes(childTemporaryBytes);
                childTemporaryBytes = 0L;
            }
            long remainingChildMemory = childOwnedMemoryBytes;
            childOwnedMemoryBytes = 0L;
            bootstrapMemoryBytes = 0L;
            reportedChildMemoryBytes = 0L;
            if (remainingChildMemory != 0L) {
                resources.releaseRetainedOwnedMemory(
                        remainingChildMemory);
            }
        }

        private static void shutdownAndAwait(ExecutorService executor) {
            executor.shutdownNow();
            boolean interrupted = false;
            while (!executor.isTerminated()) {
                try {
                    executor.awaitTermination(1L, TimeUnit.SECONDS);
                } catch (InterruptedException failure) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        private static void terminateAndAwait(Process process) {
            process.destroyForcibly();
            boolean interrupted = false;
            while (process.isAlive()) {
                try {
                    process.waitFor();
                } catch (InterruptedException failure) {
                    interrupted = true;
                    process.destroyForcibly();
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        private static void startErrorDrainer(final InputStream error) {
            Thread thread = daemonThreadFactory(
                    "folio-pdf-worker-stderr").newThread(() -> {
                        byte[] buffer = new byte[4096];
                        try {
                            while (error.read(buffer) >= 0) {
                                Arrays.fill(buffer, (byte) 0);
                            }
                        } catch (IOException ignored) {
                            // Process teardown owns diagnostics and cleanup.
                        } finally {
                            Arrays.fill(buffer, (byte) 0);
                        }
                    });
            thread.start();
        }
    }

    private static ThreadFactory daemonThreadFactory(final String name) {
        return new ThreadFactory() {
            @Override
            public Thread newThread(Runnable task) {
                Thread thread = new Thread(task, name);
                thread.setDaemon(true);
                return thread;
            }
        };
    }

    static String workerClassPath() throws DocumentFailure {
        Set<Path> entries = new LinkedHashSet<Path>();
        addProjectCodeSource(
                entries,
                HardenedWorkerMain.class,
                DOCUMENT_CLASS_INVENTORY,
                DOCUMENT_CLASS_INVENTORY_SHA256,
                "pdf-document");
        addProjectCodeSource(
                entries,
                ProviderSelection.class,
                PROVIDER_CLASS_INVENTORY,
                PROVIDER_CLASS_INVENTORY_SHA256,
                "pdf-provider-contract");
        addDependencyCodeSource(entries, PDDocument.class, PDFBOX_SHA256);
        addDependencyCodeSource(
                entries,
                RandomAccessRead.class,
                PDFBOX_IO_SHA256);
        addDependencyCodeSource(entries, TrueTypeFont.class, FONTBOX_SHA256);
        addDependencyCodeSource(entries, Log.class, COMMONS_LOGGING_SHA256);
        addOptionalTiffCodeSources(entries);
        StringBuilder result = new StringBuilder();
        for (Path path : entries) {
            if (result.length() > 0) {
                result.append(File.pathSeparatorChar);
            }
            result.append(path);
        }
        return result.toString();
    }

    static void requireLiteralClassPathEntry(Path path)
            throws DocumentFailure {
        String value = path.toString();
        if (value.isEmpty()
                || value.indexOf(File.pathSeparatorChar) >= 0
                || value.indexOf('*') >= 0) {
            throw unavailable();
        }
    }

    private static void addProjectCodeSource(
            Set<Path> entries,
            Class<?> type,
            String inventoryName,
            String inventorySha256,
            String artifactId) throws DocumentFailure {
        Path path = codeSource(type);
        requireProjectOnlyCodeSource(
                path,
                inventoryName,
                inventorySha256,
                artifactId);
        requireLiteralClassPathEntry(path);
        entries.add(path);
    }

    private static void addDependencyCodeSource(
            Set<Path> entries,
            Class<?> type,
            String expectedSha256) throws DocumentFailure {
        Path path = codeSource(type);
        requireExactDependencyCodeSource(path, expectedSha256);
        requireLiteralClassPathEntry(path);
        entries.add(path);
    }

    private static Path codeSource(Class<?> type) throws DocumentFailure {
        try {
            if (type.getProtectionDomain() == null
                    || type.getProtectionDomain().getCodeSource() == null
                    || type.getProtectionDomain().getCodeSource()
                            .getLocation() == null
                    || !"file".equals(type.getProtectionDomain()
                            .getCodeSource().getLocation().getProtocol())) {
                throw unavailable();
            }
            Path path = Paths.get(type.getProtectionDomain()
                    .getCodeSource().getLocation().toURI())
                    .toRealPath();
            if (!Files.isReadable(path)) {
                throw unavailable();
            }
            return path;
        } catch (URISyntaxException | IOException | RuntimeException failure) {
            throw unavailable();
        }
    }

    static void requireProjectOnlyCodeSource(Path path)
            throws DocumentFailure {
        requireProjectOnlyCodeSource(
                path,
                DOCUMENT_CLASS_INVENTORY,
                DOCUMENT_CLASS_INVENTORY_SHA256,
                "pdf-document");
    }

    static void requirePdfBoxCodeSource(Path path) throws DocumentFailure {
        requireExactDependencyCodeSource(path, PDFBOX_SHA256);
    }

    private static void requireExactDependencyCodeSource(
            Path path,
            String expectedSha256) throws DocumentFailure {
        try {
            if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)
                    || !expectedSha256.equals(sha256(path))) {
                throw unavailable();
            }
        } catch (IOException | RuntimeException failure) {
            throw unavailable();
        }
    }

    private static void requireProjectOnlyCodeSource(
            Path path,
            String inventoryName,
            String inventorySha256,
            String artifactId) throws DocumentFailure {
        try {
            if (Files.isDirectory(path)) {
                Path inventoryPath = path.resolve(inventoryName);
                long inventoryLength = Files.size(inventoryPath);
                Set<String> expectedClasses = readClassInventory(
                        Files.newInputStream(inventoryPath),
                        inventoryLength,
                        inventorySha256);
                requireProjectOnlyDirectory(
                        path,
                        expectedClasses,
                        inventoryName,
                        artifactId);
                return;
            }
            if (!Files.isRegularFile(path)) {
                throw unavailable();
            }
            try (JarFile archive = new JarFile(path.toFile())) {
                requireNoManifestClassPath(archive);
                JarEntry inventory = archive.getJarEntry(inventoryName);
                if (inventory == null || inventory.isDirectory()) {
                    throw unavailable();
                }
                long inventoryLength = inventory.getSize();
                Set<String> expectedClasses = readClassInventory(
                        archive.getInputStream(inventory),
                        inventoryLength,
                        inventorySha256);
                Set<String> seenEntries = new LinkedHashSet<String>();
                java.util.Enumeration<JarEntry> entries = archive.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.isDirectory()) {
                        requireSafeDirectoryEntry(entry.getName());
                    } else {
                        requireProjectOnlyEntry(
                                entry.getName(),
                                expectedClasses,
                                seenEntries,
                                inventoryName,
                                artifactId);
                    }
                }
                requireNoMissingClasses(expectedClasses);
            }
        } catch (IOException | RuntimeException failure) {
            throw unavailable();
        }
    }

    private static void requireProjectOnlyDirectory(
            Path root,
            Set<String> expectedClasses,
            String inventoryName,
            String artifactId)
            throws IOException, DocumentFailure {
        Set<String> seenEntries = new LinkedHashSet<String>();
        try (Stream<Path> paths = Files.walk(root)) {
            java.util.Iterator<Path> entries = paths.iterator();
            while (entries.hasNext()) {
                Path entry = entries.next();
                if (entry.equals(root)) {
                    continue;
                }
                if (Files.isSymbolicLink(entry)) {
                    throw unavailable();
                }
                if (Files.isDirectory(entry)) {
                    continue;
                }
                if (!Files.isRegularFile(entry)) {
                    throw unavailable();
                }
                requireProjectOnlyEntry(
                        root.relativize(entry).toString()
                                .replace(File.separatorChar, '/'),
                        expectedClasses,
                        seenEntries,
                        inventoryName,
                        artifactId);
            }
        }
        requireNoMissingClasses(expectedClasses);
    }

    private static void requireProjectOnlyEntry(
            String name,
            Set<String> expectedClasses,
            Set<String> seenEntries,
            String inventoryName,
            String artifactId)
            throws DocumentFailure {
        if (!isSafeArchiveName(name) || !seenEntries.add(name)) {
            throw unavailable();
        }
        if (name.endsWith(".class")) {
            if (!expectedClasses.remove(name)) {
                throw unavailable();
            }
            return;
        }
        String mavenRoot = "META-INF/maven/net.zerocloud/" + artifactId + "/";
        if (!name.equals(inventoryName)
                && !name.equals("META-INF/LICENSE")
                && !name.equals("META-INF/NOTICE")
                && !name.equals("META-INF/MANIFEST.MF")
                && !name.equals(mavenRoot + "pom.xml")
                && !name.equals(mavenRoot + "pom.properties")) {
            throw unavailable();
        }
    }

    private static void requireNoMissingClasses(Set<String> expectedClasses)
            throws DocumentFailure {
        if (!expectedClasses.isEmpty()) {
            throw unavailable();
        }
    }

    private static void requireSafeDirectoryEntry(String name)
            throws DocumentFailure {
        if (name.isEmpty() || name.charAt(name.length() - 1) != '/'
                || !isSafeArchiveName(
                        name.substring(0, name.length() - 1))) {
            throw unavailable();
        }
    }

    private static void requireNoManifestClassPath(JarFile archive)
            throws IOException, DocumentFailure {
        if (archive.getManifest() != null
                && archive.getManifest().getMainAttributes().getValue(
                        Attributes.Name.CLASS_PATH) != null) {
            throw unavailable();
        }
    }

    private static Set<String> readClassInventory(
            InputStream input,
            long length,
            String expectedSha256) throws IOException, DocumentFailure {
        try (InputStream value = input) {
            if (length <= 0L || length > MAXIMUM_CLASS_INVENTORY_BYTES) {
                throw unavailable();
            }
            byte[] bytes = new byte[(int) length];
            int offset = 0;
            while (offset < bytes.length) {
                int count = value.read(bytes, offset, bytes.length - offset);
                if (count < 0) {
                    throw unavailable();
                }
                if (count == 0) {
                    int octet = value.read();
                    if (octet < 0) {
                        throw unavailable();
                    }
                    bytes[offset++] = (byte) octet;
                } else {
                    offset += count;
                }
            }
            if (value.read() >= 0 || !expectedSha256.equals(sha256(bytes))) {
                throw unavailable();
            }
            return parseClassInventory(bytes);
        }
    }

    private static Set<String> parseClassInventory(byte[] bytes)
            throws DocumentFailure {
        for (byte value : bytes) {
            int character = value & 0xff;
            if (character != '\n'
                    && (character < 0x20 || character > 0x7e)) {
                throw unavailable();
            }
        }
        String[] lines = new String(bytes, StandardCharsets.US_ASCII)
                .split("\\n", -1);
        if (lines.length < 2 || !lines[lines.length - 1].isEmpty()) {
            throw unavailable();
        }
        Set<String> entries = new LinkedHashSet<String>();
        for (int index = 0; index < lines.length - 1; index++) {
            String name = lines[index];
            if (!name.endsWith(".class") || !isSafeArchiveName(name)
                    || !entries.add(name)) {
                throw unavailable();
            }
        }
        return entries;
    }

    private static String sha256(byte[] value) throws DocumentFailure {
        return hexadecimal(newSha256().digest(value));
    }

    private static String sha256(Path path)
            throws IOException, DocumentFailure {
        MessageDigest digest = newSha256();
        byte[] buffer = new byte[8192];
        try (InputStream input = Files.newInputStream(path)) {
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count > 0) {
                    digest.update(buffer, 0, count);
                }
            }
        } finally {
            Arrays.fill(buffer, (byte) 0);
        }
        return hexadecimal(digest.digest());
    }

    private static MessageDigest newSha256() throws DocumentFailure {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw unavailable();
        }
    }

    private static String hexadecimal(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte octet : value) {
            int unsigned = octet & 0xff;
            result.append(Character.forDigit(unsigned >>> 4, 16));
            result.append(Character.forDigit(unsigned & 0x0f, 16));
        }
        return result.toString();
    }

    private static boolean isSafeArchiveName(String name) {
        if (name.isEmpty() || name.charAt(0) == '/'
                || name.indexOf('\\') >= 0) {
            return false;
        }
        String[] segments = name.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment)
                    || "..".equals(segment)) {
                return false;
            }
        }
        return true;
    }

    private static void addOptionalTiffCodeSources(Set<Path> entries)
            throws DocumentFailure {
        ClassLoader loader = HardenedWorkerEngine.class.getClassLoader();
        Class<?> provider;
        try {
            provider = Class.forName(
                    "com.twelvemonkeys.imageio.plugins.tiff.TIFFImageReaderSpi",
                    false,
                    loader);
        } catch (ClassNotFoundException absent) {
            return;
        } catch (LinkageError | RuntimeException failure) {
            throw unavailable();
        }
        addDependencyCodeSource(entries, provider, TIFF_SHA256);
        String[] dependencies = {
            "com.twelvemonkeys.imageio.ImageReaderBase",
            "com.twelvemonkeys.imageio.metadata.Directory",
            "com.twelvemonkeys.lang.Validate",
            "com.twelvemonkeys.io.LittleEndianDataInputStream",
            "com.twelvemonkeys.image.ResampleOp"
        };
        String[] digests = {
            IMAGEIO_CORE_SHA256,
            IMAGEIO_METADATA_SHA256,
            COMMON_LANG_SHA256,
            COMMON_IO_SHA256,
            COMMON_IMAGE_SHA256
        };
        for (int index = 0; index < dependencies.length; index++) {
            try {
                addDependencyCodeSource(
                        entries,
                        Class.forName(dependencies[index], false, loader),
                        digests[index]);
            } catch (ClassNotFoundException | LinkageError
                    | RuntimeException failure) {
                throw unavailable();
            }
        }
    }

    private static long ceilingSeconds(Duration duration) {
        long seconds = duration.getSeconds();
        if (duration.getNano() > 0 && seconds < Long.MAX_VALUE) {
            seconds++;
        }
        return Math.max(1L, seconds);
    }

    private static boolean isWindows() {
        return File.separatorChar == '\\';
    }

    private static boolean isLinux() {
        return "Linux".equals(System.getProperty("os.name"));
    }

    static boolean requiresSecurityManagerAllowOption() {
        String version = System.getProperty(
                "java.specification.version",
                "");
        String major = version.startsWith("1.")
                ? version.substring(2)
                : version;
        int separator = major.indexOf('.');
        if (separator >= 0) {
            major = major.substring(0, separator);
        }
        try {
            return Integer.parseInt(major) >= 17;
        } catch (NumberFormatException failure) {
            return false;
        }
    }

    private static DocumentFailure unavailable() {
        return WorkerCodecIO.workerFailure(
                DocumentFailureCode.WORKER_UNAVAILABLE,
                "The supported local Worker launcher is unavailable.");
    }

    private static void closeQuietly(java.io.Closeable value) {
        try {
            value.close();
        } catch (IOException ignored) {
            // Teardown must not replace the primary outcome.
        }
    }
}
