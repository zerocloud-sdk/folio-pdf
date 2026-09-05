package net.zerocloud.pdf;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Hostile framing checks at the authenticated protocol and real process seam. */
public final class WorkerProtocolBoundaryTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void realWorkerRejectsMissingIncorrectAndTamperedAuthentication()
            throws Exception {
        assertRejectedBeforeInitialization(null, null);

        byte[] workerKey = key(4);
        byte[] wrongKey = key(5);
        assertRejectedBeforeInitialization(workerKey, wrongKey);

        byte[] frameKey = key(6);
        byte[] tampered = encodedFrame(
                frameKey,
                WorkerProtocol.INITIALIZE,
                new byte[] {0, 0, 0, 1});
        tampered[tampered.length - 1] ^= 1;
        assertRejectedBeforeInitialization(frameKey, tampered);
    }

    @Test
    public void realWorkerDoesNotReflectForgedFailureDetails()
            throws Exception {
        String secretCapability = "secret-capability-/private/input.pdf";
        String secretDiagnostic = "secret-password-and-backend-exception";
        byte[] payload = values(output -> {
            output.writeInt(1);
            output.writeInt(Integer.MAX_VALUE);
            output.writeString(secretCapability);
            output.writeString(secretDiagnostic);
        });
        try (LiveWorker worker = startWorker(
                "forged-failure",
                65_536,
                41)) {
            sendToWorker(worker.endpoint, WorkerProtocol.FAILURE, payload);
            DocumentFailure failure = receiveFailure(worker.endpoint);
            assertEquals(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    failure.getCode());
            assertEquals(HardenedWorkerEngine.CAPABILITY_ID,
                    failure.getCapabilityId());
            assertFalse(failure.getDiagnostic().contains(secretCapability));
            assertFalse(failure.getDiagnostic().contains(secretDiagnostic));
            worker.assertProductUnchanged();
        } finally {
            Arrays.fill(payload, (byte) 0);
        }
    }

    @Test
    public void realWorkerRequiresExactMemoryControlLength()
            throws Exception {
        byte[] excess = header(
                WorkerProtocol.VERSION_1,
                WorkerProtocol.MEMORY_GRANTED,
                2L,
                WorkerMessages.MEMORY_AMOUNT_BYTES + 1);
        assertLiveRawFailure(
                "memory-control-first-excess",
                excess,
                DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                42);
    }

    @Test
    public void realWorkerRejectsMissingWrongAndRepeatedReceiveGrants()
            throws Exception {
        assertReceiveGrantFailure("missing-receive-grant", 43, 0);
        assertReceiveGrantFailure("wrong-receive-grant", 44, 1);
        assertReceiveGrantFailure("repeated-receive-grant", 45, 2);
    }

    @Test
    public void realWorkerRejectsUnknownOpcodeAndReplayWithoutPublication()
            throws Exception {
        Path root = temporaryFolder.newFolder("unknown-opcode").toPath();
        Path product = Files.createFile(root.resolve("opaque-product.pdf"));
        byte[] sentinel = new byte[] {7, 7, 7};
        Files.write(product, sentinel);
        byte[] key = key(7);
        byte[] initialization = initialization(root, product, 1_000_000);
        Process process = launch(root, 1_000_000);
        startDrainer(process.getErrorStream());
        WorkerProtocol.writeAuthenticationKey(process.getOutputStream(), key);
        WorkerProtocol.Endpoint endpoint = WorkerProtocol.endpoint(
                process.getInputStream(),
                process.getOutputStream(),
                key,
                1_000_000);
        endpoint.send(WorkerProtocol.INITIALIZE, initialization);
        WorkerProtocol.Frame ready = receiveApplicationFrame(endpoint);
        assertEquals(WorkerProtocol.READY, ready.getOpcode());
        ready.clear();
        endpoint.send((short) 999, new byte[0]);
        WorkerProtocol.Frame failureFrame = receiveApplicationFrame(endpoint);
        try {
            assertEquals(WorkerProtocol.FAILURE, failureFrame.getOpcode());
            DocumentFailure failure = WorkerMessages.decodeFailure(
                    failureFrame.getPayload());
            assertEquals(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    failure.getCode());
        } finally {
            failureFrame.clear();
            endpoint.close();
            process.destroyForcibly();
            process.waitFor(5L, TimeUnit.SECONDS);
            Arrays.fill(initialization, (byte) 0);
        }
        assertArrayEquals(sentinel, Files.readAllBytes(product));

        Path replayRoot = temporaryFolder.newFolder("replay").toPath();
        Path replayProduct = Files.createFile(
                replayRoot.resolve("opaque-product.pdf"));
        Files.write(replayProduct, sentinel);
        byte[] replayInitialization = initialization(
                replayRoot,
                replayProduct,
                1_000_000);
        byte[] firstFrame = encodedFrame(
                key,
                WorkerProtocol.INITIALIZE,
                replayInitialization);
        Process replay = launch(replayRoot, 1_000_000);
        startDrainer(replay.getErrorStream());
        WorkerProtocol.writeAuthenticationKey(replay.getOutputStream(), key);
        WorkerProtocol.Endpoint replayEndpoint = WorkerProtocol.endpoint(
                replay.getInputStream(),
                replay.getOutputStream(),
                key,
                1_000_000);
        replayEndpoint.send(
                WorkerProtocol.INITIALIZE,
                replayInitialization);
        WorkerProtocol.Frame replayReady = receiveApplicationFrame(
                replayEndpoint);
        assertEquals(WorkerProtocol.READY, replayReady.getOpcode());
        replayReady.clear();
        replay.getOutputStream().write(firstFrame);
        replay.getOutputStream().flush();
        assertTrue(replay.waitFor(5L, TimeUnit.SECONDS));
        assertArrayEquals(sentinel, Files.readAllBytes(replayProduct));
        Arrays.fill(replayInitialization, (byte) 0);
    }

    @Test
    public void realWorkerEnforcesExactAndFirstExcessMessageBoundaries()
            throws Exception {
        final int maximumBytes = 65_536;
        byte[] exactPayload = WorkerCodecIO.encode(
                maximumBytes,
                output -> {
                    output.writeInt(1);
                    output.writeInt(1);
                    output.writeInt(8);
                    output.writeInt(1);
                    output.writeInt(1);
                    output.writeString("A");
                    output.writeByte(3);
                    output.writeBytes(new byte[maximumBytes - 35]);
                    output.writeInt(0);
                });
        assertEquals(maximumBytes, exactPayload.length);
        try (LiveWorker worker = startWorker(
                "exact-message",
                maximumBytes,
                20)) {
            beginSingleCommand(
                    worker.endpoint,
                    WorkerCommandCodec.PREFLIGHT_METADATA,
                    0);
            sendToWorker(
                    worker.endpoint,
                    WorkerProtocol.COMMAND_ITEM,
                    exactPayload);
            WorkerProtocol.Frame completed = receiveApplicationFrame(
                    worker.endpoint);
            try {
                if (completed.getOpcode() == WorkerProtocol.FAILURE) {
                    DocumentFailure failure = WorkerMessages.decodeFailure(
                            completed.getPayload());
                    org.junit.Assert.fail(failure.getCode() + ": "
                            + failure.getDiagnostic());
                }
                assertEquals(
                        WorkerProtocol.COMMAND_COMPLETED,
                        completed.getOpcode());
            } finally {
                completed.clear();
            }
            worker.assertProductUnchanged();
        } finally {
            Arrays.fill(exactPayload, (byte) 0);
        }

        try (LiveWorker worker = startWorker(
                "excess-message",
                maximumBytes,
                21)) {
            byte[] oversizedHeader = header(
                    WorkerProtocol.VERSION_1,
                    WorkerProtocol.QUERY,
                    1L,
                    maximumBytes + 1);
            overwriteSequence(
                    oversizedHeader,
                    worker.endpoint.nextSendSequence());
            worker.process.getOutputStream().write(oversizedHeader);
            worker.process.getOutputStream().flush();
            assertEquals(
                    DocumentFailureCode.WORKER_MESSAGE_LIMIT_EXCEEDED,
                    receiveFailure(worker.endpoint).getCode());
            worker.assertProductUnchanged();
        }
    }

    @Test
    public void authenticatedTransferCrossesOneFrameAtAnExactAggregateBound()
            throws Exception {
        final int maximumFrameBytes = 96;
        final int maximumTransferMemoryBytes = 368;
        byte[] key = key(63);
        byte[] exact = new byte[maximumTransferMemoryBytes
                - maximumFrameBytes];
        for (int index = 0; index < exact.length; index++) {
            exact[index] = (byte) (index * 31);
        }
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        WorkerProtocol.Endpoint sender = WorkerProtocol.endpoint(
                new ByteArrayInputStream(new byte[0]),
                wire,
                key,
                maximumFrameBytes,
                maximumTransferMemoryBytes);
        sender.send(WorkerProtocol.QUERY, exact);

        WorkerProtocol.Endpoint receiver = WorkerProtocol.endpoint(
                new ByteArrayInputStream(wire.toByteArray()),
                new ByteArrayOutputStream(),
                key,
                maximumFrameBytes,
                maximumTransferMemoryBytes);
        WorkerProtocol.Frame received = receiver.receive();
        try {
            assertEquals(WorkerProtocol.QUERY, received.getOpcode());
            assertArrayEquals(exact, received.getPayload());
        } finally {
            received.clear();
            sender.close();
            receiver.close();
            Arrays.fill(exact, (byte) 0);
            Arrays.fill(key, (byte) 0);
        }

        ByteArrayOutputStream rejectedWire = new ByteArrayOutputStream();
        WorkerProtocol.Endpoint firstExcess = WorkerProtocol.endpoint(
                new ByteArrayInputStream(new byte[0]),
                rejectedWire,
                key(64),
                maximumFrameBytes,
                maximumTransferMemoryBytes);
        try {
            firstExcess.send(
                    WorkerProtocol.QUERY,
                    new byte[maximumTransferMemoryBytes
                            - maximumFrameBytes + 1]);
            org.junit.Assert.fail("Expected the first excess transfer byte");
        } catch (WorkerProtocol.ProtocolException failure) {
            assertEquals(
                    DocumentFailureCode.WORKER_MESSAGE_LIMIT_EXCEEDED,
                    failure.getCode());
        } finally {
            firstExcess.close();
        }
        assertEquals(0, rejectedWire.size());
    }

    @Test
    public void authenticatedTransferRejectsHostileIdentityOrderLengthAndData()
            throws Exception {
        final int maximumFrameBytes = 96;
        final int maximumTransferMemoryBytes = 368;
        byte[] authenticationKey = key(65);
        byte[] value = new byte[maximumTransferMemoryBytes
                - maximumFrameBytes];
        for (int index = 0; index < value.length; index++) {
            value[index] = (byte) (index * 17 + 3);
        }
        byte[] encoded = encodedTransfer(
                authenticationKey,
                maximumFrameBytes,
                maximumTransferMemoryBytes,
                value);
        List<byte[]> frames = physicalFrames(encoded);
        assertTrue(frames.size() > 3);

        assertTransferFailure(
                joinFrames(frames.subList(0, frames.size() - 1)),
                authenticationKey,
                maximumFrameBytes,
                maximumTransferMemoryBytes,
                DocumentFailureCode.WORKER_PROTOCOL_REJECTED);

        List<byte[]> reordered = copiedFrames(frames);
        Collections.swap(reordered, 1, 2);
        assertTransferFailure(
                joinFrames(reordered),
                authenticationKey,
                maximumFrameBytes,
                maximumTransferMemoryBytes,
                DocumentFailureCode.WORKER_AUTHENTICATION_FAILED);

        List<byte[]> duplicated = copiedFrames(frames);
        duplicated.add(2, duplicated.get(1).clone());
        assertTransferFailure(
                joinFrames(duplicated),
                authenticationKey,
                maximumFrameBytes,
                maximumTransferMemoryBytes,
                DocumentFailureCode.WORKER_AUTHENTICATION_FAILED);

        List<byte[]> corrupted = copiedFrames(frames);
        corrupted.get(1)[40] ^= 1;
        assertTransferFailure(
                joinFrames(corrupted),
                authenticationKey,
                maximumFrameBytes,
                maximumTransferMemoryBytes,
                DocumentFailureCode.WORKER_AUTHENTICATION_FAILED);

        List<byte[]> wrongIdentity = copiedFrames(frames);
        wrongIdentity.get(1)[20] ^= 1;
        retag(wrongIdentity.get(1), authenticationKey);
        assertTransferFailure(
                joinFrames(wrongIdentity),
                authenticationKey,
                maximumFrameBytes,
                maximumTransferMemoryBytes,
                DocumentFailureCode.WORKER_PROTOCOL_REJECTED);

        List<byte[]> wrongLength = copiedFrames(frames);
        writeInt(wrongLength.get(0), 38, value.length - 1);
        retag(wrongLength.get(0), authenticationKey);
        assertTransferFailure(
                joinFrames(wrongLength),
                authenticationKey,
                maximumFrameBytes,
                maximumTransferMemoryBytes,
                DocumentFailureCode.WORKER_PROTOCOL_REJECTED);

        List<byte[]> wrongIntegrity = copiedFrames(frames);
        wrongIntegrity.get(1)[40] ^= 1;
        retag(wrongIntegrity.get(1), authenticationKey);
        assertTransferFailure(
                joinFrames(wrongIntegrity),
                authenticationKey,
                maximumFrameBytes,
                maximumTransferMemoryBytes,
                DocumentFailureCode.WORKER_AUTHENTICATION_FAILED);

        assertTransferFailure(
                encoded,
                authenticationKey,
                maximumFrameBytes,
                maximumTransferMemoryBytes - 1L,
                DocumentFailureCode.WORKER_MESSAGE_LIMIT_EXCEEDED);

        Arrays.fill(value, (byte) 0);
        Arrays.fill(encoded, (byte) 0);
        Arrays.fill(authenticationKey, (byte) 0);
    }

    @Test
    public void trailingTransferChunkRequiresAvailableFrameScratch()
            throws Exception {
        final int maximumFrameBytes = 96;
        final int maximumTransferMemoryBytes = 368;
        byte[] authenticationKey = key(67);
        byte[] value = new byte[maximumTransferMemoryBytes
                - maximumFrameBytes];
        byte[] encoded = encodedTransfer(
                authenticationKey,
                maximumFrameBytes,
                maximumTransferMemoryBytes,
                value);
        List<byte[]> frames = physicalFrames(encoded);
        byte[] excess = frames.get(1).clone();
        overwriteSequence(excess, frames.size() + 1L);
        retag(excess, authenticationKey);
        frames.add(excess);
        byte[] hostile = joinFrames(frames);

        WorkflowResourcePolicy defaults =
                WorkflowResourcePolicy.safeDefaults();
        WorkflowResourcePolicy policy = WorkflowResourcePolicy.builder()
                .maximumInputBytes(defaults.getMaximumInputBytes())
                .maximumPages(defaults.getMaximumPages())
                .maximumObjects(defaults.getMaximumObjects())
                .maximumNestingDepth(defaults.getMaximumNestingDepth())
                .maximumDecompressedBytes(
                        defaults.getMaximumDecompressedBytes())
                .maximumDecodedPixels(defaults.getMaximumDecodedPixels())
                .maximumOwnedMemoryBytes(maximumTransferMemoryBytes)
                .maximumTemporaryStorageBytes(
                        defaults.getMaximumTemporaryStorageBytes())
                .maximumElapsedTime(defaults.getMaximumElapsedTime())
                .maximumConcurrentWorkflows(
                        defaults.getMaximumConcurrentWorkflows())
                .build();
        WorkflowResourceContext resources = WorkflowResourceContext.open(
                policy,
                Clock.systemUTC(),
                CancellationToken.none(),
                null,
                temporaryFolder.newFolder("transfer-excess").toPath());
        WorkerProtocol.Endpoint receiver = WorkerProtocol.endpoint(
                new ByteArrayInputStream(hostile),
                new ByteArrayOutputStream(),
                authenticationKey,
                maximumFrameBytes,
                resources);
        WorkflowResourceContext.MemoryReservation pressure = null;
        try {
            WorkerProtocol.Frame received = receiver.receive();
            try {
                assertArrayEquals(value, received.getPayload());
            } finally {
                received.clear();
            }
            pressure = resources.reserveOwnedMemory(
                    maximumTransferMemoryBytes - maximumFrameBytes + 1L);
            try {
                receiver.receive();
                org.junit.Assert.fail(
                        "Expected excess transfer scratch refusal");
            } catch (WorkerProtocol.ProtocolException failure) {
                assertEquals(
                        DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                        failure.getCode());
            }
        } finally {
            if (pressure != null) {
                pressure.close();
            }
            receiver.close();
            resources.close();
            Arrays.fill(value, (byte) 0);
            Arrays.fill(encoded, (byte) 0);
            Arrays.fill(hostile, (byte) 0);
            Arrays.fill(authenticationKey, (byte) 0);
            for (byte[] frame : frames) {
                Arrays.fill(frame, (byte) 0);
            }
        }
    }

    @Test
    public void failedAuthenticatedTransferReleasesOwnedMemoryAndStorage()
            throws Exception {
        final int maximumFrameBytes = 96;
        final int maximumTransferMemoryBytes = 368;
        byte[] authenticationKey = key(66);
        byte[] value = new byte[maximumTransferMemoryBytes
                - maximumFrameBytes];
        byte[] encoded = encodedTransfer(
                authenticationKey,
                maximumFrameBytes,
                maximumTransferMemoryBytes,
                value);
        List<byte[]> frames = physicalFrames(encoded);
        frames.get(1)[40] ^= 1;
        retag(frames.get(1), authenticationKey);
        byte[] hostile = joinFrames(frames);

        Path parent = temporaryFolder.newFolder("transfer-cleanup").toPath();
        Path target = parent.resolve("uncommitted-target.pdf");
        byte[] sentinel = new byte[] {71, 72, 73};
        Files.write(target, sentinel);
        WorkflowResourcePolicy defaults =
                WorkflowResourcePolicy.safeDefaults();
        WorkflowResourcePolicy policy = WorkflowResourcePolicy.builder()
                .maximumInputBytes(defaults.getMaximumInputBytes())
                .maximumPages(defaults.getMaximumPages())
                .maximumObjects(defaults.getMaximumObjects())
                .maximumNestingDepth(defaults.getMaximumNestingDepth())
                .maximumDecompressedBytes(
                        defaults.getMaximumDecompressedBytes())
                .maximumDecodedPixels(defaults.getMaximumDecodedPixels())
                .maximumOwnedMemoryBytes(maximumTransferMemoryBytes)
                .maximumTemporaryStorageBytes(
                        defaults.getMaximumTemporaryStorageBytes())
                .maximumElapsedTime(defaults.getMaximumElapsedTime())
                .maximumConcurrentWorkflows(
                        defaults.getMaximumConcurrentWorkflows())
                .build();
        WorkflowResourceContext resources = WorkflowResourceContext.open(
                policy,
                Clock.systemUTC(),
                CancellationToken.none(),
                null,
                parent);
        Path ownedRoot = resources.getTemporaryRoot();
        WorkerProtocol.Endpoint receiver = WorkerProtocol.endpoint(
                new ByteArrayInputStream(hostile),
                new ByteArrayOutputStream(),
                authenticationKey,
                maximumFrameBytes,
                resources);
        try {
            try {
                WorkerProtocol.Frame received = receiver.receive();
                received.clear();
                org.junit.Assert.fail("Expected transfer integrity failure");
            } catch (WorkerProtocol.ProtocolException failure) {
                assertEquals(
                        DocumentFailureCode.WORKER_AUTHENTICATION_FAILED,
                        failure.getCode());
            }
            assertEquals(
                    maximumTransferMemoryBytes,
                    resources.getRemainingOwnedMemoryBytes());
            assertArrayEquals(sentinel, Files.readAllBytes(target));
        } finally {
            receiver.close();
            Arrays.fill(value, (byte) 0);
            Arrays.fill(encoded, (byte) 0);
            Arrays.fill(hostile, (byte) 0);
            Arrays.fill(authenticationKey, (byte) 0);
            for (byte[] frame : frames) {
                Arrays.fill(frame, (byte) 0);
            }
            resources.close();
        }
        assertFalse(Files.exists(ownedRoot));
        assertArrayEquals(sentinel, Files.readAllBytes(target));
    }

    @Test
    public void realWorkerRejectsMalformedFramesWithoutPublication()
            throws Exception {
        assertLiveRawFailure(
                "unknown-version",
                header((short) 2, WorkerProtocol.QUERY, 2L, 0),
                DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                22);
        assertLiveRawFailure(
                "negative-length",
                header(
                        WorkerProtocol.VERSION_1,
                        WorkerProtocol.QUERY,
                        2L,
                        -1),
                DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                23);
        assertLiveRawFailure(
                "overflowing-length",
                header(
                        WorkerProtocol.VERSION_1,
                        WorkerProtocol.QUERY,
                        2L,
                        Integer.MAX_VALUE),
                DocumentFailureCode.WORKER_MESSAGE_LIMIT_EXCEEDED,
                24);
        assertLiveRawFailure(
                "truncated-header",
                new byte[] {1, 2, 3},
                DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                25);
        assertLiveRawFailure(
                "truncated-body",
                concatenate(
                        header(
                                WorkerProtocol.VERSION_1,
                                WorkerProtocol.QUERY,
                                2L,
                                1),
                        new byte[] {0}),
                DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                26,
                1L);
    }

    @Test
    public void realWorkerRejectsTrailingSerializationAndForbiddenValues()
            throws Exception {
        try (LiveWorker worker = startWorker(
                "hostile-values",
                65_536,
                27)) {
            byte[] trailing = values(output -> {
                output.writeInt(1);
                output.writeInt(0);
                output.writeInt(99);
            });
            assertEquals(
                    DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    sendInvalidCommandItem(worker.endpoint, trailing).getCode());

            assertEquals(
                    DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    sendInvalidCommandItem(
                            worker.endpoint,
                            new byte[] {(byte) 0xac, (byte) 0xed, 0, 5})
                            .getCode());

            for (String forbidden : Arrays.asList(
                    "java.lang.Runtime",
                    "java.lang.reflect.Method",
                    "javascript:app.alert(1)",
                    "https://example.invalid/")) {
                byte[] payload = values(output -> {
                    output.writeInt(1);
                    output.writeInt(1);
                    output.writeInt(1);
                    output.writeString(forbidden);
                });
                DocumentFailure failure = sendInvalidCommandItem(
                        worker.endpoint,
                        payload);
                assertEquals(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        failure.getCode());
                assertFalse(failure.getDiagnostic().contains(forbidden));
            }
            worker.assertProductUnchanged();
        }
    }

    @Test
    public void realWorkerCatalogsMalformedCodecCounts() throws Exception {
        try (LiveWorker worker = startWorker(
                "malformed-codec-count",
                65_536,
                46)) {
            byte[] payload = values(output -> {
                output.writeInt(1);
                output.writeInt(1);
                output.writeInt(8);
                output.writeInt(1);
                output.writeInt(-1);
            });
            DocumentFailure failure = sendInvalidCommandItem(
                    worker.endpoint,
                    payload);
            assertEquals(
                    DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                    failure.getCode());
            assertEquals(
                    "The Worker protocol value is invalid.",
                    failure.getDiagnostic());
            worker.assertProductUnchanged();
        }
    }

    @Test
    public void realWorkerSynchronizesAtomicAndIndexedWaitStates()
            throws Exception {
        try (LiveWorker worker = startWorker(
                "memory-synchronization-waits",
                65_536,
                47)) {
            byte[] atomic = values(output -> {
                output.writeInt(1);
                output.writeInt(1);
                output.writeInt(1);
                output.writeInt(1);
            });
            try {
                offerAtomic(worker.endpoint, atomic.length);
                synchronizeMemory(worker.endpoint);
                sendToWorker(worker.endpoint, WorkerProtocol.COMMANDS, atomic);
                WorkerProtocol.Frame completed = receiveApplicationFrame(
                        worker.endpoint);
                try {
                    assertEquals(
                            WorkerProtocol.COMMAND_COMPLETED,
                            completed.getOpcode());
                } finally {
                    completed.clear();
                }
                synchronizeMemory(worker.endpoint);
            } finally {
                Arrays.fill(atomic, (byte) 0);
            }

            byte[] declaration = values(output -> {
                output.writeInt(1);
                output.writeInt(1);
            });
            byte[] preflight = values(output -> {
                output.writeInt(1);
                output.writeInt(WorkerCommandCodec.PREFLIGHT_UNKNOWN);
                output.writeInt(0);
            });
            byte[] item = values(output -> {
                output.writeInt(1);
                output.writeInt(1);
                output.writeInt(1);
                output.writeInt(1);
            });
            try {
                sendToWorker(
                        worker.endpoint,
                        WorkerProtocol.COMMAND_BATCH,
                        declaration);
                requireIndexedRequest(
                        worker.endpoint,
                        WorkerProtocol.COMMAND_PREFLIGHT_REQUIRED,
                        0);
                synchronizeMemory(worker.endpoint);
                sendToWorker(
                        worker.endpoint,
                        WorkerProtocol.COMMAND_PREFLIGHT,
                        preflight);
                requireIndexedRequest(
                        worker.endpoint,
                        WorkerProtocol.COMMAND_REQUIRED,
                        0);
                synchronizeMemory(worker.endpoint);
                sendToWorker(
                        worker.endpoint,
                        WorkerProtocol.COMMAND_ITEM,
                        item);
                WorkerProtocol.Frame completed = receiveApplicationFrame(
                        worker.endpoint);
                try {
                    assertEquals(
                            WorkerProtocol.COMMAND_COMPLETED,
                            completed.getOpcode());
                } finally {
                    completed.clear();
                }
                worker.assertProductUnchanged();
            } finally {
                Arrays.fill(declaration, (byte) 0);
                Arrays.fill(preflight, (byte) 0);
                Arrays.fill(item, (byte) 0);
            }
        }
    }

    @Test
    public void realWorkerProgressRequiresAcknowledgementBeforeContinuing()
            throws Exception {
        try (LiveWorker worker = startWorker(
                "progress-barriers",
                65_536,
                28)) {
            executeBlankPage(worker.endpoint);

            CountDownLatch staged = new CountDownLatch(1);
            CountDownLatch releaseStaged = new CountDownLatch(1);
            CountDownLatch validated = new CountDownLatch(1);
            CountDownLatch releaseValidated = new CountDownLatch(1);
            AtomicReference<Throwable> controllerFailure =
                    new AtomicReference<Throwable>();
            Thread controller = new Thread(() -> {
                try {
                    worker.endpoint.send(
                            WorkerProtocol.FINISH,
                            new byte[0]);
                    requireProgress(
                            worker.endpoint,
                            WorkflowProgressPhase.STAGED);
                    staged.countDown();
                    assertTrue(releaseStaged.await(5L, TimeUnit.SECONDS));
                    acknowledgeProgress(
                            worker.endpoint,
                            WorkflowProgressPhase.STAGED);

                    requireProgress(
                            worker.endpoint,
                            WorkflowProgressPhase.VALIDATED);
                    validated.countDown();
                    assertTrue(releaseValidated.await(5L, TimeUnit.SECONDS));
                    acknowledgeProgress(
                            worker.endpoint,
                            WorkflowProgressPhase.VALIDATED);

                    WorkerProtocol.Frame finished = receiveApplicationFrame(
                            worker.endpoint);
                    try {
                        assertEquals(
                                WorkerProtocol.FINISHED,
                                finished.getOpcode());
                        assertEquals(
                                PdfBoxWorkflowEngine.CAPABILITY_ID,
                                WorkerMessages.decodeFinished(
                                        finished.getPayload()));
                    } finally {
                        finished.clear();
                    }
                } catch (Throwable failure) {
                    controllerFailure.set(failure);
                    staged.countDown();
                    validated.countDown();
                }
            }, "worker-progress-barrier-controller");
            controller.start();

            assertTrue(staged.await(5L, TimeUnit.SECONDS));
            rethrowControllerFailure(controllerFailure.get());
            assertWorkerCannotContinue(worker);
            releaseStaged.countDown();

            assertTrue(validated.await(5L, TimeUnit.SECONDS));
            rethrowControllerFailure(controllerFailure.get());
            assertWorkerCannotContinue(worker);
            releaseValidated.countDown();

            controller.join(TimeUnit.SECONDS.toMillis(5L));
            assertFalse(controller.isAlive());
            rethrowControllerFailure(controllerFailure.get());
            assertTrue(worker.process.waitFor(5L, TimeUnit.SECONDS));
            byte[] product = Files.readAllBytes(worker.product);
            assertTrue(product.length >= 4);
            assertArrayEquals(
                    new byte[] {'%', 'P', 'D', 'F'},
                    Arrays.copyOf(product, 4));
        }
    }

    private void assertRejectedBeforeInitialization(
            byte[] authenticationKey,
            byte[] frameOrFrameKey) throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        Path product = Files.createFile(root.resolve("opaque-product.pdf"));
        byte[] sentinel = new byte[] {1, 3, 5, 7};
        Files.write(product, sentinel);
        Process process = launch(root, 1_000_000);
        startDrainer(process.getErrorStream());
        if (authenticationKey == null) {
            process.getOutputStream().close();
        } else {
            WorkerProtocol.writeAuthenticationKey(
                    process.getOutputStream(),
                    authenticationKey);
            byte[] frame = frameOrFrameKey.length
                    == WorkerProtocol.AUTHENTICATION_KEY_BYTES
                    ? encodedFrame(
                            frameOrFrameKey,
                            WorkerProtocol.INITIALIZE,
                            initialization(root, product, 1_000_000))
                    : frameOrFrameKey;
            process.getOutputStream().write(frame);
            process.getOutputStream().flush();
            process.getOutputStream().close();
        }
        assertTrue(process.waitFor(5L, TimeUnit.SECONDS));
        assertArrayEquals(sentinel, Files.readAllBytes(product));
    }

    private LiveWorker startWorker(
            String name,
            int maximumBytes,
            int keySeed) throws Exception {
        Path root = temporaryFolder.newFolder(name).toPath();
        Path product = Files.createFile(root.resolve("opaque-product.pdf"));
        byte[] sentinel = new byte[] {11, 13, 17, 19};
        Files.write(product, sentinel);
        byte[] initialization = initialization(root, product, maximumBytes);
        byte[] authenticationKey = key(keySeed);
        Process process = launch(root, maximumBytes);
        startDrainer(process.getErrorStream());
        WorkerProtocol.writeAuthenticationKey(
                process.getOutputStream(),
                authenticationKey);
        WorkerProtocol.Endpoint endpoint = WorkerProtocol.endpoint(
                process.getInputStream(),
                process.getOutputStream(),
                authenticationKey,
                maximumBytes);
        Arrays.fill(authenticationKey, (byte) 0);
        try {
            endpoint.send(WorkerProtocol.INITIALIZE, initialization);
            WorkerProtocol.Frame ready = receiveApplicationFrame(endpoint);
            try {
                assertEquals(WorkerProtocol.READY, ready.getOpcode());
            } finally {
                ready.clear();
            }
            return new LiveWorker(process, endpoint, product, sentinel);
        } catch (Throwable failure) {
            endpoint.close();
            process.destroyForcibly();
            process.waitFor(5L, TimeUnit.SECONDS);
            throw failure;
        } finally {
            Arrays.fill(initialization, (byte) 0);
        }
    }

    private void assertLiveRawFailure(
            String name,
            byte[] frame,
            DocumentFailureCode code,
            int keySeed) throws Exception {
        assertLiveRawFailure(name, frame, code, keySeed, -1L);
    }

    private void assertLiveRawFailure(
            String name,
            byte[] frame,
            DocumentFailureCode code,
            int keySeed,
            long pregrantBytes) throws Exception {
        try (LiveWorker worker = startWorker(name, 65_536, keySeed)) {
            if (pregrantBytes >= 0L) {
                byte[] grant = WorkerMessages.encodeMemoryAmount(
                        pregrantBytes);
                try {
                    worker.endpoint.send(WorkerProtocol.MEMORY_GRANTED, grant);
                } finally {
                    Arrays.fill(grant, (byte) 0);
                }
            }
            if (frame.length >= 20) {
                overwriteSequence(
                        frame,
                        worker.endpoint.nextSendSequence());
            }
            worker.process.getOutputStream().write(frame);
            worker.process.getOutputStream().flush();
            worker.process.getOutputStream().close();
            assertEquals(code, receiveFailure(worker.endpoint).getCode());
            assertTrue(worker.process.waitFor(5L, TimeUnit.SECONDS));
            worker.assertProductUnchanged();
        } finally {
            Arrays.fill(frame, (byte) 0);
        }
    }

    private void assertReceiveGrantFailure(
            String name,
            int keySeed,
            int mode) throws Exception {
        try (LiveWorker worker = startWorker(name, 65_536, keySeed)) {
            byte[] request = new byte[] {0};
            byte[] grant = WorkerMessages.encodeMemoryAmount(
                    mode == 1 ? request.length + 1L : request.length);
            try {
                if (mode != 0) {
                    worker.endpoint.send(
                            WorkerProtocol.MEMORY_GRANTED,
                            grant);
                }
                if (mode == 2) {
                    worker.endpoint.send(
                            WorkerProtocol.MEMORY_GRANTED,
                            grant);
                } else {
                    worker.endpoint.send(WorkerProtocol.QUERY, request);
                }
                DocumentFailure failure = receiveFailure(worker.endpoint);
                assertEquals(
                        DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                        failure.getCode());
                worker.assertProductUnchanged();
            } finally {
                Arrays.fill(request, (byte) 0);
                Arrays.fill(grant, (byte) 0);
            }
        }
    }

    private static DocumentFailure sendInvalidCommandItem(
            WorkerProtocol.Endpoint endpoint,
            byte[] item) throws Exception {
        try {
            beginSingleCommand(
                    endpoint,
                    WorkerCommandCodec.PREFLIGHT_UNKNOWN,
                    0);
            sendToWorker(endpoint, WorkerProtocol.COMMAND_ITEM, item);
            return receiveFailure(endpoint);
        } finally {
            Arrays.fill(item, (byte) 0);
        }
    }

    private static void executeBlankPage(WorkerProtocol.Endpoint endpoint)
            throws Exception {
        byte[] item = values(output -> {
            output.writeInt(1);
            output.writeInt(1);
            output.writeInt(1);
            output.writeInt(1);
        });
        try {
            offerAtomic(endpoint, item.length);
            sendToWorker(endpoint, WorkerProtocol.COMMANDS, item);
            WorkerProtocol.Frame completed = receiveApplicationFrame(
                    endpoint);
            try {
                assertEquals(
                        WorkerProtocol.COMMAND_COMPLETED,
                        completed.getOpcode());
            } finally {
                completed.clear();
            }
        } finally {
            Arrays.fill(item, (byte) 0);
        }
    }

    private static void offerAtomic(
            WorkerProtocol.Endpoint endpoint,
            int payloadLength) throws Exception {
        byte[] offer = values(output -> {
            output.writeInt(1);
            output.writeInt(payloadLength);
        });
        try {
            sendToWorker(endpoint, WorkerProtocol.COMMANDS_OFFER, offer);
            WorkerProtocol.Frame accepted = receiveApplicationFrame(
                    endpoint);
            try {
                assertEquals(
                        WorkerProtocol.COMMANDS_ACCEPTED,
                        accepted.getOpcode());
                WorkerCodecIO.Input input = WorkerCodecIO.input(
                        accepted.getPayload());
                WorkerCommandCodec.requireVersion(input.readInt(), 1);
                input.requireFullyConsumed();
            } finally {
                accepted.clear();
            }
        } finally {
            Arrays.fill(offer, (byte) 0);
        }
    }

    private static void beginSingleCommand(
            WorkerProtocol.Endpoint endpoint,
            int preflightCategory,
            int pageNumber) throws Exception {
        byte[] declaration = values(output -> {
            output.writeInt(1);
            output.writeInt(1);
        });
        byte[] preflight = values(output -> {
            output.writeInt(1);
            output.writeInt(preflightCategory);
            output.writeInt(pageNumber);
        });
        try {
            sendToWorker(endpoint, WorkerProtocol.COMMAND_BATCH, declaration);
            requireIndexedRequest(
                    endpoint,
                    WorkerProtocol.COMMAND_PREFLIGHT_REQUIRED,
                    0);
            sendToWorker(
                    endpoint,
                    WorkerProtocol.COMMAND_PREFLIGHT,
                    preflight);
            requireIndexedRequest(
                    endpoint,
                    WorkerProtocol.COMMAND_REQUIRED,
                    0);
        } finally {
            Arrays.fill(declaration, (byte) 0);
            Arrays.fill(preflight, (byte) 0);
        }
    }

    private static void requireIndexedRequest(
            WorkerProtocol.Endpoint endpoint,
            short opcode,
            int index) throws Exception {
        WorkerProtocol.Frame response = receiveApplicationFrame(endpoint);
        try {
            assertEquals(opcode, response.getOpcode());
            WorkerCodecIO.Input input = WorkerCodecIO.input(
                    response.getPayload());
            WorkerCommandCodec.requireVersion(input.readInt(), 1);
            assertEquals(index, input.readInt());
            input.requireFullyConsumed();
        } finally {
            response.clear();
        }
    }

    private static void requireProgress(
            WorkerProtocol.Endpoint endpoint,
            WorkflowProgressPhase expected) throws Exception {
        WorkerProtocol.Frame progress = receiveApplicationFrame(endpoint);
        try {
            assertEquals(WorkerProtocol.PROGRESS, progress.getOpcode());
            assertEquals(expected, WorkerMessages.decodeProgress(
                    progress.getPayload(),
                    null));
        } finally {
            progress.clear();
        }
    }

    private static void acknowledgeProgress(
            WorkerProtocol.Endpoint endpoint,
            WorkflowProgressPhase phase) throws Exception {
        byte[] acknowledgement = values(output -> {
            output.writeInt(1);
            output.writeInt(phase == WorkflowProgressPhase.STAGED ? 1 : 2);
        });
        try {
            sendToWorker(
                    endpoint,
                    WorkerProtocol.PROGRESS_ACKNOWLEDGED,
                    acknowledgement);
        } finally {
            Arrays.fill(acknowledgement, (byte) 0);
        }
    }

    private static void synchronizeMemory(
            WorkerProtocol.Endpoint endpoint) throws Exception {
        endpoint.send(WorkerProtocol.MEMORY_SYNCHRONIZE, new byte[0]);
        WorkerProtocol.Frame synchronizedFrame = receiveApplicationFrame(
                endpoint);
        try {
            assertEquals(
                    WorkerProtocol.MEMORY_SYNCHRONIZED,
                    synchronizedFrame.getOpcode());
            assertEquals(0, synchronizedFrame.getPayload().length);
        } finally {
            synchronizedFrame.clear();
        }
    }

    private static void assertWorkerCannotContinue(LiveWorker worker)
            throws Exception {
        long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(300L);
        do {
            assertTrue(worker.process.isAlive());
            worker.assertProductUnchanged();
            Thread.sleep(10L);
        } while (System.nanoTime() - until < 0L);
    }

    private static void rethrowControllerFailure(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof AssertionError) {
            throw (AssertionError) failure;
        }
        throw new AssertionError(failure);
    }

    private static void sendToWorker(
            WorkerProtocol.Endpoint endpoint,
            short opcode,
            byte[] payload) throws Exception {
        if (payload.length != 0) {
            byte[] grant = WorkerMessages.encodeMemoryAmount(
                    endpoint.requiredReceiveMemory(payload.length));
            try {
                endpoint.send(WorkerProtocol.MEMORY_GRANTED, grant);
            } finally {
                Arrays.fill(grant, (byte) 0);
            }
        }
        endpoint.send(opcode, payload);
    }

    private static WorkerProtocol.Frame receiveApplicationFrame(
            WorkerProtocol.Endpoint endpoint) throws Exception {
        while (true) {
            WorkerProtocol.Frame frame = endpoint.receive();
            if (frame.getOpcode() == WorkerProtocol.MEMORY_RESERVE
                    || frame.getOpcode() == WorkerProtocol.TEMPORARY_RESERVE) {
                short grantOpcode = frame.getOpcode() == WorkerProtocol.MEMORY_RESERVE
                        ? WorkerProtocol.MEMORY_GRANTED : WorkerProtocol.TEMPORARY_GRANTED;
                long amount;
                try {
                    amount = WorkerMessages.decodeMemoryAmount(
                            frame.getPayload());
                } finally {
                    frame.clear();
                }
                byte[] grant = WorkerMessages.encodeMemoryAmount(amount);
                try {
                    endpoint.send(grantOpcode, grant);
                } finally {
                    Arrays.fill(grant, (byte) 0);
                }
                continue;
            }
            if (frame.getOpcode() == WorkerProtocol.MEMORY_RELEASE
                    || frame.getOpcode() == WorkerProtocol.TEMPORARY_RELEASE) {
                WorkerMessages.decodeMemoryAmount(frame.getPayload());
                frame.clear();
                continue;
            }
            if (frame.getOpcode() == WorkerProtocol.RESOURCE_USAGE) {
                WorkerMessages.decodeResourceUsage(frame.getPayload(), null);
                frame.clear();
                continue;
            }
            return frame;
        }
    }

    private static DocumentFailure receiveFailure(
            WorkerProtocol.Endpoint endpoint) throws Exception {
        WorkerProtocol.Frame response = receiveApplicationFrame(endpoint);
        try {
            assertEquals(WorkerProtocol.FAILURE, response.getOpcode());
            return WorkerMessages.decodeFailure(response.getPayload());
        } finally {
            response.clear();
        }
    }

    private static byte[] concatenate(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        Arrays.fill(first, (byte) 0);
        Arrays.fill(second, (byte) 0);
        return result;
    }

    private static byte[] initialization(
            Path root,
            Path product,
            int maximumBytes) throws DocumentFailure {
        WorkflowRequest request = WorkflowRequest.builder()
                .target("result", PublicationTarget.path(product))
                .saveMode(SaveMode.REWRITE)
                .build();
        Map<String, Path> targets = new LinkedHashMap<String, Path>();
        targets.put("result", product);
        return WorkerMessages.encodeInitialization(
                request,
                WorkflowResourcePolicy.safeDefaults(),
                Collections.<String, Path>emptyMap(),
                targets,
                Collections.<Path>emptyList(),
                maximumBytes);
    }

    private static Process launch(Path root, int maximumBytes)
            throws IOException, DocumentFailure {
        Path java = Paths.get(
                System.getProperty("java.home"),
                "bin",
                File.separatorChar == '\\' ? "java.exe" : "java");
        List<String> command = new java.util.ArrayList<String>();
        Collections.addAll(
                command,
                "/usr/bin/prlimit",
                "--cpu=5",
                "--nofile=64",
                "--",
                java.toString(),
                "-Xmx128m",
                "-XX:MaxDirectMemorySize=128m",
                "-Xss1m");
        if (HardenedWorkerEngine.requiresSecurityManagerAllowOption()) {
            command.add("-Djava.security.manager=allow");
        }
        Collections.addAll(
                command,
                "-Djava.io.tmpdir=" + root,
                "-cp",
                HardenedWorkerEngine.workerClassPath(),
                HardenedWorkerMain.class.getName(),
                root.toString(),
                Integer.toString(maximumBytes),
                Long.toString(WorkflowResourcePolicy.safeDefaults()
                        .getMaximumOwnedMemoryBytes()));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(root.toFile());
        builder.environment().clear();
        return builder.start();
    }

    private static byte[] encodedFrame(
            byte[] key,
            short opcode,
            byte[] payload) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        WorkerProtocol.endpoint(
                new ByteArrayInputStream(new byte[0]),
                output,
                key,
                Math.max(payload.length, 1)).send(opcode, payload);
        return output.toByteArray();
    }

    private static byte[] encodedTransfer(
            byte[] key,
            int maximumFrameBytes,
            long maximumTransferMemoryBytes,
            byte[] payload) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        WorkerProtocol.Endpoint endpoint = WorkerProtocol.endpoint(
                new ByteArrayInputStream(new byte[0]),
                output,
                key,
                maximumFrameBytes,
                maximumTransferMemoryBytes);
        try {
            endpoint.send(WorkerProtocol.QUERY, payload);
            return output.toByteArray();
        } finally {
            endpoint.close();
        }
    }

    private static void assertTransferFailure(
            byte[] encoded,
            byte[] key,
            int maximumFrameBytes,
            long maximumTransferMemoryBytes,
            DocumentFailureCode expectedCode) throws Exception {
        WorkerProtocol.Endpoint endpoint = WorkerProtocol.endpoint(
                new ByteArrayInputStream(encoded),
                new ByteArrayOutputStream(),
                key,
                maximumFrameBytes,
                maximumTransferMemoryBytes);
        try {
            WorkerProtocol.Frame frame = endpoint.receive();
            frame.clear();
            org.junit.Assert.fail("Expected a hostile transfer failure");
        } catch (WorkerProtocol.ProtocolException failure) {
            assertEquals(expectedCode, failure.getCode());
        } finally {
            endpoint.close();
        }
    }

    private static List<byte[]> physicalFrames(byte[] encoded) {
        List<byte[]> frames = new ArrayList<byte[]>();
        int offset = 0;
        while (offset < encoded.length) {
            if (encoded.length - offset < 20) {
                throw new AssertionError("Truncated encoded frame header");
            }
            int payloadLength = readInt(encoded, offset + 16);
            int frameLength = 20 + payloadLength
                    + WorkerProtocol.AUTHENTICATION_TAG_BYTES;
            if (payloadLength < 0 || frameLength < 0
                    || frameLength > encoded.length - offset) {
                throw new AssertionError("Invalid encoded frame length");
            }
            frames.add(Arrays.copyOfRange(
                    encoded,
                    offset,
                    offset + frameLength));
            offset += frameLength;
        }
        return frames;
    }

    private static List<byte[]> copiedFrames(List<byte[]> frames) {
        List<byte[]> copy = new ArrayList<byte[]>(frames.size());
        for (byte[] frame : frames) {
            copy.add(frame.clone());
        }
        return copy;
    }

    private static byte[] joinFrames(List<byte[]> frames) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] frame : frames) {
            output.write(frame, 0, frame.length);
        }
        return output.toByteArray();
    }

    private static void retag(byte[] frame, byte[] key) throws Exception {
        int payloadLength = readInt(frame, 16);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        mac.update(frame, 4, 16 + payloadLength);
        byte[] tag = mac.doFinal();
        try {
            System.arraycopy(tag, 0, frame, 20 + payloadLength, tag.length);
        } finally {
            Arrays.fill(tag, (byte) 0);
        }
    }

    private static int readInt(byte[] source, int offset) {
        return (source[offset] & 0xff) << 24
                | (source[offset + 1] & 0xff) << 16
                | (source[offset + 2] & 0xff) << 8
                | source[offset + 3] & 0xff;
    }

    private static void writeInt(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }

    private static byte[] header(
            short version,
            short opcode,
            long sequence,
            int length) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(output);
        data.writeInt(WorkerProtocol.MAGIC);
        data.writeShort(version);
        data.writeShort(opcode);
        data.writeLong(sequence);
        data.writeInt(length);
        data.flush();
        return output.toByteArray();
    }

    private static void overwriteSequence(byte[] frame, long sequence) {
        for (int index = 0; index < 8; index++) {
            frame[8 + index] = (byte) (sequence >>> (56 - 8 * index));
        }
    }

    private static byte[] values(WorkerCodecIO.Encoder encoder)
            throws DocumentFailure {
        return WorkerCodecIO.encode(4096, encoder);
    }

    private static byte[] key(int seed) {
        byte[] key = new byte[WorkerProtocol.AUTHENTICATION_KEY_BYTES];
        for (int index = 0; index < key.length; index++) {
            key[index] = (byte) (seed + index);
        }
        return key;
    }

    private static void startDrainer(final InputStream stream) {
        Thread thread = new Thread(() -> {
            byte[] bytes = new byte[1024];
            try {
                while (stream.read(bytes) >= 0) {
                    Arrays.fill(bytes, (byte) 0);
                }
            } catch (IOException ignored) {
                // Process completion is asserted by each test.
            }
        }, "worker-protocol-test-stderr");
        thread.setDaemon(true);
        thread.start();
    }

    private static final class LiveWorker implements AutoCloseable {

        private final Process process;
        private final WorkerProtocol.Endpoint endpoint;
        private final Path product;
        private final byte[] sentinel;

        private LiveWorker(
                Process process,
                WorkerProtocol.Endpoint endpoint,
                Path product,
                byte[] sentinel) {
            this.process = process;
            this.endpoint = endpoint;
            this.product = product;
            this.sentinel = sentinel;
        }

        private void assertProductUnchanged() throws IOException {
            assertArrayEquals(sentinel, Files.readAllBytes(product));
        }

        @Override
        public void close() throws Exception {
            endpoint.close();
            process.destroyForcibly();
            process.waitFor(5L, TimeUnit.SECONDS);
            Arrays.fill(sentinel, (byte) 0);
        }
    }
}
