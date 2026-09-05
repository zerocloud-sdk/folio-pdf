package net.zerocloud.pdf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.zerocloud.pdf.composition.FontSource;
import net.zerocloud.pdf.composition.ReferenceFontSet;

/** Explicit version-1 control-message codecs for the Worker transaction. */
final class WorkerMessages {

    static final int MEMORY_AMOUNT_BYTES = 12;
    static final int RESOURCE_USAGE_BYTES = 72;

    private static final int INITIALIZATION_VERSION = 1;
    private static final int RESULT_VERSION = 1;
    private static final int PROGRESS_VERSION = 1;
    private static final int INPUT_VERSION = 1;
    private static final int FONT_VERSION = 1;
    private static final int MEMORY_VERSION = 1;
    private static final int RESOURCE_USAGE_VERSION = 1;

    private static final int OUTCOME_RENDERING = 13;
    private static final int OUTCOME_WORKFLOW = 1;
    private static final int OUTCOME_INCREMENTAL = 2;
    private static final int OUTCOME_VERSION_SECURITY = 3;
    private static final int OUTCOME_PAGE = 4;
    private static final int OUTCOME_METADATA = 5;
    private static final int OUTCOME_ANNOTATION = 6;
    private static final int OUTCOME_VALUE = 7;
    private static final int OUTCOME_CANVAS = 8;
    private static final int OUTCOME_CANVAS_RESOURCE = 9;
    private static final int OUTCOME_POSITIONED_TEXT = 10;
    private static final int OUTCOME_TEXT_STRUCTURE = 11;
    private static final int OUTCOME_IMAGE_RESOURCE = 12;

    static final int SOURCE_CREDENTIAL = 1;
    static final int OUTPUT_OWNER_CREDENTIAL = 2;
    static final int OUTPUT_USER_CREDENTIAL = 3;

    private WorkerMessages() {
    }

    static byte[] encodeInitialization(
            final WorkflowRequest request,
            final WorkflowResourcePolicy policy,
            final Map<String, Path> sourcePaths,
            final Map<String, Path> targetPaths,
            final List<Path> referenceFontPaths,
            int maximumBytes) throws DocumentFailure {
        return encodeInitialization(
                request,
                policy,
                sourcePaths,
                targetPaths,
                referenceFontPaths,
                null,
                maximumBytes);
    }

    static byte[] encodeInitialization(
            final WorkflowRequest request,
            final WorkflowResourcePolicy policy,
            final Map<String, Path> sourcePaths,
            final Map<String, Path> targetPaths,
            final List<Path> referenceFontPaths,
            WorkflowResourceContext resources,
            int maximumBytes) throws DocumentFailure {
        return encode(resources, maximumBytes, output -> {
            output.writeInt(INITIALIZATION_VERSION);
            output.writeInt(maximumBytes);
            writePolicy(output, policy);
            output.writeString(request.getSaveMode().name());
            output.writeNullableString(request.getPrimarySourceName());
            // The parent owns deadline evaluation because its injectable
            // Clock cannot be faithfully reconstructed in another process.
            output.writeBoolean(false);
            writeOutputPolicyDescriptor(output, request.getOutputPolicy());
            output.writeNullableString(request.getLegacySecurityMode() == null
                    ? null : request.getLegacySecurityMode().name());
            output.writeInt(request.getSources().size());
            for (Map.Entry<String, DocumentSource> entry
                    : request.getSources().entrySet()) {
                output.writeString(entry.getKey());
                Path materialized = sourcePaths.get(entry.getKey());
                output.writeBoolean(materialized != null);
                if (materialized != null) {
                    output.writeString(fileName(materialized));
                }
                output.writeBoolean(entry.getValue().getCredential() != null);
            }
            output.writeInt(targetPaths.size());
            for (Map.Entry<String, Path> entry : targetPaths.entrySet()) {
                output.writeString(entry.getKey());
                output.writeString(fileName(entry.getValue()));
            }
            output.writeInt(referenceFontPaths.size());
            for (Path path : referenceFontPaths) {
                output.writeString(fileName(path));
            }
        });
    }

    static DecodedInitialization decodeInitialization(
            byte[] payload,
            Path workerRoot) throws DocumentFailure {
        return decodeInitialization(payload, workerRoot, Long.MAX_VALUE);
    }

