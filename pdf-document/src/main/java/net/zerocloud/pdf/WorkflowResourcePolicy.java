package net.zerocloud.pdf;

import java.time.Duration;

/**
 * Immutable backend-neutral resource limits for one Document Workflow.
 *
 * <p>Every version-1 limit is mandatory, finite, and nonnegative. Zero permits
 * no consumption in that dimension. Exact consumption of a numeric boundary
 * succeeds and the first excess fails with a stable
 * {@link DocumentFailureCode}. Concurrency is an admission ceiling. The
 * elapsed limit is measured with the {@link java.time.Clock} owned by the
 * {@link WorkflowEnvironment}; it composes with an absolute request deadline
 * by stopping at whichever limit is observed first.</p>
 *
 * <p>These cooperative limits cover project-owned work in the trusted
 * in-process profile. They do not provide JVM-wide isolation or hard
 * termination; hostile multi-tenant input still requires the Hardened Worker
 * Profile.</p>
 *
 * @since 0.1.0
 */
public final class WorkflowResourcePolicy {

    /** The currently supported policy representation version. */
    public static final int VERSION_1 = 1;

    /** Hard stack-safe version-1 ceiling for caller-selected nesting. */
    public static final int MAXIMUM_NESTING_DEPTH_VERSION_1 = 16384;

    /** Default aggregate accepted Source bytes: 1 GiB. */
    public static final long DEFAULT_MAXIMUM_INPUT_BYTES = 1L << 30;

    /** Default page count: 5,000. */
    public static final int DEFAULT_MAXIMUM_PAGES = 5000;

    /** Default indirect PDF object count: 2,000,000. */
    public static final long DEFAULT_MAXIMUM_OBJECTS = 2_000_000L;

    /** Default container and supported graph nesting depth: 16,384. */
    public static final int DEFAULT_MAXIMUM_NESTING_DEPTH = 16384;

    /** Default aggregate supported filter-stage output: 4 GiB. */
    public static final long DEFAULT_MAXIMUM_DECOMPRESSED_BYTES = 4L << 30;

    /** Default aggregate decoded pixels: 1,000,000,000. */
    public static final long DEFAULT_MAXIMUM_DECODED_PIXELS = 1_000_000_000L;

    /** Default accounted owned in-process memory: 256 MiB. */
    public static final long DEFAULT_MAXIMUM_OWNED_MEMORY_BYTES = 256L << 20;

    /** Default aggregate transaction temporary storage: 4 GiB. */
    public static final long DEFAULT_MAXIMUM_TEMPORARY_STORAGE_BYTES = 4L << 30;

    /** Default elapsed processing time: five minutes. */
    public static final Duration DEFAULT_MAXIMUM_ELAPSED_TIME =
            Duration.ofMinutes(5L);

    /** Default simultaneous workflows sharing one environment: four. */
    public static final int DEFAULT_MAXIMUM_CONCURRENT_WORKFLOWS = 4;

    private static final Duration LARGEST_SUPPORTED_DURATION =
            Duration.ofNanos(Long.MAX_VALUE);

    private static final WorkflowResourcePolicy SAFE_DEFAULTS = builder()
            .maximumInputBytes(DEFAULT_MAXIMUM_INPUT_BYTES)
            .maximumPages(DEFAULT_MAXIMUM_PAGES)
            .maximumObjects(DEFAULT_MAXIMUM_OBJECTS)
            .maximumNestingDepth(DEFAULT_MAXIMUM_NESTING_DEPTH)
            .maximumDecompressedBytes(DEFAULT_MAXIMUM_DECOMPRESSED_BYTES)
            .maximumDecodedPixels(DEFAULT_MAXIMUM_DECODED_PIXELS)
            .maximumOwnedMemoryBytes(DEFAULT_MAXIMUM_OWNED_MEMORY_BYTES)
            .maximumTemporaryStorageBytes(
                    DEFAULT_MAXIMUM_TEMPORARY_STORAGE_BYTES)
            .maximumElapsedTime(DEFAULT_MAXIMUM_ELAPSED_TIME)
            .maximumConcurrentWorkflows(
                    DEFAULT_MAXIMUM_CONCURRENT_WORKFLOWS)
            .build();

