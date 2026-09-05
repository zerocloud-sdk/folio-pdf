package net.zerocloud.pdf.composition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable semantic paragraph of unshaped Unicode text and atomic inline graphics.
 * Version 1 wraps at ASCII spaces and falls back to Unicode scalar boundaries for
 * an overlong word. LF is an explicit line break; tabs and other control characters
 * are rejected. Version 2 adds tabs, insets and hard pagination constraints. Spaces are preserved, including at automatic line boundaries.
 *
 * @since 0.1.0
 */
public final class Paragraph {
    /** Supported representation version. */
    public static final int VERSION_1 = 1;
    /** Advanced pagination representation. */ public static final int VERSION_2 = 2;

    /** Horizontal handling of an overlong atomic unit; vertical flow always uses finite areas. */
    public enum Overflow {
        /** Wrap words at scalar boundaries; graphics and tab fields remain atomic. */ WRAP,
        /** Move an unbreakable word, graphic or tab field to a fitting area, or fail. */ REJECT,
        /** Preserve an overlong unit and its ink beyond the line's right edge. */ VISIBLE
    }

    /** Horizontal placement within the paragraph's available width. */
    public enum Alignment {
        /** Place at the left edge. */ LEFT,
        /** Distribute unused width equally on either side. */ CENTER,
        /** Place at the right edge. */ RIGHT,
        /** Expand spaces on automatic, nonfinal lines. */ JUSTIFIED
    }

    private final int version;
    private final double leftIndent;
    private final double rightIndent;
    private final double firstLineIndent;
    private final double tabInterval;
    private final List<TabStop> tabStops;
    private final boolean keepWithNext;
    private final boolean keepTogether;
    private final int widows;
    private final int orphans;
    private final Overflow overflow;
    private final List<Inline> inlines;
    private final double leading;
    private final double maximumWidth;
    private final Alignment alignment;

    private Paragraph(Builder builder) {
        inlines = Collections.unmodifiableList(new ArrayList<Inline>(builder.inlines));
        version = builder.version;
        leftIndent = builder.leftIndent;
        rightIndent = builder.rightIndent;
        firstLineIndent = builder.firstLineIndent;
        tabInterval = builder.tabInterval;
        tabStops = Collections.unmodifiableList(new ArrayList<TabStop>(builder.tabStops));
        keepWithNext = builder.keepWithNext;
        keepTogether = builder.keepTogether;
        widows = builder.widows;
        orphans = builder.orphans;
        overflow = builder.overflow;
        leading = builder.leading;
        maximumWidth = builder.maximumWidth;
        alignment = builder.alignment;
    }

    /**
     * Begins a paragraph with a positive minimum line-box height in points.
     * Each line reserves the larger of leading and its content's ascent plus descent.
     */
    public static Builder version1(double leading) { return new Builder(VERSION_1, leading); }
    /** @return representation version */ public int getVersion() { return version; }
    /** Begins advanced pagination; all advanced options have neutral defaults. */
    public static Builder version2(double leading) { return new Builder(VERSION_2, leading); }
    /** @return nonnegative left inset in points */ public double getLeftIndent() { return leftIndent; }
    /** @return nonnegative right inset in points */ public double getRightIndent() { return rightIndent; }
    /** @return signed extra inset on the first line only */ public double getFirstLineIndent() { return firstLineIndent; }
    /** @return positive repeating tab interval in points */ public double getTabInterval() { return tabInterval; }
    /** @return ordered explicit tab stops */ public List<TabStop> getTabStops() { return tabStops; }
    /** @return whether the final line must share an area with the next paragraph */
    public boolean isKeepWithNext() { return keepWithNext; }
    /** @return whether every line must occupy one area */ public boolean isKeepTogether() { return keepTogether; }
    /** @return minimum lines in each continuation fragment */ public int getWidows() { return widows; }
    /** @return minimum lines before each split */ public int getOrphans() { return orphans; }
    /** @return horizontal overflow policy */ public Overflow getOverflow() { return overflow; }
    /** @return ordered, immutable inline content */ public List<Inline> getInlines() { return inlines; }
    /** @return minimum line-box height */ public double getLeading() { return leading; }
    /** @return width cap in points, or zero to use the full area width */
    public double getMaximumWidth() { return maximumWidth; }
    /** @return horizontal alignment */ public Alignment getAlignment() { return alignment; }

