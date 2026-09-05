package net.zerocloud.pdf.composition;

import java.util.Objects;

/** Immutable version-1 tab stop relative to the paragraph's left inset, in points. */
public final class TabStop {
    /** Representation version. */ public static final int VERSION_1 = 1;
    /** Placement of the following atomic tab field. */
    public enum Alignment {
        /** Align the field's left edge. */ LEFT,
        /** Align the field's center. */ CENTER,
        /** Align the field's right edge. */ RIGHT,
        /** Align its first anchor scalar, or its right edge if absent. */ ANCHOR
    }
    private final double position;
    private final Alignment alignment;
    private final int anchor;

    private TabStop(double position, Alignment alignment, int anchor) {
        this.position = position;
        this.alignment = Objects.requireNonNull(alignment, "alignment");
        this.anchor = anchor;
    }
    /** Declares a stop; ANCHOR uses a full stop (U+002E). */
    public static TabStop version1(double position, Alignment alignment) {
        return new TabStop(position, alignment, '.');
    }
    /** Declares an ANCHOR stop using an explicit Unicode scalar. */
    public static TabStop anchored(double position, int anchor) {
        return new TabStop(position, Alignment.ANCHOR, anchor);
    }
    /** @return representation version */ public int getVersion() { return VERSION_1; }
    /** @return positive position relative to the paragraph's left inset */
    public double getPosition() { return position; }
    /** @return field alignment */ public Alignment getAlignment() { return alignment; }
    /** @return anchor scalar */ public int getAnchor() { return anchor; }
}
