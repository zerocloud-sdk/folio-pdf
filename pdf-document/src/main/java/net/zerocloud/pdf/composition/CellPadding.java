package net.zerocloud.pdf.composition;

/** Nonnegative point insets measured inward from a cell's inner border edges. */
public final class CellPadding {
    private final double top;
    private final double right;
    private final double bottom;
    private final double left;
    private CellPadding(double top, double right, double bottom, double left) {
        this.top = top; this.right = right; this.bottom = bottom; this.left = left;
    }
    /** Declares top, right, bottom and left padding in points; execution validates geometry. */
    public static CellPadding of(double top, double right, double bottom, double left) {
        return new CellPadding(top, right, bottom, left);
    }
    /** @return top inset */ public double getTop() { return top; }
    /** @return right inset */ public double getRight() { return right; }
    /** @return bottom inset */ public double getBottom() { return bottom; }
    /** @return left inset */ public double getLeft() { return left; }
}
