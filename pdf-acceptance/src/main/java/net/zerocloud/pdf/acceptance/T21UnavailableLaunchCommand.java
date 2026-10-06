package net.zerocloud.pdf.acceptance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowEnvironment;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowRequest;

/** Actual public prerequisite refusal with a controlled non-executable Linux launcher. */
public final class T21UnavailableLaunchCommand {
    private T21UnavailableLaunchCommand() { }
    public static void main(String[] arguments) throws Exception {
        if (Files.isExecutable(Paths.get("/usr/bin/prlimit"))) { throw new AssertionError("The prerequisite negative was not applied"); }
        Path output = Files.createDirectory(Paths.get(arguments[0]));
        Path storage = Files.createDirectory(output.resolve("owned-storage"));
        Path target = output.resolve("sentinel");
        byte[] sentinel = {17, 18, 19};
        Files.write(target, sentinel);
        Properties observed = new Properties();
        try {
            new DocumentWorkflow(WorkflowEnvironment.builder().temporaryDirectory(storage).build()).execute(
                    WorkflowRequest.builder().target("result", PublicationTarget.path(target)).saveMode(SaveMode.REWRITE)
                            .executionProfile(WorkflowExecutionProfile.HARDENED_WORKER).build(),
                    session -> { throw new AssertionError("Unavailable launcher ran caller work"); });
            throw new AssertionError("Unavailable launcher passed");
        } catch (DocumentFailure failure) {
            if (failure.getCode() != DocumentFailureCode.WORKER_UNAVAILABLE || failure.getCause() != null
                    || failure.getPublicationReceipts().size() != 1
                    || failure.getPublicationReceipts().get(0).getStatus() != PublicationStatus.NOT_ATTEMPTED
                    || !"result".equals(failure.getPublicationReceipts().get(0).getTargetName())
                    || failure.getPublicationReceipts().get(0).isPartialOutputPossible()) {
                throw new AssertionError("Incorrect unavailable-launch outcome");
            }
            observed.setProperty("code", failure.getCode().name()); observed.setProperty("diagnostic", failure.getDiagnostic());
            net.zerocloud.pdf.PublicationReceipt receipt = failure.getPublicationReceipts().get(0);
            observed.setProperty("receipts", receipt.getTargetName() + ":" + receipt.getStatus().name() + ":" + receipt.isPartialOutputPossible());
        }
        if (!Arrays.equals(sentinel, Files.readAllBytes(target))) { throw new AssertionError("Unavailable launch mutated its Target"); }
        try (java.util.stream.Stream<Path> roots = Files.list(storage)) {
            if (roots.count() != 0) { throw new AssertionError("Unavailable launch retained owned data"); }
        }
        observed.setProperty("cleanup", "pass"); observed.setProperty("sentinel", "unchanged");
        observed.setProperty("prlimit-executable", "false");
        try (java.io.OutputStream stream = Files.newOutputStream(output.resolve("observations.properties"))) {
            observed.store(stream, "Actual unavailable prlimit observation");
        }
        RetainedEvidence retained = new RetainedEvidence(output);
        retained.retain(target, EvidenceFiles.sha256(target));
        retained.retain(output.resolve("observations.properties"), EvidenceFiles.sha256(output.resolve("observations.properties")));
        String manifest = retained.publishManifest("pass");
        Properties result = new Properties();
        result.setProperty("profile", "T21-unavailable-prlimit"); result.setProperty("result", "pass");
        result.setProperty("retained-files-sha256", manifest);
        retained.write(output.resolve("result.properties"), result);
        retained.verify();
        System.out.println("T21 absent-prlimit control passed");
    }
}
