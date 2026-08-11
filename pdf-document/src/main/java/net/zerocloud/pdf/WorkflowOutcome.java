package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The detached result, capability and execution information, safe
 * diagnostics, and publication receipts of a completed workflow.
 *
 * @param <R> the caller result type
 * @since 0.1.0
 */
public final class WorkflowOutcome<R> {

    private final R result;
    private final String capabilityId;
    private final WorkflowExecutionProfile executionProfile;
    private final SaveMode saveMode;
    private final List<String> diagnostics;
    private final List<PublicationReceipt> publicationReceipts;

    WorkflowOutcome(
            R result,
            String capabilityId,
            WorkflowExecutionProfile executionProfile,
            SaveMode saveMode,
            List<String> diagnostics,
            List<PublicationReceipt> publicationReceipts) {
        this.result = result;
        this.capabilityId = Objects.requireNonNull(capabilityId, "capabilityId");
        this.executionProfile = Objects.requireNonNull(
                executionProfile,
                "executionProfile");
        this.saveMode = Objects.requireNonNull(saveMode, "saveMode");
        this.diagnostics = Collections.unmodifiableList(
                new ArrayList<String>(
                        Objects.requireNonNull(diagnostics, "diagnostics")));
        this.publicationReceipts = Collections.unmodifiableList(
                new ArrayList<PublicationReceipt>(
                        Objects.requireNonNull(
                                publicationReceipts,
                                "publicationReceipts")));
    }

    /**
     * Returns the value produced by the caller callback.
     *
     * @return the caller result, which may be {@code null}
     */
    public R getResult() {
        return result;
    }

    /**
     * Returns the stable capability identifier for this outcome.
     *
     * @return the capability identifier
     */
    public String getCapabilityId() {
        return capabilityId;
    }

    /**
     * Returns the execution boundary that completed the workflow.
     *
     * @return the execution profile
     */
    public WorkflowExecutionProfile getExecutionProfile() {
        return executionProfile;
    }

    /**
     * Returns the explicit Save Mode used by this execution.
     *
     * @return the Save Mode
     */
    public SaveMode getSaveMode() {
        return saveMode;
    }

    /**
     * Returns immutable, document-safe diagnostics from a successful run.
     *
     * <p>The list is empty when the workflow has no diagnostic to report.</p>
     *
     * @return immutable safe diagnostics
     */
    public List<String> getDiagnostics() {
        return diagnostics;
    }

    /**
     * Returns immutable publication receipts, or an empty list for read-only work.
     *
     * @return the publication receipts
     */
    public List<PublicationReceipt> getPublicationReceipts() {
        return publicationReceipts;
    }
}
