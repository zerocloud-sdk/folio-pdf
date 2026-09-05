package net.zerocloud.pdf;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.query.PageCount;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Real-process fault injection observed at the public workflow seam. */
public final class HardenedWorkerRecoveryFaultTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void workerDeathIsRecoverableBeforePublication() throws Exception {
        assertRecoverableFault(
                WorkflowTransactionId.of("worker-death-0001"),
                DocumentFailureCode.WORKER_TERMINATED,
                session -> {
                    HardenedWorkerEngine.terminateWorkerForTest(session);
                    session.query(PageCount.INSTANCE);
                    return null;
                });
    }

    @Test
    public void malformedWorkerResponseIsRecoverableBeforePublication()
            throws Exception {
        assertRecoverableFault(
                WorkflowTransactionId.of("malformed-response-0001"),
                DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                HardenedWorkerEngine::requestMalformedResponseForTest);
    }

    private void assertRecoverableFault(
            WorkflowTransactionId transactionId,
            DocumentFailureCode failureCode,
            DocumentWork<Void> fault) throws Exception {
        Path target = temporaryFolder.getRoot().toPath()
                .resolve(transactionId.getValue() + ".pdf");
        byte[] original = new byte[] {17, 18, 19};
        Files.write(target, original);
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .temporaryDirectory(temporaryFolder.getRoot().toPath())
                .build();
        DocumentWorkflow workflow = new DocumentWorkflow(environment);
        WorkflowRequest request = request(target, transactionId);

        try {
            workflow.execute(request, fault);
            fail("Expected the injected Worker fault");
        } catch (DocumentFailure failure) {
            assertEquals(failureCode, failure.getCode());
            assertEquals(transactionId, failure.getTransactionId().get());
            assertEquals(
                    PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(0).getStatus());
        }

        WorkflowTransactionStatus status =
                workflow.lookupTransaction(transactionId).get();
        assertEquals(WorkflowTransactionState.RECOVERABLE, status.getState());
        assertEquals(failureCode, status.getFailureCode().get());
        assertEquals(
                PublicationStatus.NOT_ATTEMPTED,
                status.getPublicationReceipts().get(0).getStatus());
        assertArrayEquals(original, Files.readAllBytes(target));
        assertNoWorkflowRoots(temporaryFolder.getRoot().toPath());

        workflow.execute(request, session -> {
            session.execute(AddBlankPage.INSTANCE);
            return null;
        });
        assertEquals(
                WorkflowTransactionState.COMPLETED,
                workflow.lookupTransaction(transactionId).get().getState());
        assertFalse(java.util.Arrays.equals(original, Files.readAllBytes(target)));
        assertNoWorkflowRoots(temporaryFolder.getRoot().toPath());
    }

    private static void assertNoWorkflowRoots(Path parent) throws Exception {
        try (DirectoryStream<Path> roots = Files.newDirectoryStream(
                parent,
                ".folio-pdf-workflow-*")) {
            assertFalse(roots.iterator().hasNext());
        }
    }

    private static WorkflowRequest request(
            Path target,
            WorkflowTransactionId transactionId) {
        return WorkflowRequest.builder()
                .transactionId(transactionId)
                .target("result", PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE)
                .executionProfile(WorkflowExecutionProfile.HARDENED_WORKER)
                .build();
    }
}
