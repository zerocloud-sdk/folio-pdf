package net.zerocloud.pdf;

/**
 * The execution boundary that completed a Document Workflow.
 *
 * <p>The trusted profile executes the backend in the caller JVM. The Hardened
 * Worker Profile keeps the callback in the caller JVM while all document
 * parsing, Commands, Queries, staging, and validation execute in a bounded
 * local child process.</p>
 *
 * @since 0.1.0
 */
public enum WorkflowExecutionProfile {
    /** The workflow completed in the caller process. */
    IN_PROCESS,

    /** The document transaction completed in an isolated local Worker. */
    HARDENED_WORKER
}
