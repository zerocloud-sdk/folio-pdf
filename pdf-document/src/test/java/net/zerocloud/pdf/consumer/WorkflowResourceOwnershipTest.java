package net.zerocloud.pdf.consumer;

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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import net.zerocloud.pdf.CancellationToken;
import net.zerocloud.pdf.DocumentCommand;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowEnvironment;
import net.zerocloud.pdf.WorkflowProgressPhase;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.query.PageCount;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class WorkflowResourceOwnershipTest {

    private static final Path PROCESS_FILE_DESCRIPTORS =
            Paths.get("/proc/self/fd");

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void moduleOpenedPathDescriptorClosesAcrossT03ExitCategories()
            throws Exception {
        Assume.assumeTrue(Files.isDirectory(PROCESS_FILE_DESCRIPTORS));
        Path source = temporaryFolder.getRoot().toPath()
                .resolve("path-resource.pdf");
        createDocument(source);

        for (PathExit exit : PathExit.values()) {
            runPathExit(source, exit);
            assertFalse(
                    "Path descriptor remained open after " + exit,
                    hasOpenDescriptor(source));
        }
    }

    @Test
    public void callerOwnedInputsRemainOpenAcrossT03ExitCategories()
            throws Exception {
        Path fixture = temporaryFolder.getRoot().toPath()
                .resolve("caller-input.pdf");
        createDocument(fixture);
        byte[] pdf = Files.readAllBytes(fixture);

        for (InputForm form : InputForm.values()) {
            for (InputExit exit : InputExit.values()) {
                CallerInput input = createCallerInput(form, exit, pdf);
                runInputExit(input, exit);
                assertTrue(
                        form + " was closed after " + exit,
                        input.isOpen());
            }
        }
    }

    @Test
    public void callerOwnedOutputsRemainOpenAcrossT03ExitCategories()
            throws Exception {
        for (OutputExit exit : OutputExit.values()) {
            CallerOutput output = createCallerOutput(exit);
            runOutputExit(output, exit);
            assertTrue(
                    "Output stream was closed after " + exit,
                    output.isOpen());
        }
    }

    private void runPathExit(Path source, PathExit exit) throws Exception {
        WorkflowRequest.Builder request = WorkflowRequest.builder()
                .source("path", DocumentSource.path(source))
                .primarySource("path")
                .saveMode(SaveMode.REWRITE);
        RuntimeException expected = new IllegalStateException(
                "path exit " + exit);

        switch (exit) {
            case SUCCESS:
                new DocumentWorkflow().execute(
                        request.build(),
                        session -> {
                            assertPathDescriptorOpen(source, exit);
                            return session.query(PageCount.INSTANCE);
                        });
                return;
            case CALLBACK_RUNTIME:
                try {
                    new DocumentWorkflow().execute(
                            request.build(),
                            session -> {
                                assertPathDescriptorOpen(source, exit);
                                throw expected;
                            });
                    fail("Expected callback failure");
                } catch (RuntimeException actual) {
                    assertSame(expected, actual);
                }
                return;
            case DOCUMENT_FAILURE:
                try {
                    new DocumentWorkflow().execute(
                            request.build(),
                            session -> {
                                assertPathDescriptorOpen(source, exit);
                                session.execute(new DocumentCommand() {
                                });
                                return null;
                            });
                    fail("Expected command rejection");
                } catch (DocumentFailure failure) {
                    assertEquals(
                            DocumentFailureCode.COMMAND_REJECTED,
                            failure.getCode());
                }
                return;
            case CANCELLATION:
                CancellationToken cancellation = CancellationToken.create();
                try {
                    new DocumentWorkflow().execute(
                            request.cancellationToken(cancellation).build(),
                            session -> {
                                assertPathDescriptorOpen(source, exit);
                                cancellation.cancel();
                                return null;
                            });
                    fail("Expected cancellation");
                } catch (DocumentFailure failure) {
                    assertEquals(
                            DocumentFailureCode.WORKFLOW_CANCELLED,
                            failure.getCode());
                }
                return;
            case DEADLINE:
                MutableClock clock = new MutableClock(
                        Instant.parse("2026-08-10T12:00:00Z"),
                        ZoneId.of("UTC"));
                WorkflowEnvironment environment =
                        WorkflowEnvironment.withClock(clock);
                try {
                    new DocumentWorkflow(environment).execute(
                            request.deadline(clock.instant().plusSeconds(1L)).build(),
                            session -> {
                                assertPathDescriptorOpen(source, exit);
                                clock.advance(Duration.ofSeconds(1L));
                                return null;
                            });
                    fail("Expected deadline expiry");
                } catch (DocumentFailure failure) {
                    assertEquals(
                            DocumentFailureCode.DEADLINE_EXCEEDED,
                            failure.getCode());
                }
                return;
            case PROGRESS_RUNTIME:
                try {
                    new DocumentWorkflow().execute(
                            request.progressListener(phase -> {
                                if (phase == WorkflowProgressPhase.SOURCE_OPENED) {
                                    assertPathDescriptorOpen(source, exit);
                                    throw expected;
                                }
                            }).build(),
                            session -> {
                                fail("Progress failure must prevent caller work");
                                return null;
                            });
                    fail("Expected progress-listener failure");
                } catch (RuntimeException actual) {
                    assertSame(expected, actual);
                }
                return;
            case PUBLICATION_FAILURE:
                FailingOutputStream output = new FailingOutputStream();
                try {
                    new DocumentWorkflow().execute(
                            request.target(
                                    "failing-output",
                                    PublicationTarget.stream(output))
                                    .build(),
                            session -> {
                                assertPathDescriptorOpen(source, exit);
                                return null;
                            });
                    fail("Expected publication failure");
                } catch (DocumentFailure failure) {
                    assertEquals(
                            DocumentFailureCode.PUBLICATION_FAILED,
                            failure.getCode());
                }
                assertTrue(output.isOpen());
                return;
            default:
                throw new AssertionError("Unhandled Path exit: " + exit);
        }
    }

    private void runInputExit(CallerInput input, InputExit exit)
            throws Exception {
        WorkflowRequest.Builder request = WorkflowRequest.builder()
                .source("caller-input", input.source)
                .primarySource("caller-input")
                .saveMode(SaveMode.REWRITE);
        RuntimeException expected = input.runtimeFailure;

        switch (exit) {
            case SUCCESS:
                new DocumentWorkflow().execute(
                        request.build(),
                        session -> session.query(PageCount.INSTANCE));
                return;
            case CALLBACK_RUNTIME:
                expected = new IllegalStateException("callback failure");
                try {
                    RuntimeException callbackFailure = expected;
                    new DocumentWorkflow().execute(
                            request.build(),
                            session -> {
                                throw callbackFailure;
                            });
                    fail("Expected callback failure");
                } catch (RuntimeException actual) {
                    assertSame(expected, actual);
                }
                return;
            case DOCUMENT_FAILURE:
                try {
                    new DocumentWorkflow().execute(
                            request.build(),
                            session -> {
                                session.execute(new DocumentCommand() {
                                });
                                return null;
                            });
                    fail("Expected command rejection");
                } catch (DocumentFailure failure) {
                    assertEquals(
                            DocumentFailureCode.COMMAND_REJECTED,
                            failure.getCode());
                }
                return;
            case PRE_CANCELLED:
                CancellationToken preCancelled = CancellationToken.create();
                preCancelled.cancel();
                try {
                    new DocumentWorkflow().execute(
                            request.cancellationToken(preCancelled).build(),
                            session -> {
                                fail("Pre-cancelled work must not run");
                                return null;
                            });
                    fail("Expected pre-cancellation");
                } catch (DocumentFailure failure) {
                    assertEquals(
                            DocumentFailureCode.WORKFLOW_CANCELLED,
                            failure.getCode());
                }
                return;
            case CANCELLATION:
                CancellationToken cancellation = CancellationToken.create();
                try {
                    new DocumentWorkflow().execute(
                            request.cancellationToken(cancellation).build(),
                            session -> {
                                cancellation.cancel();
                                return null;
                            });
                    fail("Expected cancellation");
                } catch (DocumentFailure failure) {
                    assertEquals(
                            DocumentFailureCode.WORKFLOW_CANCELLED,
                            failure.getCode());
                }
                return;
            case DEADLINE:
                MutableClock clock = new MutableClock(
                        Instant.parse("2026-08-10T12:00:00Z"),
                        ZoneId.of("UTC"));
                WorkflowEnvironment environment =
                        WorkflowEnvironment.withClock(clock);
                try {
                    new DocumentWorkflow(environment).execute(
                            request.deadline(clock.instant().plusSeconds(1L)).build(),
                            session -> {
                                clock.advance(Duration.ofSeconds(1L));
                                return null;
                            });
                    fail("Expected deadline expiry");
                } catch (DocumentFailure failure) {
                    assertEquals(
                            DocumentFailureCode.DEADLINE_EXCEEDED,
                            failure.getCode());
                }
                return;
            case PUBLICATION_FAILURE:
                try {
                    new DocumentWorkflow().execute(
                            request.target(
                                    "failing-output",
                                    PublicationTarget.stream(
                                            new FailingOutputStream()))
                                    .build(),
                            session -> null);
                    fail("Expected publication failure");
                } catch (DocumentFailure failure) {
                    assertEquals(
                            DocumentFailureCode.PUBLICATION_FAILED,
                            failure.getCode());
                }
                return;
            case SOURCE_LIMIT:
                try {
                    new DocumentWorkflow().execute(
                            request.build(),
                            session -> {
                                fail("Oversized source work must not run");
                                return null;
                            });
                    fail("Expected source limit failure");
                } catch (DocumentFailure failure) {
                    assertEquals(
                            DocumentFailureCode.SOURCE_LIMIT_EXCEEDED,
                            failure.getCode());
                }
                return;
            case MALFORMED_SOURCE:
                try {
                    new DocumentWorkflow().execute(
                            request.build(),
                            session -> {
                                fail("Malformed source work must not run");
                                return null;
                            });
                    fail("Expected source read failure");
                } catch (DocumentFailure failure) {
                    assertEquals(
                            DocumentFailureCode.SOURCE_READ_FAILED,
                            failure.getCode());
                }
                return;
            case RUNTIME_SOURCE:
                try {
                    new DocumentWorkflow().execute(
                            request.build(),
                            session -> {
                                fail("Runtime source work must not run");
                                return null;
                            });
                    fail("Expected runtime source failure");
                } catch (DocumentFailure failure) {
                    fail("Caller programming errors must remain unchecked");
                } catch (RuntimeException actual) {
                    assertSame(expected, actual);
                }
                return;
            default:
                throw new AssertionError("Unhandled input exit: " + exit);
        }
    }

    private void runOutputExit(CallerOutput output, OutputExit exit)
            throws Exception {
        WorkflowRequest.Builder request = WorkflowRequest.builder()
                .target("caller-output", output.target)
                .saveMode(SaveMode.REWRITE);
        RuntimeException expected = output.runtimeFailure;

        try {
            switch (exit) {
                case SUCCESS:
                    new DocumentWorkflow().execute(
                            request.build(),
                            session -> {
                                session.execute(AddBlankPage.INSTANCE);
                                return null;
                            });
                    return;
                case CALLBACK_RUNTIME:
                    expected = new IllegalStateException("callback failure");
                    RuntimeException callbackFailure = expected;
                    new DocumentWorkflow().execute(
                            request.build(),
                            session -> {
                                throw callbackFailure;
                            });
                    fail("Expected callback failure");
                    return;
                case DOCUMENT_FAILURE:
                    new DocumentWorkflow().execute(
                            request.build(),
                            session -> {
                                session.execute(new DocumentCommand() {
                                });
                                return null;
                            });
                    fail("Expected command rejection");
                    return;
                case VALIDATION_FAILURE:
                    new DocumentWorkflow().execute(
                            request.build(),
                            session -> null);
                    fail("Expected validation failure");
                    return;
                case PRE_CANCELLED:
                    CancellationToken preCancelled = CancellationToken.create();
                    preCancelled.cancel();
                    new DocumentWorkflow().execute(
                            request.cancellationToken(preCancelled).build(),
                            session -> {
                                fail("Pre-cancelled work must not run");
                                return null;
                            });
                    fail("Expected pre-cancellation");
                    return;
                case CANCELLATION:
                    CancellationToken cancellation = CancellationToken.create();
                    new DocumentWorkflow().execute(
                            request.cancellationToken(cancellation).build(),
                            session -> {
                                session.execute(AddBlankPage.INSTANCE);
                                cancellation.cancel();
                                return null;
                            });
                    fail("Expected cancellation");
                    return;
                case DEADLINE:
                    MutableClock clock = new MutableClock(
                            Instant.parse("2026-08-10T12:00:00Z"),
                            ZoneId.of("UTC"));
                    WorkflowEnvironment environment =
                            WorkflowEnvironment.withClock(clock);
                    new DocumentWorkflow(environment).execute(
                            request.deadline(clock.instant().plusSeconds(1L)).build(),
                            session -> {
                                session.execute(AddBlankPage.INSTANCE);
                                clock.advance(Duration.ofSeconds(1L));
                                return null;
                            });
                    fail("Expected deadline expiry");
                    return;
                case IO_FAILURE:
                case RUNTIME_FAILURE:
                    new DocumentWorkflow().execute(
                            request.build(),
                            session -> {
                                session.execute(AddBlankPage.INSTANCE);
                                return null;
                            });
                    fail("Expected publication failure");
                    return;
                default:
                    throw new AssertionError("Unhandled output exit: " + exit);
            }
        } catch (DocumentFailure failure) {
            if (exit == OutputExit.RUNTIME_FAILURE
                    || exit == OutputExit.CALLBACK_RUNTIME) {
                fail("Caller programming errors must remain unchecked");
            }
            assertExpectedOutputFailure(exit, failure);
        } catch (RuntimeException actual) {
            if (exit != OutputExit.RUNTIME_FAILURE
                    && exit != OutputExit.CALLBACK_RUNTIME) {
                throw actual;
            }
            assertSame(expected, actual);
        }
    }

    private static void assertExpectedOutputFailure(
            OutputExit exit,
            DocumentFailure failure) {
        switch (exit) {
            case DOCUMENT_FAILURE:
                assertEquals(
                        DocumentFailureCode.COMMAND_REJECTED,
                        failure.getCode());
                return;
            case VALIDATION_FAILURE:
                assertEquals(
                        DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                        failure.getCode());
                return;
            case PRE_CANCELLED:
            case CANCELLATION:
                assertEquals(
                        DocumentFailureCode.WORKFLOW_CANCELLED,
                        failure.getCode());
                return;
            case DEADLINE:
                assertEquals(
                        DocumentFailureCode.DEADLINE_EXCEEDED,
                        failure.getCode());
                return;
            case IO_FAILURE:
                assertEquals(
                        DocumentFailureCode.PUBLICATION_FAILED,
                        failure.getCode());
                return;
            default:
                throw new AssertionError(
                        "Unexpected checked output failure for " + exit);
        }
    }

    private CallerInput createCallerInput(
            InputForm form,
            InputExit exit,
            byte[] pdf) {
        byte[] content = exit == InputExit.MALFORMED_SOURCE
                ? new byte[] {1, 2, 3, 4}
                : pdf;
        long maximumBytes = exit == InputExit.SOURCE_LIMIT
                ? content.length - 1L
                : content.length;
        RuntimeException runtimeFailure = exit == InputExit.RUNTIME_SOURCE
                ? new IllegalStateException("caller input failure")
                : null;

        if (form == InputForm.STREAM) {
            TrackingInputStream stream = runtimeFailure == null
                    ? new TrackingInputStream(content)
                    : new RuntimeFailingInputStream(runtimeFailure);
            return new CallerInput(
                    DocumentSource.stream(stream, maximumBytes),
                    stream,
                    runtimeFailure);
        }

        TrackingChannel channel = runtimeFailure == null
                ? new TrackingChannel(content)
                : new RuntimeFailingChannel(runtimeFailure);
        return new CallerInput(
                DocumentSource.channel(channel, maximumBytes),
                channel,
                runtimeFailure);
    }

    private static CallerOutput createCallerOutput(OutputExit exit) {
        if (exit == OutputExit.IO_FAILURE) {
            FailingOutputStream stream = new FailingOutputStream();
            return new CallerOutput(
                    PublicationTarget.stream(stream),
                    stream,
                    null);
        }
        if (exit == OutputExit.RUNTIME_FAILURE) {
            RuntimeException failure = new IllegalStateException(
                    "caller output failure");
            RuntimeFailingOutputStream stream =
                    new RuntimeFailingOutputStream(failure);
            return new CallerOutput(
                    PublicationTarget.stream(stream),
                    stream,
                    failure);
        }
        TrackingOutputStream stream = new TrackingOutputStream();
        return new CallerOutput(
                PublicationTarget.stream(stream),
                stream,
                null);
    }

    private static void assertPathDescriptorOpen(Path source, PathExit exit) {
        try {
            assertTrue(
                    "Path fixture did not hold a descriptor during " + exit,
                    hasOpenDescriptor(source));
        } catch (IOException failure) {
            throw new AssertionError("Could not inspect process descriptors", failure);
        }
    }

    private static boolean hasOpenDescriptor(Path source) throws IOException {
        Path expected = source.toAbsolutePath().normalize();
        try (DirectoryStream<Path> descriptors =
                Files.newDirectoryStream(PROCESS_FILE_DESCRIPTORS)) {
            for (Path descriptor : descriptors) {
                try {
                    Path destination = Files.readSymbolicLink(descriptor);
                    Path resolved = destination.isAbsolute()
                            ? destination.normalize()
                            : descriptor.getParent().resolve(destination).normalize();
                    if (expected.equals(resolved)) {
                        return true;
                    }
                } catch (IOException ignored) {
                    // A descriptor may disappear while /proc is enumerated.
                }
            }
        }
        return false;
    }

    private static void createDocument(Path target) throws Exception {
        new DocumentWorkflow().execute(
                WorkflowRequest.create(target, SaveMode.REWRITE),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });
    }

    private enum PathExit {
        SUCCESS,
        CALLBACK_RUNTIME,
        DOCUMENT_FAILURE,
        CANCELLATION,
        DEADLINE,
        PROGRESS_RUNTIME,
        PUBLICATION_FAILURE
    }

    private enum InputForm {
        STREAM,
        CHANNEL
    }

    private enum InputExit {
        SUCCESS,
        CALLBACK_RUNTIME,
        DOCUMENT_FAILURE,
        PRE_CANCELLED,
        CANCELLATION,
        DEADLINE,
        PUBLICATION_FAILURE,
        SOURCE_LIMIT,
        MALFORMED_SOURCE,
        RUNTIME_SOURCE
    }

    private enum OutputExit {
        SUCCESS,
        CALLBACK_RUNTIME,
        DOCUMENT_FAILURE,
        VALIDATION_FAILURE,
        PRE_CANCELLED,
        CANCELLATION,
        DEADLINE,
        IO_FAILURE,
        RUNTIME_FAILURE
    }

    private interface OpenProbe {
        boolean isOpen();
    }

    private static final class CallerInput {

        private final DocumentSource source;
        private final OpenProbe probe;
        private final RuntimeException runtimeFailure;

        private CallerInput(
                DocumentSource source,
                OpenProbe probe,
                RuntimeException runtimeFailure) {
            this.source = source;
            this.probe = probe;
            this.runtimeFailure = runtimeFailure;
        }

        private boolean isOpen() {
            return probe.isOpen();
        }
    }

    private static final class CallerOutput {

        private final PublicationTarget target;
        private final OpenProbe probe;
        private final RuntimeException runtimeFailure;

        private CallerOutput(
                PublicationTarget target,
                OpenProbe probe,
                RuntimeException runtimeFailure) {
            this.target = target;
            this.probe = probe;
            this.runtimeFailure = runtimeFailure;
        }

        private boolean isOpen() {
            return probe.isOpen();
        }
    }

    private static class TrackingInputStream extends ByteArrayInputStream
            implements OpenProbe {

        private boolean open = true;

        private TrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            open = false;
            super.close();
        }

        @Override
        public boolean isOpen() {
            return open;
        }
    }

    private static final class RuntimeFailingInputStream
            extends TrackingInputStream {

        private final RuntimeException failure;

        private RuntimeFailingInputStream(RuntimeException failure) {
            super(new byte[0]);
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
    }

    private static class TrackingChannel
            implements ReadableByteChannel, OpenProbe {

        private final ReadableByteChannel delegate;

        private TrackingChannel(byte[] bytes) {
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
            delegate.close();
        }
    }

    private static final class RuntimeFailingChannel extends TrackingChannel {

        private final RuntimeException failure;

        private RuntimeFailingChannel(RuntimeException failure) {
            super(new byte[0]);
            this.failure = failure;
        }

        @Override
        public int read(ByteBuffer destination) {
            throw failure;
        }
    }

    private static class TrackingOutputStream extends ByteArrayOutputStream
            implements OpenProbe {

        private boolean open = true;

        @Override
        public void close() throws IOException {
            open = false;
            super.close();
        }

        @Override
        public boolean isOpen() {
            return open;
        }
    }

    private static final class FailingOutputStream extends OutputStream
            implements OpenProbe {

        private boolean open = true;

        @Override
        public void write(int value) throws IOException {
            throw new IOException("fixture write failure");
        }

        @Override
        public void write(byte[] bytes, int offset, int length)
                throws IOException {
            throw new IOException("fixture write failure");
        }

        @Override
        public void close() {
            open = false;
        }

        @Override
        public boolean isOpen() {
            return open;
        }
    }

    private static final class RuntimeFailingOutputStream extends OutputStream
            implements OpenProbe {

        private final RuntimeException failure;
        private boolean open = true;

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
            open = false;
        }

        @Override
        public boolean isOpen() {
            return open;
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