    /** Closed inline declaration. A graphic's local bounding box scales to its width and height. */
    public static final class Inline {
        /** Supported inline kinds. */
        public enum Kind { TEXT, GRAPHIC }
        private final Kind kind;
        private final String text;
        private final double fontSize;
        private final CanvasTransparencyGroup graphic;
        private final double width;
        private final double height;

        private Inline(String text, double fontSize) {
            this.kind = Kind.TEXT;
            this.text = Objects.requireNonNull(text, "text");
            this.fontSize = fontSize;
            this.graphic = null;
            this.width = 0;
            this.height = 0;
        }

        private Inline(CanvasTransparencyGroup graphic, double width, double height) {
            this.kind = Kind.GRAPHIC;
            this.text = null;
            this.fontSize = 0;
            this.graphic = Objects.requireNonNull(graphic, "graphic");
            this.width = width;
            this.height = height;
        }

        /** @return inline kind */ public Kind getKind() { return kind; }
        /** @return text for TEXT, otherwise null */ public String getText() { return text; }
        /** @return text size in points */ public double getFontSize() { return fontSize; }
        /** @return graphic for GRAPHIC, otherwise null */
        public CanvasTransparencyGroup getGraphic() { return graphic; }
        /** @return graphic width */ public double getWidth() { return width; }
        /** @return graphic height above the baseline */ public double getHeight() { return height; }
    }

    /** Records paragraph declarations; operational validation occurs during execution. */
    public static final class Builder {
        private final List<Inline> inlines = new ArrayList<Inline>();
        private final int version;
        private final double leading;
        private double leftIndent;
        private double rightIndent;
        private double firstLineIndent;
        private double tabInterval = 36;
        private final List<TabStop> tabStops = new ArrayList<TabStop>();
        private boolean keepWithNext;
        private boolean keepTogether;
        private int widows = 1;
        private int orphans = 1;
        private Overflow overflow = Overflow.WRAP;
        private double maximumWidth;
        private Alignment alignment = Alignment.LEFT;
        private Builder(int version, double leading) { this.version = version; this.leading = leading; }
        /** Sets version-2 left/right insets and signed first-line offset. @return this builder */
        public Builder indentation(double left, double right, double firstLine) {
            leftIndent = left; rightIndent = right; firstLineIndent = firstLine; return this;
        }
        /** Sets the positive version-2 repeating tab grid. @return this builder */
        public Builder tabInterval(double points) { tabInterval = points; return this; }
        /** Appends a strictly increasing version-2 stop. @return this builder */
        public Builder tabStop(TabStop stop) { tabStops.add(Objects.requireNonNull(stop, "stop")); return this; }
        /** Keeps the final line with the next paragraph's first fragment. @return this builder */
        public Builder keepWithNext(boolean value) { keepWithNext = value; return this; }
        /** Requires the complete paragraph to occupy one area. @return this builder */
        public Builder keepTogether(boolean value) { keepTogether = value; return this; }
        /** Sets the positive continuation-fragment minimum. @return this builder */
        public Builder widows(int value) { widows = value; return this; }
        /** Sets the positive outgoing-fragment minimum. @return this builder */
        public Builder orphans(int value) { orphans = value; return this; }
        /** Sets version-2 horizontal overflow. @return this builder */
        public Builder overflow(Overflow value) { overflow = Objects.requireNonNull(value, "overflow"); return this; }
        /** Appends text at an explicit font size. @return this builder */
        public Builder text(String text, double fontSize) {
            inlines.add(new Inline(text, fontSize));
            return this;
        }
        /** Appends a bounded atomic graphic, aligned to the text baseline. @return this builder */
        public Builder graphic(CanvasTransparencyGroup graphic, double width, double height) {
            inlines.add(new Inline(graphic, width, height));
            return this;
        }
        /** Sets horizontal alignment. @return this builder */
        public Builder alignment(Alignment value) {
            alignment = Objects.requireNonNull(value, "alignment");
            return this;
        }
        /** Caps paragraph width; zero selects the area width. @return this builder */
        public Builder maximumWidth(double value) { maximumWidth = value; return this; }
        /** @return immutable paragraph */ public Paragraph build() { return new Paragraph(this); }
    }
}
