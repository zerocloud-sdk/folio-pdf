package net.zerocloud.pdf.command;

import java.util.Objects;
import net.zerocloud.pdf.DocumentCommand;
import net.zerocloud.pdf.PageRange;

/**
 * Copies an inclusive page range before a one-based position in the current
 * page sequence.
 *
 * @since 0.1.0
 */
public final class CopyPages implements DocumentCommand {

    /** The currently supported command representation version. */
    public static final int VERSION_1 = 1;

    private final PageRange range;
    private final int insertionPageNumber;

    private CopyPages(PageRange range, int insertionPageNumber) {
        this.range = Objects.requireNonNull(range, "range");
        this.insertionPageNumber = insertionPageNumber;
    }

    /**
     * Creates a version-1 page-copy command.
     *
     * @param range the inclusive one-based range to copy
     * @param insertionPageNumber the one-based position in the original sequence
     * @return the immutable command
     */
    public static CopyPages version1(
            PageRange range,
            int insertionPageNumber) {
        return new CopyPages(range, insertionPageNumber);
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
     * Returns the insertion position in the original page sequence.
     *
     * @return the one-based insertion position
     */
    public int getInsertionPageNumber() {
        return insertionPageNumber;
    }
}
