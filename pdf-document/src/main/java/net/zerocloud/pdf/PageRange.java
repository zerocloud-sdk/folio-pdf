package net.zerocloud.pdf;

/**
 * An inclusive range of one-based page numbers.
 *
 * @since 0.1.0
 */
public final class PageRange {

    private final int firstPageNumber;
    private final int lastPageNumber;

    private PageRange(int firstPageNumber, int lastPageNumber) {
        this.firstPageNumber = firstPageNumber;
        this.lastPageNumber = lastPageNumber;
    }

    /**
     * Creates an inclusive one-based page range.
     *
     * @param firstPageNumber the first page number
     * @param lastPageNumber the last page number
     * @return the immutable range
     */
    public static PageRange of(int firstPageNumber, int lastPageNumber) {
        return new PageRange(firstPageNumber, lastPageNumber);
    }

    /**
     * Returns the inclusive first page number.
     *
     * @return the first page number
     */
    public int getFirstPageNumber() {
        return firstPageNumber;
    }

    /**
     * Returns the inclusive last page number.
     *
     * @return the last page number
     */
    public int getLastPageNumber() {
        return lastPageNumber;
    }
}
