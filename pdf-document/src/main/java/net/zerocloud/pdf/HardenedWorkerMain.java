package net.zerocloud.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Dedicated JVM entry point. It is not a supported application entry point. */
final class HardenedWorkerMain {

    static final String STDERR_PROBE_MARKER =
            "folio-pdf-worker-stderr-probe";

    private HardenedWorkerMain() {
    }

    /** Runs one authenticated Worker transaction. */
    @SuppressWarnings("removal")
    public static void main(String[] arguments) {
        if (arguments.length != 3) {
            return;
        }
        java.nio.file.Path root;
        int maximumMessageBytes;
        long maximumOwnedMemoryBytes;
        try {
            root = Paths.get(arguments[0]).toAbsolutePath().normalize();
            maximumMessageBytes = Integer.parseInt(arguments[1]);
            maximumOwnedMemoryBytes = Long.parseLong(arguments[2]);
            if (!Files.isDirectory(root)
                    || maximumMessageBytes < 0
                    || maximumOwnedMemoryBytes < 0L) {
                return;
            }
        } catch (RuntimeException failure) {
            return;
        }

        byte[] key = null;
        WorkerProtocol.Endpoint endpoint = null;
        WorkerMessages.DecodedInitialization initialization = null;
        WorkerInputResolver inputResolver = null;
        final AtomicReference<WorkflowResourceContext> workerResources =
                new AtomicReference<WorkflowResourceContext>();
        try {
            key = WorkerProtocol.readAuthenticationKey(System.in);
            endpoint = WorkerProtocol.endpoint(
                    System.in,
                    System.out,
                    key,
                    maximumMessageBytes,
                    maximumOwnedMemoryBytes);
            WorkerProtocol.Frame frame = endpoint.receive();
            try {
                if (frame.getOpcode() != WorkerProtocol.INITIALIZE) {
                    throw WorkerCodecIO.workerFailure(
                            DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                            "The Worker initialization opcode is unsupported.");
                }
                initialization = WorkerMessages.decodeInitialization(
                        frame.getPayload(),
                        root,
                        maximumOwnedMemoryBytes);
            } finally {
                frame.clear();
            }
            if (initialization.getMaximumMessageBytes()
                    != maximumMessageBytes) {
                throw WorkerCodecIO.workerFailure(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "The Worker message limit does not match its launch boundary.");
            }
            if (initialization.getPolicy().getMaximumOwnedMemoryBytes()
                    != maximumOwnedMemoryBytes) {
                throw WorkerCodecIO.workerFailure(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "The Worker owned-memory grant does not match its launch boundary.");
            }

            WorkflowResourceContext.OwnedMemoryAuthority memoryAuthority =
                    new ParentMemoryAuthority(
                            endpoint,
                            maximumOwnedMemoryBytes);
            endpoint.acceptMemoryGrantsWith(
                    (ParentMemoryAuthority) memoryAuthority);
            WorkflowEnvironment environment = WorkflowEnvironment.builder()
                    .temporaryDirectory(root)
                    .defaultResourcePolicy(initialization.getPolicy())
                    .referenceFontSet(initialization.getReferenceFontSet())
                    .ownedMemoryAuthority(memoryAuthority)
                    .build();
            java.lang.management.ManagementFactory
                    .getRuntimeMXBean()
                    .getName();
            System.setSecurityManager(new HardenedWorkerSecurityManager(root));

            final WorkerProtocol.Endpoint workerEndpoint = endpoint;
            final int messageLimit = maximumMessageBytes;
            WorkflowRequest workerRequest = initialization.getRequest()
                    .withProgressListener(phase -> {
                        if (phase == WorkflowProgressPhase.STAGED
                                || phase == WorkflowProgressPhase.VALIDATED) {
                            sendProgress(
                                    workerEndpoint,
                                    phase,
                                    workerResources.get(),
                                    messageLimit);
                        }
                    });
            inputResolver = new WorkerInputResolver(
                    workerEndpoint,
                    root,
                    workerResources,
                    messageLimit,
                    initialization);
            initialization = null;
            final WorkerInputResolver activeInputResolver = inputResolver;
            WorkflowOutcome<Void> outcome = new DocumentWorkflow(environment)
                    .executeWithInputResolver(
                            workerRequest,
                            session -> {
                                return runRequests(
                                        workerEndpoint,
                                        session,
                                        messageLimit,
                                        root);
                            },
                            activeInputResolver);
            byte[] finished = WorkerMessages.encodeFinished(
                    outcome,
                    maximumMessageBytes);
            try {
                endpoint.send(WorkerProtocol.FINISHED, finished);
            } finally {
                Arrays.fill(finished, (byte) 0);
            }
        } catch (WorkerAbort ignored) {
            // The authenticated parent abandoned the transaction.
        } catch (WorkerProgressFailure failure) {
            sendFailure(
                    endpoint,
                    failure.getDocumentFailure(),
                    workerResources.get(),
                    maximumMessageBytes);
        } catch (DocumentFailure failure) {
            sendFailure(
                    endpoint,
                    failure,
                    workerResources.get(),
                    maximumMessageBytes);
        } catch (WorkerProtocol.ProtocolException failure) {
            // A frame that cannot be authenticated receives no oracle response.
        } catch (IOException failure) {
            // A disconnected parent owns teardown.
        } catch (RuntimeException | Error failure) {
            sendFailure(
                    endpoint,
                    WorkerCodecIO.workerFailure(
                            DocumentFailureCode.WORKER_TERMINATED,
                            "The Worker could not complete the transaction."),
                    workerResources.get(),
                    maximumMessageBytes);
        } finally {
            if (inputResolver != null) {
                inputResolver.close();
            }
            if (initialization != null) {
                initialization.close();
            }
            if (endpoint != null) {
                endpoint.close();
            }
            if (key != null) {
                Arrays.fill(key, (byte) 0);
            }
        }
    }

