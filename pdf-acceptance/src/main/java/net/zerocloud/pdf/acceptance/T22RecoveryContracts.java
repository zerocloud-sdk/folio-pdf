package net.zerocloud.pdf.acceptance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import net.zerocloud.pdf.*;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.query.PageCount;

/** Retained lookup, receipt and replay outcomes through the public Native Interface. */
final class T22RecoveryContracts {
    private final Path output;
    private final Path storage;
    private final DocumentWorkflow workflow;
    private final RetainedEvidence retained;
    private final Properties observed = new Properties();
    private static final byte[] SENTINEL = {17, 18, 19};

    private T22RecoveryContracts(Path output) throws IOException {
        this.output = output;
        storage = Files.createDirectory(output.resolve("owned-storage"));
        workflow = new DocumentWorkflow(WorkflowEnvironment.builder().temporaryDirectory(storage).build());
        retained = new RetainedEvidence(output);
    }

    static RetainedEvidence record(Path output) throws Exception {
        T22RecoveryContracts contract = new T22RecoveryContracts(output);
        contract.pathAcknowledgement(); contract.streamAcknowledgement(); contract.partialPublication(); contract.retry();
        contract.observed.setProperty("execution-profile", "HARDENED_WORKER");
        contract.retained.write(output.resolve("recovery-observations.properties"), contract.observed);
        return contract.retained;
    }

    private void pathAcknowledgement() throws Exception {
        Path first = product("native-acknowledgement"), second = product("native-acknowledgement-second");
        RuntimeException lost = new RuntimeException("caller acknowledgement lost");
        final int[] committed = {0};
        WorkflowRequest request = request("path-acknowledgement").target("first", PublicationTarget.path(first))
                .target("second", PublicationTarget.path(second)).progressListener(phase -> {
                    if (phase == WorkflowProgressPhase.TARGET_COMMITTED && ++committed[0] == 2) { throw lost; }
                }).build();
        try { publish(request); throw new AssertionError("Lost acknowledgement was hidden"); }
        catch (RuntimeException actual) { check(actual == lost, "caller exception identity changed"); }
        WorkflowTransactionStatus status = status(request, WorkflowTransactionState.COMPLETED);
        observe("path-acknowledgement", status);
        check(receipts(status.getPublicationReceipts()).equals("first:COMMITTED:false,second:COMMITTED:false"), "Path commit receipts");
        String firstHash = EvidenceFiles.sha256(first), secondHash = EvidenceFiles.sha256(second);
        finalReplay(request);
        check(firstHash.equals(EvidenceFiles.sha256(first)) && secondHash.equals(EvidenceFiles.sha256(second)), "Path replay published");
        observed.setProperty("path-acknowledgement.replay", "rejected; callback=0; products-unchanged");
        retainBlank(first); retainBlank(second); cleanup();
    }

    private void streamAcknowledgement() throws Exception {
        BorrowedOutput stream = new BorrowedOutput();
        RuntimeException lost = new RuntimeException("caller stream acknowledgement lost");
        WorkflowRequest request = request("stream-acknowledgement").target("result", PublicationTarget.stream(stream))
                .progressListener(phase -> { if (phase == WorkflowProgressPhase.TARGET_COMMITTED) { throw lost; } }).build();
        try { publish(request); throw new AssertionError("Lost stream acknowledgement was hidden"); }
        catch (RuntimeException actual) { check(actual == lost, "caller exception identity changed"); }
        WorkflowTransactionStatus status = status(request, WorkflowTransactionState.COMPLETED);
        check(receipts(status.getPublicationReceipts()).equals("result:COMMITTED:false"), "Stream commit receipt");
        observe("stream-acknowledgement", status);
        byte[] bytes = stream.toByteArray(); finalReplay(request);
        check(!stream.closed && Arrays.equals(bytes, stream.toByteArray()), "committed stream was closed/replayed");
        observed.setProperty("stream-acknowledgement.replay", "rejected; callback=0; bytes-unchanged");
        observed.setProperty("stream-acknowledgement.caller-output-open", "true");
        Path product = product("native-stream"); retained.write(product, bytes); retainBlank(product); cleanup();
    }

    private void partialPublication() throws Exception {
        Path first = product("native-before-failure"), later = output.resolve("native-later.sentinel");
        retained.write(later, SENTINEL);
        BrokenOutput current = new BrokenOutput();
        WorkflowRequest request = request("partial-publication").target("first", PublicationTarget.path(first))
                .target("current", PublicationTarget.stream(current)).target("later", PublicationTarget.path(later)).build();
        try { publish(request); throw new AssertionError("Broken stream passed"); }
        catch (DocumentFailure failure) { check(failure.getCode() == DocumentFailureCode.PUBLICATION_FAILED, "unstable publication code"); }
        WorkflowTransactionStatus status = status(request, WorkflowTransactionState.FAILED);
        check(receipts(status.getPublicationReceipts()).equals("first:COMMITTED:false,current:FAILED:true,later:NOT_ATTEMPTED:false"), "ordered partial receipts");
        observe("partial-publication", status);
        check(status.getFailureCode().get() == DocumentFailureCode.PUBLICATION_FAILED, "retained failure changed");
        int bytes = current.count; String committed = EvidenceFiles.sha256(first); finalReplay(request);
        check(bytes == current.count && !current.closed && committed.equals(EvidenceFiles.sha256(first))
                && Arrays.equals(SENTINEL, Files.readAllBytes(later)), "partial output replay, ownership or later Target");
        observed.setProperty("partial-publication.replay", "rejected; callback=0; bytes-unchanged");
        observed.setProperty("partial-publication.caller-output-open", "true");
        observed.setProperty("partial-publication.later-target", "unchanged");
        retainBlank(first); cleanup();
    }

