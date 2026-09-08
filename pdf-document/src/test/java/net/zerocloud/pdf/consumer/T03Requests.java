package net.zerocloud.pdf.consumer;

import java.nio.file.Path;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowRequest;

/** Runs the same public T03 outcomes at the explicitly selected boundary. */
final class T03Requests {
    private T03Requests() {
    }

    static WorkflowExecutionProfile profile() {
        return WorkflowExecutionProfile.valueOf(
                System.getProperty("folio.t03.executionProfile", "IN_PROCESS"));
    }

    static WorkflowRequest.Builder builder() {
        return WorkflowRequest.builder().executionProfile(profile());
    }

    static WorkflowRequest create(Path target, SaveMode mode) {
        if (profile() == WorkflowExecutionProfile.IN_PROCESS) {
            return WorkflowRequest.create(target, mode);
        }
        return builder().target("target", PublicationTarget.path(target)).saveMode(mode).build();
    }

    static WorkflowRequest open(Path source, SaveMode mode) {
        if (profile() == WorkflowExecutionProfile.IN_PROCESS) {
            return WorkflowRequest.open(source, mode);
        }
        return builder().source("source", DocumentSource.path(source)).primarySource("source")
                .saveMode(mode).build();
    }
}
