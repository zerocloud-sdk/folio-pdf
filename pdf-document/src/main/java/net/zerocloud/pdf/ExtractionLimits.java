package net.zerocloud.pdf;

/**
 * Caller-declared bounds for one text and logical-structure extraction.
 *
 * <p>Every version-1 bound is mandatory. A zero bound permits an empty
 * corresponding result and rejects the first matching value.</p>
 *
 * <p>Page-tree nodes count the root plus every {@code Kids} entry. Streams
 * count page streams and executed Form occurrences; page-stream depth starts
 * at one and cannot exceed
 * {@link #MAXIMUM_CONTENT_STREAM_DEPTH_VERSION_1}. Decoded bytes also include
 * distinct {@code ToUnicode},
 * embedded font-program, and CID-to-GID streams reached by extraction.
 * Structure items count root and element {@code K} entries, and
 * structure depth starts at one for a root element. The Unicode budget covers
 * accepted mapping observations and extracted metadata without charging an
 * aggregate view of the same text again. The {@code ToUnicode} mapping budget
 * bounds every character entry that FontBox would materialize, including each
 * code represented by a range. The font-data-entry budget bounds every item
 * inspected in distinct simple-font {@code Differences} arrays, every raw
 * font-width array item inspected, and every CID width entry that PDFBox
 * would materialize from a compact range.</p>
 *
 * @since 0.1.0
 */
public final class ExtractionLimits {

    /** The currently supported limits representation version. */
    public static final int VERSION_1 = 1;

    /** The stack-safe nested content-stream depth ceiling for version 1. */
    public static final int MAXIMUM_CONTENT_STREAM_DEPTH_VERSION_1 = 32;

    private final int maximumPages;
    private final int maximumPageTreeNodes;
    private final int maximumContentStreams;
    private final int maximumContentStreamDepth;
    private final long maximumDecodedBytes;
    private final int maximumTextItems;
    private final int maximumUnicodeCodePoints;
    private final int maximumToUnicodeMappings;
    private final int maximumFontDataEntries;
    private final int maximumMarkedContentSequences;
    private final int maximumMarkedContentDepth;
    private final int maximumStructureElements;
    private final int maximumStructureItems;
    private final int maximumStructureDepth;
    private final int maximumRoleMappings;

    private ExtractionLimits(Builder builder) {
        this.maximumPages = builder.maximumPages;
        this.maximumPageTreeNodes = builder.maximumPageTreeNodes;
        this.maximumContentStreams = builder.maximumContentStreams;
        this.maximumContentStreamDepth = builder.maximumContentStreamDepth;
        this.maximumDecodedBytes = builder.maximumDecodedBytes;
        this.maximumTextItems = builder.maximumTextItems;
        this.maximumUnicodeCodePoints = builder.maximumUnicodeCodePoints;
        this.maximumToUnicodeMappings = builder.maximumToUnicodeMappings;
        this.maximumFontDataEntries = builder.maximumFontDataEntries;
        this.maximumMarkedContentSequences =
                builder.maximumMarkedContentSequences;
        this.maximumMarkedContentDepth = builder.maximumMarkedContentDepth;
        this.maximumStructureElements = builder.maximumStructureElements;
        this.maximumStructureItems = builder.maximumStructureItems;
        this.maximumStructureDepth = builder.maximumStructureDepth;
        this.maximumRoleMappings = builder.maximumRoleMappings;
    }

    /** Begins a version-1 limits declaration. @return a new builder */
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

    /** Returns the traversed content-stream bound. @return the bound */
    public int getMaximumContentStreams() {
        return maximumContentStreams;
    }

    /** Returns the nested content-stream depth bound. @return the bound */
    public int getMaximumContentStreamDepth() {
        return maximumContentStreamDepth;
    }

    /** Returns the aggregate decoded-data byte bound. @return the bound */
    public long getMaximumDecodedBytes() {
        return maximumDecodedBytes;
    }

    /** Returns the extracted text-item bound. @return the bound */
    public int getMaximumTextItems() {
        return maximumTextItems;
    }

    /** Returns the aggregate public Unicode code-point bound. @return the bound */
    public int getMaximumUnicodeCodePoints() {
        return maximumUnicodeCodePoints;
    }

    /** Returns the materialized {@code ToUnicode} mapping bound. @return bound */
    public int getMaximumToUnicodeMappings() {
        return maximumToUnicodeMappings;
    }

    /** Returns the traversed or materialized font-data-entry bound. @return the bound */
    public int getMaximumFontDataEntries() {
        return maximumFontDataEntries;
    }

    /** Returns the marked-content sequence bound. @return the bound */
    public int getMaximumMarkedContentSequences() {
        return maximumMarkedContentSequences;
    }

    /** Returns the marked-content nesting bound. @return the bound */
    public int getMaximumMarkedContentDepth() {
        return maximumMarkedContentDepth;
    }

    /** Returns the logical-structure element bound. @return the bound */
    public int getMaximumStructureElements() {
        return maximumStructureElements;
    }

    /** Returns the logical-structure child-item bound. @return the bound */
    public int getMaximumStructureItems() {
        return maximumStructureItems;
    }

    /** Returns the logical-structure depth bound. @return the bound */
    public int getMaximumStructureDepth() {
        return maximumStructureDepth;
    }

    /** Returns the role-map entry bound. @return the bound */
    public int getMaximumRoleMappings() {
        return maximumRoleMappings;
    }

    /** Builds one complete immutable limits declaration. @since 0.1.0 */
    public static final class Builder {