    private void retry() throws Exception {
        Path target = product("native-retry"); Files.write(target, SENTINEL);
        CancellationToken cancelled = CancellationToken.create(); cancelled.cancel();
        WorkflowRequest initial = request("recoverable-cancellation").target("result", PublicationTarget.path(target)).cancellationToken(cancelled).build();
        try { workflow.execute(initial, session -> { throw new AssertionError("Cancelled callback ran"); }); throw new AssertionError("Cancellation passed"); }
        catch (DocumentFailure failure) { check(failure.getCode() == DocumentFailureCode.WORKFLOW_CANCELLED, "cancellation code changed"); }
        WorkflowTransactionStatus recoverable = status(initial, WorkflowTransactionState.RECOVERABLE);
        check(receipts(recoverable.getPublicationReceipts()).equals("result:NOT_ATTEMPTED:false"), "retry eligibility receipts");
        check(Arrays.equals(SENTINEL, Files.readAllBytes(target)), "cancelled Target changed"); observe("cancelled", recoverable); cleanup();
        WorkflowRequest retry = request("recoverable-cancellation").target("result", PublicationTarget.path(target)).build();
        workflow.execute(retry, session -> {
            observe("running", status(retry, WorkflowTransactionState.RUNNING));
            try { workflow.execute(retry, ignored -> { throw new AssertionError("Running duplicate callback ran"); }); throw new AssertionError("Running duplicate passed"); }
            catch (DocumentFailure failure) { check(failure.getCode() == DocumentFailureCode.TRANSACTION_IN_PROGRESS, "running duplicate code"); }
            WorkflowRequest mismatch = request("recoverable-cancellation").target("different", PublicationTarget.path(target)).build();
            try { workflow.execute(mismatch, ignored -> { throw new AssertionError("Mismatch callback ran"); }); throw new AssertionError("Mismatch passed"); }
            catch (DocumentFailure failure) { check(failure.getCode() == DocumentFailureCode.TRANSACTION_IDENTITY_MISMATCH, "material envelope conflict"); }
            session.execute(AddBlankPage.INSTANCE); return null;
        });
        observe("retry", status(retry, WorkflowTransactionState.COMPLETED)); finalReplay(retry); retainBlank(target); cleanup();
        observed.setProperty("running.duplicate", "TRANSACTION_IN_PROGRESS; callback=0");
        observed.setProperty("running.conflict", "TRANSACTION_IDENTITY_MISMATCH; callback=0");
        observed.setProperty("retry.replay", "TRANSACTION_ALREADY_FINAL; callback=0");
        check(!new DocumentWorkflow().lookupTransaction(retry.getTransactionId().get()).isPresent(), "identity escaped Environment");
        observed.setProperty("environment-local", "lookup absent in another Environment");
    }

    private WorkflowRequest.Builder request(String id) {
        return WorkflowRequest.builder().transactionId(WorkflowTransactionId.of(id)).saveMode(SaveMode.REWRITE)
                .executionProfile(WorkflowExecutionProfile.HARDENED_WORKER);
    }
    private void publish(WorkflowRequest request) throws DocumentFailure {
        workflow.execute(request, session -> { session.execute(AddBlankPage.INSTANCE); return null; });
    }
    private WorkflowTransactionStatus status(WorkflowRequest request, WorkflowTransactionState expected) {
        WorkflowTransactionStatus status = workflow.lookupTransaction(request.getTransactionId().get()).get();
        check(status.getState() == expected && status.getTransactionId().equals(request.getTransactionId().get()), "lookup state/identity");
        return status;
    }
    private void observe(String name, WorkflowTransactionStatus status) {
        observed.setProperty(name + ".identity", status.getTransactionId().getValue());
        observed.setProperty(name + ".state", status.getState().name());
        observed.setProperty(name + ".receipts", receipts(status.getPublicationReceipts()));
        if (status.getFailureCode().isPresent()) { observed.setProperty(name + ".failure-code", status.getFailureCode().get().name()); }
    }
    private void finalReplay(WorkflowRequest request) throws DocumentFailure {
        try { workflow.execute(request, session -> { throw new AssertionError("Final callback ran"); }); throw new AssertionError("Final replay passed"); }
        catch (DocumentFailure failure) { check(failure.getCode() == DocumentFailureCode.TRANSACTION_ALREADY_FINAL, "final replay code"); }
    }
    private Path product(String name) throws IOException { return Files.createDirectory(output.resolve(name)).resolve("blank.pdf"); }
    private void retainBlank(Path product) throws Exception {
        check(T03BlankSemantics.inspect(product, WorkflowExecutionProfile.HARDENED_WORKER) == EvidenceResult.PASS, "public reopen semantics");
        retained.retain(product, EvidenceFiles.sha256(product));
    }
    private void cleanup() throws IOException {
        try (java.util.stream.Stream<Path> roots = Files.list(storage)) { check(roots.count() == 0, "owned storage remained"); }
        observed.setProperty("owned-storage-cleanup", "pass");
    }
    private static String receipts(List<PublicationReceipt> receipts) {
        StringBuilder result = new StringBuilder();
        for (PublicationReceipt receipt : receipts) {
            if (result.length() > 0) { result.append(','); }
            result.append(receipt.getTargetName()).append(':').append(receipt.getStatus()).append(':').append(receipt.isPartialOutputPossible());
        }
        return result.toString();
    }
    private static void check(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
    private static final class BorrowedOutput extends ByteArrayOutputStream {
        private boolean closed;
        @Override public void close() { closed = true; }
    }
    private static final class BrokenOutput extends java.io.OutputStream {
        private int count; private boolean closed;
        @Override public void write(int value) throws IOException { if (count == 1) { throw new IOException("caller-owned broken stream"); } count++; }
        @Override public void close() { closed = true; }
    }
}
