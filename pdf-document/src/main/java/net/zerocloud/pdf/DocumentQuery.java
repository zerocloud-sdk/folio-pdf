package net.zerocloud.pdf;

/**
 * A project-defined query that observes all preceding commands in a session.
 *
 * <p>This interface is not a user extension point. A session rejects query
 * implementations that are not supplied by Open PDF.</p>
 *
 * @param <R> the detached query result type
 * @since 0.1.0
 */
public interface DocumentQuery<R> {
}
