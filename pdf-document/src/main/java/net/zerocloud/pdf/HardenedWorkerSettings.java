package net.zerocloud.pdf;

/**
 * Immutable limits for the local Hardened Worker transport and JVM boundary.
 *
 * <p>The message limit applies to each authenticated protocol payload and is
 * checked before allocation. The heap limit is a hard child-JVM maximum; the
 * transaction's {@link WorkflowResourcePolicy} remains the more detailed
 * operation-level resource contract.</p>
 *
 * @since 0.1.0
 */
public final class HardenedWorkerSettings {

    /** The currently supported settings representation version. */
    public static final int VERSION_1 = 1;

    /** Default maximum authenticated message payload: 64 MiB. */
    public static final int DEFAULT_MAXIMUM_MESSAGE_BYTES = 64 << 20;

    /** Default maximum Worker JVM heap: 512 MiB. */
    public static final long DEFAULT_MAXIMUM_HEAP_BYTES = 512L << 20;

    private static final HardenedWorkerSettings SAFE_DEFAULTS = builder()
            .maximumMessageBytes(DEFAULT_MAXIMUM_MESSAGE_BYTES)
            .maximumHeapBytes(DEFAULT_MAXIMUM_HEAP_BYTES)
            .build();

    private final int maximumMessageBytes;
    private final long maximumHeapBytes;

    private HardenedWorkerSettings(Builder builder) {
        maximumMessageBytes = builder.maximumMessageBytes;
        maximumHeapBytes = builder.maximumHeapBytes;
    }

    /** Returns the finite system-default Worker settings. */
    public static HardenedWorkerSettings safeDefaults() {
        return SAFE_DEFAULTS;
    }

    /** Begins a complete version-1 settings declaration. */
    public static Builder builder() {
        return new Builder();
    }

    /** @return {@link #VERSION_1} */
    public int getVersion() {
        return VERSION_1;
    }

    /** @return maximum payload bytes in one authenticated frame */
    public int getMaximumMessageBytes() {
        return maximumMessageBytes;
    }

    /** @return hard maximum heap bytes supplied to the Worker JVM */
    public long getMaximumHeapBytes() {
        return maximumHeapBytes;
    }

    /** Builds one complete immutable settings value. */
    public static final class Builder {

        private int maximumMessageBytes = -1;
        private long maximumHeapBytes = -1L;

        private Builder() {
        }

        /** Sets the inclusive per-frame payload bound. */
        public Builder maximumMessageBytes(int value) {
            if (value < 0) {
                throw new IllegalArgumentException(
                        "maximumMessageBytes must not be negative");
            }
            maximumMessageBytes = value;
            return this;
        }

        /** Sets the hard child-JVM heap ceiling. */
        public Builder maximumHeapBytes(long value) {
            if (value < (32L << 20)) {
                throw new IllegalArgumentException(
                        "maximumHeapBytes must be at least 32 MiB");
            }
            maximumHeapBytes = value;
            return this;
        }

        /** Builds the settings after every version-1 value is supplied. */
        public HardenedWorkerSettings build() {
            if (maximumMessageBytes < 0 || maximumHeapBytes < 0L) {
                throw new IllegalStateException(
                        "Every version-1 Hardened Worker setting must be declared.");
            }
            return new HardenedWorkerSettings(this);
        }
    }
}
