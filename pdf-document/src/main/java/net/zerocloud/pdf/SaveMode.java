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
     * Append a new revision while preserving the complete primary Source as
     * an unchanged prefix, subject to the incremental command and Existing
     * Signature permission policy.
     */
    INCREMENTAL
}
