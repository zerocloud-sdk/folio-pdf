package net.zerocloud.pdf.provider;

/**
 * The location and mechanism used by a Capability Provider engine.
 *
 * <p>This is deliberately distinct from a Document Workflow execution
 * profile. A workflow may execute in-process while brokering a subprocess or
 * remote Provider.</p>
 *
 * @since 0.1.0
 */
public enum ProviderExecutionMode {
    /** The engine is Java code in the caller process. */
    IN_PROCESS,

    /** The engine is reached through native linkage in the caller process. */
    NATIVE,

    /** The engine runs as a local child process. */
    SUBPROCESS,

    /** The engine runs behind a remote service. */
    REMOTE
}