    static DecodedInitialization decodeInitialization(
            byte[] payload,
            Path workerRoot,
            long maximumOwnedMemoryBytes) throws DocumentFailure {
        WorkerCodecIO.Input input = WorkerCodecIO.boundedInput(
                payload,
                maximumOwnedMemoryBytes);
        requireVersion(input.readInt(), INITIALIZATION_VERSION);
        int maximumMessageBytes = input.readInt();
        if (maximumMessageBytes < 0) {
            throw rejected("The Worker message limit is invalid.");
        }
        WorkflowResourcePolicy policy = readPolicy(input);
        SaveMode saveMode = enumValue(
                SaveMode.class,
                input.readString(),
                "Save Mode");
        String primarySourceName = input.readNullableString();
        Instant deadline = null;
        if (input.readBoolean()) {
            long seconds = input.readLong();
            int nano = input.readInt();
            try {
                deadline = Instant.now().plus(
                        Duration.ofSeconds(seconds, nano));
            } catch (RuntimeException failure) {
                throw rejected("The Worker deadline is invalid.");
            }
        }

        List<PasswordCredential> credentials =
                new ArrayList<PasswordCredential>();
        try {
            PdfOutputPolicy outputPolicy = readOutputPolicyDescriptor(
                    input,
                    credentials);
            String legacyName = input.readNullableString();
            LegacySecurityMode legacyMode = legacyName == null ? null
                    : enumValue(
                            LegacySecurityMode.class,
                            legacyName,
                            "legacy security mode");

            WorkflowRequest.Builder request = WorkflowRequest.builder()
                    .saveMode(saveMode)
                    .resourcePolicy(policy);
            int sourceCount = readCount(input, "Source");
            for (int index = 0; index < sourceCount; index++) {
                String name = input.readString();
                boolean materialized = input.readBoolean();
                Path path = materialized
                        ? resolvePrivateFile(
                                workerRoot,
                                input.readString(),
                                true)
                        : workerRoot.resolve(
                                ".pending-worker-source-" + index)
                                .toAbsolutePath()
                                .normalize();
                PasswordCredential credential = input.readBoolean()
                        ? PasswordCredential.of(new char[0])
                        : null;
                if (credential != null) {
                    credentials.add(credential);
                }
                DocumentSource source = materialized
                        ? DocumentSource.workflowSnapshot(path)
                        : DocumentSource.path(path);
                if (credential != null) {
                    source = source.withCredential(credential);
                }
                request.source(name, source);
            }
            if (primarySourceName != null) {
                request.primarySource(primarySourceName);
            }

            Map<String, Path> targetPaths =
                    new LinkedHashMap<String, Path>();
            int targetCount = readCount(input, "Target");
            for (int index = 0; index < targetCount; index++) {
                String name = input.readString();
                Path path = resolvePrivateFile(
                        workerRoot,
                        input.readString(),
                        false);
                request.target(name, PublicationTarget.workerPath(path));
                targetPaths.put(name, path);
            }

            int fontCount = readCount(input, "Reference Font");
            FontSource[] fonts = new FontSource[fontCount];
            for (int index = 0; index < fontCount; index++) {
                fonts[index] = FontSource.path(resolvePrivateFile(
                        workerRoot,
                        input.readString(),
                        true));
            }
            input.requireFullyConsumed();

            if (deadline != null) {
                request.deadline(deadline);
            }
            if (outputPolicy != null) {
                request.outputPolicy(outputPolicy);
            }
            if (legacyMode != null) {
                request.legacySecurityMode(legacyMode);
            }
            return new DecodedInitialization(
                    request.build(),
                    policy,
                    targetPaths,
                    fontCount == 0
                            ? ReferenceFontSet.empty()
                            : ReferenceFontSet.version1(fonts),
                    maximumMessageBytes,
                    input.getStandaloneRetainedMemory(),
                    credentials);
        } catch (DocumentFailure | RuntimeException failure) {
            closeCredentials(credentials);
            if (failure instanceof DocumentFailure) {
                throw (DocumentFailure) failure;
            }
            throw rejected("The Worker initialization value is invalid.");
        }
    }

    static byte[] encodeSourceRequest(
            final String name,
            final long temporaryStorageAllowance,
            WorkflowResourceContext resources,
            int maximumBytes) throws DocumentFailure {
        return WorkerCodecIO.encode(resources, maximumBytes, output -> {
            output.writeInt(INPUT_VERSION);
            output.writeString(name);
            output.writeLong(temporaryStorageAllowance);
        });
    }

    static SourceRequest decodeSourceRequest(
            byte[] payload,
            WorkflowResourceContext resources) throws DocumentFailure {
        WorkerCodecIO.Input input = WorkerCodecIO.accountedInput(
                payload,
                resources);
        try {
            requireVersion(input.readInt(), INPUT_VERSION);
            String name = input.readString();
            long temporaryStorageAllowance = input.readLong();
            input.requireFullyConsumed();
            if (temporaryStorageAllowance < 0L) {
                throw rejected(
                        "The Worker Source temporary-storage allowance is invalid.");
            }
            SourceRequest request = new SourceRequest(
                    name,
                    temporaryStorageAllowance,
                    resources);
            request.retainDecodedMemory(input.transferDecodedMemory());
            return request;
        } finally {
            input.releaseDecodedMemory();
        }
    }

    static byte[] encodeSourceResponse(
            final String name,
            final Path path,
            WorkflowResourceContext resources,
            int maximumBytes) throws DocumentFailure {
        return WorkerCodecIO.encode(resources, maximumBytes, output -> {
            output.writeInt(INPUT_VERSION);
            output.writeString(name);
            output.writeString(fileName(path));
        });
    }