    private static final class ParentMemoryAuthority
            implements WorkflowResourceContext.OwnedMemoryAuthority,
            WorkerProtocol.MemoryGrantConsumer {

        private final WorkerProtocol.Endpoint endpoint;
        private final long maximumOwnedMemoryBytes;
        private long pendingProtocolPayloadGrant;

        private ParentMemoryAuthority(
                WorkerProtocol.Endpoint endpoint,
                long maximumOwnedMemoryBytes) {
            this.endpoint = endpoint;
            this.maximumOwnedMemoryBytes = maximumOwnedMemoryBytes;
        }

        @Override
        public synchronized void reserve(long amount)
                throws DocumentFailure {
            byte[] payload = WorkerMessages.encodeMemoryAmount(amount);
            try {
                endpoint.send(WorkerProtocol.MEMORY_RESERVE, payload);
                endpoint.receiveMemoryGrant(amount);
            } catch (WorkerProtocol.ProtocolException failure) {
                if (failure.getDocumentFailure() != null) {
                    throw failure.getDocumentFailure();
                }
                throw WorkerCodecIO.workerFailure(
                        failure.getCode(), failure.getMessage());
            } catch (IOException failure) {
                throw WorkerCodecIO.workerFailure(
                        DocumentFailureCode.WORKER_TERMINATED,
                        "The Worker could not complete the transaction.");
            } finally {
                Arrays.fill(payload, (byte) 0);
            }
        }

        @Override
        public synchronized void reserveProtocolPayload(long amount)
                throws DocumentFailure {
            if (amount != pendingProtocolPayloadGrant) {
                throw WorkerCodecIO.workerFailure(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "The Worker receive-memory grant is unavailable.");
            }
            pendingProtocolPayloadGrant = 0L;
        }

        @Override
        public synchronized void acceptMemoryGrant(long amount)
                throws DocumentFailure {
            if (pendingProtocolPayloadGrant != 0L
                    || amount > maximumOwnedMemoryBytes) {
                throw WorkerCodecIO.workerFailure(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "The Worker receive-memory grant is inapplicable.");
            }
            pendingProtocolPayloadGrant = amount;
        }

        @Override
        public synchronized void requireMemoryGrantConsumed()
                throws DocumentFailure {
            if (pendingProtocolPayloadGrant != 0L) {
                throw WorkerCodecIO.workerFailure(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "The Worker receive-memory grant is inapplicable.");
            }
        }

        @Override
        public void release(long amount) {
            byte[] payload = WorkerMessages.encodeMemoryAmount(amount);
            try {
                endpoint.send(WorkerProtocol.MEMORY_RELEASE, payload);
            } catch (IOException | WorkerProtocol.ProtocolException failure) {
                throw new MemoryAuthorityFailure();
            } finally {
                Arrays.fill(payload, (byte) 0);
            }
        }
    }

    private static final class MemoryAuthorityFailure
            extends RuntimeException {

        private static final long serialVersionUID = 1L;
    }

    private static final class WorkerInputResolver
            implements PdfBoxWorkflowEngine.InputResolver, AutoCloseable {

        private final WorkerProtocol.Endpoint endpoint;
        private final java.nio.file.Path root;
        private final AtomicReference<WorkflowResourceContext> resourceHolder;
        private final int maximumMessageBytes;
        private final long initializationMemoryBytes;
        private WorkerMessages.DecodedInitialization initialization;
        private final Map<String, PasswordCredential> sourceCredentials =
                new HashMap<String, PasswordCredential>();
        private final List<WorkerMessages.DecodedCredential> credentials =
                new java.util.ArrayList<WorkerMessages.DecodedCredential>();
        private PasswordSecurityPolicy outputSecurity;
        private WorkflowResourceContext.MemoryReservation
                initializationMemory;

        private WorkerInputResolver(
                WorkerProtocol.Endpoint endpoint,
                java.nio.file.Path root,
                AtomicReference<WorkflowResourceContext> resourceHolder,
                int maximumMessageBytes,
                WorkerMessages.DecodedInitialization initialization) {
            this.endpoint = endpoint;
            this.root = root;
            this.resourceHolder = resourceHolder;
            this.maximumMessageBytes = maximumMessageBytes;
            this.initialization = initialization;
            this.initializationMemoryBytes =
                    initialization.getRetainedOwnedMemoryBytes();
        }

        @Override
        public DocumentSource resolveSource(
                String name,
                DocumentSource source,
                WorkflowResourceContext resources) throws DocumentFailure {
            activate(resources);
            if (source.getKind() == DocumentSource.Kind.PATH
                    && Files.isRegularFile(source.getPath())) {
                return source;
            }
            byte[] request = WorkerMessages.encodeSourceRequest(
                    name,
                    resources.getRemainingTemporaryStorageBytes(),
                    resources,
                    maximumMessageBytes);
            WorkerProtocol.Frame response = exchange(
                    WorkerProtocol.SOURCE_REQUIRED,
                    request,
                    WorkerProtocol.SOURCE_MATERIALIZED,
                    resources);
            try {
                java.nio.file.Path path = WorkerMessages.decodeSourceResponse(
                        response.getPayload(),
                        name,
                        root,
                        resources);
                resources.registerTemporaryFile(path);
                DocumentSource resolved = DocumentSource.workflowSnapshot(
                        path);
                return source.getCredential() == null
                        ? resolved
                        : resolved.withCredential(source.getCredential());
            } finally {
                response.clear();
            }
        }

        @Override
        public PasswordCredential resolveSourceCredential(
                String sourceName,
                PasswordCredential credential,
                WorkflowResourceContext resources) throws DocumentFailure {
            activate(resources);
            if (credential == null) {
                return null;
            }
            PasswordCredential existing = sourceCredentials.get(sourceName);
            if (existing != null) {
                return existing;
            }
            PasswordCredential resolved = requestCredential(
                    WorkerMessages.SOURCE_CREDENTIAL,
                    sourceName,
                    resources);
            sourceCredentials.put(sourceName, resolved);
            return resolved;
        }

        @Override
        public PasswordSecurityPolicy resolveOutputSecurity(
                PasswordSecurityPolicy descriptor,
                WorkflowResourceContext resources) throws DocumentFailure {
            activate(resources);
            if (outputSecurity != null) {
                return outputSecurity;
            }
            PasswordCredential owner = requestCredential(
                    WorkerMessages.OUTPUT_OWNER_CREDENTIAL,
                    null,
                    resources);
            PasswordCredential user = requestCredential(
                    WorkerMessages.OUTPUT_USER_CREDENTIAL,
                    null,
                    resources);
            outputSecurity = PasswordSecurityPolicy.builder(owner, user)
                    .algorithm(descriptor.getAlgorithm())
                    .encryptionScope(descriptor.getEncryptionScope())
                    .permissions(descriptor.getPermissions())
                    .build();
            return outputSecurity;
        }

        private PasswordCredential requestCredential(
                int kind,
                String sourceName,
                WorkflowResourceContext resources) throws DocumentFailure {
            byte[] request = WorkerMessages.encodeCredentialRequest(
                    kind,
                    sourceName,
                    resources,
                    maximumMessageBytes);
            WorkerProtocol.Frame response = exchange(
                    WorkerProtocol.CREDENTIAL_REQUIRED,
                    request,
                    WorkerProtocol.CREDENTIAL_MATERIALIZED,
                    resources);
            try {
                WorkerMessages.DecodedCredential decoded =
                        WorkerMessages.decodeCredentialResponse(
                                response.getPayload(),
                                kind,
                                sourceName,
                                resources);
                try {
                    credentials.add(decoded);
                    return decoded.getCredential();
                } catch (RuntimeException | Error failure) {
                    decoded.close();
                    throw failure;
                }
            } finally {
                response.clear();
            }
        }

        private WorkerProtocol.Frame exchange(
                short requestOpcode,
                byte[] request,
                short expectedResponseOpcode,
                WorkflowResourceContext resources) throws DocumentFailure {
            try {
                endpoint.send(requestOpcode, request);
            } catch (WorkerProtocol.ProtocolException failure) {
                throw WorkerCodecIO.workerFailure(
                        failure.getCode(),
                        failure.getMessage());
            } catch (IOException failure) {
                throw new WorkerAbort();
            } finally {
                WorkerCodecIO.clearRetained(resources, request);
            }
            WorkerProtocol.Frame response = receiveApplicationFrame(endpoint);
            if (response.getOpcode() != expectedResponseOpcode) {
                response.clear();
                throw WorkerCodecIO.workerFailure(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "The Worker input response opcode is unsupported.");
            }
            return response;
        }

        @Override
        public void activate(WorkflowResourceContext resources)
                throws DocumentFailure {
            WorkflowResourceContext existing = resourceHolder.get();
            if (existing == null) {
                initializationMemory = resources.reserveOwnedMemory(
                        initializationMemoryBytes);
                resourceHolder.set(resources);
                endpoint.accountWith(resources);
            } else if (existing != resources) {
                throw new IllegalStateException(
                        "Worker input resources changed during execution.");
            }
        }

        @Override
        public void close() {
            for (WorkerMessages.DecodedCredential credential : credentials) {
                credential.close();
            }
            credentials.clear();
            sourceCredentials.clear();
            outputSecurity = null;
            if (initialization != null) {
                initialization.close();
                initialization = null;
            }
            if (initializationMemory != null) {
                initializationMemory.close();
                initializationMemory = null;
            }
            resourceHolder.set(null);
        }
    }

