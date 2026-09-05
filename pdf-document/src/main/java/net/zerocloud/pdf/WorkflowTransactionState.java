package net.zerocloud.pdf;

/**
 * Observable lifecycle state of an identified Document Workflow.
 *
 * @since 0.1.0
 */
public enum WorkflowTransactionState {
    /** One admitted attempt is currently executing. */
    RUNNING,

    /** No target was attempted and the same logical request may be retried. */
    RECOVERABLE,

    /** The workflow completed and every reported publication is final. */
    COMPLETED,

    /** The workflow ended terminally and must not be replayed with this identity. */
    FAILED
}
