package net.zerocloud.pdf.query;

import java.util.List;
import net.zerocloud.pdf.DocumentQuery;
import net.zerocloud.pdf.EmbeddedFileSummary;

/**
 * Lists the document's embedded files as detached immutable summaries in
 * name-tree order after all preceding session commands.
 *
 * <p>The caller declares the maximum file count; a larger tree fails with
 * {@link net.zerocloud.pdf.DocumentFailureCode#METADATA_LIMIT_EXCEEDED}.
 * Malformed embedded-file structures fail with
 * {@link net.zerocloud.pdf.DocumentFailureCode#QUERY_FAILED}.</p>
 *
 * @since 0.1.0
 */
public final class EmbeddedFiles
        implements DocumentQuery<List<EmbeddedFileSummary>> {

    /** The currently supported query representation version. */
    public static final int VERSION_1 = 1;

    private final int maximumEntries;

    private EmbeddedFiles(int maximumEntries) {
        this.maximumEntries = maximumEntries;
    }

    /**
     * Creates a version-1 embedded-files query.
     *
     * @param maximumEntries the maximum embedded-file count
     * @return the immutable query
     */
    public static EmbeddedFiles version1(int maximumEntries) {
        if (maximumEntries < 0) {
            throw new IllegalArgumentException(
                    "maximumEntries must not be negative");
        }
        return new EmbeddedFiles(maximumEntries);
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
     * Returns the caller-declared maximum file count.
     *
     * @return the entry bound
     */
    public int getMaximumEntries() {
        return maximumEntries;
    }
}