    private static final class WorkerRemoteFonts
            implements WorkerCompositionCodec.RemoteFontSource,
            AutoCloseable {

        private final WorkerProtocol.Endpoint endpoint;
        private final WorkflowResourceContext resources;
        private final int maximumMessageBytes;
        private final java.util.Set<Long> opened =
                new java.util.HashSet<Long>();
        private final List<RemoteFontInputStream> streams =
                new java.util.ArrayList<RemoteFontInputStream>();

        private WorkerRemoteFonts(
                WorkerProtocol.Endpoint endpoint,
                WorkflowResourceContext resources,
                int maximumMessageBytes) {
            this.endpoint = endpoint;
            this.resources = resources;
            this.maximumMessageBytes = maximumMessageBytes;
        }

        @Override
        public InputStream open(long identifier) throws DocumentFailure {
            if (!opened.add(Long.valueOf(identifier))) {
                throw WorkerCodecIO.workerFailure(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "A Worker Font Source identifier was repeated.");
            }
            RemoteFontInputStream stream = new RemoteFontInputStream(
                    endpoint,
                    resources,
                    maximumMessageBytes,
                    identifier);
            streams.add(stream);
            return stream;
        }

        @Override
        public void close() {
            for (RemoteFontInputStream stream : streams) {
                stream.close();
            }
            streams.clear();
            opened.clear();
        }
    }

    private static final class RemoteFontInputStream extends InputStream {

        private final WorkerProtocol.Endpoint endpoint;
        private final WorkflowResourceContext resources;
        private final int maximumMessageBytes;
        private final long identifier;
        private final byte[] singleByte = new byte[1];
        private WorkflowResourceContext.OwnedBytes value;
        private int position;
        private boolean ended;

        private RemoteFontInputStream(
                WorkerProtocol.Endpoint endpoint,
                WorkflowResourceContext resources,
                int maximumMessageBytes,
                long identifier) {
            this.endpoint = endpoint;
            this.resources = resources;
            this.maximumMessageBytes = maximumMessageBytes;
            this.identifier = identifier;
        }

        @Override
        public int read() throws IOException {
            int count = read(singleByte, 0, 1);
            return count < 0 ? -1 : singleByte[0] & 0xff;
        }

        @Override
        public int read(byte[] target, int offset, int length)
                throws IOException {
            if (target == null) {
                throw new NullPointerException("target");
            }
            if (offset < 0 || length < 0
                    || offset > target.length - length) {
                throw new IndexOutOfBoundsException();
            }
            if (length == 0) {
                return 0;
            }
            if (ended) {
                return -1;
            }
            if (value == null) {
                materialize();
            }
            byte[] bytes = value.getBytes();
            int count = Math.min(length, bytes.length - position);
            if (count == 0) {
                close();
                return -1;
            }
            System.arraycopy(bytes, position, target, offset, count);
            position += count;
            if (position == bytes.length) {
                close();
            }
            return count;
        }

        @Override
        public void close() {
            ended = true;
            if (value != null) {
                Arrays.fill(value.getBytes(), (byte) 0);
                value.close();
                value = null;
            }
        }

        private void materialize() throws IOException {
            byte[] request;
            try {
                request = WorkerMessages.encodeFontRequest(
                        identifier,
                        resources,
                        maximumMessageBytes);
            } catch (DocumentFailure failure) {
                throw WorkerCodecIO.transportedFailure(failure);
            }
            WorkerProtocol.Frame response;
            try {
                endpoint.send(WorkerProtocol.FONT_REQUIRED, request);
            } catch (WorkerProtocol.ProtocolException failure) {
                throw WorkerCodecIO.transportedFailure(
                        WorkerCodecIO.workerFailure(
                                failure.getCode(),
                                failure.getMessage()));
            } finally {
                WorkerCodecIO.clearRetained(resources, request);
            }
            try {
                response = receiveApplicationFrame(endpoint);
            } catch (DocumentFailure failure) {
                throw WorkerCodecIO.transportedFailure(failure);
            }
            try {
                if (response.getOpcode() == WorkerProtocol.FONT_VALUE_FAILED) {
                    throw WorkerCodecIO.transportedFailure(
                            WorkerMessages.decodeFailure(
                                    response.getPayload(),
                                    resources));
                }
                if (response.getOpcode() != WorkerProtocol.FONT_VALUE) {
                    throw WorkerCodecIO.transportedFailure(
                            WorkerCodecIO.workerFailure(
                                    DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                                    "The Worker font-source response opcode is unsupported."));
                }
                value = WorkerMessages.decodeFontValue(
                        response.getPayload(),
                        identifier,
                        resources);
            } catch (DocumentFailure failure) {
                throw WorkerCodecIO.transportedFailure(failure);
            } finally {
                response.clear();
            }
        }
    }

