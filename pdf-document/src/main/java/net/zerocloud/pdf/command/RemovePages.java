package net.zerocloud.pdf.command;

import java.util.Objects;
import net.zerocloud.pdf.DocumentCommand;
import net.zerocloud.pdf.PageRange;

/**
 * Removes an inclusive range of pages from the current document.
 *
 * @since 0.1.0
 */
public final class RemovePages implements DocumentCommand {

    /** The currently supported command representation version. */
    public static final int VERSION_1 = 1;

    private final PageRange range;

    private RemovePages(PageRange range) {
        this.range = Objects.requireNonNull(range, "range");
    }

    /**
     * Creates a version-1 page-removal command.
     *
     * @param range the inclusive one-based range to remove
     * @return the immutable command
     */
    public static RemovePages version1(PageRange range) {
        return new RemovePages(range);
    }

    /**
     * Returns the command representation version.
     *
     * @return {@link #VERSION_1}
     */
    public int getVersion() {
        return VERSION_1;
    }

    /**
     * Returns the requested page range.
     *
     * @return the inclusive range
     */
    public PageRange getRange() {
        return range;
    }
}
