package net.zerocloud.pdf.composition;

import java.util.Objects;

/** Immutable fill alpha, stroke alpha, and blend-mode declaration. */
public final class CanvasTransparencyState {

    /** The currently supported declaration representation version. */
    public static final int VERSION_1 = 1;

    private final double fillAlpha;
    private final double strokeAlpha;
    private final CanvasBlendMode blendMode;

    private CanvasTransparencyState(
            double fillAlpha,
            double strokeAlpha,
            CanvasBlendMode blendMode) {
        this.fillAlpha = fillAlpha;
        this.strokeAlpha = strokeAlpha;
        this.blendMode = Objects.requireNonNull(blendMode, "blendMode");
    }

    /** Creates a version-1 transparency-state declaration. */
    public static CanvasTransparencyState version1(
            double fillAlpha,
            double strokeAlpha,
            CanvasBlendMode blendMode) {
        return new CanvasTransparencyState(fillAlpha, strokeAlpha, blendMode);
    }

    /** @return {@link #VERSION_1} */
    public int getVersion() { return VERSION_1; }
    /** @return nonstroking alpha */
    public double getFillAlpha() { return fillAlpha; }
    /** @return stroking alpha */
    public double getStrokeAlpha() { return strokeAlpha; }
    /** @return blend mode */
    public CanvasBlendMode getBlendMode() { return blendMode; }

    @Override
    public boolean equals(Object candidate) {
        if (!(candidate instanceof CanvasTransparencyState)) {
            return false;
        }
        CanvasTransparencyState other = (CanvasTransparencyState) candidate;
        return Double.doubleToLongBits(fillAlpha)
                        == Double.doubleToLongBits(other.fillAlpha)
                && Double.doubleToLongBits(strokeAlpha)
                        == Double.doubleToLongBits(other.strokeAlpha)
                && blendMode == other.blendMode;
    }

    @Override
    public int hashCode() {
        long fill = Double.doubleToLongBits(fillAlpha);
        long stroke = Double.doubleToLongBits(strokeAlpha);
        int result = (int) (fill ^ (fill >>> 32));
        result = 31 * result + (int) (stroke ^ (stroke >>> 32));
        result = 31 * result + blendMode.hashCode();
        return result;
    }
}
