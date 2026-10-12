package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
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
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.query.PageCount;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Public workflow outcomes distinguish elapsed time from absolute wall time. */
public final class WorkflowElapsedTimeContractTest {

    private static final Duration LIMIT = Duration.ofMinutes(5);

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void forwardWallClockStepDoesNotExhaustMonotonicWorkflowBudget()
            throws Exception {
        for (WorkflowExecutionProfile profile : WorkflowExecutionProfile.values()) {
            MutableClock clock = new MutableClock();
            AtomicLong ticks = new AtomicLong(17L);
            Path target = temporary.getRoot().toPath().resolve(profile + "-step.pdf");
            DocumentWorkflow workflow = new DocumentWorkflow(WorkflowEnvironment.builder()
                    .clock(clock).monotonicTicker(ticks::get)
                    .temporaryDirectory(temporary.getRoot().toPath()).build());
            WorkflowOutcome<Integer> outcome = workflow.execute(request(profile, target), session -> {
                session.execute(AddBlankPage.INSTANCE);
                clock.now = clock.now.plusSeconds(3600);
                ticks.addAndGet(Duration.ofSeconds(2).toNanos());
                return session.query(PageCount.INSTANCE);
            });
            assertEquals(Integer.valueOf(1), outcome.getResult());
            assertEquals(Duration.ofSeconds(2), outcome.getResourceUsage().getElapsedTime());
            assertEquals(PublicationStatus.COMMITTED, outcome.getPublicationReceipts().get(0).getStatus());
            assertEquals(Integer.valueOf(1), new DocumentWorkflow().execute(
                    WorkflowRequest.builder().source("input", DocumentSource.path(target))
                            .primarySource("input").saveMode(SaveMode.REWRITE).build(),
                    session -> session.query(PageCount.INSTANCE)).getResult());
            assertNoOwnedStorage();
        }
    }