    static Path decodeSourceResponse(
            byte[] payload,
            String expectedName,
            Path workerRoot,
            WorkflowResourceContext resources) throws DocumentFailure {
        WorkerCodecIO.Input input = WorkerCodecIO.accountedInput(
                payload,
                resources);
        try {
            requireVersion(input.readInt(), INPUT_VERSION);
            if (!expectedName.equals(input.readString())) {
                throw rejected("The Worker Source response is inapplicable.");
            }
            Path path = resolvePrivateFile(
                    workerRoot,
                    input.readString(),
                    true);
            input.requireFullyConsumed();
            return path;
        } finally {
            input.releaseDecodedMemory();
        }
    }

    static byte[] encodeCredentialRequest(
            final int kind,
            final String sourceName,
            WorkflowResourceContext resources,
            int maximumBytes) throws DocumentFailure {
        return WorkerCodecIO.encode(resources, maximumBytes, output -> {
            output.writeInt(INPUT_VERSION);
            output.writeInt(kind);
            output.writeNullableString(sourceName);
        });
    }

    static CredentialRequest decodeCredentialRequest(
            byte[] payload,
            WorkflowResourceContext resources) throws DocumentFailure {
        WorkerCodecIO.Input input = WorkerCodecIO.accountedInput(
                payload,
                resources);
        try {
            requireVersion(input.readInt(), INPUT_VERSION);
            int kind = input.readInt();
            String sourceName = input.readNullableString();
            input.requireFullyConsumed();
            if ((kind == SOURCE_CREDENTIAL) != (sourceName != null)
                    || (kind != SOURCE_CREDENTIAL
                            && kind != OUTPUT_OWNER_CREDENTIAL
                            && kind != OUTPUT_USER_CREDENTIAL)) {
                throw rejected("The Worker credential request is invalid.");
            }
            CredentialRequest request = new CredentialRequest(
                    kind,
                    sourceName,
                    resources);
            request.retainDecodedMemory(input.transferDecodedMemory());
            return request;
        } finally {
            input.releaseDecodedMemory();
        }
    }

    static byte[] encodeCredentialResponse(
            final int kind,
            final String sourceName,
            final PasswordCredential credential,
            WorkflowResourceContext resources,
            int maximumBytes) throws DocumentFailure {
        return WorkerCodecIO.encode(resources, maximumBytes, output -> {
            output.writeInt(INPUT_VERSION);
            output.writeInt(kind);
            output.writeNullableString(sourceName);
            writeCredential(output, credential);
        });
    }

    static DecodedCredential decodeCredentialResponse(
            byte[] payload,
            int expectedKind,
            String expectedSourceName,
            WorkflowResourceContext resources) throws DocumentFailure {
        WorkerCodecIO.Input input = WorkerCodecIO.accountedInput(
                payload,
                resources);
        WorkflowResourceContext.MemoryReservation retained = null;
        WorkflowResourceContext.MemoryReservation temporary = null;
        PasswordCredential credential = null;
        char[] characters = null;
        try {
            requireVersion(input.readInt(), INPUT_VERSION);
            if (input.readInt() != expectedKind
                    || !java.util.Objects.equals(
                            expectedSourceName,
                            input.readNullableString())) {
                throw rejected("The Worker credential response is inapplicable.");
            }
            if (!input.readBoolean()) {
                throw rejected("A required Worker credential is missing.");
            }
            int count = input.readInt();
            if (count < 0 || count > input.available() / 2) {
                throw rejected("A Worker credential length is invalid.");
            }
            retained = resources.reserveOwnedMemory(2L * count);
            temporary = resources.reserveOwnedMemory(2L * count);
            characters = new char[count];
            for (int index = 0; index < count; index++) {
                characters[index] = (char) (input.readShort() & 0xffff);
            }
            input.requireFullyConsumed();
            credential = PasswordCredential.of(characters);
            DecodedCredential decoded = new DecodedCredential(
                    credential,
                    retained);
            credential = null;
            retained = null;
            return decoded;
        } finally {
            if (characters != null) {
                Arrays.fill(characters, '\0');
            }
            if (temporary != null) {
                temporary.close();
            }
            if (credential != null) {
                credential.close();
            }
            if (retained != null) {
                retained.close();
            }
            input.releaseDecodedMemory();
        }
    }

    static byte[] encodeFontRequest(
            final long identifier,
            WorkflowResourceContext resources,
            int maximumBytes) throws DocumentFailure {
        return WorkerCodecIO.encode(resources, maximumBytes, output -> {
            output.writeInt(FONT_VERSION);
            output.writeLong(identifier);
        });
    }

    static long decodeFontRequest(
            byte[] payload,
            WorkflowResourceContext resources) throws DocumentFailure {
        WorkerCodecIO.Input input = WorkerCodecIO.accountedInput(
                payload,
                resources);
        try {
            requireVersion(input.readInt(), FONT_VERSION);
            long identifier = input.readLong();
            input.requireFullyConsumed();
            if (identifier < 1L) {
                throw rejected("The Worker font-source read is invalid.");
            }
            return identifier;
        } finally {
            input.releaseDecodedMemory();
        }
    }

    static byte[] encodeFontValue(
            final long identifier,
            final WorkerFontSourceCache.FontValue value,
            WorkflowResourceContext resources,
            int maximumBytes) throws DocumentFailure {
        return WorkerCodecIO.encode(resources, maximumBytes, output -> {
            output.writeInt(FONT_VERSION);
            output.writeLong(identifier);
            output.writeBytes(value.getBytes());
        });
    }

