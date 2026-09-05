package net.zerocloud.pdf.composition;

/** Immutable page insets, in PDF points. Numeric validation occurs in the workflow. */
public final class PageMargins {
    private final double top;
    private final double right;
    private final double bottom;
    private final double left;

    private PageMargins(double top, double right, double bottom, double left) {
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.left = left;
    }

    /** Declares the top, right, bottom and left insets. */
    public static PageMargins of(double top, double right, double bottom, double left) {
        return new PageMargins(top, right, bottom, left);
    }

    /** @return top inset */ public double getTop() { return top; }
    /** @return right inset */ public double getRight() { return right; }
    /** @return bottom inset */ public double getBottom() { return bottom; }
    /** @return left inset */ public double getLeft() { return left; }
}
