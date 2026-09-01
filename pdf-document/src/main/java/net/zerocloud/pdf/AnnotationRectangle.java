package net.zerocloud.pdf;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * An immutable annotation rectangle in default user-space coordinates.
 *
 * <p>The right coordinate must be greater than the left coordinate and the
 * top coordinate must be greater than the bottom coordinate.</p>
 *
 * @since 0.1.0
 */
public final class AnnotationRectangle {

    private final BigDecimal left;
    private final BigDecimal bottom;
    private final BigDecimal right;
    private final BigDecimal top;

    private AnnotationRectangle(
            BigDecimal left,
            BigDecimal bottom,
            BigDecimal right,
            BigDecimal top) {
        this.left = Objects.requireNonNull(left, "left");
        this.bottom = Objects.requireNonNull(bottom, "bottom");
        this.right = Objects.requireNonNull(right, "right");
        this.top = Objects.requireNonNull(top, "top");
        if (right.compareTo(left) <= 0) {
            throw new IllegalArgumentException("right must be greater than left");
        }
        if (top.compareTo(bottom) <= 0) {
            throw new IllegalArgumentException("top must be greater than bottom");
        }
    }

    /**
     * Creates a rectangle from exact decimal coordinates.
     *
     * @param left the left coordinate
     * @param bottom the bottom coordinate
     * @param right the right coordinate
     * @param top the top coordinate
     * @return the immutable rectangle
     */
    public static AnnotationRectangle of(
            BigDecimal left,
            BigDecimal bottom,
            BigDecimal right,
            BigDecimal top) {
        return new AnnotationRectangle(left, bottom, right, top);
    }

    /**
     * Creates a rectangle from integer coordinates.
     *
     * @param left the left coordinate
     * @param bottom the bottom coordinate
     * @param right the right coordinate
     * @param top the top coordinate
     * @return the immutable rectangle
     */
    public static AnnotationRectangle of(
            long left,
            long bottom,
            long right,
            long top) {
        return of(
                BigDecimal.valueOf(left),
                BigDecimal.valueOf(bottom),
                BigDecimal.valueOf(right),
                BigDecimal.valueOf(top));
    }

    /** Returns the left coordinate. @return the coordinate */
    public BigDecimal getLeft() {
        return left;
    }

    /** Returns the bottom coordinate. @return the coordinate */
    public BigDecimal getBottom() {
        return bottom;
    }

    /** Returns the right coordinate. @return the coordinate */
    public BigDecimal getRight() {
        return right;
    }

    /** Returns the top coordinate. @return the coordinate */
    public BigDecimal getTop() {
        return top;
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof AnnotationRectangle
                && left.equals(((AnnotationRectangle) candidate).left)
                && bottom.equals(((AnnotationRectangle) candidate).bottom)
                && right.equals(((AnnotationRectangle) candidate).right)
                && top.equals(((AnnotationRectangle) candidate).top);
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, bottom, right, top);
    }

    @Override
    public String toString() {
        return "AnnotationRectangle[" + left + ", " + bottom + ", "
                + right + ", " + top + "]";
    }
}