    static WorkflowResourceContext.OwnedBytes decodeFontValue(
            byte[] payload,
            long expectedIdentifier,
            WorkflowResourceContext resources) throws DocumentFailure {
        WorkerCodecIO.Input input = WorkerCodecIO.accountedInput(
                payload,
                resources);
        try {
            requireVersion(input.readInt(), FONT_VERSION);
            if (input.readLong() != expectedIdentifier) {
                throw rejected("The Worker font-source response is inapplicable.");
            }
            WorkflowResourceContext.OwnedBytes value =
                    input.readWorkingBytes();
            try {
                input.requireFullyConsumed();
                return value;
            } catch (DocumentFailure | RuntimeException | Error failure) {
                value.close();
                throw failure;
            }
        } finally {
            input.releaseDecodedMemory();
        }
    }

    static byte[] encodeFinished(
            final WorkflowOutcome<?> outcome,
            int maximumBytes) throws DocumentFailure {
        return WorkerCodecIO.encode(maximumBytes, output -> {
            output.writeInt(RESULT_VERSION);
            output.writeInt(outcomeCapabilityToken(outcome.getCapabilityId()));
        });
    }

    static byte[] encodeProgress(
            final WorkflowProgressPhase phase,
            WorkflowResourceContext resources,
            int maximumBytes) throws DocumentFailure {
        return WorkerCodecIO.encode(resources, maximumBytes, output -> {
            output.writeInt(PROGRESS_VERSION);
            if (phase == WorkflowProgressPhase.STAGED) {
                output.writeInt(1);
            } else if (phase == WorkflowProgressPhase.VALIDATED) {
                output.writeInt(2);
            } else {
                throw new IllegalArgumentException(
                        "Only Worker-internal progress phases may cross the boundary.");
            }
        });
    }

    static WorkflowProgressPhase decodeProgress(
            byte[] payload,
            WorkflowResourceContext resources) throws DocumentFailure {
        WorkerCodecIO.Input input = WorkerCodecIO.accountedInput(
                payload,
                resources);
        requireVersion(input.readInt(), PROGRESS_VERSION);
        int value = input.readInt();
        input.requireFullyConsumed();
        if (value == 1) {
            return WorkflowProgressPhase.STAGED;
        }
        if (value == 2) {
            return WorkflowProgressPhase.VALIDATED;
        }
        throw rejected("The Worker progress phase is unsupported.");
    }

    static String decodeFinished(byte[] payload) throws DocumentFailure {
        return decodeFinished(payload, null);
    }

    static String decodeFinished(
            byte[] payload,
            WorkflowResourceContext resources) throws DocumentFailure {
        WorkerCodecIO.Input input = resources == null
                ? WorkerCodecIO.input(payload)
                : WorkerCodecIO.accountedInput(payload, resources);
        requireVersion(input.readInt(), RESULT_VERSION);
        String capabilityId = outcomeCapability(input.readInt());
        input.requireFullyConsumed();
        return capabilityId;
    }

    static byte[] encodeResourceUsage(
            final WorkflowResourceUsage usage,
            int maximumBytes) throws DocumentFailure {
        return WorkerCodecIO.encode(maximumBytes, output -> {
            output.writeInt(RESOURCE_USAGE_VERSION);
            output.writeLong(usage.getAcceptedInputBytes());
            output.writeLong(usage.getObservedPages());
            output.writeLong(usage.getObservedObjects());
            output.writeLong(usage.getDecompressedBytes());
            output.writeLong(usage.getDecodedPixels());
            output.writeLong(usage.getPeakOwnedMemoryBytes());
            output.writeLong(usage.getPeakTemporaryStorageBytes());
            output.writeLong(usage.getElapsedTime().getSeconds());
            output.writeInt(usage.getElapsedTime().getNano());
        });
    }

    static WorkflowResourceUsage decodeResourceUsage(
            byte[] payload,
            WorkflowResourceContext resources) throws DocumentFailure {
        WorkerCodecIO.Input input = resources == null
                ? WorkerCodecIO.input(payload)
                : WorkerCodecIO.accountedInput(payload, resources);
        try {
            requireVersion(input.readInt(), RESOURCE_USAGE_VERSION);
            long acceptedInputBytes = input.readLong();
            long observedPages = input.readLong();
            long observedObjects = input.readLong();
            long decompressedBytes = input.readLong();
            long decodedPixels = input.readLong();
            long peakOwnedMemoryBytes = input.readLong();
            long peakTemporaryStorageBytes = input.readLong();
            long elapsedSeconds = input.readLong();
            int elapsedNanos = input.readInt();
            input.requireFullyConsumed();
            if (elapsedSeconds < 0L
                    || elapsedNanos < 0
                    || elapsedNanos >= 1_000_000_000) {
                throw rejected("The Worker resource usage is invalid.");
            }
            try {
                return new WorkflowResourceUsage(
                        acceptedInputBytes,
                        observedPages,
                        observedObjects,
                        decompressedBytes,
                        decodedPixels,
                        peakOwnedMemoryBytes,
                        peakTemporaryStorageBytes,
                        Duration.ofSeconds(elapsedSeconds, elapsedNanos));
            } catch (IllegalArgumentException failure) {
                throw rejected("The Worker resource usage is invalid.");
            }
        } finally {
            input.releaseDecodedMemory();
        }
    }

