package net.zerocloud.pdf.composition;

/** Immutable rectangle in PDF default user space. */
public final class CanvasRectangle {

    private final double lowerLeftX;
    private final double lowerLeftY;
    private final double upperRightX;
    private final double upperRightY;

    private CanvasRectangle(
            double lowerLeftX,
            double lowerLeftY,
            double upperRightX,
            double upperRightY) {
        this.lowerLeftX = lowerLeftX;
        this.lowerLeftY = lowerLeftY;
        this.upperRightX = upperRightX;
        this.upperRightY = upperRightY;
    }

    /** Creates a rectangle; full numeric validation occurs during drawing. */
    public static CanvasRectangle of(
            double lowerLeftX,
            double lowerLeftY,
            double upperRightX,
            double upperRightY) {
        return new CanvasRectangle(
                lowerLeftX,
                lowerLeftY,
                upperRightX,
                upperRightY);
    }

    /** @return lower-left x */ public double getLowerLeftX() { return lowerLeftX; }
    /** @return lower-left y */ public double getLowerLeftY() { return lowerLeftY; }
    /** @return upper-right x */ public double getUpperRightX() { return upperRightX; }
    /** @return upper-right y */ public double getUpperRightY() { return upperRightY; }

    @Override
    public boolean equals(Object candidate) {
        if (!(candidate instanceof CanvasRectangle)) {
            return false;
        }
        CanvasRectangle other = (CanvasRectangle) candidate;
        return bits(lowerLeftX) == bits(other.lowerLeftX)
                && bits(lowerLeftY) == bits(other.lowerLeftY)
                && bits(upperRightX) == bits(other.upperRightX)
                && bits(upperRightY) == bits(other.upperRightY);
    }

    @Override
    public int hashCode() {
        int result = hash(lowerLeftX);
        result = 31 * result + hash(lowerLeftY);
        result = 31 * result + hash(upperRightX);
        result = 31 * result + hash(upperRightY);
        return result;
    }

    private static long bits(double value) { return Double.doubleToLongBits(value); }
    private static int hash(double value) {
        long bits = bits(value);
        return (int) (bits ^ (bits >>> 32));
    }
}
