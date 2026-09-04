package net.zerocloud.pdf.composition;

import java.util.Arrays;
import java.util.Objects;

/** An immutable Canvas Color in one declared Canvas Color Space. @since 0.1.0 */
public final class CanvasColor {

    /** The currently supported declaration representation version. */
    public static final int VERSION_1 = 1;

    private final CanvasColorSpace colorSpace;
    private final double[] components;

    private CanvasColor(CanvasColorSpace colorSpace, double[] components) {
        this.colorSpace = Objects.requireNonNull(colorSpace, "colorSpace");
        this.components = Objects.requireNonNull(components, "components").clone();
    }

    /** Creates a color in an explicitly declared color space. */
    public static CanvasColor of(
            CanvasColorSpace colorSpace,
            double... components) {
        return new CanvasColor(colorSpace, components);
    }

    /** Creates a DeviceGray color. */
    public static CanvasColor gray(double gray) {
        return of(CanvasColorSpace.deviceGray(), gray);
    }

    /** Creates a DeviceRGB color. */
    public static CanvasColor rgb(double red, double green, double blue) {
        return of(CanvasColorSpace.deviceRgb(), red, green, blue);
    }

    /** Creates a DeviceCMYK color. */
    public static CanvasColor cmyk(
            double cyan,
            double magenta,
            double yellow,
            double black) {
        return of(CanvasColorSpace.deviceCmyk(), cyan, magenta, yellow, black);
    }

    /** @return {@link #VERSION_1} */
    public int getVersion() { return VERSION_1; }

    /** @return the immutable color-space declaration */
    public CanvasColorSpace getColorSpace() { return colorSpace; }

    /** @return a defensive component copy */
    public double[] getComponents() { return components.clone(); }

    /** @return the exact component count without copying it */
    public int getComponentCount() { return components.length; }

    @Override
    public boolean equals(Object candidate) {
        if (!(candidate instanceof CanvasColor)) {
            return false;
        }
        CanvasColor other = (CanvasColor) candidate;
        return colorSpace.equals(other.colorSpace)
                && Arrays.equals(components, other.components);
    }

    @Override
    public int hashCode() {
        return 31 * colorSpace.hashCode() + Arrays.hashCode(components);
    }
}
