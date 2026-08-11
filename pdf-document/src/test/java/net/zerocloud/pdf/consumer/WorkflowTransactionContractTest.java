package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.zerocloud.pdf.CancellationToken;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowEnvironment;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowProgressPhase;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.query.PageCount;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class WorkflowTransactionContractTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void namedPrimaryPathSourceIsRewrittenToNamedPathTarget() throws Exception {
        Path first = temporaryFolder.getRoot().toPath().resolve("first.pdf");
        Path primary = temporaryFolder.getRoot().toPath().resolve("primary.pdf");
        Path target = temporaryFolder.getRoot().toPath().resolve("published.pdf");
        createDocument(first, 1);
        createDocument(primary, 2);

        WorkflowRequest request = WorkflowRequest.builder()
                .source("first", DocumentSource.path(first))
                .source("selected", DocumentSource.path(primary))
                .primarySource("selected")
                .target("published", PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE)
                .build();

        WorkflowOutcome<Integer> outcome = new DocumentWorkflow().execute(
                request,
                session -> {
                    int originalPageCount = session.query(PageCount.INSTANCE).intValue();
                    session.execute(AddBlankPage.INSTANCE);
                    return Integer.valueOf(originalPageCount);
                });

        assertEquals(Integer.valueOf(2), outcome.getResult());
        assertEquals(
                "document.blank.create-publish-reopen",
                outcome.getCapabilityId());
        assertEquals(
                WorkflowExecutionProfile.IN_PROCESS,
                outcome.getExecutionProfile());
        assertEquals(SaveMode.REWRITE, outcome.getSaveMode());
        assertTrue(outcome.getDiagnostics().isEmpty());
        try {
            outcome.getDiagnostics().add("caller mutation");
            fail("Expected outcome diagnostics to be immutable");
        } catch (UnsupportedOperationException expected) {
            // Outcome information is detached and immutable.
        }
        assertEquals(1, outcome.getPublicationReceipts().size());
        assertEquals("published",
                outcome.getPublicationReceipts().get(0).getTargetName());
        assertEquals(target, outcome.getPublicationReceipts().get(0).getTarget());
        assertEquals(PublicationStatus.COMMITTED,
                outcome.getPublicationReceipts().get(0).getStatus());

        WorkflowOutcome<Integer> reopened = new DocumentWorkflow().execute(
                WorkflowRequest.open(target, SaveMode.REWRITE),
                session -> session.query(PageCount.INSTANCE));
        assertEquals(Integer.valueOf(3), reopened.getResult());
    }

    @Test
    public void streamChannelAndBoundedBytesAreCallerOwnedPrimarySources() throws Exception {
        Path fixture = temporaryFolder.getRoot().toPath().resolve("source-forms.pdf");
        createDocument(fixture, 2);

        byte[] copiedBytes = Files.readAllBytes(fixture);
        DocumentSource boundedBytes = DocumentSource.bytes(copiedBytes, copiedBytes.length);
        copiedBytes[0] = 0;
        assertPrimaryPageCount(boundedBytes, 2);

        TrackingInputStream stream =
                new TrackingInputStream(Files.readAllBytes(fixture));
        assertPrimaryPageCount(DocumentSource.stream(stream, Files.size(fixture)), 2);
        assertFalse(stream.closeCalled);

        TrackingReadableByteChannel channel =
                new TrackingReadableByteChannel(Files.readAllBytes(fixture));
        assertPrimaryPageCount(DocumentSource.channel(channel, Files.size(fixture)), 2);
        assertFalse(channel.closeCalled);
        assertTrue(channel.isOpen());
    }

    @Test
    public void boundedSourcesFailSafelyWithoutClosingCallerResources() throws Exception {
        Path fixture = temporaryFolder.getRoot().toPath().resolve("bounded-source.pdf");
        createDocument(fixture, 1);
        byte[] pdf = Files.readAllBytes(fixture);
        long insufficientLimit = pdf.length - 1L;

        TrackingInputStream stream = new TrackingInputStream(pdf);
        assertSourceLimitExceeded(DocumentSource.stream(stream, insufficientLimit));
        assertFalse(stream.closeCalled);

        TrackingReadableByteChannel channel = new TrackingReadableByteChannel(pdf);
        assertSourceLimitExceeded(DocumentSource.channel(channel, insufficientLimit));
        assertFalse(channel.closeCalled);
        assertTrue(channel.isOpen());

        assertSourceLimitExceeded(DocumentSource.bytes(pdf, insufficientLimit));
    }

    @Test
    public void sourceFactoriesRejectNegativeByteLimitsAsProgrammingErrors() {
        try {
            DocumentSource.stream(new ByteArrayInputStream(new byte[0]), -1L);
            fail("Expected a negative stream limit to be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals(
                    "maximumBytes must be non-negative.",
                    expected.getMessage());
        }

        try {
            DocumentSource.channel(
                    Channels.newChannel(new ByteArrayInputStream(new byte[0])),
                    -1L);
            fail("Expected a negative channel limit to be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals(
                    "maximumBytes must be non-negative.",
                    expected.getMessage());
        }

        try {
            DocumentSource.bytes(new byte[0], -1L);
            fail("Expected a negative byte-array limit to be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals(
                    "maximumBytes must be non-negative.",
                    expected.getMessage());
        }
    }

    @Test
    public void callerOwnedInputRuntimeExceptionPropagatesUnchanged()
            throws Exception {
        RuntimeException expected = new IllegalStateException(
                "caller input programming failure");
        RuntimeFailingInputStream source =
                new RuntimeFailingInputStream(expected);
        WorkflowRequest request = WorkflowRequest.builder()
                .source("runtime-input", DocumentSource.stream(source, 1024L))
                .primarySource("runtime-input")
                .saveMode(SaveMode.REWRITE)
                .build();

        try {
            new DocumentWorkflow().execute(
                    request,
                    session -> {
                        fail("Caller work must not run when source access fails");
                        return null;
                    });
            fail("Expected the caller-owned input failure");
        } catch (DocumentFailure failure) {
            fail("Caller programming errors must remain unchecked");
        } catch (RuntimeException actual) {
            assertSame(expected, actual);
        }

        assertFalse(source.closeCalled);
    }

    @Test
    public void callerOwnedOutputRuntimeExceptionPropagatesUnchanged()
            throws Exception {
        RuntimeException expected = new IllegalStateException(
                "caller output programming failure");
        RuntimeFailingOutputStream target =
                new RuntimeFailingOutputStream(expected);
        WorkflowRequest request = WorkflowRequest.builder()
                .target("runtime-output", PublicationTarget.stream(target))
                .saveMode(SaveMode.REWRITE)
                .build();

        try {
            new DocumentWorkflow().execute(
                    request,
                    session -> {
                        session.execute(AddBlankPage.INSTANCE);
                        return null;
                    });
            fail("Expected the caller-owned output failure");
        } catch (DocumentFailure failure) {
            fail("Caller programming errors must remain unchecked");
        } catch (RuntimeException actual) {
            assertSame(expected, actual);
        }

        assertFalse(target.closeCalled);
    }

    @Test
    public void convenienceFactoriesDoNotSelectSaveModeImplicitly()
            throws Exception {
        assertNoImplicitSaveModeFactory("create");
        assertNoImplicitSaveModeFactory("open");
    }

    @Test
    public void requestRequiresUniqueNamesAndADeclaredPrimarySource() {
        Path sourcePath = temporaryFolder.getRoot().toPath().resolve("source.pdf");
        Path targetPath = temporaryFolder.getRoot().toPath().resolve("target.pdf");

        try {
            WorkflowRequest.builder()
                    .source("input", DocumentSource.path(sourcePath))
                    .source("input", DocumentSource.path(sourcePath));
            fail("Expected duplicate source names to be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals("Duplicate source name: input", expected.getMessage());
        }

        try {
            WorkflowRequest.builder()
                    .target("output", PublicationTarget.path(targetPath))
                    .target("output", PublicationTarget.path(targetPath));
            fail("Expected duplicate target names to be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals("Duplicate target name: output", expected.getMessage());
        }

        try {
            WorkflowRequest.builder()
                    .source("input", DocumentSource.path(sourcePath))
                    .saveMode(SaveMode.REWRITE)
                    .build();
            fail("Expected a primary source to be required");
        } catch (IllegalStateException expected) {
            assertEquals(
                    "A request with sources must select a declared primary source.",
                    expected.getMessage());
        }

        try {
            WorkflowRequest.builder()
                    .source("input", DocumentSource.path(sourcePath))
                    .primarySource("missing")
                    .saveMode(SaveMode.REWRITE)
                    .build();
            fail("Expected an undeclared primary source to be rejected");
        } catch (IllegalStateException expected) {
            assertEquals(
                    "A request with sources must select a declared primary source.",
                    expected.getMessage());
        }

        try {
            WorkflowRequest.builder()
                    .target("output", PublicationTarget.path(targetPath))
                    .build();
            fail("Expected an explicit Save Mode to be required");
        } catch (IllegalStateException expected) {
            assertEquals(
                    "A workflow request must select a Save Mode.",
                    expected.getMessage());
        }
    }

    @Test
    public void successfulPublicationCommitsEveryNamedPathAndStreamTarget()
            throws Exception {
        Path archive = temporaryFolder.getRoot().toPath().resolve("archive.pdf");
        Path mirror = temporaryFolder.getRoot().toPath().resolve("mirror.pdf");
        TrackingOutputStream response = new TrackingOutputStream();

        WorkflowRequest request = WorkflowRequest.builder()
                .target("archive", PublicationTarget.path(archive))
                .target("response", PublicationTarget.stream(response))
                .target("mirror", PublicationTarget.path(mirror))
                .saveMode(SaveMode.REWRITE)
                .build();

        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(
                request,
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });

        assertEquals(3, outcome.getPublicationReceipts().size());
        assertCommitted(outcome, 0, "archive");
        assertCommitted(outcome, 1, "response");
        assertCommitted(outcome, 2, "mirror");
        assertEquals(archive, outcome.getPublicationReceipts().get(0).getTarget());
        assertEquals(mirror, outcome.getPublicationReceipts().get(2).getTarget());
        assertFalse(response.closeCalled);
        assertTrue(response.size() > 0);

        assertPrimaryPageCount(DocumentSource.path(archive), 1);
        assertPrimaryPageCount(
                DocumentSource.bytes(response.toByteArray(), response.size()),
                1);
        assertPrimaryPageCount(DocumentSource.path(mirror), 1);
    }

    @Test
    public void multiTargetFailureReportsCommittedFailedAndNotAttemptedTargets()
            throws Exception {
        Path committed = temporaryFolder.getRoot().toPath().resolve("committed.pdf");
        Path untouched = temporaryFolder.getRoot().toPath().resolve("untouched.pdf");
        byte[] existing = new byte[] {11, 22, 33, 44};
        Files.write(untouched, existing);
        FailingOutputStream failing = new FailingOutputStream();

        WorkflowRequest request = WorkflowRequest.builder()
                .target("committed", PublicationTarget.path(committed))
                .target("failing-stream", PublicationTarget.stream(failing))
                .target("untouched", PublicationTarget.path(untouched))
                .saveMode(SaveMode.REWRITE)
                .build();

        try {
            new DocumentWorkflow().execute(
                    request,
                    session -> {
                        session.execute(AddBlankPage.INSTANCE);
                        return null;
                    });
            fail("Expected publication to fail at the stream target");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.PUBLICATION_FAILED, failure.getCode());
            assertEquals(
                    "The validated document could not be written to its stream target.",
                    failure.getDiagnostic());
            assertEquals(3, failure.getPublicationReceipts().size());
            assertReceipt(
                    failure,
                    0,
                    "committed",
                    PublicationStatus.COMMITTED,
                    false);
            assertReceipt(
                    failure,
                    1,
                    "failing-stream",
                    PublicationStatus.FAILED,
                    true);
            assertReceipt(
                    failure,
                    2,
                    "untouched",
                    PublicationStatus.NOT_ATTEMPTED,
                    false);
        }

        assertPrimaryPageCount(DocumentSource.path(committed), 1);
        assertTrue(failing.size() > 0);
        assertFalse(failing.closeCalled);
        assertArrayEquals(existing, Files.readAllBytes(untouched));
    }

    @Test
    public void nestedWorkflowFailureCannotLeakForeignReceipts()
            throws Exception {
        Path outerTarget = temporaryFolder.getRoot().toPath()
                .resolve("outer-target.pdf");
        byte[] existing = new byte[] {31, 32, 33};
        Files.write(outerTarget, existing);
        Path innerFirst = temporaryFolder.getRoot().toPath()
                .resolve("inner-first.pdf");
        Path innerSecond = temporaryFolder.getRoot().toPath()
                .resolve("inner-second.pdf");

        WorkflowRequest outer = WorkflowRequest.builder()
                .target("outer", PublicationTarget.path(outerTarget))
                .saveMode(SaveMode.REWRITE)
                .build();
        WorkflowRequest inner = WorkflowRequest.builder()
                .target("inner-first", PublicationTarget.path(innerFirst))
                .target("inner-second", PublicationTarget.path(innerSecond))
                .saveMode(SaveMode.REWRITE)
                .build();

        try {
            new DocumentWorkflow().execute(
                    outer,
                    outerSession -> new DocumentWorkflow().execute(
                            inner,
                            innerSession -> null));
            fail("Expected the nested workflow validation failure");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                    failure.getCode());
            assertEquals(1, failure.getPublicationReceipts().size());
            assertReceipt(
                    failure,
                    0,
                    "outer",
                    PublicationStatus.NOT_ATTEMPTED,
                    false);
        }

        assertArrayEquals(existing, Files.readAllBytes(outerTarget));
        assertFalse(Files.exists(innerFirst));
        assertFalse(Files.exists(innerSecond));
    }

    @Test
    public void validationFailureTouchesNoTargetAndReportsEveryTarget()
            throws Exception {
        Path preserved = temporaryFolder.getRoot().toPath().resolve("preserved.pdf");
        byte[] existing = new byte[] {91, 92, 93};
        Files.write(preserved, existing);
        TrackingOutputStream untouchedStream = new TrackingOutputStream();
        DocumentSession[] retained = new DocumentSession[1];

        WorkflowRequest request = WorkflowRequest.builder()
                .target("preserved", PublicationTarget.path(preserved))
                .target("untouched-stream", PublicationTarget.stream(untouchedStream))
                .saveMode(SaveMode.REWRITE)
                .build();

        try {
            new DocumentWorkflow().execute(
                    request,
                    session -> {
                        retained[0] = session;
                        return null;
                    });
            fail("Expected validation to reject a document without pages");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                    failure.getCode());
            assertEquals(
                    "The staged document must contain at least one page.",
                    failure.getDiagnostic());
            assertEquals(2, failure.getPublicationReceipts().size());
            assertReceipt(
                    failure,
                    0,
                    "preserved",
                    PublicationStatus.NOT_ATTEMPTED,
                    false);
            assertReceipt(
                    failure,
                    1,
                    "untouched-stream",
                    PublicationStatus.NOT_ATTEMPTED,
                    false);
        }

        assertArrayEquals(existing, Files.readAllBytes(preserved));
        assertEquals(0, untouchedStream.size());
        assertFalse(untouchedStream.closeCalled);
        try {
            retained[0].query(PageCount.INSTANCE);
            fail("Expected the retained session to be invalid");
        } catch (IllegalStateException expected) {
            assertEquals(
                    "Document Session is no longer active.",
                    expected.getMessage());
        }
    }

    @Test
    public void incrementalSaveModeFailsWithStableUnsupportedResult()
            throws Exception {
        Path target = temporaryFolder.getRoot().toPath().resolve("incremental.pdf");
        byte[] existing = new byte[] {71, 72, 73};
        Files.write(target, existing);

        WorkflowRequest request = WorkflowRequest.builder()
                .target("incremental", PublicationTarget.path(target))
                .saveMode(SaveMode.INCREMENTAL)
                .build();

        try {
            new DocumentWorkflow().execute(
                    request,
                    session -> {
                        fail("Caller work must not run for unsupported INCREMENTAL mode");
                        return null;
                    });
            fail("Expected INCREMENTAL to be unsupported");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.SAVE_MODE_UNSUPPORTED,
                    failure.getCode());
            assertEquals(
                    "INCREMENTAL publication is not supported until T15.",
                    failure.getDiagnostic());
            assertEquals(1, failure.getPublicationReceipts().size());
            assertReceipt(
                    failure,
                    0,
                    "incremental",
                    PublicationStatus.NOT_ATTEMPTED,
                    false);
        }

        assertArrayEquals(existing, Files.readAllBytes(target));
    }

    @Test
    public void cancellationBeforeAndDuringWorkPreventsPublication()
            throws Exception {
        byte[] existing = new byte[] {51, 52, 53};
        Path preCancelledTarget =
                temporaryFolder.getRoot().toPath().resolve("pre-cancelled.pdf");
        Files.write(preCancelledTarget, existing);
        CancellationToken preCancelled = CancellationToken.create();
        preCancelled.cancel();

        WorkflowRequest preCancelledRequest = WorkflowRequest.builder()
                .target("pre-cancelled", PublicationTarget.path(preCancelledTarget))
                .saveMode(SaveMode.REWRITE)
                .cancellationToken(preCancelled)
                .build();

        try {
            new DocumentWorkflow().execute(
                    preCancelledRequest,
                    session -> {
                        fail("Pre-cancelled work must not invoke caller work");
                        return null;
                    });
            fail("Expected the pre-cancelled workflow to fail");
        } catch (DocumentFailure failure) {
            assertCancelled(failure, "pre-cancelled");
        }
        assertArrayEquals(existing, Files.readAllBytes(preCancelledTarget));

        Path cancelledAfterWorkTarget =
                temporaryFolder.getRoot().toPath().resolve("cancelled-after-work.pdf");
        Files.write(cancelledAfterWorkTarget, existing);
        CancellationToken cancelledAfterWork = CancellationToken.create();
        DocumentSession[] retained = new DocumentSession[1];
        WorkflowRequest cancelledAfterWorkRequest = WorkflowRequest.builder()
                .target(
                        "cancelled-after-work",
                        PublicationTarget.path(cancelledAfterWorkTarget))
                .saveMode(SaveMode.REWRITE)
                .cancellationToken(cancelledAfterWork)
                .build();

        try {
            new DocumentWorkflow().execute(
                    cancelledAfterWorkRequest,
                    session -> {
                        retained[0] = session;
                        session.execute(AddBlankPage.INSTANCE);
                        cancelledAfterWork.cancel();
                        return null;
                    });
            fail("Expected cancellation after caller work");
        } catch (DocumentFailure failure) {
            assertCancelled(failure, "cancelled-after-work");
        }
        assertArrayEquals(existing, Files.readAllBytes(cancelledAfterWorkTarget));
        try {
            retained[0].query(PageCount.INSTANCE);
            fail("Expected the retained session to be invalid");
        } catch (IllegalStateException expected) {
            assertEquals(
                    "Document Session is no longer active.",
                    expected.getMessage());
        }
    }

    @Test
    public void deadlineExpiryUsesAnInjectedClockWithoutSleeping()
            throws Exception {
        byte[] existing = new byte[] {61, 62, 63};
        Path target = temporaryFolder.getRoot().toPath().resolve("deadline.pdf");
        Files.write(target, existing);
        MutableClock clock = new MutableClock(
                Instant.parse("2026-08-10T12:00:00Z"),
                ZoneId.of("UTC"));
        DocumentSession[] retained = new DocumentSession[1];

        WorkflowRequest request = WorkflowRequest.builder()
                .target("deadline", PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE)
                .deadline(clock.instant().plusSeconds(5L))
                .build();

        try {
            WorkflowEnvironment environment =
                    WorkflowEnvironment.withClock(clock);
            new DocumentWorkflow(environment).execute(
                    request,
                    session -> {
                        retained[0] = session;
                        session.execute(AddBlankPage.INSTANCE);
                        clock.advance(Duration.ofSeconds(5L));
                        return null;
                    });
            fail("Expected the deadline to expire after caller work");
        } catch (DocumentFailure failure) {
            assertEquals(
                    DocumentFailureCode.DEADLINE_EXCEEDED,
                    failure.getCode());
            assertEquals(
                    "The workflow deadline has expired.",
                    failure.getDiagnostic());
            assertEquals(1, failure.getPublicationReceipts().size());
            assertReceipt(
                    failure,
                    0,
                    "deadline",
                    PublicationStatus.NOT_ATTEMPTED,
                    false);
        }

        try {
            DocumentWorkflow.class.getConstructor(Clock.class);
            fail("DocumentWorkflow must obtain Clock from WorkflowEnvironment");
        } catch (NoSuchMethodException expected) {
            // Workflow Environment owns the execution clock.
        }

        assertArrayEquals(existing, Files.readAllBytes(target));
        try {
            retained[0].query(PageCount.INSTANCE);
            fail("Expected the retained session to be invalid");
        } catch (IllegalStateException expected) {
            assertEquals(
                    "Document Session is no longer active.",
                    expected.getMessage());
        }
    }

    @Test
    public void progressEventsExposeOnlyDeterministicTransactionPhases()
            throws Exception {
        Path sensitivePath = temporaryFolder.getRoot().toPath()
                .resolve("customer-secret-password.pdf");
        TrackingOutputStream stream = new TrackingOutputStream();
        List<WorkflowProgressPhase> phases =
                new ArrayList<WorkflowProgressPhase>();

        WorkflowRequest request = WorkflowRequest.builder()
                .target("sensitive-target-name", PublicationTarget.path(sensitivePath))
                .target("private-response", PublicationTarget.stream(stream))
                .saveMode(SaveMode.REWRITE)
                .progressListener(phases::add)
                .build();

        new DocumentWorkflow().execute(
                request,
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });

        assertEquals(
                Arrays.asList(
                        WorkflowProgressPhase.STARTED,
                        WorkflowProgressPhase.WORK_STARTED,
                        WorkflowProgressPhase.WORK_COMPLETED,
                        WorkflowProgressPhase.STAGED,
                        WorkflowProgressPhase.VALIDATED,
                        WorkflowProgressPhase.PUBLICATION_STARTED,
                        WorkflowProgressPhase.TARGET_COMMITTED,
                        WorkflowProgressPhase.TARGET_COMMITTED,
                        WorkflowProgressPhase.COMPLETED),
                phases);
        for (WorkflowProgressPhase phase : phases) {
            assertFalse(phase.name().contains("customer"));
            assertFalse(phase.name().contains("password"));
            assertFalse(phase.name().contains("target-name"));
            assertFalse(phase.name().contains("response"));
        }
    }

    private static void assertCancelled(
            DocumentFailure failure,
            String targetName) {
        assertEquals(DocumentFailureCode.WORKFLOW_CANCELLED, failure.getCode());
        assertEquals("The workflow was cancelled.", failure.getDiagnostic());
        assertEquals(1, failure.getPublicationReceipts().size());
        assertReceipt(
                failure,
                0,
                targetName,
                PublicationStatus.NOT_ATTEMPTED,
                false);
    }

    private static void assertNoImplicitSaveModeFactory(String methodName)
            throws Exception {
        try {
            WorkflowRequest.class.getMethod(methodName, Path.class);
            fail(methodName + "(Path) must not choose a Save Mode for the caller");
        } catch (NoSuchMethodException expected) {
            // The explicit Builder or a factory accepting SaveMode is required.
        }
    }

    private static void assertReceipt(
            DocumentFailure failure,
            int index,
            String targetName,
            PublicationStatus status,
            boolean partialOutputPossible) {
        assertEquals(
                targetName,
                failure.getPublicationReceipts().get(index).getTargetName());
        assertEquals(
                status,
                failure.getPublicationReceipts().get(index).getStatus());
        assertEquals(
                partialOutputPossible,
                failure.getPublicationReceipts().get(index)
                        .isPartialOutputPossible());
    }

    private static void assertCommitted(
            WorkflowOutcome<?> outcome,
            int index,
            String targetName) {
        assertEquals(
                targetName,
                outcome.getPublicationReceipts().get(index).getTargetName());
        assertEquals(
                PublicationStatus.COMMITTED,
                outcome.getPublicationReceipts().get(index).getStatus());
        assertFalse(
                outcome.getPublicationReceipts().get(index)
                        .isPartialOutputPossible());
    }

    private static void assertPrimaryPageCount(DocumentSource source, int expected)
            throws Exception {
        WorkflowRequest request = WorkflowRequest.builder()
                .source("selected", source)
                .primarySource("selected")
                .saveMode(SaveMode.REWRITE)
                .build();

        WorkflowOutcome<Integer> outcome = new DocumentWorkflow().execute(
                request,
                session -> session.query(PageCount.INSTANCE));

        assertEquals(Integer.valueOf(expected), outcome.getResult());
    }

    private static void assertSourceLimitExceeded(DocumentSource source) throws Exception {
        WorkflowRequest request = WorkflowRequest.builder()
                .source("bounded", source)
                .primarySource("bounded")
                .saveMode(SaveMode.REWRITE)
                .build();

        try {
            new DocumentWorkflow().execute(
                    request,
                    session -> {
                        fail("Caller work must not run for an oversized source");
                        return null;
                    });
            fail("Expected the source limit failure");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.SOURCE_LIMIT_EXCEEDED, failure.getCode());
            assertEquals(
                    "document.blank.create-publish-reopen",
                    failure.getCapabilityId());
            assertEquals(
                    "The source exceeds its declared byte limit.",
                    failure.getDiagnostic());
        }
    }

    private static void createDocument(Path target, int pageCount) throws Exception {
        new DocumentWorkflow().execute(
                WorkflowRequest.create(target, SaveMode.REWRITE),
                session -> {
                    for (int page = 0; page < pageCount; page++) {
                        session.execute(AddBlankPage.INSTANCE);
                    }
                    return null;
                });
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {

        private boolean closeCalled;

        private TrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            closeCalled = true;
            super.close();
        }
    }

    private static final class TrackingReadableByteChannel implements ReadableByteChannel {

        private final ReadableByteChannel delegate;
        private boolean closeCalled;

        private TrackingReadableByteChannel(byte[] bytes) {
            this.delegate = Channels.newChannel(new ByteArrayInputStream(bytes));
        }

        @Override
        public int read(ByteBuffer destination) throws IOException {
            return delegate.read(destination);
        }

        @Override
        public boolean isOpen() {
            return delegate.isOpen();
        }

        @Override
        public void close() throws IOException {
            closeCalled = true;
            delegate.close();
        }
    }

    private static final class TrackingOutputStream extends ByteArrayOutputStream {

        private boolean closeCalled;

        @Override
        public void close() throws IOException {
            closeCalled = true;
            super.close();
        }
    }

    private static final class FailingOutputStream extends OutputStream {

        private final ByteArrayOutputStream written = new ByteArrayOutputStream();
        private boolean closeCalled;

        @Override
        public void write(int value) throws IOException {
            written.write(value);
            throw new IOException("fixture write failure");
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            int partialLength = Math.min(8, length);
            written.write(bytes, offset, partialLength);
            throw new IOException("fixture write failure");
        }

        @Override
        public void close() throws IOException {
            closeCalled = true;
        }

        private int size() {
            return written.size();
        }
    }

    private static final class RuntimeFailingInputStream extends InputStream {

        private final RuntimeException failure;
        private boolean closeCalled;

        private RuntimeFailingInputStream(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public int read() {
            throw failure;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            throw failure;
        }

        @Override
        public void close() {
            closeCalled = true;
        }
    }

    private static final class RuntimeFailingOutputStream extends OutputStream {

        private final RuntimeException failure;
        private boolean closeCalled;

        private RuntimeFailingOutputStream(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public void write(int value) {
            throw failure;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            throw failure;
        }

        @Override
        public void close() {
            closeCalled = true;
        }
    }

    private static final class MutableClock extends Clock {

        private Instant current;
        private final ZoneId zone;

        private MutableClock(Instant current, ZoneId zone) {
            this.current = current;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId requestedZone) {
            return new MutableClock(current, requestedZone);
        }

        @Override
        public Instant instant() {
            return current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }
    }
}
