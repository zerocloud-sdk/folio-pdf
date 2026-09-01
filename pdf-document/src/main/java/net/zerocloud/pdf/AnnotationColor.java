package net.zerocloud.pdf;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An immutable device color used by a supported annotation.
 *
 * @since 0.1.0
 */
public final class AnnotationColor {

    /** Supported device color spaces. @since 0.1.0 */
    public enum Space {
        /** One gray component. */ GRAY,
        /** Red, green, and blue components. */ RGB,
        /** Cyan, magenta, yellow, and black components. */ CMYK
    }

    private final Space space;
    private final List<BigDecimal> components;

    private AnnotationColor(Space space, BigDecimal... components) {
        this.space = Objects.requireNonNull(space, "space");
        List<BigDecimal> copied = new ArrayList<BigDecimal>(components.length);
        for (BigDecimal component : components) {
            BigDecimal value = Objects.requireNonNull(component, "components");
            if (value.compareTo(BigDecimal.ZERO) < 0
                    || value.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException(
                        "color components must be between zero and one");
            }
            copied.add(value);
        }
        this.components = Collections.unmodifiableList(copied);
    }

    /** Creates a gray color. @param gray gray component @return the color */
    public static AnnotationColor gray(BigDecimal gray) {
        return new AnnotationColor(Space.GRAY, gray);
    }

    /**
     * Creates an RGB color.
     * @param red red component
     * @param green green component
     * @param blue blue component
     * @return the color
     */
    public static AnnotationColor rgb(
            BigDecimal red,
            BigDecimal green,
            BigDecimal blue) {
        return new AnnotationColor(Space.RGB, red, green, blue);
    }

    /**
     * Creates a CMYK color.
     * @param cyan cyan component
     * @param magenta magenta component
     * @param yellow yellow component
     * @param black black component
     * @return the color
     */
    public static AnnotationColor cmyk(
            BigDecimal cyan,
            BigDecimal magenta,
            BigDecimal yellow,
            BigDecimal black) {
        return new AnnotationColor(Space.CMYK,
                cyan, magenta, yellow, black);
    }

    /** Returns the device color space. @return the space */
    public Space getSpace() {
        return space;
    }

    /** Returns immutable components in device order. @return the components */
    public List<BigDecimal> getComponents() {
        return components;
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof AnnotationColor
                && space == ((AnnotationColor) candidate).space
                && components.equals(((AnnotationColor) candidate).components);
    }

    @Override
    public int hashCode() {
        return Objects.hash(space, components);
    }

    @Override
    public String toString() {
        return "AnnotationColor[space=" + space
                + ", components=" + components + "]";
    }
}