    private static int outcomeCapabilityToken(String capabilityId)
            throws DocumentFailure {
        if (Rendering.CAPABILITY_ID.equals(capabilityId)) { return OUTCOME_RENDERING; }
        if (PdfBoxWorkflowEngine.CAPABILITY_ID.equals(capabilityId)) {
            return OUTCOME_WORKFLOW;
        }
        if (PdfBoxWorkflowEngine.INCREMENTAL_CAPABILITY_ID.equals(capabilityId)) {
            return OUTCOME_INCREMENTAL;
        }
        if (PdfBoxWorkflowEngine.VERSION_SECURITY_CAPABILITY_ID.equals(
                capabilityId)) {
            return OUTCOME_VERSION_SECURITY;
        }
        if (PdfBoxPageOperations.CAPABILITY_ID.equals(capabilityId)) {
            return OUTCOME_PAGE;
        }
        if (PdfBoxMetadataOperations.CAPABILITY_ID.equals(capabilityId)) {
            return OUTCOME_METADATA;
        }
        if (PdfBoxAnnotationOperations.CAPABILITY_ID.equals(capabilityId)) {
            return OUTCOME_ANNOTATION;
        }
        if (PdfBoxValueAdapter.CAPABILITY_ID.equals(capabilityId)) {
            return OUTCOME_VALUE;
        }
        if (PdfBoxCanvasOperations.CAPABILITY_ID.equals(capabilityId)) {
            return OUTCOME_CANVAS;
        }
        if (PdfBoxCanvasResourceOperations.CAPABILITY_ID.equals(capabilityId)) {
            return OUTCOME_CANVAS_RESOURCE;
        }
        if (PdfBoxPositionedTextOperations.CAPABILITY_ID.equals(capabilityId)) {
            return OUTCOME_POSITIONED_TEXT;
        }
        if (PdfBoxTextStructureExtractionOperations.CAPABILITY_ID.equals(
                capabilityId)) {
            return OUTCOME_TEXT_STRUCTURE;
        }
        if (PdfBoxImageResourceExtractionOperations.CAPABILITY_ID.equals(
                capabilityId)) {
            return OUTCOME_IMAGE_RESOURCE;
        }
        throw rejected("The Worker outcome capability is unsupported.");
    }

    private static String outcomeCapability(int token) throws DocumentFailure {
        switch (token) {
            case OUTCOME_RENDERING:
                return Rendering.CAPABILITY_ID;
            case OUTCOME_WORKFLOW:
                return PdfBoxWorkflowEngine.CAPABILITY_ID;
            case OUTCOME_INCREMENTAL:
                return PdfBoxWorkflowEngine.INCREMENTAL_CAPABILITY_ID;
            case OUTCOME_VERSION_SECURITY:
                return PdfBoxWorkflowEngine.VERSION_SECURITY_CAPABILITY_ID;
            case OUTCOME_PAGE:
                return PdfBoxPageOperations.CAPABILITY_ID;
            case OUTCOME_METADATA:
                return PdfBoxMetadataOperations.CAPABILITY_ID;
            case OUTCOME_ANNOTATION:
                return PdfBoxAnnotationOperations.CAPABILITY_ID;
            case OUTCOME_VALUE:
                return PdfBoxValueAdapter.CAPABILITY_ID;
            case OUTCOME_CANVAS:
                return PdfBoxCanvasOperations.CAPABILITY_ID;
            case OUTCOME_CANVAS_RESOURCE:
                return PdfBoxCanvasResourceOperations.CAPABILITY_ID;
            case OUTCOME_POSITIONED_TEXT:
                return PdfBoxPositionedTextOperations.CAPABILITY_ID;
            case OUTCOME_TEXT_STRUCTURE:
                return PdfBoxTextStructureExtractionOperations.CAPABILITY_ID;
            case OUTCOME_IMAGE_RESOURCE:
                return PdfBoxImageResourceExtractionOperations.CAPABILITY_ID;
            default:
                throw rejected("The Worker outcome capability is unsupported.");
        }
    }

    static byte[] encodeFailure(
            final DocumentFailure failure,
            int maximumBytes) throws DocumentFailure {
        return encodeFailure(failure, null, maximumBytes);
    }

    static byte[] encodeFailure(
            final DocumentFailure failure,
            WorkflowResourceContext resources,
            int maximumBytes) throws DocumentFailure {
        return encode(resources, maximumBytes, output -> {
            output.writeInt(RESULT_VERSION);
            output.writeInt(WorkerFailureCatalog.encode(failure));
        });
    }

    private static byte[] encode(
            WorkflowResourceContext resources,
            int maximumBytes,
            WorkerCodecIO.Encoder encoder) throws DocumentFailure {
        return resources == null
                ? WorkerCodecIO.encode(maximumBytes, encoder)
                : WorkerCodecIO.encode(resources, maximumBytes, encoder);
    }

