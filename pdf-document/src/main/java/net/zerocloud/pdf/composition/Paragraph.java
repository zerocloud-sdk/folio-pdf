package net.zerocloud.pdf.composition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable semantic paragraph of unshaped Unicode text and atomic inline graphics.
 * Version 1 wraps at ASCII spaces and falls back to Unicode scalar boundaries for
 * an overlong word. LF is an explicit line break; tabs and other control characters
 * are rejected. Spaces are preserved, including at automatic line boundaries.
 *
 * @since 0.1.0
 */
public final class Paragraph {
    /** Supported representation version. */
    public static final int VERSION_1 = 1;

    /** Horizontal placement within the paragraph's available width. */
    public enum Alignment {
        /** Place at the left edge. */ LEFT,
        /** Distribute unused width equally on either side. */ CENTER,
        /** Place at the right edge. */ RIGHT,
        /** Expand spaces on automatic, nonfinal lines. */ JUSTIFIED
    }

    private final List<Inline> inlines;
    private final double leading;
    private final double maximumWidth;
    private final Alignment alignment;

    private Paragraph(Builder builder) {
        inlines = Collections.unmodifiableList(new ArrayList<Inline>(builder.inlines));
        leading = builder.leading;
        maximumWidth = builder.maximumWidth;
        alignment = builder.alignment;
    }

    /**
     * Begins a paragraph with a positive minimum line-box height in points.
     * Each line reserves the larger of leading and its content's ascent plus descent.
     */
    public static Builder version1(double leading) { return new Builder(leading); }
    /** @return representation version */ public int getVersion() { return VERSION_1; }
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
        private final double leading;
        private double maximumWidth;
        private Alignment alignment = Alignment.LEFT;
        private Builder(double leading) { this.leading = leading; }
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
