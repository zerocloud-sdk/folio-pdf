package net.zerocloud.pdf.composition;

/**
 * Four solid black strips inside each cell rectangle. Adjacent cells retain both
 * strips; their widths add. Spanning cells have no internal grid lines.
 */
public final class TableBorders {
    private final double top;
    private final double right;
    private final double bottom;
    private final double left;
    private TableBorders(double top, double right, double bottom, double left) {
        this.top = top; this.right = right; this.bottom = bottom; this.left = left;
    }
    /** Declares nonnegative top, right, bottom and left strip widths in points. */
    public static TableBorders of(double top, double right, double bottom, double left) {
        return new TableBorders(top, right, bottom, left);
    }
    /** @return top width */ public double getTop() { return top; }
    /** @return right width */ public double getRight() { return right; }
    /** @return bottom width */ public double getBottom() { return bottom; }
    /** @return left width */ public double getLeft() { return left; }
}