    private final long maximumInputBytes;
    private final int maximumPages;
    private final long maximumObjects;
    private final int maximumNestingDepth;
    private final long maximumDecompressedBytes;
    private final long maximumDecodedPixels;
    private final long maximumOwnedMemoryBytes;
    private final long maximumTemporaryStorageBytes;
    private final Duration maximumElapsedTime;
    private final int maximumConcurrentWorkflows;

    private WorkflowResourcePolicy(Builder builder) {
        maximumInputBytes = builder.maximumInputBytes;
        maximumPages = builder.maximumPages;
        maximumObjects = builder.maximumObjects;
        maximumNestingDepth = builder.maximumNestingDepth;
        maximumDecompressedBytes = builder.maximumDecompressedBytes;
        maximumDecodedPixels = builder.maximumDecodedPixels;
        maximumOwnedMemoryBytes = builder.maximumOwnedMemoryBytes;
        maximumTemporaryStorageBytes =
                builder.maximumTemporaryStorageBytes;
        maximumElapsedTime = builder.maximumElapsedTime;
        maximumConcurrentWorkflows = builder.maximumConcurrentWorkflows;
    }

    /** Returns the documented finite policy used by system defaults. */
    public static WorkflowResourcePolicy safeDefaults() {
        return SAFE_DEFAULTS;
    }

    /** Begins a complete version-1 policy declaration. */
    public static Builder builder() {
        return new Builder();
    }

    /** @return {@link #VERSION_1} */
    public int getVersion() {
        return VERSION_1;
    }

    /** @return aggregate accepted bytes across all named Sources */
    public long getMaximumInputBytes() {
        return maximumInputBytes;
    }

    /** @return maximum pages observable by caller work */
    public int getMaximumPages() {
        return maximumPages;
    }

    /** @return maximum indirect PDF objects */
    public long getMaximumObjects() {
        return maximumObjects;
    }

    /** @return maximum container and supported graph nesting depth */
    public int getMaximumNestingDepth() {
        return maximumNestingDepth;
    }

    /** @return aggregate bytes produced by supported decoding stages */
    public long getMaximumDecompressedBytes() {
        return maximumDecompressedBytes;
    }

    /** @return aggregate pixels accepted by decoded-image preparation */
    public long getMaximumDecodedPixels() {
        return maximumDecodedPixels;
    }

    /** @return maximum accounted project-owned in-process bytes */
    public long getMaximumOwnedMemoryBytes() {
        return maximumOwnedMemoryBytes;
    }

    /** @return aggregate transaction temporary-storage bytes */
    public long getMaximumTemporaryStorageBytes() {
        return maximumTemporaryStorageBytes;
    }

    /** @return maximum elapsed processing time, inclusive */
    public Duration getMaximumElapsedTime() {
        return maximumElapsedTime;
    }

    /** @return shared simultaneous-workflow ceiling */
    public int getMaximumConcurrentWorkflows() {
        return maximumConcurrentWorkflows;
    }

    /** Builds one complete immutable version-1 policy. */
    public static final class Builder {

        private long maximumInputBytes = -1L;
        private int maximumPages = -1;
        private long maximumObjects = -1L;
        private int maximumNestingDepth = -1;
        private long maximumDecompressedBytes = -1L;
        private long maximumDecodedPixels = -1L;
        private long maximumOwnedMemoryBytes = -1L;
        private long maximumTemporaryStorageBytes = -1L;
        private Duration maximumElapsedTime;
        private int maximumConcurrentWorkflows = -1;

        private Builder() {
        }

        /** Sets the aggregate accepted Source-byte bound. */
        public Builder maximumInputBytes(long value) {
            maximumInputBytes = nonNegative(value, "maximumInputBytes");
            return this;
        }

