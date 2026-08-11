package net.zerocloud.pdf;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A thread-safe signal used to cancel one or more workflow requests.
 *
 * @since 0.1.0
 */
public final class CancellationToken {

    private final AtomicBoolean cancellationRequested;

    private CancellationToken() {
        this.cancellationRequested = new AtomicBoolean();
    }

    /**
     * Creates a token that is initially not cancelled.
     *
     * @return a new cancellation token
     */
    public static CancellationToken create() {
        return new CancellationToken();
    }

    static CancellationToken none() {
        return new CancellationToken();
    }

    /**
     * Requests cancellation. Repeated calls are harmless.
     */
    public void cancel() {
        cancellationRequested.set(true);
    }

    /**
     * Reports whether cancellation has been requested.
     *
     * @return {@code true} after {@link #cancel()} is called
     */
    public boolean isCancellationRequested() {
        return cancellationRequested.get();
    }
}
