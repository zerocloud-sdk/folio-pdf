package net.zerocloud.pdf;

/**
 * Receives sanitized progress phases synchronously on the executing thread.
 *
 * <p>A listener is caller code and must not throw. It receives only a phase
 * enum so transaction data cannot be exposed through progress events.</p>
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface WorkflowProgressListener {

    /**
     * Observes one transaction phase.
     *
     * @param phase the sanitized phase
     */
    void onProgress(WorkflowProgressPhase phase);
}
