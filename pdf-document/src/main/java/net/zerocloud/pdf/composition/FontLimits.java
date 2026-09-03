package net.zerocloud.pdf.composition;

/**
 * Complete caller-declared bounds for one version-1 font operation.
 *
 * @since 0.1.0
 */
public final class FontLimits {

    /** The currently supported limits representation version. */
    public static final int VERSION_1 = 1;

    private final int maximumFontSources;
    private final long maximumSourceBytes;
    private final int maximumCodePoints;
    private final long maximumFallbackChecks;
    private final long maximumGeneratedContentBytes;

    private FontLimits(Builder builder) {
        maximumFontSources = builder.maximumFontSources;
        maximumSourceBytes = builder.maximumSourceBytes;
        maximumCodePoints = builder.maximumCodePoints;
        maximumFallbackChecks = builder.maximumFallbackChecks;
        maximumGeneratedContentBytes = builder.maximumGeneratedContentBytes;
    }

    /** Begins a complete version-1 declaration. @return builder */
    public static Builder builder() {
        return new Builder();
    }

    /** @return {@link #VERSION_1} */
    public int getVersion() { return VERSION_1; }
    /** @return maximum ordered source declarations */
    public int getMaximumFontSources() { return maximumFontSources; }
    /** @return maximum aggregate staged source bytes */
    public long getMaximumSourceBytes() { return maximumSourceBytes; }
    /** @return maximum input Unicode scalar count */
    public int getMaximumCodePoints() { return maximumCodePoints; }
    /** @return maximum ordered fallback candidate visits */
    public long getMaximumFallbackChecks() { return maximumFallbackChecks; }
    /** @return maximum exact generated operator bytes */
    public long getMaximumGeneratedContentBytes() {
        return maximumGeneratedContentBytes;
    }

    /** Builds complete immutable font limits. */
    public static final class Builder {

        private int maximumFontSources = -1;
        private long maximumSourceBytes = -1L;
        private int maximumCodePoints = -1;
        private long maximumFallbackChecks = -1L;
        private long maximumGeneratedContentBytes = -1L;

        private Builder() {
        }

        /** Sets the source declaration bound. @return this builder */
        public Builder maximumFontSources(int value) {
            maximumFontSources = nonNegative(value, "maximumFontSources");
            return this;
        }

        /** Sets the aggregate staged-source byte bound. @return this builder */
        public Builder maximumSourceBytes(long value) {
            maximumSourceBytes = nonNegative(value, "maximumSourceBytes");
            return this;
        }

        /** Sets the Unicode scalar bound. @return this builder */
        public Builder maximumCodePoints(int value) {
            maximumCodePoints = nonNegative(value, "maximumCodePoints");
            return this;
        }

        /** Sets the fallback candidate-visit bound. @return this builder */
        public Builder maximumFallbackChecks(long value) {
            maximumFallbackChecks = nonNegative(value, "maximumFallbackChecks");
            return this;
        }

        /** Sets the generated operator byte bound. @return this builder */
        public Builder maximumGeneratedContentBytes(long value) {
            maximumGeneratedContentBytes = nonNegative(
                    value,
                    "maximumGeneratedContentBytes");
            return this;
        }

        /** Builds after all version-1 fields have been declared. */
        public FontLimits build() {
            if (maximumFontSources < 0
                    || maximumSourceBytes < 0L
                    || maximumCodePoints < 0
                    || maximumFallbackChecks < 0L
                    || maximumGeneratedContentBytes < 0L) {
                throw new IllegalStateException(
                        "Every version-1 Font limit must be declared.");
            }
            return new FontLimits(this);
        }

        private static int nonNegative(int value, String name) {
            if (value < 0) {
                throw new IllegalArgumentException(name + " must not be negative");
            }
            return value;
        }

        private static long nonNegative(long value, String name) {
            if (value < 0L) {
                throw new IllegalArgumentException(name + " must not be negative");
            }
            return value;
        }
    }
}
