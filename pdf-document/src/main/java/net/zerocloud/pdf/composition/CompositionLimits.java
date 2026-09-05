package net.zerocloud.pdf.composition;

import java.util.Objects;

/** Complete finite bounds for one paragraph flow. All builder fields are mandatory. */
public final class CompositionLimits {
    /** Supported representation version. */ public static final int VERSION_1 = 1;
    /** Includes bounded pagination search and relayout. */ public static final int VERSION_2 = 2;
    private final int version;
    private final int maximumLayoutAttempts;
    private final int maximumRelayouts;
    private final int maximumPages;
    private final int maximumAreas;
    private final int maximumFlowItems;
    private final int maximumInlines;
    private final int maximumLines;
    private final long maximumGeneratedContentBytes;
    private final FontLimits fontLimits;
    private final CanvasResourceLimits graphicLimits;

    private CompositionLimits(Builder builder) {
        version = builder.version;
        maximumLayoutAttempts = builder.maximumLayoutAttempts;
        maximumRelayouts = builder.maximumRelayouts;
        maximumPages = builder.maximumPages;
        maximumAreas = builder.maximumAreas;
        maximumFlowItems = builder.maximumFlowItems;
        maximumInlines = builder.maximumInlines;
        maximumLines = builder.maximumLines;
        maximumGeneratedContentBytes = builder.maximumGeneratedContentBytes;
        fontLimits = builder.fontLimits;
        graphicLimits = builder.graphicLimits;
    }

    /** @return a complete-limits builder */ public static Builder builder() { return new Builder(VERSION_1); }
    /** @return representation version */ public int getVersion() { return version; }
    /** Begins complete limits including mandatory search and relayout bounds. */
    public static Builder version2() { return new Builder(VERSION_2); }
    /** @return candidate line and search transition bound per layout, zero for version 1 */
    public int getMaximumLayoutAttempts() { return maximumLayoutAttempts; }
    /** @return attempted relayout bound for the buffered flow, zero for version 1 */
    public int getMaximumRelayouts() { return maximumRelayouts; }
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
        private final int version;
        private int maximumLayoutAttempts;
        private int maximumRelayouts;
        private int maximumPages = -1;
        private int maximumAreas = -1;
        private int maximumFlowItems = -1;
        private int maximumInlines = -1;
        private int maximumLines = -1;
        private long maximumGeneratedContentBytes = -1;
        private FontLimits fontLimits;
        private CanvasResourceLimits graphicLimits;
        private Builder(int version) {
            this.version = version;
            maximumLayoutAttempts = version == VERSION_2 ? -1 : 0;
            maximumRelayouts = version == VERSION_2 ? -1 : 0;
        }
        /** Sets the version-2 candidate line and search transition bound. @return this builder */
        public Builder maximumLayoutAttempts(int value) {
            nonnegative(value); maximumLayoutAttempts = value; return this;
        }
        /** Sets the version-2 total relayout attempt bound for a buffered flow. @return this builder */
        public Builder maximumRelayouts(int value) { nonnegative(value); maximumRelayouts = value; return this; }
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
            if (maximumLayoutAttempts < 0 || maximumRelayouts < 0 || maximumPages < 0 || maximumAreas < 0 || maximumFlowItems < 0
                    || maximumInlines < 0 || maximumLines < 0
                    || maximumGeneratedContentBytes < 0 || fontLimits == null || graphicLimits == null) {
                throw new IllegalStateException("Every Composition limit for the selected version must be declared.");
            }
            return new CompositionLimits(this);
        }
        private static void nonnegative(long value) {
            if (value < 0) { throw new IllegalArgumentException("A Composition limit must not be negative."); }
        }
    }
}