    private static Void runRequests(
            WorkerProtocol.Endpoint endpoint,
            DocumentSession session,
            int maximumMessageBytes,
            java.nio.file.Path root) throws DocumentFailure {
        WorkflowResourceContext resources = resources(session);
        endpoint.accountWith(resources);
        sendEmpty(
                endpoint,
                WorkerProtocol.READY,
                resources,
                maximumMessageBytes);
        WorkerReferenceRegistry references = WorkerReferenceRegistry.forWorker();
        WorkerValueViewCodec.WorkerRegistry valueViews =
                new WorkerValueViewCodec.WorkerRegistry(references);
        int pendingAtomicLength = -1;
        while (true) {
            WorkerProtocol.Frame frame = receiveApplicationFrame(endpoint);
            try {
                if (pendingAtomicLength >= 0) {
                    if (frame.getOpcode() != WorkerProtocol.COMMANDS
                            || frame.getPayload().length
                                    != pendingAtomicLength) {
                        throw WorkerCodecIO.workerFailure(
                                DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                                "The accepted atomic Command batch is inapplicable.");
                    }
                    pendingAtomicLength = -1;
                    executeCommands(
                            endpoint,
                            session,
                            references,
                            frame.getPayload(),
                            resources,
                            maximumMessageBytes);
                    continue;
                }
                switch (frame.getOpcode()) {
                    case WorkerProtocol.COMMANDS_OFFER:
                        pendingAtomicLength = offerAtomicCommands(
                                endpoint,
                                frame.getPayload(),
                                resources,
                                maximumMessageBytes);
                        break;
                    case WorkerProtocol.COMMAND_BATCH:
                        executeCommandBatch(
                                endpoint,
                                session,
                                references,
                                valueViews,
                                frame.getPayload(),
                                resources,
                                maximumMessageBytes);
                        break;
                    case WorkerProtocol.COMMANDS:
                        throw WorkerCodecIO.workerFailure(
                                DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                                "An atomic Worker Command batch was not accepted.");
                    case WorkerProtocol.VALUE_VIEW:
                        executeValueView(
                                endpoint,
                                valueViews,
                                frame.getPayload(),
                                resources,
                                maximumMessageBytes);
                        break;
                    case WorkerProtocol.QUERY:
                        executeQuery(
                                endpoint,
                                session,
                                references,
                                valueViews,
                                frame.getPayload(),
                                resources,
                                maximumMessageBytes);
                        break;
                    case WorkerProtocol.QUERY_PREFLIGHT:
                        executeQueryPreflight(
                                endpoint,
                                (PdfBoxDocumentSession) session,
                                frame.getPayload(),
                                resources,
                                maximumMessageBytes);
                        break;
                    case WorkerProtocol.NETWORK_PROBE:
                        executeIsolationProbe(
                                endpoint,
                                frame.getPayload(),
                                maximumMessageBytes,
                                resources,
                                root);
                        break;
                    case WorkerProtocol.FINISH:
                        if (frame.getPayload().length != 0) {
                            throw WorkerCodecIO.workerFailure(
                                    DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                                    "The Worker finish message is invalid.");
                        }
                        return null;
                    case WorkerProtocol.ABORT:
                        throw new WorkerAbort();
                    default:
                        throw WorkerCodecIO.workerFailure(
                                DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                                "The Worker request opcode is unsupported.");
                }
            } finally {
                frame.clear();
            }
        }
    }

    private static int offerAtomicCommands(
            WorkerProtocol.Endpoint endpoint,
            byte[] payload,
            WorkflowResourceContext resources,
            int maximumMessageBytes) throws DocumentFailure {
        WorkerCodecIO.Input input = WorkerCodecIO.accountedInput(
                payload,
                resources);
        int offeredLength;
        try {
            WorkerCommandCodec.requireVersion(input.readInt(), 1);
            offeredLength = input.readInt();
            input.requireFullyConsumed();
        } finally {
            input.releaseDecodedMemory();
        }
        if (offeredLength < 8 || offeredLength > maximumMessageBytes) {
            throw WorkerCodecIO.workerFailure(
                    DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    "The atomic Worker Command batch length is invalid.");
        }
        long remainingAfterOffer = resources.getRemainingOwnedMemoryBytes();
        if (remainingAfterOffer <= Long.MAX_VALUE - payload.length) {
            remainingAfterOffer += payload.length;
        } else {
            remainingAfterOffer = Long.MAX_VALUE;
        }
        boolean accepted = offeredLength <= remainingAfterOffer;
        sendEmpty(
                endpoint,
                accepted
                        ? WorkerProtocol.COMMANDS_ACCEPTED
                        : WorkerProtocol.COMMANDS_DEFERRED,
                resources,
                maximumMessageBytes);
        return accepted ? offeredLength : -1;
    }

