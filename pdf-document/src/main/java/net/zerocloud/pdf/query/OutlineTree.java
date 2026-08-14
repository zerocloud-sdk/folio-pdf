package net.zerocloud.pdf.query;

import java.util.List;
import net.zerocloud.pdf.DocumentQuery;
import net.zerocloud.pdf.OutlineItem;

/**
 * Reports the document outline as a detached immutable item tree after all
 * preceding session commands.
 *
 * <p>The caller declares the maximum total item count across all levels; a
 * larger tree fails with
 * {@link net.zerocloud.pdf.DocumentFailureCode#METADATA_LIMIT_EXCEEDED}.
 * Malformed outline structures fail with
 * {@link net.zerocloud.pdf.DocumentFailureCode#QUERY_FAILED}.</p>
 *
 * @since 0.1.0
 */
public final class OutlineTree implements DocumentQuery<List<OutlineItem>> {

    /** The currently supported query representation version. */
    public static final int VERSION_1 = 1;

    private final int maximumItems;

    private OutlineTree(int maximumItems) {
        this.maximumItems = maximumItems;
    }

    /**
     * Creates a version-1 outline query.
     *
     * @param maximumItems the maximum total outline item count
     * @return the immutable query
     */
    public static OutlineTree version1(int maximumItems) {
        if (maximumItems < 0) {
            throw new IllegalArgumentException(
                    "maximumItems must not be negative");
        }
        return new OutlineTree(maximumItems);
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
     * Returns the caller-declared maximum total item count.
     *
     * @return the item bound
     */
    public int getMaximumItems() {
        return maximumItems;
    }
}
