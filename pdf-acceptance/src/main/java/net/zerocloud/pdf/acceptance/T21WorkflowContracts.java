package net.zerocloud.pdf.acceptance;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PublicationReceipt;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowEnvironment;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowProgressPhase;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.command.InsertBlankPage;
import net.zerocloud.pdf.query.PageCount;

/** Public T21 observations; no backend, protocol shape or private call is an oracle. */
final class T21WorkflowContracts {
    private final Path output;
    private final Path storage;
    private final DocumentWorkflow workflow;
    private final RetainedEvidence retained;
    private final Properties observed = new Properties();
    private static final byte[] SENTINEL = {17, 18, 19};

    private T21WorkflowContracts(Path output) throws IOException {
        this.output = output;
        storage = Files.createDirectory(output.resolve("owned-storage"));
        workflow = new DocumentWorkflow(WorkflowEnvironment.builder().temporaryDirectory(storage)
                .clock(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)).build());
        retained = new RetainedEvidence(output);
    }

    static RetainedEvidence record(Path output) throws Exception {
        T21WorkflowContracts contract = new T21WorkflowContracts(output);
        contract.ordering();
        contract.ownership();
        contract.failures();
        contract.partialPublication();
        contract.observed.setProperty("execution-profile", "HARDENED_WORKER");
        contract.observed.setProperty("case-count", "8");
        contract.retained.write(output.resolve("observations.properties"), contract.observed);
        return contract.retained;
    }

    private void ordering() throws Exception {
        Path product = product("native-success");
        final DocumentSession[] view = new DocumentSession[1];
        List<WorkflowProgressPhase> progress = new ArrayList<WorkflowProgressPhase>();
        WorkflowOutcome<Integer> result = workflow.execute(request(product).progressListener(progress::add).build(), session -> {
            view[0] = session;
            try {
                session.executeBatch(Arrays.asList(AddBlankPage.INSTANCE, InsertBlankPage.version1(0), AddBlankPage.INSTANCE));
                throw new AssertionError("An invalid ordered Command passed");
            } catch (DocumentFailure expected) {
                check(expected.getCode() == DocumentFailureCode.PAGE_POSITION_INVALID, "first failure changed");
                observed.setProperty("ordered-prefix.code", expected.getCode().name());
                observed.setProperty("ordered-prefix.diagnostic", expected.getDiagnostic());
            }
            check(session.query(PageCount.INSTANCE) == 1, "Query did not observe the successful prefix");
            session.executeBatch(Arrays.asList(AddBlankPage.INSTANCE));
            check(session.query(PageCount.INSTANCE) == 2, "Query barrier missed a completed batch");
            session.execute(net.zerocloud.pdf.command.RemovePages.version1(net.zerocloud.pdf.PageRange.of(2, 2)));
            return session.query(PageCount.INSTANCE);
        });
        observed.setProperty("ordered-prefix.receipts", success(result));
        check(progress.equals(Arrays.asList(WorkflowProgressPhase.STARTED, WorkflowProgressPhase.WORK_STARTED,
                WorkflowProgressPhase.WORK_COMPLETED, WorkflowProgressPhase.STAGED, WorkflowProgressPhase.VALIDATED,
                WorkflowProgressPhase.PUBLICATION_STARTED, WorkflowProgressPhase.TARGET_COMMITTED,
                WorkflowProgressPhase.COMPLETED)), "progress order changed: " + progress);
        observed.setProperty("ordered-prefix.pages", "1");
        observed.setProperty("ordered-prefix.barrier-pages", "2");
        observed.setProperty("ordered-prefix.progress", "STARTED,WORK_STARTED,WORK_COMPLETED,STAGED,VALIDATED,PUBLICATION_STARTED,TARGET_COMMITTED,COMPLETED");
        try { view[0].query(PageCount.INSTANCE); throw new AssertionError("Expired Session remained usable"); }
        catch (IllegalStateException expected) { observed.setProperty("ordered-prefix.expired-view", "pass"); }
        check(T03BlankSemantics.inspect(product, WorkflowExecutionProfile.HARDENED_WORKER) == EvidenceResult.PASS, "public reopen failed");
        retained.retain(product, EvidenceFiles.sha256(product));
        complete("ordered-prefix");
    }

    private void ownership() throws Exception {
        BorrowedInput input = new BorrowedInput(Files.readAllBytes(output.resolve("native-success/blank.pdf")));
        BorrowedOutput target = new BorrowedOutput();
        WorkflowOutcome<Integer> result = workflow.execute(WorkflowRequest.builder()
                .source("source", DocumentSource.stream(input, input.available())).primarySource("source")
                .target("result", PublicationTarget.stream(target)).saveMode(SaveMode.REWRITE)
                .executionProfile(WorkflowExecutionProfile.HARDENED_WORKER).build(), session -> session.query(PageCount.INSTANCE));
        observed.setProperty("caller-ownership.receipts", success(result));
        check(!input.closed && !target.closed, "caller-owned stream was closed");
        Path product = product("native-owned-stream");
        retained.write(product, target.toByteArray());
        check(T03BlankSemantics.inspect(product, WorkflowExecutionProfile.HARDENED_WORKER) == EvidenceResult.PASS, "stream outcome did not reopen");
        observed.setProperty("caller-ownership.input-open", "true");
        observed.setProperty("caller-ownership.output-open", "true");
        observed.setProperty("caller-ownership.pages", "1");
        complete("caller-ownership");
    }

    private void failures() throws Exception {
        for (String kind : Arrays.asList("checked-failure", "cancelled", "deadline", "caller-failure")) {
            Path target = output.resolve(kind + ".sentinel");
            retained.write(target, SENTINEL);
            WorkflowRequest.Builder request = request(target);
            if ("cancelled".equals(kind)) {
                net.zerocloud.pdf.CancellationToken cancellation = net.zerocloud.pdf.CancellationToken.create();
                cancellation.cancel();
                request.cancellationToken(cancellation);
            }
            if ("deadline".equals(kind)) { request.deadline(Instant.EPOCH); }
            RuntimeException caller = new RuntimeException("T21 caller identity control");
            try {
                workflow.execute(request.build(), session -> {
                    if ("caller-failure".equals(kind)) { throw caller; }
                    if (!"checked-failure".equals(kind)) { throw new AssertionError("Rejected admission ran caller work"); }
                    session.execute(InsertBlankPage.version1(0));
                    return null;
                });
                throw new AssertionError("Required failure passed: " + kind);
            } catch (DocumentFailure failure) {
                DocumentFailureCode expected = "checked-failure".equals(kind) ? DocumentFailureCode.PAGE_POSITION_INVALID
                        : "cancelled".equals(kind) ? DocumentFailureCode.WORKFLOW_CANCELLED : DocumentFailureCode.DEADLINE_EXCEEDED;
                check(failure.getCode() == expected && failure.getCause() == null, "owning checked failure changed");
                observed.setProperty(kind + ".receipts", receipts(failure.getPublicationReceipts(), PublicationStatus.NOT_ATTEMPTED));
                observed.setProperty(kind + ".code", failure.getCode().name());
                observed.setProperty(kind + ".diagnostic", failure.getDiagnostic());
            } catch (RuntimeException failure) {
                check("caller-failure".equals(kind) && failure == caller && failure.getSuppressed().length == 0,
                        "caller exception identity or precedence changed");
                observed.setProperty(kind + ".same-exception", "true");
            }
            check(Arrays.equals(SENTINEL, Files.readAllBytes(target)), "failed workflow replaced its Target");
            observed.setProperty(kind + ".sentinel", "unchanged");
            complete(kind);
        }
    }

    private void partialPublication() throws Exception {
        Path first = product("native-before-failure"), last = output.resolve("native-later.sentinel");
        retained.write(last, SENTINEL);
        BrokenOutput current = new BrokenOutput();
        try {
            workflow.execute(WorkflowRequest.builder().target("first", PublicationTarget.path(first))
                    .target("current", PublicationTarget.stream(current)).target("later", PublicationTarget.path(last))
                    .saveMode(SaveMode.REWRITE).executionProfile(WorkflowExecutionProfile.HARDENED_WORKER).build(), session -> {
                        session.execute(AddBlankPage.INSTANCE); return null;
                    });
            throw new AssertionError("Partial stream publication passed");
        } catch (DocumentFailure failure) {
            check(failure.getCode() == DocumentFailureCode.PUBLICATION_FAILED && failure.getCause() == null, "unsafe publication failure");
            observed.setProperty("ordered-publication.receipts", receipts(failure.getPublicationReceipts(),
                    PublicationStatus.COMMITTED, PublicationStatus.FAILED, PublicationStatus.NOT_ATTEMPTED));
            check(failure.getPublicationReceipts().get(1).isPartialOutputPossible(), "partial stream receipt was lost");
            observed.setProperty("ordered-publication.code", failure.getCode().name());
            observed.setProperty("ordered-publication.diagnostic", failure.getDiagnostic());
        }
        check(current.writes == 2 && !current.closed && Arrays.equals(SENTINEL, Files.readAllBytes(last)), "publication ownership or later Target changed");
        check(T03BlankSemantics.inspect(first, WorkflowExecutionProfile.HARDENED_WORKER) == EvidenceResult.PASS, "committed prefix did not reopen");
        retained.retain(first, EvidenceFiles.sha256(first));
        observed.setProperty("ordered-publication.output-open", "true");
        observed.setProperty("ordered-publication.sentinel", "unchanged");
        complete("ordered-publication");
        observed.setProperty("owned-cleanup.empty", "true");
        complete("owned-cleanup");
    }

    private Path product(String name) throws IOException { return Files.createDirectory(output.resolve(name)).resolve("blank.pdf"); }
    private WorkflowRequest.Builder request(Path target) {
        return WorkflowRequest.builder().target("result", PublicationTarget.path(target)).saveMode(SaveMode.REWRITE)
                .executionProfile(WorkflowExecutionProfile.HARDENED_WORKER);
    }
    private static String success(WorkflowOutcome<Integer> outcome) {
        check(outcome.getResult() == 1 && outcome.getExecutionProfile() == WorkflowExecutionProfile.HARDENED_WORKER, "actual outcome/profile changed");
        return receipts(outcome.getPublicationReceipts(), PublicationStatus.COMMITTED);
    }
    private static String receipts(List<PublicationReceipt> values, PublicationStatus... expected) {
        check(values.size() == expected.length, "receipt count changed");
        String[] names = expected.length == 1 ? new String[] {"result"} : new String[] {"first", "current", "later"};
        StringBuilder observed = new StringBuilder();
        for (int i = 0; i < expected.length; i++) {
            PublicationReceipt receipt = values.get(i);
            check(receipt.getTargetName().equals(names[i]) && receipt.getStatus() == expected[i], "receipt target/order changed");
            check(receipt.isPartialOutputPossible() == (expected[i] == PublicationStatus.FAILED), "receipt partial-output flag changed");
            if (i > 0) { observed.append(','); }
            observed.append(receipt.getTargetName()).append(':').append(receipt.getStatus().name())
                    .append(':').append(receipt.isPartialOutputPossible());
        }
        return observed.toString();
    }
    private void complete(String name) throws IOException {
        try (java.util.stream.Stream<Path> paths = Files.list(storage)) { check(paths.count() == 0, "owned transaction data remained"); }
        observed.setProperty(name + ".cleanup", "pass");
        observed.setProperty(name + ".result", "pass");
    }
    private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError("T21 " + message); } }
    private static final class BorrowedInput extends ByteArrayInputStream {
        private boolean closed;
        BorrowedInput(byte[] bytes) { super(bytes); }
        @Override public void close() { closed = true; }
    }
    private static final class BorrowedOutput extends ByteArrayOutputStream {
        private boolean closed;
        @Override public void close() { closed = true; }
    }
    private static final class BrokenOutput extends OutputStream {
        private int writes;
        private boolean closed;
        @Override public void write(int value) throws IOException { if (++writes > 1) { throw new IOException("T21 broken stream control"); } }
        @Override public void close() { closed = true; }
    }
}
