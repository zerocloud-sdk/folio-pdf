package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.zerocloud.pdf.provider.ProviderSelection;

/**
 * The caller result, capability and execution information, safe diagnostics,
 * publication receipts, transaction identity and resource observations, and
 * declaration-ordered Capability Provider selection metadata of a completed
 * workflow.
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
    private final List<ProviderSelection> providerSelections;
    private final WorkflowTransactionId transactionId;
    private final WorkflowResourceUsage resourceUsage;

    WorkflowOutcome(
            R result,
            String capabilityId,
            WorkflowExecutionProfile executionProfile,
            SaveMode saveMode,
            List<String> diagnostics,
            List<PublicationReceipt> publicationReceipts,
            List<ProviderSelection> providerSelections) {
        this(
                result,
                capabilityId,
                executionProfile,
                saveMode,
                diagnostics,
                publicationReceipts,
                providerSelections,
                null);
    }

    WorkflowOutcome(
            R result,
            String capabilityId,
            WorkflowExecutionProfile executionProfile,
            SaveMode saveMode,
            List<String> diagnostics,
            List<PublicationReceipt> publicationReceipts,
            List<ProviderSelection> providerSelections,
            WorkflowTransactionId transactionId) {
        this(
                result,
                capabilityId,
                executionProfile,
                saveMode,
                diagnostics,
                publicationReceipts,
                providerSelections,
                transactionId,
                null);
    }

    private WorkflowOutcome(
            R result,
            String capabilityId,
            WorkflowExecutionProfile executionProfile,
            SaveMode saveMode,
            List<String> diagnostics,
            List<PublicationReceipt> publicationReceipts,
            List<ProviderSelection> providerSelections,
            WorkflowTransactionId transactionId,
            WorkflowResourceUsage resourceUsage) {
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
        this.providerSelections = Collections.unmodifiableList(
                new ArrayList<ProviderSelection>(
                        Objects.requireNonNull(
                                providerSelections,
                                "providerSelections")));
        this.transactionId = transactionId;
        this.resourceUsage = resourceUsage;
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

    WorkflowOutcome<R> withAdditionalDiagnostics(List<String> additional) {
        if (additional.isEmpty()) { return this; }
        List<String> combined = new ArrayList<String>(diagnostics);
        for (String diagnostic : additional) {
            if (!combined.contains(diagnostic)) { combined.add(diagnostic); }
        }
        return new WorkflowOutcome<R>(result, capabilityId, executionProfile, saveMode,
                combined, publicationReceipts, providerSelections, transactionId, resourceUsage);
    }

    WorkflowOutcome<R> withProviderSelections(List<ProviderSelection> selections) {
        return new WorkflowOutcome<R>(result, capabilityId, executionProfile, saveMode,
                diagnostics, publicationReceipts, selections, transactionId, resourceUsage);
    }

    /**
     * Returns immutable publication receipts, or an empty list for read-only work.
     *
     * @return the publication receipts
     */
    public List<PublicationReceipt> getPublicationReceipts() {
        return publicationReceipts;
    }

    /**
     * Returns declaration-ordered Capability Provider selections made for the
     * workflow request.
     *
     * @return immutable Provider selections, empty when none were requested
     */
    public List<ProviderSelection> getProviderSelections() {
        return providerSelections;
    }

    /** @return the optional identity of this logical workflow transaction */
    public Optional<WorkflowTransactionId> getTransactionId() {
        return Optional.ofNullable(transactionId);
    }

    /**
     * Returns the completed transaction's Folio-owned resource observations.
     *
     * @return detached resource usage
     */
    public WorkflowResourceUsage getResourceUsage() {
        return Objects.requireNonNull(
                resourceUsage,
                "Resource usage is attached by DocumentWorkflow.execute.");
    }

    WorkflowOutcome<R> withResourceUsage(WorkflowResourceUsage value) {
        return new WorkflowOutcome<R>(
                result,
                capabilityId,
                executionProfile,
                saveMode,
                diagnostics,
                publicationReceipts,
                providerSelections,
                transactionId,
                Objects.requireNonNull(value, "value"));
    }
}
