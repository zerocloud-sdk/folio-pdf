package net.zerocloud.pdf;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.query.PageCount;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Real-process checks for Worker containment and transaction isolation. */
public final class HardenedWorkerIsolationTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void callbackStaysInCallerAndWorkerDeniesAmbientCapabilities()
            throws Exception {
        Path temporaryParent = temporaryFolder.newFolder("worker-roots")
                .toPath();
        Path target = temporaryFolder.getRoot().toPath().resolve("identity.pdf");
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .temporaryDirectory(temporaryParent)
                .build();
        String callerIdentity = ManagementFactory.getRuntimeMXBean().getName();
        final String[] callbackIdentity = new String[1];
        final String[] privateRootName = new String[1];

        HardenedWorkerEngine.IsolationProbe probe =
                new DocumentWorkflow(environment).execute(
                        request(target),
                        session -> {
                            callbackIdentity[0] = ManagementFactory
                                    .getRuntimeMXBean().getName();
                            List<Path> roots = children(temporaryParent);
                            assertEquals(1, roots.size());
                            privateRootName[0] = roots.get(0)
                                    .getFileName().toString();
                            assertEquals(EnumSet.of(
                                    PosixFilePermission.OWNER_READ,
                                    PosixFilePermission.OWNER_WRITE,
                                    PosixFilePermission.OWNER_EXECUTE),
                                    ownerPermissions(roots.get(0)));
                            session.execute(AddBlankPage.INSTANCE);
                            return HardenedWorkerEngine.probeIsolation(session);
                        }).getResult();

        assertEquals(callerIdentity, callbackIdentity[0]);
        assertNotEquals(callerIdentity, probe.getWorkerProcessIdentity());
        assertTrue(probe.isOutboundNetworkDenied());
        assertTrue(probe.isListeningNetworkDenied());
        assertTrue(probe.isUnixDomainConnectDenied());
        assertTrue(probe.isUnixDomainListenDenied());
        assertTrue(probe.isDescendantProcessDenied());
        assertTrue(probe.isFilesystemEscapeDenied());
        assertTrue(probe.isCallerClassPathDenied());
        assertTrue(probe.isDeepReflectionDenied());
        assertTrue(probe.isNativePathLoadDenied());
        assertTrue(probe.isNativeLibraryLoadDenied());
        assertFalse(privateRootName[0].contains("identity"));
        assertTrue(children(temporaryParent).isEmpty());
    }

    @Test
    public void workerStderrIsDiscardedAtTheProcessBoundary()
            throws Exception {
        Path target = temporaryFolder.getRoot().toPath()
                .resolve("stderr-discard.pdf");
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream original = System.err;
        PrintStream replacement = new PrintStream(
                captured,
                true,
                StandardCharsets.UTF_8.name());
        try {
            System.setErr(replacement);
            new DocumentWorkflow().execute(request(target), session -> {
                HardenedWorkerEngine.probeIsolation(session);
                session.execute(AddBlankPage.INSTANCE);
                return null;
            });
        } finally {
            System.setErr(original);
            replacement.close();
        }

        assertFalse(new String(
                captured.toByteArray(),
                StandardCharsets.UTF_8).contains(
                        HardenedWorkerMain.STDERR_PROBE_MARKER));
        assertTrue(Files.exists(target));
    }

    @Test
    public void childCanOwnMoreThanHalfTheAggregateMemoryBudget()
            throws Exception {
        long maximumMemory = 4L * 1024L * 1024L;
        long childAllocation = 3L * 1024L * 1024L;
        Path target = temporaryFolder.getRoot().toPath()
                .resolve("child-heavy-memory.pdf");

        new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .target("result", PublicationTarget.path(target))
                        .saveMode(SaveMode.REWRITE)
                        .resourcePolicy(memoryPolicy(maximumMemory))
                        .executionProfile(
                                WorkflowExecutionProfile.HARDENED_WORKER)
                        .build(),
                session -> {
                    HardenedWorkerEngine.probeOwnedMemory(
                            session,
                            childAllocation);
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });

        assertTrue(Files.exists(target));
    }

    @Test
    public void aggregateMemoryAllowsTheExactByteAndRejectsTheFirstExcess()
            throws Exception {
        long maximumMemory = 1024L * 1024L;
        Path exact = temporaryFolder.getRoot().toPath()
                .resolve("aggregate-memory-exact.pdf");
        new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .target("result", PublicationTarget.path(exact))
                        .saveMode(SaveMode.REWRITE)
                        .resourcePolicy(memoryPolicy(maximumMemory))
                        .executionProfile(
                                WorkflowExecutionProfile.HARDENED_WORKER)
                        .build(),
                session -> {
                    HardenedWorkerEngine.probeOwnedMemoryBoundary(
                            session,
                            false);
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });
        assertTrue(Files.exists(exact));

        Path excess = temporaryFolder.getRoot().toPath()
                .resolve("aggregate-memory-excess.pdf");
        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .target("result", PublicationTarget.path(excess))
                            .saveMode(SaveMode.REWRITE)
                            .resourcePolicy(memoryPolicy(maximumMemory))
                            .executionProfile(
                                    WorkflowExecutionProfile.HARDENED_WORKER)
                            .build(),
                    session -> {
                        HardenedWorkerEngine.probeOwnedMemoryBoundary(
                                session,
                                true);
                        return null;
                    });
            fail("Expected the aggregate first-excess byte to fail");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                    failure.getCode());
        }
        assertFalse(Files.exists(excess));
    }

    @Test
    public void initializationCollectionsRemainChargedAfterReady()
            throws Exception {
        int manyTargetCount = 64;
        long baseline = exactBoundaryAfterInitialization(1, false);
        long manyTargets = exactBoundaryAfterInitialization(
                manyTargetCount,
                false);

        assertTrue(baseline - manyTargets
                >= (manyTargetCount - 1L)
                        * WorkerCodecIO.DECODED_COLLECTION_ENTRY_BYTES);
        exactBoundaryAfterInitialization(manyTargetCount, true);
    }

    @Test
    public void workerClasspathExcludesCallerAndTestCode() throws Exception {
        List<String> entries = Arrays.asList(
                HardenedWorkerEngine.workerClassPath().split(
                        java.util.regex.Pattern.quote(File.pathSeparator)));

        assertFalse(entries.contains(codeSource(
                HardenedWorkerIsolationTest.class)));
        assertFalse(entries.contains(codeSource(org.junit.Test.class)));
        assertTrue(entries.contains(codeSource(HardenedWorkerMain.class)));
        assertTrue(entries.contains(codeSource(
                org.apache.pdfbox.pdmodel.PDDocument.class)));
    }

    @Test
    public void exactProjectClassInventoryAcceptsRepackedArtifact()
            throws Exception {
        HardenedWorkerEngine.requireProjectOnlyCodeSource(
                projectArchive("project-only.jar", null));
    }

    @Test
    public void classpathDelimiterAndWildcardArtifactsAreRefused()
            throws Exception {
        org.junit.Assume.assumeTrue(File.pathSeparatorChar == ':');
        Path delimiter = projectArchive("project:only.jar", null);
        Path wildcard = projectArchive("*", null);

        HardenedWorkerEngine.requireProjectOnlyCodeSource(delimiter);
        HardenedWorkerEngine.requireProjectOnlyCodeSource(wildcard);
        assertUnavailableClassPathEntry(delimiter);
        assertUnavailableClassPathEntry(wildcard);
    }

    @Test
    public void samePackageApplicationClassIsRefusedBeforeWorkerLaunch()
            throws Exception {
        Path mixed = projectArchive(
                "same-package-application.jar",
                "net/zerocloud/pdf/application/Caller.class");

        assertUnavailableCodeSource(mixed);
    }

    @Test
    public void multiReleaseClassIsRefusedBeforeWorkerLaunch()
            throws Exception {
        Path mixed = projectArchive(
                "multi-release-application.jar",
                "META-INF/versions/9/net/zerocloud/pdf/Caller.class");

        assertUnavailableCodeSource(mixed);
    }

    @Test
    public void mixedPdfBoxServiceProviderJarIsRefusedBeforeWorkerLaunch()
            throws Exception {
        Path original = Paths.get(codeSource(
                org.apache.pdfbox.pdmodel.PDDocument.class));
        Path mixed = temporaryFolder.newFile("mixed-pdfbox.jar").toPath();
        Files.copy(original, mixed, StandardCopyOption.REPLACE_EXISTING);
        HardenedWorkerEngine.requirePdfBoxCodeSource(mixed);

        URI archiveUri = URI.create("jar:" + mixed.toUri().toString());
        try (FileSystem archive = FileSystems.newFileSystem(
                archiveUri,
                Collections.<String, String>emptyMap())) {
            Path service = archive.getPath(
                    "/META-INF/services/javax.imageio.spi.ImageReaderSpi");
            Files.createDirectories(service.getParent());
            Files.write(
                    service,
                    "net.zerocloud.pdf.application.Reader\n".getBytes(
                            StandardCharsets.US_ASCII));
        }

        assertUnavailablePdfBoxCodeSource(mixed);
    }

    private static void assertUnavailableCodeSource(Path mixed)
            throws Exception {
        try {
            HardenedWorkerEngine.requireProjectOnlyCodeSource(mixed);
            fail("Expected mixed code source rejection");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.WORKER_UNAVAILABLE,
                    failure.getCode());
        }
    }

    private static void assertUnavailablePdfBoxCodeSource(Path mixed)
            throws Exception {
        try {
            HardenedWorkerEngine.requirePdfBoxCodeSource(mixed);
            fail("Expected mixed dependency code source rejection");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.WORKER_UNAVAILABLE,
                    failure.getCode());
        }
    }

    private static void assertUnavailableClassPathEntry(Path path)
            throws Exception {
        try {
            HardenedWorkerEngine.requireLiteralClassPathEntry(path);
            fail("Expected ambiguous class path entry rejection");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.WORKER_UNAVAILABLE,
                    failure.getCode());
        }
    }

    @Test
    public void concurrentAndSequentialTransactionsUseDisjointCleanRoots()
            throws Exception {
        Path temporaryParent = temporaryFolder.newFolder("isolated-roots")
                .toPath();
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .temporaryDirectory(temporaryParent)
                .build();
        CountDownLatch rootsReady = new CountDownLatch(2);
        CountDownLatch probesDone = new CountDownLatch(2);
        String[] rootNames = new String[2];
        byte[][] markers = new byte[][] {
            new byte[] {10, 11, 12},
            new byte[] {20, 21, 22}
        };
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> futures = new ArrayList<Future<Boolean>>();
            for (int index = 0; index < 2; index++) {
                final int transaction = index;
                futures.add(executor.submit(() -> new DocumentWorkflow(environment)
                        .execute(
                                request(temporaryFolder.getRoot().toPath()
                                        .resolve("parallel-" + transaction + ".pdf")),
                                session -> {
                                    HardenedWorkerEngine.IsolationProbe own =
                                            HardenedWorkerEngine
                                                    .probeIsolation(session);
                                    rootNames[transaction] =
                                            own.getWorkerRootName();
                                    writeMarker(
                                            temporaryParent.resolve(
                                                    rootNames[transaction])
                                                    .resolve("isolation-marker"),
                                            markers[transaction]);
                                    rootsReady.countDown();
                                    await(rootsReady);
                                    int other = 1 - transaction;
                                    HardenedWorkerEngine.IsolationProbe probe =
                                            HardenedWorkerEngine.probeIsolation(
                                                    session,
                                                    rootNames[other]);
                                    probesDone.countDown();
                                    await(probesDone);
                                    assertArrayEquals(
                                            markers[other],
                                            readMarker(temporaryParent.resolve(
                                                    rootNames[other])
                                                    .resolve("isolation-marker")));
                                    session.execute(AddBlankPage.INSTANCE);
                                    return Boolean.valueOf(
                                            probe.isFilesystemEscapeDenied());
                                }).getResult().booleanValue()));
            }
            assertTrue(rootsReady.await(5L, TimeUnit.SECONDS));
            List<Path> concurrentRoots = children(temporaryParent);
            assertEquals(2, concurrentRoots.size());
            assertNotEquals(
                    concurrentRoots.get(0).getFileName(),
                    concurrentRoots.get(1).getFileName());
            for (Future<Boolean> future : futures) {
                assertTrue(future.get(10L, TimeUnit.SECONDS).booleanValue());
            }
        } finally {
            executor.shutdownNow();
        }
        assertTrue(children(temporaryParent).isEmpty());

        final String[] priorRootName = new String[1];
        new DocumentWorkflow(environment).execute(
                request(temporaryFolder.getRoot().toPath()
                        .resolve("sequential-first.pdf")),
                session -> {
                    priorRootName[0] = HardenedWorkerEngine
                            .probeIsolation(session)
                            .getWorkerRootName();
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });
        assertTrue(children(temporaryParent).isEmpty());

        Path residue = temporaryParent.resolve(priorRootName[0]);
        Path marker = residue.resolve("isolation-marker");
        byte[] residueValue = new byte[] {31, 32, 33};
        Files.createDirectory(residue);
        Files.setPosixFilePermissions(residue, EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE));
        Files.write(marker, residueValue);
        try {
            Boolean denied = new DocumentWorkflow(environment).execute(
                    request(temporaryFolder.getRoot().toPath()
                            .resolve("sequential-second.pdf")),
                    session -> {
                        HardenedWorkerEngine.IsolationProbe probe =
                                HardenedWorkerEngine.probeIsolation(
                                        session,
                                        priorRootName[0]);
                        assertNotEquals(
                                priorRootName[0],
                                probe.getWorkerRootName());
                        session.execute(AddBlankPage.INSTANCE);
                        return Boolean.valueOf(
                                probe.isFilesystemEscapeDenied());
                    }).getResult();
            assertTrue(denied.booleanValue());
            assertArrayEquals(residueValue, Files.readAllBytes(marker));
        } finally {
            Files.deleteIfExists(marker);
            Files.deleteIfExists(residue);
        }
        assertTrue(children(temporaryParent).isEmpty());
    }

    @Test
    public void physicalElapsedLimitDuringStartupPreservesItsFailureCode()
            throws Exception {
        Path target = temporaryFolder.getRoot().toPath().resolve("startup-timeout.pdf");
        byte[] original = new byte[] {1, 2, 3, 4};
        Files.write(target, original);
        WorkflowEnvironment environment = WorkflowEnvironment.withClock(
                Clock.fixed(Instant.parse("2000-01-01T00:00:00Z"), ZoneOffset.UTC));
        WorkflowResourcePolicy shortRun = copyWithElapsed(
                WorkflowResourcePolicy.safeDefaults(), Duration.ofMillis(75L));
        // A fixed logical Clock leaves the physical Worker watchdog responsible
        // for stopping startup while the caller waits for the first response.
        try {
            new DocumentWorkflow(environment).execute(
                    WorkflowRequest.builder()
                            .target("result", PublicationTarget.path(target))
                            .saveMode(SaveMode.REWRITE)
                            .resourcePolicy(shortRun)
                            .executionProfile(WorkflowExecutionProfile.HARDENED_WORKER)
                            .build(),
                    session -> {
                        session.execute(AddBlankPage.INSTANCE);
                        return null;
                    });
            fail("Expected physical elapsed-time termination");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.ELAPSED_TIME_LIMIT_EXCEEDED,
                    failure.getCode());
        }
        assertArrayEquals(original, Files.readAllBytes(target));
    }

    @Test
    public void elapsedLimitHardStopsWorkerAndPreservesTarget()
            throws Exception {
        Path target = temporaryFolder.getRoot().toPath().resolve("timeout.pdf");
        byte[] original = new byte[] {1, 2, 3, 4};
        Files.write(target, original);
        WorkflowResourcePolicy defaults = WorkflowResourcePolicy.safeDefaults();
        WorkflowResourcePolicy shortRun = copyWithElapsed(
                defaults,
                Duration.ofMillis(200L));
        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .target("result", PublicationTarget.path(target))
                            .saveMode(SaveMode.REWRITE)
                            .resourcePolicy(shortRun)
                            .executionProfile(
                                    WorkflowExecutionProfile.HARDENED_WORKER)
                            .build(),
                    session -> {
                        try {
                            Thread.sleep(500L);
                        } catch (InterruptedException failure) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(failure);
                        }
                        session.query(PageCount.INSTANCE);
                        return null;
                    });
            fail("Expected elapsed-time termination");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.ELAPSED_TIME_LIMIT_EXCEEDED,
                    failure.getCode());
        }
        assertTrue(java.util.Arrays.equals(original, Files.readAllBytes(target)));
    }

    private long exactBoundaryAfterInitialization(
            int targetCount,
            boolean firstExcess) throws Exception {
        long maximumMemory = 4L * 1024L * 1024L;
        WorkflowRequest.Builder request = WorkflowRequest.builder()
                .saveMode(SaveMode.REWRITE)
                .resourcePolicy(memoryPolicy(maximumMemory))
                .executionProfile(WorkflowExecutionProfile.HARDENED_WORKER);
        for (int index = 0; index < targetCount; index++) {
            String suffix = String.format(Locale.ROOT, "%03d", index);
            request.target(
                    "t" + suffix,
                    PublicationTarget.path(temporaryFolder.getRoot().toPath()
                            .resolve("retained-target-" + suffix + ".pdf")));
        }

        if (firstExcess) {
            try {
                new DocumentWorkflow().execute(request.build(), session -> {
                    HardenedWorkerEngine.probeOwnedMemoryBoundary(
                            session,
                            true);
                    return null;
                });
                fail("Expected the post-READY first-excess byte to fail");
            } catch (DocumentFailure failure) {
                assertEquals(
                        DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                        failure.getCode());
            }
            return -1L;
        }

        final long[] amount = new long[1];
        RuntimeException marker = new RuntimeException(
                "stop after retained-memory probe");
        try {
            new DocumentWorkflow().execute(request.build(), session -> {
                amount[0] = HardenedWorkerEngine.probeOwnedMemoryBoundary(
                        session,
                        false);
                throw marker;
            });
            fail("Expected the probe marker");
        } catch (RuntimeException failure) {
            if (failure != marker) {
                throw failure;
            }
        }
        return amount[0];
    }

    private static WorkflowRequest request(Path target) {
        return WorkflowRequest.builder()
                .target("result", PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE)
                .executionProfile(WorkflowExecutionProfile.HARDENED_WORKER)
                .build();
    }

    private static WorkflowResourcePolicy copyWithElapsed(
            WorkflowResourcePolicy value,
            Duration elapsed) {
        return WorkflowResourcePolicy.builder()
                .maximumInputBytes(value.getMaximumInputBytes())
                .maximumPages(value.getMaximumPages())
                .maximumObjects(value.getMaximumObjects())
                .maximumNestingDepth(value.getMaximumNestingDepth())
                .maximumDecompressedBytes(value.getMaximumDecompressedBytes())
                .maximumDecodedPixels(value.getMaximumDecodedPixels())
                .maximumOwnedMemoryBytes(value.getMaximumOwnedMemoryBytes())
                .maximumTemporaryStorageBytes(
                        value.getMaximumTemporaryStorageBytes())
                .maximumElapsedTime(elapsed)
                .maximumConcurrentWorkflows(value.getMaximumConcurrentWorkflows())
                .build();
    }

    private static WorkflowResourcePolicy memoryPolicy(long memory) {
        WorkflowResourcePolicy defaults = WorkflowResourcePolicy
                .safeDefaults();
        return WorkflowResourcePolicy.builder()
                .maximumInputBytes(defaults.getMaximumInputBytes())
                .maximumPages(defaults.getMaximumPages())
                .maximumObjects(defaults.getMaximumObjects())
                .maximumNestingDepth(defaults.getMaximumNestingDepth())
                .maximumDecompressedBytes(
                        defaults.getMaximumDecompressedBytes())
                .maximumDecodedPixels(defaults.getMaximumDecodedPixels())
                .maximumOwnedMemoryBytes(memory)
                .maximumTemporaryStorageBytes(
                        defaults.getMaximumTemporaryStorageBytes())
                .maximumElapsedTime(defaults.getMaximumElapsedTime())
                .maximumConcurrentWorkflows(
                        defaults.getMaximumConcurrentWorkflows())
                .build();
    }

    private static List<Path> children(Path directory) {
        try (java.util.stream.Stream<Path> stream = Files.list(directory)) {
            return stream.collect(java.util.stream.Collectors.toList());
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5L, TimeUnit.SECONDS)) {
                throw new AssertionError("Concurrent Worker did not rendezvous");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    private static void writeMarker(Path path, byte[] value) {
        try {
            Files.write(path, value);
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static byte[] readMarker(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static java.util.Set<PosixFilePermission> ownerPermissions(
            Path directory) {
        try {
            return Files.getPosixFilePermissions(directory);
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static String codeSource(Class<?> type) {
        try {
            return Paths.get(type.getProtectionDomain()
                    .getCodeSource().getLocation().toURI())
                    .toRealPath().toString();
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private Path projectArchive(String name, String additionalClass)
            throws Exception {
        Path root = Paths.get(codeSource(HardenedWorkerMain.class));
        Path result = temporaryFolder.newFile(name).toPath();
        try (JarOutputStream archive = new JarOutputStream(
                Files.newOutputStream(result));
                java.util.stream.Stream<Path> paths = Files.walk(root)) {
            java.util.Iterator<Path> entries = paths.iterator();
            while (entries.hasNext()) {
                Path entry = entries.next();
                if (Files.isRegularFile(entry)) {
                    addArchiveEntry(archive, root, entry);
                }
            }
            if (additionalClass != null) {
                addArchiveEntry(archive, additionalClass);
            }
        }
        return result;
    }

    private static void addArchiveEntry(
            JarOutputStream archive,
            Path root,
            Path entry) throws java.io.IOException {
        String name = root.relativize(entry).toString()
                .replace(File.separatorChar, '/');
        archive.putNextEntry(new JarEntry(name));
        Files.copy(entry, archive);
        archive.closeEntry();
    }

    private static void addArchiveEntry(
            JarOutputStream archive,
            String name) throws java.io.IOException {
        archive.putNextEntry(new JarEntry(name));
        archive.write(0);
        archive.closeEntry();
    }
}
