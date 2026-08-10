package net.zerocloud.pdf;

/**
 * Caller work performed inside one thread-confined {@link DocumentSession}.
 *
 * @param <R> the caller result type
 * @since 0.1.0
 */
@FunctionalInterface
public interface DocumentWork<R> {

    /**
     * Performs caller work through the supplied public session.
     *
     * @param session the active session
     * @return a caller-defined result
     * @throws DocumentFailure if a document operation fails
     */
    R perform(DocumentSession session) throws DocumentFailure;
}
