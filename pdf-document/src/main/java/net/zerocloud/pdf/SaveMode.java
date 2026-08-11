package net.zerocloud.pdf;

/**
 * The explicit publication strategy for a changed document.
 *
 * @since 0.1.0
 */
public enum SaveMode {
    /** Serialize a complete replacement document. */
    REWRITE,

    /**
     * Append a new revision while preserving earlier revisions.
     *
     * <p>This value is reserved by the public vocabulary but returns
     * {@link DocumentFailureCode#SAVE_MODE_UNSUPPORTED} until T15.</p>
     */
    INCREMENTAL
}
