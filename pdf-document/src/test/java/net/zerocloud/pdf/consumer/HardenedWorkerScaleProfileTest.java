package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.zerocloud.pdf.DocumentCommand;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowEnvironment;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.WorkflowResourcePolicy;
import net.zerocloud.pdf.WorkflowResourceUsage;
import net.zerocloud.pdf.WorkflowTransactionId;
import net.zerocloud.pdf.WorkflowTransactionState;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.query.PageCount;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Opt-in generated Foundation-scale profiles; no large fixture is retained. */
public final class HardenedWorkerScaleProfileTest {

    private static final String SCALE_PROPERTY = "folio.pdf.t22.scale";
    private static final int FOUNDATION_PAGES = 5_000;
    private static final long FOUNDATION_INPUT_BYTES = 1L << 30;

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void generatedFiveThousandPageWorkloadCompletesAndReopens()
            throws Exception {
        assumeProfile("pages");
        Path target = temporaryFolder.getRoot().toPath()
                .resolve("generated-5000-pages.pdf");
        WorkflowResourcePolicy policy = WorkflowResourcePolicy.safeDefaults();
        WorkflowEnvironment environment = environment(policy);
        List<DocumentCommand> commands = new ArrayList<DocumentCommand>(
                FOUNDATION_PAGES);
        for (int index = 0; index < FOUNDATION_PAGES; index++) {
            commands.add(AddBlankPage.INSTANCE);
        }

        long started = System.nanoTime();
        WorkflowOutcome<Integer> created = new DocumentWorkflow(environment)
                .execute(
                        createRequest(target, "scale-pages-5000"),
                        session -> {
                            session.executeBatch(commands);
                            return session.query(PageCount.INSTANCE);
                        });
        long wallNanos = System.nanoTime() - started;

        assertEquals(Integer.valueOf(FOUNDATION_PAGES), created.getResult());
        assertEquals(Integer.valueOf(FOUNDATION_PAGES),
                new DocumentWorkflow(environment).execute(
                        readRequest(target),
                        session -> session.query(PageCount.INSTANCE))
                        .getResult());
        assertTrue(created.getResourceUsage().getObservedPages()
                >= FOUNDATION_PAGES);
        assertUsageWithin(created.getResourceUsage(), policy);
        record(
                "pages",
                "5000 project-owned AddBlankPage commands; published and reopened",
                policy,
                created.getResourceUsage(),
                wallNanos,
                1);
    }

    @Test
    public void generatedExactOneGiBInputCompletesWithinStagingProfile()
            throws Exception {
        assumeProfile("input");
        WorkflowResourcePolicy policy = WorkflowResourcePolicy.safeDefaults();
        WorkflowEnvironment environment = environment(policy);
        GeneratedLargePdfStream generated = new GeneratedLargePdfStream(
                FOUNDATION_INPUT_BYTES);
        WorkflowRequest request = WorkflowRequest.builder()
                .source(
                        "generated",
                        DocumentSource.stream(
                                generated,
                                FOUNDATION_INPUT_BYTES))
                .primarySource("generated")
                .saveMode(SaveMode.REWRITE)
                .executionProfile(WorkflowExecutionProfile.HARDENED_WORKER)
                .build();

        long started = System.nanoTime();
        WorkflowOutcome<Integer> outcome = new DocumentWorkflow(environment)
                .execute(request, session -> session.query(PageCount.INSTANCE));
        long wallNanos = System.nanoTime() - started;

        WorkflowResourceUsage usage = outcome.getResourceUsage();
        assertEquals(Integer.valueOf(1), outcome.getResult());
        assertEquals(FOUNDATION_INPUT_BYTES, generated.getReadBytes());
        assertFalse(generated.isClosed());
        assertEquals(FOUNDATION_INPUT_BYTES, usage.getAcceptedInputBytes());
        assertTrue(usage.getPeakTemporaryStorageBytes()
                >= FOUNDATION_INPUT_BYTES);
        assertUsageWithin(usage, policy);
        assertNoWorkflowRoots(temporaryFolder.getRoot().toPath());
        record(
                "input",
                "exact 1-GiB generated InputStream: header, newline fill, valid one-page xref tail",
                policy,
                usage,
                wallNanos,
                1);
    }

