package net.zerocloud.pdf;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One immutable text-markup quadrilateral in PDF QuadPoints order.
 *
 * <p>Coordinates are top-left, top-right, bottom-left, and bottom-right.</p>
 *
 * @since 0.1.0
 */
public final class AnnotationQuad {

    private final List<BigDecimal> coordinates;

    private AnnotationQuad(BigDecimal... coordinates) {
        List<BigDecimal> copied = new ArrayList<BigDecimal>(8);
        for (BigDecimal coordinate : coordinates) {
            copied.add(Objects.requireNonNull(coordinate, "coordinates"));
        }
        this.coordinates = Collections.unmodifiableList(copied);
    }

    /**
     * Creates a quadrilateral from exact decimal coordinates.
     * @return the immutable quadrilateral
     */
    public static AnnotationQuad of(
            BigDecimal topLeftX,
            BigDecimal topLeftY,
            BigDecimal topRightX,
            BigDecimal topRightY,
            BigDecimal bottomLeftX,
            BigDecimal bottomLeftY,
            BigDecimal bottomRightX,
            BigDecimal bottomRightY) {
        return new AnnotationQuad(
                topLeftX, topLeftY, topRightX, topRightY,
                bottomLeftX, bottomLeftY, bottomRightX, bottomRightY);
    }

    /**
     * Creates a quadrilateral from integer coordinates.
     * @return the immutable quadrilateral
     */
    public static AnnotationQuad of(
            long topLeftX,
            long topLeftY,
            long topRightX,
            long topRightY,
            long bottomLeftX,
            long bottomLeftY,
            long bottomRightX,
            long bottomRightY) {
        return of(
                BigDecimal.valueOf(topLeftX), BigDecimal.valueOf(topLeftY),
                BigDecimal.valueOf(topRightX), BigDecimal.valueOf(topRightY),
                BigDecimal.valueOf(bottomLeftX), BigDecimal.valueOf(bottomLeftY),
                BigDecimal.valueOf(bottomRightX), BigDecimal.valueOf(bottomRightY));
    }

    /** Returns immutable coordinates in PDF order. @return the coordinates */
    public List<BigDecimal> getCoordinates() {
        return coordinates;
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof AnnotationQuad
                && coordinates.equals(((AnnotationQuad) candidate).coordinates);
    }

    @Override
    public int hashCode() {
        return coordinates.hashCode();
    }

    @Override
    public String toString() {
        return "AnnotationQuad" + coordinates;
    }
}