    private static void executeCommandBatch(
            WorkerProtocol.Endpoint endpoint,
            DocumentSession session,
            WorkerReferenceRegistry references,
            WorkerValueViewCodec.WorkerRegistry valueViews,
            byte[] payload,
            WorkflowResourceContext resources,
            int maximumMessageBytes) throws DocumentFailure {
        try {
            int commandCount = decodeBatchDeclaration(payload, resources);
            for (int index = 0; index < commandCount; index++) {
                sendCommandPreflightRequired(
                        endpoint,
                        index,
                        resources,
                        maximumMessageBytes);
                WorkerProtocol.Frame preflight = receiveBatchItem(
                        endpoint,
                        valueViews,
                        resources,
                        maximumMessageBytes);
                int preflightDetails;
                try {
                    if (completeBatchAbort(
                            endpoint,
                            preflight,
                            resources,
                            maximumMessageBytes)) {
                        return;
                    }
                    if (preflight.getOpcode()
                            != WorkerProtocol.COMMAND_PREFLIGHT) {
                        throw WorkerCodecIO.workerFailure(
                                DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                                "The Worker Command preflight is unsupported.");
                    }
                    preflightDetails = WorkerCommandCodec.applyPreflight(
                            preflight.getPayload(),
                            (PdfBoxDocumentSession) session,
                            resources);
                } finally {
                    preflight.clear();
                }
                while (preflightDetails
                        != WorkerCommandCodec.PREFLIGHT_DETAILS_NONE) {
                    sendCommandPreflightDetailsRequired(
                            endpoint,
                            index,
                            preflightDetails,
                            resources,
                            maximumMessageBytes);
                    WorkerProtocol.Frame details = receiveBatchItem(
                            endpoint,
                            valueViews,
                            resources,
                            maximumMessageBytes);
                    try {
                        if (completeBatchAbort(
                                endpoint,
                                details,
                                resources,
                                maximumMessageBytes)) {
                            return;
                        }
                        if (details.getOpcode()
                                != WorkerProtocol
                                        .COMMAND_PREFLIGHT_DETAILS) {
                            throw WorkerCodecIO.workerFailure(
                                    DocumentFailureCode
                                            .WORKER_PROTOCOL_REJECTED,
                                    "The Worker Command preflight detail is unsupported.");
                        }
                        preflightDetails =
                                WorkerCommandCodec.applyPreflightDetails(
                                details.getPayload(),
                                (PdfBoxDocumentSession) session,
                                resources);
                    } finally {
                        details.clear();
                    }
                }
                sendCommandRequired(
                        endpoint,
                        index,
                        resources,
                        maximumMessageBytes);
                WorkerProtocol.Frame item = receiveBatchItem(
                        endpoint,
                        valueViews,
                        resources,
                        maximumMessageBytes);
                try {
                    if (completeBatchAbort(
                            endpoint,
                            item,
                            resources,
                            maximumMessageBytes)) {
                        return;
                    }
                    if (item.getOpcode() != WorkerProtocol.COMMAND_ITEM) {
                        throw WorkerCodecIO.workerFailure(
                                DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                                "The Worker Command batch item is unsupported.");
                    }
                    executeCommandItem(
                            session,
                            references,
                            item.getPayload(),
                            endpoint,
                            maximumMessageBytes,
                            resources);
                } finally {
                    item.clear();
                }
            }
            sendEmpty(
                    endpoint,
                    WorkerProtocol.COMMAND_COMPLETED,
                    resources,
                    maximumMessageBytes);
        } catch (DocumentFailure failure) {
            if (!sendFailure(
                    endpoint,
                    failure,
                    resources,
                    maximumMessageBytes)) {
                throw new WorkerAbort();
            }
        }
    }

    private static boolean completeBatchAbort(
            WorkerProtocol.Endpoint endpoint,
            WorkerProtocol.Frame frame,
            WorkflowResourceContext resources,
            int maximumMessageBytes) throws DocumentFailure {
        if (frame.getOpcode() != WorkerProtocol.COMMAND_BATCH_ABORT) {
            return false;
        }
        requireBatchAbort(frame.getPayload(), resources);
        sendEmpty(
                endpoint,
                WorkerProtocol.COMMAND_BATCH_ABORTED,
                resources,
                maximumMessageBytes);
        return true;
    }

    private static void executeCommands(
            WorkerProtocol.Endpoint endpoint,
            DocumentSession session,
            WorkerReferenceRegistry references,
            byte[] payload,
            WorkflowResourceContext resources,
            int maximumMessageBytes) throws DocumentFailure {
        try {
            WorkerCodecIO.Input input = WorkerCodecIO.accountedInput(
                    payload,
                    resources);
            try {
                WorkerCommandCodec.executeAtomicBatch(input, session);
            } finally {
                input.releaseDecodedMemory();
            }
            sendEmpty(
                    endpoint,
                    WorkerProtocol.COMMAND_COMPLETED,
                    resources,
                    maximumMessageBytes);
        } catch (DocumentFailure failure) {
            if (!sendFailure(
                    endpoint,
                    failure,
                    resources,
                    maximumMessageBytes)) {
                throw new WorkerAbort();
            }
        }
    }

    private static int decodeBatchDeclaration(
            byte[] payload,
            WorkflowResourceContext resources) throws DocumentFailure {
        WorkerCodecIO.Input input = WorkerCodecIO.accountedInput(
                payload,
                resources);
        try {
            WorkerCommandCodec.requireVersion(input.readInt(), 1);
            int count = input.readInt();
            if (count < 0) {
                throw WorkerCodecIO.workerFailure(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "The Worker Command batch count is invalid.");
            }
            input.requireFullyConsumed();
            return count;
        } finally {
            input.releaseDecodedMemory();
        }
    }

    private static void sendCommandRequired(
            WorkerProtocol.Endpoint endpoint,
            int index,
            WorkflowResourceContext resources,
            int maximumMessageBytes) throws DocumentFailure {
        sendIndexedControl(
                endpoint,
                WorkerProtocol.COMMAND_REQUIRED,
                index,
                WorkerCommandCodec.PREFLIGHT_DETAILS_NONE,
                resources,
                maximumMessageBytes);
    }

    private static void sendCommandPreflightRequired(
            WorkerProtocol.Endpoint endpoint,
            int index,
            WorkflowResourceContext resources,
            int maximumMessageBytes) throws DocumentFailure {
        sendIndexedControl(
                endpoint,
                WorkerProtocol.COMMAND_PREFLIGHT_REQUIRED,
                index,
                WorkerCommandCodec.PREFLIGHT_DETAILS_NONE,
                resources,
                maximumMessageBytes);
    }

    private static void sendCommandPreflightDetailsRequired(
            WorkerProtocol.Endpoint endpoint,
            int index,
            int detailKind,
            WorkflowResourceContext resources,
            int maximumMessageBytes) throws DocumentFailure {
        sendIndexedControl(
                endpoint,
                WorkerProtocol.COMMAND_PREFLIGHT_DETAILS_REQUIRED,
                index,
                detailKind,
                resources,
                maximumMessageBytes);
    }

    private static void sendIndexedControl(
            WorkerProtocol.Endpoint endpoint,
            short opcode,
            int index,
            int detailKind,
            WorkflowResourceContext resources,
            int maximumMessageBytes) throws DocumentFailure {
        byte[] payload = WorkerCodecIO.encode(
                resources,
                maximumMessageBytes,
                output -> {
                    output.writeInt(1);
                    output.writeInt(index);
                    if (detailKind
                            != WorkerCommandCodec.PREFLIGHT_DETAILS_NONE) {
                        output.writeInt(detailKind);
                    }
                });
        try {
            endpoint.send(opcode, payload);
        } catch (WorkerProtocol.ProtocolException failure) {
            throw WorkerCodecIO.workerFailure(
                    failure.getCode(),
                    failure.getMessage());
        } catch (IOException failure) {
            throw new WorkerAbort();
        } finally {
            WorkerCodecIO.clearRetained(resources, payload);
        }
    }

