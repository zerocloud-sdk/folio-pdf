package net.zerocloud.pdf.provider;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable input, output, and maximum-duration policy for one Provider.
 * The common contract enforces byte bounds and validates the requested
 * timeout; the execution-mode adapter enforces elapsed execution time.
 */
public final class ProviderLimits {

    private final long maximumInputBytes;
    private final long maximumOutputBytes;
    private final Duration maximumDuration;

    private ProviderLimits(
            long maximumInputBytes,
            long maximumOutputBytes,
            Duration maximumDuration) {
        if (maximumInputBytes <= 0L) {
            throw new IllegalArgumentException("maximumInputBytes must be positive");
        }
        if (maximumOutputBytes <= 0L) {
            throw new IllegalArgumentException("maximumOutputBytes must be positive");
        }
        Duration requiredDuration = Objects.requireNonNull(
                maximumDuration,
                "maximumDuration");
        if (requiredDuration.isZero() || requiredDuration.isNegative()) {
            throw new IllegalArgumentException("maximumDuration must be positive");
        }
        this.maximumInputBytes = maximumInputBytes;
        this.maximumOutputBytes = maximumOutputBytes;
        this.maximumDuration = requiredDuration;
    }

    /**
     * Creates explicit bounded operating limits.
     *
     * @param maximumInputBytes maximum accepted request bytes
     * @param maximumOutputBytes maximum accepted result bytes
     * @param maximumDuration maximum execution duration
     * @return immutable limits
     */
    public static ProviderLimits bounded(
            long maximumInputBytes,
            long maximumOutputBytes,
            Duration maximumDuration) {
        return new ProviderLimits(
                maximumInputBytes,
                maximumOutputBytes,
                maximumDuration);
    }

    public long getMaximumInputBytes() {
        return maximumInputBytes;
    }

    public long getMaximumOutputBytes() {
        return maximumOutputBytes;
    }

    public Duration getMaximumDuration() {
        return maximumDuration;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProviderLimits)) {
            return false;
        }
        ProviderLimits that = (ProviderLimits) other;
        return maximumInputBytes == that.maximumInputBytes
                && maximumOutputBytes == that.maximumOutputBytes
                && maximumDuration.equals(that.maximumDuration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Long.valueOf(maximumInputBytes),
                Long.valueOf(maximumOutputBytes),
                maximumDuration);
    }
}
