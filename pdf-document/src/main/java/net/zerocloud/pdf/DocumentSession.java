package net.zerocloud.pdf;

/**
 * The thread-confined interaction scope of one Document Workflow execution.
 *
 * <p>A session is valid only while its {@link DocumentWork} callback is
 * running. Commands are applied in order, and a query observes every earlier
 * command.</p>
 *
 * @since 0.1.0
 */
public interface DocumentSession {

    /**
     * Applies one library-owned command.
     *
     * @param command the command to apply
     * @throws DocumentFailure if the command cannot be applied
     */
    void execute(DocumentCommand command) throws DocumentFailure;

    /**
     * Evaluates one library-owned query after all preceding commands.
     *
     * @param query the query to evaluate
     * @param <R> the detached result type
     * @return the query result
     * @throws DocumentFailure if the query cannot be evaluated
     */
    <R> R query(DocumentQuery<R> query) throws DocumentFailure;
}
