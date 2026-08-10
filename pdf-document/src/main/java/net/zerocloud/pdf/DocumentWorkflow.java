package net.zerocloud.pdf;

import java.util.Objects;

/**
 * Reusable entry point for isolated document transactions.
 *
 * <p>The workflow owns document opening, staged publication, validation, and
 * cleanup. Instances contain no per-execution state and may be reused by
 * independent callers; each supplied session remains thread-confined.</p>
 *
 * @since 0.1.0
 */
public final class DocumentWorkflow {

    /**
     * Executes one create or open request through a caller-side callback.
     *
     * @param request the immutable workflow request
     * @param work caller work at the public Document Session seam
     * @param <R> the caller result type
     * @return the detached result and publication receipts
     * @throws DocumentFailure if an operational document step fails
     */
    public <R> WorkflowOutcome<R> execute(
            WorkflowRequest request,
            DocumentWork<R> work) throws DocumentFailure {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(work, "work");
        return PdfBoxWorkflowEngine.execute(request, work);
    }
}
