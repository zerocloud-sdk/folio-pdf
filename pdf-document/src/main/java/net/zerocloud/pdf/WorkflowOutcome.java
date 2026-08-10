package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The detached caller result and publication receipts of a completed workflow.
 *
 * @param <R> the caller result type
 * @since 0.1.0
 */
public final class WorkflowOutcome<R> {

    private final R result;
    private final List<PublicationReceipt> publicationReceipts;

    WorkflowOutcome(R result, List<PublicationReceipt> publicationReceipts) {
        this.result = result;
        this.publicationReceipts = Collections.unmodifiableList(
                new ArrayList<PublicationReceipt>(publicationReceipts));
    }

    /**
     * Returns the value produced by the caller callback.
     *
     * @return the caller result, which may be {@code null}
     */
    public R getResult() {
        return result;
    }

    /**
     * Returns immutable publication receipts, or an empty list for read-only work.
     *
     * @return the publication receipts
     */
    public List<PublicationReceipt> getPublicationReceipts() {
        return publicationReceipts;
    }
}