    static DocumentFailure decodeFailure(byte[] payload)
            throws DocumentFailure {
        return decodeFailure(payload, null);
    }

    static DocumentFailure decodeFailure(
            byte[] payload,
            WorkflowResourceContext resources) throws DocumentFailure {
        WorkerCodecIO.Input input = resources == null
                ? WorkerCodecIO.input(payload)
                : WorkerCodecIO.accountedInput(payload, resources);
        requireVersion(input.readInt(), RESULT_VERSION);
        int descriptor = input.readInt();
        input.requireFullyConsumed();
        return WorkerFailureCatalog.decode(descriptor);
    }

    static byte[] encodeMemoryAmount(long amount) {
        if (amount <= 0L) {
            throw new IllegalArgumentException(
                    "Worker memory amounts must be positive.");
        }
        byte[] value = new byte[MEMORY_AMOUNT_BYTES];
        writeInt(value, 0, MEMORY_VERSION);
        writeLong(value, 4, amount);
        return value;
    }

    static long decodeMemoryAmount(byte[] payload) throws DocumentFailure {
        WorkerCodecIO.Input input = WorkerCodecIO.input(payload);
        requireVersion(input.readInt(), MEMORY_VERSION);
        long amount = input.readLong();
        input.requireFullyConsumed();
        if (amount <= 0L) {
            throw rejected("The Worker memory amount is invalid.");
        }
        return amount;
    }

    private static void writeInt(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }

    private static void writeLong(byte[] target, int offset, long value) {
        target[offset] = (byte) (value >>> 56);
        target[offset + 1] = (byte) (value >>> 48);
        target[offset + 2] = (byte) (value >>> 40);
        target[offset + 3] = (byte) (value >>> 32);
        target[offset + 4] = (byte) (value >>> 24);
        target[offset + 5] = (byte) (value >>> 16);
        target[offset + 6] = (byte) (value >>> 8);
        target[offset + 7] = (byte) value;
    }

    private static void writePolicy(
            WorkerCodecIO.Output output,
            WorkflowResourcePolicy policy) throws IOException {
        output.writeInt(WorkflowResourcePolicy.VERSION_1);
        output.writeLong(policy.getMaximumInputBytes());
        output.writeInt(policy.getMaximumPages());
        output.writeLong(policy.getMaximumObjects());
        output.writeInt(policy.getMaximumNestingDepth());
        output.writeLong(policy.getMaximumDecompressedBytes());
        output.writeLong(policy.getMaximumDecodedPixels());
        output.writeLong(policy.getMaximumOwnedMemoryBytes());
        output.writeLong(policy.getMaximumTemporaryStorageBytes());
        output.writeLong(policy.getMaximumElapsedTime().getSeconds());
        output.writeInt(policy.getMaximumElapsedTime().getNano());
        output.writeInt(policy.getMaximumConcurrentWorkflows());
    }

    private static WorkflowResourcePolicy readPolicy(
            WorkerCodecIO.Input input) throws DocumentFailure {
        requireVersion(input.readInt(), WorkflowResourcePolicy.VERSION_1);
        long maximumInputBytes = input.readLong();
        int maximumPages = input.readInt();
        long maximumObjects = input.readLong();
        int maximumNestingDepth = input.readInt();
        long maximumDecompressedBytes = input.readLong();
        long maximumDecodedPixels = input.readLong();
        long maximumOwnedMemoryBytes = input.readLong();
        long maximumTemporaryStorageBytes = input.readLong();
        long elapsedSeconds = input.readLong();
        int elapsedNanos = input.readInt();
        int maximumConcurrentWorkflows = input.readInt();
        try {
            return WorkflowResourcePolicy.builder()
                    .maximumInputBytes(maximumInputBytes)
                    .maximumPages(maximumPages)
                    .maximumObjects(maximumObjects)
                    .maximumNestingDepth(maximumNestingDepth)
                    .maximumDecompressedBytes(maximumDecompressedBytes)
                    .maximumDecodedPixels(maximumDecodedPixels)
                    .maximumOwnedMemoryBytes(maximumOwnedMemoryBytes)
                    .maximumTemporaryStorageBytes(maximumTemporaryStorageBytes)
                    .maximumElapsedTime(Duration.ofSeconds(
                            elapsedSeconds,
                            elapsedNanos))
                    .maximumConcurrentWorkflows(maximumConcurrentWorkflows)
                    .build();
        } catch (RuntimeException failure) {
            throw rejected("The Worker resource policy is invalid.");
        }
    }

    private static void writeOutputPolicyDescriptor(
            WorkerCodecIO.Output output,
            PdfOutputPolicy policy) throws IOException {
        output.writeBoolean(policy != null);
        if (policy == null) {
            return;
        }
        output.writeInt(policy.getVersion().getMajor());
        output.writeInt(policy.getVersion().getMinor());
        PasswordSecurityPolicy security = policy.getPasswordSecurity();
        output.writeBoolean(security != null);
        if (security != null) {
            output.writeString(security.getAlgorithm().name());
            output.writeString(security.getEncryptionScope().name());
            output.writeInt(security.getPermissions().getStandardMask());
        }
    }