    private static WorkerProtocol.Frame receiveBatchItem(
            WorkerProtocol.Endpoint endpoint,
            WorkerValueViewCodec.WorkerRegistry valueViews,
            WorkflowResourceContext resources,
            int maximumMessageBytes) throws DocumentFailure {
        while (true) {
            WorkerProtocol.Frame frame = receiveApplicationFrame(endpoint);
            if (frame.getOpcode() != WorkerProtocol.VALUE_VIEW) {
                return frame;
            }
            try {
                executeValueView(
                        endpoint,
                        valueViews,
                        frame.getPayload(),
                        resources,
                        maximumMessageBytes);
            } finally {
                frame.clear();
            }
        }
    }

    private static void requireBatchAbort(
            byte[] payload,
            WorkflowResourceContext resources) throws DocumentFailure {
        WorkerCodecIO.Input input = WorkerCodecIO.accountedInput(
                payload,
                resources);
        try {
            WorkerCommandCodec.requireVersion(input.readInt(), 1);
            input.requireFullyConsumed();
        } finally {
            input.releaseDecodedMemory();
        }
    }

    private static void executeCommandItem(
            DocumentSession session,
            WorkerReferenceRegistry references,
            byte[] payload,
            WorkerProtocol.Endpoint endpoint,
            int maximumMessageBytes,
            WorkflowResourceContext resources) throws DocumentFailure {
        WorkerCodecIO.Input input = WorkerCodecIO.accountedInput(
                payload,
                resources);
        try (WorkerRemoteFonts remoteFonts = new WorkerRemoteFonts(
                endpoint,
                resources,
                maximumMessageBytes)) {
            List<DocumentCommand> commands = WorkerCommandCodec.decodeBatch(
                    input,
                    references,
                    remoteFonts);
            if (commands.size() != 1) {
                throw WorkerCodecIO.workerFailure(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "The Worker Command batch item count is invalid.");
            }
            session.execute(commands.get(0));
        } finally {
            input.releaseDecodedMemory();
        }
    }

    private static void executeQuery(
            WorkerProtocol.Endpoint endpoint,
            DocumentSession session,
            WorkerReferenceRegistry references,
            WorkerValueViewCodec.WorkerRegistry valueViews,
            byte[] payload,
            WorkflowResourceContext resources,
            int maximumMessageBytes) throws DocumentFailure {
        try {
            WorkerCodecIO.Input input = WorkerCodecIO.accountedInput(
                    payload,
                    resources);
            try {
                DocumentQuery<?> query = WorkerQueryCodec.decodeQuery(
                        input,
                        references);
                Object result = evaluate(session, query);
                byte[] encoded = query instanceof net.zerocloud.pdf.query.InspectObject
                        ? valueViews.encodeRoot(
                                (PdfValue) result,
                                resources,
                                maximumMessageBytes)
                        : WorkerQueryCodec.encodeResult(
                                query,
                                result,
                                references,
                                resources,
                                maximumMessageBytes);
                try {
                    endpoint.send(WorkerProtocol.QUERY_COMPLETED, encoded);
                } catch (WorkerProtocol.ProtocolException failure) {
                    throw WorkerCodecIO.workerFailure(
                            failure.getCode(),
                            failure.getMessage());
                } catch (IOException failure) {
                    throw new WorkerAbort();
                } finally {
                    WorkerCodecIO.clearRetained(resources, encoded);
                }
            } finally {
                input.releaseDecodedMemory();
            }
        } catch (DocumentFailure failure) {
            if (!sendFailure(
                    endpoint,
                    failure,
                    resources,
                    maximumMessageBytes)) {
                throw new WorkerAbort();
            }
        }
    }

    private static void executeQueryPreflight(
            WorkerProtocol.Endpoint endpoint,
            PdfBoxDocumentSession session,
            byte[] payload,
            WorkflowResourceContext resources,
            int maximumMessageBytes) throws DocumentFailure {
        try {
            WorkerQueryCodec.applyPreflight(payload, session, resources);
            sendEmpty(
                    endpoint,
                    WorkerProtocol.QUERY_PREFLIGHT_COMPLETED,
                    resources,
                    maximumMessageBytes);
        } catch (DocumentFailure failure) {
            if (!sendFailure(
                    endpoint,
                    failure,
                    resources,
                    maximumMessageBytes)) {
                throw new WorkerAbort();
            }
        }
    }

