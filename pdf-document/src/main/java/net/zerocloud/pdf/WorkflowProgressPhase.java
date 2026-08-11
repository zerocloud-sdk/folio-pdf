package net.zerocloud.pdf;

/**
 * Sanitized transaction phases emitted in execution order.
 *
 * <p>Phases carry no filenames, paths, document data, metadata, credentials,
 * target names, or exception details.</p>
 *
 * @since 0.1.0
 */
public enum WorkflowProgressPhase {
    /** The request passed initial cancellation and deadline checks. */
    STARTED,

    /** The selected primary source was opened. */
    SOURCE_OPENED,

    /** Caller work is about to run. */
    WORK_STARTED,

    /** Caller work returned and its Session was invalidated. */
    WORK_COMPLETED,

    /** The rewrite was staged without touching publication targets. */
    STAGED,

    /** The staged rewrite passed document validation. */
    VALIDATED,

    /** Ordered target publication is about to begin. */
    PUBLICATION_STARTED,

    /** One target was committed. */
    TARGET_COMMITTED,

    /** The workflow completed successfully. */
    COMPLETED
}