    @Test
    public void genuineElapsedExcessStopsBothProfilesEvenWithAFrozenWallClock()
            throws Exception {
        for (WorkflowExecutionProfile profile : WorkflowExecutionProfile.values()) {
            AtomicLong ticks = new AtomicLong(23L);
            Path target = temporary.getRoot().toPath().resolve(profile + "-excess.pdf");
            byte[] sentinel = new byte[] {3, 8, 3};
            Files.write(target, sentinel);
            // Configure the ticker first to prove setter order is immaterial.
            DocumentWorkflow workflow = new DocumentWorkflow(WorkflowEnvironment.builder()
                    .monotonicTicker(ticks::get).clock(new MutableClock())
                    .temporaryDirectory(temporary.getRoot().toPath()).build());
            try {
                workflow.execute(request(profile, target), session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    ticks.addAndGet(LIMIT.toNanos() + 1L);
                    return session.query(PageCount.INSTANCE);
                });
                fail("Expected a genuinely exhausted elapsed budget");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.ELAPSED_TIME_LIMIT_EXCEEDED, failure.getCode());
                assertEquals(PublicationStatus.NOT_ATTEMPTED,
                        failure.getPublicationReceipts().get(0).getStatus());
                assertFalse(failure.getPublicationReceipts().get(0).isPartialOutputPossible());
            }
            assertArrayEquals(sentinel, Files.readAllBytes(target));
            assertNoOwnedStorage();
        }
    }

    @Test
    public void inclusiveMonotonicBoundarySurvivesTickerWrapAndBackwardWallStep()
            throws Exception {
        for (WorkflowExecutionProfile profile : WorkflowExecutionProfile.values()) {
            MutableClock clock = new MutableClock();
            AtomicLong ticks = new AtomicLong(Long.MAX_VALUE - 5L);
            DocumentWorkflow workflow = new DocumentWorkflow(WorkflowEnvironment.builder()
                    .clock(clock).monotonicTicker(ticks::get)
                    .temporaryDirectory(temporary.getRoot().toPath()).build());
            WorkflowOutcome<Integer> outcome = workflow.execute(
                    request(profile, temporary.getRoot().toPath().resolve(profile + "-inclusive.pdf")),
                    session -> {
                        session.execute(AddBlankPage.INSTANCE);
                        clock.now = clock.now.minusSeconds(3600);
                        ticks.addAndGet(LIMIT.toNanos());
                        return session.query(PageCount.INSTANCE);
                    });
            assertEquals(Integer.valueOf(1), outcome.getResult());
            assertEquals(LIMIT, outcome.getResourceUsage().getElapsedTime());
            assertNoOwnedStorage();
        }
    }

    @Test
    public void systemNanosecondTickerIgnoresStepsInACustomWallClock()
            throws Exception {
        for (WorkflowExecutionProfile profile : WorkflowExecutionProfile.values()) {
            MutableClock clock = new MutableClock();
            DocumentWorkflow workflow = new DocumentWorkflow(WorkflowEnvironment.builder()
                    .clock(clock).monotonicTicker(System::nanoTime)
                    .temporaryDirectory(temporary.getRoot().toPath()).build());
            WorkflowOutcome<Integer> outcome = workflow.execute(
                    request(profile, temporary.getRoot().toPath().resolve(profile + "-system-step.pdf")),
                    session -> {
                        session.execute(AddBlankPage.INSTANCE);
                        clock.now = clock.now.plusSeconds(3600);
                        return session.query(PageCount.INSTANCE);
                    });
            assertEquals(Integer.valueOf(1), outcome.getResult());
            assertTrue(outcome.getResourceUsage().getElapsedTime().compareTo(LIMIT) < 0);
            assertEquals(PublicationStatus.COMMITTED,
                    outcome.getPublicationReceipts().get(0).getStatus());
            assertNoOwnedStorage();
        }
    }

    @Test
    public void absoluteDeadlineStillUsesWallClockWithAnIndependentTicker()
            throws Exception {
        for (WorkflowExecutionProfile profile : WorkflowExecutionProfile.values()) {
            MutableClock clock = new MutableClock();
            AtomicLong ticks = new AtomicLong();
            Path target = temporary.getRoot().toPath().resolve(profile + "-deadline.pdf");
            DocumentWorkflow workflow = new DocumentWorkflow(WorkflowEnvironment.builder()
                    .clock(clock).monotonicTicker(ticks::get)
                    .temporaryDirectory(temporary.getRoot().toPath()).build());
            try {
                workflow.execute(WorkflowRequest.builder().executionProfile(profile)
                        .saveMode(SaveMode.REWRITE).deadline(clock.now.plusSeconds(5))
                        .target("output", PublicationTarget.path(target)).build(), session -> {
                            session.execute(AddBlankPage.INSTANCE);
                            clock.now = clock.now.plusSeconds(5);
                            return null;
                        });
                fail("Expected the absolute deadline to expire");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.DEADLINE_EXCEEDED, failure.getCode());
                assertEquals(PublicationStatus.NOT_ATTEMPTED,
                        failure.getPublicationReceipts().get(0).getStatus());
            }
            assertFalse(Files.exists(target));
            assertNoOwnedStorage();
        }
    }

    @Test
    public void systemTickerStopsARealLongWorkflow() throws Exception {
        Path target = temporary.getRoot().toPath().resolve("real-time.pdf");
        DocumentWorkflow workflow = new DocumentWorkflow(WorkflowEnvironment.builder()
                .temporaryDirectory(temporary.getRoot().toPath())
                .defaultResourcePolicy(oneSecondPolicy()).build());
        try {
            workflow.execute(request(WorkflowExecutionProfile.IN_PROCESS, target), session -> {
                session.execute(AddBlankPage.INSTANCE);
                long started = System.nanoTime();
                while (System.nanoTime() - started <= Duration.ofMillis(1100).toNanos()) {
                    try { Thread.sleep(10); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(interrupted);
                    }
                }
                return null;
            });
            fail("Expected a real elapsed-time limit failure");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.ELAPSED_TIME_LIMIT_EXCEEDED, failure.getCode());
            assertEquals(PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(0).getStatus());
        }
        assertFalse(Files.exists(target));
        assertNoOwnedStorage();
    }

    private WorkflowRequest request(WorkflowExecutionProfile profile, Path target) {
        return WorkflowRequest.builder().executionProfile(profile).saveMode(SaveMode.REWRITE)
                .target("output", PublicationTarget.path(target)).build();
    }

    private WorkflowResourcePolicy oneSecondPolicy() {
        WorkflowResourcePolicy defaults = WorkflowResourcePolicy.safeDefaults();
        return WorkflowResourcePolicy.builder()
                .maximumInputBytes(defaults.getMaximumInputBytes())
                .maximumPages(defaults.getMaximumPages())
                .maximumObjects(defaults.getMaximumObjects())
                .maximumNestingDepth(defaults.getMaximumNestingDepth())
                .maximumDecompressedBytes(defaults.getMaximumDecompressedBytes())
                .maximumDecodedPixels(defaults.getMaximumDecodedPixels())
                .maximumOwnedMemoryBytes(defaults.getMaximumOwnedMemoryBytes())
                .maximumTemporaryStorageBytes(defaults.getMaximumTemporaryStorageBytes())
                .maximumElapsedTime(Duration.ofSeconds(1))
                .maximumConcurrentWorkflows(defaults.getMaximumConcurrentWorkflows()).build();
    }

    private void assertNoOwnedStorage() throws Exception {
        try (java.util.stream.Stream<Path> children = Files.list(temporary.getRoot().toPath())) {
            assertTrue(children.noneMatch(path -> path.getFileName().toString().startsWith(".folio-pdf-workflow-")));
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