    private static void executeValueView(
            WorkerProtocol.Endpoint endpoint,
            WorkerValueViewCodec.WorkerRegistry valueViews,
            byte[] payload,
            WorkflowResourceContext resources,
            int maximumMessageBytes) throws DocumentFailure {
        try {
            byte[] encoded = valueViews.respond(
                    payload,
                    resources,
                    maximumMessageBytes);
            try {
                endpoint.send(WorkerProtocol.VALUE_VIEW_COMPLETED, encoded);
            } catch (WorkerProtocol.ProtocolException failure) {
                throw WorkerCodecIO.workerFailure(
                        failure.getCode(),
                        failure.getMessage());
            } catch (IOException failure) {
                throw new WorkerAbort();
            } finally {
                WorkerCodecIO.clearRetained(resources, encoded);
            }
        } catch (DocumentFailure failure) {
            if (!sendFailure(
                    endpoint,
                    failure,
                    resources,
                    maximumMessageBytes)) {
                throw new WorkerAbort();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Object evaluate(
            DocumentSession session,
            DocumentQuery<?> query) throws DocumentFailure {
        return session.query((DocumentQuery<Object>) query);
    }

    private static void sendEmpty(
            WorkerProtocol.Endpoint endpoint,
            short opcode,
            WorkflowResourceContext resources,
            int maximumMessageBytes) throws DocumentFailure {
        byte[] payload = WorkerCodecIO.encode(
                resources,
                maximumMessageBytes,
                output -> output.writeInt(1));
        try {
            endpoint.send(opcode, payload);
        } catch (WorkerProtocol.ProtocolException failure) {
            throw WorkerCodecIO.workerFailure(
                    failure.getCode(),
                    failure.getMessage());
        } catch (IOException failure) {
            throw new WorkerAbort();
        } finally {
            WorkerCodecIO.clearRetained(resources, payload);
        }
    }

    private static void sendProgress(
            WorkerProtocol.Endpoint endpoint,
            WorkflowProgressPhase phase,
            WorkflowResourceContext resources,
            int maximumMessageBytes) {
        if (resources == null) {
            throw new WorkerProgressFailure(WorkerCodecIO.workerFailure(
                    DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    "The Worker progress boundary is unavailable."));
        }
        byte[] payload = null;
        try {
            payload = WorkerMessages.encodeProgress(
                    phase,
                    resources,
                    maximumMessageBytes);
            endpoint.send(WorkerProtocol.PROGRESS, payload);
        } catch (DocumentFailure failure) {
            throw new WorkerProgressFailure(failure);
        } catch (WorkerProtocol.ProtocolException failure) {
            throw new WorkerProgressFailure(WorkerCodecIO.workerFailure(
                    failure.getCode(),
                    failure.getMessage()));
        } catch (IOException failure) {
            throw new WorkerAbort();
        } finally {
            if (payload != null) {
                WorkerCodecIO.clearRetained(resources, payload);
            }
        }
        WorkerProtocol.Frame acknowledgement;
        try {
            acknowledgement = receiveApplicationFrame(endpoint);
        } catch (DocumentFailure failure) {
            throw new WorkerProgressFailure(failure);
        }
        try {
            if (acknowledgement.getOpcode()
                    != WorkerProtocol.PROGRESS_ACKNOWLEDGED) {
                throw new WorkerProgressFailure(WorkerCodecIO.workerFailure(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "The Worker progress acknowledgement is unsupported."));
            }
            WorkflowProgressPhase acknowledged =
                    WorkerMessages.decodeProgress(
                            acknowledgement.getPayload(),
                            resources);
            if (acknowledged != phase) {
                throw new WorkerProgressFailure(WorkerCodecIO.workerFailure(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "The Worker progress acknowledgement is invalid."));
            }
        } catch (DocumentFailure failure) {
            throw new WorkerProgressFailure(failure);
        } finally {
            acknowledgement.clear();
        }
    }

    private static WorkerProtocol.Frame receiveApplicationFrame(
            WorkerProtocol.Endpoint endpoint) throws DocumentFailure {
        while (true) {
            WorkerProtocol.Frame frame;
            try {
                frame = endpoint.receive();
            } catch (WorkerProtocol.ProtocolException failure) {
                if (failure.getDocumentFailure() != null) {
                    throw failure.getDocumentFailure();
                }
                throw WorkerCodecIO.workerFailure(
                        failure.getCode(),
                        failure.getMessage());
            } catch (IOException failure) {
                throw new WorkerAbort();
            }
            if (frame.getOpcode() != WorkerProtocol.MEMORY_SYNCHRONIZE) {
                return frame;
            }
            try {
                if (frame.getPayload().length != 0) {
                    throw WorkerCodecIO.workerFailure(
                            DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                            "The Worker memory synchronization is invalid.");
                }
                try {
                    endpoint.send(
                            WorkerProtocol.MEMORY_SYNCHRONIZED,
                            new byte[0]);
                } catch (WorkerProtocol.ProtocolException failure) {
                    throw WorkerCodecIO.workerFailure(
                            failure.getCode(),
                            failure.getMessage());
                } catch (IOException failure) {
                    throw new WorkerAbort();
                }
            } finally {
                frame.clear();
            }
        }
    }

    static boolean sendFailure(
            WorkerProtocol.Endpoint endpoint,
            DocumentFailure failure,
            WorkflowResourceContext resources,
            int maximumMessageBytes) {
        if (endpoint == null) {
            return false;
        }
        byte[] payload = null;
        boolean accounted = false;
        try {
            if (resources != null && resources.isOpen()) {
                try {
                    payload = WorkerMessages.encodeFailure(
                            failure,
                            resources,
                            maximumMessageBytes);
                    accounted = true;
                } catch (DocumentFailure | RuntimeException exhausted) {
                    try {
                        // The primary failure can itself poison or exhaust the
                        // modeled budget. Preserve a bounded, unaccounted
                        // emergency envelope so the stable cause remains
                        // reportable instead of becoming an EOF.
                        payload = WorkerMessages.encodeFailure(
                                failure,
                                maximumMessageBytes);
                    } catch (DocumentFailure | RuntimeException unsupported) {
                        payload = WorkerMessages.encodeFailure(
                                WorkerCodecIO.workerFailure(
                                        DocumentFailureCode.WORKER_TERMINATED,
                                        "The Worker could not complete the transaction."),
                                maximumMessageBytes);
                    }
                }
            } else {
                // Bootstrap and post-context failures have no live transaction
                // ledger. Their explicit codec is a fixed eight-byte control.
                payload = WorkerMessages.encodeFailure(
                        failure,
                        maximumMessageBytes);
            }
            endpoint.send(WorkerProtocol.FAILURE, payload);
            return true;
        } catch (DocumentFailure | WorkerProtocol.ProtocolException
                | IOException | RuntimeException ignored) {
            // An unreportable failure terminates the Worker without an oracle.
            return false;
        } finally {
            if (payload != null) {
                if (accounted) {
                    WorkerCodecIO.clearRetained(resources, payload);
                } else {
                    Arrays.fill(payload, (byte) 0);
                }
            }
        }
    }

    private static void executeIsolationProbe(
            WorkerProtocol.Endpoint endpoint,
            byte[] request,
            int maximumMessageBytes,
            WorkflowResourceContext resources,
            java.nio.file.Path root) throws DocumentFailure {
        WorkerCodecIO.Input input = WorkerCodecIO.accountedInput(
                request,
                resources);
        try {
            WorkerCommandCodec.requireVersion(input.readInt(), 1);
            String siblingRootName = input.readNullableString();
            long ownedMemoryProbeBytes = input.readLong();
            if (ownedMemoryProbeBytes < 0L
                    || ownedMemoryProbeBytes > Integer.MAX_VALUE) {
                throw WorkerCodecIO.workerFailure(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        "The Worker owned-memory probe is invalid.");
            }
            input.requireFullyConsumed();
            exerciseOwnedMemory(resources, (int) ownedMemoryProbeBytes);
            System.err.println(STDERR_PROBE_MARKER);
            System.err.flush();
            java.nio.file.Path escape = isolationTarget(root, siblingRootName);
            byte[] payload = WorkerCodecIO.encode(
                    resources,
                    maximumMessageBytes,
                    output -> {
                        output.writeInt(1);
                        output.writeBoolean(
                                isDenied(() -> new Socket("127.0.0.1", 9)));
                        output.writeBoolean(
                                isDenied(() -> new ServerSocket(0)));
                        output.writeBoolean(unixDomainDenied(
                                root.resolve("connect-probe.sock"),
                                false));
                        output.writeBoolean(unixDomainDenied(
                                root.resolve("listen-probe.sock"),
                                true));
                        output.writeBoolean(isDenied(() -> {
                            new ProcessBuilder(Paths.get("/bin/true").toString())
                                    .start();
                            return null;
                        }));
                        output.writeBoolean(
                                isDenied(() -> Files.newInputStream(escape))
                                && isDenied(() -> Files.newOutputStream(escape)));
                        output.writeBoolean(HardenedWorkerMain.class
                                .getClassLoader()
                                .getResource("net/zerocloud/pdf/"
                                        + "HardenedWorkerIsolationTest.class")
                                == null);
                        output.writeBoolean(deepReflectionDenied());
                        output.writeBoolean(nativePathLoadDenied(root));
                        output.writeBoolean(nativeLibraryLoadDenied());
                        output.writeString(
                                java.lang.management.ManagementFactory
                                        .getRuntimeMXBean()
                                        .getName());
                        output.writeString(root.getFileName().toString());
                    });
            try {
                endpoint.send(WorkerProtocol.NETWORK_PROBE_COMPLETED, payload);
            } catch (WorkerProtocol.ProtocolException failure) {
                throw WorkerCodecIO.workerFailure(
                        failure.getCode(),
                        failure.getMessage());
            } catch (IOException failure) {
                throw new WorkerAbort();
            } finally {
                WorkerCodecIO.clearRetained(resources, payload);
            }
        } finally {
            input.releaseDecodedMemory();
        }
    }

    private static void exerciseOwnedMemory(
            WorkflowResourceContext resources,
            int amount) throws DocumentFailure {
        if (amount == 0) {
            return;
        }
        try (WorkflowResourceContext.MemoryReservation reservation =
                resources.reserveOwnedMemory(amount)) {
            byte[] memory = new byte[amount];
            try {
                memory[0] = 1;
                memory[memory.length - 1] = 1;
            } finally {
                Arrays.fill(memory, (byte) 0);
            }
        }
    }

    private static java.nio.file.Path isolationTarget(
            java.nio.file.Path root,
            String siblingRootName) throws DocumentFailure {
        if (siblingRootName == null) {
            return root.getParent().resolve("worker-escape-probe");
        }
        java.nio.file.Path segment;
        try {
            segment = Paths.get(siblingRootName);
        } catch (RuntimeException failure) {
            throw WorkerCodecIO.workerFailure(
                    DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    "The Worker isolation-probe target is invalid.");
        }
        if (siblingRootName.isEmpty()
                || segment.isAbsolute()
                || segment.getNameCount() != 1
                || ".".equals(siblingRootName)
                || "..".equals(siblingRootName)) {
            throw WorkerCodecIO.workerFailure(
                    DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    "The Worker isolation-probe target is invalid.");
        }
        java.nio.file.Path sibling = root.getParent()
                .resolve(segment)
                .toAbsolutePath()
                .normalize();
        if (sibling.equals(root)
                || !sibling.getParent().equals(root.getParent())) {
            throw WorkerCodecIO.workerFailure(
                    DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    "The Worker isolation-probe target is invalid.");
        }
        return sibling.resolve("isolation-marker");
    }

    private static WorkflowResourceContext resources(DocumentSession session) {
        if (!(session instanceof PdfBoxDocumentSession)) {
            throw new IllegalStateException(
                    "The Worker Session does not expose resource accounting.");
        }
        return ((PdfBoxDocumentSession) session).getResources();
    }

    private static boolean isDenied(IoAttempt attempt) {
        try {
            java.io.Closeable value = attempt.open();
            if (value != null) {
                value.close();
            }
            return false;
        } catch (SecurityException expected) {
            return true;
        } catch (IOException failure) {
            return false;
        }
    }

    private static boolean deepReflectionDenied() {
        try {
            java.lang.reflect.Field paths =
                    HardenedWorkerSecurityManager.class.getDeclaredField(
                            "readableRuntimePaths");
            paths.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<java.nio.file.Path> readable =
                    (List<java.nio.file.Path>) paths.get(
                            System.getSecurityManager());
            readable.add(Paths.get(java.io.File.separator));
            return false;
        } catch (SecurityException expected) {
            return true;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return false;
        }
    }

    private static boolean nativePathLoadDenied(java.nio.file.Path root) {
        try {
            System.load(root.resolve("worker-native-probe").toString());
            return false;
        } catch (SecurityException expected) {
            return true;
        } catch (LinkageError failure) {
            return false;
        }
    }

    private static boolean nativeLibraryLoadDenied() {
        try {
            System.loadLibrary("folio_pdf_worker_native_probe");
            return false;
        } catch (SecurityException expected) {
            return true;
        } catch (LinkageError failure) {
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean unixDomainDenied(
            java.nio.file.Path path,
            boolean listening) {
        java.io.Closeable channel = null;
        try {
            Class<?> addressType = Class.forName(
                    "java.net.UnixDomainSocketAddress");
            Object address = addressType.getMethod("of", java.nio.file.Path.class)
                    .invoke(null, path);
            Class<?> familyType = Class.forName("java.net.ProtocolFamily");
            Class<? extends Enum> standardFamily = (Class<? extends Enum>)
                    Class.forName("java.net.StandardProtocolFamily");
            Object unix = Enum.valueOf(standardFamily, "UNIX");
            Class<?> channelType = Class.forName(listening
                    ? "java.nio.channels.ServerSocketChannel"
                    : "java.nio.channels.SocketChannel");
            channel = (java.io.Closeable) channelType
                    .getMethod("open", familyType)
                    .invoke(null, unix);
            channelType.getMethod(
                    listening ? "bind" : "connect",
                    java.net.SocketAddress.class).invoke(channel, address);
            return false;
        } catch (ClassNotFoundException unsupported) {
            return true;
        } catch (InvocationTargetException failure) {
            return failure.getCause() instanceof SecurityException;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return false;
        } finally {
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException ignored) {
                    // The probe result is already determined.
                }
            }
        }
    }

    private interface IoAttempt {
        java.io.Closeable open() throws IOException;
    }

    private static final class WorkerAbort extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private static final class WorkerProgressFailure extends RuntimeException {

        private static final long serialVersionUID = 1L;
        private final DocumentFailure documentFailure;

        private WorkerProgressFailure(DocumentFailure documentFailure) {
            this.documentFailure = documentFailure;
        }

        private DocumentFailure getDocumentFailure() {
            return documentFailure;
        }
    }
}
