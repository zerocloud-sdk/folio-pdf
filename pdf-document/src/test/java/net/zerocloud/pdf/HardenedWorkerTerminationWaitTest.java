package net.zerocloud.pdf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.query.PageCount;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Real Worker termination observed through public publication and lookup. */
public final class HardenedWorkerTerminationWaitTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test(timeout = 10000)
    public void successfulExitDuringDelayedParentCheckpointCompletesPublication()
            throws Exception {
        completesPublication(ExitMode.PARENT_DELAY);
    }

    @Test(timeout = 10000)
    public void delayedExitBeyondOneSecondCompletesPublication() throws Exception {
        completesPublication(ExitMode.CHILD_DELAY);
    }

    private void completesPublication(ExitMode mode) throws Exception {
        Path root = temporaryFolder.getRoot().toPath();
        Path launcherHome = root.resolve("delayed-launcher");
        Path release = launcherHome.resolve(".t83-parent-checkpoint-release");
        String originalHome = System.getProperty("java.home");
        ExitCheckpointClock clock = new ExitCheckpointClock(release, mode);
        try {
            createDelayedLauncher(Paths.get(originalHome), launcherHome, mode);
            assertFalse("Release signal must not alias a JDK entry",
                    Files.exists(release));
            System.setProperty("java.home", launcherHome.toString());
            DocumentWorkflow workflow = new DocumentWorkflow(
                    WorkflowEnvironment.builder().clock(clock)
                            .temporaryDirectory(root).build());
            Path target = root.resolve("completed.pdf");
            WorkflowTransactionId id = WorkflowTransactionId.of("delayed-parent-exit");
            WorkflowOutcome<Void> outcome = workflow.execute(
                    WorkflowRequest.builder().transactionId(id)
                            .executionProfile(WorkflowExecutionProfile.HARDENED_WORKER)
                            .target("result", PublicationTarget.path(target))
                            .saveMode(SaveMode.REWRITE).build(),
                    session -> {
                        clock.observeActualProcess(session);
                        session.execute(AddBlankPage.INSTANCE);
                        return null;
                    });

            assertTrue("The actual post-wait checkpoint must be exercised",
                    clock.delayed.get());
            assertEquals("The real managed process must exit successfully", 0,
                    clock.process.exitValue());
            if (mode == ExitMode.PARENT_DELAY) {
                assertTrue("The process exited zero before the parent resumed", clock.exitedZero);
            } else {
                assertTrue("The child must remain alive beyond the old one-second grace",
                        System.nanoTime() - clock.exitWaitStarted >= TimeUnit.MILLISECONDS.toNanos(1400));
            }
            assertFalse(clock.process.isAlive());
            assertEquals(PublicationStatus.COMMITTED,
                    outcome.getPublicationReceipts().get(0).getStatus());
            WorkflowTransactionStatus status = workflow.lookupTransaction(id).get();
            assertEquals(WorkflowTransactionState.COMPLETED, status.getState());
            assertEquals(PublicationStatus.COMMITTED,
                    status.getPublicationReceipts().get(0).getStatus());
            assertTrue(Files.isRegularFile(target));
            System.setProperty("java.home", originalHome);
            Integer pages = new DocumentWorkflow().execute(
                    WorkflowRequest.builder().source("source", DocumentSource.path(target))
                            .primarySource("source").saveMode(SaveMode.REWRITE).build(),
                    session -> session.query(PageCount.INSTANCE)).getResult();
            assertEquals(Integer.valueOf(1), pages);
            try (DirectoryStream<Path> roots = Files.newDirectoryStream(
                    root, ".folio-pdf-workflow-*")) {
                assertFalse("Owned workflow storage must be released",
                        roots.iterator().hasNext());
            }
        } finally {
            System.setProperty("java.home", originalHome);
            cleanLauncher(launcherHome, release, clock.process);
        }
    }

    // This case exercises the actual 30-second bound, beyond ordinary 10-second
    // JUnit timeouts. No synthetic shortened product grace certifies it.
    @Test(timeout = 60000)
    public void stuckExitRetainsElapsedLivenessAndChildStateDiagnostics() throws Exception {
        DocumentFailure failure = failsBeforePublication(ExitMode.STUCK,
                DocumentFailureCode.WORKER_TERMINATED);
        String diagnostic = failure.getDiagnostic();
        System.out.println("T22_WORKER_EXIT_GRACE " + diagnostic);
        assertTrue(diagnostic, diagnostic.startsWith(
                "The Worker did not terminate after completing its response."));
        assertTrue(diagnostic, diagnostic.contains("exitGraceSeconds=30"));
        assertTrue(diagnostic, diagnostic.contains("childAlive=true"));
        assertTrue(diagnostic, diagnostic.matches(".*childPid=[0-9]+.*"));
        assertTrue(diagnostic, diagnostic.matches(".*childState=[RSDTtXZPIWK].*"));
        assertTrue(diagnostic, diagnostic.matches(".*threadStates=[RSDTtXZPIWK,]+.*"));
        Matcher elapsed = Pattern.compile("elapsedSinceFinishedMillis=([0-9]+)").matcher(diagnostic);
        assertTrue(diagnostic, elapsed.find());
        assertTrue(diagnostic, Long.parseLong(elapsed.group(1)) >= 30000L);
        assertTrue("Only bounded non-sensitive state may be retained", diagnostic.length() < 1024);
        assertFalse(diagnostic, diagnostic.contains(temporaryFolder.getRoot().toString()));
    }

    @Test(timeout = 10000)
    public void cancellationDuringExitWaitPreventsPublication() throws Exception {
        failsBeforePublication(ExitMode.CANCEL, DocumentFailureCode.WORKFLOW_CANCELLED);
    }

    @Test(timeout = 10000)
    public void callerDeadlineDuringExitWaitPreventsPublication() throws Exception {
        failsBeforePublication(ExitMode.DEADLINE, DocumentFailureCode.DEADLINE_EXCEEDED);
    }

    @Test(timeout = 10000)
    public void fiveMinuteElapsedBoundDuringExitWaitPreventsPublication() throws Exception {
        failsBeforePublication(ExitMode.ELAPSED, DocumentFailureCode.ELAPSED_TIME_LIMIT_EXCEEDED);
    }

    @Test(timeout = 10000)
    public void interruptedExitWaitRetainsDiagnosticsAndPreventsPublication() throws Exception {
        try {
            DocumentFailure failure = failsBeforePublication(ExitMode.INTERRUPT,
                    DocumentFailureCode.WORKER_TERMINATED);
            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(failure.getDiagnostic(), failure.getDiagnostic().contains("elapsedSinceFinishedMillis="));
            assertTrue(failure.getDiagnostic(), failure.getDiagnostic().contains("childAlive=true"));
            assertTrue(failure.getDiagnostic(), failure.getDiagnostic().contains("childState="));
        } finally {
            Thread.interrupted();
        }
    }

    @Test(timeout = 10000)
    public void nonzeroExitAfterFinishedRetainsDiagnosticsAndPreventsPublication() throws Exception {
        DocumentFailure failure = failsBeforePublication(ExitMode.NONZERO,
                DocumentFailureCode.WORKER_TERMINATED);
        assertTrue(failure.getDiagnostic(), failure.getDiagnostic().contains("childAlive=false"));
        assertTrue(failure.getDiagnostic(), failure.getDiagnostic().contains("childExitCode=37"));
    }

    private DocumentFailure failsBeforePublication(ExitMode mode, DocumentFailureCode code)
            throws Exception {
        Path root = temporaryFolder.getRoot().toPath();
        Path launcherHome = root.resolve("delayed-launcher");
        Path release = launcherHome.resolve(".t83-parent-checkpoint-release");
        String originalHome = System.getProperty("java.home");
        ExitCheckpointClock clock = new ExitCheckpointClock(release, mode);
        byte[] sentinel = "caller-owned target".getBytes(StandardCharsets.UTF_8);
        Path first = root.resolve("first.pdf"), second = root.resolve("second.pdf");
        Files.write(first, sentinel);
        Files.write(second, sentinel);
        try {
            createDelayedLauncher(Paths.get(originalHome), launcherHome, mode);
            System.setProperty("java.home", launcherHome.toString());
            DocumentWorkflow workflow = new DocumentWorkflow(WorkflowEnvironment.builder()
                    .clock(clock).temporaryDirectory(root).build());
            WorkflowTransactionId id = WorkflowTransactionId.of("exit-wait-failure");
            WorkflowRequest.Builder request = WorkflowRequest.builder().transactionId(id)
                    .executionProfile(WorkflowExecutionProfile.HARDENED_WORKER)
                    .target("first", PublicationTarget.path(first))
                    .target("second", PublicationTarget.path(second))
                    .cancellationToken(clock.cancelled).saveMode(SaveMode.REWRITE);
            if (mode == ExitMode.DEADLINE) { request.deadline(clock.started.plusSeconds(10)); }
            assertEquals(java.time.Duration.ofMinutes(5),
                    WorkflowResourcePolicy.safeDefaults().getMaximumElapsedTime());
            try {
                workflow.execute(request.build(), session -> {
                    clock.observeActualProcess(session);
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });
                fail("Expected a safe failure before publication");
                return null;
            } catch (DocumentFailure failure) {
                // Java 8 NIO rejects reads while interrupted. First qualify
                // the caller's preserved flag, then clear it only for fixture
                // inspection and restore it before returning to the test.
                boolean interrupted = Thread.interrupted();
                try {
                    if (mode == ExitMode.INTERRUPT) {
                        assertTrue("The workflow must preserve caller interruption", interrupted);
                    }
                    assertTrue("The actual post-FINISHED checkpoint must be reached", clock.delayed.get());
                    assertEquals(code, failure.getCode());
                    assertFalse("The established operational capability must be retained",
                            failure.getCapabilityId().isEmpty());
                    if (code == DocumentFailureCode.WORKER_TERMINATED) {
                        assertEquals(HardenedWorkerEngine.CAPABILITY_ID, failure.getCapabilityId());
                    }
                    assertEquals(id, failure.getTransactionId().get());
                    assertEquals(2, failure.getPublicationReceipts().size());
                    assertEquals("first", failure.getPublicationReceipts().get(0).getTargetName());
                    assertEquals("second", failure.getPublicationReceipts().get(1).getTargetName());
                    for (PublicationReceipt receipt : failure.getPublicationReceipts()) {
                        assertEquals(PublicationStatus.NOT_ATTEMPTED, receipt.getStatus());
                    }
                    assertFalse("Cleanup must confirm termination of the actual child", clock.process.isAlive());
                    org.junit.Assert.assertArrayEquals(sentinel, Files.readAllBytes(first));
                    org.junit.Assert.assertArrayEquals(sentinel, Files.readAllBytes(second));
                    WorkflowTransactionStatus status = workflow.lookupTransaction(id).get();
                    assertEquals(WorkflowTransactionState.RECOVERABLE, status.getState());
                    assertEquals(code, status.getFailureCode().get());
                    assertEquals(2, status.getPublicationReceipts().size());
                    for (PublicationReceipt receipt : status.getPublicationReceipts()) {
                        assertEquals(PublicationStatus.NOT_ATTEMPTED, receipt.getStatus());
                    }
                    try (DirectoryStream<Path> roots = Files.newDirectoryStream(root, ".folio-pdf-workflow-*")) {
                        assertFalse("Owned workflow storage must be released", roots.iterator().hasNext());
                    }
                    String diagnostic = failure.getDiagnostic();
                    assertTrue(diagnostic, diagnostic.contains("elapsedSinceFinishedMillis="));
                    assertTrue(diagnostic, diagnostic.matches(".*childAlive=(true|false).*"));
                    assertTrue(diagnostic, diagnostic.contains("childExitCode="));
                    assertTrue(diagnostic, diagnostic.contains("childPid="));
                    assertTrue(diagnostic, diagnostic.contains("childState="));
                    assertTrue(diagnostic, diagnostic.contains("threadStates="));
                    assertTrue("Diagnostics must remain bounded", diagnostic.length() < 1024);
                    assertFalse(diagnostic, diagnostic.contains(root.toString()));
                    return failure;
                } finally {
                    if (interrupted) { Thread.currentThread().interrupt(); }
                }
            }
        } finally {
            System.setProperty("java.home", originalHome);
            // Cleanup may wait while preserving the interrupted caller's flag.
            boolean interrupted = Thread.interrupted();
            try { cleanLauncher(launcherHome, release, clock.process); }
            finally { if (interrupted) { Thread.currentThread().interrupt(); } }
        }
    }

    private static void cleanLauncher(Path launcherHome, Path release, Process process)
            throws Exception {
        if (Files.isDirectory(launcherHome) && !Files.exists(release)) { Files.createFile(release); }
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            assertTrue("Fixture must not leave the real process alive", process.waitFor(5, TimeUnit.SECONDS));
        }
        if (Files.exists(launcherHome)) {
            // Do not follow the fixture's links into the actual JDK.
            try (Stream<Path> paths = Files.walk(launcherHome)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                    Files.delete(path);
                }
            }
        }
    }

    private static void createDelayedLauncher(Path original, Path home, ExitMode mode)
            throws Exception {
        Files.createDirectories(home.resolve("bin"));
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(original)) {
            for (Path entry : entries) {
                if (!"bin".equals(entry.getFileName().toString())) {
                    Files.createSymbolicLink(home.resolve(entry.getFileName()), entry);
                }
            }
        }
        // The actual JVM and Worker run unchanged. Only the caller-side launcher
        // stays alive until the parent checkpoint releases it. Both sides are
        // bounded; parent descheduling before requireExited cannot skip injection.
        // This fixture is outside the product and qualified runtime closure.
        String java = original.resolve("bin/java").toString().replace("'", "'\"'\"'");
        String release = home.resolve(".t83-parent-checkpoint-release")
                .toString().replace("'", "'\"'\"'");
        Path launcher = home.resolve("bin/java");
        Files.write(launcher, ("#!/bin/sh\n'" + java
                + "' \"$@\"\nstatus=$?\ncount=0\n"
                + "while [ ! -f '" + release + "' ] && [ \"$count\" -lt 5000 ]; do\n"
                + "  /bin/sleep 0.01\n  count=$((count + 1))\ndone\n"
                + "[ -f '" + release + "' ] || exit 125\n"
                + (mode == ExitMode.CHILD_DELAY ? "/bin/sleep 1.5\n" : "")
                + (mode == ExitMode.NONZERO ? "exit 37\n" : "exit \"$status\"\n"))
                .getBytes(StandardCharsets.UTF_8));
        assertTrue("Fixture launcher must be executable",
                launcher.toFile().setExecutable(true, true));
    }

    private enum ExitMode { PARENT_DELAY, CHILD_DELAY, STUCK, CANCEL, DEADLINE, ELAPSED, INTERRUPT, NONZERO }

    private static final class ExitCheckpointClock extends Clock {
        private final Path release;
        private final ExitMode mode;
        private final AtomicBoolean delayed = new AtomicBoolean();
        private final CancellationToken cancelled = CancellationToken.create();
        private final Instant started = Instant.now();
        private volatile Process process;
        private volatile boolean exitedZero;
        private long exitWaitStarted;

        private ExitCheckpointClock(Path release, ExitMode mode) {
            this.release = release;
            this.mode = mode;
        }

        void observeActualProcess(DocumentSession session) {
            // Reflection observes lifecycle only; failure assertions use the
            // public workflow, receipt, lookup and reopened document above.
            try {
                Field connection = session.getClass().getDeclaredField("connection");
                connection.setAccessible(true);
                Object value = connection.get(session);
                Field child = value.getClass().getDeclaredField("process");
                child.setAccessible(true);
                process = (Process) child.get(value);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Actual Worker process observation failed", failure);
            }
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!getZone().equals(zone)) {
                throw new IllegalArgumentException("Fixture requires UTC");
            }
            return this;
        }

        @Override
        public Instant instant() {
            for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
                if (frame.getClassName().equals(
                        "net.zerocloud.pdf.HardenedWorkerEngine$WorkerConnection")
                        && frame.getMethodName().equals("requireExited")
                        && delayed.compareAndSet(false, true)) {
                    try {
                        exitWaitStarted = System.nanoTime();
                        if (mode == ExitMode.PARENT_DELAY || mode == ExitMode.CHILD_DELAY || mode == ExitMode.NONZERO) {
                            Files.createFile(release);
                        }
                        if (mode == ExitMode.PARENT_DELAY) {
                            // Model the parent being descheduled across the old deadline.
                            Thread.sleep(1200);
                            exitedZero = process != null
                                    && process.waitFor(500, TimeUnit.MILLISECONDS)
                                    && process.exitValue() == 0;
                            assertTrue("Real process must exit zero before parent resumes", exitedZero);
                        } else if (mode == ExitMode.CANCEL) {
                            cancelled.cancel();
                        } else if (mode == ExitMode.INTERRUPT) {
                            Thread.currentThread().interrupt();
                        }
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("Fixture checkpoint interrupted", failure);
                    } catch (java.io.IOException failure) {
                        throw new AssertionError("Fixture launcher release failed", failure);
                    }
                    break;
                }
            }
            if (delayed.get() && mode == ExitMode.DEADLINE) { return started.plusSeconds(11); }
            if (delayed.get() && mode == ExitMode.ELAPSED) { return started.plusSeconds(301); }
            return started;
        }
    }
}
