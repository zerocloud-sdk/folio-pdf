package net.zerocloud.pdf;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.query.PageCount;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Actual interruption and malformed Source gaps, with public recovery/lifetime oracles. */
public final class T22RecoveryObservationCases {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private static final byte[] SENTINEL = {17, 18, 19};

    @Test public void actualInterruptedWorkerWaitIsRecoverableAfterTerminationAndCleanup() throws Exception {
        Path parent = temporary.newFolder("owned").toPath(), target = temporary.getRoot().toPath().resolve("target");
        Files.write(target, SENTINEL);
        DocumentWorkflow workflow = new DocumentWorkflow(WorkflowEnvironment.builder().temporaryDirectory(parent).build());
        WorkflowTransactionId id = WorkflowTransactionId.of("actual-interruption");
        WorkflowRequest request = request(target, id).build();
        final long[] pid = {0};
        DocumentFailure failure;
        try {
            workflow.execute(request, session -> {
                pid[0] = Long.parseLong(HardenedWorkerEngine.probeIsolation(session).getWorkerProcessIdentity().split("@", 2)[0]);
                Thread.currentThread().interrupt();
                return session.query(PageCount.INSTANCE);
            });
            throw new AssertionError("Interrupted Worker wait passed");
        } catch (DocumentFailure expected) {
            failure = expected;
            assertEquals(DocumentFailureCode.WORKER_TERMINATED, failure.getCode());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
        assertTrue(pid[0] > 0);
        assertFalse(Files.exists(Paths.get("/proc", Long.toString(pid[0]))));
        assertArrayEquals(SENTINEL, Files.readAllBytes(target));
        empty(parent);
        WorkflowTransactionStatus status = workflow.lookupTransaction(id).get();
        assertEquals(WorkflowTransactionState.RECOVERABLE, status.getState());
        assertEquals(PublicationStatus.NOT_ATTEMPTED, status.getPublicationReceipts().get(0).getStatus());
        workflow.execute(request, session -> { session.execute(AddBlankPage.INSTANCE); return null; });
        assertEquals(WorkflowTransactionState.COMPLETED, workflow.lookupTransaction(id).get().getState());
        byte[] committed = Files.readAllBytes(target);
        finalReplay(workflow, request);
        assertArrayEquals(committed, Files.readAllBytes(target));
        empty(parent);
        record("interruption", failure, "RECOVERABLE", true);
    }

    @Test public void malformedInputIsTerminalAndCannotReplayCallerWorkOrPublication() throws Exception {
        Path parent = temporary.newFolder("owned").toPath(), target = temporary.getRoot().toPath().resolve("target");
        Files.write(target, SENTINEL);
        DocumentWorkflow workflow = new DocumentWorkflow(WorkflowEnvironment.builder().temporaryDirectory(parent).build());
        WorkflowTransactionId id = WorkflowTransactionId.of("actual-malformed-input");
        byte[] input = "%PDF-1.7\nnot a valid object graph\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
        WorkflowRequest request = request(target, id).source("input", DocumentSource.bytes(input, input.length)).primarySource("input").build();
        DocumentFailure failure;
        AtomicBoolean callback = new AtomicBoolean();
        try {
            workflow.execute(request, session -> { callback.set(true); return null; });
            throw new AssertionError("Malformed PDF passed");
        } catch (DocumentFailure expected) {
            failure = expected;
            assertEquals(DocumentFailureCode.SOURCE_READ_FAILED, failure.getCode());
        }
        assertFalse(callback.get());
        assertEquals(WorkflowTransactionState.FAILED, workflow.lookupTransaction(id).get().getState());
        finalReplay(workflow, request);
        assertArrayEquals(SENTINEL, Files.readAllBytes(target));
        empty(parent);
        record("malformed-input", failure, "FAILED", false);
    }

    private static WorkflowRequest.Builder request(Path target, WorkflowTransactionId id) {
        return WorkflowRequest.builder().transactionId(id).target("result", PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE).executionProfile(WorkflowExecutionProfile.HARDENED_WORKER);
    }
    private static void finalReplay(DocumentWorkflow workflow, WorkflowRequest request) throws Exception {
        try { workflow.execute(request, session -> { throw new AssertionError("Final callback replayed"); }); fail("Final replay passed"); }
        catch (DocumentFailure failure) { assertEquals(DocumentFailureCode.TRANSACTION_ALREADY_FINAL, failure.getCode()); }
    }
    private static void empty(Path parent) throws Exception {
        try (java.util.stream.Stream<Path> paths = Files.list(parent)) { assertEquals(0L, paths.count()); }
    }
    private static void record(String name, DocumentFailure failure, String state, boolean retry) throws Exception {
        assertEquals("result", failure.getPublicationReceipts().get(0).getTargetName());
        assertEquals(PublicationStatus.NOT_ATTEMPTED, failure.getPublicationReceipts().get(0).getStatus());
        assertFalse(failure.getPublicationReceipts().get(0).isPartialOutputPossible());
        String output = System.getProperty("folio.t21.observationOutput");
        if (output == null) { return; }
        Path directory = Paths.get(output).resolve("recovery-failures"); Files.createDirectories(directory);
        Properties observed = new Properties();
        observed.setProperty("code", failure.getCode().name()); observed.setProperty("diagnostic", failure.getDiagnostic());
        observed.setProperty("receipts", "result:NOT_ATTEMPTED:false"); observed.setProperty("retained-state", state);
        observed.setProperty("sentinel", "unchanged"); observed.setProperty("cleanup", "pass");
        observed.setProperty("final-replay", "rejected-before-callback-publication");
        observed.setProperty("retry", retry ? "COMPLETED" : "ineligible");
        if (retry) { observed.setProperty("child-terminated", "true"); observed.setProperty("interrupt-preserved", "true"); }
        Files.write(directory.resolve(name + ".sentinel"), SENTINEL);
        if (!retry) { Files.write(directory.resolve("malformed-input.pdf"), "%PDF-1.7\nnot a valid object graph\n%%EOF\n".getBytes(StandardCharsets.US_ASCII)); }
        try (java.io.OutputStream stream = Files.newOutputStream(directory.resolve(name + ".properties"))) { observed.store(stream, "Actual T22 public outcomes"); }
    }
}
