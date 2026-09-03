package net.zerocloud.pdf.composition;

/**
 * Caller-declared bounds for one version-2 Canvas drawing command.
 *
 * <p>Every version-1 field is mandatory and nonnegative. An exact boundary
 * succeeds; the first excess is rejected before publication.</p>
 *
 * @since 0.1.0
 */
public final class CanvasResourceLimits {

    /** The currently supported limits representation version. */
    public static final int VERSION_1 = 1;

    /** The hard stack-safe transparency-group depth ceiling. */
    public static final int MAXIMUM_TRANSPARENCY_GROUP_DEPTH_VERSION_1 = 16;

    private final long maximumEncodedImageBytes;
    private final long maximumDecodedImagePixels;
    private final long maximumDecodedImageBytes;
    private final long maximumIccProfileBytes;
    private final long maximumMaskBytes;
    private final long maximumGeneratedContentBytes;
    private final int maximumResourceDeclarations;
    private final int maximumTransparencyGroupDepth;

    private CanvasResourceLimits(Builder builder) {
        this.maximumEncodedImageBytes = builder.maximumEncodedImageBytes;
        this.maximumDecodedImagePixels = builder.maximumDecodedImagePixels;
        this.maximumDecodedImageBytes = builder.maximumDecodedImageBytes;
        this.maximumIccProfileBytes = builder.maximumIccProfileBytes;
        this.maximumMaskBytes = builder.maximumMaskBytes;
        this.maximumGeneratedContentBytes = builder.maximumGeneratedContentBytes;
        this.maximumResourceDeclarations = builder.maximumResourceDeclarations;
        this.maximumTransparencyGroupDepth = builder.maximumTransparencyGroupDepth;
    }

    /** Begins a complete version-1 limits declaration. @return a new builder */
    public static Builder builder() {
        return new Builder();
    }

    /** @return {@link #VERSION_1} */
    public int getVersion() { return VERSION_1; }

    /** @return aggregate encoded input-byte bound */
    public long getMaximumEncodedImageBytes() { return maximumEncodedImageBytes; }

    /** @return aggregate decoded pixel bound */
    public long getMaximumDecodedImagePixels() { return maximumDecodedImagePixels; }

    /** @return aggregate decoded sample-byte bound */
    public long getMaximumDecodedImageBytes() { return maximumDecodedImageBytes; }

    /** @return aggregate ICC profile-byte bound */
    public long getMaximumIccProfileBytes() { return maximumIccProfileBytes; }

    /** @return aggregate explicit, soft, and decoded-alpha mask-byte bound */
    public long getMaximumMaskBytes() { return maximumMaskBytes; }

    /** @return aggregate generated page and group content-byte bound */
    public long getMaximumGeneratedContentBytes() {
        return maximumGeneratedContentBytes;
    }

    /** @return distinct resource-declaration bound */
    public int getMaximumResourceDeclarations() {
        return maximumResourceDeclarations;
    }

    /** @return nested transparency-group depth bound */
    public int getMaximumTransparencyGroupDepth() {
        return maximumTransparencyGroupDepth;
    }

    /** Builds one complete immutable limits declaration. */
    public static final class Builder {

        private long maximumEncodedImageBytes = -1L;
        private long maximumDecodedImagePixels = -1L;
        private long maximumDecodedImageBytes = -1L;
        private long maximumIccProfileBytes = -1L;
        private long maximumMaskBytes = -1L;
        private long maximumGeneratedContentBytes = -1L;
        private int maximumResourceDeclarations = -1;
        private int maximumTransparencyGroupDepth = -1;

        private Builder() {
        }

        /** Declares the aggregate encoded image input bound. @return this builder */
        public Builder maximumEncodedImageBytes(long value) {
            maximumEncodedImageBytes = nonNegative(value, "maximumEncodedImageBytes");
            return this;
        }

        /** Declares the aggregate decoded pixel bound. @return this builder */
        public Builder maximumDecodedImagePixels(long value) {
            maximumDecodedImagePixels = nonNegative(value, "maximumDecodedImagePixels");
            return this;
        }

        /** Declares the aggregate decoded sample-byte bound. @return this builder */
        public Builder maximumDecodedImageBytes(long value) {
            maximumDecodedImageBytes = nonNegative(value, "maximumDecodedImageBytes");
            return this;
        }

        /** Declares the aggregate ICC profile-data bound. @return this builder */
        public Builder maximumIccProfileBytes(long value) {
            maximumIccProfileBytes = nonNegative(value, "maximumIccProfileBytes");
            return this;
        }

        /** Declares the aggregate mask-data bound. @return this builder */
        public Builder maximumMaskBytes(long value) {
            maximumMaskBytes = nonNegative(value, "maximumMaskBytes");
            return this;
        }

        /** Declares the aggregate generated content bound. @return this builder */
        public Builder maximumGeneratedContentBytes(long value) {
            maximumGeneratedContentBytes = nonNegative(
                    value,
                    "maximumGeneratedContentBytes");
            return this;
        }

        /** Declares the distinct resource-declaration bound. @return this builder */
        public Builder maximumResourceDeclarations(int value) {
            maximumResourceDeclarations = nonNegative(
                    value,
                    "maximumResourceDeclarations");
            return this;
        }

        /** Declares the nested transparency-group depth bound. @return this builder */
        public Builder maximumTransparencyGroupDepth(int value) {
            int checked = nonNegative(value, "maximumTransparencyGroupDepth");
            if (checked > MAXIMUM_TRANSPARENCY_GROUP_DEPTH_VERSION_1) {
                throw new IllegalArgumentException(
                        "maximumTransparencyGroupDepth must not exceed "
                                + MAXIMUM_TRANSPARENCY_GROUP_DEPTH_VERSION_1
                                + " in version 1");
            }
            maximumTransparencyGroupDepth = checked;
            return this;
        }

        /** Builds the limits after every version-1 field is declared. */
        public CanvasResourceLimits build() {
            if (maximumEncodedImageBytes < 0L
                    || maximumDecodedImagePixels < 0L
                    || maximumDecodedImageBytes < 0L
                    || maximumIccProfileBytes < 0L
                    || maximumMaskBytes < 0L
                    || maximumGeneratedContentBytes < 0L
                    || maximumResourceDeclarations < 0
                    || maximumTransparencyGroupDepth < 0) {
                throw new IllegalStateException(
                        "Every version-1 Canvas resource limit must be declared.");
            }
            return new CanvasResourceLimits(this);
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
