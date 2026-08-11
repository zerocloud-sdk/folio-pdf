package net.zerocloud.pdf.query;

import net.zerocloud.pdf.DocumentQuery;
import net.zerocloud.pdf.ObjectReference;

/**
 * Obtains the current Session's stable document-catalog Object Reference.
 *
 * @since 0.1.0
 */
public final class DocumentRootReference
        implements DocumentQuery<ObjectReference> {

    /** The query representation version. */
    public static final int VERSION = 1;

    /** The immutable version-1 query instance. */
    public static final DocumentRootReference INSTANCE =
            new DocumentRootReference();

    private DocumentRootReference() {
    }

    /**
     * Returns this query's representation version.
     *
     * @return {@link #VERSION}
     */
    public int getVersion() {
        return VERSION;
    }
}
