package net.zerocloud.pdf.composition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One new, unrotated page and its declaration-ordered layout areas.
 * Areas use coordinates relative to the bottom-left of the margin box.
 * An empty area list selects the complete margin box as one area.
 *
 * @since 0.1.0
 */
public final class LayoutPage {
    /** Supported representation version. */
    public static final int VERSION_1 = 1;
    private final double width;
    private final double height;
    private final PageMargins margins;
    private final List<CanvasRectangle> areas;

    private LayoutPage(double width, double height, PageMargins margins,
            CanvasRectangle[] areas) {
        this.width = width;
        this.height = height;
        this.margins = Objects.requireNonNull(margins, "margins");
        List<CanvasRectangle> copy = new ArrayList<CanvasRectangle>();
        for (CanvasRectangle area : areas) {
            copy.add(Objects.requireNonNull(area, "area"));
        }
        this.areas = Collections.unmodifiableList(copy);
    }

    /** Declares page dimensions, insets and optional explicit areas, all in points. */
    public static LayoutPage version1(double width, double height,
            PageMargins margins, CanvasRectangle... areas) {
        return new LayoutPage(width, height, margins, areas);
    }

    /** @return representation version */ public int getVersion() { return VERSION_1; }
    /** @return page width */ public double getWidth() { return width; }
    /** @return page height */ public double getHeight() { return height; }
    /** @return page insets */ public PageMargins getMargins() { return margins; }
    /** @return immutable area sequence, or empty for the margin box */
    public List<CanvasRectangle> getAreas() { return areas; }
}
