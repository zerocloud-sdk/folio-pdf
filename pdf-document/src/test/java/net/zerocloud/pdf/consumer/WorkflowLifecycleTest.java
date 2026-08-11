package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicReference;
import net.zerocloud.pdf.DocumentCommand;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.query.PageCount;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class WorkflowLifecycleTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void queryObservesTheBlankPageAddedEarlierInTheSession() throws Exception {
        Path target = temporaryFolder.getRoot().toPath().resolve("ordered.pdf");

        WorkflowOutcome<Integer> outcome = new DocumentWorkflow().execute(
                WorkflowRequest.create(target, SaveMode.REWRITE),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return session.query(PageCount.INSTANCE);
                });

        assertEquals(Integer.valueOf(1), outcome.getResult());
    }

    @Test
    public void callerRuntimeFailureAbortsPublicationAndPropagatesUnchanged() throws Exception {
        Path target = temporaryFolder.getRoot().toPath().resolve("preserved.pdf");
        byte[] existingContent = "existing publication".getBytes(StandardCharsets.UTF_8);
        Files.write(target, existingContent);
        RuntimeException expected = new RuntimeException("caller failure");

        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.create(target, SaveMode.REWRITE),
                    session -> {
                        session.execute(AddBlankPage.INSTANCE);
                        throw expected;
                    });
            fail("Expected the caller failure");
        } catch (RuntimeException actual) {
            assertSame(expected, actual);
        }

        assertArrayEquals(existingContent, Files.readAllBytes(target));
    }

    @Test
    public void sessionIsInvalidAfterTheWorkflowReturns() throws Exception {
        Path target = temporaryFolder.getRoot().toPath().resolve("closed.pdf");
        final DocumentSession[] retained = new DocumentSession[1];

        new DocumentWorkflow().execute(
                WorkflowRequest.create(target, SaveMode.REWRITE),
                session -> {
                    retained[0] = session;
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });

        try {
            retained[0].query(PageCount.INSTANCE);
            fail("Expected the retained session to be invalid");
        } catch (IllegalStateException expected) {
            assertEquals("Document Session is no longer active.", expected.getMessage());
        }
    }

    @Test
    public void callerDefinedCommandIsRejectedWithoutPublication() throws Exception {
        Path target = temporaryFolder.getRoot().toPath().resolve("rejected.pdf");
        DocumentCommand callerDefinedCommand = new DocumentCommand() {
        };

        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.create(target, SaveMode.REWRITE),
                    session -> {
                        session.execute(callerDefinedCommand);
                        return null;
                    });
            fail("Expected the caller-defined command to be rejected");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.COMMAND_REJECTED, failure.getCode());
            assertEquals("document.blank.create-publish-reopen", failure.getCapabilityId());
        }

        assertFalse(Files.exists(target));
    }

    @Test
    public void activeSessionRejectsCrossThreadUseWithoutBackendDetails() throws Exception {
        Path target = temporaryFolder.getRoot().toPath().resolve("thread-confined.pdf");
        AtomicReference<Throwable> crossThreadFailure = new AtomicReference<Throwable>();

        new DocumentWorkflow().execute(
                WorkflowRequest.create(target, SaveMode.REWRITE),
                session -> {
                    Thread otherThread = new Thread(() -> {
                        try {
                            session.query(PageCount.INSTANCE);
                        } catch (Throwable failure) {
                            crossThreadFailure.set(failure);
                        }
                    });
                    otherThread.start();
                    try {
                        otherThread.join();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("Interrupted while awaiting contract check");
                    }
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });

        assertEquals(IllegalStateException.class, crossThreadFailure.get().getClass());
        assertEquals(
                "Document Session is thread-confined.",
                crossThreadFailure.get().getMessage());
    }

    @Test
    public void sessionIsInvalidAfterCallerAndDocumentFailureExits() throws Exception {
        DocumentSession[] afterCallerFailure = new DocumentSession[1];
        RuntimeException callerFailure = new RuntimeException("caller failure");

        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.create(
                            temporaryFolder.getRoot().toPath().resolve("caller-exit.pdf"),
                            SaveMode.REWRITE),
                    session -> {
                        afterCallerFailure[0] = session;
                        throw callerFailure;
                    });
            fail("Expected the caller failure");
        } catch (RuntimeException actual) {
            assertSame(callerFailure, actual);
        }
        assertInactive(afterCallerFailure[0]);

        DocumentSession[] afterDocumentFailure = new DocumentSession[1];
        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.create(
                            temporaryFolder.getRoot().toPath().resolve("document-exit.pdf"),
                            SaveMode.REWRITE),
                    session -> {
                        afterDocumentFailure[0] = session;
                        session.execute(new DocumentCommand() {
                        });
                        return null;
                    });
            fail("Expected the document failure");
        } catch (DocumentFailure expected) {
            assertEquals(DocumentFailureCode.COMMAND_REJECTED, expected.getCode());
        }
        assertInactive(afterDocumentFailure[0]);
    }

    @Test
    public void pathSourceCanBeReplacedAfterSuccessAndFailureExits()
            throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("resource-source.pdf");
        new DocumentWorkflow().execute(
                WorkflowRequest.create(source, SaveMode.REWRITE),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });

        new DocumentWorkflow().execute(
                WorkflowRequest.open(source, SaveMode.REWRITE),
                session -> session.query(PageCount.INSTANCE));
        moveAwayAndBack(source);

        RuntimeException callerFailure = new RuntimeException("caller failure");
        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.open(source, SaveMode.REWRITE),
                    session -> {
                        throw callerFailure;
                    });
            fail("Expected the caller failure");
        } catch (RuntimeException actual) {
            assertSame(callerFailure, actual);
        }
        moveAwayAndBack(source);

        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.open(source, SaveMode.REWRITE),
                    session -> {
                        session.execute(new DocumentCommand() {
                        });
                        return null;
                    });
            fail("Expected the document failure");
        } catch (DocumentFailure expected) {
            assertEquals(DocumentFailureCode.COMMAND_REJECTED, expected.getCode());
        }
        moveAwayAndBack(source);
    }

    private static void moveAwayAndBack(Path source) throws Exception {
        Path moved = source.resolveSibling(source.getFileName() + ".moved");
        Files.move(source, moved, StandardCopyOption.REPLACE_EXISTING);
        Files.move(moved, source, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void assertInactive(DocumentSession session) throws Exception {
        try {
            session.query(PageCount.INSTANCE);
            fail("Expected the retained session to be invalid");
        } catch (IllegalStateException expected) {
            assertEquals("Document Session is no longer active.", expected.getMessage());
        }
    }
}