    @Test
    public void configuredConcurrencyLimitAdmitsLimitAndRejectsFirstExcess()
            throws Exception {
        assumeProfile("concurrency");
        final int concurrency = 2;
        WorkflowResourcePolicy policy = withConcurrency(
                WorkflowResourcePolicy.safeDefaults(),
                concurrency);
        WorkflowEnvironment environment = environment(policy);
        DocumentWorkflow workflow = new DocumentWorkflow(environment);
        CountDownLatch entered = new CountDownLatch(concurrency);
        CountDownLatch release = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<Thread>();
        List<AtomicReference<Throwable>> failures =
                new ArrayList<AtomicReference<Throwable>>();
        List<AtomicReference<WorkflowOutcome<Void>>> outcomes =
                new ArrayList<AtomicReference<WorkflowOutcome<Void>>>();
        long started = System.nanoTime();
        for (int index = 0; index < concurrency; index++) {
            final int workerIndex = index;
            AtomicReference<Throwable> failure =
                    new AtomicReference<Throwable>();
            AtomicReference<WorkflowOutcome<Void>> outcome =
                    new AtomicReference<WorkflowOutcome<Void>>();
            failures.add(failure);
            outcomes.add(outcome);
            Thread thread = new Thread(() -> {
                try {
                    Path target = temporaryFolder.getRoot().toPath()
                            .resolve("concurrent-" + workerIndex + ".pdf");
                    outcome.set(workflow.execute(
                            createRequest(
                                    target,
                                    "scale-concurrent-" + workerIndex),
                            session -> {
                                entered.countDown();
                                try {
                                    if (!release.await(30L, TimeUnit.SECONDS)) {
                                        throw new AssertionError(
                                                "Timed out releasing scale workflow");
                                    }
                                } catch (InterruptedException interrupted) {
                                    Thread.currentThread().interrupt();
                                    throw new AssertionError(interrupted);
                                }
                                session.execute(AddBlankPage.INSTANCE);
                                return null;
                            }));
                } catch (Throwable thrown) {
                    failure.set(thrown);
                }
            }, "t22-scale-concurrency-" + index);
            threads.add(thread);
            thread.start();
        }

        WorkflowTransactionId excessId =
                WorkflowTransactionId.of("scale-concurrency-excess");
        Path excessTarget = temporaryFolder.getRoot().toPath()
                .resolve("concurrent-excess.pdf");
        try {
            assertTrue(entered.await(30L, TimeUnit.SECONDS));
            try {
                workflow.execute(
                        createRequest(excessTarget, excessId.getValue()),
                        session -> {
                            fail("Excess workflow work must not start");
                            return null;
                        });
                fail("Expected first excess concurrency rejection");
            } catch (DocumentFailure failure) {
                assertEquals(
                        DocumentFailureCode.CONCURRENCY_LIMIT_EXCEEDED,
                        failure.getCode());
                assertEquals(excessId, failure.getTransactionId().get());
                assertEquals(
                        PublicationStatus.NOT_ATTEMPTED,
                        failure.getPublicationReceipts().get(0).getStatus());
            }
            assertEquals(
                    WorkflowTransactionState.RECOVERABLE,
                    workflow.lookupTransaction(excessId).get().getState());
            assertFalse(Files.exists(excessTarget));
        } finally {
            release.countDown();
            for (Thread thread : threads) {
                thread.join(TimeUnit.SECONDS.toMillis(30L));
            }
        }
        for (int index = 0; index < concurrency; index++) {
            assertFalse(threads.get(index).isAlive());
            if (failures.get(index).get() != null) {
                throw new AssertionError(failures.get(index).get());
            }
            assertNotNull(outcomes.get(index).get());
            assertUsageWithin(
                    outcomes.get(index).get().getResourceUsage(),
                    policy);
        }

        WorkflowOutcome<Void> retried = workflow.execute(
                createRequest(excessTarget, excessId.getValue()),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });
        long wallNanos = System.nanoTime() - started;
        assertTrue(Files.exists(excessTarget));
        assertEquals(
                WorkflowTransactionState.COMPLETED,
                workflow.lookupTransaction(excessId).get().getState());
        assertUsageWithin(retried.getResourceUsage(), policy);
        record(
                "concurrency",
                "two held public workflows; nonblocking first excess; retry after release",
                policy,
                retried.getResourceUsage(),
                wallNanos,
                concurrency);
    }

    private WorkflowEnvironment environment(WorkflowResourcePolicy policy) {
        return WorkflowEnvironment.builder()
                .temporaryDirectory(temporaryFolder.getRoot().toPath())
                .defaultResourcePolicy(policy)
                .build();
    }

    private static WorkflowRequest createRequest(
            Path target,
            String transactionId) {
        return WorkflowRequest.builder()
                .transactionId(WorkflowTransactionId.of(transactionId))
                .target("result", PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE)
                .executionProfile(WorkflowExecutionProfile.HARDENED_WORKER)
                .build();
    }

    private static WorkflowRequest readRequest(Path source) {
        return WorkflowRequest.builder()
                .source("source", DocumentSource.path(source))
                .primarySource("source")
                .saveMode(SaveMode.REWRITE)
                .executionProfile(WorkflowExecutionProfile.HARDENED_WORKER)
                .build();
    }

    private static void assumeProfile(String profile) {
        String selected = System.getProperty(SCALE_PROPERTY, "");
        Assume.assumeTrue("all".equals(selected) || profile.equals(selected));
    }

    private static void assertUsageWithin(
            WorkflowResourceUsage usage,
            WorkflowResourcePolicy policy) {
        assertTrue(usage.isWithin(policy));
        assertTrue(usage.getPeakOwnedMemoryBytes()
                <= policy.getMaximumOwnedMemoryBytes());
        assertTrue(usage.getPeakTemporaryStorageBytes()
                <= policy.getMaximumTemporaryStorageBytes());
        assertTrue(usage.getElapsedTime().compareTo(
                policy.getMaximumElapsedTime()) <= 0);
    }

    private static void assertNoWorkflowRoots(Path parent)
            throws IOException {
        DirectoryStream<Path> entries = Files.newDirectoryStream(
                parent,
                ".folio-pdf-workflow-*");
        try {
            assertFalse(entries.iterator().hasNext());
        } finally {
            entries.close();
        }
    }

    private static WorkflowResourcePolicy withConcurrency(
            WorkflowResourcePolicy value,
            int concurrency) {
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
                .maximumElapsedTime(value.getMaximumElapsedTime())
                .maximumConcurrentWorkflows(concurrency)
                .build();
    }

    private static void record(
            String profile,
            String construction,
            WorkflowResourcePolicy policy,
            WorkflowResourceUsage usage,
            long wallNanos,
            int concurrency) {
        System.out.println("T22_SCALE"
                + " profile=" + profile
                + " os=" + quoted(System.getProperty("os.name"))
                + " osVersion=" + quoted(System.getProperty("os.version"))
                + " arch=" + quoted(System.getProperty("os.arch"))
                + " javaVendor=" + quoted(System.getProperty("java.vendor"))
                + " javaVersion=" + quoted(System.getProperty("java.version"))
                + " construction=" + quoted(construction)
                + " policyInput=" + policy.getMaximumInputBytes()
                + " policyPages=" + policy.getMaximumPages()
                + " policyObjects=" + policy.getMaximumObjects()
                + " policyNesting=" + policy.getMaximumNestingDepth()
                + " policyDecompressed="
                + policy.getMaximumDecompressedBytes()
                + " policyPixels=" + policy.getMaximumDecodedPixels()
                + " policyMemory=" + policy.getMaximumOwnedMemoryBytes()
                + " policyTemporary="
                + policy.getMaximumTemporaryStorageBytes()
                + " policyElapsed="
                + quoted(policy.getMaximumElapsedTime().toString())
                + " policyConcurrency="
                + policy.getMaximumConcurrentWorkflows()
                + " acceptedInput=" + usage.getAcceptedInputBytes()
                + " observedPages=" + usage.getObservedPages()
                + " observedObjects=" + usage.getObservedObjects()
                + " decompressed=" + usage.getDecompressedBytes()
                + " decodedPixels=" + usage.getDecodedPixels()
                + " peakMemory=" + usage.getPeakOwnedMemoryBytes()
                + " peakTemporary="
                + usage.getPeakTemporaryStorageBytes()
                + " accountedElapsed="
                + quoted(usage.getElapsedTime().toString())
                + " wallMillis="
                + TimeUnit.NANOSECONDS.toMillis(wallNanos)
                + " concurrency=" + concurrency);
    }

    private static String quoted(String value) {
        return '"' + value.replace("\\", "\\\\")
                .replace("\"", "\\\"") + '"';
    }

    /** Constant-memory clean-room PDF generator with its xref at byte 1 GiB. */
    private static final class GeneratedLargePdfStream extends InputStream {

        private static final byte[] HEADER =
                "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);

        private final long length;
        private final long tailOffset;
        private final byte[] tail;
        private long position;
        private boolean closed;

        private GeneratedLargePdfStream(long length) {
            if (length <= 1024L) {
                throw new IllegalArgumentException("length is too small");
            }
            this.length = length;
            long candidateOffset = length - 512L;
            byte[] candidate = null;
            for (int attempt = 0; attempt < 8; attempt++) {
                candidate = tail(candidateOffset);
                long exactOffset = length - candidate.length;
                if (exactOffset == candidateOffset) {
                    this.tailOffset = exactOffset;
                    this.tail = candidate;
                    return;
                }
                candidateOffset = exactOffset;
            }
            throw new IllegalStateException("Could not stabilize PDF tail");
        }

        @Override
        public int read() {
            byte[] one = new byte[1];
            int count = read(one, 0, 1);
            return count < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] bytes, int offset, int count) {
            if (bytes == null) {
                throw new NullPointerException("bytes");
            }
            if (offset < 0 || count < 0 || count > bytes.length - offset) {
                throw new IndexOutOfBoundsException();
            }
            if (count == 0) {
                return 0;
            }
            if (position >= length) {
                return -1;
            }
            int copied = (int) Math.min((long) count, length - position);
            Arrays.fill(bytes, offset, offset + copied, (byte) '\n');
            copyIntersection(
                    HEADER,
                    0L,
                    bytes,
                    offset,
                    position,
                    copied);
            copyIntersection(
                    tail,
                    tailOffset,
                    bytes,
                    offset,
                    position,
                    copied);
            position += copied;
            return copied;
        }

        @Override
        public void close() {
            closed = true;
        }

        private long getReadBytes() {
            return position;
        }

        private boolean isClosed() {
            return closed;
        }

        private static void copyIntersection(
                byte[] source,
                long sourceOffset,
                byte[] destination,
                int destinationOffset,
                long readOffset,
                int readLength) {
            long from = Math.max(sourceOffset, readOffset);
            long to = Math.min(
                    sourceOffset + source.length,
                    readOffset + readLength);
            if (from >= to) {
                return;
            }
            System.arraycopy(
                    source,
                    (int) (from - sourceOffset),
                    destination,
                    destinationOffset + (int) (from - readOffset),
                    (int) (to - from));
        }

        private static byte[] tail(long offset) {
            String object1 = "1 0 obj\n"
                    + "<< /Type /Catalog /Pages 2 0 R >>\n"
                    + "endobj\n";
            String object2 = "2 0 obj\n"
                    + "<< /Type /Pages /Kids [3 0 R] /Count 1 >>\n"
                    + "endobj\n";
            String object3 = "3 0 obj\n"
                    + "<< /Type /Page /Parent 2 0 R "
                    + "/MediaBox [0 0 72 72] >>\n"
                    + "endobj\n";
            long object1Offset = offset;
            long object2Offset = object1Offset + asciiLength(object1);
            long object3Offset = object2Offset + asciiLength(object2);
            long xrefOffset = object3Offset + asciiLength(object3);
            String xref = "xref\n0 4\n"
                    + "0000000000 65535 f \n"
                    + xrefEntry(object1Offset)
                    + xrefEntry(object2Offset)
                    + xrefEntry(object3Offset)
                    + "trailer\n<< /Size 4 /Root 1 0 R >>\n"
                    + "startxref\n" + xrefOffset + "\n%%EOF\n";
            return (object1 + object2 + object3 + xref)
                    .getBytes(StandardCharsets.US_ASCII);
        }

        private static String xrefEntry(long offset) {
            return String.format(Locale.ROOT, "%010d 00000 n \n", offset);
        }

        private static int asciiLength(String value) {
            return value.getBytes(StandardCharsets.US_ASCII).length;
        }
    }
}