    private static PdfOutputPolicy readOutputPolicyDescriptor(
            WorkerCodecIO.Input input,
            List<PasswordCredential> credentials) throws DocumentFailure {
        if (!input.readBoolean()) {
            return null;
        }
        PdfVersion version = PdfVersion.from(input.readInt(), input.readInt());
        if (version == null) {
            throw rejected("The Worker PDF output version is unsupported.");
        }
        PdfOutputPolicy policy = PdfOutputPolicy.version(version);
        if (!input.readBoolean()) {
            return policy;
        }
        PasswordCredential owner = PasswordCredential.of(new char[0]);
        credentials.add(owner);
        PasswordCredential user = PasswordCredential.of(new char[0]);
        credentials.add(user);
        PasswordEncryptionAlgorithm algorithm = enumValue(
                PasswordEncryptionAlgorithm.class,
                input.readString(),
                "password algorithm");
        PasswordEncryptionScope scope = enumValue(
                PasswordEncryptionScope.class,
                input.readString(),
                "password scope");
        DocumentPermissions permissions = readCanonicalPermissions(input);
        PasswordSecurityPolicy security = PasswordSecurityPolicy.builder(
                        owner,
                        user)
                .algorithm(algorithm)
                .encryptionScope(scope)
                .permissions(permissions)
                .build();
        return policy.withPasswordSecurity(security);
    }

    static DocumentPermissions readCanonicalPermissions(
            WorkerCodecIO.Input input) throws DocumentFailure {
        int mask = input.readInt();
        DocumentPermissions decoded = DocumentPermissions.fromStandardMask(
                mask);
        DocumentPermissions canonical = DocumentPermissions.builder()
                .allowPrinting(decoded.canPrint())
                .allowModification(decoded.canModify())
                .allowContentExtraction(decoded.canExtractContent())
                .allowAnnotationModification(
                        decoded.canModifyAnnotations())
                .allowFormFilling(decoded.canFillForms())
                .allowAccessibilityExtraction(
                        decoded.canExtractForAccessibility())
                .allowDocumentAssembly(decoded.canAssembleDocument())
                .allowFaithfulPrinting(decoded.canPrintFaithfully())
                .build();
        if (canonical.getStandardMask() != mask) {
            throw rejected(
                    "The Worker document-permission mask is invalid.");
        }
        return canonical;
    }

    private static void writeCredential(
            WorkerCodecIO.Output output,
            PasswordCredential credential) throws IOException, DocumentFailure {
        output.writeBoolean(credential != null);
        if (credential == null) {
            return;
        }
        try (WorkflowCredentialCharacters copied =
                WorkflowCredentialCharacters.copyOf(
                        credential,
                        output.resources())) {
            char[] characters = copied.get();
            output.writeInt(characters.length);
            for (char character : characters) {
                output.writeShort(character);
            }
        }
    }

    private static PasswordCredential readCredential(
            WorkerCodecIO.Input input) throws DocumentFailure {
        if (!input.readBoolean()) {
            return null;
        }
        int count = input.readInt();
        if (count < 0 || count > input.available() / 2) {
            throw rejected("A Worker credential length is invalid.");
        }
        input.accountDecodedMemory(4L * count);
        char[] characters = new char[count];
        try {
            for (int index = 0; index < count; index++) {
                characters[index] = (char) (input.readShort() & 0xffff);
            }
            return PasswordCredential.of(characters);
        } finally {
            Arrays.fill(characters, '\0');
        }
    }

    private static PasswordCredential readRequiredCredential(
            WorkerCodecIO.Input input) throws DocumentFailure {
        PasswordCredential credential = readCredential(input);
        if (credential == null) {
            throw rejected("A required Worker credential is missing.");
        }
        return credential;
    }

    private static Path resolvePrivateFile(
            Path workerRoot,
            String fileName,
            boolean requireExisting) throws DocumentFailure {
        Path name;
        try {
            name = workerRoot.getFileSystem().getPath(fileName);
        } catch (RuntimeException failure) {
            throw rejected("A Worker private-file reference is invalid.");
        }
        if (name.isAbsolute() || name.getNameCount() != 1) {
            throw rejected("A Worker private-file reference is invalid.");
        }
        Path normalizedRoot = workerRoot.toAbsolutePath().normalize();
        Path path = normalizedRoot.resolve(name).normalize();
        if (!path.getParent().equals(normalizedRoot)
                || (requireExisting && !Files.isRegularFile(path))) {
            throw rejected("A Worker private-file reference is unavailable.");
        }
        return path;
    }

    private static int readCount(
            WorkerCodecIO.Input input,
            String label) throws DocumentFailure {
        int count = input.readInt();
        if (count < 0 || count > input.available()) {
            throw rejected("The Worker protocol value is invalid.");
        }
        input.accountCollectionEntries(count);
        return count;
    }

    private static String fileName(Path path) throws DocumentFailure {
        Path name = path.getFileName();
        if (name == null) {
            throw rejected("A Worker private-file reference is invalid.");
        }
        return name.toString();
    }

