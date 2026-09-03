package net.zerocloud.pdf.composition.command;

import java.util.Objects;
import net.zerocloud.pdf.DocumentCommand;
import net.zerocloud.pdf.composition.FontLimits;
import net.zerocloud.pdf.composition.PositionedUnicodeText;

/**
 * Appends one explicitly positioned Unicode text run to a one-based page.
 *
 * @since 0.1.0
 */
public final class DrawPositionedUnicodeText implements DocumentCommand {

    /** The currently supported command representation version. */
    public static final int VERSION_1 = 1;

    private final int pageNumber;
    private final PositionedUnicodeText positionedUnicodeText;
    private final FontLimits limits;

    private DrawPositionedUnicodeText(
            int pageNumber,
            PositionedUnicodeText positionedUnicodeText,
            FontLimits limits) {
        this.pageNumber = pageNumber;
        this.positionedUnicodeText = Objects.requireNonNull(
                positionedUnicodeText,
                "positionedUnicodeText");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    /** Creates a version-1 font-loading and positioned-text command. */
    public static DrawPositionedUnicodeText version1(
            int pageNumber,
            PositionedUnicodeText positionedUnicodeText,
            FontLimits limits) {
        return new DrawPositionedUnicodeText(
                pageNumber,
                positionedUnicodeText,
                limits);
    }

    /** @return {@link #VERSION_1} */
    public int getVersion() { return VERSION_1; }
    /** @return one-based target page */
    public int getPageNumber() { return pageNumber; }
    /** @return immutable positioned text */
    public PositionedUnicodeText getPositionedUnicodeText() {
        return positionedUnicodeText;
    }
    /** @return complete font-operation limits */
    public FontLimits getLimits() { return limits; }
}
