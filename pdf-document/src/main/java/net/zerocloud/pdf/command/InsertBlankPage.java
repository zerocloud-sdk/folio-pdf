package net.zerocloud.pdf.command;

import net.zerocloud.pdf.DocumentCommand;

/**
 * Inserts one library-default blank page at a one-based page position.
 *
 * @since 0.1.0
 */
public final class InsertBlankPage implements DocumentCommand {

    /** The currently supported command representation version. */
    public static final int VERSION_1 = 1;

    private final int pageNumber;

    private InsertBlankPage(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    /**
     * Creates a version-1 insertion command.
     *
     * @param pageNumber the one-based position before which the page is inserted
     * @return the immutable command
     */
    public static InsertBlankPage version1(int pageNumber) {
        return new InsertBlankPage(pageNumber);
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
     * Returns the requested one-based insertion position.
     *
     * @return the page position
     */
    public int getPageNumber() {
        return pageNumber;
    }
}
