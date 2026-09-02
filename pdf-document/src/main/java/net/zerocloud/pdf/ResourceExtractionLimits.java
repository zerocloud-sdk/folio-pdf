package net.zerocloud.pdf;

/**
 * Caller-declared bounds for one image and Document Resource Inventory
 * extraction.
 *
 * <p>Every version-1 bound is mandatory. A zero permits an empty matching
 * dimension and rejects the first matching value. Exact accounting semantics
 * are documented in {@code docs/image-resource-extraction.md}.</p>
 *
 * @since 0.1.0
 */
public final class ResourceExtractionLimits {

    /** The currently supported limits representation version. */
    public static final int VERSION_1 = 1;

    /** The stack-safe resource graph depth ceiling for version 1. */
    public static final int MAXIMUM_RESOURCE_TRAVERSAL_DEPTH_VERSION_1 = 64;

    private final int maximumPages;
    private final int maximumPageTreeNodes;
    private final long maximumTraversedResourceValues;
    private final int maximumResourceTraversalDepth;
    private final long maximumDecodedPixels;
    private final long maximumDecompressedBytes;
    private final long maximumReturnedBytes;

    private ResourceExtractionLimits(Builder builder) {
        this.maximumPages = builder.maximumPages;
        this.maximumPageTreeNodes = builder.maximumPageTreeNodes;
        this.maximumTraversedResourceValues =
                builder.maximumTraversedResourceValues;
        this.maximumResourceTraversalDepth =
                builder.maximumResourceTraversalDepth;
        this.maximumDecodedPixels = builder.maximumDecodedPixels;
        this.maximumDecompressedBytes = builder.maximumDecompressedBytes;
        this.maximumReturnedBytes = builder.maximumReturnedBytes;
    }

    /** Begins a complete version-1 limits declaration. @return a new builder */
    public static Builder builder() {
        return new Builder();
    }

    /** Returns the limits version. @return {@link #VERSION_1} */
    public int getVersion() {
        return VERSION_1;
    }

    /** Returns the page-count bound. @return the bound */
    public int getMaximumPages() {
        return maximumPages;
    }

    /** Returns the page-tree node-occurrence bound. @return the bound */
    public int getMaximumPageTreeNodes() {
        return maximumPageTreeNodes;
    }

    /** Returns the traversed resource-value bound. @return the bound */
    public long getMaximumTraversedResourceValues() {
        return maximumTraversedResourceValues;
    }

    /** Returns the resource-graph depth bound. @return the bound */
    public int getMaximumResourceTraversalDepth() {
        return maximumResourceTraversalDepth;
    }

    /** Returns the decoded-pixel bound. @return the bound */
    public long getMaximumDecodedPixels() {
        return maximumDecodedPixels;
    }

    /** Returns the aggregate filter-output byte bound. @return the bound */
    public long getMaximumDecompressedBytes() {
        return maximumDecompressedBytes;
    }

    /** Returns the aggregate detached returned-byte bound. @return the bound */
    public long getMaximumReturnedBytes() {
        return maximumReturnedBytes;
    }

    /** Builds one complete immutable limits declaration. @since 0.1.0 */
    public static final class Builder {

        private int maximumPages = -1;
        private int maximumPageTreeNodes = -1;
        private long maximumTraversedResourceValues = -1L;
        private int maximumResourceTraversalDepth = -1;
        private long maximumDecodedPixels = -1L;
        private long maximumDecompressedBytes = -1L;
        private long maximumReturnedBytes = -1L;

        private Builder() {
        }

        /** Declares the page-count bound. @param value bound @return this builder */
        public Builder maximumPages(int value) {
            maximumPages = nonNegative(value, "maximumPages");
            return this;
        }

        /** Declares the page-tree node bound. @param value bound @return this builder */
        public Builder maximumPageTreeNodes(int value) {
            maximumPageTreeNodes = nonNegative(value, "maximumPageTreeNodes");
            return this;
        }

        /** Declares the resource-value bound. @param value bound @return this builder */
        public Builder maximumTraversedResourceValues(long value) {
            maximumTraversedResourceValues = nonNegative(
                    value,
                    "maximumTraversedResourceValues");
            return this;
        }

        /** Declares the graph-depth bound. @param value bound @return this builder */
        public Builder maximumResourceTraversalDepth(int value) {
            int checked = nonNegative(value, "maximumResourceTraversalDepth");
            if (checked > MAXIMUM_RESOURCE_TRAVERSAL_DEPTH_VERSION_1) {
                throw new IllegalArgumentException(
                        "maximumResourceTraversalDepth must not exceed "
                                + MAXIMUM_RESOURCE_TRAVERSAL_DEPTH_VERSION_1
                                + " in version 1");
            }
            maximumResourceTraversalDepth = checked;
            return this;
        }

        /** Declares the pixel bound. @param value bound @return this builder */
        public Builder maximumDecodedPixels(long value) {
            maximumDecodedPixels = nonNegative(value, "maximumDecodedPixels");
            return this;
        }

        /** Declares the filter-output bound. @param value bound @return this builder */
        public Builder maximumDecompressedBytes(long value) {
            maximumDecompressedBytes = nonNegative(
                    value,
                    "maximumDecompressedBytes");
            return this;
        }

        /** Declares the returned-byte bound. @param value bound @return this builder */
        public Builder maximumReturnedBytes(long value) {
            maximumReturnedBytes = nonNegative(value, "maximumReturnedBytes");
            return this;
        }

        /** Builds the limits after every version-1 field is declared. @return limits */
        public ResourceExtractionLimits build() {
            if (maximumPages < 0
                    || maximumPageTreeNodes < 0
                    || maximumTraversedResourceValues < 0L
                    || maximumResourceTraversalDepth < 0
                    || maximumDecodedPixels < 0L
                    || maximumDecompressedBytes < 0L
                    || maximumReturnedBytes < 0L) {
                throw new IllegalStateException(
                        "Every version-1 resource extraction limit must be declared.");
            }
            return new ResourceExtractionLimits(this);
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
