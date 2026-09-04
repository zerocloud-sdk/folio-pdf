package net.zerocloud.pdf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import net.zerocloud.pdf.command.AddBlankPage;
import org.apache.pdfbox.io.RandomAccess;
import org.apache.pdfbox.io.RandomAccessStreamCache;
import org.junit.Rule;
import org.junit.Assume;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class WorkflowResourceCacheAccountingTest {

    private static final long CACHE_PAGE_BYTES = 4096L;

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void closedBuffersReuseAlreadyChargedCachePages()
            throws Exception {
        WorkflowResourceContext resources = open(CACHE_PAGE_BYTES);
        try {
            RandomAccessStreamCache cache = resources
                    .streamCacheFactory()
                    .create();
            writeAndClose(cache);
            writeAndClose(cache);
        } finally {
            resources.close();
        }
    }

    @Test
    public void firstCachePageHonorsExactBoundaryAndFirstExcess()
            throws Exception {
        WorkflowResourceContext exact = open(CACHE_PAGE_BYTES);
        try {
            RandomAccess buffer = exact.streamCacheFactory()
                    .create()
                    .createBuffer();
            buffer.close();
        } finally {
            exact.close();
        }

        WorkflowResourceContext excess = open(CACHE_PAGE_BYTES - 1L);
        try {
            try {
                RandomAccess buffer = excess.streamCacheFactory()
                        .create()
                        .createBuffer();
                fail("Expected cache-page accounting to exhaust storage");
            } catch (IOException failure) {
                DocumentFailure resourceFailure =
                        WorkflowResourceContext.findResourceFailure(failure);
                assertEquals(
                        DocumentFailureCode.TEMPORARY_STORAGE_LIMIT_EXCEEDED,
                        resourceFailure.getCode());
            }
        } finally {
            excess.close();
        }
    }

    @Test
    public void clearedBufferRetainsItsInitialCachePage()
            throws Exception {
        WorkflowResourceContext resources = open(CACHE_PAGE_BYTES);
        try {
            RandomAccessStreamCache cache = resources
                    .streamCacheFactory()
                    .create();
            RandomAccess retained = cache.createBuffer();
            retained.clear();
            try {
                cache.createBuffer();
                fail("Expected the retained cache page to remain charged");
            } catch (IOException failure) {
                DocumentFailure resourceFailure =
                        WorkflowResourceContext.findResourceFailure(failure);
                assertEquals(
                        DocumentFailureCode.TEMPORARY_STORAGE_LIMIT_EXCEEDED,
                        resourceFailure.getCode());
            } finally {
                retained.close();
            }
        } finally {
            resources.close();
        }
    }

    @Test
    public void accumulatorAccountsGrowthAndFinalCopyAtNonPowerBoundary()
            throws Exception {
        WorkflowResourceContext exact = openWithMemory(13L);
        try {
            WorkflowResourceContext.OwnedByteAccumulator accumulator =
                    exact.ownedByteAccumulator();
            try {
                byte[] payload = "12345".getBytes(StandardCharsets.US_ASCII);
                for (byte value : payload) {
                    accumulator.write(value);
                }
                WorkflowResourceContext.OwnedBytes result =
                        accumulator.finishWorking();
                result.close();
            } finally {
                accumulator.close();
            }
        } finally {
            exact.close();
        }

        WorkflowResourceContext excess = openWithMemory(12L);
        try {
            WorkflowResourceContext.OwnedByteAccumulator accumulator =
                    excess.ownedByteAccumulator();
            try {
                byte[] payload = "12345".getBytes(StandardCharsets.US_ASCII);
                for (byte value : payload) {
                    accumulator.write(value);
                }
                try {
                    accumulator.finishWorking();
                    fail("Expected final-copy memory peak to exhaust policy");
                } catch (DocumentFailure failure) {
                    assertEquals(
                            DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                            failure.getCode());
                }
            } finally {
                accumulator.close();
            }
        } finally {
            excess.close();
        }
    }

    @Test
    public void textAccumulatorAccountsBuilderAndFinalStringPeak()
            throws Exception {
        WorkflowResourceContext exact = openWithMemory(20L);
        try {
            try (WorkflowResourceContext.OwnedMemoryScope ownership =
                            exact.ownedMemoryScope();
                    WorkflowResourceContext.OwnedTextAccumulator text =
                            exact.ownedTextAccumulator()) {
                text.append("12345");
                assertEquals("12345", ownership.hold(text.finishWorking()));
                ownership.transfer();
            }
        } finally {
            exact.close();
        }

        WorkflowResourceContext excess = openWithMemory(19L);
        try {
            try (WorkflowResourceContext.OwnedTextAccumulator text =
                    excess.ownedTextAccumulator()) {
                text.append("12345");
                try {
                    text.finishWorking();
                    fail("Expected final-String memory peak to exhaust policy");
                } catch (DocumentFailure failure) {
                    assertEquals(
                            DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                            failure.getCode());
                }
            }
        } finally {
            excess.close();
        }
    }

    @Test
    public void cmapNumberStringIsReservedBeforeMaterialization()
            throws Exception {
        byte[] cmap = "12345 ".getBytes(StandardCharsets.US_ASCII);
        WorkflowResourceContext exact = openWithMemory(10L);
        try {
            assertEquals(
                    0,
                    PdfBoxCMapPreflight.countMappings(
                            cmap, Integer.MAX_VALUE, exact));
        } finally {
            exact.close();
        }

        WorkflowResourceContext excess = openWithMemory(9L);
        try {
            try {
                PdfBoxCMapPreflight.countMappings(
                        cmap, Integer.MAX_VALUE, excess);
                fail("Expected CMap token memory to exhaust policy");
            } catch (IOException failure) {
                DocumentFailure resourceFailure =
                        WorkflowResourceContext.findResourceFailure(failure);
                assertEquals(
                        DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                        resourceFailure.getCode());
            }
        } finally {
            excess.close();
        }
    }

    @Test
    public void decodedTransportMemoryIsReleasedAtMessageBoundary()
            throws Exception {
        WorkflowResourceContext resources = openWithMemory(100L);
        try {
            byte[] payload = WorkerCodecIO.encode(100, output -> {
                output.writeString("ab");
                output.writeBytes(new byte[] {1, 2, 3});
            });
            long before = resources.getRemainingOwnedMemoryBytes();
            WorkerCodecIO.Input input = WorkerCodecIO.accountedInput(
                    payload,
                    resources);
            assertEquals("ab", input.readString());
            assertEquals(3, input.readBytes(3).length);
            assertEquals(
                    before - 13L,
                    resources.getRemainingOwnedMemoryBytes());

            input.releaseDecodedMemory();

            assertEquals(before, resources.getRemainingOwnedMemoryBytes());
        } finally {
            resources.close();
        }
    }

    @Test
    public void returnedRequestNamesRetainMemoryUntilConsumerClose()
            throws Exception {
        WorkflowResourceContext resources = openWithMemory(100L);
        try {
            byte[] sourcePayload = WorkerCodecIO.encode(100, output -> {
                output.writeInt(1);
                output.writeString("name");
                output.writeLong(10L);
            });
            WorkerMessages.SourceRequest source =
                    WorkerMessages.decodeSourceRequest(
                            sourcePayload,
                            resources);
            assertEquals(92L, resources.getRemainingOwnedMemoryBytes());
            assertEquals("name", source.getName());
            source.close();
            assertEquals(100L, resources.getRemainingOwnedMemoryBytes());

            byte[] credentialPayload = WorkerCodecIO.encode(100, output -> {
                output.writeInt(1);
                output.writeInt(WorkerMessages.SOURCE_CREDENTIAL);
                output.writeNullableString("name");
            });
            WorkerMessages.CredentialRequest credential =
                    WorkerMessages.decodeCredentialRequest(
                            credentialPayload,
                            resources);
            assertEquals(92L, resources.getRemainingOwnedMemoryBytes());
            assertEquals("name", credential.getSourceName());
            credential.close();
            assertEquals(100L, resources.getRemainingOwnedMemoryBytes());
        } finally {
            resources.close();
        }
    }

    @Test
    public void decodedCredentialReleasesMemoryWhenDestroyed()
            throws Exception {
        WorkflowResourceContext resources = openWithMemory(100L);
        try {
            byte[] payload = WorkerCodecIO.encode(100, output -> {
                output.writeInt(1);
                output.writeInt(WorkerMessages.OUTPUT_OWNER_CREDENTIAL);
                output.writeNullableString(null);
                output.writeBoolean(true);
                output.writeInt(2);
                output.writeShort('a');
                output.writeShort('b');
            });
            WorkerMessages.DecodedCredential decoded =
                    WorkerMessages.decodeCredentialResponse(
                            payload,
                            WorkerMessages.OUTPUT_OWNER_CREDENTIAL,
                            null,
                            resources);
            PasswordCredential credential = decoded.getCredential();
            assertFalse(credential.isDestroyed());
            assertEquals(96L, resources.getRemainingOwnedMemoryBytes());

            decoded.close();

            assertTrue(credential.isDestroyed());
            assertEquals(100L, resources.getRemainingOwnedMemoryBytes());
        } finally {
            resources.close();
        }
    }

    @Test
    public void inputResolverClosesBeforeWorkflowResources() throws Exception {
        final WorkflowResourceContext[] active =
                new WorkflowResourceContext[1];
        final WorkflowResourceContext.MemoryReservation[] retained =
                new WorkflowResourceContext.MemoryReservation[1];
        final boolean[] closed = new boolean[1];
        PdfBoxWorkflowEngine.InputResolver resolver =
                new PdfBoxWorkflowEngine.InputResolver() {
                    @Override
                    public void activate(WorkflowResourceContext resources)
                            throws DocumentFailure {
                        active[0] = resources;
                        retained[0] = resources.reserveOwnedMemory(1L);
                    }

                    @Override
                    public DocumentSource resolveSource(
                            String name,
                            DocumentSource source,
                            WorkflowResourceContext resources) {
                        return source;
                    }

                    @Override
                    public PasswordCredential resolveSourceCredential(
                            String sourceName,
                            PasswordCredential credential,
                            WorkflowResourceContext resources) {
                        return credential;
                    }

                    @Override
                    public PasswordSecurityPolicy resolveOutputSecurity(
                            PasswordSecurityPolicy security,
                            WorkflowResourceContext resources) {
                        return security;
                    }

                    @Override
                    public void close() {
                        assertTrue(active[0].isOpen());
                        long before = active[0]
                                .getRemainingOwnedMemoryBytes();
                        retained[0].close();
                        assertEquals(
                                before + 1L,
                                active[0].getRemainingOwnedMemoryBytes());
                        closed[0] = true;
                    }
                };
        Path target = temporaryFolder.getRoot().toPath()
                .resolve("resolver-lifecycle.pdf");
        WorkflowRequest request = WorkflowRequest.builder()
                .target("result", PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE)
                .build();
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .temporaryDirectory(temporaryFolder.getRoot().toPath())
                .build();

        new DocumentWorkflow(environment).executeWithInputResolver(
                request,
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                },
                resolver);

        assertTrue(closed[0]);
        assertFalse(active[0].isOpen());
    }

    @Test
    public void exhaustedMemoryStillReportsItsStableFailure()
            throws Exception {
        WorkflowResourceContext resources = openWithMemory(1L);
        try {
            DocumentFailure exhausted;
            try {
                resources.retainOwnedMemory(2L);
                throw new AssertionError("Expected the memory limit");
            } catch (DocumentFailure failure) {
                exhausted = failure;
            }
            byte[] key = new byte[WorkerProtocol.AUTHENTICATION_KEY_BYTES];
            ByteArrayOutputStream frames = new ByteArrayOutputStream();
            WorkerProtocol.Endpoint sender = WorkerProtocol.endpoint(
                    new ByteArrayInputStream(new byte[0]),
                    frames,
                    key,
                    4096);

            assertTrue(HardenedWorkerMain.sendFailure(
                    sender,
                    exhausted,
                    resources,
                    4096));

            WorkerProtocol.Endpoint receiver = WorkerProtocol.endpoint(
                    new ByteArrayInputStream(frames.toByteArray()),
                    new ByteArrayOutputStream(),
                    key,
                    4096);
            WorkerProtocol.Frame frame = receiver.receive();
            try {
                assertEquals(WorkerProtocol.FAILURE, frame.getOpcode());
                assertEquals(
                        DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                        WorkerMessages.decodeFailure(frame.getPayload())
                                .getCode());
            } finally {
                frame.clear();
            }
        } finally {
            resources.close();
        }
    }

    @Test
    public void closedContextUsesBoundedPostContextFailureControl()
            throws Exception {
        WorkflowResourceContext resources = openWithMemory(1L);
        resources.close();
        byte[] key = new byte[WorkerProtocol.AUTHENTICATION_KEY_BYTES];
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        WorkerProtocol.Endpoint sender = WorkerProtocol.endpoint(
                new ByteArrayInputStream(new byte[0]),
                frames,
                key,
                8);

        assertTrue(HardenedWorkerMain.sendFailure(
                sender,
                new DocumentFailure(
                        DocumentFailureCode.TEMPORARY_STORAGE_UNAVAILABLE,
                        WorkflowResourceContext.CAPABILITY_ID,
                        "The workflow temporary-storage root is unavailable."),
                resources,
                8));

        WorkerProtocol.Endpoint receiver = WorkerProtocol.endpoint(
                new ByteArrayInputStream(frames.toByteArray()),
                new ByteArrayOutputStream(),
                key,
                8);
        WorkerProtocol.Frame frame = receiver.receive();
        try {
            assertEquals(WorkerProtocol.FAILURE, frame.getOpcode());
            assertEquals(
                    DocumentFailureCode.TEMPORARY_STORAGE_UNAVAILABLE,
                    WorkerMessages.decodeFailure(frame.getPayload())
                            .getCode());
        } finally {
            frame.clear();
            sender.close();
            receiver.close();
        }
    }

    @Test
    public void decodedCollectionCapacityIsChargedBeforeAllocation()
            throws Exception {
        byte[] payload = WorkerCodecIO.encode(64, output -> {
            output.writeInt(1);
            output.writeInt(1);
            output.writeInt(1);
            output.writeInt(1);
        });
        WorkflowResourceContext exact = openWithMemory(64L);
        try {
            WorkerCodecIO.Input input = WorkerCodecIO.accountedInput(
                    payload,
                    exact);
            try {
                assertEquals(1, WorkerCommandCodec.decodeBatch(
                        input,
                        WorkerReferenceRegistry.forWorker()).size());
            } finally {
                input.releaseDecodedMemory();
            }
            assertEquals(64L, exact.getRemainingOwnedMemoryBytes());
        } finally {
            exact.close();
        }

        WorkflowResourceContext excess = openWithMemory(63L);
        try {
            WorkerCodecIO.Input input = WorkerCodecIO.accountedInput(
                    payload,
                    excess);
            try {
                WorkerCommandCodec.decodeBatch(
                        input,
                        WorkerReferenceRegistry.forWorker());
                fail("Expected decoded collection-capacity exhaustion");
            } catch (DocumentFailure failure) {
                assertEquals(
                        DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                        failure.getCode());
            } finally {
                input.releaseDecodedMemory();
            }
        } finally {
            excess.close();
        }
    }

    @Test
    public void cleanupFailureIsReportedInsteadOfReturningSuccess()
            throws Exception {
        WorkflowResourceContext resources = open(1024L);
        Path root = resources.getTemporaryRoot();
        Path retained = resources.createTemporaryFile("retained-", ".bin");
        Assume.assumeTrue(Files.getFileStore(root)
                .supportsFileAttributeView("posix"));
        Files.setPosixFilePermissions(root, EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_EXECUTE));
        try {
            try {
                resources.close();
                fail("Expected cleanup failure");
            } catch (DocumentFailure failure) {
                assertEquals(
                        DocumentFailureCode.TEMPORARY_STORAGE_UNAVAILABLE,
                        failure.getCode());
            }
        } finally {
            Files.setPosixFilePermissions(root, EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
            Files.deleteIfExists(retained);
            Files.deleteIfExists(root);
        }
    }

    @Test
    public void earlierFailureOwnsTerminalRecoveryFailurePrecedence()
            throws Exception {
        WorkflowResourceContext resources = open(1024L);
        DocumentFailure earlier = new DocumentFailure(
                DocumentFailureCode.COMMAND_REJECTED,
                PdfBoxWorkflowEngine.CAPABILITY_ID,
                "The Command was rejected.");
        DocumentFailure recovery = new DocumentFailure(
                DocumentFailureCode.WORKER_TERMINATED,
                HardenedWorkerEngine.CAPABILITY_ID,
                "The Worker terminated without a valid response.");
        try {
            resources.terminalFailure(recovery);
            resources.preferEarlierTerminalFailure(earlier);
            try {
                resources.rethrowTerminalFailure();
                fail("Expected the earlier failure to remain authoritative");
            } catch (DocumentFailure failure) {
                assertSame(earlier, failure);
            }
        } finally {
            resources.close();
        }
    }

    private WorkflowResourceContext open(long maximumTemporaryStorage)
            throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        return WorkflowResourceContext.open(
                policy(maximumTemporaryStorage),
                Clock.systemUTC(),
                CancellationToken.none(),
                null,
                root);
    }

    private WorkflowResourceContext openWithMemory(long maximumMemory)
            throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        WorkflowResourcePolicy defaults = WorkflowResourcePolicy.safeDefaults();
        WorkflowResourcePolicy policy = WorkflowResourcePolicy.builder()
                .maximumInputBytes(defaults.getMaximumInputBytes())
                .maximumPages(defaults.getMaximumPages())
                .maximumObjects(defaults.getMaximumObjects())
                .maximumNestingDepth(defaults.getMaximumNestingDepth())
                .maximumDecompressedBytes(
                        defaults.getMaximumDecompressedBytes())
                .maximumDecodedPixels(defaults.getMaximumDecodedPixels())
                .maximumOwnedMemoryBytes(maximumMemory)
                .maximumTemporaryStorageBytes(
                        defaults.getMaximumTemporaryStorageBytes())
                .maximumElapsedTime(Duration.ofMinutes(5L))
                .maximumConcurrentWorkflows(1)
                .build();
        return WorkflowResourceContext.open(
                policy,
                Clock.systemUTC(),
                CancellationToken.none(),
                null,
                root);
    }

    private static void writeAndClose(RandomAccessStreamCache cache)
            throws Exception {
        RandomAccess buffer = cache.createBuffer();
        buffer.write(1);
        buffer.close();
    }

    private static WorkflowResourcePolicy policy(long temporaryStorage) {
        WorkflowResourcePolicy defaults = WorkflowResourcePolicy.safeDefaults();
        return WorkflowResourcePolicy.builder()
                .maximumInputBytes(defaults.getMaximumInputBytes())
                .maximumPages(defaults.getMaximumPages())
                .maximumObjects(defaults.getMaximumObjects())
                .maximumNestingDepth(defaults.getMaximumNestingDepth())
                .maximumDecompressedBytes(
                        defaults.getMaximumDecompressedBytes())
                .maximumDecodedPixels(defaults.getMaximumDecodedPixels())
                .maximumOwnedMemoryBytes(
                        defaults.getMaximumOwnedMemoryBytes())
                .maximumTemporaryStorageBytes(temporaryStorage)
                .maximumElapsedTime(Duration.ofMinutes(5L))
                .maximumConcurrentWorkflows(1)
                .build();
    }
}
