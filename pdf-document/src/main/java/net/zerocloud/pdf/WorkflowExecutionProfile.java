package net.zerocloud.pdf;

/**
 * The execution boundary that completed a Document Workflow.
 *
 * <p>T03 implements trusted in-process execution. The Hardened Worker Profile
 * remains scoped to T21 and is not represented as an available execution
 * option here.</p>
 *
 * @since 0.1.0
 */
public enum WorkflowExecutionProfile {
    /** The workflow completed in the caller process. */
    IN_PROCESS
}
