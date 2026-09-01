package net.zerocloud.pdf.query;

import java.util.List;
import net.zerocloud.pdf.Annotation;
import net.zerocloud.pdf.DocumentQuery;

/**
 * Reads supported annotations in page and annotation-array order.
 *
 * <p>The caller bounds the total annotation count, decoded appearance bytes,
 * and decoded file-attachment bytes. The last bound is retained across every
 * version-1 annotation subtype even when a document contains no attachment
 * annotation. Malformed or unsupported graphs fail with
 * {@link net.zerocloud.pdf.DocumentFailureCode#QUERY_FAILED}; exhausting any
 * declared bound fails with
 * {@link net.zerocloud.pdf.DocumentFailureCode#ANNOTATION_LIMIT_EXCEEDED}.</p>
 *
 * @since 0.1.0
 */
public final class Annotations implements DocumentQuery<List<Annotation>> {

    /** The currently supported query representation version. */
    public static final int VERSION_1 = 1;

    private final int maximumAnnotations;
    private final long maximumAppearanceBytes;
    private final long maximumAttachmentBytes;

    private Annotations(
            int maximumAnnotations,
            long maximumAppearanceBytes,
            long maximumAttachmentBytes) {
        this.maximumAnnotations = maximumAnnotations;
        this.maximumAppearanceBytes = maximumAppearanceBytes;
        this.maximumAttachmentBytes = maximumAttachmentBytes;
    }

    /**
     * Creates a bounded version-1 annotation query.
     * @param maximumAnnotations maximum total annotation count
     * @param maximumAppearanceBytes maximum decoded normal-appearance bytes
     * @param maximumAttachmentBytes maximum decoded attachment bytes
     * @return the immutable query
     */
    public static Annotations version1(
            int maximumAnnotations,
            long maximumAppearanceBytes,
            long maximumAttachmentBytes) {
        if (maximumAnnotations < 0
                || maximumAppearanceBytes < 0L
                || maximumAttachmentBytes < 0L) {
            throw new IllegalArgumentException("annotation limits must not be negative");
        }
        return new Annotations(maximumAnnotations,
                maximumAppearanceBytes, maximumAttachmentBytes);
    }

    /** Returns the query version. @return {@link #VERSION_1} */
    public int getVersion() {
        return VERSION_1;
    }

    /** Returns the annotation-count bound. @return the bound */
    public int getMaximumAnnotations() {
        return maximumAnnotations;
    }

    /** Returns the decoded appearance-byte bound. @return the bound */
    public long getMaximumAppearanceBytes() {
        return maximumAppearanceBytes;
    }

    /** Returns the decoded attachment-byte bound. @return the bound */
    public long getMaximumAttachmentBytes() {
        return maximumAttachmentBytes;
    }
}