        private int maximumPages = -1;
        private int maximumPageTreeNodes = -1;
        private int maximumContentStreams = -1;
        private int maximumContentStreamDepth = -1;
        private long maximumDecodedBytes = -1L;
        private int maximumTextItems = -1;
        private int maximumUnicodeCodePoints = -1;
        private int maximumToUnicodeMappings = -1;
        private int maximumFontDataEntries = -1;
        private int maximumMarkedContentSequences = -1;
        private int maximumMarkedContentDepth = -1;
        private int maximumStructureElements = -1;
        private int maximumStructureItems = -1;
        private int maximumStructureDepth = -1;
        private int maximumRoleMappings = -1;

        private Builder() {
        }

        /** Declares the page-count bound. @param value bound @return this builder */
        public Builder maximumPages(int value) {
            maximumPages = nonNegative(value, "maximumPages");
            return this;
        }

        /** Declares the page-tree node-occurrence bound. @param value bound @return this builder */
        public Builder maximumPageTreeNodes(int value) {
            maximumPageTreeNodes = nonNegative(
                    value, "maximumPageTreeNodes");
            return this;
        }

        /** Declares the stream-count bound. @param value bound @return this builder */
        public Builder maximumContentStreams(int value) {
            maximumContentStreams = nonNegative(value, "maximumContentStreams");
            return this;
        }

        /** Declares the stream-depth bound. @param value bound @return this builder */
        public Builder maximumContentStreamDepth(int value) {
            int checked = nonNegative(value, "maximumContentStreamDepth");
            if (checked > MAXIMUM_CONTENT_STREAM_DEPTH_VERSION_1) {
                throw new IllegalArgumentException(
                        "maximumContentStreamDepth must not exceed "
                                + MAXIMUM_CONTENT_STREAM_DEPTH_VERSION_1
                                + " in version 1");
            }
            maximumContentStreamDepth = checked;
            return this;
        }

        /** Declares the decoded-byte bound. @param value bound @return this builder */
        public Builder maximumDecodedBytes(long value) {
            if (value < 0L) {
                throw new IllegalArgumentException(
                        "maximumDecodedBytes must not be negative");
            }
            maximumDecodedBytes = value;
            return this;
        }

        /** Declares the text-item bound. @param value bound @return this builder */
        public Builder maximumTextItems(int value) {
            maximumTextItems = nonNegative(value, "maximumTextItems");
            return this;
        }

        /** Declares the Unicode bound. @param value bound @return this builder */
        public Builder maximumUnicodeCodePoints(int value) {
            maximumUnicodeCodePoints = nonNegative(
                    value, "maximumUnicodeCodePoints");
            return this;
        }

        /** Declares the materialized {@code ToUnicode} mapping bound. @param value bound @return this builder */
        public Builder maximumToUnicodeMappings(int value) {
            maximumToUnicodeMappings = nonNegative(
                    value, "maximumToUnicodeMappings");
            return this;
        }

        /** Declares the traversed or materialized font-data-entry bound. @param value bound @return this builder */
        public Builder maximumFontDataEntries(int value) {
            maximumFontDataEntries = nonNegative(
                    value, "maximumFontDataEntries");
            return this;
        }

        /** Declares the marked-content count bound. @param value bound @return this builder */
        public Builder maximumMarkedContentSequences(int value) {
            maximumMarkedContentSequences = nonNegative(
                    value, "maximumMarkedContentSequences");
            return this;
        }

        /** Declares the marked-content depth bound. @param value bound @return this builder */
        public Builder maximumMarkedContentDepth(int value) {
            maximumMarkedContentDepth = nonNegative(
                    value, "maximumMarkedContentDepth");
            return this;
        }

        /** Declares the structure-element bound. @param value bound @return this builder */
        public Builder maximumStructureElements(int value) {
            maximumStructureElements = nonNegative(
                    value, "maximumStructureElements");
            return this;
        }

        /** Declares the structure-item bound. @param value bound @return this builder */
        public Builder maximumStructureItems(int value) {
            maximumStructureItems = nonNegative(value, "maximumStructureItems");
            return this;
        }

        /** Declares the structure-depth bound. @param value bound @return this builder */
        public Builder maximumStructureDepth(int value) {
            maximumStructureDepth = nonNegative(value, "maximumStructureDepth");
            return this;
        }

        /** Declares the role-map bound. @param value bound @return this builder */
        public Builder maximumRoleMappings(int value) {
            maximumRoleMappings = nonNegative(value, "maximumRoleMappings");
            return this;
        }

        /** Builds the limits after every field has been declared. @return limits */
        public ExtractionLimits build() {
            if (maximumPages < 0
                    || maximumPageTreeNodes < 0
                    || maximumContentStreams < 0
                    || maximumContentStreamDepth < 0
                    || maximumDecodedBytes < 0L
                    || maximumTextItems < 0
                    || maximumUnicodeCodePoints < 0
                    || maximumToUnicodeMappings < 0
                    || maximumFontDataEntries < 0
                    || maximumMarkedContentSequences < 0
                    || maximumMarkedContentDepth < 0
                    || maximumStructureElements < 0
                    || maximumStructureItems < 0
                    || maximumStructureDepth < 0
                    || maximumRoleMappings < 0) {
                throw new IllegalStateException(
                        "Every version-1 extraction limit must be declared.");
            }
            return new ExtractionLimits(this);
        }

        private static int nonNegative(int value, String name) {
            if (value < 0) {
                throw new IllegalArgumentException(
                        name + " must not be negative");
            }
            return value;
        }
    }
}