    private static void requireVersion(int actual, int expected)
            throws DocumentFailure {
        if (actual != expected) {
            throw rejected("The Worker value version is unsupported.");
        }
    }

    private static <E extends Enum<E>> E enumValue(
            Class<E> type,
            String name,
            String label) throws DocumentFailure {
        try {
            return Enum.valueOf(type, name);
        } catch (RuntimeException failure) {
            throw rejected("The Worker protocol value is invalid.");
        }
    }

    private static DocumentFailure rejected(String diagnostic) {
        return WorkerCodecIO.workerFailure(
                DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                diagnostic);
    }

    private static void closeCredentials(List<PasswordCredential> credentials) {
        for (PasswordCredential credential : credentials) {
            credential.close();
        }
    }

    static final class CredentialRequest implements AutoCloseable {

        private final int kind;
        private String sourceName;
        private WorkflowResourceContext resources;
        private long retainedDecodedMemory;

        private CredentialRequest(
                int kind,
                String sourceName,
                WorkflowResourceContext resources) {
            this.kind = kind;
            this.sourceName = sourceName;
            this.resources = resources;
        }

        int getKind() {
            return kind;
        }

        String getSourceName() {
            return sourceName;
        }

        private void retainDecodedMemory(long amount) {
            retainedDecodedMemory = amount;
        }

        @Override
        public void close() {
            WorkflowResourceContext current = resources;
            long amount = retainedDecodedMemory;
            sourceName = null;
            resources = null;
            retainedDecodedMemory = 0L;
            if (current != null && amount != 0L) {
                current.releaseRetainedOwnedMemory(amount);
            }
        }
    }

    static final class SourceRequest implements AutoCloseable {

        private String name;
        private final long temporaryStorageAllowance;
        private WorkflowResourceContext resources;
        private long retainedDecodedMemory;

        private SourceRequest(
                String name,
                long temporaryStorageAllowance,
                WorkflowResourceContext resources) {
            this.name = name;
            this.temporaryStorageAllowance = temporaryStorageAllowance;
            this.resources = resources;
        }

        String getName() {
            return name;
        }

        long getTemporaryStorageAllowance() {
            return temporaryStorageAllowance;
        }

        private void retainDecodedMemory(long amount) {
            retainedDecodedMemory = amount;
        }

        @Override
        public void close() {
            WorkflowResourceContext current = resources;
            long amount = retainedDecodedMemory;
            name = null;
            resources = null;
            retainedDecodedMemory = 0L;
            if (current != null && amount != 0L) {
                current.releaseRetainedOwnedMemory(amount);
            }
        }
    }

    static final class DecodedCredential implements AutoCloseable {

        private PasswordCredential credential;
        private WorkflowResourceContext.MemoryReservation retainedMemory;

        private DecodedCredential(
                PasswordCredential credential,
                WorkflowResourceContext.MemoryReservation retainedMemory) {
            this.credential = credential;
            this.retainedMemory = retainedMemory;
        }

        PasswordCredential getCredential() {
            return credential;
        }

        @Override
        public void close() {
            PasswordCredential currentCredential = credential;
            WorkflowResourceContext.MemoryReservation currentMemory =
                    retainedMemory;
            credential = null;
            retainedMemory = null;
            try {
                if (currentCredential != null) {
                    currentCredential.close();
                }
            } finally {
                if (currentMemory != null) {
                    currentMemory.close();
                }
            }
        }
    }

    static final class DecodedInitialization implements AutoCloseable {

        private WorkflowRequest request;
        private WorkflowResourcePolicy policy;
        private Map<String, Path> targetPaths;
        private ReferenceFontSet referenceFontSet;
        private final int maximumMessageBytes;
        private final long retainedOwnedMemoryBytes;
        private final List<PasswordCredential> credentials;

        private DecodedInitialization(
                WorkflowRequest request,
                WorkflowResourcePolicy policy,
                Map<String, Path> targetPaths,
                ReferenceFontSet referenceFontSet,
                int maximumMessageBytes,
                long retainedOwnedMemoryBytes,
                List<PasswordCredential> credentials) {
            this.request = request;
            this.policy = policy;
            this.targetPaths = targetPaths;
            this.referenceFontSet = referenceFontSet;
            this.maximumMessageBytes = maximumMessageBytes;
            this.retainedOwnedMemoryBytes = retainedOwnedMemoryBytes;
            this.credentials = credentials;
        }

        WorkflowRequest getRequest() {
            return request;
        }

        WorkflowResourcePolicy getPolicy() {
            return policy;
        }

        Map<String, Path> getTargetPaths() {
            return targetPaths;
        }

        ReferenceFontSet getReferenceFontSet() {
            return referenceFontSet;
        }

        int getMaximumMessageBytes() {
            return maximumMessageBytes;
        }

        long getRetainedOwnedMemoryBytes() {
            return retainedOwnedMemoryBytes;
        }

        @Override
        public void close() {
            closeCredentials(credentials);
            credentials.clear();
            request = null;
            policy = null;
            if (targetPaths != null) {
                targetPaths.clear();
                targetPaths = null;
            }
            referenceFontSet = null;
        }
    }
}
