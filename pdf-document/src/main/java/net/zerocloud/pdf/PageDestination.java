package net.zerocloud.pdf;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An immutable backend-neutral explicit page destination.
 *
 * <p>The page number is one-based. Operand positions follow the destination
 * syntax of ISO 32000; only the {@link Style#XYZ} form permits a {@code null}
 * operand, which leaves the corresponding view setting unchanged.</p>
 *
 * @since 0.1.0
 */
public final class PageDestination {

    /**
     * The destination presentation style.
     *
     * @since 0.1.0
     */
    public enum Style {

        /** Display the page with explicit left, top, and zoom operands. */
        XYZ,

        /** Fit the whole page in the window. */
        FIT,

        /** Fit the page width at the given top coordinate. */
        FIT_H,

        /** Fit the page height at the given left coordinate. */
        FIT_V,

        /** Fit the given rectangle in the window. */
        FIT_R,

        /** Fit the page bounding box in the window. */
        FIT_B,

        /** Fit the bounding-box width at the given top coordinate. */
        FIT_BH,

        /** Fit the bounding-box height at the given left coordinate. */
        FIT_BV
    }

    private final int pageNumber;
    private final Style style;
    private final List<BigDecimal> operands;

    private PageDestination(
            int pageNumber,
            Style style,
            List<BigDecimal> operands) {
        if (pageNumber < 1) {
            throw new IllegalArgumentException(
                    "pageNumber must be at least 1");
        }
        this.pageNumber = pageNumber;
        this.style = Objects.requireNonNull(style, "style");
        this.operands = Collections.unmodifiableList(
                new ArrayList<BigDecimal>(operands));
    }

    /**
     * Creates a fit-page destination.
     *
     * @param pageNumber the one-based page number
     * @return the immutable destination
     */
    public static PageDestination fit(int pageNumber) {
        return new PageDestination(
                pageNumber,
                Style.FIT,
                Collections.<BigDecimal>emptyList());
    }

    /**
     * Creates a fit-bounding-box destination.
     *
     * @param pageNumber the one-based page number
     * @return the immutable destination
     */
    public static PageDestination fitB(int pageNumber) {
        return new PageDestination(
                pageNumber,
                Style.FIT_B,
                Collections.<BigDecimal>emptyList());
    }

    /**
     * Creates a fit-width destination.
     *
     * @param pageNumber the one-based page number
     * @param top the top coordinate
     * @return the immutable destination
     */
    public static PageDestination fitH(int pageNumber, BigDecimal top) {
        return new PageDestination(
                pageNumber,
                Style.FIT_H,
                Collections.singletonList(
                        Objects.requireNonNull(top, "top")));
    }

    /**
     * Creates a fit-bounding-box-width destination.
     *
     * @param pageNumber the one-based page number
     * @param top the top coordinate
     * @return the immutable destination
     */
    public static PageDestination fitBH(int pageNumber, BigDecimal top) {
        return new PageDestination(
                pageNumber,
                Style.FIT_BH,
                Collections.singletonList(
                        Objects.requireNonNull(top, "top")));
    }

    /**
     * Creates a fit-height destination.
     *
     * @param pageNumber the one-based page number
     * @param left the left coordinate
     * @return the immutable destination
     */
    public static PageDestination fitV(int pageNumber, BigDecimal left) {
        return new PageDestination(
                pageNumber,
                Style.FIT_V,
                Collections.singletonList(
                        Objects.requireNonNull(left, "left")));
    }

    /**
     * Creates a fit-bounding-box-height destination.
     *
     * @param pageNumber the one-based page number
     * @param left the left coordinate
     * @return the immutable destination
     */
    public static PageDestination fitBV(int pageNumber, BigDecimal left) {
        return new PageDestination(
                pageNumber,
                Style.FIT_BV,
                Collections.singletonList(
                        Objects.requireNonNull(left, "left")));
    }

    /**
     * Creates a fit-rectangle destination.
     *
     * @param pageNumber the one-based page number
     * @param left the left coordinate
     * @param bottom the bottom coordinate
     * @param right the right coordinate
     * @param top the top coordinate
     * @return the immutable destination
     */
    public static PageDestination fitR(
            int pageNumber,
            BigDecimal left,
            BigDecimal bottom,
            BigDecimal right,
            BigDecimal top) {
        List<BigDecimal> operands = new ArrayList<BigDecimal>(4);
        operands.add(Objects.requireNonNull(left, "left"));
        operands.add(Objects.requireNonNull(bottom, "bottom"));
        operands.add(Objects.requireNonNull(right, "right"));
        operands.add(Objects.requireNonNull(top, "top"));
        return new PageDestination(pageNumber, Style.FIT_R, operands);
    }

    /**
     * Creates an explicit-position destination. Any operand may be
     * {@code null}, leaving the current view setting unchanged.
     *
     * @param pageNumber the one-based page number
     * @param left the left coordinate, or {@code null}
     * @param top the top coordinate, or {@code null}
     * @param zoom the zoom factor, or {@code null}
     * @return the immutable destination
     */
    public static PageDestination xyz(
            int pageNumber,
            BigDecimal left,
            BigDecimal top,
            BigDecimal zoom) {
        List<BigDecimal> operands = new ArrayList<BigDecimal>(3);
        operands.add(left);
        operands.add(top);
        operands.add(zoom);
        return new PageDestination(pageNumber, Style.XYZ, operands);
    }

    /**
     * Returns the one-based destination page number.
     *
     * @return the page number
     */
    public int getPageNumber() {
        return pageNumber;
    }

    /**
     * Returns the presentation style.
     *
     * @return the style
     */
    public Style getStyle() {
        return style;
    }

    /**
     * Returns the immutable style operands in destination syntax order.
     *
     * @return the operands; only {@link Style#XYZ} may contain {@code null}
     */
    public List<BigDecimal> getOperands() {
        return operands;
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof PageDestination
                && pageNumber == ((PageDestination) candidate).pageNumber
                && style == ((PageDestination) candidate).style
                && operands.equals(((PageDestination) candidate).operands);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(pageNumber),
                style,
                operands);
    }

    @Override
    public String toString() {
        return "PageDestination[page=" + pageNumber
                + ", style=" + style
                + ", operands=" + operands + "]";
    }
}
