package net.zerocloud.pdf;

/**
 * Finite retention policy for identified workflow transaction states.
 *
 * <p>Record count and modeled retained metadata per record are both bounded.
 * Records are never evicted from a live {@link WorkflowEnvironment}. Once
 * either configured capacity is unavailable, a new identity is rejected so
 * an earlier committed identity cannot become replayable through eviction.
 * Modeled bytes cover the retained request shape, identity references,
 * status, and receipts; they are not a JVM-heap or process-RSS measurement.</p>
 *
 * @since 0.1.0
 */
public final class WorkflowTransactionPolicy {

    /** Default identities retained by one Workflow Environment. */
    public static final int DEFAULT_MAXIMUM_RETAINED_TRANSACTIONS = 4096;

    /** Default modeled metadata retained for one transaction: 64 KiB. */
    public static final long DEFAULT_MAXIMUM_RETAINED_BYTES_PER_TRANSACTION =
            64L << 10;

    private static final WorkflowTransactionPolicy SAFE_DEFAULTS = builder()
            .maximumRetainedTransactions(
                    DEFAULT_MAXIMUM_RETAINED_TRANSACTIONS)
            .maximumRetainedBytesPerTransaction(
                    DEFAULT_MAXIMUM_RETAINED_BYTES_PER_TRANSACTION)
            .build();

    private final int maximumRetainedTransactions;
    private final long maximumRetainedBytesPerTransaction;

    private WorkflowTransactionPolicy(Builder builder) {
        maximumRetainedTransactions = builder.maximumRetainedTransactions;
        maximumRetainedBytesPerTransaction =
                builder.maximumRetainedBytesPerTransaction;
    }

    /** @return the finite default transaction-status retention policy */
    public static WorkflowTransactionPolicy safeDefaults() {
        return SAFE_DEFAULTS;
    }

    /** @return a new complete policy builder */
    public static Builder builder() {
        return new Builder();
    }

    /** @return maximum identities retained for the environment's lifetime */
    public int getMaximumRetainedTransactions() {
        return maximumRetainedTransactions;
    }

    /**
     * Returns the modeled retained-metadata bound for one identity.
     *
     * @return modeled request-shape, identity, status, and receipt bytes
     */
    public long getMaximumRetainedBytesPerTransaction() {
        return maximumRetainedBytesPerTransaction;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WorkflowTransactionPolicy
                && maximumRetainedTransactions
                        == ((WorkflowTransactionPolicy) other)
                                .maximumRetainedTransactions
                && maximumRetainedBytesPerTransaction
                        == ((WorkflowTransactionPolicy) other)
                                .maximumRetainedBytesPerTransaction;
    }

    @Override
    public int hashCode() {
        return 31 * maximumRetainedTransactions
                + (int) (maximumRetainedBytesPerTransaction
                        ^ (maximumRetainedBytesPerTransaction >>> 32));
    }

    /** Builds a finite transaction retention declaration. */
    public static final class Builder {

        private int maximumRetainedTransactions = -1;
        private long maximumRetainedBytesPerTransaction =
                DEFAULT_MAXIMUM_RETAINED_BYTES_PER_TRANSACTION;

        private Builder() {
        }

        /** Sets the maximum retained identities; zero disables admission. */
        public Builder maximumRetainedTransactions(int value) {
            if (value < 0) {
                throw new IllegalArgumentException(
                        "maximumRetainedTransactions must not be negative");
            }
            maximumRetainedTransactions = value;
            return this;
        }

        /**
         * Sets the modeled retained-metadata bound for each identity. Zero
         * rejects every new identified request.
         *
         * @param value the nonnegative per-identity modeled-byte bound
         * @return this builder
         */
        public Builder maximumRetainedBytesPerTransaction(long value) {
            if (value < 0L) {
                throw new IllegalArgumentException(
                        "maximumRetainedBytesPerTransaction must not be negative");
            }
            maximumRetainedBytesPerTransaction = value;
            return this;
        }

        /** @return the complete immutable policy */
        public WorkflowTransactionPolicy build() {
            if (maximumRetainedTransactions < 0) {
                throw new IllegalStateException(
                        "The transaction retention limit must be declared.");
            }
            return new WorkflowTransactionPolicy(this);
        }
    }
}
