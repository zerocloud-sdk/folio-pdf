package net.zerocloud.pdf.command;

import java.util.Objects;
import net.zerocloud.pdf.DocumentCommand;
import net.zerocloud.pdf.PageRange;

/**
 * Moves an inclusive page range to a position in the sequence that remains
 * after the selected pages are removed.
 *
 * @since 0.1.0
 */
public final class MovePages implements DocumentCommand {

    /** The currently supported command representation version. */
    public static final int VERSION_1 = 1;

    private final PageRange range;
    private final int destinationPageNumber;

    private MovePages(PageRange range, int destinationPageNumber) {
        this.range = Objects.requireNonNull(range, "range");
        this.destinationPageNumber = destinationPageNumber;
    }

    /**
     * Creates a version-1 page-movement command.
     *
     * @param range the inclusive one-based range to move
     * @param destinationPageNumber the one-based position after range removal
     * @return the immutable command
     */
    public static MovePages version1(
            PageRange range,
            int destinationPageNumber) {
        return new MovePages(range, destinationPageNumber);
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
     * Returns the selected page range.
     *
     * @return the inclusive range
     */
    public PageRange getRange() {
        return range;
    }

    /**
     * Returns the destination position in the post-removal sequence.
     *
     * @return the one-based destination position
     */
    public int getDestinationPageNumber() {
        return destinationPageNumber;
    }
}