        /** Sets the page-count bound. */
        public Builder maximumPages(int value) {
            maximumPages = nonNegative(value, "maximumPages");
            return this;
        }

        /** Sets the indirect-object-count bound. */
        public Builder maximumObjects(long value) {
            maximumObjects = nonNegative(value, "maximumObjects");
            return this;
        }

        /** Sets the stack-safe graph and container nesting bound. */
        public Builder maximumNestingDepth(int value) {
            int checked = nonNegative(
                    value,
                    "maximumNestingDepth");
            if (checked > MAXIMUM_NESTING_DEPTH_VERSION_1) {
                throw new IllegalArgumentException(
                        "maximumNestingDepth must not exceed "
                                + MAXIMUM_NESTING_DEPTH_VERSION_1
                                + " in version 1");
            }
            maximumNestingDepth = checked;
            return this;
        }

        /** Sets the aggregate supported filter-output bound. */
        public Builder maximumDecompressedBytes(long value) {
            maximumDecompressedBytes = nonNegative(
                    value,
                    "maximumDecompressedBytes");
            return this;
        }

        /** Sets the aggregate decoded-pixel bound. */
        public Builder maximumDecodedPixels(long value) {
            maximumDecodedPixels = nonNegative(
                    value,
                    "maximumDecodedPixels");
            return this;
        }

        /** Sets the accounted owned-memory bound. */
        public Builder maximumOwnedMemoryBytes(long value) {
            maximumOwnedMemoryBytes = nonNegative(
                    value,
                    "maximumOwnedMemoryBytes");
            return this;
        }

        /** Sets the aggregate temporary-storage bound. */
        public Builder maximumTemporaryStorageBytes(long value) {
            maximumTemporaryStorageBytes = nonNegative(
                    value,
                    "maximumTemporaryStorageBytes");
            return this;
        }

        /**
         * Sets the elapsed-time bound. Values whose nanosecond accounting
         * would overflow a signed {@code long} are rejected.
         */
        public Builder maximumElapsedTime(Duration value) {
            if (value == null) {
                throw new NullPointerException("maximumElapsedTime");
            }
            if (value.isNegative()) {
                throw new IllegalArgumentException(
                        "maximumElapsedTime must not be negative");
            }
            if (value.compareTo(LARGEST_SUPPORTED_DURATION) > 0) {
                throw new IllegalArgumentException(
                        "maximumElapsedTime is too large for exact accounting");
            }
            maximumElapsedTime = value;
            return this;
        }

        /** Sets the shared simultaneous-workflow ceiling. */
        public Builder maximumConcurrentWorkflows(int value) {
            maximumConcurrentWorkflows = nonNegative(
                    value,
                    "maximumConcurrentWorkflows");
            return this;
        }

        /**
         * Builds the policy after every version-1 limit is declared.
         *
         * @return the immutable policy
         */
        public WorkflowResourcePolicy build() {
            if (maximumInputBytes < 0L
                    || maximumPages < 0
                    || maximumObjects < 0L
                    || maximumNestingDepth < 0
                    || maximumDecompressedBytes < 0L
                    || maximumDecodedPixels < 0L
                    || maximumOwnedMemoryBytes < 0L
                    || maximumTemporaryStorageBytes < 0L
                    || maximumElapsedTime == null
                    || maximumConcurrentWorkflows < 0) {
                throw new IllegalStateException(
                        "Every version-1 workflow resource limit must be declared.");
            }
            return new WorkflowResourcePolicy(this);
        }

        private static int nonNegative(int value, String name) {
            if (value < 0) {
                throw new IllegalArgumentException(
                        name + " must not be negative");
            }
            return value;
        }

        private static long nonNegative(long value, String name) {
            if (value < 0L) {
                throw new IllegalArgumentException(
                        name + " must not be negative");
            }
            return value;
        }
    }
}
