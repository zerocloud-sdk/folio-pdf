package net.zerocloud.pdf.query;

import net.zerocloud.pdf.DocumentQuery;
import net.zerocloud.pdf.ObjectReference;

/**
 * Obtains the current Session's Object Reference for one one-based page.
 *
 * @since 0.1.0
 */
public final class PageObjectReference
        implements DocumentQuery<ObjectReference> {

    /** The currently supported query representation version. */
    public static final int VERSION_1 = 1;

    private final int pageNumber;

    private PageObjectReference(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    /**
     * Creates a version-1 page Object Reference query.
     *
     * @param pageNumber the one-based page number
     * @return the immutable query
     */
    public static PageObjectReference version1(int pageNumber) {
        return new PageObjectReference(pageNumber);
    }

    /**
     * Returns the query representation version.
     *
     * @return {@link #VERSION_1}
     */
    public int getVersion() {
        return VERSION_1;
    }

    /**
     * Returns the requested one-based page number.
     *
     * @return the page number
     */
    public int getPageNumber() {
        return pageNumber;
    }
}
