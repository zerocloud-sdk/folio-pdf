package net.zerocloud.pdf.composition;

import java.util.Objects;

/** Complete finite bounds for one paragraph flow. All builder fields are mandatory. */
public final class CompositionLimits {
    /** Supported representation version. */ public static final int VERSION_1 = 1;
    private final int maximumPages;
    private final int maximumAreas;
    private final int maximumFlowItems;
    private final int maximumInlines;
    private final int maximumLines;
    private final long maximumGeneratedContentBytes;
    private final FontLimits fontLimits;
    private final CanvasResourceLimits graphicLimits;

    private CompositionLimits(Builder builder) {
        maximumPages = builder.maximumPages;
        maximumAreas = builder.maximumAreas;
        maximumFlowItems = builder.maximumFlowItems;
        maximumInlines = builder.maximumInlines;
        maximumLines = builder.maximumLines;
        maximumGeneratedContentBytes = builder.maximumGeneratedContentBytes;
        fontLimits = builder.fontLimits;
        graphicLimits = builder.graphicLimits;
    }

    /** @return a complete-limits builder */ public static Builder builder() { return new Builder(); }
    /** @return representation version */ public int getVersion() { return VERSION_1; }
    /** @return page declaration bound */ public int getMaximumPages() { return maximumPages; }
    /** @return total area declaration bound */ public int getMaximumAreas() { return maximumAreas; }
    /** @return flow item bound, including breaks */ public int getMaximumFlowItems() { return maximumFlowItems; }
    /** @return total inline declaration bound */ public int getMaximumInlines() { return maximumInlines; }
    /** @return total laid-out line bound */ public int getMaximumLines() { return maximumLines; }
    /** @return aggregate new page operator bytes, excluding resource streams */
    public long getMaximumGeneratedContentBytes() { return maximumGeneratedContentBytes; }
    /** @return aggregate font staging, selection and text operator bounds for this flow */
    public FontLimits getFontLimits() { return fontLimits; }
    /** @return resource bounds applied separately to each inline graphic */
    public CanvasResourceLimits getGraphicLimits() { return graphicLimits; }

    /** Builds explicit limits; exact nonnegative boundaries are admitted. */
    public static final class Builder {
        private int maximumPages = -1;
        private int maximumAreas = -1;
        private int maximumFlowItems = -1;
        private int maximumInlines = -1;
        private int maximumLines = -1;
        private long maximumGeneratedContentBytes = -1;
        private FontLimits fontLimits;
        private CanvasResourceLimits graphicLimits;
        private Builder() { }
        /** Sets page declaration bound. @return this builder */
        public Builder maximumPages(int value) { nonnegative(value); maximumPages = value; return this; }
        /** Sets area declaration bound. @return this builder */
        public Builder maximumAreas(int value) { nonnegative(value); maximumAreas = value; return this; }
        /** Sets flow item bound. @return this builder */
        public Builder maximumFlowItems(int value) { nonnegative(value); maximumFlowItems = value; return this; }
        /** Sets aggregate inline bound. @return this builder */
        public Builder maximumInlines(int value) { nonnegative(value); maximumInlines = value; return this; }
        /** Sets line bound. @return this builder */
        public Builder maximumLines(int value) { nonnegative(value); maximumLines = value; return this; }
        /** Sets aggregate page operator byte bound. @return this builder */
        public Builder maximumGeneratedContentBytes(long value) {
            nonnegative(value); maximumGeneratedContentBytes = value; return this;
        }
        /** Sets font bounds for the complete flow. @return this builder */
        public Builder fontLimits(FontLimits value) { fontLimits = Objects.requireNonNull(value, "fontLimits"); return this; }
        /** Sets bounds for each inline graphic. @return this builder */
        public Builder graphicLimits(CanvasResourceLimits value) {
            graphicLimits = Objects.requireNonNull(value, "graphicLimits"); return this;
        }
        /** @return complete immutable limits */
        public CompositionLimits build() {
            if (maximumPages < 0 || maximumAreas < 0 || maximumFlowItems < 0
                    || maximumInlines < 0 || maximumLines < 0
                    || maximumGeneratedContentBytes < 0 || fontLimits == null || graphicLimits == null) {
                throw new IllegalStateException("Every version-1 Composition limit must be declared.");
            }
            return new CompositionLimits(this);
        }
        private static void nonnegative(long value) {
            if (value < 0) { throw new IllegalArgumentException("A Composition limit must not be negative."); }
        }
    }
}
