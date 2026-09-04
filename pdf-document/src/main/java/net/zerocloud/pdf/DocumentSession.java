package net.zerocloud.pdf;

import java.util.List;

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
     * Applies an ordered batch of library-owned commands.
     *
     * <p>The default in-process contract applies each command in declaration
     * order. Execution stops at the first failure. Process-backed Sessions use
     * internal bounded messages without exposing their wire representation as
     * a public contract.</p>
     *
     * @param commands the commands to apply in declaration order
     * @throws DocumentFailure if a command cannot be applied
     */
    default void executeBatch(
            List<? extends DocumentCommand> commands) throws DocumentFailure {
        List<DocumentCommand> copied =
                DocumentWorkflow.copyAndValidateCommands(commands);
        for (DocumentCommand command : copied) {
            execute(command);
        }
    }

    /**
     * Evaluates one library-owned query after all preceding commands.
     *
     * @param query the query to evaluate
     * @param <R> the query result type; each query documents its lifecycle
     * @return the query result
     * @throws DocumentFailure if the query cannot be evaluated
     */
    <R> R query(DocumentQuery<R> query) throws DocumentFailure;
}
