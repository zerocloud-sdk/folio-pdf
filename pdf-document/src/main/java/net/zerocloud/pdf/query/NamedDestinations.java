package net.zerocloud.pdf.query;

import java.util.Map;
import net.zerocloud.pdf.DocumentQuery;
import net.zerocloud.pdf.PageDestination;

/**
 * Reports the document's named destinations in name-tree order as a detached
 * immutable map after all preceding session commands.
 *
 * <p>The caller declares the maximum destination entry count; a larger tree
 * fails with
 * {@link net.zerocloud.pdf.DocumentFailureCode#METADATA_LIMIT_EXCEEDED}.
 * Malformed name trees fail with
 * {@link net.zerocloud.pdf.DocumentFailureCode#QUERY_FAILED}.</p>
 *
 * @since 0.1.0
 */
public final class NamedDestinations
        implements DocumentQuery<Map<String, PageDestination>> {

    /** The currently supported query representation version. */
    public static final int VERSION_1 = 1;

    private final int maximumEntries;

    private NamedDestinations(int maximumEntries) {
        this.maximumEntries = maximumEntries;
    }

    /**
     * Creates a version-1 named-destination query.
     *
     * @param maximumEntries the maximum destination entry count
     * @return the immutable query
     */
    public static NamedDestinations version1(int maximumEntries) {
        if (maximumEntries < 0) {
            throw new IllegalArgumentException(
                    "maximumEntries must not be negative");
        }
        return new NamedDestinations(maximumEntries);
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
     * Returns the caller-declared maximum entry count.
     *
     * @return the entry bound
     */
    public int getMaximumEntries() {
        return maximumEntries;
    }
}
