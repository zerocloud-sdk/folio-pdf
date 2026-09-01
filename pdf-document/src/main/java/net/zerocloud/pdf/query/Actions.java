package net.zerocloud.pdf.query;

import net.zerocloud.pdf.DocumentActions;
import net.zerocloud.pdf.DocumentQuery;

/**
 * Reads supported local GoTo Action bindings after preceding Commands.
 *
 * <p>Unsupported or malformed Action graphs fail with
 * {@link net.zerocloud.pdf.DocumentFailureCode#QUERY_FAILED}; exhausting the
 * declared count fails with
 * {@link net.zerocloud.pdf.DocumentFailureCode#ACTION_LIMIT_EXCEEDED}.</p>
 *
 * @since 0.1.0
 */
public final class Actions implements DocumentQuery<DocumentActions> {

    /** The currently supported query representation version. */
    public static final int VERSION_1 = 1;

    private final int maximumActions;

    private Actions(int maximumActions) {
        this.maximumActions = maximumActions;
    }

    /**
     * Creates a bounded version-1 Action query.
     * @param maximumActions maximum catalog and page Action bindings
     * @return the immutable query
     */
    public static Actions version1(int maximumActions) {
        if (maximumActions < 0) {
            throw new IllegalArgumentException(
                    "maximumActions must not be negative");
        }
        return new Actions(maximumActions);
    }

    /** Returns the query version. @return {@link #VERSION_1} */
    public int getVersion() {
        return VERSION_1;
    }

    /** Returns the Action-count bound. @return the bound */
    public int getMaximumActions() {
        return maximumActions;
    }
}
